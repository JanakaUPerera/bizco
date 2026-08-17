# Customer API

**Controller:** `CustomerController`
**Package:** `com.bizco.server.customer`
**Conventions:** see [README.md](README.md).

---

## 1. Search Customers

```
GET /api/v1/customers
```

Permission: `customer.read`

**Query parameters**

| Param | Type | Notes |
|---|---|---|
| `q` | string | matches name/phone/customer code |
| `category` | string | `RETAIL`, `WHOLESALE`, etc. — see §5 |
| `status` | string | `ACTIVE`, `BLOCKED`, etc. — see §5 |
| `page` | int | default `0` |
| `size` | int | default `20`, capped at `100` |

**Response body** `200 OK`

```json
{
  "data": [
    {
      "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "customerCode": "CUST-00042",
      "name": "ABC Traders",
      "phone": "0771234567",
      "category": "WHOLESALE",
      "status": "ACTIVE",
      "creditLimit": 250000.00,
      "anonymized": false,
      "version": 2
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

The search-result summary never includes email/address/NIC/BR fields — only the detail endpoint (§3) exposes those, and only when authorized.

---

## 2. Create Customer

```
POST /api/v1/customers
```

Permission: `customer.create`

**Request body**

```json
{
  "name": "ABC Traders",
  "phone": "0771234567",
  "email": "info@abc.lk",
  "addressLine1": "12 Main Street",
  "addressLine2": null,
  "city": "Colombo",
  "nicNumber": null,
  "brNumber": "PV12345",
  "category": "WHOLESALE",
  "creditLimit": 250000.00,
  "consentMarketing": false,
  "consentDataSharing": false
}
```

| Field | Validation |
|---|---|
| `name` | required |
| `phone` | required; must match `^(?:0\d{9}|\+94\d{9})$` (local `0XXXXXXXXX` or `+94XXXXXXXXX`) |
| `email` | optional; validated as a basic email pattern if present |
| `category` | optional, defaults to `RETAIL` if blank; must be a valid `CustomerCategory` |
| `creditLimit` | optional; rejected if negative; defaults to `0` if omitted |
| `nicNumber`, `brNumber` | optional, stored encrypted at rest |

The server generates `customerCode` — do not send one.

**Response:** `201 Created`, `Location: /api/v1/customers/{customerId}`

```json
{
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "customerCode": "CUST-00042",
  "name": "ABC Traders",
  "phone": "0771234567",
  "email": "info@abc.lk",
  "addressLine1": "12 Main Street",
  "addressLine2": null,
  "city": "Colombo",
  "nicNumber": null,
  "brNumber": "PV12345",
  "piiRevealed": true,
  "category": "WHOLESALE",
  "status": "ACTIVE",
  "creditLimit": 250000.00,
  "consentMarketing": false,
  "consentDataSharing": false,
  "consentDate": null,
  "anonymized": false,
  "anonymizedAt": null,
  "createdAt": "2026-08-16T09:00:00Z",
  "updatedAt": "2026-08-16T09:00:00Z",
  "version": 0
}
```

`piiRevealed: true` here reflects that the creating caller — who just submitted the PII — sees it echoed back; this does not require `customer.view_pii` and is not audited as a "PII reveal" event (only the explicit GET in §3 is).

**Errors:** `400 VALIDATION_FAILED` (field errors for `name`, `phone`, `email`, `category`, `creditLimit`); `409 CUSTOMER_CODE_DUPLICATE` (rare — generated-code collision).

---

## 3. Get Customer

```
GET /api/v1/customers/{customerId}
```

Permission: `customer.read`

**Response body:** same shape as §2's response.

- If the caller also holds `customer.view_pii`, `nicNumber`/`brNumber` are decrypted and returned in full, `piiRevealed` is `true`, and a `CUSTOMER_PII_REVEALED` audit event is recorded.
- Otherwise `nicNumber`/`brNumber` are returned masked as `"******"` (or `null` if never set) and `piiRevealed` is `false`.

**Errors:** `404 CUSTOMER_NOT_FOUND`.

---

## 4. Update Customer

```
PUT /api/v1/customers/{customerId}
```

Permission: `customer.update`

**Request body**

```json
{
  "name": "ABC Traders (Pvt) Ltd",
  "phone": "0771234567",
  "email": "info@abc.lk",
  "addressLine1": "12 Main Street",
  "addressLine2": "Unit 4",
  "city": "Colombo",
  "nicNumber": null,
  "brNumber": "PV12345",
  "category": "WHOLESALE",
  "creditLimit": 300000.00,
  "consentMarketing": true,
  "consentDataSharing": false,
  "version": 0
}
```

Same field validation as create, except `phone` also accepts the anonymization sentinel `"0000000000"` (used after `POST /anonymize`, see §7).

**Response body:** same shape as §2 (PII revealed to the caller without requiring `customer.view_pii`, same as create).

**Errors:** `400 VALIDATION_FAILED`; `404 CUSTOMER_NOT_FOUND`; `409 CONCURRENT_MODIFICATION` on stale `version`.

---

## 5. Block / Activate Customer

```
POST /api/v1/customers/{customerId}/block
POST /api/v1/customers/{customerId}/activate
```

Permission: `customer.update` (both). No request body.

**Response body:** same shape as §2.

`category` values (`CustomerCategory` enum): `RETAIL`, `WHOLESALE`, `CORPORATE`. `status` values (`CustomerStatus` enum): `ACTIVE`, `BLOCKED`.

---

## 6. Credit Summary

```
GET /api/v1/customers/{customerId}/credit-summary
```

Permission: `customer.credit.read`

**Response body** `200 OK`

```json
{
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
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
```

`eligibility` is computed by `CustomerCreditPolicy` from the customer's credit limit and the receivable snapshot (sourced from the sales module's `CustomerCreditQueryPort` — a stub/port until the sales module is implemented, see [README.md](README.md) §2). `outstandingReceivable`/aging figures will read as zero until that integration exists.

---

## 7. Anonymize Customer

```
POST /api/v1/customers/{customerId}/anonymize
```

Permission: `customer.anonymize` (high privilege — PDPA/right-to-erasure workflow). No request body.

**Response body:** same shape as §2, with `anonymized: true`, `anonymizedAt` set, and PII fields cleared going forward.

**Errors:** `422 CUSTOMER_ANONYMIZATION_BLOCKED` if dependent records (e.g. open invoices/receivables — via `CustomerAnonymizationEligibilityPort`) currently block anonymization; `404 CUSTOMER_NOT_FOUND`.

There is no `GET /api/v1/customers/{customerId}/invoices` endpoint yet — that depends on the not-yet-implemented sales module (see [README.md](README.md) §2).
