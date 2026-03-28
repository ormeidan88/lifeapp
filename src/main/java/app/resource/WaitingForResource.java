package app.resource;

import app.model.WaitingForItem;
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

@Path("/api/waiting-for")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WaitingForResource {

    private static final String USER = "default";

    @Inject @Named("waitingForItems") DynamoDbTable<WaitingForItem> table;

    @GET
    public Response list() {
        String today = LocalDate.now().toString();
        var items = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream()
                .sorted(Comparator.comparing(WaitingForItem::getDueDate))
                .map(i -> {
                    boolean overdue = i.getDueDate() != null && i.getDueDate().compareTo(today) <= 0
                            && !Boolean.TRUE.equals(i.getAcknowledged());
                    var m = new HashMap<String, Object>();
                    m.put("id", i.getItemId()); m.put("description", i.getDescription());
                    m.put("waitingFor", i.getWaitingFor()); m.put("dueDate", i.getDueDate());
                    m.put("acknowledged", Boolean.TRUE.equals(i.getAcknowledged()));
                    m.put("overdue", overdue); m.put("createdAt", i.getCreatedAt());
                    return m;
                }).toList();
        return Response.ok(Map.of("items", items)).build();
    }

    @POST
    public Response create(Map<String, String> body) {
        String description = body.get("description");
        String waitingFor = body.get("waitingFor");
        String dueDate = body.get("dueDate");
        if (description == null || description.isBlank() || waitingFor == null || waitingFor.isBlank() || dueDate == null || dueDate.isBlank()) {
            return Response.status(400).entity(Map.of("error", "description, waitingFor, and dueDate are required")).build();
        }
        var item = new WaitingForItem();
        item.setUserId(USER); item.setItemId(UlidCreator.getUlid().toString());
        item.setDescription(description.trim()); item.setWaitingFor(waitingFor.trim());
        item.setDueDate(dueDate); item.setAcknowledged(false);
        item.setCreatedAt(Instant.now().toString());
        table.putItem(item);
        return Response.status(201).entity(Map.of("id", item.getItemId(), "description", item.getDescription(),
                "waitingFor", item.getWaitingFor(), "dueDate", item.getDueDate(), "acknowledged", false, "createdAt", item.getCreatedAt())).build();
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return Response.noContent().build();
    }

    @POST @Path("/{id}/acknowledge")
    public Response acknowledge(@PathParam("id") String id) {
        var item = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (item == null) return Response.status(404).entity(Map.of("error", "Item not found")).build();
        item.setAcknowledged(true);
        table.putItem(item);
        return Response.ok(Map.of("id", item.getItemId(), "acknowledged", true)).build();
    }

    @GET @Path("/overdue-count")
    public Response overdueCount() {
        String today = LocalDate.now().toString();
        long count = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream()
                .filter(i -> i.getDueDate() != null && i.getDueDate().compareTo(today) <= 0
                        && !Boolean.TRUE.equals(i.getAcknowledged()))
                .count();
        return Response.ok(Map.of("count", count)).build();
    }
}
