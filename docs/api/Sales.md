# Sales API

**Controllers:** `InvoiceController`, `SalesApprovalController`, `HeldSaleController`,
`CreditNoteController`, `CustomerPaymentController`
**Package:** `com.bizco.server.sales`
**Conventions:** see [README.md](README.md).

The full sales lifecycle is implemented: draft invoices and lines (§1–§8), manager approval
evidence (§9), posting `DRAFT` → `POSTED` with stock/payment/receivable/cashbook effects (§10),
void (§11), payment recording and history (§12–§13), hold/resume (§14), credit notes/returns
(§15), and receipt/tax-invoice PDF output (§16). A `DRAFT` invoice has no stock, receivable,
cashbook, or VAT/sales report effect — every line-mutating endpoint in §1–§8 still only operates
while `status` is `DRAFT`; §10 (posting) is what gives an invoice its first real-world effect.

---

## 1. Create Draft Invoice

```
POST /api/v1/invoices
```

Permission: `invoice.create`

**Request body**

```json
{
  "invoiceDate": "2026-08-17",
  "dueDate": null,
  "invoiceType": "SALES",
  "customerId": null,
  "notes": "Walk-in"
}
```

`invoiceDate` defaults to today if omitted. `invoiceType` defaults to `SALES` if omitted (`SALES`,
`SERVICE`, `TAX` are valid). `customerId` is optional (walk-in sale); if supplied, the customer
must exist (`404 CUSTOMER_NOT_FOUND`).

**Response:** `201 Created`, `Location: /api/v1/invoices/{invoiceId}`

```json
{
  "invoiceId": "b1a2c3d4-5e6f-4a1b-9c8d-7e6f5a4b3c2d",
  "invoiceNumber": null,
  "invoiceDate": "2026-08-17",
  "status": "DRAFT",
  "customerId": null,
  "totalAmount": 0.00,
  "version": 0
}
```

No official invoice number is consumed at this stage — `invoiceNumber` stays `null` until posting.

---

## 2. Get Invoice

```
GET /api/v1/invoices/{invoiceId}
```

Permission: `invoice.read`

**Response body** `200 OK`

```json
{
  "invoiceId": "b1a2c3d4-5e6f-4a1b-9c8d-7e6f5a4b3c2d",
  "invoiceNumber": null,
  "invoiceDate": "2026-08-17",
  "dueDate": null,
  "invoiceType": "SALES",
  "status": "DRAFT",
  "customerId": null,
  "cashierId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "subtotal": 900.00,
  "discountType": "PERCENTAGE",
  "discountValue": 10.00,
  "discountAmount": 20.00,
  "taxableAmount": 180.00,
  "vatRateSnapshot": 0.0000,
  "vatAmount": 0.00,
  "totalAmount": 180.00,
  "notes": "VIP customer",
  "createdAt": "2026-08-17T09:00:00Z",
  "postedAt": null,
  "version": 3,
  "lines": [
    {
      "invoiceLineId": "c2d3e4f5-6a7b-4c1d-8e9f-1a2b3c4d5e6f",
      "lineNumber": 1,
      "lineType": "PRODUCT",
      "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a",
      "serviceId": null,
      "skuSnapshot": "CAB-001",
      "descriptionSnapshot": "USB-C Cable",
      "uomSnapshot": "PCS",
      "quantity": 2.000,
      "unitPrice": 100.00,
      "discountType": "NONE",
      "discountValue": 0.00,
      "discountAmount": 20.00,
      "taxCategorySnapshot": "STANDARD",
      "vatRateSnapshot": 0.0000,
      "taxableAmount": 180.00,
      "vatAmount": 0.00,
      "lineTotalInclVat": 180.00
    }
  ]
}
```

`vatRateSnapshot`/`vatAmount` read `0` whenever VAT is disabled system-wide (`system.tax`,
see [System.md](System.md)) — the rate is still resolved and snapshotted per line, just at `0`.
A line's `discountAmount` combines that line's own discount and its proportional share of any
invoice-level discount (see §4); the two are not broken out separately in the response.

**Errors:** `404 INVOICE_NOT_FOUND`.

---

## 3. Search Invoices

```
GET /api/v1/invoices
```

Permission: `invoice.read`

**Query parameters**

