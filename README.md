# Bizco

Bizco is a standalone business management system for Sri Lankan SMEs. It is designed for retail, trading, and service businesses that need POS, invoicing, scheduling, inventory, and operational reporting on a single PC or a local network.

## MVP Scope

The current MVP includes:

- Authentication, action-based RBAC, audit logging, and user administration
- Customer, supplier, product, category, UOM, and service management
- POS, invoices, payments, VAT, receipts, credit notes, and returns
- Appointments, calendar views, job cards, estimates, parts usage, warranty, and technician scheduling
- Stock ledger, GRNs, adjustments, supplier returns, payments, and cost history
- Cashbook, receivables, payables, daily cash closing, reports, backup, and restore

Offline synchronization, multi-branch support, full general-ledger accounting, purchase orders, full purchase invoices, payroll, and industry extensions are planned after the MVP.

## Architecture

```text
JavaFX desktop clients
        |
        v
Spring Boot REST API
        |
        v
PostgreSQL
```

JavaFX clients communicate with the API only. PostgreSQL is server-local or backend-only; clients must not connect to the database directly.

The application supports:

- Single-PC mode: client, API, PostgreSQL, and file storage on one computer
- LAN mode: multiple JavaFX clients connected to a central API server

## Technology

| Area | Technology |
|---|---|
| Client | JavaFX + ControlsFX |
| Server | Spring Boot 3.x |
| Database | PostgreSQL 18.x recommended, PostgreSQL 16+ supported |
| Migrations | Flyway |
| Security | Spring Security + BCrypt |
| Tests | JUnit 5, Testcontainers PostgreSQL, TestFX |
| Build | Maven |

## Project Documents

- [SRS](docs/SRS.md): long-term product and technical requirements
- [MVP](docs/MVP.md): current implementation scope and acceptance criteria
- [Development Plan](docs/DevelopmentPlan.md): 20-week delivery roadmap
- [.codex skills](.codex/skills): project-specific engineering guidance

## Development Status

Planning and architecture are complete. The next step is Phase 1: initialize the Maven modules, PostgreSQL/Flyway baseline, Spring Boot server, and JavaFX client shell.

## Development Principles

- Keep posted business documents immutable; correct them with reversals.
- Treat `stock_movements` as the authoritative stock ledger.
- Make sales, GRNs, returns, payments, and cash closings atomic database transactions.
- Use PostgreSQL Testcontainers for integration tests.
- Use forward-only Flyway migrations after a schema change is shared.

## License

License to be decided before external distribution.
