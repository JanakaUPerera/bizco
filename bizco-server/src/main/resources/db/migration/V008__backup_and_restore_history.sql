CREATE TABLE backup_records (
    backup_record_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    sha256_checksum VARCHAR(64),
    status VARCHAR(30) NOT NULL,
    started_by UUID,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    error_message TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (started_by) REFERENCES users(user_id),
    CHECK (status IN ('STARTED','VERIFIED','FAILED'))
);

CREATE TABLE restore_records (
    restore_record_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    backup_record_id UUID,
    status VARCHAR(30) NOT NULL,
    started_by UUID,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    error_message TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (backup_record_id) REFERENCES backup_records(backup_record_id),
    FOREIGN KEY (started_by) REFERENCES users(user_id),
    CHECK (status IN ('STARTED','VERIFIED','FAILED','REJECTED'))
);
