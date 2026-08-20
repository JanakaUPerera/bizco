# Scheduling API

**Controllers:** `AppointmentController`, `JobCardController`, `StaffTechnicianController`
**Package:** `com.bizco.server.scheduling` (technician list lives in `identity.controller` — see §7)
**Conventions:** see [README.md](README.md).

Appointment scheduling with PostgreSQL-enforced conflict protection, calendar-facing search, the
full appointment → job card conversion, and the job card / estimate / parts / service-invoice
workflow are all implemented (`DevelopmentPlan.md` Weeks 9–11).

---

## 1. Create Appointment

```
POST /api/v1/appointments
```

Permission: `appointment.create`

**Request body**

```json
{
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "serviceId": "e5f6a7b8-2222-4c33-9d44-0e5f6a7b8c9d",
  "technicianId": "7c8d9e0f-4444-4a55-9b66-1c7d8e9f0a1b",
  "startAt": "2026-08-20T09:00:00Z",
  "notes": "Screen cracked, front camera not focusing",
  "walkIn": false
}
```

`endAt` is deliberately absent from the request — the server derives it from the service's
`estimatedDurationMinutes`. `technicianId` is optional (assign later); when supplied, the user
must have an active technician `StaffProfile` (`400 APPOINTMENT_TECHNICIAN_INELIGIBLE` otherwise —
being a technician is an operational flag independent of RBAC role, MVP.md's
"technician assignment is operational capability, not RBAC role" gate decision).

**Response:** `201 Created`, `Location: /api/v1/appointments/{appointmentId}`

```json
{
  "appointmentId": "8a1b2c3d-4e5f-4a1b-9c8d-7e6f5a4b3c2d",
  "appointmentNumber": "APT-20260820-0001",
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "customerName": "Nadeesha Kumara",
  "serviceId": "e5f6a7b8-2222-4c33-9d44-0e5f6a7b8c9d",
  "serviceName": "Phone Screen Repair",
  "technicianId": "7c8d9e0f-4444-4a55-9b66-1c7d8e9f0a1b",
  "technicianName": "Kasun Perera",
  "startAt": "2026-08-20T09:00:00Z",
  "endAt": "2026-08-20T09:45:00Z",
  "blockedUntilAt": "2026-08-20T10:00:00Z",
  "status": "SCHEDULED",
  "notes": "Screen cracked, front camera not focusing",
  "walkIn": false,
  "version": 0,
  "createdAt": "2026-08-19T14:00:00Z"
}
```

`endAt = startAt + service.estimatedDurationMinutes`; `blockedUntilAt = endAt +` a configurable
buffer (`bizco.scheduling.appointment-buffer-minutes`, default 15). `blockedUntilAt` — not `endAt`
— is what the overlap guard actually compares against, so back-to-back bookings always keep the
buffer.

**Conflict protection (two layers):** an advisory pre-check runs first for a fast, friendly
error, but the PostgreSQL `ex_appointments_technician_overlap` GiST exclusion constraint
(`DatabaseDesign.md` §39) is the transactionally authoritative guard — two concurrent requests for
the same technician's overlapping range cannot both commit even if both pass the pre-check.
Either path surfaces as the same error.

**Errors:** `400 VALIDATION_FAILED`/`DOMAIN_RULE_REJECTED` (missing `startAt`); `404
CUSTOMER_NOT_FOUND`; `404 SERVICE_NOT_FOUND` (also raised, as `400`, for an inactive service);
`400 APPOINTMENT_TECHNICIAN_INELIGIBLE`; `409 APPOINTMENT_CONFLICT`.

---

## 2. Search Appointments

```
GET /api/v1/appointments
```

Permission: `appointment.read`

**Query parameters**

| Param | Type | Notes |
|---|---|---|
| `from` / `to` | instant | inclusive range on `startAt` |
| `technicianId` | UUID | exact match |
| `customerId` | UUID | exact match |
| `status` | string | `SCHEDULED`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `NO_SHOW`, `CANCELLED` |

**Response body** `200 OK` — `{ "data": [ <AppointmentResponse, shape as §1> ] }`. Not paginated
(unlike most other search endpoints in this codebase) — the calendar UI's day/week/month views
pull a bounded `from`/`to` range at a time rather than paging through all appointments.

---

## 3. Availability

```
GET /api/v1/appointments/availability?serviceId=...&technicianId=...&date=...
```

