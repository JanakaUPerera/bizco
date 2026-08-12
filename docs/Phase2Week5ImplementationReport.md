# Phase 2 Week 5 - Catalog, Services, Suppliers & Barcode Implementation Report

## 1. Scope Implemented

Implemented the Week 5 master-data vertical slice across PostgreSQL, Spring Boot, shared DTOs, JavaFX, and PostgreSQL/Testcontainers tests. The slice covers product categories and hierarchy validation, UOM reference usage, product CRUD/search/barcode lookup, SKU/barcode uniqueness, inventory vs service product typing, tax category and price fields, service catalog CRUD, supplier master CRUD, supplier activation/deactivation, supplier opening balance, barcode image generation, authorization, audit logging, optimistic locking, and regression verification.

## 2. Files Added

- `bizco-common/src/main/java/com/bizco/common/dto/catalog/CatalogDtos.java`
- `bizco-common/src/main/java/com/bizco/common/dto/purchasing/SupplierDtos.java`
- `bizco-server/src/main/resources/db/migration/V023__catalog_services_suppliers.sql`
- `bizco-server/src/main/java/com/bizco/server/catalog/**`
- `bizco-server/src/main/java/com/bizco/server/purchasing/**/Supplier*.java`
- `bizco-client/src/main/java/com/bizco/client/catalog/**`
- `bizco-client/src/main/java/com/bizco/client/purchasing/service/SupplierApiClient.java`
- `bizco-server/src/test/java/com/bizco/server/catalog/CatalogWeek5PostgresIT.java`
- `bizco-server/src/test/java/com/bizco/server/purchasing/SupplierWeek5PostgresIT.java`

## 3. Files Modified

- `bizco-common/src/main/java/com/bizco/common/api/ApiErrorCode.java`
- `bizco-common/src/main/java/module-info.java`
- `bizco-server/pom.xml`
- `bizco-client/src/main/java/com/bizco/client/BizcoClientApplication.java`
- `bizco-server/src/test/java/com/bizco/server/system/FlywayCleanInstallIT.java`
- `bizco-server/src/test/java/com/bizco/server/system/PgDumpRestoreSpikeIT.java`

## 4. Flyway Migration

Created `V023__catalog_services_suppliers.sql` as a forward migration because existing shared migrations already include `V021` and `V022`. It creates `product_categories`, `products`, `service_definitions`, and `suppliers`, adds PostgreSQL indexes including trigram product/service search and barcode/SKU lookups, and adds missing `product.view_cost` plus supplier permissions.

## 5. Product Category Implementation

Implemented category create/update/list with parent support, active flag, timestamps, version, audit, and application-level cycle prevention. Self-parent is also protected by the database check constraint.

## 6. UOM Implementation

Reused the existing `uom` table and seed data from `V021`. Week 5 exposes `GET /api/v1/uom` and verifies the required PCS, KG, LTR, BOX, DOZ, BTL, CASE, and MTR seed rows.

## 7. Product Domain and Persistence

Added product entity, repository, service validation, optimistic locking, product types `INVENTORY` and `SERVICE`, tax categories `STANDARD`, `EXEMPT`, `ZERO_RATED`, retail/wholesale/cost pricing, reorder point, activation/deactivation, and no stock side effects during product creation.

## 8. Product REST API

Implemented:

- `GET /api/v1/products`
- `POST /api/v1/products`
- `GET /api/v1/products/{productId}`
- `PUT /api/v1/products/{productId}`
- `POST /api/v1/products/{productId}/deactivate`
- `POST /api/v1/products/{productId}/activate`
- `GET /api/v1/products/{productId}/price`
- `GET /api/v1/products/barcode/{barcode}`

## 9. Product Search

Search is repository/database backed with filters for `q`, `categoryId`, `type`, `active`, `page`, and `size`. The DTO leaves pre-inventory stock fields as `null` rather than encoding fake stock truth.

## 10. Barcode Uniqueness and Lookup

