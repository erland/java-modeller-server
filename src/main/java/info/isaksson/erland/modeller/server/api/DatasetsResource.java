package info.isaksson.erland.modeller.server.api;

import info.isaksson.erland.modeller.server.api.dto.CreateDatasetRequest;
import info.isaksson.erland.modeller.server.api.dto.DatasetResponse;
import info.isaksson.erland.modeller.server.api.dto.UpdateDatasetRequest;
import info.isaksson.erland.modeller.server.domain.DatasetMetadataPolicy;
import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.domain.ValidationPolicy;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAclEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAclId;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAclRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetRepository;
import info.isaksson.erland.modeller.server.security.DatasetAuthorizationService;
import info.isaksson.erland.modeller.server.security.AuditService;
import info.isaksson.erland.modeller.server.security.PrincipalInfo;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Dataset management endpoints (Phase 1).
 *
 * Paths are aligned with the Phase 1 step plan: /datasets + /datasets/{id}.
 */
@Path("/datasets")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetsResource {

    private final DatasetRepository datasetRepository;
    private final DatasetAclRepository aclRepository;
    private final DatasetAuthorizationService authz;
    private final AuditService audit;

    @Inject
    public DatasetsResource(DatasetRepository datasetRepository,
                            DatasetAclRepository aclRepository,
                            DatasetAuthorizationService authz,
                            AuditService audit) {
        this.datasetRepository = datasetRepository;
        this.aclRepository = aclRepository;
        this.authz = authz;
        this.audit = audit;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response create(CreateDatasetRequest req) {
        PrincipalInfo principal = authz.currentPrincipal();

        if (req == null) throw new BadRequestException("Request body is required");

        String name = DatasetMetadataPolicy.normalizeAndValidateName(req.name);
        String description = DatasetMetadataPolicy.normalizeAndValidateDescription(req.description);

        ValidationPolicy policy = ValidationPolicy.tryParse(req.validationPolicy).orElse(ValidationPolicy.NONE);
        if (req.validationPolicy != null && ValidationPolicy.tryParse(req.validationPolicy).isEmpty()) {
            throw new BadRequestException("Invalid validationPolicy. Allowed: none, basic, strict");
        }

        OffsetDateTime now = OffsetDateTime.now();

        DatasetEntity ds = new DatasetEntity();
        ds.id = UUID.randomUUID();
        ds.name = name;
        ds.description = description;
        ds.createdAt = now;
        ds.updatedAt = now;
        ds.updatedBy = principal.subject();
        ds.archivedAt = null;
        ds.deletedAt = null;
        ds.createdBy = principal.subject();
        ds.updatedBy = principal.subject();
        ds.currentRevision = 0;
        ds.validationPolicy = policy;

        datasetRepository.persist(ds);

        // Creator becomes OWNER
        DatasetAclEntity acl = new DatasetAclEntity();
        acl.id = new DatasetAclId(ds.id, principal.subject());
        acl.role = Role.OWNER.name();
        acl.createdAt = now;
        aclRepository.persist(acl);

        audit.record(ds.id, principal.subject(), "DATASET_CREATE",
                audit.details().put("name", ds.name).put("description", ds.description).put("validationPolicy", policy.wireValue()));

        DatasetResponse resp = DatasetMapper.toResponse(ds, Role.OWNER);
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }

    @GET
    public List<DatasetResponse> list() {
        PrincipalInfo principal = authz.currentPrincipal();

        List<UUID> visibleIds = aclRepository.findDatasetIdsForUser(principal.subject());
        if (visibleIds.isEmpty()) {
            return List.of();
        }

        List<DatasetEntity> datasets = datasetRepository.list("id in ?1 and deletedAt is null", visibleIds);

        // Return a stable order (most recently updated first).
        datasets.sort(Comparator.comparing((DatasetEntity d) -> d.updatedAt).reversed());

        return datasets.stream()
                .map(d -> {
                    Role role = aclRepository.findRole(d.id, principal.subject()).orElse(null);
                    return DatasetMapper.toResponse(d, role);
                })
                .toList();
    }

    @GET
    @Path("/{datasetId}")
    public DatasetResponse get(@PathParam("datasetId") UUID datasetId) {
        PrincipalInfo principal = authz.currentPrincipal();
        Role role = requireVisible(datasetId, principal.subject());

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        return DatasetMapper.toResponse(ds, role);
    }

    @PUT
    @Path("/{datasetId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public DatasetResponse update(@PathParam("datasetId") UUID datasetId, UpdateDatasetRequest req) {
        PrincipalInfo principal = authz.currentPrincipal();
        Role role = requireVisible(datasetId, principal.subject());
        requireAtLeast(role, Role.OWNER); // Phase 1 policy: only Owner can update metadata

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        if (req == null) throw new BadRequestException("Request body is required");

        String beforeName = ds.name;
        String beforeDescription = ds.description;

        ds.name = DatasetMetadataPolicy.normalizeAndValidateName(req.name);
        ds.description = DatasetMetadataPolicy.normalizeAndValidateDescription(req.description);

        ValidationPolicy beforePolicy = ds.validationPolicy;
        if (req.validationPolicy != null) {
            ValidationPolicy parsed = ValidationPolicy.tryParse(req.validationPolicy)
                    .orElseThrow(() -> new BadRequestException("Invalid validationPolicy. Allowed: none, basic, strict"));
            ds.validationPolicy = parsed;
        }
        ds.updatedAt = OffsetDateTime.now();
        datasetRepository.persist(ds);

        audit.record(ds.id, principal.subject(), "DATASET_UPDATE",
                audit.details()
                        .put("beforeName", beforeName)
                        .put("afterName", ds.name)
                        .put("beforeDescription", beforeDescription)
                        .put("afterDescription", ds.description)
                        .put("beforeValidationPolicy", beforePolicy != null ? beforePolicy.wireValue() : null)
                        .put("afterValidationPolicy", ds.validationPolicy != null ? ds.validationPolicy.wireValue() : null));

        return DatasetMapper.toResponse(ds, role);
    }

    @POST
    @Path("/{datasetId}/archive")
    @Transactional
    public DatasetResponse archive(@PathParam("datasetId") UUID datasetId) {
        PrincipalInfo principal = authz.currentPrincipal();
        Role role = requireVisible(datasetId, principal.subject());
        requireAtLeast(role, Role.OWNER);

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }
        OffsetDateTime now = OffsetDateTime.now();
        ds.archivedAt = now;
        ds.updatedAt = now;
        ds.updatedBy = principal.subject();

        audit.record(ds.id, principal.subject(), "DATASET_ARCHIVE",
                audit.details().put("archivedAt", now.toString()));

        return DatasetMapper.toResponse(ds, role);
    }

    @POST
    @Path("/{datasetId}/unarchive")
    @Transactional
    public DatasetResponse unarchive(@PathParam("datasetId") UUID datasetId) {
        PrincipalInfo principal = authz.currentPrincipal();
        Role role = requireVisible(datasetId, principal.subject());
        requireAtLeast(role, Role.OWNER);

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }
        OffsetDateTime now = OffsetDateTime.now();
        ds.archivedAt = null;
        ds.updatedAt = now;
        ds.updatedBy = principal.subject();

        audit.record(ds.id, principal.subject(), "DATASET_UNARCHIVE",
                audit.details().put("unarchivedAt", now.toString()));

        return DatasetMapper.toResponse(ds, role);
    }

    @DELETE
    @Path("/{datasetId}")
    @Transactional
    public Response delete(@PathParam("datasetId") UUID datasetId) {
        PrincipalInfo principal = authz.currentPrincipal();
        Role role = requireVisible(datasetId, principal.subject());
        requireAtLeast(role, Role.OWNER);

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ds.deletedAt = now;
        ds.updatedAt = now;
        ds.updatedBy = principal.subject();

        audit.record(ds.id, principal.subject(), "DATASET_DELETE",
                audit.details().put("deletedAt", now.toString()));

        return Response.noContent().build();
    }

    private Role requireVisible(UUID datasetId, String userSub) {
        // We intentionally return "not found" if dataset is not visible to the user,
        // to avoid leaking dataset existence.
        return aclRepository.findRole(datasetId, userSub)
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
    }

    private void requireAtLeast(Role effective, Role required) {
        if (effective == null || !effective.atLeast(required)) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }
    }
}