Permission: `appointment.read`

Returns 30-minute slots across the business day (09:00–17:00, `Asia/Colombo`) for the given
service's duration, each flagged `available`. **This is a best-effort UI hint only** — computed
from currently booked slots at read time — not authoritative; the GiST exclusion constraint in §1
is the only thing that can actually reject a conflicting booking, so a slot shown available here
can still 409 if another booking commits first.

```json
{
  "slots": [
    { "startAt": "2026-08-20T03:30:00Z", "endAt": "2026-08-20T04:15:00Z", "available": true },
    { "startAt": "2026-08-20T04:00:00Z", "endAt": "2026-08-20T04:45:00Z", "available": false }
  ]
}
```

If `technicianId` is omitted every slot is reported `available` (no technician to check
overlap against).

---

## 4. Get Appointment / Reschedule / Status / Cancel

```
GET  /api/v1/appointments/{appointmentId}
PUT  /api/v1/appointments/{appointmentId}
POST /api/v1/appointments/{appointmentId}/status
POST /api/v1/appointments/{appointmentId}/cancel?reason=...
```

Permissions: `appointment.read` (get), `appointment.update` (reschedule/status), `appointment.cancel`
(cancel).

**Reschedule request** (`PUT`) — re-runs the same overlap protection as create:

```json
{
  "serviceId": null,
  "technicianId": "7c8d9e0f-4444-4a55-9b66-1c7d8e9f0a1b",
  "startAt": "2026-08-20T10:00:00Z",
  "notes": "Customer asked to move later",
  "version": 0
}
```

`serviceId`/`technicianId` are optional — omit to keep the appointment's current value; `startAt`
is required (`endAt`/`blockedUntilAt` are recalculated from whichever service applies).

**Status request** (`POST .../status`) — `targetStatus` is one of `CONFIRMED`, `IN_PROGRESS`,
`COMPLETED`, `NO_SHOW`, `CANCELLED` (mapped to the matching `StateMachines.md` §8 transition;
`reason` applies to `NO_SHOW`/`CANCELLED`):

```json
{ "targetStatus": "CONFIRMED", "reason": null, "version": 0 }
```

`POST .../cancel` is a convenience shortcut for the same `CANCELLED` transition, taking `reason`
as a query parameter instead and **not** version-checked (unlike the `/status` route).

**Errors (all four):** `404 APPOINTMENT_NOT_FOUND`; `409 CONCURRENT_MODIFICATION` (reschedule/status
only, stale `version`); `409 APPOINTMENT_INVALID_TRANSITION`; `409 APPOINTMENT_CONFLICT`
(reschedule only).

---

## 5. Convert Appointment to Job Card

```
POST /api/v1/appointments/{appointmentId}/convert-to-job
```

Permission: `appointment.convert_to_job`. Requires an `Idempotency-Key` header — a retried
convert after a lost response replays the same job card rather than creating a second one; the
response carries `Idempotency-Replayed: true|false` and status `200` on replay vs `201` on first
creation.

**Request body** — device intake captured at the point the appointment becomes a job:

```json
{
  "deviceType": "Phone",
  "brand": "Samsung",
  "model": "Galaxy A54",
  "serialNumber": "SN-88214477",
  "reportedIssue": "Cracked screen, camera won't focus",
  "accessoriesReceived": "Charger, case",
  "deviceCondition": "Minor scratches on back panel"
}
```

**Response:** `JobCardResponse` (see §8) linked back to this appointment via `appointmentId`.

**Errors:** `404 APPOINTMENT_NOT_FOUND`; `409 APPOINTMENT_ALREADY_CONVERTED`; `409
APPOINTMENT_NOT_CONVERTIBLE` (e.g. already `CANCELLED`/`NO_SHOW`).

---

## 6. Job Card — Create (Walk-in) / Search / Get / Update

```
POST /api/v1/job-cards
GET  /api/v1/job-cards
GET  /api/v1/job-cards/{jobCardId}
PUT  /api/v1/job-cards/{jobCardId}
```

Permissions: `jobcard.create` (create), `jobcard.read` (search/get), `jobcard.update` (update).

**Create request** — walk-in path, no appointment (§5 covers the appointment-linked path):

