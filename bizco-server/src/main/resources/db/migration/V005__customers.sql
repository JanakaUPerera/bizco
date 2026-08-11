CREATE TABLE customers (
    customer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(150),
    address_line1 VARCHAR(200),
    address_line2 VARCHAR(200),
    city VARCHAR(100),
    nic_ciphertext BYTEA,
    br_ciphertext BYTEA,
    category VARCHAR(20) NOT NULL DEFAULT 'RETAIL',
    credit_limit NUMERIC(15,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    consent_marketing BOOLEAN NOT NULL DEFAULT FALSE,
    consent_data_sharing BOOLEAN NOT NULL DEFAULT FALSE,
    consent_date TIMESTAMPTZ,
    is_anonymized BOOLEAN NOT NULL DEFAULT FALSE,
    anonymized_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (category IN ('RETAIL','WHOLESALE','CORPORATE')),
    CHECK (status IN ('ACTIVE','BLOCKED')),
    CHECK (credit_limit >= 0),
    CHECK ((is_anonymized = FALSE AND anonymized_at IS NULL) OR (is_anonymized = TRUE AND anonymized_at IS NOT NULL))
);

CREATE INDEX idx_customers_name_trgm ON customers USING gin (lower(name) gin_trgm_ops);
CREATE INDEX idx_customers_phone_trgm ON customers USING gin (phone gin_trgm_ops);
CREATE INDEX idx_customers_code ON customers(customer_code);
CREATE INDEX idx_customers_category_status ON customers(category, status);

