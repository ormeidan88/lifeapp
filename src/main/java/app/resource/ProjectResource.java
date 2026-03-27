package app.resource;

import app.model.Project;
import app.model.Page;
import app.model.Task;
import app.model.ListItem;
import app.model.PageContent;
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

@Path("/api/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {

    private static final String USER = "default";

    @Inject @Named("projects") DynamoDbTable<Project> table;
    @Inject @Named("pages") DynamoDbTable<Page> pagesTable;
    @Inject @Named("tasks") DynamoDbTable<Task> tasksTable;
    @Inject @Named("listItems") DynamoDbTable<ListItem> listItemsTable;
    @Inject @Named("pageContent") DynamoDbTable<PageContent> pageContentTable;

    @POST
    public Response create(Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) return Response.status(400).entity(Map.of("error", "Name is required")).build();

        String now = Instant.now().toString();
        String pageId = UlidCreator.getUlid().toString();

        // Create project page
        var page = new Page();
        page.setUserId(USER);
        page.setPageId(pageId);
        page.setTitle(name);
        page.setOwnerType("project");
        page.setOwnerId(UlidCreator.getUlid().toString());
        page.setCreatedAt(now);
        page.setUpdatedAt(now);

        var project = new Project();
        project.setUserId(USER);
        project.setProjectId(UlidCreator.getUlid().toString());
        project.setName(name);
        project.setDescription(body.get("description"));
        project.setStatus("NOT_STARTED");
        project.setPageId(pageId);
        project.setCreatedAt(now);

        page.setOwnerId(project.getProjectId());
        pagesTable.putItem(page);
        table.putItem(project);

        return Response.status(201).entity(Map.of(
                "id", project.getProjectId(), "name", project.getName(),
                "status", project.getStatus(), "pageId", project.getPageId(), "createdAt", project.getCreatedAt()
        )).build();
    }

    @GET
    public Response list(@QueryParam("status") String status) {
        var items = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream()
                .filter(p -> status == null || p.getStatus().equals(status))
                .map(p -> Map.of("id", p.getProjectId(), "name", p.getName(), "status", p.getStatus(), "pageId", p.getPageId(), "createdAt", p.getCreatedAt()))
                .toList();
        return Response.ok(Map.of("projects", items)).build();
    }

    @GET @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        var p = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (p == null) return Response.status(404).entity(Map.of("error", "Project not found")).build();
        var result = new HashMap<String, Object>();
        result.put("id", p.getProjectId()); result.put("name", p.getName());
        result.put("status", p.getStatus()); result.put("pageId", p.getPageId());
        result.put("createdAt", p.getCreatedAt());
        if (p.getDescription() != null) result.put("description", p.getDescription());
        return Response.ok(result).build();
    }

    @PATCH @Path("/{id}")
    public Response update(@PathParam("id") String id, Map<String, String> body) {
        var p = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (p == null) return Response.status(404).entity(Map.of("error", "Project not found")).build();
        if (body.containsKey("name")) p.setName(body.get("name"));
        if (body.containsKey("status")) p.setStatus(body.get("status"));
        table.putItem(p);
        return Response.ok(Map.of("id", p.getProjectId(), "name", p.getName(), "status", p.getStatus(), "pageId", p.getPageId(), "createdAt", p.getCreatedAt())).build();
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        var project = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (project == null) return Response.noContent().build();

        // Cascade: delete tasks and their list_items
        var tasks = tasksTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(id).build()))
                .items().stream().toList();
        for (var task : tasks) {
            // Remove from any lists
            listItemsTable.scan().items().stream()
                    .filter(li -> task.getTaskId().equals(li.getTaskId()))
                    .forEach(li -> listItemsTable.deleteItem(Key.builder().partitionValue(li.getListId()).sortValue(li.getTaskId()).build()));
            tasksTable.deleteItem(Key.builder().partitionValue(id).sortValue(task.getTaskId()).build());
        }

        // Cascade: delete project page and child pages
        if (project.getPageId() != null) {
            deletePageRecursive(project.getPageId());
        }

        table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return Response.noContent().build();
    }

    private void deletePageRecursive(String pageId) {
        // Delete content
        pageContentTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(pageId).build()))
                .items().forEach(c -> pageContentTable.deleteItem(Key.builder().partitionValue(pageId).sortValue(c.getVersion()).build()));
        // Delete children
        pagesTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream().filter(p -> pageId.equals(p.getParentPageId()))
                .forEach(child -> deletePageRecursive(child.getPageId()));
        pagesTable.deleteItem(Key.builder().partitionValue(USER).sortValue(pageId).build());
    }
}
