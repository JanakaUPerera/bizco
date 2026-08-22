# Bizco MVP Acceptance Test Catalogue

**Project:** SME Business Management System (Bizco)  
**Document:** Acceptance Tests  
**Version:** 1.0  
**Status:** Pre-implementation acceptance baseline  
**Based On:** `SRS.md` v2.1, `MVP.md` v1.3, `DevelopmentPlan.md` v2.0, `DomainModel.md` v1.0, `StateMachines.md` v1.0, `DatabaseDesign.md` v1.0, `ApiContracts.md` v1.0  
**Target:** JavaFX client + Spring Boot server + PostgreSQL  
**Scope Rule:** This catalogue preserves the complete MVP scope. No MVP requirement is removed or deferred.

---

# 1. Purpose

This document defines the acceptance-test baseline for Bizco MVP.

It translates the approved:

- MVP business requirements;
- business invariants;
- aggregate state machines;
- PostgreSQL integrity rules;
- REST API contracts;
- SME operating scenarios;

into executable acceptance criteria.

The goal is to make every critical MVP behavior demonstrably correct before release.

---

# 2. Test Strategy

Bizco acceptance testing uses four levels.

```text
Level 1 — Domain/unit tests
        ↓
Level 2 — PostgreSQL integration tests
        ↓
Level 3 — REST/API acceptance tests
        ↓
Level 4 — JavaFX end-to-end/UAT scenarios
```

## 2.1 Domain / Unit Tests

Use for:

- pricing;
- VAT;
- discounts;
- credit eligibility;
- state transitions;
- document-number formatting;
- value objects;
- validation rules.

No database required.

## 2.2 PostgreSQL Integration Tests

Use:

```text
JUnit 5
Spring Boot Test
Testcontainers PostgreSQL
Flyway
```

Required for:

- transactions;
- row locking;
- exclusion constraints;
- unique constraints;
- idempotency;
- stock reconciliation;
- payment allocation;
- document numbering;
- audit persistence.

H2 must not be used as a substitute for acceptance tests that depend on PostgreSQL behavior.

## 2.3 API Acceptance Tests

Use Spring Boot integration testing against PostgreSQL Testcontainers.

Test:

- authentication;
- permissions;
- DTO validation;
- state commands;
- HTTP codes;
- error codes;
- idempotency;
- optimistic locking;
- API reconciliation.

## 2.4 JavaFX UAT

Validate:

- navigation;
- permission-driven visibility;
- user-friendly validation;
- POS workflow;
- calendar behavior;
- printing/export;
- recovery/error messages;
- LAN operation.

JavaFX UAT does not replace server acceptance tests.

---

# 3. Test ID Convention

Format:

```text
<DOMAIN>-<AREA>-<NNN>
```

Examples:

```text
SEC-AUTH-001
SALE-POST-001
STK-LEDGER-001
SCH-CONFLICT-001
PUR-GRN-001
FIN-AR-001
SYS-BACKUP-001
```

## 3.1 Priority

```text
P0 = release blocker / financial/security/integrity
P1 = major MVP behavior
P2 = usability/reporting edge
```

---

# 4. Release Gate

The MVP is releasable only when:

```text
All P0 tests PASS
All P1 tests PASS
No unresolved data corruption defect
No unresolved authorization defect
No stock/finance reconciliation defect
Backup and clean restore PASS
5+ simultaneous POS/client acceptance PASS
10-user engineering concurrency test PASS
```

A failed release-gate test must not be waived merely because the UI appears functional.

---

# 5. Test Environment

## 5.1 Integration Environment

```text
Java 21 LTS
Spring Boot
PostgreSQL Testcontainer
Flyway latest project migrations
Maven Surefire/Failsafe
JUnit 5
```

## 5.2 Release/UAT Environment

At minimum:

```text
1 Spring Boot server
1 PostgreSQL instance
5 JavaFX clients
LAN connectivity
thermal/A4 PDF output capability
barcode scanner simulation or hardware
backup storage location
```

## 5.3 Data Reset

Each integration suite should start from:

```text
clean PostgreSQL
→ Flyway migrations
→ seed reference data
→ deterministic test fixtures
```

No test should depend on execution order unless explicitly a scenario sequence.

---

# 6. Canonical Seed Data

Use stable seeded test identities.

## Users

```text
superadmin
owner
manager
accountant
cashier
storekeeper
serviceofficer
auditor
```

## Products

```text
P-A
SKU: PROD-A
Inventory
Stock initially 10
Cost: 800.00
Retail: 1200.00
Wholesale: 1050.00
VAT STANDARD

P-B
Inventory
Stock initially 5
Cost: 2000.00
Retail: 2500.00
VAT EXEMPT

P-C
Inventory
Stock initially 100
Reorder point: 20

SVC-A
Service
Price: 5000.00
Requires estimate
Duration: 60 minutes
Warranty: 30 days
```

## Customers

```text
CASH-CUST
normal retail customer
credit limit 0

CREDIT-GOOD
credit limit 100000
no overdue debt

CREDIT-WARN
31–60 day overdue balance

CREDIT-CASHONLY
61–90 day overdue balance

CREDIT-BLOCKED
91+ day overdue balance
```

## Supplier

```text
SUP-A
Opening balance 0
```

---

# 7. Authentication & Session Acceptance Tests

## SEC-AUTH-001 — Successful Login

**Priority:** P0

```gherkin
Given an active unlocked user with a valid password
When the user submits valid login credentials
Then authentication succeeds
And an opaque session token is returned
And the raw token is not persisted in PostgreSQL
And effective permissions are returned
And successful login history is recorded
```

## SEC-AUTH-002 — Invalid Password

```gherkin
Given an active user
When an incorrect password is submitted
Then login fails with AUTH_INVALID_CREDENTIALS
And failed login attempts increase by one
And login history records success = false
```

## SEC-AUTH-003 — Unknown Username

```gherkin
Given no user exists for username "unknown"
When login is attempted
Then login fails
And login_history stores attempted_username = "unknown"
And user_id is null
```

## SEC-AUTH-004 — Fifth Failure Locks Account

**Priority:** P0

```gherkin
Given a user has four consecutive failed login attempts
When a fifth invalid password is submitted
Then the account becomes locked
And lockedUntil is set according to MVP policy
And subsequent valid-password login is denied while locked
```

## SEC-AUTH-005 — Automatic Unlock

```gherkin
Given an account is locked with lockedUntil in the past
When valid credentials are submitted
Then the expired lock no longer blocks authentication
And login succeeds if all other rules pass
```

## SEC-AUTH-006 — Manual Lock

```gherkin
Given a manager/admin with user.lock permission
When the target user is locked
Then the target cannot authenticate
And an audit event is recorded
```

## SEC-AUTH-007 — Inactive User

```gherkin
Given a user is inactive
When valid credentials are submitted
Then login is rejected with AUTH_ACCOUNT_INACTIVE
```

## SEC-SESSION-001 — Idle Timeout

**Priority:** P0

```gherkin
Given a valid session has had no activity for 15 minutes
When the client calls a protected endpoint
Then the request is rejected with AUTH_SESSION_EXPIRED
```

## SEC-SESSION-002 — Logout Revokes Session

```gherkin
Given a valid active session
When logout succeeds
Then the session is revoked
And the same token can no longer call protected APIs
```

## SEC-SESSION-003 — Concurrent Session Limit

```gherkin
Given a user has reached the configured concurrent session limit
When another login is attempted
Then the server enforces the MVP concurrent-session rule
And no unauthorized extra active session is created
```

## SEC-SESSION-004 — Force Logout

```gherkin
Given an administrator has session revoke permission
When active sessions of another user are revoked
Then the target user's next protected API request fails authentication
```

---

# 8. RBAC Acceptance Tests

## SEC-RBAC-001 — Primary Role Permission

```gherkin
Given a cashier's primary role includes invoice.create
When the cashier posts an allowed sale
Then authorization succeeds
```

## SEC-RBAC-002 — Missing Permission

**Priority:** P0

```gherkin
Given a cashier does not have invoice.void
When the cashier calls invoice void
Then the server returns 403 AUTH_PERMISSION_DENIED
And no invoice data changes
```

## SEC-RBAC-003 — Secondary Role Union

```gherkin
Given a cashier has an active temporary Manager secondary role
When effective permissions are calculated
Then permissions equal primary role permissions union active secondary role permissions
```

