# Bizco MVP PostgreSQL Database Design

**Project:** SME Business Management System (Bizco)  
**Document:** Database Design  
**Version:** 1.0  
**Status:** Pre-implementation / Flyway design baseline  
**Based On:** `SRS.md` v2.1, `MVP.md` v1.3, `DevelopmentPlan.md` v2.0, `DomainModel.md` v1.0, `StateMachines.md` v1.0  
**Database:** PostgreSQL 16+ supported; PostgreSQL 18.x recommended  
**Application:** JavaFX client → Spring Boot API → PostgreSQL  
**Scope Rule:** This design preserves the complete MVP scope. No MVP feature is removed or deferred.

---

# 1. Purpose

This document converts the approved Bizco MVP requirements, domain model, and state machines into the physical PostgreSQL design that will drive:

- Flyway migrations;
- JPA mappings;
- transactional application services;
- concurrency controls;
- report/read-model queries;
- backup and restore validation;
- integration tests using PostgreSQL Testcontainers.

This is the database authority for the MVP implementation unless a later approved migration/design decision supersedes it.

---

# 2. Core Database Principles

## 2.1 PostgreSQL Is Server-Only

```text
JavaFX
  ↓ HTTPS/REST
Spring Boot
  ↓ JDBC/JPA
PostgreSQL
```

Client computers never connect directly to PostgreSQL.

## 2.2 Transactional Truth

Authoritative records are immutable posted business documents and ledgers.

```text
Sales/credit/payment documents
             ↓
          Finance

GRN/returns/job parts/sales
             ↓
     stock_movements
```

## 2.3 No Editable Historical Ledger

Posted rows are not deleted or silently overwritten.

Corrections use:

- credit notes;
- refunds;
- supplier returns;
- explicit reversals;
- new approved stock adjustments;
- void metadata plus corresponding reversing effects.

## 2.4 Source-of-Truth Rules

| Business Value | Authoritative Source |
|---|---|
| Physical stock | `stock_movements` |
| Reserved stock | active `held_sale_items` |
| Available stock | physical − reserved |
| Customer receivable | posted invoices − allocations/credits/refunds according to settlement |
| Supplier payable | opening balance + posted GRNs − returns − payments |
| Cashbook | immutable `cashbook_entries` |
| VAT reports | posted invoice/credit-note tax snapshots |
| User effective permissions | role assignments + current expiry state |

Cached values may be introduced later only if they can be rebuilt and reconciled.

## 2.5 Domain Types in PostgreSQL

This design prefers:

```text
VARCHAR + CHECK constraints
```

for most business statuses rather than PostgreSQL ENUM types.

Reason:

- easier Flyway evolution;
- easier addition of future states;
- still provides DB validation;
- Java domain enums remain strongly typed.

## 2.6 Identifier Strategy

Business entities use:

```sql
UUID DEFAULT gen_random_uuid()
```

Small reference/configuration tables may use `BIGINT IDENTITY` or natural text keys.

## 2.7 Time Strategy

- business dates: `DATE`;
- actual moments: `TIMESTAMPTZ`;
- appointment scheduled instants: `TIMESTAMPTZ`;
- business display timezone: `Asia/Colombo`;
- Java: `LocalDate` for business dates, `Instant`/`OffsetDateTime` for instants.

## 2.8 Money and Quantity

```text
Money:    NUMERIC(15,2)
Quantity: NUMERIC(15,3)
Rate:     NUMERIC(7,4) where additional precision is useful
```

Java uses `BigDecimal`.

## 2.9 Optimistic Locking

Mutable aggregates have:

```sql
version BIGINT NOT NULL DEFAULT 0
```

mapped by JPA `@Version`.

## 2.10 Idempotency

Critical posting records contain:

```sql
request_id UUID UNIQUE
```

or use a dedicated command-key table when one command produces multiple root records.

---

# 3. Required PostgreSQL Extensions

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

Purpose:

| Extension | Purpose |
|---|---|
| `pgcrypto` | `gen_random_uuid()` |
| `btree_gist` | appointment technician equality + range exclusion |
| `pg_trgm` | fast partial customer/product searches |

No application encryption key is stored in PostgreSQL.

---

# 4. Naming Standards

## 4.1 Tables

Plural snake_case:

```text
users
roles
invoices
invoice_lines
stock_movements
```

## 4.2 Primary Keys

```text
<table singular>_id
```

Examples:

```text
user_id
invoice_id
stock_movement_id
```

## 4.3 Foreign Keys

Use the referenced primary key name.

## 4.4 Timestamps

```text
created_at
updated_at
posted_at
approved_at
revoked_at
```

## 4.5 Audit Actor

Where practical:

```text
created_by
posted_by
approved_by
```

rather than ambiguous `user_id`.

---

# 5. High-Level ER Model

```text
ROLE ──< ROLE_PERMISSION >── PERMISSION
  │
  ├──< USER >──< USER_ROLE_ASSIGNMENT
  │               │
  │               └── grant/revoke users
  └────────────── USER_SESSION

CUSTOMER ────────────────┐
                         │
PRODUCT ── CATEGORY       │
  │       UOM             │
  │                       ▼
SERVICE ─────────────── INVOICE ──< INVOICE_LINE
                         │
                         ├──< CUSTOMER_PAYMENT_ALLOCATION >── CUSTOMER_PAYMENT
                         │
                         ├──< CREDIT_NOTE ──< CREDIT_NOTE_LINE
                         │
                         └── optional HELD_SALE conversion

HELD_SALE ──< HELD_SALE_ITEM >── PRODUCT

PRODUCT ──< STOCK_MOVEMENT

SUPPLIER ──< GRN ──< GRN_ITEM
                      │
                      └── PRODUCT_COST_HISTORY

SUPPLIER ──< SUPPLIER_RETURN ──< SUPPLIER_RETURN_ITEM
SUPPLIER ──< SUPPLIER_PAYMENT ──< SUPPLIER_PAYMENT_ALLOCATION >── GRN

CUSTOMER ──< APPOINTMENT >── USER(technician)
                    │
                    └──0..1 JOB_CARD
                             ├──< JOB_SERVICE >── SERVICE
                             ├──< JOB_PART >──── PRODUCT
                             └──< JOB_ESTIMATE

CUSTOMER_PAYMENT ─────┐
CUSTOMER_REFUND ──────┤
SUPPLIER_PAYMENT ─────┼──> CASHBOOK_ENTRY
MANUAL ───────────────┘

USER ──< CASH_CLOSING

BUSINESS_PROFILE
TAX_CONFIGURATION
SYSTEM_CONFIG
AUDIT_LOG
BACKUP_RECORD
RESTORE_RECORD
DOCUMENT_SEQUENCE
```

---

# 6. Identity & RBAC Schema

## 6.1 `permissions`

Canonical permission registry.

```sql
CREATE TABLE permissions (
    permission_code VARCHAR(100) PRIMARY KEY,
    module VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (permission_code ~ '^[a-z][a-z0-9_.]*$')
);
```

Examples:

```text
invoice.create
invoice.void
inventory.adjustment.approve
system.backup.restore
```

## 6.2 `roles`

```sql
CREATE TABLE roles (
    role_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    role_name VARCHAR(60) NOT NULL UNIQUE,
    description VARCHAR(255),
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
```

System role codes/display names are seeded by Flyway/application startup policy.

