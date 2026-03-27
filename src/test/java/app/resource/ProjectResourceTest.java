package app.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ProjectResourceTest {
    String cookie() { return TestHelper.login(); }

    @Test void createProject() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"name\":\"Test Project\"}").when().post("/api/projects")
                .then().statusCode(201).body("status", equalTo("NOT_STARTED")).body("pageId", notNullValue());
    }

    @Test void createProjectRequiresName() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"name\":\"\"}").when().post("/api/projects").then().statusCode(400);
    }

    @Test void listProjects() {
        String c = cookie();
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"name\":\"LP\"}").when().post("/api/projects");
        given().cookie("lifeapp-session", c).when().get("/api/projects").then().statusCode(200).body("projects", not(empty()));
    }

    @Test void getProject() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Get Me\"}").when().post("/api/projects").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).when().get("/api/projects/" + id).then().statusCode(200).body("name", equalTo("Get Me"));
    }

    @Test void getProjectNotFound() {
        given().cookie("lifeapp-session", cookie()).when().get("/api/projects/nonexistent").then().statusCode(404);
    }

    @Test void updateProjectStatus() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Update Me\"}").when().post("/api/projects").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"status\":\"IN_PROGRESS\"}").when().patch("/api/projects/" + id)
                .then().statusCode(200).body("status", equalTo("IN_PROGRESS"));
    }

    @Test void deleteProjectCascades() {
        String c = cookie();
        String pid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Del Project\"}").when().post("/api/projects").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"projectId\":\"" + pid + "\",\"title\":\"Task\"}").when().post("/api/tasks");
        given().cookie("lifeapp-session", c).when().delete("/api/projects/" + pid).then().statusCode(204);
        given().cookie("lifeapp-session", c).when().get("/api/projects/" + pid).then().statusCode(404);
        given().cookie("lifeapp-session", c).when().get("/api/tasks?projectId=" + pid).then().statusCode(200).body("tasks", empty());
    }
}
