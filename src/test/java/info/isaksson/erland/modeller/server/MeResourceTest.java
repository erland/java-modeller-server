package info.isaksson.erland.modeller.server;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MeResourceTest {

    @Test
    void me_requires_authentication() {
        given()
          .when().get("/me")
          .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "alice")
    void me_returns_current_user_when_authenticated() {
        given()
          .when().get("/me")
          .then()
            .statusCode(200)
            .body("authenticated", is(true))
            .body("subject", is("alice"))
            .body("username", is((String) null))
            .body("email", is((String) null));
    }
}
