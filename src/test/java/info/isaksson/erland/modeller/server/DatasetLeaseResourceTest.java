package info.isaksson.erland.modeller.server;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class DatasetLeaseResourceTest {

    @Inject TestDataFactory data;

    @Test
    @TestSecurity(user = "alice", roles = {"user"})
    void acquire_refresh_status_and_release_happy_path_as_holder() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        // acquire by alice
        String token1 =
                given()
                        .when().post("/datasets/" + datasetId + "/lease")
                        .then()
                        .statusCode(200)
                        .body("datasetId", equalTo(datasetId.toString()))
                        .body("active", equalTo(true))
                        .body("holderSub", equalTo("alice"))
                        .body("expiresAt", notNullValue())
                        .body("leaseToken", notNullValue())
                        .extract().path("leaseToken");

        // status never exposes token
        given()
                .when().get("/datasets/" + datasetId + "/lease")
                .then()
                .statusCode(200)
                .body("active", equalTo(true))
                .body("holderSub", equalTo("alice"))
                .body("leaseToken", nullValue());

        // refresh by alice keeps token stable
        String token2 =
                given()
                        .when().post("/datasets/" + datasetId + "/lease")
                        .then()
                        .statusCode(200)
                        .body("active", equalTo(true))
                        .body("holderSub", equalTo("alice"))
                        .body("leaseToken", notNullValue())
                        .extract().path("leaseToken");

        org.junit.jupiter.api.Assertions.assertEquals(token1, token2);

        // release by alice
        given()
                .when().delete("/datasets/" + datasetId + "/lease")
                .then()
                .statusCode(204);

        // status now inactive
        given()
                .when().get("/datasets/" + datasetId + "/lease")
                .then()
                .statusCode(200)
                .body("active", equalTo(false));

        org.junit.jupiter.api.Assertions.assertEquals(1, data.countAudit(datasetId, "LEASE_ACQUIRED"));
        org.junit.jupiter.api.Assertions.assertEquals(1, data.countAudit(datasetId, "LEASE_REFRESHED"));
        org.junit.jupiter.api.Assertions.assertEquals(1, data.countAudit(datasetId, "LEASE_RELEASED"));
    }

    @Test
    @TestSecurity(user = "bob", roles = {"user"})
    void editor_cannot_acquire_when_leased_by_other_and_cannot_release_when_not_owner() {
        UUID datasetId = data.createDatasetVisibleTo("alice");
        data.grantAcl(datasetId, "bob", "EDITOR");

        // alice acquires (direct DB setup not needed; call endpoint as alice not possible here)
        // Instead: create lease row via REST as bob is not allowed; so we simulate by creating it as owner in DB.
        data.createLease(datasetId, "alice", 600);

        // acquire by bob while active => conflict
        given()
                .when().post("/datasets/" + datasetId + "/lease")
                .then()
                .statusCode(409)
                .body("datasetId", equalTo(datasetId.toString()))
                .body("holderSub", equalTo("alice"))
                .body("expiresAt", notNullValue());

        // bob cannot release (not holder, not owner)
        given()
                .when().delete("/datasets/" + datasetId + "/lease")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "bob", roles = {"user"})
    void non_member_gets_404_to_avoid_leakage() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        given()
                .when().get("/datasets/" + datasetId + "/lease")
                .then()
                .statusCode(404);

        given()
                .when().post("/datasets/" + datasetId + "/lease")
                .then()
                .statusCode(404);

        given()
                .when().delete("/datasets/" + datasetId + "/lease")
                .then()
                .statusCode(404);
    }
}
