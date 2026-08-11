# Phase 2 Week 4 - Customer Management Implementation Report

## 1. Scope Implemented

Implemented the Week 4 customer vertical slice across PostgreSQL, Spring Boot, shared DTOs, JavaFX, and automated tests. The implementation covers customer CRUD/search, categories, status transitions, credit limits, credit aging policy, credit summary query port, AES-256-GCM encrypted NIC/BR storage, server-side PII masking/reveal, PII access audit, anonymization, optimistic locking, and server-side permission enforcement.

## 2. Files Added

- `bizco-common/src/main/java/com/bizco/common/dto/customer/CustomerDtos.java`
- `bizco-server/src/main/resources/db/migration/V005__customers.sql`
- `bizco-server/src/main/resources/db/migration/V022__customer_pii_permission.sql`
- `bizco-server/src/main/java/com/bizco/server/customer/**`
- `bizco-client/src/main/java/com/bizco/client/customer/**`
- `bizco-server/src/test/java/com/bizco/server/customer/**`

## 3. Files Modified

- `bizco-common/src/main/java/com/bizco/common/api/ApiErrorCode.java`
- `bizco-common/src/main/java/module-info.java`
- `bizco-client/src/main/java/com/bizco/client/BizcoClientApplication.java`
- `bizco-server/src/test/java/com/bizco/server/support/PostgresIntegrationTest.java`
- `bizco-server/src/test/java/com/bizco/server/system/FlywayCleanInstallIT.java`
- `bizco-server/src/test/java/com/bizco/server/system/PgDumpRestoreSpikeIT.java`

## 4. Database Migration

Created `V005__customers.sql` with the customer table, UUID PK, unique `customer_code`, category/status checks, non-negative credit-limit check, encrypted PII columns, timestamps, version, and trigram/code/status indexes.

Created `V022__customer_pii_permission.sql` to add `customer.view_pii` without rewriting existing shared migrations.

## 5. Customer Domain Model

Added `Customer` aggregate plus category/status/credit eligibility enums and `CustomerCreditPolicy`. Domain behavior includes profile update, credit limit change, block, activate, and anonymize.

## 6. REST API Implemented

- `GET /api/v1/customers`
- `POST /api/v1/customers`
- `GET /api/v1/customers/{customerId}`
- `PUT /api/v1/customers/{customerId}`
- `POST /api/v1/customers/{customerId}/block`
- `POST /api/v1/customers/{customerId}/activate`
- `GET /api/v1/customers/{customerId}/credit-summary`
- `POST /api/v1/customers/{customerId}/anonymize`

## 7. JavaFX UI Implemented

Added Customer Management screen with server-backed search, paging, filters, detail/form editing, credit summary display, block/activate/anonymize actions, masked/full PII display according to REST response, and permission-driven action state.

## 8. PII Encryption

NIC and BR number use application-layer AES/GCM/NoPadding with a required external `bizco.security.pii-key-base64` 256-bit key. The ciphertext stores a random 12-byte nonce plus authenticated ciphertext. The key is not stored in PostgreSQL or committed.

## 9. PII Permissions and Audit

Server masks PII unless the authenticated user has `customer.view_pii`. Revealing stored PII through detail read records `CUSTOMER_PII_REVEALED` without plaintext values.

## 10. Credit Policy

Implemented reusable aging/limit policy with outcomes `NORMAL`, `WARNING`, `CASH_ONLY`, `BLOCK_ALL`, and `LIMIT_EXCEEDED`. Credit summary uses `CustomerCreditQueryPort`; the current production adapter returns zero as explicit pre-Sales behavior.

## 11. Optimistic Locking

Customer uses JPA `@Version`; stale update requests return `CONCURRENT_MODIFICATION`.

## 12. Acceptance Test Results

- `CUS-CRUD-001`: PASS
- `CUS-VAL-001`: PASS
- `CUS-SEARCH-001`: PASS
- `CUS-PII-001`: PASS
- `CUS-PII-002`: PASS
- `CUS-ANON-001`: PASS

Covered by `CustomerPostgresIT`, `PiiEncryptionServiceTest`, `CustomerCreditPolicyTest`, and `CustomerControllerSecurityTest`.

## 13. Regression Test Results

`mvn clean verify`: PASS.

## 14. Known Issues

No Week 4 blockers found. JavaFX customer behavior is compile-tested and wired into the shell; no automated TestFX interaction test was added in this pass.

## 15. Deferred Dependencies

Real invoice-backed receivable balance, aging, and invoice history remain owned by later Sales/Finance phases. The Week 4 implementation exposes the required query port and does not store a fake mutable current balance.

## 16. Week 4 Readiness

PASS

## 17. Ready for Phase 2 Week 5?

YES. Customer Management is implemented and verified, and no Product/Catalog work was started beyond shared navigation wiring.

