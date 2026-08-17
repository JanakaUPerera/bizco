# Audit API

**Controller:** `AuditLogController`
**Package:** `com.bizco.server.audit`
**Conventions:** see [README.md](README.md).

Audit records are created internally by other services (e.g. `UserService`, `RoleService`) via `AuditService.record(...)`; there is no write endpoint. This module exposes read-only search over the resulting log.

---

## 1. Search Audit Logs

```
GET /api/v1/audit-logs
```

Permission: `audit.read`

**Query parameters**

| Param | Type | Required | Notes |
|---|---|---|---|
| `entityType` | string | no | e.g. `USER`, `ROLE` |
| `actionCode` | string | no | e.g. `USER_CREATED`, `ACCOUNT_LOCKED` |
| `actorUserId` | UUID | no | filters to a specific acting user |
| `from` | ISO-8601 instant | no | e.g. `2026-08-01T00:00:00Z` |
| `to` | ISO-8601 instant | no | e.g. `2026-08-16T23:59:59Z` |
| `page` | int | no | default `0` |
| `size` | int | no | default `20` |

**Response body** `200 OK`

```json
{
  "data": [
    {
      "id": 10234,
      "entityType": "USER",
      "entityId": "7dd3929a-697c-46e1-a76f-989c76a67a24",
      "actionCode": "USER_UPDATED",
      "actorType": "USER",
      "actorUserId": "9d6a5c2e-1234-4a11-9a2b-0f1e2d3c4b5a",
      "occurredAt": "2026-08-16T09:12:44Z",
      "details": {
        "username": "cashier"
      },
      "changedFields": {
        "displayName": "Kasun P.",
        "primaryRoleId": 3,
        "active": true
      },
      "ipAddress": "192.168.1.42",
      "clientId": "POS-PC-01",
      "correlationId": "9b1f2e3a-5678-49ab-8cde-f0123456789a"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

| Field | Notes |
|---|---|
| `details` | free-form context captured at write time (arbitrary JSON object) |
| `changedFields` | before/after or new-value snapshot for update-type actions; `null`/omitted keys mean not tracked for that action |
| `actorUserId` | `null` for system-initiated actions |

**Errors**

| Status | Code | Cause |
|---|---|---|
| 401 | `AUTH_SESSION_INVALID` | missing/invalid session |
| 403 | `AUTH_PERMISSION_DENIED` | caller lacks `audit.read` |

There is no single-record `GET /api/v1/audit-logs/{id}` endpoint implemented yet (design-only in `ApiContracts.md` §43.2) — retrieve a specific entry by filtering the search endpoint.
