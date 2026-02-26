package info.isaksson.erland.modeller.server;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
public class DatasetSnapshotResourceTest {

    @Inject
    TestDataFactory data;

    @Test
    @TestSecurity(user = "alice")
    public void get_latest_returns_empty_snapshot_when_missing() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        given()
                .when().get("/datasets/" + datasetId + "/snapshot")
                .then()
                .statusCode(200)
                .header("ETag", "\"0\"")
                .body("datasetId", equalTo(datasetId.toString()))
                .body("revision", equalTo(0))
                .body("payload", notNullValue());
    }

    @Test
    @TestSecurity(user = "alice")
    public void get_latest_returns_payload_and_etag_when_present() throws Exception {
        UUID datasetId = data.createDatasetVisibleTo("alice");
        data.createSnapshot(datasetId, 12, 12);

        given()
                .when().get("/datasets/" + datasetId + "/snapshot")
                .then()
                .statusCode(200)
                .header("ETag", "\"12\"")
                .body("datasetId", equalTo(datasetId.toString()))
                .body("revision", equalTo(12))
                .body("schemaVersion", equalTo(12));
    }
}
