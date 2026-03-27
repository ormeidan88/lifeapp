package app.resource;

import app.model.*;
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

@Path("/api/inbox")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InboxResource {

    private static final String USER = "default";

    @Inject @Named("inboxItems") DynamoDbTable<InboxItem> table;
    @Inject @Named("tasks") DynamoDbTable<Task> tasksTable;
    @Inject @Named("projects") DynamoDbTable<Project> projectsTable;
    @Inject @Named("pages") DynamoDbTable<Page> pagesTable;
    @Inject @Named("listItems") DynamoDbTable<ListItem> listItemsTable;

    @POST
    public Response create(Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Text is required")).build();
        }
        var item = new InboxItem();
        item.setUserId(USER);
        item.setItemId(UlidCreator.getUlid().toString());
        item.setText(text.trim());
        item.setCreatedAt(Instant.now().toString());
        table.putItem(item);
        return Response.status(201).entity(Map.of("id", item.getItemId(), "text", item.getText(), "createdAt", item.getCreatedAt())).build();
    }

    @GET
    public Response list() {
        var items = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream()
                .sorted(Comparator.comparing(InboxItem::getCreatedAt).reversed())
                .map(i -> Map.of("id", i.getItemId(), "text", i.getText(), "createdAt", i.getCreatedAt()))
                .toList();
        return Response.ok(Map.of("items", items)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/convert")
    public Response convert(@PathParam("id") String id, Map<String, String> body) {
        var item = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (item == null) return Response.status(404).entity(Map.of("error", "Inbox item not found")).build();

        String targetType = body.getOrDefault("targetType", "task");
        String createdId;
        String now = Instant.now().toString();

        switch (targetType) {
            case "task" -> {
                String projectId = body.get("projectId");
                if (projectId == null) return Response.status(400).entity(Map.of("error", "projectId required for task conversion")).build();
                var project = projectsTable.getItem(Key.builder().partitionValue(USER).sortValue(projectId).build());
                if (project == null) return Response.status(404).entity(Map.of("error", "Project not found")).build();
                long count = tasksTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(projectId).build()))
                        .items().stream().count();
                var task = new Task();
                task.setProjectId(projectId);
                task.setTaskId(UlidCreator.getUlid().toString());
                task.setTitle(item.getText());
                task.setDone(false);
                task.setPosition((int) count);
                task.setCreatedAt(now);
                tasksTable.putItem(task);
                createdId = task.getTaskId();
            }
            case "project" -> {
                String name = body.getOrDefault("name", item.getText());
                String pageId = UlidCreator.getUlid().toString();
                String projectId = UlidCreator.getUlid().toString();
                var page = new Page();
                page.setUserId(USER); page.setPageId(pageId); page.setTitle(name);
                page.setOwnerType("project"); page.setOwnerId(projectId);
                page.setCreatedAt(now); page.setUpdatedAt(now);
                pagesTable.putItem(page);
                var project = new Project();
                project.setUserId(USER); project.setProjectId(projectId); project.setName(name);
                project.setStatus("NOT_STARTED"); project.setPageId(pageId); project.setCreatedAt(now);
                projectsTable.putItem(project);
                createdId = projectId;
            }
            case "list-item" -> {
                String listId = body.get("listId");
                String projectId = body.get("projectId");
                if (listId == null || projectId == null) return Response.status(400).entity(Map.of("error", "listId and projectId required for list-item conversion")).build();
                var project = projectsTable.getItem(Key.builder().partitionValue(USER).sortValue(projectId).build());
                if (project == null) return Response.status(404).entity(Map.of("error", "Project not found")).build();
                long count = tasksTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(projectId).build()))
                        .items().stream().count();
                var task = new Task();
                task.setProjectId(projectId);
                task.setTaskId(UlidCreator.getUlid().toString());
                task.setTitle(item.getText());
                task.setDone(false);
                task.setPosition((int) count);
                task.setCreatedAt(now);
                tasksTable.putItem(task);
                var li = new ListItem();
                li.setListId(listId); li.setTaskId(task.getTaskId());
                listItemsTable.putItem(li);
                createdId = task.getTaskId();
            }
            default -> {
                return Response.status(400).entity(Map.of("error", "Invalid targetType. Use task, project, or list-item")).build();
            }
        }

        table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return Response.status(201).entity(Map.of("id", createdId, "type", targetType, "inboxItemDeleted", true)).build();
    }
}
