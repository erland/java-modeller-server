-- Phase 2: snapshot history hardening (retention + indexing + metadata)

-- Add metadata columns (with safe defaults for existing rows)
ALTER TABLE dataset_snapshot_history
  ADD COLUMN IF NOT EXISTS payload_bytes INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS saved_action TEXT NOT NULL DEFAULT 'WRITE',
  ADD COLUMN IF NOT EXISTS saved_message TEXT NULL;

-- Ensure saved_at has a default for robustness
ALTER TABLE dataset_snapshot_history
  ALTER COLUMN saved_at SET DEFAULT NOW();

-- Backfill payload_bytes for pre-existing rows (best-effort)
UPDATE dataset_snapshot_history
SET payload_bytes = COALESCE(payload_bytes, 0);

-- Helpful indexes for polling/history UX and pruning
CREATE INDEX IF NOT EXISTS idx_snapshot_history_dataset_saved_at_desc
  ON dataset_snapshot_history(dataset_id, saved_at DESC);

CREATE INDEX IF NOT EXISTS idx_snapshot_history_dataset_saved_by
  ON dataset_snapshot_history(dataset_id, saved_by);

CREATE INDEX IF NOT EXISTS idx_snapshot_history_dataset_action
  ON dataset_snapshot_history(dataset_id, saved_action);
