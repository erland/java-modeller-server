package info.isaksson.erland.modeller.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end "Phase 2 happy path" integration test.
 *
 * Covers: validationPolicy, lease acquire/status/refresh/release,
 * snapshot writes with If-Match + X-Lease-Token, history metadata, restore endpoint, and head polling.
 */
@QuarkusTest
public class Phase2HappyPathIT {

    @Inject ObjectMapper objectMapper;

    @Test
    @TestSecurity(user = "alice")
    public void phase2_happy_path() {
        // 1) Create dataset with Phase 2 validation policy (basic)
        UUID datasetId =
                given()
                        .contentType(ContentType.JSON)
                        .body("{\"name\":\"Phase2 DS\",\"description\":\"E2E\",\"validationPolicy\":\"basic\"}")
                .when()
                        .post("/datasets")
                .then()
                        .statusCode(201)
                        .body("validationPolicy", equalTo("basic"))
                        .extract().jsonPath().getUUID("id");

        // 2) Acquire lease (token returned)
        String leaseToken =
                given()
                .when()
                        .post("/datasets/" + datasetId + "/lease")
                .then()
                        .statusCode(200)
                        .body("datasetId", equalTo(datasetId.toString()))
                        .body("active", equalTo(true))
                        .body("holderSub", equalTo("alice"))
                        .body("leaseToken", notNullValue())
                        .extract().jsonPath().getString("leaseToken");

        // 3) Head endpoint should show lease without exposing token
        given()
        .when()
                .get("/datasets/" + datasetId + "/head")
        .then()
                .statusCode(200)
                .body("datasetId", equalTo(datasetId.toString()))
                .body("validationPolicy", equalTo("basic"))
                .body("leaseActive", equalTo(true))
                .body("leaseHolderSub", equalTo("alice"))
                .body("leaseExpiresAt", notNullValue())
                .body("$", not(hasKey("leaseToken")));

        // 4) First snapshot write: If-Match "0" + X-Lease-Token; schemaVersion required by basic
        ObjectNode payload1 = objectMapper.createObjectNode()
                .put("schemaVersion", 1)
                .set("model", objectMapper.createObjectNode().put("hello", "world"));

        given()
                .contentType(ContentType.JSON)
                .header("If-Match", "\"0\"")
                .header("X-Lease-Token", leaseToken)
                .body(payload1.toString())
        .when()
                .put("/datasets/" + datasetId + "/snapshot")
        .then()
                .statusCode(200)
                .header("ETag", equalTo("\"1\""))
                .body("datasetId", equalTo(datasetId.toString()))
                .body("revision", equalTo(1))
                .body("etag", equalTo("1"));

        // 5) Refresh lease (POST again) should keep holder and return token
        String refreshedToken =
                given()
                .when()
                        .post("/datasets/" + datasetId + "/lease")
                .then()
                        .statusCode(200)
                        .body("active", equalTo(true))
                        .body("holderSub", equalTo("alice"))
                        .body("leaseToken", notNullValue())
                        .extract().jsonPath().getString("leaseToken");

        // 6) Second snapshot write: If-Match "1"
        ObjectNode payload2 = objectMapper.createObjectNode()
                .put("schemaVersion", 1)
                .set("model", objectMapper.createObjectNode().put("n", 2));

        given()
                .contentType(ContentType.JSON)
                .header("If-Match", "\"1\"")
                .header("X-Lease-Token", refreshedToken)
                .body(payload2.toString())
        .when()
                .put("/datasets/" + datasetId + "/snapshot")
        .then()
                .statusCode(200)
                .header("ETag", equalTo("\"2\""))
                .body("revision", equalTo(2))
                .body("etag", equalTo("2"));

        // 7) History should include Phase 2 metadata fields (payloadBytes, savedAction)
        given()
        .when()
                .get("/datasets/" + datasetId + "/snapshots?limit=10")
        .then()
                .statusCode(200)
                .body("items.size()", greaterThanOrEqualTo(1))
                .body("items[0].payloadBytes", greaterThanOrEqualTo(1))
                .body("items[0].savedAction", anyOf(equalTo("WRITE"), equalTo("RESTORE")));

        // 8) Restore snapshot revision 1 -> creates new latest revision 3 (If-Match current "2")
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .header("If-Match", "\"2\"")
                .header("X-Lease-Token", refreshedToken)
        .when()
                .post("/datasets/" + datasetId + "/snapshots/1/restore")
        .then()
                .statusCode(200)
                .header("ETag", equalTo("\"3\""))
                .body("revision", equalTo(3))
                .body("etag", equalTo("3"));

        // 9) Head should reflect current revision after restore
        given()
        .when()
                .get("/datasets/" + datasetId + "/head")
        .then()
                .statusCode(200)
                .body("currentRevision", equalTo(3))
                .body("currentEtag", equalTo("3"));

        // 10) Release lease
        given()
        .when()
                .delete("/datasets/" + datasetId + "/lease")
        .then()
                .statusCode(204);

        // 11) Lease status should now be inactive
        given()
        .when()
                .get("/datasets/" + datasetId + "/lease")
        .then()
                .statusCode(200)
                .body("active", equalTo(false));
    }
}
