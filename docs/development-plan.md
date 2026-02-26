# Development Plan — Phase 1 Server MVP (Plan A) for `java-modeller-server`

## 0. Goal
Build the Phase 1 **Central Modeling Server** that provides:
- **Keycloak (OIDC)** protected REST API
- Dataset CRUD + dataset-level ACL (Owner/Editor/Viewer)
- Snapshot **read/write** with **optimistic concurrency** via **ETag / If-Match**
- PostgreSQL persistence
- **Flyway** automatic schema migrations
- OpenAPI documentation + stable error contract
- Integration tests covering auth, authz, and conflict handling

---

## 1. Technology choices

### 1.1 Quarkus
**Why**
- Strong fit with your existing Java ecosystem and RHEL/JBoss-style deployment patterns.
- First-class support for OIDC resource server, REST endpoints, OpenAPI, and Dev Services.

### 1.2 PostgreSQL
**Why**
- Natural fit for relational metadata (datasets, ACL) and durable storage of snapshots + audit.
- Good concurrency and locking behavior for revision updates.

### 1.3 Keycloak (OIDC)
**Why**
- Standard enterprise identity provider; tokens include stable `sub` (subject) for user identity.
- Clean separation: server validates tokens, stores its own dataset ACL.

### 1.4 Flyway
**Why**
- Predictable, reviewable versioned migrations.
- Easy to run automatically at application startup (dev/test), and can be run as a separate migration job in prod.

### 1.5 REST + JSON + OpenAPI
**Why**
- Phase 1 operations are straightforward and map cleanly to REST.
- OpenAPI gives stable contracts for the PWA’s RemoteDatasetBackend integration.

### 1.6 Concurrency: ETag / If-Match
**Why**
- Standard HTTP optimistic concurrency.
- Prevents silent overwrites and scales to multi-user async editing in Phase 2.

---

## 2. Assumptions and constraints
- Phase 1 is **central storage** and **safe writes**, not real-time collaboration.
- Access control is **dataset-level** only.
- Snapshots are treated as **opaque JSON** by default; server enforces only structural validation in Phase 1.
- Primary deployment may be single-instance initially, but the design should not prevent multiple instances later.

---

## 3. Project structure (recommended)
Single Maven module (Phase 1) with clear package boundaries:

```
java-modeller-server/
  src/main/java/...
    api/            # JAX-RS resources + request/response DTOs + exception mappers
    domain/         # business rules, role checks, validation, revision semantics
    persistence/    # JPA entities, repositories, transactions
    security/       # principal extraction, authz helpers
    util/           # shared helpers (time, ids)
  src/main/resources/
    application.properties
    db/migration/   # Flyway migrations
  src/test/java/...
    it/             # integration tests (QuarkusTest + testcontainers/devservices)
```

**Boundary rule of thumb**
- `domain` must not depend on Quarkus annotations.
- `api` is thin: maps HTTP ↔ domain services.
- `persistence` handles transactions and DB mapping.

---

## 4. Quarkus extensions (Phase 1)
Use these (names are conceptual; pick the matching Quarkus extensions):
- REST: `quarkus-rest` (or RESTEasy Reactive depending on Quarkus version)
- JSON: `quarkus-rest-jackson` (or equivalent)
- OpenAPI: `quarkus-smallrye-openapi`
- Security OIDC: `quarkus-oidc`
- PostgreSQL JDBC: `quarkus-jdbc-postgresql`
- ORM: `quarkus-hibernate-orm` (JPA)
- Flyway: `quarkus-flyway`
- Health (optional but useful): `quarkus-smallrye-health`
- Testing: `quarkus-junit5` (+ Dev Services / Testcontainers via Quarkus)

---

## 5. Data model (Phase 1)

### 5.1 Tables and key columns (conceptual)
**datasets**
- `dataset_id` (UUID or string id)
- `name` (string)
- `description` (nullable)
- `status` (ACTIVE | ARCHIVED | DELETED)
- `created_at`, `created_by_sub`
- `updated_at`, `updated_by_sub`
- `current_revision` (bigint)

**dataset_acl**
- `dataset_id`
- `principal_sub` (OIDC subject)
- `role` (OWNER | EDITOR | VIEWER)
- `granted_by_sub`
- `granted_at`
- PK: (`dataset_id`, `principal_sub`)

**dataset_snapshots**
- `dataset_id` (PK)
- `revision` (bigint) — should equal datasets.current_revision
- `payload` (jsonb recommended)
- `schema_version` (int or string)
- `saved_at`
- `saved_by_sub`

**audit_log**
- `audit_id`
- `dataset_id`
- `action` (CREATE_DATASET | UPDATE_METADATA | SAVE_SNAPSHOT | GRANT_ROLE | REVOKE_ROLE | ARCHIVE | UNARCHIVE | DELETE)
- `actor_sub`
- `at`
- `from_revision` (nullable)
- `to_revision` (nullable)
- `details` (optional small json)

### 5.2 Revision behavior
- `current_revision` is monotonic.
- Snapshot save transaction:
  1) verify base revision matches current_revision
  2) increment revision
  3) write snapshot row
  4) update datasets row
  5) write audit entry  
  Must be atomic.

---

## 6. API contract (Phase 1, behavioral)

