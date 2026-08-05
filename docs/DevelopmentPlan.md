# Bizco MVP Development Plan

**Project:** SME Business Management System (Bizco)
**Version:** 2.0
**Duration:** 20 weeks
**Start Date:** TBD
**Based On:** SRS v2.1, MVP v1.2

---

# 1. Project Overview

## 1.1 Objective

Deliver a Minimum Viable Product (MVP) of Bizco that enables Sri Lankan SMEs to:
- Manage customers and products
- Process sales via POS with barcode scanning
- Generate invoices with VAT compliance
- Schedule service appointments and manage job cards
- Track inventory with basic stock management
- Manage supplier receipts, returns, payments, and outstanding balances
- Reconcile cashbook, receivables, payables, and daily cash closing
- Create and verify backups and restore the system safely
- View business KPIs and basic reports

## 1.2 Scope

| In Scope | Out of Scope |
|---|---|
| Authentication, action-based RBAC, audit | Offline resilience (SQLite cache) |
| Customer CRUD with credit limits | Multi-branch support |
| Product CRUD with categories | Product variants, batch/serial tracking |
| POS with barcode scanning | Consignment management |
| Invoicing (sales, service, tax) | Full double-entry accounting/GL |
| Credit notes & returns | SSCL, WHT, e-Invoicing |
| Appointment scheduling & calendar | Loyalty points |
| Job card management | Payroll |
| Basic inventory (GRN, adjustments) | Asset management |
| Supplier CRUD, returns, payments, cost history | Purchase orders and full purchase invoices |
| Cashbook, receivables, payables, daily closing | Full chart of accounts and financial statements |
| VAT calculation & tax invoice | Advanced analytics |
| Dashboard & basic reports | Notification center (email/SMS) |
| Audit logging | Document management |
| Backup, verification & restore | Mobile/customer portal |

---

# 2. Team Structure

## 2.1 Recommended Team

| Role | Count | Responsibilities |
|---|---|---|
| Full-Stack Developer | 1-2 | Backend API, frontend UI, database |
| UI/UX Designer | 0.5 | Wireframes, design system, usability |
| QA Tester | 0.5 | Test cases, bug tracking, UAT support |
| Project Manager | 0.5 | Planning, coordination, stakeholder communication |

## 2.2 Solo Developer Option

The 20-week baseline assumes approximately **1.5 developer FTE** (one lead developer plus consistent implementation support), part-time QA/business-analysis support, and scheduled UI and accounting reviews. A solo developer performing all roles should plan for **26-30 weeks**. The approved scope, including full Event Scheduling, remains unchanged; only the forecast changes.

---

# 3. Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| Frontend | JavaFX on the project-approved LTS JDK | Desktop UI framework |
| UI Components | ControlsFX | Enhanced JavaFX controls |
| Backend | Spring Boot 3.x | REST API server |
| ORM | Spring Data JPA / Hibernate | Database access |
| Database | PostgreSQL 18.x recommended / PostgreSQL 16+ supported | Production database |
| Integration DB | PostgreSQL via Testcontainers | Database and API integration testing |
| Migration | Flyway | Schema versioning |
| Auth | Spring Security + BCrypt | Authentication & RBAC |
| PDF | JasperReports / PDFBox | Invoice & receipt generation |
| Barcode | ZXing | Barcode scanning & generation |
| Build | Maven | Build automation |
| VCS | Git | Version control |
| Testing | JUnit 5, AssertJ, Mockito, TestFX | Unit, integration, and selected UI tests |

Architecture rules:

- JavaFX clients communicate with the Spring Boot API only; PostgreSQL is never exposed to client machines.
- Organize server and client code by business capability: identity, customer, catalog, sales, scheduling, inventory, purchasing, finance, reporting, and system.
- Shared code contains DTOs, errors, validation contracts, and stable enums, not JPA entities or repositories.
- `stock_movements` is the authoritative stock ledger; receivables, payables, and cashbook balances reconcile from immutable posted records.
- Sale, credit note, job-part usage, GRN, supplier return, supplier payment, and daily closing posting each run as one PostgreSQL transaction.

---

# 4. Development Phases

## Phase 1: Foundation (Weeks 1-3)

### Week 1: Project Setup

