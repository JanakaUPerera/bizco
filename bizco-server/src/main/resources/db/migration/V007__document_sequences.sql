CREATE TABLE document_sequences (
    sequence_key VARCHAR(50) PRIMARY KEY,
    prefix VARCHAR(20) NOT NULL,
    sequence_date DATE,
    next_value BIGINT NOT NULL DEFAULT 1,
    padding INTEGER NOT NULL DEFAULT 4,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (next_value > 0),
    CHECK (padding >= 1 AND padding <= 12)
);
