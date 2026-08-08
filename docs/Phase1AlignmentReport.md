# Phase 1 Alignment Report

## 1. Executive Summary

This audit compares the existing Phase 1 implementation against the finalized source-of-truth documents:

- `docs/MVP.md` v1.3
- `docs/DomainModel.md`
- `docs/StateMachines.md`
- `docs/DatabaseDesign.md`
- `docs/ApiContracts.md`
- `docs/AcceptanceTests.md`
- `docs/DevelopmentPlan_v2.1.md`

The repository has a useful early Phase 1 foundation: a Maven parent with `bizco-common`, `bizco-server`, and `bizco-client`; Spring Boot and JavaFX modules; Flyway enabled; JPA validation; BCrypt cost 12; opaque bearer tokens stored as hashes; login/logout/change-password service code; login history; secondary role grants; effective permission union logic; a JavaFX login/shell; and permission-filtered JavaFX navigation.

However, the finalized documents materially changed the Phase 1 target. The current implementation still reflects the old Phase 1 design in several important places:

- API paths use `/api/...`, not finalized `/api/v1/...`.
- RBAC uses JSON role permissions and old permission names such as `identity.user.write`, not the finalized permission registry and `module.action` matrix.
- Flyway migrations are early development migrations `V001` through `V005`, not the finalized `V001` through `V021` sequence.
- Session persistence is fixed-duration token expiry, not full idle-session management with `last_activity_at`, concurrent-session enforcement, current-session refresh, force logout, and role-sensitive expiry behavior.
- Error responses do not yet match the finalized `ApiContracts.md` error contract with `fieldErrors`, `path`, and `correlationId`.
- Correlation ID and idempotency infrastructure are absent.
- Backup/restore is not implemented beyond the planning requirement.
- Testcontainers/PostgreSQL integration acceptance tests are not present.

Classification summary:

| Area | Status |
|---|---|
| Maven multi-module structure | PASS |
| Spring Boot configuration | MODIFY |
| JavaFX application structure | MODIFY |
| Spring Security | MODIFY |
| Core login/logout/change-password | MODIFY |
| BCrypt cost 12 | PASS |
| Users/roles/temporary role foundation | MODIFY |
| Final permission registry/RBAC matrix | MISSING |
| Session management final behavior | MODIFY |
| Login history | MODIFY |
| Audit logging | MODIFY |
| Business profile | MODIFY |
| Tax configuration | MODIFY |
| First-launch onboarding | MODIFY |
| API client and token header | MODIFY |
| Permission-based JavaFX UI | MODIFY |
| Flyway/PostgreSQL final schema | MODIFY |
| Testcontainers | MISSING |
| API error handling | MODIFY |
| Optimistic locking/version fields | MISSING |
| Correlation IDs | MISSING |
| Idempotency infrastructure | MISSING |
| Backup/restore readiness | MISSING |

No production source code was modified during this audit. This report is the only created file.

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
    src/main/resources/db/migration/V001..V005
    src/test/java/com/bizco/server/...
  bizco-client/
    pom.xml
    src/main/java/com/bizco/client/...
    src/main/resources/com/bizco/client/application.css
    src/test/java/com/bizco/client/...
  docs/
