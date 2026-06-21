package app.resource;

import app.model.Thought;
import app.model.ThoughtContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Path("/api/thoughts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ThoughtResource {

    private static final String USER = "default";
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Inject @Named("thoughts") DynamoDbTable<Thought> table;
    @Inject @Named("thoughtContent") DynamoDbTable<ThoughtContent> contentTable;
    @Inject ObjectMapper mapper;

    @GET @Path("/search")
    public Response search(@QueryParam("q") String q) {
        if (q == null || q.isBlank()) return Response.ok(Map.of("thoughts", List.of())).build();
        String lower = q.toLowerCase();
        var results = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream()
                .filter(t -> t.getTitle() != null && t.getTitle().toLowerCase().contains(lower))
                .map(t -> Map.of("id", t.getThoughtId(), "title", t.getTitle()))
                .toList();
        return Response.ok(Map.of("thoughts", results)).build();
    }

    @POST
    public Response create(Map<String, String> body) {
        String now = Instant.now().toString();
        String kind = body.getOrDefault("kind", "subject");

        if ("daily".equals(kind)) {
            String today = LocalDate.now().format(DAY_FMT);
            var existing = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                    .items().stream()
                    .filter(t -> "daily".equals(t.getKind()))
                    .filter(t -> today.equals(t.getDay()))
                    .filter(t -> t.getParentThoughtId() == null)
                    .findFirst().orElse(null);
            if (existing != null) {
                return Response.status(201).entity(Map.of(
                        "id", existing.getThoughtId(), "title", existing.getTitle(),
                        "kind", existing.getKind(), "day", existing.getDay(),
                        "createdAt", existing.getCreatedAt()
                )).build();
            }
            var thought = new Thought();
            thought.setUserId(USER);
            thought.setThoughtId(UlidCreator.getUlid().toString());
            thought.setTitle(today);
            thought.setKind("daily");
            thought.setDay(today);
            thought.setCreatedAt(now);
            thought.setUpdatedAt(now);
            table.putItem(thought);
            return Response.status(201).entity(Map.of(
                    "id", thought.getThoughtId(), "title", thought.getTitle(),
                    "kind", thought.getKind(), "day", thought.getDay(),
                    "createdAt", thought.getCreatedAt()
            )).build();
        }

        var thought = new Thought();
        thought.setUserId(USER);
        thought.setThoughtId(UlidCreator.getUlid().toString());
        thought.setTitle(body.getOrDefault("title", "Untitled"));
        thought.setKind("subject");
        thought.setParentThoughtId(body.get("parentThoughtId"));
        thought.setCreatedAt(now);
        thought.setUpdatedAt(now);
        table.putItem(thought);
        return Response.status(201).entity(Map.of(
                "id", thought.getThoughtId(), "title", thought.getTitle(),
                "kind", thought.getKind(), "createdAt", thought.getCreatedAt()
        )).build();
    }

    @GET
    public Response list(@QueryParam("kind") String kind,
                         @QueryParam("parentThoughtId") String parentThoughtId) {
        var all = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream();

        if (parentThoughtId != null) {
            all = all.filter(t -> parentThoughtId.equals(t.getParentThoughtId()));
        } else {
            all = all.filter(t -> t.getParentThoughtId() == null);
        }
        if (kind != null) {
            all = all.filter(t -> kind.equals(t.getKind()));
        }

        var list = new ArrayList<>(all.toList());
        if ("daily".equals(kind)) {
            list.sort(Comparator.comparing(Thought::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        } else if ("subject".equals(kind)) {
            list.sort(Comparator.comparing(t -> t.getTitle() == null ? "" : t.getTitle().toLowerCase()));
        }

        var thoughts = list.stream().map(t -> {
            var m = new HashMap<String, Object>();
            m.put("id", t.getThoughtId()); m.put("title", t.getTitle());
            m.put("kind", t.getKind()); m.put("createdAt", t.getCreatedAt());
            m.put("updatedAt", t.getUpdatedAt());
            if (t.getDay() != null) m.put("day", t.getDay());
            if (t.getParentThoughtId() != null) m.put("parentThoughtId", t.getParentThoughtId());
            return m;
        }).toList();
        return Response.ok(Map.of("thoughts", thoughts)).build();
    }

    @GET @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        var thought = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (thought == null) return Response.status(404).entity(Map.of("error", "Thought not found")).build();

        // Get latest content
        var contents = contentTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(id).build()))
                .items().stream().toList();
        Object content = null;
        if (!contents.isEmpty()) {
            try { content = mapper.readTree(contents.getLast().getContent()); }
            catch (Exception e) { content = contents.getLast().getContent(); }
        }

        var result = new HashMap<String, Object>();
        result.put("id", thought.getThoughtId()); result.put("title", thought.getTitle());
        result.put("kind", thought.getKind()); result.put("createdAt", thought.getCreatedAt());
        result.put("updatedAt", thought.getUpdatedAt()); result.put("content", content);
        if (thought.getDay() != null) result.put("day", thought.getDay());
        if (thought.getParentThoughtId() != null) result.put("parentThoughtId", thought.getParentThoughtId());
        return Response.ok(result).build();
    }

    @PATCH @Path("/{id}")
    public Response update(@PathParam("id") String id, JsonNode body) {
        var thought = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (thought == null) return Response.status(404).entity(Map.of("error", "Thought not found")).build();

        String now = Instant.now().toString();
        if (body.has("title")) thought.setTitle(body.get("title").asText());
        thought.setUpdatedAt(now);
        table.putItem(thought);

        if (body.has("content")) {
            String contentJson = body.get("content").toString();
            if (contentJson.length() > 400_000) {
                return Response.status(413).entity(Map.of("error", "Thought content too large")).build();
            }
            // Delete old versions, keep only latest
            contentTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(id).build()))
                    .items().forEach(c -> contentTable.deleteItem(Key.builder().partitionValue(id).sortValue(c.getVersion()).build()));
            var tc = new ThoughtContent();
            tc.setThoughtId(id);
            tc.setVersion(now);
            tc.setContent(contentJson);
            contentTable.putItem(tc);
        }

        return Response.ok(Map.of("id", thought.getThoughtId(), "title", thought.getTitle(), "updatedAt", now)).build();
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        // Delete content
        contentTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(id).build()))
                .items().forEach(c -> contentTable.deleteItem(Key.builder().partitionValue(id).sortValue(c.getVersion()).build()));
        // Delete child thoughts recursively
        table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream().filter(t -> id.equals(t.getParentThoughtId()))
                .forEach(child -> delete(child.getThoughtId()));
        // Delete thought
        table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return Response.noContent().build();
    }
}