| Task | Description | Duration |
|---|---|---|
| 1.1 | Initialize Maven project with Spring Boot starter | 0.5 day |
| 1.2 | Configure project structure (multi-module: server, client, common) | 0.5 day |
| 1.3 | Set up PostgreSQL database and Flyway migrations | 0.5 day |
| 1.4 | Create initial database schema (users, roles, business_profile) | 1 day |
| 1.5 | Configure Spring Security with BCrypt | 1 day |
| 1.6 | Set up Git repository with branching strategy | 0.5 day |
| 1.7 | Configure CI/CD pipeline (basic) | 0.5 day |
| 1.8 | Set up logging (Logback) | 0.5 day |

### Week 2: Authentication & User Management

| Task | Description | Duration |
|---|---|---|
| 2.1 | Implement User entity and repository | 0.5 day |
| 2.2 | Implement Role entity with permissions JSON | 0.5 day |
| 2.3 | Implement UserRole entity (secondary roles) | 0.5 day |
| 2.4 | Create Auth API (login, logout, change password) | 1 day |
| 2.5 | Create User CRUD API with permission checks | 1 day |
| 2.6 | Create Role Management API (CRUD + permissions) | 1 day |
| 2.7 | Create Secondary Role API (grant, revoke, list) | 0.5 day |
| 2.8 | Implement effective permissions calculation (primary + secondary) | 0.5 day |
| 2.9 | Implement session management (timeout, concurrent limits) | 0.5 day |
| 2.10 | Implement login history tracking | 0.5 day |
| 2.11 | Implement account locking (5 failed attempts) | 0.5 day |
| 2.12 | Implement background job for auto-revoking expired secondary roles | 0.5 day |

### Week 3: JavaFX Client Foundation

| Task | Description | Duration |
|---|---|---|
| 3.1 | Initialize JavaFX project with Maven | 0.5 day |
| 3.2 | Create main application shell (sidebar, topbar, content area) | 1 day |
| 3.3 | Implement login screen | 0.5 day |
| 3.4 | Create API client service (HTTP calls to Spring Boot) | 1 day |
| 3.5 | Implement token-based authentication in client | 0.5 day |
| 3.6 | Create navigation framework (module switching) | 0.5 day |
| 3.7 | Create User Management screen (list, form) | 1 day |
| 3.8 | Create Role Management screen (permissions editor) | 1 day |
| 3.9 | Create Secondary Role grant/revoke UI | 0.5 day |
| 3.10 | Implement permission-based UI visibility (hide unauthorized elements) | 0.5 day |
| 3.11 | First launch wizard (business profile setup) | 0.5 day |
| 3.12 | Seed data script (default users, roles with permissions, UOM, tax config) | 0.5 day |

**Milestone 1:** System boots, admin can login, user/role management with multi-role and action-based permissions works.

---

## Phase 2: Core Master Data (Weeks 4-5)

### Week 4: Customer Management

| Task | Description | Duration |
|---|---|---|
| 4.1 | Create Customer entity and repository | 0.5 day |
| 4.2 | Create Customer CRUD API | 1 day |
| 4.3 | Create Customer list screen (JavaFX) | 0.5 day |
| 4.4 | Create Customer form screen (create/edit) | 1 day |
| 4.5 | Implement customer search (code, name, phone) | 0.5 day |
| 4.6 | Implement credit limit enforcement logic | 0.5 day |

### Week 5: Product Management

| Task | Description | Duration |
|---|---|---|
| 5.1 | Create Product entity and repository | 0.5 day |
| 5.2 | Create Category entity (hierarchical) | 0.5 day |
| 5.3 | Create Product CRUD API | 1 day |
| 5.4 | Create Category management screen | 0.5 day |
| 5.5 | Create Product list screen | 0.5 day |
| 5.6 | Create Product form screen (create/edit) | 1 day |
| 5.7 | Create basic Supplier entity and CRUD | 0.5 day |
| 5.8 | Create UOM reference data | 0.5 day |

**Milestone 2:** Customers, suppliers, products, categories, UOMs, and services are manageable.

---

## Phase 3: Invoicing Facility (Weeks 6-8)

### Week 6: POS Screen

| Task | Description | Duration |
|---|---|---|
| 6.1 | Design POS screen layout (product grid, cart, totals) | 0.5 day |
| 6.2 | Implement product grid with search | 1 day |
| 6.3 | Implement barcode scanning integration | 0.5 day |
| 6.4 | Implement cart operations (add, remove, qty change) | 1 day |
| 6.5 | Implement line item discount | 0.5 day |
| 6.6 | Implement customer selection at POS | 0.5 day |

