-- Phase 6 / Week 17, Step 2 of the DatabaseDesign.md §56.3 retrofit sequence: backfill data
-- only, no schema change (V025 already added the tables) and no repointing yet (Week 18).
--
-- One default variant per existing product, copying the columns that move onto product_variants
-- going forward. `products` columns are read here, not modified -- they stay live and readable
-- until Week 18 Step 6 drops them once every consumer has cut over.
INSERT INTO product_variants (product_id, sku, barcode, variant_label, cost_price, selling_price,
    wholesale_price, reorder_point, is_active, image_path, is_default)
SELECT product_id, sku, barcode, name, cost_price, selling_price, wholesale_price, reorder_point,
    is_active, image_path, TRUE
FROM products;

INSERT INTO permissions (permission_code, module, action, description)
VALUES
    ('product.variant.read', 'product', 'variant.read', 'Read product variants'),
    ('product.variant.create', 'product', 'variant.create', 'Create product variants'),
    ('product.variant.update', 'product', 'variant.update', 'Update product variants')
ON CONFLICT (permission_code) DO UPDATE SET
    module = EXCLUDED.module,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

-- Re-run for SUPER_ADMIN against the current state of `permissions` (same pattern as
-- V011/V020/V024): V009's original CROSS JOIN only ran once, against whatever rows existed at
-- that point -- it does not retroactively cover permissions inserted by later migrations.
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
    'product.variant.read', 'product.variant.create', 'product.variant.update'
)
WHERE r.role_name IN ('OWNER', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code = 'product.variant.read'
WHERE r.role_name IN ('ACCOUNTANT', 'AUDITOR', 'STORE_KEEPER', 'CASHIER', 'SERVICE_OFFICER')
ON CONFLICT DO NOTHING;