## SEC-RBAC-004 — Secondary Role Expiry Is Immediate

**Priority:** P0

```gherkin
Given a user remains logged in
And a secondary role expires now
When the user performs a protected manager action
Then the server denies the action
Even if a cleanup scheduler has not yet marked the assignment inactive
```

## SEC-RBAC-005 — Revoked Secondary Role

```gherkin
Given a secondary role is active
When it is revoked
Then it ceases to authorize the next protected action
```

## SEC-RBAC-006 — Cannot Revoke Primary Role as Secondary

```gherkin
Given a user has MANAGER as primary role
When an API request attempts to revoke MANAGER via the secondary-role endpoint
Then the request is rejected
```

## SEC-RBAC-007 — SUPER_ADMIN Coverage

**Priority:** P0

```gherkin
Given all MVP permissions are seeded
When SUPER_ADMIN effective permissions are resolved
Then every registered MVP permission is present
```

## SEC-RBAC-008 — System Role Protection

```gherkin
Given a role is a protected system role
When delete is requested
Then deletion is rejected
```

---

# 9. Customer Acceptance Tests

## CUS-CRUD-001 — Create Customer

```gherkin
Given valid customer details
When a permitted user creates a customer
Then a unique customer code is generated
And the customer is ACTIVE
```

## CUS-VAL-001 — Negative Credit Limit

```gherkin
When a customer is created with a negative credit limit
Then validation fails
And no customer is persisted
```

## CUS-SEARCH-001 — Search

```gherkin
Given customers exist
When search is performed by name, phone, or code
Then matching customers are returned with pagination
```

## CUS-PII-001 — Restricted PII

**Priority:** P0

```gherkin
Given a user lacks sensitive customer data permission
When customer detail is requested
Then protected PII is masked or omitted
And no decrypted NIC/BR is exposed
```

## CUS-PII-002 — Encryption at Rest

**Priority:** P0

```gherkin
Given a customer NIC/BR is saved
When database rows are inspected
Then plaintext NIC/BR is not stored in customer fields
```

## CUS-ANON-001 — Anonymization Preserves History

```gherkin
Given a customer has posted invoices
When eligible anonymization is executed
Then customer master PII is anonymized
And historical posted transaction snapshots remain intact
```

---

# 10. Catalog Acceptance Tests

## CAT-PROD-001 — Create Inventory Product

```gherkin
Given valid product/category/UOM
When product is created
Then SKU is unique
And barcode is unique if present
And no stock movement is created merely by product creation
```

## CAT-PROD-002 — Duplicate SKU

```gherkin
Given SKU PROD-A already exists
When another product uses PROD-A
Then the server returns PRODUCT_SKU_DUPLICATE
```

## CAT-PROD-003 — Duplicate Barcode

**Priority:** P0

```gherkin
Given a barcode is assigned to Product A
When Product B is created with the same barcode
Then PostgreSQL uniqueness prevents the duplicate
And the API returns PRODUCT_BARCODE_DUPLICATE
```

## CAT-PROD-004 — Multiple Null Barcodes

```gherkin
Given multiple products have no barcode
When they are created
Then all are accepted
```

## CAT-PROD-005 — Service Product Does Not Affect Inventory

```gherkin
Given a catalog item is SERVICE type
When it is sold
Then no physical stock movement is created for that service line
```

## CAT-SVC-001 — Service Validation

```gherkin
When a service is created with duration <= 0
Then validation fails
```

## CAT-CAT-001 — Category Self Parent

```gherkin
When a category is assigned itself as parent
Then persistence is rejected
```

## CAT-CAT-002 — Circular Hierarchy

```gherkin
Given category A is parent of B
When A is changed to have B as parent
Then the application rejects the cycle
```

## CAT-BRAND-001 — Duplicate Brand Name Rejected

**Priority:** P0

```gherkin
Given a brand "Acme" already exists
When another brand is created with the name "Acme"
Then the API returns BRAND_NAME_DUPLICATE
```

## CAT-BRAND-002 — Product Brand Is Optional

```gherkin
Given a valid product/category/UOM with no brand selected
When the product is created
Then the product is accepted with a null brand
And the product remains valid when later assigned a brand
```

## CAT-ATTR-001 — Attribute Values Restricted To ENUM Type

**Priority:** P0

```gherkin
Given an attribute with data_type TEXT, NUMBER, or BOOLEAN
When an attribute value is added to it
Then the API rejects the request
And attribute values are only accepted for an ENUM-type attribute
```

## CAT-ATTR-002 — Category Attribute Duplicate Assignment Rejected

**Priority:** P0

```gherkin
Given attribute "RAM" is already assigned to category "Phones"
When "RAM" is assigned to "Phones" again
Then the API returns CATEGORY_ATTRIBUTE_DUPLICATE
```

## CAT-ATTR-003 — Required Category Attribute Reflected In Rendered Form

```gherkin
Given category "Phones" has attribute "RAM" assigned as required
When the category-driven attribute form is rendered for "Phones"
Then a control for "RAM" appears in the form
And it is marked required
```

## VAR-SCHEMA-001 — Every Product Has Exactly One Default Variant

**Priority:** P0

```gherkin
When a product is created
Then a product_variants row is created for it with is_default = true
And its sku/selling price mirror the product's own sku/selling price
And no second default variant can be created for the same product
```

## VAR-SCHEMA-002 — Default Variant Sync Stops Once A Second Variant Exists

```gherkin
Given a product has only its default variant
When the product's price is updated
Then the default variant's price updates to match
Given a second variant is then generated for the product
When the product's price is updated again
Then the default variant's price no longer changes
```

## VAR-BACKFILL-001 — Attribute-Driven Variant Generation Produces The Cartesian Combination

```gherkin
Given category "Apparel" has ENUM attributes "Color" (Red, Blue) and "Size" (S, M) assigned
When variants are generated for a product in "Apparel" selecting both colors and both sizes
Then 4 variants are created, one per Color × Size combination
And each variant's label joins its selected values (e.g. "Red / S")
```

## VAR-BACKFILL-002 — Non-ENUM Or Unassigned Attributes Cannot Be Used For Generation

**Priority:** P0

```gherkin
Given an attribute is TEXT/NUMBER/BOOLEAN typed, or is not assigned to the product's category
When it is used in a variant-generation request
Then the API rejects the request
```

## VAR-CUTOVER-001 — Stock Movements And Locks Are Variant-Granular

**Priority:** P0

```gherkin
Given a product with a single default variant
When a sale, sale void, customer return, job-card part consumption, goods receipt, supplier
  return, or stock adjustment is posted for it
Then the resulting stock_movements/stock_adjustments row carries the resolved product_variant_id
And StockPostingService locks and checks availability at the variant, not the product
```

## VAR-CUTOVER-002 — Sale Pricing And Below-Cost Checks Resolve From The Variant

**Priority:** P0

```gherkin
Given a product's default variant has its own selling/wholesale/cost price
When a PRODUCT invoice line is priced, or a below-cost sale is evaluated
Then the price and cost used are read from the resolved ProductVariant
And not from the parent Product's own pricing columns
```

## VAR-CUTOVER-003 — Every Repointed Table's product_variant_id Resolves To Its Own product_id

```gherkin
Given rows are created through the application write paths for invoice_lines, held_sale_items,
  job_parts, stock_movements, stock_adjustments, supplier_products, purchase_order_items,
  goods_receipt_items, supplier_return_items, and product_cost_history
When each row's non-null product_variant_id is joined back to product_variants
Then the variant's own product_id equals the row's own product_id, for every one of the 10 tables
```

## VAR-CUTOVER-004 — Goods Receipt Posting Updates The Variant's Cost, Not The Product's

**Priority:** P0

```gherkin
Given a goods receipt line for a product's default variant
When the goods receipt is posted
Then product_variants.cost_price for that variant is updated to the received unit cost
And a product_cost_history row is recorded carrying both product_id and product_variant_id
And the parent Product's own cost_price column is left unchanged
```

## VAR-POS-001 — Barcode Scan Resolves To The Specific Variant It Belongs To

**Priority:** P0

```gherkin
Given a product variant has its own barcode, distinct from its parent product's barcode
When that variant's barcode is scanned
Then the barcode lookup resolves variantSpecific = true and resolvedVariantId to that exact variant
Given a product's own barcode matches no variant's barcode
When that product-level barcode is scanned
Then the lookup falls back to variantSpecific = false, resolving to the product's default variant
```

