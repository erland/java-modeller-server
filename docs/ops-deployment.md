# Ops & Deployment Posture (Phase 1)

This document captures the **recommended operational posture** for running `java-modeller-server` in Phase 1.

## 1. Runtime baseline
- **JDK:** 21
- **Quarkus:** 3.x
- **Database:** PostgreSQL 16+
- **Identity Provider:** Keycloak (OIDC)

## 2. Configuration
In production, configuration is typically provided via environment variables or an external configuration source.

Common env vars:
- `QUARKUS_DATASOURCE_JDBC_URL`
- `QUARKUS_DATASOURCE_USERNAME`
- `QUARKUS_DATASOURCE_PASSWORD`
- `QUARKUS_OIDC_AUTH_SERVER_URL`
- `QUARKUS_OIDC_CLIENT_ID`

## 3. Flyway migrations strategy

### 3.1 Environments
- **dev**: migrations run automatically at startup (`%dev.quarkus.flyway.migrate-at-start=true`)
- **test**: migrations run automatically at startup (`%test.quarkus.flyway.migrate-at-start=true`)
- **prod**: default is **off** (`%prod.quarkus.flyway.migrate-at-start=false`)

### 3.2 Recommended production approach
Run migrations as an **explicit deployment step**:
1. Apply migrations (job/init-container/one-off task)
2. Start the application instances

This reduces risk and makes deployments easier to reason about.

### 3.3 Multi-instance deployments
If you run multiple instances (replicas):
- Avoid having *all* instances attempt migrations on startup.
- If you choose “migrate at startup” anyway, ensure only **one** instance does it (or a dedicated migration job),
  then roll out the rest with migrations disabled.

## 4. Build and packaging
- Build: `./mvnw -DskipTests=false package`
- Run (dev): `./mvnw quarkus:dev`
- Run (jar): `java -jar target/quarkus-app/quarkus-run.jar`

## 5. Observability endpoints (Phase 1)
- Health: `/q/health`
- OpenAPI: `/q/openapi`
- Swagger UI: `/q/swagger-ui`
