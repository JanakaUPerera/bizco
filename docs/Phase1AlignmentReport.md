# Phase 1 Alignment Report

## 1. Executive Summary

This audit compares the existing Phase 1 implementation against the finalized source-of-truth documents:

- `docs/MVP.md` v1.3
- `docs/DomainModel.md`
- `docs/StateMachines.md`
- `docs/DatabaseDesign.md`
- `docs/ApiContracts.md`
- `docs/AcceptanceTests.md`
- `docs/DevelopmentPlan.md`

The repository has a useful early Phase 1 foundation: a Maven parent with `bizco-common`, `bizco-server`, and `bizco-client`; Spring Boot and JavaFX modules; Flyway enabled; JPA validation; BCrypt cost 12; opaque bearer tokens stored as hashes; login/logout/change-password service code; login history; secondary role grants; effective permission union logic; a JavaFX login/shell; and permission-filtered JavaFX navigation.

The finalized documents materially changed the Phase 1 target. The current implementation has now been aligned for the main foundation contracts, with remaining gaps mostly in onboarding, release hardening, and later workflow APIs:

- Current Phase 1 API paths and JavaFX clients now use `/api/v1/...`, with selected legacy aliases still present during transition.
- RBAC now uses normalized `permissions` and `role_permissions`, final action permission names, final seeded system roles, and runtime expiry-aware effective permission calculation.
- Flyway migrations have now been rebuilt as a clean pre-production Phase 1 foundation baseline using the finalized ordering where applicable: `V001`, `V002`, `V003`, `V004`, `V007`, `V017`, `V018`, and `V021`.
- Session persistence now supports opaque token hashes, idle timeout, concurrent-session enforcement, current-session lookup, and force logout. Role-sensitive/session policy UI remains later hardening.
- Error responses now have the finalized foundation shape with `fieldErrors`, `path`, and `correlationId`; broader endpoint-by-endpoint domain error mapping remains ongoing as new APIs are added.
- Correlation ID infrastructure now exists; idempotency infrastructure is still absent beyond shared header constants and a JavaFX helper.
- Backup/restore is not implemented as a full Week 16 UI/service yet, but the early technical spike now proves `pg_dump`/`pg_restore` feasibility in the supported Testcontainers PostgreSQL environment.
- PostgreSQL Testcontainers are now the authoritative Phase 1 integration-test environment. `mvn clean verify` runs a clean PostgreSQL/Flyway/JPA validation install, restart/rerun check, auth/session/RBAC acceptance coverage, and PostgreSQL constraint tests.

Classification summary:

| Area | Status |
|---|---|
| Maven multi-module structure | PASS |
| Spring Boot configuration | PASS |
| JavaFX application structure | PASS |
| Spring Security | PASS |
| Core login/logout/change-password | PASS |
| BCrypt cost 12 | PASS |
| Users/roles/temporary role foundation | PASS |
| Final permission registry/RBAC matrix | PASS |
| Session management final behavior | PASS |
| Login history | MODIFY |
| Audit logging | PASS |
| Business profile | PASS |
| Tax configuration | MODIFY |
| First-launch onboarding | MODIFY |
| API client and token/correlation headers | PASS |
| Permission-based JavaFX UI | MODIFY |
| Flyway/PostgreSQL Phase 1 foundation schema | PASS |
| Testcontainers dependencies | PASS |
| API error contract foundation | PASS |
| Optimistic locking/version fields | PASS |
| Correlation IDs | PASS |
| Idempotency infrastructure | MODIFY |
| Backup/restore technical spike | PASS |
| Backup/restore full implementation | MODIFY |

This report has since been updated after the Phase 1 foundation alignment implementation. No Phase 2/customer, product, invoice, appointment, inventory, purchasing, or finance feature implementation was started.

## 2. Repository Structure Found

Status: PASS

Existing structure:

```text
bizco/
  pom.xml
  bizco-common/
    pom.xml
    src/main/java/com/bizco/common/...
  bizco-server/
    pom.xml
    src/main/java/com/bizco/server/...
    src/main/resources/application.yml
    src/main/resources/db/migration/V001, V002, V003, V004, V007, V017, V018, V021
    src/test/java/com/bizco/server/...
  bizco-client/
    pom.xml
    src/main/java/com/bizco/client/...
    src/main/resources/com/bizco/client/application.css
    src/test/java/com/bizco/client/...
  docs/
```

The Maven module names match `DevelopmentPlan.md` Section 6. Package names use `com.bizco.server`, `com.bizco.client`, and `com.bizco.common`, which is compatible with the finalized plan. Several future capability packages exist as placeholders: `customer`, `catalog`, `sales`, `scheduling`, `inventory`, `purchasing`, `finance`, and `reporting`.

## 3. Already Compliant

### Maven multi-module structure

Classification: PASS

- Existing files/classes: root `pom.xml`, `bizco-common/pom.xml`, `bizco-server/pom.xml`, `bizco-client/pom.xml`.
- Existing behavior: Reactor builds common, server, and client modules; server depends on common; client depends on common.
- Finalized requirement satisfied: `DevelopmentPlan.md` Section 6 requires `bizco-common`, `bizco-server`, and `bizco-client`.

### Java 21 baseline

Classification: PASS

- Existing files/classes: root `pom.xml`.
- Existing behavior: `java.version` is `21` and compiler release uses that value.
- Finalized requirement satisfied: `DevelopmentPlan.md` Section 4 requires Java 21 LTS.

### Core Spring Boot server module

Classification: PASS

- Existing files/classes: `bizco-server/pom.xml`, `BizcoServerApplication`.
- Existing behavior: Spring Boot web, security, validation, JPA, actuator, Flyway, and PostgreSQL dependencies are present.
- Finalized requirement satisfied: Spring Boot REST server foundation in `DevelopmentPlan.md` Sections 4 and 6.

### Flyway is enabled and JPA validates schema

Classification: PASS

- Existing files/classes: `bizco-server/src/main/resources/application.yml`.
- Existing behavior: Flyway is enabled at `classpath:db/migration`; Hibernate `ddl-auto` is `validate`; `open-in-view` is disabled.
- Finalized requirement satisfied: Database is migration-owned and JPA validates instead of auto-mutating schema, per `DatabaseDesign.md` and `DevelopmentPlan.md`.

### Phase 1 PostgreSQL/Flyway foundation baseline

Classification: PASS

