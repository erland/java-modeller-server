package info.isaksson.erland.modeller.server.api;

import info.isaksson.erland.modeller.server.api.dto.DatasetResponse;
import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;

public final class DatasetMapper {

    private DatasetMapper() {}

    public static DatasetResponse toResponse(DatasetEntity e, Role role) {
        DatasetResponse r = new DatasetResponse();
        r.id = e.id;
        r.name = e.name;
        r.description = e.description;
        r.createdAt = e.createdAt;
        r.updatedAt = e.updatedAt;
        r.archivedAt = e.archivedAt;
        r.deletedAt = e.deletedAt;
        r.createdBy = e.createdBy;
        r.updatedBy = e.updatedBy;
        r.currentRevision = e.currentRevision;
        r.status = deriveStatus(e);
        r.role = role != null ? role.name() : null;
        return r;
    }



    private static String deriveStatus(DatasetEntity e) {
        if (e == null) {
            return null;
        }
        if (e.deletedAt != null) {
            return "DELETED";
        }
        if (e.archivedAt != null) {
            return "ARCHIVED";
        }
        return "ACTIVE";
    }
}
