-- DatabaseDesign.md Section 20: Job Card / Service Work Schema (DevelopmentPlan.md Week 11).
-- job_services/job_parts reference service_definitions/products, not the illustrative "services"
-- table name used in the docs (same correction already applied to appointments in V017).
CREATE TABLE job_cards (
    job_card_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    job_number VARCHAR(30) NOT NULL UNIQUE,

    appointment_id UUID UNIQUE,
    customer_id UUID NOT NULL,
    technician_id UUID,
    created_by UUID,
    -- Not in DatabaseDesign.md's illustrative DDL, but StateMachines.md 10.9 requires checking
    -- "required linked invoice exists ... payment/authorized credit condition satisfied" before
    -- completion, which needs somewhere to record which invoice that is once one is generated
    -- (ApiContracts.md Section 33).
    service_invoice_id UUID,

    device_type VARCHAR(50),
    brand VARCHAR(100),
    model VARCHAR(100),
    serial_number VARCHAR(100),

    reported_issue TEXT,
    customer_notes TEXT,
    accessories_received TEXT,
    device_condition TEXT,

    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',

    estimated_completion_date DATE,
    actual_completion_date DATE,
    pickup_date DATE,
    warranty_end_date DATE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CHECK (status IN (
        'CREATED',
        'ESTIMATE_PENDING',
        'ESTIMATE_APPROVED',
        'IN_PROGRESS',
        'READY_FOR_PICKUP',
        'COMPLETED',
        'CANCELLED'
    )),

    FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (technician_id) REFERENCES users(user_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (service_invoice_id) REFERENCES invoices(invoice_id)
);

CREATE INDEX idx_job_cards_status ON job_cards(status);
CREATE INDEX idx_job_cards_technician_status ON job_cards(technician_id, status);
CREATE INDEX idx_job_cards_customer ON job_cards(customer_id);

CREATE TABLE job_services (
    job_service_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_card_id UUID NOT NULL,
    service_id UUID NOT NULL,
    estimated_cost NUMERIC(15,2),
    actual_cost NUMERIC(15,2),
    estimated_duration_minutes INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes TEXT,

    CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
    CHECK (actual_cost IS NULL OR actual_cost >= 0),
    CHECK (estimated_duration_minutes IS NULL OR estimated_duration_minutes > 0),
    CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED')),

    FOREIGN KEY (job_card_id) REFERENCES job_cards(job_card_id),
    FOREIGN KEY (service_id) REFERENCES service_definitions(service_id)
);

CREATE INDEX idx_job_services_job_card ON job_services(job_card_id);

-- Section 39-style gap note (see also HeldSale/held_sales, V015): a JOB_PART stock movement is
-- documented (Section 20.3: "each row produces one JOB_PART stock movement") but stock_movements
-- does not exist until Phase 5 (Week 12) - job_parts is safe to stand up now since nothing here
-- depends on that table; the movement itself is created once Phase 5 lands.
CREATE TABLE job_parts (
    job_part_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    job_card_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity_used NUMERIC(15,3) NOT NULL,
    unit_price_snapshot NUMERIC(15,2) NOT NULL,
    cost_price_snapshot NUMERIC(15,2) NOT NULL,
    is_warranty_covered BOOLEAN NOT NULL DEFAULT FALSE,
    posted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (quantity_used > 0),
    CHECK (unit_price_snapshot >= 0),
    CHECK (cost_price_snapshot >= 0),

    FOREIGN KEY (job_card_id) REFERENCES job_cards(job_card_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE INDEX idx_job_parts_job_card ON job_parts(job_card_id);

CREATE TABLE job_estimates (
    job_estimate_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_card_id UUID NOT NULL,
    estimate_version INTEGER NOT NULL,
    estimated_total NUMERIC(15,2) NOT NULL,
    description TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    customer_response VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    customer_response_at TIMESTAMPTZ,
    notes TEXT,

    UNIQUE (job_card_id, estimate_version),

    CHECK (estimate_version > 0),
    CHECK (estimated_total >= 0),
    CHECK (customer_response IN ('PENDING','ACCEPTED','DECLINED')),

    FOREIGN KEY (job_card_id) REFERENCES job_cards(job_card_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE INDEX idx_job_estimates_job_card ON job_estimates(job_card_id);

-- ApiContracts.md Section 33 / DomainModel.md Section 14.9: a PRODUCT invoice line generated from
-- a JobPart is marked with its source so a future Phase 5 stock-posting step can recognise the
-- part's stock was already consumed via JOB_PART and skip a second SALE deduction. No such
-- deduction exists yet either (PostSaleService does not create stock movements today), so this
-- column is inert until Phase 5 - added now to avoid a second invoice_lines migration then.
ALTER TABLE invoice_lines ADD COLUMN source_job_part_id UUID REFERENCES job_parts(job_part_id);
