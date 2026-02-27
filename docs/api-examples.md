# API Examples — java-modeller-server (Phase 1 + Phase 2)

These examples illustrate **typical request/response shapes** and the **ETag / If-Match** optimistic concurrency contract.
Paths are illustrative; keep them consistent with your implementation.

> Notes
> - All protected calls include: `Authorization: Bearer <access_token>`
> - Snapshot reads return `ETag: "<revision>"`
> - Snapshot writes require `If-Match: "<revision>"`

---

## 1) Get current user (`GET /me`)

**Request**
```http
GET /me HTTP/1.1
Authorization: Bearer <token>
```

**Response (200)**
```json
{
  "sub": "4f3f1e2c-....",
  "preferredUsername": "erland",
  "email": "erland@example.com"
}
```

**Response (401/403)**
```json
{
  "code": "AUTH_REQUIRED",
  "message": "Authentication required."
}
```

---

## 2) Create dataset (`POST /datasets`)

**Request**
```http
POST /datasets HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "name": "Architecture 2026",
  "description": "Main enterprise architecture dataset",
  "tags": ["archimate", "bpmn", "uml"]
}
```

**Response (201)**
```json
{
  "datasetId": "d_9b7d3f0f-9b0a-4c9c-9de2-1b9a8b0f5c21",
  "name": "Architecture 2026",
  "description": "Main enterprise architecture dataset",
  "status": "ACTIVE",
  "tags": ["archimate", "bpmn", "uml"],
  "createdAt": "2026-02-26T20:15:10Z",
  "createdBy": "4f3f1e2c-....",
  "updatedAt": "2026-02-26T20:15:10Z",
  "updatedBy": "4f3f1e2c-....",
  "currentRevision": 1
}
```

**Errors**
- Invalid name:
```json
{ "code": "VALIDATION_FAILED", "message": "Dataset name is invalid.", "details": { "field": "name" } }
```

---

## 3) List datasets (`GET /datasets`)

**Request**
```http
GET /datasets HTTP/1.1
Authorization: Bearer <token>
```

**Response (200)**
```json
{
  "items": [
    {
      "datasetId": "d_9b7d3f0f-...",
      "name": "Architecture 2026",
      "status": "ACTIVE",
      "updatedAt": "2026-02-26T20:15:10Z",
      "updatedBy": "4f3f1e2c-....",
      "currentRevision": 12
    }
  ]
}
```

---

## 4) Get dataset metadata (`GET /datasets/{id}`)

**Request**
```http
GET /datasets/d_9b7d3f0f-... HTTP/1.1
Authorization: Bearer <token>
```

**Response (200)** — same shape as create response.

**Not found / forbidden policy**
```json
{ "code": "NOT_FOUND", "message": "Dataset not found." }
```

---

## 5) Grant access (`POST /datasets/{id}/acl/grant`)

**Request**
```http
POST /datasets/d_9b7d3f0f-.../acl/grant HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "principalSub": "9a2c1c00-....",
  "role": "EDITOR"
}
```

**Response (200)**
```json
{
  "datasetId": "d_9b7d3f0f-...",
  "principalSub": "9a2c1c00-....",
  "role": "EDITOR",
  "grantedAt": "2026-02-26T20:20:00Z",
  "grantedBy": "4f3f1e2c-...."
}
```

**Errors**
- Not owner:
```json
{ "code": "FORBIDDEN", "message": "You do not have permission to modify sharing for this dataset." }
```

---

## 6) Read latest snapshot (`GET /datasets/{id}/snapshot`)

**Request**
```http
GET /datasets/d_9b7d3f0f-.../snapshot HTTP/1.1
Authorization: Bearer <token>
```

**Response (200)**
```http
HTTP/1.1 200 OK
Content-Type: application/json
ETag: "12"
```

```json
{
  "datasetId": "d_9b7d3f0f-...",
  "revision": 12,
  "savedAt": "2026-02-26T21:00:00Z",
  "savedBy": "4f3f1e2c-....",
  "schemaVersion": 12,
  "payload": {
    "schemaVersion": 12,
    "model": { /* ... full modeller dataset json ... */ }
  }
}
```

**If no snapshot exists yet (choose one policy)**
- Option A: Return an empty initial payload with `ETag: "0"`
- Option B: Return `404 NOT_FOUND`

Example (Option A):
```http
HTTP/1.1 200 OK
ETag: "0"
```

```json
{
  "datasetId": "d_9b7d3f0f-...",
  "revision": 0,
  "schemaVersion": 12,
  "payload": { "schemaVersion": 12, "model": { } }
}
```

---

## 7) Save snapshot (`PUT /datasets/{id}/snapshot`) with optimistic concurrency

