-- Phase 3: operation log (append-only)

CREATE TABLE IF NOT EXISTS dataset_operation (
  dataset_id   UUID        NOT NULL,
  revision     BIGINT      NOT NULL,
  op_id        TEXT        NOT NULL,
  op_type      TEXT        NOT NULL,
  payload_json JSONB       NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by   TEXT,
  PRIMARY KEY (dataset_id, revision)
);

-- idempotency / dedup within dataset
CREATE UNIQUE INDEX IF NOT EXISTS uq_dataset_operation_opid
  ON dataset_operation(dataset_id, op_id);

-- efficient range queries by revision (Step 5)
CREATE INDEX IF NOT EXISTS ix_dataset_operation_dataset_revision
  ON dataset_operation(dataset_id, revision);
