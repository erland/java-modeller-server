# Functional specification — Central Modeling Server (PWA Modeller)

## 1. Purpose
Provide a central server that stores, serves, and governs **model datasets** used by the PWA Modeller. The server enables:
- Centralized persistence (replacing purely local browser storage)
- Controlled sharing and access across users
- Safe concurrent usage via versioning and conflict detection
- A foundation for asynchronous and (later) real-time multi-user collaboration

This specification is **technology-agnostic** and describes behavior, not implementation.

---

## 2. Scope

### 2.1 In scope
- Dataset management (create, list, read metadata, delete/archiving)
- Dataset content storage and retrieval (snapshots, later changes)
- Versioning and concurrency control
- Authentication and authorization
- Optional server-side validation gates (policy-driven)
- Audit and history (phase-dependent)
- Collaboration features (phase-dependent)

### 2.2 Out of scope (explicitly)
- UI implementation details in the client
- Any specific database, framework, message bus, or deployment model
- Any particular diagram layout engine execution location
- Direct editing tools in the server (server exposes APIs; client provides UX)

### 2.3 Core definitions
- **Dataset**: A named container of model content (elements, relationships, views/diagrams, etc.) plus metadata.
- **Snapshot**: A complete serialized dataset state at a point in time.
- **Revision**: A server-managed monotonic identifier representing the latest accepted dataset state (or change stream position).
- **User**: An authenticated identity interacting with the server.
- **Role**: Permission set granted per dataset (e.g., Owner, Editor, Viewer).
- **Client**: PWA Modeller or other tools using the API.
- **Conflict**: A write attempt against a stale revision.

---

## 3. Actors and permissions

### 3.1 Actors
- **Anonymous**: Not authenticated.
- **Authenticated user**: Logged in user.
- **Dataset owner**: User who created the dataset or was granted ownership.
- **Dataset editor**: Can read and modify dataset content.
- **Dataset viewer**: Can read dataset content.
- **Administrator** (optional): Can manage users/datasets globally (policy-driven).

### 3.2 Roles and capabilities (minimum set)
- **Owner**
  - All Editor + manage dataset settings, sharing, delete/archive, transfer ownership
- **Editor**
  - Read dataset metadata and content
  - Write dataset content (phase-dependent method)
  - View history (if enabled)
- **Viewer**
  - Read dataset metadata and content
  - Cannot write
- **Admin** (optional)
  - Global visibility and management actions

### 3.3 Authorization rules
- Every dataset operation MUST check permissions.
- Dataset content is never readable without at least Viewer.
- Dataset writes are never allowed without Editor.
- Sharing/role changes are Owner-only (or Admin).

---

## 4. Data model (conceptual)

### 4.1 Dataset metadata
A dataset has:
- `datasetId` (server-generated, immutable)
- `name` (human-readable; unique within an owner scope or globally by policy)
- `description` (optional)
- `createdAt`, `createdBy`
- `updatedAt`, `updatedBy`
- `currentRevision` (server-managed)
- `status` (Active | Archived | Deleted) — deletion may be soft-delete by policy
- `tags` (optional, for discovery)
- `sizeInfo` (optional, e.g., last snapshot size)
- `validationPolicy` (optional; see section 8)

### 4.2 Dataset content
Dataset content is a single logical object consisting of:
- Model entities (elements, relationships)
- Views/diagrams (including view geometry)
- Any dataset-scoped derived indexes if the product chooses to store them (optional)
- Content MUST be treated as an opaque payload by default; the server may optionally inspect it for validation.

### 4.3 Identity and access
- User identities and role assignments per dataset.
- Share entries: (datasetId, principalId, role, grantedBy, grantedAt)

### 4.4 History (phase-dependent)
Depending on phase:
- Snapshot history (store prior snapshots)
- Change history (store a log of commands/patches)
- Audit trail (store who changed what and when)

---

## 5. API capabilities (behavioral requirements)

> Note: This section defines required behaviors; the exact protocol/paths are not mandated.

### 5.1 Authentication
- The server MUST support an authentication mechanism to identify users.
- The server MUST reject protected endpoints for anonymous users.
- The server MUST provide a way for the client to determine the current user identity and session state.