## VAR-POS-002 — POS Can Sell An Explicitly Chosen Non-Default Variant

**Priority:** P0

```gherkin
Given a product has more than one active variant
When a PRODUCT invoice line names a specific, non-default productVariantId
Then stock is posted against that exact variant, not the product's default variant
Given a productVariantId that belongs to a different product than the line's productId
When the line is added
Then the API rejects it with VARIANT_PRODUCT_MISMATCH
```

## VAR-POS-003 — Sale, Return, And Adjustment Of One Variant Never Affect Its Sibling Variant

```gherkin
Given a product has two variants, each independently stocked
When one variant is sold, partially returned with restock, and then stock-adjusted
Then the variant-scoped stock query reflects exactly that variant's net movement
And its sibling variant's stock, seeded and never touched, reads unchanged
```

---

# 11. Document Number Acceptance Tests

## DOC-NUM-001 — Draft Invoice Has No Official Number

**Priority:** P0

```gherkin
Given a draft invoice is created
Then invoiceNumber is null
And the daily sequence is not consumed
```

## DOC-NUM-002 — Post Allocates Number

```gherkin
When a valid draft invoice is posted
Then one official INV-YYYYMMDD-NNNN number is allocated
```

## DOC-NUM-003 — Rollback Does Not Permanently Consume Number

**Priority:** P0

```gherkin
Given invoice posting allocates a sequence number inside its transaction
And a downstream posting step fails
When the transaction rolls back
Then no posted invoice remains
And the document sequence increment is rolled back with the transaction
```

## DOC-NUM-004 — Concurrent Numbering

**Priority:** P0

```gherkin
Given ten valid invoices are posted concurrently on the same business date
When all transactions succeed
Then all ten invoice numbers are unique
And their daily sequence values are ordered without duplicate allocation
```

## DOC-NUM-005 — No MAX+1 Race

Implementation review/test confirms numbering is not implemented with a non-locking `MAX()+1` query.

---

# 12. Invoice Draft Acceptance Tests

## SALE-DRAFT-001 — Create Draft

```gherkin
When a permitted user creates an invoice
Then status is DRAFT
And no stock movement exists
And no receivable exists
And no cashbook entry exists
And the invoice is excluded from VAT/sales reports
```

## SALE-DRAFT-002 — Edit Draft

```gherkin
Given an invoice is DRAFT
When line quantity, customer, notes, or permitted discount is changed
Then update succeeds
And version increases
```

## SALE-DRAFT-003 — Stale Version

```gherkin
Given a draft was updated from version 2 to 3 by another user
When a client submits version 2
Then the server returns 409 CONCURRENT_MODIFICATION
And newer data is not overwritten
```

## SALE-DRAFT-004 — Posted Invoice Cannot Be Edited

**Priority:** P0

```gherkin
Given an invoice is POSTED
When a normal update endpoint is called
Then the server rejects the mutation
And historical values remain unchanged
```

---

# 13. Pricing, Discount & VAT Acceptance Tests

## SALE-PRICE-001 — Retail Price Resolution

```gherkin
Given a retail customer/product
When a product line is added
Then retail selling price is used by default
```

## SALE-PRICE-002 — Wholesale Price Resolution

```gherkin
Given an eligible wholesale customer
When a product line is added
Then wholesale price is selected according to MVP pricing rule
```

## SALE-DISC-001 — Small Discount

```gherkin
Given user has invoice.discount.apply
When a discount within the permitted tier is applied
Then it is accepted
```

## SALE-DISC-002 — Approval Required

```gherkin
Given a discount exceeds the cashier's permitted tier
When no valid manager approval exists
Then posting fails with INVOICE_DISCOUNT_APPROVAL_REQUIRED
```

## SALE-DISC-003 — Approval Bound to Invoice

```gherkin
Given a manager approval was created for Invoice A
When the same approval ID is submitted for Invoice B
Then it is rejected
```

## SALE-DISC-004 — Below Cost

```gherkin
Given a requested sale price is below cost
When required permission/reason is absent
Then posting is rejected
```

## TAX-CALC-001 — Standard VAT Line

```gherkin
Given VAT is enabled at 18%
And a STANDARD line has taxable value 1000.00
When the line is calculated
Then VAT is 180.00
And line total is 1180.00
```

## TAX-CALC-002 — Exempt Line

```gherkin
Given an EXEMPT line
When calculated
Then VAT amount is 0
```

## TAX-CALC-003 — Zero Rated

```gherkin
Given a ZERO_RATED line
When calculated
Then VAT amount is 0
And tax category snapshot remains ZERO_RATED
```

## TAX-ROUND-001 — HALF_UP

**Priority:** P0

Verify line-level monetary values use:

```text
BigDecimal
RoundingMode.HALF_UP
2 decimal places
```

## TAX-ROUND-002 — Header Reconciliation

```gherkin
Given an invoice has multiple lines with rounding
When final totals are calculated
Then invoice VAT equals SUM(line VAT)
And invoice total equals SUM(final line totals)
```

## TAX-DISC-001 — Invoice Discount Allocation

```gherkin
Given an invoice-level discount spans several eligible lines
When discount is allocated
Then sum of line allocations equals invoice discount exactly
And the deterministic rounding remainder is assigned once
And tax totals reconcile
```

---

# 14. Invoice Posting Acceptance Tests

## SALE-POST-001 — Fully Paid Cash Sale

**Priority:** P0

```gherkin
Given Product A has available stock 10
And a DRAFT invoice sells quantity 2
And total is fully paid in cash
When the invoice is posted
Then status becomes POSTED
And an official invoice number is created
And a SALE stock movement of -2 is created
And physical stock becomes 8
And a customer payment is persisted
And a payment allocation is persisted
And a cashbook IN entry is created
And payment status is PAID
And audit INVOICE_POSTED exists
And all records commit atomically
```

## SALE-POST-002 — Split Payment

```gherkin
Given invoice total is 8000
When payment is 5000 CASH and 3000 CARD
Then two payment records/allocations are represented correctly
And total allocated payment is 8000
And invoice status is PAID
And cashbook reflects both monetary receipts correctly
```

## SALE-POST-003 — Immediate Sale Cannot Leave Balance

```gherkin
Given invoice total is 10000
And creditSale = false
When only 3000 payment is submitted
Then posting fails with INVOICE_PAYMENT_REQUIRED
And no stock/finance posting commits
```

## SALE-POST-004 — Valid Credit Sale

**Priority:** P0

```gherkin
Given customer CREDIT-GOOD has adequate credit
And invoice total is 10000
And immediate payment is 3000
When creditSale = true
Then invoice posts
And amountPaid = 3000
And balanceDue = 7000
And paymentStatus = PARTIAL
And receivable increases by 7000
```

## SALE-POST-005 — Zero Payment Credit Sale

```gherkin
Given eligible credit customer
When a 10000 invoice is posted with no immediate payment and creditSale = true
Then invoice posts as UNPAID
And receivable increases by 10000
```

## SALE-POST-006 — Credit Requires Customer

```gherkin
Given no customer is selected
When creditSale = true
Then posting fails with INVOICE_CREDIT_CUSTOMER_REQUIRED
```

## SALE-POST-007 — Atomic Failure

**Priority:** P0

```gherkin
Given a valid sale
And a forced failure occurs after invoice persistence but before stock/finance posting completes
When posting executes
Then the entire transaction rolls back
And no POSTED invoice remains
And no stock movement remains
And no payment/cashbook entry remains
```

## SALE-POST-008 — Historical Snapshots

**Priority:** P0

```gherkin
Given Product A is sold at 1200 with VAT 18%
When the invoice posts
And later product name/price/tax configuration changes
Then the historical invoice still renders the original description, price, UOM, tax category, VAT rate, VAT amount, and total
```

---

# 15. Credit Policy Acceptance Tests

## CRD-001 — Normal Credit

```gherkin
Given oldest outstanding is <=30 days
And new credit remains within limit
When credit sale posts
Then it is allowed
```

## CRD-002 — Warning

```gherkin
Given oldest outstanding is 31–60 days
When sale is attempted
Then server returns warning state
And permitted credit behavior follows MVP rule
```

## CRD-003 — Cash Only

**Priority:** P0