- Existing files/classes: `V001__extensions.sql`, `V002__permissions_roles_and_users.sql`, `V003__sessions_login_history_and_staff_profiles.sql`, `V004__business_tax_and_system_configuration.sql`, `V007__document_sequences.sql`, `V017__audit_logs.sql`, `V018__backup_and_restore_history.sql`, `V021__seed_permissions_roles_uom_tax.sql`.
- Existing behavior: clean PostgreSQL migration creates `pgcrypto`, `btree_gist`, `pg_trgm`, normalized `permissions`/`roles`/`role_permissions`, final-shape users, secondary role assignments, sessions, login history, staff profiles, single-row business/tax configuration, system config, document sequences, audit logs, and backup/restore history.
- Finalized requirement satisfied: `DatabaseDesign.md` Sections 3, 6, 7, 17, 18, 45, 46, and 53 for the Phase 1 foundation scope. Phase 2 feature tables are intentionally not created in this task.

### BCrypt cost factor

Classification: PASS

- Existing files/classes: `SecurityConfig`.
- Existing behavior: `PasswordEncoder` is `new BCryptPasswordEncoder(12)`.
- Finalized requirement satisfied: BCrypt cost 12 in `MVP.md`, `DevelopmentPlan.md`, and `AcceptanceTests.md` security checklist.

### Opaque bearer token foundation

Classification: PASS

- Existing files/classes: `TokenService`, `UserSession`, `SessionAuthenticationFilter`, `AuthService`, `ApiClient`.
- Existing behavior: login creates a random 32-byte URL-safe token; server stores SHA-256 hash; JavaFX sends `Authorization: Bearer <token>`.
- Finalized requirement satisfied: `ApiContracts.md` specifies opaque bearer session tokens and token hashes stored server-side.

### Effective permission union foundation

Classification: PASS

- Existing files/classes: `PermissionService`, `UserRoleRepository`, `PermissionServiceTest`.
- Existing behavior: effective permissions are the union of primary-role permissions and active non-expired secondary-role permissions.
- Finalized requirement satisfied: `MVP.md` Section 2.4, `DomainModel.md` Section 6.6, `StateMachines.md` Section 20.

### JavaFX does not connect directly to PostgreSQL

Classification: PASS

- Existing files/classes: `ApiClient`, `AuthApiClient`, `IdentityApiClient`, `BusinessProfileApiClient`, `HealthApiClient`.
- Existing behavior: JavaFX uses `java.net.http.HttpClient` against Spring API paths; no client JDBC or PostgreSQL dependency is present.
- Finalized requirement satisfied: `Architecture` rules in `DomainModel.md`, `DatabaseDesign.md`, and `DevelopmentPlan.md`.

### Permission-based JavaFX navigation foundation

Classification: PASS

- Existing files/classes: `BizcoClientApplication`, `ClientSession`.
- Existing behavior: sidebar module visibility uses `session.hasPermission(...)` with finalized action permission codes for Phase 1 shell navigation.
- Finalized requirement satisfied: JavaFX permission-based UX expectation in `MVP.md`, `StateMachines.md`, and `ApiContracts.md`. Permission refresh after expiry/revoke remains a later session/API gap.

### Current tests pass

Classification: PASS

- Existing files/classes: tests under `bizco-common/src/test`, `bizco-server/src/test`, and `bizco-client/src/test`.
- Existing behavior: `mvn clean verify` passes with 79 tests total: 3 common unit tests, 53 server unit/slice tests, 20 server PostgreSQL integration tests, and 3 JavaFX client tests.
- Finalized requirement satisfied: Phase 1 unit/slice/client tests plus PostgreSQL-backed acceptance/integrity gates run in the standard Maven verification lifecycle.

### Testcontainers integration baseline

Classification: PASS

- Existing files/classes: `bizco-server/pom.xml`, `PostgresIntegrationTest`, `FlywayCleanInstallIT`, `Phase1SecurityPostgresIT`, `PostgresSchemaIntegrityIT`.
- Existing behavior: Failsafe runs `*IT` during `mvn clean verify`; the reusable test base starts PostgreSQL `postgres:16-alpine`, applies Flyway `11.7.2` through version `021`, and starts Spring with Hibernate `ddl-auto=validate`.
- Finalized requirement satisfied: `SYS-INSTALL-001`, `SYS-INSTALL-002`, `SEC-AUTH-001..007`, `SEC-SESSION-001..004`, `SEC-RBAC-001..008`, and the current Phase 1 PostgreSQL integrity checks are covered against PostgreSQL, not H2.
- Remaining gap: broader packaged install, backup restore, and release rehearsal evidence remain open for later hardening.

### Backup/restore technical spike

Classification: PASS

- Existing files/classes: `docs/BackupRestoreSpike.md`, `PgDumpRestoreSpikeIT`, `PostgresToolPathResolver`, `PgDumpRestoreTestUtility`, `PostgresToolPathResolverTest`.
- Existing behavior: Testcontainers PostgreSQL `postgres:16-alpine` provides PostgreSQL 16.14, `pg_dump` 16.14, and `pg_restore` 16.14. The spike creates a custom-format dump, copies it to a host temporary file, restores it into a clean temporary database, and verifies Flyway schema version `021` plus readable Phase 1 tables.
- Finalized requirement satisfied: early backup/restore feasibility called out by `AcceptanceTests.md` and `DevelopmentPlan.md` before Phase 2.
- Remaining gap: full Week 16 backup UI/service, maintenance-mode restore guard, checksum persistence, audit records, retention policy, and release recovery rehearsal are intentionally not implemented in this spike.

### Shared API error and header contracts

Classification: PASS

- Existing files/classes: `ApiError`, `FieldError`, `ApiErrorCode`, `ApiHeaders`, `PageResponse`, `ApiErrorTest`.
- Existing behavior: common now exposes the finalized error shape with `details`, `fieldErrors`, `path`, and `correlationId`; stable error-code constants and API header constants exist; page-response DTO exists for future paged APIs.
- Finalized requirement satisfied: `ApiContracts.md` Section 5 and Section 53.

### Correlation ID infrastructure

Classification: PASS

- Existing files/classes: `CorrelationIdFilter`, `ApiExceptionHandler`, `logback-spring.xml`, `CorrelationIdFilterTest`, `AuthControllerTest`.
- Existing behavior: server accepts or generates `X-Correlation-Id`, returns it in the response header, stores it in request attributes, places it in MDC, and includes it in API error bodies. Log patterns include the MDC correlation value.
- Finalized requirement satisfied: `ApiContracts.md` Section 3.2 and `API-007` foundation.

### JavaFX correlation and idempotency header foundation

Classification: PASS

