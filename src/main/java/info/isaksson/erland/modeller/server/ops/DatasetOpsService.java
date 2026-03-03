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
import info.isaksson.erland.modeller.server.api.error.ApiError;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
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
import info.isaksson.erland.modeller.server.validation.ValidationResult;
import io.smallrye.mutiny.Multi;
import info.isaksson.erland.modeller.server.ops.policy.LeasePolicyEnforcer;
import info.isaksson.erland.modeller.server.ops.policy.RevisionGate;
import info.isaksson.erland.modeller.server.ops.materialize.SnapshotMaterializer;
import info.isaksson.erland.modeller.server.ops.persist.OpsPersister;
import info.isaksson.erland.modeller.server.ops.DatasetOpsSseHub;
import info.isaksson.erland.modeller.server.ops.events.OpsEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
    private final LeasePolicyEnforcer leasePolicyEnforcer;
    private final RevisionGate revisionGate;
    private final SnapshotMaterializer snapshotMaterializer;
    private final OpsPersister opsPersister;

    private final DatasetAuthorizationService authz;
    private final ObjectMapper mapper;
    private final OpsEventPublisher opsEventPublisher;
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
                             LeasePolicyEnforcer leasePolicyEnforcer,
                             RevisionGate revisionGate,
                             DatasetAuthorizationService authz,
                             SnapshotMaterializer snapshotMaterializer,
                             OpsPersister opsPersister,
                             ObjectMapper mapper,
                     OpsEventPublisher opsEventPublisher,
                             AuditService auditService,
            DatasetOpsSseHub sseHub) {
        this.datasetRepository = datasetRepository;
        this.aclRepository = aclRepository;
        this.snapshotLatestRepository = snapshotLatestRepository;
        this.historyRepository = historyRepository;
        this.operationRepository = operationRepository;
        this.leaseRepository = leaseRepository;
        this.leasePolicyEnforcer = leasePolicyEnforcer;
        this.revisionGate = revisionGate;
        this.authz = authz;
        this.snapshotMaterializer = snapshotMaterializer;
        this.opsPersister = opsPersister;
        this.mapper = mapper;
        this.opsEventPublisher = opsEventPublisher;
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

        LeasePolicyEnforcer.Result leaseDecision = leasePolicyEnforcer.check(principal, role, activeLeaseOpt, leaseToken, force);
        if (leaseDecision.outcome() == LeasePolicyEnforcer.Outcome.FORBIDDEN) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }
        if (leaseDecision.outcome() == LeasePolicyEnforcer.Outcome.CONFLICT) {
            return toResponse(datasetId, new info.isaksson.erland.modeller.server.ops.append.AppendOpsResult.Conflict(
                    leaseDecision.conflict()
            ));
        }
        if (leaseDecision.forcedOverride()) {
            forcedLeaseOverride = true;
            forcedLeaseHolder = leaseDecision.forcedLeaseHolderSub();
        }

        // Concurrency safety + revision assignment: lock, refresh, compare baseRevision, compute newRevision
        RevisionGate.Result gate = revisionGate.lockCompareAndAdvance(ds, request.baseRevision, request.operations.size());
        if (gate instanceof RevisionGate.Conflict c) {
            return toResponse(datasetId, new info.isaksson.erland.modeller.server.ops.append.AppendOpsResult.Conflict(
                    new info.isaksson.erland.modeller.server.ops.append.OpsConflict.RevisionConflict(c.expectedBaseRevision(), c.currentRevision())
            ));
        }
        RevisionGate.Ok ok = (RevisionGate.Ok) gate;
        long currentRevision = ok.previousRevision();
        long previousRevision = ok.previousRevision();
        long newRevision = ok.newRevision();

        // We use the revision as a deterministic ETag for the "latest" snapshot.
        String etag = String.valueOf(newRevision);


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
                return toResponse(datasetId, new info.isaksson.erland.modeller.server.ops.append.AppendOpsResult.Conflict(
                        new info.isaksson.erland.modeller.server.ops.append.OpsConflict.DuplicateOpId(opId, existing.get().revision)
                ));
            }
        }

        SnapshotMaterializer.Result materialized =
                snapshotMaterializer.materialize(baseSnapshot, ops, ds.validationPolicy);

        if (materialized instanceof SnapshotMaterializer.ValidationFailed vf) {
            return toResponse(datasetId, new info.isaksson.erland.modeller.server.ops.append.AppendOpsResult.Conflict(
                    new info.isaksson.erland.modeller.server.ops.append.OpsConflict.ValidationFailed(
                            vf.validationResult().errors().stream()
                                    .map(ValidationErrorDto::fromIssue)
                                    .collect(Collectors.toList())
                    )
            ));
        }

        SnapshotMaterializer.Ok okMat = (SnapshotMaterializer.Ok) materialized;
        JsonNode nextSnapshot = okMat.snapshot();
        String payloadJson = okMat.payloadJson();
        OffsetDateTime now = OffsetDateTime.now();

        // Persist (dataset metadata + ops + snapshots)
        opsPersister.persistAppend(new OpsPersister.AppendRequest(
                datasetId,
                ds,
                ops,
                previousRevision,
                newRevision,
                now,
                principal,
                latest,
                payloadJson,
                etag,
                nextSnapshot,
                forcedLeaseOverride,
                forcedLeaseHolder,
                snapshotHistoryKeep,
                snapshotHistoryMaxAgeDays
        ));

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

        return toResponse(datasetId, new info.isaksson.erland.modeller.server.ops.append.AppendOpsResult.Success(
                previousRevision,
                newRevision,
                now,
                principal.subject(),
                accepted,
                forcedLeaseOverride,
                forcedLeaseHolder
        ));
    }

    private Response toResponse(UUID datasetId, info.isaksson.erland.modeller.server.ops.append.AppendOpsResult result) {
        if (result instanceof info.isaksson.erland.modeller.server.ops.append.AppendOpsResult.Success s) {
            AppendOperationsResponse resp = new AppendOperationsResponse(s.newRevision(), s.accepted());

            // Publish SSE events only after successful commit.
            // (If the transaction rolls back, subscribers should not observe any of these operations.)
            // Ordering guarantee: publish in revision order per dataset (the hub enforces ordering).
            opsEventPublisher.publishAcceptedEventsAfterCommit(datasetId, s.previousRevision(), s.accepted());
return Response.ok(resp).build();
        }

        info.isaksson.erland.modeller.server.ops.append.OpsConflict conflict =
                ((info.isaksson.erland.modeller.server.ops.append.AppendOpsResult.Conflict) result).conflict();

        if (conflict instanceof info.isaksson.erland.modeller.server.ops.append.OpsConflict.LeaseConflict lc) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new LeaseConflictResponse(datasetId, lc.holderSub(), lc.expiresAt()))
                    .build();
        }

        if (conflict instanceof info.isaksson.erland.modeller.server.ops.append.OpsConflict.LeaseTokenRequired) {
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

        if (conflict instanceof info.isaksson.erland.modeller.server.ops.append.OpsConflict.RevisionConflict rc) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new RevisionConflictResponse(datasetId, rc.requestedBaseRevision(), rc.currentRevision()))
                    .build();
        }

        if (conflict instanceof info.isaksson.erland.modeller.server.ops.append.OpsConflict.DuplicateOpId dup) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new DuplicateOpIdResponse(datasetId, dup.opId(), dup.existingRevision()))
                    .build();
        }

        if (conflict instanceof info.isaksson.erland.modeller.server.ops.append.OpsConflict.ValidationFailed vf) {
            String mdcRequestId = (String) MDC.get("mdcRequestId");
            ApiError err = new ApiError(
                    OffsetDateTime.now(),
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    "VALIDATION_FAILED",
                    "Snapshot validation failed",
                    null,
                    mdcRequestId
            ).withValidationErrors(vf.errors());

            return Response.status(Response.Status.BAD_REQUEST).entity(err).build();
        }

        // Should be unreachable as OpsConflict is sealed.
        throw new jakarta.ws.rs.InternalServerErrorException("Unsupported append conflict type: " + conflict.getClass().getName());
    }

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