#!/usr/bin/env bash
set -euo pipefail

# Phase 3 E2E demo helper
#
# This script prints (and partially executes) the commands used in docs/phase3-e2e-demo.md.
# It will:
#   - obtain an access token
#   - create a dataset (validationPolicy=NONE)
#   - acquire a lease
#   - append two operations
#   - fetch ops since revision 0
#   - fetch snapshot
#
# It DOES NOT keep an SSE connection open; use the curl command printed by this script
# in a separate terminal to watch streamed events.

BASE_URL=${BASE_URL:-http://localhost}
USERNAME=${USERNAME:-user}
PASSWORD=${PASSWORD:-change-me-user}
CLIENT_ID=${CLIENT_ID:-modeller-dev-cli}

echo "BASE_URL=$BASE_URL"

echo "==> Getting token"
TOKEN=$(curl -s \
  -X POST "$BASE_URL/auth/realms/modeller/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "grant_type=password" \
  -d "client_id=$CLIENT_ID" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD" \
  | jq -r .access_token)

if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "ERROR: Could not obtain access token. Check Keycloak, user credentials, and client." >&2
  exit 1
fi

echo "TOKEN=${TOKEN:0:16}..."

echo "==> Creating dataset"
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

echo "==> Acquiring lease"
LEASE_TOKEN=$(curl -s \
  -X POST "$BASE_URL/api/datasets/$DATASET_ID/lease" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"ttlSeconds": 300}' \
  | jq -r .token)

echo "LEASE_TOKEN=${LEASE_TOKEN:0:10}..."

echo
echo "==> SSE subscribe command (run this in Terminal A)"
echo "curl -N -H \"Authorization: Bearer $TOKEN\" \"$BASE_URL/api/datasets/$DATASET_ID/ops/stream?fromRevision=0\""
echo

echo "==> Appending ops (Terminal B)"
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

echo "==> Ops since revision 0"
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/datasets/$DATASET_ID/ops?fromRevision=0&limit=100" \
  | jq

echo "==> Snapshot"
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/datasets/$DATASET_ID/snapshot" \
  | jq

echo
echo "Done. Dataset: $DATASET_ID"
