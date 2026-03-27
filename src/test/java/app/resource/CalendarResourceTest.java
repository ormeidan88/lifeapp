package app.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CalendarResourceTest {
    String cookie() { return TestHelper.login(); }

    @Test void createEvent() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"title\":\"Standup\",\"date\":\"2026-03-20\",\"startTime\":\"09:00\",\"endTime\":\"09:30\"}")
                .when().post("/api/calendar/events").then().statusCode(201).body("source", equalTo("manual"));
    }

    @Test void createEventRequiresTitleAndDate() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"date\":\"2026-03-20\"}").when().post("/api/calendar/events").then().statusCode(400);
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"title\":\"No date\"}").when().post("/api/calendar/events").then().statusCode(400);
    }

    @Test void endBeforeStart() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"title\":\"Bad\",\"date\":\"2026-03-20\",\"startTime\":\"10:00\",\"endTime\":\"09:00\"}")
                .when().post("/api/calendar/events").then().statusCode(400);
    }

    @Test void createViaApiKey() {
        given().header("X-API-Key", "test-api-key").contentType(ContentType.JSON)
                .body("{\"title\":\"Ext\",\"date\":\"2026-03-20\",\"startTime\":\"14:00\",\"endTime\":\"15:00\",\"source\":\"external\"}")
                .when().post("/api/calendar/events").then().statusCode(201).body("source", equalTo("external"));
    }

    @Test void listByRange() {
        String c = cookie();
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"R\",\"date\":\"2026-04-01\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}").when().post("/api/calendar/events");
        given().cookie("lifeapp-session", c).when().get("/api/calendar/events?from=2026-04-01&to=2026-04-01")
                .then().statusCode(200).body("events", not(empty()));
    }

    @Test void listRequiresFromAndTo() {
        given().cookie("lifeapp-session", cookie()).when().get("/api/calendar/events").then().statusCode(400);
    }

    @Test void updateEvent() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"Upd\",\"date\":\"2026-03-20\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}").when().post("/api/calendar/events").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"Updated\"}").when().patch("/api/calendar/events/" + id)
                .then().statusCode(200).body("title", equalTo("Updated"));
    }

    @Test void deleteEvent() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"Del\",\"date\":\"2026-03-20\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}").when().post("/api/calendar/events").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).when().delete("/api/calendar/events/" + id).then().statusCode(204);
    }
}
