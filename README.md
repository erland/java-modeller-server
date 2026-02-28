# java-modeller-server

Quarkus backend for the EA Modeller ecosystem, with Keycloak (OIDC) and a simple “edge” nginx to provide stable paths:

- PWA: `/pwa-modeller/`
- API: `/api/`
- Keycloak: `/auth/`

This repository also contains a **ready-to-run dev and test stack** so you can avoid configuring URLs in the PWA UI.

## What’s in this repo

- `src/` — Quarkus application (API, persistence, security).
- `keycloak/` — Keycloak dev/test configuration (realm template, optional dev user provisioning).
- `deploy/` — nginx configs + runtime `config.json` for the PWA.
- `docker-compose.edge.dev.yml` — **Dev** edge stack (nginx + keycloak + postgres), proxies to local Quarkus + local PWA dev server.
- `docker-compose.edge.test.yml` — **Test** stack (everything in Docker, including the PWA container image).
- `.github/workflows/publish-ghcr.yml` — Builds & publishes the server image to GHCR.

Docs bundle:
- `docs/functional-specification.md`
- `docs/development-plan.md`
- `docs/phase1-step-by-step-plan-A.md`
- `docs/api-examples.md`
- `docs/ops-deployment.md`
- `docs/phase3-e2e-demo.md` (Phase 3 ops + SSE walkthrough)

## Environments at a glance

| Environment | Browser URL you use | PWA | Quarkus | Keycloak | DB |
|---|---|---|---|---|---|
| **Dev (recommended)** | `http://localhost/pwa-modeller/` | local `npm run dev` | local `./mvnw quarkus:dev` | Docker (`/auth`) | Docker |
| **Test (recommended)** | `http://localhost/pwa-modeller/` | Docker image | Docker container | Docker (`/auth`) | Docker |

Why the edge nginx? It keeps the browser on a **single origin** (`http://localhost`) so you typically avoid CORS issues.

---

# Local development (recommended): Edge nginx + Keycloak in Docker, Quarkus + PWA locally

## Prereqs
- Docker Desktop / Docker Engine
- JDK 21
- Node.js (for the PWA repo)

## 1) Start the docker infra (edge nginx + keycloak + postgres)

From this repo root:

```bash
docker compose -f docker-compose.edge.dev.yml up -d
```

Verify:
- Keycloak (via nginx): `http://localhost/auth/`
- API health (once Quarkus is running): `http://localhost/api/q/health`

> Linux note: if you’re on Linux (not Docker Desktop), `host.docker.internal` may require the compose `extra_hosts` setting. This repo includes it where needed.

## 2) Configure local dev overrides (once per machine)

Create your local-only overrides (not committed):

```bash
cp -n config/dev-local.properties.example config/dev-local.properties
```

The important setting for this dev topology is:

```properties
quarkus.oidc.auth-server-url=http://localhost/auth/realms/modeller
```

(Quarkus needs `/auth` because Keycloak is mounted under that path.)

## 3) Run Quarkus locally

```bash
./mvnw quarkus:dev
```

Notes:
- Postgres is available at `localhost:5432` with user/password `modeller/modeller` (as defined in compose).
- If you run via the edge URL (`http://localhost/api/...`) you normally don’t need CORS.

## 4) Run the PWA locally (in your sibling repo)

Assuming you have a sibling directory layout:

```
<workspace>/
  pwa-modeller/
  java-modeller-server/
```

Start the PWA dev server from `pwa-modeller/`:

```bash
npm install
npm run dev
```

Open the app via edge nginx:

- `http://localhost/pwa-modeller/`

### PWA runtime config (dev)

nginx serves a runtime config at:

- `http://localhost/pwa-modeller/config.json`

File location:
- `deploy/dev/pwa-modeller/config.json`

Tip:
- Prefer fetching config using a **relative** URL in the PWA (e.g. `fetch("./config.json")`) so it works under `/pwa-modeller/`.

## Keycloak: users & passwords (manual is supported)

You can manage users/passwords yourself in the Keycloak admin UI:

- Admin console: `http://localhost/auth/admin/`
- Admin user: `admin`
- Admin password: defined in `keycloak/keycloak.secrets.env` (change it locally as needed)

Optional convenience:
- `keycloak/dev-users.list` can auto-provision dev users on container start (see `keycloak/entrypoint.sh`).
- If you prefer fully manual user management, keep `dev-users.list` empty and create users in the admin UI.

## Stop / reset (dev)

```bash
docker compose -f docker-compose.edge.dev.yml down
# To wipe DB + Keycloak data:
docker compose -f docker-compose.edge.dev.yml down -v
```

---

# Test setup (recommended): Everything in Docker behind one nginx

This runs:
- edge nginx on `80:80`
- Keycloak at `/auth`
- Quarkus at `/api`
- PWA container at `/pwa-modeller`

The PWA image used by default:
- `ghcr.io/erland/pwa-modeller:latest`

## Start test stack

```bash
docker compose -f docker-compose.edge.test.yml up -d --build
```

Open:
- PWA: `http://localhost/pwa-modeller/`
- Keycloak admin: `http://localhost/auth/admin/`
- API: `http://localhost/api/`

Runtime config is served at:
- `http://localhost/pwa-modeller/config.json`

Config file:
- `deploy/test/pwa-modeller/config.json`

## Reset all data (test)

```bash
docker compose -f docker-compose.edge.test.yml down -v
```

---

# Publishing the server image to GHCR

Workflow:
- `.github/workflows/publish-ghcr.yml`

Publishes:
- `ghcr.io/erland/java-modeller-server`

Tagging:
- `latest` on the default branch
- `sha-...` for each commit
- `v*` for version tags (e.g. `v1.0.0`)

---

# Troubleshooting

## Blank/empty screen at `http://localhost/pwa-modeller/` in dev
If you can open the app directly at `http://localhost:5173/pwa-modeller/` but not via nginx, you likely need the dev nginx config that proxies Vite dev endpoints.

Fix:
```bash
docker compose -f docker-compose.edge.dev.yml restart nginx
```

## Vite HMR WebSocket warning when using `http://localhost/pwa-modeller/`
You may see a Vite console message about websocket fallback. The UI will still work; it mostly affects hot-reload behavior. If you want perfect HMR while developing, you can also open:
- `http://localhost:5173/pwa-modeller/`

## Quarkus OIDC discovery 404
If Quarkus logs discovery requests failing at `/realms/modeller/...` (missing `/auth`), ensure you have:

```properties
quarkus.oidc.auth-server-url=http://localhost/auth/realms/modeller
```

in `config/dev-local.properties`.

