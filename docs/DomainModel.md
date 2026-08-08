# Bizco MVP Domain Model & Aggregate Design

**Project:** SME Business Management System (Bizco)  
**Document:** Domain Model & Aggregate Design  
**Version:** 1.0  
**Status:** Pre-implementation design baseline  
**Based On:** `SRS.md` v2.1, `MVP.md` v1.3, `DevelopmentPlan.md` v2.0  
**Target:** JavaFX client + Spring Boot server + PostgreSQL  
**Scope Rule:** This document preserves the complete MVP scope. It does not remove or defer any requirement contained in MVP v1.3.

---

# 1. Purpose

This document translates the approved Bizco MVP requirements into an implementation-oriented domain model.

It defines:

- business capability boundaries;
- aggregates and aggregate roots;
- entities and value objects;
- commands and business operations;
- state ownership;
- invariants;
- transaction boundaries;
- cross-module dependencies;
- domain events;
- repository responsibilities;
- authorization boundaries;
- historical-snapshot rules;
- concurrency and idempotency expectations.

This document is intentionally technology-aware but framework-independent at the domain level. JPA annotations, REST controllers, JavaFX controllers, database migrations, and report templates are implementation concerns built around this model.

---

# 2. Architecture Position

Bizco MVP is implemented as a **modular monolith** on the Spring Boot server.

```text
┌───────────────────────────────┐
│         JavaFX Client         │
│                               │
│ presentation + client state   │
└───────────────┬───────────────┘
                │ HTTPS / REST
                ▼
┌────────────────────────────────────────────────────────────┐
│                    Spring Boot Server                      │
│                                                            │
│ Identity │ Customer │ Catalog │ Sales │ Scheduling         │
│ Inventory│ Purchasing│ Finance│ Reporting│ System          │
│                                                            │
│ Domain + Application Services + Adapters                   │
└──────────────────────┬─────────────────────────────────────┘
                       │
                       ▼
                ┌──────────────┐
                │ PostgreSQL   │
                └──────────────┘
```

## 2.1 Architectural Rules

1. JavaFX never connects directly to PostgreSQL.
2. Every protected business operation is authorized on the server.
3. Business rules live in domain/application services, not JavaFX controllers.
4. JPA entities never cross the REST boundary.
5. `bizco-common` may contain stable DTO contracts, validation contracts, error codes, and stable enums, but not persistence entities or repositories.
6. Capability modules own their write models.
7. Cross-module writes occur through application services, not direct repository access across arbitrary modules.
8. Posted financial/stock documents are immutable.
9. `stock_movements` is the authoritative physical-stock ledger.
10. Receivable, payable, cashbook, and report balances reconcile from posted source records.
11. Critical posting operations are transactional and idempotent.
12. Reporting is a read concern and does not own transactional truth.
13. This is **not** an event-sourced system. Domain events are integration/application notifications around normal relational persistence.

---

# 3. Domain Capability Map

```text
                         ┌──────────────┐
                         │   Identity   │
                         │ users/RBAC   │
                         └──────┬───────┘
                                │ authorizes
                                ▼
┌──────────────┐        ┌──────────────┐        ┌──────────────┐
│   Customer   │───────▶│    Sales     │◀───────│   Catalog    │
│ customer/    │        │ invoice/POS  │        │ product/     │
│ credit       │        └──────┬───────┘        │ service/UOM  │
└──────┬───────┘               │                └──────┬───────┘
       │                       │                       │
       │                       ▼                       │
       │                ┌──────────────┐               │
       └───────────────▶│   Finance    │◀──────────────┘
                        │ AR/AP/cash   │
                        └──────┬───────┘
                               │
       ┌───────────────────────┼────────────────────────┐
       ▼                       ▼                        ▼
┌──────────────┐       ┌──────────────┐        ┌──────────────┐
│ Scheduling   │──────▶│  Inventory   │◀───────│ Purchasing   │
│ appt/job     │       │ stock ledger │        │ GRN/supplier │
└──────────────┘       └──────────────┘        └──────────────┘

             ┌───────────────────────────────────────┐
             │ Reporting / Dashboard / Audit Reads  │
             └───────────────────────────────────────┘

             ┌───────────────────────────────────────┐
             │ System / Config / Backup / Recovery   │
             └───────────────────────────────────────┘
```

---

# 4. Domain Modeling Vocabulary

## 4.1 Aggregate

An aggregate is a consistency boundary. One aggregate root controls changes to entities inside that boundary.

Example:

```text
Invoice (Aggregate Root)
├── InvoiceLine
└── invoice lifecycle state
```

A command that changes an invoice must enter through the `Invoice` aggregate or its application service.

## 4.2 Entity

An entity has identity and lifecycle.

Examples:

- User
- Customer
- Product
- Invoice
- Appointment
- JobCard
- GRN

## 4.3 Value Object

A value object is defined by value, is preferably immutable, and validates itself.

Examples:

- Money
- Quantity
- TaxRate
- Percentage
- DateRange
- AppointmentTimeRange
- DocumentNumber
- PhoneNumber
- EmailAddress

## 4.4 Domain Service

A domain service contains business logic that does not naturally belong to one entity.

Examples:

- CreditEligibilityPolicy
- InvoicePricingCalculator
- AppointmentConflictPolicy
- EffectivePermissionResolver

## 4.5 Application Service

An application service coordinates use cases, authorization, transactions, repositories, external adapters, and domain objects.

Example:

```text
PostSaleService
```

coordinates invoice posting, inventory, finance, reservations, numbering, and audit.

---

# 5. Shared Kernel / Common Contracts

The shared module must remain small.

## 5.1 Allowed Shared Concepts

```text
Money
CurrencyCode
Quantity
Percentage
BusinessDate
DateTimeRange
PageRequest/PageResult DTO contracts
ErrorCode
FieldError
API error contract
PermissionCode constants
stable public enums
IdempotencyKey
```

## 5.2 Money

MVP is LKR-focused.

```text
Money
├── amount: BigDecimal
└── currency: LKR
```

Rules:

- `BigDecimal` only.
- Never `double` or `float`.
- Monetary DB scale: `DECIMAL(15,2)`.
- Calculation precision must be higher internally where needed and rounded only at defined business boundaries.
- Equality includes amount and currency.

## 5.3 Quantity

```text
Quantity
└── value: BigDecimal
```

Database scale supports `DECIMAL(15,3)` for stock quantities.

Rules:

- transaction quantities must be positive unless represented as a signed ledger movement;
- stock movements themselves may be positive or negative but never zero.

## 5.4 Percentage / TaxRate

```text
Percentage
└── value: BigDecimal [0..100]
```

Used for:

- VAT rate;
- discount rate;
- threshold comparisons.

## 5.5 IdempotencyKey

```text
IdempotencyKey
└── UUID/string
```

The same committed command key must not create a second financial/stock posting.

---

# 6. Identity & Access Domain

## 6.1 Responsibility

Owns:

- users;
- roles;
- permissions;
- primary role;
- secondary temporary roles;
- login attempts;
- account locking;
- password lifecycle;
- server sessions;
- effective permissions;
- role-related audit events.

Does not own business transactions.

## 6.2 Aggregate: User

```text
User
├── userId
├── username
├── passwordHash
├── firstName
├── lastName
├── email
├── phone
├── primaryRoleId
├── active
├── locked
├── failedLoginAttempts
├── lockedUntil
├── passwordChangedAt
├── lastLoginAt
└── secondaryRoleAssignments [separate persisted entities]
```

### Commands

```text
CreateUser
UpdateUserProfile
ActivateUser
DeactivateUser
ChangePrimaryRole
ResetPassword
ChangeOwnPassword
LockUser
UnlockUser
GrantSecondaryRole
RevokeSecondaryRole
```

