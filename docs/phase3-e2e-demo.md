# Phase 3 end-to-end demo scenario (Ops + SSE)

This demo shows an end-to-end Phase 3 flow:

1) **Client A** subscribes to a dataset SSE stream
2) **Client B** appends operations to the same dataset
3) **Client A** receives operation events and can reconcile via `ops since`

The steps below are written for the **edge nginx topology** (recommended) where:

- Keycloak is at `http://localhost/auth/`
- API is at `http://localhost/api/`

If you run Quarkus directly without nginx, replace the base URL accordingly.

---

## Prereqs

- Docker
- JDK 21
- `curl` and `jq`

---

## 0) Start the stack

### Option A: Dev stack (nginx + keycloak + postgres in Docker, Quarkus locally)

```bash
docker compose -f docker-compose.edge.dev.yml up -d
./mvnw quarkus:dev
```

### Option B: Test stack (everything in Docker)

```bash
docker compose -f docker-compose.edge.test.yml up -d --build
```

---

## 1) Get an access token

The Keycloak realm includes a dev CLI client that supports password grant.

```bash
BASE_URL=http://localhost

TOKEN=$(curl -s \
  -X POST "$BASE_URL/auth/realms/modeller/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=modeller-dev-cli' \
  -d 'username=user' \
  -d 'password=change-me-user' \
  | jq -r .access_token)

echo "TOKEN=${TOKEN:0:16}..."
```

If you do not have the `user` account provisioned, either:

- enable provisioning via `keycloak/dev-users.list`, or
- create a user manually in the Keycloak Admin Console (`/auth/admin/`).

---

## 2) Create a dataset

Create a dataset with `validationPolicy=NONE` so the demo can use a minimal snapshot shape.

```bash
DATASET_ID=$(curl -s \
  -X POST "$BASE_URL/api/datasets" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
        "name": "Phase3 demo dataset",
        "description": "Ops + SSE demo",
        "validationPolicy": "NONE"
      }' \
  | jq -r .id)

echo "DATASET_ID=$DATASET_ID"
```

---

## 3) Acquire a lease (Phase 3 uses lease policy A)

Phase 3 appends require the lease token **when a lease is active**.

```bash
LEASE_TOKEN=$(curl -s \
  -X POST "$BASE_URL/api/datasets/$DATASET_ID/lease" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"ttlSeconds": 300}' \
  | jq -r .token)

echo "LEASE_TOKEN=${LEASE_TOKEN:0:10}..."
```

---

## 4) Terminal A: Subscribe to the SSE stream

Open **Terminal A** and run:

```bash
curl -N \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/datasets/$DATASET_ID/ops/stream?fromRevision=0"
```

You should see events in the format:

- `event: op`
- `data: { ... OperationEvent JSON ... }`

---

## 5) Terminal B: Append operations

In **Terminal B**, append a batch of ops.

This example first replaces the whole snapshot, then patches it.

```bash
curl -s \
  -X POST "$BASE_URL/api/datasets/$DATASET_ID/ops" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Lease-Token: $LEASE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
        "baseRevision": 0,
        "operations": [
          {
            "opId": "op-001",
            "type": "SNAPSHOT_REPLACE",
            "payload": {
              "meta": {"name": "demo"},
              "elements": [],
              "relationships": []
            }
          },
          {
            "opId": "op-002",
            "type": "JSON_PATCH",
            "payload": [
              {"op": "replace", "path": "/meta/name", "value": "demo-updated"}
            ]
          }
        ]
      }' \
  | jq
```

Expected:

- Response contains `newRevision: 2`
- Terminal A receives two `op` events (revisions 1 and 2)

---

## 6) Verify via `ops since` (recovery path)

Even if the SSE connection drops, the client can recover by fetching ops since its last applied revision.

```bash
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/datasets/$DATASET_ID/ops?fromRevision=0&limit=100" \
  | jq
```

---

## 7) Verify snapshot state

```bash
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/datasets/$DATASET_ID/snapshot" \
  | jq
```

You should see `meta.name` set to `demo-updated`.

---

## Cleanup

Stop the stack (and optionally wipe volumes):

```bash
docker compose -f docker-compose.edge.test.yml down -v
```
