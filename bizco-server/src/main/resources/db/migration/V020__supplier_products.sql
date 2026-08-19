-- DatabaseDesign.md Section 17.2 (V019 renumbered as "current" -> this is V020 in the actual sequence).
-- Week 12 scope-expansion decision (MVP.md Section 1.2a): per-supplier product catalog (SRS.md
-- Section 6.9.4), pulled into MVP scope. References products(product_id) - not
-- product_variants(product_variant_id) - because Phase 5 (this migration) lands before Phase 6's
-- variant retrofit; DatabaseDesign.md Section 56.3 adds this table to that retrofit's scope.
CREATE TABLE supplier_products (
    supplier_product_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL,
    product_id UUID NOT NULL,
    supplier_sku VARCHAR(50),
    purchase_price NUMERIC(15,2) NOT NULL,
    min_order_qty NUMERIC(15,3) NOT NULL DEFAULT 1,
    lead_time_days INTEGER,
    last_purchase_price NUMERIC(15,2),
    is_preferred BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_supplier_products UNIQUE (supplier_id, product_id),
    CHECK (purchase_price >= 0),
    CHECK (min_order_qty > 0),
    CHECK (lead_time_days IS NULL OR lead_time_days >= 0),

    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE INDEX idx_supplier_products_supplier ON supplier_products(supplier_id);
CREATE INDEX idx_supplier_products_product ON supplier_products(product_id);

-- At most one preferred supplier per product.
CREATE UNIQUE INDEX uq_supplier_products_preferred
ON supplier_products(product_id)
WHERE is_preferred = TRUE;

INSERT INTO permissions (permission_code, module, action, description)
VALUES
    ('purchasing.supplier_product.create', 'purchasing', 'supplier_product.create', 'Add supplier product catalog entries'),
    ('purchasing.supplier_product.update', 'purchasing', 'supplier_product.update', 'Edit supplier product catalog entries')
ON CONFLICT (permission_code) DO UPDATE SET
    module = EXCLUDED.module,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN (
    'purchasing.supplier_product.create', 'purchasing.supplier_product.update'
)
WHERE r.role_name IN ('OWNER', 'MANAGER', 'STORE_KEEPER')
ON CONFLICT DO NOTHING;

-- SUPER_ADMIN already receives every registered permission via the CROSS JOIN in V009.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'SUPER_ADMIN'
AND p.permission_code IN ('purchasing.supplier_product.create', 'purchasing.supplier_product.update')
ON CONFLICT DO NOTHING;