MVP roles:

```text
SUPER_ADMIN
OWNER
MANAGER
ACCOUNTANT
CASHIER
STORE_KEEPER
SERVICE_OFFICER
AUDITOR
```

## 6.3 `role_permissions`

```sql
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID,
    PRIMARY KEY (role_id, permission_code),
    FOREIGN KEY (role_id) REFERENCES roles(role_id),
    FOREIGN KEY (permission_code) REFERENCES permissions(permission_code)
);
```

`assigned_by` FK is added after `users` exists or through a later migration to avoid bootstrap cycle.

## 6.4 `users`

```sql
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),
    email VARCHAR(150),
    phone VARCHAR(20),
    primary_role_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    password_changed_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT ck_users_failed_attempts CHECK (failed_login_attempts >= 0),
    FOREIGN KEY (primary_role_id) REFERENCES roles(role_id)
);
```

Recommended application normalization:

```text
username stored in canonical lowercase form
```

or use a functional unique index:

```sql
CREATE UNIQUE INDEX uq_users_username_ci
ON users (lower(username));
```

If this index is used, the plain unique constraint can be omitted.

## 6.5 `user_role_assignments`

```sql
CREATE TABLE user_role_assignments (
    user_role_assignment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    role_id BIGINT NOT NULL,
    granted_by UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,
    revoke_reason TEXT,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (role_id) REFERENCES roles(role_id),
    FOREIGN KEY (granted_by) REFERENCES users(user_id),
    FOREIGN KEY (revoked_by) REFERENCES users(user_id),
    CHECK (expires_at IS NULL OR expires_at > granted_at),
    CHECK (
        (is_active = TRUE AND revoked_at IS NULL)
        OR
        (is_active = FALSE)
    )
);
```

Prevent duplicate simultaneously active role assignment at application level and optionally with a partial unique index:

```sql
CREATE UNIQUE INDEX uq_active_secondary_role
ON user_role_assignments(user_id, role_id)
WHERE is_active = TRUE;
```

Runtime authorization must still check `expires_at`.

## 6.6 `user_sessions`

```sql
CREATE TABLE user_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    client_id VARCHAR(100),
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    CHECK (expires_at > created_at)
);
```

Indexes:

```sql
CREATE INDEX idx_sessions_user_active
ON user_sessions(user_id, expires_at)
WHERE revoked_at IS NULL;
```

Concurrent session limit is checked transactionally by application service.

## 6.7 `login_history`

```sql
CREATE TABLE login_history (
    login_history_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id UUID,
    attempted_username VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address INET,
    client_id VARCHAR(100),
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(100),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
```

Unknown username attempts have `user_id = NULL`.

## 6.8 `staff_profiles`

MVP uses this only to mark users eligible for technician assignment.

```sql
CREATE TABLE staff_profiles (
    user_id UUID PRIMARY KEY,
    is_technician BOOLEAN NOT NULL DEFAULT FALSE,
    display_name VARCHAR(150),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
```

This avoids coupling operational technician status to RBAC role.

---

# 7. Business Profile & Configuration

## 7.1 `business_profile`

Single-row MVP configuration.

```sql
CREATE TABLE business_profile (
    business_profile_id SMALLINT PRIMARY KEY DEFAULT 1,
    business_name VARCHAR(200) NOT NULL,
    address_line1 VARCHAR(200),
    address_line2 VARCHAR(200),
    city VARCHAR(100),
    province VARCHAR(100),
    postal_code VARCHAR(20),
    phone VARCHAR(20),
    email VARCHAR(150),
    website VARCHAR(200),
    tin_number VARCHAR(30),
    vat_registered BOOLEAN NOT NULL DEFAULT FALSE,
    logo_path VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (business_profile_id = 1)
);
```

Historical documents never depend exclusively on the current row; they store snapshots.

## 7.2 `tax_configuration`

```sql
CREATE TABLE tax_configuration (
    tax_configuration_id SMALLINT PRIMARY KEY DEFAULT 1,
    vat_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    vat_rate NUMERIC(7,4) NOT NULL DEFAULT 18.0000,
    changed_by UUID,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (tax_configuration_id = 1),
    CHECK (vat_rate >= 0 AND vat_rate <= 100),
    FOREIGN KEY (changed_by) REFERENCES users(user_id)
);
```

## 7.3 `system_config`

```sql
CREATE TABLE system_config (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value JSONB NOT NULL,
    description VARCHAR(255),
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (updated_by) REFERENCES users(user_id)
);
```

Known keys are application-controlled.

---

# 8. Customer Schema

## 8.1 `customers`

```sql
CREATE TABLE customers (
    customer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(150),
    address_line1 VARCHAR(200),
    address_line2 VARCHAR(200),
    city VARCHAR(100),

    nic_ciphertext BYTEA,
    br_ciphertext BYTEA,

    category VARCHAR(20) NOT NULL DEFAULT 'RETAIL',
    credit_limit NUMERIC(15,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    consent_marketing BOOLEAN NOT NULL DEFAULT FALSE,
    consent_data_sharing BOOLEAN NOT NULL DEFAULT FALSE,
    consent_date TIMESTAMPTZ,

    is_anonymized BOOLEAN NOT NULL DEFAULT FALSE,
    anonymized_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CHECK (category IN ('RETAIL','WHOLESALE','CORPORATE')),
    CHECK (status IN ('ACTIVE','BLOCKED')),
    CHECK (credit_limit >= 0)
);
```

### PII Encryption

NIC/BR encryption is application-level:

```text
AES-256-GCM
```

The encryption key must be external to the database:

- environment/secret store;
- never source-controlled;
- never persisted in `system_config`.

MVP does not require NIC search, therefore no deterministic/search hash is required.

## 8.2 Search Indexes

```sql
CREATE INDEX idx_customers_name_trgm
ON customers USING gin (lower(name) gin_trgm_ops);

CREATE INDEX idx_customers_phone_trgm
ON customers USING gin (phone gin_trgm_ops);

CREATE INDEX idx_customers_code
ON customers(customer_code);
```

## 8.3 Current Balance

There is intentionally **no authoritative `current_balance` column**.

API field:

```text
currentBalance
```

is populated from the receivable query/view.

This preserves the MVP requirement while preventing balance drift.

---

# 9. Catalog Schema

## 9.1 `product_categories`

```sql
CREATE TABLE product_categories (
    category_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (parent_id) REFERENCES product_categories(category_id),
    CHECK (parent_id IS NULL OR parent_id <> category_id)
);
```

Cycle prevention is enforced by application service plus integration test; PostgreSQL recursive validation may be added if required.

## 9.2 `uom`

```sql
CREATE TABLE uom (
    uom_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    category VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);
```

Seed:

```text
PCS, KG, LTR, BOX, DOZ, BTL, CASE, MTR
```

## 9.3 `products`

