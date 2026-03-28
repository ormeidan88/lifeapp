package app.resource;

import app.model.Persona;
import com.github.f4b6a3.ulid.UlidCreator;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.time.Instant;
import java.util.*;

@Path("/api/converse")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConverseResource {

    private static final String USER = "default";

    public static final List<Map<String, String>> AVAILABLE_MODELS = List.of(
            Map.of("id", "us.anthropic.claude-haiku-4-5-20251001-v1:0", "name", "Claude Haiku 4.5"),
            Map.of("id", "us.anthropic.claude-sonnet-4-5-20251001-v1:0", "name", "Claude Sonnet 4.5")
    );

    private static volatile BedrockRuntimeAsyncClient bedrockClient;

    @Inject @Named("personas") DynamoDbTable<Persona> personasTable;

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
    public Multi<String> sendMessage(ConversationRequest req) {
        // Resolve system prompt from persona
        String systemPrompt = "";
        if (req.personaId() != null && !req.personaId().isBlank()) {
            var persona = personasTable.getItem(Key.builder().partitionValue(USER).sortValue(req.personaId()).build());
            if (persona != null && persona.getSystemPrompt() != null) {
                systemPrompt = persona.getSystemPrompt();
            }
        }

        // Build Bedrock Converse messages from history + new user message
        // Bedrock requires strict user/assistant alternation — skip blank entries and
        // drop any consecutive same-role messages (keeps the last one).
        List<Message> messages = new ArrayList<>();
        if (req.history() != null) {
            for (var h : req.history()) {
                String role = h.getOrDefault("role", "user");
                String content = h.getOrDefault("content", "");
                if (content.isBlank()) continue;
                ConversationRole bedrockRole = "assistant".equals(role) ? ConversationRole.ASSISTANT : ConversationRole.USER;
                if (!messages.isEmpty() && messages.get(messages.size() - 1).role() == bedrockRole) {
                    // Merge consecutive same-role entries by keeping the last one
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
                ? req.modelId()
                : AVAILABLE_MODELS.get(0).get("id");

        final List<Message> finalMessages = Collections.unmodifiableList(messages);
        final String finalModelId = modelId;
        final String finalSystemPrompt = systemPrompt;

        return Multi.createFrom().emitter(emitter -> {
            try {
                ConverseStreamRequest.Builder reqBuilder = ConverseStreamRequest.builder()
                        .modelId(finalModelId)
                        .messages(finalMessages);

                if (!finalSystemPrompt.isBlank()) {
                    reqBuilder.system(SystemContentBlock.fromText(finalSystemPrompt));
                }

                bedrock().converseStream(reqBuilder.build(),
                        ConverseStreamResponseHandler.builder()
                                .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                                        .onContentBlockDelta(event -> {
                                            String text = event.delta().text();
                                            if (text != null) emitter.emit(jsonEncode(text));
                                        })
                                        .build())
                                .onComplete(() -> emitter.complete())
                                .onError(e -> {
                                    emitter.emit(jsonEncode("STREAMING_ERROR: " + friendlyError(e)));
                                    emitter.complete();
                                })
                                .build());
            } catch (Exception e) {
                emitter.emit(jsonEncode("STREAMING_ERROR: " + friendlyError(e)));
                emitter.complete();
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
