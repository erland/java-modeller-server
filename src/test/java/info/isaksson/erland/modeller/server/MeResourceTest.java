package info.isaksson.erland.modeller.server;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MeResourceTest {

    @Test
    void me_returns_stub_payload() {
        given()
          .when().get("/me")
          .then()
            .statusCode(200)
            .body("authenticated", is(false))
            .body("subject", is((String) null))
            .body("username", is((String) null))
            .body("email", is((String) null));
    }
}