### Invariants

- username is unique;
- user always has exactly one primary role;
- inactive user cannot authenticate;
- locked user cannot authenticate until unlocked/expiry;
- five failed attempts trigger lock;
- password follows MVP policy;
- secondary role cannot duplicate the user's primary role;
- expired/revoked secondary role contributes no effective permission;
- the primary role cannot be revoked as if it were a secondary assignment.

## 6.3 Aggregate: Role

```text
Role
├── roleId
├── name
├── description
├── systemRole
└── permissionCodes
```

### Commands

```text
CreateCustomRole
RenameCustomRole
AssignPermission
RemovePermission
DeleteCustomRole
```

### Invariants

- role name unique;
- system roles are protected from deletion;
- a role assigned to active users cannot be deleted without reassignment;
- unknown permission code cannot be assigned;
- `SUPER_ADMIN` owns all registered MVP permissions.

## 6.4 Entity: SecondaryRoleAssignment

```text
SecondaryRoleAssignment
├── assignmentId
├── userId
├── roleId
├── grantedBy
├── grantedAt
├── expiresAt
├── active
├── revokedAt
├── revokedBy
└── revokeReason
```

### State

```text
ACTIVE
EXPIRED
REVOKED
```

Persistence may derive EXPIRED from `expiresAt` plus active flag, but the business semantics remain explicit.

## 6.5 Aggregate: UserSession

```text
UserSession
├── sessionId
├── userId
├── tokenHash
├── clientId
├── ipAddress
├── createdAt
├── lastActivityAt
├── expiresAt
├── revokedAt
└── revokedReason
```

### Commands

```text
OpenSession
TouchSession
RevokeSession
ForceLogoutUser
ExpireIdleSession
```

### Invariants

- session token stored only as a secure hash;
- maximum concurrent session rule enforced;
- 15-minute idle timeout;
- revoked/expired session cannot authorize;
- permission checks resolve current permissions instead of trusting a stale UI role snapshot.

## 6.6 Domain Service: EffectivePermissionResolver

```text
effectivePermissions(user, now)
=
primaryRole.permissions
UNION
permissions of active non-expired secondary roles
```

## 6.7 Security Boundary

All application services expose required permissions.

Example:

```text
PostInvoice              → invoice.create
VoidInvoice              → invoice.void
ApproveStockAdjustment   → inventory.adjustment.approve
RestoreBackup            → system.backup.restore
```

JavaFX may hide buttons, but the server always performs the authoritative check.

---

# 7. Customer Domain

## 7.1 Responsibility

Owns:

- customer master profile;
- customer category;
- credit limit;
- customer active/blocked status;
- searchable identity/contact information;
- consent fields included by MVP data model;
- credit eligibility decision inputs.

Finance owns receivable transactions. Sales owns invoices.

## 7.2 Aggregate: Customer

```text
Customer
├── customerId
├── customerCode
├── name
├── phone
├── email
├── address
├── nicNumber
├── brNumber
├── category
├── creditLimit
├── status
├── marketingConsent
├── dataSharingConsent
└── consentDate
```

### Commands

```text
CreateCustomer
UpdateCustomer
ChangeCustomerCategory
SetCreditLimit
BlockCustomer
ActivateCustomer
AnonymizeCustomer [where permitted by MVP rules]
```

### Invariants

- customer code unique;
- phone follows MVP Sri Lankan validation;
- credit limit cannot be negative;
- blocked status is enforced by sales eligibility rules;
- sensitive PII access requires permission;
- anonymization cannot silently delete transaction history.

## 7.3 Value Objects

```text
CustomerCode
PhoneNumber
EmailAddress
CreditLimit
CustomerCategory
```

## 7.4 Domain Service: CustomerCreditPolicy

Inputs:

```text
customer
currentReceivable
oldestOutstandingAge
requestedCreditAmount
```

Output:

```text
ALLOW
ALLOW_WITH_WARNING
CASH_ONLY
BLOCK_ALL_SALES
LIMIT_EXCEEDED
```

### Aging Rules

| Outstanding Age | Decision |
|---|---|
| 0–30 days | normal |
| 31–60 | warning |
| 61–90 | credit blocked, cash allowed |
| 91+ | all sales blocked until qualifying payment |

A credit sale must also remain within the customer's permitted credit limit.

## 7.5 Read Models

Customer screens may query composed read models:

```text
CustomerSummary
├── profile
├── currentReceivable
├── agingBuckets
├── lastPurchase
└── recentInvoices
```

The Customer aggregate does not persist authoritative receivable balance independently.

---

# 8. Catalog Domain

## 8.1 Responsibility

Owns:

- products;
- service catalog;
- categories;
- UOM reference data;
- selling/wholesale/cost master values;
- barcode;
- tax category;
- reorder point;
- active/inactive status.

Inventory owns physical stock.
Purchasing owns received purchase history.
Sales owns transaction-price snapshots.

## 8.2 Aggregate: Product

```text
Product
├── productId
├── sku
├── barcode
├── name
├── description
├── categoryId
├── uomId
├── productType
├── taxCategory
├── costPrice
├── sellingPrice
├── wholesalePrice
├── reorderPoint
└── active
```

### Commands

```text
CreateProduct
UpdateProductDetails
ChangeProductPrices
ChangeProductTaxCategory
ChangeReorderPoint
DeactivateProduct
ReactivateProduct
AssignBarcode
```

### Invariants

- SKU unique;
- barcode unique when present;
- selling/cost/wholesale prices cannot be negative;
- inventory product participates in stock ledger;
- service product does not produce stock movements;
- category must be valid/active according to category policy;
- only supported MVP product types are allowed.

## 8.3 Aggregate: ProductCategory

```text
ProductCategory
├── categoryId
├── name
├── parentId
├── description
└── active
```

### Invariants

- no circular parent chain;
- product is assigned to a leaf category under the MVP rule;
- category with dependent products/children cannot be destructively removed without business-safe reassignment/deactivation.

## 8.4 Aggregate: ServiceDefinition

```text
ServiceDefinition
├── serviceId
├── serviceCode
├── name
├── description
├── category
├── basePrice
├── estimatedDurationMinutes
├── requiresEstimate
├── warrantyDays
└── active
```

### Commands

```text
CreateService
UpdateService
ChangeServicePrice
ChangeDuration
SetEstimateRequirement
SetWarrantyDays
DeactivateService
```

### Invariants

- service code unique;
- base price non-negative;
- duration positive;
- warranty days non-negative.

## 8.5 UOM

Reference aggregate/table:

```text
Uom
├── uomId
├── code
├── name
└── category
```

MVP does not introduce Phase 2 UOM conversion complexity.

## 8.6 Pricing Policy

MVP price resolution:

```text
Customer category default tier
→ product price for resolved tier
→ authorized manual override
```

A resolved transaction price is copied into the invoice-line snapshot.

---

# 9. Sales Domain

## 9.1 Responsibility

Owns:

- POS/cart posting;
- draft invoices;
- posted sales/service/tax invoices;
- invoice lines;
- invoice-level and line-level discounts;
- invoice lifecycle;
- credit notes/customer returns;
- held bills;
- receipt/reprint metadata;
- official sales document numbering coordination;
- customer-facing payment association.

Inventory owns stock ledger entries.
Finance owns cashbook and receivable/payable read truth.
Catalog provides product/service master data.

## 9.2 Aggregate: Invoice

