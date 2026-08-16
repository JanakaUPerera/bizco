---
name: logging
description: Implement Bizco application, audit, activity, PII-access, security, and tax-configuration logging with safe data handling and retention.
---

# Logging Skill

## Purpose

Guide logging implementation for debugging, auditing, and monitoring.

## Guidelines

- Logback for application logging
- Structured logging with correlation IDs
- Separate log levels for dev vs production
- Audit logging for all data changes
- PII access logging

## Log Levels

- ERROR: System errors, exceptions
- WARN: Recoverable issues, validation failures
- INFO: Business events, transactions
- DEBUG: Development debugging (disabled in prod)
- TRACE: Ultra-detailed diagnostics — variable values, method entry/exit (SRS §12.1)

## Log Format & Retention

- Format: `[timestamp] [level] [module] [user] [client] message` (SRS §12.3) — no formal correlation-ID field is specified, just user/client tags
- Retention (SRS §12.2): Application logs 90 days, Security logs 1 year, **Audit logs 7 years**, Performance logs 30 days, System logs 90 days

## Audit Logging

- Record posting success/failure and reversal references for sales, credit notes, GRNs, supplier returns/payments, cashbook, and cash closings without logging sensitive values unnecessarily.
- Record backup start, verification, failure, and restore attempts with the initiating user and result.
There are three distinct log types in SRS — don't collapse them into one "audit log":

- **Audit Log** (SRS §6.18.1): all CRUD operations — who changed what and when
- **Activity Log** (SRS §6.18.2): non-data events (logins, exports, report views)
- **PII Access Log** (SRS §6.18.3 / §6.2.1): every view/export of sensitive PII
- **Tax Configuration Change Log** (SRS §6.18.4): rate/threshold changes
- Role changes with full details (SRS §5.8 — narrowly scoped to role grant/revoke/expiry, nested under RBAC, distinct from the §6.18 audit system above)
- Login history (IP, timestamp, client_id, success/failure — SRS §6.1.3)

## References

- See `docs/SRS.md` Section 6.18: Audit & Activity Logging (the comprehensive system)
- See `docs/SRS.md` Section 12: Logging Strategy (levels, format, retention)
- See `docs/SRS.md` Section 5.8: Audit Trail for Role Changes (RBAC-specific subset only)
