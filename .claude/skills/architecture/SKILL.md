---
name: architecture
description: Design and review Bizco's JavaFX, Spring Boot, PostgreSQL, LAN, and phased offline architecture. Use for module boundaries, deployment, API, transaction, or integration decisions.
---

# Architecture Skill

## Purpose

Guide architectural decisions for the Bizco project including system design, module boundaries, and technology integration patterns.

## Guidelines

- The current MVP is a 20-week modular monolith: identity, customer, catalog, sales, scheduling, inventory, purchasing, finance, reporting, and system.
- JavaFX uses the backend API only. PostgreSQL port 5432 remains server-local or backend-only.
- The current API contracts are in MVP Section 17; offline resilience and multi-branch remain future-phase work.
- Follow Client-Server (LAN Based) architecture as defined in SRS
- Use layered architecture: Presentation → Application → Data
- Maintain separation between bizco-server, bizco-client, and bizco-common modules
- Follow REST API conventions for client-server communication (see MVP.md §16 API Contracts for the concrete endpoint shapes defined so far)
- Design toward offline resilience (SRS §4.3) and multi-branch readiness (SRS §4.4) so the schema/API don't need reshaping later — but do not build either one during MVP: both are explicitly deferred to Phase 2 (Months 5-8) per MVP.md §1.3 and DevelopmentPlan.md §9.1. Note SRS.md itself never labels these "Phase 2" — it describes them as core architecture; the phase deferral is an MVP-scoping decision, not a spec limitation.

## Architecture Patterns

- Keep shared code to DTOs, error contracts, validation, and stable enums. Do not share JPA entities with JavaFX.
- Make sale, credit note, job-part, GRN, supplier return, supplier payment, and cash-closing posting atomic transactions.
- Spring Boot REST API server
- JavaFX desktop client; local SQLite offline cache + 3-phase sync protocol (SRS §4.3.1-4.3.6) is Phase 2, not MVP
- Shared DTOs and models via bizco-common module
- Flyway for database migrations
- Token-based authentication via Spring Security. SRS never mandates JWT specifically (grep confirms "JWT" appears nowhere in SRS.md or MVP.md); DevelopmentPlan.md's illustrative file tree names a `JwtTokenProvider.java`, which is the only trace of JWT as an implementation choice — treat it as a reasonable default, not a hard requirement

## When You Actually Build Offline Resilience (Phase 2)

Even though it's deferred, if/when picked up, the SRS already specifies the protocol in detail — don't redesign it:
- Client heartbeat `GET /api/health` every 30s; retry backoff 30s→60s→120s→300s (SRS §4.3.5)
- Offline invoice numbers `OFF-{CLIENT_ID}-YYYYMMDD-NNNN`, reassigned a permanent number on sync with both retained (SRS §4.3.6)
- Conflict resolution rules are pre-defined per conflict type (price change, oversold stock, credit limit override, duplicate customer) — see SRS §4.3.4

## References

- Use MVP Section 17 for the current API contracts and DevelopmentPlan Section 4 for the 20-week implementation sequence.
- See `docs/SRS.md` Section 4: System Architecture
- See `docs/MVP.md` §1.3 for what's deferred out of MVP, and §16 for current API Contracts
- See `docs/DevelopmentPlan.md` for module structure and phase schedule
