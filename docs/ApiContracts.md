# Bizco MVP REST API Contracts

**Project:** SME Business Management System (Bizco)  
**Document:** REST API Contracts  
**Version:** 1.0  
**Status:** Pre-implementation API baseline  
**Based On:** `SRS.md` v2.1, `MVP.md` v1.3, `DevelopmentPlan.md` v2.0, `DomainModel.md` v1.0, `StateMachines.md` v1.0, `DatabaseDesign.md` v1.0  
**Client:** JavaFX desktop application  
**Server:** Spring Boot  
**Transport:** HTTPS / JSON REST  
**Scope Rule:** This document preserves the complete MVP scope. No MVP capability is removed or deferred.

---

# 1. Purpose

This document defines the authoritative REST contract between the Bizco JavaFX client and Spring Boot server.

It specifies:

- URI structure;
- HTTP methods;
- authentication/session behavior;
- permission requirements;
- request/response DTOs;
- validation rules;
- state-transition endpoints;
- idempotency behavior;
- optimistic-lock handling;
- pagination/filtering;
- standard error responses;
- approval flows;
- payment/allocation behavior;
- report contracts;
- PDF/CSV download contracts;
- backup/restore commands;
- concurrency/error semantics.

The API should be implemented from this document rather than inferred independently by UI developers.

---

# 2. API Design Principles

## 2.1 Base Path

```text
/api/v1
```

Example:

```text
POST /api/v1/invoices/{invoiceId}/post
```

## 2.2 JSON Naming

JSON uses lower camel case.

```json
{
  "customerId": "uuid",
  "invoiceDate": "2026-08-08",
  "paymentMethod": "CASH"
}
```

## 2.3 Identifiers

UUIDs are serialized as strings.

```text
"7dd3929a-697c-46e1-a76f-989c76a67a24"
```

Reference/master IDs that use BIGINT remain JSON integers.

## 2.4 Dates and Time

Business date:

```text
YYYY-MM-DD
```

Example:

```text
2026-08-08
```

Instants:

```text
ISO-8601 UTC/offset timestamp
```

Example:

```text
2026-08-08T15:30:00Z
```

JavaFX converts displayed values to:

```text
Asia/Colombo
```

## 2.5 Money

Money is serialized as a JSON number with decimal precision.

```json
{
  "amount": 1250.50
}
```

Server uses `BigDecimal`.

No client is permitted to submit binary floating-point-calculated authoritative totals.

## 2.6 Server-Calculated Fields

The following are always recalculated/validated by Spring Boot:

- subtotal;
- discounts;
- taxable amount;
- VAT;
- total;
- amount paid;
- outstanding balance;
- receivable/payable;
- stock on hand;
- available stock;
- cash expected;
- variance;
- document numbering;
- credit eligibility;
- appointment conflict.

The client may display previews only.

## 2.7 Explicit Business Commands

State-changing actions use explicit command endpoints.

Preferred:

```text
POST /invoices/{id}/post
POST /invoices/{id}/void
```

Avoid generic mutation of posted documents.

## 2.8 Protected Endpoint Rule

JavaFX permissions are for UX only.

Every protected endpoint must authorize the current session on Spring Boot.

---

# 3. Common HTTP Headers

## 3.1 Authentication

After login:

```http
Authorization: Bearer <opaque-session-token>
```

The raw token is returned only to the client.

PostgreSQL stores only its hash.

## 3.2 Correlation ID

Optional client request:

```http
X-Correlation-Id: <uuid>
```

If absent, server generates one.

Response always includes:

```http
X-Correlation-Id: <uuid>
```

This ID is also useful for logs/audit tracing.

## 3.3 Idempotency

Critical commands require:

```http
Idempotency-Key: <uuid>
```

Applies at minimum to:

```text
invoice post
payment
refund
credit note
GRN post
supplier return
supplier payment
job part consumption
stock adjustment approval
cash closing
backup
restore
production order (Produce)
```

## 3.4 Optimistic Lock

Mutable DTOs expose:

```json
{
  "version": 4
}
```

Update commands submit expected version in the request body.

Optional HTTP ETag support may be added later, but `version` is the MVP contract.

---

# 4. Standard Response Conventions

## 4.1 Successful Entity Response

```json
{
  "data": {
    "id": "uuid"
  },
  "meta": {
    "timestamp": "2026-08-08T15:00:00Z",
    "correlationId": "uuid"
  }
}
```

For normal object GETs, the server may return the object directly if the project chooses a simpler convention. For consistency, the recommended implementation is the envelope above.

## 4.2 Collection Response

```json
{
  "data": [
    {}
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 115,
    "totalPages": 6
  },
  "meta": {
    "timestamp": "2026-08-08T15:00:00Z",
    "correlationId": "uuid"
  }
}
```

## 4.3 Command Response

```json
{
  "data": {
    "resourceId": "uuid",
    "previousState": "DRAFT",
    "newState": "POSTED",
    "documentNumber": "INV-20260808-0001",
    "version": 5,
    "committedAt": "2026-08-08T15:00:00Z"
  }
}
```

---

# 5. Standard Error Contract

All API errors use:

```json
{
  "code": "INVOICE_INVALID_STATE",
  "message": "Invoice cannot be posted from the current state.",
  "details": {
    "currentState": "POSTED"
  },
  "fieldErrors": [],
  "timestamp": "2026-08-08T15:00:00Z",
  "path": "/api/v1/invoices/...",
  "correlationId": "uuid"
}
```

## 5.1 Validation Error

```json
{
  "code": "VALIDATION_FAILED",
  "message": "One or more fields are invalid.",
  "fieldErrors": [
    {
      "field": "phone",
      "code": "INVALID_PHONE",
      "message": "Phone number format is invalid."
    }
  ]
}
```

## 5.2 HTTP Mapping

| HTTP | Meaning |
|---|---|
| 200 | successful GET/update/command |
| 201 | new resource created |
| 204 | successful deletion/revocation with no body |
| 400 | malformed/validation/business-input error |
| 401 | unauthenticated/session invalid |
| 403 | authenticated but permission denied |
| 404 | resource not found |
| 409 | conflict, invalid concurrent state, duplicate, idempotency conflict |
| 422 | valid JSON but business rule prevents operation |
| 429 | login/session/rate protection where applicable |
| 500 | unexpected server error |
| 503 | maintenance/recovery temporarily unavailable |

Business conflicts should use stable domain error codes.

---

# 6. Pagination, Sorting and Filtering

## 6.1 Pagination

```text
?page=0
&size=20
```

Maximum recommended MVP page size:

```text
100
```

## 6.2 Sorting

```text
?sort=createdAt,desc
```

Multiple:

```text
?sort=status,asc&sort=createdAt,desc
```

Only whitelisted fields are accepted.

## 6.3 Generic Search

Where supported:

```text
?q=fernando
```

Search meaning is endpoint-specific.

Example customer search:

```text
name
phone
customer code
```

## 6.4 Date Filtering

```text
?fromDate=2026-08-01
&toDate=2026-08-08
```

Business dates use inclusive date semantics unless endpoint documents otherwise.

---

# 7. Authentication & Session API

## 7.1 Login

```text
POST /api/v1/auth/login
```

Public endpoint.

### Request

```json
{
  "username": "cashier",
  "password": "secret",
  "clientId": "POS-PC-01"
}
```

### Response

```json
{
  "data": {
    "sessionToken": "opaque-token",
    "expiresAt": "2026-08-08T16:00:00Z",
    "user": {
      "userId": "uuid",
      "username": "cashier",
      "displayName": "Kasun Perera",
      "primaryRole": "CASHIER",
      "effectivePermissions": [
        "invoice.create",
        "invoice.read"
      ]
    }
  }
}
```

### Errors

```text
AUTH_INVALID_CREDENTIALS
AUTH_ACCOUNT_LOCKED
AUTH_ACCOUNT_INACTIVE
AUTH_CONCURRENT_SESSION_LIMIT
```

Unknown usernames and failed attempts are logged.

## 7.2 Logout

```text
POST /api/v1/auth/logout
```

Authenticated.

Effect:

```text
current session revoked
```

## 7.3 Current Session

```text
GET /api/v1/auth/me
```

Response includes current effective permissions.

Use this when:

- app launches with stored session;
- permissions may have changed;
- secondary role expired/revoked.

## 7.4 Change Own Password

```text
POST /api/v1/auth/change-password
```

### Request

```json
{
  "currentPassword": "old",
  "newPassword": "new"
}
```

## 7.5 Force Logout User