### Week 7: Invoice & Payment

| Task | Description | Duration |
|---|---|---|
| 7.1 | Create Invoice entity and repository | 0.5 day |
| 7.2 | Create InvoiceLineItem entity | 0.5 day |
| 7.3 | Create Invoice API (CRUD, void) | 1 day |
| 7.4 | Implement VAT calculation logic | 0.5 day |
| 7.5 | Implement invoice-level discount | 0.5 day |
| 7.6 | Create Payment API and source-linked cashbook posting | 0.5 day |
| 7.7 | Create payment screen (cash, card, split) | 1 day |
| 7.8 | Implement stock deduction on sale | 0.5 day |

### Week 8: Receipts, Credit Notes, and Returns

| Task | Description | Duration |
|---|---|---|
| 8.1 | Create receipt template (thermal printer format) | 0.5 day |
| 8.2 | Implement receipt printing (PDF generation) | 1 day |
| 8.3 | Create Credit Note entity and API | 0.5 day |
| 8.4 | Create Credit Note screen (return workflow) | 1 day |
| 8.5 | Implement hold/resume bill functionality | 0.5 day |
| 8.6 | Create invoice history screen with reprint | 0.5 day |
| 8.7 | Implement tax invoice format | 0.5 day |

**Milestone 3:** End-to-end POS sale works, receipts print, VAT correct.

---

## Phase 4: Event Scheduling Facility (Weeks 9-11)

### Week 9: Service Catalog & Appointments

| Task | Description | Duration |
|---|---|---|
| 9.1 | Create Service entity and repository | 0.5 day |
| 9.2 | Create Service CRUD API | 0.5 day |
| 9.3 | Create Service catalog screen | 0.5 day |
| 9.4 | Create Appointment entity and repository | 0.5 day |
| 9.5 | Create Appointment API (CRUD, status changes) | 1 day |
| 9.6 | Create appointment form screen | 1 day |

### Week 10: Calendar View

| Task | Description | Duration |
|---|---|---|
| 10.1 | Design calendar view layout (daily/weekly/monthly) | 0.5 day |
| 10.2 | Implement daily view with time slots | 1 day |
| 10.3 | Implement weekly view | 0.5 day |
| 10.4 | Implement monthly view | 0.5 day |
| 10.5 | Implement technician filter | 0.5 day |
| 10.6 | Implement click-to-create appointment | 0.5 day |
| 10.7 | Implement drag-to-reschedule | 0.5 day |
| 10.8 | Implement double-booking prevention | 0.5 day |

### Week 11: Job Cards

| Task | Description | Duration |
|---|---|---|
| 11.1 | Create JobCard entity and repository | 0.5 day |
| 11.2 | Create JobService, JobPart, JobEstimate entities | 0.5 day |
| 11.3 | Create Job Card API (CRUD, status, parts, estimates) | 1 day |
| 11.4 | Create job card form screen | 1 day |
| 11.5 | Implement appointment-to-job-card conversion | 0.5 day |
| 11.6 | Implement parts usage (inventory deduction) | 0.5 day |
| 11.7 | Implement estimate creation and approval | 0.5 day |
| 11.8 | Implement technician calendar view | 0.5 day |

**Milestone 4:** Appointments scheduled on calendar, job cards tracked through completion.

---

## Phase 5: Inventory & Purchasing (Weeks 12-14)

### Week 12: Stock Operations

| Task | Description | Duration |
|---|---|---|
| 12.1 | Implement signed StockMovement ledger and repository | 1 day |
| 12.2 | Route sale, return, and job-part stock through one service | 1 day |
| 12.3 | Implement stock-on-hand and movement-history queries | 0.5 day |
| 12.4 | Implement stock adjustment request and API | 0.5 day |
| 12.5 | Implement approval, rejection, reversal, and audit | 1 day |
| 12.6 | Add transaction, concurrency, and reconciliation tests | 1 day |

### Week 13: GRN and Product Cost History

| Task | Description | Duration |
|---|---|---|
| 13.1 | Implement immutable GRN header, items, and posting API | 1 day |
| 13.2 | Post stock, cost history, and supplier payable atomically | 1 day |
| 13.3 | Build GRN entry, list, detail, and print/export screens | 1.5 days |
| 13.4 | Build product cost-history and stock-history views | 0.5 day |
| 13.5 | Implement low-stock alerts and dashboard indicator | 0.5 day |
| 13.6 | Add GRN validation, duplicate-reference, and rollback tests | 0.5 day |

