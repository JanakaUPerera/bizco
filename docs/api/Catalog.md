# Catalog API

**Controller:** `CatalogController`
**Package:** `com.bizco.server.catalog`
**Conventions:** see [README.md](README.md).

Covers product categories, units of measure, products, barcode images, and services.

---

## 1. Product Categories

### 1.1 List Categories

```
GET /api/v1/product-categories
```

Permission: `product.category.read`

**Response body** `200 OK`

```json
[
  {
    "categoryId": 12,
    "name": "Mobile Accessories",
    "parentId": null,
    "parentName": null,
    "description": "Cables, chargers, cases",
    "active": true,
    "createdAt": "2026-01-10T09:00:00Z",
    "updatedAt": "2026-01-10T09:00:00Z",
    "version": 0
  }
]
```

### 1.2 Create Category

```
POST /api/v1/product-categories
```

Permission: `product.category.create`

**Request body**

```json
{
  "name": "Cables",
  "parentId": 12,
  "description": "USB, HDMI, and charging cables"
}
```

**Response:** `201 Created`, `Location: /api/v1/product-categories/{categoryId}`, body shape as §1.1 entry.

**Errors:** `CATEGORY_NOT_FOUND` if `parentId` doesn't resolve; `CATEGORY_CYCLE` if the parent chain would form a loop.

### 1.3 Update Category

```
PUT /api/v1/product-categories/{categoryId}
```

Permission: `product.category.update`

**Request body**

```json
{
  "name": "Cables & Adapters",
  "parentId": 12,
  "description": "USB, HDMI, charging cables and adapters",
  "active": true,
  "version": 0
}
```

**Response body:** same shape as §1.1 entry.

**Errors:** `409 CONCURRENT_MODIFICATION` on stale `version`; `CATEGORY_CYCLE` if `parentId` would make the category its own ancestor. There is no delete endpoint — `active` is toggled via this update call instead. (`CATEGORY_IN_USE` is a defined error code in `ApiErrorCode` but is not currently raised anywhere in the service — deactivating a category with products still assigned to it is not blocked today.)

---

## 2. Units of Measure

```
GET /api/v1/uom
```

Permission: `product.read`

**Response body** `200 OK`

```json
[
  { "uomId": 1, "code": "PCS", "name": "Pieces", "category": "COUNT", "active": true },
  { "uomId": 2, "code": "KG", "name": "Kilogram", "category": "WEIGHT", "active": true }
]
```

Read-only lookup — no create/update endpoint is exposed yet.

---

## 3. Products

### 3.1 Search Products

```
GET /api/v1/products
```

Permission: `product.read`

**Query parameters**

| Param | Type | Notes |
|---|---|---|
| `q` | string | matches name/SKU/barcode |
| `categoryId` | long | filter by category |
| `type` | string | e.g. `INVENTORY`, `SERVICE` |
| `active` | boolean | filter active/inactive |
| `page` | int | default `0` |
| `size` | int | default `20` |

**Response body** `200 OK`