```text
POST /api/v1/users/{userId}/sessions/revoke
```

Permission:

```text
user.session.revoke
```

Revokes active sessions of target user.

---

# 8. Users, Roles & Permissions API

## 8.1 List Users

```text
GET /api/v1/users
```

Permission:

```text
user.read
```

Filters:

```text
?q=
&active=true
&role=MANAGER
&page=
&size=
```

## 8.2 Create User

```text
POST /api/v1/users
```

Permission:

```text
user.create
```

### Request

```json
{
  "username": "manager02",
  "firstName": "Nimal",
  "lastName": "Silva",
  "email": "nimal@example.com",
  "phone": "0771234567",
  "primaryRoleId": 3,
  "temporaryPassword": "Temp@1234"
}
```

### Response

201.

Never return password hash.

## 8.3 Get User

```text
GET /api/v1/users/{userId}
```

Permission:

```text
user.read
```

## 8.4 Update User

```text
PUT /api/v1/users/{userId}
```

Permission:

```text
user.update
```

### Request

```json
{
  "firstName": "Nimal",
  "lastName": "Silva",
  "email": "nimal@example.com",
  "phone": "0771234567",
  "primaryRoleId": 3,
  "active": true,
  "version": 2
}
```

## 8.5 Lock / Unlock

```text
POST /api/v1/users/{userId}/lock
POST /api/v1/users/{userId}/unlock
```

Permissions:

```text
user.lock
user.unlock
```

## 8.6 Reset Password

```text
POST /api/v1/users/{userId}/reset-password
```

Permission:

```text
user.reset_password
```

### Request

```json
{
  "temporaryPassword": "NewTemp@123"
}
```

Sets:

```text
mustChangePassword = true
```

## 8.7 Roles

```text
GET    /api/v1/roles
POST   /api/v1/roles
GET    /api/v1/roles/{roleId}
PUT    /api/v1/roles/{roleId}
DELETE /api/v1/roles/{roleId}
```

Permissions:

```text
role.read
role.create
role.update
role.delete
```

Protected system roles cannot be deleted.

## 8.8 Permission Registry

```text
GET /api/v1/permissions
```

Permission:

```text
role.read
```

## 8.9 Assign Role Permission

```text
PUT /api/v1/roles/{roleId}/permissions
```

### Request

```json
{
  "permissionCodes": [
    "invoice.read",
    "invoice.create"
  ],
  "version": 3
}
```

Server replaces/updates assigned permission set atomically.

## 8.10 Grant Secondary Role

```text
POST /api/v1/users/{userId}/roles
```

Permission:

```text
user.grant_role
```

### Request

```json
{
  "roleId": 3,
  "expiresAt": "2026-08-08T18:00:00Z"
}
```

## 8.11 Revoke Secondary Role

```text
DELETE /api/v1/users/{userId}/roles/{assignmentId}
```

Permission:

```text
user.revoke_role
```

### Optional Request Body

```json
{
  "reason": "Temporary duty completed."
}
```

---

# 9. Customers API

## 9.1 List / Search

```text
GET /api/v1/customers
```

Permission:

```text
customer.read
```

Filters:

```text
?q=
&category=RETAIL
&status=ACTIVE
&page=
&size=
```

## 9.2 Create

```text
POST /api/v1/customers
```

Permission:

```text
customer.create
```

### Request

```json
{
  "name": "ABC Traders",
  "phone": "0771234567",
  "email": "info@abc.lk",
  "addressLine1": "12 Main Street",
  "city": "Colombo",
  "nicNumber": null,
  "brNumber": "PV12345",
  "category": "WHOLESALE",
  "creditLimit": 250000.00,
  "consentMarketing": false,
  "consentDataSharing": false
}
```

Server generates customer code.

## 9.3 Get

```text
GET /api/v1/customers/{customerId}
```

Permission:

```text
customer.read
```

PII fields are omitted/masked unless permission allows access.

## 9.4 Update

```text
PUT /api/v1/customers/{customerId}
```

Permission:

```text
customer.update
```

Requires version.

## 9.5 Block / Activate

```text
POST /api/v1/customers/{customerId}/block
POST /api/v1/customers/{customerId}/activate
```

Permissions according to customer-management policy.

## 9.6 Credit Summary

```text
GET /api/v1/customers/{customerId}/credit-summary
```

Permission:

```text
customer.credit.read
```

### Response

```json
{
  "data": {
    "customerId": "uuid",
    "creditLimit": 250000.00,
    "outstandingReceivable": 125000.00,
    "availableCredit": 125000.00,
    "oldestOutstandingDays": 35,
    "eligibility": "WARNING",
    "aging": {
      "days0To30": 70000.00,
      "days31To60": 55000.00,
      "days61To90": 0.00,
      "days91Plus": 0.00
    }
  }
}
```

## 9.7 Customer Invoice History

```text
GET /api/v1/customers/{customerId}/invoices
```

Permission:

```text
invoice.read
```

## 9.8 Anonymize

```text
POST /api/v1/customers/{customerId}/anonymize
```

High privilege.

Permission:

```text
customer.anonymize
```

Historical legally required transaction snapshots remain.

---

# 10. Product Categories & UOM API

## 10.1 Categories

```text
GET    /api/v1/product-categories
POST   /api/v1/product-categories
PUT    /api/v1/product-categories/{categoryId}
DELETE /api/v1/product-categories/{categoryId}
```

Permissions:

```text
product.category.read
product.category.create
product.category.update
product.category.delete
```

Unsafe delete returns:

```text
CATEGORY_IN_USE
```

## 10.2 UOM

```text
GET /api/v1/uom
```

Reference lookup.

Management endpoints may be restricted to system configuration roles if exposed.

---

# 11. Products API

## 11.1 Search Products

```text
GET /api/v1/products
```

Filters:

```text
?q=
&categoryId=
&type=INVENTORY
&active=true
&lowStock=true
&page=
&size=
```

Permission:

```text
product.read
```

Response may include:

```json
{
  "productId": "uuid",
  "sku": "P0001",
  "barcode": "4791234567890",
  "name": "USB Cable",
  "sellingPrice": 1500.00,
  "wholesalePrice": 1250.00,
  "taxCategory": "STANDARD",
  "physicalStock": 12.000,
  "reservedStock": 2.000,
  "availableStock": 10.000,
  "reorderPoint": 5.000,
  "active": true,
  "version": 3
}
```

## 11.2 Barcode Lookup

```text
GET /api/v1/products/barcode/{barcode}
```

Permission:

```text
product.read
```

Optimized for POS scanning.

## 11.3 Create Product

```text
POST /api/v1/products
```

Permission:

```text
product.create
```

### Request

```json
{
  "sku": "CAB-001",
  "barcode": "4791234567890",
  "name": "USB-C Cable",
  "description": "1m cable",
  "categoryId": 12,
  "uomId": 1,
  "productType": "INVENTORY",
  "taxCategory": "STANDARD",
  "costPrice": 800.00,
  "sellingPrice": 1200.00,
  "wholesalePrice": 1050.00,
  "reorderPoint": 5.000
}
```

## 11.4 Update Product

```text
PUT /api/v1/products/{productId}
```

Permission:

```text
product.update
```

Requires version.

## 11.5 Deactivate / Activate

```text
POST /api/v1/products/{productId}/deactivate
POST /api/v1/products/{productId}/activate
```

---

# 12. Services API

```text
GET    /api/v1/services
POST   /api/v1/services
GET    /api/v1/services/{serviceId}
PUT    /api/v1/services/{serviceId}
POST   /api/v1/services/{serviceId}/deactivate
POST   /api/v1/services/{serviceId}/activate
```

Permissions:

```text
service.read
service.create
service.update
```

### Create/Update DTO

```json
{
  "serviceCode": "SVC-001",
  "name": "Screen Replacement",
  "description": "Replace damaged screen",
  "category": "Mobile Repair",
  "basePrice": 5000.00,
  "estimatedDurationMinutes": 60,
  "requiresEstimate": true,
  "warrantyDays": 30,
  "version": 0
}
```

---

# 13. Invoice Draft API

## 13.1 Create Draft

```text
POST /api/v1/invoices
```

Permission:

```text
invoice.create
```

### Request

```json
{
  "invoiceDate": "2026-08-08",
  "dueDate": "2026-08-08",
  "invoiceType": "SALES",
  "customerId": null,
  "notes": null
}
```

### Response

201:

```json
{
  "data": {
    "invoiceId": "uuid",
    "status": "DRAFT",
    "invoiceNumber": null,
    "version": 0
  }
}
```

