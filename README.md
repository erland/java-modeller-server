# java-modeller-server — docs bundle

This bundle is intended to be **self-contained** for starting development in a new chat.

## Contents
- `docs/functional-specification.md` — Full functional specification (scope, behaviors, phased capabilities).
- `docs/development-plan.md` — Phase 1 development plan with technical choices, structure, schema, API behaviors, and verification.
- `docs/phase1-step-by-step-plan-A.md` — Step-by-step Phase 1 implementation plan (server only).
- `docs/api-examples.md` — Concrete REST examples (headers + JSON payloads) including ETag/If-Match conflict handling.
- `docs/ops-deployment.md` — Ops/deployment posture (migrations strategy, multi-instance notes).

## Repo
Intended GitHub repository: `java-modeller-server`



## Step 1 status (Quarkus skeleton)
This repository currently contains the Phase 1 / Step 1 Quarkus skeleton:
- REST endpoint: `GET /me` (stub; will be protected in Step 5)
- Health endpoint: `/q/health`
- OpenAPI: `/q/openapi`
- Swagger UI: `/q/swagger-ui`

### Build & run
- `./mvnw test` (or `mvn test`)
- `./mvnw quarkus:dev`


## Step 2 — PostgreSQL connectivity + dev services

This project includes the PostgreSQL JDBC driver (`quarkus-jdbc-postgresql`).

### Option A: Quarkus Dev Services (recommended for local dev)
If you have Docker running, Quarkus can automatically start a PostgreSQL container in `dev` mode.
Run:

- `./mvnw quarkus:dev`

### Option B: Local Postgres via docker-compose
Start a local Postgres with:

- `docker compose -f docker-compose.dev.yml up -d`

If you want to explicitly point Quarkus to it, set (for example) in `application.properties`:

- `%dev.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/modeller`

(Dev Services is used when no JDBC URL is set.)


## Step 3 — Flyway baseline

This project uses **Flyway** for database schema migrations.

- Migrations are located under `src/main/resources/db/migration/`.
- In the **dev** profile, migrations run automatically on startup (`%dev.quarkus.flyway.migrate-at-start=true`).
- In the **test** profile, Flyway is disabled for now (`%test.quarkus.flyway.enabled=false`) to keep Step 1/2 tests DB-free. We'll enable DB-backed integration tests in later steps.

To see migrations apply in dev:

```bash
./mvnw quarkus:dev
```

Then check logs for Flyway migration output.


## Progress
- Step 1: Quarkus skeleton + module layout ✅
- Step 2: PostgreSQL connectivity + dev services ✅
- Step 3: Flyway + first migration baseline ✅
- Step 4: Phase 1 data model (domain + persistence) ✅

## Troubleshooting

### Java version
Build and run the server with **JDK 21** (recommended baseline for this project).


## Step 6 — Authorization model (dataset ACL)

Implemented the dataset-level role model (OWNER/EDITOR/VIEWER) and a reusable authorization service (`DatasetAuthorizationService`) that checks the current principal against dataset ACL entries.



## Step 7 — Dataset management endpoints
Implemented dataset CRUD + archive/delete under `/datasets`.

Run tests:
- `./mvnw test`

In dev (with Docker) Quarkus Dev Services will start PostgreSQL automatically.

## Step 11 status (Ops & deployment posture)

### Recommended runtime baseline
- **Build/runtime JDK:** 21 (supported and stable for Quarkus 3.x)
- **DB:** PostgreSQL 16+
- **Identity provider:** Keycloak (OIDC)

### Migrations posture (Flyway)
This project uses Flyway migrations in `src/main/resources/db/migration`.

**Profiles**
- `%dev`: `quarkus.flyway.migrate-at-start=true` (developer convenience)
- `%test`: `quarkus.flyway.migrate-at-start=true` (integration tests)
- `%prod`: **default is `false`** (recommended)

**Why disable migrate-at-start in prod?**
In multi-instance deployments (2+ replicas), letting every instance attempt migrations at startup can slow deploys and create confusing failure modes. Flyway uses DB locks to serialize migrations, but the recommended operational posture is:

- Run migrations as a **separate, explicit step** (CI/CD job, init container, one-off task), then
- Start/roll the application instances normally.