| Param | Type | Notes |
|---|---|---|
| `number` | string | partial match on `invoiceNumber` |
| `customerId` | UUID | exact match |
| `status` | string | `DRAFT`, `POSTED`, `VOIDED` |
| `type` | string | `SALES`, `SERVICE`, `TAX` |
| `fromDate` / `toDate` | date | inclusive range on `invoiceDate` |
| `page` | int | default `0` |
| `size` | int | default `20`, capped at `100` |

**Response body** `200 OK`

```json
{
  "data": [
    {
      "invoiceId": "b1a2c3d4-5e6f-4a1b-9c8d-7e6f5a4b3c2d",
      "invoiceNumber": null,
      "invoiceDate": "2026-08-17",
      "status": "DRAFT",
      "customerId": null,
      "totalAmount": 180.00,
      "version": 3
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

There is no `paymentStatus` filter yet on this endpoint, even though payment recording itself is
implemented (§12) — `paymentStatus` is derived from posted payments against the invoice
(`PostInvoiceResponse.paymentStatus`, §10), not a stored/indexed column this search can filter on
today.

---

## 4. Update Draft Header

```
PUT /api/v1/invoices/{invoiceId}
```

Permission: `invoice.create`. Only allowed while `status` is `DRAFT`.

**Request body**

```json
{
  "invoiceDate": "2026-08-17",
  "dueDate": "2026-09-16",
  "invoiceType": "SALES",
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "discount": {
    "type": "PERCENTAGE",
    "value": 10.00
  },
  "notes": "VIP customer",
  "version": 2
}
```

`discount.type` is `NONE`, `PERCENTAGE`, or `FIXED`. An invoice-level discount is allocated
proportionally across every line by that line's pre-discount taxable amount, with the rounding
remainder assigned to the last line (by line number) so the allocated amounts always sum to
exactly the invoice discount — see `InvoicePricingCalculator`. The response reflects the
recalculated totals immediately (same shape as §2).

**Errors:** `404 INVOICE_NOT_FOUND`; `409 CONCURRENT_MODIFICATION` (stale `version`, checked
before the draft-status check); `409 INVOICE_NOT_DRAFT`.

---

## 5. Add Line

```
POST /api/v1/invoices/{invoiceId}/lines
```

Permission: `invoice.create`. Only allowed while `status` is `DRAFT`. Returns the full recalculated
invoice (same shape as §2), not just the new line.

**PRODUCT line**

```json
{
  "lineType": "PRODUCT",
  "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a",
  "quantity": 2.000,
  "requestedUnitPrice": null,
  "discount": { "type": "NONE", "value": 0 }
}
```

`unitPrice` resolves automatically when `requestedUnitPrice` is `null`: the invoice's customer
category selects the tier (`WHOLESALE` customers get the product's wholesale price when one is
set; everyone else — including walk-ins — gets the retail selling price), matching the price
resolution `GET /api/v1/products/{productId}/price` performs standalone (see
[Catalog.md](Catalog.md) §3.7). `sku`/`uom`/`taxCategory` snapshot from the product at add time.
`requestedUnitPrice`, if supplied, is used as-is with no override-permission check at draft time —
see the note at the end of this section.

**SERVICE line**

```json
{
  "lineType": "SERVICE",
  "serviceId": "e5f6a7b8-2222-4c33-9d44-0e5f6a7b8c9d",
  "quantity": 1.000,
  "requestedUnitPrice": null,
  "discount": { "type": "NONE", "value": 0 }
}
```

`unitPrice` defaults to the service's `basePrice` when `requestedUnitPrice` is `null`. Tax category
is always `STANDARD` for service lines (`ServiceDefinition` carries no tax category of its own yet).

**CUSTOM line**

```json
{
  "lineType": "CUSTOM",
  "description": "Emergency call-out fee",
  "quantity": 1.000,
  "requestedUnitPrice": 2500.00,
  "taxCategory": "STANDARD",
  "discount": { "type": "NONE", "value": 0 }
}
```

`description` and `requestedUnitPrice` are required for `CUSTOM` lines. `taxCategory` defaults to
`STANDARD` if omitted (`STANDARD`, `EXEMPT`, `ZERO_RATED` are valid).

> **Approval enforcement note:** this implementation performs zero discount-tier, price-override,
> or below-cost validation at draft add/update time — a `DRAFT` has no financial effect, so any
> value can be set here. That enforcement (`INVOICE_DISCOUNT_APPROVAL_REQUIRED`,
> `INVOICE_BELOW_COST_APPROVAL_REQUIRED`, etc., cross-checked against §9's approval evidence) all
> happens at posting time — see §10.

**Errors:** `400 VALIDATION_FAILED` (missing `productId`/`serviceId`/`description`/
`requestedUnitPrice` for the given `lineType`, or `quantity <= 0`); `404 PRODUCT_NOT_FOUND` /
`404 SERVICE_NOT_FOUND` (unknown ID); `409 PRODUCT_INACTIVE` (inactive product); `409
SERVICE_NOT_FOUND` (inactive service — reuses the not-found code with a `409` instead of `404`
rather than a dedicated "service inactive" code); `409 INVOICE_NOT_DRAFT`.

---

## 6. Update Line

```
PUT /api/v1/invoices/{invoiceId}/lines/{lineId}
```

Permission: `invoice.create`. Only allowed while `status` is `DRAFT`.

**Request body**

```json
{
  "quantity": 3.000,
  "requestedUnitPrice": null,
  "discount": { "type": "PERCENTAGE", "value": 5.00 },
  "version": 3
}
```

`quantity`, if supplied, replaces the line's quantity (must be `> 0`). `requestedUnitPrice` is
currently accepted by the DTO but **not applied** — unit price can only be set on add, not update,
in the present implementation. `discount` always replaces the line's own discount config wholesale,
including resetting it to `NONE` if `discount` is omitted entirely — there is no "leave discount
unchanged" option. Returns the full recalculated invoice (same shape as §2).

**Errors:** `404 INVOICE_NOT_FOUND`; `404 INVOICE_LINE_NOT_FOUND`; `409 CONCURRENT_MODIFICATION`
(stale `version`, checked before the draft-status check, same as §4); `409 INVOICE_NOT_DRAFT`.

---

## 7. Delete Line

```
DELETE /api/v1/invoices/{invoiceId}/lines/{lineId}
```

Permission: `invoice.create`. Only allowed while `status` is `DRAFT`. No request body. Remaining
lines are renumbered contiguously from `1`. Returns the full recalculated invoice (same shape as
§2).

**Errors:** `404 INVOICE_LINE_NOT_FOUND`; `409 INVOICE_NOT_DRAFT`.

---

## 8. Preview

```
POST /api/v1/invoices/{invoiceId}/preview
```

Permission: `invoice.create`. No request body, no persistent posting, no official number
allocated. Recalculates every line and header total from scratch via `InvoicePricingCalculator`
and persists that snapshot to the draft — in the current implementation this is equivalent to
what already happens automatically after every line/header mutation (§4–§7), so calling `/preview`
without an intervening change returns identical, idempotent totals. Response shape as §2.

---

## 9. Request Sales Approval

```
POST /api/v1/invoices/{invoiceId}/approvals
```

Permission: `invoice.create` (the *requester's* permission — see below for the approver check).

**Request body**

```json
{
  "approvalType": "DISCOUNT_10_25",
  "invoiceLineId": null,
  "requestedValue": 15.00,
  "reason": "Regular customer",
  "approverUsername": "manager",
  "approverSecret": "Correct1!"
}
```

`approvalType` is one of `DISCOUNT_10_25`, `DISCOUNT_OVER_25`, `PRICE_OVERRIDE`, `BELOW_COST`.
`invoiceLineId` is optional — omit for an invoice-level approval, or supply an existing line's ID
for a line-level one (`404 INVOICE_LINE_NOT_FOUND` if it doesn't belong to this invoice).

The server **independently authenticates the approver** by `approverUsername`/`approverSecret`
(BCrypt-checked against that user's actual password) — it does not trust the requester's own
session for this. It then checks the approver holds the permission the approval type requires:

| `approvalType` | Required approver permission |
|---|---|
| `DISCOUNT_10_25` | `invoice.discount.approve_25` |
| `DISCOUNT_OVER_25` | `invoice.discount.approve_50` |
| `PRICE_OVERRIDE` | `invoice.override_price` |
| `BELOW_COST` | `invoice.sell_below_cost` |

`approverSecret` (the approver's password) is verified but **never persisted** — only who approved,
what type, the optional reason, and when.

**Response body** `200 OK`

```json
{
  "approvalId": "f6a7b8c9-3333-4d55-9e66-1f7a8b9c0d1e",
  "approvalType": "DISCOUNT_10_25",
  "approvedBy": {
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "displayName": "Store Manager"
  },
  "approvedAt": "2026-08-17T09:05:00Z"
}
```

The approval is bound to this invoice (`invoiceId`, stored but not echoed in the response) —
nothing today re-validates that binding, since posting (which would consume `approvalId`s
submitted with a post request) is not yet implemented.

**Errors:** `404 INVOICE_NOT_FOUND`; `404 INVOICE_LINE_NOT_FOUND`; `401 AUTH_INVALID_CREDENTIALS`
(unknown `approverUsername` or wrong `approverSecret`); `401 AUTH_ACCOUNT_INACTIVE` (approver
account locked/inactive); `403 AUTH_PERMISSION_DENIED` (approver authenticated fine but lacks the
required permission for `approvalType`).

---

## 10. Post Invoice

```
POST /api/v1/invoices/{invoiceId}/post
```

Permission: `invoice.create`. Requires an `Idempotency-Key` header (`ApiHeaders.IDEMPOTENCY_KEY`)
— this is the DRAFT → POSTED transaction boundary (validate → lock/check stock → allocate number
→ snapshot → SALE stock movements → payment/receivable → cashbook → held-sale conversion → audit
→ commit), all inside one PostgreSQL transaction, so a retried POST with the same key replays the
original result instead of posting twice. Response carries `Idempotency-Replayed: true|false`.

**Request body**

```json
{
  "version": 3,
  "payments": [
    { "paymentMethod": "CASH", "amount": 5900.00, "referenceNumber": null }
  ],
  "creditSale": false,
  "approvalIds": []
}
```

`payments` — zero or more payment lines (split payment supported); each needs a positive `amount`
and a valid `paymentMethod` (`CASH`, `CARD`, `BANK_TRANSFER`, `CHEQUE`). For an **immediate-payment
sale** (`creditSale: false`), the payment total must equal the invoice total exactly — no more, no
less. For a **credit sale** (`creditSale: true`), the payment total may be less than the invoice
total; the shortfall becomes a customer receivable, subject to credit-limit/aging checks below.
`approvalIds` references approvals already recorded via §9 — the server independently re-derives
what approval each line/invoice actually needs and checks the referenced approvals cover it; it
does not trust the client's claim that an approval applies.

**What posting validates and does, in order:**

1. **Version and status** — stale `version` or non-`DRAFT` status rejects before anything else.
2. **Discount-tier approval** (line and invoice level, MVP.md §5.6/§7.5): a discount computed
   above 25% needs `invoice.discount.approve_50` (actor permission) or a matching
   `DISCOUNT_OVER_25` approval; above 10% needs `invoice.discount.approve_25` or `DISCOUNT_10_25`.
   `≤10%` needs neither.
3. **Below-cost approval:** any `PRODUCT` line whose `unitPrice` is below the product's current
   `costPrice` needs `invoice.sell_below_cost` or a `BELOW_COST` approval.
4. **Payment total validation** — see above; a payment line with `amount <= 0` or an unrecognized
   `paymentMethod` is rejected outright.
5. **Credit eligibility** (credit sales only, MVP.md §3.3) — the customer is row-locked
   (`findByIdForUpdate`, so two concurrent credit postings for the same customer serialize) and
   evaluated against `CustomerCreditPolicy`: a `BLOCKED` customer is rejected outright
   (`CUSTOMER_SALES_BLOCKED`); exceeding the credit limit is `CUSTOMER_CREDIT_LIMIT_EXCEEDED`;
   61–90+ days overdue aging blocks further credit (`CUSTOMER_CREDIT_BLOCKED_BY_AGING`, covering
   both the "cash only" 61–90 day band and the "block all" 91+ day band from MVP.md §3.3).
6. **Stock lock and availability check** for every `PRODUCT` line — locked in stable order, then
   checked, *before* any posting effect runs, so a failure here leaves nothing to roll back. Lines
   sourced from a `JobPart` (`sourceJobPartId` set) are excluded — their stock already left as
   `JOB_PART` when the part was consumed ([Scheduling.md](Scheduling.md) §9), so posting the
   invoice must not deduct it a second time.
7. **Number allocation, snapshot, and posting effects** — official `invoice_number` allocated
   (`INV-YYYYMMDD-NNNN`), business/customer snapshots frozen, one `SALE` stock movement per
   product line, one `CustomerPayment` (+ `CashbookEntry`, direction `IN`) per payment line, and —
   if this invoice is linked to a held sale — that held sale is marked `CONVERTED` in the same
   transaction (so there is no window where the invoice is `POSTED` but the held sale still shows
   `HELD`/`RESUMED`).

**Response body** `200 OK`

```json
{
  "invoiceId": "b1a2c3d4-5e6f-4a1b-9c8d-7e6f5a4b3c2d",
  "invoiceNumber": "INV-20260819-0007",
  "status": "POSTED",
  "paymentStatus": "PAID",
  "subtotal": 900.00,
  "vatAmount": 0.00,
  "totalAmount": 900.00,
  "amountPaid": 900.00,
  "balanceDue": 0.00,
  "postedAt": "2026-08-19T09:12:00Z",
  "version": 4
}
```

`paymentStatus` is `PAID` (`balanceDue <= 0`), `PARTIAL` (`amountPaid > 0` but a balance remains),
or `UNPAID` (`amountPaid = 0`, credit sale with no payment lines at all).

**Errors:** `404 INVOICE_NOT_FOUND`; `409 CONCURRENT_MODIFICATION` (stale `version`); `409
INVOICE_NOT_DRAFT`; `400 DOMAIN_RULE_REJECTED` (empty invoice, bad payment line); `409
INVOICE_DISCOUNT_APPROVAL_REQUIRED`; `409 INVOICE_BELOW_COST_APPROVAL_REQUIRED`; `400
INVOICE_CREDIT_CUSTOMER_REQUIRED` (`creditSale: true` with no customer); `400
INVOICE_PAYMENT_REQUIRED` (immediate sale not paid in full); `404 CUSTOMER_NOT_FOUND`; `409
CUSTOMER_SALES_BLOCKED`; `409 CUSTOMER_CREDIT_LIMIT_EXCEEDED`; `409
CUSTOMER_CREDIT_BLOCKED_BY_AGING`; `409 STOCK_INSUFFICIENT`.

---

## 11. Void Invoice

```
POST /api/v1/invoices/{invoiceId}/void
```

Permission: `invoice.void`. `POSTED` → `VOIDED` (`StateMachines.md` §4.5). Not idempotency-keyed
(unlike posting) — a repeat call simply 409s with `INVOICE_NOT_POSTED` since the invoice is no
longer `POSTED` after the first call succeeds.

**Request body:** `{ "reason": "Customer changed their mind", "version": 4 }`

**Deliberate MVP simplification:** voiding does **not** reverse existing payments or cashbook
entries — money already collected stays recorded as collected; recovering it is the separate,
manual §15 credit-note `REFUND` settlement, not something void does automatically. What void does
enforce is the "avoid duplicate economic reversal" precondition: an invoice that already has a
credit note issued against it cannot be voided at all (`DOMAIN_RULE_REJECTED`) — the credit note
is the correction instrument at that point.

**What void does do:** historical totals/snapshots are left exactly as posted (never edited), and
since receivable views already exclude `VOIDED` invoices, that alone removes it from the
customer's outstanding balance. Physical stock **is** reversed: each `PRODUCT` line (excluding
`JobPart`-sourced lines, which never had a `SALE` movement to reverse) gets a `SALE_VOID` stock
movement.

**Response body:** full `InvoiceDetailResponse` (§2 shape), `status: "VOIDED"`.

**Errors:** `404 INVOICE_NOT_FOUND`; `409 CONCURRENT_MODIFICATION` (stale `version`); `409
DOMAIN_RULE_REJECTED` (credit notes already issued against it); `409 INVOICE_NOT_POSTED`
(not currently `POSTED` — covers both "still `DRAFT`" and "already `VOIDED`").

---

## 12. Record Payment

```
POST /api/v1/invoices/{invoiceId}/payments          (against one specific invoice)
POST /api/v1/customer-payments                       (one payment split across several invoices)
```

Permission: `invoice.payment.create` (both). Both require an `Idempotency-Key` header; response
carries `Idempotency-Replayed`, status `201` first time / `200` on replay. These record a payment
against an **already-`POSTED`** invoice *after* posting — distinct from the payment lines
accepted inline by §10's post request, which only ever pay the invoice being posted in that same
transaction. The customer is row-locked for the duration so two concurrent payments against the
same customer's invoices serialize (a combined over-allocation cannot commit).

**Single-invoice request** (`/invoices/{invoiceId}/payments`):

```json
{
  "paymentDate": "2026-08-19T10:00:00Z",
  "paymentMethod": "BANK_TRANSFER",
  "amount": 15000.00,
  "referenceNumber": "TXN-88213",
  "notes": null
}
```

**Multi-invoice request** (`/customer-payments`) — one payment, several allocations, must sum
exactly to `amount`:

```json
{
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "paymentDate": "2026-08-19T10:00:00Z",
  "paymentMethod": "CASH",
  "amount": 20000.00,
  "referenceNumber": null,
  "notes": "Partial settlement of running account",
  "allocations": [
    { "invoiceId": "b1a2c3d4-...", "amount": 12000.00 },
    { "invoiceId": "c2d3e4f5-...", "amount": 8000.00 }
  ]
}
```

Each allocation's target invoice must belong to the given customer, be `POSTED`, and the
allocation amount cannot exceed that invoice's current remaining balance. A `CashbookEntry`
(direction `IN`, source `CUSTOMER_PAYMENT`) is recorded in the same transaction.

**Response body** `200/201`

```json
{
  "customerPaymentId": "d4e5f6a7-8b9c-4d1e-8f0a-2b3c4d5e6f7a",
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "paymentDate": "2026-08-19T10:00:00Z",
  "paymentMethod": "CASH",
  "amount": 20000.00,
  "referenceNumber": null,
  "receivedBy": "7c8d9e0f-4444-4a55-9b66-1c7d8e9f0a1b",
  "allocations": [
    { "invoiceId": "b1a2c3d4-...", "amount": 12000.00, "invoiceBalanceAfter": 0.00 },
    { "invoiceId": "c2d3e4f5-...", "amount": 8000.00, "invoiceBalanceAfter": 3000.00 }
  ]
}
```

**Errors:** `404 INVOICE_NOT_FOUND`; `404 CUSTOMER_NOT_FOUND`; `409 INVOICE_NOT_POSTED`; `400
DOMAIN_RULE_REJECTED` (invoice/customer mismatch, non-positive amount, no allocations); `400
PAYMENT_ALLOCATION_MISMATCH` (allocations don't sum to `amount`, multi-invoice only); `409
PAYMENT_ALLOCATION_EXCEEDS_BALANCE`.

---

## 13. Payment History

```
GET /api/v1/invoices/{invoiceId}/payments
GET /api/v1/customers/{customerId}/payments
```

Permissions: `invoice.read` (by invoice), `customer.read` (by customer). Both return
`CustomerPaymentSearchResponse` — `{ "data": [ <CustomerPaymentResponse, shape as §12> ] }`, not
paginated.

---

## 14. Hold / Resume Bill

```
POST   /api/v1/held-sales
GET    /api/v1/held-sales
GET    /api/v1/held-sales/{heldSaleId}
POST   /api/v1/held-sales/{heldSaleId}/resume
PUT    /api/v1/held-sales/{heldSaleId}
POST   /api/v1/held-sales/{heldSaleId}/cancel
POST   /api/v1/held-sales/{heldSaleId}/convert
```

Permission: `invoice.hold_bill` for every route on this controller. A hold reserves stock but
posts **no** stock movement (`StateMachines.md` §7.3) — it only has to prove the reservation is
coverable right now, checked under the same product lock every stock-affecting path takes.

**Hold request:**

```json
{
  "customerId": null,
  "items": [
    { "productId": "d3e4f5a6-...", "quantity": 2, "unitPrice": null, "discount": { "type": "NONE", "value": 0 } }
  ],
  "notes": "Customer stepped out to get their car"
}
```

`unitPrice` defaults to the product's selling price when omitted. Expiry
(`bizco.sales.held-sale-expiry-minutes`, default 120 minutes) is set automatically on hold and
refreshed on every `PUT` update.

**Response:** `201 Created`, `Location: /api/v1/held-sales/{heldSaleId}` — `HeldSaleDetailResponse`:

```json
{
  "heldSaleId": "e5f6a7b8-9c0d-4e1f-8a2b-3c4d5e6f7a8b",
  "heldNumber": "HLD-20260819-0004",
  "customerId": null,
  "cashierId": "7c8d9e0f-4444-4a55-9b66-1c7d8e9f0a1b",
  "status": "HELD",
  "heldAt": "2026-08-19T11:00:00Z",
  "expiresAt": "2026-08-19T13:00:00Z",
  "convertedInvoiceId": null,
  "notes": "Customer stepped out to get their car",
  "version": 0,
  "items": [
    { "heldSaleItemId": "...", "productId": "d3e4f5a6-...", "sku": "CAB-001", "productName": "USB-C Cable",
      "quantity": 2.000, "unitPriceSnapshot": 100.00, "discountType": "NONE", "discountValue": 0.00,
      "estimatedLineTotal": 200.00 }
  ]
}
```

**`PUT` update** replaces the item list and/or customer/notes wholesale, re-checking availability
against the *net* new demand (crediting back this same hold's existing reservation first, so
resizing an active hold isn't double-counted against itself). **`resume`** flips `HELD` →
`RESUMED` (no other effect — a UI signal that the cashier is actively working this cart again).
**`cancel`** releases the reservation (`HELD`/`RESUMED` → `CANCELLED`). **`convert`** builds a
real `DRAFT` invoice from the held cart's items and links it — the held sale itself stays
`HELD`/`RESUMED` until that invoice is actually posted (§10), at which point posting marks it
`CONVERTED` atomically in the same transaction. Calling `convert` again after a successful first
call returns the same already-linked draft rather than creating a second one.

**Search query parameters:** `status`, `cashierId`. Returns `{ "data": [ <HeldSaleSummaryResponse> ] }`,
not paginated.

**Errors:** `404 HELD_SALE_NOT_FOUND`; `404 CUSTOMER_NOT_FOUND`; `404 PRODUCT_NOT_FOUND`; `409
CONCURRENT_MODIFICATION` (update, stale `version`); `409 HELD_SALE_NOT_ACTIVE` (resume/update/
cancel/convert on an already `CANCELLED`/`EXPIRED`/`CONVERTED` hold); `400 DOMAIN_RULE_REJECTED`
(no items); `409 STOCK_INSUFFICIENT`.

---

## 15. Credit Notes / Returns

```
GET  /api/v1/invoices/{invoiceId}/return-eligibility
POST /api/v1/credit-notes
GET  /api/v1/credit-notes/{creditNoteId}
GET  /api/v1/credit-notes
```

Permissions: `invoice.credit_note.create` (eligibility, issue), `invoice.read` (get, search).
`POST /credit-notes` requires an `Idempotency-Key` header (status `201` first time / `200` on
replay).

**Return eligibility** — call before building the credit note request, to show the cashier what's
still returnable:

```
GET /api/v1/invoices/{invoiceId}/return-eligibility
```

```json
{
  "invoiceId": "b1a2c3d4-...",
  "invoiceDate": "2026-08-15",
  "returnDeadline": "2026-08-22",
  "withinWindow": true,
  "lines": [
    { "invoiceLineId": "c2d3e4f5-...", "description": "USB-C Cable", "quantitySold": 2.000,
      "quantityAlreadyReturned": 0.000, "quantityRemaining": 2.000, "taxCategory": "STANDARD",
      "vatRateSnapshot": 0.0000, "restockEligible": true }
  ]
}
```

The return window is one uniform configurable number of days (`bizco.sales.return-window-days`,
default `7`) applied to every invoice — MVP.md §5.8.1's per-business-type window (7 days general
retail, 14 days electronics) is **not yet differentiated**, since nothing in the current schema
classifies a product by business type (only `ProductType` INVENTORY/SERVICE, a different axis).

**Issue credit note:**

```json
{
  "originalInvoiceId": "b1a2c3d4-...",
  "reason": "Customer changed their mind",
  "lines": [
    { "invoiceLineId": "c2d3e4f5-...", "quantityReturned": 1.000, "restock": true }
  ],
  "settlement": { "type": "REFUND", "paymentMethod": "CASH", "originalCustomerPaymentId": null }
}
```

`settlement.type` is one of:

| Type | Effect |
|---|---|
| `APPLY_TO_BALANCE` | Reduces the original invoice's remaining balance. No cashbook effect. Rejected if the credit note total exceeds that remaining balance. |
| `REFUND` | Records a `CustomerRefund` and a `CashbookEntry` (direction `OUT`, source `CUSTOMER_REFUND`). No invoice-balance effect. Requires `settlement.paymentMethod`. |
| `CUSTOMER_CREDIT` (default if `settlement` omitted) | Leaves the credit note `ISSUED` with no immediate settlement effect — a standing credit against the customer, applied manually later. |

The invoice is row-locked for the duration, so a concurrent credit note against the same invoice
serializes behind this one (safe for the cumulative-returned-quantity check). Each line's
`quantityAlreadyReturned` (summed across every prior credit note against that line) plus this
request's `quantityReturned` cannot exceed the original quantity sold
(`RETURN_QUANTITY_EXCEEDED` otherwise). A restockable `PRODUCT` line (`restock: true`) posts one
`CUSTOMER_RETURN` stock movement.

**Response:** `CreditNoteResponse`:

```json
{
  "creditNoteId": "f7a8b9c0-1d2e-4f3a-8b4c-5d6e7f8a9b0c",
  "creditNoteNumber": "CN-20260819-0002",
  "originalInvoiceId": "b1a2c3d4-...",
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "APPLIED",
  "reason": "Customer changed their mind",
  "subtotal": 100.00,
  "vatAmount": 0.00,
  "totalAmount": 100.00,
  "issuedAt": "2026-08-19T12:00:00Z",
  "issuedBy": "7c8d9e0f-4444-4a55-9b66-1c7d8e9f0a1b",
  "appliedAt": "2026-08-19T12:00:00Z",
  "lines": [
    { "creditNoteLineId": "...", "originalInvoiceLineId": "c2d3e4f5-...", "quantityReturned": 1.000,
      "unitPriceSnapshot": 100.00, "taxableAmount": 100.00, "vatRateSnapshot": 0.0000, "vatAmount": 0.00,
      "lineTotal": 100.00, "restock": true }
  ]
}
```

`status` is `ISSUED` until settled (`APPLY_TO_BALANCE`/`REFUND` settle immediately at issue time,
so in practice a credit note is created already `APPLIED` unless `settlement.type` is
`CUSTOMER_CREDIT`, which leaves it `ISSUED`). **Search** (`GET /credit-notes`) accepts
`customerId`, `invoiceId`, `number`, `fromDate`/`toDate`; returns `{ "data": [...] }`, not
paginated.

**Errors:** `404 INVOICE_NOT_FOUND`; `404 INVOICE_LINE_NOT_FOUND`; `409 INVOICE_NOT_POSTED` (only
a `POSTED` invoice can be returned against); `400 INVOICE_CREDIT_CUSTOMER_REQUIRED` (walk-in sale
with no customer — `credit_notes.customer_id` is `NOT NULL`, so a customer-less invoice cannot be
returned via this endpoint at all); `400 DOMAIN_RULE_REJECTED` (no lines, unknown settlement
type, missing refund payment method, credit exceeds remaining balance); `409
RETURN_QUANTITY_EXCEEDED`; `409 RETURN_WINDOW_EXPIRED`.

---

## 16. Receipt / Tax Invoice PDF

```
GET  /api/v1/invoices/{invoiceId}/receipt
POST /api/v1/invoices/{invoiceId}/receipt/reprint
```

Permissions: `invoice.read` (plain fetch), `invoice.reprint` (reprint — a distinct, audited
action). Both return `Content-Type: application/pdf`, `Content-Disposition: inline`. Only
available once the invoice is `POSTED` (`409 INVOICE_NOT_POSTED` for a `DRAFT`).

One A4 layout serves both the checkout print and a later reprint — there is no separate thermal-
receipt template; MVP.md's thermal printer requirement (§7.7) is a physical output device
consideration, not a distinct document layout. A `VOIDED` invoice's receipt is watermarked
`*** VOIDED - <reason> ***`.

The QR code embedded on the receipt is an **internal Bizco verification payload**
(`BIZCO-INV|<invoiceNumber>|TIN:<tin>|TOTAL:<amount>`), not a LankaQR/e-Invoice compliant,
digitally-signed code — full statutory e-Invoicing (SRS.md §6.11.6.1) remains out of MVP scope. A
CODE-128 barcode of the invoice number is also embedded.

Calling `/receipt/reprint` records an `INVOICE_REPRINTED` audit log entry; the plain `/receipt`
fetch does not.

**Errors:** `404 INVOICE_NOT_FOUND`; `409 INVOICE_NOT_POSTED`.
