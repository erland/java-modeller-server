package info.isaksson.erland.modeller.server.persistence.entities;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite id for DatasetOperationEntity: (datasetId, revision). */
public class DatasetOperationId implements Serializable {
    public UUID datasetId;
    public long revision;

    public DatasetOperationId() {}

    public DatasetOperationId(UUID datasetId, long revision) {
        this.datasetId = datasetId;
        this.revision = revision;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatasetOperationId that = (DatasetOperationId) o;
        return revision == that.revision && Objects.equals(datasetId, that.datasetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, revision);
    }
}