No official invoice number consumed.

## 13.2 Get Invoice

```text
GET /api/v1/invoices/{invoiceId}
```

Permission:

```text
invoice.read
```

## 13.3 List Invoices

```text
GET /api/v1/invoices
```

Filters:

```text
?number=
&customerId=
&status=
&paymentStatus=
&type=
&fromDate=
&toDate=
&page=
&size=
```

## 13.4 Update Draft Header

```text
PUT /api/v1/invoices/{invoiceId}
```

Permission:

```text
invoice.create
```

Only allowed when DRAFT.

### Request

```json
{
  "invoiceDate": "2026-08-08",
  "dueDate": "2026-09-07",
  "invoiceType": "SALES",
  "customerId": "uuid",
  "discount": {
    "type": "PERCENTAGE",
    "value": 5.00
  },
  "notes": "Delivery next week",
  "version": 2
}
```

## 13.5 Add Product Line

```text
POST /api/v1/invoices/{invoiceId}/lines
```

### Request

```json
{
  "lineType": "PRODUCT",
  "productId": "uuid",
  "quantity": 2.000,
  "requestedUnitPrice": null,
  "discount": {
    "type": "NONE",
    "value": 0
  }
}
```

Server resolves standard/wholesale price and calculates preview.

## 13.6 Add Service Line

```json
{
  "lineType": "SERVICE",
  "serviceId": "uuid",
  "quantity": 1.000,
  "requestedUnitPrice": null,
  "discount": {
    "type": "NONE",
    "value": 0
  }
}
```

## 13.7 Add Custom Line

```json
{
  "lineType": "CUSTOM",
  "description": "Emergency call-out fee",
  "quantity": 1.000,
  "requestedUnitPrice": 2500.00,
  "taxCategory": "STANDARD",
  "discount": {
    "type": "NONE",
    "value": 0
  }
}
```

## 13.8 Update Draft Line

```text
PUT /api/v1/invoices/{invoiceId}/lines/{lineId}
```

Only DRAFT.

## 13.9 Delete Draft Line

```text
DELETE /api/v1/invoices/{invoiceId}/lines/{lineId}
```

Only DRAFT.

---

# 14. Pricing & Approval API

## 14.1 Preview Invoice

```text
POST /api/v1/invoices/{invoiceId}/preview
```

No persistent posting.

Returns authoritative recalculated draft preview.

## 14.2 Request/Apply Discount Approval

The approval flow is synchronous for MVP.

If an action exceeds cashier permission, JavaFX prompts for manager approval credentials/PIN according to security design and submits them through an approval endpoint.

```text
POST /api/v1/invoices/{invoiceId}/approvals
```

### Request

```json
{
  "approvalType": "DISCOUNT_10_25",
  "invoiceLineId": "uuid",
  "requestedValue": 20.00,
  "reason": "Customer bulk purchase",
  "approverUsername": "manager",
  "approverSecret": "secret"
}
```

### Server Behavior

1. authenticate approver independently;
2. verify required permission;
3. store approval evidence;
4. never persist approver secret;
5. return approval ID/token scoped to invoice/action.

### Response

```json
{
  "data": {
    "approvalId": "uuid",
    "approvalType": "DISCOUNT_10_25",
    "approvedBy": {
      "userId": "uuid",
      "displayName": "Manager"
    },
    "approvedAt": "2026-08-08T15:00:00Z"
  }
}
```

If project chooses manager PIN rather than normal password, transport contract remains equivalent but uses:

```text
approverPin
```

after the security mechanism is finalized.

---

# 15. Post Invoice / POS Sale

## 15.1 Post Draft Invoice

```text
POST /api/v1/invoices/{invoiceId}/post
```

Permission:

```text
invoice.create
```

Required:

```http
Idempotency-Key: <uuid>
```

### Request

```json
{
  "version": 5,
  "payments": [
    {
      "paymentMethod": "CASH",
      "amount": 5000.00,
      "referenceNumber": null
    },
    {
      "paymentMethod": "CARD",
      "amount": 3000.00,
      "referenceNumber": "AUTH-123"
    }
  ],
  "creditSale": false,
  "approvalIds": []
}
```

Credit sale:

```json
{
  "version": 5,
  "payments": [
    {
      "paymentMethod": "CASH",
      "amount": 3000.00
    }
  ],
  "creditSale": true,
  "approvalIds": []
}
```

### Server Validation

- status DRAFT;
- version current;
- lines valid;
- price/discount approvals;
- below-cost approvals;
- current product/service state;
- available stock;
- customer requirements;
- credit aging/limit;
- tax;
- payment total;
- supported payment method;
- idempotency.

### Success

```json
{
  "data": {
    "invoiceId": "uuid",
    "invoiceNumber": "INV-20260808-0001",
    "status": "POSTED",
    "paymentStatus": "PAID",
    "subtotal": 6779.66,
    "vatAmount": 1220.34,
    "totalAmount": 8000.00,
    "amountPaid": 8000.00,
    "balanceDue": 0.00,
    "postedAt": "2026-08-08T15:00:00Z",
    "version": 6
  }
}
```

### Key Errors

```text
INVOICE_NOT_DRAFT
CONCURRENT_MODIFICATION
STOCK_INSUFFICIENT
INVOICE_DISCOUNT_APPROVAL_REQUIRED
INVOICE_BELOW_COST_APPROVAL_REQUIRED
INVOICE_CREDIT_CUSTOMER_REQUIRED
CUSTOMER_CREDIT_LIMIT_EXCEEDED
CUSTOMER_CREDIT_BLOCKED_BY_AGING
CUSTOMER_SALES_BLOCKED
INVOICE_PAYMENT_REQUIRED
IDEMPOTENCY_CONFLICT
```

---

# 16. Hold / Resume POS Bill

## 16.1 Hold

```text
POST /api/v1/held-sales
```

Permission:

```text
invoice.hold_bill
```

### Request

```json
{
  "customerId": null,
  "items": [
    {
      "productId": "uuid",
      "quantity": 2.000,
      "unitPrice": 1500.00,
      "discount": {
        "type": "NONE",
        "value": 0
      }
    }
  ],
  "notes": "Customer will return"
}
```

Server validates/reserves available stock.

## 16.2 List Active Held Bills

```text
GET /api/v1/held-sales
```

Filters:

```text
?status=HELD
&cashierId=
```

## 16.3 Resume

```text
POST /api/v1/held-sales/{heldSaleId}/resume
```

## 16.4 Update Resumed/Held Bill

```text
PUT /api/v1/held-sales/{heldSaleId}
```

Requires version and re-validates reservations.

## 16.5 Cancel

```text
POST /api/v1/held-sales/{heldSaleId}/cancel
```

Releases reservation.

## 16.6 Convert to Invoice

```text
POST /api/v1/held-sales/{heldSaleId}/convert
```

This may:

1. create draft invoice from held sale; then normal `/post`;
or
2. post sale in one transactional command.

**MVP contract decision:** use two stages for clarity:

```text
POST /held-sales/{id}/convert
→ creates/returns linked DRAFT invoice

POST /invoices/{invoiceId}/post
→ atomically converts/release reservation when posting
```

The held sale becomes `CONVERTED` only when invoice posting commits.

---

# 17. Invoice Payments

## 17.1 Record Later Payment Against Invoice

```text
POST /api/v1/invoices/{invoiceId}/payments
```

Permission:

```text
invoice.payment.create
```

Required:

```http
Idempotency-Key
```

### Request

```json
{
  "paymentDate": "2026-08-08T15:30:00Z",
  "paymentMethod": "BANK_TRANSFER",
  "amount": 25000.00,
  "referenceNumber": "TRX-112233",
  "notes": null
}
```

Server creates:

```text
customer_payment
customer_payment_allocation to invoice
cashbook IN
```

## 17.2 General Customer Payment

For one payment across several invoices:

```text
POST /api/v1/customer-payments
```

Permission:

```text
invoice.payment.create
```

### Request

```json
{
  "customerId": "uuid",
  "paymentDate": "2026-08-08T15:30:00Z",
  "paymentMethod": "BANK_TRANSFER",
  "amount": 50000.00,
  "referenceNumber": "TRX-5566",
  "allocations": [
    {
      "invoiceId": "uuid-1",
      "amount": 20000.00
    },
    {
      "invoiceId": "uuid-2",
      "amount": 30000.00
    }
  ]
}
```

## 17.3 Payment History

