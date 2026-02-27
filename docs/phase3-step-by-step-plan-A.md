# Phase 3 Step-by-step plan A — Server (operation-based writes + real-time sync)

This plan extends the server in this repository from **Phase 2 (snapshot + leases)** to **Phase 3 (operation/command-based writes + real-time sync)** as described in `docs/functional-specification.md` (§6.3).

## Assumptions

- Current server is Quarkus + JAX-RS + Hibernate/Panache-style repositories (as in Phase 1–2).
- Phase 2 endpoints remain supported for backwards compatibility during the migration window.
- Phase 3 real-time channel will be implemented using **Server-Sent Events (SSE)** first (simple, HTTP-friendly, easy behind nginx). WebSocket can be added later if needed.
- Operations are applied **sequentially** by the server; ordering is authoritative server-side.
- “Presence” is optional and can be deferred until after operations + subscriptions are stable.

## Definitions

- **Revision**: monotonically increasing integer per dataset (already exists via snapshot history/latest).
- **Operation**: a single deterministic command that mutates the model, with a payload.
- **Op batch**: one request containing one or more operations.

## High-level deliverables

- New persistence for operation log (append-only) and latest revision tracking.
- New REST API to append operations (with validation + auth) and query operations since a revision.
- New SSE endpoint to subscribe to accepted operations + revision updates.
- Tests: unit + resource/integration tests covering ordering, auth, and concurrency.

---

## Step 1 — Add persistence model for operation log

### Changes
- Add new DB table(s) and entity/repository:
  - `dataset_operation` (dataset_id, revision, op_id, op_type, payload_json, created_at, created_by)
- Add migration script in `src/main/resources/db` (consistent with existing migration approach).

### Deliverables
- New entity + repository (e.g. `DatasetOperationEntity`, `DatasetOperationRepository`).
- Migration included and runs in tests/dev.

### Verification
- `./mvnw test` (repository tests)
- Start dev compose and verify DB contains new table.

---

## Step 2 — Define API DTOs for operations + stream events

### Changes
- Add DTOs under the existing API package conventions:
  - `OperationRequest` (opId, type, payload)
  - `AppendOperationsRequest` (baseRevision, operations[])
  - `AppendOperationsResponse` (newRevision, acceptedOperations[])
  - `OperationEvent` (datasetId, revision, opId, type, payload, createdAt, createdBy)
  - `OpsSinceResponse` (fromRevision, toRevision, operations[])
- Define a stable error model for:
  - `409 REVISION_CONFLICT` (baseRevision behind)
  - `400 VALIDATION_FAILED` (reuse Phase 2 validation error structure)
  - `403/404` authorization/not found

### Deliverables
- DTO classes + JSON serialization works in tests.

### Verification
- `./mvnw test` (DTO serialization tests if present)

---

## Step 3 — Implement “append operations” write endpoint

### Endpoint
- `POST /datasets/{datasetId}/ops`
  - Request: `AppendOperationsRequest`
  - Behavior:
    - Authorize caller (role must be EDITOR or OWNER)
    - Validate `baseRevision` equals current dataset revision
    - Validate each operation payload (syntactic) + invariants (domain rules)
    - Apply operations sequentially to produce new model state
    - Persist:
      - append op rows with assigned revisions
      - update “latest snapshot” (either recompute snapshot or apply delta) depending on strategy in Step 4
    - Emit events to subscribers (Step 6)
    - Return `AppendOperationsResponse`

### Deliverables
- New resource class (e.g. `DatasetOpsResource`) + service layer (e.g. `DatasetOpsService`).
- Error mapping matches DTO in Step 2.

### Verification
- Integration test:
  - append ops at correct baseRevision advances revision
  - wrong baseRevision yields 409 with expected body

---

## Step 4 — Decide and implement server-side state materialization strategy

Pick one (document choice in code + docs):
- **Strategy A (recommended initially):** keep storing full snapshots as today; when ops are appended, server applies ops to the latest snapshot and writes a new snapshot + history row.
- **Strategy B:** store only ops and periodically compact to snapshots.