- Existing files/classes: `ApiClient`, `AuthApiClient`.
- Existing behavior: JavaFX API calls send `X-Correlation-Id`; authenticated API calls use shared `ApiHeaders`; `ApiClient` has a protected `postIdempotent` helper for later critical commands.
- Finalized requirement satisfied: client-side correlation header foundation and a narrow idempotency contract hook.

## 4. Partially Compliant

### Spring Boot configuration

Classification: MODIFY

- Current implementation: `application.yml` configures datasource from environment, Flyway, JPA validation, actuator health/info, and log file output.
- Problem: API base path is not configured around `/api/v1`; TLS is not configured; no explicit production profile/security hardening is visible.
- Required change: align controllers to `/api/v1`, add correlation ID filter/log MDC, document TLS/deployment configuration, keep JPA validation and Flyway.
- Impact: Existing configuration can be preserved, but all API clients/tests must move to final paths and headers.

### Spring Security

Classification: PASS

- Current implementation: `SecurityConfig` disables CSRF for REST, uses stateless sessions, permits `/api/health`, `/api/v1/health`, `/actuator/health`, `/api/auth/login`, and `/api/v1/auth/login`, authenticates all other requests, and enables method security.
- Existing behavior: authentication and access-denied handlers return finalized JSON error codes `AUTH_SESSION_INVALID` and `AUTH_PERMISSION_DENIED`; `SessionAuthenticationFilter` resolves opaque token hashes, touches active sessions, rejects idle-expired sessions as `AUTH_SESSION_EXPIRED`, and recalculates permissions per request.
- Finalized requirement satisfied: opaque server-side sessions, server-authoritative permission checks, final auth/session error codes, and backward-compatible old auth route aliases while JavaFX uses `/api/v1/auth/login`.

### Login/logout/change-password

Classification: PASS

- Current implementation: `AuthService` implements login, logout, current session, change password, concurrent-session enforcement, idle-session expiry, and force session revocation; `AuthController` exposes `/api/v1/auth/login`, `/api/v1/auth/logout`, `/api/v1/auth/me`, and `/api/v1/auth/change-password` with old `/api/auth/*` aliases preserved.
- Existing behavior: login validates username/password, returns raw opaque token only to the client, stores only token hash, records login history including unknown usernames, enforces password policy on change, locks after five failures for 30 minutes, auto-unlocks after expiry, and maps final auth error codes.
- Finalized requirement satisfied: `ApiContracts.md` Section 7 and `AcceptanceTests.md` `SEC-AUTH-001..007`, `SEC-SESSION-002..004` for the current Phase 1 foundation.

### Users

Classification: MODIFY

- Current implementation: `User` has username, display name, password hash, primary role, status, failed attempts, lock timestamp, last login, active flag, created/updated timestamps; `UserService` supports list/create/update.
- Problem: final `DatabaseDesign.md` requires `user_id`, `first_name`, `last_name`, `email`, `phone`, `is_locked`, `must_change_password`, `password_changed_at`, `version`, and case-normalized username uniqueness. Current schema uses `id`, `display_name`, enum `status`, and lacks optimistic locking.
- Required change: migrate users table and entity to final shape, split display name into required fields, add version and password lifecycle fields, preserve existing working auth behavior.
- Impact: User DTOs, JavaFX user screen, seeding, and tests need updates.

### Roles

Classification: MODIFY

- Current implementation after alignment: `Role` uses final `role_id BIGINT`, `role_name`, `description`, `is_system_role`, `is_active`, `version`, and an authoritative `role_permissions` collection. The API still adapts permissions to the existing map-based JavaFX role editor.
- Problem: system-role deletion is now protected, custom roles remain supported, and final permission codes are used; remaining gaps are final `/api/v1` paths, actor/reason capture, and fuller audit integration.
- Required change: align final API paths and route role lifecycle events through the common audit log once that service exists.
- Impact: Existing Role UI/API behavior is preserved while using normalized storage.

### Permissions

Classification: PASS

- Current implementation after alignment: permissions are normalized in `permissions` and `role_permissions`, seeded with the MVP permission registry and role matrix; `SUPER_ADMIN` receives all registered permissions.
- Existing behavior: server `@PreAuthorize` checks and JavaFX navigation now use final action permission codes; the JavaFX Role Management screen loads the registered permission catalog from the API when available.
- Finalized requirement satisfied: `DatabaseDesign.md`, `ApiContracts.md`, and `AcceptanceTests.md` RBAC model for normalized permission registration, role-permission assignment/removal, custom roles, and SUPER_ADMIN coverage.

### Primary roles

Classification: MODIFY

- Current implementation: `User.primaryRole` is mandatory and persisted.
- Problem: final design requires exactly one primary role with audit for changes; no primary-role change audit is currently visible in `UserService.updateUser`.
- Required change: keep mandatory primary role and add audit event for changes.
- Impact: Moderate; current concept is already correct.

### Temporary secondary roles

Classification: MODIFY

- Current implementation: `UserRole` maps `user_role_assignments`, stores user, role, granted/expires/revoked timestamps, active flag, actor fields, and grant/revoke APIs exist.
- Problem: primary-role grant/revoke guardrails and final permission codes now exist, but the API still needs final `/api/v1` paths plus explicit revoke reason/actor propagation from the authenticated session.
- Required change: record actor/reason from the authenticated session and align the final API path/DTO shape.
- Impact: Existing role-grant UI/service can be adapted.

### Secondary-role expiry

Classification: MODIFY

- Current implementation: `PermissionService` queries active grants as of `Instant.now()` and `UserRole.activeAt` also checks `expiresAt`.
- Problem: no scheduled expiry/audit job is visible; expiry audit is not recorded.
- Required change: keep runtime expiry check, add scheduler to mark expired assignments and write `SECONDARY_ROLE_EXPIRED`.
- Impact: Runtime authorization is already on the right path; audit/compliance gap remains.

### User lock/unlock

Classification: PASS

- Current implementation: failed login locks after five attempts for 30 minutes; valid login after `lockedUntil` has passed clears the lock; manual `/api/v1/users/{userId}/lock` and `/api/v1/users/{userId}/unlock` endpoints exist with `user.lock` and `user.unlock`.
- Existing behavior: service tests cover automatic lock, auto-unlock, and manual lock/unlock state changes.
- Finalized requirement satisfied: `SEC-AUTH-004`, `SEC-AUTH-005`, and `SEC-AUTH-006` foundation behavior. Manual and failed-login account lock/unlock actions now write common audit events.

### Failed login handling

Classification: PASS

