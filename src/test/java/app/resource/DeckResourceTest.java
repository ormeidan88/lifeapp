package app.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class DeckResourceTest {
    String cookie() { return TestHelper.login(); }

    @Test void createDeck() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"name\":\"Spanish\"}").when().post("/api/decks")
                .then().statusCode(201).body("name", equalTo("Spanish")).body("cardCount", equalTo(0));
    }

    @Test void createDeckRequiresName() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"name\":\"\"}").when().post("/api/decks").then().statusCode(400);
    }

    @Test void createCard() {
        String c = cookie();
        String did = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"D\"}").when().post("/api/decks").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"front\":\"Hola\",\"back\":\"Hello\"}").when().post("/api/decks/" + did + "/cards")
                .then().statusCode(201).body("fsrs.state", notNullValue());
    }

    @Test void createCardRequiresFields() {
        String c = cookie();
        String did = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"D2\"}").when().post("/api/decks").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"front\":\"\"}").when().post("/api/decks/" + did + "/cards").then().statusCode(400);
    }

    @Test void createCardViaApiKey() {
        String c = cookie();
        String did = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"API\"}").when().post("/api/decks").then().extract().jsonPath().getString("id");
        given().header("X-API-Key", "test-api-key").contentType(ContentType.JSON)
                .body("{\"front\":\"Q\",\"back\":\"A\"}").when().post("/api/decks/" + did + "/cards").then().statusCode(201);
    }

    @Test void reviewCard() {
        String c = cookie();
        String did = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Rev\"}").when().post("/api/decks").then().extract().jsonPath().getString("id");
        String cid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"front\":\"Q\",\"back\":\"A\"}").when().post("/api/decks/" + did + "/cards").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"rating\":\"GOOD\"}").when().post("/api/decks/" + did + "/cards/" + cid + "/review")
                .then().statusCode(200).body("fsrs.due", notNullValue());
    }

    @Test void reviewInvalidRating() {
        String c = cookie();
        String did = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"IR\"}").when().post("/api/decks").then().extract().jsonPath().getString("id");
        String cid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"front\":\"Q\",\"back\":\"A\"}").when().post("/api/decks/" + did + "/cards").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"rating\":\"PERFECT\"}").when().post("/api/decks/" + did + "/cards/" + cid + "/review").then().statusCode(400);
    }

    @Test void reviewNotFound() {
        String c = cookie();
        String did = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"NF\"}").when().post("/api/decks").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"rating\":\"GOOD\"}").when().post("/api/decks/" + did + "/cards/fake/review").then().statusCode(404);
    }

    @Test void deleteDeck() {
        String c = cookie();
        String did = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Del\"}").when().post("/api/decks").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"front\":\"Q\",\"back\":\"A\"}").when().post("/api/decks/" + did + "/cards");
        given().cookie("lifeapp-session", c).when().delete("/api/decks/" + did).then().statusCode(204);
        given().cookie("lifeapp-session", c).when().get("/api/decks/" + did + "/cards").then().statusCode(200).body("cards", empty());
    }
}
