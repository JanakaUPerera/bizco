-- DatabaseDesign.md Section 17.8-17.11 & Section 18. Week 15 (Phase 5's final week):
-- Supplier Returns, Payments & Allocations, plus the v_goods_receipt_outstanding read view that
-- finally makes goods-receipt payables reconcilable (FIN-AP-001..003).
--
-- Both supplier_returns and supplier_payments are one-shot documents - created and settled in a
-- single request, unlike the DRAFT-then-POSTED purchase_orders/goods_receipts. That is why
-- request_id is NOT NULL UNIQUE here (not nullable, as it is on the DRAFT-capable documents): the
-- idempotency key is claimed at creation, not at a later "post" transition, mirroring credit_notes'
-- one-shot settlement pattern (CreditNote.java Javadoc).
--
-- Reuses purchasing.return.create / purchasing.payment.create (seeded in V009) - no new permission
-- codes needed.
CREATE TABLE supplier_returns (
    supplier_return_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL UNIQUE,
    return_number VARCHAR(30) NOT NULL UNIQUE,
    supplier_id UUID NOT NULL,
    goods_receipt_id UUID NOT NULL,
    total_amount NUMERIC(15,2) NOT NULL,
    reason TEXT NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (total_amount >= 0),

    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(goods_receipt_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE INDEX idx_supplier_returns_supplier ON supplier_returns(supplier_id, created_at);
CREATE INDEX idx_supplier_returns_goods_receipt ON supplier_returns(goods_receipt_id);

CREATE TABLE supplier_return_items (
    supplier_return_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_return_id UUID NOT NULL,
    goods_receipt_item_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity_returned NUMERIC(15,3) NOT NULL,
    unit_cost NUMERIC(15,2) NOT NULL,
    line_total NUMERIC(15,2) NOT NULL,

    CHECK (quantity_returned > 0),
    CHECK (unit_cost >= 0),
    CHECK (line_total >= 0),

    FOREIGN KEY (supplier_return_id) REFERENCES supplier_returns(supplier_return_id),
    FOREIGN KEY (goods_receipt_item_id) REFERENCES goods_receipt_items(goods_receipt_item_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE INDEX idx_supplier_return_items_return ON supplier_return_items(supplier_return_id);
CREATE INDEX idx_supplier_return_items_receipt_item ON supplier_return_items(goods_receipt_item_id);

-- Cumulative return <= received usable quantity is enforced transactionally in
-- SupplierReturnService (goods-receipt row lock + a sum over existing supplier_return_items),
-- the same "lock then check then post" contract StockPostingService documents for stock - there is
-- no DB-level aggregate constraint for it, matching how PurchaseOrder receiving progress is
-- enforced in Java rather than SQL.
CREATE TABLE supplier_payments (
    supplier_payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL UNIQUE,
    supplier_id UUID NOT NULL,
    payment_date TIMESTAMPTZ NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    reference_number VARCHAR(100),
    notes TEXT,
    paid_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (payment_method IN ('CASH','CARD','BANK_TRANSFER','CHEQUE')),
    CHECK (amount > 0),

    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (paid_by) REFERENCES users(user_id)
);

CREATE INDEX idx_supplier_payments_supplier ON supplier_payments(supplier_id, payment_date);

CREATE TABLE supplier_payment_allocations (
    supplier_payment_allocation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_payment_id UUID NOT NULL,
    goods_receipt_id UUID NOT NULL,
    allocated_amount NUMERIC(15,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (allocated_amount > 0),
    UNIQUE (supplier_payment_id, goods_receipt_id),

    FOREIGN KEY (supplier_payment_id) REFERENCES supplier_payments(supplier_payment_id),
    FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(goods_receipt_id)
);

CREATE INDEX idx_supplier_payment_allocations_payment ON supplier_payment_allocations(supplier_payment_id);
CREATE INDEX idx_supplier_payment_allocations_receipt ON supplier_payment_allocations(goods_receipt_id);

-- Allocation Rule (DatabaseDesign.md Section 17.11, enforced in SupplierPaymentService): lock the
-- target goods receipts, then verify allocated amount <= that receipt's outstanding_amount (below),
-- sum of allocations for the payment <= payment amount, and every goods receipt belongs to the
-- payment's supplier. An unallocated remainder is left as supplier-account credit/prepayment -
-- there is no requirement that a payment fully allocate.
--
-- "Ledger, not cached balance" - same pattern as v_available_stock/v_stock_on_hand/
-- invoice-balance views: outstanding is always derived from goods_receipts/supplier_returns/
-- supplier_payment_allocations at read time, never stored.
CREATE VIEW v_goods_receipt_outstanding AS
SELECT
    g.goods_receipt_id,
    g.supplier_id,
    g.receipt_number,
    g.receipt_date,
    g.total_amount,
    COALESCE(r.returned_amount, 0) AS returned_amount,
    COALESCE(p.paid_amount, 0) AS paid_amount,
    (g.total_amount - COALESCE(r.returned_amount, 0) - COALESCE(p.paid_amount, 0))::NUMERIC(15,2) AS outstanding_amount
FROM goods_receipts g
LEFT JOIN (
    SELECT goods_receipt_id, SUM(total_amount) AS returned_amount
    FROM supplier_returns
    GROUP BY goods_receipt_id
) r ON r.goods_receipt_id = g.goods_receipt_id
LEFT JOIN (
    SELECT spa.goods_receipt_id, SUM(spa.allocated_amount) AS paid_amount
    FROM supplier_payment_allocations spa
    GROUP BY spa.goods_receipt_id
) p ON p.goods_receipt_id = g.goods_receipt_id
WHERE g.status = 'POSTED';