```sql
CREATE TABLE products (
    product_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(20) NOT NULL,
    barcode VARCHAR(50),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category_id BIGINT NOT NULL,
    uom_id BIGINT NOT NULL,
    product_type VARCHAR(20) NOT NULL DEFAULT 'INVENTORY',
    tax_category VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    cost_price NUMERIC(15,2) NOT NULL DEFAULT 0,
    selling_price NUMERIC(15,2) NOT NULL,
    wholesale_price NUMERIC(15,2),
    reorder_point NUMERIC(15,3) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    image_path VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT uq_products_barcode UNIQUE (barcode),

    CHECK (product_type IN ('INVENTORY','SERVICE')),
    CHECK (tax_category IN ('STANDARD','EXEMPT','ZERO_RATED')),
    CHECK (cost_price >= 0),
    CHECK (selling_price >= 0),
    CHECK (wholesale_price IS NULL OR wholesale_price >= 0),
    CHECK (reorder_point >= 0),

    FOREIGN KEY (category_id) REFERENCES product_categories(category_id),
    FOREIGN KEY (uom_id) REFERENCES uom(uom_id)
);
```

PostgreSQL unique constraint allows multiple null barcodes.

Indexes:

```sql
CREATE INDEX idx_products_name_trgm
ON products USING gin (lower(name) gin_trgm_ops);

CREATE INDEX idx_products_active_category
ON products(category_id, is_active);

CREATE INDEX idx_products_barcode_lookup
ON products(barcode)
WHERE barcode IS NOT NULL;
```

## 9.4 `services`

```sql
CREATE TABLE services (
    service_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    base_price NUMERIC(15,2) NOT NULL,
    estimated_duration_minutes INTEGER NOT NULL,
    requires_estimate BOOLEAN NOT NULL DEFAULT FALSE,
    warranty_days INTEGER NOT NULL DEFAULT 30,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (base_price >= 0),
    CHECK (estimated_duration_minutes > 0),
    CHECK (warranty_days >= 0)
);
```

---

# 10. Document Numbering

## 10.1 `document_sequences`

```sql
CREATE TABLE document_sequences (
    document_type VARCHAR(30) NOT NULL,
    business_date DATE NOT NULL,
    last_number INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (document_type, business_date),
    CHECK (last_number >= 0)
);
```

## 10.2 Allocation Algorithm

Inside the posting transaction:

```sql
INSERT INTO document_sequences(document_type, business_date, last_number)
VALUES (:type, :date, 1)
ON CONFLICT (document_type, business_date)
DO UPDATE SET last_number = document_sequences.last_number + 1
RETURNING last_number;
```

The returned value is formatted by server policy.

Example:

```text
INV + 2026-08-08 + 42
→ INV-20260808-0042
```

Because the sequence row update is part of the same transaction, a rollback also rolls back the allocation.

Prohibited:

```sql
SELECT MAX(invoice_number) + 1
```

## 10.3 Document Types

MVP requires at least:

```text
INV
CN
APT
JC
GRN
SR
HELD
CUST
SUP
SVC
```

Master-data codes may use a similar safe allocator or application-generated sequence policy.

---

# 11. Sales / Invoice Schema

## 11.1 `invoices`

```sql
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
```

### Notes

`amount_paid`, `balance_due`, and `payment_status` are not authoritative columns.

They are exposed through a view.

A POSTED credit invoice can legitimately have a positive balance.

## 11.2 `invoice_lines`

```sql
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
    FOREIGN KEY (service_id) REFERENCES services(service_id)
);
```

Posted-line mutation is blocked by application rules and integration tests. An optional trigger can later hard-block updates when parent invoice is not DRAFT.

## 11.3 `sales_approvals`

Captures approval evidence for discounts/price overrides/below-cost selling.

```sql
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
```

Authentication of the approver is a server security action; the database stores evidence, not the manager PIN/password.

---

# 12. Held Sale / Reservation Schema

## 12.1 `held_sales`

```sql
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
```

## 12.2 `held_sale_items`

```sql
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

    FOREIGN KEY (held_sale_id) REFERENCES held_sales(held_sale_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);
```

Reserved stock exists only while parent status is:

```text
HELD
RESUMED
```

---

# 13. Customer Payment & Receivable Schema

## 13.1 Physical Design Decision

The MVP API may continue to expose:

```text
POST /api/invoices/{id}/payments
```

but the database uses:

```text
customer_payments
customer_payment_allocations
```

This supports:

- immediate payment;
- split payment;
- later partial payment;
- one payment allocated across more than one invoice if required by receivable workflow;
- future-safe customer account settlement without redesign.

This changes persistence design, not MVP functionality.

## 13.2 `customer_payments`

```sql
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
```

Walk-in immediate payment may have `customer_id = NULL`, but it must be allocated to the corresponding invoice.

## 13.3 `customer_payment_allocations`

```sql
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
```

Business transaction validates:

```text
sum allocations <= payment amount
allocation <= current invoice outstanding
invoice is POSTED and not economically closed/voided
customer relationship is compatible
```

## 13.4 Unallocated Customer Payment

The schema permits:

```text
payment amount > allocated total
```

to preserve valid customer-credit/prepayment accounting behavior without forcing a destructive redesign later.

The initial MVP UI may allocate payments directly to invoices.

Receivable views treat unallocated customer money as customer credit when customer is identified.

---

# 14. Credit Notes & Refunds

## 14.1 `credit_notes`

```sql
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
```

## 14.2 `credit_note_lines`

```sql
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
```

Cumulative return quantity is enforced transactionally using row locks on the original invoice line and prior credit-note lines.

## 14.3 `credit_note_applications`

```sql
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
```

For the normal return workflow, the credit applies to the original invoice/customer balance.

## 14.4 `customer_refunds`

```sql
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
```

Refund total cannot exceed unapplied/refundable credit note amount; enforced transactionally.

---

# 15. Stock Ledger

## 15.1 `stock_movements`

```sql
CREATE TABLE stock_movements (
    stock_movement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,

    movement_type VARCHAR(30) NOT NULL,
    quantity NUMERIC(15,3) NOT NULL,

    reference_type VARCHAR(30) NOT NULL,
    reference_id UUID NOT NULL,
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

    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);
```

`reference_id` is the source aggregate ID.

`source_line_id` is the precise source row where applicable:

```text
invoice_line_id
grn_item_id
credit_note_line_id
supplier_return_item_id
job_part_id
stock_adjustment_id
```

## 15.2 Source Uniqueness

Recommended:

```sql
CREATE UNIQUE INDEX uq_stock_movement_source
ON stock_movements(movement_type, source_line_id)
WHERE source_line_id IS NOT NULL;
```

This prevents a retry from creating the same movement twice.

## 15.3 Indexes

```sql
CREATE INDEX idx_stock_movements_product_time
ON stock_movements(product_id, created_at);

CREATE INDEX idx_stock_movements_reference
ON stock_movements(reference_type, reference_id);
```

## 15.4 Stock View

```sql
CREATE VIEW v_stock_on_hand AS
SELECT
    product_id,
    COALESCE(SUM(quantity), 0)::NUMERIC(15,3) AS physical_stock
FROM stock_movements
GROUP BY product_id;
```

## 15.5 Reserved Stock View

```sql
CREATE VIEW v_reserved_stock AS
SELECT
    hsi.product_id,
    COALESCE(SUM(hsi.quantity), 0)::NUMERIC(15,3) AS reserved_stock
FROM held_sale_items hsi
JOIN held_sales hs ON hs.held_sale_id = hsi.held_sale_id
WHERE hs.status IN ('HELD','RESUMED')
GROUP BY hsi.product_id;
```

## 15.6 Available Stock View

```sql
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
```

---

# 16. Stock Adjustment Schema

## 16.1 `stock_adjustments`

```sql
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
```

