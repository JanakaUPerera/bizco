-- DatabaseDesign.md Section 14: Credit Notes & Refunds.
CREATE TABLE credit_notes (
    credit_note_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL UNIQUE,
    credit_note_number VARCHAR(30) NOT NULL UNIQUE,
    original_invoice_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    reason TEXT NOT NULL,

    subtotal NUMERIC(15,2) NOT NULL,
    vat_amount NUMERIC(15,2) NOT NULL,
    total_amount NUMERIC(15,2) NOT NULL,

    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    issued_by UUID NOT NULL,
    applied_at TIMESTAMPTZ,

    CHECK (status IN ('ISSUED','APPLIED')),
    CHECK (subtotal >= 0),
    CHECK (vat_amount >= 0),
    CHECK (total_amount >= 0),

    FOREIGN KEY (original_invoice_id) REFERENCES invoices(invoice_id),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (issued_by) REFERENCES users(user_id)
);

CREATE INDEX idx_credit_notes_original_invoice ON credit_notes(original_invoice_id);
CREATE INDEX idx_credit_notes_customer ON credit_notes(customer_id);

CREATE TABLE credit_note_lines (
    credit_note_line_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_note_id UUID NOT NULL,
    original_invoice_line_id UUID NOT NULL,
    quantity_returned NUMERIC(15,3) NOT NULL,
    unit_price_snapshot NUMERIC(15,2) NOT NULL,
    taxable_amount NUMERIC(15,2) NOT NULL,
    vat_rate_snapshot NUMERIC(7,4) NOT NULL,
    vat_amount NUMERIC(15,2) NOT NULL,
    line_total NUMERIC(15,2) NOT NULL,
    restock BOOLEAN NOT NULL DEFAULT TRUE,

    CHECK (quantity_returned > 0),
    CHECK (unit_price_snapshot >= 0),
    CHECK (taxable_amount >= 0),
    CHECK (vat_amount >= 0),
    CHECK (line_total >= 0),

    FOREIGN KEY (credit_note_id) REFERENCES credit_notes(credit_note_id),
    FOREIGN KEY (original_invoice_line_id) REFERENCES invoice_lines(invoice_line_id)
);

CREATE INDEX idx_credit_note_lines_credit_note ON credit_note_lines(credit_note_id);
-- Cumulative return quantity per original line is enforced transactionally (row lock on the
-- invoice, DatabaseDesign.md 14.2) rather than by a constraint here, since it requires comparing
-- against the original line's own quantity.
CREATE INDEX idx_credit_note_lines_original_line ON credit_note_lines(original_invoice_line_id);

CREATE TABLE credit_note_applications (
    credit_note_application_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_note_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    applied_amount NUMERIC(15,2) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (applied_amount > 0),
    UNIQUE (credit_note_id, invoice_id),

    FOREIGN KEY (credit_note_id) REFERENCES credit_notes(credit_note_id),
    FOREIGN KEY (invoice_id) REFERENCES invoices(invoice_id)
);

CREATE TABLE customer_refunds (
    customer_refund_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL UNIQUE,
    credit_note_id UUID NOT NULL,
    original_customer_payment_id UUID,
    refund_date TIMESTAMPTZ NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    reference_number VARCHAR(100),
    refunded_by UUID NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (payment_method IN ('CASH','CARD','BANK_TRANSFER','CHEQUE')),
    CHECK (amount > 0),

    FOREIGN KEY (credit_note_id) REFERENCES credit_notes(credit_note_id),
    FOREIGN KEY (original_customer_payment_id) REFERENCES customer_payments(customer_payment_id),
    FOREIGN KEY (refunded_by) REFERENCES users(user_id)
);

-- DatabaseDesign.md Section 23.2/23.3: now that credit_note_applications exists, replace
-- V026's placeholder (credit_applied hardcoded to 0) with the real join. Same output column
-- list/types, so CREATE OR REPLACE is safe for the dependent v_customer_receivables view.
CREATE VIEW v_invoice_credit_totals AS
SELECT
    i.invoice_id,
    COALESCE(SUM(cna.applied_amount), 0)::NUMERIC(15,2) AS credit_amount
FROM invoices i
LEFT JOIN credit_note_applications cna
  ON cna.invoice_id = i.invoice_id
GROUP BY i.invoice_id;

CREATE OR REPLACE VIEW v_invoice_balances AS
SELECT
    i.invoice_id,
    i.invoice_number,
    i.customer_id,
    i.invoice_date,
    i.due_date,
    i.total_amount,
    COALESCE(p.payment_amount, 0) AS amount_paid,
    -- Explicit cast, not just relying on c.credit_amount's own typmod: CREATE OR REPLACE VIEW
    -- requires every output column's type to match exactly, and COALESCE(x, 0) drops x's typmod
    -- (precision/scale) even when x is already numeric(15,2) - V026's original credit_applied
    -- column (0::NUMERIC(15,2), a literal) was numeric(15,2), so this needs the same explicit
    -- cast to replace cleanly; amount_paid is untouched since it's the same expression as before.
    COALESCE(c.credit_amount, 0)::NUMERIC(15,2) AS credit_applied,
    GREATEST(
      i.total_amount - COALESCE(p.payment_amount, 0) - COALESCE(c.credit_amount, 0),
      0
    )::NUMERIC(15,2) AS balance_due,
    CASE
      WHEN i.status = 'VOIDED' THEN 'VOIDED'
      WHEN (i.total_amount - COALESCE(p.payment_amount, 0) - COALESCE(c.credit_amount, 0)) <= 0 THEN 'PAID'
      WHEN COALESCE(p.payment_amount, 0) + COALESCE(c.credit_amount, 0) > 0 THEN 'PARTIAL'
      ELSE 'UNPAID'
    END AS payment_status
FROM invoices i
LEFT JOIN v_invoice_payment_totals p ON p.invoice_id = i.invoice_id
LEFT JOIN v_invoice_credit_totals c ON c.invoice_id = i.invoice_id
WHERE i.status IN ('POSTED','VOIDED');
