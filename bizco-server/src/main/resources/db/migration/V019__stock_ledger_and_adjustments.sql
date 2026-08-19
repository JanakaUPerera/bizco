-- DatabaseDesign.md Section 15: Stock Ledger, Section 16: Stock Adjustment Schema.
-- DevelopmentPlan.md Week 12 (Phase 5). stock_movements is the sole authoritative source of
-- physical stock (STK-LEDGER-001): every posting flow (sale, sale void, GRN, customer return,
-- supplier return, job part, adjustment) writes exactly one row here instead of touching a
-- denormalized quantity column on products. Rows are never updated/deleted by the application
-- (STK-LEDGER-002) - a correction is always a new, oppositely-signed movement.
CREATE TABLE stock_movements (
    stock_movement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,

    movement_type VARCHAR(30) NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,

    reference_type VARCHAR(30) NOT NULL,
    reference_id UUID NOT NULL,
    -- The precise source row, one of: invoice_line_id, grn_item_id, credit_note_line_id,
    -- supplier_return_item_id, job_part_id, stock_adjustment_id (DatabaseDesign.md 15.1). Deliberately
    -- not a foreign key - it is polymorphic across six source tables, so referential integrity for
    -- it is enforced by each posting service writing exactly one movement per source row, not by
    -- the schema.
    source_line_id UUID,

    notes TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (quantity <> 0),
    CHECK (movement_type IN (
        'GRN',
        'GRN_REVERSAL',
        'SALE',
        'SALE_VOID',
        'CUSTOMER_RETURN',
        'SUPPLIER_RETURN',
        'JOB_PART',
        'JOB_PART_REVERSAL',
        'ADJUSTMENT',
        'ADJUSTMENT_REVERSAL'
    )),
    CHECK (reference_type IN (
        'INVOICE',
        'GRN',
        'CREDIT_NOTE',
        'SUPPLIER_RETURN',
        'JOB_CARD',
        'STOCK_ADJUSTMENT'
    )),

    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- STK-SOURCE-001: a retried posting attempt for a source row that already has its movement must
-- not create a second one. Movement type is part of the key because a single source row can
-- legitimately carry two opposite-signed movements over its lifetime (e.g. an ADJUSTMENT and its
-- later ADJUSTMENT_REVERSAL both key off the same stock_adjustment_id but under different types).
CREATE UNIQUE INDEX uq_stock_movement_source
ON stock_movements(movement_type, source_line_id)
WHERE source_line_id IS NOT NULL;

CREATE INDEX idx_stock_movements_product_time ON stock_movements(product_id, created_at);
CREATE INDEX idx_stock_movements_reference ON stock_movements(reference_type, reference_id);

-- Authoritative physical stock (STK-LEDGER-001, REC-STK-001): SUM of every posted movement.
CREATE VIEW v_stock_on_hand AS
SELECT
    product_id,
    COALESCE(SUM(quantity), 0)::NUMERIC(15,3) AS physical_stock
FROM stock_movements
GROUP BY product_id;

-- v_reserved_stock already exists (V015__held_sales.sql) - it has no dependency on stock_movements
-- and was safe to stand up before this migration. available = physical - reserved, restricted to
-- INVENTORY products (SERVICE products are never stock-tracked).
CREATE VIEW v_available_stock AS
SELECT
    p.product_id,
    COALESCE(soh.physical_stock, 0)::NUMERIC(15,3) AS physical_stock,
    COALESCE(rs.reserved_stock, 0)::NUMERIC(15,3) AS reserved_stock,
    (
      COALESCE(soh.physical_stock, 0)
      - COALESCE(rs.reserved_stock, 0)
    )::NUMERIC(15,3) AS available_stock
FROM products p
LEFT JOIN v_stock_on_hand soh ON soh.product_id = p.product_id
LEFT JOIN v_reserved_stock rs ON rs.product_id = p.product_id
WHERE p.product_type = 'INVENTORY';

-- DatabaseDesign.md Section 16: manual stock corrections go through a two-step
-- PENDING -> APPROVED/REJECTED workflow (segregation of duties between inventory.adjustment.create
-- and inventory.adjustment.approve, already seeded in V009) so a stock-keeper cannot both request
-- and post their own correction. Exactly one signed ADJUSTMENT movement is created on approval
-- (StateMachines.md Section 17); a PENDING or REJECTED adjustment never touches stock_movements.
CREATE TABLE stock_adjustments (
    stock_adjustment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    product_id UUID NOT NULL,
    adjustment_type VARCHAR(20) NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    decided_by UUID,
    decided_at TIMESTAMPTZ,
    decision_reason TEXT,

    reverses_adjustment_id UUID,
    version BIGINT NOT NULL DEFAULT 0,

    CHECK (adjustment_type IN ('POSITIVE','NEGATIVE','DAMAGE')),
    CHECK (quantity > 0),
    CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CHECK (
        (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
        OR
        (status IN ('APPROVED','REJECTED') AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
    ),

    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (decided_by) REFERENCES users(user_id),
    FOREIGN KEY (reverses_adjustment_id) REFERENCES stock_adjustments(stock_adjustment_id)
);

CREATE INDEX idx_stock_adjustments_product ON stock_adjustments(product_id);
CREATE INDEX idx_stock_adjustments_status ON stock_adjustments(status);
