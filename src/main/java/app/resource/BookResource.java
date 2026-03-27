package app.resource;

import app.model.Book;
import app.model.Page;
import app.model.PageContent;
import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.Optional;

@Path("/api/books")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookResource {

    private static final String USER = "default";

    @Inject @Named("books") DynamoDbTable<Book> table;
    @Inject @Named("pages") DynamoDbTable<Page> pagesTable;
    @Inject @Named("pageContent") DynamoDbTable<PageContent> pageContentTable;

    @ConfigProperty(name = "app.books.google-api-key")
    Optional<String> googleApiKey;

    @GET @Path("/search")
    public Response search(@QueryParam("q") String query) {
        try {
            var client = HttpClient.newHttpClient();
            var req = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/books/v1/volumes?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&maxResults=10" + (googleApiKey.isEmpty() || googleApiKey.get().isEmpty() ? "" : "&key=" + googleApiKey.get())))
                    .GET().build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return Response.ok(Map.of("results", parseGoogleBooksResponse(resp.body()))).build();
        } catch (Exception e) {
            return Response.status(502).entity(Map.of("error", "Book search temporarily unavailable")).build();
        }
    }

    List<Map<String, Object>> parseGoogleBooksResponse(String json) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(json);
        var results = new java.util.ArrayList<Map<String, Object>>();
        var items = root.get("items");
        if (items != null && items.isArray()) {
            for (var item : items) {
                var info = item.get("volumeInfo");
                if (info == null) continue;
                var r = new java.util.HashMap<String, Object>();
                r.put("googleBooksId", item.get("id").asText());
                r.put("title", info.has("title") ? info.get("title").asText() : "Unknown");
                var authors = new java.util.ArrayList<String>();
                if (info.has("authors")) for (var a : info.get("authors")) authors.add(a.asText());
                r.put("authors", authors);
                // Cover URL: prefer larger image, force https, fallback to Open Library
                String coverUrl = "";
                if (info.has("imageLinks")) {
                    var imgLinks = info.get("imageLinks");
                    for (String key : new String[]{"large", "medium", "small", "thumbnail", "smallThumbnail"}) {
                        if (imgLinks.has(key)) {
                            coverUrl = imgLinks.get(key).asText()
                                    .replace("http://", "https://")
                                    .replace("&edge=curl", "");
                            break;
                        }
                    }
                }
                if (coverUrl.isEmpty() && info.has("industryIdentifiers")) {
                    for (var id : info.get("industryIdentifiers")) {
                        if ("ISBN_13".equals(id.get("type").asText())) {
                            coverUrl = "https://covers.openlibrary.org/b/isbn/" + id.get("identifier").asText() + "-L.jpg";
                            break;
                        }
                    }
                }
                r.put("coverUrl", coverUrl);
                r.put("description", info.has("description") ? info.get("description").asText() : "");
                r.put("pageCount", info.has("pageCount") ? info.get("pageCount").asInt() : 0);
                results.add(r);
            }
        }
        return results;
    }

    @POST
    public Response create(Map<String, Object> body) {
        String googleBooksId = (String) body.get("googleBooksId");
        // Check duplicate
        var existing = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream().filter(b -> googleBooksId.equals(b.getGoogleBooksId())).findFirst();
        if (existing.isPresent()) {
            return Response.status(409).entity(Map.of("error", "Book already exists", "bookId", existing.get().getBookId())).build();
        }

        String now = Instant.now().toString();
        String bookId = UlidCreator.getUlid().toString();
        String pageId = UlidCreator.getUlid().toString();

        var page = new Page();
        page.setUserId(USER); page.setPageId(pageId);
        page.setTitle((String) body.get("title")); page.setOwnerType("book");
        page.setOwnerId(bookId); page.setCreatedAt(now); page.setUpdatedAt(now);
        pagesTable.putItem(page);

        var book = new Book();
        book.setUserId(USER); book.setBookId(bookId);
        book.setGoogleBooksId(googleBooksId);
        book.setTitle((String) body.get("title"));
        @SuppressWarnings("unchecked")
        List<String> authors = (List<String>) body.get("authors");
        book.setAuthors(authors);
        book.setCoverUrl((String) body.get("coverUrl"));
        book.setPageId(pageId); book.setCreatedAt(now);
        table.putItem(book);

        return Response.status(201).entity(Map.of("id", bookId, "googleBooksId", googleBooksId, "title", book.getTitle(), "authors", book.getAuthors(), "coverUrl", book.getCoverUrl(), "pageId", pageId, "createdAt", now)).build();
    }

    @GET
    public Response list() {
        var books = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream().map(b -> Map.of("id", (Object) b.getBookId(), "title", b.getTitle(), "authors", b.getAuthors(), "coverUrl", b.getCoverUrl(), "pageId", b.getPageId(), "createdAt", b.getCreatedAt())).toList();
        return Response.ok(Map.of("books", books)).build();
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        var book = table.getItem(Key.builder().partitionValue(USER).sortValue(id).build());
        if (book != null && book.getPageId() != null) {
            deletePageRecursive(book.getPageId());
        }
        table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return Response.noContent().build();
    }

    private void deletePageRecursive(String pageId) {
        pageContentTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(pageId).build()))
                .items().forEach(c -> pageContentTable.deleteItem(Key.builder().partitionValue(pageId).sortValue(c.getVersion()).build()));
        pagesTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream().filter(p -> pageId.equals(p.getParentPageId()))
                .forEach(child -> deletePageRecursive(child.getPageId()));
        pagesTable.deleteItem(Key.builder().partitionValue(USER).sortValue(pageId).build());
    }
}
