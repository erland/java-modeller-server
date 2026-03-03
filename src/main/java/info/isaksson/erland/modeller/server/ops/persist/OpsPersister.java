package info.isaksson.erland.modeller.server.ops.persist;

import com.fasterxml.jackson.databind.JsonNode;
import info.isaksson.erland.modeller.server.api.dto.OperationRequest;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetOperationRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotHistoryRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotLatestRepository;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetOperationEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotHistoryEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotLatestEntity;
import info.isaksson.erland.modeller.server.security.PrincipalInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates all DB writes performed as part of appending operations to a dataset:
 * - dataset metadata revision + timestamps
 * - operation rows
 * - latest snapshot row
 * - history snapshot row + pruning
 */
@ApplicationScoped
public class OpsPersister {

    private final DatasetOperationRepository operationRepository;
    private final DatasetSnapshotLatestRepository snapshotLatestRepository;
    private final DatasetSnapshotHistoryRepository historyRepository;

    @Inject
    public OpsPersister(
            DatasetOperationRepository operationRepository,
            DatasetSnapshotLatestRepository snapshotLatestRepository,
            DatasetSnapshotHistoryRepository historyRepository
    ) {
        this.operationRepository = Objects.requireNonNull(operationRepository);
        this.snapshotLatestRepository = Objects.requireNonNull(snapshotLatestRepository);
        this.historyRepository = Objects.requireNonNull(historyRepository);
    }

    public record AppendRequest(
            UUID datasetId,
            DatasetEntity datasetEntity,
            List<OperationRequest> ops,
            long previousRevision,
            long newRevision,
            OffsetDateTime now,
            PrincipalInfo principal,
            DatasetSnapshotLatestEntity latestEntityOrNull,
            String latestPayloadJson,
            String latestEtag,
            JsonNode historySnapshot,
            boolean forcedLeaseOverride,
            String forcedLeaseHolder,
            int historyKeep,
            int historyMaxAgeDays
    ) { }

    public void persistAppend(AppendRequest req) {
        Objects.requireNonNull(req, "req");
        Objects.requireNonNull(req.datasetId(), "datasetId");
        Objects.requireNonNull(req.datasetEntity(), "datasetEntity");
        Objects.requireNonNull(req.ops(), "ops");
        Objects.requireNonNull(req.now(), "now");
        Objects.requireNonNull(req.principal(), "principal");
        Objects.requireNonNull(req.latestPayloadJson(), "latestPayloadJson");
        Objects.requireNonNull(req.latestEtag(), "latestEtag");
        Objects.requireNonNull(req.historySnapshot(), "historySnapshot");

        OffsetDateTime now = req.now().withOffsetSameInstant(ZoneOffset.UTC);

        // Update dataset metadata (managed entity, flushed with Tx)
        req.datasetEntity().updatedAt = now;
        req.datasetEntity().currentRevision = req.newRevision();
        req.datasetEntity().updatedBy = req.principal().subject();

        // Persist operation rows
        for (int i = 0; i < req.ops().size(); i++) {
            OperationRequest op = req.ops().get(i);
            DatasetOperationEntity operationEntity = new DatasetOperationEntity();
            operationEntity.datasetId = req.datasetId();
            operationEntity.revision = req.previousRevision() + i + 1;
            operationEntity.opId = op.opId;
            operationEntity.opType = op.type;
            operationEntity.payloadJson = op.payload == null ? null : op.payload.toString();
            operationEntity.createdAt = now;
            operationEntity.createdBy = req.principal().subject();

            operationRepository.persist(operationEntity);
        }

        // Upsert latest snapshot row
        DatasetSnapshotLatestEntity latestEntity = req.latestEntityOrNull();
        boolean isNewLatest = false;
        if (latestEntity == null) {
            isNewLatest = true;
            latestEntity = new DatasetSnapshotLatestEntity();
            latestEntity.datasetId = req.datasetId();
        }
        latestEntity.revision = req.newRevision();
        latestEntity.updatedAt = now;
        latestEntity.etag = req.latestEtag();
        latestEntity.payloadJson = req.latestPayloadJson();

        if (isNewLatest) {
            snapshotLatestRepository.persist(latestEntity);
        }

        // Persist history snapshot row (one per revision)
        String historySnapshotJson = req.historySnapshot().toString();
        DatasetSnapshotHistoryEntity snapshotHistoryEntity = new DatasetSnapshotHistoryEntity();
        snapshotHistoryEntity.datasetId = req.datasetId();
        snapshotHistoryEntity.revision = req.newRevision();
        snapshotHistoryEntity.savedAt = now;
        snapshotHistoryEntity.savedBy = req.principal().subject();
        snapshotHistoryEntity.etag = req.latestEtag();
        snapshotHistoryEntity.payloadJson = historySnapshotJson;
        snapshotHistoryEntity.schemaVersion = extractSchemaVersion(req.historySnapshot());
        snapshotHistoryEntity.payloadBytes = historySnapshotJson.getBytes(StandardCharsets.UTF_8).length;
        snapshotHistoryEntity.savedAction = req.forcedLeaseOverride() ? "APPEND_OPS_FORCE_LEASE" : "APPEND_OPS";
        snapshotHistoryEntity.savedMessage = req.forcedLeaseOverride()
                ? ("lease overridden from " + req.forcedLeaseHolder())
                : null;

        historyRepository.persist(snapshotHistoryEntity);

        // Prune history to keep table bounded
        if (req.historyKeep() > 0) {
            historyRepository.pruneKeepLatest(req.datasetId(), req.historyKeep());
        }
        if (req.historyMaxAgeDays() > 0) {
            historyRepository.pruneByMaxAgeDays(req.datasetId(), req.historyMaxAgeDays());
        }
    }

    private static Integer extractSchemaVersion(JsonNode node) {
        JsonNode schemaVersion = node.get("schemaVersion");
        if (schemaVersion == null || schemaVersion.isNull()) {
            return null;
        }
        if (schemaVersion.isInt()) {
            return schemaVersion.asInt();
        }
        if (schemaVersion.isNumber()) {
            return schemaVersion.intValue();
        }
        if (schemaVersion.isTextual()) {
            try {
                return Integer.parseInt(schemaVersion.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