```gherkin
Given oldest outstanding is 61–90 days
When a new credit sale is attempted
Then it is rejected
When a fully paid cash sale is attempted
Then it may proceed
```

## CRD-004 — Block All

**Priority:** P0

```gherkin
Given oldest outstanding is 91+ days
When any sale is attempted
Then sale is blocked according to MVP policy
```

## CRD-005 — Limit Exceeded

```gherkin
Given customer credit limit is 100000
And current valid exposure is 95000
When new credit of 10000 is requested
Then posting fails with CUSTOMER_CREDIT_LIMIT_EXCEEDED
```

## CRD-CON-001 — Concurrent Credit Limit

**Priority:** P0

```gherkin
Given customer has only 10000 credit capacity remaining
When two clients concurrently post 8000 credit invoices
Then at most one can consume capacity if both together would exceed the limit
And the other is rejected
```

---

# 16. Idempotency Acceptance Tests

## SYS-IDEM-001 — Invoice Post Retry

**Priority:** P0

```gherkin
Given invoice post with Idempotency-Key X commits
And the client loses the response
When the same logical request is retried with X
Then the original result is returned
And no second invoice posting or stock movement is created
```

## SYS-IDEM-002 — Same Key Different Payload

```gherkin
Given request key X was used for amount 1000
When X is reused for a logically different payload
Then 409 IDEMPOTENCY_CONFLICT is returned
```

## SYS-IDEM-003 — Payment Retry

```gherkin
Given payment X commits
When X is retried
Then exactly one payment and one cashbook source posting exist
```

## SYS-IDEM-004 — GRN Retry

Exactly one GRN posting/stock/cost/payable effect.

## SYS-IDEM-005 — Supplier Payment Retry

Exactly one supplier payment/allocation/cashbook effect.

## SYS-IDEM-006 — Stock Approval Retry

Exactly one movement.

## SYS-IDEM-007 — Cash Closing Retry

Exactly one closing for cashier/date.

---

# 17. Held Bill Acceptance Tests

## SALE-HOLD-001 — Hold Reserves Stock

**Priority:** P0

```gherkin
Given physical stock is 10
When quantity 3 is held
Then physical stock remains 10
And reserved stock becomes 3
And available stock becomes 7
And no SALE stock movement exists
```

## SALE-HOLD-002 — Cancel Releases Reservation

```gherkin
Given a held bill reserves 3
When it is cancelled
Then reserved stock decreases by 3
And physical stock remains unchanged
```

## SALE-HOLD-003 — Expiry Releases Reservation

```gherkin
Given a held bill expires under configured policy
When expiry processing occurs
Then its reservation no longer contributes to reserved stock
```

## SALE-HOLD-004 — Resume

```gherkin
Given a HELD bill
When cashier resumes it
Then cart is restored
And reservation remains active
```

## SALE-HOLD-005 — Convert and Post

**Priority:** P0

```gherkin
Given a held bill reserves quantity 3
When it converts to a draft invoice and posts successfully
Then reservation is released
And one SALE movement of -3 is posted
And physical stock decreases by 3
And no double deduction occurs
```

## SALE-HOLD-CON-001 — Concurrent Reservation

```gherkin
Given available stock is 1
When two terminals attempt to hold quantity 1 concurrently
Then only one reservation succeeds
And available stock never becomes invalid through double reservation
```

---

# 18. Payment Allocation Acceptance Tests

## FIN-AR-PAY-001 — Later Partial Payment

```gherkin
Given a posted invoice has balance 10000
When customer later pays 4000
Then customer_payment exists
And allocation = 4000
And balance becomes 6000
And paymentStatus becomes PARTIAL
And cashbook IN = 4000
```

## FIN-AR-PAY-002 — Final Payment

```gherkin
Given balance is 6000
When 6000 is allocated
Then balance becomes 0
And paymentStatus becomes PAID
```

## FIN-AR-PAY-003 — Multi-Invoice Payment

```gherkin
Given customer has Invoice A balance 20000 and Invoice B balance 30000
When a 50000 payment allocates 20000 + 30000
Then both invoices become paid
And one customer payment exists
And allocations total 50000
```

## FIN-AR-PAY-004 — Over Allocation

**Priority:** P0

```gherkin
Given invoice balance is 5000
When allocation of 6000 is attempted
Then the transaction is rejected
And no payment/cashbook partial posting remains
```

## FIN-AR-CON-001 — Concurrent Allocation

**Priority:** P0

```gherkin
Given invoice balance is 5000
When two clients concurrently attempt to allocate 4000 each
Then both cannot commit
And final allocated total does not exceed 5000
```

---

# 19. Credit Note / Customer Return Acceptance Tests

## SALE-CN-001 — Valid Return

**Priority:** P0

```gherkin
Given a posted invoice sold 2 units of Product A
When 1 eligible unit is returned
Then one credit note is posted
And tax reversal uses original line snapshots
And CUSTOMER_RETURN stock movement +1 is created when restockable
And receivable/refund effect is correct
```

## SALE-CN-002 — Excess Return

```gherkin
Given 2 units sold and 1 already returned
When another return requests quantity 2
Then the request is rejected
```

## SALE-CN-003 — Non-Restock Return

```gherkin
Given returned item is not restockable
When credit note is posted
Then financial/tax credit occurs
And no CUSTOMER_RETURN stock movement is added
```

## SALE-CN-004 — Refund

```gherkin
Given a valid credit note settlement is REFUND
When refund commits
Then customer_refund is persisted
And cashbook OUT is created
And refund does not exceed refundable amount
```

## SALE-CN-005 — Apply to Balance

```gherkin
Given customer has outstanding receivable
When credit note is applied
Then credit-note application reduces outstanding balance
And no cashbook OUT is created
```

---

# 20. Invoice Void Acceptance Tests

## SALE-VOID-001 — Authorized Void

```gherkin
Given a POSTED invoice
And user has invoice.void
And reason is supplied
When valid void is executed
Then invoice status becomes VOIDED
And original historical values remain
And required reversing effects are posted
And audit INVOICE_VOIDED exists
```

## SALE-VOID-002 — Cashier Cannot Void

```gherkin
Given cashier lacks invoice.void
When void is attempted
Then 403 is returned
And no data changes
```

## SALE-VOID-003 — Void Without Reason

```gherkin
When void reason is blank
Then operation fails
```

## SALE-VOID-004 — No Direct Delete

**Priority:** P0

Verify there is no supported API path to delete a POSTED invoice.

---

# 21. Stock Ledger Acceptance Tests

## STK-LEDGER-001 — Authoritative Stock

**Priority:** P0

```gherkin
Given all stock changes are posted through movements
When stock is queried
Then physical stock equals SUM(stock_movements.quantity)
```

## STK-LEDGER-002 — Movement Immutability

```gherkin
Given a posted movement exists
When an update/delete is attempted through application
Then it is rejected
```

## STK-LEDGER-003 — Sale Movement

Product sale creates negative movement.

## STK-LEDGER-004 — GRN Movement

GRN creates positive movement.

## STK-LEDGER-005 — Customer Return Movement

Restockable return creates positive movement.

## STK-LEDGER-006 — Supplier Return Movement

Supplier return creates negative movement.

## STK-LEDGER-007 — Job Part Movement

Job part creates negative movement.

## STK-SOURCE-001 — Source Uniqueness

**Priority:** P0

```gherkin
Given a source line already has its expected movement
When posting logic is retried
Then DB/source uniqueness prevents a duplicate movement
```

---

# 22. Stock Concurrency Acceptance Tests

## STK-CON-001 — Concurrent Sale

**Priority:** P0

```gherkin
Given available stock is 1
When two terminals concurrently sell quantity 1
Then exactly one sale succeeds
And final physical stock is 0
And no negative oversell occurs
```

## STK-CON-002 — Sale vs Hold

```gherkin
Given available stock is 1
When one client holds 1 while another concurrently posts sale 1
Then locking/validation ensures only a valid combination commits
And available stock does not become negative
```

## STK-CON-003 — Stable Lock Ordering

Integration stress test executes multi-product postings with different cart order and confirms no systematic deadlock from inconsistent product lock ordering.

---

# 23. Stock Adjustment Acceptance Tests

## STK-ADJ-001 — Create Pending Adjustment

```gherkin
When stock adjustment is created
Then status is PENDING
And no stock movement exists
```

