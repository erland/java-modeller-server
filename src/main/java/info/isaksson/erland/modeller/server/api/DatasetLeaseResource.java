package info.isaksson.erland.modeller.server.api;

import info.isaksson.erland.modeller.server.api.dto.DatasetLeaseResponse;
import info.isaksson.erland.modeller.server.api.dto.LeaseConflictResponse;
import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetLeaseEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAclRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetLeaseRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetRepository;
import info.isaksson.erland.modeller.server.security.AuditService;
import info.isaksson.erland.modeller.server.security.DatasetAuthorizationService;
import info.isaksson.erland.modeller.server.security.PrincipalInfo;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

@Path("/datasets/{datasetId}/lease")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetLeaseResource {

    @Inject DatasetAuthorizationService authz;
    @Inject DatasetAclRepository aclRepository;
    @Inject DatasetRepository datasetRepository;
    @Inject DatasetLeaseRepository leaseRepository;
    @Inject AuditService audit;

    @ConfigProperty(name = "modeller.lease.ttlSeconds", defaultValue = "300")
    long ttlSeconds;

    @GET
    @Transactional
    public DatasetLeaseResponse status(@PathParam("datasetId") UUID datasetId) {
        PrincipalInfo principal = authz.currentPrincipal();
        Role role = aclRepository.findRole(datasetId, principal.subject())
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
        if (!role.atLeast(Role.VIEWER)) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        OffsetDateTime now = OffsetDateTime.now();
        DatasetLeaseEntity lease = leaseRepository.findById(datasetId);
        if (lease == null || lease.expiresAt == null || !lease.expiresAt.isAfter(now)) {
            return DatasetLeaseResponse.inactive(datasetId);
        }

        DatasetLeaseResponse r = new DatasetLeaseResponse();
        r.datasetId = datasetId;
        r.active = true;
        r.holderSub = lease.holderSub;
        r.acquiredAt = lease.acquiredAt;
        r.renewedAt = lease.renewedAt;
        r.expiresAt = lease.expiresAt;
        r.leaseToken = null; // never expose token via status
        return r;
    }

    @POST
    @Transactional
    public Response acquireOrRefresh(@PathParam("datasetId") UUID datasetId) {
        PrincipalInfo principal = authz.currentPrincipal();
        Role role = aclRepository.findRole(datasetId, principal.subject())
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
        if (!role.atLeast(Role.EDITOR)) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusSeconds(ttlSeconds);

        DatasetLeaseEntity lease = leaseRepository.findById(datasetId);

        // No lease or expired => create/replace
        if (lease == null || lease.expiresAt == null || !lease.expiresAt.isAfter(now)) {
            boolean replacing = (lease != null);

            boolean isNew = false;

            if (lease == null) {
                lease = new DatasetLeaseEntity();
                lease.datasetId = datasetId;
                isNew = true;
            }

            String previousHolder = lease.holderSub;

            lease.holderSub = principal.subject();
            lease.leaseToken = UUID.randomUUID().toString();
            lease.acquiredAt = now;
            lease.renewedAt = now;
            lease.expiresAt = expiresAt;


            if (isNew) {
                leaseRepository.persist(lease);
            }

            var details = audit.details();
            details.put("ttlSeconds", ttlSeconds);
            details.put("expiresAt", expiresAt.toString());
            details.put("replacing", replacing);
            if (previousHolder != null) {
                details.put("previousHolderSub", previousHolder);
            }
            audit.record(datasetId, principal.subject(), "LEASE_ACQUIRED", details);

            return Response.ok(toResponse(lease, true)).build();
        }

        // Lease held by caller => refresh
        if (principal.subject().equals(lease.holderSub)) {
            lease.renewedAt = now;
            lease.expiresAt = expiresAt;



            var details = audit.details();
            details.put("ttlSeconds", ttlSeconds);
            details.put("expiresAt", expiresAt.toString());
            audit.record(datasetId, principal.subject(), "LEASE_REFRESHED", details);

            return Response.ok(toResponse(lease, true)).build();
        }

        // Lease held by someone else and still active => conflict
        LeaseConflictResponse conflict = new LeaseConflictResponse(datasetId, lease.holderSub, lease.expiresAt);
        return Response.status(Response.Status.CONFLICT).entity(conflict).build();
    }

    @DELETE
    @Transactional
    public Response release(@PathParam("datasetId") UUID datasetId) {
        PrincipalInfo principal = authz.currentPrincipal();
        Role role = aclRepository.findRole(datasetId, principal.subject())
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
        if (!role.atLeast(Role.EDITOR)) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        DatasetLeaseEntity lease = leaseRepository.findById(datasetId);
        if (lease == null) {
            return Response.noContent().build();
        }

        // Holder can release, owner can release; others forbidden.
        if (!principal.subject().equals(lease.holderSub) && !role.atLeast(Role.OWNER)) {
            throw new jakarta.ws.rs.ForbiddenException("Only lease holder or owner can release lease");
        }

        leaseRepository.deleteById(datasetId);

        var details = audit.details();
        details.put("holderSub", lease.holderSub);
        if (lease.expiresAt != null) { details.put("expiresAt", lease.expiresAt.toString()); } else { details.putNull("expiresAt"); }
        audit.record(datasetId, principal.subject(), "LEASE_RELEASED", details);

        return Response.noContent().build();
    }

    static DatasetLeaseResponse toResponse(DatasetLeaseEntity lease, boolean includeToken) {
        DatasetLeaseResponse r = new DatasetLeaseResponse();
        r.datasetId = lease.datasetId;
        r.active = true;
        r.holderSub = lease.holderSub;
        r.acquiredAt = lease.acquiredAt;
        r.renewedAt = lease.renewedAt;
        r.expiresAt = lease.expiresAt;
        r.leaseToken = includeToken ? lease.leaseToken : null;
        return r;
    }
}
