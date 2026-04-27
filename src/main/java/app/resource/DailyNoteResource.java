package app.resource;

import app.model.DailyNote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.*;

@Path("/api/daily-notes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DailyNoteResource {

    private static final String USER = "default";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject @Named("dailyNotes") DynamoDbTable<DailyNote> table;

    @GET
    @Path("/{date}")
    public Response get(@PathParam("date") String date) {
        var note = table.getItem(Key.builder().partitionValue(USER).sortValue(date).build());
        if (note == null) return Response.status(404).entity(Map.of("error", "No note for this date")).build();
        return Response.ok(noteToMap(note)).build();
    }

    @PUT
    @Path("/{date}")
    public Response put(@PathParam("date") String date, Map<String, String> body) {
        var note = new DailyNote();
        note.setUserId(USER);
        note.setDate(date);
        note.setContent(body.get("content"));
        note.setUpdatedAt(Instant.now().toString());
        table.putItem(note);
        return Response.ok(noteToMap(note)).build();
    }

    @GET
    public Response list(@QueryParam("from") String from, @QueryParam("to") String to) {
        if (from == null || to == null)
            return Response.status(400).entity(Map.of("error", "from and to are required")).build();
        var notes = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream()
                .filter(n -> n.getDate().compareTo(from) >= 0 && n.getDate().compareTo(to) <= 0)
                .sorted(Comparator.comparing(DailyNote::getDate).reversed())
                .map(this::noteToMap).toList();
        return Response.ok(Map.of("notes", notes)).build();
    }

    @GET
    @Path("/search")
    public Response search(@QueryParam("q") String q) {
        if (q == null || q.isBlank())
            return Response.status(400).entity(Map.of("error", "q is required")).build();
        String lower = q.toLowerCase();
        var notes = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream()
                .filter(n -> {
                    String text = extractPlainText(n.getContent());
                    return text.toLowerCase().contains(lower);
                })
                .sorted(Comparator.comparing(DailyNote::getDate).reversed())
                .map(n -> {
                    String text = extractPlainText(n.getContent());
                    String snippet = text.length() > 200 ? text.substring(0, 200) + "..." : text;
                    return Map.of("date", n.getDate(), "snippet", snippet, "updatedAt", n.getUpdatedAt());
                })
                .toList();
        return Response.ok(Map.of("notes", notes)).build();
    }

    private Map<String, String> noteToMap(DailyNote n) {
        var m = new LinkedHashMap<String, String>();
        m.put("date", n.getDate());
        m.put("content", n.getContent());
        m.put("updatedAt", n.getUpdatedAt());
        return m;
    }

    private String extractPlainText(String content) {
        if (content == null || content.isBlank()) return "";
        try {
            JsonNode root = MAPPER.readTree(content);
            StringBuilder sb = new StringBuilder();
            collectText(root, sb);
            return sb.toString().trim();
        } catch (Exception e) {
            return content;
        }
    }

    private void collectText(JsonNode node, StringBuilder sb) {
        if (node.has("text")) sb.append(node.get("text").asText()).append(" ");
        if (node.has("content")) {
            for (JsonNode child : node.get("content")) collectText(child, sb);
        }
    }
}