```text
GET /api/v1/invoices/{invoiceId}/payments
GET /api/v1/customers/{customerId}/payments
```

---

# 18. Credit Notes / Returns

## 18.1 Eligible Return Data

```text
GET /api/v1/invoices/{invoiceId}/return-eligibility
```

Permission:

```text
invoice.credit_note.create
```

Response includes:

- original lines;
- quantity sold;
- already returned;
- remaining returnable;
- tax snapshot;
- return deadline;
- restock eligibility.

## 18.2 Post Credit Note

```text
POST /api/v1/credit-notes
```

Permission:

```text
invoice.credit_note.create
```

Required:

```http
Idempotency-Key
```

### Request

```json
{
  "originalInvoiceId": "uuid",
  "reason": "Customer returned defective item",
  "lines": [
    {
      "invoiceLineId": "uuid",
      "quantityReturned": 1.000,
      "restock": true
    }
  ],
  "settlement": {
    "type": "APPLY_TO_BALANCE"
  }
}
```

Refund:

```json
{
  "settlement": {
    "type": "REFUND",
    "paymentMethod": "CASH",
    "originalCustomerPaymentId": "uuid"
  }
}
```

### Settlement Types

```text
APPLY_TO_BALANCE
REFUND
CUSTOMER_CREDIT
```

MVP UI may focus on original-invoice adjustment/refund, but the contract supports correct receivable treatment.

## 18.3 Get Credit Note

```text
GET /api/v1/credit-notes/{creditNoteId}
```

## 18.4 List

```text
GET /api/v1/credit-notes
```

Filters by customer, invoice, date, number.

---

# 19. Void Invoice

```text
POST /api/v1/invoices/{invoiceId}/void
```

Permission:

```text
invoice.void
```

Required:

```http
Idempotency-Key
```

### Request

```json
{
  "reason": "Invoice created against wrong transaction",
  "settlementAction": "REFUND",
  "refundPaymentMethod": "CASH"
}
```

Possible settlement actions:

```text
NO_PAYMENT_EXISTS
REFUND
CUSTOMER_CREDIT
```

Server refuses inconsistent void where downstream return/payment history makes the proposed correction invalid.

---

# 20. Receipt / Invoice Output

## 20.1 PDF

```text
GET /api/v1/invoices/{invoiceId}/pdf
```

Permission:

```text
invoice.read
```

Response:

```http
Content-Type: application/pdf
Content-Disposition: inline; filename="INV-20260808-0001.pdf"
```

## 20.2 Reprint

```text
POST /api/v1/invoices/{invoiceId}/reprint
```

Permission:

```text
invoice.reprint
```

Returns PDF or a print payload depending deployment decision.

Reprint event is audited.

## 20.3 QR

Invoice PDF/receipt includes internal Bizco QR/reference content generated from posted invoice identity.

---

# 21. Inventory API

## 21.1 Stock Summary

```text
GET /api/v1/inventory/stock
```

Permission:

```text
inventory.read
```

Filters:

```text
?q=
&categoryId=
&lowStock=true
&page=
&size=
```

## 21.2 Product Stock

```text
GET /api/v1/inventory/products/{productId}
```

Response:

```json
{
  "data": {
    "productId": "uuid",
    "physicalStock": 25.000,
    "reservedStock": 4.000,
    "availableStock": 21.000,
    "reorderPoint": 10.000,
    "lowStock": false
  }
}
```

## 21.3 Stock Movement History

```text
GET /api/v1/inventory/products/{productId}/movements
```

Filters:

```text
?movementType=
&fromDate=
&toDate=
&page=
&size=
```

## 21.4 Low Stock

```text
GET /api/v1/inventory/low-stock
```

Uses:

```text
availableStock <= reorderPoint
```

---

# 22. Stock Adjustments

## 22.1 Create

```text
POST /api/v1/stock-adjustments
```

Permission:

```text
inventory.adjustment.create
```

### Request

```json
{
  "productId": "uuid",
  "adjustmentType": "NEGATIVE",
  "quantity": 2.000,
  "reason": "Damaged stock"
}
```

Returns:

```text
PENDING
```

No stock effect.

## 22.2 List / Get

```text
GET /api/v1/stock-adjustments
GET /api/v1/stock-adjustments/{id}
```

## 22.3 Approve

```text
POST /api/v1/stock-adjustments/{id}/approve
```

Permission:

```text
inventory.adjustment.approve
```

Required idempotency key.

### Request

```json
{
  "version": 1,
  "decisionReason": "Verified physical count."
}
```

## 22.4 Reject

```text
POST /api/v1/stock-adjustments/{id}/reject
```

Permission:

```text
inventory.adjustment.approve
```

### Request

```json
{
  "version": 1,
  "decisionReason": "Count not verified."
}
```

---

# 23. Suppliers API

```text
GET    /api/v1/suppliers
POST   /api/v1/suppliers
GET    /api/v1/suppliers/{supplierId}
PUT    /api/v1/suppliers/{supplierId}
POST   /api/v1/suppliers/{supplierId}/deactivate
POST   /api/v1/suppliers/{supplierId}/activate
```

Permissions follow:

```text
supplier.read
supplier.create
supplier.update
supplier.deactivate
```

## 23.1 Supplier Statement

```text
GET /api/v1/suppliers/{supplierId}/statement
```

Includes:

- opening balance;
- GRNs;
- returns;
- payments;
- outstanding balance.

---

# 24. GRN API

## 24.1 Create Draft GRN

```text
POST /api/v1/grns
```

Permission:

```text
purchasing.grn.create
```

### Request

```json
{
  "supplierId": "uuid",
  "supplierReference": "INV-SUP-1002",
  "grnDate": "2026-08-08",
  "notes": null,
  "items": [
    {
      "productId": "uuid",
      "quantityReceived": 20.000,
      "unitCost": 800.00
    }
  ]
}
```

### Response

```text
DRAFT
grnNumber = null
```

## 24.2 Update Draft GRN

```text
PUT /api/v1/grns/{grnId}
```

Requires version.

Only DRAFT.

## 24.3 Post GRN

```text
POST /api/v1/grns/{grnId}/post
```

Permission:

```text
purchasing.grn.create
```

Required idempotency key.

### Request

```json
{
  "version": 3
}
```

### Success

```json
{
  "data": {
    "grnId": "uuid",
    "grnNumber": "GRN-20260808-0001",
    "status": "POSTED",
    "totalAmount": 16000.00,
    "postedAt": "2026-08-08T15:00:00Z"
  }
}
```

## 24.4 List/Get

```text
GET /api/v1/grns
GET /api/v1/grns/{grnId}
```

---

# 25. Supplier Returns

## 25.1 Return Eligibility

```text
GET /api/v1/grns/{grnId}/return-eligibility
```

## 25.2 Post Return

```text
POST /api/v1/supplier-returns
```

Permission:

```text
purchasing.return.create
```

Required idempotency key.

### Request

```json
{
  "supplierId": "uuid",
  "grnId": "uuid",
  "reason": "Damaged on receipt",
  "items": [
    {
      "grnItemId": "uuid",
      "quantityReturned": 2.000
    }
  ]
}
```

Server derives original cost.

---

# 26. Supplier Payments

## 26.1 Post Payment

```text
POST /api/v1/supplier-payments
```

Permission:

```text
purchasing.payment.create
```

Required idempotency key.

### Request

```json
{
  "supplierId": "uuid",
  "paymentDate": "2026-08-08T15:30:00Z",
  "paymentMethod": "BANK_TRANSFER",
  "amount": 100000.00,
  "referenceNumber": "BANK-5566",
  "notes": null,
  "allocations": [
    {
      "grnId": "uuid-1",
      "amount": 25000.00
    },
    {
      "grnId": "uuid-2",
      "amount": 40000.00
    },
    {
      "grnId": "uuid-3",
      "amount": 35000.00
    }
  ]
}
```

## 26.2 Outstanding GRNs

```text
GET /api/v1/suppliers/{supplierId}/outstanding-grns
```

## 26.3 Payment Details

```text
GET /api/v1/supplier-payments/{paymentId}
```

Includes allocations.

---

# 27. Appointments API

## 27.1 List/Calendar Query

```text
GET /api/v1/appointments
```

Permission:

```text
appointment.read
```

Filters:

```text
?from=
&to=
&technicianId=
&customerId=
&status=
&view=daily|weekly|monthly
```

## 27.2 Availability Query

```text
GET /api/v1/appointments/availability
```

Query:

```text
?serviceId=
&technicianId=
&date=2026-08-09
```