```text
Invoice
├── invoiceId
├── requestId
├── invoiceNumber [null while draft]
├── invoiceDate
├── dueDate
├── type
├── status
├── customerId
├── cashierId
├── businessSnapshot
├── customerSnapshot
├── InvoiceLine[]
├── invoiceDiscount
├── subtotal
├── taxableAmount
├── vatAmount
├── totalAmount
├── paymentStatus
├── notes
├── createdAt
├── postedAt
├── voidedAt
├── voidedBy
└── voidReason
```

## 9.3 Entity: InvoiceLine

```text
InvoiceLine
├── lineItemId
├── lineType
├── productId?
├── serviceId?
├── skuSnapshot
├── descriptionSnapshot
├── uomSnapshot
├── quantity
├── unitPrice
├── discount
├── taxCategorySnapshot
├── vatRateSnapshot
├── taxableAmount
├── vatAmount
└── lineTotalIncludingVat
```

### Line-Type Rules

```text
PRODUCT → productId required
SERVICE → serviceId required
CUSTOM  → description required; product/service reference optional/not required
```

Only PRODUCT invoice lines produce sale stock requirements.

## 9.4 Invoice State Machine

```text
           ┌─────────┐
           │  DRAFT  │
           └────┬────┘
                │ post
                ▼
           ┌─────────┐
           │ POSTED  │
           └────┬────┘
                │ authorized void
                ▼
           ┌─────────┐
           │ VOIDED  │
           └─────────┘
```

### DRAFT

- editable;
- no official invoice number;
- no stock effect;
- no receivable effect;
- no cashbook effect;
- no VAT/reporting effect.

### POSTED

- immutable;
- official number assigned;
- stock/finance effects committed atomically;
- printable/reportable.

### VOIDED

- original document retained;
- reason mandatory;
- reversal semantics recorded;
- not changed back to draft.

## 9.5 Invoice Commands

```text
CreateDraftInvoice
SetInvoiceCustomer
AddProductLine
AddServiceLine
AddCustomLine
ChangeLineQuantity
RemoveLine
ApplyLineDiscount
ApplyInvoiceDiscount
OverrideLinePrice
PostInvoice
VoidInvoice
ReprintInvoice
```

## 9.6 Discount Policy

```text
≤10%      → invoice.discount.apply
>10–25%   → invoice.discount.approve_25
>25%      → invoice.discount.approve_50
below cost→ invoice.sell_below_cost + reason
price edit→ invoice.override_price + reason
```

Approval identity/reason is captured in audit evidence.

## 9.7 Domain Service: InvoicePricingCalculator

Calculates:

```text
line gross
→ line discount
→ line taxable amount
→ line VAT
→ invoice subtotal
→ invoice-level discount allocation/calculation
→ taxable total
→ VAT total
→ grand total
```

The final posted line/header values are snapshotted.

## 9.8 Aggregate: HeldSale

```text
HeldSale
├── heldSaleId
├── heldNumber
├── customerId?
├── cashierId
├── status
├── heldAt
├── expiresAt
├── items[]
└── convertedInvoiceId?
```

### State Machine

```text
HELD
 ├── resume ─────▶ RESUMED
 ├── cancel ─────▶ CANCELLED
 ├── timeout ────▶ EXPIRED
 └── complete ───▶ CONVERTED
```

The persisted record may transition RESUMED back to HELD if the cart is held again, but only one active reservation representation is allowed.

### Invariants

- active held items reserve available stock;
- no physical `SALE` stock movement occurs while merely held;
- cancellation/expiry releases reservation;
- conversion to posted invoice releases reservation and posts sale stock atomically.

## 9.9 Aggregate: CreditNote

```text
CreditNote
├── creditNoteId
├── requestId
├── creditNoteNumber
├── originalInvoiceId
├── customerId
├── reason
├── lines[]
├── subtotal
├── vatAmount
├── totalAmount
├── status
├── issuedDate
└── issuedBy
```

### Invariants

- must reference a posted eligible invoice;
- returned quantity cannot exceed original sold quantity minus previous accepted returns;
- tax reversal is proportional to the original posted line snapshot;
- stock increases only for returnable/restockable goods according to the workflow;
- cash refund is separately traceable;
- credit note is immutable after posting.

## 9.10 Sale Posting Application Service

`PostSaleService` owns the transaction boundary.

```text
Authorize
→ Check idempotency key
→ Load draft/command
→ Validate customer credit/aging
→ Validate product/service state
→ Validate available stock
→ Validate discount approvals
→ Resolve current tax config
→ Freeze snapshots
→ Allocate official invoice number
→ Persist POSTED invoice
→ Post SALE stock movements
→ Persist payment/receivable effects
→ Post cashbook effects
→ Release held reservation if applicable
→ Audit
→ Commit
```

No partially committed sale is allowed.

## 9.11 Payment Status

```text
UNPAID
PARTIAL
PAID
CREDIT_NOTE
```

This is separate from `InvoiceStatus`.

For an immediate sale:

```text
balance must reach 0
```

For authorized credit:

```text
remaining balance becomes receivable
```

---

# 10. Inventory Domain

## 10.1 Responsibility

Owns:

- authoritative stock movement ledger;
- stock-on-hand query;
- reserved/available stock calculation support;
- stock adjustments and approval;
- low-stock determination.

It does not own product master prices or supplier documents.

## 10.2 Aggregate: StockMovement

A posted stock movement is an immutable ledger record.

```text
StockMovement
├── movementId
├── productId
├── movementType
├── signedQuantity
├── referenceType
├── referenceId
├── notes
├── createdBy
└── createdAt
```

### Movement Types

```text
GRN               +
SALE              -
CUSTOMER_RETURN   +
SUPPLIER_RETURN   -
JOB_PART          -
ADJUSTMENT        +/-
```

### Invariants

- quantity never zero;
- sign must agree with movement meaning except ADJUSTMENT, whose sign is determined by approved adjustment;
- product must be an inventory product;
- posted movement is never edited/deleted;
- movement references the source business transaction;
- duplicate movement set from the same idempotent posting must not be produced.

## 10.3 Stock Calculations

```text
Physical Stock
=
SUM(posted stock_movements.quantity)

Reserved Stock
=
SUM(active held_sale_item quantities)

Available Stock
=
Physical Stock - Reserved Stock
```

Any cached balance is a projection only.

## 10.4 Aggregate: StockAdjustment

```text
StockAdjustment
├── adjustmentId
├── requestId?
├── productId
├── type
├── quantity
├── reason
├── status
├── createdBy
├── createdAt
├── approvedBy
└── approvedAt
```

### State Machine

```text
PENDING
 ├── approve ──▶ APPROVED
 └── reject ───▶ REJECTED
```

A separate reversal transaction is used for correcting a posted approved adjustment.

### Invariants

- request quantity positive; adjustment type determines effect;
- reason required;
- approval permission required;
- stock movement is created exactly once on approval;
- rejected request has no stock effect.

## 10.5 Domain Service: StockAvailabilityService

Queries:

```text
physicalStock(product)
reservedStock(product)
availableStock(product)
canReserve(product, qty)
canConsume(product, qty)
```

Server-side stock validation occurs immediately before posting/reservation in the database transaction.

## 10.6 Low Stock

```text
lowStock = available/physical quantity <= reorderPoint
```

The selected exact reporting basis must remain consistent throughout dashboard and reports. For MVP operational ordering, available stock is the safer alert basis because held stock is not actually available for another sale.

---

# 11. Purchasing Domain

## 11.1 Responsibility

Owns:

- supplier master;
- GRN;
- GRN items;
- purchase cost history;
- supplier return;
- supplier payment document;
- payment allocation to GRNs;
- supplier outstanding calculation inputs.

No purchase-order/full supplier-invoice module exists in MVP.

## 11.2 Aggregate: Supplier

```text
Supplier
├── supplierId
├── supplierCode
├── name
├── contactPerson
├── address
├── phone
├── email
├── tinNumber
├── paymentTerms
├── openingBalance
└── status
```

