# Bizco — Development Gap Report

**Date:** 2026-08-23 (originally 2026-08-19; updated for Phase 6 and Phase 7 completion)
**Branch:** `feature/manufacture-bill-of-materials` (last commit inspected: `6590747` — "feat(manufacturing): wire Manufacturing into client navigation")
**Compared against:** `docs/SRS.md` v2.2, `docs/MVP.md` v1.4, `docs/DevelopmentPlan.md` v2.2 (31-week, 11-phase baseline)
**Method:** Static inspection of Flyway migrations, JPA entities, Spring controllers, and JavaFX views. No tests were executed and no runtime behavior was checked — a class or endpoint existing is treated as "built," not as "verified correct."

**Snapshot:** 21 of 31 weeks delivered · Phases 1–7 of 11 complete · Milestone M7 of M11 reached · 29 Flyway migrations shipped (V001–V029).

---

## 1. Done

Fully implemented, migrated, and covered by tests (74 server-side test classes across these modules).

### Phase 1 — Foundation & Identity (weeks 1–3)
- Permission registry, action-based RBAC, primary + expiring secondary roles
- Server-side opaque sessions, lockout/auto-unlock, login history
- JavaFX shell with permission-driven navigation
- `identity/` package, migrations V002–V004, 11 test classes

### Phase 2 — Master Data (weeks 4–5)
- Customer CRUD/search/credit limits, encrypted PII, and an anonymization workflow (`CustomerAnonymizationEligibilityPort`)
- Products, categories, UOM, services, suppliers (flat-product granularity at the time; variants were added later by Phase 6, see above)

### Phase 3 — Sales, POS & Receivables (weeks 6–8)
- Invoice lifecycle: `DRAFT → POSTED → VOIDED`, transactional server-side numbering
- Line/invoice discount approval tiers, split payment, credit sale with credit-limit/aging checks
- Hold/resume with stock reservation
- Credit notes with proportional VAT reversal, void/reversal
- Receipt / tax-invoice PDF with CODE-128 barcode + QR
- 10 test classes, including PostgreSQL-backed integration tests

### Phase 4 — Event Scheduling (weeks 9–11)
- Appointments protected by a PostgreSQL GiST exclusion constraint (server/DB-enforced, not just client-side)
- Calendar view: day/week/month modes with drag-to-reschedule
- Job cards, estimates, parts (posts a `JOB_PART` stock movement), service-invoice generation

### Phase 5 — Inventory & Purchasing (weeks 12–15)
- Stock ledger with adjustment approval
- Supplier product catalog
- Full Purchase Order → Goods Receipt flow (partial/multi-delivery, damaged/rejected qty, cost history)
- Supplier returns, payments, and allocations
- 11 test classes — the largest single test suite in the codebase

