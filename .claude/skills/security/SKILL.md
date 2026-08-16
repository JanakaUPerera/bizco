---
name: security
description: Implement and review Bizco authentication, RBAC, sessions, PII protection, PDPA controls, auditability, and secure LAN communication.
---

# Security Skill

## Purpose

Guide security implementation for authentication, authorization, and data protection.

## Guidelines

- BCrypt password hashing (cost factor 12)
- Token-based session management via Spring Security. Note: neither SRS.md nor MVP.md ever say "JWT" — the only trace of it is an example filename (`JwtTokenProvider.java`) in DevelopmentPlan.md's illustrative file tree. Treat JWT as a reasonable default implementation, not a spec requirement.
- TLS **1.3** specifically for client-server communication (SRS §7.2, §6.22.7 — not just "TLS", the version is spec'd)
- Action-based RBAC with multi-role support
- Session timeout (15 min idle, configurable **per role** — SRS §5.6)
- Account lock after 5 failed attempts

## RBAC Implementation

- Permissions follow `module.action` pattern
- Primary role (permanent) + Secondary roles (temporary with expiry)
- Effective permissions = union of all active roles
- Permission check at runtime before every action
- Audit trail for all role changes

## Data Protection

- Restrict backup creation and restore to explicit system permissions; require confirmation, maintenance-mode checks, and audit records for restores.
- Enforce server-side permission checks for supplier, finance, purchasing, reporting, and backup APIs as well as POS and scheduling APIs.
- PII encryption at rest (AES-256) for NIC, BR Number (SRS §6.2.1 "Sensitive" tier). Note SRS has an internal tension here: §6.22.7's Encryption Standards table also lists phone and email as AES-256 encrypted, while §6.2.1 classifies phone/email as the lighter "Personal" tier (access-controlled + logged, not necessarily encrypted). Until clarified, prefer the broader §6.22.7 protection for phone/email rather than assuming §6.2.1's lighter tier is sufficient.
- PII access logging
- PDPA compliance for customer consent
- Right to erasure/anonymization workflow

## References

- See `docs/SRS.md` Section 5: User Roles & Access Control
- See `docs/SRS.md` Section 6.1: Authentication & Security
