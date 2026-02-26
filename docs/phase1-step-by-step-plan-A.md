# Phase 1 Development Plan (Server MVP) — java-modeller-server

## 0. Goal
Deliver a **Phase 1 Central Modeling Server** providing:
- Authenticated access via **Keycloak (OIDC)**
- Dataset CRUD (create/list/read/update metadata/archive/delete)
- Snapshot read/write with **optimistic concurrency** using **ETag / If-Match**
- Dataset-level ACL (Owner/Editor/Viewer)
- Automatic DB migrations using **Flyway** (run on startup in dev; safe pattern for prod noted)
- PostgreSQL persistence (store snapshot payload in DB for Phase 1)

**Repository:** `java-modeller-server` (GitHub)

---

## 1. Technical choices (Phase 1 baseline)
### 1.1 Frameworks / stack
- **Quarkus**: REST API + security integration + good packaging for RHEL/JBoss-style environments
- **PostgreSQL**: primary data store for dataset metadata, ACL, snapshots
- **Keycloak (OIDC)**: identity provider; server acts as an OIDC resource server
- **Flyway**: schema migrations; applied automatically

### 1.2 API style
- **REST + JSON**
- **OpenAPI** published from server (code-first DTOs; stable response shapes)

### 1.3 Concurrency
- **ETag / If-Match** is the canonical concurrency mechanism:
  - `GET snapshot` returns `ETag: "<revision>"`
  - `PUT snapshot` requires `If-Match: "<revision>"`
  - mismatch → **CONFLICT** response (include current revision + updatedBy/updatedAt)

### 1.4 Persistence model
- Store snapshot payload in Postgres as:
  - `jsonb` (preferred if you may query parts later), or
  - `bytea` (opaque blob) if you want maximal flexibility.
- Phase 1 recommendation: **jsonb** + a server-enforced `schemaVersion` field at the top-level payload.

### 1.5 Validation policy
- Phase 1: **structural validation only**
  - parseable JSON
  - required top-level fields (at minimum: `schemaVersion` and model root)
  - size limits

---

## 2. Assumptions
- You have a Keycloak realm available (dev realm is fine).
- Users authenticate externally (server validates bearer tokens).
- Phase 1 supports central storage and safe writes, not realtime collaboration.
- One server instance initially; multi-replica deployment can be supported later (see Step 11).

---

## 3. Deliverables
- A Quarkus project in repo `java-modeller-server`
- Flyway migrations for all Phase 1 tables
- REST API implementing Phase 1 endpoints and rules
- OIDC security configuration + local dev profile
- Integration tests (happy path + authz + conflict)
- OpenAPI endpoint exposed
- Basic operational docs (`README.md`): run locally, env vars, Keycloak setup notes

---

## 4. Step-by-step plan (each step is “one prompt” sized)

### Step 1 — Create Quarkus project skeleton + module layout
**Deliverables**
- Initial Quarkus app (REST) checked into `java-modeller-server`
- Package layout created:
  - `api/` (REST resources + DTOs)
  - `domain/` (behavior rules; no Quarkus annotations ideally)
  - `persistence/` (entities + repositories)
  - `security/` (principal extraction, authz helpers)
  - `migrations/` (Flyway scripts)
  - `tests/` (integration tests)

**Verification**
- `./mvnw test` (or `mvn test`) succeeds
- `./mvnw quarkus:dev` starts

---

### Step 2 — Add PostgreSQL connectivity + dev services
**Deliverables**
- Datasource config (dev profile can use local Postgres or dev services)
- Baseline `application.properties` with:
  - datasource
  - logging
  - OpenAPI enabled

**Verification**
- Server starts and can connect to DB
- Health endpoint responds

---

### Step 3 — Add Flyway + first migration baseline
**Deliverables**
- Flyway enabled, migrations run automatically at startup (dev/test)
- Migration folder established (e.g. `db/migration`)

**Migration V1 creates tables (minimal Phase 1):**
- `datasets`
- `dataset_acl`
- `dataset_snapshots` (current snapshot per dataset)
- `audit_log` (minimal: datasetId, action, actor, time, fromRev, toRev)

**Verification**
- Starting server applies Flyway migrations cleanly
- Re-start does not reapply (Flyway reports up-to-date)

---

### Step 4 — Define Phase 1 data model (domain + persistence)
**Deliverables**
- Domain concepts:
  - Dataset metadata
  - Roles (OWNER/EDITOR/VIEWER)
  - Snapshot envelope (payload + schemaVersion + revision metadata)
- Persistence mapping (tables → entities)
- Repository layer for datasets, ACL, snapshots, audit

**Verification**
- Unit tests for domain validation (name constraints, role rules)
- Repository test (persist + load dataset)

