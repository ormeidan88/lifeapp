package app.resource;

import app.config.AuthConfig;
import app.model.Persona;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.UlidCreator;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Path("/api/converse")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConverseResource {

    private static final String USER = "default";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile BedrockRuntimeAsyncClient bedrockClient;
    private static volatile HttpClient httpClientInstance;

    public static final List<Map<String, String>> AVAILABLE_MODELS = List.of(
            Map.of("id", "us.anthropic.claude-haiku-4-5-20251001-v1:0", "name", "Claude Haiku 4.5"),
            Map.of("id", "us.anthropic.claude-sonnet-4-5-20251001-v1:0", "name", "Claude Sonnet 4.5"),
            Map.of("id", "global.anthropic.claude-opus-4-6-v1", "name", "Claude Opus 4.6")
    );

    @Inject @Named("personas") DynamoDbTable<Persona> personasTable;
    @Inject AuthConfig authConfig;

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int serverPort;

    private List<Tool> tools;

    @PostConstruct
    void init() {
        tools = buildTools();
    }

    // ── Models ───────────────────────────────────────────────────────────────

    @GET
    @Path("/models")
    public jakarta.ws.rs.core.Response getModels() {
        return jakarta.ws.rs.core.Response.ok(Map.of("models", AVAILABLE_MODELS)).build();
    }

    // ── Personas CRUD ────────────────────────────────────────────────────────

    @GET
    @Path("/personas")
    public jakarta.ws.rs.core.Response listPersonas() {
        var list = personasTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream()
                .sorted(Comparator.comparing(Persona::getCreatedAt))
                .map(this::toMap)
                .toList();
        return jakarta.ws.rs.core.Response.ok(Map.of("personas", list)).build();
    }

    @POST
    @Path("/personas")
    public jakarta.ws.rs.core.Response createPersona(Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank())
            return jakarta.ws.rs.core.Response.status(400).entity(Map.of("error", "Name is required")).build();
        var p = new Persona();
        p.setUserId(USER);
        p.setPersonaId(UlidCreator.getUlid().toString());
        p.setName(name.trim());
        p.setSystemPrompt(body.getOrDefault("systemPrompt", ""));
        p.setCreatedAt(Instant.now().toString());
        personasTable.putItem(p);
        return jakarta.ws.rs.core.Response.status(201).entity(toMap(p)).build();
    }

    @PATCH
    @Path("/personas/{id}")
    public jakarta.ws.rs.core.Response updatePersona(@PathParam("id") String id, Map<String, String> body) {
        var p = personasTable.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (p == null)
            return jakarta.ws.rs.core.Response.status(404).entity(Map.of("error", "Persona not found")).build();
        if (body.containsKey("name") && !body.get("name").isBlank()) p.setName(body.get("name").trim());
        if (body.containsKey("systemPrompt")) p.setSystemPrompt(body.get("systemPrompt"));
        personasTable.putItem(p);
        return jakarta.ws.rs.core.Response.ok(toMap(p)).build();
    }

    @DELETE
    @Path("/personas/{id}")
    public jakarta.ws.rs.core.Response deletePersona(@PathParam("id") String id) {
        personasTable.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return jakarta.ws.rs.core.Response.noContent().build();
    }

    // ── Streaming message ────────────────────────────────────────────────────

    @POST
    @Path("/message")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Blocking
    public Multi<String> sendMessage(ConversationRequest req, @Context HttpHeaders requestHeaders) {
        String systemPrompt = "";
        if (req.personaId() != null && !req.personaId().isBlank()) {
            var persona = personasTable.getItem(Key.builder().partitionValue(USER).sortValue(req.personaId()).build());
            if (persona != null && persona.getSystemPrompt() != null) {
                systemPrompt = persona.getSystemPrompt();
            }
        }

        List<Message> messages = new ArrayList<>();
        if (req.history() != null) {
            for (var h : req.history()) {
                String role = h.getOrDefault("role", "user");
                String content = h.getOrDefault("content", "");
                if (content.isBlank()) continue;
                ConversationRole bedrockRole = "assistant".equals(role) ? ConversationRole.ASSISTANT : ConversationRole.USER;
                if (!messages.isEmpty() && messages.get(messages.size() - 1).role() == bedrockRole) {
                    messages.remove(messages.size() - 1);
                }
                messages.add(Message.builder()
                        .role(bedrockRole)
                        .content(ContentBlock.fromText(content))
                        .build());
            }
        }
        messages.add(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(req.message()))
                .build());

        String modelId = (req.modelId() != null && !req.modelId().isBlank())
                ? req.modelId() : AVAILABLE_MODELS.get(0).get("id");

        final List<Message> finalMessages = new ArrayList<>(messages);
        final String finalModelId = modelId;
        final String finalSystemPrompt = systemPrompt;

        return Multi.createFrom().emitter(emitter ->
                runAgentLoop(finalMessages, finalModelId, finalSystemPrompt, emitter)
        );
    }

    private void runAgentLoop(List<Message> messages, String modelId, String systemPrompt,
                               MultiEmitter<? super String> emitter) {
        List<ToolUseBlock> pendingToolUses = Collections.synchronizedList(new ArrayList<>());
        StringBuilder assistantText = new StringBuilder();
        AtomicReference<String> currentToolId = new AtomicReference<>();
        AtomicReference<String> currentToolName = new AtomicReference<>();
        StringBuilder currentToolInput = new StringBuilder();

        ConverseStreamRequest.Builder reqBuilder = ConverseStreamRequest.builder()
                .modelId(modelId)
                .messages(messages)
                .toolConfig(ToolConfiguration.builder().tools(tools).build());

        if (!systemPrompt.isBlank()) {
            reqBuilder.system(SystemContentBlock.fromText(systemPrompt));
        }

        bedrock().converseStream(reqBuilder.build(),
                ConverseStreamResponseHandler.builder()
                        .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                                .onContentBlockStart(event -> {
                                    if (event.start() != null && event.start().toolUse() != null) {
                                        currentToolId.set(event.start().toolUse().toolUseId());
                                        currentToolName.set(event.start().toolUse().name());
                                        currentToolInput.setLength(0);
                                    }
                                })
                                .onContentBlockDelta(event -> {
                                    if (event.delta().text() != null) {
                                        String text = event.delta().text();
                                        emitter.emit(jsonEncode(text));
                                        assistantText.append(text);
                                    } else if (event.delta().toolUse() != null
                                            && event.delta().toolUse().input() != null) {
                                        currentToolInput.append(event.delta().toolUse().input());
                                    }
                                })
                                .onContentBlockStop(event -> {
                                    String toolId = currentToolId.getAndSet(null);
                                    String toolName = currentToolName.getAndSet(null);
                                    if (toolId != null && toolName != null) {
                                        pendingToolUses.add(ToolUseBlock.builder()
                                                .toolUseId(toolId)
                                                .name(toolName)
                                                .input(jsonToDocument(currentToolInput.toString()))
                                                .build());
                                    }
                                })
                                .build())
                        .onComplete(() -> {
                            if (pendingToolUses.isEmpty()) {
                                emitter.complete();
                                return;
                            }
                            // Tool use requested — execute on virtual thread to allow blocking HTTP calls
                            Thread.ofVirtual().start(() -> {
                                try {
                                    // Build assistant message: text (if any) + tool use blocks
                                    List<ContentBlock> assistantBlocks = new ArrayList<>();
                                    if (!assistantText.isEmpty()) {
                                        assistantBlocks.add(ContentBlock.fromText(assistantText.toString()));
                                    }
                                    for (ToolUseBlock tu : pendingToolUses) {
                                        assistantBlocks.add(ContentBlock.fromToolUse(tu));
                                    }
                                    messages.add(Message.builder()
                                            .role(ConversationRole.ASSISTANT)
                                            .content(assistantBlocks)
                                            .build());

                                    // Execute each tool and collect results
                                    List<ContentBlock> resultBlocks = new ArrayList<>();
                                    for (ToolUseBlock tu : pendingToolUses) {
                                        String result = executeTool(tu.name(), tu.input());
                                        resultBlocks.add(ContentBlock.fromToolResult(
                                                ToolResultBlock.builder()
                                                        .toolUseId(tu.toolUseId())
                                                        .content(ToolResultContentBlock.fromText(result))
                                                        .build()
                                        ));
                                    }
                                    messages.add(Message.builder()
                                            .role(ConversationRole.USER)
                                            .content(resultBlocks)
                                            .build());

                                    runAgentLoop(messages, modelId, systemPrompt, emitter);
                                } catch (Exception e) {
                                    emitter.emit(jsonEncode("STREAMING_ERROR: " + friendlyError(e)));
                                    emitter.complete();
                                }
                            });
                        })
                        .onError(e -> {
                            emitter.emit(jsonEncode("STREAMING_ERROR: " + friendlyError(e)));
                            emitter.complete();
                        })
                        .build());
    }

    // ── Tool definitions ─────────────────────────────────────────────────────

    private List<Tool> buildTools() {
        List<Tool> t = new ArrayList<>();

        // Inbox
        t.add(tool("list_inbox", "List all items in the inbox capture queue", props(), req()));
        t.add(tool("add_inbox_item", "Add a new item to the inbox",
                props("text", "The text of the inbox item"), req("text")));
        t.add(tool("convert_inbox_item", "Convert an inbox item to a task, project, or list item",
                props("id", "Inbox item ID",
                        "targetType", "One of: task, project, list-item",
                        "projectId", "Required for task or list-item target types",
                        "listId", "Required for list-item target type",
                        "name", "Optional project name when targetType is project"),
                req("id", "targetType")));

        // Projects
        t.add(tool("list_projects", "List all projects, optionally filtered by status (ACTIVE, COMPLETED, ARCHIVED)",
                props("status", "Optional filter: ACTIVE, COMPLETED, or ARCHIVED"), req()));
        t.add(tool("get_project", "Get details of a specific project by ID",
                props("id", "The project ID"), req("id")));
        t.add(tool("create_project", "Create a new project",
                props("name", "Project name", "description", "Optional description"), req("name")));
        t.add(tool("update_project", "Update a project's name or status",
                props("id", "The project ID",
                        "name", "New name",
                        "status", "New status: ACTIVE, COMPLETED, or ARCHIVED"),
                req("id")));

        // Tasks
        t.add(tool("list_tasks", "List all tasks in a project",
                props("projectId", "The project ID"), req("projectId")));
        t.add(tool("create_task", "Create a new task in a project",
                props("projectId", "The project ID",
                        "title", "Task title",
                        "parentTaskId", "Optional parent task ID for subtasks"),
                req("projectId", "title")));
        t.add(tool("update_task", "Update a task — mark it done, change its title, etc.",
                props("id", "The task ID",
                        "projectId", "The project ID",
                        "title", "New title",
                        "done", "true or false"),
                req("id", "projectId")));
        t.add(tool("reorder_tasks", "Reorder tasks within a project",
                props("projectId", "The project ID",
                        "taskIds", "Comma-separated task IDs in the desired order"),
                req("projectId", "taskIds")));

        // Lists
        t.add(tool("list_lists", "List all custom lists and system lists",
                props(), req()));
        t.add(tool("list_list_items", "Get all items in a specific list",
                props("listId", "The list ID"), req("listId")));
        t.add(tool("create_list", "Create a new custom list",
                props("name", "List name"), req("name")));
        t.add(tool("add_to_list", "Add a task to a list by task ID",
                props("listId", "The list ID", "taskId", "The task ID"), req("listId", "taskId")));

        // Pages
        t.add(tool("list_pages", "List pages, optionally filtered by owner or parent",
                props("ownerType", "Filter: standalone, project, or book",
                        "ownerId", "Filter by owner ID",
                        "parentPageId", "Filter by parent page ID"),
                req()));
        t.add(tool("get_page", "Get a page and its full content by ID",
                props("id", "The page ID"), req("id")));
        t.add(tool("search_pages", "Search pages by title keyword",
                props("q", "Search query"), req("q")));
        t.add(tool("create_page", "Create a new page",
                props("title", "Page title",
                        "parentPageId", "Optional parent page ID",
                        "ownerType", "Owner type: standalone, project, or book",
                        "ownerId", "Owner ID"),
                req()));
        t.add(tool("update_page", "Update a page's title",
                props("id", "The page ID", "title", "New title"), req("id")));

        // Habits
        t.add(tool("list_habits", "List all habits with current and longest streaks",
                props(), req()));
        t.add(tool("get_habit_entries", "Get habit log entries for a date range",
                props("id", "The habit ID", "from", "Start date YYYY-MM-DD", "to", "End date YYYY-MM-DD"),
                req("id", "from", "to")));
        t.add(tool("create_habit", "Create a new habit to track",
                props("name", "Habit name", "color", "Optional hex color code"), req("name")));
        t.add(tool("update_habit", "Update a habit's name or color",
                props("id", "The habit ID", "name", "New name", "color", "New color"), req("id")));
        t.add(tool("log_habit_entry", "Log a habit completion for a specific date",
                props("id", "The habit ID",
                        "date", "Date YYYY-MM-DD",
                        "value", "One of: YES, NO, SKIP"),
                req("id", "date", "value")));

        // Books
        t.add(tool("list_books", "List all books in the reading list",
                props(), req()));
        t.add(tool("search_books", "Search Google Books catalog by query",
                props("q", "Search query"), req("q")));
        t.add(tool("add_book", "Add a book to the reading list",
                props("googleBooksId", "Google Books ID",
                        "title", "Book title",
                        "authors", "Comma-separated authors",
                        "coverUrl", "Optional cover image URL"),
                req("googleBooksId", "title")));

        // Calendar
        t.add(tool("list_calendar_events", "List calendar events in a date range",
                props("from", "Start date YYYY-MM-DD", "to", "End date YYYY-MM-DD"),
                req("from", "to")));
        t.add(tool("add_calendar_event", "Add a new calendar event",
                props("title", "Event title",
                        "date", "Date YYYY-MM-DD",
                        "startTime", "Optional start time HH:MM",
                        "endTime", "Optional end time HH:MM",
                        "color", "Optional color code"),
                req("title", "date")));
        t.add(tool("update_calendar_event", "Update an existing calendar event",
                props("id", "The event ID",
                        "title", "New title",
                        "date", "New date YYYY-MM-DD",
                        "startTime", "New start time HH:MM",
                        "endTime", "New end time HH:MM",
                        "color", "New color"),
                req("id")));

        // Waiting For
        t.add(tool("list_waiting_for", "List all 'Waiting For' items",
                props(), req()));
        t.add(tool("get_waiting_for_overdue_count", "Get the count of overdue 'Waiting For' items",
                props(), req()));
        t.add(tool("add_waiting_for", "Add a new 'Waiting For' item",
                props("description", "What you are waiting for",
                        "waitingFor", "Who or what you are waiting from",
                        "dueDate", "Expected by date YYYY-MM-DD"),
                req("description", "waitingFor", "dueDate")));
        t.add(tool("acknowledge_waiting_for", "Mark a 'Waiting For' item as received/acknowledged",
                props("id", "The item ID"), req("id")));

        // Decks / Memorize
        t.add(tool("list_decks", "List all flashcard decks with due card counts",
                props(), req()));

        // Daily Notes
        t.add(tool("get_daily_notes", "Get daily notes for a date range",
                props("from", "Start date YYYY-MM-DD", "to", "End date YYYY-MM-DD"),
                req("from", "to")));
        t.add(tool("search_daily_notes", "Search daily notes by keyword",
                props("query", "Search text to find in notes"),
                req("query")));
        t.add(tool("list_deck_cards", "List all cards in a flashcard deck",
                props("deckId", "The deck ID"), req("deckId")));
        t.add(tool("get_due_cards", "Get cards currently due for review in a deck",
                props("deckId", "The deck ID"), req("deckId")));
        t.add(tool("create_deck", "Create a new flashcard deck",
                props("name", "Deck name"), req("name")));
        t.add(tool("add_deck_card", "Add a new flashcard to a deck",
                props("deckId", "The deck ID",
                        "front", "Front (question) side",
                        "back", "Back (answer) side"),
                req("deckId", "front", "back")));
        t.add(tool("review_card", "Submit a review rating for a flashcard",
                props("deckId", "The deck ID",
                        "cardId", "The card ID",
                        "rating", "Rating: AGAIN, HARD, GOOD, or EASY"),
                req("deckId", "cardId", "rating")));

        return t;
    }

    private Tool tool(String name, String description, Document properties, Document required) {
        Document schema = Document.mapBuilder()
                .putString("type", "object")
                .putDocument("properties", properties)
                .putDocument("required", required)
                .build();
        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(ToolInputSchema.fromJson(schema))
                        .build())
                .build();
    }

    /** Build a properties Document from alternating (name, description) pairs. */
    private Document props(String... pairs) {
        Document.MapBuilder b = Document.mapBuilder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            b.putDocument(pairs[i], Document.mapBuilder()
                    .putString("type", "string")
                    .putString("description", pairs[i + 1])
                    .build());
        }
        return b.build();
    }

    /** Build a required-fields Document from a list of field names. */
    private Document req(String... fields) {
        Document.ListBuilder b = Document.listBuilder();
        for (String f : fields) b.addDocument(Document.fromString(f));
        return b.build();
    }

    // ── Tool execution ────────────────────────────────────────────────────────

    private String executeTool(String name, Document input) {
        Map<String, String> p = docToStringMap(input);
        try {
            return switch (name) {
                // Inbox
                case "list_inbox" -> get("/api/inbox", Map.of());
                case "add_inbox_item" -> post("/api/inbox", Map.of("text", p.get("text")));
                case "convert_inbox_item" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("targetType", p.get("targetType"));
                    if (p.containsKey("projectId")) body.put("projectId", p.get("projectId"));
                    if (p.containsKey("listId")) body.put("listId", p.get("listId"));
                    if (p.containsKey("name")) body.put("name", p.get("name"));
                    yield post("/api/inbox/" + p.get("id") + "/convert", body);
                }

                // Projects
                case "list_projects" -> {
                    Map<String, String> q = new LinkedHashMap<>();
                    if (p.containsKey("status")) q.put("status", p.get("status"));
                    yield get("/api/projects", q);
                }
                case "get_project" -> get("/api/projects/" + p.get("id"), Map.of());
                case "create_project" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("name", p.get("name"));
                    if (p.containsKey("description")) body.put("description", p.get("description"));
                    yield post("/api/projects", body);
                }
                case "update_project" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    if (p.containsKey("name")) body.put("name", p.get("name"));
                    if (p.containsKey("status")) body.put("status", p.get("status"));
                    yield patch("/api/projects/" + p.get("id"), body);
                }

                // Tasks
                case "list_tasks" -> get("/api/tasks", Map.of("projectId", p.get("projectId")));
                case "create_task" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("projectId", p.get("projectId"));
                    body.put("title", p.get("title"));
                    if (p.containsKey("parentTaskId")) body.put("parentTaskId", p.get("parentTaskId"));
                    yield post("/api/tasks", body);
                }
                case "update_task" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("projectId", p.get("projectId"));
                    if (p.containsKey("title")) body.put("title", p.get("title"));
                    if (p.containsKey("done")) body.put("done", Boolean.parseBoolean(p.get("done")));
                    yield patch("/api/tasks/" + p.get("id"), body);
                }
                case "reorder_tasks" -> {
                    List<String> ids = Arrays.asList(p.get("taskIds").split(",\\s*"));
                    yield post("/api/tasks/reorder", Map.of("projectId", p.get("projectId"), "taskIds", ids));
                }

                // Lists
                case "list_lists" -> get("/api/lists", Map.of());
                case "list_list_items" -> get("/api/lists/" + p.get("listId") + "/items", Map.of());
                case "create_list" -> post("/api/lists", Map.of("name", p.get("name")));
                case "add_to_list" -> post("/api/lists/" + p.get("listId") + "/items",
                        Map.of("taskId", p.get("taskId")));

                // Pages
                case "list_pages" -> {
                    Map<String, String> q = new LinkedHashMap<>();
                    if (p.containsKey("ownerType")) q.put("ownerType", p.get("ownerType"));
                    if (p.containsKey("ownerId")) q.put("ownerId", p.get("ownerId"));
                    if (p.containsKey("parentPageId")) q.put("parentPageId", p.get("parentPageId"));
                    yield get("/api/pages", q);
                }
                case "get_page" -> get("/api/pages/" + p.get("id"), Map.of());
                case "search_pages" -> get("/api/pages/search", Map.of("q", p.get("q")));
                case "create_page" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    if (p.containsKey("title")) body.put("title", p.get("title"));
                    if (p.containsKey("parentPageId")) body.put("parentPageId", p.get("parentPageId"));
                    if (p.containsKey("ownerType")) body.put("ownerType", p.get("ownerType"));
                    if (p.containsKey("ownerId")) body.put("ownerId", p.get("ownerId"));
                    yield post("/api/pages", body);
                }
                case "update_page" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    if (p.containsKey("title")) body.put("title", p.get("title"));
                    yield patch("/api/pages/" + p.get("id"), body);
                }

                // Habits
                case "list_habits" -> get("/api/habits", Map.of());
                case "get_habit_entries" -> get("/api/habits/" + p.get("id") + "/entries",
                        Map.of("from", p.get("from"), "to", p.get("to")));
                case "create_habit" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("name", p.get("name"));
                    if (p.containsKey("color")) body.put("color", p.get("color"));
                    yield post("/api/habits", body);
                }
                case "update_habit" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    if (p.containsKey("name")) body.put("name", p.get("name"));
                    if (p.containsKey("color")) body.put("color", p.get("color"));
                    yield patch("/api/habits/" + p.get("id"), body);
                }
                case "log_habit_entry" -> post("/api/habits/" + p.get("id") + "/entries",
                        Map.of("date", p.get("date"), "value", p.get("value")));

                // Books
                case "list_books" -> get("/api/books", Map.of());
                case "search_books" -> get("/api/books/search", Map.of("q", p.get("q")));
                case "add_book" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("googleBooksId", p.get("googleBooksId"));
                    body.put("title", p.get("title"));
                    if (p.containsKey("authors"))
                        body.put("authors", List.of(p.get("authors").split(",\\s*")));
                    if (p.containsKey("coverUrl")) body.put("coverUrl", p.get("coverUrl"));
                    yield post("/api/books", body);
                }

                // Calendar
                case "list_calendar_events" -> get("/api/calendar/events",
                        Map.of("from", p.get("from"), "to", p.get("to")));
                case "add_calendar_event" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("title", p.get("title"));
                    body.put("date", p.get("date"));
                    if (p.containsKey("startTime")) body.put("startTime", p.get("startTime"));
                    if (p.containsKey("endTime")) body.put("endTime", p.get("endTime"));
                    if (p.containsKey("color")) body.put("color", p.get("color"));
                    yield post("/api/calendar/events", body);
                }
                case "update_calendar_event" -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    if (p.containsKey("title")) body.put("title", p.get("title"));
                    if (p.containsKey("date")) body.put("date", p.get("date"));
                    if (p.containsKey("startTime")) body.put("startTime", p.get("startTime"));
                    if (p.containsKey("endTime")) body.put("endTime", p.get("endTime"));
                    if (p.containsKey("color")) body.put("color", p.get("color"));
                    yield patch("/api/calendar/events/" + p.get("id"), body);
                }

                // Waiting For
                case "list_waiting_for" -> get("/api/waiting-for", Map.of());
                case "get_waiting_for_overdue_count" -> get("/api/waiting-for/overdue-count", Map.of());
                case "add_waiting_for" -> post("/api/waiting-for", Map.of(
                        "description", p.get("description"),
                        "waitingFor", p.get("waitingFor"),
                        "dueDate", p.get("dueDate")));
                case "acknowledge_waiting_for" ->
                        post("/api/waiting-for/" + p.get("id") + "/acknowledge", Map.of());

                // Decks
                case "list_decks" -> get("/api/decks", Map.of());
                case "list_deck_cards" -> get("/api/decks/" + p.get("deckId") + "/cards", Map.of());
                case "get_due_cards" -> get("/api/decks/" + p.get("deckId") + "/review", Map.of());
                case "create_deck" -> post("/api/decks", Map.of("name", p.get("name")));
                case "add_deck_card" -> post("/api/decks/" + p.get("deckId") + "/cards",
                        Map.of("front", p.get("front"), "back", p.get("back")));
                case "review_card" -> post(
                        "/api/decks/" + p.get("deckId") + "/cards/" + p.get("cardId") + "/review",
                        Map.of("rating", p.get("rating")));

                // Daily Notes
                case "get_daily_notes" -> get("/api/daily-notes",
                        Map.of("from", p.get("from"), "to", p.get("to")));
                case "search_daily_notes" -> get("/api/daily-notes/search",
                        Map.of("q", p.get("query")));

                default -> "{\"error\":\"Unknown tool: " + name + "\"}";
            };
        } catch (Exception e) {
            return "{\"error\":\"Tool execution failed: " + e.getMessage() + "\"}";
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String get(String path, Map<String, String> query) throws Exception {
        StringBuilder url = new StringBuilder("http://localhost:").append(serverPort).append(path);
        if (!query.isEmpty()) {
            url.append("?");
            query.forEach((k, v) -> url.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append("=").append(URLEncoder.encode(v != null ? v : "", StandardCharsets.UTF_8))
                    .append("&"));
            url.setLength(url.length() - 1);
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("X-API-Key", authConfig.getApiKey())
                .GET()
                .build();
        return httpClient().send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String post(String path, Map<String, Object> body) throws Exception {
        String url = "http://localhost:" + serverPort + path;
        String json = MAPPER.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-API-Key", authConfig.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient().send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String patch(String path, Map<String, Object> body) throws Exception {
        String url = "http://localhost:" + serverPort + path;
        String json = MAPPER.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-API-Key", authConfig.getApiKey())
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient().send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    private HttpClient httpClient() {
        if (httpClientInstance == null) {
            synchronized (ConverseResource.class) {
                if (httpClientInstance == null) {
                    httpClientInstance = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();
                }
            }
        }
        return httpClientInstance;
    }

    // ── Document conversion ───────────────────────────────────────────────────

    private Map<String, String> docToStringMap(Document doc) {
        if (doc == null || !doc.isMap()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        doc.asMap().forEach((k, v) -> {
            if (v.isString()) result.put(k, v.asString());
            else if (v.isNumber()) result.put(k, v.asNumber().toString());
            else if (v.isBoolean()) result.put(k, String.valueOf(v.asBoolean()));
            else result.put(k, v.toString());
        });
        return result;
    }

    private Document jsonToDocument(String json) {
        if (json == null || json.isBlank()) return Document.mapBuilder().build();
        try {
            return nodeToDocument(MAPPER.readTree(json));
        } catch (Exception e) {
            return Document.mapBuilder().build();
        }
    }

    private Document nodeToDocument(JsonNode node) {
        return switch (node.getNodeType()) {
            case OBJECT -> {
                Document.MapBuilder b = Document.mapBuilder();
                node.fields().forEachRemaining(e -> b.putDocument(e.getKey(), nodeToDocument(e.getValue())));
                yield b.build();
            }
            case ARRAY -> {
                Document.ListBuilder b = Document.listBuilder();
                node.elements().forEachRemaining(e -> b.addDocument(nodeToDocument(e)));
                yield b.build();
            }
            case STRING -> Document.fromString(node.asText());
            case NUMBER -> Document.fromNumber(node.decimalValue());
            case BOOLEAN -> Document.fromBoolean(node.asBoolean());
            default -> Document.fromNull();
        };
    }

    // ── Existing helpers ──────────────────────────────────────────────────────

    private BedrockRuntimeAsyncClient bedrock() {
        if (bedrockClient == null) {
            synchronized (ConverseResource.class) {
                if (bedrockClient == null) {
                    bedrockClient = BedrockRuntimeAsyncClient.builder()
                            .region(Region.US_EAST_1)
                            .httpClientBuilder(NettyNioAsyncHttpClient.builder())
                            .build();
                }
            }
        }
        return bedrockClient;
    }

    private Map<String, String> toMap(Persona p) {
        return Map.of(
                "id", p.getPersonaId(),
                "name", p.getName(),
                "systemPrompt", p.getSystemPrompt() != null ? p.getSystemPrompt() : "",
                "createdAt", p.getCreatedAt()
        );
    }

    private String jsonEncode(String s) {
        var sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private String friendlyError(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) msg = e.getClass().getSimpleName();
        if (msg.contains("credentials") || msg.contains("Unable to load AWS credentials") || msg.contains("credential"))
            return "AWS credentials not available. Configure your AWS credentials to use Converse.";
        if (msg.contains("expired") || msg.contains("ExpiredToken"))
            return "AWS credentials have expired. Please refresh your credentials.";
        if (msg.contains("AccessDenied") || msg.contains("not authorized"))
            return "Not authorized to call Bedrock. Check your AWS IAM permissions.";
        if (msg.contains("ResourceNotFoundException") || msg.contains("not found"))
            return "Model not found in Bedrock. Ensure the model is enabled in your AWS account (us-east-1).";
        return msg;
    }

    public record ConversationRequest(
            String message,
            String personaId,
            String modelId,
            List<Map<String, String>> history
    ) {}
}