**Errors**
- Unauthenticated: return an authentication-required error.
- Invalid/expired session: return an authentication error with remediation guidance.

### 5.2 Dataset discovery and management
Required behaviors:
- Create dataset
- List datasets visible to the current user
- Read dataset metadata
- Update dataset metadata (name/description/tags) — Owner (or Editor by policy)
- Archive/unarchive dataset — Owner
- Delete dataset — Owner (or Admin); may be soft-delete

**Rules**
- Dataset IDs are immutable.
- Name constraints (length, characters) are enforced deterministically.
- Listing may be filtered/sorted (by name, updatedAt, tag) — optional in Phase 1, recommended by Phase 2.

**Edge cases**
- Attempt to access dataset not visible to user → behave as “not found” or “forbidden” per security policy (must be consistent).

### 5.3 Dataset content read (snapshot read)
Required behaviors:
- Fetch the latest dataset content snapshot
- Fetch dataset content at a specific revision (if history enabled)
- Return the `currentRevision` with reads

**Rules**
- Reads MUST be consistent: returned content corresponds to the returned revision.
- If requested revision does not exist → return a deterministic not-found error.

### 5.4 Dataset content write (phase-dependent)

#### Phase 1–2: Snapshot write with optimistic concurrency
Required behaviors:
- Client submits a complete snapshot payload as the desired next state.
- Client includes the revision it is based on (the “base revision”).
- Server accepts write only if base revision equals current revision.
- On acceptance:
  - Server assigns a new revision
  - Updates metadata (updatedAt/updatedBy)
  - Optionally stores previous snapshot in history
  - Returns new revision and updated metadata
- On conflict:
  - Server rejects with a conflict error including the current revision and optionally a summary to help client decide (e.g., “changed by X at time Y”).

**Rules**
- The server MUST be the only authority assigning revisions.
- Conflicting writes MUST NOT overwrite current content.

#### Phase 3: Command/operation-based writes + real-time sync (see 6.3)
Required behaviors:
- Client submits one or more operations/commands referencing a base revision.
- Server validates, applies, and advances revision.
- Server broadcasts accepted changes to subscribers.

### 5.5 Dataset sharing and access control
Required behaviors:
- Owner can grant Viewer/Editor/Owner roles to other users/groups.
- Owner can revoke access.
- Owner can list who has access and their roles.
- Optional: invite-by-email workflow (policy-driven).

**Rules**
- Cannot remove the last Owner unless ownership is transferred.
- Role changes are audited (who granted/revoked).

### 5.6 Dataset export and import
Required behaviors:
- Export dataset snapshot as a portable bundle (format may be opaque).
- Import a snapshot to:
  - Create a new dataset, or
  - Replace an existing dataset (Owner-only), respecting concurrency rules.

**Rules**
- Imports must be validated for structural integrity (at minimum: parseable and not exceeding limits).
- Import replacement must follow the same concurrency contract (base revision).

### 5.7 Search and indexing (optional, phase-dependent)
- Phase 1: not required
- Phase 2: optional server-side indexing for dataset discovery and basic content metadata
- Phase 3: optional advanced indexing (element search, full text), not required for collaboration

---

## 6. Collaboration model by phase

### 6.1 Phase 1 — Central storage for single-user semantics
Goal: Replace local persistence with central persistence safely.

Capabilities:
- Authentication
- Dataset CRUD (create/list/read/update metadata/archive/delete)
- Read latest snapshot
- Write snapshot with optimistic concurrency
- Basic sharing (at least per-user; group support optional)
- Minimal audit fields (created/updated by/time)
- Optional: snapshot history (limited retention)

Non-goals:
- Real-time multi-user editing
- Server-side merge
- Operation log / command replay

Client expectations:
- Client loads dataset → receives snapshot + revision
- Client edits locally
- Client saves snapshot with base revision
- If conflict → client must choose to reload or force overwrite (force overwrite only if policy allows and user has role)

