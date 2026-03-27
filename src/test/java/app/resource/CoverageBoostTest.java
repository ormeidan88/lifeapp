package app.resource;

import app.auth.AuthResourceTest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Additional tests to increase coverage on under-tested paths.
 */
@QuarkusTest
class CoverageBoostTest {
    String cookie() { return TestHelper.login(); }

    // --- InboxResource convert paths ---

    @Test void convertToProject() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"text\":\"New project idea\"}").when().post("/api/inbox").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"targetType\":\"project\",\"name\":\"From Inbox\"}")
                .when().post("/api/inbox/" + id + "/convert")
                .then().statusCode(201).body("type", equalTo("project")).body("inboxItemDeleted", equalTo(true));
    }

    @Test void convertToListItem() {
        String c = cookie();
        String pid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"LI Project\"}").when().post("/api/projects").then().extract().jsonPath().getString("id");
        String listId = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Custom List\"}").when().post("/api/lists").then().extract().jsonPath().getString("id");
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"text\":\"List item idea\"}").when().post("/api/inbox").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"targetType\":\"list-item\",\"listId\":\"" + listId + "\",\"projectId\":\"" + pid + "\"}")
                .when().post("/api/inbox/" + id + "/convert")
                .then().statusCode(201).body("type", equalTo("list-item"));
    }

    @Test void convertToListItemRequiresProjectId() {
        String c = cookie();
        String listId = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"CL2\"}").when().post("/api/lists").then().extract().jsonPath().getString("id");
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"text\":\"No pid\"}").when().post("/api/inbox").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"targetType\":\"list-item\",\"listId\":\"" + listId + "\"}")
                .when().post("/api/inbox/" + id + "/convert")
                .then().statusCode(400);
    }

    @Test void convertToTaskWithNonExistentProject() {
        String c = cookie();
        String id = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"text\":\"Bad project\"}").when().post("/api/inbox").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"targetType\":\"task\",\"projectId\":\"nonexistent\"}")
                .when().post("/api/inbox/" + id + "/convert")
                .then().statusCode(404).body("error", equalTo("Project not found"));
    }

    // --- BookResource additional paths ---

    @Test void listBooks() {
        given().cookie("lifeapp-session", cookie()).when().get("/api/books")
                .then().statusCode(200).body("books", notNullValue());
    }

    @Test void createBookAutoCreatesPage() {
        String c = cookie();
        String gid = "page" + System.nanoTime();
        String pageId = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"googleBooksId\":\"" + gid + "\",\"title\":\"Page Book\",\"authors\":[\"A\"],\"coverUrl\":\"\"}")
                .when().post("/api/books").then().statusCode(201).extract().jsonPath().getString("pageId");
        // Verify the page exists
        given().cookie("lifeapp-session", c).when().get("/api/pages/" + pageId)
                .then().statusCode(200).body("ownerType", equalTo("book"));
    }

    // --- DeckResource additional paths ---

    @Test void getReviewReturnsEmptyWhenNoDue() {
        String c = cookie();
        String did = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Empty\"}").when().post("/api/decks").then().extract().jsonPath().getString("id");
        // No cards added — review should return empty
        given().cookie("lifeapp-session", c).when().get("/api/decks/" + did + "/review")
                .then().statusCode(200).body("cards", empty());
    }

    @Test void reviewAllRatings() {
        String c = cookie();
        String did = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Ratings\"}").when().post("/api/decks").then().extract().jsonPath().getString("id");
        for (String rating : new String[]{"AGAIN", "HARD", "GOOD", "EASY"}) {
            String cid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                    .body("{\"front\":\"Q" + rating + "\",\"back\":\"A\"}").when().post("/api/decks/" + did + "/cards").then().extract().jsonPath().getString("id");
            given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                    .body("{\"rating\":\"" + rating + "\"}").when().post("/api/decks/" + did + "/cards/" + cid + "/review")
                    .then().statusCode(200);
        }
    }

    // --- HabitResource streak edge cases ---

    @Test void streakWithSkips() {
        String c = cookie();
        String hid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Streak\"}").when().post("/api/habits").then().extract().jsonPath().getString("id");
        // YES, SKIP, YES — streak should be 2 (SKIP doesn't break)
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"date\":\"2026-03-01\",\"value\":\"YES\"}").when().post("/api/habits/" + hid + "/entries");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"date\":\"2026-03-02\",\"value\":\"SKIP\"}").when().post("/api/habits/" + hid + "/entries");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"date\":\"2026-03-03\",\"value\":\"YES\"}").when().post("/api/habits/" + hid + "/entries");
        var habits = given().cookie("lifeapp-session", c).when().get("/api/habits")
                .then().extract().jsonPath().getList("habits");
        var habit = habits.stream().filter(h -> hid.equals(((java.util.Map<?,?>)h).get("id"))).findFirst().orElseThrow();
        Assertions.assertEquals(2, ((Number)((java.util.Map<?,?>)habit).get("currentStreak")).intValue());
    }

    @Test void streakBrokenByNo() {
        String c = cookie();
        String hid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"name\":\"Broken\"}").when().post("/api/habits").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"date\":\"2026-03-01\",\"value\":\"YES\"}").when().post("/api/habits/" + hid + "/entries");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"date\":\"2026-03-02\",\"value\":\"NO\"}").when().post("/api/habits/" + hid + "/entries");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"date\":\"2026-03-03\",\"value\":\"YES\"}").when().post("/api/habits/" + hid + "/entries");
        var habits = given().cookie("lifeapp-session", c).when().get("/api/habits")
                .then().extract().jsonPath().getList("habits");
        var habit = habits.stream().filter(h -> hid.equals(((java.util.Map<?,?>)h).get("id"))).findFirst().orElseThrow();
        Assertions.assertEquals(1, ((Number)((java.util.Map<?,?>)habit).get("currentStreak")).intValue());
        Assertions.assertEquals(1, ((Number)((java.util.Map<?,?>)habit).get("longestStreak")).intValue());
    }

    // --- PageResource additional paths ---

    @Test void updatePageNotFound() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"title\":\"Nope\"}").when().patch("/api/pages/nonexistent").then().statusCode(404);
    }

    @Test void listChildPages() {
        String c = cookie();
        String parent = given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"P\",\"ownerType\":\"standalone\"}").when().post("/api/pages").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"title\":\"C\",\"parentPageId\":\"" + parent + "\",\"ownerType\":\"standalone\"}").when().post("/api/pages");
        given().cookie("lifeapp-session", c).when().get("/api/pages?parentPageId=" + parent)
                .then().statusCode(200).body("pages", hasSize(1));
    }

    // --- CalendarResource additional ---

    @Test void createEventWithColor() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"title\":\"Colored\",\"date\":\"2026-05-01\",\"startTime\":\"09:00\",\"endTime\":\"10:00\",\"color\":\"#8B9E7C\"}")
                .when().post("/api/calendar/events").then().statusCode(201).body("color", equalTo("#8B9E7C"));
    }

    @Test void updateEventNotFound() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"title\":\"Nope\"}").when().patch("/api/calendar/events/nonexistent").then().statusCode(404);
    }

    // --- ListResource additional ---

    @Test void removeItemFromList() {
        String c = cookie();
        String pid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"name\":\"RP\"}").when().post("/api/projects").then().extract().jsonPath().getString("id");
        String tid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"projectId\":\"" + pid + "\",\"title\":\"RT\"}").when().post("/api/tasks").then().extract().jsonPath().getString("id");
        String lid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"name\":\"RL\"}").when().post("/api/lists").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"taskId\":\"" + tid + "\"}").when().post("/api/lists/" + lid + "/items");
        given().cookie("lifeapp-session", c).when().delete("/api/lists/" + lid + "/items/" + tid).then().statusCode(204);
    }

    // --- HabitResource update ---

    @Test void updateHabitNotFound() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"name\":\"Nope\"}").when().patch("/api/habits/nonexistent").then().statusCode(404);
    }

    // --- ProjectResource filter ---

    @Test void listProjectsFilterByStatus() {
        String c = cookie();
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"name\":\"FP\"}").when().post("/api/projects");
        given().cookie("lifeapp-session", c).when().get("/api/projects?status=NOT_STARTED")
                .then().statusCode(200).body("projects.status", everyItem(equalTo("NOT_STARTED")));
    }

    @Test void updateProjectNotFound() {
        given().cookie("lifeapp-session", cookie()).contentType(ContentType.JSON)
                .body("{\"name\":\"Nope\"}").when().patch("/api/projects/nonexistent").then().statusCode(404);
    }

    // --- TaskResource additional ---

    @Test void updateTaskNotFound() {
        String c = cookie();
        String pid = given().cookie("lifeapp-session", c).contentType(ContentType.JSON).body("{\"name\":\"TNF\"}").when().post("/api/projects").then().extract().jsonPath().getString("id");
        given().cookie("lifeapp-session", c).contentType(ContentType.JSON)
                .body("{\"projectId\":\"" + pid + "\",\"title\":\"Nope\"}").when().patch("/api/tasks/nonexistent?projectId=" + pid).then().statusCode(404);
    }

    @Test void deleteTaskRequiresProjectId() {
        given().cookie("lifeapp-session", cookie()).when().delete("/api/tasks/fake").then().statusCode(400);
    }
}
