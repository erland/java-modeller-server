package info.isaksson.erland.modeller.server.api;

import info.isaksson.erland.modeller.server.api.dto.AppendOperationsRequest;
import info.isaksson.erland.modeller.server.ops.DatasetOpsService;
import info.isaksson.erland.modeller.server.api.dto.OperationEvent;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestStreamElementType;
import io.smallrye.common.annotation.Blocking;

import java.util.UUID;

/**
 * Phase 3: operation-based writes.
 */
@Path("/datasets/{datasetId}/ops")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetOpsResource {

    @Inject
    DatasetOpsService opsService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response append(@PathParam("datasetId") UUID datasetId,
                           @HeaderParam("X-Lease-Token") String leaseToken,
                           @QueryParam("force") @DefaultValue("false") boolean force,
                           AppendOperationsRequest request) {
        return opsService.appendOps(datasetId, request, leaseToken, force);
    }

    @GET
    public Response opsSince(@PathParam("datasetId") UUID datasetId,
                             @QueryParam("fromRevision") Long fromRevision,
                             @QueryParam("limit") Integer limit) {
        return opsService.opsSince(datasetId, fromRevision, limit);
    }

    /**
     * Phase 3: SSE subscription channel.
     *
     * Endpoint: GET /datasets/{datasetId}/ops/stream?fromRevision=<n>&limit=<m>
     */
    @GET
    @Path("stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Blocking
    public Multi<OperationEvent> stream(@PathParam("datasetId") UUID datasetId,
                           @QueryParam("fromRevision") Long fromRevision,
                           @QueryParam("limit") Integer limit) {
        return opsService.streamOps(datasetId, fromRevision, limit);
    }
}
