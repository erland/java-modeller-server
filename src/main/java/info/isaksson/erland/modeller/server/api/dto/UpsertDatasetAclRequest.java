package info.isaksson.erland.modeller.server.api.dto;

public class UpsertDatasetAclRequest {
    public String role;

    public UpsertDatasetAclRequest() {}

    public UpsertDatasetAclRequest(String role) {
        this.role = role;
    }
}
