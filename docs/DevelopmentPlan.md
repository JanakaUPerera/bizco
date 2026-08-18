# Bizco MVP Development Plan

**Project:** SME Business Management System (Bizco)  
**Version:** 2.1  
**Team Baseline Duration:** 20 weeks  
**Solo Developer Forecast:** 26–30 weeks  
**Start Date:** TBD  
**Based On:** `SRS.md` v2.1, `MVP.md` v1.3, `DomainModel.md` v1.0, `StateMachines.md` v1.0, `DatabaseDesign.md` v1.0, `ApiContracts.md` v1.0, `AcceptanceTests.md` v1.0

---

# 1. Project Overview

## 1.1 Objective

Deliver the complete approved Bizco Minimum Viable Product for Sri Lankan SMEs using:

```text
JavaFX Client
      ↓ HTTPS / REST
Spring Boot Server
      ↓
PostgreSQL
```

The MVP enables SMEs to:

- authenticate users and enforce action-based RBAC;
- manage users, roles, temporary secondary roles, and sessions;
- manage customers and customer credit;
- manage products, categories, UOMs, services, pricing, barcode data, and tax categories;
- process retail, wholesale, service, and hybrid sales;
- use POS barcode scanning, hold/resume, discounts, approvals, and split payments;
- create sales, service, and tax invoices;
- support authorized credit sales, partial payments, receivables, returns, refunds, and credit notes;
- schedule appointments and manage technicians;
- manage repair/service job cards, estimates, services, parts, pickup, and warranty;
- track inventory through an immutable stock-movement ledger;
- receive goods through GRNs and track product cost history;
- manage suppliers, supplier returns, payments, allocations, and outstanding balances;
- maintain cashbook, receivables, payables, and daily cash closing;
- calculate VAT using posted transaction snapshots;
- provide dashboards, reports, PDF/CSV exports, receipts, and invoice QR references;
- create, verify, and restore PostgreSQL backups safely;
- operate on one PC or multiple LAN clients through the same client-server architecture.

## 1.2 Scope Protection Rule

`MVP.md` v1.3 is the authoritative MVP functional scope.

This development plan does **not** remove or defer any requirement listed as part of the MVP.

When implementation complexity affects timing:

```text
scope remains unchanged
forecast changes
```

rather than reducing the MVP.

## 1.3 Document Authority

```text
SRS.md
= full product vision

MVP.md
= authoritative MVP functional scope

DomainModel.md
= aggregate/business ownership model

StateMachines.md
= lifecycle and transition authority

DatabaseDesign.md
= physical PostgreSQL design authority

ApiContracts.md
= JavaFX ↔ Spring Boot contract

AcceptanceTests.md
= release acceptance authority

DevelopmentPlan.md
= implementation sequence and delivery schedule
```

## 1.4 In Scope / Out of Scope

| In Scope | Out of Scope |
|---|---|
| Authentication, sessions, action-based RBAC, audit | Offline SQLite cache/sync |
| Primary + temporary secondary roles | Multi-branch |
| Customer CRUD, search, credit limits, aging rules | Loyalty |
| Product/category/UOM/service management | Product variants |
| Barcode lookup/generation | Batch/serial tracking |
| POS, hold/resume, split payment | Consignment |
| Sales/service/tax invoices | Full GL/double-entry |
| Credit sale, partial payment, receivables | Financial statements |
| Returns, refunds, credit notes | SSCL/WHT |
| Internal Bizco invoice QR | IRD e-Invoicing integration |
| Appointment scheduling/calendar | SMS/email notification center |
| Job cards, estimates, parts, warranty | Payroll |
| Stock ledger, adjustment/approval | Asset management |
| Suppliers, GRN, cost history | Purchase orders |
| Supplier returns/payments/allocations | Full supplier invoice workflow |
| Cashbook, payables, cash closing | Advanced accounting |
| VAT calculation/report | Advanced analytics |
| Dashboard & MVP reports | Document management |
| Backup, verification, restore | Customer/mobile portal |

---

# 2. Pre-Development Design Gate

The following design artifacts are completed and form the implementation baseline:

```text
[✓] MVP.md v1.3
[✓] DomainModel.md v1.0
[✓] StateMachines.md v1.0
[✓] DatabaseDesign.md v1.0
[✓] ApiContracts.md v1.0
[✓] AcceptanceTests.md v1.0
```

## 2.1 Gate Decisions Already Resolved

Implementation starts with these decisions fixed:

- invoices use `DRAFT → POSTED → VOIDED`;
- posted business documents are immutable;
- official numbers are allocated transactionally only at posting;
- customer payments use payment + allocation records;
- supplier payments use payment + GRN allocation records;
- customer/supplier balances are derived and reconcilable;
- stock movements are authoritative physical stock;
- held bills reserve stock separately;
- available stock = physical − reserved;
- service/custom/product invoice lines are supported;
- historical transaction snapshots are mandatory;
- appointments use PostgreSQL-enforced overlap protection;
- business instants use `TIMESTAMPTZ`;
- technician assignment is operational capability, not RBAC role;
- critical posting APIs are idempotent;
- mutable aggregates use optimistic locking;
- audit action codes are extensible;
- backup and restore histories are separate;
- VAT is calculated line-by-line using `BigDecimal` and `HALF_UP`;
- invoice-level discounts are allocated proportionally across eligible lines;
- zero-variance cash closing completes as APPROVED;
- PostgreSQL-specific tests use Testcontainers.

## 2.2 Change Control

If development discovers a defect in these designs:

1. record the issue;
2. update the relevant design document;
3. create a new Flyway migration if schema already exists outside disposable development;
4. update API/test traceability;
5. never silently implement behavior that contradicts the approved documents.

---

# 3. Team Structure

## 3.1 Recommended Team

| Role | Count | Responsibilities |
|---|---:|---|
| Lead/Full-Stack Java Developer | 1 | architecture, backend, JavaFX, DB |
| Supporting Developer | 0.5 | features, tests, UI, review |
| QA / BA Support | 0.5 | acceptance tests, regression, UAT |
| UI/UX Support | 0.25–0.5 | workflow/layout review |
| Project/Stakeholder Review | part-time | priorities, business sign-off |

