-- DatabaseDesign.md Section 17.3/17.4. Week 12 scope-expansion decision (MVP.md Section 1.2a):
-- Purchase Order document (SRS.md Section 6.9.1), replacing the single-step GRN this codebase's
-- earlier design used. References products(product_id), not product_variants - see the
-- "Build-order note" in DatabaseDesign.md Section 17: Phase 5 lands before Phase 6's variant
-- retrofit, which will repoint purchase_order_items alongside the other tables listed there.
CREATE TABLE purchase_orders (
    purchase_order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    po_number VARCHAR(30) UNIQUE,
    supplier_id UUID NOT NULL,
    po_date DATE NOT NULL,
    expected_date DATE,
    valid_until DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    subtotal NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    notes TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CHECK (status IN ('DRAFT','APPROVED','SENT','PARTIALLY_RECEIVED','FULLY_RECEIVED','CLOSED','CANCELLED')),
    CHECK (subtotal >= 0),
    CHECK (total_amount >= 0),
    -- CANCELLED is reachable from DRAFT (never numbered) or from any later state (already
    -- numbered), so it is exempt from the "non-DRAFT implies numbered" rule that governs every
    -- other status.
    CHECK (
        (status = 'DRAFT' AND po_number IS NULL)
        OR
        (status = 'CANCELLED')
        OR
        (status NOT IN ('DRAFT','CANCELLED') AND po_number IS NOT NULL)
    ),

    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (approved_by) REFERENCES users(user_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE INDEX idx_purchase_orders_supplier ON purchase_orders(supplier_id);
CREATE INDEX idx_purchase_orders_status ON purchase_orders(status);

CREATE TABLE purchase_order_items (
    purchase_order_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    product_id UUID NOT NULL,
    quantity_ordered NUMERIC(15,3) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL,
    line_total NUMERIC(15,2) NOT NULL,

    UNIQUE (purchase_order_id, line_number),

    CHECK (quantity_ordered > 0),
    CHECK (unit_price >= 0),
    CHECK (line_total >= 0),

    FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(purchase_order_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE INDEX idx_purchase_order_items_po ON purchase_order_items(purchase_order_id);

-- Value-based approval threshold (DatabaseDesign.md Section 17.3): a PO at or above this total
-- must be APPROVED (purchasing.po.approve) before it can be SENT; below it, purchasing.po.create
-- alone is enough to send directly from DRAFT. Amount is business-configurable, same pattern as
-- other system_config-driven settings.
INSERT INTO system_config (config_key, config_value, description)
VALUES ('purchasing.po_approval_threshold', '50000.00'::jsonb, 'Purchase order total (LKR) at or above which manager/owner approval is required before sending')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO permissions (permission_code, module, action, description)
VALUES
    ('purchasing.po.create', 'purchasing', 'po.create', 'Create and send purchase orders'),
    ('purchasing.po.approve', 'purchasing', 'po.approve', 'Approve purchase orders at or above the value threshold')
ON CONFLICT (permission_code) DO UPDATE SET
    module = EXCLUDED.module,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN ('purchasing.po.create', 'purchasing.po.approve')
WHERE r.role_name IN ('OWNER', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code = 'purchasing.po.create'
WHERE r.role_name = 'STORE_KEEPER'
ON CONFLICT DO NOTHING;

-- SUPER_ADMIN already receives every registered permission via the CROSS JOIN in V009.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'SUPER_ADMIN'
AND p.permission_code IN ('purchasing.po.create', 'purchasing.po.approve')
ON CONFLICT DO NOTHING;