- Current implementation: failed password increments attempts and records login history; unknown username is logged.
- Existing behavior: invalid credentials return `AUTH_INVALID_CREDENTIALS`; inactive users return `AUTH_ACCOUNT_INACTIVE`; locked users return `AUTH_ACCOUNT_LOCKED`; concurrent-session overflow returns `AUTH_CONCURRENT_SESSION_LIMIT`.
- Finalized requirement satisfied: stable auth error-code mapping for the implemented Phase 1 auth/session API.

### Login history

Classification: MODIFY

- Current implementation: `login_history` table and `LoginHistory` entity record success/failure, attempted username, IP, client ID, and reason.
- Problem: schema now uses `login_history_id BIGINT`, nullable `user_id`, `attempted_username`, `occurred_at`, and `ip_address INET`; login-history API `/api/v1/login-history` with `user.login_history.read` is not implemented.
- Required change: add API and permission enforcement for login-history reads.
- Impact: Existing data capture behavior is useful.

### Audit logging

Classification: MODIFY

- Current implementation: `audit_logs` now has the finalized extensible `action_code` shape with `entity_type`, `entity_id`, `actor_type`, `actor_user_id`, `occurred_at`, `details`, `changed_fields`, `ip_address`, `client_id`, and `correlation_id`; `AuditService` records correlation ID from MDC. Existing `role_change_audit` is preserved and bridged by continuing to write role-change entries while also writing general audit events.
- Existing behavior: login success/failure, account lock, logout/session revoke, password change, user create/update/lock/unlock, role create/update/delete, role permission updates, secondary-role grant/revoke, business profile update, and tax configuration update now route through the common audit service where implemented.
- Remaining problem: audit read/search APIs are not implemented, actor propagation is still mostly system/null for admin actions, and secondary-role expiry scheduler auditing remains pending.
- Required change: add read-only audit APIs with `audit.read`, propagate authenticated actor/client/IP consistently, and record scheduler-driven secondary-role expiry events.
- Impact: The common foundation is now usable for future Phase 1 and later-module audit events without a restrictive enum.

### Business profile

Classification: MODIFY

- Current implementation: `business_profile` table/entity use the single-row final table with `version`; DTOs expose version, stale updates return `CONCURRENT_MODIFICATION`, `/api/settings/business` and `/api/v1/settings/business` use `system.config`/`system.config.read`, JavaFX submits the loaded version, and updates write general audit records.
- Remaining problem: endpoint naming still needs final contract polish if `/api/v1/system/business-profile` is adopted as the canonical path, and several legacy DTO field names remain for UI compatibility.
- Required change: finalize route/DTO naming once the system-settings API contract is completed.
- Impact: Existing first-launch/UI behavior is preserved while stale-write protection is now active.

### Tax configuration

Classification: MODIFY

- Current implementation after alignment: `tax_configuration` is a single-row table seeded with VAT disabled and rate `18.0000`; `system_config` stores JSONB system settings.
- Problem: final single-row `tax_configuration` now exists and is seeded, but API `/api/v1/system/tax` is absent.
- Required change: add API/UI/audit for tax configuration.
- Impact: This should happen before sales/VAT work.

### First-launch onboarding

Classification: MODIFY

- Current implementation: if an authenticated admin-like user has old role permissions and no business profile exists, JavaFX opens `BusinessProfileView`; `DefaultIdentitySeeder` can create admin/manager/cashier if `bizco.initial-admin-password` is set.
- Problem: final onboarding is a wizard: business profile, tax configuration, admin password, confirmation; seed behavior should create default users with temporary passwords via onboarding logic, not Flyway hardcoding. Current approach is partial and environment-variable driven.
- Required change: implement first-launch wizard and backend setup state; align permissions and default user policy.
- Impact: Current profile prompt is reusable as one wizard step.

### API client

Classification: PASS

- Current implementation: JavaFX API clients use `/api/v1` for current Phase 1 surfaces, attach `Authorization: Bearer <opaque-session-token>`, send `X-Correlation-Id`, and parse the standard `ApiError` body through reusable client exceptions.
- Existing behavior: session-expired/unauthenticated errors map to `SessionExpiredException`, permission denials map to `PermissionDeniedException`, network/server failures map to `ServerUnavailableException`, and normal UI workflows remain functional.
- Finalized requirement satisfied: Step 6 API client alignment for current Phase 1 JavaFX calls. Critical-command idempotency remains a later posting-endpoint concern.

### Authorization header/token handling

Classification: PASS

- Current implementation: server reads `Authorization: Bearer ...`; JavaFX sends the raw opaque token returned by `/api/v1/auth/login`; server hashes tokens for lookup and stores only `token_hash`.
- Existing behavior: `/api/v1/auth/me` returns the current user/session/effective permissions; protected requests update `last_activity_at` and extend `expires_at` for the 15-minute idle window.
- Finalized requirement satisfied: `ApiContracts.md` Sections 3 and 7 plus `SEC-SESSION-001..004` foundation behavior.

### Permission-based JavaFX UI

Classification: MODIFY

- Current implementation: navigation items are hidden by final action permissions; Role Management loads registered permissions from the server and falls back to the final Phase 1 list.
- Problem: JavaFX does not refresh permissions after `/auth/me`, 403, grant/revoke, or expiry; placeholders exist for most modules.
- Required change: add permission refresh strategy and denied/error states after `/auth/me` and session APIs exist.
- Impact: Foundation is good; remaining work is session freshness and final API path cleanup.

### API error handling

Classification: PASS

- Current implementation: `ApiExceptionHandler`, `SecurityConfig`, and `SessionAuthenticationFilter` return the finalized `ApiError` shape with `code`, `message`, `details`, `fieldErrors`, `timestamp`, `path`, and `correlationId`.
- Existing behavior: validation/input failures return 400, unauthenticated/expired sessions return 401, permission denials return 403, missing resources return 404, stale versions return 409, and unexpected errors return 500 without stack traces or secrets. Correlation IDs are accepted/generated, echoed as `X-Correlation-Id`, added to MDC, and included in error responses.
- Finalized requirement satisfied: `API-002`, `API-003`, `API-004`, `API-005`, `API-007` foundation semantics for current Phase 1 APIs.

## 5. Conflicting With Finalized Design

### API paths

Classification: MODIFY

Current controllers use:

```text
/api/auth
/api/identity/users
/api/identity/roles
/api/settings/business
/api/health
```

Final API requires:

```text
/api/v1/auth
/api/v1/users
/api/v1/roles
/api/v1/system/business-profile
/api/v1/health
```

Impact: JavaFX clients and tests must be updated together with controllers.

