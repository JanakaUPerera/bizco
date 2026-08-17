# Purchasing API

**Controller:** `SupplierController`
**Package:** `com.bizco.server.purchasing`
**Conventions:** see [README.md](README.md).

Only the supplier master is implemented today. GRN, supplier returns, and supplier payments (`ApiContracts.md` §24–26) are design-only — see [README.md](README.md) §2.

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

There is no `GET /api/v1/suppliers/{supplierId}/statement` endpoint yet (`ApiContracts.md` §23.1 is design-only, pending GRN/payment implementation).
