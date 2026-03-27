package app.resource;

import app.model.Habit;
import app.model.HabitEntry;
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
import java.util.*;

@Path("/api/habits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HabitResource {

    private static final String USER = "default";

    @Inject @Named("habits") DynamoDbTable<Habit> table;
    @Inject @Named("habitEntries") DynamoDbTable<HabitEntry> entriesTable;

    @POST
    public Response create(Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) return Response.status(400).entity(Map.of("error", "name is required")).build();
        var habit = new Habit();
        habit.setUserId(USER);
        habit.setHabitId(UlidCreator.getUlid().toString());
        habit.setName(name);
        habit.setColor(body.getOrDefault("color", "#8B9E7C"));
        habit.setCreatedAt(Instant.now().toString());
        table.putItem(habit);
        return Response.status(201).entity(Map.of("id", habit.getHabitId(), "name", habit.getName(), "color", habit.getColor(), "createdAt", habit.getCreatedAt())).build();
    }

    @GET
    public Response list() {
        var habits = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream().map(h -> {
                    var entries = entriesTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(h.getHabitId()).build()))
                            .items().stream().sorted(Comparator.comparing(HabitEntry::getDate)).toList();
                    int[] streaks = calculateStreaks(entries);
                    var m = new HashMap<String, Object>();
                    m.put("id", h.getHabitId()); m.put("name", h.getName());
                    m.put("color", h.getColor()); m.put("createdAt", h.getCreatedAt());
                    m.put("currentStreak", streaks[0]); m.put("longestStreak", streaks[1]);
                    return m;
                }).toList();
        return Response.ok(Map.of("habits", habits)).build();
    }

    @PATCH @Path("/{id}")
    public Response update(@PathParam("id") String id, Map<String, String> body) {
        var h = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (h == null) return Response.status(404).entity(Map.of("error", "Habit not found")).build();
        if (body.containsKey("name")) h.setName(body.get("name"));
        if (body.containsKey("color")) h.setColor(body.get("color"));
        table.putItem(h);
        return Response.ok(Map.of("id", h.getHabitId(), "name", h.getName(), "color", h.getColor())).build();
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        entriesTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(id).build()))
                .items().forEach(e -> entriesTable.deleteItem(Key.builder().partitionValue(id).sortValue(e.getDate()).build()));
        table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return Response.noContent().build();
    }

    @POST @Path("/{id}/entries")
    public Response createEntry(@PathParam("id") String id, Map<String, String> body) {
        var habit = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (habit == null) return Response.status(404).entity(Map.of("error", "Habit not found")).build();
        String date = body.get("date");
        String value = body.get("value");
        if (date == null || value == null || !Set.of("YES", "NO", "SKIP").contains(value)) {
            return Response.status(400).entity(Map.of("error", "date and value (YES|NO|SKIP) are required")).build();
        }
        var entry = new HabitEntry();
        entry.setHabitId(id);
        entry.setDate(date);
        entry.setValue(value);
        entriesTable.putItem(entry); // upsert
        return Response.status(201).entity(Map.of("habitId", id, "date", entry.getDate(), "value", entry.getValue())).build();
    }

    @GET @Path("/{id}/entries")
    public Response listEntries(@PathParam("id") String id, @QueryParam("from") String from, @QueryParam("to") String to) {
        var entries = entriesTable.query(QueryConditional.sortBetween(
                        Key.builder().partitionValue(id).sortValue(from != null ? from : "0000-01-01").build(),
                        Key.builder().partitionValue(id).sortValue(to != null ? to : "9999-12-31").build()))
                .items().stream().map(e -> Map.of("date", e.getDate(), "value", e.getValue())).toList();
        return Response.ok(Map.of("entries", entries)).build();
    }

    // Streak: consecutive YES days. SKIP is neutral (skipped over). NO breaks.
    private int[] calculateStreaks(List<HabitEntry> entries) {
        int longest = 0, current = 0;
        LocalDate today = LocalDate.now();
        LocalDate lastYes = null;

        for (var e : entries) {
            if ("YES".equals(e.getValue())) {
                LocalDate d = LocalDate.parse(e.getDate());
                if (lastYes == null || isConsecutiveSkippingSkips(lastYes, d, entries)) {
                    current++;
                } else {
                    current = 1;
                }
                lastYes = d;
                longest = Math.max(longest, current);
            } else if ("NO".equals(e.getValue())) {
                current = 0;
                lastYes = null;
            }
            // SKIP: do nothing
        }
        return new int[]{current, longest};
    }

    private boolean isConsecutiveSkippingSkips(LocalDate prev, LocalDate curr, List<HabitEntry> entries) {
        LocalDate d = prev.plusDays(1);
        while (d.isBefore(curr)) {
            String ds = d.toString();
            var entry = entries.stream().filter(e -> e.getDate().equals(ds)).findFirst();
            if (entry.isPresent() && "NO".equals(entry.get().getValue())) return false;
            // SKIP or missing = neutral
            d = d.plusDays(1);
        }
        return true;
    }
}
