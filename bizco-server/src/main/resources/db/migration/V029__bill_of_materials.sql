-- Phase 7 / Week 20 (DevelopmentPlan.md Week 20, DatabaseDesign.md §57): Bill of Materials schema
-- and the Produce transaction's own aggregate (production_orders/production_order_items — not
-- named in DatabaseDesign.md §57, designed here to mirror goods_receipts/goods_receipt_items:
-- SRS.md §6.4.11.2 step 6 requires "Production event recorded for traceability", and every other
-- multi-row stock posting in this codebase already has such an aggregate row for its
-- reference_id/reference_type). Also extends stock_movements' movement_type/reference_type CHECK
-- constraints (added by V019, never altered since) with the two new values DatabaseDesign.md
-- §57.2 requires.

CREATE TABLE bill_of_materials (
    bom_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    finished_variant_id UUID NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    FOREIGN KEY (finished_variant_id) REFERENCES product_variants(product_variant_id)
);

CREATE TABLE bom_items (
    bom_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bom_id UUID NOT NULL,
    component_variant_id UUID NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    wastage_qty NUMERIC(15,3) NOT NULL DEFAULT 0,
    estimated_cost NUMERIC(15,2),

    CONSTRAINT uq_bom_items UNIQUE (bom_id, component_variant_id),
    CHECK (quantity > 0),
    CHECK (wastage_qty >= 0),

    FOREIGN KEY (bom_id) REFERENCES bill_of_materials(bom_id),
    FOREIGN KEY (component_variant_id) REFERENCES product_variants(product_variant_id)
);

CREATE INDEX idx_bom_items_bom ON bom_items(bom_id);
CREATE INDEX idx_bom_items_component ON bom_items(component_variant_id);

-- The Produce transaction's own aggregate (SRS.md §6.4.11.2 step 6). Unlike goods_receipts there
-- is no DRAFT state: a row only ever exists already-posted, since lock/validate/consume is one
-- atomic application-service call with no intermediate editable document (task 21.1).
CREATE TABLE production_orders (
    production_order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    production_number VARCHAR(30) UNIQUE,
    bom_id UUID NOT NULL,
    finished_variant_id UUID NOT NULL,
    quantity_produced NUMERIC(15,3) NOT NULL,
    production_mode VARCHAR(20) NOT NULL DEFAULT 'STOCKED',
    total_component_cost NUMERIC(15,2) NOT NULL DEFAULT 0,
    notes TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (quantity_produced > 0),
    CHECK (production_mode IN ('STOCKED', 'MADE_TO_ORDER')),

    FOREIGN KEY (bom_id) REFERENCES bill_of_materials(bom_id),
    FOREIGN KEY (finished_variant_id) REFERENCES product_variants(product_variant_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE production_order_items (
    production_order_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    production_order_id UUID NOT NULL,
    component_variant_id UUID NOT NULL,
    quantity_consumed NUMERIC(15,3) NOT NULL,
    unit_cost_at_production NUMERIC(15,2) NOT NULL,
    total_cost NUMERIC(15,2) NOT NULL,

    CONSTRAINT uq_production_order_items UNIQUE (production_order_id, component_variant_id),
    CHECK (quantity_consumed > 0),
    CHECK (unit_cost_at_production >= 0),

    FOREIGN KEY (production_order_id) REFERENCES production_orders(production_order_id),
    FOREIGN KEY (component_variant_id) REFERENCES product_variants(product_variant_id)
);

CREATE INDEX idx_production_orders_bom ON production_orders(bom_id);
CREATE INDEX idx_production_orders_finished_variant ON production_orders(finished_variant_id);
CREATE INDEX idx_production_order_items_order ON production_order_items(production_order_id);

-- DatabaseDesign.md §57.2: PRODUCTION_IN (finished variant, positive) / PRODUCTION_OUT (each
-- component, negative), both referencing the same production_order as reference_id. Looked up by
-- expression rather than a guessed generated name (V019's CHECK constraints were never named).
DO $$
DECLARE
    movement_type_constraint TEXT;
    reference_type_constraint TEXT;
BEGIN
    SELECT conname INTO movement_type_constraint
    FROM pg_constraint
    WHERE conrelid = 'stock_movements'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%movement_type%';
    EXECUTE format('ALTER TABLE stock_movements DROP CONSTRAINT %I', movement_type_constraint);

    SELECT conname INTO reference_type_constraint
    FROM pg_constraint
    WHERE conrelid = 'stock_movements'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%reference_type%';
    EXECUTE format('ALTER TABLE stock_movements DROP CONSTRAINT %I', reference_type_constraint);
END $$;

ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_movement_type_check CHECK (movement_type IN (
    'GRN', 'GRN_REVERSAL', 'SALE', 'SALE_VOID', 'CUSTOMER_RETURN', 'SUPPLIER_RETURN', 'JOB_PART',
    'JOB_PART_REVERSAL', 'ADJUSTMENT', 'ADJUSTMENT_REVERSAL', 'PRODUCTION_IN', 'PRODUCTION_OUT'
));

ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_reference_type_check CHECK (reference_type IN (
    'INVOICE', 'GRN', 'CREDIT_NOTE', 'SUPPLIER_RETURN', 'JOB_CARD', 'STOCK_ADJUSTMENT', 'PRODUCTION_ORDER'
));

INSERT INTO permissions (permission_code, module, action, description)
VALUES
    ('manufacturing.read', 'manufacturing', 'read', 'Read Bills of Materials and production history'),
    ('manufacturing.bom.manage', 'manufacturing', 'bom.manage', 'Create, update, and edit Bills of Materials'),
    ('manufacturing.produce', 'manufacturing', 'produce', 'Post a Produce transaction')
ON CONFLICT (permission_code) DO UPDATE SET
    module = EXCLUDED.module,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

-- SUPER_ADMIN already receives every permission via V009's CROSS JOIN.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN ('manufacturing.read', 'manufacturing.bom.manage', 'manufacturing.produce')
WHERE r.role_name IN ('OWNER', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN ('manufacturing.read', 'manufacturing.produce')
WHERE r.role_name = 'STORE_KEEPER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code = 'manufacturing.read'
WHERE r.role_name IN ('ACCOUNTANT', 'AUDITOR')
ON CONFLICT DO NOTHING;
