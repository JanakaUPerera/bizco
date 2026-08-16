---
name: postgres
description: Design, migrate, test, and review Bizco PostgreSQL schema, transactions, ledgers, PII protection, reporting, and backup-related persistence.
---

# PostgreSQL Skill

## Purpose

Guide PostgreSQL database design, queries, and optimization for Bizco.

## Guidelines

- PostgreSQL 18.x is recommended; PostgreSQL 16+ is supported for production.
- Use Testcontainers with PostgreSQL for integration tests. H2 is only for fast unit tests that do not validate SQL dialect behavior.
- Use UUID primary keys, JSONB for queried JSON, and PostgreSQL-native identity columns and enum types.
- Treat stock movements as the authoritative stock ledger. Preserve posted financial, payment, cost, and tax snapshots for reconciliation.
- PostgreSQL 18+ for production (SRS header / §8.3 — note SRS §3.1's hardware table itself says "15+ / 16+ with streaming replication", an inconsistency within the spec; treat 18+ from the header and §8.3 as authoritative)
- Flyway for schema versioning and migrations
- Use UUID for primary keys
- DECIMAL(15,2) for financial amounts (SRS §15.2 Currency)
- Enforce referential integrity at DB level
- Index optimization for search queries
- AES-256 column-level encryption for sensitive PII columns (NIC, Passport, BR Number, bank account — SRS §6.2.1, §6.22.7); this is a hard schema-design requirement, not optional hardening

## Schema Design

- Follow naming conventions: snake_case for tables/columns
- Use junction tables for many-to-many relationships
- Soft deletes with `is_active` flags for most entities — but Customer deletion is NOT a simple flag flip: SRS §6.2.4 requires a full anonymization workflow (name → "Deleted Customer #{id}", NIC/phone/email/address/DOB nulled, `is_anonymized`/`anonymized_at` set, and a hold on deletion while invoices are outstanding, up to 7 years)
- Audit columns: `created_at`, `updated_at`, `created_by`
- JSON column for role permissions

## Migrations

- Versioned migration files: `V1__init.sql`, `V2__seed_data.sql`
- Never modify applied migrations
- Test migrations on clean database before committing

## References

- `docs/DatabaseDesign.md` does not exist yet in this repo — do not treat it as a source; schema requirements currently live only in `docs/SRS.md` and `docs/MVP.md`
- See `docs/SRS.md` for data requirements (esp. §6.2.1, §6.22.7 for PII encryption; §15.2 for currency/decimal precision)
