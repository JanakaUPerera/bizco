# Purchasing API

**Controllers:** `SupplierController`, `SupplierProductController`, `PurchaseOrderController`, `GoodsReceiptController`, `SupplierPaymentController`, `SupplierReturnController`
**Package:** `com.bizco.server.purchasing`
**Conventions:** see [README.md](README.md).

The full v1.4 purchasing flow is implemented (`DevelopmentPlan.md` Weeks 13–15): supplier master (§1–§5), per-supplier product catalog (§6), the Purchase Order DRAFT → APPROVED/SENT → CLOSED workflow (§7), Goods Receipt DRAFT → POSTED with partial/multi-delivery and cost history (§8), supplier returns against a posted receipt (§9), and supplier payments with multi-receipt allocation (§10). This replaced the single-step GRN model MVP.md v1.3 originally specified — see MVP.md §1.2a.

> **Permission note:** several endpoints below (§6–§9) use `purchasing.read`, `purchasing.supplier_product.*`, and `purchasing.po.*` permission codes. These are **not** in MVP.md §2.1's published permission table, which only lists `supplier.read`, `purchasing.grn.create`, `purchasing.payment.create`, `purchasing.return.create`, and `purchasing.cost_history.read`. The implementation added the missing codes as this module grew past the original supplier-only scope; MVP.md's permission table has not yet been reconciled to match.

---

## 1. Search Suppliers

```
GET /api/v1/suppliers
```

Permission: `supplier.read`

**Query parameters**

| Param | Type | Notes |
|---|---|---|
| `q` | string | matches supplier name/code (blank/omit for no filter) |
| `status` | string | `ACTIVE` or `INACTIVE` |
| `page` | int | default `0` |
| `size` | int | default `20`, capped at `100` |

**Response body** `200 OK`