Response may contain free/occupied slots for UI assistance.

DB constraint remains final authority.

## 27.3 Create Appointment

```text
POST /api/v1/appointments
```

Permission:

```text
appointment.create
```

### Request

```json
{
  "customerId": "uuid",
  "serviceId": "uuid",
  "technicianId": "uuid",
  "startAt": "2026-08-09T04:30:00Z",
  "notes": null,
  "walkIn": false
}
```

Client may omit `endAt`; server derives from service duration and configured buffer.

## 27.4 Update / Reschedule

```text
PUT /api/v1/appointments/{appointmentId}
```

Permission:

```text
appointment.update
```

### Request

```json
{
  "serviceId": "uuid",
  "technicianId": "uuid",
  "startAt": "2026-08-09T05:30:00Z",
  "notes": "Rescheduled by customer",
  "version": 2
}
```

## 27.5 Status Command

```text
POST /api/v1/appointments/{appointmentId}/status
```

### Request

```json
{
  "targetStatus": "CONFIRMED",
  "reason": null,
  "version": 2
}
```

Server maps target transition to appropriate permission/rule.

## 27.6 Cancel

Either use status endpoint or explicit:

```text
POST /api/v1/appointments/{appointmentId}/cancel
```

Permission:

```text
appointment.cancel
```

Explicit endpoint is recommended for clear permissions.

## 27.7 Convert to Job

```text
POST /api/v1/appointments/{appointmentId}/convert-to-job
```

Permission:

```text
appointment.convert_to_job
```

Required idempotency key.

### Request

```json
{
  "deviceType": "Mobile Phone",
  "brand": "Samsung",
  "model": "A54",
  "serialNumber": "SN123",
  "reportedIssue": "Broken display",
  "accessoriesReceived": "Phone only",
  "deviceCondition": "Scratched frame"
}
```

Returns new job card.

---

# 28. Technician API

## 28.1 Eligible Technicians

```text
GET /api/v1/staff/technicians
```

Permission:

```text
appointment.read
```

Filters:

```text
?active=true
&q=
```

Returned users have:

```text
staffProfiles.isTechnician = true
```

They do not need a special security role solely to be schedulable.

---

# 29. Job Card API

## 29.1 List

```text
GET /api/v1/job-cards
```

Filters:

```text
?q=
&status=
&customerId=
&technicianId=
&fromDate=
&toDate=
&page=
&size=
```

## 29.2 Create Walk-In Job

```text
POST /api/v1/job-cards
```

Permission:

```text
jobcard.create
```

Used when no appointment exists.

## 29.3 Get

```text
GET /api/v1/job-cards/{jobCardId}
```

## 29.4 Update Editable Details

```text
PUT /api/v1/job-cards/{jobCardId}
```

Permission:

```text
jobcard.update
```

Requires version.

## 29.5 Status Transition

```text
POST /api/v1/job-cards/{jobCardId}/status
```

Permission varies according to command:

```text
jobcard.status_change
jobcard.complete
```

### Request

```json
{
  "targetStatus": "IN_PROGRESS",
  "reason": null,
  "version": 3
}
```

Server rejects invalid transition.

---

# 30. Job Services

## 30.1 Add Service

```text
POST /api/v1/job-cards/{jobCardId}/services
```

Permission:

```text
jobcard.update
```

### Request

```json
{
  "serviceId": "uuid",
  "estimatedCost": 5000.00,
  "estimatedDurationMinutes": 60,
  "notes": null
}
```

## 30.2 Service Status

```text
POST /api/v1/job-cards/{jobCardId}/services/{jobServiceId}/status
```

### Request

```json
{
  "targetStatus": "COMPLETED",
  "actualCost": 5000.00
}
```

---

# 31. Job Estimates

## 31.1 Create Estimate

```text
POST /api/v1/job-cards/{jobCardId}/estimates
```

Permission:

```text
jobcard.estimate.create
```

### Request

```json
{
  "estimatedTotal": 18500.00,
  "description": "Display replacement plus labour",
  "notes": null
}
```

Server increments estimate version.

## 31.2 Accept

```text
POST /api/v1/job-cards/{jobCardId}/estimates/{estimateId}/accept
```

Permission:

```text
jobcard.estimate.approve
```

### Request

```json
{
  "notes": "Customer approved by phone"
}
```

## 31.3 Decline

```text
POST /api/v1/job-cards/{jobCardId}/estimates/{estimateId}/decline
```

Requires response note where appropriate.

---

# 32. Job Parts

```text
POST /api/v1/job-cards/{jobCardId}/parts
```

Permission:

```text
jobcard.parts.add
```

Required idempotency key.

### Request

```json
{
  "productId": "uuid",
  "quantity": 1.000,
  "customerUnitPrice": 12000.00,
  "warrantyCovered": false
}
```

Server:

- checks stock;
- snapshots cost;
- stores customer charge price;
- creates `JOB_PART` stock movement.

### Response

```json
{
  "data": {
    "jobPartId": "uuid",
    "productId": "uuid",
    "quantity": 1.000,
    "unitPrice": 12000.00,
    "costPrice": 8000.00,
    "stockMovementId": "uuid"
  }
}
```

---

# 33. Service Invoice from Job

```text
POST /api/v1/job-cards/{jobCardId}/invoice
```

Permission:

```text
invoice.create
```

### Request

```json
{
  "includeCompletedServices": true,
  "includeParts": true,
  "customLines": []
}
```

Response:

```text
DRAFT invoice
```

The normal invoice post workflow then handles:

- tax;
- payment/credit;
- numbering;
- receipt;
- reporting.

Job-part stock has already been consumed; generating the service invoice must not create a second stock deduction for the same job part.

Therefore product lines generated from `JobPart` are marked with source context so sale posting knows they are charge lines, not new physical stock consumption.

---

# 34. Finance / Cashbook API

## 34.1 Cashbook

```text
GET /api/v1/finance/cashbook
```

Permission:

```text
finance.cashbook.read
```

Filters:

```text
?fromDate=
&toDate=
&direction=
&paymentMethod=
&sourceType=
&page=
&size=
```

## 34.2 Manual Cash Entry

```text
POST /api/v1/finance/cashbook
```

Permission:

```text
finance.cashbook.create
```

Required idempotency key.

### Request

```json
{
  "entryDate": "2026-08-08T15:00:00Z",
  "direction": "OUT",
  "paymentMethod": "CASH",
  "amount": 1500.00,
  "category": "Office Expense",
  "reference": "Voucher 12",
  "reason": "Printer ink"
}
```

## 34.3 Reverse Manual Entry

```text
POST /api/v1/finance/cashbook/{entryId}/reverse
```

Permission:

```text
finance.cashbook.reverse
```

Creates reversing entry; never edits original.

---

# 35. Receivables API

## 35.1 Customer Receivables

```text
GET /api/v1/finance/receivables
```

Filters:

```text
?customerId=
&agingBucket=
&minBalance=
&page=
&size=
```

## 35.2 Aging Summary

```text
GET /api/v1/finance/receivables/aging
```

Response:

```json
{
  "data": {
    "days0To30": 125000.00,
    "days31To60": 42000.00,
    "days61To90": 10000.00,
    "days91Plus": 5000.00,
    "totalOutstanding": 182000.00
  }
}
```

---

# 36. Payables API

## 36.1 Supplier Payables

```text
GET /api/v1/finance/payables
```

Filters:

```text
?supplierId=
&minBalance=
&page=
&size=
```

## 36.2 Supplier Outstanding Detail

```text
GET /api/v1/finance/payables/{supplierId}
```

Includes open GRNs, returns, payments, unallocated credit where applicable.

---

# 37. Cash Closing API

## 37.1 Preview Expected Cash

```text
GET /api/v1/finance/cash-closings/preview
```

Permission:

```text
finance.cash_closing.create
```

Query:

```text
?businessDate=2026-08-08
&cashierId=uuid
```

Response:

```json
{
  "data": {
    "businessDate": "2026-08-08",
    "cashierId": "uuid",
    "expectedCash": 125000.00
  }
}
```

## 37.2 Create Closing

```text
POST /api/v1/finance/cash-closings
```

Permission:

```text
finance.cash_closing.create
```

Required idempotency key.

### Request

```json
{
  "businessDate": "2026-08-08",
  "cashierId": "uuid",
  "countedCash": 124500.00,
  "varianceReason": "Rs. 500 cash shortage under review"
}
```

Server recalculates expected and variance.

## 37.3 Approve Variance

```text
POST /api/v1/finance/cash-closings/{id}/approve
```

