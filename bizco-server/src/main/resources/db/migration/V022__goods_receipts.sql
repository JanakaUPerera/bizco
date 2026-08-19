-- DatabaseDesign.md Section 17.5-17.7. Week 12 scope-expansion decision (MVP.md Section 1.2a):
-- Goods Receipt document (SRS.md Section 6.9.1), replacing the single-step GRN this codebase's
-- earlier design used. References products(product_id), not product_variants - see the
-- "Build-order note" in DatabaseDesign.md Section 17: Phase 5 lands before Phase 6's variant
-- retrofit, which will repoint these tables alongside the others listed there.
--
-- Reuses purchasing.grn.create (seeded in V009) rather than a new permission code - the business
-- action ("receive goods and post supplier liability") is unchanged, only the document shape is
-- richer now (optional PO link, partial/multi-delivery, damaged/rejected qty per line).
CREATE TABLE goods_receipts (
    goods_receipt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    receipt_number VARCHAR(30) UNIQUE,
    purchase_order_id UUID,
    supplier_id UUID NOT NULL,
    supplier_reference VARCHAR(100),
    receipt_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    notes TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at TIMESTAMPTZ,
    reversed_at TIMESTAMPTZ,
    reversed_by UUID,
    reversal_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,

    CHECK (status IN ('DRAFT','POSTED','REVERSED')),
    CHECK (total_amount >= 0),
    CHECK (
        (status = 'DRAFT' AND receipt_number IS NULL AND posted_at IS NULL)
        OR
        (status IN ('POSTED','REVERSED') AND receipt_number IS NOT NULL AND posted_at IS NOT NULL)
    ),

    FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(purchase_order_id),
    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (reversed_by) REFERENCES users(user_id)
);

CREATE INDEX idx_goods_receipts_supplier ON goods_receipts(supplier_id, receipt_date);
CREATE INDEX idx_goods_receipts_po ON goods_receipts(purchase_order_id);
CREATE INDEX idx_goods_receipts_status ON goods_receipts(status);

-- Duplicate supplier-reference protection, same pattern as the original single-step GRN design.
CREATE UNIQUE INDEX uq_goods_receipt_supplier_reference
ON goods_receipts(supplier_id, supplier_reference)
WHERE supplier_reference IS NOT NULL
  AND status IN ('POSTED','REVERSED');

CREATE TABLE goods_receipt_items (
    goods_receipt_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goods_receipt_id UUID NOT NULL,
    purchase_order_item_id UUID,
    line_number INTEGER NOT NULL,
    product_id UUID NOT NULL,
    quantity_received NUMERIC(15,3) NOT NULL,
    quantity_damaged NUMERIC(15,3) NOT NULL DEFAULT 0,
    quantity_rejected NUMERIC(15,3) NOT NULL DEFAULT 0,
    unit_cost NUMERIC(15,2) NOT NULL,
    total_cost NUMERIC(15,2) NOT NULL,

    UNIQUE (goods_receipt_id, line_number),

    CHECK (quantity_received > 0),
    CHECK (quantity_damaged >= 0),
    CHECK (quantity_rejected >= 0),
    CHECK (quantity_damaged + quantity_rejected <= quantity_received),
    CHECK (unit_cost >= 0),
    CHECK (total_cost >= 0),

    FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(goods_receipt_id),
    FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(purchase_order_item_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE INDEX idx_goods_receipt_items_receipt ON goods_receipt_items(goods_receipt_id);
CREATE INDEX idx_goods_receipt_items_po_item ON goods_receipt_items(purchase_order_item_id);

-- Only (quantity_received - quantity_damaged - quantity_rejected) - the usable quantity - ever
-- posts a positive GRN stock movement; damaged/rejected quantity is recorded on the line but
-- never enters stock (DatabaseDesign.md Section 17.6).
CREATE TABLE product_cost_history (
    product_cost_history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    goods_receipt_item_id UUID NOT NULL UNIQUE,
    unit_cost NUMERIC(15,2) NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (unit_cost >= 0),

    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (goods_receipt_item_id) REFERENCES goods_receipt_items(goods_receipt_item_id)
);

CREATE INDEX idx_product_cost_history_product ON product_cost_history(product_id, effective_at);
