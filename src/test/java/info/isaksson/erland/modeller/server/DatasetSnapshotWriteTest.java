package info.isaksson.erland.modeller.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class DatasetSnapshotWriteTest {

    @Inject TestDataFactory data;
    @Inject ObjectMapper objectMapper;

    @Test
    @TestSecurity(user = "alice")
    public void put_snapshot_requires_if_match() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        ObjectNode payload = objectMapper.createObjectNode()
                .put("schemaVersion", 1)
                .set("model", objectMapper.createObjectNode());

        given()
                .contentType("application/json")
                .body(payload.toString())
                .when().put("/datasets/" + datasetId + "/snapshot")
                .then()
                .statusCode(428);
    }

    @Test
    @TestSecurity(user = "alice")
    public void put_snapshot_creates_first_revision_when_if_match_zero() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        ObjectNode payload = objectMapper.createObjectNode()
                .put("schemaVersion", 1)
                .set("model", objectMapper.createObjectNode());

        given()
                .header("If-Match", "\"0\"")
                .contentType("application/json")
                .body(payload.toString())
                .when().put("/datasets/" + datasetId + "/snapshot")
                .then()
                .statusCode(200)
                .header("ETag", "\"1\"")
                .body("datasetId", equalTo(datasetId.toString()))
                .body("revision", equalTo(1))
                .body("schemaVersion", equalTo(1));

        // GET should now reflect the written snapshot
        given()
                .when().get("/datasets/" + datasetId + "/snapshot")
                .then()
                .statusCode(200)
                .header("ETag", "\"1\"")
                .body("revision", equalTo(1))
                .body("schemaVersion", equalTo(1));
    }

    @Test
    @TestSecurity(user = "alice")
    public void put_snapshot_rejects_stale_if_match() throws Exception {
        UUID datasetId = data.createDatasetVisibleTo("alice");
        data.createSnapshot(datasetId, 5, 5);

        ObjectNode payload = objectMapper.createObjectNode()
                .put("schemaVersion", 6)
                .set("model", objectMapper.createObjectNode());

        given()
                .header("If-Match", "\"4\"")
                .contentType("application/json")
                .body(payload.toString())
                .when().put("/datasets/" + datasetId + "/snapshot")
                .then()
                .statusCode(409)
                .header("ETag", "\"5\"")
                .body("datasetId", equalTo(datasetId.toString()))
                .body("currentRevision", equalTo(5))
                .body("currentEtag", equalTo("5"));
    }


@Test
@TestSecurity(user = "alice")
public void put_snapshot_rejects_missing_schema_version_when_policy_basic() {
    UUID datasetId = data.createDatasetVisibleTo("alice", info.isaksson.erland.modeller.server.domain.ValidationPolicy.BASIC);

    ObjectNode payload = objectMapper.createObjectNode()
            .set("model", objectMapper.createObjectNode());

    given()
            .header("If-Match", "\"0\"")
            .contentType("application/json")
            .body(payload.toString())
            .when().put("/datasets/" + datasetId + "/snapshot")
            .then()
            .statusCode(400)
            .body("code", equalTo("VALIDATION_FAILED"))
            .body("message", equalTo("Snapshot validation failed"))
            .body("validationErrors.size()", equalTo(1))
            .body("validationErrors[0].rule", equalTo("schemaVersion.required"))
            .body("validationErrors[0].severity", equalTo("ERROR"));
}

@Test
@TestSecurity(user = "alice")
public void put_snapshot_rejects_write_when_dataset_leased_by_other_user() {
    UUID datasetId = data.createDatasetVisibleTo("alice");
    // Active lease held by bob
    data.createLease(datasetId, "bob", 600);

    ObjectNode payload = objectMapper.createObjectNode()
            .put("schemaVersion", 1)
            .set("model", objectMapper.createObjectNode());

    given()
            .header("If-Match", "\"0\"")
            .contentType("application/json")
            .body(payload.toString())
            .when().put("/datasets/" + datasetId + "/snapshot")
            .then()
            .statusCode(409)
            .body("datasetId", equalTo(datasetId.toString()))
            .body("holderSub", equalTo("bob"));
}

@Test
@TestSecurity(user = "alice")
public void put_snapshot_requires_lease_token_when_active_lease_held_by_caller() {
    UUID datasetId = data.createDatasetVisibleTo("alice");
    String token = data.createLeaseWithToken(datasetId, "alice", 600);

    ObjectNode payload = objectMapper.createObjectNode()
            .put("schemaVersion", 1)
            .set("model", objectMapper.createObjectNode());

    // Missing token -> 428
    given()
            .header("If-Match", "\"0\"")
            .contentType("application/json")
            .body(payload.toString())
            .when().put("/datasets/" + datasetId + "/snapshot")
            .then()
            .statusCode(428)
            .body("code", equalTo("LEASE_TOKEN_REQUIRED"));

    // Correct token -> 200
    given()
            .header("If-Match", "\"0\"")
            .header("X-Lease-Token", token)
            .contentType("application/json")
            .body(payload.toString())
            .when().put("/datasets/" + datasetId + "/snapshot")
            .then()
            .statusCode(200)
            .body("revision", equalTo(1));


}

    @Test
    @TestSecurity(user = "alice")
    public void owner_force_override_can_write_while_leased_by_other_user() throws Exception {
        UUID datasetId = data.createDatasetVisibleTo("alice");
        data.grantAcl(datasetId, "bob", "EDITOR");
        // Bob holds an active lease
        data.createLeaseWithToken(datasetId, "bob", 300);

        ObjectNode payload = objectMapper.createObjectNode()
                .put("schemaVersion", 1)
                .set("model", objectMapper.createObjectNode().put("k", "v"));

        given()
                .header("If-Match", "\"0\"")
                .contentType("application/json")
                .body(payload.toString())
        .when()
                .put("/datasets/" + datasetId + "/snapshot?force=true")
        .then()
                .statusCode(200)
                .body("revision", equalTo(1));
    }
}
