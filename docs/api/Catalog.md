# Catalog API

**Controllers:** `CatalogController`, `VariantController`
**Package:** `com.bizco.server.catalog`
**Conventions:** see [README.md](README.md).

Covers product categories, units of measure, products, barcode images, services, brands,
attributes, and product variants.

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

### 1.4 Category Attributes

**Added in Phase 6 Week 16** (`DevelopmentPlan.md` Week 16, `DatabaseDesign.md` §55.2) — drives the category-driven dynamic attribute form (`MVP.md` §4.7).

```
GET /api/v1/product-categories/{categoryId}/attributes
```

Permission: `product.attribute.read`

**Response body** `200 OK`

```json
[
  { "categoryAttributeId": 1, "categoryId": 12, "attributeId": 3, "attributeName": "RAM", "dataType": "TEXT", "required": true }
]
```

```
POST /api/v1/product-categories/{categoryId}/attributes
```

Permission: `product.attribute.update`

**Request body**

```json
{ "attributeId": 3, "required": true }
```

**Response:** `201 Created`, `Location: /api/v1/product-categories/{categoryId}/attributes/{attributeId}`, body shape as above.

**Errors:** `CATEGORY_NOT_FOUND`, `ATTRIBUTE_NOT_FOUND`, `CATEGORY_ATTRIBUTE_DUPLICATE` if already assigned.

```
DELETE /api/v1/product-categories/{categoryId}/attributes/{attributeId}
```

Permission: `product.attribute.update`. **Response:** `204 No Content`. **Errors:** `CATEGORY_ATTRIBUTE_NOT_FOUND` if not assigned.

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
| `brandId` | long | filter to one brand (Phase 6 Week 19) |
| `attributeValueId` | long | filter to products having ≥1 variant with this ENUM attribute value (Phase 6 Week 19) |
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
      "brandName": null,
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

`costPrice` is included only when the caller holds `product.view_cost`; otherwise it is returned as `null`. `brandName`/`brandId` are `null` for a product with no brand assigned (`MVP.md` §4.8 — brand is optional).

### 3.2 Barcode Lookup

```
GET /api/v1/products/barcode/{barcode}
```

Permission: `product.read`

**Response body** `200 OK`

```json
{
  "product": {
    "productId": "b2f2e6b0-1111-4a11-9a2b-0f1e2d3c4b5a",
    "sku": "CAB-001",
    "..."
  },
  "resolvedVariantId": "c3f3e7c1-2222-4a22-9a3b-1f2e3d4c5b6a",
  "variantSpecific": true
}
```

`product` is the same shape as one row of §3.1 (same cost-masking rule). Phase 6 Week 19: the
scanned barcode is checked against a variant's own barcode first — the more specific match; if
found, `resolvedVariantId` names that exact variant and `variantSpecific` is `true`. If no variant
carries that barcode, it falls back to the product-level barcode (as in earlier weeks):
`resolvedVariantId` then names the product's default variant and `variantSpecific` is `false`, so
the caller (POS) knows whether to add the resolved variant directly or still check whether the
product has other variants worth prompting for. `PRODUCT_NOT_FOUND` if neither matches.

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
  "brandId": null,
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
  "brandId": null,
  "brandName": null,
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

**Errors:** `PRODUCT_SKU_DUPLICATE`, `PRODUCT_BARCODE_DUPLICATE`, `CATEGORY_NOT_FOUND`, `UOM_NOT_FOUND`, `BRAND_NOT_FOUND` if `brandId` doesn't resolve (`brandId` itself is optional — omit or send `null` for an unbranded product).

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
  "brandId": null,
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

---

## 5. Brands

**Added in Phase 6 Week 16** (`DevelopmentPlan.md` Week 16, `DatabaseDesign.md` §55.1). A product's brand is optional (`MVP.md` §4.8) — see §3 for how `brandId` attaches to a product.

### 5.1 List Brands

```
GET /api/v1/brands
```

Permission: `product.brand.read`

**Response body** `200 OK`

```json
[
  {
    "brandId": 4,
    "name": "Acme",
    "description": "House brand",
    "logoPath": null,
    "active": true,
    "createdAt": "2026-08-16T09:00:00Z",
    "updatedAt": "2026-08-16T09:00:00Z",
    "version": 0
  }
]
```

### 5.2 Create Brand

```
POST /api/v1/brands
```

Permission: `product.brand.create`

**Request body**

```json
{ "name": "Acme", "description": "House brand", "logoPath": null }
```

**Response:** `201 Created`, `Location: /api/v1/brands/{brandId}`, body shape as §5.1 entry.

**Errors:** `BRAND_NAME_DUPLICATE`.

### 5.3 Update Brand

```
PUT /api/v1/brands/{brandId}
```

Permission: `product.brand.update`

**Request body**

```json
{ "name": "Acme", "description": "House brand", "logoPath": "/logos/acme.png", "active": true, "version": 0 }
```

**Response body:** same shape as §5.1 entry. **Errors:** `409 CONCURRENT_MODIFICATION` on stale `version`; `BRAND_NAME_DUPLICATE`. There is no delete endpoint — `active` is toggled via this update call instead.

---

## 6. Attributes

**Added in Phase 6 Week 16** (`DevelopmentPlan.md` Week 16, `DatabaseDesign.md` §55.2). Attributes are a fixed, admin-defined vocabulary (`MVP.md` §4.7) assigned to categories via §1.4; per-variant attribute *values* are a Phase 6 Week 17+ concern (`DatabaseDesign.md` §56) once `product_variants`/`variant_attribute_values` exist.

### 6.1 List Attributes

```
GET /api/v1/attributes
```

Permission: `product.attribute.read`

**Response body** `200 OK`

