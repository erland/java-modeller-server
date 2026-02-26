# API Examples — java-modeller-server (Phase 1)

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
