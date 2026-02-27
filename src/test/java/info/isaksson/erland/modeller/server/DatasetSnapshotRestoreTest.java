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
public class DatasetSnapshotRestoreTest {

    @Inject TestDataFactory data;
    @Inject ObjectMapper objectMapper;

    @Test
    @TestSecurity(user = "alice")
    public void restore_snapshot_revision_creates_new_revision_with_restored_payload() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        ObjectNode payloadA = objectMapper.createObjectNode()
                .put("schemaVersion", 1)
                .set("model", objectMapper.createObjectNode().put("name", "A"));

        ObjectNode payloadB = objectMapper.createObjectNode()
                .put("schemaVersion", 1)
                .set("model", objectMapper.createObjectNode().put("name", "B"));

        // Write rev 1
        given()
                .header("If-Match", "\"0\"")
                .contentType("application/json")
                .body(payloadA.toString())
                .when().put("/datasets/" + datasetId + "/snapshot")
                .then()
                .statusCode(200)
                .header("ETag", "\"1\"")
                .body("revision", equalTo(1));

        // Write rev 2
        given()
                .header("If-Match", "\"1\"")
                .contentType("application/json")
                .body(payloadB.toString())
                .when().put("/datasets/" + datasetId + "/snapshot")
                .then()
                .statusCode(200)
                .header("ETag", "\"2\"")
                .body("revision", equalTo(2));

        // Restore revision 1 -> creates rev 3 with payload A
        given()
                .header("If-Match", "\"2\"")
                .contentType("application/json")
                .when().post("/datasets/" + datasetId + "/snapshots/1/restore")
                .then()
                .statusCode(200)
                .header("ETag", "\"3\"")
                .body("revision", equalTo(3))
                .body("payload.model.name", equalTo("A"));
    }
}