### Commands

```text
CreateSupplier
UpdateSupplier
DeactivateSupplier
ReactivateSupplier
```

### Invariants

- supplier code unique;
- opening balance is controlled master opening data, not repeatedly mutated for transactions;
- inactive supplier cannot receive new normal GRNs without explicit business-safe handling.

## 11.3 Aggregate: GRN

```text
GRN
├── grnId
├── requestId
├── grnNumber
├── status
├── supplierId
├── grnDate
├── items[]
├── totalAmount
├── notes
├── createdBy
├── createdAt
└── postedAt
```

## 11.4 Entity: GRNItem

```text
GRNItem
├── grnItemId
├── productId
├── quantityReceived
├── unitCost
└── totalCost
```

### State Machine

```text
DRAFT
  │ post
  ▼
POSTED
  │ controlled reversal where required
  ▼
REVERSED
```

### Invariants

- posted GRN immutable;
- only inventory products may create stock receipt;
- quantity positive;
- unit cost non-negative;
- total is server calculated;
- posting creates stock, cost history, and payable effect in one transaction;
- GRN date follows MVP date validation.

## 11.5 ProductCostHistory

Immutable record:

```text
ProductCostHistory
├── productId
├── grnItemId
├── unitCost
└── effectiveAt
```

The product master's default cost may be updated by policy, but historical GRN/invoice/job values remain unchanged.

## 11.6 Aggregate: SupplierReturn

```text
SupplierReturn
├── supplierReturnId
├── requestId
├── returnNumber
├── supplierId
├── originalGrnId
├── items[]
├── totalAmount
├── reason
├── createdBy
└── createdAt
```

### Invariants

- original received item required;
- returned quantity cannot exceed eligible received quantity net of earlier returns;
- stock decreases exactly once;
- payable reduces exactly once;
- return is immutable after posting.

## 11.7 Aggregate: SupplierPayment

```text
SupplierPayment
├── supplierPaymentId
├── requestId
├── supplierId
├── paymentDate
├── paymentMethod
├── totalAmount
├── referenceNumber
├── notes
├── paidBy
└── allocations[]
```

## 11.8 Entity: SupplierPaymentAllocation

```text
SupplierPaymentAllocation
├── allocationId
├── grnId
└── amount
```

### Invariants

- payment amount > 0;
- sum(allocations) <= payment total;
- every allocated GRN belongs to same supplier;
- allocation cannot exceed GRN's available outstanding amount;
- duplicate allocation to the same GRN inside a payment is not allowed;
- unallocated supplier payment behavior, if accepted by UI, must remain explicitly identifiable; MVP screens primarily allocate to outstanding GRNs;
- posted payment immutable.

## 11.9 GRN Posting Application Service

```text
Authorize
→ idempotency check
→ validate supplier/items
→ allocate GRN number
→ persist GRN POSTED
→ post GRN stock movements
→ create product cost history
→ create payable source effect
→ audit
→ commit
```

---

# 12. Finance Domain

## 12.1 Responsibility

Owns:

- receivable read/derived balance;
- payable read/derived balance;
- cashbook;
- manual cash receipts/expenses;
- daily cash closing;
- allocation/reconciliation services;
- monetary source-to-ledger traceability.

MVP does **not** implement full GL/double-entry financial statements.

## 12.2 Receivable Model

Receivable is a derived financial position, not an independently editable customer balance.

```text
Receivable
=
posted invoice amounts
- posted customer payments
- applied credit notes
± qualifying reversals/refunds
```

Read models may expose:

```text
CustomerReceivableSummary
├── customerId
├── totalOutstanding
├── aging0To30
├── aging31To60
├── aging61To90
├── aging90Plus
└── openDocuments[]
```

## 12.3 Customer Payment

The current MVP API remains invoice-friendly, but the domain should allow a payment to be represented independently enough to support receivable reconciliation.

```text
CustomerPayment
├── paymentId
├── requestId
├── customerId
├── paymentDate
├── paymentMethod
├── amount
├── reference
├── receivedBy
└── allocations[]
```

For simple POS immediate payment, the allocation is to the just-posted invoice.

A future UI need not be created now beyond the MVP, but the model must not prevent valid partial/credit settlement behavior already required by MVP.

## 12.4 Aggregate: CashbookEntry

Posted cashbook entries are immutable.

```text
CashbookEntry
├── entryId
├── entryDate
├── direction
├── source
├── amount
├── paymentMethod
├── category?
├── referenceType
├── referenceId
├── reason?
├── createdBy
├── reversedEntryId?
└── createdAt
```

### Sources

```text
CUSTOMER_PAYMENT
REFUND
SUPPLIER_PAYMENT
MANUAL
```

### Invariants

- amount > 0;
- direction explicit;
- system-generated entries must reference source transaction;
- one monetary source cannot accidentally produce duplicate equivalent cashbook postings;
- correction is via reversal, not in-place edit.

## 12.5 Manual Cashbook Command

Requires `finance.cashbook.create`.

```text
RecordManualCashReceipt
RecordManualCashExpense
ReverseManualCashEntry
```

Reason/category/reference are required according to business rule.

## 12.6 Aggregate: CashClosing

```text
CashClosing
├── cashClosingId
├── requestId
├── businessDate
├── cashierId
├── expectedCash
├── countedCash
├── variance
├── varianceReason
├── status
├── closedBy
├── closedAt
├── approvedBy
└── approvedAt
```

### State Machine

```text
if variance = 0:
    create → APPROVED/complete according to configured rule

if variance != 0:
    create → PENDING_APPROVAL
                  │ manager approval
                  ▼
               APPROVED
```

### Invariants

- one closing per business date + cashier;
- expected cash calculated by server from eligible posted cashbook records;
- variance = counted - expected;
- non-zero variance requires reason;
- approval requires `finance.cash_closing.approve`;
- approved closing immutable.

## 12.7 Finance Reconciliation Services

```text
ReceivableReconciliationService
PayableReconciliationService
CashbookReconciliationService
CashClosingCalculationService
```

These services are heavily tested against seeded acceptance data.

---

# 13. Scheduling Domain

## 13.1 Responsibility

Owns:

- appointments;
- appointment status;
- technician assignment;
- calendar time;
- double-booking rules;
- appointment-to-job conversion reference.

## 13.2 Aggregate: Appointment

```text
Appointment
├── appointmentId
├── appointmentNumber
├── customerId
├── serviceId
├── appointmentDate
├── startTime
├── endTime
├── technicianId?
├── status
├── notes
├── walkIn
├── convertedJobCardId?
├── createdBy
└── createdAt
```

## 13.3 Appointment State Machine

```text
SCHEDULED
   ├── confirm ─────────▶ CONFIRMED
   ├── start ───────────▶ IN_PROGRESS
   ├── no-show ─────────▶ NO_SHOW
   └── cancel ──────────▶ CANCELLED

CONFIRMED
   ├── start ───────────▶ IN_PROGRESS
   ├── no-show ─────────▶ NO_SHOW
   └── cancel ──────────▶ CANCELLED

IN_PROGRESS
   └── complete ────────▶ COMPLETED
```

Walk-in:

```text
create walk-in appointment
→ immediate conversion/start according to authorized workflow
```

## 13.4 Commands

```text
CreateAppointment
UpdateAppointmentDetails
AssignTechnician
RescheduleAppointment
ConfirmAppointment
StartAppointment
CompleteAppointment
MarkNoShow
CancelAppointment
ConvertAppointmentToJobCard
```

## 13.5 Value Object: AppointmentTimeRange

```text
AppointmentTimeRange
├── date
├── startTime
└── endTime
```