### Phase 6 — Catalog Expansion: Brands, Attributes & Variants (weeks 16–19)
- Brands (MVP §4.8) and dynamic per-category attributes (§4.7), including ENUM-only attribute values and required-attribute rendering
- Product variants (§4.6): every product gets an always-present default variant kept in sync with the product's own sku/price until a second variant exists; attribute-driven Cartesian variant generation
- The variant cutover: `invoice_line_items`, `credit_note_line_items`, `held_sale_items`, `job_parts`, `stock_movements`, `stock_adjustments`, `supplier_products`, `purchase_order_items`, `goods_receipt_items`, `supplier_return_items`, and `product_cost_history` all repointed to `product_variant_id`, with `StockPostingService` locking/checking availability at the variant, not the product
- Variant-aware POS: barcode lookup resolves to the specific variant (falling back to the product's default variant), a variant picker for selling a non-default variant, `VARIANT_NOT_FOUND`/`VARIANT_PRODUCT_MISMATCH` error cases
- Migrations V024–V028; test classes include `BrandAttributePostgresIT`, `ProductVariantPostgresIT`, `VariantCutoverIntegrityIT`, `VariantAwarePosIT`, `CatalogControllerSecurityTest`

### Phase 7 — Manufacturing / Bill of Materials (weeks 20–21)
- Bill of Materials CRUD keyed on `ProductVariant` (finished and component sides), one BOM per finished variant, component quantity plus optional wastage per line
- Cost roll-up: `totalEstimatedCost` is recomputed on every read from each component's current cost price (wastage excluded from the estimate, included in physical consumption)
- Circular-reference guard rejecting a component that is, directly or transitively, itself built from the BOM's own finished variant
- The Produce transaction (`/api/v1/production-orders`): locks every distinct component variant plus the finished variant in one ascending-order lock acquisition, validates availability for every component before posting any movement (never a partial production), posts one `PRODUCTION_OUT` per component and, for `STOCKED` mode only, one `PRODUCTION_IN` for the finished variant — atomic and idempotent
- `STOCKED` vs `MADE_TO_ORDER` production modes (§6.4.11.3): `MADE_TO_ORDER` still consumes and costs every component but never stocks the finished variant
- JavaFX client: `BillOfMaterialsApiClient`/`ProductionOrderApiClient`, a two-tab `ManufacturingManagementView` (BOM management + Produce/production history), wired into client navigation
- Migration V029; test classes `BillOfMaterialsServicePostgresIT` (BOM-001..004), `ProductionServicePostgresIT` (BOM-PROD-001..003), `ProductionConcurrencyIT` (BOM-CON-001), plus `ProductionServiceLockingOrderTest`

---

## 2. Partially Done

Functionality exists but doesn't fully satisfy the MVP requirement yet.

| Item | What exists | What's missing |
|---|---|---|
| **Finance API surface** | Cashbook entries, customer payments/refunds are fully modeled and posted (`finance/domain`, built early in Phase 3) | No `finance/api` or `finance/application` package. MVP §17.15's `/api/finance/receivables`, `/payables`, `/cashbook` endpoints don't exist — payment endpoints live under `sales/api`, and supplier balances are only reachable via `purchasing`'s `GoodsReceiptOutstandingRepository` |
| **Backup & Restore** | `PgDumpRestoreTestUtility` under `src/test/.../support/backup` validates `pg_dump`/`pg_restore` command construction — this is exactly the Week-1 recovery spike the plan calls for | No production `BackupService`, controller, backup-history persistence, restore preflight, or UI. Correctly scheduled for Week 25, not a defect today |
| **Audit trail** | `AuditLogController` + `audit_logs` table exist server-side | No client-side audit viewer/search screen |

---

## 3. Missed - in MVP scope, not yet started

Zero code found (`Package`, `Promotion`, `Loyalty`, and the reporting/dashboard modules all return no hits outside docs). Phases 6 and 7 (Catalog Expansion and Manufacturing/BOM), added by the Week-12 scope-expansion decision (v1.4), have since shipped and moved to §1 above. These are Phases 8-10 of the plan.

### Phase 8 — Merchandising (weeks 22–23)
- Packages / bundles (§4.9) — depends on variants existing first
- Promotions & discount campaigns (§5.6a)

### Phase 9 — Finance & Recovery (weeks 24–25), remaining pieces
- Daily cash closing (expected/counted/variance/approval) — no `CashClosing` entity or migration
- Loyalty points (§3.4) — no `loyalty_transactions` table, no earn/redeem/clawback/expiry logic
- Backup & restore application feature (see §2 above)

### Phase 10 — Reporting & Dashboard (weeks 26–28)
- Dashboard KPIs (§10.1)
- All 18 MVP reports — daily sales, stock, customer/supplier balances, tax summary, variant/BOM/package/promotion/loyalty reports, etc. (§10.2). `reporting/` is currently a bare `package-info.java` on both client and server
- Shared PDF/CSV export pipeline (only the invoice/receipt PDF path from Phase 3 exists)

---

## 4. Future Developments

### Phase 11 — Stabilization & Release (weeks 29–31, still in MVP)
Not reachable until Phases 8–10 land:
- Five-client LAN test, 10-user concurrency test
- SC-01…SC-07 UAT scenario pack (SC-06 variant/brand retail and SC-07 manufacturing specifically require Phases 6–7)
- Packaging and release rehearsal

### Deferred beyond the MVP entirely (SRS.md vision, MVP.md §1.3)
Explicitly out of scope for all 31 weeks — future phases per the SRS roadmap:

| Feature | Deferred to |
|---|---|
| Offline resilience (SQLite cache, sync) | Phase 2 |
| Multi-branch support | Phase 2 |
| Batch/serial tracking, expiry dates | Phase 2 |
| Consignment management | Phase 2 |
| Full double-entry accounting / GL | Phase 2 |
| Full purchase 3-way matching, tolerance auto-approval, GRPI accounting | Phase 2 |
| SSCL, WHT, e-Invoicing | Phase 2 |
| Payroll (EPF/ETF/APIT) | Phase 2 |
| Asset management | Phase 3 |
| Advanced reporting & analytics | Phase 2 |
| Notification center (email/SMS) | Phase 2 |
| Document management | Phase 3 |
| Mobile app / customer portal | Phase 3 |
| Industry extensions (restaurant, education, travel, photo studio, courier) | Phase 3 |

---

## 5. Recommendation

The plan's sequencing is sound and has been followed faithfully so far — no reordering needed. Two things worth deciding explicitly before Week 16:

1. **Treat the variant cutover as its own risk item, not routine schema work.** It's the change most likely to destabilize the five phases already shipped, since it touches every stock-affecting table those phases built. Keep the Week 17–18 regression pass the plan already budgets for, rather than compressing it.
2. **Decide whether the Finance API gap gets closed opportunistically now or bundled into Week 24.** The underlying data already exists; only routing/aggregation is missing. Closing it early would let Phase 10's dashboard/report work build against a stable read API instead of `sales`/`purchasing` internals directly.
