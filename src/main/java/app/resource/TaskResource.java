package app.resource;

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

@Path("/api/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskResource {

    @Inject @Named("tasks") DynamoDbTable<Task> table;

    @POST
    public Response create(Map<String, Object> body) {
        String projectId = (String) body.get("projectId");
        String title = (String) body.get("title");
        if (projectId == null || title == null || title.isBlank())
            return Response.status(400).entity(Map.of("error", "projectId and title are required")).build();

        // Count existing tasks to set position
        long count = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(projectId).build()))
                .items().stream().count();

        var task = new Task();
        task.setProjectId(projectId);
        task.setTaskId(UlidCreator.getUlid().toString());
        task.setTitle(title);
        task.setDone(false);
        task.setParentTaskId((String) body.get("parentTaskId"));
        task.setPosition((int) count);
        task.setCreatedAt(Instant.now().toString());
        table.putItem(task);

        var result = new HashMap<String, Object>();
        result.put("id", task.getTaskId()); result.put("projectId", task.getProjectId());
        result.put("title", task.getTitle()); result.put("done", task.getDone());
        result.put("parentTaskId", task.getParentTaskId()); result.put("position", task.getPosition());
        result.put("createdAt", task.getCreatedAt());
        return Response.status(201).entity(result).build();
    }

    @GET
    public Response list(@QueryParam("projectId") String projectId) {
        if (projectId == null) return Response.status(400).entity(Map.of("error", "projectId is required")).build();
        var tasks = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(projectId).build()))
                .items().stream()
                .sorted(Comparator.comparingInt(t -> t.getPosition() != null ? t.getPosition() : 0))
                .map(t -> {
                    var m = new HashMap<String, Object>();
                    m.put("id", t.getTaskId()); m.put("projectId", t.getProjectId());
                    m.put("title", t.getTitle()); m.put("done", t.getDone());
                    m.put("parentTaskId", t.getParentTaskId()); m.put("position", t.getPosition());
                    m.put("createdAt", t.getCreatedAt());
                    return m;
                }).toList();
        return Response.ok(Map.of("tasks", tasks)).build();
    }

    @PATCH @Path("/{id}")
    public Response update(@PathParam("id") String id, @QueryParam("projectId") String projectId, Map<String, Object> body) {
        // Need projectId to look up — try from body or query
        String pid = projectId != null ? projectId : (String) body.get("projectId");
        if (pid == null) return Response.status(400).entity(Map.of("error", "projectId required")).build();
        var task = table.getItem(Key.builder().partitionValue(pid).sortValue(id).build());
        if (task == null) return Response.status(404).entity(Map.of("error", "Task not found")).build();
        if (body.containsKey("title")) task.setTitle((String) body.get("title"));
        if (body.containsKey("done")) task.setDone((Boolean) body.get("done"));
        if (body.containsKey("position")) task.setPosition((Integer) body.get("position"));
        table.putItem(task);
        return Response.ok(Map.of("id", task.getTaskId(), "projectId", task.getProjectId(), "title", task.getTitle(), "done", task.getDone(), "position", task.getPosition(), "createdAt", task.getCreatedAt())).build();
    }

    @POST @Path("/reorder")
    public Response reorder(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> taskIds = (List<String>) body.get("taskIds");
        String projectId = (String) body.get("projectId");
        if (taskIds == null || projectId == null) return Response.status(400).entity(Map.of("error", "taskIds and projectId required")).build();
        var updated = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < taskIds.size(); i++) {
            var task = table.getItem(Key.builder().partitionValue(projectId).sortValue(taskIds.get(i)).build());
            if (task != null) {
                task.setPosition(i);
                table.putItem(task);
                updated.add(Map.of("id", task.getTaskId(), "position", i));
            }
        }
        return Response.ok(Map.of("tasks", updated)).build();
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") String id, @QueryParam("projectId") String projectId) {
        if (projectId == null) return Response.status(400).entity(Map.of("error", "projectId required")).build();
        // Cascade: delete subtasks
        table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(projectId).build()))
                .items().stream().filter(t -> id.equals(t.getParentTaskId()))
                .forEach(sub -> delete(sub.getTaskId(), projectId));
        table.deleteItem(Key.builder().partitionValue(projectId).sortValue(id).build());
        return Response.noContent().build();
    }
}