Rules:

- end > start;
- duration normally derives from service estimated duration;
- business hours and configured buffer applied.

## 13.6 Domain Service: AppointmentConflictPolicy

Conflicting active appointment:

```text
same technician
AND active status
AND requested.start < existing.end + relevant buffer
AND requested.end + relevant buffer > existing.start
```

Final correctness is enforced transactionally/database-side.

JavaFX calendar checks are advisory only.

## 13.7 Technician Capability

`technicianId` references a User, but technician assignment is operational capability, not permission-role identity.

Minimum MVP representation:

```text
User/Staff eligibility for technician assignment
```

may be implemented by a specific profile flag/query/configuration without introducing Phase 2 skill management.

---

# 14. Job Card / Service Work Domain

## 14.1 Responsibility

Owns:

- job cards;
- device/item intake details;
- job services;
- job parts;
- estimates;
- job lifecycle;
- warranty dates;
- conversion from appointment;
- readiness/pickup/completion rules.

## 14.2 Aggregate: JobCard

```text
JobCard
├── jobCardId
├── jobNumber
├── appointmentId?
├── customerId
├── deviceType
├── brand
├── model
├── serialNumber
├── reportedIssue
├── customerNotes
├── accessoriesReceived
├── deviceCondition
├── status
├── technicianId?
├── estimatedCompletionDate
├── actualCompletionDate
├── pickupDate
├── warrantyEndDate
├── jobServices[]
├── jobParts[]
├── estimates[]
└── createdAt
```

## 14.3 Entity: JobService

```text
JobService
├── jobServiceId
├── serviceId
├── estimatedCost
├── actualCost
├── estimatedDurationMinutes
├── status
└── notes
```

Status:

```text
PENDING
IN_PROGRESS
COMPLETED
```

## 14.4 Entity: JobPart

```text
JobPart
├── jobPartId
├── productId
├── quantityUsed
├── customerUnitPriceSnapshot
├── costPriceSnapshot
└── warrantyCovered
```

Adding a job part is a transactional business command that creates a `JOB_PART` stock movement exactly once.

## 14.5 Entity/Aggregate Child: JobEstimate

```text
JobEstimate
├── estimateId
├── estimatedTotal
├── description
├── createdBy
├── createdAt
├── customerResponse
├── customerResponseAt
└── notes
```

Responses:

```text
PENDING
ACCEPTED
DECLINED
```

## 14.6 Job Card State Machine

```text
CREATED
 ├── estimate needed ─────▶ ESTIMATE_PENDING
 └── authorized skip ─────▶ IN_PROGRESS

ESTIMATE_PENDING
 ├── accept ───────────────▶ ESTIMATE_APPROVED
 └── decline ──────────────▶ CANCELLED

ESTIMATE_APPROVED
 └── start work ───────────▶ IN_PROGRESS

IN_PROGRESS
 └── complete services ────▶ READY_FOR_PICKUP

READY_FOR_PICKUP
 └── payment + pickup ─────▶ COMPLETED

Permitted active states
 └── reason required ──────▶ CANCELLED
```

## 14.7 Job Commands

```text
CreateJobCard
CreateJobFromAppointment
AssignJobTechnician
AddJobService
StartJobService
CompleteJobService
CreateEstimate
AcceptEstimate
DeclineEstimate
AddJobPart
MoveJobToInProgress
MarkReadyForPickup
CompleteJobAndPickup
CancelJob
```

## 14.8 Job Invariants

- customer required;
- appointment can map to at most one converted job card;
- estimate-required service follows estimate approval unless authorized override;
- a part may only be consumed from an inventory product;
- part stock must be available at posting time;
- every added part produces exactly one immutable stock movement;
- moving to READY_FOR_PICKUP requires service completion rules;
- moving to COMPLETED requires pickup/payment conditions in the MVP workflow;
- cancellation reason required;
- warranty dates derive from configured service/job rules at pickup.

## 14.9 Service Invoice Generation

A service invoice can contain:

```text
SERVICE lines from completed JobService
+
PRODUCT lines for customer-charged JobPart
+
CUSTOM lines for permitted additional charges
```

Values are copied as transaction snapshots.

---

# 15. Tax Domain / Tax Policy

## 15.1 Responsibility

MVP tax is intentionally basic:

- VAT enable/disable;
- configurable VAT rate;
- Standard / Exempt / Zero-rated categories;
- business TIN;
- tax invoice generation;
- VAT report from posted snapshots.

SSCL, WHT, statutory e-invoicing integration, and full tax accounting remain outside MVP as already defined.

## 15.2 Aggregate: TaxConfiguration

```text
TaxConfiguration
├── vatEnabled
├── vatRate
├── businessTin
├── changedBy
└── changedAt
```

All changes audited.

## 15.3 Domain Service: TaxCalculator

Inputs:

```text
tax configuration
line tax category
discounted taxable line amount
```

Output:

```text
TaxSnapshot
├── category
├── rate
├── taxableAmount
└── vatAmount
```

## 15.4 Historical Rule

Changing tax configuration affects new postings only.

```text
Current configuration
→ posting
→ snapshot
→ immutable historical report value
```

## 15.5 Invoice QR

MVP QR is an internal Bizco invoice/reference QR.

It does not mean:

- IRD gateway submission;
- statutory digital signature;
- Phase 2 e-Invoicing integration.

---

# 16. Reporting Domain

## 16.1 Responsibility

Reporting is a read-model capability.

It owns:

- report query services;
- dashboard query services;
- export orchestration;
- report-specific DTOs;
- aggregation queries.

It does **not** own transactional source data.

## 16.2 Dashboard Read Model

```text
DashboardKpis
├── todaySales
├── monthlyRevenue
├── outstandingReceivables
├── outstandingPayables
├── lowStockCount
├── todayAppointments
└── pendingJobCards
```

## 16.3 MVP Reports

```text
Daily Sales
Sales by Product
Sales by Payment Method
Stock on Hand
Low Stock
Customer Balances
Supplier Balances
Cashbook
Daily Cash Closing
Appointment Summary
Job Card Status
Tax Summary
```

## 16.4 Reporting Invariant

Every KPI/report definition has one canonical calculation and reconciles to source documents/ledgers.

Example:

```text
Daily Sales
=
eligible POSTED sales/service/tax invoices
for business date
less qualifying posted credit-note impact
excluding DRAFT
handling VOIDED according to explicit report definition
```

## 16.5 Export

PDF/CSV is an output adapter concern.

Report application service:

```text
query → report DTO → PDF/CSV renderer
```

JasperReports/PDFBox must not contain authoritative business calculations that differ from server query logic.

---

# 17. System & Configuration Domain

## 17.1 Responsibility

Owns:

- business profile;
- system configuration;
- first-run setup;
- backup history;
- backup creation;
- restore coordination;
- maintenance-mode recovery guard.

## 17.2 Aggregate: BusinessProfile

```text
BusinessProfile
├── businessName
├── address
├── city/province/postalCode
├── phone
├── email
├── website
├── tinNumber
├── vatRegistered
├── vatRate/config reference
└── logoReference
```

Historical documents store snapshots and do not re-render old invoices from only the current profile.

## 17.3 Aggregate: SystemConfiguration

Key/value configuration with controlled known keys.

```text
business.*
tax.*
pos.*
appointment.*
job_card.*
return.*
backup.*
finance.*
```

Commands must validate type/range instead of accepting arbitrary invalid strings.

## 17.4 Aggregate: BackupRecord

```text
BackupRecord
├── backupId
├── fileName
├── fileSize
├── sha256
├── status
├── initiatedBy
├── startedAt
├── completedAt
└── errorMessage
```

Status:

```text
STARTED
VERIFIED
FAILED
RESTORED
```

## 17.5 Backup Commands

```text
CreateBackup
VerifyBackup
RestoreBackup
ApplyRetentionPolicy
```

## 17.6 Backup Invariants

- only authorized user may create/restore;
- restore requires Super Administrator permission;
- restore requires maintenance-safe condition and active-session guard;
- checksum recorded;
- failed operation remains visible/auditable;
- successful dump alone is not enough for `VERIFIED`;
- release acceptance requires successful clean restore verification.

---

# 18. Audit Domain / Audit Capability

Audit is cross-cutting but persistence belongs to the system/identity infrastructure area.

## 18.1 AuditEvent

```text
AuditEvent
├── eventId
├── entityType
├── entityId
├── actionCode
├── actorUserId?
├── occurredAt
├── changedFields/details
├── ipAddress/clientId?
└── correlation/requestId?
```

## 18.2 Required Event Families

```text
USER_CREATED
LOGIN_SUCCEEDED
LOGIN_FAILED
USER_LOCKED
SECONDARY_ROLE_GRANTED
SECONDARY_ROLE_REVOKED
SECONDARY_ROLE_EXPIRED
ROLE_PERMISSION_CHANGED

INVOICE_POSTED
INVOICE_VOIDED
DISCOUNT_APPROVED
PRICE_OVERRIDDEN
CREDIT_NOTE_POSTED
REFUND_POSTED

GRN_POSTED
SUPPLIER_RETURN_POSTED
SUPPLIER_PAYMENT_POSTED

STOCK_ADJUSTMENT_CREATED
STOCK_ADJUSTMENT_APPROVED
STOCK_ADJUSTMENT_REJECTED

CASH_CLOSING_CREATED
CASH_CLOSING_APPROVED

BACKUP_STARTED
BACKUP_VERIFIED
BACKUP_FAILED
RESTORE_STARTED
RESTORE_COMPLETED
RESTORE_FAILED
```

Audit logs are append-oriented and never silently rewritten.

---

# 19. Aggregate Ownership Matrix

| Capability | Aggregate Root | Owns Writes To | Reads From |
|---|---|---|---|
| Identity | User | users, role assignments | roles, sessions |
| Identity | Role | roles, role_permissions | permissions |
| Identity | UserSession | sessions | user/effective permissions |
| Customer | Customer | customers | finance receivable read model |
| Catalog | Product | products | category/UOM |
| Catalog | ProductCategory | categories | product references |
| Catalog | ServiceDefinition | services | — |
| Sales | Invoice | invoice header/lines | customer, catalog, tax |
| Sales | HeldSale | held sales/items | catalog, stock availability |
| Sales | CreditNote | credit notes/lines | original invoice |
| Inventory | StockAdjustment | adjustment request | product, stock |
| Inventory | StockMovement | immutable ledger | source document |
| Purchasing | Supplier | suppliers | — |
| Purchasing | GRN | grn/items | supplier, product |
| Purchasing | SupplierReturn | return/items | original GRN |
| Purchasing | SupplierPayment | payment/allocations | supplier, GRN balances |
| Finance | CustomerPayment | payment/allocations | invoice/receivable |
| Finance | CashbookEntry | cashbook | monetary source |
| Finance | CashClosing | closing | cashbook |
| Scheduling | Appointment | appointments | customer/service/user |
| Service Work | JobCard | job/services/parts/estimates | appointment/customer/catalog |
| Tax | TaxConfiguration | tax config | business profile |
| System | BusinessProfile | profile | — |
| System | BackupRecord | backup history | runtime/server state |

---

# 20. Cross-Module Dependency Rules

## 20.1 Allowed Direction

A module may call another module's published application interface/read service.

Example:

```text
Sales → CustomerCreditQuery
Sales → ProductQuery
Sales → StockAvailabilityService
Sales → StockPostingPort/Application Service
Sales → FinancePostingService
Sales → TaxCalculator
```

## 20.2 Prohibited Coupling

Do not do:

```java
salesService -> stockMovementJpaRepository.save(...)
```

across module ownership boundaries.

Instead:

```java
salesService -> inventoryPostingService.postSale(...)
```

This protects invariants.

## 20.3 Suggested Server Packages

```text
com.bizco
├── identity
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
├── customer
├── catalog
├── sales
├── scheduling
├── servicework
├── inventory
├── purchasing
├── finance
├── tax
├── reporting
└── system
```

`servicework` may remain under `scheduling` if the team wants to match the DevelopmentPlan's top-level package exactly. If so:

```text
scheduling/
├── appointment/
└── jobcard/
```

is preferable to adding a new top-level Maven module.

---

# 21. Command Catalogue

## 21.1 Identity

```text
Login
Logout
ChangePassword
ResetPassword
CreateUser
UpdateUser
ActivateUser
DeactivateUser
LockUser
UnlockUser
CreateRole
UpdateRole
DeleteRole
GrantSecondaryRole
RevokeSecondaryRole
```

## 21.2 Customer

```text
CreateCustomer
UpdateCustomer
SetCreditLimit
ChangeCustomerStatus
AnonymizeCustomer
```

## 21.3 Catalog

```text
CreateProduct
UpdateProduct
DeactivateProduct
CreateCategory
UpdateCategory
DeleteCategorySafely
CreateService
UpdateService
DeactivateService
```

## 21.4 Sales

```text
CreateDraftInvoice
UpdateDraftInvoice
PostInvoice
VoidInvoice
RecordInvoicePayment
CreateCreditNote
PostCreditNote
HoldSale
ResumeHeldSale
CancelHeldSale
ReprintReceipt
```

## 21.5 Scheduling / Jobs

```text
CreateAppointment
RescheduleAppointment
ChangeAppointmentStatus
ConvertAppointmentToJob
CreateJobCard
UpdateJobCard
CreateEstimate
RespondToEstimate
AddJobPart
ChangeJobStatus
CompleteJob
```

## 21.6 Inventory

```text
CreateStockAdjustment
ApproveStockAdjustment
RejectStockAdjustment
ReverseStockAdjustment
```

## 21.7 Purchasing

```text
CreateSupplier
UpdateSupplier
DeactivateSupplier
CreateDraftGRN
PostGRN
CreateSupplierReturn
PostSupplierReturn
RecordSupplierPayment
```

## 21.8 Finance

```text
RecordCustomerPayment
RecordManualCashReceipt
RecordManualCashExpense
ReverseCashbookEntry
CreateCashClosing
ApproveCashClosing
```

## 21.9 System

```text
UpdateBusinessProfile
UpdateTaxConfiguration
UpdateSystemConfiguration
CreateBackup
RestoreBackup
```

---

# 22. Domain Event Catalogue

Events are useful for audit, UI refresh, and controlled cross-capability reactions.

## 22.1 Identity

```text
UserCreated
UserLocked
UserUnlocked
SecondaryRoleGranted
SecondaryRoleRevoked
SecondaryRoleExpired
```

## 22.2 Sales

```text
InvoicePosted
InvoiceVoided
PaymentRecorded
CreditNotePosted
HeldSaleCreated
HeldSaleReleased
```

## 22.3 Inventory

```text
StockMovementPosted
StockAdjustmentApproved
```

## 22.4 Purchasing

```text
GrnPosted
SupplierReturnPosted
SupplierPaymentPosted
```

## 22.5 Scheduling

```text
AppointmentCreated
AppointmentRescheduled
AppointmentConvertedToJob
JobCardReadyForPickup
JobCardCompleted
JobPartConsumed
```

## 22.6 Finance/System

```text
CashClosingCreated
CashClosingApproved
BackupVerified
RestoreCompleted
```