```

The Maven module names match `DevelopmentPlan_v2.1.md` Section 6. Package names use `com.bizco.server`, `com.bizco.client`, and `com.bizco.common`, which is compatible with the finalized plan. Several future capability packages exist as placeholders: `customer`, `catalog`, `sales`, `scheduling`, `inventory`, `purchasing`, `finance`, and `reporting`.

## 3. Already Compliant

### Maven multi-module structure

Classification: PASS

- Existing files/classes: root `pom.xml`, `bizco-common/pom.xml`, `bizco-server/pom.xml`, `bizco-client/pom.xml`.
- Existing behavior: Reactor builds common, server, and client modules; server depends on common; client depends on common.
- Finalized requirement satisfied: `DevelopmentPlan_v2.1.md` Section 6 requires `bizco-common`, `bizco-server`, and `bizco-client`.

### Java 21 baseline

Classification: PASS

- Existing files/classes: root `pom.xml`.
- Existing behavior: `java.version` is `21` and compiler release uses that value.
- Finalized requirement satisfied: `DevelopmentPlan_v2.1.md` Section 4 requires Java 21 LTS.

### Core Spring Boot server module

Classification: PASS

- Existing files/classes: `bizco-server/pom.xml`, `BizcoServerApplication`.
- Existing behavior: Spring Boot web, security, validation, JPA, actuator, Flyway, and PostgreSQL dependencies are present.
- Finalized requirement satisfied: Spring Boot REST server foundation in `DevelopmentPlan_v2.1.md` Sections 4 and 6.

### Flyway is enabled and JPA validates schema

Classification: PASS

- Existing files/classes: `bizco-server/src/main/resources/application.yml`.
- Existing behavior: Flyway is enabled at `classpath:db/migration`; Hibernate `ddl-auto` is `validate`; `open-in-view` is disabled.
- Finalized requirement satisfied: Database is migration-owned and JPA validates instead of auto-mutating schema, per `DatabaseDesign.md` and `DevelopmentPlan_v2.1.md`.

### BCrypt cost factor

Classification: PASS

- Existing files/classes: `SecurityConfig`.
- Existing behavior: `PasswordEncoder` is `new BCryptPasswordEncoder(12)`.
- Finalized requirement satisfied: BCrypt cost 12 in `MVP.md`, `DevelopmentPlan_v2.1.md`, and `AcceptanceTests.md` security checklist.

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
- Finalized requirement satisfied: `Architecture` rules in `DomainModel.md`, `DatabaseDesign.md`, and `DevelopmentPlan_v2.1.md`.

### Permission-based JavaFX navigation foundation

Classification: PASS

- Existing files/classes: `BizcoClientApplication`, `ClientSession`.
- Existing behavior: sidebar module visibility uses `session.hasPermission(...)`.
- Finalized requirement satisfied: JavaFX permission-based UX expectation in `MVP.md`, `StateMachines.md`, and `ApiContracts.md`, with the caveat that final permission names differ.

### Current tests pass

Classification: PASS

- Existing files/classes: tests under `bizco-common/src/test`, `bizco-server/src/test`, and `bizco-client/src/test`.
- Existing behavior: `mvn test` passes, with 22 tests run and no failures.
- Finalized requirement satisfied: basic Phase 1 unit/slice test health. This does not satisfy the finalized Testcontainers acceptance baseline.

## 4. Partially Compliant

### Spring Boot configuration

Classification: MODIFY

- Current implementation: `application.yml` configures datasource from environment, Flyway, JPA validation, actuator health/info, and log file output.
- Problem: API base path is not configured around `/api/v1`; TLS is not configured; correlation logging is not implemented; no explicit production profile/security hardening is visible.
- Required change: align controllers to `/api/v1`, add correlation ID filter/log MDC, document TLS/deployment configuration, keep JPA validation and Flyway.
- Impact: Existing configuration can be preserved, but all API clients/tests must move to final paths and headers.

### Spring Security

Classification: MODIFY

- Current implementation: `SecurityConfig` disables CSRF for REST, uses stateless sessions, permits `/api/health`, `/actuator/health`, and `/api/auth/login`, authenticates all other requests, and enables method security.
- Problem: paths are old; final contract requires `/api/v1/auth/login` and protected endpoints under `/api/v1`; auth failure responses do not use final error codes; no explicit concurrent-session enforcement.
- Required change: update endpoint matchers to final API paths, implement finalized 401/403 error contract, enforce concurrent-session and idle-session rules.
- Impact: Filter and token approach can remain, but path/security error behavior needs coordinated API/client/test updates.

### Login/logout/change-password

Classification: MODIFY

- Current implementation: `AuthService` implements login, logout, and change password; `AuthController` exposes `/api/auth/login`, `/api/auth/logout`, and `/api/auth/change-password`.
- Problem: final API paths are `/api/v1/auth/...`; login response is not wrapped as `data`; change password lacks visible password policy validation; changing password revokes sessions but does not set or clear `mustChangePassword`; lock duration is 15 minutes instead of the finalized 30-minute auto-unlock rule.
- Required change: align request/response DTOs, enforce password policy, add `must_change_password`, update lockout duration, return stable auth error codes.
- Impact: Existing service methods are useful but need final DTOs, state fields, and tests mapped to `SEC-AUTH-001..007` and `SEC-SESSION-002`.

### Users

Classification: MODIFY

- Current implementation: `User` has username, display name, password hash, primary role, status, failed attempts, lock timestamp, last login, active flag, created/updated timestamps; `UserService` supports list/create/update.
- Problem: final `DatabaseDesign.md` requires `user_id`, `first_name`, `last_name`, `email`, `phone`, `is_locked`, `must_change_password`, `password_changed_at`, `version`, and case-normalized username uniqueness. Current schema uses `id`, `display_name`, enum `status`, and lacks optimistic locking.
- Required change: migrate users table and entity to final shape, split display name into required fields, add version and password lifecycle fields, preserve existing working auth behavior.
- Impact: User DTOs, JavaFX user screen, seeding, and tests need updates.

### Roles

Classification: MODIFY

- Current implementation: `Role` uses UUID `id`, `code`, `name`, `permissions JSONB`, `is_system`, and `is_active`; `RoleService` supports list/create/update.
- Problem: final design requires `roles` with role IDs, protected system roles, canonical MVP role names, separate `permissions` registry, and `role_permissions` junction table. Current code also allows updating system roles without visible protection.
- Required change: introduce normalized permission registry and role-permission mapping, seed final MVP roles and matrix, protect system roles from deletion/unsafe edits.
- Impact: Role UI and API can be reused conceptually but must target the final role DTOs and permission list.

### Permissions

Classification: MODIFY

- Current implementation: permissions are JSON keys inside each role, using old codes like `identity.user.read`, `identity.user.write`, `settings.business.write`, `sales.pos.open`.
- Problem: final docs require all MVP `module.action` permissions, for example `user.create`, `role.read`, `invoice.create`, `system.backup.restore`; `SUPER_ADMIN` must receive all registered MVP permissions.
- Required change: create `permissions` and `role_permissions`, replace old permission constants in controllers/client navigation, seed the final matrix.
- Impact: This is the largest Phase 1 alignment change because security annotations, JavaFX visibility, seed data, and tests all depend on permission strings.

### Primary roles

Classification: MODIFY

- Current implementation: `User.primaryRole` is mandatory and persisted.
- Problem: final design requires exactly one primary role with audit for changes and final role IDs/names; no primary-role change audit is currently visible in `UserService.updateUser`.
- Required change: keep mandatory primary role, add audit event for changes, align schema/DTO names.
- Impact: Moderate; current concept is already correct.

### Temporary secondary roles

Classification: MODIFY

- Current implementation: `UserRole` stores user, role, granted/expires/revoked timestamps and grant/revoke APIs exist.
- Problem: final table is `user_role_assignments`, requires `is_active`, `revoked_by`, `revoke_reason`, `granted_by` not null, cannot duplicate primary role, partial unique active assignment, and endpoint paths under `/api/v1/users/{id}/roles`.
- Required change: migrate schema/entity, reject primary-role grants, record actor/reason, use final API shape and permission codes `user.grant_role`/`user.revoke_role`.
- Impact: Existing role-grant UI/service can be adapted.

### Secondary-role expiry

Classification: MODIFY

- Current implementation: `PermissionService` queries active grants as of `Instant.now()` and `UserRole.activeAt` also checks `expiresAt`.
- Problem: no scheduled expiry/audit job is visible; no `is_active` or explicit EXPIRED state; expiry audit is not recorded.
- Required change: keep runtime expiry check, add scheduler to mark expired assignments and write `SECONDARY_ROLE_EXPIRED`.
- Impact: Runtime authorization is already on the right path; audit/compliance gap remains.

### User lock/unlock

Classification: MODIFY

- Current implementation: failed login locks after five attempts through `recordFailedLogin`; auto-unlock is indirectly handled by `canAuthenticate` when `lockedUntil` is past.
- Problem: lock duration is 15 minutes, final rule is 30 minutes; no manual lock/unlock API was found; final permissions `user.lock` and `user.unlock` are not implemented.
- Required change: add manual lock/unlock endpoints/services/audit, use final duration, add tests `SEC-AUTH-004..006`.
- Impact: Current failed-login mechanism can be retained but corrected.

### Failed login handling

Classification: MODIFY

- Current implementation: failed password increments attempts and records login history; unknown username is logged.
- Problem: error code contract is generic `IDENTITY_ERROR`; lock reason and status mapping are not final; failed unknown-user handling does not necessarily return the final `AUTH_INVALID_CREDENTIALS`.
- Required change: map failures to stable auth codes without leaking account existence; keep login-history persistence.
- Impact: Mostly API/error/test alignment.

### Login history

Classification: MODIFY

- Current implementation: `login_history` table and `LoginHistory` entity record success/failure, attempted username, IP, client ID, and reason.
- Problem: final table uses `login_history_id BIGINT`, `attempted_username`, `occurred_at`, `ip_address INET`; login-history API `/api/v1/login-history` with `user.login_history.read` is not implemented.
- Required change: align schema names/types, add API and permission.
- Impact: Existing data capture behavior is useful.

### Audit logging

Classification: MODIFY

- Current implementation: `role_change_audit` records role creation/update and secondary grant/revoke.
- Problem: final design requires general `audit_logs` for security, approval, posting, backup/restore, config changes, and role events; current actor is mostly null and there is no correlation ID.
- Required change: introduce common audit service/table and route all security/config actions through it; retain role-change audit data or migrate it.
- Impact: Needed before further modules to avoid duplicating audit behavior.

### Business profile

Classification: MODIFY

- Current implementation: `business_profile` table, entity, service, controller, JavaFX view, and first-launch prompt when no profile exists.
- Problem: final schema is single-row `business_profile_id = 1` with `business_name`, address fields, `tin_number`, `vat_registered`, `logo_path`, `version`; current schema has UUID `id`, legal name, `vat_registration_number`, `br_number_ciphertext`, country/currency/timezone; controller uses old `/api/settings/business` and incorrect permissions `identity.role.read/write`.
- Required change: align schema/API to `/api/v1/system/business-profile`, use `system.config`/`system.config.read`, add optimistic versioning and audit.
- Impact: Existing UI and service can be reused after DTO/schema updates.

### Tax configuration

Classification: MODIFY

- Current implementation: `tax_config` table is seeded with VAT standard/zero/exempt rows; `system_settings` includes `tax.vat_enabled` and `tax.vat_rate`.
- Problem: final design uses single-row `tax_configuration` with `vat_enabled`, `vat_rate`, `changed_by`, `changed_at`, `version`; API `/api/v1/system/tax` is absent.
- Required change: replace or migrate old tax config to final single-row config and add API/UI/audit.
- Impact: This should happen before sales/VAT work.

### First-launch onboarding

Classification: MODIFY

- Current implementation: if an authenticated admin-like user has old role permissions and no business profile exists, JavaFX opens `BusinessProfileView`; `DefaultIdentitySeeder` can create admin/manager/cashier if `bizco.initial-admin-password` is set.
- Problem: final onboarding is a wizard: business profile, tax configuration, admin password, confirmation; seed behavior should create default users with temporary passwords via onboarding logic, not Flyway hardcoding. Current approach is partial and environment-variable driven.
- Required change: implement first-launch wizard and backend setup state; align permissions and default user policy.
- Impact: Current profile prompt is reusable as one wizard step.

### API client

Classification: MODIFY

- Current implementation: `ApiClient` supports JSON GET/POST/PUT/DELETE and attaches bearer token.
- Problem: no `/api/v1` convention, no response envelope handling, no `X-Correlation-Id`, no `Idempotency-Key`, no structured `ApiError` parsing, no 401/403 session refresh handling.
- Required change: update path base, add header infrastructure, parse final error contract, expose idempotency for critical commands.
- Impact: Best done before building more JavaFX modules.

### Authorization header/token handling

Classification: MODIFY

- Current implementation: server reads `Authorization: Bearer ...`; client sends it; raw token is hashed for lookup.
- Problem: no current-session endpoint `/api/v1/auth/me`; session token does not update idle activity; token hash length column is 128 and uses SHA-256 base64, which currently fits but should be reviewed against final `VARCHAR(255)`.
- Required change: add `/auth/me`, `last_activity_at` update/touch behavior, final table names and response DTO.
- Impact: Existing token flow can remain.

### Permission-based JavaFX UI

Classification: MODIFY

- Current implementation: navigation items are hidden by old permissions.
- Problem: final permission codes differ; JavaFX does not refresh permissions after `/auth/me`, 403, grant/revoke, or expiry; placeholders exist for most modules.
- Required change: switch to final permission codes, add permission refresh strategy and denied/error states.
- Impact: Foundation is good, but all permission strings need replacement.

### API error handling

Classification: MODIFY

- Current implementation: `ApiExceptionHandler` maps `IdentityException` to HTTP 400 with `ApiError(code="IDENTITY_ERROR", message, fieldViolations, timestamp)`.
- Problem: final contract requires `code`, `message`, `details`, `fieldErrors`, `timestamp`, `path`, and `correlationId`, with correct HTTP status mapping.
- Required change: create common exception hierarchy/handler and validation error mapping.
- Impact: Should be done before API surface expands.

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

Classification: MODIFY

Current code uses old names:

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

Final docs require codes such as:

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

Impact: Current RBAC works mechanically, but it authorizes the wrong final capability model.

### Role storage model

Classification: MODIFY

Current `roles.permissions JSONB` conflicts with the finalized normalized `permissions` plus `role_permissions` schema. It also lacks a canonical permission registry, which is required for `SUPER_ADMIN` coverage and API/JavaFX permission discovery.

### User status schema

Classification: MODIFY

Current `user_status` PostgreSQL enum (`ACTIVE`, `LOCKED`, `DISABLED`) conflicts with final guidance to prefer `VARCHAR + CHECK` for statuses and final user fields `is_locked`, `locked_until`, and `is_active`. This can be migrated without deleting existing migrations.

### Business/tax configuration schema

Classification: MODIFY

Current `business_profile`, `tax_config`, and `system_settings` are not the finalized single-row `business_profile`, `tax_configuration`, and `system_config` designs. Existing behavior should be preserved where compatible, but table shapes need alignment before Phase 2/customer or sales work starts.

## 6. Missing Requirements

| Item | Classification | Evidence | Required change |
|---|---|---|---|
 Final `permissions` registry | MISSING | No `permissions` table/entity/controller found | Add canonical permission table, seed all MVP permissions |
 Final `role_permissions` junction | MISSING | Role permissions are JSONB | Normalize role-permission assignments |
 Role delete/protection API | MISSING | No delete endpoint; system-role protection incomplete | Add final role lifecycle behavior |
 Manual user lock/unlock | MISSING | No `/lock` or `/unlock` endpoint | Add service/API/audit/tests |
 Reset password | MISSING | No reset-password endpoint | Add final reset flow with `mustChangePassword` |
 Force logout | MISSING | No user session revoke endpoint | Add `/users/{userId}/sessions/revoke` |
 `/auth/me` | MISSING | No current-session endpoint | Add current session/effective permission refresh |
 Concurrent-session limit | MISSING | No limit check in `AuthService.login` | Enforce configured max active sessions |
 Idle timeout touch | MISSING | `UserSession` has `issuedAt`/`expiresAt`, not `lastActivityAt` | Add idle-session tracking and update per protected request |
 Login-history API | MISSING | Table exists, endpoint absent | Add `/api/v1/login-history` |
 General audit log | MISSING | Only `role_change_audit` exists | Add `audit_logs` and common audit service |
 Correlation IDs | MISSING | No header/filter/MDC found | Add `X-Correlation-Id` request/response handling |
 Idempotency infrastructure | MISSING | No request key table/service/header support found | Add common idempotency contracts before posting commands |
 Optimistic locking/version fields | MISSING | No `@Version` usage found | Add `version` to mutable aggregates |
 Final tax configuration API | MISSING | No `/system/tax` endpoint | Add tax config service/API/UI |
 Final system config API | MISSING | `system_settings` table only | Add JSONB `system_config` and API |
 Staff profiles | MISSING | No `staff_profiles` table/entity | Add before scheduling/technician assignment |
 Document sequences | MISSING | No `document_sequences` migration/entity | Add before invoice/GRN numbering |
 Backup records/restore records | MISSING | No backup/restore schema/service | Add recovery spike/final infrastructure |
 Testcontainers | MISSING | No dependency or `PostgreSQLContainer` use found | Add PostgreSQL integration baseline |
 OpenAPI/Swagger | MISSING | No springdoc dependency/config found | Add generated API docs during implementation |

## 7. Database/Flyway Differences

### Existing migration files

Classification: MODIFY

Existing files:

```text
bizco-server/src/main/resources/db/migration/V001__extensions_and_reference_types.sql
bizco-server/src/main/resources/db/migration/V002__identity_and_business_profile.sql
bizco-server/src/main/resources/db/migration/V003__identity_sessions_and_login_history.sql
bizco-server/src/main/resources/db/migration/V004__reference_data_seed.sql
bizco-server/src/main/resources/db/migration/V005__business_profile_code_columns.sql
```

Per the user instruction, these should not be deleted now.

### Existing schema

Classification: MODIFY

Existing schema includes:

```text
pgcrypto extension
user_status PostgreSQL enum
roles(id UUID, code, name, permissions JSONB, is_system, is_active, ...)
users(id UUID, username, display_name, password_hash, primary_role_id, status, failed_login_attempts, locked_until, ...)
user_roles(id UUID, user_id, role_id, granted_at, expires_at, revoked_at, granted_by, revoked_by)
business_profile(id UUID, business_name, legal_name, vat_registration_number, br_number_ciphertext, ...)
user_sessions(id UUID, user_id, token_hash, client_id, issued_at, expires_at, revoked_at)
login_history(id UUID, user_id, username, client_id, ip_address, success, failure_reason, attempted_at)
role_change_audit(id UUID, user_id, role_id, grant_id, action, actor_user_id, details JSONB, created_at)
uom(id UUID, code, name, precision_scale, is_active, ...)
tax_config(id UUID, code, name, rate_percent, is_enabled, is_default, ...)
system_settings(key, value TEXT, description, updated_at)
```

### Required target schema

Classification: MODIFY

`DatabaseDesign.md` and `DevelopmentPlan_v2.1.md` target:

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

### Can the current development DB safely be recreated?

Classification: PASS, with condition

Based on repository evidence, the current schema appears to be early Phase 1 development data only. The docs and git status show finalized design files are newly added/modified and current migrations are old baseline files. If no real business or non-disposable stakeholder data has been entered, the current development DB can safely be dropped and recreated to match the finalized migration baseline.

Do not assume safety for any shared or real-data database. Before recreation:

1. Confirm it is a disposable development DB.
2. Take a backup if there is any doubt.
3. Preserve existing migrations in git history; do not delete them as part of this audit.

## 8. RBAC/Security Differences

Classification: MODIFY

Current strengths:

- BCrypt cost 12.
- Opaque bearer token generated on login.
- Token hash persisted instead of raw token.
- Method-level `@PreAuthorize` exists on user/role/profile endpoints.
- Effective permission calculation unions primary and active secondary roles.
- Failed login attempts and login history exist.
- Runtime secondary-role expiry is considered during permission calculation.

Current gaps:

- Permission codes do not match final MVP matrix.
- No normalized permission registry or `role_permissions`.
- No final `SUPER_ADMIN`, `OWNER`, `STORE_KEEPER`, `SERVICE_OFFICER`, `AUDITOR` seed set. Current seeds include `ADMIN`, `MANAGER`, `CASHIER`, `ACCOUNTANT`, `TECHNICIAN`.
- No manual lock/unlock/reset-password/session-revoke endpoints.
- No final password policy validation found.
- No general audit log for all security-sensitive actions.
- No final 401/403 error contract.
- No TLS configuration evidence.
- No customer PII work yet, which is appropriate because Phase 2/customer must not start in this task.

Acceptance mapping:

- Partially covered: `SEC-AUTH-001`, `SEC-AUTH-002`, `SEC-AUTH-003`, `SEC-AUTH-004`, `SEC-AUTH-005`, `SEC-SESSION-002`, `SEC-RBAC-003`, `SEC-RBAC-005`.
- Not covered or incomplete: `SEC-AUTH-006`, `SEC-AUTH-007`, `SEC-SESSION-001`, `SEC-SESSION-003`, `SEC-SESSION-004`, `SEC-RBAC-001`, `SEC-RBAC-002`, `SEC-RBAC-004`, `SEC-RBAC-006`, `SEC-RBAC-007`, `SEC-RBAC-008`.

## 9. Session Differences

Classification: MODIFY

Current implementation:

- `UserSession` stores user, token hash, client ID, issued time, expiry time, and revoked time.
- Login creates a session with 15-minute expiry.
- Logout revokes the matching session.
- Filter rejects expired/revoked sessions.

Final requirement:

- `user_sessions` includes `session_id`, `token_hash`, `client_id`, `ip_address`, `created_at`, `last_activity_at`, `expires_at`, `revoked_at`, and `revoked_reason`.
- 15-minute idle timeout.
- Maximum concurrent session rule.
- `TouchSession`, `RevokeSession`, `ForceLogoutUser`, and `ExpireIdleSession`.
- `/auth/me` returns current effective permissions.
- Permission checks must resolve current role state each request.

Required change:

Keep the opaque-token model but add idle activity tracking, concurrent-session enforcement, current-session endpoint, force logout, final table fields, and final tests.

## 10. API Contract Differences

Classification: MODIFY

Current implementation:

- Uses unversioned `/api` paths.
- Returns direct DTOs, not the recommended response envelope.
- `ApiError` has `fieldViolations`, not final `fieldErrors`, `details`, `path`, or `correlationId`.
- No idempotency header support.
- No correlation ID handling.
- No OpenAPI configuration found.

Final target:

- Base path `/api/v1`.
- `Authorization: Bearer <opaque-session-token>`.
- `X-Correlation-Id` request/response.
- `Idempotency-Key` for critical commands.
- Stable error codes and HTTP mapping.
- Mutable update DTOs include `version`.
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

- Current UI uses old permission codes.
- It does not refresh permissions after role expiry/revocation.
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
- `HealthControllerTest`
- `BizcoClientApplicationTest`

`mvn test` result:

```text
Tests run: 22
Failures: 0
Errors: 0
Skipped: 0
Build: SUCCESS
```

Missing finalized acceptance coverage:

| Acceptance ID(s) | Gap |
|---|---|
 `SYS-INSTALL-001`, `SYS-INSTALL-002` | No clean PostgreSQL/Testcontainers Flyway install test |
 `SEC-AUTH-001..007` | Only partial service/controller unit coverage; final error codes, inactive user, 30-minute lock, password policy incomplete |
 `SEC-SESSION-001` | No idle timeout test based on `last_activity_at` |
 `SEC-SESSION-003` | No concurrent-session limit test |
 `SEC-SESSION-004` | No force logout implementation/test |
 `SEC-RBAC-001..008` | Old permission names and JSON permissions prevent final RBAC acceptance |
 Permission negative-test matrix | Only a small user-controller security slice exists |
 `API-001` | `/api/v1` not implemented |
 `API-004`, `API-007` | Final validation/correlation error contract missing |
 `DB-002` | Case-normalized username uniqueness not tested against PostgreSQL |
 Security checklist | No TLS, raw-token, password-policy, concurrent session, or general audit release checks |

Testcontainers:

No `testcontainers` dependency or `PostgreSQLContainer` usage was found. This is MISSING relative to `AcceptanceTests.md` and `DevelopmentPlan_v2.1.md`.

## 13. Refactoring Plan

Dependency order:

1. MODIFY - Freeze current work as early Phase 1 only; do not delete existing migrations.
2. MODIFY - Decide whether the current development DB is disposable. If yes, recreate it from a new finalized baseline; if not, add forward migrations from current schema to final schema.
3. MODIFY - Align API base paths to `/api/v1` and update JavaFX clients/tests.
4. MODIFY - Introduce common API contracts: response/error model, validation errors, correlation ID filter, and final auth error mapping.
5. MODIFY - Replace JSON permission storage with final `permissions` and `role_permissions` model, or add migration path that preserves existing role data while normalizing.
6. MODIFY - Seed final MVP permission registry and role matrix: `SUPER_ADMIN`, `OWNER`, `MANAGER`, `ACCOUNTANT`, `CASHIER`, `STORE_KEEPER`, `SERVICE_OFFICER`, `AUDITOR`.
7. MODIFY - Update all server `@PreAuthorize` annotations and JavaFX visibility checks to final permission codes.
8. MODIFY - Align users and sessions schema/entities: final user fields, `@Version`, `must_change_password`, session `last_activity_at`, `revoked_reason`, IP address.
9. MISSING - Add manual lock/unlock, reset-password, `/auth/me`, force logout, login-history API, and secondary-role revoke reason/actor behavior.
10. MODIFY - Add secondary-role expiry scheduler and audit event while keeping runtime expiry checks.
11. MODIFY - Replace `role_change_audit`-only behavior with common `audit_logs` infrastructure for security/config actions.
12. MODIFY - Align business profile and tax configuration schema/API/UI with final `system.config` permissions.
13. MISSING - Add `system_config`, `staff_profiles`, and `document_sequences` foundation tables before proceeding to later Phase 1/Phase 3 work.
14. MISSING - Add idempotency infrastructure and client header support before any posting/backup/restore command endpoints.
15. MISSING - Add backup/restore technical-spike scaffolding and tables as required by Week 1 readiness, without building full final backup UI yet.
16. MISSING - Add Testcontainers dependency and a clean Flyway install test mapped to `SYS-INSTALL-001`.
17. MODIFY - Expand identity/session/RBAC tests to final `SEC-AUTH`, `SEC-SESSION`, `SEC-RBAC`, and API contract IDs.
18. NOT_APPLICABLE - Do not start Phase 2/customer management implementation during this alignment step.

Overall next move:

The safest next implementation step is a Phase 1 alignment branch focused on database/API/security foundation only. Preserve working login/RBAC concepts, but migrate the schema, permission vocabulary, API paths, session model, and tests to the finalized documents before adding any Phase 2 customer functionality.
