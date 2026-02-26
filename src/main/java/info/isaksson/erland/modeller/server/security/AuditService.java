package info.isaksson.erland.modeller.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAuditEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAuditRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Simple audit writer for Phase A.
 * Stores a row in dataset_audit with an action code and optional JSON details.
 */
@ApplicationScoped
public class AuditService {

    @Inject DatasetAuditRepository auditRepository;
    @Inject ObjectMapper objectMapper;

    public void record(UUID datasetId, String actorSub, String action, ObjectNode details) {
        DatasetAuditEntity e = new DatasetAuditEntity();
        e.datasetId = datasetId;
        e.actorSub = actorSub;
        e.action = action;
        e.createdAt = OffsetDateTime.now();
        e.detailsJson = details == null ? null : details.toString();
        auditRepository.persist(e);
    }

    public ObjectNode details() {
        return objectMapper.createObjectNode();
    }
}