**If you do want the app to run migrations in prod**
Ensure only **one** instance has `quarkus.flyway.migrate-at-start=true` during rollout (e.g., a dedicated “migration” job/instance), then scale up the rest with it off.

### Production config (environment variables)
Typical prod configuration is supplied via environment variables / external config:

- `QUARKUS_DATASOURCE_JDBC_URL`
- `QUARKUS_DATASOURCE_USERNAME`
- `QUARKUS_DATASOURCE_PASSWORD`
- `QUARKUS_OIDC_AUTH_SERVER_URL`
- `QUARKUS_OIDC_CLIENT_ID`

See `src/main/resources/application.properties` for examples.

## Keycloak (dev)

This repo includes a reproducible dev Keycloak setup **without persistent storage**.
Each `docker compose up` imports the `modeller` realm and provisions dev users from a local file.

### 1) Create local, uncommitted secrets files

```bash
cp keycloak/keycloak.secrets.env.example keycloak/keycloak.secrets.env
cp keycloak/dev-users.list.example keycloak/dev-users.list
# Edit both files and set passwords
```

### 2) Start Keycloak

```bash
docker compose -f docker-compose.keycloak.dev.yml up
> If you get `permission denied` for `/opt/keycloak/entrypoint.sh` on macOS, run:
>
> `chmod +x keycloak/entrypoint.sh`
>

```

### 3) Verify realm

Open:
- `http://localhost:18080/realms/modeller/.well-known/openid-configuration`

### Notes
- `keycloak/keycloak.secrets.env` and `keycloak/dev-users.list` are **gitignored**.
- Add more users by adding lines to `keycloak/dev-users.list` (comma-separated: username,password,email,first,last).


## Keycloak dev (no persistence, reproducible)

This repo includes a Keycloak dev setup that:
- imports the `modeller` realm on each start (no persistent Keycloak volume)
- provisions dev users from a local, **gitignored** file
- configures a PKCE-ready client for the PWA (`pwa-modeller`) using URLs from a local, **gitignored** file

### 1) Create local-only config files (not committed)
```bash
cp keycloak/keycloak.secrets.env.example keycloak/keycloak.secrets.env
cp keycloak/keycloak.urls.env.example keycloak/keycloak.urls.env
cp keycloak/dev-users.list.example keycloak/dev-users.list
# edit the copied files to set passwords and (optionally) override dev URLs / users
```

### 2) Start Keycloak
```bash
docker compose -f docker-compose.keycloak.dev.yml up
> If you get `permission denied` for `/opt/keycloak/entrypoint.sh` on macOS, run:
>
> `chmod +x keycloak/entrypoint.sh`
>

```

### Notes
- PWA dev defaults (from the example file) assume the modeller runs at:
  - `http://localhost:5173/pwa-modeller/`
- The PKCE client uses redirect URI wildcard:
  - `${KC_PWA_REDIRECT_BASE}*`
- If you change the PWA base path/port, update `keycloak/keycloak.urls.env` and restart Keycloak.

## Local dev CORS (PWA calling the server)

Browser calls from the PWA dev server (e.g. `http://localhost:5173`) require CORS to be enabled on the API.

Create a local-only config file (gitignored):

```bash
mkdir -p config
cp config/dev-local.properties.example config/dev-local.properties
# edit config/dev-local.properties and set quarkus.http.cors.origins to your PWA origin(s)
```

Then start the server:

```bash
./mvnw quarkus:dev
```

Notes:
- The server loads `config/dev-local.properties` in **dev** via `quarkus.config.locations` using `${user.dir}` so it works both in terminal and IDE run configs.
- If you still see preflight/CORS errors, keep `quarkus.http.cors.headers=*` (recommended for dev) or add the exact headers shown in the browser preflight request.


## CORS (dev)

The browser PWA needs CORS when calling the API from `http://localhost:5173`.

Create a local (uncommitted) overrides file:

```bash
mkdir -p config
cp config/dev-local.properties.example config/dev-local.properties
```

Edit `config/dev-local.properties` and set `modeller.cors.origins` to your PWA origin.

Restart `./mvnw quarkus:dev`.
