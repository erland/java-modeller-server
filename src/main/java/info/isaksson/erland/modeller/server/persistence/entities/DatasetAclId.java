package info.isaksson.erland.modeller.server.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DatasetAclId implements Serializable {

    @Column(name = "dataset_id", nullable = false)
    public UUID datasetId;

    @Column(name = "user_sub", nullable = false)
    public String userSub;

    public DatasetAclId() {}

    public DatasetAclId(UUID datasetId, String userSub) {
        this.datasetId = datasetId;
        this.userSub = userSub;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DatasetAclId that)) return false;
        return Objects.equals(datasetId, that.datasetId) && Objects.equals(userSub, that.userSub);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datasetId, userSub);
    }
}