Approval application service creates exactly one `ADJUSTMENT` movement.

Reversal uses a new approved adjustment linked through `reverses_adjustment_id` and creates an `ADJUSTMENT_REVERSAL` movement.

---

# 17. Supplier & Purchasing Schema

## 17.1 `suppliers`

```sql
CREATE TABLE suppliers (
    supplier_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    contact_person VARCHAR(100),
    address VARCHAR(300),
    phone VARCHAR(20),
    email VARCHAR(150),
    tin_number VARCHAR(30),
    payment_terms VARCHAR(100),
    opening_balance NUMERIC(15,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CHECK (opening_balance >= 0),
    CHECK (status IN ('ACTIVE','INACTIVE'))
);
```

## 17.2 `grns`

```sql
CREATE TABLE grns (
    grn_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    grn_number VARCHAR(30) UNIQUE,
    supplier_id UUID NOT NULL,
    supplier_reference VARCHAR(100),
    grn_date DATE NOT NULL,
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
        (status = 'DRAFT' AND grn_number IS NULL AND posted_at IS NULL)
        OR
        (status IN ('POSTED','REVERSED') AND grn_number IS NOT NULL AND posted_at IS NOT NULL)
    ),

    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (reversed_by) REFERENCES users(user_id)
);
```

Optional duplicate supplier reference protection:

```sql
CREATE UNIQUE INDEX uq_grn_supplier_reference
ON grns(supplier_id, supplier_reference)
WHERE supplier_reference IS NOT NULL
  AND status IN ('POSTED','REVERSED');
```

## 17.3 `grn_items`

```sql
CREATE TABLE grn_items (
    grn_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grn_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    product_id UUID NOT NULL,
    quantity_received NUMERIC(15,3) NOT NULL,
    unit_cost NUMERIC(15,2) NOT NULL,
    total_cost NUMERIC(15,2) NOT NULL,

    UNIQUE (grn_id, line_number),

    CHECK (quantity_received > 0),
    CHECK (unit_cost >= 0),
    CHECK (total_cost >= 0),

    FOREIGN KEY (grn_id) REFERENCES grns(grn_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);
```

## 17.4 `product_cost_history`

```sql
CREATE TABLE product_cost_history (
    product_cost_history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    grn_item_id UUID NOT NULL UNIQUE,
    unit_cost NUMERIC(15,2) NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (unit_cost >= 0),

    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (grn_item_id) REFERENCES grn_items(grn_item_id)
);
```

## 17.5 `supplier_returns`

```sql
CREATE TABLE supplier_returns (
    supplier_return_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL UNIQUE,
    return_number VARCHAR(30) NOT NULL UNIQUE,
    supplier_id UUID NOT NULL,
    grn_id UUID NOT NULL,
    total_amount NUMERIC(15,2) NOT NULL,
    reason TEXT NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (total_amount >= 0),

    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (grn_id) REFERENCES grns(grn_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);
```

## 17.6 `supplier_return_items`

```sql
CREATE TABLE supplier_return_items (
    supplier_return_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_return_id UUID NOT NULL,
    grn_item_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity_returned NUMERIC(15,3) NOT NULL,
    unit_cost NUMERIC(15,2) NOT NULL,
    line_total NUMERIC(15,2) NOT NULL,

    CHECK (quantity_returned > 0),
    CHECK (unit_cost >= 0),
    CHECK (line_total >= 0),

    FOREIGN KEY (supplier_return_id) REFERENCES supplier_returns(supplier_return_id),
    FOREIGN KEY (grn_item_id) REFERENCES grn_items(grn_item_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);
```

Cumulative return ≤ received is enforced transactionally with locks.

## 17.7 `supplier_payments`

```sql
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
```

## 17.8 `supplier_payment_allocations`

```sql
CREATE TABLE supplier_payment_allocations (
    supplier_payment_allocation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_payment_id UUID NOT NULL,
    grn_id UUID NOT NULL,
    allocated_amount NUMERIC(15,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (allocated_amount > 0),
    UNIQUE (supplier_payment_id, grn_id),

    FOREIGN KEY (supplier_payment_id) REFERENCES supplier_payments(supplier_payment_id),
    FOREIGN KEY (grn_id) REFERENCES grns(grn_id)
);
```

### Allocation Rule

Transaction locks:

- supplier payment;
- target GRNs;
- existing allocations.

Then verifies:

```text
allocated amount <= GRN outstanding
sum allocations <= payment amount
all GRNs belong to payment supplier
```

An unallocated remainder is visible as supplier-account credit/prepayment; initial MVP UI should normally allocate payments to outstanding GRNs.

---

# 18. Payable Views

## 18.1 GRN Balance Concept

For each posted GRN:

```text
GRN amount
- supplier-return amount linked to GRN
- payment allocations
= outstanding
```

Example view structure:

```sql
CREATE VIEW v_grn_outstanding AS
SELECT
    g.grn_id,
    g.supplier_id,
    g.grn_number,
    g.grn_date,
    g.total_amount,
    COALESCE(r.returned_amount, 0) AS returned_amount,
    COALESCE(p.paid_amount, 0) AS paid_amount,
    (
      g.total_amount
      - COALESCE(r.returned_amount, 0)
      - COALESCE(p.paid_amount, 0)
    )::NUMERIC(15,2) AS outstanding_amount
FROM grns g
LEFT JOIN (
    SELECT grn_id, SUM(total_amount) AS returned_amount
    FROM supplier_returns
    GROUP BY grn_id
) r ON r.grn_id = g.grn_id
LEFT JOIN (
    SELECT spa.grn_id, SUM(spa.allocated_amount) AS paid_amount
    FROM supplier_payment_allocations spa
    GROUP BY spa.grn_id
) p ON p.grn_id = g.grn_id
WHERE g.status = 'POSTED';
```

Supplier opening balance and any unallocated supplier credit are combined at supplier-statement query level.

---

# 19. Appointment Schema

## 19.1 Physical Time Decision

Appointments store absolute scheduled instants:

```text
start_at TIMESTAMPTZ
end_at TIMESTAMPTZ
blocked_until_at TIMESTAMPTZ
```

`blocked_until_at` snapshots the configured appointment buffer at create/reschedule time.

Example:

```text
start       10:00
service end 11:00
buffer      15m
blocked     11:15
```

This makes the exclusion constraint deterministic even if future configuration changes.

## 19.2 `appointments`

```sql
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
    FOREIGN KEY (service_id) REFERENCES services(service_id),
    FOREIGN KEY (technician_id) REFERENCES users(user_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);
```

## 19.3 Database Double-Booking Protection

```sql
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
```

This means two active appointments for the same technician cannot overlap.

`CANCELLED`, `NO_SHOW`, and `COMPLETED` no longer block new schedules.

### Rescheduling

Update of:

```text
start_at
end_at
blocked_until_at
technician_id
```

is subject to the same exclusion constraint.

Application catches exclusion violation and returns:

```text
409 APPOINTMENT_CONFLICT
```

---

# 20. Job Card Schema

## 20.1 `job_cards`

```sql
CREATE TABLE job_cards (
    job_card_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    job_number VARCHAR(30) NOT NULL UNIQUE,

    appointment_id UUID UNIQUE,
    customer_id UUID NOT NULL,
    technician_id UUID,

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
    FOREIGN KEY (technician_id) REFERENCES users(user_id)
);
```

