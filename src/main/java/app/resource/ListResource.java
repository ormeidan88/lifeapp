package app.resource;

import app.model.ListEntity;
import app.model.ListItem;
import app.model.Task;
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

@Path("/api/lists")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ListResource {

    private static final String USER = "default";

    @Inject @Named("lists") DynamoDbTable<ListEntity> table;
    @Inject @Named("listItems") DynamoDbTable<ListItem> itemsTable;
    @Inject @Named("tasks") DynamoDbTable<Task> tasksTable;

    private void ensureSystemLists() {
        var existing = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream().map(ListEntity::getName).toList();
        String now = Instant.now().toString();
        if (!existing.contains("Today")) createSystemList("Today", now);
        if (!existing.contains("Someday")) createSystemList("Someday", now);
    }

    private void createSystemList(String name, String now) {
        var list = new ListEntity();
        list.setUserId(USER);
        list.setListId(UlidCreator.getUlid().toString());
        list.setName(name);
        list.setType("SYSTEM");
        list.setCreatedAt(now);
        table.putItem(list);
    }

    @GET
    public Response list() {
        ensureSystemLists();
        var lists = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream().map(l -> {
                    long count = itemsTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(l.getListId()).build()))
                            .items().stream().count();
                    return Map.of("id", l.getListId(), "name", l.getName(), "type", l.getType(), "itemCount", count, "createdAt", l.getCreatedAt());
                }).toList();
        return Response.ok(Map.of("lists", lists)).build();
    }

    @POST
    public Response create(Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) return Response.status(400).entity(Map.of("error", "name is required")).build();
        var list = new ListEntity();
        list.setUserId(USER);
        list.setListId(UlidCreator.getUlid().toString());
        list.setName(name);
        list.setType(body.getOrDefault("type", "CUSTOM"));
        list.setCreatedAt(Instant.now().toString());
        table.putItem(list);
        return Response.status(201).entity(Map.of("id", list.getListId(), "name", list.getName(), "type", list.getType(), "createdAt", list.getCreatedAt())).build();
    }

    @POST @Path("/{listId}/items")
    public Response addItem(@PathParam("listId") String listId, Map<String, String> body) {
        String taskId = body.get("taskId");
        if (taskId == null || taskId.isBlank()) return Response.status(400).entity(Map.of("error", "taskId is required")).build();
        var item = new ListItem();
        item.setListId(listId);
        item.setTaskId(taskId);
        itemsTable.putItem(item);
        return Response.status(201).entity(Map.of("listId", listId, "taskId", item.getTaskId())).build();
    }

    @GET @Path("/{listId}/items")
    public Response listItems(@PathParam("listId") String listId) {
        var items = itemsTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(listId).build()))
                .items().stream().map(i -> {
                    // Join: find the task across all projects
                    var taskObj = tasksTable.scan().items().stream()
                            .filter(t -> t.getTaskId().equals(i.getTaskId()))
                            .findFirst().orElse(null);
                    var m = new HashMap<String, Object>();
                    m.put("taskId", i.getTaskId());
                    if (taskObj != null) {
                        m.put("task", Map.of(
                                "id", taskObj.getTaskId(), "projectId", taskObj.getProjectId(),
                                "title", taskObj.getTitle(), "done", taskObj.getDone(),
                                "position", taskObj.getPosition() != null ? taskObj.getPosition() : 0,
                                "createdAt", taskObj.getCreatedAt()
                        ));
                    }
                    return m;
                }).toList();
        return Response.ok(Map.of("items", items)).build();
    }

    @DELETE @Path("/{listId}/items/{taskId}")
    public Response removeItem(@PathParam("listId") String listId, @PathParam("taskId") String taskId) {
        itemsTable.deleteItem(Key.builder().partitionValue(listId).sortValue(taskId).build());
        return Response.noContent().build();
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        var list = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (list != null && "SYSTEM".equals(list.getType())) {
            return Response.status(403).entity(Map.of("error", "Cannot delete system lists")).build();
        }
        // Delete all items in the list
        itemsTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(id).build()))
                .items().forEach(i -> itemsTable.deleteItem(Key.builder().partitionValue(id).sortValue(i.getTaskId()).build()));
        table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return Response.noContent().build();
    }
}
