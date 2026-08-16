-- DatabaseDesign.md Section 28.3 "Optional idempotency_records" (centralized handling).
-- Critical posting commands (invoice post, payment, refund, credit note, GRN post, supplier
-- return, supplier payment, job part consumption, stock adjustment approval, cash closing,
-- backup, restore - ApiContracts.md Section 3.3) require an Idempotency-Key header; this table
-- lets the server detect an exact retry and replay the original result instead of re-running it.
CREATE TABLE idempotency_records (
    request_id UUID PRIMARY KEY,
    operation VARCHAR(80) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_type VARCHAR(60),
    resource_id UUID,
    response_code INTEGER,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Supports future retention/cleanup of old records (ApiContracts.md 49.4: retained long enough
-- to cover normal client retries and operational reconciliation, not forever).
CREATE INDEX idx_idempotency_records_created_at ON idempotency_records(created_at);