## STK-ADJ-002 — Approve

**Priority:** P0

```gherkin
Given a PENDING adjustment
When authorized manager approves
Then status becomes APPROVED
And exactly one ADJUSTMENT movement is created
```

## STK-ADJ-003 — Reject

```gherkin
Given PENDING adjustment
When rejected
Then status becomes REJECTED
And no stock movement exists
```

## STK-ADJ-004 — Double Approval

```gherkin
Given adjustment already APPROVED
When approval is attempted again
Then operation is rejected or idempotently returns existing result
And no second movement is created
```

## STK-ADJ-005 — Negative Adjustment Insufficient Stock

Validate configured MVP negative-stock rule and reject invalid deduction.

---

# 24. GRN Acceptance Tests

## PUR-GRN-001 — Draft GRN

```gherkin
When GRN draft is created
Then status is DRAFT
And no official GRN number is allocated
And no stock/payable/cost-history effect exists
```

## PUR-GRN-002 — Post GRN

**Priority:** P0

```gherkin
Given a valid DRAFT GRN receives 20 units at 800
When it posts
Then GRN gets official number
And status becomes POSTED
And stock movement +20 exists
And product cost history records 800
And supplier payable increases by 16000
And audit GRN_POSTED exists
```

## PUR-GRN-003 — Atomic Failure

```gherkin
Given a forced failure after GRN row change
When posting runs
Then GRN/stock/cost/payable all roll back
```

## PUR-GRN-004 — Posted Immutability

Cannot edit/delete posted items.

## PUR-GRN-005 — Duplicate Supplier Reference

Where the supplier reference uniqueness rule applies, duplicate posted reference for same supplier is rejected.

---

# 25. Supplier Return Acceptance Tests

## PUR-RET-001 — Valid Supplier Return

```gherkin
Given GRN received 20 at unit cost 800
When 2 are returned
Then supplier return posts
And stock movement -2 exists
And payable decreases by 1600
```

## PUR-RET-002 — Return Uses Original Cost

```gherkin
Given current product cost later changed
When original GRN item is returned
Then return valuation uses original GRN item cost
```

## PUR-RET-003 — Cannot Exceed Received

```gherkin
Given received 5 and already returned 4
When return of 2 is attempted
Then rejected
```

## PUR-RET-CON-001 — Concurrent Returns

**Priority:** P0

Two concurrent returns cannot cumulatively exceed received quantity.

---

# 26. Supplier Payment Acceptance Tests

## PUR-PAY-001 — Full Allocation

```gherkin
Given supplier GRN outstanding is 25000
When supplier payment 25000 is allocated
Then GRN outstanding becomes 0
And cashbook OUT = 25000
```

## PUR-PAY-002 — Partial Allocation

```gherkin
Given outstanding 25000
When payment 10000 is allocated
Then outstanding becomes 15000
```

## PUR-PAY-003 — Multi-GRN Allocation

```gherkin
Given three GRNs have 25000, 40000, and 35000 outstanding
When a 100000 payment allocates those amounts
Then all three balances become 0
And one supplier payment exists
And allocations total 100000
```

## PUR-PAY-004 — Over Allocation

```gherkin
Given GRN outstanding is 5000
When allocation 6000 is attempted
Then transaction fails
And cashbook/payment do not partially commit
```

## PUR-PAY-CON-001 — Concurrent Allocation

**Priority:** P0

Two clients cannot both consume the same outstanding supplier balance.

---

# 27. Appointment Acceptance Tests

## SCH-APT-001 — Create Appointment

```gherkin
Given customer, service, and eligible technician
And selected slot has no conflict
When appointment is created
Then status is SCHEDULED
And appointment number is assigned
```

## SCH-APT-002 — End and Buffer

```gherkin
Given service duration is 60 minutes
And buffer is 15 minutes
When appointment starts at 10:00
Then end is 11:00
And blockedUntil is 11:15
```

## SCH-CONFLICT-001 — Overlapping Appointment

**Priority:** P0

```gherkin
Given technician T has active appointment 10:00–11:15 blocked range
When another overlapping appointment is created
Then PostgreSQL exclusion protection rejects it
And API returns 409 APPOINTMENT_CONFLICT
```

## SCH-CONFLICT-002 — Concurrent Double Booking

**Priority:** P0

```gherkin
Given technician T has no booking
When two clients concurrently create overlapping bookings
Then exactly one commits
And the other receives APPOINTMENT_CONFLICT
```

## SCH-CONFLICT-003 — Different Technician

Same time for different technician is allowed.

## SCH-CONFLICT-004 — Cancel Frees Slot

```gherkin
Given appointment A blocks slot
When A becomes CANCELLED
Then another appointment for same technician/time can be created
```

## SCH-STATE-001 — Valid State

SCHEDULED → CONFIRMED succeeds.

## SCH-STATE-002 — Invalid State

```gherkin
Given appointment is COMPLETED
When request attempts SCHEDULED
Then APPOINTMENT_INVALID_TRANSITION
```

## SCH-RESCH-001 — Reschedule Uses Same Constraint

Drag/reschedule cannot bypass conflict protection.

---

# 28. Appointment-to-Job Acceptance Tests

## JOB-CONV-001 — Convert Once

```gherkin
Given appointment is eligible
When convert-to-job is executed
Then one JobCard is created
And job_cards.appointment_id references the appointment
```

## JOB-CONV-002 — Duplicate Conversion

**Priority:** P0

```gherkin
Given appointment already has a job
When conversion is attempted again
Then a second job is not created
```

## JOB-CONV-003 — Idempotent Conversion Retry

Same request ID returns existing job card.

---

# 29. Job Estimate Acceptance Tests

## JOB-EST-001 — Estimate Required

```gherkin
Given job contains service requiring estimate
When normal work start is attempted before accepted estimate
Then JOB_ESTIMATE_REQUIRED is returned
```

## JOB-EST-002 — Create Estimate Version

```gherkin
When first estimate is created
Then version = 1
When revised estimate is created
Then new row version = 2
And original remains unchanged
```

## JOB-EST-003 — Accept

PENDING → ACCEPTED and job moves appropriately to ESTIMATE_APPROVED.

## JOB-EST-004 — Decline

PENDING → DECLINED and job follows cancellation rule.

---

# 30. Job Card State Acceptance Tests

## JOB-STATE-001 — Created to Estimate Pending

Valid when estimate-required service exists.

## JOB-STATE-002 — Estimate Approved to In Progress

Valid with permission.

## JOB-STATE-003 — In Progress to Ready for Pickup

Requires relevant services completed.

## JOB-STATE-004 — Ready to Completed

**Priority:** P0

```gherkin
Given job is READY_FOR_PICKUP
And required invoice/payment or authorized credit condition is satisfied
And pickup is confirmed
When CompleteJob is executed
Then status becomes COMPLETED
And pickup date is stored
And warranty dates are stored
```

## JOB-STATE-005 — Invalid Direct Completion

```gherkin
Given job is IN_PROGRESS
When direct COMPLETED transition is requested
Then JOB_INVALID_TRANSITION is returned
```

## JOB-CANCEL-001 — Cancellation Reason

Cancellation without required reason is rejected.

---

# 31. Job Part Acceptance Tests

## JOB-PART-001 — Consume Part

**Priority:** P0

```gherkin
Given job permits parts
And Product A available stock is 5
When quantity 1 is added
Then JobPart is stored
And cost-price snapshot is stored
And customer-price snapshot is stored
And one JOB_PART stock movement -1 exists
And available physical stock becomes 4
```

## JOB-PART-002 — Insufficient Stock

No JobPart or movement commits.

## JOB-PART-003 — Retry

Same idempotency key creates no second part/movement.

## JOB-PART-004 — Job Invoice No Double Deduction

**Priority:** P0

```gherkin
Given a job part already consumed stock through JOB_PART movement
When a service invoice is generated from the job
And that draft invoice is posted
Then the charge line appears on invoice
And no additional SALE stock movement is created for the same job part
```

---

# 32. Cashbook Acceptance Tests

## FIN-CASH-001 — Customer Payment

Customer payment creates IN source entry.

## FIN-CASH-002 — Supplier Payment

Supplier payment creates OUT source entry.

## FIN-CASH-003 — Refund

Customer refund creates OUT source entry.

## FIN-CASH-004 — Manual Entry