### 6.2 Phase 2 — Multi-user safe, asynchronous collaboration
Goal: Allow multiple users to work without silent overwrites; enable recovery and traceability.

Additional capabilities:
- Stronger audit trail (who changed what dataset and when)
- Configurable retention of snapshot history
- Conflict UX support data:
  - conflict responses include “last updated by/at”
  - optional server-provided diff summary (high-level, not required)
- Optional: soft locks / leases (see section 7.3) to reduce conflicts
- Optional: per-dataset validation policies (server-side validation gating)

Non-goals:
- Real-time cursor presence
- Live updates while editing (push is optional, not required)

Client expectations:
- Client can detect remote changes (poll or manual refresh)
- Conflicts are handled explicitly
- Users can review history and restore a prior snapshot (Owner-only or Editor by policy)

### 6.3 Phase 3 — Real-time collaboration (optional future)
Goal: Provide near-real-time shared editing.

Additional capabilities:
- Operation/command-based write API (instead of full snapshots)
- Real-time subscription channel per dataset for:
  - accepted operations
  - revision updates
  - presence (optional)
- Server-side validation of operations and invariants
- Optional: branching or sessions (not required)

Non-goals:
- Guaranteed offline-first CRDT unless explicitly chosen later

Client expectations:
- Client maintains a local view synchronized via operations
- Conflicts are minimized by server ordering; client replays as needed

---

## 7. Deterministic behavior rules

### 7.1 Concurrency and revisioning
- Every dataset has exactly one `currentRevision`.
- Reads return the revision they correspond to.
- Writes must include `baseRevision`.
- If `baseRevision != currentRevision` → reject with Conflict.
- Server MAY support a privileged “force write” for Owners/Admins; if supported it MUST:
  - be explicit (separate flag)
  - be audited
  - return a new revision

### 7.2 Validation and integrity
Minimum integrity checks (all phases):
- Payload must be parseable
- Must not exceed size limits
- Must include required top-level fields per server’s accepted dataset schema version(s)

Optional semantic validation (Phase 2+):
- Server may validate according to a dataset’s `validationPolicy`
- Validation failures return structured errors with:
  - severity (error/warning)
  - location/path if possible
  - message and rule identifier

### 7.3 Locks / leases (optional, Phase 2+)
If implemented:
- An Editor may acquire a time-limited lease on a dataset.
- While leased:
  - Other Editors can still read
  - Writes by others are rejected or require explicit override depending on policy
- Leases auto-expire; Owner/Admin can revoke.

### 7.4 Limits and quotas
The server MUST enforce configurable limits to protect stability:
- Max dataset size
- Max request size
- Rate limits (per user / per dataset)
- Max datasets per user (optional)
- Retention caps for history (Phase 2+)

Errors must be deterministic and include the violated limit.

---

## 8. Validation policy (optional but recommended)

A dataset may have a `validationPolicy`:
- `none` — only structural checks
- `basic` — schema + minimal invariants
- `strict` — enforce full domain validation rules

Policy effects:
- On write, if validation fails at “error” level, the write is rejected.
- Warnings may be returned without rejecting, depending on policy.

---

## 9. Audit, history, and restore

### 9.1 Audit (all phases, minimum)
For accepted writes, the server records:
- who performed the action
- when it occurred
- previous revision and new revision
- datasetId

### 9.2 History (Phase 2+ recommended)
- Server stores a configurable number/time range of prior snapshots.
- Server provides:
  - list history entries (revision, timestamp, user, optional message)
  - fetch snapshot at revision
  - restore a prior snapshot to become the latest revision (subject to permissions and concurrency)

### 9.3 Change log (Phase 3)
If operation-based:
- Server stores the operation stream with sequencing and authorship.
- Server can rebuild the latest state from operations (optional but recommended for robustness).

---

## 10. Notifications and subscriptions

### 10.1 Phase 1
- Not required.
- Optional: server returns “lastUpdatedAt/by” in metadata and on conflict.

### 10.2 Phase 2
- Optional: endpoint to check if dataset has changed since a given revision.

### 10.3 Phase 3
- Required: real-time subscription for dataset revision updates and accepted operations.
- Optional: presence channel (who is viewing/editing).

