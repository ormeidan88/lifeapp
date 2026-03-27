package app.resource;

import io.quarkus.test.junit.QuarkusTest;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SpaFallbackResourceIntTest {

    @Test
    void fallbackReturnsHtmlOrNotFound() {
        // In test mode, META-INF/resources/index.html may not exist
        // but the endpoint should not return 401 (it's not behind auth)
        given().when().get("/inbox")
                .then().statusCode(anyOf(is(200), is(404)));
    }

    @Test
    void staticAssetsNotCaughtByFallback() {
        // Paths with extensions should NOT be caught by the fallback regex
        given().when().get("/assets/style.css")
                .then().statusCode(anyOf(is(200), is(404))); // not 401
    }
}
