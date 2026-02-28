# API Stability Notes (Phase 1)

This Phase 1 server aims for **stable client-facing contracts** from the start.

## Documentation endpoints
- OpenAPI: `/openapi`
- Swagger UI: `/swagger-ui`

## Correlation headers
All responses include:
- `X-Request-Id`: request correlation ID (client may supply one; otherwise generated)
- `X-API-Version`: currently `1`

## Error envelope
Unless a resource explicitly returns its own JSON body (e.g., snapshot conflict responses), errors are returned as:

```json
{
  "timestamp": "2026-02-26T09:00:00+01:00",
  "status": 404,
  "code": "NOT_FOUND",
  "message": "Dataset not found",
  "path": "/datasets/<id>",
  "requestId": "<uuid>"
}
```

Codes are derived from HTTP status and are intended to be stable.


## Phase 3 API additions (operations + SSE)

### New endpoints
- `POST /datasets/{datasetId}/ops` — append operations (EDITOR+)
- `GET /datasets/{datasetId}/ops?fromRevision=...` — read operation log (VIEWER+)
- `GET /datasets/{datasetId}/ops/stream` — SSE stream of operations (VIEWER+)

### Compatibility notes
- Phase 2 snapshot endpoints remain supported. The server continues to materialize a **full snapshot** after each successful ops append, so:
  - `GET /datasets/{datasetId}/snapshot` remains the canonical way to fetch the latest state.
  - Revisions/ETags advance when ops are appended.
- Clients that do not understand operations can continue to use snapshot read/write semantics without change.
- Lease policy applies to ops appends:
  - If a lease is held by the caller, `X-Lease-Token` is required (428 if missing).
  - If a lease is held by someone else, appends return 409 unless `force=true` is used by an Owner+.
- `ApiError` includes both `code` and `errorCode` fields for compatibility with existing clients/tests.

### SSE expectations
- SSE is best-effort delivery for live updates.
- Clients MUST be able to recover by calling the `ops?fromRevision=...` endpoint after reconnect.
- Current implementation is single-node (in-memory broadcast). Multi-node deployments require an external fan-out mechanism.