Events should preferably be emitted after successful commit or stored/handled in a transactionally safe manner where they drive durable behavior.

---

# 23. Transaction Boundary Catalogue

The transaction boundaries from MVP v1.3 are retained.

## 23.1 Post Sale

```text
Invoice
+ stock movements
+ customer payment/receivable effect
+ cashbook effect
+ held-reservation release
+ audit
```

One PostgreSQL transaction.

## 23.2 Post Credit Note / Return

```text
Credit note
+ returned-stock movement where eligible
+ receivable/refund effect
+ cashbook effect if refund
+ VAT reversal snapshot
+ audit
```

One PostgreSQL transaction.

## 23.3 Post GRN

```text
GRN
+ stock movements
+ cost history
+ supplier payable effect
+ audit
```

One PostgreSQL transaction.

## 23.4 Supplier Return

```text
supplier return
+ stock movement
+ payable reduction
+ audit
```

One PostgreSQL transaction.

## 23.5 Supplier Payment

```text
supplier payment
+ allocations
+ payable reduction
+ cashbook OUT
+ audit
```

One PostgreSQL transaction.

## 23.6 Job Part Usage

```text
JobPart
+ JOB_PART stock movement
+ audit
```

One PostgreSQL transaction.

## 23.7 Stock Adjustment Approval

```text
adjustment state change
+ ADJUSTMENT stock movement
+ audit
```

One PostgreSQL transaction.

## 23.8 Daily Cash Closing

```text
expected cash calculation
+ closing
+ variance
+ approval state
+ audit
```

One PostgreSQL transaction.

---

# 24. Concurrency Strategy by Domain

| Risk | Required Control |
|---|---|
| duplicate invoice/document number | atomic row lock/upsert on `document_sequences` |
| duplicate barcode/SKU | DB unique constraint + friendly 409 |
| double appointment | server transaction + PostgreSQL conflict protection |
| overselling physical stock | authoritative availability check in posting transaction |
| duplicate network retry | unique idempotency/request key |
| double stock-adjustment approval | state check/optimistic or pessimistic locking |
| double supplier allocation | transaction + outstanding-balance validation |
| duplicate cash closing | unique `(business_date, cashier_id)` |
| stale role permission | server resolves current role state |
| two users edit same draft | optimistic locking/version column recommended |

## 24.1 Optimistic Locking

Mutable aggregates should normally have:

```text
version
```

Examples:

- draft invoice;
- customer;
- product;
- appointment;
- pending job card;
- pending stock adjustment;
- draft GRN.

JPA can map this using `@Version`.

Posted immutable records generally do not require normal edit-version semantics.

---

# 25. Idempotency Strategy

## 25.1 Commands Requiring Idempotency

At minimum:

```text
PostInvoice
RecordPayment
PostCreditNote
PostGRN
PostSupplierReturn
RecordSupplierPayment
ApproveStockAdjustment
CreateCashClosing
```

## 25.2 Behavior

```text
Client generates requestId
→ sends command
→ server checks existing committed result for requestId
    ├── exists → return same result
    └── absent → execute and commit
```

If request ID exists with incompatible payload semantics, return a conflict rather than silently executing a different command.

---

# 26. Document Numbering Domain Service

## 26.1 Document Types

```text
INV
CN
APT
JC
GRN
SUPPLIER_RETURN
HELD
```

Formats remain per MVP requirements.

## 26.2 Posted Financial Numbering

Official invoice and credit-note numbers are allocated inside their posting transaction.

Never use:

```text
SELECT MAX(number) + 1
```

Use `document_sequences` with row-level concurrency control.

Draft invoices do not consume official invoice numbers.

---

# 27. Historical Snapshot Policy

## 27.1 Must Snapshot On Posting

Invoice/header as applicable:

```text
business name
business address
business TIN
customer name
customer address
customer TIN
```

Invoice line:

```text
SKU
description
UOM
unit price
discount
tax category
VAT rate
taxable amount
VAT amount
line total
```

Job part:

```text
cost price at usage
customer charge price
```

GRN:

```text
received unit cost
```

## 27.2 Master Data Changes

A later change to:

```text
Product
Customer
Service
BusinessProfile
TaxConfiguration
```

must never change the factual rendering or tax calculation of a posted historical document.

---

# 28. Domain Error Catalogue

Application/domain errors should map to stable error codes.

Examples:

```text
AUTH_INVALID_CREDENTIALS
AUTH_ACCOUNT_LOCKED
AUTH_PERMISSION_DENIED
AUTH_SESSION_EXPIRED

CUSTOMER_NOT_FOUND
CUSTOMER_CREDIT_LIMIT_EXCEEDED
CUSTOMER_CREDIT_BLOCKED_BY_AGING
CUSTOMER_SALES_BLOCKED

PRODUCT_NOT_FOUND
PRODUCT_INACTIVE
PRODUCT_BARCODE_DUPLICATE
PRODUCT_SKU_DUPLICATE

INVOICE_NOT_DRAFT
INVOICE_ALREADY_POSTED
INVOICE_INVALID_STATE
INVOICE_PAYMENT_REQUIRED
INVOICE_CREDIT_CUSTOMER_REQUIRED
INVOICE_DISCOUNT_APPROVAL_REQUIRED
INVOICE_BELOW_COST_APPROVAL_REQUIRED

STOCK_INSUFFICIENT
STOCK_ADJUSTMENT_ALREADY_DECIDED

APPOINTMENT_CONFLICT
APPOINTMENT_INVALID_TRANSITION

JOB_ESTIMATE_REQUIRED
JOB_INVALID_TRANSITION
JOB_PART_STOCK_INSUFFICIENT

GRN_NOT_DRAFT
GRN_RETURN_EXCEEDS_RECEIVED

SUPPLIER_PAYMENT_OVERALLOCATION

CASH_CLOSING_ALREADY_EXISTS
CASH_VARIANCE_REASON_REQUIRED
CASH_CLOSING_APPROVAL_REQUIRED

BACKUP_FAILED
BACKUP_INVALID
RESTORE_ACTIVE_SESSIONS
RESTORE_FAILED

IDEMPOTENCY_CONFLICT
CONCURRENT_MODIFICATION
```

REST adapter maps these to the MVP error response format and suitable HTTP status.

---

# 29. Repository Interfaces

Repositories belong to domain/application abstractions, implemented by infrastructure/JPA.

Examples:

```text
UserRepository
RoleRepository
UserSessionRepository

CustomerRepository

ProductRepository
CategoryRepository
ServiceRepository

InvoiceRepository
HeldSaleRepository
CreditNoteRepository

StockMovementRepository
StockAdjustmentRepository

SupplierRepository
GrnRepository
SupplierReturnRepository
SupplierPaymentRepository

AppointmentRepository
JobCardRepository

CashbookRepository
CashClosingRepository

BusinessProfileRepository
BackupRecordRepository
```

Do not expose a generic base repository as the only domain API where aggregate-specific operations matter.

---

# 30. Read Query Interfaces

High-volume screens/reports should not need to hydrate entire write aggregates.

Examples:

```text
ProductSearchQuery
CustomerSearchQuery
InvoiceHistoryQuery
StockOnHandQuery
StockMovementHistoryQuery
SupplierStatementQuery
ReceivableQuery
PayableQuery
CashbookQuery
AppointmentCalendarQuery
JobCardListQuery
DashboardQuery
ReportQuery
AuditLogQuery
```

These may use optimized projections/native SQL where justified and tested.

---

# 31. Java Implementation Guidance

## 31.1 Domain Layer

Prefer plain Java.

```java
public final class Invoice {
    private final UUID id;
    private InvoiceStatus status;
    private final List<InvoiceLine> lines;
    // domain behavior
}
```

