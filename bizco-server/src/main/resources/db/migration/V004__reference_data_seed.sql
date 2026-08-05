CREATE TABLE uom (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(24) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    precision_scale INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tax_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    rate_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE system_settings (
    key VARCHAR(120) PRIMARY KEY,
    value TEXT NOT NULL,
    description VARCHAR(240),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO roles (code, name, permissions, is_system, is_active)
VALUES
    ('ADMIN', 'Administrator', '{
        "identity.user.read": true,
        "identity.user.write": true,
        "identity.role.read": true,
        "identity.role.write": true,
        "settings.business.read": true,
        "settings.business.write": true,
        "sales.pos.open": true,
        "sales.invoice.write": true,
        "sales.invoice.void": true,
        "customer.read": true,
        "customer.write": true,
        "catalog.product.read": true,
        "catalog.product.write": true,
        "scheduling.appointment.read": true,
        "scheduling.appointment.write": true,
        "inventory.stock.read": true,
        "inventory.stock.adjust": true,
        "purchasing.grn.write": true,
        "finance.cashbook.write": true,
        "report.sales.view": true,
        "report.finance.view": true,
        "report.tax.view": true,
        "report.export": true,
        "report.view_audit_logs": true
    }'::jsonb, TRUE, TRUE),
    ('MANAGER', 'Manager', '{
        "identity.user.read": true,
        "identity.role.read": true,
        "settings.business.read": true,
        "sales.pos.open": true,
        "sales.invoice.write": true,
        "customer.read": true,
        "customer.write": true,
        "catalog.product.read": true,
        "catalog.product.write": true,
        "scheduling.appointment.read": true,
        "scheduling.appointment.write": true,
        "inventory.stock.read": true,
        "report.sales.view": true,
        "report.finance.view": true,
        "report.tax.view": true,
        "report.export": true
    }'::jsonb, TRUE, TRUE),
    ('CASHIER', 'Cashier', '{
        "sales.pos.open": true,
        "sales.invoice.write": true,
        "customer.read": true,
        "catalog.product.read": true
    }'::jsonb, TRUE, TRUE),
    ('ACCOUNTANT', 'Accountant', '{
        "finance.cashbook.write": true,
        "report.sales.view": true,
        "report.finance.view": true,
        "report.tax.view": true,
        "report.export": true
    }'::jsonb, TRUE, TRUE),
    ('TECHNICIAN', 'Technician', '{
        "customer.read": true,
        "scheduling.appointment.read": true,
        "scheduling.appointment.write": true,
        "inventory.stock.read": true
    }'::jsonb, TRUE, TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    permissions = roles.permissions || EXCLUDED.permissions,
    is_system = TRUE,
    is_active = TRUE,
    updated_at = now();

INSERT INTO uom (code, name, precision_scale)
VALUES
    ('EA', 'Each', 0),
    ('PCS', 'Pieces', 0),
    ('BOX', 'Box', 0),
    ('PACK', 'Pack', 0),
    ('KG', 'Kilogram', 3),
    ('G', 'Gram', 3),
    ('L', 'Litre', 3),
    ('ML', 'Millilitre', 3),
    ('M', 'Metre', 2),
    ('HOUR', 'Hour', 2)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    precision_scale = EXCLUDED.precision_scale,
    is_active = TRUE,
    updated_at = now();

INSERT INTO tax_config (code, name, rate_percent, is_enabled, is_default)
VALUES
    ('VAT_STANDARD', 'VAT Standard', 18.00, FALSE, TRUE),
    ('VAT_ZERO', 'VAT Zero Rated', 0.00, FALSE, FALSE),
    ('VAT_EXEMPT', 'VAT Exempt', 0.00, FALSE, FALSE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    rate_percent = EXCLUDED.rate_percent,
    is_default = EXCLUDED.is_default,
    updated_at = now();

INSERT INTO system_settings (key, value, description)
VALUES
    ('tax.vat_enabled', 'false', 'VAT collection enabled flag'),
    ('tax.vat_rate', '18.00', 'Default VAT rate percentage'),
    ('business.currency_code', 'LKR', 'Default currency'),
    ('business.timezone', 'Asia/Colombo', 'Default business timezone')
ON CONFLICT (key) DO UPDATE SET
    value = EXCLUDED.value,
    description = EXCLUDED.description,
    updated_at = now();
