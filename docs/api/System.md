# System API

**Controllers:** `HealthController`, `BusinessProfileController`, `SystemConfigController`, `TaxConfigurationController`
**Package:** `com.bizco.server.system`
**Conventions:** see [README.md](README.md).

---

## 1. Health (`HealthController`)

Base paths: `/api/health` and `/api/v1/health`.

```
GET /api/v1/health
```

Public — no `Authorization` header or permission required. Performs a live DB connection check (2-second validation timeout).

**Response body** `200 OK` (both up and down cases return `200`, not a non-2xx status — check the `status`/`databaseStatus` fields, don't rely on HTTP status alone)

```json
{
  "status": "UP",
  "checkedAt": "2026-08-16T09:00:00Z",
  "databaseStatus": "UP",
  "message": "Server and database are ready."
}
```

Database failure:

```json
{
  "status": "DOWN",
  "checkedAt": "2026-08-16T09:00:00Z",
  "databaseStatus": "DOWN",
  "message": "Database connection failed: Connection refused"
}
```

> The `message` field can leak the raw JDBC exception text on failure — treat `/health` as an operational endpoint, not one to expose unauthenticated on a public network segment, despite it needing no auth today.

There is no separate `GET /api/v1/system/info` endpoint yet (`ApiContracts.md` §47.2 is design-only).

---

## 2. Business Profile (`BusinessProfileController`)

Base paths: `/api/settings/business`, `/api/v1/settings/business`, `/api/v1/system/business-profile` (same controller). Single-row configuration — there is exactly one business profile per deployment.

### 2.1 Get Profile

```
GET /api/v1/system/business-profile
```

Permission: `system.config.read`

**Response body** `200 OK`

```json
{
  "id": 1,
  "businessName": "Bizco Traders",
  "legalName": "Bizco Traders (Pvt) Ltd",
  "vatRegistrationNumber": "134567890-7000",
  "phone": "0112345678",
  "email": "info@bizcotraders.lk",
  "addressLine1": "12 Main Street",
  "addressLine2": null,
  "city": "Colombo",
  "countryCode": "LK",
  "currencyCode": "LKR",
  "timezone": "Asia/Colombo",
  "version": 3
}
```

**Response if not yet configured:** `404 Not Found`, empty body (no `ApiError` envelope — this is a bare `ResponseEntity.notFound()`).

### 2.2 Save Profile

```
PUT /api/v1/system/business-profile
```

Permission: `system.config`

**Request body**

```json
{
  "businessName": "Bizco Traders",
  "legalName": "Bizco Traders (Pvt) Ltd",
  "vatRegistrationNumber": "134567890-7000",
  "phone": "0112345678",
  "email": "info@bizcotraders.lk",
  "addressLine1": "12 Main Street",
  "addressLine2": null,
  "city": "Colombo",
  "countryCode": "LK",
  "currencyCode": "LKR",
  "timezone": "Asia/Colombo",
  "version": 0
}
```

`businessName` is required (a blank value throws a generic `IllegalArgumentException`, which the global handler maps to `500 INTERNAL_ERROR` today — not a `400`; treat this as a known gap when integrating). On first save (no row yet), send `version: 0`.

**Response body:** same shape as §2.1.

**Errors:** `409 CONCURRENT_MODIFICATION` on stale `version`.

Logo upload (`ApiContracts.md` §39, `multipart/form-data`) is not implemented.

---

## 3. Tax Configuration (`TaxConfigurationController`)

Base paths: `/api/settings/tax`, `/api/v1/settings/tax`, `/api/v1/system/tax` (same controller). Single-row configuration (fixed id `1`).

### 3.1 Get Configuration

```
GET /api/v1/system/tax
```

Permission: `system.config.read`

**Response body** `200 OK`

```json
{
  "id": 1,
  "vatEnabled": true,
  "vatRate": 18.0000,
  "version": 2
}
```

If no row exists yet, the server returns an in-memory default (`vatEnabled: false`, `vatRate: 18.0000`) rather than 404 — this default is **not persisted** until the first `PUT`.

### 3.2 Update Configuration

```
PUT /api/v1/system/tax
```

Permission: `system.config`

**Request body**

```json
{
  "vatEnabled": true,
  "vatRate": 18.0000,
  "version": 0
}
```

**Response body:** same shape as §3.1. Changing tax configuration does not recalculate historical invoices.

**Errors:** `409 CONCURRENT_MODIFICATION` on stale `version`.

---

## 4. General System Configuration (`SystemConfigController`)

Base path: `/api/v1/system/config`. Generic key/value store for arbitrary JSON configuration values — unlike `ApiContracts.md` §40, there is currently **no key whitelist**; any `configKey` string can be created via `PUT`.

### 4.1 List All Entries

```
GET /api/v1/system/config
```

Permission: `system.config.read`

**Response body** `200 OK`

```json
{
  "data": [
    {
      "configKey": "pos.receiptFooterText",
      "configValue": "Thank you for shopping with us!",
      "description": "Text printed at the bottom of POS receipts.",
      "updatedAt": "2026-08-10T12:00:00Z",
      "version": 1
    },
    {
      "configKey": "appointment.defaultBufferMinutes",
      "configValue": 15,
      "description": "Minutes reserved between appointments.",
      "updatedAt": "2026-08-10T12:00:00Z",
      "version": 0
    }
  ]
}
```

`configValue` can be any JSON-serializable value (string, number, boolean, object, array) — it is stored as a JSON string internally and decoded back on read.

### 4.2 Get One Entry

```
GET /api/v1/system/config/{configKey}
```

Permission: `system.config.read`

**Response body:** single object as in §4.1. **Errors:** `404 RESOURCE_NOT_FOUND` if `configKey` doesn't exist.

### 4.3 Upsert Entry

```
PUT /api/v1/system/config/{configKey}
```

Permission: `system.config`

**Request body**

```json
{
  "configValue": 20,
  "description": "Minutes reserved between appointments.",
  "version": 0
}
```

Creates the entry if `configKey` doesn't exist yet (in which case send `version: 0`), or updates it otherwise.

**Response body:** same shape as §4.1 entry, with `version` incremented and `updatedAt` refreshed.

**Errors:** `409 CONCURRENT_MODIFICATION` on stale `version`; `400 VALIDATION_FAILED` if `configValue` cannot be serialized to JSON.
