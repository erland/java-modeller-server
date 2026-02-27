-- Phase 2: validation policy + collaborative edit leases
--
-- This migration introduces:
-- 1) datasets.validation_policy: per-dataset validation mode (none/basic/strict)
-- 2) dataset_leases: optional soft-locks / leases to reduce concurrent write conflicts

-- Per-dataset validation policy (defaults to 'none' for backwards compatibility)
ALTER TABLE datasets
  ADD COLUMN IF NOT EXISTS validation_policy TEXT NOT NULL DEFAULT 'none';

-- Optional: accelerate "list my datasets" queries (ACL lookup by user)
CREATE INDEX IF NOT EXISTS idx_dataset_acl_user_sub
  ON dataset_acl(user_sub);

-- Dataset edit leases (soft locks). Lease enforcement is implemented at the application layer.
CREATE TABLE IF NOT EXISTS dataset_leases (
  dataset_id UUID PRIMARY KEY REFERENCES datasets(id) ON DELETE CASCADE,
  holder_sub TEXT NOT NULL,
  lease_token TEXT NOT NULL,
  acquired_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  renewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dataset_leases_expires_at
  ON dataset_leases(expires_at);

-- Keep lease tokens unique to avoid ambiguity when debugging / auditing
CREATE UNIQUE INDEX IF NOT EXISTS ux_dataset_leases_token
  ON dataset_leases(lease_token);