Permission:

```text
finance.cash_closing.approve
```

### Request

```json
{
  "decisionNote": "Reviewed and accepted."
}
```

## 37.4 List

```text
GET /api/v1/finance/cash-closings
```

---

# 38. Tax Configuration API

## 38.1 Get

```text
GET /api/v1/system/tax
```

Permission:

```text
system.config.read
```

## 38.2 Update

```text
PUT /api/v1/system/tax
```

Permission:

```text
system.config
```

### Request

```json
{
  "vatEnabled": true,
  "vatRate": 18.0000,
  "version": 2
}
```

Changing tax config does not recalculate historical invoices.

---

# 39. Business Profile API

```text
GET /api/v1/system/business-profile
PUT /api/v1/system/business-profile
```

Permission:

```text
system.config
```

Update uses version.

Logo upload may use:

```text
POST /api/v1/system/business-profile/logo
Content-Type: multipart/form-data
```

MVP may store a managed file reference on the server.

---

# 40. General System Configuration

```text
GET /api/v1/system/config
PUT /api/v1/system/config/{configKey}
```

Permission:

```text
system.config
```

Only whitelisted known keys are accepted.

Examples:

```text
pos.*
appointment.*
job_card.*
return.*
backup.*
finance.*
```

---

# 41. Dashboard API

```text
GET /api/v1/dashboard
```

Permission:

```text
dashboard.read
```

Optional:

```text
?businessDate=2026-08-08
```

Response:

```json
{
  "data": {
    "todaySales": 250000.00,
    "monthlyRevenue": 3450000.00,
    "outstandingReceivables": 525000.00,
    "outstandingPayables": 410000.00,
    "lowStockCount": 8,
    "todayAppointments": 12,
    "pendingJobCards": 7
  }
}
```

All values use canonical report definitions.

---

# 42. Reports API

## 42.1 Report Types

```text
daily-sales
sales-by-product
sales-by-payment-method
stock-on-hand
low-stock
customer-balances
supplier-balances
cashbook
daily-cash-closing
appointment-summary
job-card-status
tax-summary
```

## 42.2 JSON Report

```text
GET /api/v1/reports/{reportType}
```

Example:

```text
GET /api/v1/reports/daily-sales?date=2026-08-08
```

Permission depends on report domain.

## 42.3 PDF

```text
GET /api/v1/reports/{reportType}/pdf
```

Response:

```http
Content-Type: application/pdf
Content-Disposition: attachment; filename="daily-sales-2026-08-08.pdf"
```

## 42.4 CSV

```text
GET /api/v1/reports/{reportType}/csv
```

Response:

```http
Content-Type: text/csv
Content-Disposition: attachment; filename="daily-sales-2026-08-08.csv"
```

## 42.5 Report Generation Rule

Filters used for JSON, PDF, and CSV must call the same server-side report query/calculation.

PDF/CSV renderer cannot independently calculate different totals.

---

# 43. Audit API

## 43.1 Search

```text
GET /api/v1/audit-logs
```

Permission:

```text
audit.read
```

Filters:

```text
?entityType=
&entityId=
&actionCode=
&actorUserId=
&from=
&to=
&page=
&size=
```

## 43.2 Audit Detail

```text
GET /api/v1/audit-logs/{auditId}
```

Audit records are read-only.

---

# 44. Login History API

```text
GET /api/v1/login-history
```

Permission:

```text
user.login_history.read
```

Filters:

```text
?username=
&success=
&from=
&to=
&page=
&size=
```

---

# 45. Backup API

## 45.1 List

```text
GET /api/v1/system/backups
```

Permission:

```text
system.backup.read
```

## 45.2 Create Backup

```text
POST /api/v1/system/backups
```

Permission:

```text
system.backup.create
```

Required idempotency key.

### Request

```json
{
  "requestedFileName": null
}
```

The server determines safe storage path.

### Response

```json
{
  "data": {
    "backupId": "uuid",
    "fileName": "bizco-20260808-214500.backup",
    "status": "VERIFIED",
    "fileSizeBytes": 45812212,
    "checksumSha256": "..."
  }
}
```

MVP API call may be synchronous if operationally acceptable. If implementation needs long-running status polling, it can return `STARTED` and expose status; this does not change the backup requirement.

## 45.3 Backup Detail

```text
GET /api/v1/system/backups/{backupId}
```

---

# 46. Restore API

## 46.1 Preflight

```text
GET /api/v1/system/backups/{backupId}/restore-preflight
```

Permission:

```text
system.backup.restore
```

Response:

```json
{
  "data": {
    "backupVerified": true,
    "activeSessionCount": 1,
    "currentUserSessionOnly": true,
    "databaseVersionCompatible": true,
    "canRestore": true,
    "warnings": [
      "All client sessions will be disconnected."
    ]
  }
}
```

## 46.2 Restore

```text
POST /api/v1/system/backups/{backupId}/restore
```

Permission:

```text
system.backup.restore
```

Required idempotency key.

### Request

```json
{
  "confirmationText": "RESTORE",
  "disconnectOtherSessions": true
}
```

Possible response:

```json
{
  "data": {
    "restoreId": "uuid",
    "status": "VERIFIED",
    "restoredSchemaVersion": "21"
  }
}
```

During restore/maintenance, normal APIs may return:

```text
503 SYSTEM_MAINTENANCE
```

---

# 47. Health & Version API

## 47.1 Health

```text
GET /api/v1/health
```

May be minimally public on local deployments or protected according to configuration.

Response:

```json
{
  "status": "UP",
  "database": "UP",
  "version": "1.0.0"
}
```

Do not expose secrets, DB URLs, credentials, filesystem paths, or stack traces.

## 47.2 Application Info

```text
GET /api/v1/system/info
```

Protected.

Can expose:

- application version;
- DB schema/Flyway version;
- server time;
- configured business timezone.

---

# 48. Concurrency Contracts

## 48.1 Optimistic Update

If request has stale version:

```http
409 Conflict
```

```json
{
  "code": "CONCURRENT_MODIFICATION",
  "message": "The record was modified by another user.",
  "details": {
    "currentVersion": 6
  }
}
```

JavaFX should reload.

## 48.2 Appointment Conflict

```http
409 Conflict
```

```json
{
  "code": "APPOINTMENT_CONFLICT",
  "message": "The technician is already booked for the selected time."
}
```

## 48.3 Duplicate SKU/Barcode

```http
409 Conflict
```

Codes:

```text
PRODUCT_SKU_DUPLICATE
PRODUCT_BARCODE_DUPLICATE
```

## 48.4 Stock

If another terminal consumes stock before current post:

```http
422
```

```text
STOCK_INSUFFICIENT
```

Response includes latest available stock.

---

# 49. Idempotency Contract

## 49.1 First Request

```http
Idempotency-Key: abc
```

Server processes and commits.

## 49.2 Exact Retry

Same:

```text
endpoint/operation
idempotency key
logical payload
```

returns the original committed result.

Recommended response header:

```http
Idempotent-Replay: true
```

## 49.3 Same Key, Different Payload

```http
409 Conflict
```

```text
IDEMPOTENCY_CONFLICT
```

## 49.4 Retention

Idempotency records should be retained long enough to safely cover normal client retries and operational reconciliation.

For posted business records, source `request_id` provides long-term duplicate protection.

---

# 50. Authorization Failure Contract

Unauthenticated:

```http
401
AUTH_SESSION_INVALID
```

Expired idle session:

```http
401
AUTH_SESSION_EXPIRED
```

Authenticated without permission:

```http
403
AUTH_PERMISSION_DENIED
```

Response may include required permission for support/debugging only if safe:

```json
{
  "details": {
    "requiredPermission": "invoice.void"
  }
}
```

---

# 51. Manager Approval Security

Approval credentials are never:

- stored in invoice;
- stored in audit;
- logged;
- returned to client;
- included in error output.

Flow:

```text
Cashier action needs approval
→ JavaFX asks manager
→ manager authenticates approval
→ server verifies permission
→ approval evidence stored
→ business command references approval ID
```

Approval ID is scoped to:

```text
invoice
approval type
approved value/context
short validity window or transaction use
```

It cannot be reused for unrelated invoices.

---

# 52. JavaFX Client Contract Rules

## 52.1 No Domain Reimplementation

JavaFX may calculate display previews, but server response replaces local calculated truth.

## 52.2 Offline

MVP is online LAN/single-PC client-server.

If server/database unavailable:

```text
show clear unavailable state
do not create hidden local financial transactions
```

