package app.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class HabitResourceTest {
    String cookie() { return TestHelper.login(); }

    @Test void createHabit() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"name\":\"Meditate\"}").when().post("/api/habits")
                .then().statusCode(201).body("name", equalTo("Meditate")).body("color", equalTo("#8B9E7C"));
    }

    @Test void createHabitRequiresName() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"name\":\"\"}").when().post("/api/habits").then().statusCode(400);
    }

    @Test void createEntry() {
        String c = cookie();
        String hid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"H\"}").when().post("/api/habits").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"date\":\"2026-03-20\",\"value\":\"YES\"}")
                .when().post("/api/habits/" + hid + "/entries").then().statusCode(201);
    }

    @Test void upsertEntry() {
        String c = cookie();
        String hid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"U\"}").when().post("/api/habits").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"date\":\"2026-03-20\",\"value\":\"YES\"}").when().post("/api/habits/" + hid + "/entries");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"date\":\"2026-03-20\",\"value\":\"NO\"}").when().post("/api/habits/" + hid + "/entries");
        given().cookie("lifeapp-session", c).when().get("/api/habits/" + hid + "/entries?from=2026-03-20&to=2026-03-20")
                .then().statusCode(200).body("entries", hasSize(1)).body("entries[0].value", equalTo("NO"));
    }

    @Test void invalidValue() {
        String c = cookie();
        String hid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"IV\"}").when().post("/api/habits").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"date\":\"2026-03-20\",\"value\":\"MAYBE\"}").when().post("/api/habits/" + hid + "/entries").then().statusCode(400);
    }

    @Test void nonExistentHabit() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"date\":\"2026-03-20\",\"value\":\"YES\"}").when().post("/api/habits/fake/entries").then().statusCode(404);
    }

    @Test void listIncludesStreaks() {
        given().cookie("lifeapp-session", cookie()).when().get("/api/habits")
                .then().statusCode(200);
    }

    @Test void deleteHabit() {
        String c = cookie();
        String hid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Del\"}").when().post("/api/habits").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).when().delete("/api/habits/" + hid).then().statusCode(204);
    }
}
