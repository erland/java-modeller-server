# java-modeller-server — docs bundle

This bundle is intended to be **self-contained** for starting development in a new chat.

## Contents
- `docs/functional-specification.md` — Full functional specification (scope, behaviors, phased capabilities).
- `docs/development-plan.md` — Phase 1 development plan with technical choices, structure, schema, API behaviors, and verification.
- `docs/phase1-step-by-step-plan-A.md` — Step-by-step Phase 1 implementation plan (server only).
- `docs/api-examples.md` — Concrete REST examples (headers + JSON payloads) including ETag/If-Match conflict handling.

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
