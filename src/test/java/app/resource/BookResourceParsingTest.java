package app.resource;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BookResourceParsingTest {

    BookResource resource = new BookResource();

    @Test
    void parsesFullResponse() throws Exception {
        String json = """
                {"items":[{"id":"abc","volumeInfo":{"title":"Deep Work","authors":["Cal Newport"],"description":"Focus","pageCount":304,"imageLinks":{"thumbnail":"https://cover.jpg"}}}]}
                """;
        var results = resource.parseGoogleBooksResponse(json);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("googleBooksId")).isEqualTo("abc");
        assertThat(results.get(0).get("title")).isEqualTo("Deep Work");
        @SuppressWarnings("unchecked")
        List<String> authors = (List<String>) results.get(0).get("authors");
        assertThat(authors).containsExactly("Cal Newport");
        assertThat(results.get(0).get("coverUrl")).isEqualTo("https://cover.jpg");
        assertThat(results.get(0).get("description")).isEqualTo("Focus");
        assertThat(results.get(0).get("pageCount")).isEqualTo(304);
    }

    @Test
    void parsesMinimalResponse() throws Exception {
        String json = """
                {"items":[{"id":"x","volumeInfo":{"title":"Minimal"}}]}
                """;
        var results = resource.parseGoogleBooksResponse(json);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("title")).isEqualTo("Minimal");
        assertThat((List<?>) results.get(0).get("authors")).isEmpty();
        assertThat(results.get(0).get("coverUrl")).isEqualTo("");
        assertThat(results.get(0).get("description")).isEqualTo("");
        assertThat(results.get(0).get("pageCount")).isEqualTo(0);
    }

    @Test
    void parsesEmptyResponse() throws Exception {
        var results = resource.parseGoogleBooksResponse("{\"totalItems\":0}");
        assertThat(results).isEmpty();
    }

    @Test
    void skipsNullVolumeInfo() throws Exception {
        var results = resource.parseGoogleBooksResponse("{\"items\":[{\"id\":\"x\"}]}");
        assertThat(results).isEmpty();
    }

    @Test
    void parsesMultipleItems() throws Exception {
        String json = """
                {"items":[{"id":"a","volumeInfo":{"title":"A"}},{"id":"b","volumeInfo":{"title":"B"}}]}
                """;
        var results = resource.parseGoogleBooksResponse(json);
        assertThat(results).hasSize(2);
    }
}