```json
[
  { "attributeId": 3, "name": "RAM", "dataType": "TEXT", "createdAt": "2026-08-16T09:00:00Z" }
]
```

### 6.2 Create Attribute

```
POST /api/v1/attributes
```

Permission: `product.attribute.create`

**Request body**

```json
{ "name": "Color", "dataType": "ENUM" }
```

`dataType` is one of `TEXT`, `NUMBER`, `BOOLEAN`, `ENUM`. **Response:** `201 Created`, `Location: /api/v1/attributes/{attributeId}`, body shape as §6.1 entry. **Errors:** `ATTRIBUTE_NAME_DUPLICATE`; `400 VALIDATION_FAILED` for an unrecognised `dataType`. There is no update endpoint — attributes are a fixed vocabulary once created.

### 6.3 List / Add Attribute Values

Only meaningful for an `ENUM`-type attribute (§55.2 — `TEXT`/`NUMBER`/`BOOLEAN` attributes are entered freely, with no value pick-list).

```
GET /api/v1/attributes/{attributeId}/values
```

Permission: `product.attribute.read`

**Response body** `200 OK`

```json
[
  { "attributeValueId": 10, "attributeId": 3, "value": "Red" }
]
```

```
POST /api/v1/attributes/{attributeId}/values
```

Permission: `product.attribute.create`

**Request body**

```json
{ "value": "Red" }
```

**Response:** `201 Created`, body shape as above. **Errors:** `400 VALIDATION_FAILED` if the attribute's `dataType` is not `ENUM`; `ATTRIBUTE_VALUE_DUPLICATE` if the value already exists for this attribute.

---

## 7. Product Variants

**Added in Phase 6 Week 17** (`DevelopmentPlan.md` Week 17, `DatabaseDesign.md` §56). Served by a
separate `VariantController` (`com.bizco.server.catalog.api.VariantController`), matching the
`VariantService` service-boundary split from `CatalogService`. Every product has exactly one
`defaultVariant: true` variant (`MVP.md` §4.6) — created automatically on `POST /api/v1/products`
and kept in sync with the product's own sku/barcode/pricing fields for as long as the product has
no other variant; once a second variant exists, product-level edits stop touching variant rows.

### 7.1 List Variants

```
GET /api/v1/products/{productId}/variants
```

Permission: `product.variant.read`

**Response body** `200 OK`

```json
[
  {
    "productVariantId": "c1d2e3f4-1111-4a11-9a2b-0f1e2d3c4b5a",
    "productId": "b2f2e6b0-1111-4a11-9a2b-0f1e2d3c4b5a",
    "sku": "TSH-001",
    "barcode": null,
    "variantLabel": "Red / M",
    "costPrice": 800.00,
    "sellingPrice": 1200.00,
    "wholesalePrice": null,
    "reorderPoint": 0.000,
    "active": true,
    "imagePath": null,
    "defaultVariant": false,
    "createdAt": "2026-08-20T09:00:00Z",
    "updatedAt": "2026-08-20T09:00:00Z",
    "version": 0
  }
]
```

### 7.2 Create Variant

```
POST /api/v1/products/{productId}/variants
```

Permission: `product.variant.create`

**Request body**

```json
{ "sku": "TSH-001-XL", "barcode": null, "variantLabel": "XL", "costPrice": 800.00,
  "sellingPrice": 1200.00, "wholesalePrice": null, "reorderPoint": 0, "imagePath": null }
```

**Response:** `201 Created`, `Location: /api/v1/products/{productId}/variants/{productVariantId}`,
body shape as §7.1. A manually created variant is never `defaultVariant`. **Errors:**
`PRODUCT_NOT_FOUND`, `VARIANT_SKU_DUPLICATE`, `VARIANT_BARCODE_DUPLICATE`, `400 VALIDATION_FAILED`.

### 7.3 Update Variant

```
PUT /api/v1/products/{productId}/variants/{variantId}
```

Permission: `product.variant.update`

**Request body:** same fields as §7.2 plus `active` and `version`. **Response body:** same shape
as §7.1. **Errors:** `VARIANT_NOT_FOUND`, `409 CONCURRENT_MODIFICATION` on stale `version`,
`VARIANT_SKU_DUPLICATE`, `VARIANT_BARCODE_DUPLICATE`.

### 7.4 Generate Variants (Attribute-Driven)

```
POST /api/v1/products/{productId}/variants/generate
```

Permission: `product.variant.create`

Cartesian generation restricted to `ENUM` attributes assigned to the product's category (§6.3) —
`TEXT`/`NUMBER`/`BOOLEAN` attributes have no discrete value list to generate from. Generated
variants inherit the product's current pricing as a starting point; refine each one afterward via
§7.3.

**Request body**

```json
{
  "selections": [
    { "attributeId": 3, "attributeValueIds": [10, 11] },
    { "attributeId": 4, "attributeValueIds": [20, 21] }
  ]
}
```

**Response:** `200 OK`, `List<VariantResponse>` (§7.1 shape) — the newly created variants only,
one per combination (e.g. 2 colors × 2 sizes = 4 variants), labeled by joining the selected values
with `" / "` (e.g. `"Red / S"`).

**Errors:** `PRODUCT_NOT_FOUND`; `VARIANT_GENERATE_EMPTY_SELECTION` if `selections` is empty or any
attribute has no selected values; `VARIANT_ATTRIBUTE_NOT_ASSIGNED` if an attribute isn't assigned
to the product's category; `VARIANT_ATTRIBUTE_NOT_ENUM` if a selected attribute isn't `ENUM`;
`VARIANT_LABEL_DUPLICATE` if a computed combination's label already exists as a variant on this
product (the whole request rolls back — no partial generation).
