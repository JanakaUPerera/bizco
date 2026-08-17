-- DatabaseDesign.md Section 13: Customer Payment & Receivable Schema.
CREATE TABLE customer_payments (
    customer_payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL UNIQUE,
    customer_id UUID,
    payment_date TIMESTAMPTZ NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    reference_number VARCHAR(100),
    received_by UUID NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (payment_method IN ('CASH','CARD','BANK_TRANSFER','CHEQUE')),
    CHECK (amount > 0),

    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (received_by) REFERENCES users(user_id)
);

CREATE INDEX idx_customer_payments_customer ON customer_payments(customer_id);

CREATE TABLE customer_payment_allocations (
    customer_payment_allocation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_payment_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    allocated_amount NUMERIC(15,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (allocated_amount > 0),
    UNIQUE (customer_payment_id, invoice_id),

    FOREIGN KEY (customer_payment_id) REFERENCES customer_payments(customer_payment_id),
    FOREIGN KEY (invoice_id) REFERENCES invoices(invoice_id)
);

CREATE INDEX idx_customer_payment_allocations_invoice ON customer_payment_allocations(invoice_id);

-- DatabaseDesign.md Section 21: Cashbook Schema.
CREATE TABLE cashbook_entries (
    cashbook_entry_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,

    entry_date TIMESTAMPTZ NOT NULL,
    direction VARCHAR(10) NOT NULL,
    source_type VARCHAR(30) NOT NULL,

    amount NUMERIC(15,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,

    category VARCHAR(100),
    reference_id UUID,
    reason TEXT,

    created_by UUID NOT NULL,
    reversed_entry_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (direction IN ('IN','OUT')),
    CHECK (source_type IN (
        'CUSTOMER_PAYMENT',
        'CUSTOMER_REFUND',
        'SUPPLIER_PAYMENT',
        'MANUAL'
    )),
    CHECK (payment_method IN ('CASH','CARD','BANK_TRANSFER','CHEQUE')),
    CHECK (amount > 0),

    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (reversed_entry_id) REFERENCES cashbook_entries(cashbook_entry_id)
);

-- Section 21.2: one system-generated cashbook entry per source record.
CREATE UNIQUE INDEX uq_cashbook_source
ON cashbook_entries(source_type, reference_id)
WHERE source_type <> 'MANUAL'
  AND reference_id IS NOT NULL;

-- DatabaseDesign.md Section 23: read views for invoice/customer balances.
--
-- v_invoice_balances omits the credit-note join from Section 23.2/23.3 (credit_note_applications
-- does not exist yet - credit notes are a later addition); credit_applied is a literal 0 until
-- then, so balance_due is currently payments-only. Update this view (not the callers) once
-- credit_note_applications lands.
CREATE VIEW v_invoice_payment_totals AS
SELECT
    i.invoice_id,
    COALESCE(SUM(cpa.allocated_amount), 0)::NUMERIC(15,2) AS payment_amount
FROM invoices i
LEFT JOIN customer_payment_allocations cpa
  ON cpa.invoice_id = i.invoice_id
GROUP BY i.invoice_id;

CREATE VIEW v_invoice_balances AS
SELECT
    i.invoice_id,
    i.invoice_number,
    i.customer_id,
    i.invoice_date,
    i.due_date,
    i.total_amount,
    COALESCE(p.payment_amount, 0) AS amount_paid,
    0::NUMERIC(15,2) AS credit_applied,
    GREATEST(
      i.total_amount - COALESCE(p.payment_amount, 0),
      0
    )::NUMERIC(15,2) AS balance_due,
    CASE
      WHEN i.status = 'VOIDED' THEN 'VOIDED'
      WHEN (i.total_amount - COALESCE(p.payment_amount, 0)) <= 0 THEN 'PAID'
      WHEN COALESCE(p.payment_amount, 0) > 0 THEN 'PARTIAL'
      ELSE 'UNPAID'
    END AS payment_status
FROM invoices i
LEFT JOIN v_invoice_payment_totals p ON p.invoice_id = i.invoice_id
WHERE i.status IN ('POSTED','VOIDED');

CREATE VIEW v_customer_receivables AS
SELECT
    customer_id,
    SUM(balance_due)::NUMERIC(15,2) AS outstanding_receivable,
    MIN(invoice_date) FILTER (WHERE balance_due > 0) AS oldest_outstanding_invoice_date
FROM v_invoice_balances
WHERE payment_status NOT IN ('VOIDED', 'PAID')
  AND customer_id IS NOT NULL
GROUP BY customer_id;