```json
{
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "technicianId": null,
  "deviceType": "Laptop",
  "brand": "Dell",
  "model": "Inspiron 15",
  "serialNumber": "SN-55221199",
  "reportedIssue": "Won't power on",
  "customerNotes": "Dropped yesterday",
  "accessoriesReceived": "Charger",
  "deviceCondition": "Dent on left corner"
}
```

**Search query parameters:** `q` (device/customer text match), `status`, `customerId`,
`technicianId`, `fromDate`/`toDate` (instant range on `createdAt`), `page`/`size` (default `0`/`25`).
Search returns `JobCardSearchResponse` — a flat, **unpaginated** `data` list of
`JobCardSummaryResponse` rows despite accepting `page`/`size` inputs (the paging parameters are
threaded through to the query but not reflected back in the response envelope the way other
search endpoints in this codebase do).

**Response (create/get):** `JobCardResponse` — see §8. **Update request** accepts the same intake
fields as create plus `technicianId` and `version`; all fields optional (`null` leaves the
existing value).

**Errors:** `404 JOB_CARD_NOT_FOUND`; `409 CONCURRENT_MODIFICATION` (update, stale `version`).

---

## 7. Job Card — Status, Services, Estimates

```
POST /api/v1/job-cards/{jobCardId}/status
POST /api/v1/job-cards/{jobCardId}/services
POST /api/v1/job-cards/{jobCardId}/services/{jobServiceId}/status
POST /api/v1/job-cards/{jobCardId}/estimates
POST /api/v1/job-cards/{jobCardId}/estimates/{estimateId}/accept
POST /api/v1/job-cards/{jobCardId}/estimates/{estimateId}/decline
```

Permissions: the controller allows `jobcard.status_change` **or** `jobcard.complete` for
`/status` — but transitioning specifically *to* `COMPLETED` additionally requires
`jobcard.complete` itself, checked inside the service, not just one of the two at the controller
level. `jobcard.update` (services), `jobcard.estimate.create` (create estimate),
`jobcard.estimate.approve` (accept/decline).

**Status request** — `targetStatus` follows `StateMachines.md` §10 / MVP.md §6.4.3's table
(`CREATED → ESTIMATE_PENDING → ESTIMATE_APPROVED → IN_PROGRESS → READY_FOR_PICKUP → COMPLETED`,
plus `CANCELLED` from any state):

```json
{
  "targetStatus": "IN_PROGRESS",
  "reason": null,
  "overrideEstimateRequirement": false,
  "version": 2
}
```

`overrideEstimateRequirement` backs the manager-override rule for starting work before a
mandatory estimate is accepted (`StateMachines.md` §10.4).

**Add service:**

```json
{
  "serviceId": "e5f6a7b8-2222-4c33-9d44-0e5f6a7b8c9d",
  "estimatedCost": 4500.00,
  "estimatedDurationMinutes": 60,
  "notes": "Includes diagnostic"
}
```

**Change service status:** `{ "targetStatus": "COMPLETED", "actualCost": 4200.00 }` — `targetStatus`
is `PENDING`, `IN_PROGRESS`, or `COMPLETED`.

**Create estimate:**

```json
{
  "estimatedTotal": 8500.00,
  "description": "Screen replacement + labor",
  "notes": "Customer to confirm by phone"
}
```

**Accept/decline estimate:** `{ "notes": "Customer approved over the phone" }` — moves
`customerResponse` to `ACCEPTED`/`DECLINED`.

**Response (all of the above):** the full `JobCardResponse` (§8), recalculated.

**Errors:** `404 JOB_CARD_NOT_FOUND`; `404 JOB_SERVICE_NOT_FOUND`; `404 JOB_ESTIMATE_NOT_FOUND`;
`409 JOB_CARD_INVALID_TRANSITION`; `409 JOB_ESTIMATE_REQUIRED`; `409 JOB_ESTIMATE_NOT_PENDING`
(accept/decline on an already-decided estimate); `409 CONCURRENT_MODIFICATION` (status, stale
`version`).

---

## 8. Job Card Response Shape

