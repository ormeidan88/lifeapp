package app.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class TaskResourceTest {
    String cookie() { return TestHelper.login(); }

    String createProject(String c) {
        return given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"TP\"}").when().post("/api/projects").then().extract().jsonPath().getString("id");
    }

    @Test void createTask() {
        String c = cookie(); String pid = createProject(c);
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"projectId\":\"" + pid + "\",\"title\":\"Do it\"}")
                .when().post("/api/tasks").then().statusCode(201).body("done", equalTo(false)).body("position", equalTo(0));
    }

    @Test void createSubtask() {
        String c = cookie(); String pid = createProject(c);
        String tid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"projectId\":\"" + pid + "\",\"title\":\"Parent\"}").when().post("/api/tasks").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"projectId\":\"" + pid + "\",\"title\":\"Child\",\"parentTaskId\":\"" + tid + "\"}")
                .when().post("/api/tasks").then().statusCode(201).body("parentTaskId", equalTo(tid));
    }

    @Test void createTaskRequiresFields() {
        String c = cookie();
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"title\":\"No pid\"}").when().post("/api/tasks").then().statusCode(400);
    }

    @Test void listTasks() {
        String c = cookie(); String pid = createProject(c);
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"projectId\":\"" + pid + "\",\"title\":\"T1\"}").when().post("/api/tasks");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"projectId\":\"" + pid + "\",\"title\":\"T2\"}").when().post("/api/tasks");
        given().cookie("lifeapp-session", c).when().get("/api/tasks?projectId=" + pid).then().statusCode(200).body("tasks", hasSize(2));
    }

    @Test void markDone() {
        String c = cookie(); String pid = createProject(c);
        String tid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"projectId\":\"" + pid + "\",\"title\":\"Done\"}").when().post("/api/tasks").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"projectId\":\"" + pid + "\",\"done\":true}").when().patch("/api/tasks/" + tid + "?projectId=" + pid)
                .then().statusCode(200).body("done", equalTo(true));
    }

    @Test void reorder() {
        String c = cookie(); String pid = createProject(c);
        String t1 = given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"projectId\":\"" + pid + "\",\"title\":\"A\"}").when().post("/api/tasks").then().extract().jsonPath().getString("id");
        String t2 = given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"projectId\":\"" + pid + "\",\"title\":\"B\"}").when().post("/api/tasks").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"projectId\":\"" + pid + "\",\"taskIds\":[\"" + t2 + "\",\"" + t1 + "\"]}")
                .when().post("/api/tasks/reorder").then().statusCode(200);
    }

    @Test void deleteCascadesSubtasks() {
        String c = cookie(); String pid = createProject(c);
        String parent = given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"projectId\":\"" + pid + "\",\"title\":\"P\"}").when().post("/api/tasks").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"projectId\":\"" + pid + "\",\"title\":\"C\",\"parentTaskId\":\"" + parent + "\"}").when().post("/api/tasks");
        given().cookie("lifeapp-session", c).when().delete("/api/tasks/" + parent + "?projectId=" + pid).then().statusCode(204);
    }
}
