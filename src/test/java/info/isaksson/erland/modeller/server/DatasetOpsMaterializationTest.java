package info.isaksson.erland.modeller.server;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAuditRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class DatasetOpsMaterializationTest {

    @Inject
    TestDataFactory data;

    @Inject
    DatasetAuditRepository auditRepository;

    @Test
    @TestSecurity(user = "alice")
    public void append_ops_materializes_snapshot_strategyA() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        // Replace snapshot via ops (Strategy A materialization)
        Map<String, Object> request = Map.of(
                "baseRevision", 0,
                "operations", java.util.List.of(
                        Map.of(
                                "opId", "op-1",
                                "type", "SNAPSHOT_REPLACE",
                                "payload", Map.of(
                                        "schemaVersion", 1,
                                        "model", Map.of(
                                                "elements", java.util.List.of(Map.of("id", "e1"))
                                        )
                                )
                        )
                )
        );

        given()
                .contentType("application/json")
                .body(request)
                .when().post("/datasets/" + datasetId + "/ops")
                .then()
                .statusCode(200)
                .body("newRevision", equalTo(1));

        // Audit integration: one OPS_APPEND entry should be written.
        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                auditRepository.countForDatasetAndAction(datasetId, "OPS_APPEND"),
                "Expected OPS_APPEND audit entry"
        );

        given()
                .when().get("/datasets/" + datasetId + "/snapshot")
                .then()
                .statusCode(200)
                .header("ETag", "\"1\"")
                .body("datasetId", equalTo(datasetId.toString()))
                .body("revision", equalTo(1))
                .body("payload", notNullValue())
                .body("payload.schemaVersion", equalTo(1))
                .body("payload.model.elements[0].id", equalTo("e1"));
    }

    @Test
    @TestSecurity(user = "alice")
    public void append_ops_rejects_duplicate_opId() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        Map<String, Object> request = Map.of(
                "baseRevision", 0,
                "operations", java.util.List.of(
                        Map.of(
                                "opId", "dup-1",
                                "type", "SNAPSHOT_REPLACE",
                                "payload", Map.of("schemaVersion", 1, "model", Map.of())
                        )
                )
        );

        given().contentType("application/json").body(request)
                .when().post("/datasets/" + datasetId + "/ops")
                .then().statusCode(200)
                .body("newRevision", equalTo(1));

        // Retry with same opId should be rejected with 409 (duplicate opId per dataset)
        given().contentType("application/json").body(Map.of(
                        "baseRevision", 1,
                        "operations", java.util.List.of(
                                Map.of(
                                        "opId", "dup-1",
                                        "type", "JSON_PATCH",
                                        "payload", java.util.List.of()
                                )
                        )
                ))
                .when().post("/datasets/" + datasetId + "/ops")
                .then().statusCode(409)
                .body("opId", equalTo("dup-1"))
                .body("existingRevision", equalTo(1));
    }
}