### Permission names

Classification: PASS

Legacy names previously used:

```text
identity.user.read
identity.user.write
identity.role.read
identity.role.write
settings.business.read
settings.business.write
sales.pos.open
sales.invoice.write
```

Current code and seed data now use final action names such as:

```text
user.create
user.read
user.update
role.read
role.update
invoice.create
system.config
system.backup.restore
```

Impact: The old permission-name conflict is resolved for the current Phase 1 RBAC surface. Remaining permission-refresh gaps are tracked separately.

### Role storage model

Classification: MODIFY

Resolved at the database/JPA and behavior level for current Phase 1 RBAC: `roles.permissions JSONB` was removed as authoritative storage and replaced with normalized `permissions` plus `role_permissions`; controllers and JavaFX navigation now use final action permission codes. Remaining work is final API-path cleanup, common audit logging, and session permission refresh.

### User status schema

Classification: MODIFY

Resolved at the database/JPA foundation level: the old PostgreSQL `user_status` enum was removed from the rebuilt baseline. `users` now uses `is_locked`, `locked_until`, `is_active`, and related password/session lifecycle columns. Remaining behavior gaps are manual lock/unlock and final auth error-code mapping.

### Business/tax configuration schema

Classification: MODIFY

Resolved at the database/JPA foundation level: `business_profile`, `tax_configuration`, and `system_config` now use the finalized table shapes. API/client behavior is now aligned for current Phase 1 routes; remaining differences are DTO naming polish and tax configuration UI.

### Step 6 / Step 7 ordering

No blocking conflict was introduced by completing Step 7 before this Step 6 pass. The Step 7 optimistic-lock and audit work already used the finalized `ApiError` shape, correlation IDs, and 409 `CONCURRENT_MODIFICATION` semantics. This Step 6 pass resolved the ordering gap by adding `/api/v1` aliases/canonical client paths, reusable JavaFX error parsing, validation/not-found/security error handlers, and API acceptance coverage.

## 6. Missing Requirements

| Item | Classification | Evidence | Required change |
|---|---|---|---|
 Final `permissions` registry | PASS | `permissions` table is seeded in `V021`; code uses final action permission names | Maintain the registry as new Phase 1 surfaces are added |
 Final `role_permissions` junction | PASS | `role_permissions` is authoritative, mapped by JPA, and covered by assignment/removal tests | Add final `/api/v1` paths later |
 Role delete/protection API | PASS | Delete endpoint/service exists and rejects system roles | Route final audit event through common audit log later |
 Manual user lock/unlock | PASS | `/api/v1/users/{userId}/lock` and `/unlock` exist with permissions and tests | Route events through common audit service later |
 Reset password | MISSING | No reset-password endpoint | Add final reset flow with `mustChangePassword` |
 Force logout | PASS | `/api/v1/users/{userId}/sessions/revoke` exists with `user.session.revoke` and records `AUTH_SESSION_REVOKED` | Propagate acting admin user when auth context wiring is added |
 `/auth/me` | PASS | `/api/v1/auth/me` returns current session and effective permissions | Add JavaFX refresh workflow |
 Concurrent-session limit | PASS | `AuthService.login` enforces `bizco.security.max-concurrent-sessions` | Add configuration UI/documentation later |
 Idle timeout touch | PASS | `SessionAuthenticationFilter` updates `last_activity_at`/`expires_at` and rejects idle sessions | Add JavaFX expired-session UX |
 Login-history API | MISSING | Table exists, endpoint absent | Add `/api/v1/login-history` |
 General audit log | PASS | `audit_logs`, `AuditLog`, `AuditLogRepository`, and `AuditService` exist; security/user/role/business/tax events are routed through it | Add read-only audit API/search and authenticated actor propagation |
 Correlation IDs | PASS | `CorrelationIdFilter` and shared header constants exist; `AuditService` captures MDC correlation ID | Extend use into idempotency records later |
 Idempotency infrastructure | MISSING | No request key table/service/header support found | Add common idempotency contracts before posting commands |
 Optimistic locking/version fields | PASS | `User`, `Role`, `BusinessProfile`, and `TaxConfiguration` expose `@Version`; mutable update DTOs carry expected version; stale user/role/business profile writes return `CONCURRENT_MODIFICATION` | Continue adding `version` with each new mutable root |
 Final tax configuration API | MODIFY | `TaxConfiguration` entity/service/repository and `/api/settings/tax`, `/api/v1/settings/tax` endpoints exist with version and audit | Add JavaFX UI and finalize canonical route naming |
 Final system config API | MODIFY | `system_config` table exists, API absent | Add JSONB `system_config` API |
 Staff profiles | PASS | `staff_profiles` table exists in `V003` | Add service/API only when scheduling/job assignment reaches scope |
 Document sequences | PASS | `document_sequences` table exists in `V007` | Add allocation service before invoice/GRN posting |
 Backup records/restore records | MODIFY | Backup/restore history schema exists; early `pg_dump`/`pg_restore` spike and test proof now exist | Add full Week 16 service/UI, maintenance-mode guard, checksum persistence, and recovery rehearsal |
 Testcontainers clean install/rerun | PASS | `FlywayCleanInstallIT` runs PostgreSQL Testcontainer, Flyway, Spring context, JPA validation, and rerun check | Expand packaged install/recovery rehearsal later |
 OpenAPI/Swagger | MISSING | No springdoc dependency/config found | Add generated API docs during implementation |

## 7. Database/Flyway Differences

### Existing migration files

Classification: MODIFY

Selected strategy: clean baseline rebuild.

Reason: repository evidence shows the previous `V001` through `V005` migrations were an early Phase 1 development baseline only. The project is still pre-production, no production/shared migration history is documented, the default database is a local development database, and `DatabaseDesign.md` explicitly allows editing/rebuilding migrations before they have run outside disposable development databases. The old migration files were not blindly renamed; they were replaced with a new finalized Phase 1 foundation sequence. If any non-disposable database has already used the old migrations, that database must be backed up/recreated or a separate forward-migration branch must be prepared before use.

Existing files after alignment:

```text
bizco-server/src/main/resources/db/migration/V001__extensions.sql
bizco-server/src/main/resources/db/migration/V002__permissions_roles_and_users.sql
bizco-server/src/main/resources/db/migration/V003__sessions_login_history_and_staff_profiles.sql
bizco-server/src/main/resources/db/migration/V004__business_tax_and_system_configuration.sql
bizco-server/src/main/resources/db/migration/V007__document_sequences.sql
bizco-server/src/main/resources/db/migration/V017__audit_logs.sql
bizco-server/src/main/resources/db/migration/V018__backup_and_restore_history.sql
bizco-server/src/main/resources/db/migration/V021__seed_permissions_roles_uom_tax.sql
```