Barcode is optional, unique when supplied, and exact lookup is implemented through `findByBarcode`. PostgreSQL permits multiple null barcodes and rejects duplicates through `uq_products_barcode`.

## 11. Barcode Generation

Added ZXing dependencies and `BarcodeImageService` for CODE-128 PNG generation via `GET /api/v1/barcodes/code128/{value}`. No filesystem path is exposed.

## 12. Pricing Fields and Cost Visibility

Products store `costPrice`, `sellingPrice`, and `wholesalePrice`. Server DTO mapping omits raw `costPrice` unless the authenticated user has `product.view_cost`. A simple price-resolution endpoint supports later retail/wholesale sales defaults without implementing invoice calculations.

## 13. Service Catalog

Implemented service definition persistence and API with service code uniqueness, base price, duration, estimate-required flag, warranty days, activation/deactivation, optimistic locking, and audit.

## 14. Supplier Master

Implemented supplier master persistence and API with unique supplier code, optional concurrency-safe generated supplier code using existing `document_sequences`, contact fields, payment terms, opening balance, `ACTIVE/INACTIVE` status, activation/deactivation, optimistic locking, and audit. GRN, supplier returns, payments, and payable balance logic were not started.

## 15. JavaFX Product UI

Added Master Data screen product tab with search, filters, server paging, fields for SKU/barcode/name/category/UOM/type/tax/prices/reorder/status, create/update, activate/deactivate, and permission-driven cost/action behavior.

## 16. JavaFX Category UI

Added category tab with category list, create/update, parent selection, description, active state, and server-side cycle validation.

## 17. JavaFX Service UI

Added service tab with list/search, service code, name, category, base price, estimated duration, estimate requirement, warranty days, and activate/deactivate actions.

## 18. JavaFX Supplier UI

Added supplier tab with search/list, create/update, contact fields, opening balance, and activate/deactivate actions.

## 19. Optimistic Locking

Products, categories, services, and suppliers use JPA `@Version`. Stale updates return `CONCURRENT_MODIFICATION`.

## 20. Authorization and Audit

All REST endpoints use server-side `@PreAuthorize`. JavaFX hides/disables unavailable actions based on `/api/v1/auth/me` effective permissions. Audit records are written for create/update/activation/deactivation and product price changes without storing sensitive values.

## 21. Acceptance Test Results

- `CAT-PROD-001`: PASS
- `CAT-PROD-002`: PASS
- `CAT-PROD-003`: PASS
- `CAT-PROD-004`: PASS
- `CAT-PROD-005`: PASS as catalog-level/pre-inventory behavior
- `CAT-SVC-001`: PASS
- `CAT-CAT-001`: PASS through DB constraint/application validation coverage
- `CAT-CAT-002`: PASS
- `DB-001`: PASS

Additional tests cover product price validation, reorder point validation, service optimistic locking, supplier duplicate code, supplier opening balance validation, supplier activation/deactivation, supplier optimistic locking, UOM seed data, required indexes, and cost visibility.

## 22. Regression Test Results

`mvn clean verify`: PASS.

Observed totals: `bizco-common` 3 tests, `bizco-server` 30 tests, `bizco-client` 3 tests. Existing Phase 1 and Week 4 tests passed. Non-blocking warnings remain for Mockito dynamic agent behavior and one JavaFX unchecked generic warning.

## 23. Known Issues

No Week 5 blockers found. JavaFX screens are compile-verified and wired into the shell; automated TestFX interaction coverage was not added in this pass.

## 24. Deferred Dependencies

- Real physical/reserved/available stock quantities from Inventory.
- Stock movements and low-stock truth from the inventory ledger.
- GRN, supplier returns, supplier payments, and payable derivation.
- Invoice pricing transaction logic and manual overrides.
- Appointments/jobs selecting service definitions.

## 25. Week 5 Readiness

PASS

## 26. Ready for Phase 3 Week 6?

YES. Week 5 master data is implemented and regression-tested. Phase 3 can begin invoice draft/POS work using the new product/service/customer APIs without direct database access from JavaFX.