The **20-week baseline** assumes roughly 1.5 developer FTE plus part-time QA/business review.

## 3.2 Solo Developer Forecast

A solo developer performing:

- business analysis;
- architecture;
- Spring Boot;
- JavaFX;
- PostgreSQL;
- testing;
- packaging;
- documentation;
- UAT support;

should plan for:

```text
26–30 weeks
```

The exact same MVP scope remains.

Recommended solo approach:

```text
strict WIP limit
one vertical slice at a time
continuous Testcontainers integration
weekly packaged demo
```

---

# 4. Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| Language | Java 21 LTS | Server/client |
| Client | JavaFX | Desktop application |
| UI Extensions | ControlsFX | Enhanced controls |
| Backend | Spring Boot 3.x | REST server |
| Security | Spring Security + BCrypt cost 12 | Authentication/RBAC |
| Persistence | Spring Data JPA / Hibernate | ORM |
| Database | PostgreSQL 18.x recommended; 16+ supported | Production |
| Migration | Flyway | Versioned schema |
| Integration DB | PostgreSQL Testcontainers | Real DB tests |
| PDF | JasperReports / PDFBox | Receipts/reports |
| Barcode/QR | ZXing | Barcode + internal invoice QR |
| Build | Maven | Multi-module build |
| Tests | JUnit 5, AssertJ, Mockito | Unit/integration |
| UI Tests | TestFX selectively | Critical JavaFX flows |
| Version Control | Git | Source control |
| API Docs | OpenAPI/Swagger | Generated API reference |

---

# 5. Architecture Rules

These rules are release-level constraints.

```text
AR-001 JavaFX never accesses PostgreSQL directly.
AR-002 All protected actions are authorized by Spring Boot.
AR-003 JPA entities never cross the REST boundary.
AR-004 Server code is organized by business capability.
AR-005 Shared/common module contains contracts, not persistence entities.
AR-006 Posted business documents are immutable.
AR-007 stock_movements is authoritative physical stock.
AR-008 Finance balances reconcile from posted source records.
AR-009 Critical posting commands are transactional.
AR-010 Critical posting commands are idempotent.
AR-011 Reports do not own independent financial definitions.
AR-012 Historical documents use stored snapshots.
AR-013 JavaFX validation is advisory; server/database are authoritative.
```

---

# 6. Proposed Maven Structure

```text
bizco/
├── pom.xml
├── bizco-common/
│   └── DTOs, API errors, validation contracts, stable enums/constants
├── bizco-server/
│   ├── src/main/java/com/bizco/
│   │   ├── identity/
│   │   ├── customer/
│   │   ├── catalog/
│   │   ├── sales/
│   │   ├── scheduling/
│   │   │   ├── appointment/
│   │   │   └── jobcard/
│   │   ├── inventory/
│   │   ├── purchasing/
│   │   ├── finance/
│   │   ├── tax/
│   │   ├── reporting/
│   │   └── system/
│   ├── src/main/resources/db/migration/
│   └── src/test/
├── bizco-client/
│   ├── src/main/java/com/bizco/client/
│   │   ├── shell/
│   │   ├── identity/
│   │   ├── customer/
│   │   ├── catalog/
│   │   ├── sales/
│   │   ├── scheduling/
│   │   ├── inventory/
│   │   ├── purchasing/
│   │   ├── finance/
│   │   ├── reporting/
│   │   └── system/
│   └── src/test/
├── docs/
│   ├── SRS.md
│   ├── MVP.md
│   ├── DomainModel.md
│   ├── StateMachines.md
│   ├── DatabaseDesign.md
│   ├── ApiContracts.md
│   ├── AcceptanceTests.md
│   └── DevelopmentPlan.md
├── packaging/
└── scripts/
```

Each server capability may use:

```text
api/
application/
domain/
infrastructure/
```

without forcing unnecessary heavy DDD.

---

# 7. Flyway Migration Baseline

The sequence below is the as-built baseline, current as of 2026-08-18. Implementation order
diverged from the original planned sequence (audit logs, backup history, and seed data landed
early; catalog, sales, payments, and credit notes landed later, out of the original planned
order), and the version numbers were renumbered once to close the resulting gaps before adding
further developers — see 7.2.

```text
V001__extensions.sql
V002__permissions_roles_and_users.sql
V003__sessions_login_history_and_staff_profiles.sql
V004__business_tax_and_system_configuration.sql
V005__customers.sql
V006__document_sequences.sql
V007__audit_logs.sql
V008__backup_and_restore_history.sql
V009__seed_permissions_roles_uom_tax.sql
V010__customer_pii_permission.sql
V011__catalog_services_suppliers.sql
V012__idempotency_records.sql
V013__sales_invoices_and_approvals.sql
V014__customer_payments_and_cashbook.sql
V015__held_sales.sql
V016__credit_notes_and_refunds.sql
```

