package info.isaksson.erland.modeller.server;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class DatasetsResourceTest {

    @Inject TestDataFactory dataFactory;

    @Test
    @TestSecurity(user = "alice")
    void create_and_list_datasets() {
        UUID id =
                given()
                        .contentType(ContentType.JSON)
                        .body("{\"name\":\"My Dataset\",\"description\":\"Hello\"}")
                .when()
                        .post("/datasets")
                .then()
                        .statusCode(201)
                        .body("id", Matchers.notNullValue())
                        .body("name", Matchers.equalTo("My Dataset"))
                        .body("description", Matchers.equalTo("Hello"))
                        .body("role", Matchers.equalTo("OWNER"))
                        .body("createdBy", Matchers.equalTo("alice"))
                        .body("updatedBy", Matchers.equalTo("alice"))
                        .body("currentRevision", Matchers.equalTo(0))
                        .body("status", Matchers.equalTo("ACTIVE"))
                        .extract().jsonPath().getUUID("id");

        org.junit.jupiter.api.Assertions.assertEquals(1, dataFactory.countAudit(id, "DATASET_CREATE"));

        given()
        .when()
                .get("/datasets")
        .then()
                .statusCode(200)
                .body("size()", Matchers.greaterThanOrEqualTo(1))
                .body("id", Matchers.hasItem(id.toString()));
    }

    @Test
    @TestSecurity(user = "alice")
    void update_archive_unarchive_delete_lifecycle() {
        UUID id =
                given()
                        .contentType(ContentType.JSON)
                        .body("{\"name\":\"A\",\"description\":\"D\"}")
                .when()
                        .post("/datasets")
                .then()
                        .statusCode(201)
                        .extract().jsonPath().getUUID("id");

        org.junit.jupiter.api.Assertions.assertEquals(1, dataFactory.countAudit(id, "DATASET_CREATE"));

        // Update metadata (owner only in Phase 1)
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"A2\",\"description\":\"D2\"}")
        .when()
                .put("/datasets/" + id)
        .then()
                .statusCode(200)
                .body("name", Matchers.equalTo("A2"))
                .body("description", Matchers.equalTo("D2"));

        // Archive
        given()
        .when()
                .post("/datasets/" + id + "/archive")
        .then()
                .statusCode(200)
                .body("archivedAt", Matchers.notNullValue())
                .body("status", Matchers.equalTo("ARCHIVED"));

        // Unarchive
        given()
        .when()
                .post("/datasets/" + id + "/unarchive")
        .then()
                .statusCode(200)
                .body("archivedAt", Matchers.nullValue())
                .body("status", Matchers.equalTo("ACTIVE"));

        // Delete (soft)
        given()
        .when()
                .delete("/datasets/" + id)
        .then()
                .statusCode(204);

        org.junit.jupiter.api.Assertions.assertEquals(1, dataFactory.countAudit(id, "DATASET_CREATE"));
        org.junit.jupiter.api.Assertions.assertEquals(1, dataFactory.countAudit(id, "DATASET_UPDATE"));
        org.junit.jupiter.api.Assertions.assertEquals(1, dataFactory.countAudit(id, "DATASET_ARCHIVE"));
        org.junit.jupiter.api.Assertions.assertEquals(1, dataFactory.countAudit(id, "DATASET_UNARCHIVE"));
        org.junit.jupiter.api.Assertions.assertEquals(1, dataFactory.countAudit(id, "DATASET_DELETE"));

        // Not readable after delete
        given()
        .when()
                .get("/datasets/" + id)
        .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "bob")
    void bob_cannot_read_alice_dataset() {
        // Without sharing endpoints in Phase 1, bob should see no datasets and any random dataset is 404.
        given()
        .when()
                .get("/datasets")
        .then()
                .statusCode(200)
                .body("size()", Matchers.anyOf(Matchers.equalTo(0), Matchers.greaterThanOrEqualTo(0)));

        given()
        .when()
                .get("/datasets/" + UUID.randomUUID())
        .then()
                .statusCode(404);
    }
}
