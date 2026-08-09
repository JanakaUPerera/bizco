CREATE TABLE business_profile (
    business_profile_id SMALLINT PRIMARY KEY DEFAULT 1,
    business_name VARCHAR(200) NOT NULL,
    address_line1 VARCHAR(200),
    address_line2 VARCHAR(200),
    city VARCHAR(100),
    province VARCHAR(100),
    postal_code VARCHAR(20),
    phone VARCHAR(20),
    email VARCHAR(150),
    website VARCHAR(200),
    tin_number VARCHAR(30),
    vat_registered BOOLEAN NOT NULL DEFAULT FALSE,
    logo_path VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (business_profile_id = 1)
);

CREATE TABLE tax_configuration (
    tax_configuration_id SMALLINT PRIMARY KEY DEFAULT 1,
    vat_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    vat_rate NUMERIC(7,4) NOT NULL DEFAULT 18.0000,
    changed_by UUID,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (tax_configuration_id = 1),
    CHECK (vat_rate >= 0 AND vat_rate <= 100),
    FOREIGN KEY (changed_by) REFERENCES users(user_id)
);

CREATE TABLE system_config (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value JSONB NOT NULL,
    description VARCHAR(255),
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (updated_by) REFERENCES users(user_id)
);
