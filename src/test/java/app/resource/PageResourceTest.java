package app.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PageResourceTest {
    String cookie() { return TestHelper.login(); }

    @Test void createPage() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"title\":\"Notes\",\"ownerType\":\"standalone\"}")
                .when().post("/api/pages").then().statusCode(201).body("title", equalTo("Notes"));
    }

    @Test void createChildPage() {
        String c = cookie();
        String pid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"Parent\",\"ownerType\":\"standalone\"}").when().post("/api/pages").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"Child\",\"parentPageId\":\"" + pid + "\",\"ownerType\":\"standalone\"}")
                .when().post("/api/pages").then().statusCode(201);
    }

    @Test void listStandaloneOnly() {
        String c = cookie();
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"title\":\"SA\",\"ownerType\":\"standalone\"}").when().post("/api/pages");
        given().cookie("lifeapp-session", c).when().get("/api/pages?ownerType=standalone").then().statusCode(200).body("pages", not(empty()));
    }

    @Test void saveAndGetContent() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"Content\",\"ownerType\":\"standalone\"}").when().post("/api/pages").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"content\":{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Hello\"}]}]}}")
                .when().patch("/api/pages/" + id).then().statusCode(200);
        given().cookie("lifeapp-session", c).when().get("/api/pages/" + id)
                .then().statusCode(200).body("content.content[0].content[0].text", equalTo("Hello"));
    }

    @Test void getNotFound() {
        given().cookie("lifeapp-session", cookie()).when().get("/api/pages/nonexistent").then().statusCode(404);
    }

    @Test void deleteCascadesChildren() {
        String c = cookie();
        String parent = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"P\",\"ownerType\":\"standalone\"}").when().post("/api/pages").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"C\",\"parentPageId\":\"" + parent + "\",\"ownerType\":\"standalone\"}").when().post("/api/pages");
        given().cookie("lifeapp-session", c).when().delete("/api/pages/" + parent).then().statusCode(204);
        given().cookie("lifeapp-session", c).when().get("/api/pages/" + parent).then().statusCode(404);
    }
}