### Week 14: Supplier Returns and Payments

| Task | Description | Duration |
|---|---|---|
| 14.1 | Implement supplier payments with full/partial GRN allocation | 1 day |
| 14.2 | Implement supplier returns linked to original GRN items | 1 day |
| 14.3 | Post return stock and payable reductions atomically | 0.5 day |
| 14.4 | Build supplier statement, payment, and return screens | 1.5 days |
| 14.5 | Reconcile opening balance, GRNs, returns, payments, and closing balance | 1 day |

**Milestone 5:** Stock, product costs, supplier documents, and outstanding balances are traceable and reconcile.

---

## Phase 6: Basic Finance & Recovery (Weeks 15-16)

### Week 15: Cashbook, Balances, and Daily Closing

| Task | Description | Duration |
|---|---|---|
| 15.1 | Generate cashbook entries from customer payments and refunds | 0.5 day |
| 15.2 | Generate cashbook entries from supplier payments | 0.5 day |
| 15.3 | Implement authorized manual cash receipts and expenses | 0.5 day |
| 15.4 | Implement receivable and payable views with drill-down | 1 day |
| 15.5 | Implement daily cash closing, variance reason, and approval | 1.5 days |
| 15.6 | Add source-to-balance and cash-closing reconciliation tests | 1 day |

### Week 16: Backup, Restore, and Operational Safety

| Task | Description | Duration |
|---|---|---|
| 16.1 | Implement server-side PostgreSQL backup service | 1 day |
| 16.2 | Record checksum, size, status, user, and audit metadata | 0.5 day |
| 16.3 | Implement backup history, retention, and error UI | 0.5 day |
| 16.4 | Implement maintenance mode and active-session restore guard | 1 day |
| 16.5 | Verify restore into a clean supported PostgreSQL instance | 1 day |
| 16.6 | Test unavailable storage, insufficient space, and invalid backup | 1 day |

**Milestone 6:** Cash activity and balances reconcile, and a verified backup restores a clean environment.

---

## Phase 7: Reporting & Dashboard (Weeks 17-18)

### Week 17: Dashboard, Sales, and Inventory Reports

| Task | Description | Duration |
|---|---|---|
| 17.1 | Implement server-calculated dashboard KPIs | 1 day |
| 17.2 | Implement daily sales and sales-by-product reports | 1 day |
| 17.3 | Implement sales-by-payment-method report | 0.5 day |
| 17.4 | Implement stock-on-hand and low-stock reports | 0.5 day |
| 17.5 | Add filters, pagination, permissions, and PDF/CSV export | 1 day |
| 17.6 | Measure query plans and add justified indexes | 1 day |

### Week 18: Scheduling, Finance, and VAT Reports

| Task | Description | Duration |
|---|---|---|
| 18.1 | Implement appointment and job-card reports | 0.5 day |
| 18.2 | Implement customer and supplier balance reports | 0.5 day |
| 18.3 | Implement cashbook and daily-closing reports | 0.5 day |
| 18.4 | Implement VAT report from document tax snapshots | 0.5 day |
| 18.5 | Reconcile every report against seeded acceptance data | 1.5 days |
| 18.6 | Test empty, high-volume, date-boundary, and denied-access cases | 1.5 days |

**Milestone 7:** All MVP reports agree with source documents and ledgers.

## Phase 8: Stabilization & Release (Weeks 19-20)

### Week 19: System Verification

| Task | Description | Duration |
|---|---|---|
| 19.1 | Run unit, PostgreSQL integration, API security, and TestFX suites | 1 day |
| 19.2 | Test full POS and Event Scheduling workflows | 1 day |
| 19.3 | Test inventory, purchasing, finance, and recovery workflows | 1 day |
| 19.4 | Test five concurrent LAN clients and performance targets | 0.5 day |
| 19.5 | Test supported printers, scanners, firewall, and restart behavior | 0.5 day |
| 19.6 | Resolve all critical/high defects and triage remaining issues | 1 day |

### Week 20: UAT and Release

