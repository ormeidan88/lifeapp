package app.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class BookResourceTest {
    String cookie() { return TestHelper.login(); }

    @Test void searchBooks() {
        given().cookie("lifeapp-session", cookie()).when().get("/api/books/search?q=deep+work")
                .then().statusCode(200).body("results", not(empty()));
    }

    @Test void createBook() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"googleBooksId\":\"unique" + System.nanoTime() + "\",\"title\":\"Test Book\",\"authors\":[\"Author\"],\"coverUrl\":\"\"}")
                .when().post("/api/books").then().statusCode(201).body("pageId", notNullValue());
    }

    @Test void duplicateBook() {
        String c = cookie();
        String gid = "dup" + System.nanoTime();
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"googleBooksId\":\"" + gid + "\",\"title\":\"B\",\"authors\":[\"A\"],\"coverUrl\":\"\"}").when().post("/api/books");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"googleBooksId\":\"" + gid + "\",\"title\":\"B\",\"authors\":[\"A\"],\"coverUrl\":\"\"}").when().post("/api/books")
                .then().statusCode(409);
    }

    @Test void deleteBook() {
        String c = cookie();
        String bid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"googleBooksId\":\"del" + System.nanoTime() + "\",\"title\":\"Del\",\"authors\":[\"A\"],\"coverUrl\":\"\"}").when().post("/api/books").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).when().delete("/api/books/" + bid).then().statusCode(204);
    }
}