Permitted user can create manual IN/OUT with reason/category.

## FIN-CASH-005 — Source Uniqueness

**Priority:** P0

```gherkin
Given a system-generated source payment already has a cashbook entry
When source processing is retried
Then a second equivalent cashbook entry cannot be created
```

## FIN-CASH-006 — Reversal

Manual correction creates reversing entry; original is unchanged.

---

# 33. Receivable Acceptance Tests

## FIN-AR-001 — Derived Balance

**Priority:** P0

```gherkin
Given posted invoice 10000
And payment allocation 3000
And applied credit note 2000
When receivable is queried
Then balance is 5000
```

## FIN-AR-002 — No Independent Balance Drift

Verify no service updates a standalone authoritative `customers.current_balance`.

## FIN-AR-003 — Aging

```gherkin
Given invoice due date is 40 days old
When aging is calculated
Then balance appears in 31–60 bucket
```

## FIN-AR-004 — Due Date Fallback

If due date absent, invoice date is used.

---

# 34. Payable Acceptance Tests

## FIN-AP-001 — Derived GRN Outstanding

```gherkin
Given GRN total 100000
And supplier return 10000
And payment allocation 40000
Then GRN outstanding is 50000
```

## FIN-AP-002 — Supplier Statement

Opening balance + posted GRNs − returns − payments reconciles to displayed supplier balance.

## FIN-AP-003 — No Independent Balance Drift

No independent mutable supplier-current-balance field is authoritative.

---

# 35. Cash Closing Acceptance Tests

## FIN-CLOSE-001 — Zero Variance

```gherkin
Given expected cash is 100000
When counted cash is 100000
Then closing is created directly as APPROVED
And variance = 0
```

## FIN-CLOSE-002 — Non-Zero Requires Reason

```gherkin
Given expected = 100000
And counted = 99500
When no variance reason is supplied
Then creation fails
```

## FIN-CLOSE-003 — Non-Zero Pending Approval

```gherkin
Given expected = 100000
And counted = 99500
And reason supplied
When closing is created
Then variance = -500
And status = PENDING_APPROVAL
```

## FIN-CLOSE-004 — Manager Approval

Authorized manager changes PENDING_APPROVAL → APPROVED.

## FIN-CLOSE-005 — Cashier Cannot Approve Without Permission

403 and state unchanged.

## FIN-CLOSE-006 — Duplicate Closing

**Priority:** P0

```gherkin
Given closing already exists for cashier/date
When a second is created
Then DB uniqueness rejects it
```

## FIN-CLOSE-007 — Approved Immutability

Approved closing cannot be edited.

---

# 36. Reporting Acceptance Tests

## RPT-SALE-001 — Daily Sales Reconciliation

**Priority:** P0

```gherkin
Given deterministic posted invoices and credit notes
When daily sales report runs
Then its total equals canonical eligible posted sales calculation
```

## RPT-PAY-001 — Sales by Payment Method

Cash/card/bank/cheque totals equal source customer payments for eligible sales.

## RPT-STK-001 — Stock on Hand

Report equals stock ledger sums.

## RPT-STK-002 — Low Stock

Uses:

```text
available stock <= reorder point
```

not a different definition.

## RPT-AR-001 — Customer Balances

Matches receivable source calculations.

## RPT-AP-001 — Supplier Balances

Matches payable source calculations.

## RPT-CASH-001 — Cashbook

Matches immutable cashbook records.

## RPT-CLOSE-001 — Daily Closing

Matches stored/derived closing data.

## RPT-SCH-001 — Appointment Summary

Matches authoritative appointment rows.

## RPT-JOB-001 — Job Status

Counts by canonical job states.

## RPT-TAX-001 — VAT Report Snapshot

**Priority:** P0

```gherkin
Given historical invoices were posted at prior tax settings
When current VAT configuration changes
Then VAT report for historical period still uses posted snapshots
```

## RPT-EXPORT-001 — PDF and JSON Same Totals

PDF total equals JSON report total for identical filters.

## RPT-EXPORT-002 — CSV and JSON Same Rows/Totals

CSV output reconciles to JSON/query model.

---

# 37. Dashboard Acceptance Tests

## DASH-001 — Today's Sales

Matches canonical daily sales query.

## DASH-002 — Monthly Revenue

Matches canonical month-to-date sales query.

## DASH-003 — Receivable

Matches finance receivable.

## DASH-004 — Payable

Matches finance payable.

## DASH-005 — Low Stock Count

Matches low-stock report count.

## DASH-006 — Today's Appointments

Uses Asia/Colombo business date.

## DASH-007 — Pending Jobs

Counts only:

```text
CREATED
ESTIMATE_PENDING
ESTIMATE_APPROVED
IN_PROGRESS
READY_FOR_PICKUP
```

---

# 38. Audit Acceptance Tests

## AUD-001 — Invoice Post Audit

Audit contains:

```text
entity
entity id
INVOICE_POSTED
actor
timestamp
correlation/request context
```

## AUD-002 — Role Grant Audit

Recorded.

## AUD-003 — Stock Approval Audit

Recorded.

## AUD-004 — Backup/Restore Audit

Recorded.

## AUD-005 — Login Audit

Successful and failed attempts represented.

## AUD-006 — Audit Read Only

Normal APIs cannot alter/delete audit rows.

## AUD-PII-001 — PII View Audit

Where MVP/SRS requires sensitive-field access audit, viewing protected PII generates audit evidence.

---

# 39. Backup Acceptance Tests

## SYS-BACKUP-001 — Successful Backup

**Priority:** P0

```gherkin
Given PostgreSQL and backup storage are healthy
When authorized backup is created
Then backup file exists
And size > 0
And SHA-256 checksum is recorded
And status becomes VERIFIED only after verification
And audit event exists
```

## SYS-BACKUP-002 — Insufficient Storage

```gherkin
Given destination has insufficient capacity
When backup runs
Then operation becomes FAILED
And error is actionable
And no record is falsely marked VERIFIED
```

## SYS-BACKUP-003 — Invalid Tooling

Missing/incompatible `pg_dump` yields FAILED with clear diagnostic.

## SYS-BACKUP-004 — Unauthorized User

403 and backup not started.

---

# 40. Restore Acceptance Tests

## SYS-RESTORE-001 — Reject Unverified Backup

**Priority:** P0

```gherkin
Given backup status is FAILED/unverified
When restore is requested
Then restore is rejected
```

## SYS-RESTORE-002 — Active Session Guard

```gherkin
Given other active clients violate maintenance condition
When restore preflight runs
Then canRestore = false or explicit disconnect workflow is required
```

## SYS-RESTORE-003 — Clean Restore

**Priority:** P0

```gherkin
Given a VERIFIED backup
When it is restored into a supported clean environment
Then PostgreSQL starts/readability succeeds
And Flyway/schema version is readable
And the application can authenticate
And seeded reconciliation checks pass
And restore record status becomes VERIFIED
```

## SYS-RESTORE-004 — Failed Restore

Failure is recorded as FAILED and not hidden.

## SYS-RESTORE-005 — Audit

Restore start/completion/failure actions are audited.

---

# 41. API Contract Acceptance Tests

## API-001 — Base Version

MVP endpoints exist under:

```text
/api/v1
```

## API-002 — Protected Endpoint Without Token

Returns 401.

## API-003 — Protected Endpoint Without Permission

Returns 403.

## API-004 — Validation Contract

Invalid field produces:

```text
VALIDATION_FAILED
fieldErrors[]
correlationId
```

## API-005 — Not Found

Returns stable 404 error.

## API-006 — Concurrency

Stale version returns 409 `CONCURRENT_MODIFICATION`.

## API-007 — Correlation ID

Server returns correlation ID for success/error.

## API-008 — PDF Media Type

Invoice/report PDF endpoint returns `application/pdf`.

## API-009 — CSV Media Type

CSV endpoint returns `text/csv`.

---

# 42. JavaFX Acceptance Tests

## UI-AUTH-001

Login screen handles success, invalid credentials, locked account, and expired session clearly.

## UI-RBAC-001

Unavailable permission actions are hidden/disabled.

Server is still tested separately as authoritative.

## UI-POS-001

Cashier can:

```text
scan barcode
add quantity
change/remove item
select customer
apply permitted discount
hold
resume
take split payment
post sale
print receipt
```

## UI-POS-002

