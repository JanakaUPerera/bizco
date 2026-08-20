# Inventory API

**Controllers:** `StockController`, `StockAdjustmentController`
**Package:** `com.bizco.server.inventory`
**Conventions:** see [README.md](README.md).

Stock levels, movement history, low-stock, and the stock-adjustment PENDING → APPROVED/REJECTED
workflow are implemented (`DevelopmentPlan.md` Week 12). `stock_movements` is authoritative for
physical stock — every response here is a read projection over that ledger, never a separately
maintained counter. There is no `inventory.read`-level "current balance" column stored anywhere;
`StockLevelResponse.physicalStock` is computed from movements each time.

---

## 1. Stock Levels

```
GET /api/v1/stock/levels
```

Permission: `inventory.read`

**Query parameters**

| Param | Type | Notes |
|---|---|---|
| `q` | string | matches product name/SKU (blank/omit for no filter) |
| `page` | int | default `0` |
| `size` | int | default `20`, capped at `100` |

**Response body** `200 OK`

```json
{
  "data": [
    {
      "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a",
      "sku": "CAB-001",
      "name": "USB-C Cable",
      "reorderPoint": 10.000,
      "physicalStock": 42.000,
      "reservedStock": 3.000,
      "availableStock": 39.000,
      "lowStock": false
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`physicalStock` sums every posted `stock_movements` row for the product. `reservedStock` is the
sum of active (`HELD`/`RESUMED`) held-sale item quantities for the product (MVP.md §5.9).
`availableStock = physicalStock - reservedStock`. `lowStock` is `availableStock <= reorderPoint`.

---

## 2. Stock Level for One Product

```
GET /api/v1/stock/levels/{productId}
```

Permission: `inventory.read`. Response shape as a single item from §1.

---

## 3. Low Stock

```
GET /api/v1/stock/low-stock
GET /api/v1/stock/low-stock/summary
```

Permission: `inventory.read`

`GET /low-stock` returns the same paginated `StockLevelSearchResponse` shape as §1, filtered to
`lowStock = true`. `GET /low-stock/summary` returns just the count:

```json
{ "lowStockCount": 4 }
```

Intended as the dashboard KPI source once the (not-yet-implemented) Reporting module's dashboard
lands — see [README.md](README.md) §2.

---

## 4. Movement History

```
GET /api/v1/stock/movements?productId=...
GET /api/v1/stock/movements/by-reference?referenceType=...&referenceId=...
```

Permission: `inventory.read`

**Query parameters**

| Param | Type | Notes |
|---|---|---|
| `productId` | UUID | required on `/movements` |
| `referenceType` | string | required on `/movements/by-reference` — `INVOICE`, `GRN`, `CREDIT_NOTE`, `SUPPLIER_RETURN`, `JOB_CARD`, `STOCK_ADJUSTMENT` |
| `referenceId` | UUID | required on `/movements/by-reference` — the id of that reference document |
| `page` / `size` | int | default `0` / `20`, `size` capped at `100` |

**Response body** `200 OK`

```json
{
  "data": [
    {
      "stockMovementId": "e1f2a3b4-5c6d-4e7f-8a9b-0c1d2e3f4a5b",
      "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a",
      "sku": "CAB-001",
      "productName": "USB-C Cable",
      "movementType": "SALE",
      "quantity": -2.000,
      "referenceType": "INVOICE",
      "referenceId": "b1a2c3d4-5e6f-4a1b-9c8d-7e6f5a4b3c2d",
      "sourceLineId": "c2d3e4f5-6a7b-4c1d-8e9f-1a2b3c4d5e6f",
      "notes": null,
      "createdBy": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "createdAt": "2026-08-17T09:05:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

`movementType` is one of `GRN`, `GRN_REVERSAL`, `SALE`, `SALE_VOID`, `CUSTOMER_RETURN`,
`SUPPLIER_RETURN`, `JOB_PART`, `JOB_PART_REVERSAL`, `ADJUSTMENT`, `ADJUSTMENT_REVERSAL` — the full
`stock_movement_type` set as it stands today, wider than MVP.md §18's original DDL sketch because
every posting/void/reversal path (invoice post, invoice void, credit note restock, supplier
return, job part, job part reversal, adjustment approval) got its own distinct type as those
modules landed. `quantity` is signed: negative for outflows (`SALE`, `SUPPLIER_RETURN`,
`JOB_PART`), positive for inflows (`GRN`, `CUSTOMER_RETURN`) and reversals of an outflow
(`SALE_VOID`, `JOB_PART_REVERSAL`). `sourceLineId` points at the originating line item (invoice
line, GRN item, credit note line, etc.) when the movement type has one.

---

## 5. Create Stock Adjustment

```
POST /api/v1/stock-adjustments
```

Permission: `inventory.adjustment.create`

**Request body**

```json
{
  "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a",
  "adjustmentType": "NEGATIVE",
  "quantity": 3.000,
  "reason": "Stock count shortage"
}
```

`adjustmentType` is `POSITIVE`, `NEGATIVE`, or `DAMAGE` (`quantity` is always entered as a
positive magnitude regardless of type — the service derives the signed effect at approval time).
`productId` must reference an `INVENTORY`-type product (`409
STOCK_ADJUSTMENT_PRODUCT_NOT_INVENTORY` for a `SERVICE` product). Creating an adjustment **never**
touches `stock_movements` — it only ever reaches `PENDING`; segregation of duties between
`inventory.adjustment.create` and `inventory.adjustment.approve` means the same user cannot
self-approve unless they hold both permissions.

**Response:** `201 Created`, `Location: /api/v1/stock-adjustments/{stockAdjustmentId}`

```json
{
  "stockAdjustmentId": "f6a7b8c9-3333-4d55-9e66-1f7a8b9c0d1e",
  "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a",
  "sku": "CAB-001",
  "productName": "USB-C Cable",
  "adjustmentType": "NEGATIVE",
  "quantity": 3.000,
  "reason": "Stock count shortage",
  "status": "PENDING",
  "createdBy": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "createdAt": "2026-08-17T09:10:00Z",
  "decidedBy": null,
  "decidedAt": null,
  "decisionReason": null,
  "reversesAdjustmentId": null,
  "version": 0
}
```

**Errors:** `400 VALIDATION_FAILED` (missing `productId`/`adjustmentType`/`reason`, or
`quantity <= 0`); `404 PRODUCT_NOT_FOUND`; `400 STOCK_ADJUSTMENT_PRODUCT_NOT_INVENTORY`.

---

## 6. Search / Get Stock Adjustments

```
GET /api/v1/stock-adjustments
GET /api/v1/stock-adjustments/{adjustmentId}
```

Permission: `inventory.read`

**Query parameters (search)**

| Param | Type | Notes |
|---|---|---|
| `productId` | UUID | exact match |
| `status` | string | `PENDING`, `APPROVED`, `REJECTED` |
| `page` / `size` | int | default `0` / `20`, `size` capped at `100` |

Response shapes as §5 (paginated for search).

---

## 7. Approve / Reject Stock Adjustment

```
POST /api/v1/stock-adjustments/{adjustmentId}/approve
POST /api/v1/stock-adjustments/{adjustmentId}/reject
```

Permission: `inventory.adjustment.approve` (for both). Both require an
`Idempotency-Key` header (`ApiHeaders.IDEMPOTENCY_KEY`) — repeating the same key after a lost
response replays the original decision rather than deciding twice; the response carries an
`Idempotency-Replayed: true|false` header.

**Request body**

```json
{
  "decisionReason": "Confirmed by physical recount",
  "version": 0
}
```

**On approve:** the product is locked (same per-product locking every stock-posting path uses),
and for a `NEGATIVE`/`DAMAGE` adjustment, available stock must cover the quantity
(`409 STOCK_INSUFFICIENT` if not — this is the one adjustment path that can actually be rejected
for insufficient stock, since `POSITIVE` is purely additive). On success, one `ADJUSTMENT` stock
movement is posted with the type's signed quantity (`POSITIVE` → `+quantity`,
`NEGATIVE`/`DAMAGE` → `-quantity`).

**On reject:** no stock movement is posted; the adjustment moves straight to `REJECTED`.

**Response body:** same shape as §5, `status` now `APPROVED`/`REJECTED`, `decidedBy`/`decidedAt`/
`decisionReason` populated.

**Errors:** `404 STOCK_ADJUSTMENT_NOT_FOUND`; `409 CONCURRENT_MODIFICATION` (stale `version`);
`409 STOCK_ADJUSTMENT_ALREADY_DECIDED` (already `APPROVED`/`REJECTED` — a concurrent second
decide blocks on the row lock, then sees this once it proceeds); `409 STOCK_INSUFFICIENT`
(approve only, `NEGATIVE`/`DAMAGE` only).