**Request**
```http
PUT /datasets/d_9b7d3f0f-.../snapshot HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
If-Match: "12"
```

```json
{
  "schemaVersion": 12,
  "payload": {
    "schemaVersion": 12,
    "model": { /* updated dataset */ }
  },
  "message": "Updated application layer diagram"
}
```

**Response (200)**
```http
HTTP/1.1 200 OK
ETag: "13"
Content-Type: application/json
```

```json
{
  "datasetId": "d_9b7d3f0f-...",
  "revision": 13,
  "savedAt": "2026-02-26T21:10:00Z",
  "savedBy": "4f3f1e2c-...."
}
```

**Conflict (409 or 412 — pick one and keep consistent)**
```http
HTTP/1.1 409 Conflict
Content-Type: application/json
```

```json
{
  "code": "CONFLICT",
  "message": "Snapshot save rejected because the dataset has changed.",
  "details": {
    "expectedRevision": 12,
    "currentRevision": 14,
    "updatedAt": "2026-02-26T21:12:02Z",
    "updatedBy": "9a2c1c00-...."
  }
}
```

---

## 8) Standard error format (example)

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Snapshot payload is invalid.",
  "details": {
    "field": "schemaVersion",
    "reason": "Missing required field"
  }
}
```

---

## 9) CORS (if the PWA runs on a different origin)
If the PWA is hosted separately, the server should allow configured origins.

Preflight example:
```http
OPTIONS /datasets HTTP/1.1
Origin: https://pwa.example.com
Access-Control-Request-Method: GET
Access-Control-Request-Headers: Authorization
```

Expected response includes appropriate `Access-Control-Allow-*` headers.

---

## 10) Notes for Phase 2 / 3 compatibility
- Keep `revision` monotonic and always returned.
- Conflict responses should always include `currentRevision` and updater metadata.
- If you later add operation-based writes, keep snapshot endpoints for backward compatibility.

---

# Phase 2 additions

These examples cover the Phase 2 collaboration features: **validationPolicy**, **leases**, **head polling**, **enriched conflict bodies**, **deterministic validation errors**, and **restore**.

> Additional notes (Phase 2)
> - Datasets have a `validationPolicy`: `"none" | "basic" | "strict"`
> - Leases are **optional soft locks**. If a dataset is leased:
>   - another user’s snapshot write returns **409** with a lease conflict body
>   - the lease holder must include `X-Lease-Token: <token>` when writing snapshots (or restoring)
> - Snapshot writes still require `If-Match` and still use revision/ETag optimistic concurrency.

---

## P2-1) Create dataset with validation policy (`POST /datasets`)

**Request**
```http
POST /datasets HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "My dataset",
  "description": "Team dataset",
  "validationPolicy": "basic"
}
```

**Response (201)**
```json
{
  "id": "9fcb2a3d-....",
  "name": "My dataset",
  "description": "Team dataset",
  "validationPolicy": "basic",
  "createdAt": "2026-02-27T20:12:34.123Z",
  "createdBy": "user-sub-1",
  "updatedAt": "2026-02-27T20:12:34.123Z",
  "updatedBy": "user-sub-1"
}
```

**Response (400 — invalid policy)**
```json
{
  "code": "BAD_REQUEST",
  "message": "Invalid validationPolicy. Allowed: none, basic, strict"
}
```

---

## P2-2) Update validation policy (`PATCH /datasets/{datasetId}`)

**Request**
```http
PATCH /datasets/9fcb2a3d-.... HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json