### Existing schema

Classification: PASS for Phase 1 foundation

Existing aligned schema includes:

```text
pgcrypto extension
btree_gist extension
pg_trgm extension
permissions(permission_code, module, action, description, created_at)
roles(role_id BIGINT, role_name, description, is_system_role, is_active, version, ...)
role_permissions(role_id, permission_code, assigned_at, assigned_by)
users(user_id UUID, username, password_hash, first_name, primary_role_id, is_active, is_locked, must_change_password, version, ...)
user_role_assignments(user_role_assignment_id UUID, user_id, role_id, granted_by, expires_at, is_active, revoked_at, revoked_by, revoke_reason)
user_sessions(session_id UUID, user_id, token_hash, client_id, ip_address INET, created_at, last_activity_at, expires_at, revoked_at, revoked_reason)
login_history(login_history_id BIGINT, nullable user_id, attempted_username, occurred_at, ip_address INET, client_id, success, failure_reason)
staff_profiles(user_id, is_technician, display_name, updated_at)
business_profile(business_profile_id = 1, business_name, tin_number, vat_registered, logo_path, version, ...)
tax_configuration(tax_configuration_id = 1, vat_enabled, vat_rate, changed_by, changed_at, version)
system_config(config_key, config_value JSONB, updated_by, version, ...)
document_sequences(sequence_key, prefix, sequence_date, next_value, version, ...)
audit_logs(audit_log_id, entity_type, entity_id, action_code, actor_type, actor_user_id, occurred_at, details JSONB, changed_fields JSONB, ip_address INET, client_id, correlation_id)
backup_records / restore_records
uom(uom_id, code, name, category, is_active)
```

### Required target schema

Classification: MODIFY

`DatabaseDesign.md` and `DevelopmentPlan.md` target the full MVP sequence:

```text
V001__extensions.sql
V002__permissions_roles_and_users.sql
V003__sessions_login_history_and_staff_profiles.sql
V004__business_tax_and_system_configuration.sql
V005__customers.sql
V006__catalog_categories_uom_products_services.sql
V007__document_sequences.sql
...
V021__seed_permissions_roles_uom_tax.sql
```

Phase 1 target tables include:

```text
permissions
roles
role_permissions
users
user_role_assignments
user_sessions
login_history
staff_profiles
business_profile
tax_configuration
system_config
document_sequences
audit_logs
backup_records / restore_records, at least for recovery-readiness planning
```

Current baseline status: Phase 1 foundation tables above are present. Full-MVP feature tables from `V005`, `V006`, `V008` through `V016`, `V019`, and `V020` are intentionally not implemented in this task because they include customer/catalog/sales/scheduling/inventory/purchasing/finance/reporting features outside the current instruction.

### Can the current development DB safely be recreated?

Classification: PASS, with condition

Based on repository evidence, the current schema appears to be early Phase 1 development data only. The old migration history has been rebuilt as a clean baseline, so any database that has already applied `V001__extensions_and_reference_types.sql` through `V005__business_profile_code_columns.sql` will not be upgrade-compatible by Flyway checksum/version alone.

If no real business or non-disposable stakeholder data has been entered, the current development DB can safely be dropped and recreated to match the finalized migration baseline.

Do not assume safety for any shared or real-data database. Before recreation:

1. Confirm it is a disposable development DB.
2. Take a backup if there is any doubt.
3. For any non-disposable database, stop and create forward migrations instead of using this rebuilt baseline.

## 8. RBAC/Security Differences

Classification: MODIFY

Current strengths:

- BCrypt cost 12.
- Opaque bearer token generated on login.
- Token hash persisted instead of raw token.
- Method-level `@PreAuthorize` exists on user/role/profile endpoints using final action permission codes.
- Effective permission calculation unions primary and active secondary roles.
- Failed login attempts and login history exist.
- Runtime secondary-role expiry is considered during permission calculation.
- Normalized permission registry and `role_permissions` now exist.
- Final MVP system roles are seeded: `SUPER_ADMIN`, `OWNER`, `MANAGER`, `ACCOUNTANT`, `CASHIER`, `STORE_KEEPER`, `SERVICE_OFFICER`, `AUDITOR`.
- `SUPER_ADMIN` receives every registered permission in the clean-install seed.
- System roles cannot be deleted through the role service/API.
- Primary roles cannot be granted or revoked through the secondary-role path.
- Custom roles and permission assignment/removal remain supported.
- Final auth/session API endpoints exist under `/api/v1/auth`.
- Login returns the finalized `data.sessionToken` response shape and stores only token hashes.
- 30-minute lockout, auto-unlock, password policy, 15-minute idle timeout, concurrent-session limit, `/auth/me`, logout, and force logout are implemented.

Current gaps:

- No reset-password endpoint.
- No general audit log for all security-sensitive actions.
- No TLS configuration evidence.
- No customer PII work yet, which is appropriate because Phase 2/customer must not start in this task.

Acceptance mapping:

- Covered for current Phase 1 RBAC foundation: `SEC-RBAC-001`, `SEC-RBAC-002`, `SEC-RBAC-003`, `SEC-RBAC-004`, `SEC-RBAC-005`, `SEC-RBAC-006`, `SEC-RBAC-007`, `SEC-RBAC-008`.
- Covered for current Phase 1 auth/session foundation: `SEC-AUTH-001`, `SEC-AUTH-002`, `SEC-AUTH-003`, `SEC-AUTH-004`, `SEC-AUTH-005`, `SEC-AUTH-006`, `SEC-AUTH-007`, `SEC-SESSION-001`, `SEC-SESSION-002`, `SEC-SESSION-003`, `SEC-SESSION-004`.
- Remaining security acceptance work: reset-password, final audit routing, TLS configuration evidence, and broader endpoint-by-endpoint negative permission matrix as feature APIs are added.

## 9. Session Differences

Classification: PASS

Current implementation:

- `UserSession` stores user, token hash, client ID, IP address, created time, last activity time, expiry time, revoked time, and revoked reason.
- Login creates a server-side opaque session with a 15-minute idle expiry and stores only the token hash.
- Logout revokes the matching session.
- Filter rejects expired/revoked sessions, touches active sessions, extends the idle expiry, and recalculates effective permissions for each request.
- Concurrent session limits are enforced by `AuthService.login` using `bizco.security.max-concurrent-sessions`.
- `/api/v1/auth/me` returns current effective permissions.
- `/api/v1/users/{userId}/sessions/revoke` supports force logout for users with `user.session.revoke`.