### Appointment Conversion Decision

The logical MVP field:

```text
appointment.converted_to_job_card_id
```

is implemented as a **derived relationship** from:

```text
job_cards.appointment_id UNIQUE
```

This avoids storing the same one-to-one link twice and eliminates circular-FK inconsistency.

The API can still return:

```text
convertedToJobCardId
```

by querying the job card.

## 20.2 `job_services`

```sql
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
    FOREIGN KEY (service_id) REFERENCES services(service_id)
);
```

## 20.3 `job_parts`

```sql
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
```

Each row produces one `JOB_PART` stock movement using `source_line_id = job_part_id`.

## 20.4 `job_estimates`

```sql
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
```

A revised estimate is a new version rather than silently rewriting an accepted/declined historical estimate.

---

# 21. Cashbook Schema

## 21.1 `cashbook_entries`

```sql
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
```

## 21.2 Source Uniqueness

System-generated monetary source should create one cashbook entry.

```sql
CREATE UNIQUE INDEX uq_cashbook_source
ON cashbook_entries(source_type, reference_id)
WHERE source_type <> 'MANUAL'
  AND reference_id IS NOT NULL;
```

For a manual entry, `request_id` protects against retry duplication.

## 21.3 Direction Rules

Application posting rules:

```text
CUSTOMER_PAYMENT → IN
CUSTOMER_REFUND  → OUT
SUPPLIER_PAYMENT → OUT
MANUAL           → IN or OUT
```

Integration tests enforce these semantic rules.

---

# 22. Cash Closing Schema

## 22.1 `cash_closings`

```sql
CREATE TABLE cash_closings (
    cash_closing_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL UNIQUE,

    business_date DATE NOT NULL,
    cashier_id UUID NOT NULL,

    expected_cash NUMERIC(15,2) NOT NULL,
    counted_cash NUMERIC(15,2) NOT NULL,
    variance NUMERIC(15,2) NOT NULL,

    variance_reason TEXT,
    status VARCHAR(20) NOT NULL,

    closed_by UUID NOT NULL,
    closed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    approved_by UUID,
    approved_at TIMESTAMPTZ,

    UNIQUE (business_date, cashier_id),

    CHECK (status IN ('PENDING_APPROVAL','APPROVED')),
    CHECK (
        variance = counted_cash - expected_cash
    ),
    CHECK (
        variance = 0
        OR length(trim(variance_reason)) > 0
    ),
    CHECK (
        (status = 'PENDING_APPROVAL' AND approved_by IS NULL AND approved_at IS NULL)
        OR
        (status = 'APPROVED' AND (
            variance = 0
            OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
        ))
    ),

    FOREIGN KEY (cashier_id) REFERENCES users(user_id),
    FOREIGN KEY (closed_by) REFERENCES users(user_id),
    FOREIGN KEY (approved_by) REFERENCES users(user_id)
);
```

### Zero-Variance Decision

**Resolved physical design:** zero-variance closing is stored directly as:

```text
APPROVED
```

without requiring a separate manager approver.

This does not bypass the create permission; it means no variance approval is needed when expected = counted.

For non-zero variance:

```text
PENDING_APPROVAL → APPROVED
```

requires manager approval.

---

# 23. Receivable Views

## 23.1 Invoice Applied Payments

```sql
CREATE VIEW v_invoice_payment_totals AS
SELECT
    i.invoice_id,
    COALESCE(SUM(cpa.allocated_amount), 0)::NUMERIC(15,2) AS payment_amount
FROM invoices i
LEFT JOIN customer_payment_allocations cpa
  ON cpa.invoice_id = i.invoice_id
GROUP BY i.invoice_id;
```

## 23.2 Invoice Credit Applications

```sql
CREATE VIEW v_invoice_credit_totals AS
SELECT
    i.invoice_id,
    COALESCE(SUM(cna.applied_amount), 0)::NUMERIC(15,2) AS credit_amount
FROM invoices i
LEFT JOIN credit_note_applications cna
  ON cna.invoice_id = i.invoice_id
GROUP BY i.invoice_id;
```

## 23.3 Invoice Balance

```sql
CREATE VIEW v_invoice_balances AS
SELECT
    i.invoice_id,
    i.invoice_number,
    i.customer_id,
    i.invoice_date,
    i.due_date,
    i.total_amount,
    COALESCE(p.payment_amount, 0) AS amount_paid,
    COALESCE(c.credit_amount, 0) AS credit_applied,
    GREATEST(
      i.total_amount
      - COALESCE(p.payment_amount, 0)
      - COALESCE(c.credit_amount, 0),
      0
    )::NUMERIC(15,2) AS balance_due,
    CASE
      WHEN i.status = 'VOIDED' THEN 'VOIDED'
      WHEN (
        i.total_amount
        - COALESCE(p.payment_amount, 0)
        - COALESCE(c.credit_amount, 0)
      ) <= 0 THEN 'PAID'
      WHEN COALESCE(p.payment_amount, 0) + COALESCE(c.credit_amount, 0) > 0
        THEN 'PARTIAL'
      ELSE 'UNPAID'
    END AS payment_status
FROM invoices i
LEFT JOIN v_invoice_payment_totals p ON p.invoice_id = i.invoice_id
LEFT JOIN v_invoice_credit_totals c ON c.invoice_id = i.invoice_id
WHERE i.status IN ('POSTED','VOIDED');
```

## 23.4 Customer Receivable

```sql
CREATE VIEW v_customer_receivables AS
SELECT
    customer_id,
    SUM(balance_due)::NUMERIC(15,2) AS outstanding_receivable
FROM v_invoice_balances
WHERE payment_status <> 'VOIDED'
GROUP BY customer_id;
```

Aging buckets are produced by query using invoice/due dates and business date.

Unallocated customer payments are shown separately as customer credit and netted in the customer statement according to finance query rules.

---

# 24. Audit Schema

## 24.1 `audit_logs`

Use a flexible action code rather than a restrictive PostgreSQL enum.

```sql
CREATE TABLE audit_logs (
    audit_log_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,

    entity_type VARCHAR(60) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action_code VARCHAR(80) NOT NULL,

    actor_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    actor_user_id UUID,

    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    details JSONB,
    changed_fields JSONB,

    ip_address INET,
    client_id VARCHAR(100),
    correlation_id UUID,

    CHECK (actor_type IN ('USER','SYSTEM')),
    CHECK (
        (actor_type = 'USER' AND actor_user_id IS NOT NULL)
        OR actor_type = 'SYSTEM'
    ),

    FOREIGN KEY (actor_user_id) REFERENCES users(user_id)
);
```

Indexes:

```sql
CREATE INDEX idx_audit_entity
ON audit_logs(entity_type, entity_id, occurred_at DESC);

CREATE INDEX idx_audit_actor
ON audit_logs(actor_user_id, occurred_at DESC);

CREATE INDEX idx_audit_action
ON audit_logs(action_code, occurred_at DESC);
```

PII view/export actions are written here.

---

# 25. Backup & Restore Schema

## 25.1 Design Decision

Use separate backup and restore histories.

Reason:

- one verified backup may be restored more than once;
- changing one backup row to `RESTORED` loses repeated restore history;
- audit/recovery evidence is clearer with two entities.

## 25.2 `backup_records`

