package app.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Additional tests targeting remaining low-coverage paths.
 */
@QuarkusTest
class CoverageBoost2Test {
    String cookie() { return TestHelper.login(); }

    // --- BookResource: search returns structured results ---
    @Test void searchBooksReturnsStructuredResults() {
        given().cookie("lifeapp-session", cookie()).when().get("/api/books/search?q=atomic+habits")
                .then().statusCode(200)
                .body("results[0].googleBooksId", notNullValue())
                .body("results[0].title", notNullValue())
                .body("results[0].authors", notNullValue())
                .body("results[0].coverUrl", notNullValue())
                .body("results[0].description", notNullValue())
                .body("results[0].pageCount", notNullValue());
    }

    @Test void searchBooksMultipleResults() {
        given().cookie("lifeapp-session", cookie()).when().get("/api/books/search?q=productivity")
                .then().statusCode(200)
                .body("results.size()", greaterThanOrEqualTo(1));
    }

    // --- ImageResource: upload gif and webp ---
    @Test void uploadGif() throws Exception {
        java.io.File tmp = java.io.File.createTempFile("test", ".gif");
        try (var fos = new java.io.FileOutputStream(tmp)) { fos.write(new byte[]{0x47, 0x49, 0x46}); }
        given().cookie("lifeapp-session", cookie()).multiPart("file", tmp, "image/gif")
                .when().post("/api/images").then().statusCode(201).body("url", endsWith(".gif"));
        tmp.delete();
    }

    @Test void uploadWebp() throws Exception {
        java.io.File tmp = java.io.File.createTempFile("test", ".webp");
        try (var fos = new java.io.FileOutputStream(tmp)) { fos.write(new byte[]{0x52, 0x49, 0x46, 0x46}); }
        given().cookie("lifeapp-session", cookie()).multiPart("file", tmp, "image/webp")
                .when().post("/api/images").then().statusCode(201).body("url", endsWith(".webp"));
        tmp.delete();
    }

    // --- ListResource: list shows itemCount ---
    @Test void listShowsItemCount() {
        String c = cookie();
        given().cookie("lifeapp-session", c).when().get("/api/lists")
                .then().statusCode(200)
                .body("lists[0].itemCount", notNullValue());
    }

    // --- BookResource: delete cascades page tree ---
    @Test void deleteBookCascadesPageTree() {
        String c = cookie();
        String gid = "cascade" + System.nanoTime();
        var resp = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"googleBooksId\":\"" + gid + "\",\"title\":\"Cascade\",\"authors\":[\"A\"],\"coverUrl\":\"\"}")
                .when().post("/api/books").then().statusCode(201).extract().jsonPath();
        String bookId = resp.getString("id");
        String pageId = resp.getString("pageId");

        // Add a child page to the book's page
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"Child Note\",\"parentPageId\":\"" + pageId + "\",\"ownerType\":\"book\"}")
                .when().post("/api/pages").then().statusCode(201);

        // Delete book — should cascade to page and child page
        given().cookie("lifeapp-session", c).when().delete("/api/books/" + bookId).then().statusCode(204);
        given().cookie("lifeapp-session", c).when().get("/api/pages/" + pageId).then().statusCode(404);
    }

    // --- ProjectResource: create auto-creates page ---
    @Test void createProjectAutoCreatesPage() {
        String c = cookie();
        var resp = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Page Project\"}").when().post("/api/projects").then().statusCode(201).extract().jsonPath();
        String pageId = resp.getString("pageId");
        given().cookie("lifeapp-session", c).when().get("/api/pages/" + pageId)
                .then().statusCode(200).body("ownerType", equalTo("project"));
    }

    // --- DeckResource: list shows dueCount ---
    @Test void listDecksShowsDueCount() {
        String c = cookie();
        String did = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Due\"}").when().post("/api/decks").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"front\":\"Q\",\"back\":\"A\"}").when().post("/api/decks/" + did + "/cards");
        given().cookie("lifeapp-session", c).when().get("/api/decks")
                .then().statusCode(200)
                .body("decks.find { it.id == '" + did + "' }.dueCount", greaterThanOrEqualTo(1));
    }

    // --- PageResource: update title ---
    @Test void updatePageTitleOnly() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"Old\",\"ownerType\":\"standalone\"}").when().post("/api/pages").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"New\"}").when().patch("/api/pages/" + id)
                .then().statusCode(200).body("title", equalTo("New"));
    }

    // --- HabitResource: get entries with default range ---
    @Test void getEntriesDefaultRange() {
        String c = cookie();
        String hid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Range\"}").when().post("/api/habits").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"date\":\"2026-01-15\",\"value\":\"YES\"}").when().post("/api/habits/" + hid + "/entries");
        // No from/to — should use defaults
        given().cookie("lifeapp-session", c).when().get("/api/habits/" + hid + "/entries")
                .then().statusCode(200).body("entries", not(empty()));
    }

    // --- CalendarResource: create with source=external ---
    @Test void createExternalEvent() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"title\":\"Outlook\",\"date\":\"2026-06-01\",\"startTime\":\"10:00\",\"endTime\":\"11:00\",\"source\":\"external\"}")
                .when().post("/api/calendar/events").then().statusCode(201).body("source", equalTo("external"));
    }

    // --- CalendarResource: update multiple fields ---
    @Test void updateEventMultipleFields() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"Multi\",\"date\":\"2026-06-01\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}")
                .when().post("/api/calendar/events").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"Updated\",\"date\":\"2026-06-02\",\"startTime\":\"10:00\",\"endTime\":\"11:00\",\"color\":\"#C4836A\"}")
                .when().patch("/api/calendar/events/" + id)
                .then().statusCode(200).body("title", equalTo("Updated")).body("color", equalTo("#C4836A"));
    }
}
