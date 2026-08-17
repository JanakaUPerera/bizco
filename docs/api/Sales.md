# Sales API

**Controllers:** `InvoiceController`, `SalesApprovalController`
**Package:** `com.bizco.server.sales`
**Conventions:** see [README.md](README.md).

Draft invoices, lines, pricing preview, and manager approval evidence are implemented. Posting
(`DRAFT` → `POSTED`), held sales, payments, credit notes, void, and receipts are design-only —
see [README.md](README.md) §2. A `DRAFT` invoice has no stock, receivable, cashbook, or VAT/sales
report effect; every mutating endpoint below rejects a non-`DRAFT` invoice.

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

There is no `paymentStatus` filter or field yet — it depends on the not-yet-implemented payment
recording (`ApiContracts.md` §17), which is derived from posted payments, not stored on the draft.

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

> **Approval enforcement note:** unlike `ApiContracts.md`'s posting-time description, this
> implementation performs zero discount-tier, price-override, or below-cost validation at draft
> add/update time — a `DRAFT` has no financial effect, so any value can be set here. That
> enforcement (`INVOICE_DISCOUNT_APPROVAL_REQUIRED`, etc., cross-checked against §9's approval
> evidence) is deferred entirely to the not-yet-implemented `POST /invoices/{id}/post`.

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