Offline sync remains post-MVP.

## 52.3 Retries

JavaFX retries safe GET operations normally.

For critical POST commands:

```text
reuse same Idempotency-Key
```

Do not generate a new key on timeout retry.

## 52.4 Permission Refresh

JavaFX should refresh `/auth/me`:

- on login;
- after role grant/revoke where current user affected;
- after 403 where stale UI permission is possible;
- periodically if desired.

Server remains authoritative.

---

# 53. API DTO Summary

## Core Shared DTOs

```text
PageResponse<T>
ApiError
FieldError
TransitionResponse
Money/decimal fields
AuditActorSummary
UserSummary
CustomerSummary
ProductSummary
```

## Sales

```text
InvoiceDto
InvoiceLineDto
CreateInvoiceRequest
UpdateInvoiceRequest
PostInvoiceRequest
InvoicePaymentDto
CreditNoteDto
HeldSaleDto
```

## Inventory

```text
StockSummaryDto
StockMovementDto
StockAdjustmentDto
```

## Purchasing

```text
SupplierDto
GrnDto
GrnItemDto
SupplierReturnDto
SupplierPaymentDto
SupplierPaymentAllocationDto
```

## Scheduling

```text
AppointmentDto
AppointmentAvailabilityDto
JobCardDto
JobServiceDto
JobPartDto
JobEstimateDto
```

## Finance

```text
ReceivableDto
PayableDto
CashbookEntryDto
CashClosingDto
```

## System

```text
BusinessProfileDto
TaxConfigurationDto
BackupDto
RestoreDto
SystemInfoDto
```

---

# 54. API Permission Matrix by Capability

| Capability | Main Read | Main Write/Transition |
|---|---|---|
| Users | `user.read` | `user.create`, `user.update`, `user.lock`, `user.unlock` |
| Roles | `role.read` | `role.create`, `role.update`, `role.delete` |
| Customers | `customer.read` | `customer.create`, `customer.update` |
| Products | `product.read` | `product.create`, `product.update`, `product.delete` |
| Invoice | `invoice.read` | `invoice.create`, `invoice.void` |
| Payments | `invoice.read` | `invoice.payment.create`, `invoice.payment.refund` |
| Credit Note | `invoice.read` | `invoice.credit_note.create` |
| Appointment | `appointment.read` | `appointment.create`, `appointment.update`, `appointment.cancel` |
| Job Cards | `jobcard.read` | `jobcard.create`, `jobcard.update`, `jobcard.status_change`, `jobcard.complete` |
| Inventory | `inventory.read` | `inventory.adjustment.create`, `inventory.adjustment.approve` |
| GRN | `purchasing.read` | `purchasing.grn.create` |
| Supplier Return | `purchasing.read` | `purchasing.return.create` |
| Supplier Payment | `purchasing.read` | `purchasing.payment.create` |
| Finance | `finance.read` | `finance.cashbook.create`, `finance.cash_closing.create`, `finance.cash_closing.approve` |
| Reports | module-specific | export permission if separately configured |
| Audit | `audit.read` | none |
| Backup | `system.backup.read` | `system.backup.create`, `system.backup.restore` |
| Config | `system.config.read` | `system.config` |

Exact permission registry is seeded from MVP role/permission matrix and should remain synchronized with this API.

---

# 55. API Integration Test Requirements

Every command endpoint must test:

1. valid authenticated request;
2. unauthenticated request;
3. missing permission;
4. validation failure;
5. invalid aggregate state;
6. database rollback on downstream failure;
7. idempotent retry where applicable;
8. optimistic-lock conflict where applicable;
9. audit event creation;
10. persisted source/ledger reconciliation.

Examples:

```text
POST /invoices/{id}/post
→ invoice
→ stock
→ payment
→ cashbook
→ audit
```

must reconcile in one test.

---

# 56. End-to-End Scenario API Tests

## Retail

```text
POST supplier
POST product
POST GRN
POST GRN/{id}/post
POST invoice
POST invoice lines
POST invoice/{id}/post
GET invoice PDF
GET stock
POST cash closing
GET daily sales report
```

## Wholesale Credit

```text
POST customer with limit
POST credit invoice
GET receivable
POST later payment
GET customer credit summary
```

## Repair

```text
POST appointment
POST convert-to-job
POST estimate
POST accept estimate
POST job part
POST job status
POST job invoice
POST invoice post
```

## Appointment Service

```text
POST appointment
attempt overlapping concurrent appointment
expect one success + one APPOINTMENT_CONFLICT
```

## Recovery

```text
POST backup
GET backup VERIFIED
GET restore preflight
POST restore
verify system/schema
```

---

# 57. API Versioning

MVP API prefix:

```text
/api/v1
```

Breaking changes require:

```text
/api/v2
```

Non-breaking additions can remain v1.

Do not version every DTO independently.

---

# 58. OpenAPI / Swagger Requirement

Spring Boot should publish OpenAPI documentation during development.

Recommended endpoint:

```text
/v3/api-docs
/swagger-ui
```

Production exposure can be disabled/configured.

OpenAPI should include:

- request/response schemas;
- permissions in descriptions;
- error examples;
- idempotency header;
- version fields;
- pagination parameters;
- PDF/CSV media types.

The generated specification does not replace this business API document; it is generated implementation documentation and should remain consistent with it.

---

# 59. Final API Design Decisions

| Area | Decision |
|---|---|
| API base | `/api/v1` |
| Auth | opaque bearer session token |
| Permission check | server-side every protected action |
| DTO style | API DTOs, never JPA entities |
| Money | decimal/BigDecimal |
| Time | ISO-8601; business DATE separate |
| Mutable concurrency | version field / optimistic lock |
| Critical retry | `Idempotency-Key` |
| Draft invoice | explicit create/update |
| Posting | explicit `/post` command |
| Void | explicit `/void` command |
| Payments | normalized customer payment/allocation persistence |
| Invoice payment API | remains convenient `/invoices/{id}/payments` |
| Held bills | separate resource + conversion to draft invoice |
| Approvals | independently authenticated approval evidence |
| Appointment conflict | DB-authoritative, API returns 409 |
| GRN | DRAFT then explicit POST |
| Job invoice | creates DRAFT invoice; normal invoice posting follows |
| Reports | same query model for JSON/PDF/CSV |
| Backup/restore | explicit command resources |
| Errors | stable domain error codes |
| API docs | OpenAPI generated from implementation |

---

# 60. Implementation Sequence After API Freeze

The system design sequence is now:

```text
MVP.md v1.3
        ↓
DomainModel.md
        ↓
StateMachines.md
        ↓
DatabaseDesign.md
        ↓
ApiContracts.md              ← THIS DOCUMENT
        ↓
AcceptanceTests.md
        ↓
DevelopmentPlan synchronization
        ↓
Maven project initialization
        ↓
Flyway migrations
        ↓
Spring Boot vertical slices
        ↓
JavaFX screens
```

The next artifact should be:

```text
AcceptanceTests.md
```

It will convert the requirements, invariants, state machines, database rules, and API contracts into a traceable acceptance-test catalogue with scenario IDs, Given/When/Then cases, Testcontainers requirements, permission tests, concurrency tests, idempotency tests, reconciliation tests, and release-gate criteria.

---

# 61. Bill of Materials API

Phase 7 (DevelopmentPlan.md Week 20). Base path `/api/v1/boms`. A Bill of Materials always keys on `ProductVariant` (finished side and every component side), not `Product` - it is downstream of the Phase 6 variant cutover. At most one BOM exists per finished variant (`finished_variant_id` is unique).

## 61.1 Search

```text
GET /api/v1/boms?activeOnly=false&page=0&size=20
```

Permission:

```text
manufacturing.read
```

### Response

