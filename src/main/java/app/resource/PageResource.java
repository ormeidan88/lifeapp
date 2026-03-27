package app.resource;

import app.model.Page;
import app.model.PageContent;
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
import java.util.*;
import java.util.List;

@Path("/api/pages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PageResource {

    private static final String USER = "default";

    @Inject @Named("pages") DynamoDbTable<Page> table;
    @Inject @Named("pageContent") DynamoDbTable<PageContent> contentTable;
    @Inject ObjectMapper mapper;

    @GET @Path("/search")
    public Response search(@QueryParam("q") String q) {
        if (q == null || q.isBlank()) return Response.ok(Map.of("pages", List.of())).build();
        String lower = q.toLowerCase();
        var results = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream()
                .filter(p -> p.getTitle() != null && p.getTitle().toLowerCase().contains(lower))
                .map(p -> Map.of("id", p.getPageId(), "title", p.getTitle()))
                .toList();
        return Response.ok(Map.of("pages", results)).build();
    }

    @POST
    public Response create(Map<String, String> body) {
        String now = Instant.now().toString();
        var page = new Page();
        page.setUserId(USER);
        page.setPageId(UlidCreator.getUlid().toString());
        page.setTitle(body.getOrDefault("title", "Untitled"));
        page.setParentPageId(body.get("parentPageId"));
        page.setOwnerType(body.getOrDefault("ownerType", "standalone"));
        page.setOwnerId(body.get("ownerId"));
        page.setCreatedAt(now);
        page.setUpdatedAt(now);
        table.putItem(page);
        return Response.status(201).entity(Map.of(
                "id", page.getPageId(), "title", page.getTitle(),
                "ownerType", page.getOwnerType(), "createdAt", page.getCreatedAt()
        )).build();
    }

    @GET
    public Response list(@QueryParam("ownerType") @DefaultValue("standalone") String ownerType,
                         @QueryParam("ownerId") String ownerId,
                         @QueryParam("parentPageId") String parentPageId) {
        var all = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream();

        if (parentPageId != null) {
            all = all.filter(p -> parentPageId.equals(p.getParentPageId()));
        } else {
            all = all.filter(p -> ownerType.equals(p.getOwnerType()))
                     .filter(p -> p.getParentPageId() == null);
            if (ownerId != null) all = all.filter(p -> ownerId.equals(p.getOwnerId()));
        }

        var pages = all.map(p -> {
            var m = new HashMap<String, Object>();
            m.put("id", p.getPageId()); m.put("title", p.getTitle());
            m.put("ownerType", p.getOwnerType()); m.put("createdAt", p.getCreatedAt());
            m.put("updatedAt", p.getUpdatedAt());
            if (p.getOwnerId() != null) m.put("ownerId", p.getOwnerId());
            if (p.getParentPageId() != null) m.put("parentPageId", p.getParentPageId());
            return m;
        }).toList();
        return Response.ok(Map.of("pages", pages)).build();
    }

    @GET @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        var page = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (page == null) return Response.status(404).entity(Map.of("error", "Page not found")).build();

        // Get latest content
        var contents = contentTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(id).build()))
                .items().stream().toList();
        Object content = null;
        if (!contents.isEmpty()) {
            try { content = mapper.readTree(contents.getLast().getContent()); }
            catch (Exception e) { content = contents.getLast().getContent(); }
        }

        var result = new HashMap<String, Object>();
        result.put("id", page.getPageId()); result.put("title", page.getTitle());
        result.put("ownerType", page.getOwnerType()); result.put("createdAt", page.getCreatedAt());
        result.put("updatedAt", page.getUpdatedAt()); result.put("content", content);
        if (page.getOwnerId() != null) result.put("ownerId", page.getOwnerId());
        if (page.getParentPageId() != null) result.put("parentPageId", page.getParentPageId());
        return Response.ok(result).build();
    }

    @PATCH @Path("/{id}")
    public Response update(@PathParam("id") String id, JsonNode body) {
        var page = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (page == null) return Response.status(404).entity(Map.of("error", "Page not found")).build();

        String now = Instant.now().toString();
        if (body.has("title")) page.setTitle(body.get("title").asText());
        page.setUpdatedAt(now);
        table.putItem(page);

        if (body.has("content")) {
            String contentJson = body.get("content").toString();
            if (contentJson.length() > 400_000) {
                return Response.status(413).entity(Map.of("error", "Page content too large")).build();
            }
            // Delete old versions, keep only latest
            contentTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(id).build()))
                    .items().forEach(c -> contentTable.deleteItem(Key.builder().partitionValue(id).sortValue(c.getVersion()).build()));
            var pc = new PageContent();
            pc.setPageId(id);
            pc.setVersion(now);
            pc.setContent(contentJson);
            contentTable.putItem(pc);
        }

        return Response.ok(Map.of("id", page.getPageId(), "title", page.getTitle(), "updatedAt", now)).build();
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        // Delete content
        contentTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(id).build()))
                .items().forEach(c -> contentTable.deleteItem(Key.builder().partitionValue(id).sortValue(c.getVersion()).build()));
        // Delete child pages recursively
        table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream().filter(p -> id.equals(p.getParentPageId()))
                .forEach(child -> delete(child.getPageId()));
        // Delete page
        table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return Response.noContent().build();
    }
}
