---
name: code-review
description: Review Bizco changes for defects, security, transaction safety, RBAC, financial and stock integrity, database migration safety, and missing tests.
---

# Code Review Skill

## Purpose

Guide code review practices to maintain code quality and consistency.

## Guidelines

- Review all changes before merging
- Check for security vulnerabilities
- Verify RBAC permission checks
- Ensure proper error handling
- Validate business logic correctness

## Review Checklist

- [ ] Stock movements, supplier balances, cashbook entries, and tax snapshots reconcile from posted source documents
- [ ] Financial and inventory postings are atomic, immutable, and corrected by reversal rather than edit/delete
- [ ] Flyway migrations are forward-only and tested against PostgreSQL
- [ ] Backup/restore changes have authorization, audit, failure handling, and recovery tests
- [ ] Code follows Java naming conventions
- [ ] No commented-out code
- [ ] Methods under 50 lines
- [ ] Proper exception handling
- [ ] Database transactions used correctly
- [ ] SQL injection prevention
- [ ] Input validation present
- [ ] Audit logging implemented
- [ ] Tests included for new features
- [ ] No secrets or credentials in code

## RBAC-Specific Checks

DevelopmentPlan.md §7.2 calls these out explicitly as their own checklist items — don't fold them into a generic "permission checks" bullet:

- [ ] Every endpoint/action checks the specific `module.action` permission it needs, not a coarser one
- [ ] Effective permissions are computed as the union of primary role + active secondary roles (SRS §5.3.5)
- [ ] Secondary role expiry is respected (expired roles excluded even if `is_active` wasn't yet flipped by the background job — SRS §5.3.4)
- [ ] Role grant/revoke/expiry writes an audit log entry (SRS §5.8)

## Security Checks

- Permission checks before data access
- PII handling follows PDPA guidelines — see SRS §6.22 (Data Privacy), §5.5 (PII Access Restrictions matrix), §6.18.3 (PII Access Log)
- Password hashing with BCrypt
- Input sanitization
- Proper session management

## References

- See `docs/SRS.md` for requirements
- See `docs/DevelopmentPlan.md` Section 7: Quality Assurance
