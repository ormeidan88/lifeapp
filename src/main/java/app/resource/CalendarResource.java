package app.resource;

import app.model.CalendarEvent;
import app.service.RecurrenceExpander;
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
import java.util.stream.Collectors;

@Path("/api/calendar/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CalendarResource {

    private static final String USER = "default";
    private static final String DELETED = "__DELETED__";

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
        event.setTitle(title);
        event.setDate(date);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setColor(body.get("color"));
        event.setNotes(body.get("notes"));
        event.setRecurrenceRule(body.get("recurrenceRule"));
        event.setSource(body.getOrDefault("source", "manual"));
        event.setCreatedAt(Instant.now().toString());
        table.putItem(event);

        return Response.status(201).entity(eventToMap(event, null)).build();
    }

    @GET
    public Response list(@QueryParam("from") String from, @QueryParam("to") String to) {
        if (from == null || to == null)
            return Response.status(400).entity(Map.of("error", "from and to are required")).build();

        var allEvents = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream().toList();

        // Index exceptions by recurrenceId + recurrenceDate
        Map<String, CalendarEvent> exceptions = allEvents.stream()
                .filter(e -> e.getRecurrenceId() != null)
                .collect(Collectors.toMap(
                        e -> e.getRecurrenceId() + "|" + e.getRecurrenceDate(),
                        e -> e, (a, b) -> b));

        List<Map<String, Object>> result = new ArrayList<>();

        for (var e : allEvents) {
            if (e.getRecurrenceId() != null) continue; // skip exceptions, handled via merge

            if (e.getRecurrenceRule() != null) {
                // Recurring series: expand
                var dates = RecurrenceExpander.expand(e.getRecurrenceRule(), e.getDate(), from, to);
                for (String d : dates) {
                    String key = e.getEventId() + "|" + d;
                    var exc = exceptions.get(key);
                    if (exc != null) {
                        if (DELETED.equals(exc.getTitle())) continue; // deleted occurrence
                        result.add(eventToMap(exc, e.getEventId())); // use exception data
                    } else {
                        var m = eventToMap(e, null);
                        m.put("date", d);
                        m.put("isRecurring", true);
                        m.put("seriesId", e.getEventId());
                        result.add(m);
                    }
                }
            } else {
                // One-off event
                if (e.getDate() != null && e.getDate().compareTo(from) >= 0 && e.getDate().compareTo(to) <= 0) {
                    result.add(eventToMap(e, null));
                }
            }
        }

        result.sort(Comparator.comparing((Map<String, Object> m) -> (String) m.get("date"))
                .thenComparing(m -> m.get("startTime") != null ? (String) m.get("startTime") : ""));

        return Response.ok(Map.of("events", result)).build();
    }

    @PATCH @Path("/{id}")
    public Response update(@PathParam("id") String id, Map<String, String> body) {
        String editMode = body.get("editMode");
        String occurrenceDate = body.get("occurrenceDate");

        var event = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (event == null) return Response.status(404).entity(Map.of("error", "Event not found")).build();

        if ("single".equals(editMode) && event.getRecurrenceRule() != null && occurrenceDate != null) {
            // Create exception for this single occurrence
            var exc = new CalendarEvent();
            exc.setUserId(USER);
            exc.setEventId(UlidCreator.getUlid().toString());
            exc.setRecurrenceId(event.getEventId());
            exc.setRecurrenceDate(occurrenceDate);
            exc.setTitle(body.getOrDefault("title", event.getTitle()));
            exc.setDate(body.getOrDefault("date", occurrenceDate));
            exc.setStartTime(body.containsKey("startTime") ? body.get("startTime") : event.getStartTime());
            exc.setEndTime(body.containsKey("endTime") ? body.get("endTime") : event.getEndTime());
            exc.setColor(body.containsKey("color") ? body.get("color") : event.getColor());
            exc.setNotes(body.containsKey("notes") ? body.get("notes") : event.getNotes());
            exc.setSource(event.getSource());
            exc.setCreatedAt(Instant.now().toString());
            table.putItem(exc);
            return Response.ok(eventToMap(exc, event.getEventId())).build();

        } else if ("all".equals(editMode) && event.getRecurrenceRule() != null && occurrenceDate != null) {
            // Update series from this date forward
            event.setDate(occurrenceDate);
            if (body.containsKey("title")) event.setTitle(body.get("title"));
            if (body.containsKey("startTime")) event.setStartTime(body.get("startTime"));
            if (body.containsKey("endTime")) event.setEndTime(body.get("endTime"));
            if (body.containsKey("color")) event.setColor(body.get("color"));
            if (body.containsKey("notes")) event.setNotes(body.get("notes"));
            if (body.containsKey("recurrenceRule")) event.setRecurrenceRule(body.get("recurrenceRule"));
            table.putItem(event);
            // Delete exceptions >= occurrenceDate
            deleteExceptions(event.getEventId(), occurrenceDate);
            return Response.ok(eventToMap(event, null)).build();

        } else {
            // Non-recurring or direct update
            if (body.containsKey("title")) event.setTitle(body.get("title"));
            if (body.containsKey("date")) event.setDate(body.get("date"));
            if (body.containsKey("startTime")) event.setStartTime(body.get("startTime"));
            if (body.containsKey("endTime")) event.setEndTime(body.get("endTime"));
            if (body.containsKey("color")) event.setColor(body.get("color"));
            if (body.containsKey("notes")) event.setNotes(body.get("notes"));
            if (body.containsKey("recurrenceRule")) event.setRecurrenceRule(body.get("recurrenceRule"));
            table.putItem(event);
            return Response.ok(eventToMap(event, null)).build();
        }
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") String id,
                           @QueryParam("deleteMode") String deleteMode,
                           @QueryParam("occurrenceDate") String occurrenceDate) {

        if ("single".equals(deleteMode) && occurrenceDate != null) {
            // Find the series — id is the seriesId for recurring occurrences
            var series = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
            if (series == null || series.getRecurrenceRule() == null)
                return Response.status(404).entity(Map.of("error", "Series not found")).build();
            // Create a deleted exception
            var exc = new CalendarEvent();
            exc.setUserId(USER);
            exc.setEventId(UlidCreator.getUlid().toString());
            exc.setRecurrenceId(id);
            exc.setRecurrenceDate(occurrenceDate);
            exc.setTitle(DELETED);
            exc.setDate(occurrenceDate);
            exc.setSource("manual");
            exc.setCreatedAt(Instant.now().toString());
            table.putItem(exc);
            return Response.noContent().build();

        } else if ("all".equals(deleteMode)) {
            // Delete series + all exceptions
            deleteExceptions(id, null);
            table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
            return Response.noContent().build();

        } else {
            // Non-recurring: simple delete
            table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
            return Response.noContent().build();
        }
    }

    private void deleteExceptions(String seriesId, String fromDate) {
        table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream()
                .filter(e -> seriesId.equals(e.getRecurrenceId()))
                .filter(e -> fromDate == null || (e.getRecurrenceDate() != null && e.getRecurrenceDate().compareTo(fromDate) >= 0))
                .forEach(e -> table.deleteItem(Key.builder().partitionValue(USER).sortValue(e.getEventId()).build()));
    }

    private Map<String, Object> eventToMap(CalendarEvent e, String seriesId) {
        var m = new HashMap<String, Object>();
        m.put("id", e.getEventId());
        m.put("title", e.getTitle());
        m.put("date", e.getDate());
        m.put("startTime", e.getStartTime());
        m.put("endTime", e.getEndTime());
        m.put("source", e.getSource());
        m.put("createdAt", e.getCreatedAt());
        if (e.getColor() != null) m.put("color", e.getColor());
        if (e.getNotes() != null) m.put("notes", e.getNotes());
        if (e.getRecurrenceRule() != null) {
            m.put("recurrenceRule", e.getRecurrenceRule());
            m.put("isRecurring", true);
            m.put("seriesId", e.getEventId());
        }
        if (e.getRecurrenceId() != null) {
            m.put("isRecurring", true);
            m.put("seriesId", seriesId != null ? seriesId : e.getRecurrenceId());
            m.put("recurrenceDate", e.getRecurrenceDate());
        }
        return m;
    }
}