```sql
CREATE TABLE backup_records (
    backup_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT,
    checksum_sha256 CHAR(64),

    status VARCHAR(20) NOT NULL,
    initiated_by UUID NOT NULL,

    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    error_message TEXT,

    CHECK (status IN ('STARTED','VERIFIED','FAILED')),
    CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0),

    FOREIGN KEY (initiated_by) REFERENCES users(user_id)
);
```

## 25.3 `restore_records`

```sql
CREATE TABLE restore_records (
    restore_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL UNIQUE,
    backup_id UUID NOT NULL,

    status VARCHAR(20) NOT NULL,
    initiated_by UUID NOT NULL,

    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,

    restored_schema_version VARCHAR(100),
    verification_details JSONB,
    error_message TEXT,

    CHECK (status IN ('STARTED','VERIFIED','FAILED')),

    FOREIGN KEY (backup_id) REFERENCES backup_records(backup_id),
    FOREIGN KEY (initiated_by) REFERENCES users(user_id)
);
```

A restore counts as successful only when:

```text
status = VERIFIED
```

after database readability/schema/application smoke verification.

---

# 26. Invoice Void Representation

## 26.1 Design Decision

Invoice void metadata remains on `invoices`:

```text
status = VOIDED
voided_at
voided_by
void_reason
```

Economic corrections are represented by explicit immutable reversal records.

## 26.2 Stock

For product sale quantity reversal:

```text
stock_movements.movement_type = SALE_VOID
```

Each reversal movement references the original invoice line as source context and must have unique reversal identity.

## 26.3 Payment

If money was already collected, voiding does not silently delete payment.

The application must perform:

```text
refund / retained customer credit / other valid corrective treatment
```

using the existing payment/credit-note/refund model.

## 26.4 VAT

Historical posted invoice remains in audit/report history with VOIDED status; VAT reporting uses the valid reversing document/effect according to configured report rules.

---

# 27. GRN Reversal Representation

Normal posted GRN correction uses:

```text
Supplier Return
```

which is the primary MVP workflow.

A true full GRN reversal is reserved for exceptional administrative correction.

When used:

- GRN status becomes `REVERSED`;
- `GRN_REVERSAL` stock movements are created;
- payable effect is reversed;
- reason/actor/time recorded;
- it cannot be used if downstream returns/payments make full reversal inconsistent without corresponding corrective processing.

Implementation should keep full reversal restricted.

---

# 28. Request Idempotency

## 28.1 Per-Root `request_id`

The following tables have a unique request ID:

```text
invoices (posting request)
customer_payments
customer_refunds
credit_notes
stock_adjustments where relevant
grns
supplier_returns
supplier_payments
job_parts where created as direct posting
cash_closings
backup_records
restore_records
```

## 28.2 Payload Conflict

A retry with the same request ID and the same logical command returns the original result.

Same ID with incompatible payload:

```text
409 IDEMPOTENCY_CONFLICT
```

The server may persist a request hash if necessary to distinguish legitimate retry from key misuse.

## 28.3 Optional `idempotency_records`

If centralized handling is preferred:

```sql
CREATE TABLE idempotency_records (
    request_id UUID PRIMARY KEY,
    operation VARCHAR(80) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_type VARCHAR(60),
    resource_id UUID,
    response_code INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**Recommended implementation:** use this centralized table for API consistency while also keeping unique request IDs on critical root tables where useful.

---

# 29. Search & Performance Index Plan

Indexes should be justified by actual queries and `EXPLAIN ANALYZE`, but these are expected MVP indexes.

## Identity

```text
users(lower(username))
user_sessions(user_id, expires_at) active partial
user_role_assignments(user_id, is_active, expires_at)
```

## Customer

```text
customer_code unique
name trigram
phone trigram
status/category
```

## Product

```text
sku unique
barcode unique
name trigram
category + active
```

## Invoice

```sql
CREATE INDEX idx_invoices_date_status
ON invoices(invoice_date, status);

CREATE INDEX idx_invoices_customer_date
ON invoices(customer_id, invoice_date DESC);

