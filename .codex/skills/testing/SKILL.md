---
name: testing
description: Design and implement Bizco unit, PostgreSQL integration, API, JavaFX, reconciliation, recovery, and acceptance tests.
---

# Testing Skill

## Purpose

Guide testing strategies, test cases, and quality assurance for Bizco.

## Guidelines

- JUnit 5 + Mockito for unit tests
- Integration tests for API endpoints
- PostgreSQL Testcontainers for integration, migration, transaction, and repository tests
- H2 only for fast unit tests that do not validate PostgreSQL behavior
- Test all RBAC permission scenarios
- End-to-end workflow tests

## Test Checklist

- [ ] All API endpoints tested
- [ ] POS workflow end-to-end
- [ ] Invoice calculation accuracy (VAT, discounts)
- [ ] Credit limit enforcement
- [ ] Stock deduction atomicity
- [ ] Stock on hand equals the signed stock-movement ledger
- [ ] GRN creates stock, cost history, and supplier payable atomically
- [ ] Supplier returns and payments reconcile to supplier balance
- [ ] Cashbook, receivables, payables, and daily closing reconcile
- [ ] Verified backup restores a clean supported PostgreSQL instance
- [ ] Appointment double-booking prevention
- [ ] Job card status transitions
- [ ] RBAC permission checks
- [ ] Secondary role granting/revocation with expiry
- [ ] Effective permissions calculation

## Acceptance Criteria

- POS: Scan → Add → Discount → Pay → Print in < 2 minutes
- Invoice: VAT calculation matches the configured rate (default 18%, SRS §6.11.2.1 — VAT is explicitly configurable, so tests should assert against the configured rate, not hardcode 18%; Sri Lanka's rate has changed before, e.g. NBT's 2022 abolition per SRS §6.11.4)
- Appointments: No double-bookings
- Stock: on hand equals the sum of signed posted stock movements
- Purchasing: GRNs, returns, payments, costs, and payables reconcile
- Finance: cashbook and daily closing reconcile from source documents

## Non-Functional Targets to Test Against

- For the MVP, verify its stated 10+ concurrent-user target and the five-client LAN release rehearsal; treat broader SRS targets as future-scale design targets.
Not just functional correctness — SRS §7.1 Performance sets concrete targets DevelopmentPlan.md's Week 16 task 16.5 ("Performance testing") expects verified:
- Invoice generation < 2 seconds
- Product search < 1 second
- Support 50+ concurrent users (SRS §3.3 network architecture also caps at 50 concurrent clients)

## References

- See `docs/DevelopmentPlan.md` Section 7: Quality Assurance
- See `docs/SRS.md` Section 7.1: Performance (non-functional acceptance targets)
