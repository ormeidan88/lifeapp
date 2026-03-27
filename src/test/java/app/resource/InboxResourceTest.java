package app.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class InboxResourceTest {

    String cookie() { return TestHelper.login(); }

    @Test void createItem() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"text\":\"Buy milk\"}")
                .when().post("/api/inbox")
                .then().statusCode(201).body("id", notNullValue()).body("text", equalTo("Buy milk"));
    }

    @Test void createItemTrimsWhitespace() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"text\":\"  Trim me  \"}")
                .when().post("/api/inbox").then().statusCode(201).body("text", equalTo("Trim me"));
    }

    @Test void createItemEmptyText() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"text\":\"\"}").when().post("/api/inbox").then().statusCode(400);
    }

    @Test void createItemWhitespaceOnly() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"text\":\"   \"}").when().post("/api/inbox").then().statusCode(400);
    }

    @Test void listItems() {
        String c = cookie();
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"text\":\"List test\"}").when().post("/api/inbox");
        given().cookie("lifeapp-session", c).when().get("/api/inbox").then().statusCode(200).body("items", not(empty()));
    }

    @Test void deleteItem() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"text\":\"Delete me\"}").when().post("/api/inbox").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).when().delete("/api/inbox/" + id).then().statusCode(204);
    }

    @Test void convertNonExistent() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"targetType\":\"task\",\"projectId\":\"fake\"}")
                .when().post("/api/inbox/nonexistent/convert").then().statusCode(404);
    }

    @Test void convertInvalidType() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"text\":\"Bad\"}").when().post("/api/inbox").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"targetType\":\"invalid\"}").when().post("/api/inbox/" + id + "/convert").then().statusCode(400);
    }

    @Test void convertToTask() {
        String c = cookie();
        String pid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Conv Project\"}").when().post("/api/projects").then().extract().jsonPath().getString("id");
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"text\":\"Convert me\"}").when().post("/api/inbox").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"targetType\":\"task\",\"projectId\":\"" + pid + "\"}")
                .when().post("/api/inbox/" + id + "/convert")
                .then().statusCode(201).body("inboxItemDeleted", equalTo(true));
    }
}
