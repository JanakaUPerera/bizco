INSERT INTO permissions (permission_code, module, action, description)
VALUES
    ('user.create', 'user', 'create', 'Create users'),
    ('user.read', 'user', 'read', 'Read users'),
    ('user.update', 'user', 'update', 'Update users'),
    ('user.lock', 'user', 'lock', 'Lock users'),
    ('user.unlock', 'user', 'unlock', 'Unlock users'),
    ('user.grant_role', 'user', 'grant_role', 'Grant secondary roles'),
    ('user.revoke_role', 'user', 'revoke_role', 'Revoke secondary roles'),
    ('user.login_history.read', 'user', 'login_history.read', 'Read login history'),
    ('user.reset_password', 'user', 'reset_password', 'Reset user passwords'),
    ('user.session.revoke', 'user', 'session.revoke', 'Revoke user sessions'),
    ('role.create', 'role', 'create', 'Create roles'),
    ('role.read', 'role', 'read', 'Read roles'),
    ('role.update', 'role', 'update', 'Update roles'),
    ('role.delete', 'role', 'delete', 'Delete roles'),
    ('customer.read', 'customer', 'read', 'Read customers'),
    ('customer.create', 'customer', 'create', 'Create customers'),
    ('customer.update', 'customer', 'update', 'Update customers'),
    ('customer.anonymize', 'customer', 'anonymize', 'Anonymize customers'),
    ('customer.credit.read', 'customer', 'credit.read', 'Read customer credit'),
    ('product.read', 'product', 'read', 'Read products'),
    ('product.create', 'product', 'create', 'Create products'),
    ('product.update', 'product', 'update', 'Update products'),
    ('product.delete', 'product', 'delete', 'Delete products'),
    ('product.category.read', 'product', 'category.read', 'Read product categories'),
    ('product.category.create', 'product', 'category.create', 'Create product categories'),
    ('product.category.update', 'product', 'category.update', 'Update product categories'),
    ('product.category.delete', 'product', 'category.delete', 'Delete product categories'),
    ('service.read', 'service', 'read', 'Read services'),
    ('service.create', 'service', 'create', 'Create services'),
    ('service.update', 'service', 'update', 'Update services'),
    ('invoice.read', 'invoice', 'read', 'Read invoices'),
    ('invoice.create', 'invoice', 'create', 'Create invoices'),
    ('invoice.void', 'invoice', 'void', 'Void invoices'),
    ('invoice.payment.create', 'invoice', 'payment.create', 'Create invoice payments'),
    ('invoice.payment.refund', 'invoice', 'payment.refund', 'Refund invoice payments'),
    ('invoice.credit_note.create', 'invoice', 'credit_note.create', 'Create credit notes'),
    ('invoice.hold_bill', 'invoice', 'hold_bill', 'Hold bills'),
    ('invoice.override_price', 'invoice', 'override_price', 'Override prices'),
    ('invoice.sell_below_cost', 'invoice', 'sell_below_cost', 'Sell below cost'),
    ('appointment.read', 'appointment', 'read', 'Read appointments'),
    ('appointment.create', 'appointment', 'create', 'Create appointments'),
    ('appointment.update', 'appointment', 'update', 'Update appointments'),
    ('appointment.cancel', 'appointment', 'cancel', 'Cancel appointments'),
    ('appointment.convert_to_job', 'appointment', 'convert_to_job', 'Convert appointments to jobs'),
    ('jobcard.read', 'jobcard', 'read', 'Read job cards'),
    ('jobcard.create', 'jobcard', 'create', 'Create job cards'),
    ('jobcard.update', 'jobcard', 'update', 'Update job cards'),
    ('jobcard.status_change', 'jobcard', 'status_change', 'Change job status'),
    ('jobcard.parts.add', 'jobcard', 'parts.add', 'Add job parts'),
    ('jobcard.estimate.create', 'jobcard', 'estimate.create', 'Create estimates'),
    ('jobcard.estimate.approve', 'jobcard', 'estimate.approve', 'Approve estimates'),
    ('jobcard.complete', 'jobcard', 'complete', 'Complete jobs'),
    ('inventory.read', 'inventory', 'read', 'Read inventory'),
    ('inventory.adjustment.create', 'inventory', 'adjustment.create', 'Create stock adjustments'),
    ('inventory.adjustment.approve', 'inventory', 'adjustment.approve', 'Approve stock adjustments'),
    ('purchasing.read', 'purchasing', 'read', 'Read purchasing'),
    ('purchasing.grn.create', 'purchasing', 'grn.create', 'Create GRNs'),
    ('purchasing.return.create', 'purchasing', 'return.create', 'Create supplier returns'),
    ('purchasing.payment.create', 'purchasing', 'payment.create', 'Create supplier payments'),
    ('finance.read', 'finance', 'read', 'Read finance data'),
    ('finance.cashbook.create', 'finance', 'cashbook.create', 'Create cashbook entries'),
    ('finance.cashbook.reverse', 'finance', 'cashbook.reverse', 'Reverse cashbook entries'),
    ('finance.cash_closing.create', 'finance', 'cash_closing.create', 'Create cash closings'),
    ('finance.cash_closing.approve', 'finance', 'cash_closing.approve', 'Approve cash closings'),
    ('audit.read', 'audit', 'read', 'Read audit logs'),
    ('report.sales.read', 'report', 'sales.read', 'Read sales reports'),
    ('report.finance.read', 'report', 'finance.read', 'Read finance reports'),
    ('report.tax.read', 'report', 'tax.read', 'Read tax reports'),
    ('report.export', 'report', 'export', 'Export reports'),
    ('system.config', 'system', 'config', 'Update system configuration'),
    ('system.config.read', 'system', 'config.read', 'Read system configuration'),
    ('system.backup.read', 'system', 'backup.read', 'Read backup status'),
    ('system.backup.create', 'system', 'backup.create', 'Create backups'),
    ('system.backup.restore', 'system', 'backup.restore', 'Restore backups'),
    ('dashboard.read', 'dashboard', 'read', 'Read dashboard')