CREATE INDEX idx_invoices_number
ON invoices(invoice_number)
WHERE invoice_number IS NOT NULL;
```

## Payment

```text
customer_payments(customer_id, payment_date)
customer_payment_allocations(invoice_id)
```

## Stock

```text
stock_movements(product_id, created_at)
stock_movements(reference_type, reference_id)
```

## Purchasing

```text
grns(supplier_id, grn_date)
supplier_payments(supplier_id, payment_date)
supplier_payment_allocations(grn_id)
supplier_returns(grn_id)
```

## Scheduling

GiST exclusion index generated by the exclusion constraint plus:

```text
appointments(start_at)
appointments(technician_id, start_at)
appointments(customer_id, start_at desc)
```

## Jobs

```text
job_cards(status, estimated_completion_date)
job_cards(customer_id, created_at desc)
job_cards(technician_id, status)
```

## Audit

As defined earlier.

---

# 30. Foreign-Key Delete Policy

Default rule:

```text
NO CASCADE DELETE for business history
```

Use deactivation/status changes instead.

## 30.1 Appropriate Cascade

Only tightly owned draft/detail rows may cascade if safe.

Potential examples:

```text
DRAFT invoice → invoice_lines
held_sale → held_sale_items
DRAFT GRN → grn_items
```

However JPA/application logic should still control deletion.

## 30.2 Never Cascade Historical Deletion

Do not cascade-delete:

- posted invoice;
- payments;
- credit notes;
- stock movements;
- posted GRN;
- supplier payments/returns;
- cashbook;
- cash closing;
- audit records;
- backup/restore history.

---

# 31. Immutability Enforcement Strategy

## 31.1 Application First

Domain/application services reject edits to posted/approved records.

## 31.2 Database Guard

For especially critical records, PostgreSQL triggers may reject `UPDATE`/`DELETE` of immutable business fields.

Recommended protected tables after posting:

```text
invoice_lines when invoice POSTED/VOIDED
credit_notes
credit_note_lines
stock_movements
customer_payments
customer_payment_allocations
customer_refunds
product_cost_history
supplier_returns/items
supplier_payments/allocations
cashbook_entries
approved cash_closings
audit_logs
```

## 31.3 Trigger Philosophy

Use triggers as **integrity guards**, not as the primary place for business workflows.

Do not hide the entire sale-posting workflow inside database triggers.

Spring application services remain the explicit transaction orchestrator.

---

# 32. Updated Timestamp Strategy

For mutable master data, application updates:

```text
updated_at = current timestamp
```

Optionally a shared PostgreSQL trigger may manage `updated_at`, but the project should choose one consistent approach.

Recommended:

```text
application/JPA auditing for updated_at
database default for created_at
```

Critical posted timestamps are explicitly set by application/server clock.

---

# 33. Tax & Rounding Storage

## 33.1 Stored Values

Posted invoice lines store:

```text
unit_price
discount_amount
taxable_amount
vat_rate_snapshot
vat_amount
line_total_incl_vat
```

Invoice header stores:

```text
subtotal
discount_amount
taxable_amount
vat_rate_snapshot
vat_amount
total_amount
```

## 33.2 Rounding Decision

**Resolved MVP rule:**

- calculations use `BigDecimal`;
- intermediate multiplication uses sufficient precision;
- each invoice line's monetary outputs are rounded to 2 decimal places using `RoundingMode.HALF_UP`;
- invoice VAT is the sum of rounded line VAT amounts;
- invoice total is the sum of final line totals after invoice-level discount allocation;
- no binary floating-point arithmetic.

## 33.3 Invoice-Level Discount Allocation

To maintain line-level tax accuracy, an invoice-level discount is allocated proportionally across eligible lines.

For line `i`:

```text
allocation ratio
=
line taxable before invoice discount
/
total eligible taxable before invoice discount
```

The server distributes rounding remainder deterministically to ensure:

```text
SUM(line allocated discounts) = invoice discount amount
SUM(line VAT) = invoice VAT
SUM(line totals) = invoice total
```

The exact deterministic remainder algorithm must have unit tests.

---

# 34. Low-Stock Definition

**Resolved MVP design:**

```text
low stock
=
available stock <= reorder point
```

where:

```text
available stock
=
physical stock - active held reservations
```

This is used consistently for:

- dashboard low-stock count;
- low-stock report;
- inventory alert screen.

Stock-on-hand report separately shows physical stock.

---

# 35. Customer Credit & Aging Query Design

## 35.1 Aging Basis

Open posted invoice balance is aged from:

```text
due_date if present
else invoice_date
```

as of the selected business date.

Buckets:

```text
0–30
31–60
61–90
91+
```

## 35.2 Server Posting Lock

During credit sale posting:

1. lock/select customer;
2. compute current receivable from committed posted sources;
3. compute aging status;
4. add requested new credit;
5. enforce limit/rule;
6. post within same transaction.

This prevents two concurrent credit invoices from independently seeing the same remaining credit capacity.

---

# 36. Stock Concurrency Strategy

To avoid concurrent overselling:

## 36.1 Recommended Product Lock

For every inventory product in a stock-affecting transaction:

```sql
SELECT product_id
FROM products
WHERE product_id IN (...)
ORDER BY product_id
FOR UPDATE;
```

Then calculate stock/reservations and post movements.

Sorting lock IDs avoids inconsistent lock ordering/deadlock risk.

## 36.2 Operations Requiring Stock Locks

```text
Post sale
Hold/reserve sale
Convert held sale
Post customer return
Post supplier return
Post job part
Approve negative stock adjustment
Post GRN when needed for ordered lock consistency
```

MVP policy may allow negative stock only where explicitly intended. Normal online sale should reject insufficient available stock.

---

# 37. Supplier Allocation Concurrency

When recording supplier payment:

```text
lock supplier
lock target GRNs in stable ID order
query existing allocations/returns
validate outstanding
insert payment + allocations
insert cashbook
commit
```

Two users cannot both allocate the same remaining GRN balance.

---

# 38. Customer Allocation Concurrency

When recording customer payment:

```text
lock target invoice(s) in stable ID order
query existing allocations/credit applications
validate outstanding
insert payment + allocations
insert cashbook
commit
```

This prevents invoice overpayment through concurrent clients.

---

# 39. Appointment Concurrency Decision

The PostgreSQL exclusion constraint is the final authority.

Application sequence:

```text
pre-check for friendly UI
→ attempt insert/update
→ DB exclusion constraint
→ success OR translate exclusion violation to APPOINTMENT_CONFLICT
```

No application-level `existsConflict()` check alone is considered sufficient.

---

# 40. Reporting Read Models

Recommended database views/materialized views are only for read convenience.

Initial MVP should prefer normal views/query projections unless measured performance requires materialization.

## Required query models

```text
v_stock_on_hand
v_reserved_stock
v_available_stock
v_invoice_balances
v_customer_receivables
v_grn_outstanding
supplier statement query
cashbook report query
daily sales query
sales-by-product query
sales-by-payment-method query
low-stock query
appointment summary query
job-card status query
VAT summary query
```

Reports must be tested against seeded source documents.

---

# 41. Dashboard Query Definitions

## Today's Sales

```text
eligible POSTED invoice totals
for current Sri Lankan business date
less qualifying applied credit-note effect
```

## Monthly Revenue

Same canonical sales basis over month-to-date.

## Outstanding Receivables

Net customer receivable read model.

## Outstanding Payables

Net supplier payable including opening balance and valid unallocated credits according to supplier statement rules.

## Low Stock

Count from `v_available_stock` where:

```text
available_stock <= reorder_point
```

## Today's Appointments

Appointment date derived in `Asia/Colombo`.

## Pending Job Cards

Statuses:

```text
CREATED
ESTIMATE_PENDING
ESTIMATE_APPROVED
IN_PROGRESS
READY_FOR_PICKUP
```

---

# 42. Data Retention & Anonymization

Customer anonymization must preserve transactional integrity.

## 42.1 Customer Master

Eligible anonymization:

```text
name → "Deleted Customer <code/id>"
phone/email/address → null or anonymized
encrypted NIC/BR → null
is_anonymized → true
anonymized_at → now
```

## 42.2 Historical Transactions

Invoices and other posted documents retain legal/business snapshots required for statutory/audit retention according to the MVP/SRS policy.

The exact Sri Lankan legal retention period is a compliance decision outside this physical schema document unless separately verified and approved.

---

# 43. Database Security

## 43.1 Roles

Production PostgreSQL should not run Bizco with superuser credentials.

Recommended:

```text
bizco_owner     migration/schema ownership
bizco_app       runtime CRUD on approved schema objects
bizco_backup    backup role where operationally suitable
```

Actual deployment can simplify on a single-PC installation but should preserve least privilege where practical.

## 43.2 Client Isolation

Port 5432:

```text
server-local/backend-only
```

LAN clients connect only to Spring Boot.

## 43.3 Secrets

Do not store:

- raw passwords;
- DB admin passwords;
- AES encryption key;
- session raw tokens;

in business tables.

Session table stores token hash only.

---

# 44. Backup/Restore Data Considerations

Application database backup includes all transactional/configuration/audit tables.

External filesystem items such as:

```text
business logo
generated exports if retained
```

need a documented file-backup policy if they are necessary for full recovery.

MVP backup acceptance must at minimum prove PostgreSQL business data recovery as defined in MVP v1.3.

---

# 45. Seed Data Strategy

Flyway/reference initialization should seed:

## Permissions

All MVP permission codes.

## Roles

```text
SUPER_ADMIN
OWNER
MANAGER
ACCOUNTANT
CASHIER
STORE_KEEPER
SERVICE_OFFICER
AUDITOR
```

## Role Permissions

Approved MVP matrix.

`SUPER_ADMIN` receives all registered MVP permissions.

## UOM

```text
PCS
KG
LTR
BOX
DOZ
BTL
CASE
MTR
```

## Tax

```text
VAT disabled by default
VAT rate 18.0000
```

## Users

Passwords must not be hardcoded into Flyway SQL.

First-launch/onboarding application service securely creates:

```text
admin
manager
cashier
```

with generated/set temporary passwords and `must_change_password = true`, matching MVP onboarding behavior.

---

# 46. Proposed Flyway Migration Plan

The original DevelopmentPlan sequence is retained conceptually but refined to match the final model.

```text
V001__extensions.sql

V002__permissions_roles_and_users.sql
V003__sessions_login_history_and_staff_profiles.sql

V004__business_tax_and_system_configuration.sql

V005__customers.sql
V006__catalog_categories_uom_products_services.sql

