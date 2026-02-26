package info.isaksson.erland.modeller.server.api;

import info.isaksson.erland.modeller.server.api.dto.DatasetAclEntryResponse;
import info.isaksson.erland.modeller.server.api.dto.DatasetAclListResponse;
import info.isaksson.erland.modeller.server.api.dto.UpsertDatasetAclRequest;
import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAclEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAclRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetRepository;
import info.isaksson.erland.modeller.server.security.DatasetAuthorizationService;
import info.isaksson.erland.modeller.server.security.PrincipalInfo;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
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

@Path("/datasets/{datasetId}/acl")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetAclResource {

    private final DatasetRepository datasetRepository;
    private final DatasetAclRepository aclRepository;
    private final DatasetAuthorizationService authz;

    @Inject
    public DatasetAclResource(DatasetRepository datasetRepository,
                             DatasetAclRepository aclRepository,
                             DatasetAuthorizationService authz) {
        this.datasetRepository = datasetRepository;
        this.aclRepository = aclRepository;
        this.authz = authz;
    }

    @GET
    public DatasetAclListResponse list(@PathParam("datasetId") UUID datasetId) {
        PrincipalInfo principal = authz.currentPrincipal();
        Role callerRole = requireVisible(datasetId, principal.subject());
        requireAtLeast(callerRole, Role.OWNER); // Phase A: owner-managed ACL

        List<DatasetAclEntryResponse> items = aclRepository.listEntries(datasetId).stream()
                .sorted(Comparator.comparing((DatasetAclEntity e) -> e.createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(e -> new DatasetAclEntryResponse(
                        e.id != null ? e.id.userSub : null,
                        e.role,
                        e.createdAt
                ))
                .toList();

        return new DatasetAclListResponse(datasetId, items);
    }

    @PUT
    @Path("/{userSub}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public DatasetAclEntryResponse upsert(@PathParam("datasetId") UUID datasetId,
                                         @PathParam("userSub") String userSub,
                                         UpsertDatasetAclRequest req) {
        PrincipalInfo principal = authz.currentPrincipal();
        Role callerRole = requireVisible(datasetId, principal.subject());
        requireAtLeast(callerRole, Role.OWNER);

        ensureDatasetActive(datasetId);

        if (userSub == null || userSub.isBlank()) {
            throw new BadRequestException("userSub is required");
        }
        if (req == null || req.role == null || req.role.isBlank()) {
            throw new BadRequestException("role is required");
        }

        Role newRole;
        try {
            newRole = Role.parse(req.role);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown role: " + req.role);
        }

        Role existingRole = aclRepository.findRole(datasetId, userSub).orElse(null);

        // Last-owner protection: if we're downgrading the last owner, reject.
        if (existingRole == Role.OWNER && newRole != Role.OWNER) {
            long owners = aclRepository.countOwners(datasetId);
            if (owners <= 1) {
                throw new jakarta.ws.rs.WebApplicationException(conflictLastOwner());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        aclRepository.upsert(datasetId, userSub, newRole, now);

        DatasetAclEntity saved = aclRepository.findById(new info.isaksson.erland.modeller.server.persistence.entities.DatasetAclId(datasetId, userSub));
        OffsetDateTime createdAt = saved != null ? saved.createdAt : (existingRole == null ? now : null);

        return new DatasetAclEntryResponse(userSub, newRole.name(), createdAt);
    }

    @DELETE
    @Path("/{userSub}")
    @Transactional
    public Response revoke(@PathParam("datasetId") UUID datasetId,
                           @PathParam("userSub") String userSub) {
        PrincipalInfo principal = authz.currentPrincipal();
        Role callerRole = requireVisible(datasetId, principal.subject());
        requireAtLeast(callerRole, Role.OWNER);

        ensureDatasetActive(datasetId);

        if (userSub == null || userSub.isBlank()) {
            throw new BadRequestException("userSub is required");
        }

        Role existingRole = aclRepository.findRole(datasetId, userSub).orElse(null);
        if (existingRole == null) {
            // Idempotent revoke - no row, treat as success.
            return Response.noContent().build();
        }

        if (existingRole == Role.OWNER) {
            long owners = aclRepository.countOwners(datasetId);
            if (owners <= 1) {
                throw new jakarta.ws.rs.WebApplicationException(conflictLastOwner());
            }
        }

        aclRepository.deleteEntry(datasetId, userSub);
        return Response.noContent().build();
    }

    private void ensureDatasetActive(UUID datasetId) {
        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }
    }

    private Role requireVisible(UUID datasetId, String userSub) {
        // Avoid leaking existence: non-members see 404.
        return aclRepository.findRole(datasetId, userSub)
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
    }

    private void requireAtLeast(Role effective, Role required) {
        if (effective == null || !effective.atLeast(required)) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }
    }

    private Response conflictLastOwner() {
        return Response.status(Response.Status.CONFLICT)
                .entity(java.util.Map.of(
                        "code", "LAST_OWNER",
                        "message", "Cannot remove or downgrade the last remaining Owner"
                ))
                .build();
    }
}
