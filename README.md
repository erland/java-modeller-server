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
