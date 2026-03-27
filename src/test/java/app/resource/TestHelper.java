package app.resource;

import io.restassured.http.ContentType;
import static io.restassured.RestAssured.given;

public class TestHelper {
    public static String login() {
        return given().contentType(ContentType.JSON).body("{\"password\":\"dev\"}")
                .when().post("/api/auth/login")
                .then().extract().cookie("lifeapp-session");
    }
}
