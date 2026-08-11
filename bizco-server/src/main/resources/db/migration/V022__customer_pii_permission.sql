INSERT INTO permissions (permission_code, module, action, description)
VALUES ('customer.view_pii', 'customer', 'view_pii', 'View sensitive customer PII')
ON CONFLICT (permission_code) DO UPDATE SET
    module = EXCLUDED.module,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, 'customer.view_pii'
FROM roles r
WHERE r.role_name IN ('SUPER_ADMIN', 'OWNER', 'MANAGER', 'ACCOUNTANT', 'SERVICE_OFFICER', 'AUDITOR')
ON CONFLICT DO NOTHING;

