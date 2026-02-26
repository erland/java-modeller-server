-- Phase 1 baseline schema (V1)
-- Keep this intentionally minimal; later migrations will evolve the schema.

CREATE TABLE IF NOT EXISTS datasets (
  id UUID PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  archived_at TIMESTAMPTZ NULL,
  deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE IF NOT EXISTS dataset_acl (
  dataset_id UUID NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
  user_sub TEXT NOT NULL,
  role TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (dataset_id, user_sub)
);

-- Latest snapshot per dataset (Phase 1 stores full snapshot payload in DB)
CREATE TABLE IF NOT EXISTS dataset_snapshot_latest (
  dataset_id UUID PRIMARY KEY REFERENCES datasets(id) ON DELETE CASCADE,
  revision BIGINT NOT NULL DEFAULT 0,
  etag TEXT NOT NULL,
  payload JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dataset_audit (
  id BIGSERIAL PRIMARY KEY,
  dataset_id UUID NULL REFERENCES datasets(id) ON DELETE SET NULL,
  actor_sub TEXT NULL,
  action TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  details JSONB NULL
);
