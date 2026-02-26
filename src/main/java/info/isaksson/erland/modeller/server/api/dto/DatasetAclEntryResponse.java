package info.isaksson.erland.modeller.server.api.dto;

import java.time.OffsetDateTime;

public class DatasetAclEntryResponse {
    public String userSub;
    public String role;
    public OffsetDateTime createdAt;

    public DatasetAclEntryResponse() {}

    public DatasetAclEntryResponse(String userSub, String role, OffsetDateTime createdAt) {
        this.userSub = userSub;
        this.role = role;
        this.createdAt = createdAt;
    }
}