| Task | Description | Duration |
|---|---|---|
| 20.1 | Run realistic Sri Lankan SME UAT | 1 day |
| 20.2 | Obtain qualified VAT and finance-output review | 0.5 day |
| 20.3 | Complete user, admin, installation, and recovery guides | 1 day |
| 20.4 | Build versioned artifacts, release notes, and checksums | 0.5 day |
| 20.5 | Rehearse clean install, migration, smoke test, backup, and restore | 1 day |
| 20.6 | Record known limitations, support process, and sign-off | 1 day |

**Milestone 8:** Production-ready MVP accepted and repeatably deployable.

---

# 5. Milestones Summary

| Milestone | Week | Description | Verification |
|---|---|---|---|
| M1 | 3 | System boots, admin can login | Login works, user CRUD works |
| M2 | 5 | Master data manageable | Customers, suppliers, products, categories, UOMs, services |
| M3 | 8 | Sales operational | Sale → Payment → Receipt/Tax Invoice → Stock/Cashbook |
| M4 | 11 | Full Event Scheduling operational | Appointment → Job Card → Estimate → Parts → Invoice → Pickup |
| M5 | 14 | Inventory and purchasing controlled | Stock, GRN, cost, supplier return/payment reconciliation |
| M6 | 16 | Finance and recovery controlled | Cashbook, balances, closing, verified backup/restore |
| M7 | 18 | Reports accepted | Every report reconciles to source records |
| M8 | 20 | Production-ready MVP | UAT and clean deployment rehearsal passed |

---

# 6. Dependencies & Risks

## 6.1 Dependencies

| Dependency | Impact | Mitigation |
|---|---|---|
| JavaFX learning curve | Slows UI development | Start with simple screens, iterate |
| Thermal printer integration | Receipt printing may vary | Test with actual printer early |
| PostgreSQL setup on target machine | Blocks testing | Testcontainers for integration; documented production install |
| Barcode scanner compatibility | POS functionality | Test with USB keyboard wedge mode |
| JasperReports learning curve | Report generation | Use PDFBox for simple reports first |
| Qualified VAT/accounting review | Blocks acceptance | Book review checkpoints before Weeks 8, 18, and 20 |
| Target LAN and hardware | Blocks realistic UAT | Confirm server, five clients, scanner, and printer by Week 6 |

## 6.2 Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Scope creep | High | High | Require explicit MVP change approval and re-estimation |
| Event Scheduling complexity | Medium | High | Stabilize state machine in Week 9 and demonstrate weekly |
| JavaFX UI complexity | Medium | Medium | Prototype POS/calendar early with actual users |
| Tax calculation errors | Medium | High | Thorough testing, Sri Lankan accountant review |
| Printer compatibility | Medium | Medium | Support PDF fallback, test early |
| Stock/payable divergence | Medium | High | One posting service, immutable records, reconciliation tests |
| Unrestorable backup | Low | Critical | Checksum and clean restore rehearsal before release |
| Database performance | Low | Medium | Measure query plans before adding indexes |
| Single developer bottleneck | High | High | Use 26-30 week solo forecast and strict WIP limits |

## 6.3 Critical Path

```text
Foundation -> Master Data -> Sales -> Inventory/Purchasing
                                      -> Finance -> Reports -> UAT
                         -> Event Scheduling -^

Foundation -> Backup implementation -> Restore proof -> UAT
```

Event Scheduling starts after customer, service, user, and permission contracts stabilize. Job-part and service-invoice completion depend on stock and sales posting contracts. Reports start after their source transaction models stabilize.

---

# 7. Quality Assurance

## 7.1 Code Quality

- Follow consistent code style (checkstyle)
- Code review for all changes (if team)
- No commented-out code in production
- Meaningful variable and method names
- Maximum method length: 50 lines

## 7.2 Testing Checklist

