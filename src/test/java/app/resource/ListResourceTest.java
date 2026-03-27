package app.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ListResourceTest {
    String cookie() { return TestHelper.login(); }

    @Test void listCreatesSystemLists() {
        given().cookie("lifeapp-session", cookie()).when().get("/api/lists")
                .then().statusCode(200).body("lists.name", hasItems("Today", "Someday"));
    }

    @Test void createCustomList() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"name\":\"Waiting\"}").when().post("/api/lists")
                .then().statusCode(201).body("type", equalTo("CUSTOM"));
    }

    @Test void createListRequiresName() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"name\":\"\"}").when().post("/api/lists").then().statusCode(400);
    }

    @Test void cannotDeleteSystemList() {
        String c = cookie();
        String todayId = given().cookie("lifeapp-session", c).when().get("/api/lists")
                .then().extract().jsonPath().getString("lists.find { it.name == 'Today' }.id");
        given().cookie("lifeapp-session", c).when().delete("/api/lists/" + todayId).then().statusCode(403);
    }

    @Test void addItemRequiresTaskId() {
        String c = cookie();
        String todayId = given().cookie("lifeapp-session", c).when().get("/api/lists")
                .then().extract().jsonPath().getString("lists.find { it.name == 'Today' }.id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{}")
                .when().post("/api/lists/" + todayId + "/items").then().statusCode(400);
    }

    @Test void addAndGetItems() {
        String c = cookie();
        String pid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"name\":\"LP\"}").when().post("/api/projects").then().extract().jsonPath().getString("id");
        String tid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"projectId\":\"" + pid + "\",\"title\":\"T\"}").when().post("/api/tasks").then().extract().jsonPath().getString("id");
        String todayId = given().cookie("lifeapp-session", c).when().get("/api/lists").then().extract().jsonPath().getString("lists.find { it.name == 'Today' }.id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"taskId\":\"" + tid + "\"}").when().post("/api/lists/" + todayId + "/items").then().statusCode(201);
        given().cookie("lifeapp-session", c).when().get("/api/lists/" + todayId + "/items").then().statusCode(200).body("items", not(empty()));
    }

    @Test void deleteCustomList() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"name\":\"Temp\"}").when().post("/api/lists").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).when().delete("/api/lists/" + id).then().statusCode(204);
    }
}
