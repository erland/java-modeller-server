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
public class DatasetHeadResourceTest {

    @Inject TestDataFactory data;

    @Test
    @TestSecurity(user = "alice")
    public void head_returns_revision_etag_and_updated_fields() throws Exception {
        UUID datasetId = data.createDatasetVisibleTo("alice");
        data.createSnapshot(datasetId, 1, 12);

        given()
                .when()
                .get("/datasets/" + datasetId + "/head")
                .then()
                .statusCode(200)
                .header("ETag", "\"1\"")
                .body("datasetId", equalTo(datasetId.toString()))
                .body("currentRevision", equalTo(1))
                .body("currentEtag", equalTo("1"))
                .body("updatedAt", notNullValue())
                .body("updatedBy", equalTo("alice"))
                .body("validationPolicy", equalTo("none"))
                .body("leaseActive", equalTo(false));
    }

    @Test
    @TestSecurity(user = "alice")
    public void head_includes_active_lease_without_token() throws Exception {
        UUID datasetId = data.createDatasetVisibleTo("alice");
        data.createSnapshot(datasetId, 1, 12);
        data.createLease(datasetId, "bob", 120);

        given()
                .when()
                .get("/datasets/" + datasetId + "/head")
                .then()
                .statusCode(200)
                .body("leaseActive", equalTo(true))
                .body("leaseHolderSub", equalTo("bob"))
                .body("leaseExpiresAt", notNullValue());
    }
}
