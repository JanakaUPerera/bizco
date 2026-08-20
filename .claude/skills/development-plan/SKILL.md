---
name: development-plan
description: Plan and track Bizco MVP delivery using the approved 31-week roadmap, milestones, dependencies, quality gates, and post-MVP boundaries.
---

# Development Plan Skill

## Purpose

Guide project development phases, milestones, and task execution.

## Guidelines

- Current-scope plan: 31 weeks, 11 phases, 11 milestones (M1-M11, DevelopmentPlan.md §9). This supersedes the earlier 20-week/8-phase and 17-week/7-phase plans — the Week 12 scope-expansion decision (MVP.md §1.2a, §1.2/§1.3) inserted three new phases (Catalog Expansion, Manufacturing/BOM, Merchandising) between the original Phase 5 and Phase 6, moved purchase orders/product variants/loyalty from deferred into MVP scope, and pushed Purchasing from 3 to 4 weeks.
- 31-week baseline assumes ~1.5 developer FTE plus part-time QA/business review (DevelopmentPlan.md §3.1); a solo developer plans 26-30 weeks (§3.2) for the same scope.
- Test each milestone and phase exit gate before proceeding.
- Preserve full scope (including the expanded catalog/manufacturing/merchandising modules) unless the MVP changes explicitly.

## Phases

1. Foundation & Identity (Weeks 1-3): runtime, Flyway, identity, RBAC, client shell — M1
2. Master Data (Weeks 4-5): customers, credit/PII, catalog, services, suppliers, barcode — M2
3. Sales, POS & Receivables (Weeks 6-8): cart, pricing, invoices, payments, credit notes, returns, receipts — M3
4. Scheduling & Service Work (Weeks 9-11): appointments, calendar, job cards, estimates, parts, service invoice — M4
5. Inventory & Purchasing (Weeks 12-15): stock ledger, supplier catalog/POs, goods receipt/cost history, supplier returns/payments — M5
6. Catalog Expansion: Brands, Attributes & Variants (Weeks 16-19): brands, dynamic attributes, product_variants schema/backfill, variant cutover across sales/scheduling/inventory, variant-aware POS — M6
7. Manufacturing: Bill of Materials (Weeks 20-21): BOM schema/management, production/assembly transaction — M7
8. Merchandising: Packages & Promotions (Weeks 22-23): packages/bundles, promotions/discount campaigns — M8
9. Finance & Recovery (Weeks 24-25): cashbook, receivables/payables, daily closing, loyalty, backup/restore — M9
10. Reporting, Dashboard & Audit (Weeks 26-28): audit/read views, dashboard, sales/stock/finance/scheduling/VAT reports, expanded-scope reports — M10
11. Stabilization & Release (Weeks 29-31): system verification/concurrency/packaging, expanded-scope regression, UAT/scenario pack/release — M11

Variants are the one addition in Phase 6 that isn't purely additive: every product (even one with no real variation) gets exactly one `product_variants` row, so there is no simple/varianted special-casing anywhere downstream — see DevelopmentPlan.md Phase 6 preamble.

Everything not in MVP.md §1.2 ("What's IN the MVP") is out of scope — see MVP.md §1.3 and DevelopmentPlan.md §20 for the current deferred list: offline resilience/sync, multi-branch, full purchase-invoice 3-way matching/GRPI accounting, batch/serial tracking and expiry, full GL, SSCL/WHT, statutory e-Invoicing integration, notifications, advanced analytics (Post-MVP Phase 2); assets, payroll, document management, industry-specific modules, mobile companion (Post-MVP Phase 3); AI forecasting, portals, e-commerce integration, broader BI (Post-MVP Phase 4). Purchase orders, product variants, and loyalty are no longer deferred — they moved into MVP scope as Phases 5-9 above.

## References

- See `docs/DevelopmentPlan.md` for detailed tasks, weekly task tables, acceptance-test IDs, and the milestone summary (§9)
- See `docs/MVP.md` for the authoritative in/out-of-scope module list, including the §1.2a Version 1.4 scope-expansion decision