### Deliverables
- Implementation of chosen strategy in `DatasetOpsService`.
- Ensure existing snapshot endpoints remain correct and reflect op-applied state.

### Verification
- Existing Phase 2 snapshot tests still pass.
- New test: after `POST /ops`, `GET /snapshot` returns updated model.

---

## Step 5 — Implement “ops since revision” read endpoint

### Endpoint
- `GET /datasets/{datasetId}/ops?fromRevision=<n>&limit=<m>`
  - Authorize caller (VIEWER+)
  - Return ordered operations with revisions > fromRevision

### Deliverables
- Endpoint backed by `DatasetOperationRepository` with pagination.
- Stable ordering and deterministic output.

### Verification
- Integration test: append 3 ops then fetch fromRevision=0 returns 3 in order.

---

## Step 6 — Implement SSE subscription channel per dataset

### Endpoint
- `GET /datasets/{datasetId}/events` (SSE)
  - Authorize caller (VIEWER+)
  - Emits events:
    - `op` event: `OperationEvent`
    - `revision` event: lightweight `{revision}` (optional)
    - `presence` (optional later)

### Changes
- Add an in-memory subscriber registry keyed by datasetId.
- On accepted ops (Step 3), broadcast events to all subscribers.

### Deliverables
- SSE resource + broadcaster service.
- Basic keepalive ping (e.g. comment event) to avoid idle timeouts behind proxies.

### Verification
- Resource test using Quarkus test client:
  - open SSE stream, append ops, observe events delivered.

---

## Step 7 — Concurrency + ordering guarantees

### Changes
- Ensure two concurrent `POST /ops` requests do not interleave incorrectly:
  - Use DB transaction with row-level locking on dataset “latest” row, or
  - Use optimistic concurrency with retry based on revision check.
- Ensure revision assignment is strictly increasing and consistent.

### Deliverables
- Concurrency-safe implementation.
- Test with two parallel writers where one gets 409 or retries deterministically.

### Verification
- `./mvnw test` includes concurrency test.

---

## Step 8 — Authorization and audit integration

### Changes
- Write audit entries for:
  - ops appended (count, types)
  - subscription opened (optional)
- Ensure role rules:
  - VIEWER: read snapshot, head, history, ops since, subscribe
  - EDITOR: append ops (requires lease token policy decision; see Step 9)
  - OWNER: append ops, force behaviors (if needed)

### Deliverables
- Authorization service updates if required.
- Audit repository entries.

### Verification
- Tests for 403 on viewer append.

---

## Step 9 — Lease interaction policy for Phase 3

Choose and implement one policy (documented):
- **Policy A:** leases still required for append-ops writes (recommended initially to match Phase 2 mental model).
  - Require `X-Lease-Token` header on `POST /ops` when lease active.
- **Policy B:** leases not used for ops; conflicts handled purely via revision ordering.

### Deliverables
- Server enforcement consistent with chosen policy.
- Error code for missing/invalid lease token aligned with Phase 2 (`428 LEASE_TOKEN_REQUIRED`).

### Verification
- Test: append ops without token when required returns 428.

---

## Step 10 — Update docs and provide compatibility notes

### Changes
- Update `docs/api-examples.md` with:
  - append ops example
  - ops since example
  - SSE subscribe example (curl)
- Update `docs/api-stability.md` with:
  - Phase 2 snapshot API still supported
  - recommended client migration path

### Verification
- Manual: run compose, try curl examples.

---

## Step 11 — End-to-end demo scenario

### Deliverables
- A runnable script or doc snippet demonstrating:
  1) Client A subscribes
  2) Client B appends ops
  3) Client A receives ops and sees revision advance
- This can be a `docs/phase3-e2e-demo.md` or an integration test.

### Verification
- Manual acceptance in dev environment.

---

## Verification commands (quick)

- Unit + integration tests: `./mvnw test`
- Dev run (example): `docker compose -f docker-compose.dev.yml up --build`
