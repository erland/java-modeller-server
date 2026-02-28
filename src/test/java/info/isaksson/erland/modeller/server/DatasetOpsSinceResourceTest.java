package info.isaksson.erland.modeller.server;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
public class DatasetOpsSinceResourceTest {

    @Inject
    TestDataFactory data;

    @Test
    @TestSecurity(user = "alice")
    public void ops_since_returns_ordered_operations_after_revision() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        Map<String, Object> request = Map.of(
                "baseRevision", 0,
                "operations", java.util.List.of(
                        Map.of(
                                "opId", "op-1",
                                "type", "JSON_PATCH",
                                "payload", java.util.List.of(Map.of("op", "add", "path", "/model/a", "value", 1))
                        ),
                        Map.of(
                                "opId", "op-2",
                                "type", "JSON_PATCH",
                                "payload", java.util.List.of(Map.of("op", "add", "path", "/model/b", "value", 2))
                        ),
                        Map.of(
                                "opId", "op-3",
                                "type", "JSON_PATCH",
                                "payload", java.util.List.of(Map.of("op", "add", "path", "/model/c", "value", 3))
                        )
                )
        );

        given()
                .contentType("application/json")
                .body(request)
                .when().post("/datasets/" + datasetId + "/ops")
                .then()
                .statusCode(200)
                .body("newRevision", equalTo(3));

        given()
                .when().get("/datasets/" + datasetId + "/ops?fromRevision=0&limit=10")
                .then()
                .statusCode(200)
                .body("fromRevision", equalTo(0))
                .body("toRevision", equalTo(3))
                .body("operations", hasSize(3))
                .body("operations[0].revision", equalTo(1))
                .body("operations[0].opId", equalTo("op-1"))
                .body("operations[1].revision", equalTo(2))
                .body("operations[1].opId", equalTo("op-2"))
                .body("operations[2].revision", equalTo(3))
                .body("operations[2].opId", equalTo("op-3"));

        given()
                .when().get("/datasets/" + datasetId + "/ops?fromRevision=1&limit=10")
                .then()
                .statusCode(200)
                .body("fromRevision", equalTo(1))
                .body("toRevision", equalTo(3))
                .body("operations", hasSize(2))
                .body("operations[0].revision", equalTo(2))
                .body("operations[1].revision", equalTo(3));
    }

    @Test
    @TestSecurity(user = "alice")
    public void ops_since_respects_limit() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        Map<String, Object> request = Map.of(
                "baseRevision", 0,
                "operations", java.util.List.of(
                        Map.of("opId", "op-1", "type", "JSON_PATCH", "payload", java.util.List.of(Map.of("op", "add", "path", "/model/a", "value", 1))),
                        Map.of("opId", "op-2", "type", "JSON_PATCH", "payload", java.util.List.of(Map.of("op", "add", "path", "/model/b", "value", 2)))
                )
        );

        given()
                .contentType("application/json")
                .body(request)
                .when().post("/datasets/" + datasetId + "/ops")
                .then()
                .statusCode(200)
                .body("newRevision", equalTo(2));

        given()
                .when().get("/datasets/" + datasetId + "/ops?fromRevision=0&limit=1")
                .then()
                .statusCode(200)
                .body("operations", hasSize(1))
                .body("toRevision", equalTo(1));
    }
}