```json
{
  "data": [
    {
      "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
      "supplierCode": "SUP-0012",
      "name": "Colombo Electronics Distributors",
      "contactPerson": "Ravi Fernando",
      "phone": "0112345678",
      "email": "sales@ced.lk",
      "openingBalance": 0.00,
      "status": "ACTIVE",
      "version": 1
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## 2. Create Supplier

```
POST /api/v1/suppliers
```

Permission: `supplier.create`

**Request body**

```json
{
  "supplierCode": null,
  "name": "Colombo Electronics Distributors",
  "contactPerson": "Ravi Fernando",
  "address": "45 Galle Road, Colombo 03",
  "phone": "0112345678",
  "email": "sales@ced.lk",
  "tinNumber": "134567890",
  "paymentTerms": "NET_30",
  "openingBalance": 0.00
}
```

| Field | Notes |
|---|---|
| `supplierCode` | optional on create — server generates one (`SupplierCodeGenerator`) when blank/omitted |
| `name` | required |
| `phone` | optional; validated against `^(?:0\d{9}|\+94\d{9})$` when present |
| `email` | optional; basic email pattern when present |
| `openingBalance` | optional; rejected if negative; defaults to `0` |

**Response:** `201 Created`, `Location: /api/v1/suppliers/{supplierId}`

```json
{
  "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "supplierCode": "SUP-0012",
  "name": "Colombo Electronics Distributors",
  "contactPerson": "Ravi Fernando",
  "address": "45 Galle Road, Colombo 03",
  "phone": "0112345678",
  "email": "sales@ced.lk",
  "tinNumber": "134567890",
  "paymentTerms": "NET_30",
  "openingBalance": 0.00,
  "status": "ACTIVE",
  "createdAt": "2026-08-16T09:00:00Z",
  "updatedAt": "2026-08-16T09:00:00Z",
  "version": 0
}
```

**Errors:** `400 VALIDATION_FAILED`; `409 SUPPLIER_CODE_DUPLICATE`.

---

## 3. Get Supplier

```
GET /api/v1/suppliers/{supplierId}
```

Permission: `supplier.read`. Response shape as §2. **Errors:** `404 SUPPLIER_NOT_FOUND`.

---

## 4. Update Supplier

```
PUT /api/v1/suppliers/{supplierId}
```

Permission: `supplier.update`

**Request body**

```json
{
  "supplierCode": "SUP-0012",
  "name": "Colombo Electronics Distributors (Pvt) Ltd",
  "contactPerson": "Ravi Fernando",
  "address": "45 Galle Road, Colombo 03",
  "phone": "0112345678",
  "email": "sales@ced.lk",
  "tinNumber": "134567890",
  "paymentTerms": "NET_45",
  "openingBalance": 0.00,
  "status": "ACTIVE",
  "version": 0
}
```

Unlike create, `supplierCode` is **required** on update. `status` accepts `ACTIVE`/`INACTIVE` (defaults to `ACTIVE` if blank).

**Response body:** same shape as §2, `version` incremented.

**Errors:** `400 VALIDATION_FAILED`; `404 SUPPLIER_NOT_FOUND`; `409 CONCURRENT_MODIFICATION` (stale `version`); `409 SUPPLIER_CODE_DUPLICATE`.

---

## 5. Activate / Deactivate Supplier

```
POST /api/v1/suppliers/{supplierId}/activate
POST /api/v1/suppliers/{supplierId}/deactivate
```

Permissions: `supplier.update` (activate), `supplier.deactivate` (deactivate). No request body.

**Response body:** same shape as §2 with `status` toggled between `ACTIVE`/`INACTIVE`.

There is still no `GET /api/v1/suppliers/{supplierId}/statement` endpoint under the supplier
resource itself (`ApiContracts.md` §23.1's exact shape is design-only) — the equivalent
supplier-statement/outstanding-balance view that did land lives under the goods-receipt resource
instead: `GET /api/v1/goods-receipts/outstanding?supplierId=...` (§8.6).

---

## 6. Supplier Product Catalog

Per-supplier SKU/price/lead-time entries (MVP.md §8.4a, SRS.md §6.9.4). Lets a Purchase Order
line (§7.3) pre-fill from a known supplier-variant relationship. A product may be catalogued
under multiple suppliers; at most one entry per product is marked `preferred` — marking a new
entry preferred automatically demotes whichever other entry for that same product previously held
it, rather than requiring the caller to un-check the old one first.

```
GET  /api/v1/supplier-products
POST /api/v1/supplier-products
PUT  /api/v1/supplier-products/{supplierProductId}
```

Permissions: `purchasing.read` (search), `purchasing.supplier_product.create` (create),
`purchasing.supplier_product.update` (update).

**Search query parameters:** `supplierId`, `productId`, `page`/`size` (default `0`/`20`, `size`
capped at `100`).

**Create request:**

```json
{
  "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a",
  "supplierSku": "CED-CAB-USBC-01",
  "purchasePrice": 350.00,
  "minOrderQty": 10,
  "leadTimeDays": 5,
  "preferred": true
}
```

**Response:** `201 Created`, `Location: /api/v1/supplier-products/{supplierProductId}`

```json
{
  "supplierProductId": "a2b3c4d5-6e7f-4a1b-9c8d-7e6f5a4b3c2e",
  "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "supplierName": "Colombo Electronics Distributors",
  "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a",
  "sku": "CAB-001",
  "productName": "USB-C Cable",
  "supplierSku": "CED-CAB-USBC-01",
  "purchasePrice": 350.00,
  "minOrderQty": 10.000,
  "leadTimeDays": 5,
  "lastPurchasePrice": null,
  "preferred": true,
  "createdAt": "2026-08-19T09:00:00Z",
  "updatedAt": "2026-08-19T09:00:00Z",
  "version": 0
}
```

`lastPurchasePrice` starts `null` and is updated automatically to the unit cost of every goods
receipt posted against this supplier+product pair (§8.5) — distinct from `purchasePrice`, the
manually-maintained catalog/quoted price.

**Update request:** same body shape minus `supplierId`/`productId` (immutable after creation),
plus `version`.

**Errors:** `404 SUPPLIER_NOT_FOUND`; `404 PRODUCT_NOT_FOUND`; `404
SUPPLIER_PRODUCT_NOT_FOUND` (update); `409 CONCURRENT_MODIFICATION` (update, stale `version`);
`409 SUPPLIER_PRODUCT_DUPLICATE` (this supplier already has a catalog entry for this product).

---

## 7. Purchase Orders

`DRAFT → APPROVED → SENT → PARTIALLY_RECEIVED → FULLY_RECEIVED → CLOSED/CANCELLED`
(`DatabaseDesign.md` §17.3, MVP.md §8.1). `PARTIALLY_RECEIVED`/`FULLY_RECEIVED` are set by Goods
Receipt posting (§8.5), not by any endpoint on this controller.

### 7.1 Create Draft / Update Header

```
POST /api/v1/purchase-orders
PUT  /api/v1/purchase-orders/{purchaseOrderId}
```

Permission: `purchasing.po.create` (both).

```json
{
  "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "poDate": "2026-08-19",
  "expectedDate": "2026-08-26",
  "validUntil": "2026-09-02",
  "notes": "Monthly restock"
}
```

`poDate` defaults to today if omitted. Update takes the same fields plus `version`; a PO's items
are managed separately (§7.2), not inline on this request.

**Response:** `201 Created` (create), `200 OK` (update):

```json
{
  "purchaseOrderId": "b3c4d5e6-7f8a-4b1c-9d0e-1f2a3b4c5d6e",
  "poNumber": null,
  "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "supplierName": "Colombo Electronics Distributors",
  "poDate": "2026-08-19",
  "expectedDate": "2026-08-26",
  "validUntil": "2026-09-02",
  "status": "DRAFT",
  "subtotal": 0.00,
  "totalAmount": 0.00,
  "approvedBy": null,
  "approvedAt": null,
  "notes": "Monthly restock",
  "createdAt": "2026-08-19T09:00:00Z",
  "updatedAt": "2026-08-19T09:00:00Z",
  "version": 0,
  "items": []
}
```

`poNumber` stays `null` until the PO first reaches `APPROVED` or `SENT` (§7.4/§7.5) — like
invoice numbering, a PO number is not consumed by a draft.

**Errors:** `404 SUPPLIER_NOT_FOUND`; `404 PURCHASE_ORDER_NOT_FOUND` (update); `409
CONCURRENT_MODIFICATION` (update, stale `version`).

### 7.2 Add / Remove Item

```
POST   /api/v1/purchase-orders/{purchaseOrderId}/items
DELETE /api/v1/purchase-orders/{purchaseOrderId}/items/{itemId}
```

Permission: `purchasing.po.create` (both).

```json
{ "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a", "quantityOrdered": 100, "unitPrice": 320.00 }
```

`quantityOrdered` must be `> 0`, `unitPrice` must be `>= 0`. Every add/remove recalculates
`subtotal`/`totalAmount` (currently identical — there is no separate tax/shipping component on a
PO) and returns the full recalculated `PurchaseOrderDetailResponse` (§7.1 shape).

**Errors:** `400 VALIDATION_FAILED`; `404 PURCHASE_ORDER_NOT_FOUND`; `404 PRODUCT_NOT_FOUND`; `404
PURCHASE_ORDER_ITEM_NOT_FOUND` (remove).

### 7.3 Approve / Send

```
POST /api/v1/purchase-orders/{purchaseOrderId}/approve
POST /api/v1/purchase-orders/{purchaseOrderId}/send
```

Permissions: `purchasing.po.approve` (approve), `purchasing.po.create` (send). Both require an
`Idempotency-Key` header; response carries `Idempotency-Replayed`.

**Value-based approval threshold** (MVP.md/DevelopmentPlan.md Week 13 task 13.6): a PO whose
`totalAmount` is **at or above** a configurable threshold
(`purchasing.po_approval_threshold` system config key, default `LKR 50,000.00`) must pass through
`APPROVED` (requiring `purchasing.po.approve`) before it can be `SENT`. Below the threshold,
`purchasing.po.create` alone is enough to send directly from `DRAFT` — `send` skips the approve
step entirely for a small PO. `poNumber` is allocated at whichever of `approve`/`send` the PO
actually reaches first (an approved-then-sent PO keeps the number it got at approval).

**Request body:** `{ "version": 0 }` for both.

**Response body:** `PurchaseOrderDetailResponse` (§7.1 shape), `status` now `APPROVED` or `SENT`,
`poNumber` allocated (`PO-YYYYMMDD-NNNN`).

**Errors:** `404 PURCHASE_ORDER_NOT_FOUND`; `409 CONCURRENT_MODIFICATION` (stale `version`); `409
PURCHASE_ORDER_NOT_DRAFT` (approve only); `409 PURCHASE_ORDER_APPROVAL_REQUIRED` (send attempted
on a `DRAFT` at/above the threshold); `409 PURCHASE_ORDER_INVALID_TRANSITION` (send from a status
that isn't `DRAFT`-below-threshold or `APPROVED`).

### 7.4 Cancel / Close Remaining Balance

```
POST /api/v1/purchase-orders/{purchaseOrderId}/cancel
POST /api/v1/purchase-orders/{purchaseOrderId}/close-remaining-balance
```

Permission: `purchasing.po.create` (both). `{ "reason": "...", "version": 0 }`.

`cancel` is for a PO that hasn't (fully) proceeded. `close-remaining-balance` is the Week 14
"write off what a `PARTIALLY_RECEIVED` PO will never receive the rest of" operation — it closes a
PO that has received *some* but not all of its ordered quantity and isn't going to receive the
rest, without needing to fabricate a zero-quantity final receipt just to reach `CLOSED`.

**Errors:** `404 PURCHASE_ORDER_NOT_FOUND`; `409 CONCURRENT_MODIFICATION`; `409
PURCHASE_ORDER_INVALID_TRANSITION`; `400 VALIDATION_FAILED` (missing reason).

### 7.5 Search / Get

```
GET /api/v1/purchase-orders
GET /api/v1/purchase-orders/{purchaseOrderId}
```

Permission: `purchasing.read` (both). Search params: `supplierId`, `status`, `page`/`size`
(default `0`/`20`, capped at `100`) — returns `PurchaseOrderSearchResponse`
(`PurchaseOrderSummaryResponse` rows, no `items`). Get returns the full §7.1 shape.

---

## 8. Goods Receipts

`DRAFT → POSTED` (`REVERSED` exists in the status enum for a future reversal path, but nothing in
this module currently transitions a receipt to it). This is the document that actually moves
stock — MVP.md §1.2a: the v1.4 replacement for the single-step "GRN" model, optionally linked to
a Purchase Order (§7) but not required to be (`purchaseOrderId` stays nullable so a small, ad-hoc
purchase can still be received directly).

### 8.1 Create Draft

```
POST /api/v1/goods-receipts
```

Permission: `purchasing.grn.create`.

```json
{
  "purchaseOrderId": "b3c4d5e6-7f8a-4b1c-9d0e-1f2a3b4c5d6e",
  "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "supplierReference": "INV-CED-88213",
  "receiptDate": "2026-08-19",
  "notes": "First of two partial deliveries"
}
```

`purchaseOrderId` is optional. `receiptDate` defaults to today if omitted.

**Response:** `201 Created`, `Location: /api/v1/goods-receipts/{goodsReceiptId}`

```json
{
  "goodsReceiptId": "c4d5e6f7-8a9b-4c1d-8e2f-3a4b5c6d7e8f",
  "receiptNumber": null,
  "purchaseOrderId": "b3c4d5e6-7f8a-4b1c-9d0e-1f2a3b4c5d6e",
  "poNumber": "PO-20260819-0001",
  "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "supplierName": "Colombo Electronics Distributors",
  "supplierReference": "INV-CED-88213",
  "receiptDate": "2026-08-19",
  "status": "DRAFT",
  "totalAmount": 0.00,
  "notes": "First of two partial deliveries",
  "createdBy": "7c8d9e0f-4444-4a55-9b66-1c7d8e9f0a1b",
  "createdAt": "2026-08-19T09:00:00Z",
  "postedAt": null,
  "version": 0,
  "items": []
}
```

`receiptNumber` stays `null` until posted. **Errors:** `404 SUPPLIER_NOT_FOUND`; `404
PURCHASE_ORDER_NOT_FOUND` (if `purchaseOrderId` supplied but unknown).

### 8.2 Add / Remove Item

```
POST   /api/v1/goods-receipts/{goodsReceiptId}/items
DELETE /api/v1/goods-receipts/{goodsReceiptId}/items/{itemId}
```

Permission: `purchasing.grn.create` (both).

```json
{
  "purchaseOrderItemId": "d5e6f7a8-9b0c-4d1e-8f2a-3b4c5d6e7f8a",
  "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a",
  "quantityReceived": 50,
  "quantityDamaged": 2,
  "quantityRejected": 0,
  "unitCost": 320.00
}
```

`purchaseOrderItemId` is optional (present when this line fulfils a specific PO line — omit for
an ad-hoc receipt or an unplanned extra item). `quantityReceived` must be `> 0`, `unitCost` must
be `>= 0`. The line's `usableQuantity = quantityReceived - quantityDamaged - quantityRejected` is
what actually posts to stock at §8.5 — damaged/rejected units are recorded but never increase
available stock.

**Errors:** `400 VALIDATION_FAILED`; `404 GOODS_RECEIPT_NOT_FOUND`; `404 PRODUCT_NOT_FOUND`; `404
GOODS_RECEIPT_ITEM_NOT_FOUND` (remove).

### 8.3 Post

```
POST /api/v1/goods-receipts/{goodsReceiptId}/post
```

Permission: `purchasing.grn.create`. Requires an `Idempotency-Key` header; response carries
`Idempotency-Replayed`. `DRAFT` → `POSTED`, atomically:

1. Every distinct product on the receipt is locked (stable order).
2. `receiptNumber` is allocated (`GRN-YYYYMMDD-NNNN`).
3. For each item with `usableQuantity > 0`, a `GRN` stock movement is posted.
4. A `ProductCostHistory` row is recorded for every item (regardless of usable quantity), and
   both the product's own `costPrice` and — if a matching `SupplierProduct` catalog entry exists
   (§6) — that entry's `lastPurchasePrice` are updated to the line's `unitCost`.
5. If linked to a Purchase Order, that PO's receiving progress is recalculated: the PO is locked,
   cumulative posted-receipt quantity per PO line is compared against each line's ordered
   quantity, and the PO moves to `PARTIALLY_RECEIVED` or `FULLY_RECEIVED` accordingly (only if at
   least one line has received progress at all).

**Request body:** `{ "version": 0 }`.

**Response body:** same shape as §8.1, `status: "POSTED"`, `receiptNumber` populated.

**Errors:** `404 GOODS_RECEIPT_NOT_FOUND`; `409 CONCURRENT_MODIFICATION` (stale `version`); `409
GOODS_RECEIPT_NOT_DRAFT`; `409 GOODS_RECEIPT_SUPPLIER_REFERENCE_DUPLICATE` (this
`supplierReference` was already used on another posted receipt); `409
PURCHASE_ORDER_INVALID_TRANSITION` (linked PO in an unexpected state).

### 8.4 Search / Get

```
GET /api/v1/goods-receipts
GET /api/v1/goods-receipts/{goodsReceiptId}
```

Permission: `purchasing.read` (both). Search params: `supplierId`, `status`, `page`/`size`
(default `0`/`20`, capped at `100`) — `GoodsReceiptSummaryResponse` rows (no `items`). Get
returns the full §8.1/§8.3 shape.

### 8.5 Product Cost History

```
GET /api/v1/goods-receipts/cost-history?productId=...
```

Permission: `purchasing.cost_history.read`.

```json
{
  "data": [
    { "productCostHistoryId": "...", "productId": "d3e4f5a6-...", "goodsReceiptItemId": "...",
      "unitCost": 320.00, "effectiveAt": "2026-08-19T09:15:00Z" }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Ordered most-recent-first. One row is written per posted goods-receipt item regardless of
`usableQuantity` — a fully damaged/rejected line still records what it cost.

### 8.6 Outstanding / Supplier Statement

```
GET /api/v1/goods-receipts/outstanding?supplierId=...&outstandingOnly=...
```

Permission: `purchasing.read`. The supplier statement / outstanding-balance view
(`DatabaseDesign.md` §18, DevelopmentPlan.md Week 15 task 15.5) — see §5's closing note. Backed by
the `v_goods_receipt_outstanding` view: one row per posted goods receipt, netting off supplier
returns and payment allocations.

```json
{
  "data": [
    { "goodsReceiptId": "c4d5e6f7-...", "receiptNumber": "GRN-20260819-0001", "supplierId": "8a1b2c3d-...",
      "supplierName": "Colombo Electronics Distributors", "receiptDate": "2026-08-19", "totalAmount": 16000.00,
      "returnedAmount": 640.00, "paidAmount": 10000.00, "outstandingAmount": 5360.00 }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

`outstandingOnly=true` filters to receipts where `outstandingAmount > 0`.

---

## 9. Supplier Returns

Returns goods out of one already-**`POSTED`** goods receipt (`DatabaseDesign.md` §17.8/17.9,
Week 15). One-shot — no draft/post split, unlike Purchase Orders and Goods Receipts: created and
settled (stock deducted) in a single idempotency-wrapped request, the same shape as credit notes
on the sales side ([Sales.md](Sales.md) §15).

```
POST /api/v1/supplier-returns
GET  /api/v1/supplier-returns
GET  /api/v1/supplier-returns/{supplierReturnId}
```

Permissions: `purchasing.return.create` (create), `purchasing.read` (search/get). Create requires
an `Idempotency-Key` header; response carries `Idempotency-Replayed` and `Location`.

**Create request:**

```json
{
  "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "goodsReceiptId": "c4d5e6f7-8a9b-4c1d-8e2f-3a4b5c6d7e8f",
  "reason": "2 units arrived damaged, discovered after posting",
  "items": [
    { "goodsReceiptItemId": "d5e6f7a8-9b0c-4d1e-8f2a-3b4c5d6e7f8a", "quantityReturned": 2 }
  ]
}
```

`unitCost` is deliberately **not** client-supplied on a return line — it is read from the goods
receipt item's own recorded `unitCost` (the same "trust the ledger" rule the rest of this module
follows). The goods receipt is row-locked for the duration, so two concurrent returns against the
same receipt's lines serialize — cumulative returned quantity (across every prior return against
that line) plus this request's `quantityReturned` cannot exceed the line's `usableQuantity`. One
`SUPPLIER_RETURN` stock movement is posted per return line.

**Response:** `201 Created` (or `200` on idempotent replay), `Location:
/api/v1/supplier-returns/{supplierReturnId}`

```json
{
  "supplierReturnId": "e6f7a8b9-0c1d-4e2f-8a3b-4c5d6e7f8a9b",
  "returnNumber": "SRT-20260819-0001",
  "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "supplierName": "Colombo Electronics Distributors",
  "goodsReceiptId": "c4d5e6f7-8a9b-4c1d-8e2f-3a4b5c6d7e8f",
  "receiptNumber": "GRN-20260819-0001",
  "totalAmount": 640.00,
  "reason": "2 units arrived damaged, discovered after posting",
  "createdBy": "7c8d9e0f-4444-4a55-9b66-1c7d8e9f0a1b",
  "createdAt": "2026-08-19T13:00:00Z",
  "items": [
    { "supplierReturnItemId": "...", "goodsReceiptItemId": "d5e6f7a8-...", "productId": "d3e4f5a6-...",
      "sku": "CAB-001", "productName": "USB-C Cable", "quantityReturned": 2.000, "unitCost": 320.00,
      "lineTotal": 640.00 }
  ]
}
```

There is deliberately no separate "reduce supplier payable" step to call out here — reducing what
is owed is a *read-side* effect: `v_goods_receipt_outstanding` (§8.6) nets `returnedAmount`
against `totalAmount` automatically once this return is posted, rather than the return writing to
some separate payable balance field.

**Search query parameters:** `supplierId`, `goodsReceiptId`, `page`/`size` (default `0`/`20`,
capped at `100`).

**Errors:** `400 DOMAIN_RULE_REJECTED` (missing reason, no items, missing
`goodsReceiptItemId`/non-positive `quantityReturned`); `404 GOODS_RECEIPT_NOT_FOUND`; `409
GOODS_RECEIPT_NOT_POSTED`; `400 DOMAIN_RULE_REJECTED` (goods receipt doesn't belong to the given
supplier); `404 GOODS_RECEIPT_ITEM_NOT_FOUND`; `409 RETURN_QUANTITY_EXCEEDED`.

---

## 10. Supplier Payments

Records a payment to a supplier, optionally allocated across one or more of that supplier's
outstanding goods receipts (`DatabaseDesign.md` §17.10/17.11, Week 15). One-shot, mirroring
[Sales.md](Sales.md) §12's `/customer-payments`, and writes a matching `CashbookEntry`
(direction `OUT`, source `SUPPLIER_PAYMENT`) in the same transaction.

```
POST /api/v1/supplier-payments
GET  /api/v1/supplier-payments
GET  /api/v1/supplier-payments/{supplierPaymentId}
```

Permission: `purchasing.payment.create` (create), `purchasing.read` (search/get). Create requires
an `Idempotency-Key` header; response carries `Idempotency-Replayed` and `Location`.

**Request body:**

```json
{
  "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "paymentDate": "2026-08-19T14:00:00Z",
  "paymentMethod": "BANK_TRANSFER",
  "amount": 10000.00,
  "referenceNumber": "TXN-77213",
  "notes": null,
  "allocations": [
    { "goodsReceiptId": "c4d5e6f7-8a9b-4c1d-8e2f-3a4b5c6d7e8f", "amount": 10000.00 }
  ]
}
```

`allocations` may be **empty** — an unallocated payment is simply a supplier-account
credit/prepayment, not an error. Every goods receipt named in `allocations` is locked (in
ascending id order, to avoid two concurrent payments deadlocking against each other) and must be:
`POSTED`, belong to the given supplier, and not already allocated more than once within the same
request. Each allocation's `amount` cannot exceed that receipt's current outstanding balance
(§8.6), and the sum of allocations cannot exceed the payment's own `amount` (an
under-allocated remainder is allowed — it's simply unallocated credit).

**Response:**

```json
{
  "supplierPaymentId": "f7a8b9c0-1d2e-4f3a-8b4c-5d6e7f8a9b0c",
  "supplierId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "supplierName": "Colombo Electronics Distributors",
  "paymentDate": "2026-08-19T14:00:00Z",
  "paymentMethod": "BANK_TRANSFER",
  "amount": 10000.00,
  "referenceNumber": "TXN-77213",
  "notes": null,
  "paidBy": "7c8d9e0f-4444-4a55-9b66-1c7d8e9f0a1b",
  "createdAt": "2026-08-19T14:00:00Z",
  "allocations": [
    { "goodsReceiptId": "c4d5e6f7-...", "receiptNumber": "GRN-20260819-0001", "amount": 10000.00,
      "outstandingAfter": 5360.00 }
  ]
}
```

**Search query parameters:** `supplierId`, `page`/`size` (default `0`/`20`, capped at `100`) —
`SupplierPaymentSearchResponse` is `{ "data": [...] }`, not actually paginated in the response
envelope despite accepting paging inputs.

**Errors:** `404 SUPPLIER_NOT_FOUND`; `400 DOMAIN_RULE_REJECTED` (non-positive amount, missing
`goodsReceiptId` on an allocation, a receipt allocated twice in one request, allocations exceed
payment amount); `404 GOODS_RECEIPT_NOT_FOUND`; `409 GOODS_RECEIPT_NOT_POSTED`; `400
DOMAIN_RULE_REJECTED` (receipt doesn't belong to this supplier); `409
PAYMENT_ALLOCATION_EXCEEDS_BALANCE`.