When server rejects insufficient stock after a stale UI preview, JavaFX shows updated stock and does not show false success.

## UI-SCH-001

Daily/weekly/monthly calendar displays server appointments.

## UI-SCH-002

Drag/reschedule conflict shows friendly rejection and reloads server state.

## UI-JOB-001

Service user can progress job through valid states.

## UI-RPT-001

Reports can export PDF/CSV.

## UI-REC-001

When server is unavailable, JavaFX shows clear connection state and does not create hidden local financial transactions.

---

# 43. SME Scenario Acceptance Pack

# 43.1 SC-01 Retail / Trading

## SC01-001 — Receive and Sell

```gherkin
Given Supplier A and Product A exist
When 20 Product A are received through posted GRN
Then stock increases by 20
When customer buys 2 by cash
Then invoice posts
And stock decreases by 2
And cashbook increases by sale payment
And daily sales report includes sale
And stock report reconciles
```

## SC01-002 — Held Sale

Hold 3, confirm available stock reduces by reservation only, then cancel and verify availability restores.

## SC01-003 — Return

Sell 2, return 1, verify credit note/stock/refund/tax.

---

# 43.2 SC-02 Wholesale / Credit Trading

## SC02-001 — Credit Invoice

```gherkin
Given wholesale customer has valid credit capacity
When wholesale invoice posts partially paid
Then wholesale pricing applies
And receivable equals unpaid balance
```

## SC02-002 — Later Settlement

Record later payment and verify aging/balance/cashbook.

## SC02-003 — Credit Block

Create overdue condition and verify sales rule.

---

# 43.3 SC-03 Repair Centre

## SC03-001 — Full Repair Workflow

```gherkin
Given a customer books repair service
When appointment converts to job
And estimate is created and accepted
And a part is consumed
And service completes
And job becomes READY_FOR_PICKUP
And invoice is generated and paid
And pickup is recorded
Then job becomes COMPLETED
And part stock was deducted exactly once
And invoice includes service + part charge
And warranty dates exist
```

---

# 43.4 SC-04 Appointment-Based Service

## SC04-001 — Standard Appointment

Create customer/service/appointment, confirm, start, complete, invoice, payment.

## SC04-002 — Concurrent Booking

Two users attempt same technician slot; only one succeeds.

---

# 43.5 SC-05 Hybrid Product + Service

## SC05-001 — Mixed Invoice

```gherkin
Given invoice contains PRODUCT, SERVICE, and CUSTOM lines
When it posts
Then all line types appear historically
And only inventory PRODUCT lines requiring physical sale movement affect stock
And VAT/totals reconcile
```

---

# 44. Performance & Concurrency Acceptance Tests

## PERF-001 — Five Active POS Clients

**Priority:** P0

```gherkin
Given five JavaFX/POS clients use the same server
When they perform normal concurrent sales
Then transactions remain correct
And no duplicate document number occurs
And no stock corruption occurs
```

## PERF-002 — Ten Concurrent Users

Engineering target:

```text
10 concurrent users
```

Run mixed workload:

- searches;
- sales;
- appointment reads;
- product reads;
- report reads;
- GRN/payment actions.

Verify acceptable response behavior and integrity.

## PERF-003 — Product Search

Representative dataset search should meet practical MVP response target and use indexed query plan.

## PERF-004 — Customer Search

Same.

## PERF-005 — Report Query

Representative 12-month dataset report completes within agreed MVP performance target.

Performance targets should be finalized during implementation benchmark setup rather than invented in this document.

---

# 45. Transaction Rollback Fault-Injection Tests

For each P0 transaction, inject a controlled exception at multiple steps.

## TX-SALE-001

Fail:

```text
after invoice
after stock
after payment
after cashbook
before audit
```

Verify entire sale rolls back.

## TX-GRN-001

Fail after:

```text
GRN
stock
cost history
payable
```

Verify all rollback.

## TX-SUPPAY-001

Fail after payment but before allocation/cashbook.

## TX-CN-001

Fail after credit note but before stock/refund.

## TX-ADJ-001

Fail after approval state before movement.

## TX-CLOSE-001

Fail during closing transaction.

These tests are mandatory because atomicity is a core MVP design principle.

---

# 46. Database Constraint Acceptance Tests

## DB-001

Duplicate product barcode rejected.

## DB-002

Duplicate username (case-normalized policy) rejected.

## DB-003

Invoice POSTED without number cannot persist.

## DB-004

DRAFT invoice with official number cannot persist under final constraint.

## DB-005

Appointment overlap rejected.

## DB-006

Duplicate cash closing cashier/date rejected.

## DB-007

Zero stock movement rejected.

## DB-008

Negative payment amount rejected.

## DB-009

Invalid status rejected by CHECK.

## DB-010

Duplicate payment-to-invoice allocation pair rejected.

## DB-011

Duplicate supplier-payment-to-GRN allocation pair rejected.

## DB-012

Job card duplicate appointment reference rejected.

---

# 47. Historical Data Acceptance Tests

## HIST-001 — Product Rename

Posted invoice remains old product description snapshot.

## HIST-002 — Price Change

Posted invoice remains old unit price.

## HIST-003 — Customer Rename

Historical invoice customer snapshot remains.

## HIST-004 — Business Profile Change

Old invoice retains old business profile snapshot where stored/required.

## HIST-005 — VAT Change

Old invoice retains old VAT.

## HIST-006 — GRN Cost

Product cost history remains original received cost.

## HIST-007 — Job Part Cost

Job part retains usage-time cost snapshot.

---

# 48. Permission Negative-Test Matrix

For every protected transition, create at least one test where an authenticated user without permission receives 403.

Required coverage:

```text
user.create
user.update
user.lock
user.unlock
user.grant_role
user.revoke_role

product.create
product.update
product.delete

invoice.create
invoice.void
invoice.payment.create
invoice.payment.refund
invoice.credit_note.create
invoice.hold_bill
invoice.override_price
invoice.sell_below_cost

appointment.create
appointment.update
appointment.cancel
appointment.convert_to_job

jobcard.create
jobcard.update
jobcard.status_change
jobcard.parts.add
jobcard.estimate.create
jobcard.estimate.approve
jobcard.complete

inventory.adjustment.create
inventory.adjustment.approve

purchasing.grn.create
purchasing.return.create
purchasing.payment.create

finance.cashbook.create
finance.cash_closing.create
finance.cash_closing.approve

audit.read
system.config
system.backup.create
system.backup.restore
```

No permission should exist only in JavaFX without a matching server test.

---

# 49. Traceability Matrix

| Requirement Area | Primary Test Groups |
|---|---|
| Authentication | SEC-AUTH, SEC-SESSION |
| RBAC | SEC-RBAC, permission-negative matrix |
| Customer | CUS |
| Catalog | CAT |
| POS | SALE-DRAFT, SALE-POST, SALE-HOLD |
| Discount approvals | SALE-DISC |
| VAT | TAX |
| Credit sales | CRD, FIN-AR |
| Payments | FIN-AR-PAY |
| Returns | SALE-CN |
| Invoice void | SALE-VOID |
| Stock | STK |
| GRN | PUR-GRN |
| Supplier return | PUR-RET |
| Supplier payment | PUR-PAY |
| Scheduling | SCH |
| Job cards | JOB |
| Receivables | FIN-AR |
| Payables | FIN-AP |
| Cashbook | FIN-CASH |
| Cash closing | FIN-CLOSE |
| Reporting | RPT |
| Dashboard | DASH |
| Audit | AUD |
| Backup/restore | SYS-BACKUP, SYS-RESTORE |
| API contract | API |
| JavaFX | UI |
| SME scenarios | SC01–SC05 |
| Concurrency/performance | PERF, *-CON |
| Transaction atomicity | TX |
| DB constraints | DB |
| Historical snapshots | HIST |

---

# 50. Suggested JUnit Test Classes