- [ ] All API endpoints tested
- [ ] POS workflow end-to-end
- [ ] Invoice calculation accuracy (VAT, discounts)
- [ ] Credit limit enforcement
- [ ] Stock deduction atomicity
- [ ] Stock on hand equals the signed stock-movement ledger
- [ ] GRN creates stock, cost history, and supplier payable atomically
- [ ] Supplier returns and payments reconcile to supplier balance
- [ ] Cashbook reconciles to customer and supplier monetary events
- [ ] Daily closing expected cash and variance approval are correct
- [ ] Appointment double-booking prevention
- [ ] Job card status transitions
- [ ] Receipt generation and printing
- [ ] Report accuracy
- [ ] PostgreSQL Flyway migrations pass from a clean database
- [ ] Integration tests run against PostgreSQL Testcontainers
- [ ] Verified backup restores into a clean supported PostgreSQL instance
- [ ] Five concurrent LAN clients complete critical workflows
- [ ] Error handling for all edge cases
- [ ] Session timeout works
- [ ] Account locking works
- [ ] **RBAC: Action-based permission checks work correctly**
- [ ] **RBAC: Primary role permissions enforced**
- [ ] **RBAC: Secondary role granting with expiry works**
- [ ] **RBAC: Secondary role auto-revocation on expiry**
- [ ] **RBAC: Manual secondary role revocation works**
- [ ] **RBAC: Effective permissions calculation (union of roles)**
- [ ] **RBAC: Unauthorized actions blocked with proper message**
- [ ] **RBAC: Role change audit trail recorded**

## 7.3 Acceptance Criteria

| Module | Criteria |
|---|---|
| POS | Scan → Add → Discount → Pay → Print in < 2 minutes |
| Invoicing | VAT calculation matches expected (18% of taxable) |
| Appointments | No double-bookings, calendar reflects real schedule |
| Job Cards | Status transitions enforced, parts deducted from stock |
| Inventory | Stock on hand equals all signed posted stock movements |
| Purchasing | GRNs, returns, payments, costs, and supplier balances reconcile |
| Finance | Receivables, payables, cashbook, and daily closing reconcile |
| Backup/Restore | Verified backup restores a clean operable environment |
| Reports | Numbers match source documents and ledgers |
| **RBAC** | **Action-based permissions enforced, multi-role works, expiry auto-revokes** |

## 7.4 Definition of Done

A work item is done only when its acceptance examples pass, backend authorization and validation are present, Flyway impact is handled, unit and PostgreSQL integration tests pass, JavaFX loading/empty/error states work, audit requirements are covered, documentation is updated, and the result is demonstrated from a packaged build.

---

# 8. Deliverables

## 8.1 Software Deliverables

| Deliverable | Format | Description |
|---|---|---|
| Server Application | JAR file | Spring Boot REST API |
| Client Application | JAR + Lib folder | JavaFX desktop application |
| Database Scripts | SQL files | Flyway migrations |
| Configuration | Properties files | Server and client config |
| Automated Tests | Source + test resources | Unit, PostgreSQL integration, API, UI, reconciliation, recovery |
| Release Evidence | Checksums + results | UAT, restore rehearsal, known limitations, release notes |
| Documentation | PDF/MD | User guide, installation guide |

## 8.2 Documentation Deliverables

| Document | Content |
|---|---|
| Installation Guide | Server setup, client setup, database setup |
| User Guide | Screenshots and instructions for each module |
| API Documentation | Endpoint reference (auto-generated from code) |
| Admin Guide | User management, system configuration |
| Backup & Restore Guide | Retention, verification, recovery, failure handling |
| UAT Pack | Business scenarios, expected results, stakeholder sign-off |

---

# 9. Post-MVP Roadmap

## 9.1 Phase 2 (Months 5-8)

- Offline resilience (SQLite cache, sync)
- Multi-branch support
- Purchase orders and full purchase invoices
- Product variants, batch/serial tracking
- Full GL accounting
- SSCL, WHT, e-Invoicing
- Loyalty points
- Advanced reporting & analytics
- Notification center (email/SMS)

## 9.2 Phase 3 (Months 9-12)

- Asset management
- Payroll
- Document management
- Industry extensions (restaurant, travel, etc.)
- Mobile companion app

## 9.3 Phase 4 (Year 2)

- AI sales forecasting
- Customer/supplier portals
- E-commerce integration
- Business intelligence engine

---

# 10. Resource & Cost Planning

Cost figures must be validated when the start date, staffing model, target hardware, and vendor quotes are known. The schedule baseline is the stable planning input.

## 10.1 Solo Developer

| Item | Cost (LKR) | Notes |
|---|---|---|
| Development (26-30 weeks) | Self-funded | Solo developer performs all project roles |
| TLS certificate | Obtain current quotation | Only if the chosen deployment requires a purchased certificate |
| Testing devices | Obtain current quotation | Receipt printer, barcode scanner, and representative client PC |
| Server and backup storage | Obtain current quotation | Sized after pilot data-volume and retention review |

## 10.2 Small Team (2 developers)

