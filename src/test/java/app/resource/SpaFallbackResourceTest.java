package app.resource;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SpaFallbackResourceTest {

    @Test
    void fallbackReturns404WhenNoIndexHtml() {
        var resource = new SpaFallbackResource();
        var response = resource.fallback("inbox");
        // In unit test context, META-INF/resources/index.html doesn't exist
        assertThat(response.getStatus()).isEqualTo(404);
    }
}
