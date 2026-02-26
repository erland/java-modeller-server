-- Add Phase 1 dataset metadata fields to align closer with functional spec.
-- created_by / updated_by: best-effort identity (OIDC subject) of creator/updater
-- current_revision: quick access to latest snapshot revision (0 if none)
ALTER TABLE datasets
  ADD COLUMN IF NOT EXISTS created_by TEXT NULL;

ALTER TABLE datasets
  ADD COLUMN IF NOT EXISTS updated_by TEXT NULL;

ALTER TABLE datasets
  ADD COLUMN IF NOT EXISTS current_revision BIGINT NOT NULL DEFAULT 0;