```text
identity/
  AuthenticationIT
  SessionIT
  RolePermissionIT
  SecondaryRoleExpiryIT

customer/
  CustomerApiIT
  CustomerCreditPolicyTest
  CustomerPiiIT

catalog/
  ProductApiIT
  ProductUniquenessIT
  ServiceApiIT

sales/
  InvoiceDraftIT
  InvoicePostingIT
  InvoicePricingTest
  InvoiceVatTest
  CreditSaleIT
  HeldSaleIT
  CustomerPaymentIT
  CreditNoteIT
  InvoiceVoidIT
  SalesIdempotencyIT

inventory/
  StockLedgerIT
  StockConcurrencyIT
  StockAdjustmentIT

purchasing/
  GrnPostingIT
  SupplierReturnIT
  SupplierPaymentAllocationIT

scheduling/
  AppointmentIT
  AppointmentConcurrencyIT
  JobCardIT
  JobPartIT

finance/
  ReceivableReconciliationIT
  PayableReconciliationIT
  CashbookIT
  CashClosingIT

reporting/
  DashboardIT
  SalesReportIT
  StockReportIT
  TaxReportIT

system/
  AuditIT
  BackupIT
  RestoreIT
  FlywayCleanInstallIT
```

---

# 51. Testcontainers Baseline

Example integration lifecycle:

```text
Start PostgreSQL container
→ apply Flyway
→ seed role/permission/UOM reference data
→ seed test fixtures
→ execute test
→ rollback/reset or recreate context as needed
```

Tests depending on PostgreSQL-specific behavior include:

```text
GiST exclusion
row locking
transaction isolation
partial indexes
trigram indexes
TIMESTAMPTZ
JSONB
```

They must execute against PostgreSQL.

---

# 52. Clean Installation Acceptance Test

## SYS-INSTALL-001

**Priority:** P0

```gherkin
Given an empty supported PostgreSQL database
When all Flyway migrations run from V001 to latest
Then migrations succeed
And seeded reference data exists
And Spring Boot starts
And JPA schema validation passes
And no manual SQL intervention is required
```

## SYS-INSTALL-002 — Re-run

Application restart does not reapply successful migrations incorrectly.

---

# 53. LAN Acceptance Tests

## LAN-001 — Client Connectivity

Five JavaFX clients connect to one Spring server over LAN.

## LAN-002 — PostgreSQL Isolation

Clients do not require or receive PostgreSQL credentials.

## LAN-003 — Server Disconnect

Client detects API/server loss and shows safe disconnected state.

## LAN-004 — Retry Safety

A timed-out posted command reused with same idempotency key does not duplicate transaction.

---

# 54. Single-PC Acceptance Tests

## SPC-001

JavaFX + Spring Boot + PostgreSQL on same PC use the same client-server API architecture.

## SPC-002

No alternate direct JDBC path exists in the JavaFX client.

## SPC-003

Backup/restore works in supported single-PC installation.

---

# 55. Security Acceptance Checklist

Release blocker if any fail:

```text
[ ] BCrypt cost 12 password hashes
[ ] no plaintext password storage
[ ] no raw session token storage
[ ] server-side permission checks
[ ] TLS supported/configured for LAN
[ ] account lockout
[ ] idle session timeout
[ ] temporary role expiry
[ ] encrypted customer NIC/BR at rest
[ ] secrets excluded from logs
[ ] manager approval secret never persisted
[ ] PII/audit controls
[ ] DB credentials not shipped to JavaFX
```

---

# 56. Finance Reconciliation Release Tests

## REC-FIN-001

For deterministic scenario:

```text
sum invoice receivables
= customer receivable report
= dashboard outstanding receivables
```

## REC-FIN-002

```text
opening supplier balance
+ posted GRNs
- supplier returns
- allocated payments
= supplier payable report
= dashboard outstanding payables
```

## REC-FIN-003

Cashbook generated from customer payments/refunds/supplier payments reconciles to source transactions.

## REC-FIN-004

Cash closing expected cash reconciles to eligible cashbook cash entries.

All are P0.

---

# 57. Stock Reconciliation Release Tests

## REC-STK-001

For every product:

```text
physical stock shown
=
SUM(stock movements)
```

## REC-STK-002

```text
available
=
physical
-
active held reservations
```

## REC-STK-003

Low-stock dashboard count equals low-stock report count using available-stock rule.

All are P0.

---

# 58. VAT Reconciliation Release Tests

## REC-TAX-001

For a seeded period:

```text
VAT report total
=
sum valid posted line VAT snapshots
-
valid credit/reversal VAT effects
```

## REC-TAX-002

Changing current tax rate after postings does not change historical result.

P0.

---

# 59. Backup/Restore Release Gate

Before production release, perform:

```text
1. Seed meaningful data:
   users
   roles
   customers
   products
   GRNs
   sales
   credit sale
   payments
   appointments
   job card
   stock adjustment
   audit

2. Create VERIFIED backup.

3. Restore into clean supported PostgreSQL instance.

4. Start Bizco server against restored DB.

5. Authenticate.

6. Verify:
   stock reconciliation
   receivable
   payable
   cashbook
   invoices
   job cards
   appointments
   audit
   Flyway schema version.

7. Mark release recovery gate PASS.
```

No production MVP release without this successful recovery exercise.

---

# 60. Definition of Done for a Feature

A feature is complete only when:

```text
Requirement implemented
+ server permission enforced
+ state machine enforced
+ PostgreSQL constraints/migrations present
+ unit tests pass
+ integration tests pass
+ negative tests pass
+ audit behavior verified
+ reconciliation verified where applicable
+ JavaFX flow works
+ documentation updated
```

A screen that "works" is not sufficient.

---

# 61. MVP Final Acceptance Checklist

## Identity

```text
[ ] Authentication
[ ] account lockout/unlock
[ ] sessions
[ ] RBAC
[ ] secondary roles
[ ] role expiry
[ ] login history
```

## Customer & Catalog

```text
[ ] customer CRUD/search
[ ] credit limits
[ ] PII protection
[ ] products
[ ] categories
[ ] UOM
[ ] services
[ ] barcode
[ ] pricing tiers
```

## Sales

```text
[ ] POS
[ ] product/service/custom lines
[ ] discounts
[ ] manager approvals
[ ] split payments
[ ] credit sales
[ ] invoice lifecycle
[ ] held bills
[ ] returns
[ ] credit notes
[ ] refunds
[ ] void
[ ] receipt/PDF/QR
```

## Scheduling & Service Work

```text
[ ] daily/weekly/monthly appointments
[ ] technician assignment
[ ] rescheduling
[ ] conflict protection
[ ] job cards
[ ] estimates
[ ] parts
[ ] service invoice
[ ] pickup
[ ] warranty
```

## Inventory & Purchasing

```text
[ ] stock ledger
[ ] available/reserved stock
[ ] low stock
[ ] stock adjustments
[ ] GRN
[ ] cost history
[ ] supplier return
[ ] supplier payment allocation
```

## Finance

```text
[ ] receivables
[ ] payables
[ ] cashbook
[ ] daily cash closing
```

## Tax & Reporting

```text
[ ] VAT configuration
[ ] snapshot calculations
[ ] VAT report
[ ] dashboard
[ ] all MVP reports
[ ] PDF
[ ] CSV
```

## System

```text
[ ] audit
[ ] backup
[ ] restore
[ ] single-PC
[ ] LAN
[ ] concurrency
[ ] idempotency
[ ] clean migration
```

---

# 62. Final Acceptance Decision

MVP acceptance is achieved only when the implemented system demonstrates that:

```text
business workflows work
AND
permissions are enforced
AND
transactions are atomic
AND
stock reconciles
AND
finance reconciles
AND
historical documents remain stable
AND
concurrent clients cannot corrupt state
AND
retries cannot duplicate financial transactions
AND
reports reconcile
AND
a verified backup can be restored
```

This is the release definition for Bizco MVP.

---

# 63. Next Project Step

The design and acceptance baseline is now:

```text
MVP.md v1.3
        ↓
DomainModel.md
        ↓
StateMachines.md
        ↓
DatabaseDesign.md
        ↓
ApiContracts.md
        ↓
AcceptanceTests.md          ← THIS DOCUMENT
```

The next activity is to synchronize `DevelopmentPlan.md` with these finalized design decisions.

The updated development plan should:

- preserve every MVP requirement;
- add the pre-development design gate as completed;
- update Flyway migrations to match `DatabaseDesign.md`;
- update server package/capability plan;
- incorporate acceptance test IDs into weekly delivery;
- add early backup/restore technical spike;
- add explicit concurrency/idempotency test milestones;
- keep the realistic solo-developer schedule rather than reducing scope.

After that synchronization, implementation can begin with project scaffolding and the first Flyway migrations.

---

*(End of Bizco MVP Acceptance Test Catalogue)*
