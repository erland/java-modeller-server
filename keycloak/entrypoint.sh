#!/usr/bin/env bash
set -euo pipefail

KC_SERVER_URL="http://localhost:8080"
KC_REALM="modeller"
KC_USERS_FILE="${KC_DEV_USERS_FILE:-/opt/keycloak/dev-users.list}"

# PWA (PKCE) client URL settings (typically provided via keycloak.urls.env)
KC_PWA_ORIGIN="${KC_PWA_ORIGIN:-http://localhost:5173}"
KC_PWA_REDIRECT_BASE="${KC_PWA_REDIRECT_BASE:-http://localhost:5173/pwa-modeller/}"

IMPORT_DIR="/opt/keycloak/data/import"
REALM_TEMPLATE="${IMPORT_DIR}/modeller-realm.template.json"
REALM_FILE="${IMPORT_DIR}/modeller-realm.json"

# Ensure redirect base ends with a slash, then append wildcard
if [[ "${KC_PWA_REDIRECT_BASE}" != */ ]]; then
  KC_PWA_REDIRECT_BASE="${KC_PWA_REDIRECT_BASE}/"
fi
KC_PWA_REDIRECT_URI="${KC_PWA_REDIRECT_BASE}*"

# Generate realm import file from template (no secrets committed to git)
if [[ -f "${REALM_TEMPLATE}" ]]; then
  cp "${REALM_TEMPLATE}" "${REALM_FILE}"
  # Replace placeholders (safe for typical URLs)
  sed -i "s|__KC_PWA_ORIGIN__|${KC_PWA_ORIGIN}|g" "${REALM_FILE}"
  sed -i "s|__KC_PWA_REDIRECT_URI__|${KC_PWA_REDIRECT_URI}|g" "${REALM_FILE}"
  echo "Generated realm import: ${REALM_FILE}"
else
  echo "ERROR: Realm template not found at ${REALM_TEMPLATE}" >&2
  exit 1
fi

# Start Keycloak in the background (imports realm from /opt/keycloak/data/import)
"/opt/keycloak/bin/kc.sh" start-dev --import-realm &
KC_PID=$!

# Wait for Keycloak to be ready (master realm well-known should return 200)
echo "Waiting for Keycloak to be ready..."
for i in {1..60}; do
  if curl -fsS "${KC_SERVER_URL}/realms/master/.well-known/openid-configuration" >/dev/null 2>&1; then
    echo "Keycloak is ready."
    break
  fi
  sleep 1
done

# Login to admin CLI (kcadm)
"/opt/keycloak/bin/kcadm.sh" config credentials \
  --server "${KC_SERVER_URL}" \
  --realm master \
  --user "${KEYCLOAK_ADMIN}" \
  --password "${KEYCLOAK_ADMIN_PASSWORD}" >/dev/null

# Provision dev users from CSV-like file:
# username,password,email,firstName,lastName
if [[ -f "${KC_USERS_FILE}" ]]; then
  echo "Provisioning users from ${KC_USERS_FILE} into realm ${KC_REALM}..."
  tail -n +1 "${KC_USERS_FILE}" | while IFS= read -r line; do
    [[ -z "${line}" ]] && continue
    [[ "${line}" =~ ^# ]] && continue
    # allow header line
    if [[ "${line}" == "username,password,email,firstName,lastName" ]]; then
      continue
    fi
    IFS=',' read -r username password email firstName lastName <<< "${line}"

    # Create user if missing (idempotent)
    existing=$("/opt/keycloak/bin/kcadm.sh" get users -r "${KC_REALM}" -q username="${username}" | grep -o '"id" *: *"[^"]*"' | head -n1 | cut -d'"' -f4 || true)
    if [[ -z "${existing}" ]]; then
      "/opt/keycloak/bin/kcadm.sh" create users -r "${KC_REALM}" \
        -s username="${username}" \
        -s enabled=true \
        -s email="${email}" \
        -s firstName="${firstName}" \
        -s lastName="${lastName}" >/dev/null
      existing=$("/opt/keycloak/bin/kcadm.sh" get users -r "${KC_REALM}" -q username="${username}" | grep -o '"id" *: *"[^"]*"' | head -n1 | cut -d'"' -f4 || true)
    else
      # Ensure enabled and update basic fields
      "/opt/keycloak/bin/kcadm.sh" update users/${existing} -r "${KC_REALM}" \
        -s enabled=true \
        -s email="${email}" \
        -s firstName="${firstName}" \
        -s lastName="${lastName}" >/dev/null
    fi

    # Set password (always)
    "/opt/keycloak/bin/kcadm.sh" set-password -r "${KC_REALM}" --username "${username}" --new-password "${password}" >/dev/null
    echo " - ensured user: ${username}"
  done
else
  echo "WARNING: Users file not found at ${KC_USERS_FILE}. Skipping user provisioning."
fi

# Keep container running
wait "${KC_PID}"
