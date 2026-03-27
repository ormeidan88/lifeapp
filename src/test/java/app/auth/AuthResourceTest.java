package app.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class AuthResourceTest {

    @Test
    void loginWithCorrectPassword() {
        given().contentType(ContentType.JSON).body("{\"password\":\"dev\"}")
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("message", equalTo("ok"))
                .cookie("lifeapp-session", notNullValue());
    }

    @Test
    void loginWithWrongPassword() {
        given().contentType(ContentType.JSON).body("{\"password\":\"wrong\"}")
                .when().post("/api/auth/login")
                .then().statusCode(401)
                .body("error", equalTo("Invalid password"));
    }

    @Test
    void loginWithEmptyPassword() {
        given().contentType(ContentType.JSON).body("{\"password\":\"\"}")
                .when().post("/api/auth/login")
                .then().statusCode(401);
    }

    @Test
    void loginWithNoBody() {
        given().contentType(ContentType.JSON).body("{}")
                .when().post("/api/auth/login")
                .then().statusCode(401);
    }

    @Test
    void checkWithValidSession() {
        String cookie = given().contentType(ContentType.JSON).body("{\"password\":\"dev\"}")
                .when().post("/api/auth/login")
                .then().extract().cookie("lifeapp-session");

        given().cookie("lifeapp-session", cookie)
                .when().get("/api/auth/check")
                .then().statusCode(200)
                .body("authenticated", equalTo(true));
    }

    @Test
    void checkWithoutSession() {
        given().when().get("/api/auth/check")
                .then().statusCode(401)
                .body("authenticated", equalTo(false));
    }

    @Test
    void checkWithInvalidSession() {
        given().cookie("lifeapp-session", "invalid-token")
                .when().get("/api/auth/check")
                .then().statusCode(401);
    }

    @Test
    void logoutClearsCookie() {
        String cookie = given().contentType(ContentType.JSON).body("{\"password\":\"dev\"}")
                .when().post("/api/auth/login")
                .then().extract().cookie("lifeapp-session");

        given().cookie("lifeapp-session", cookie).contentType(ContentType.JSON)
                .when().post("/api/auth/logout")
                .then().statusCode(200)
                .body("message", equalTo("ok"));

        // Session should be invalidated
        given().cookie("lifeapp-session", cookie)
                .when().get("/api/auth/check")
                .then().statusCode(401);
    }

    @Test
    void apiKeyAuthentication() {
        given().header("X-API-Key", "test-api-key")
                .when().get("/api/inbox")
                .then().statusCode(200);
    }

    @Test
    void invalidApiKey() {
        given().header("X-API-Key", "wrong-key")
                .when().get("/api/inbox")
                .then().statusCode(401);
    }

    @Test
    void unauthenticatedRequestBlocked() {
        given().when().get("/api/inbox")
                .then().statusCode(401);
    }

    @Test
    void healthEndpointNotBlocked() {
        given().when().get("/q/health")
                .then().statusCode(anyOf(is(200), is(404))); // 404 if health ext not added, but not 401
    }

    // Helper to get a valid session cookie for other tests
    public static String login() {
        return given().contentType(ContentType.JSON).body("{\"password\":\"dev\"}")
                .when().post("/api/auth/login")
                .then().extract().cookie("lifeapp-session");
    }
}
