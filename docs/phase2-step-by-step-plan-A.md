# Phase 2 Step-by-step Plan (Plan A — java-modeller-server)

## Purpose
Implement **Phase 2** of the server functional specification for **multi-user safe collaboration**: richer conflict responses, optional server-side validation policies, optional dataset edit leases (soft locks), and snapshot history + restore hardening.

This plan is designed to be **self-contained** so you can paste it into a new chat and execute step-by-step.

---

## Starting state (baseline)
Repository: **java-modeller-server** (Quarkus / Java 17 / Maven) with:
- REST API for datasets, ACL, latest snapshot, snapshot history (Phase 1)
- PostgreSQL + Flyway migrations
- OIDC (Keycloak) support for dev; tests run with OIDC disabled
- Existing tests around datasets/ACL/snapshot concurrency/history

---

## Phase 2 scope (server-side)
### In scope
1. **Conflict enrichment**: on write conflicts, return `currentRevision`, `updatedAt`, `updatedBy` (and keep `ETag`).
2. **Validation policies (per dataset)**: configurable `validationPolicy` that can **gate writes**:
   - `none` (structural only)
   - `basic` (minimal invariants)
   - `strict` (full rules, as defined/available)
3. **Leases (soft locks)**: time-limited lease per dataset:
   - acquire/refresh, release, read lease status
   - enforce lease on writes (unless explicit override by OWNER/Admin)
4. **History + restore hardening**:
   - ensure history metadata is complete (`revision`, `createdAt`, `createdBy`, optional `message`)
   - add “restore revision as latest” endpoint with full auditing + concurrency semantics
   - retention: keep last N (already present) but verify behavior + indexes

### Out of scope (explicitly)
- Real-time presence/cursors
- Auto-merge
- Push updates (SSE/WebSockets) beyond basic polling-friendly endpoints

---

## Assumptions
- PostgreSQL is the backing store in all environments.
- Dataset access is controlled by ACL roles: `VIEWER < EDITOR < OWNER`.
- “Admin” (if any) is represented via a claim/role mapping in OIDC; if not available, OWNER is the top authority.
- Snapshot payload remains an **opaque JSON blob** to the server, except for structural checks and optional schemaVersion extraction.

---

## Deliverables
- New Flyway migrations for Phase 2 tables/columns/indexes.
- New/updated REST endpoints for leases, validationPolicy, restore.
- Updated conflict response format.
- Updated/expanded test coverage.
- Updated `docs/api-examples.md` with Phase 2 examples (optional but recommended).

---

# Step-by-step implementation plan

## Step 1 — Add Phase 2 database schema (Flyway)
**Goal:** Introduce columns/tables needed for validation policies, lease management, and restore metadata.

**Changes**
- Add Flyway migration `V4__phase2_validation_policy_and_leases.sql` (name can vary) to:
  - Add `validation_policy` to `datasets` (default `none`).
  - Create `dataset_lease` table:
    - `dataset_id` (PK/FK)
    - `leased_by` (subject)
    - `leased_at` (timestamp)
    - `expires_at` (timestamp)
    - optional `lease_token` (opaque string) if you want stronger client binding
  - Add indexes for lease expiry queries.
- If missing, ensure history table captures `created_by` and `created_at`; if not present add them.

**Deliverables**
- New migration file(s).
- Updated entity mappings if columns/tables are mapped.

**Verify**
- `mvn -q test` (or at least `mvn -q -DskipTests=false test`) locally.

---

## Step 2 — Introduce ValidationPolicy domain model + persistence mapping
**Goal:** Represent dataset validation policy consistently across API, domain and persistence.

**Changes**
- Add `ValidationPolicy` enum in `domain/` (or `api/dto/`) with allowed values: `NONE`, `BASIC`, `STRICT`.
- Map `datasets.validation_policy` in `DatasetEntity` and expose via DTOs:
  - include in dataset GET/list payloads
  - allow update via dataset update endpoint (Owner-only or by policy)

**Deliverables**
- Enum + mapping + DTO fields.
- Tests for validationPolicy serialization and persistence.

**Verify**
- `mvn -q test`

---

## Step 3 — Implement server-side validation pipeline (structural → policy)
**Goal:** Add a single validation entry point for snapshot writes.

**Changes**
- Create `SnapshotValidationService` with:
  - `validate(payloadJson, schemaVersion, policy) -> ValidationResult`
  - Structural checks always: JSON parseable, size limit, required top-level fields.
  - Policy checks:
    - `NONE`: pass if structural ok
    - `BASIC`: enforce minimal invariants (define from spec; keep deterministic)
    - `STRICT`: enforce stricter rules (can initially be same as BASIC if rules aren’t implemented yet, but must be explicit)
- Update snapshot write path to call validation service before persistence.

**Deliverables**
- Service + unit tests (pure).
- Update snapshot write tests to cover `VALIDATION_FAILED`.

**Verify**
- `mvn -q test`

---

## Step 4 — Define a deterministic validation error format
**Goal:** Ensure clients get machine-readable, consistent errors.

**Changes**
- Extend error DTO to support:
  - `code` (e.g., `VALIDATION_FAILED`)
  - `message`
  - `details.validationErrors[]` with:
    - `ruleId`
    - `severity` (`ERROR|WARNING`)
    - `path` (JSON pointer or dotted path)
    - `message`