---

### Step 5 — Add OIDC resource server security (Keycloak)
**Deliverables**
- OIDC bearer token validation configured
- Standard identity extraction:
  - `subject` (OIDC `sub`) as stable principal id
  - optionally username/email for display (if token provides it)
- “Current user” endpoint to validate auth wiring

**Verification**
- Anonymous calls to protected endpoints fail
- Valid token can call `/me` and returns principal info

---

### Step 6 — Authorization model (dataset ACL)
**Deliverables**
- ACL rules implemented:
  - Create dataset → creator becomes OWNER
  - Owners can grant/revoke roles
  - Editors can write snapshots
  - Viewers can read only
- Authorization helpers used by all dataset endpoints

**Verification**
- Integration tests:
  - viewer cannot write
  - editor cannot manage sharing
  - owner can grant/revoke

---

### Step 7 — Dataset management endpoints (CRUD + archive/delete)
**Deliverables**
- Endpoints supporting:
  - create dataset
  - list datasets visible to user
  - get dataset metadata
  - update metadata (policy: owner-only)
  - archive/unarchive
  - delete (soft-delete recommended)

**Behavior rules**
- Access control enforced for every call
- “Not found” behavior consistent with security policy

**Verification**
- Integration tests for each endpoint with role permutations

---

### Step 8 — Snapshot read endpoint (GET latest snapshot)
**Deliverables**
- `GET latest snapshot` endpoint:
  - returns snapshot payload + revision metadata
  - includes `ETag` header set to current revision
  - includes `updatedAt/updatedBy` in response body

**Verification**
- Test reads return correct ETag and payload

---

### Step 9 — Snapshot write endpoint (PUT snapshot with If-Match)
**Deliverables**
- `PUT snapshot` endpoint:
  - requires `If-Match` header
  - rejects if mismatch (conflict response includes current rev and who/when)
  - on success:
    - server increments revision (monotonic)
    - stores new snapshot
    - updates dataset metadata (updatedAt/updatedBy)
    - writes audit entry

**Verification**
- Integration tests:
  - correct revision increment
  - conflict when If-Match stale
  - no silent overwrite

---

### Step 10 — Optional: Snapshot history (minimal, Phase 1-friendly)
**Deliverables**
- Decide one of:
  - Phase 1: keep only latest snapshot (simplest)
  - Phase 1+: keep N previous snapshots (recommended if easy)
- If enabling history:
  - table `dataset_snapshot_history` with revision and payload
  - endpoint to list history (optional for Phase 1)

**Verification**
- Tests if enabled

---

### Step 11 — Ops & deployment posture (migrations + multi-instance note)
**Deliverables**
- Document “automatic migrations” strategy:
  - Dev/Test: migrate on startup
  - Prod: either migrate on startup (with DB locking) OR run a separate migration job during deployment
- Add basic limits:
  - max payload size
  - max datasets per user (optional)
  - rate limiting (optional Phase 1)

**Verification**
- Start server twice concurrently (if you test this) does not corrupt migrations
- Oversized payload returns clear error

---

### Step 12 — OpenAPI + API stability hardening
**Deliverables**
- OpenAPI is available and reflects endpoints + schemas
- Standard error response shape:
  - machine code + message + details
- Document key usage patterns:
  - read snapshot → get ETag
  - write snapshot with If-Match
  - handle conflict

**Verification**
- OpenAPI endpoint loads
- Error shapes stable in tests

---

## 5. Minimal Phase 1 endpoint catalog (behavioral)
(Names are illustrative; keep consistent across implementation.)

### Auth
- `GET /me` → current principal

### Datasets
- `POST /datasets`
- `GET /datasets`
- `GET /datasets/{datasetId}`
- `PATCH /datasets/{datasetId}`
- `POST /datasets/{datasetId}:archive`
- `POST /datasets/{datasetId}:unarchive`
- `DELETE /datasets/{datasetId}` (soft-delete recommended)

### ACL
- `GET /datasets/{datasetId}/acl`
- `POST /datasets/{datasetId}/acl/grant`
- `POST /datasets/{datasetId}/acl/revoke`

### Snapshots
- `GET /datasets/{datasetId}/snapshot` (returns ETag)
- `PUT /datasets/{datasetId}/snapshot` (requires If-Match)

---

## 6. Verification checklist (Phase 1 “definition of done”)
- Flyway migrations run cleanly on fresh DB and on restart
- Keycloak-protected endpoints require token
- Dataset ACL enforced for all operations
- Snapshot writes require If-Match and reject conflicts
- OpenAPI is available
- Integration tests cover:
  - auth required
  - role enforcement
  - optimistic concurrency conflict
  - dataset CRUD