{
  "validationPolicy": "strict"
}
```

**Response (200)** returns the updated dataset (including `validationPolicy`).

---

## P2-3) Acquire or refresh lease (`POST /datasets/{datasetId}/lease`)

**Request**
```http
POST /datasets/9fcb2a3d-..../lease HTTP/1.1
Authorization: Bearer <token>
```

**Response (200 — acquired or refreshed)**
```json
{
  "datasetId": "9fcb2a3d-....",
  "active": true,
  "holderSub": "user-sub-1",
  "acquiredAt": "2026-02-27T20:20:00.000Z",
  "renewedAt": "2026-02-27T20:20:00.000Z",
  "expiresAt": "2026-02-27T20:25:00.000Z",
  "leaseToken": "b7f8d2c5-...."
}
```

**Response (409 — leased by someone else)**
```json
{
  "datasetId": "9fcb2a3d-....",
  "holderSub": "user-sub-2",
  "expiresAt": "2026-02-27T20:25:00.000Z"
}
```

---

## P2-4) Lease status (`GET /datasets/{datasetId}/lease`)

**Request**
```http
GET /datasets/9fcb2a3d-..../lease HTTP/1.1
Authorization: Bearer <token>
```

**Response (200)**
```json
{
  "datasetId": "9fcb2a3d-....",
  "active": true,
  "holderSub": "user-sub-1",
  "acquiredAt": "2026-02-27T20:20:00.000Z",
  "renewedAt": "2026-02-27T20:20:00.000Z",
  "expiresAt": "2026-02-27T20:25:00.000Z",
  "leaseToken": null
}
```

---

## P2-5) Release lease (`DELETE /datasets/{datasetId}/lease`)

**Request**
```http
DELETE /datasets/9fcb2a3d-..../lease HTTP/1.1
Authorization: Bearer <token>
```

**Response (204)**

---

## P2-6) Head endpoint for polling (`GET /datasets/{datasetId}/head`)

Use this to detect remote changes without downloading the full snapshot payload.

**Request**
```http
GET /datasets/9fcb2a3d-..../head HTTP/1.1
Authorization: Bearer <token>
```

**Response (200)**  
Also returns `ETag: "<currentRevision>"`.

```json
{
  "datasetId": "9fcb2a3d-....",
  "currentRevision": 12,
  "currentEtag": "12",
  "updatedAt": "2026-02-27T20:30:00.000Z",
  "updatedBy": "user-sub-3",
  "validationPolicy": "basic",
  "archivedAt": null,
  "deletedAt": null,
  "leaseActive": true,
  "leaseHolderSub": "user-sub-1",
  "leaseExpiresAt": "2026-02-27T20:35:00.000Z"
}
```

---

## P2-7) Snapshot write with lease token (`PUT /datasets/{datasetId}/snapshot`)

**Request**
```http
PUT /datasets/9fcb2a3d-..../snapshot HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
If-Match: "12"
X-Lease-Token: b7f8d2c5-....

{
  "schemaVersion": 12,
  "model": { "elements": [], "relationships": [] }
}
```

**Response (200)** includes `ETag: "13"` and the snapshot response body.

**Response (428 — lease token required)**
```json
{
  "code": "LEASE_TOKEN_REQUIRED",
  "message": "A valid X-Lease-Token header is required to write while holding an active lease."
}
```

---

## P2-8) Deterministic validation errors (400)

When validation fails (based on dataset `validationPolicy`), the API returns a stable envelope with `validationErrors`.

**Response (400 — example)**
```json
{
  "code": "VALIDATION_FAILED",
  "message": "Snapshot validation failed",
  "validationErrors": [
    {
      "severity": "ERROR",
      "rule": "schemaVersion.required",
      "path": "/schemaVersion",
      "message": "schemaVersion is required for this validation policy"
    }
  ]
}
```

---

## P2-9) Enriched conflict body on stale `If-Match` (409)

**Response (409)** also includes `ETag: "<currentRevision>"`.

```json
{
  "datasetId": "9fcb2a3d-....",
  "currentRevision": 13,
  "currentEtag": "13",
  "savedAt": "2026-02-27T20:31:00.000Z",
  "savedBy": "user-sub-3",
  "updatedAt": "2026-02-27T20:31:00.000Z",
  "updatedBy": "user-sub-3"
}
```

---

## P2-10) Restore snapshot revision (`POST /datasets/{datasetId}/snapshots/{revision}/restore`)

Restores the stored snapshot payload for a historical `revision`, but creates a **new revision** as the latest snapshot.

**Request**
```http
POST /datasets/9fcb2a3d-..../snapshots/7/restore HTTP/1.1
Authorization: Bearer <token>
If-Match: "13"
X-Lease-Token: b7f8d2c5-....
```

**Response (200)** includes `ETag: "14"`.

---

## P2-11) Snapshot history list (`GET /datasets/{datasetId}/snapshots`)

History entries include additional metadata (`payloadBytes`, `savedAction`, `savedMessage`) introduced in Phase 2 hardening.

**Request**
```http
GET /datasets/9fcb2a3d-..../snapshots HTTP/1.1
Authorization: Bearer <token>
```

**Response (200)**
```json
{
  "datasetId": "9fcb2a3d-....",
  "items": [
    {
      "revision": 14,
      "savedAt": "2026-02-27T20:40:00.000Z",
      "savedBy": "user-sub-1",
      "payloadBytes": 123456,
      "savedAction": "RESTORE",
      "savedMessage": "Restored from revision 7"
    },
    {
      "revision": 13,
      "savedAt": "2026-02-27T20:31:00.000Z",
      "savedBy": "user-sub-3",
      "payloadBytes": 118000,
      "savedAction": "WRITE",
      "savedMessage": null
    }
  ]
}
```