```json
{
  "jobCardId": "9b2c3d4e-5f6a-4b1c-8d9e-0f1a2b3c4d5e",
  "jobNumber": "JC-202608-0001",
  "appointmentId": null,
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "customerName": "Nadeesha Kumara",
  "technicianId": "7c8d9e0f-4444-4a55-9b66-1c7d8e9f0a1b",
  "technicianName": "Kasun Perera",
  "deviceType": "Phone",
  "brand": "Samsung",
  "model": "Galaxy A54",
  "serialNumber": "SN-88214477",
  "reportedIssue": "Cracked screen, camera won't focus",
  "customerNotes": null,
  "accessoriesReceived": "Charger, case",
  "deviceCondition": "Minor scratches on back panel",
  "status": "IN_PROGRESS",
  "estimatedCompletionDate": null,
  "actualCompletionDate": null,
  "pickupDate": null,
  "warrantyEndDate": null,
  "serviceInvoiceId": null,
  "version": 3,
  "createdAt": "2026-08-19T14:05:00Z",
  "services": [ { "jobServiceId": "...", "serviceId": "...", "serviceName": "Phone Screen Repair",
                  "estimatedCost": 4500.00, "actualCost": null, "estimatedDurationMinutes": 60,
                  "status": "IN_PROGRESS", "notes": null } ],
  "parts": [],
  "estimates": []
}
```

---

## 9. Add Job Part

```
POST /api/v1/job-cards/{jobCardId}/parts
```

Permission: `jobcard.parts.add`. Requires an `Idempotency-Key` header (same replay contract as
§5) — status `201` on first add, `200` on replay.

**Request body**

```json
{
  "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a",
  "quantity": 1.000,
  "customerUnitPrice": 3500.00,
  "warrantyCovered": false
}
```

`productId` must be an `INVENTORY`-type product (`400 JOB_PART_PRODUCT_NOT_INVENTORY` for a
`SERVICE` product). The product is locked and checked for available stock, then a `JOB_PART`
stock movement is posted immediately (MVP.md §6.4.4: "stock deducted through a posted `JOB_PART`
stock movement at time of parts usage" — not deferred to invoice posting). The part's line is
marked so the eventual service invoice (§10) does not deduct this stock a second time as `SALE`.

**Response:** `JobPartResponse`:

```json
{
  "jobPartId": "a1b2c3d4-5e6f-4a1b-9c8d-7e6f5a4b3c2e",
  "productId": "d3e4f5a6-7b8c-4d1e-9f0a-2b3c4d5e6f7a",
  "sku": "SCR-A54",
  "productName": "Galaxy A54 Screen Assembly",
  "quantityUsed": 1.000,
  "unitPriceSnapshot": 3500.00,
  "costPriceSnapshot": 2200.00,
  "warrantyCovered": false,
  "postedAt": "2026-08-19T15:00:00Z"
}
```

**Errors:** `404 JOB_CARD_NOT_FOUND`; `404 PRODUCT_NOT_FOUND`; `400
JOB_PART_PRODUCT_NOT_INVENTORY`; `409 STOCK_INSUFFICIENT`.

---

## 10. Generate Service Invoice

```
POST /api/v1/job-cards/{jobCardId}/invoice
```

Permission: `invoice.create` (not a `jobcard.*` permission — generating an invoice is treated as
an invoicing action).

**Request body**

```json
{
  "includeCompletedServices": true,
  "includeParts": true,
  "customLines": [
    { "description": "Emergency call-out fee", "quantity": 1, "unitPrice": 1500.00, "taxCategory": "STANDARD" }
  ]
}
```

Builds a `DRAFT` invoice (§5.3.3 of MVP.md) with one line per included completed `JobService`, one
line per `JobPart` (each flagged so posting the invoice skips its stock deduction — already
deducted at parts-add time, §9), plus any `customLines`. **Returns a `DRAFT` `InvoiceDetailResponse`**
(see [Sales.md](Sales.md) §2) — posting it, taking payment, and printing the receipt are the
regular Sales-module invoice flow from that point on, not part of this endpoint.

**Errors:** `404 JOB_CARD_NOT_FOUND`.

---

## 11. Technicians

```
GET /api/v1/staff/technicians?active=...&q=...
```

Permission: `appointment.read`. Lives in `identity.controller.StaffTechnicianController`, not the
`scheduling` package — technician eligibility is a `StaffProfile` flag on a `User`
(`ApiContracts.md` §28.1: "schedulable staff, independent of RBAC role"), so listing technicians
reads identity data, not scheduling data. `active` filters to technicians whose user account is
active; `q` matches display name.

```json
{ "data": [ { "userId": "7c8d9e0f-4444-4a55-9b66-1c7d8e9f0a1b", "fullName": "Kasun Perera", "active": true } ] }
```