### 6.1 Authentication
- All endpoints except health/OpenAPI require a valid bearer token.
- `sub` from token is primary principal identifier.

### 6.2 Endpoints (illustrative)
**Auth**
- `GET /me` → returns `{ sub, preferredUsername?, email? }`

**Datasets**
- `POST /datasets` → create; creator becomes OWNER
- `GET /datasets` → list datasets current user can access
- `GET /datasets/{id}` → metadata
- `PATCH /datasets/{id}` → update metadata (Owner-only)
- `POST /datasets/{id}:archive` (Owner-only)
- `POST /datasets/{id}:unarchive` (Owner-only)
- `DELETE /datasets/{id}` (Owner-only; recommended soft delete)

**ACL**
- `GET /datasets/{id}/acl` (Owner-only by default; or Editor by policy)
- `POST /datasets/{id}/acl/grant` (Owner-only)
- `POST /datasets/{id}/acl/revoke` (Owner-only)

**Snapshots**
- `GET /datasets/{id}/snapshot`
  - returns JSON payload + metadata
  - includes `ETag: "<revision>"`
- `PUT /datasets/{id}/snapshot`
  - requires `If-Match: "<revision>"`
  - on success: returns new ETag and updated metadata
  - on mismatch: returns conflict with `currentRevision`, `updatedAt`, `updatedBy` (if allowed)

### 6.3 Error response shape (standard)
All errors return:
- `code` (machine readable)
- `message` (human readable)
- `details` (optional object)

Minimum codes:
- `AUTH_REQUIRED`, `FORBIDDEN`, `NOT_FOUND`,
- `CONFLICT`, `VALIDATION_FAILED`,
- `PAYLOAD_TOO_LARGE`, `INTERNAL_ERROR`

---

## 7. Step-by-step implementation plan (optimized for iterative LLM collaboration)

### Step 1 — Repo initialization + Quarkus skeleton
**Deliverables**
- New repo `java-modeller-server` with Quarkus skeleton
- Basic endpoints: health + `/me` stub (protected)

**Verification**
- `./mvnw test`
- `./mvnw quarkus:dev`

### Step 2 — PostgreSQL + Flyway baseline
**Deliverables**
- Datasource configured
- Flyway migrations folder and V1 migration creating Phase 1 tables
- Auto-migrate enabled in dev/test

**Verification**
- Start server on fresh DB → migrations applied
- Restart → no reapply

### Step 3 — Persistence layer (entities + repositories)
**Deliverables**
- JPA entities for datasets, acl, snapshots, audit
- Repository methods needed by endpoints

**Verification**
- Repository-focused tests: create/read/update

### Step 4 — Security: OIDC validation + principal extraction
**Deliverables**
- OIDC resource server enabled
- Principal info extracted (sub + optional username/email)
- `/me` returns current identity

**Verification**
- No token → 401/403
- Valid token → `/me` OK

### Step 5 — Domain services: authorization + dataset rules
**Deliverables**
- Role checks (Owner/Editor/Viewer)
- Rules: creator becomes Owner; cannot remove last owner; etc.

**Verification**
- Unit tests for role-rule edge cases

### Step 6 — Dataset CRUD endpoints + audit entries
**Deliverables**
- Dataset endpoints implemented
- Audit log written for each action
- Consistent “not found/forbidden” policy

**Verification**
- Integration tests for CRUD and permissions

### Step 7 — ACL endpoints (grant/revoke/list)
**Deliverables**
- Grant/revoke with invariants (last owner rule)
- List ACL entries

**Verification**
- Integration tests for role enforcement

### Step 8 — Snapshot read endpoint with ETag
**Deliverables**
- GET snapshot returns payload and sets ETag
- Includes updatedBy/updatedAt in body

**Verification**
- Test ETag matches revision in DB

### Step 9 — Snapshot write with If-Match conflict handling
**Deliverables**
- PUT snapshot requires If-Match
- Atomic revision bump + snapshot persist
- Conflict response includes current revision + last updater metadata

**Verification**
- Integration tests:
  - two saves with same base revision → second conflicts
  - no silent overwrites

### Step 10 — Hardening: size limits + stable error mapper + OpenAPI
**Deliverables**
- Request size limit configured
- Central exception mapper emits standard error shape
- OpenAPI endpoint documents all endpoints/schemas

**Verification**
- Oversized payload returns `PAYLOAD_TOO_LARGE`
- OpenAPI loads and matches endpoints

### Step 11 — Operational docs + dev Keycloak profile
**Deliverables**
- README: run Postgres, run Keycloak (dev), env vars
- Clarify migrations:
  - dev/test: migrate-at-start
  - prod: either migrate-at-start or separate migration job

**Verification**
- Fresh clone: follow README to run locally

---

## 8. Verification commands (baseline)
- Build/test: `./mvnw test`
- Run dev: `./mvnw quarkus:dev`
- (Optional) Format/lint: according to chosen tooling
- DB reset (dev): drop schema or recreate DB, then start server to re-run migrations

---

## 9. Expected outcome (Phase 1)
At the end of this plan:
- You can create datasets, grant access, and store/load snapshots centrally.
- Saves are safe with optimistic concurrency and explicit conflicts.
- DB evolves automatically via Flyway.
- The PWA can be integrated via a RemoteDatasetBackend (Plan B).
