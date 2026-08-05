---
name: development-plan
description: Plan and track Bizco MVP delivery using the approved 20-week roadmap, milestones, dependencies, quality gates, and post-MVP boundaries.
---

# Development Plan Skill

## Purpose

Guide project development phases, milestones, and task execution.

## Guidelines

- Current-scope override: use the 20-week, eight-phase plan below. Ignore any older wording in this file that mentions a 17-week plan, seven phases, or reducing Event Scheduling.
- 20-week baseline with approximately 1.5 developer FTE; a solo developer plans 26-30 weeks
- 8 phases with defined milestones (M1-M8, DevelopmentPlan.md §5)
- POS + Invoicing before Event Scheduling is the phase order below, not an independent priority call — if timeline pressure forces cuts, DevelopmentPlan.md §2.2's solo-developer variant is the one that explicitly states "prioritize POS + Invoicing over Event Scheduling"; the standard team plan already sequences them this way by default
- Test each milestone and phase exit gate before proceeding.
- Preserve the complete Event Scheduling scope unless the MVP changes explicitly.

## Phases

1. Foundation (Weeks 1-3): runtime, Flyway, identity, RBAC, client shell
2. Core Master Data (Weeks 4-5): customers, suppliers, products, categories, UOMs, services
3. POS & Invoicing (Weeks 6-8): cart, pricing, invoices, payments, receipts, credit notes, returns
4. Event Scheduling (Weeks 9-11): appointments, calendar, job cards, estimates, parts, warranty, technician views
5. Inventory & Purchasing (Weeks 12-14): ledger, GRNs, costs, supplier returns/payments
6. Basic Finance & Recovery (Weeks 15-16): cashbook, balances, closing, backup/restore
7. Reporting & Dashboard (Weeks 17-18): reports, exports, reconciliations
8. Stabilization & Release (Weeks 19-20): integration, LAN/hardware, UAT, release rehearsal

Everything not in MVP.md §1.2 ("What's IN the MVP") is out of scope for these 17 weeks — see MVP.md §1.3 for the deferred list (offline resilience, multi-branch, variants/batch/serial, consignment, full GL, SSCL/WHT/e-Invoicing, loyalty, payroll, asset management, industry extensions, etc.), which is far larger than what these 7 phases build.

## References

- See `docs/DevelopmentPlan.md` for detailed tasks
- See `docs/MVP.md` for the authoritative in/out-of-scope module list these phases implement
