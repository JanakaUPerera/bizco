-- DatabaseDesign.md Section 11: Sales / Invoice Schema.
CREATE TABLE invoices (
    invoice_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    invoice_number VARCHAR(30) UNIQUE,

    invoice_date DATE NOT NULL,
    due_date DATE,
    invoice_type VARCHAR(20) NOT NULL DEFAULT 'SALES',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    customer_id UUID,
    cashier_id UUID NOT NULL,

    business_name_snapshot VARCHAR(200),
    business_address_snapshot VARCHAR(500),
    business_tin_snapshot VARCHAR(30),

    customer_name_snapshot VARCHAR(200),
    customer_address_snapshot VARCHAR(500),
    customer_tin_snapshot VARCHAR(30),

    subtotal NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    discount_value NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    taxable_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_rate_snapshot NUMERIC(7,4) NOT NULL DEFAULT 0,
    vat_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,

    notes TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at TIMESTAMPTZ,
    voided_at TIMESTAMPTZ,
    voided_by UUID,
    void_reason TEXT,

    version BIGINT NOT NULL DEFAULT 0,

    CHECK (invoice_type IN ('SALES','SERVICE','TAX')),
    CHECK (status IN ('DRAFT','POSTED','VOIDED')),
    CHECK (discount_type IN ('NONE','PERCENTAGE','FIXED')),
    CHECK (discount_value >= 0),
    CHECK (discount_amount >= 0),
    CHECK (subtotal >= 0),
    CHECK (taxable_amount >= 0),
    CHECK (vat_rate_snapshot >= 0 AND vat_rate_snapshot <= 100),
    CHECK (vat_amount >= 0),
    CHECK (total_amount >= 0),
    CHECK (
        (status = 'DRAFT' AND invoice_number IS NULL AND posted_at IS NULL)
        OR
        (status IN ('POSTED','VOIDED') AND invoice_number IS NOT NULL AND posted_at IS NOT NULL)
    ),
    CHECK (
        status <> 'VOIDED'
        OR (voided_at IS NOT NULL AND voided_by IS NOT NULL AND length(trim(void_reason)) > 0)
    ),

    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (cashier_id) REFERENCES users(user_id),
    FOREIGN KEY (voided_by) REFERENCES users(user_id)
);

CREATE INDEX idx_invoices_customer ON invoices(customer_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_invoice_date ON invoices(invoice_date);

CREATE TABLE invoice_lines (
    invoice_line_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL,

    line_number INTEGER NOT NULL,
    line_type VARCHAR(20) NOT NULL,

    product_id UUID,
    service_id UUID,

    sku_snapshot VARCHAR(50),
    description_snapshot VARCHAR(250) NOT NULL,
    uom_snapshot VARCHAR(20),

    quantity NUMERIC(15,3) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL,

    discount_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    discount_value NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,

    tax_category_snapshot VARCHAR(20) NOT NULL,
    vat_rate_snapshot NUMERIC(7,4) NOT NULL DEFAULT 0,
    taxable_amount NUMERIC(15,2) NOT NULL,
    vat_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_total_incl_vat NUMERIC(15,2) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (invoice_id, line_number),

    CHECK (line_type IN ('PRODUCT','SERVICE','CUSTOM')),
    CHECK (quantity > 0),
    CHECK (unit_price >= 0),
    CHECK (discount_type IN ('NONE','PERCENTAGE','FIXED')),
    CHECK (discount_value >= 0),
    CHECK (discount_amount >= 0),
    CHECK (tax_category_snapshot IN ('STANDARD','EXEMPT','ZERO_RATED')),
    CHECK (vat_rate_snapshot >= 0 AND vat_rate_snapshot <= 100),
    CHECK (taxable_amount >= 0),
    CHECK (vat_amount >= 0),
    CHECK (line_total_incl_vat >= 0),
    CHECK (
        (line_type = 'PRODUCT' AND product_id IS NOT NULL)
        OR
        (line_type = 'SERVICE' AND service_id IS NOT NULL)
        OR
        (line_type = 'CUSTOM')
    ),

    FOREIGN KEY (invoice_id) REFERENCES invoices(invoice_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (service_id) REFERENCES service_definitions(service_id)
);

CREATE INDEX idx_invoice_lines_invoice ON invoice_lines(invoice_id);

CREATE TABLE sales_approvals (
    sales_approval_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL,
    invoice_line_id UUID,
    approval_type VARCHAR(40) NOT NULL,
    requested_by UUID NOT NULL,
    approved_by UUID NOT NULL,
    reason TEXT,
    approved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (approval_type IN (
        'DISCOUNT_10_25',
        'DISCOUNT_OVER_25',
        'PRICE_OVERRIDE',
        'BELOW_COST'
    )),

    FOREIGN KEY (invoice_id) REFERENCES invoices(invoice_id),
    FOREIGN KEY (invoice_line_id) REFERENCES invoice_lines(invoice_line_id),
    FOREIGN KEY (requested_by) REFERENCES users(user_id),
    FOREIGN KEY (approved_by) REFERENCES users(user_id)
);

CREATE INDEX idx_sales_approvals_invoice ON sales_approvals(invoice_id);

-- MVP.md Section "Invoice & POS" lists these permissions; V009 seeded the rest of the invoice.*
-- set but missed the discount-tier and reprint permissions this migration's endpoints need.
INSERT INTO permissions (permission_code, module, action, description)
VALUES
    ('invoice.discount.apply', 'invoice', 'discount.apply', 'Apply discounts up to 10 percent'),
    ('invoice.discount.approve_25', 'invoice', 'discount.approve_25', 'Approve discounts 10 to 25 percent'),
    ('invoice.discount.approve_50', 'invoice', 'discount.approve_50', 'Approve discounts over 25 percent'),
    ('invoice.reprint', 'invoice', 'reprint', 'Reprint receipts')
ON CONFLICT (permission_code) DO UPDATE SET
    module = EXCLUDED.module,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN (
    'invoice.discount.apply', 'invoice.discount.approve_25', 'invoice.discount.approve_50', 'invoice.reprint'
)
WHERE r.role_name IN ('OWNER', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
JOIN permissions p ON p.permission_code IN ('invoice.discount.apply', 'invoice.reprint')
WHERE r.role_name = 'CASHIER'
ON CONFLICT DO NOTHING;

-- SUPER_ADMIN already receives every registered permission via the CROSS JOIN in V009.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.role_id, p.permission_code
FROM roles r
CROSS JOIN permissions p
WHERE r.role_name = 'SUPER_ADMIN'
AND p.permission_code IN (
    'invoice.discount.apply', 'invoice.discount.approve_25', 'invoice.discount.approve_50', 'invoice.reprint'
)
ON CONFLICT DO NOTHING;
