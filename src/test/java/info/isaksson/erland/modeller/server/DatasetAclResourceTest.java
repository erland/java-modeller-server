package info.isaksson.erland.modeller.server;

import info.isaksson.erland.modeller.server.api.dto.UpsertDatasetAclRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class DatasetAclResourceTest {

    @Inject TestDataFactory data;

    @Test
    @TestSecurity(user = "alice", roles = {"user"})
    void owner_can_list_and_grant_and_revoke_with_last_owner_protection() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        // list includes self
        given()
                .when().get("/datasets/" + datasetId + "/acl")
                .then()
                .statusCode(200)
                .body("datasetId", equalTo(datasetId.toString()))
                .body("items.size()", greaterThanOrEqualTo(1))
                .body("items.userSub", hasItem("alice"))
                .body("items.role", hasItem("OWNER"));

        // grant bob EDITOR
        given()
                .contentType("application/json")
                .body(new UpsertDatasetAclRequest("EDITOR"))
                .when().put("/datasets/" + datasetId + "/acl/bob")
                .then()
                .statusCode(200)
                .body("userSub", equalTo("bob"))
                .body("role", equalTo("EDITOR"));

        // revoke bob
        given()
                .when().delete("/datasets/" + datasetId + "/acl/bob")
                .then()
                .statusCode(204);

        org.junit.jupiter.api.Assertions.assertEquals(1, data.countAudit(datasetId, "ACL_GRANT"));
        org.junit.jupiter.api.Assertions.assertEquals(1, data.countAudit(datasetId, "ACL_REVOKE"));

        // cannot revoke last owner
        given()
                .when().delete("/datasets/" + datasetId + "/acl/alice")
                .then()
                .statusCode(409)
                .body("code", equalTo("LAST_OWNER"));
    }

    @Test
    @TestSecurity(user = "bob", roles = {"user"})
    void non_member_gets_404_to_avoid_leakage() {
        UUID datasetId = data.createDatasetVisibleTo("alice");

        given()
                .when().get("/datasets/" + datasetId + "/acl")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "bob", roles = {"user"})
    void editor_cannot_modify_acl() {
        UUID datasetId = data.createDatasetVisibleTo("alice");
        data.grantAcl(datasetId, "bob", "EDITOR");

        // visible but insufficient => 403
        given()
                .contentType("application/json")
                .body(new UpsertDatasetAclRequest("VIEWER"))
                .when().put("/datasets/" + datasetId + "/acl/charlie")
                .then()
                .statusCode(403);
    }
}