Domain behavior methods:

```text
addLine()
applyDiscount()
post()
voidInvoice()
```

rather than public setters for every field.

## 31.2 Application Layer

Example:

```text
PostInvoiceUseCase
CreateAppointmentUseCase
PostGrnUseCase
ApproveStockAdjustmentUseCase
```

Responsibilities:

- authorization;
- transaction;
- repository loading;
- call domain methods;
- coordinate other module ports/services;
- persist;
- audit;
- return DTO.

## 31.3 API Layer

Responsibilities:

- HTTP mapping;
- validation of request shape;
- authentication context extraction;
- DTO conversion;
- error translation.

No authoritative business calculation belongs in controllers.

## 31.4 Infrastructure Layer

Contains:

- JPA mappings/repositories;
- PostgreSQL-specific queries/constraints;
- Flyway;
- PDF/Jasper/PDFBox;
- `pg_dump`/`pg_restore` adapters;
- password hashing adapter;
- clock implementation;
- printing adapters where server-side.

## 31.5 JavaFX Client

Feature-based:

```text
client/
├── shell
├── identity
├── customer
├── catalog
├── sales
├── scheduling
├── inventory
├── purchasing
├── finance
├── reporting
└── system
```

Each UI feature should have:

```text
View/FXML
Controller/ViewModel
API Client
UI DTO mapping
```

JavaFX should not recreate business rules as authoritative truth.

---

# 32. Suggested Aggregate Classes by Module

## Identity

```text
User
Role
SecondaryRoleAssignment
UserSession
```

## Customer

```text
Customer
```

## Catalog

```text
Product
ProductCategory
ServiceDefinition
Uom
```

## Sales

```text
Invoice
InvoiceLine
HeldSale
HeldSaleItem
CreditNote
CreditNoteLine
```

## Scheduling

```text
Appointment
JobCard
JobService
JobPart
JobEstimate
```

## Inventory

```text
StockMovement
StockAdjustment
```

## Purchasing

```text
Supplier
GRN
GRNItem
ProductCostHistory
SupplierReturn
SupplierReturnItem
SupplierPayment
SupplierPaymentAllocation
```

## Finance

```text
CustomerPayment
CustomerPaymentAllocation
CashbookEntry
CashClosing
```

## Tax/System

```text
TaxConfiguration
BusinessProfile
SystemConfiguration
BackupRecord
AuditEvent
```

---

# 33. State Machine Summary

| Aggregate | States |
|---|---|
| Invoice | DRAFT → POSTED → VOIDED |
| HeldSale | HELD / RESUMED → CONVERTED, CANCELLED, EXPIRED |
| CreditNote | ISSUED → APPLIED |
| Appointment | SCHEDULED → CONFIRMED/IN_PROGRESS → COMPLETED; NO_SHOW/CANCELLED |
| JobCard | CREATED → ESTIMATE_PENDING → ESTIMATE_APPROVED → IN_PROGRESS → READY_FOR_PICKUP → COMPLETED; CANCELLED |
| JobService | PENDING → IN_PROGRESS → COMPLETED |
| JobEstimate | PENDING → ACCEPTED/DECLINED |
| StockAdjustment | PENDING → APPROVED/REJECTED |
| GRN | DRAFT → POSTED → REVERSED |
| SecondaryRole | ACTIVE → EXPIRED/REVOKED |
| CashClosing | PENDING_APPROVAL → APPROVED, or direct approved completion where no variance according to rule |
| BackupRecord | STARTED → VERIFIED/FAILED; VERIFIED → RESTORED after successful restore record/update |

Detailed transition tables belong in the next `StateMachines.md` document.

---

# 34. Business Scenario Traceability

## SC-01 Retail / Trading

Primary aggregates:

```text
Supplier
GRN
Product
Customer(optional)
HeldSale(optional)
Invoice
StockMovement
CustomerPayment
CashbookEntry
CashClosing
CreditNote(return)
```

## SC-02 Wholesale / Credit Trading

Primary aggregates:

```text
Customer
Invoice
CustomerPayment
Receivable read model
CreditNote
CashbookEntry
```

Critical rules:

```text
credit limit
aging
partial payment
outstanding balance
```

## SC-03 Repair / Maintenance

Primary aggregates:

```text
Customer
Appointment
JobCard
JobEstimate
JobPart
StockMovement
Invoice
Payment
```

## SC-04 Appointment Service

Primary aggregates:

```text
Customer
ServiceDefinition
Appointment
JobCard where applicable
Invoice
```

Critical rule:

```text
technician overlap protection
```

## SC-05 Hybrid Product + Service

Primary aggregates:

```text
Product
ServiceDefinition
JobCard
Invoice with PRODUCT/SERVICE/CUSTOM lines
StockMovement
Payment/Receivable
```

## Recovery

Primary aggregates/capabilities:

```text
BackupRecord
UserSession
AuditEvent
SystemConfiguration
```

---

# 35. MVP Requirements Coverage Check

This domain design explicitly preserves:

- Authentication and action-based RBAC
- Primary + temporary secondary roles
- User management and login history
- Customer CRUD/search/credit limits
- Product/category/UOM/service management
- Pricing tiers and authorized overrides
- POS barcode/search/cart
- Discounts and approval tiers
- Split payments
- Cash/card/bank/cheque
- Authorized credit sales
- Invoice/service/tax invoice
- Internal invoice QR
- Hold/resume
- Returns and credit notes
- Receipt/reprint
- Appointment daily/weekly/monthly support through read models
- Technician filtering and assignment
- Rescheduling and conflict prevention
- Job cards
- Estimates
- Parts usage
- Warranty dates
- Stock ledger
- Stock adjustments/approval
- GRN
- Purchase cost history
- Supplier returns
- Supplier payments and outstanding balances
- Cashbook
- Receivables
- Payables
- Daily cash closing
- VAT configuration/calculation/reporting
- Dashboard KPIs
- MVP reports and exports
- Audit logging
- Backup, verification and restore
- Single-PC and LAN deployment
- JavaFX/Spring Boot/PostgreSQL architecture
- PostgreSQL transaction consistency
- concurrent users and integrity requirements

No MVP capability is intentionally removed by this model.

---

# 36. Decisions Required Before ER Model Freeze

The following are design details to finalize in the next two documents, not scope questions:

1. Exact PostgreSQL strategy for appointment exclusion/overlap enforcement.
2. Exact rounding rule for line/invoice VAT and discount allocation.
3. Whether immediate POS payment rows remain physically in `invoice_payments` or are normalized under a more general `customer_payments` table with allocations while maintaining the same API behavior.
4. Exact GRN numbering format if not already fixed by final MVP business rule.
5. Exact behavior of unallocated supplier payments, if allowed.
6. Exact zero-variance cash-closing status representation.
7. Optimistic-lock version columns on each mutable aggregate.
8. Exact reversal representation for voided invoices and approved stock adjustments.
9. Exact customer anonymization implementation compatible with retained transactional snapshots.
10. Exact active appointment statuses participating in the PostgreSQL overlap rule.

These decisions must be resolved before final `ERD.md` / Flyway migrations.

---

# 37. Recommended Next Design Deliverables

The implementation-design sequence is now:

```text
MVP.md v1.3
    ↓
DomainModel.md              ← THIS DOCUMENT
    ↓
StateMachines.md
    ↓
ERD.md / DatabaseDesign.md
    ↓
ApiContracts.md
    ↓
AcceptanceTests.md
    ↓
Final Flyway migration plan
    ↓
Implementation
```

`StateMachines.md` should be created next because state transitions determine database constraints, service commands, permissions, audit events, and API endpoints.

---

*(End of Bizco MVP Domain Model & Aggregate Design)*
