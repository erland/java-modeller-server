-- Phase 1 optional: snapshot history (keep prior snapshots)
CREATE TABLE IF NOT EXISTS dataset_snapshot_history (
  dataset_id UUID NOT NULL,
  revision BIGINT NOT NULL,
  etag TEXT NOT NULL,
  payload JSONB NOT NULL,
  schema_version INTEGER NULL,
  saved_at TIMESTAMPTZ NOT NULL,
  saved_by TEXT NULL,
  PRIMARY KEY (dataset_id, revision),
  CONSTRAINT fk_snapshot_history_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_snapshot_history_dataset_rev_desc
  ON dataset_snapshot_history(dataset_id, revision DESC);