| Item | Cost (LKR) | Notes |
|---|---|---|
| Development (20 weeks) | Obtain current quotations | One full-time developer plus part-time support |
| Infrastructure | Obtain current quotation | Server, clients, network, and backup storage |
| Testing | Obtain current quotation | Devices and any external QA support |
| **Total** | **TBD from current quotations** | Approve before project kickoff |

---

# 11. Success Metrics

| Metric | Target | Measurement |
|---|---|---|
| POS transaction time | < 2 minutes (scan to receipt) | User testing |
| Invoice accuracy | 100% VAT calculation | Automated tests |
| Stock accuracy | 99.9% (system vs physical) | Stock count comparison |
| User satisfaction | > 80% positive feedback | UAT survey |
| Bug count at release | 0 open critical/high bugs | Bug tracking |
| Supplier balance accuracy | 100% for acceptance dataset | Reconciliation tests |
| Cash closing accuracy | 100% for acceptance dataset | Reconciliation tests |
| Recovery | Clean restore succeeds | Release rehearsal |
| System uptime | > 99% during business hours | Monitoring |

---

# 12. Communication Plan

| Meeting | Frequency | Participants | Purpose |
|---|---|---|---|
| Standup | Daily | Developer, PM | Progress, blockers |
| Weekly Review | Weekly | Team + Stakeholder | Packaged demo, decisions, risks |
| Weekly Planning | Weekly | Team | Next outcomes and acceptance tests |
| Phase Gate | End of each phase | Team + Stakeholder | Approve milestone or corrective work |
| UAT Session | Week 20 | Business owner + domain reviewer | Acceptance testing and sign-off |

---

# Appendix A: Proposed File Structure

```text
bizco/
|-- pom.xml
|-- bizco-common/       # API DTOs, validation, errors, stable enums only
|-- bizco-server/
|   |-- src/main/java/com/bizco/
|   |   |-- identity/       |-- customer/     |-- catalog/
|   |   |-- sales/          |-- scheduling/   |-- inventory/
|   |   |-- purchasing/     |-- finance/      |-- reporting/
|   |   `-- system/
|   |-- src/main/resources/db/migration/
|   `-- src/test/
|-- bizco-client/
|   |-- src/main/java/com/bizco/client/
|   |   |-- shell/          |-- identity/     |-- customer/
|   |   |-- catalog/        |-- sales/        |-- scheduling/
|   |   |-- inventory/      |-- purchasing/   |-- finance/
|   |   |-- reporting/      `-- system/
|   |-- src/main/resources/
|   `-- src/test/
|-- docs/
|   |-- SRS.md
|   |-- MVP.md
|   `-- DevelopmentPlan.md
|-- packaging/
`-- scripts/
```

The common module must not contain server persistence entities. Each business module owns its domain, application service, API adapter, persistence adapter, JavaFX views, and tests.

---

# Appendix B: Git Branching Strategy

```text
main (production-ready)
  |-- feature/pos-screen
  |-- feature/appointment-calendar
  |-- feature/inventory-grn
  |-- release/v1.0.0
  `-- hotfix/xxx
```

## Branch Rules

- `main`: Always deployable, tagged releases only
- `feature/*`: Short-lived branch for one accepted outcome; merge to main via review
- `release/*`: Release preparation, bug fixes only
- `hotfix/*`: Emergency production correction; merge to main and tag
- Required checks: compile, unit tests, PostgreSQL integration tests, and Flyway validation

## Commit Message Format

```
<type>(<scope>): <description>

Types: feat, fix, docs, style, refactor, test, chore
Scope: pos, invoice, appointment, inventory, auth, etc.
Example: feat(invoice): add VAT calculation logic
```

---

# Appendix C: Initial Flyway Sequence

```text
V001__extensions_and_reference_types.sql
V002__identity_and_business_profile.sql
V003__customers_suppliers_and_catalog.sql
V004__sales_invoices_payments_and_credit_notes.sql
V005__scheduling_and_job_cards.sql
V006__stock_ledger_and_adjustments.sql
V007__grn_supplier_returns_and_payments.sql
V008__cashbook_and_cash_closings.sql
V009__audit_configuration_and_backup_history.sql
V010__indexes_and_seed_reference_data.sql
```

Never modify a migration after it has run outside a disposable developer database. Add a new migration for every later change.

---

*(End of Development Plan)*
