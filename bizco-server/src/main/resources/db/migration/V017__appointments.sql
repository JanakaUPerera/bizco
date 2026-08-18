-- DatabaseDesign.md Section 19: Appointment Schema (DevelopmentPlan.md Week 9).
-- Appointments store absolute scheduled instants. blocked_until_at snapshots the configured
-- appointment buffer (service end + buffer) at create/reschedule time, so the exclusion
-- constraint below stays deterministic even if the buffer configuration changes later.
--
-- Note: DatabaseDesign.md's illustrative DDL references a `services` table; the catalog module
-- actually landed as `service_definitions` (V011), so the FK below targets that real table.
CREATE TABLE appointments (
    appointment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    appointment_number VARCHAR(30) NOT NULL UNIQUE,

    customer_id UUID NOT NULL,
    service_id UUID NOT NULL,
    technician_id UUID,

    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    blocked_until_at TIMESTAMPTZ NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    notes TEXT,
    is_walk_in BOOLEAN NOT NULL DEFAULT FALSE,

    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CHECK (status IN (
        'SCHEDULED',
        'CONFIRMED',
        'IN_PROGRESS',
        'COMPLETED',
        'NO_SHOW',
        'CANCELLED'
    )),
    CHECK (end_at > start_at),
    CHECK (blocked_until_at >= end_at),

    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (service_id) REFERENCES service_definitions(service_id),
    FOREIGN KEY (technician_id) REFERENCES users(user_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- DatabaseDesign.md Section 19.3 / 39: PostgreSQL is the final authority on double-booking, not
-- application code. Two active appointments (SCHEDULED/CONFIRMED/IN_PROGRESS) for the same
-- technician can never have overlapping [start_at, blocked_until_at) ranges. CANCELLED/NO_SHOW/
-- COMPLETED appointments no longer block new schedules. Requires btree_gist (enabled in V001).
ALTER TABLE appointments
ADD CONSTRAINT ex_appointments_technician_overlap
EXCLUDE USING gist (
    technician_id WITH =,
    tstzrange(start_at, blocked_until_at, '[)') WITH &&
)
WHERE (
    technician_id IS NOT NULL
    AND status IN ('SCHEDULED','CONFIRMED','IN_PROGRESS')
);

CREATE INDEX idx_appointments_start_at ON appointments(start_at);
CREATE INDEX idx_appointments_technician_start ON appointments(technician_id, start_at);
CREATE INDEX idx_appointments_customer_start ON appointments(customer_id, start_at DESC);
