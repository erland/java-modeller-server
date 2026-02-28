package info.isaksson.erland.modeller.server;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class DatasetOpsLeasePolicyTest {

    @Inject
    TestDataFactory data;

    private static Map<String, Object> replaceSnapshotOpRequest(long baseRevision, String opId) {
        return Map.of(
                "baseRevision", baseRevision,
                "operations", java.util.List.of(
                        Map.of(
                                "opId", opId,
                                "type", "SNAPSHOT_REPLACE",
                                "payload", Map.of("schemaVersion", 1, "model", Map.of())
                        )
                )
        );
    }

    @Test
    @TestSecurity(user = "alice")
    public void append_ops_blocked_when_active_lease_held_by_other_without_force() {
        UUID datasetId = data.createDatasetVisibleTo("alice");
        data.grantAcl(datasetId, "bob", "EDITOR");
        data.createLease(datasetId, "bob", 300);

        given()
                .contentType("application/json")
                .body(replaceSnapshotOpRequest(0, "op-lease-1"))
                .when().post("/datasets/" + datasetId + "/ops")
                .then()
                .statusCode(409)
                .body("holderSub", equalTo("bob"));
    }

    @Test
    @TestSecurity(user = "alice")
    public void append_ops_owner_can_force_override_lease_held_by_other() {
        UUID datasetId = data.createDatasetVisibleTo("alice");
        data.grantAcl(datasetId, "bob", "EDITOR");
        data.createLease(datasetId, "bob", 300);

        given()
                .contentType("application/json")
                .queryParam("force", true)
                .body(replaceSnapshotOpRequest(0, "op-lease-2"))
                .when().post("/datasets/" + datasetId + "/ops")
                .then()
                .statusCode(200)
                .body("newRevision", equalTo(1));
    }

    @Test
    @TestSecurity(user = "alice")
    public void append_ops_requires_lease_token_when_caller_is_lease_holder() {
        UUID datasetId = data.createDatasetVisibleTo("alice");
        String token = data.createLeaseWithToken(datasetId, "alice", 300);

        // Missing token
        given()
                .contentType("application/json")
                .body(replaceSnapshotOpRequest(0, "op-lease-3"))
                .when().post("/datasets/" + datasetId + "/ops")
                .then()
                .statusCode(428)
                .body("errorCode", equalTo("LEASE_TOKEN_REQUIRED"));

        // With token
        given()
                .contentType("application/json")
                .header("X-Lease-Token", token)
                .body(replaceSnapshotOpRequest(0, "op-lease-4"))
                .when().post("/datasets/" + datasetId + "/ops")
                .then()
                .statusCode(200)
                .body("newRevision", equalTo(1));
    }
}
