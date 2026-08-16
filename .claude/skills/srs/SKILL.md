---
name: srs
description: Interpret Bizco SRS v2.1 alongside MVP v1.2, resolving scope, phase, requirement, Sri Lankan tax, privacy, and acceptance-criteria questions.
---

# SRS Skill

## Purpose

Guide understanding and implementation of Software Requirements Specification.

## Guidelines

- Treat SRS v2.1 as the long-term requirements source and MVP v1.2 as the current delivery boundary.
- When requirements conflict with MVP scope, preserve the SRS design intent but implement only the MVP-approved behavior.
- For current MVP work, include basic supplier/purchasing, finance, and backup/restore, while deferring offline sync, multi-branch, full GL, purchase orders, full purchase invoices, and statutory extensions.
- SRS is the source of truth for all features
- Implement features as defined in SRS sections
- Defer Phase 2 features as specified in MVP
- Follow Sri Lankan tax and accounting practices
- Support PDPA compliance for customer data

## Key Modules

SRS.md Section 6 defines 22 functional modules — the list below was missing more than half of them. Full picture (SRS §6.x):

- Authentication & Security (§6.1)
- Customer Management (§6.2)
- Supplier Management, incl. Consignment Agreements (§6.3)
- Product Management (§6.4)
- Service Management: Job Cards, Appointments (§6.5)
- Inventory Management (§6.6)
- Point of Sale / POS (§6.7)
- Invoice Management (§6.8)
- Purchasing Management, incl. 3-Way Match (§6.9)
- Finance & Accounting: General Ledger, AR/AP, Banking, Cash Management (§6.10)
- Tax Management — Sri Lanka: VAT, SSCL, NBT (legacy), WHT, e-Invoicing (§6.11)
- Expense Management (§6.12)
- Payroll Management: EPF/ETF/APIT (§6.13)
- Asset Management (§6.14)
- Industry Extensions: Restaurants, Educational Institutes, Travel Agencies, Photo Studios, Courier Services (§6.15)
- Reporting & Analytics (§6.16)
- Dashboard (§6.17)
- Audit & Activity Logging (§6.18)
- Backup & Restore (§6.19)
- Document Management (§6.20)
- Notification Center (§6.21)
- Data Privacy / PDPA Compliance (§6.22)

Most of these are out of MVP scope (see MVP.md §1.3) — but "out of MVP" is not the same as "doesn't exist in the spec." If asked to build ahead of the MVP phase plan, or to design schema/APIs that shouldn't need reshaping later, check the relevant §6.x section even for modules not yet scheduled.

## References

- Primary: `docs/SRS.md` — the full long-term specification (v2.1), covers all 22 modules above regardless of build phase
- MVP Scope: `docs/MVP.md` — the authoritative in/out-of-scope module list for the current build (v1.2, aligned to SRS v2.1)
- Schedule: `docs/DevelopmentPlan.md` — phases, weeks, and milestones for building the MVP.md scope
