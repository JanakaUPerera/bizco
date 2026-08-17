# Identity API

**Controllers:** `AuthController`, `UserController`, `RoleController`, `LoginHistoryController`
**Package:** `com.bizco.server.identity`
**Conventions:** see [README.md](README.md) for auth header, correlation ID, error shape, and versioning rules.

---

## 1. Authentication (`AuthController`)

Base paths: `/api/auth` and `/api/v1/auth` (same controller, both registered).

### 1.1 Login

```
POST /api/v1/auth/login
```

Public endpoint — no `Authorization` header required.

**Request body**

```json
{
  "username": "cashier",
  "password": "secret",
  "clientId": "POS-PC-01"
}
```

| Field | Type | Notes |
|---|---|---|
| `username` | string | required |
| `password` | string | required |
| `clientId` | string | optional, identifies the calling POS/workstation |

**Response body** `200 OK`

```json
{
  "data": {
    "sessionToken": "b2f2e6b0-....-opaque-token",
    "expiresAt": "2026-08-16T18:00:00Z",
    "user": {
      "userId": "7dd3929a-697c-46e1-a76f-989c76a67a24",
      "username": "cashier",
      "displayName": "Kasun Perera",
      "primaryRole": "CASHIER",
      "effectivePermissions": ["invoice.create", "invoice.read"],
      "mustChangePassword": false
    }
  }
}
```

**Errors**

| Status | Code | Cause |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `username`/`password` missing or blank |
| 401 | `AUTH_INVALID_CREDENTIALS` | unknown username or wrong password |
| 401 | `AUTH_ACCOUNT_INACTIVE` | account deactivated |
| 401 | `AUTH_ACCOUNT_LOCKED` | account locked (failed-attempt lockout) |
| 429 | `AUTH_CONCURRENT_SESSION_LIMIT` | user already has the maximum number of active sessions |

Every attempt (success or failure) is recorded in login history.

### 1.2 Logout

```
POST /api/v1/auth/logout
```

Requires `Authorization: Bearer <token>`. Revokes the current session.

**Response:** `204 No Content`

### 1.3 Current Session

```
GET /api/v1/auth/me
```

**Response body** `200 OK`

```json
{
  "userId": "7dd3929a-697c-46e1-a76f-989c76a67a24",
  "username": "cashier",
  "displayName": "Kasun Perera",
  "primaryRole": "CASHIER",
  "expiresAt": "2026-08-16T18:00:00Z",
  "effectivePermissions": ["invoice.create", "invoice.read"],
  "mustChangePassword": false
}
```

Call this after login and whenever stale permissions are suspected (e.g. after a 403, or after a secondary role grant/revoke).

### 1.4 Change Own Password

```
POST /api/v1/auth/change-password
```

**Request body**

```json
{
  "currentPassword": "old-secret",
  "newPassword": "new-secret"
}
```

**Response:** `204 No Content`

**Errors:** `400 VALIDATION_FAILED` if the new password fails policy; identity-service exceptions for a wrong current password.

---

## 2. Users (`UserController`)

Base paths: `/api/identity/users` and `/api/v1/users`.

### 2.1 List Users

```
GET /api/v1/users
```

Permission: `user.read`

**Response body** `200 OK`

```json
[
  {
    "id": "7dd3929a-697c-46e1-a76f-989c76a67a24",
    "username": "cashier",
    "displayName": "Kasun Perera",
    "primaryRoleId": 3,
    "status": "ACTIVE",
    "active": true,
    "lastLoginAt": "2026-08-16T08:00:00Z",
    "version": 3
  }
]
```

> Note: this endpoint returns a plain array (no pagination) — there is currently no `q`/`role`/`page`/`size` filtering as described in `ApiContracts.md` §8.1.

### 2.2 Create User

```
POST /api/v1/users
```

Permission: `user.create`

**Request body**

```json
{
  "username": "manager02",
  "displayName": "Nimal Silva",
  "password": "Temp@1234",
  "primaryRoleId": 3
}
```

**Response body** `200 OK`

```json
{
  "id": "9d6a5c2e-1234-4a11-9a2b-0f1e2d3c4b5a",
  "username": "manager02",
  "displayName": "Nimal Silva",
  "primaryRoleId": 3,
  "status": "ACTIVE",
  "active": true,
  "lastLoginAt": null,
  "version": 0
}
```

**Errors:** identity-service exception (`IDENTITY_ERROR`, 400) if the username already exists or `primaryRoleId` doesn't resolve to a role.

### 2.3 Get User

```
GET /api/v1/users/{userId}
```

Permission: `user.read`

**Response body:** same shape as §2.2. `404`-equivalent (`RESOURCE_NOT_FOUND`, mapped as `IdentityException` with `HttpStatus.NOT_FOUND`) if `userId` doesn't exist.

### 2.4 Update User

```
PUT /api/v1/users/{userId}
```

Permission: `user.update`

**Request body**

```json
{
  "displayName": "Nimal Silva",
  "primaryRoleId": 3,
  "active": true,
  "version": 2
}
```

**Response body:** same shape as §2.2, `version` incremented.

**Errors:** `409 CONCURRENT_MODIFICATION` if `version` is stale.

### 2.5 Effective Permissions

```
GET /api/v1/users/{userId}/effective-permissions
```

Permission: `user.read`

**Response body**