Not yet implemented (will be assigned the next sequential number, starting at `V017`, in
whatever order they're actually built — do not pre-pin numbers to these in other docs/comments):
stock ledger & adjustments, supplier GRN & cost history, supplier returns/payments/allocations,
appointments, job cards/services/parts/estimates, read views, indexes & constraints review.

## 7.1 Migration Rules

- Test every migration against clean PostgreSQL.
- Never change a migration already used outside a disposable development database.
- Add a new migration for every later schema correction.
- Run Flyway before JPA schema validation.
- Production JPA setting should validate, not auto-create/update schema.
- First user passwords are created by onboarding logic, not hardcoded in SQL.

## 7.2 Migration Renumbering (2026-08-18)

While solo-developed and pre-release (no shared or production database has ever applied these
migrations), `V007`, `V017`, `V018`, and `V021`–`V028` were renumbered down to `V006`–`V016` to
close gaps left by out-of-plan-order implementation, before additional developers join the
project. Per the rule above, this is a one-time exception done only because no non-disposable
database exists yet — it must not be repeated once any such database exists.

| Original | Renumbered | Content |
|---|---|---|
| V007 | V006 | document_sequences |
| V017 | V007 | audit_logs |
| V018 | V008 | backup_and_restore_history |
| V021 | V009 | seed_permissions_roles_uom_tax |
| V022 | V010 | customer_pii_permission |
| V023 | V011 | catalog_services_suppliers |
| V024 | V012 | idempotency_records |
| V025 | V013 | sales_invoices_and_approvals |
| V026 | V014 | customer_payments_and_cashbook |
| V027 | V015 | held_sales |
| V028 | V016 | credit_notes_and_refunds |

---

# 8. Development Phases

# Phase 1 — Foundation & Identity (Weeks 1–3)

## Week 1 — Project, Database Baseline & Recovery Spike

| Task | Description | Duration |
|---|---|---:|
| 1.1 | Initialize Maven parent + `common`, `server`, `client` modules | 0.5 d |
| 1.2 | Configure Java 21, Spring Boot, JavaFX, dependency management | 0.5 d |
| 1.3 | Configure PostgreSQL dev + Testcontainers | 0.5 d |
| 1.4 | Implement Flyway V001–V007 foundations | 1.5 d |
| 1.5 | Configure JPA validation and base auditing/version conventions | 0.5 d |
| 1.6 | Set up common API error/correlation/idempotency contracts | 0.5 d |
| 1.7 | Configure Git/CI: compile + unit + Testcontainers + Flyway validation | 0.5 d |
| 1.8 | Logging with correlation ID and secret redaction | 0.5 d |
| 1.9 | **Early backup/restore technical spike** using `pg_dump`/`pg_restore` | 1.0 d |

### Week 1 Recovery Spike

Validate early:

```text
PostgreSQL binary discovery
version compatibility
Windows path/permissions
Linux path/permissions if supported
credential handling
backup directory access
disk-full behavior
clean restore feasibility
```

This is not the final backup feature; it eliminates architectural risk before Week 16.

### Acceptance Focus

```text
SYS-INSTALL-001
SYS-INSTALL-002
basic DB extension/constraint tests
```

## Week 2 — Authentication, RBAC & Sessions

| Task | Description | Duration |
|---|---|---:|
| 2.1 | Implement permission registry and normalized roles/role_permissions | 0.5 d |
| 2.2 | Implement User aggregate/repository | 0.5 d |
| 2.3 | Implement secondary role assignment | 0.5 d |
| 2.4 | Implement server-side UserSession / opaque token authentication | 1.0 d |
| 2.5 | Implement login/logout/change password | 0.5 d |
| 2.6 | Implement lockout, auto-unlock, manual lock/unlock | 0.5 d |
| 2.7 | Implement effective permission resolver | 0.5 d |
| 2.8 | Implement user CRUD and role CRUD APIs | 1.0 d |
| 2.9 | Implement grant/revoke temporary role + expiry behavior | 0.5 d |
| 2.10 | Implement login history including unknown usernames | 0.5 d |
| 2.11 | Seed all MVP permissions/system roles | 0.5 d |

### Required Acceptance

```text
SEC-AUTH-001..007
SEC-SESSION-001..004
SEC-RBAC-001..008
permission negative tests
```

## Week 3 — JavaFX Foundation & First Launch

| Task | Description | Duration |
|---|---|---:|
| 3.1 | Initialize JavaFX application module | 0.5 d |
| 3.2 | Build shell: sidebar/topbar/content/status area | 0.5 d |
| 3.3 | Implement reusable HTTP API client | 1.0 d |
| 3.4 | Implement login/session handling | 0.5 d |
| 3.5 | Implement common error/dialog/loading/empty-state handling | 0.5 d |
| 3.6 | Implement permission-driven navigation/action visibility | 0.5 d |
| 3.7 | User management UI | 0.75 d |
| 3.8 | Role/permission editor | 0.75 d |
| 3.9 | Secondary role grant/revoke UI | 0.5 d |
| 3.10 | First-launch business profile/admin onboarding | 0.5 d |
| 3.11 | `/auth/me`, permission refresh, session-expiry UX | 0.5 d |

**Milestone 1:** Clean installation boots; initial admin setup works; authenticated JavaFX client uses server-only RBAC/session security.

---

# Phase 2 — Master Data (Weeks 4–5)

## Week 4 — Customers, Credit & PII

| Task | Description | Duration |
|---|---|---:|
| 4.1 | Implement Flyway/customer persistence and encrypted PII adapter | 0.5 d |
| 4.2 | Customer domain/application services | 0.5 d |
| 4.3 | Customer CRUD/search API | 0.75 d |
| 4.4 | Customer list/form JavaFX screens | 1.0 d |
| 4.5 | Credit limit and aging policy | 0.75 d |
| 4.6 | Credit summary/receivable query contract | 0.5 d |
| 4.7 | Customer PII masking and access audit | 0.5 d |
| 4.8 | Customer anonymization workflow | 0.5 d |
| 4.9 | Integration/acceptance tests | 0.5 d |

### Required Acceptance

```text
CUS-CRUD-001
CUS-VAL-001
CUS-SEARCH-001
CUS-PII-001..002
CUS-ANON-001
CRD policy unit tests
```

## Week 5 — Catalog, Services, Suppliers & Barcode

| Task | Description | Duration |
|---|---|---:|
| 5.1 | Implement categories/UOM/products/services schema/mappings | 0.5 d |
| 5.2 | Product CRUD/search/barcode API | 0.75 d |
| 5.3 | Category/UOM API | 0.5 d |
| 5.4 | Service catalog CRUD API | 0.5 d |
| 5.5 | Supplier master CRUD API | 0.5 d |
| 5.6 | Product/category/service JavaFX screens | 1.0 d |
| 5.7 | Supplier screen | 0.5 d |
| 5.8 | Barcode lookup/generation | 0.5 d |
| 5.9 | Pricing tier/default resolution rules | 0.5 d |
| 5.10 | Uniqueness/category/service tests | 0.5 d |

### Required Acceptance

```text
CAT-PROD-001..005
CAT-SVC-001
CAT-CAT-001..002
DB-001
```

**Milestone 2:** Customer, product, service, supplier and reference master data are production-ready with credit/PII/barcode rules.

---

# Phase 3 — Sales, POS & Receivables (Weeks 6–8)

## Week 6 — Sales Domain, Invoice Draft & POS Cart

| Task | Description | Duration |
|---|---|---:|
| 6.1 | Implement V008/V009 sales + held-sale persistence | 0.75 d |
| 6.2 | Invoice aggregate with PRODUCT/SERVICE/CUSTOM lines | 0.75 d |
| 6.3 | Draft invoice APIs + optimistic locking | 0.75 d |
| 6.4 | Pricing/VAT/discount calculation service | 1.0 d |
| 6.5 | Manager sales-approval evidence flow | 0.5 d |
| 6.6 | POS JavaFX product grid/search/barcode/cart | 1.0 d |
| 6.7 | Customer/pricing selection | 0.5 d |
| 6.8 | Draft preview and error states | 0.5 d |

### Required Acceptance

```text
SALE-DRAFT-001..004
SALE-PRICE-001..002
SALE-DISC-001..004
TAX-CALC-001..003
TAX-ROUND-001..002
TAX-DISC-001
```

## Week 7 — Posting, Payment, Credit Sale & Idempotency

| Task | Description | Duration |
|---|---|---:|
| 7.1 | Implement V010 customer payments/allocations | 0.5 d |
| 7.2 | Implement document sequence allocation service | 0.5 d |
| 7.3 | Implement `PostSaleService` transaction boundary | 1.5 d |
| 7.4 | Implement immediate/split/credit payment rules | 0.75 d |
| 7.5 | Implement customer credit locking/aging validation | 0.75 d |
| 7.6 | Implement cashbook source-posting integration contract | 0.5 d |
| 7.7 | Implement idempotency service/table handling | 0.5 d |
| 7.8 | POS payment JavaFX | 0.75 d |
| 7.9 | Concurrency/fault-injection tests | 1.0 d |

### Required Acceptance

```text
DOC-NUM-001..005
SALE-POST-001..008
CRD-001..005
CRD-CON-001
SYS-IDEM-001..003
TX-SALE-001
```

## Week 8 — Hold/Resume, Credit Notes, Returns, Refunds & Output

| Task | Description | Duration |
|---|---|---:|
| 8.1 | Held-sale reservation service | 0.75 d |
| 8.2 | Hold/resume/cancel/convert UI/API | 0.75 d |
| 8.3 | Credit note + return eligibility | 0.75 d |
| 8.4 | Customer refund + credit application | 0.75 d |
| 8.5 | Invoice void/reversal orchestration | 0.75 d |
| 8.6 | Invoice history + payment history | 0.5 d |
| 8.7 | Receipt/tax invoice PDF | 0.75 d |
| 8.8 | CODE-128/barcode and internal Bizco invoice QR output | 0.5 d |
| 8.9 | Reprint/audit | 0.5 d |
| 8.10 | Return/idempotency/rollback tests | 1.0 d |

### Required Acceptance

```text
SALE-HOLD-001..005
SALE-HOLD-CON-001
FIN-AR-PAY-001..004
FIN-AR-CON-001
SALE-CN-001..005
SALE-VOID-001..004
SYS-IDEM payment/credit tests
```

**Milestone 3:** Retail, wholesale/credit, mixed invoice, held bill, return/refund and receipt flows operate end-to-end.

---

# Phase 4 — Scheduling & Service Work (Weeks 9–11)

## Week 9 — Appointments & PostgreSQL Conflict Protection

| Task | Description | Duration |
|---|---|---:|
| 9.1 | Implement V014 appointments migration | 0.5 d |
| 9.2 | Implement staff technician eligibility | 0.25 d |
| 9.3 | Appointment aggregate/application service | 0.5 d |
| 9.4 | PostgreSQL GiST exclusion constraint + translation to 409 | 0.75 d |
| 9.5 | Availability query | 0.5 d |
| 9.6 | Appointment CRUD/status API | 0.75 d |
| 9.7 | Appointment form UI | 0.75 d |
| 9.8 | Concurrency tests with simultaneous overlap | 1.0 d |

### Required Acceptance

```text
SCH-APT-001..002
SCH-CONFLICT-001..004
SCH-STATE-001..002
```

## Week 10 — Calendar & Rescheduling

| Task | Description | Duration |
|---|---|---:|
| 10.1 | Daily calendar | 1.0 d |
| 10.2 | Weekly calendar | 0.75 d |
| 10.3 | Monthly calendar | 0.5 d |
| 10.4 | Technician filter | 0.5 d |
| 10.5 | Click-to-create | 0.5 d |
| 10.6 | Drag-to-reschedule | 0.75 d |
| 10.7 | Walk-in appointment flow | 0.5 d |
| 10.8 | Calendar loading/empty/error states | 0.25 d |
| 10.9 | Reschedule/conflict regression tests | 0.75 d |

### Required Acceptance

```text
SCH-RESCH-001
UI-SCH-001
UI-SCH-002
SC04-002
```

## Week 11 — Job Cards, Estimates, Parts & Service Invoice

| Task | Description | Duration |
|---|---|---:|
| 11.1 | Implement V015 job migrations | 0.5 d |
| 11.2 | JobCard/JobService/JobEstimate domain | 0.75 d |
| 11.3 | Appointment-to-job conversion | 0.5 d |
| 11.4 | Job state-machine APIs | 0.75 d |
| 11.5 | Estimate create/version/accept/decline | 0.5 d |
| 11.6 | Job-part stock-posting integration | 0.75 d |
| 11.7 | Job card JavaFX workflow | 1.0 d |
| 11.8 | Service invoice-from-job draft generation | 0.5 d |
| 11.9 | Pickup/warranty completion | 0.5 d |
| 11.10 | Full repair scenario tests | 1.0 d |

### Required Acceptance

```text
JOB-CONV-001..003
JOB-EST-001..004
JOB-STATE-001..005
JOB-CANCEL-001
JOB-PART-001..004
SC03-001
```

**Milestone 4:** Appointment and repair/service workflows operate through invoice and pickup without stock double deduction.

---

# Phase 5 — Inventory & Purchasing (Weeks 12–14)

## Week 12 — Stock Ledger & Adjustments

| Task | Description | Duration |
|---|---|---:|
| 12.1 | Implement V011 stock ledger/adjustment migration | 0.5 d |
| 12.2 | Stock posting service and source uniqueness | 0.75 d |
| 12.3 | Stock-on-hand/reserved/available queries | 0.5 d |
| 12.4 | Product-lock concurrency strategy | 0.5 d |
| 12.5 | Stock movement history UI | 0.5 d |
| 12.6 | Stock adjustment request UI/API | 0.5 d |
| 12.7 | Approval/rejection/reversal | 0.75 d |
| 12.8 | Low-stock calculation | 0.25 d |
| 12.9 | Stock reconciliation/concurrency tests | 1.25 d |

### Required Acceptance

```text
STK-LEDGER-001..007
STK-SOURCE-001
STK-CON-001..003
STK-ADJ-001..005
REC-STK-001..003
```

## Week 13 — GRN & Cost History

| Task | Description | Duration |
|---|---|---:|
| 13.1 | Implement V012 supplier/GRN/cost-history migration | 0.5 d |
| 13.2 | Draft GRN service/UI | 0.75 d |
| 13.3 | Transactional GRN posting | 1.0 d |
| 13.4 | Stock + cost history + payable integration | 0.75 d |
| 13.5 | GRN list/detail/print/export | 0.75 d |
| 13.6 | Product cost history view | 0.5 d |
| 13.7 | Duplicate supplier reference handling | 0.25 d |
| 13.8 | Rollback/idempotency tests | 1.0 d |

### Required Acceptance

```text
PUR-GRN-001..005
SYS-IDEM-004
TX-GRN-001
```

## Week 14 — Supplier Returns, Payments & Allocations

| Task | Description | Duration |
|---|---|---:|
| 14.1 | Implement V013 migrations | 0.5 d |
| 14.2 | Supplier return eligibility + posting | 0.75 d |
| 14.3 | Supplier payment + multi-GRN allocation | 1.0 d |
| 14.4 | GRN/payable locking | 0.5 d |
| 14.5 | Supplier statement/outstanding GRNs | 0.75 d |
| 14.6 | Payment/return JavaFX screens | 1.0 d |
| 14.7 | Concurrency/reconciliation/idempotency tests | 1.25 d |

### Required Acceptance

```text
PUR-RET-001..003
PUR-RET-CON-001
PUR-PAY-001..004
PUR-PAY-CON-001
SYS-IDEM-005
FIN-AP-001..003
```

**Milestone 5:** Physical stock, GRNs, costs, returns, supplier payments and outstanding balances reconcile.

---

# Phase 6 — Finance & Recovery (Weeks 15–16)

## Week 15 — Cashbook, Receivables, Payables & Daily Closing

| Task | Description | Duration |
|---|---|---:|
| 15.1 | Implement V016 cashbook/cash-closing migration | 0.5 d |
| 15.2 | Customer payment/refund cashbook integration | 0.5 d |
| 15.3 | Supplier payment cashbook integration | 0.25 d |
| 15.4 | Manual cash receipt/expense + reversal | 0.5 d |
| 15.5 | Receivable views/aging/drilldown | 0.75 d |
| 15.6 | Payable views/drilldown | 0.5 d |
| 15.7 | Cash-closing preview/create/approval | 0.75 d |
| 15.8 | Finance JavaFX screens | 1.0 d |
| 15.9 | Finance reconciliation tests | 1.0 d |

### Required Acceptance

```text
FIN-CASH-001..006
FIN-AR-001..004
FIN-AP-001..003
FIN-CLOSE-001..007
REC-FIN-001..004
TX-CLOSE-001
```

## Week 16 — Backup, Restore & Operational Safety

| Task | Description | Duration |
|---|---|---:|
| 16.1 | Implement V018 backup/restore history | 0.25 d |
| 16.2 | Production backup adapter using validated Week 1 spike | 0.75 d |
| 16.3 | Checksum/size/status/user/audit | 0.5 d |
| 16.4 | Backup history and retention UI | 0.5 d |
| 16.5 | Restore preflight | 0.5 d |
| 16.6 | Maintenance mode and active-session guard | 0.75 d |
| 16.7 | Restore execution + schema/readability verification | 1.0 d |
| 16.8 | Failure tests: storage/tooling/invalid backup | 0.75 d |
| 16.9 | Clean recovery rehearsal | 1.0 d |

### Required Acceptance

```text
SYS-BACKUP-001..004
SYS-RESTORE-001..005
SYS-IDEM backup/restore
SYS-INSTALL-001
```

**Milestone 6:** Finance reconciles, and a verified backup can restore Bizco into an operable clean environment.

---

# Phase 7 — Reporting, Dashboard & Audit (Weeks 17–18)

## Week 17 — Audit, Read Views, Dashboard & Sales/Stock Reports

| Task | Description | Duration |
|---|---|---:|
| 17.1 | Implement V017 audit log migration if not already active from earlier incremental work | 0.25 d |
| 17.2 | Implement V019 read views | 0.5 d |
| 17.3 | Implement V020 indexes/constraints and query review | 0.5 d |
| 17.4 | Dashboard KPI query service | 0.75 d |
| 17.5 | Daily sales report | 0.5 d |
| 17.6 | Sales by product | 0.5 d |
| 17.7 | Sales by payment method | 0.5 d |
| 17.8 | Stock-on-hand / low-stock reports | 0.5 d |
| 17.9 | JSON/PDF/CSV shared query pipeline | 0.75 d |
| 17.10 | Audit viewer/search UI | 0.5 d |
| 17.11 | Query-plan/performance checks | 0.75 d |

### Required Acceptance

```text
AUD-001..006
DASH-001..007
RPT-SALE-001
RPT-PAY-001
RPT-STK-001..002
RPT-EXPORT-001..002
```

## Week 18 — Finance, Scheduling & VAT Reports

| Task | Description | Duration |
|---|---|---:|
| 18.1 | Customer balances report | 0.5 d |
| 18.2 | Supplier balances report | 0.5 d |
| 18.3 | Cashbook report | 0.5 d |
| 18.4 | Daily cash-closing report | 0.5 d |
| 18.5 | Appointment summary | 0.5 d |
| 18.6 | Job-card status report | 0.5 d |
| 18.7 | VAT report from posted tax snapshots | 0.75 d |
| 18.8 | Report filters/date boundaries/permissions | 0.5 d |
| 18.9 | Seeded reconciliation pack | 1.0 d |
| 18.10 | Empty/high-volume/denied-access tests | 0.75 d |

### Required Acceptance

```text
RPT-AR-001
RPT-AP-001
RPT-CASH-001
RPT-CLOSE-001
RPT-SCH-001
RPT-JOB-001
RPT-TAX-001
REC-TAX-001..002
```

**Milestone 7:** Dashboard and every MVP report reconcile to the same source documents and ledgers.

---

# Phase 8 — Stabilization & Release (Weeks 19–20)

## Week 19 — System Verification, Concurrency & Packaging

| Task | Description | Duration |
|---|---|---:|
| 19.1 | Run complete automated P0/P1 acceptance suite | 1.0 d |
| 19.2 | Full permission-negative test matrix | 0.5 d |
| 19.3 | Transaction fault-injection regression | 0.5 d |
| 19.4 | Five-client LAN workflow test | 0.5 d |
| 19.5 | Ten-user mixed concurrency engineering test | 0.5 d |
| 19.6 | Performance/query-plan review | 0.5 d |
| 19.7 | Clean Flyway install rehearsal | 0.5 d |
| 19.8 | Full backup/restore release rehearsal | 0.75 d |
| 19.9 | Server/client packaging | 0.5 d |
| 19.10 | Security/secret/configuration review | 0.5 d |
| 19.11 | Regression defect fixes | 1.0 d |

### Required Acceptance

```text
PERF-001..005
TX-SALE-001
TX-GRN-001
TX-SUPPAY-001
TX-CN-001
TX-ADJ-001
TX-CLOSE-001
DB-001..012
HIST-001..007
LAN-001..004
SPC-001..003
```

## Week 20 — UAT, Scenario Pack & Release

| Task | Description | Duration |
|---|---|---:|
| 20.1 | SC-01 Retail/Trading UAT | 0.5 d |
| 20.2 | SC-02 Wholesale/Credit UAT | 0.5 d |
| 20.3 | SC-03 Repair Centre UAT | 0.75 d |
| 20.4 | SC-04 Appointment Service UAT | 0.5 d |
| 20.5 | SC-05 Hybrid Product + Service UAT | 0.5 d |
| 20.6 | User/admin/install/backup documentation | 0.75 d |
| 20.7 | Final P0/P1 test run | 0.5 d |
| 20.8 | Stakeholder acceptance/sign-off | 0.5 d |
| 20.9 | Release notes / known limitations | 0.25 d |
| 20.10 | Tag/build final release | 0.25 d |

### Required Acceptance

```text
SC01-001..003
SC02-001..003
SC03-001
SC04-001..002
SC05-001

all P0 PASS
all P1 PASS
reconciliation gates PASS
recovery gate PASS
```

**Milestone 8:** Bizco MVP released with full accepted scope.

---

# 9. Milestone Summary

| Milestone | Week | Exit Condition |
|---|---:|---|
| M0 Design Gate | Pre-development | All design docs approved |
| M1 Foundation & Identity | 3 | secure login/RBAC/session JavaFX |
| M2 Master Data | 5 | customer/catalog/supplier/service stable |
| M3 Sales/POS | 8 | full POS, credit, payment, return, output |
| M4 Scheduling/Jobs | 11 | appointment + repair/service flow |
| M5 Inventory/Purchasing | 14 | stock/GRN/supplier flows reconcile |
| M6 Finance/Recovery | 16 | AR/AP/cash/recovery verified |
| M7 Reporting | 18 | dashboard/reports reconcile |
| M8 Release | 20 | P0/P1 + UAT + recovery gates pass |

---

# 10. Critical Transaction Boundaries

These services must run inside one PostgreSQL transaction.

## Sale Posting

```text
validate
→ number
→ invoice snapshots
→ SALE stock
→ payment/receivable
→ cashbook
→ held reservation release
→ audit
→ commit
```

## Credit Note / Return

```text
credit note
→ tax reversal
→ restock where valid
→ receivable/refund
→ cashbook where refund
→ audit
→ commit
```

## Job Part

```text
JobPart
→ JOB_PART stock movement
→ audit
→ commit
```

## GRN

```text
GRN
→ stock
→ cost history
→ payable
→ audit
→ commit
```

## Supplier Return

```text
return
→ stock reduction
→ payable reduction
→ audit
```

## Supplier Payment

```text
payment
→ allocations
→ payable effect
→ cashbook OUT
→ audit
```

## Stock Adjustment Approval

```text
approval
→ ADJUSTMENT movement
→ audit
```

## Cash Closing

```text
expected cash
→ counted/variance
→ closing/approval state
→ audit
```

---

# 11. Dependencies & Critical Path

## 11.1 Dependency Map

```text
Design Gate
    ↓
Foundation / Identity
    ↓
Customer + Catalog
    ↓
Sales --------------------------┐
    ↓                           │
Stock/Purchasing                │
    ↓                           │
Finance                         │
    ↓                           │
Reports                         │
                                │
Customer + Service + Identity   │
    ↓                           │
Scheduling                      │
    ↓                           │
Job Cards ----------------------┘
    ↓
Service Invoice integration

Foundation
    ↓
Backup technical spike
    ↓
Final backup/restore implementation
    ↓
Recovery release gate
```

## 11.2 Key Dependencies

| Dependency | Why |
|---|---|
| Identity before modules | every protected API depends on security |
| Customer/catalog before sales | invoice lines/customer rules |
| Product locking before production sales | prevents overselling |
| Sales before receivables | invoice source data |
| GRN before supplier payable | payable source data |
| Stock before job parts | parts deduction |
| Services before appointments/jobs | service definitions |
| Sales + jobs before service invoice | combined billing |
| Finance before final dashboard | KPI source |
| Source models before reports | report reconciliation |
| Backup spike before Week 16 | reduce platform/recovery risk |

---

# 12. Risk Register

| Risk | Impact | Mitigation |
|---|---|---|
| MVP is large for one developer | High | 26–30 week solo forecast; strict vertical slices |
| Financial/stock transaction bugs | Critical | transaction services + P0 rollback tests |
| Duplicate retries on LAN | Critical | idempotency keys + source uniqueness |
| Concurrent overselling | Critical | product locks + DB transactions |
| Double appointment booking | High | PostgreSQL GiST exclusion |
| Credit-limit race | High | lock customer during credit posting |
| Balance drift | Critical | derive/reconcile from source records |
| Historical tax change corruption | Critical | transaction snapshots |
| Role expiry stale session | High | evaluate current permissions each request |
| Backup tooling mismatch | High | Week 1 technical spike |
| Restore only tested late | Critical | early spike + Week 16 + Week 19 rehearsal |
| Report mismatch | High | canonical queries + seeded reconciliation |
| JavaFX duplicates business logic | High | server authoritative; UI preview only |
| Flyway drift | High | clean migration CI test |
| PII/security leakage | Critical | encryption, masking, log redaction, tests |
| DevelopmentPlan scope pressure | High | scope protection rule; adjust forecast |

---

# 13. Quality Assurance

## 13.1 Definition of Done

A work item is done only when:

```text
business requirement implemented
+ permission enforced server-side
+ state transition enforced
+ DB migration/constraint included
+ API contract satisfied
+ unit tests pass
+ PostgreSQL integration tests pass
+ negative tests pass
+ idempotency/concurrency test if applicable
+ audit behavior tested
+ reconciliation tested if financial/stock
+ JavaFX loading/error/empty states work
+ documentation updated
```

## 13.2 Required Continuous CI Checks

Every merge to main should run:

```text
mvn compile
unit tests
PostgreSQL Testcontainers integration tests
Flyway clean-install validation
API contract tests
static checks
```

Selected UI tests may run separately if environment requirements make them slower.

## 13.3 No H2 Substitution

PostgreSQL-specific acceptance tests must run against PostgreSQL.

This includes:

- GiST exclusion;
- row locks;
- transaction behavior;
- partial indexes;
- `TIMESTAMPTZ`;
- JSONB;
- unique/partial constraints.

---

# 14. Acceptance Gates

## 14.1 Security Gate

Must pass:

```text
SEC-AUTH
SEC-SESSION
SEC-RBAC
permission-negative matrix
```

## 14.2 Sales Gate

Must pass:

```text
invoice lifecycle
numbering
VAT
discount approval
credit sale
split payment
hold/resume
return/refund
void
idempotency
```

## 14.3 Stock Gate

Must prove:

```text
physical = SUM(stock movements)
available = physical - reservations
no double stock posting
no concurrent oversell
```

## 14.4 Finance Gate

Must prove:

```text
receivables reconcile
payables reconcile
cashbook reconciles
cash closing reconciles
```

## 14.5 Recovery Gate

Must prove:

```text
backup VERIFIED
clean restore succeeds
application logs in
schema/Flyway version readable
stock/finance reconciliation passes after restore
```

## 14.6 Release Gate

```text
all P0 PASS
all P1 PASS
0 critical/high unresolved data-integrity/security defects
five-client UAT PASS
10-user engineering concurrency PASS
SC01–SC05 PASS
recovery PASS
```

---

# 15. Testing Deliverables

Suggested JUnit integration suites:

```text
AuthenticationIT
SessionIT
RolePermissionIT
SecondaryRoleExpiryIT

CustomerApiIT
CustomerPiiIT

ProductApiIT
ProductUniquenessIT
ServiceApiIT

InvoiceDraftIT
InvoicePostingIT
InvoicePricingTest
InvoiceVatTest
CreditSaleIT
HeldSaleIT
CustomerPaymentIT
CreditNoteIT
InvoiceVoidIT
SalesIdempotencyIT

StockLedgerIT
StockConcurrencyIT
StockAdjustmentIT

GrnPostingIT
SupplierReturnIT
SupplierPaymentAllocationIT

AppointmentIT
AppointmentConcurrencyIT
JobCardIT
JobPartIT

ReceivableReconciliationIT
PayableReconciliationIT
CashbookIT
CashClosingIT

DashboardIT
SalesReportIT
StockReportIT
TaxReportIT

AuditIT
BackupIT
RestoreIT
FlywayCleanInstallIT
```

---

# 16. Deliverables

## 16.1 Software

| Deliverable | Format |
|---|---|
| Spring Boot Server | executable JAR |
| JavaFX Client | packaged JAR/runtime distribution |
| PostgreSQL Schema | Flyway migrations |
| Automated Tests | source + test resources |
| Configuration Templates | properties/environment examples |
| PDF/CSV Templates | report/receipt templates |
| Release Package | server/client/config/scripts |
| Release Evidence | acceptance, recovery, checksums |

## 16.2 Documentation

| Document | Status/Timing |
|---|---|
| SRS.md | source |
| MVP.md v1.3 | completed design baseline |
| DomainModel.md | completed |
| StateMachines.md | completed |
| DatabaseDesign.md | completed |
| ApiContracts.md | completed |
| AcceptanceTests.md | completed |
| DevelopmentPlan.md v2.1 | this document |
| OpenAPI API documentation | generated during implementation |
| Installation Guide | Week 20 |
| User Guide | Week 20 |
| Admin Guide | Week 20 |
| Backup/Restore Guide | Week 16–20 |
| UAT Pack | Week 20 |

---

# 17. Success Metrics

| Metric | Target |
|---|---|
| Critical P0 acceptance | 100% pass |
| P1 acceptance | 100% pass |
| Invoice/VAT accuracy | 100% acceptance dataset |
| Stock ledger reconciliation | 100% |
| Receivable reconciliation | 100% |
| Payable reconciliation | 100% |
| Cashbook/closing reconciliation | 100% |
| Duplicate posting under retry | 0 |
| Appointment double booking under concurrency | 0 |
| Open critical/high release defects | 0 |
| POS scan-to-receipt usability target | < 2 minutes in UAT |
| Five simultaneous clients | successful critical workflows |
| Engineering concurrency | 10 users without integrity failure |
| Backup/restore | clean verified restore succeeds |
| User acceptance | >80% positive UAT feedback target |

System uptime targets are operational deployment goals, not substitutes for integrity acceptance.

---

# 18. Weekly Delivery Method

Each week should end with:

```text
1. packaged runnable build
2. Flyway migrations for that slice
3. automated tests
4. acceptance IDs executed
5. short demo
6. known issues
7. updated risk/decision log
```

Do not leave testing until Week 19.

---

# 19. Git Strategy

Recommended simple strategy:

```text
main
├── feature/identity-session
├── feature/invoice-posting
├── feature/appointment-calendar
├── feature/grn-posting
├── feature/cash-closing
├── release/v1.0.0
└── hotfix/...
```

## Rules

- `main` stays deployable.
- Feature branches are short-lived.
- Merge only after CI passes.
- Database migrations are reviewed carefully.
- No rewritten historical migration after shared use.
- Tag releases.

## Commit Format

```text
<type>(<scope>): <description>

feat(sales): add idempotent invoice posting
fix(inventory): prevent duplicate source movement
test(scheduling): add concurrent appointment conflict test
docs(api): update payment allocation contract
```

---

# 20. Post-MVP Roadmap

These remain outside MVP and are not introduced during schedule pressure.

## Phase 2

- offline resilience/sync;
- multi-branch;
- purchase orders/full supplier invoices;
- product variants;
- batch/serial tracking;
- full GL;
- SSCL/WHT;
- statutory e-Invoicing integration;
- loyalty;
- notifications;
- advanced analytics.

## Phase 3

- assets;
- payroll;
- document management;
- specialized industry modules;
- mobile companion.

## Phase 4

- AI forecasting;
- portals;
- e-commerce integration;
- broader BI.

---

# 21. Resource Planning

Current prices/costs are intentionally not hardcoded.

## Solo

```text
26–30 weeks
```

Items requiring quotation at project start:

- receipt printer;
- barcode scanner;
- representative client PCs;
- server hardware if separate;
- backup storage;
- certificate/deployment infrastructure where required.

## Small Team

```text
20-week baseline
```

Cost should be based on current developer/QA/infrastructure quotations at kickoff.

---

# 22. Communication Plan

| Activity | Cadence | Purpose |
|---|---|---|
| Developer planning | weekly | next vertical slice |
| Progress review | weekly | demo accepted behavior |
| Risk/design review | weekly/as needed | resolve design defects |
| Phase gate | end of phase | milestone approval |
| UAT | Week 20 | business acceptance |
| Recovery rehearsal | Weeks 16 & 19 | prove recoverability |

For a solo project, this can be a structured self-review plus stakeholder demo.

---

# Appendix A — Acceptance ID by Week

| Week | Main Acceptance Groups |
|---:|---|
| 1 | SYS-INSTALL |
| 2 | SEC-AUTH, SEC-SESSION, SEC-RBAC |
| 3 | UI-AUTH, UI-RBAC |
| 4 | CUS, CRD policy |
| 5 | CAT |
| 6 | SALE-DRAFT, SALE-PRICE, SALE-DISC, TAX |
| 7 | DOC-NUM, SALE-POST, CRD, SYS-IDEM, TX-SALE |
| 8 | SALE-HOLD, FIN-AR-PAY, SALE-CN, SALE-VOID |
| 9 | SCH-APT, SCH-CONFLICT |
| 10 | SCH-RESCH, UI-SCH |
| 11 | JOB-CONV, JOB-EST, JOB-STATE, JOB-PART |
| 12 | STK-LEDGER, STK-CON, STK-ADJ, REC-STK |
| 13 | PUR-GRN, TX-GRN |
| 14 | PUR-RET, PUR-PAY, FIN-AP |
| 15 | FIN-CASH, FIN-AR, FIN-AP, FIN-CLOSE, REC-FIN |
| 16 | SYS-BACKUP, SYS-RESTORE |
| 17 | AUD, DASH, sales/stock RPT |
| 18 | finance/scheduling/VAT RPT, REC-TAX |
| 19 | PERF, TX, DB, HIST, LAN, SPC |
| 20 | SC01–SC05 + final release gate |

---

# Appendix B — Final Flyway Sequence

Superseded by the as-built baseline and renumbering record in Section 7 / 7.2 — see there for the
current `V001`–`V016` sequence and the still-unbuilt items awaiting `V017`+.

---

# Appendix C — Implementation Start Checklist

Before writing Week 1 production code:

```text
[ ] Repository structure agreed
[ ] Java 21 installed
[ ] Maven installed
[ ] Docker/Testcontainers available
[ ] PostgreSQL supported version available
[ ] docs/ contains current seven design/acceptance files
[ ] Git repository initialized
[ ] branch/CI policy agreed
[ ] development secrets excluded from Git
[ ] supported OS/deployment targets identified
```

After Week 1:

```text
[ ] clean Flyway bootstrap passes
[ ] server connects to PostgreSQL
[ ] Testcontainers suite passes
[ ] logging/correlation works
[ ] backup/restore spike proves feasibility
```

---

# Appendix D — MVP Scenario Release Pack

## SC-01 Retail / Trading

```text
GRN
→ stock
→ POS
→ split/cash payment
→ receipt
→ cashbook
→ closing
→ report
→ return
```

## SC-02 Wholesale / Credit

```text
customer credit
→ wholesale price
→ credit invoice
→ receivable/aging
→ later payment
```

## SC-03 Repair Centre

```text
appointment
→ job
→ estimate
→ part
→ service
→ service invoice
→ payment
→ pickup/warranty
```

## SC-04 Appointment Service

```text
booking
→ technician conflict enforcement
→ reschedule
→ service/job
→ invoice/payment
```

## SC-05 Hybrid Product + Service

```text
PRODUCT
+ SERVICE
+ CUSTOM invoice lines
→ correct stock/tax/finance effects
```

---

*(End of Bizco MVP Development Plan v2.1)*