Final requirement status:

The finalized opaque server-side session foundation is aligned. Remaining session-adjacent work is JavaFX refresh/expired-session UX and routing session events through the common audit service.

## 10. API Contract Differences

Classification: MODIFY

Current implementation:

- Current Phase 1 auth, user, role, permission, business profile, tax configuration, and health endpoints are available under `/api/v1`; selected legacy aliases are preserved for working compatibility.
- Login returns the finalized `data.sessionToken` envelope; several non-auth endpoints still return direct DTOs.
- `ApiError` now has the final foundation fields: `code`, `message`, `details`, `fieldErrors`, `timestamp`, `path`, and `correlationId`.
- Auth/session/security/API failures now return stable final codes including `AUTH_INVALID_CREDENTIALS`, `AUTH_ACCOUNT_LOCKED`, `AUTH_ACCOUNT_INACTIVE`, `AUTH_CONCURRENT_SESSION_LIMIT`, `AUTH_SESSION_INVALID`, `AUTH_SESSION_EXPIRED`, `AUTH_PERMISSION_DENIED`, `VALIDATION_FAILED`, `RESOURCE_NOT_FOUND`, and `CONCURRENT_MODIFICATION`.
- Shared idempotency header constants and a JavaFX helper exist, but server-side idempotency persistence does not.
- Correlation ID request/response handling exists.
- No OpenAPI configuration found.

Final target:

- Base path `/api/v1`.
- `Authorization: Bearer <opaque-session-token>`.
- `X-Correlation-Id` request/response.
- `Idempotency-Key` for critical commands.
- Stable error codes and HTTP mapping.
- Mutable update DTOs include `version` for current Phase 1 mutable roots (`User`, `Role`, `BusinessProfile`, `TaxConfiguration`).
- Stale mutable-root updates return HTTP 409 with `CONCURRENT_MODIFICATION` through `IdentityException`.
- OpenAPI exposed during development.

Impact:

This should be aligned before building Phase 2 or later modules so client/server contracts do not churn repeatedly.

## 11. JavaFX Differences

Classification: MODIFY

Current implementation:

- Splash screen and health check.
- Login screen.
- Main shell with header/sidebar/footer.
- Server connection monitor every 30 seconds.
- Dashboard placeholders.
- User management, role management, and business profile views.
- Shared API client with bearer token.
- Permission-based navigation hiding.

Final Phase 1 target:

- Login/session handling, `/auth/me`, permission refresh, session-expiry UX.
- User management UI.
- Role/permission editor.
- Secondary role grant/revoke UI.
- First-launch onboarding wizard with business profile, tax configuration, and admin account flow.
- Common error/dialog/loading/empty states.
- Final permission codes.

Differences:

- Current UI uses final permission codes for the Phase 1 shell and role editor.
- It does not yet refresh permissions after role expiry/revocation or `AUTH_SESSION_EXPIRED`.
- It has a partial business-profile prompt, not the final onboarding wizard.
- It has placeholders for non-Phase-1 modules, which is acceptable as long as Phase 2/customer work does not start yet.
- It does not parse/display final structured API errors.

## 12. Test Gaps

Classification: MODIFY/MISSING

Current tests:

- `HealthResponseTest`
- `HealthEndpointSecurityTest`
- `SecurityConfigTest`
- `AuthControllerTest`
- `UserControllerSecurityTest`
- `SessionAuthenticationFilterTest`
- `AuthServiceTest`
- `PermissionServiceTest`
- `RoleServiceTest`
- `UserServiceTest`
- `PostgresToolPathResolverTest`
- `HealthControllerTest`
- `FlywayCleanInstallIT`
- `Phase1SecurityPostgresIT`
- `PgDumpRestoreSpikeIT`
- `PostgresSchemaIntegrityIT`
- `ApiErrorParserTest`
- `BizcoClientApplicationTest`

`mvn clean verify` result:

```text
Tests run: 79
Failures: 0
Errors: 0
Skipped: 0
Build: SUCCESS
```

Current acceptance coverage:

| Acceptance ID(s) | Gap |
|---|---|
 `SYS-INSTALL-001` | PASS - `FlywayCleanInstallIT` proves empty PostgreSQL migration, Spring context startup, and JPA validation |
 `SYS-INSTALL-002` | PASS - `FlywayCleanInstallIT` reruns Flyway on the existing schema and verifies the schema remains current |
 `SEC-AUTH-001..007` | PASS - service/controller tests plus PostgreSQL-backed `Phase1SecurityPostgresIT` cover successful login, invalid/unknown attempts, locking, auto-unlock, manual lock, and inactive users |
 `SEC-SESSION-001..004` | PASS - filter/service tests plus PostgreSQL-backed session tests cover idle timeout, logout revoke, concurrent-session limit, and force logout |
 `SEC-RBAC-001..008` | PASS - unit/service tests plus PostgreSQL-backed RBAC tests cover primary permissions, missing permissions, secondary union, expiry, revocation, primary-role protection, SUPER_ADMIN coverage, and system-role protection |
 PostgreSQL integrity checks | PASS - `PostgresSchemaIntegrityIT` covers username uniqueness, role/permission FK integrity, session token-hash uniqueness, active secondary-role uniqueness/expiry constraints, business-profile singleton, and tax constraints |
 Backup/restore technical spike | PASS - `PgDumpRestoreSpikeIT` proves custom-format `pg_dump`, restore into a clean temporary PostgreSQL database, Flyway version readability, key Phase 1 table readability, invalid backup handling, and missing destination failure detection |
 Permission negative-test matrix | A protected endpoint negative test exists; broaden endpoint-by-endpoint matrix as new APIs are added |
 `API-001` | Covered by `/api/v1/health` and current Phase 1 `/api/v1` controller/client paths |
 `API-002` | Covered by protected `/api/v1/users` without token returning 401 |
 `API-003` | Covered by protected `/api/v1/users` without `user.read` returning 403 |
 `API-004` | Covered by blank login validation returning `VALIDATION_FAILED` with `fieldErrors` |
 `API-005` | Covered by missing `/api/v1/users/{userId}` returning 404 `RESOURCE_NOT_FOUND` |
 `API-007` | Covered by correlation ID request/response/error tests |
 Security checklist | TLS deployment, backup/restore rehearsal, and release hardening checks remain open |

Testcontainers:

