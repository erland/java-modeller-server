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
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
public class DatasetSnapshotHistoryTest {

    @Inject TestDataFactory data;
    @Inject ObjectMapper objectMapper;

    @Test
    @TestSecurity(user = "alice")
    public void list_history_returns_latest_n_in_desc_order() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        // Write 3 snapshots via API (If-Match chain)
        ObjectNode payload1 = objectMapper.createObjectNode()
                .put("schemaVersion", 1)
                .set("model", objectMapper.createObjectNode());
        given()
                .header("If-Match", "\"0\"")
                .contentType("application/json")
                .body(payload1.toString())
                .when().put("/datasets/" + datasetId + "/snapshot")
                .then().statusCode(200)
                .header("ETag", "\"1\"");

        ObjectNode payload2 = objectMapper.createObjectNode()
                .put("schemaVersion", 2)
                .set("model", objectMapper.createObjectNode());
        given()
                .header("If-Match", "\"1\"")
                .contentType("application/json")
                .body(payload2.toString())
                .when().put("/datasets/" + datasetId + "/snapshot")
                .then().statusCode(200)
                .header("ETag", "\"2\"");

        ObjectNode payload3 = objectMapper.createObjectNode()
                .put("schemaVersion", 3)
                .set("model", objectMapper.createObjectNode());
        given()
                .header("If-Match", "\"2\"")
                .contentType("application/json")
                .body(payload3.toString())
                .when().put("/datasets/" + datasetId + "/snapshot")
                .then().statusCode(200)
                .header("ETag", "\"3\"");

        // In %test profile we set modeller.snapshot.history.keep=2
        given()
                .when().get("/datasets/" + datasetId + "/snapshots?limit=10")
                .then()
                .statusCode(200)
                .body("datasetId", equalTo(datasetId.toString()))
                .body("items", hasSize(2))
                .body("items[0].revision", equalTo(3))
                .body("items[1].revision", equalTo(2));
    }
}