```json
{
  "userId": "7dd3929a-697c-46e1-a76f-989c76a67a24",
  "permissions": ["invoice.create", "invoice.read", "customer.read"]
}
```

Union of the primary role's permissions and any active (non-expired, non-revoked) secondary role grants.

### 2.6 Force Logout / Revoke Sessions

```
POST /api/v1/users/{userId}/sessions/revoke
```

Permission: `user.session.revoke`

No request body. Revokes all active sessions for the target user (reason recorded internally as `FORCE_LOGOUT`). **Response:** `200 OK`, empty body.

### 2.7 Lock / Unlock

```
POST /api/v1/users/{userId}/lock
POST /api/v1/users/{userId}/unlock
```

Permissions: `user.lock`, `user.unlock`. No request body, no response body (`void`).

### 2.8 Reset Password

```
POST /api/v1/users/{userId}/reset-password
```

Permission: `user.reset_password`

No request body — the server generates a temporary password and forces a change on next login.

**Response body**

```json
{
  "userId": "7dd3929a-697c-46e1-a76f-989c76a67a24",
  "temporaryPassword": "Xk4!qT9pLr"
}
```

---

## 3. Roles & Permissions (`RoleController`)

Base paths: `/api/identity` and `/api/v1` (endpoints below are relative to those, e.g. `/api/v1/roles`).

### 3.1 List Roles

```
GET /api/v1/roles
```

Permission: `role.read`

**Response body**

```json
[
  {
    "id": 3,
    "code": "MANAGER",
    "name": "Manager",
    "permissions": {
      "invoice.create": true,
      "invoice.void": true,
      "customer.anonymize": false
    },
    "system": false,
    "active": true,
    "version": 1
  }
]
```

### 3.2 List Permission Registry

```
GET /api/v1/permissions
```

Permission: `role.read`

**Response body**

```json
[
  {
    "permissionCode": "invoice.create",
    "module": "invoice",
    "action": "create",
    "description": "Create and post sales invoices."
  }
]
```

### 3.3 Create Role

```
POST /api/v1/roles
```

Permission: `role.create`

**Request body**

```json
{
  "code": "SUPERVISOR",
  "name": "Supervisor",
  "permissions": {
    "invoice.create": true,
    "invoice.read": true
  },
  "active": true,
  "version": 0
}
```

**Response body:** same shape as §3.1 entry, with the assigned `id`.

**Errors:** `IDENTITY_ERROR` (400) if `code` already exists.

### 3.4 Update Role

```
PUT /api/v1/roles/{roleId}
```

Permission: `role.update`

**Request body:** same shape as §3.3, with current `version`.

**Errors:** `409 CONCURRENT_MODIFICATION` on stale `version`.

### 3.5 Delete Role

```
DELETE /api/v1/roles/{roleId}
```

Permission: `role.delete`

**Response:** `204 No Content`.

**Errors:** `IDENTITY_ERROR` (400) if the role is a protected system role (`system = true`).

### 3.6 Grant Secondary Role

```
POST /api/v1/users/{userId}/secondary-roles
POST /api/v1/users/{userId}/roles
```

(Both paths route to the same handler.) Permission: `user.grant_role`

**Request body**

```json
{
  "roleId": 3,
  "expiresAt": "2026-08-16T18:00:00Z"
}
```

**Response body**

```json
{
  "id": "c1a2b3d4-5678-49ab-8cde-f0123456789a",
  "roleId": 3,
  "roleCode": "MANAGER",
  "grantedAt": "2026-08-16T09:00:00Z",
  "expiresAt": "2026-08-16T18:00:00Z",
  "revokedAt": null
}
```

**Errors:** `IDENTITY_ERROR` (400) if `roleId` equals the user's primary role.

### 3.7 List Secondary Roles

```
GET /api/v1/users/{userId}/secondary-roles
GET /api/v1/users/{userId}/roles
```

Permission: `user.read`

**Response body:** array of the object shown in §3.6.

### 3.8 Revoke Secondary Role

```
DELETE /api/v1/secondary-roles/{grantId}
DELETE /api/v1/users/{userId}/roles/{grantId}
```

Permission: `user.revoke_role`

**Response:** `204 No Content`.

**Errors:** `IDENTITY_ERROR` (400) if the grant targets a primary role (should be revoked via user update instead).

---

## 4. Login History (`LoginHistoryController`)

```
GET /api/v1/login-history
```

Permission: `user.login_history.read`

**Query parameters**

| Param | Type | Default |
|---|---|---|
| `userId` | UUID | optional — filter to one user |
| `page` | int | `0` |
| `size` | int | `20` |

**Response body** `200 OK`

```json
{
  "data": [
    {
      "id": 4821,
      "userId": "7dd3929a-697c-46e1-a76f-989c76a67a24",
      "attemptedUsername": "cashier",
      "clientId": "POS-PC-01",
      "ipAddress": "192.168.1.42",
      "success": true,
      "failureReason": null,
      "occurredAt": "2026-08-16T08:00:00Z"
    },
    {
      "id": 4820,
      "userId": null,
      "attemptedUsername": "unknownuser",
      "clientId": "POS-PC-01",
      "ipAddress": "192.168.1.42",
      "success": false,
      "failureReason": "AUTH_INVALID_CREDENTIALS",
      "occurredAt": "2026-08-16T07:58:12Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

Both successful and failed attempts (including unknown usernames) are recorded, so `userId` is `null` for attempts against a nonexistent account.