---

## 11. Error handling requirements

All errors MUST be:
- machine-readable (code)
- human-readable (message)
- optionally include details (field/path/ruleId)

Minimum error codes (conceptual):
- `AUTH_REQUIRED`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_FAILED`
- `CONFLICT`
- `PAYLOAD_TOO_LARGE`
- `RATE_LIMITED`
- `INTERNAL_ERROR`

Conflict errors MUST include:
- `currentRevision`
- `updatedAt`
- `updatedBy` (if policy allows exposure)

---

## 12. Security and privacy requirements (non-functional)
- All traffic must be protected in transit.
- Authentication tokens/secrets must not be exposed in logs.
- Authorization checks must be performed server-side for every request.
- Least-privilege defaults: new datasets are private to creator unless shared.
- Audit logs must be protected from unauthorized access.
- If the server supports sharing via groups, group membership must be resolved securely.

---

## 13. Reliability and performance requirements (non-functional)
- Deterministic behavior for concurrency and validation.
- Data durability: accepted writes must not be acknowledged before durable storage.
- Availability target is policy-driven; server must degrade gracefully:
  - clear errors when unavailable
  - no partial writes
- Large datasets: server should support streaming upload/download behaviors where applicable (behavioral requirement: client can upload/download large snapshots within configured limits).

---

## 14. Compatibility requirements
- Server must support dataset schema versioning:
  - Accept a defined set of versions
  - Reject unknown versions with an actionable error
  - Optionally provide migration assistance (not required)
- Backward compatibility policy must be explicit (e.g., support last N versions).

---

## 15. Acceptance criteria by phase

### 15.1 Phase 1 acceptance criteria
- Authenticated users can create and list datasets they have access to.
- Users can load latest snapshot + revision.
- Users can save snapshot with base revision; server increments revision.
- Conflicting save is rejected and does not overwrite.
- Basic sharing (grant/revoke Viewer/Editor) works.
- Metadata shows created/updated by/time.

### 15.2 Phase 2 acceptance criteria
- History listing and restore are available (policy-defined permissions).
- Conflict responses provide “who/when” to support user decisions.
- Optional leases/locks reduce conflicts if enabled.
- Optional validation policies can block invalid saves.
- Auditing is queryable by Owners/Admins.

### 15.3 Phase 3 acceptance criteria
- Operation-based writes are supported and advance revisions deterministically.
- Real-time subscribers receive updates in order.
- Multiple editors can work concurrently with minimal disruption.
- Server enforces permissions and validation for operations.


---

## 6.3 Operation log and real-time sync

### Goals
Enable near real-time multi-user collaboration by allowing clients to submit **operations** against a dataset and to subscribe to a **stream** of accepted operations.

### Concepts
- **Revision**: monotonically increasing integer assigned by the server per dataset.
- **Operation**: a client-generated change command with a stable `opId` (idempotency key), a `type`, and a JSON `payload`.
- **Materialized state**: the server’s latest snapshot derived by applying accepted operations in order.

### Required behaviors
1. **Append operations**
   - Client submits one or more operations referencing a `baseRevision`.
   - Server MUST reject if `baseRevision` does not match current revision (conflict).
   - Server MUST ensure idempotency using `opId` (duplicate opId rejected or treated as already applied).
   - Server MUST assign revisions in strict order and persist an append-only log.
   - Server MUST materialize and persist updated latest snapshot for backwards compatibility with snapshot readers.

2. **Read operations since revision**
   - Client can request operations strictly after a given revision (pagination supported by `limit`).
   - Returned operations must be ordered by revision.

3. **Real-time subscription**
   - Client can subscribe to server-sent events (or equivalent) for a dataset.
   - Streamed events must be in revision order.
   - Delivery is best-effort; clients recover by re-reading from last known revision.

4. **Authorization + leases**
   - All operation endpoints MUST enforce dataset ACL roles.
   - If leases are enabled, operation appends MUST follow the same conflict policy as snapshot writes.

### Compatibility
- Snapshot-based clients remain supported: the server continues to expose the latest snapshot and revision/ETag.
