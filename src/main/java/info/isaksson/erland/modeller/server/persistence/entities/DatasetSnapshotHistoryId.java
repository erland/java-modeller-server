package info.isaksson.erland.modeller.server.persistence.entities;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class DatasetSnapshotHistoryId implements Serializable {

    public UUID datasetId;
    public long revision;

    public DatasetSnapshotHistoryId() {}

    public DatasetSnapshotHistoryId(UUID datasetId, long revision) {
        this.datasetId = datasetId;
        this.revision = revision;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DatasetSnapshotHistoryId that)) return false;
        return revision == that.revision && Objects.equals(datasetId, that.datasetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, revision);
    }
}
