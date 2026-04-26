package app.resource;

import app.model.CalendarEvent;
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
import java.util.*;

@Path("/api/calendar/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CalendarResource {

    private static final String USER = "default";

    @Inject @Named("calendarEvents") DynamoDbTable<CalendarEvent> table;

    @POST
    public Response create(Map<String, String> body) {
        String title = body.get("title");
        String date = body.get("date");
        String startTime = body.get("startTime");
        String endTime = body.get("endTime");
        if (title == null || title.isBlank() || date == null) {
            return Response.status(400).entity(Map.of("error", "title and date are required")).build();
        }
        if (startTime != null && endTime != null && endTime.compareTo(startTime) <= 0) {
            return Response.status(400).entity(Map.of("error", "End time must be after start time")).build();
        }

        var event = new CalendarEvent();
        event.setUserId(USER);
        event.setEventId(UlidCreator.getUlid().toString());
        event.setTitle(body.get("title"));
        event.setDate(body.get("date"));
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setColor(body.get("color"));
        event.setNotes(body.get("notes"));
        event.setSource(body.getOrDefault("source", "manual"));
        event.setCreatedAt(Instant.now().toString());
        table.putItem(event);

        return Response.status(201).entity(eventToMap(event)).build();
    }

    @GET
    public Response list(@QueryParam("from") String from, @QueryParam("to") String to) {
        if (from == null || to == null) return Response.status(400).entity(Map.of("error", "from and to are required")).build();
        var events = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream()
                .filter(e -> e.getDate() != null && e.getDate().compareTo(from) >= 0 && e.getDate().compareTo(to) <= 0)
                .sorted(Comparator.comparing(CalendarEvent::getDate).thenComparing(e -> e.getStartTime() != null ? e.getStartTime() : ""))
                .map(this::eventToMap).toList();
        return Response.ok(Map.of("events", events)).build();
    }

    @PATCH @Path("/{id}")
    public Response update(@PathParam("id") String id, Map<String, String> body) {
        var event = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (event == null) return Response.status(404).entity(Map.of("error", "Event not found")).build();
        if (body.containsKey("title")) event.setTitle(body.get("title"));
        if (body.containsKey("date")) event.setDate(body.get("date"));
        if (body.containsKey("startTime")) event.setStartTime(body.get("startTime"));
        if (body.containsKey("endTime")) event.setEndTime(body.get("endTime"));
        if (body.containsKey("color")) event.setColor(body.get("color"));
        if (body.containsKey("notes")) event.setNotes(body.get("notes"));
        table.putItem(event);
        return Response.ok(eventToMap(event)).build();
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return Response.noContent().build();
    }

    private Map<String, Object> eventToMap(CalendarEvent e) {
        var m = new HashMap<String, Object>();
        m.put("id", e.getEventId()); m.put("title", e.getTitle());
        m.put("date", e.getDate()); m.put("startTime", e.getStartTime());
        m.put("endTime", e.getEndTime()); m.put("source", e.getSource());
        m.put("createdAt", e.getCreatedAt());
        if (e.getColor() != null) m.put("color", e.getColor());
        if (e.getNotes() != null) m.put("notes", e.getNotes());
        return m;
    }
}