- Ensure the exception mapper returns this format.

**Deliverables**
- Updated error DTO + mapper tests.
- Golden-ish JSON assertions in tests.

**Verify**
- `mvn -q test`

---

## Step 5 — Lease API: acquire/refresh/release/status
**Goal:** Provide optional soft locks/leases for coordination.

**Endpoints (suggested)**
- `POST /datasets/{id}/lease` acquire or refresh
- `GET /datasets/{id}/lease` status
- `DELETE /datasets/{id}/lease` release

**Rules**
- Only `EDITOR+` can acquire/refresh.
- Lease has TTL (config property, e.g., `modeller.lease.ttlSeconds`).
- Acquire:
  - if no lease or expired → create/replace with caller
  - if leased by caller → refresh expiry
  - if leased by other and not expired → `409 Conflict` with lease holder and expiry
- Release:
  - allowed for lease holder OR OWNER/Admin
- All actions audited.

**Deliverables**
- Resource/controller + repository + entity.
- Tests for acquire conflict, refresh, expiry, and release.

**Verify**
- `mvn -q test`

---

## Step 6 — Enforce leases on snapshot writes (without replacing revision checks)
**Goal:** Reduce collisions while still relying on optimistic concurrency.

**Rules**
- If a valid (non-expired) lease exists:
  - writes allowed only by lease holder
  - OWNER/Admin may override only with an explicit flag/header/query param (e.g., `?force=true`)
- If no lease or lease expired:
  - normal write rules apply (revision/ETag still required)

**Deliverables**
- Snapshot write path updated.
- New tests:
  - non-holder blocked
  - holder allowed
  - override allowed for OWNER (if implemented)
  - audit entry for overrides

**Verify**
- `mvn -q test`

---

## Step 7 — Enrich conflict responses for snapshot writes
**Goal:** On conflict, return enough information for the client to present “who changed it and when”.

**Changes**
- When returning conflict from snapshot PUT, include:
  - `currentRevision`
  - `updatedAt`
  - `updatedBy`
  - `ETag` header for current snapshot
- Ensure metadata is filled on every successful write (already partly present).

**Deliverables**
- Updated conflict response DTO and tests.

**Verify**
- `mvn -q test`

---

## Step 8 — Add a lightweight “head” endpoint for polling
**Goal:** Let clients cheaply detect remote changes (Phase 2 client expectation).

**Endpoint (suggested)**
- `GET /datasets/{id}/head`
  - returns `currentRevision`, `updatedAt`, `updatedBy`, and `ETag` (no payload)

**Deliverables**
- New endpoint + tests.

**Verify**
- `mvn -q test`

---

## Step 9 — Implement “restore snapshot revision” endpoint
**Goal:** Recovery path using history (Phase 2).

**Endpoint (suggested)**
- `POST /datasets/{id}/snapshot/restore`
  - request: `{ "revision": <number>, "message": "optional restore reason" }`
  - behavior:
    - fetch payload at that revision from history
    - write it as the new latest snapshot (increment revision)
    - audit action as `SNAPSHOT_RESTORE`
    - enforce lease (same as write), and enforce role (EDITOR+ or stricter policy)

**Deliverables**
- Endpoint + service method.
- Tests:
  - restore success
  - restore non-existent revision → 404
  - restore requires permission
  - restore creates new revision and audit entry

**Verify**
- `mvn -q test`

---

## Step 10 — Harden history retention + indexing + metadata
**Goal:** Ensure history is reliable and performant.

**Changes**
- Confirm retention logic keeps last N per dataset deterministically.
- Add/verify indexes:
  - `(dataset_id, revision DESC)`
  - `(dataset_id, created_at DESC)`
- Ensure history rows store `created_by`, `created_at`, `message` (if used).

**Deliverables**
- Migration tweaks (if needed) + repository updates.
- Test verifying pruning behavior.

**Verify**
- `mvn -q test`

---

## Step 11 — Update docs with Phase 2 API examples
**Goal:** Keep the repo usable in a new chat / for future work.

**Changes**
- Update `docs/api-examples.md` with:
  - lease acquire/conflict/release
  - conflict response example including updatedBy/At
  - validation failure example
  - restore example

**Deliverables**
- Doc updates.

**Verify**
- N/A (doc-only), but run `mvn -q test` to ensure no accidental code break.

---

## Step 12 — Add an end-to-end “Phase 2 happy path” integration test
**Goal:** One test that exercises: create dataset → set policy → acquire lease → write snapshot → conflict on stale revision → restore.

**Deliverables**
- New test class under `src/test/java/...`.
- Uses existing TestDataFactory utilities.

**Verify**
- `mvn -q test`

---

## Notes on technical recommendations/framework choices
- Keep **Quarkus REST** + **Hibernate Panache** + **Flyway** for consistency with Phase 1.
- Use **optimistic concurrency** as the primary correctness mechanism; leases are optional coordination.
- Keep validation deterministic and server-controlled; return structured errors suitable for UI.
- Prefer small, focused services:
  - `LeaseService`
  - `SnapshotService`
  - `SnapshotValidationService`
  - Keep JAX-RS resources thin.

