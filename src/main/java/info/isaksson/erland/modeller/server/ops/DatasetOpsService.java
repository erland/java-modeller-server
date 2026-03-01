package info.isaksson.erland.modeller.server.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.isaksson.erland.modeller.server.api.dto.AppendOperationsRequest;
import info.isaksson.erland.modeller.server.api.dto.AppendOperationsResponse;
import info.isaksson.erland.modeller.server.api.dto.OperationRequest;
import info.isaksson.erland.modeller.server.api.dto.OperationEvent;
import info.isaksson.erland.modeller.server.api.dto.OpsSinceResponse;
import info.isaksson.erland.modeller.server.api.dto.DuplicateOpIdResponse;
import info.isaksson.erland.modeller.server.api.dto.RevisionConflictResponse;
import info.isaksson.erland.modeller.server.api.dto.ValidationErrorDto;
import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.domain.ValidationPolicy;
import info.isaksson.erland.modeller.server.api.error.ApiError;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetOperationEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotHistoryEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotLatestEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetLeaseEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAclRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetLeaseRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetOperationRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotHistoryRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotLatestRepository;
import info.isaksson.erland.modeller.server.security.DatasetAuthorizationService;
import info.isaksson.erland.modeller.server.security.PrincipalInfo;
import info.isaksson.erland.modeller.server.security.AuditService;
import info.isaksson.erland.modeller.server.api.dto.LeaseConflictResponse;
import info.isaksson.erland.modeller.server.validation.SnapshotValidationService;
import info.isaksson.erland.modeller.server.validation.ValidationResult;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.MDC;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class DatasetOpsService {

    private final DatasetRepository datasetRepository;
    private final DatasetAclRepository aclRepository;
    private final DatasetSnapshotLatestRepository snapshotLatestRepository;
    private final DatasetSnapshotHistoryRepository historyRepository;
    private final DatasetOperationRepository operationRepository;
    private final DatasetLeaseRepository leaseRepository;
    private final DatasetAuthorizationService authz;
    private final OperationsApplier applier;
    private final SnapshotValidationService validationService;
    private final ObjectMapper mapper;
    private final EntityManager em;
    private final TransactionSynchronizationRegistry txSync;
    private final DatasetOpsSseHub sseHub;
    private final AuditService auditService;

    @ConfigProperty(name = "modeller.snapshot.history.keep", defaultValue = "20")
    int snapshotHistoryKeep;

    @ConfigProperty(name = "modeller.snapshot.history.maxAgeDays", defaultValue = "0")
    int snapshotHistoryMaxAgeDays;

    @Inject
    public DatasetOpsService(DatasetRepository datasetRepository,
                             DatasetAclRepository aclRepository,
                             DatasetSnapshotLatestRepository snapshotLatestRepository,
                             DatasetSnapshotHistoryRepository historyRepository,
                             DatasetOperationRepository operationRepository,
                             DatasetLeaseRepository leaseRepository,
                             DatasetAuthorizationService authz,
                             OperationsApplier applier,
                             SnapshotValidationService validationService,
                             ObjectMapper mapper,
                             EntityManager em,
                             TransactionSynchronizationRegistry txSync,
                             DatasetOpsSseHub sseHub,
                             AuditService auditService) {
        this.datasetRepository = datasetRepository;
        this.aclRepository = aclRepository;
        this.snapshotLatestRepository = snapshotLatestRepository;
        this.historyRepository = historyRepository;
        this.operationRepository = operationRepository;
        this.leaseRepository = leaseRepository;
        this.authz = authz;
        this.applier = applier;
        this.validationService = validationService;
        this.mapper = mapper;
        this.em = em;
        this.txSync = txSync;
        this.sseHub = sseHub;
        this.auditService = auditService;
    }

    /**
     * Strategy A: materialize a full snapshot on every append.
     */
    @Transactional
    public Response appendOps(UUID datasetId, AppendOperationsRequest request, String leaseToken, boolean force) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.baseRevision == null) {
            throw new BadRequestException("baseRevision is required");
        }
        if (request.operations == null || request.operations.isEmpty()) {
            throw new BadRequestException("operations must not be empty");
        }

        // Validate opIds/types early (and ensure request-local uniqueness) to make retries deterministic.
        Set<String> seenOpIds = new HashSet<>();
        for (OperationRequest op : request.operations) {
            if (op == null) {
                throw new BadRequestException("operations must not contain null entries");
            }
            if (op.opId == null || op.opId.trim().isEmpty()) {
                throw new BadRequestException("operation.opId is required");
            }
            if (op.type == null || op.type.trim().isEmpty()) {
                throw new BadRequestException("operation.type is required");
            }
            String normalized = op.opId.trim();
            if (!seenOpIds.add(normalized)) {
                throw new BadRequestException("Duplicate opId in request: " + normalized);
            }
        }

        PrincipalInfo principal = authz.currentPrincipal();

        // Visibility check (avoid leaking dataset existence)
        Role role = aclRepository.findRole(datasetId, principal.subject())
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
        if (!role.atLeast(Role.EDITOR)) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        // Phase 3 lease policy: operation appends must respect active leases the same way snapshot PUT does.
        // - If an active lease exists and is held by someone else -> 409 LeaseConflictResponse (unless force+OWNER)
        // - If an active lease exists and is held by caller -> require matching X-Lease-Token
        OffsetDateTime nowLeaseCheck = OffsetDateTime.now();
        var activeLeaseOpt = leaseRepository.findActive(datasetId, nowLeaseCheck);
        boolean forcedLeaseOverride = false;
        String forcedLeaseHolder = null;
        if (activeLeaseOpt.isPresent()) {
            DatasetLeaseEntity activeLease = activeLeaseOpt.get();
            if (!principal.subject().equals(activeLease.holderSub)) {
                if (force) {
                    if (!role.atLeast(Role.OWNER)) {
                        throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
                    }
                    forcedLeaseOverride = true;
                    forcedLeaseHolder = activeLease.holderSub;
                } else {
                    return Response.status(Response.Status.CONFLICT)
                            .entity(new LeaseConflictResponse(datasetId, activeLease.holderSub, activeLease.expiresAt))
                            .build();
                }
            } else {
                // Caller is lease holder: token required.
                if (leaseToken == null || leaseToken.isBlank() || !leaseToken.trim().equals(activeLease.leaseToken)) {
                    String mdcRequestId = (String) MDC.get("mdcRequestId");
                    ApiError err = new ApiError(
                            OffsetDateTime.now(),
                            428,
                            "LEASE_TOKEN_REQUIRED",
                            "Missing or invalid X-Lease-Token for active lease",
                            null,
                            mdcRequestId
                    );
                    return Response.status(428).entity(err).build();
                }
            }
        }

        // Concurrency safety: lock dataset row for the duration of revision assignment
        em.lock(ds, LockModeType.PESSIMISTIC_WRITE);

        // Refresh the revision after acquiring the lock to be sure we compare against the latest committed value.
        em.refresh(ds);

        long currentRevision = ds.currentRevision;
        long previousRevision = currentRevision;
        if (request.baseRevision != currentRevision) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new RevisionConflictResponse(datasetId, request.baseRevision, currentRevision))
                    .build();
        }

        DatasetSnapshotLatestEntity latest = snapshotLatestRepository.findById(datasetId);
        JsonNode baseSnapshot = null;
        if (latest != null) {
            try {
                baseSnapshot = mapper.readTree(latest.payloadJson);
            } catch (Exception e) {
                throw new jakarta.ws.rs.InternalServerErrorException("Stored snapshot payload is invalid JSON", e);
            }
        } else {
            // default empty snapshot
            ObjectNode payload = mapper.createObjectNode();
            payload.set("model", mapper.createObjectNode());
            baseSnapshot = payload;
        }

        List<OperationRequest> ops = request.operations;

        // Idempotency / duplicate guard: opId must be unique per dataset.
        // If a client retries the same request, the server can safely detect duplicates.
        for (OperationRequest op : ops) {
            String opId = op.opId.trim();
            var existing = operationRepository.findByOpId(datasetId, opId);
            if (existing.isPresent()) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(new DuplicateOpIdResponse(datasetId, opId, existing.get().revision))
                        .build();
            }
        }

        JsonNode nextSnapshot = applier.applyAll(baseSnapshot, ops);

        // Validate final materialized snapshot according to dataset policy (reuse Phase 2 behavior)
        ValidationPolicy policy = ds.validationPolicy == null ? ValidationPolicy.NONE : ds.validationPolicy;
        String payloadJson = nextSnapshot.toString();
        ValidationResult vr = validationService.validate(nextSnapshot, payloadJson, policy);
        if (vr.hasErrors()) {
            String mdcRequestId = (String) MDC.get("mdcRequestId");
            ApiError err = new ApiError(
                    OffsetDateTime.now(),
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    "VALIDATION_FAILED",
                    "Snapshot validation failed",
                    null,
                    mdcRequestId
            ).withValidationErrors(
                    vr.errors().stream().map(ValidationErrorDto::fromIssue).collect(Collectors.toList())
            );

            return Response.status(Response.Status.BAD_REQUEST).entity(err).build();
        }

        long newRevision = currentRevision + ops.size();
        OffsetDateTime now = OffsetDateTime.now();

        // Persist operation log
        for (int i = 0; i < ops.size(); i++) {
            OperationRequest op = ops.get(i);
            DatasetOperationEntity e = new DatasetOperationEntity();
            e.datasetId = datasetId;
            e.revision = currentRevision + 1L + i;
            e.opId = op.opId.trim();
            e.opType = op.type.trim();
            e.payloadJson = op.payload == null ? "null" : op.payload.toString();
            e.createdAt = now;
            e.createdBy = principal.subject();
            operationRepository.persist(e);
        }

        // Persist snapshot_latest
        DatasetSnapshotLatestEntity newLatest = latest != null ? latest : new DatasetSnapshotLatestEntity();
        newLatest.datasetId = datasetId;
        newLatest.revision = newRevision;
        newLatest.etag = String.valueOf(newRevision);
        newLatest.payloadJson = payloadJson;
        newLatest.updatedAt = now;
        if (latest == null) {
            snapshotLatestRepository.persist(newLatest);
        }

        // Persist snapshot_history entry for the new revision
        DatasetSnapshotHistoryEntity h = new DatasetSnapshotHistoryEntity();
        h.datasetId = datasetId;
        h.revision = newRevision;
        h.etag = String.valueOf(newRevision);
        h.payloadJson = payloadJson;
        h.schemaVersion = extractSchemaVersion(nextSnapshot);
        h.savedAt = now;
        h.savedBy = principal.subject();
        h.payloadBytes = h.payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        h.savedAction = "OPS_APPEND";
        h.savedMessage = null;
        historyRepository.persist(h);

        // Prune history (same knobs as snapshot PUT)
        historyRepository.pruneKeepLatest(datasetId, snapshotHistoryKeep);
        historyRepository.pruneByMaxAgeDays(datasetId, snapshotHistoryMaxAgeDays);

        // Update dataset revision and metadata
        ds.currentRevision = newRevision;
        ds.updatedAt = now;
        ds.updatedBy = principal.subject();

        // Audit: record append (write-side) as a single entry with summary details.
        // Best-effort: a failure to write audit must not fail the append.
        try {
            ObjectNode details = auditService.details()
                    .put("previousRevision", currentRevision)
                    .put("newRevision", newRevision)
                    .put("opCount", ops.size());
            Object mdcRequestId = MDC.get("requestId");
            if (mdcRequestId != null) {
                details.put("requestId", String.valueOf(mdcRequestId));
            }
            if (!ops.isEmpty()) {
                details.put("firstOpId", ops.get(0).opId);
                details.put("lastOpId", ops.get(ops.size() - 1).opId);
            }
            if (forcedLeaseOverride) {
                details.put("forcedLeaseOverride", true);
                if (forcedLeaseHolder != null) {
                    details.put("forcedLeaseHolderSub", forcedLeaseHolder);
                }
            }
            auditService.record(datasetId, principal.subject(), "OPS_APPEND", details);
        } catch (Exception ignored) {
            // Best-effort only
        }

        List<OperationEvent> accepted = java.util.stream.IntStream.range(0, ops.size())
                .mapToObj(i -> {
                    OperationRequest o = ops.get(i);
                    long rev = currentRevision + 1L + i;
                    return new OperationEvent(
                            datasetId,
                            rev,
                            o.opId,
                            o.type,
                            o.payload,
                            now,
                            principal.subject()
                    );
                })
                .collect(Collectors.toList());

        AppendOperationsResponse resp = new AppendOperationsResponse(newRevision, accepted);

        // Publish SSE events only after successful commit.
        // (If the transaction rolls back, subscribers should not observe any of these operations.)
        // Ordering guarantee: publish in revision order per dataset (the hub enforces ordering).
        registerAfterCommit(() -> {
            sseHub.ensureBaseline(datasetId, previousRevision);
            accepted.forEach(ev -> sseHub.publish(datasetId, ev));
        });

        return Response.ok(resp).build();
    }

    private void registerAfterCommit(Runnable r) {
        txSync.registerInterposedSynchronization(new jakarta.transaction.Synchronization() {
            @Override
            public void beforeCompletion() {
                // no-op
            }

            @Override
            public void afterCompletion(int status) {
                if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                    try {
                        r.run();
                    } catch (Exception ignored) {
                        // Do not fail requests if SSE publication fails.
                    }
                }
            }
        });
    }

    /**
     * Returns operations with revision strictly greater than {@code fromRevision}.
     *
     * Endpoint: GET /datasets/{datasetId}/ops?fromRevision=<n>&limit=<m>
     */
    @Transactional
    public Response opsSince(UUID datasetId, Long fromRevision, Integer limit) {
        if (fromRevision == null) {
            throw new BadRequestException("fromRevision is required");
        }
        if (fromRevision < 0) {
            throw new BadRequestException("fromRevision must be >= 0");
        }
        int safeLimit = limit == null ? 200 : Math.max(1, Math.min(limit, 1000));

        PrincipalInfo principal = authz.currentPrincipal();

        // Visibility check (avoid leaking dataset existence)
        Role role = aclRepository.findRole(datasetId, principal.subject())
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
        if (!role.atLeast(Role.VIEWER)) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        var rows = operationRepository.listAfterRevision(datasetId, fromRevision, safeLimit);
        List<OperationEvent> ops = rows.stream()
                .map(r -> {
                    JsonNode payload;
                    try {
                        payload = mapper.readTree(r.payloadJson == null ? "null" : r.payloadJson);
                    } catch (Exception e) {
                        // Defensive: if older rows contain invalid JSON, return null payload rather than failing the entire request
                        payload = null;
                    }
                    return new OperationEvent(
                            datasetId,
                            r.revision,
                            r.opId,
                            r.opType,
                            payload,
                            r.createdAt,
                            r.createdBy
                    );
                })
                .collect(Collectors.toList());

        long toRevision = ops.isEmpty() ? fromRevision : ops.get(ops.size() - 1).revision();

        // Audit: record reads as best-effort.
        try {
            ObjectNode details = auditService.details()
                    .put("fromRevision", fromRevision)
                    .put("toRevision", toRevision)
                    .put("limit", safeLimit)
                    .put("count", ops.size());
            Object mdcRequestId = MDC.get("requestId");
            if (mdcRequestId != null) {
                details.put("requestId", String.valueOf(mdcRequestId));
            }
            auditService.record(datasetId, principal.subject(), "OPS_READ_SINCE", details);
        } catch (Exception ignored) {
            // Best-effort only
        }

        return Response.ok(new OpsSinceResponse(fromRevision, toRevision, ops)).build();
    }

    /**
     * Server-Sent Events subscription channel.
     *
     * <p>If {@code fromRevision} is provided, the stream starts by emitting all operations
     * strictly greater than that revision (up to {@code limit}), and then continues with
     * live events for the dataset.
     *
     * <p><strong>Important:</strong> This method must not be {@code @Transactional}. The SSE connection can stay open
     * for a long time and may be closed by the client/proxy at any time. If a transaction spans the reactive stream,
     * Narayana's Transaction Reaper can cancel it, causing noisy {@code RollbackException}/{@code "transaction is not active"}
     * errors. We therefore do all database work up-front in a short transaction and then return a purely in-memory stream.
     */
    public Multi<OperationEvent> streamOps(UUID datasetId, Long fromRevision, Integer limit) {
        StreamInit init = prepareStreamInit(datasetId, fromRevision, limit);

        // Live events (filter to ensure monotonicity if the client passes fromRevision)
        Multi<OperationEvent> live = sseHub.stream(datasetId)
                .select().where(ev -> ev != null && ev.revision() > init.startFrom);

        if (init.backlog.isEmpty()) {
            return live;
        }

        return Multi.createBy().concatenating().streams(Multi.createFrom().iterable(init.backlog), live);
    }

    private static final class StreamInit {
        final long startFrom;
        final List<OperationEvent> backlog;

        private StreamInit(long startFrom, List<OperationEvent> backlog) {
            this.startFrom = startFrom;
            this.backlog = backlog;
        }
    }

    /**
     * Performs ACL checks and loads an optional backlog inside a short JTA transaction.
     * The returned data is safe to use outside a transaction.
     */
    @Transactional
    StreamInit prepareStreamInit(UUID datasetId, Long fromRevision, Integer limit) {
        long startFrom;
        int safeLimit = limit == null ? 200 : Math.max(1, Math.min(limit, 1000));

        PrincipalInfo principal = authz.currentPrincipal();

        // Visibility check (avoid leaking dataset existence)
        Role role = aclRepository.findRole(datasetId, principal.subject())
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
        if (!role.atLeast(Role.VIEWER)) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        if (fromRevision == null) {
            // Default: only live events after current head.
            startFrom = ds.currentRevision;
        } else {
            if (fromRevision < 0) {
                throw new BadRequestException("fromRevision must be >= 0");
            }
            startFrom = fromRevision;
        }

        // Audit: record stream subscriptions (best-effort).
        try {
            ObjectNode details = auditService.details();
            if (fromRevision == null) {
                details.putNull("fromRevision");
            } else {
                details.put("fromRevision", fromRevision);
            }
            details.put("effectiveStartFrom", startFrom)
                    .put("limit", safeLimit);
            Object mdcRequestId = MDC.get("requestId");
            if (mdcRequestId != null) {
                details.put("requestId", String.valueOf(mdcRequestId));
            }
            auditService.record(datasetId, principal.subject(), "OPS_STREAM_SUBSCRIBE", details);
        } catch (Exception ignored) {
            // Best-effort only
        }

        var rows = operationRepository.listAfterRevision(datasetId, startFrom, safeLimit);
        List<OperationEvent> backlog = rows.stream()
                .map(r -> {
                    JsonNode payload;
                    try {
                        payload = mapper.readTree(r.payloadJson == null ? "null" : r.payloadJson);
                    } catch (Exception e) {
                        payload = null;
                    }
                    return new OperationEvent(
                            datasetId,
                            r.revision,
                            r.opId,
                            r.opType,
                            payload,
                            r.createdAt,
                            r.createdBy
                    );
                })
                .collect(Collectors.toList());

        return new StreamInit(startFrom, backlog);
    }

    private static Integer extractSchemaVersion(JsonNode snapshot) {
        if (snapshot != null && snapshot.has("schemaVersion") && snapshot.get("schemaVersion").canConvertToInt()) {
            return snapshot.get("schemaVersion").intValue();
        }
        return null;
    }
}