Server test dependencies now include Testcontainers PostgreSQL `1.21.4`. The reusable integration base starts `postgres:16-alpine`, applies Flyway `11.7.2` through version `021`, and uses Spring/Hibernate schema validation. The backup/restore spike uses PostgreSQL/`pg_dump`/`pg_restore` 16.14 from the same image. `mvn clean verify` is the authoritative Phase 1 integration command.

## 13. Refactoring Plan

Dependency order:

1. PASS - Freeze current work as early Phase 1 only; Phase 2/customer and downstream feature schemas were not started.
2. PASS - Select clean pre-production baseline rebuild strategy for disposable developer databases. Non-disposable databases require a separate forward-migration path.
3. PASS - Align current Phase 1 API base paths to `/api/v1` and update JavaFX clients/tests while preserving selected legacy aliases.
4. PASS - Introduce common API contracts: response/error model, validation errors, correlation ID filter, final auth/security/not-found/concurrency error mapping, and JavaFX structured error parsing.
5. PASS - Replace JSON permission storage with final `permissions` and `role_permissions` model.
6. PASS - Seed final MVP permission registry and role matrix: `SUPER_ADMIN`, `OWNER`, `MANAGER`, `ACCOUNTANT`, `CASHIER`, `STORE_KEEPER`, `SERVICE_OFFICER`, `AUDITOR`.
7. PASS - Update current server `@PreAuthorize` annotations and JavaFX visibility checks to final permission codes.
8. PASS - Users and sessions schema/entities now include final foundation fields; auth/session behavior includes idle touch, concurrent-session enforcement, and final password-policy checks for change password.
9. MODIFY - Manual lock/unlock, `/auth/me`, and force logout are implemented; remaining identity behavior work includes reset-password, login-history API, JavaFX permission refresh, and secondary-role revoke reason/actor propagation.
10. MODIFY - Add secondary-role expiry scheduler and audit event while keeping runtime expiry checks.
11. PASS - Replace `role_change_audit`-only behavior with common `audit_logs` infrastructure for security/config actions while preserving legacy role-change audit records.
12. MODIFY - Business profile and tax configuration now have schema/entity/service/API version/audit support; remaining work is final route naming and tax configuration UI.
13. PASS - Add `system_config`, `staff_profiles`, and `document_sequences` foundation tables before proceeding to later feature work.
14. MODIFY - Add idempotency infrastructure before any posting/backup/restore command endpoints. Shared header constants and a JavaFX idempotent POST helper now exist.
15. PASS - Backup/restore history tables exist and the early technical spike proves `pg_dump`/`pg_restore` feasibility; full Week 16 backup UI/service and release recovery rehearsal remain later work.
16. PASS - Add clean Flyway install and rerun tests mapped to `SYS-INSTALL-001` and `SYS-INSTALL-002`; PostgreSQL Testcontainers and Failsafe are configured for `mvn clean verify`.
17. PASS - PostgreSQL-backed Phase 1 tests now cover `SEC-AUTH-001..007`, `SEC-SESSION-001..004`, `SEC-RBAC-001..008`, current database integrity checks, and the early backup/restore spike.
18. NOT_APPLICABLE - Do not start Phase 2/customer management implementation during this alignment step.

Overall next move:

Phase 1 foundation reconciliation is conditionally complete. The architecture, database, RBAC, session, API contract, Testcontainers, and backup/restore spike gates are green; the remaining items above are tracked as non-blocking Phase 1 hardening or later-MVP work and should not prevent beginning Phase 2 customer management.

## 14. Final Reconciliation Status

Final status: CONDITIONAL PASS

`mvn clean verify` was run on 2026-08-09 and passed across the full Maven reactor. The test suite ran 79 tests with 0 failures, 0 errors, and 0 skips. PostgreSQL Testcontainers used `postgres:16-alpine`; Flyway reached schema version `021`.

| Verification item | Final status | Evidence |
|---|---|---|
| Maven modules match architecture | PASS | Parent reactor contains `bizco-common`, `bizco-server`, and `bizco-client` |
| JavaFX has no PostgreSQL/JPA access | PASS | Client/common dependency and source audit found no JDBC, PostgreSQL, JPA, repositories, or datasource usage |
| Flyway clean install succeeds | PASS | `FlywayCleanInstallIT` passes from clean PostgreSQL and validates rerun |
| Normalized RBAC model | PASS | `permissions`, `roles`, `role_permissions`, and `user_role_assignments` are present and tested |
| System roles and MVP permissions seeded | PASS | `V021__seed_permissions_roles_uom_tax.sql` seeds final roles/permissions |
| SUPER_ADMIN has all permissions | PASS | Flyway and RBAC integration tests assert no missing permission |
| Primary and secondary roles work | PASS | `PermissionService` unions primary role and active secondary grants |
| Expired secondary roles stop authorizing immediately | PASS | Runtime permission calculation evaluates `expiresAt`; PostgreSQL-backed test covers expiry |
| Opaque server-side sessions | PASS | Login returns raw opaque token and stores only hash |
| Raw tokens not stored in PostgreSQL | PASS | Session test verifies lookup by raw token fails and hash lookup succeeds |
| Account lockout works | PASS | Five failed attempts, manual lock, inactive account, and auto-unlock tests pass |
| Login history supports unknown usernames | PASS | `login_history.user_id` is nullable and unknown-attempt test passes |
| JavaFX login/session behavior | PASS | Client uses `/api/v1/auth/login`, bearer token headers, and structured error parsing |
| `/api/v1` use | PASS | JavaFX clients use `/api/v1`; server keeps selected legacy aliases only for transition |
| Standard API errors | PASS | Common `ApiError` and exception handling cover status/code/field/path/correlation shape |
| Correlation IDs | PASS | Filter accepts/generates/returns `X-Correlation-Id` and tests pass |
| Optimistic locking | PASS | `@Version` and stale-update conflict tests exist for Phase 1 mutable roots |
| Audit action codes/events | PASS | Audit uses extensible `actionCode`; Phase 1 security/config events are recorded |
| Business profile/tax configuration | MODIFY | Schema/entity/service/API/version/audit are aligned; tax UI and final system-config API remain open |
| PostgreSQL Testcontainers | PASS | `mvn clean verify` runs PostgreSQL-backed install/security/schema/backup tests |
| Backup/restore technical spike | PASS | `PgDumpRestoreSpikeIT` proves dump, restore, schema history, data readability, and failure cases |
| No MVP requirement removed | PASS | Remaining MVP work is tracked; no Phase 2 implementation was started |

Blocking items: none found for starting Phase 2.