```json
{
  "data": [
    {
      "bomId": "uuid",
      "finishedVariantId": "uuid",
      "finishedVariantSku": "FRAME-PHOTO-8X10",
      "finishedVariantName": "Framed Photo 8x10",
      "name": "Framed Photo",
      "active": true,
      "totalEstimatedCost": 15.00,
      "version": 2
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`totalEstimatedCost` is recomputed on every read from each component's *current* `costPrice` (Task 20.3 roll-up) - it is not a stored, potentially-stale total.

## 61.2 Get

```text
GET /api/v1/boms/{bomId}
```

Permission:

```text
manufacturing.read
```

### Response

```json
{
  "bomId": "uuid",
  "finishedVariantId": "uuid",
  "finishedVariantSku": "FRAME-PHOTO-8X10",
  "finishedVariantName": "Framed Photo 8x10",
  "name": "Framed Photo",
  "active": true,
  "totalEstimatedCost": 15.00,
  "createdAt": "2026-08-08T15:00:00Z",
  "updatedAt": "2026-08-08T15:05:00Z",
  "version": 2,
  "items": [
    {
      "bomItemId": "uuid",
      "componentVariantId": "uuid",
      "componentSku": "FRAME-8X10",
      "componentName": "Wooden Frame 8x10",
      "quantity": 1.000,
      "wastageQty": null,
      "componentCostPrice": 8.00,
      "estimatedCost": 8.00
    },
    {
      "bomItemId": "uuid",
      "componentVariantId": "uuid",
      "componentSku": "GLASS-8X10",
      "componentName": "Glass Pane 8x10",
      "quantity": 2.000,
      "wastageQty": 0.500,
      "componentCostPrice": 3.50,
      "estimatedCost": 7.00
    }
  ]
}
```

Error: `BOM_NOT_FOUND` (404).

## 61.3 Get By Finished Variant

```text
GET /api/v1/boms/by-variant/{finishedVariantId}
```

Permission:

```text
manufacturing.read
```

Same response shape as §61.2. Error: `BOM_NOT_FOUND` (404) if the variant has no Bill of Materials.

## 61.4 Create

```text
POST /api/v1/boms
```

Permission:

```text
manufacturing.bom.manage
```

### Request

```json
{
  "finishedVariantId": "uuid",
  "name": "Framed Photo"
}
```

### Response

```text
201 Created
Location: /api/v1/boms/{bomId}
```

Body is the same shape as §61.2, with an empty `items` array and `totalEstimatedCost` of `0.00`.

Errors: `BOM_ALREADY_EXISTS_FOR_VARIANT` (409) - this finished variant already has a Bill of Materials; validation (`finishedVariantId`/`name` required); `VARIANT_NOT_FOUND` (404).

## 61.5 Update

```text
PUT /api/v1/boms/{bomId}
```

Permission:

```text
manufacturing.bom.manage
```

Requires version.

### Request

```json
{
  "name": "Framed Photo (Deluxe)",
  "active": true,
  "version": 2
}
```

### Response

Same shape as §61.2.

Errors: `CONCURRENT_MODIFICATION` (409) on a stale version; validation (`name` required); `BOM_NOT_FOUND` (404).

## 61.6 Add Item

```text
POST /api/v1/boms/{bomId}/items
```

Permission:

```text
manufacturing.bom.manage
```

### Request

```json
{
  "componentVariantId": "uuid",
  "quantity": 2.000,
  "wastageQty": 0.500
}
```

`wastageQty` is optional (may be `null`); it is included in physical consumption at Produce time but excluded from the estimated-cost roll-up (SRS.md §6.4.11.4).

### Response

Full, recalculated BOM - same shape as §61.2.

Errors: `BOM_ITEM_CIRCULAR_REFERENCE` (409) - the component is, directly or transitively, itself built from this BOM's own finished variant (Task 20.4 guard, walks the candidate component's own BOM tree); `BOM_ITEM_DUPLICATE_COMPONENT` (409) - this component is already on the BOM; `VARIANT_NOT_FOUND` (404); validation (`componentVariantId` required, `quantity` must be greater than zero).

## 61.7 Update Item

```text
PUT /api/v1/boms/{bomId}/items/{bomItemId}
```

Permission:

```text
manufacturing.bom.manage
```

### Request

```json
{
  "componentVariantId": "uuid",
  "quantity": 3.000,
  "wastageQty": null
}
```

### Response

Full, recalculated BOM - same shape as §61.2.

Error: `BOM_ITEM_NOT_FOUND` (404); validation (`quantity` must be greater than zero).

## 61.8 Remove Item

```text
DELETE /api/v1/boms/{bomId}/items/{bomItemId}
```

Permission:

```text
manufacturing.bom.manage
```

### Response

Full, recalculated BOM - same shape as §61.2, with the item removed.

Error: `BOM_ITEM_NOT_FOUND` (404).

---

# 62. Production Orders API

Phase 7 (DevelopmentPlan.md Week 21). Base path `/api/v1/production-orders`. This is the Produce transaction: it locks every distinct component variant plus the finished variant in one ascending-order lock acquisition, validates availability for **every** component before posting **any** stock movement, then posts one `PRODUCTION_OUT` movement per component and - only for a `STOCKED` production - one `PRODUCTION_IN` movement for the finished variant, all inside a single transaction (SRS.md §6.4.11.2-3).

## 62.1 Search

```text
GET /api/v1/production-orders?bomId=&finishedVariantId=&page=0&size=20
```

Permission:

```text
manufacturing.read
```

`bomId` and `finishedVariantId` are both optional filters.

### Response

```json
{
  "data": [
    {
      "productionOrderId": "uuid",
      "productionNumber": "MO-20260808-0001",
      "finishedVariantId": "uuid",
      "finishedVariantSku": "FRAME-PHOTO-8X10",
      "finishedVariantName": "Framed Photo 8x10",
      "quantityProduced": 3.000,
      "productionMode": "STOCKED",
      "totalComponentCost": 22.50,
      "createdAt": "2026-08-08T15:10:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## 62.2 Get

```text
GET /api/v1/production-orders/{productionOrderId}
```

Permission:

```text
manufacturing.read
```

### Response

```json
{
  "productionOrderId": "uuid",
  "productionNumber": "MO-20260808-0001",
  "bomId": "uuid",
  "bomName": "Framed Photo",
  "finishedVariantId": "uuid",
  "finishedVariantSku": "FRAME-PHOTO-8X10",
  "finishedVariantName": "Framed Photo 8x10",
  "quantityProduced": 3.000,
  "productionMode": "STOCKED",
  "totalComponentCost": 22.50,
  "notes": "batch 1",
  "createdBy": "uuid",
  "createdAt": "2026-08-08T15:10:00Z",
  "items": [
    {
      "productionOrderItemId": "uuid",
      "componentVariantId": "uuid",
      "componentSku": "FRAME-8X10",
      "componentName": "Wooden Frame 8x10",
      "quantityConsumed": 6.000,
      "unitCostAtProduction": 8.00,
      "totalCost": 48.00
    }
  ]
}
```

`quantityConsumed` includes wastage: `(item.quantity + item.wastageQty) * quantityProduced`. `unitCostAtProduction`/`totalCost` are frozen at the component's cost price at the moment of production - they do not move if the component's cost price changes afterward (unlike the BOM's own always-refreshed estimate, §61.2).

Error: `PRODUCTION_ORDER_NOT_FOUND` (404).

## 62.3 Produce

```text
POST /api/v1/production-orders
```

Permission:

```text
manufacturing.produce
```

Required idempotency key.

### Request

```json
{
  "bomId": "uuid",
  "quantityToProduce": 3.000,
  "productionMode": "STOCKED",
  "notes": "batch 1"
}
```

`productionMode` is `"STOCKED"` or `"MADE_TO_ORDER"` (SRS.md §6.4.11.3). A `MADE_TO_ORDER` production still consumes and costs every component exactly like `STOCKED`, but never posts `PRODUCTION_IN` for the finished variant - nothing is added to finished-goods stock.

### Response

```text
200 OK
Idempotent-Replay: false
```

Body is the same shape as §62.2.

Errors:

- `BOM_NOT_FOUND` (404) - `bomId` does not resolve to a Bill of Materials;
- `BOM_NOT_ACTIVE` (409) - the Bill of Materials is inactive;
- `BOM_HAS_NO_COMPONENTS` (409) - the Bill of Materials has no items to consume;
- `STOCK_INSUFFICIENT` (409) - at least one component lacks enough physical stock for the requested quantity; every component is checked before any movement is posted, so a shortfall on one component leaves *all* components untouched, including ones that had enough (never a partial production);
- validation (`bomId` required, `quantityToProduce` must be greater than zero, `productionMode` must be `STOCKED` or `MADE_TO_ORDER`).

A retry with the same `Idempotency-Key` and the same payload returns `Idempotent-Replay: true` and the original response, without re-consuming stock or re-incrementing the finished variant (§49 Idempotency Contract). Under concurrent Produce attempts against the same last unit of a shared component, exactly one commits and the rest fail with `STOCK_INSUFFICIENT` - enforced by the component variant row lock, not an application-level pre-check (§48 Concurrency Contracts; `ProductionConcurrencyIT`).

---

*(End of Bizco MVP REST API Contracts)*