V007__document_sequences.sql

V008__sales_invoices_and_approvals.sql
V009__held_sales.sql
V010__customer_payments_credit_notes_and_refunds.sql

V011__stock_ledger_and_adjustments.sql

V012__suppliers_grn_and_cost_history.sql
V013__supplier_returns_payments_and_allocations.sql

V014__appointments.sql
V015__job_cards_services_parts_and_estimates.sql

V016__cashbook_and_cash_closings.sql

V017__audit_logs.sql
V018__backup_and_restore_history.sql

V019__read_views.sql
V020__indexes_and_constraints.sql
V021__seed_permissions_roles_uom_tax.sql
```

## 46.1 Migration Rule

Once a migration has run outside a disposable development database:

```text
never edit it
```

Add a new migration.

---

# 47. JPA Mapping Guidance

## 47.1 Avoid Giant Bidirectional Graphs

Prefer controlled aggregate mappings.

Example:

```text
Invoice → invoice lines
```

can be owned relationship.

But avoid automatically loading:

```text
Customer → every Invoice → every Line → Product
```

## 47.2 Fetching

Default to LAZY for collections.

Use explicit projections for list/report screens.

## 47.3 Version

Mutable roots map:

```java
@Version
private long version;
```

## 47.4 Money

Use:

```java
BigDecimal
```

with explicit column precision/scale.

## 47.5 Instant

Use:

```java
Instant
```

or `OffsetDateTime`.

Business date:

```java
LocalDate
```

---

# 48. Integrity Tests Required Before Release

## 48.1 Invoice

- DRAFT has no official number.
- POST assigns number once.
- POST retry produces no duplicate.
- POSTED invoice lines cannot be changed through application.
- void retains original.
- split/partial/credit balance correct.

## 48.2 Stock

- ledger sum equals stock-on-hand.
- held sale changes reserved, not physical.
- converted held sale releases reservation exactly once.
- failed sale leaves no movement.
- duplicate request creates no duplicate movement.

## 48.3 GRN

- posting creates GRN stock + cost history + payable atomically.
- failed cost/payable posting rolls back stock.
- supplier return cannot exceed receipt.

## 48.4 Finance

- payment allocation cannot exceed invoice.
- supplier allocation cannot exceed GRN.
- cashbook source does not duplicate.
- receivable/payable views match source data.
- cash closing expected cash reconciles.

## 48.5 Scheduling

- overlap constraint rejects second concurrent appointment.
- cancelled appointment frees slot.
- reschedule is constraint-protected.

## 48.6 Security

- expired role authorization denied even before cleanup job updates row.
- session timeout.
- concurrent-session limit.
- unknown username login recorded.
- SUPER_ADMIN permission set complete.

## 48.7 Recovery

- backup checksum stored.
- invalid backup rejected.
- verified backup restores.
- schema/Flyway version readable after restore.

---

# 49. Referential Integrity Summary

Key mandatory relationships:

```text
User → Primary Role
Secondary Role → User + Role + Granting User

Product → Category + UOM
Invoice → Cashier
InvoiceLine → Invoice
Product/Service line → correct referenced master

PaymentAllocation → Payment + Invoice
CreditNote → Original Invoice + Customer
CreditNoteLine → Original InvoiceLine

StockMovement → Product
StockAdjustment → Product

GRN → Supplier
GRNItem → GRN + Product
CostHistory → GRNItem

SupplierReturn → Supplier + GRN
SupplierReturnItem → Return + GRNItem + Product

SupplierPayment → Supplier
SupplierPaymentAllocation → Payment + GRN

Appointment → Customer + Service + optional Technician
JobCard → Customer + optional Appointment + optional Technician
JobService → JobCard + Service
JobPart → JobCard + Product
JobEstimate → JobCard

CashClosing → Cashier + Closing User + optional Approver

Audit → optional actor user
Backup/Restore → initiating user
```

---

# 50. Deliberately Derived Fields

The API/UI may expose these even though they are not authoritative stored columns.

| Field | Derived From |
|---|---|
| Customer current balance | receivable query |
| Supplier current balance | payable query |
| Product stock qty | stock movement ledger |
| Product available qty | stock − held reservations |
| Invoice amount paid | payment allocations |
| Invoice balance due | invoice total − payments − credits |
| Invoice payment status | balance/payment calculation |
| Appointment converted job ID | `job_cards.appointment_id` |
| Low-stock status | available stock vs reorder point |

This avoids duplicated transactional truth.

---

# 51. Remaining API-Level Decisions

The database design is sufficiently stable to proceed to API design.

The next artifact should finalize:

1. request/response DTOs;
2. create-vs-post flows;
3. idempotency header/body convention;
4. pagination/filter syntax;
5. error code mapping;
6. optimistic-lock version handling;
7. manager approval authentication flow;
8. payment allocation request shape;
9. report endpoints;
10. PDF/CSV download contracts.

These belong in:

```text
ApiContracts.md
```

and do not require additional database-model changes unless implementation review finds a defect.

---

# 52. Database Freeze Decision Summary

The following previously-open design choices are now resolved.

| Decision | Resolution |
|---|---|
| Appointment overlap | PostgreSQL GiST exclusion using technician + `tstzrange`, with snapshotted buffer end |
| Customer payment storage | normalized `customer_payments` + `customer_payment_allocations`; invoice API preserved |
| Supplier payments | normalized payment + allocation table; partial allocation supported |
| Invoice lifecycle | `DRAFT`, `POSTED`, `VOIDED` |
| GRN lifecycle | `DRAFT`, `POSTED`, `REVERSED` |
| Invoice number allocation | transactional `document_sequences`; drafts consume no official number |
| Payment status | derived from posted allocations/credits |
| Customer/supplier balances | derived/reconcilable, not independently mutable |
| Physical stock | `stock_movements` |
| Reserved stock | active held-sale items |
| Low-stock basis | available stock |
| Time | `DATE` for business dates, `TIMESTAMPTZ` for instants |
| Technician identity | user + `staff_profiles.is_technician`, independent of RBAC role |
| Job conversion link | unique `job_cards.appointment_id`, converted ID derived |
| VAT rounding | line-level HALF_UP to 2 decimals; header is sum of line results |
| Invoice discount | proportional line allocation with deterministic rounding remainder |
| Audit actions | extensible `action_code VARCHAR`, not rigid PostgreSQL enum |
| Backup history | separate backup and restore records |
| Zero-variance cash close | direct `APPROVED`; variance requires approval |
| Optimistic locking | `version` on mutable aggregates |
| Critical retries | idempotency/request keys |
| PII encryption | AES-256-GCM at application layer, key outside PostgreSQL |

---

# 53. Implementation Gate

Before writing feature code, create and validate the Flyway migrations from this design.

Required gate:

```text
Clean PostgreSQL
→ run V001..latest
→ seed reference/security data
→ Spring Boot starts
→ JPA validation passes
→ integration tests run in Testcontainers
→ schema teardown/recreate succeeds
```

Only after that baseline should feature implementation begin.

---

# 54. Recommended Next Artifact

```text
ApiContracts.md
```

After API contracts:

```text
AcceptanceTests.md
→ Final DevelopmentPlan synchronization
→ Maven project initialization
→ Flyway implementation
→ Week 1 coding
```

---

*(End of Bizco MVP PostgreSQL Database Design)*