ON CONFLICT (permission_code) DO UPDATE SET
    module = EXCLUDED.module,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

INSERT INTO roles (role_name, description, is_system_role, is_active)
VALUES
    ('SUPER_ADMIN', 'Super administrator', TRUE, TRUE),
    ('OWNER', 'Business owner', TRUE, TRUE),
    ('MANAGER', 'Manager', TRUE, TRUE),
    ('ACCOUNTANT', 'Accountant', TRUE, TRUE),
    ('CASHIER', 'Cashier', TRUE, TRUE),
    ('STORE_KEEPER', 'Store keeper', TRUE, TRUE),
    ('SERVICE_OFFICER', 'Service officer', TRUE, TRUE),
    ('AUDITOR', 'Auditor', TRUE, TRUE)
ON CONFLICT (role_name) DO UPDATE SET
    description = EXCLUDED.description,
    is_system_role = TRUE,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN (
    'user.read', 'user.create', 'user.update', 'user.lock', 'user.unlock',
    'user.grant_role', 'user.revoke_role', 'user.login_history.read',
    'role.read', 'role.create', 'role.update', 'role.delete',
    'customer.read', 'customer.create', 'customer.update', 'customer.credit.read',
    'product.read', 'product.create', 'product.update', 'product.delete',
    'product.category.read', 'product.category.create', 'product.category.update',
    'service.read', 'service.create', 'service.update',
    'invoice.read', 'invoice.create', 'invoice.void', 'invoice.payment.create',
    'invoice.payment.refund', 'invoice.credit_note.create', 'invoice.hold_bill',
    'invoice.override_price', 'invoice.sell_below_cost',
    'appointment.read', 'appointment.create', 'appointment.update', 'appointment.cancel',
    'appointment.convert_to_job', 'jobcard.read', 'jobcard.create', 'jobcard.update',
    'jobcard.status_change', 'jobcard.parts.add', 'jobcard.estimate.create',
    'jobcard.estimate.approve', 'jobcard.complete',
    'inventory.read', 'inventory.adjustment.create', 'inventory.adjustment.approve',
    'purchasing.read', 'purchasing.grn.create', 'purchasing.return.create',
    'purchasing.payment.create', 'finance.read', 'finance.cashbook.create',
    'finance.cashbook.reverse', 'finance.cash_closing.create',
    'finance.cash_closing.approve', 'audit.read', 'report.sales.read',
    'report.finance.read', 'report.tax.read', 'report.export',
    'system.config', 'system.config.read', 'system.backup.read',
    'system.backup.create', 'dashboard.read'
)
WHERE r.role_name IN ('OWNER', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN (
    'dashboard.read', 'customer.read', 'product.read', 'invoice.read',
    'invoice.create', 'invoice.payment.create', 'invoice.hold_bill'
)
WHERE r.role_name = 'CASHIER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN (
    'dashboard.read', 'finance.read', 'finance.cashbook.create',
    'finance.cash_closing.create', 'finance.cash_closing.approve',
    'report.finance.read', 'report.tax.read', 'report.export',
    'audit.read', 'system.config.read'
)
WHERE r.role_name = 'ACCOUNTANT'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN (
    'dashboard.read', 'product.read', 'inventory.read',
    'inventory.adjustment.create', 'purchasing.read',
    'purchasing.grn.create', 'purchasing.return.create', 'system.config.read'
)
WHERE r.role_name = 'STORE_KEEPER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN (
    'dashboard.read', 'customer.read', 'product.read',
    'appointment.read', 'appointment.create', 'appointment.update',
    'appointment.cancel', 'appointment.convert_to_job',
    'jobcard.read', 'jobcard.create', 'jobcard.update',
    'jobcard.status_change', 'jobcard.parts.add', 'jobcard.estimate.create',
    'jobcard.complete', 'system.config.read'
)
WHERE r.role_name = 'SERVICE_OFFICER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN (
    'dashboard.read', 'audit.read', 'finance.read', 'report.sales.read',
    'report.finance.read', 'report.tax.read', 'report.export', 'system.config.read'
)
WHERE r.role_name = 'AUDITOR'
ON CONFLICT DO NOTHING;

CREATE TABLE uom (
    uom_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    category VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO uom (code, name, category)
VALUES
    ('PCS', 'Pieces', 'COUNT'),
    ('KG', 'Kilogram', 'WEIGHT'),
    ('LTR', 'Litre', 'VOLUME'),
    ('BOX', 'Box', 'COUNT'),
    ('DOZ', 'Dozen', 'COUNT'),
    ('BTL', 'Bottle', 'VOLUME'),
    ('CASE', 'Case', 'COUNT'),
    ('MTR', 'Metre', 'LENGTH')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    is_active = TRUE;

INSERT INTO tax_configuration (tax_configuration_id, vat_enabled, vat_rate)
VALUES (1, FALSE, 18.0000)
ON CONFLICT (tax_configuration_id) DO UPDATE SET
    vat_enabled = EXCLUDED.vat_enabled,
    vat_rate = EXCLUDED.vat_rate,
    changed_at = CURRENT_TIMESTAMP;

INSERT INTO system_config (config_key, config_value, description)
VALUES
    ('business.currency_code', '"LKR"'::jsonb, 'Default currency'),
    ('business.timezone', '"Asia/Colombo"'::jsonb, 'Default business timezone')
ON CONFLICT (config_key) DO UPDATE SET
    config_value = EXCLUDED.config_value,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;
