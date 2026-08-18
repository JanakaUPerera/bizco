-- DatabaseDesign.md Section 12: Held Sale / Reservation Schema.
-- A held sale is a lightweight cart snapshot (product/qty/price/discount only, no tax
-- calculation), independent of the invoices table. It only becomes a real DRAFT invoice at
-- POST /held-sales/{id}/convert, and only becomes CONVERTED when that invoice's own POST commits
-- (StateMachines.md 7.6) - so a held sale in progress never has any invoice-side effect.
CREATE TABLE held_sales (
    held_sale_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    held_number VARCHAR(30) NOT NULL UNIQUE,
    customer_id UUID,
    cashier_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'HELD',
    held_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    converted_invoice_id UUID,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,

    CHECK (status IN ('HELD','RESUMED','CANCELLED','EXPIRED','CONVERTED')),
    CHECK (
        status <> 'CONVERTED'
        OR converted_invoice_id IS NOT NULL
    ),

    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (cashier_id) REFERENCES users(user_id),
    FOREIGN KEY (converted_invoice_id) REFERENCES invoices(invoice_id)
);

CREATE INDEX idx_held_sales_status ON held_sales(status);
CREATE INDEX idx_held_sales_cashier ON held_sales(cashier_id);
-- One draft-in-progress invoice may only ever be the conversion target of one held sale.
CREATE UNIQUE INDEX uq_held_sales_converted_invoice ON held_sales(converted_invoice_id)
    WHERE converted_invoice_id IS NOT NULL;

CREATE TABLE held_sale_items (
    held_sale_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    held_sale_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,
    unit_price_snapshot NUMERIC(15,2) NOT NULL,
    discount_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    discount_value NUMERIC(15,2) NOT NULL DEFAULT 0,

    CHECK (quantity > 0),
    CHECK (unit_price_snapshot >= 0),
    CHECK (discount_type IN ('NONE','PERCENTAGE','FIXED')),
    CHECK (discount_value >= 0),

    FOREIGN KEY (held_sale_id) REFERENCES held_sales(held_sale_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE INDEX idx_held_sale_items_held_sale ON held_sale_items(held_sale_id);

-- DatabaseDesign.md Section 15.5. stock_movements/v_stock_on_hand (Section 15) don't exist yet
-- (Week 12/Inventory) so "available = physical - reserved" can't be computed today, but this half
-- of the formula has no dependency on stock_movements and is safe to stand up now.
CREATE VIEW v_reserved_stock AS
SELECT
    hsi.product_id,
    COALESCE(SUM(hsi.quantity), 0)::NUMERIC(15,3) AS reserved_stock
FROM held_sale_items hsi
JOIN held_sales hs ON hs.held_sale_id = hsi.held_sale_id
WHERE hs.status IN ('HELD','RESUMED')
GROUP BY hsi.product_id;
