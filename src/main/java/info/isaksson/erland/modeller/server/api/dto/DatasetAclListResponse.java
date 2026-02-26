package info.isaksson.erland.modeller.server.api.dto;

import java.util.List;
import java.util.UUID;

public class DatasetAclListResponse {
    public UUID datasetId;
    public List<DatasetAclEntryResponse> items;

    public DatasetAclListResponse() {}

    public DatasetAclListResponse(UUID datasetId, List<DatasetAclEntryResponse> items) {
        this.datasetId = datasetId;
        this.items = items;
    }
}
