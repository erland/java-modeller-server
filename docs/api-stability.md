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