```json
{
  "data": [
    {
      "productId": "b2f2e6b0-1111-4a11-9a2b-0f1e2d3c4b5a",
      "sku": "CAB-001",
      "barcode": "4791234567890",
      "name": "USB-C Cable",
      "categoryName": "Cables",
      "uomCode": "PCS",
      "productType": "INVENTORY",
      "taxCategory": "STANDARD",
      "sellingPrice": 1200.00,
      "wholesalePrice": 1050.00,
      "costPrice": null,
      "reorderPoint": 5.000,
      "physicalStock": 12.000,
      "reservedStock": 2.000,
      "availableStock": 10.000,
      "active": true,
      "version": 3
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

`costPrice` is included only when the caller holds `product.view_cost`; otherwise it is returned as `null`.

### 3.2 Barcode Lookup

```
GET /api/v1/products/barcode/{barcode}
```

Permission: `product.read`

**Response body:** single object as in §3.1 (same cost-masking rule). `PRODUCT_NOT_FOUND` if no match.

### 3.3 Create Product

```
POST /api/v1/products
```

Permission: `product.create`

**Request body**

```json
{
  "sku": "CAB-001",
  "barcode": "4791234567890",
  "name": "USB-C Cable",
  "description": "1m braided cable",
  "categoryId": 12,
  "uomId": 1,
  "productType": "INVENTORY",
  "taxCategory": "STANDARD",
  "costPrice": 800.00,
  "sellingPrice": 1200.00,
  "wholesalePrice": 1050.00,
  "reorderPoint": 5.000,
  "imagePath": null
}
```

**Response:** `201 Created`, `Location: /api/v1/products/{productId}`

```json
{
  "productId": "b2f2e6b0-1111-4a11-9a2b-0f1e2d3c4b5a",
  "sku": "CAB-001",
  "barcode": "4791234567890",
  "name": "USB-C Cable",
  "description": "1m braided cable",
  "categoryId": 12,
  "categoryName": "Cables",
  "uomId": 1,
  "uomCode": "PCS",
  "productType": "INVENTORY",
  "taxCategory": "STANDARD",
  "costPrice": 800.00,
  "sellingPrice": 1200.00,
  "wholesalePrice": 1050.00,
  "reorderPoint": 5.000,
  "active": true,
  "imagePath": null,
  "createdAt": "2026-08-16T09:00:00Z",
  "updatedAt": "2026-08-16T09:00:00Z",
  "version": 0
}
```

**Errors:** `PRODUCT_SKU_DUPLICATE`, `PRODUCT_BARCODE_DUPLICATE`, `CATEGORY_NOT_FOUND`, `UOM_NOT_FOUND`.

> **Cost masking note:** the `product.view_cost` masking rule (§3.1) applies only to the *read* endpoints — search, get-by-id, and barcode lookup. The create/update/activate/deactivate responses below always echo the real `costPrice` back to the caller who just performed the write, regardless of whether that caller holds `product.view_cost`.

### 3.4 Get Product

```
GET /api/v1/products/{productId}
```

Permission: `product.read`. Response shape as §3.3, `costPrice` masked (`null`) unless caller has `product.view_cost`.

### 3.5 Update Product

```
PUT /api/v1/products/{productId}
```

Permission: `product.update`

**Request body**

```json
{
  "sku": "CAB-001",
  "barcode": "4791234567890",
  "name": "USB-C Cable (1.5m)",
  "description": "1.5m braided cable",
  "categoryId": 12,
  "uomId": 1,
  "productType": "INVENTORY",
  "taxCategory": "STANDARD",
  "costPrice": 850.00,
  "sellingPrice": 1250.00,
  "wholesalePrice": 1100.00,
  "reorderPoint": 5.000,
  "active": true,
  "imagePath": null,
  "version": 0
}
```

**Response body:** same shape as §3.3. **Errors:** `409 CONCURRENT_MODIFICATION` on stale `version`; duplicate/not-found codes as in §3.3.

### 3.6 Activate / Deactivate Product

```
POST /api/v1/products/{productId}/activate
POST /api/v1/products/{productId}/deactivate
```

Permissions: `product.update` (activate), `product.delete` (deactivate). No request body. Response shape as §3.3.

### 3.7 Resolve Price

```
GET /api/v1/products/{productId}/price?tier=RETAIL
```

Permission: `product.read`

**Query parameters:** `tier` — `RETAIL` (default) or `WHOLESALE`.

**Response body**

```json
{
  "productId": "b2f2e6b0-1111-4a11-9a2b-0f1e2d3c4b5a",
  "tier": "RETAIL",
  "unitPrice": 1200.00
}
```

### 3.8 Barcode Image (Code128 PNG)

```
GET /api/v1/barcodes/code128/{value}?width=320&height=100
```

Permission: `product.read`

**Response:** `image/png` binary body (not JSON) — a rendered Code128 barcode for `value`, sized per the `width`/`height` query params (defaults `320`×`100`).

---

## 4. Services

### 4.1 Search Services

```
GET /api/v1/services
```

Permission: `service.read`

**Query parameters:** `q`, `active`, `page` (default `0`), `size` (default `20`).

**Response body** `200 OK`

```json
{
  "data": [
    {
      "serviceId": "e5f6a7b8-2222-4c33-9d44-0e5f6a7b8c9d",
      "serviceCode": "SVC-001",
      "name": "Screen Replacement",
      "description": "Replace damaged screen",
      "category": "Mobile Repair",
      "basePrice": 5000.00,
      "estimatedDurationMinutes": 60,
      "requiresEstimate": true,
      "warrantyDays": 30,
      "active": true,
      "createdAt": "2026-02-01T09:00:00Z",
      "updatedAt": "2026-02-01T09:00:00Z",
      "version": 0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 4.2 Create Service

```
POST /api/v1/services
```

Permission: `service.create`

**Request body**

```json
{
  "serviceCode": "SVC-001",
  "name": "Screen Replacement",
  "description": "Replace damaged screen",
  "category": "Mobile Repair",
  "basePrice": 5000.00,
  "estimatedDurationMinutes": 60,
  "requiresEstimate": true,
  "warrantyDays": 30
}
```

**Response:** `201 Created`, `Location: /api/v1/services/{serviceId}`, body shape as §4.1 entry.

**Errors:** `SERVICE_CODE_DUPLICATE`.

### 4.3 Get Service

```
GET /api/v1/services/{serviceId}
```

Permission: `service.read`. `SERVICE_NOT_FOUND` if missing.

### 4.4 Update Service

```
PUT /api/v1/services/{serviceId}
```

Permission: `service.update`

**Request body**

```json
{
  "serviceCode": "SVC-001",
  "name": "Screen Replacement (OLED)",
  "description": "Replace damaged OLED screen",
  "category": "Mobile Repair",
  "basePrice": 6500.00,
  "estimatedDurationMinutes": 75,
  "requiresEstimate": true,
  "warrantyDays": 30,
  "active": true,
  "version": 0
}
```

**Response body:** same shape as §4.1 entry. **Errors:** `409 CONCURRENT_MODIFICATION` on stale `version`; `SERVICE_CODE_DUPLICATE`.

### 4.5 Activate / Deactivate Service

```
POST /api/v1/services/{serviceId}/activate
POST /api/v1/services/{serviceId}/deactivate
```

Permission: `service.update`. No request body. Response shape as §4.1 entry.
