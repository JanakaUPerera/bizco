# SME Business Management System (Bizco)

## Software Requirements Specification (SRS)

**Version:** 2.2
**Target Platform:** Java Desktop Application (JavaFX)
**Architecture:** LAN-based client-server with optional single-PC deployment and offline POS resilience
**Database:** PostgreSQL 18.x recommended / PostgreSQL 16+ supported (Server), SQLite + SQLCipher (Client Cache)
**Target Market:** Sri Lankan Small & Medium Enterprises (SMEs)

**v2.2 change:** added Section 6.4.11 (Bill of Materials / Manufacturing), new by the `MVP.md` v1.4 scope-expansion decision. Brands, dynamic attributes/variants (6.4.2–6.4.4), combo/bundle products (6.4.3), promotional pricing/price lists (6.4.5.6–6.4.5.7), and loyalty points (6.2.8) were already specified here and are unchanged by this revision — that decision only moved them from `MVP.md`'s deferred list into MVP scope; it did not change what this SRS already envisioned for them.

---

# 1. Introduction

## 1.1 Purpose

The SME Business Management System (Bizco) is a centralized business management solution designed for Sri Lankan Small and Medium Enterprises (SMEs).

The system will support:

- Product-based businesses
- Service-based businesses
- Hybrid businesses (Products + Services)

The application will provide inventory management, sales management, finance management, taxation support, customer management, reporting, auditing, and business analytics in a single platform.

## 1.2 Business Categories Supported

### Retail & Trading

- Mini Supermarkets
- Grocery Stores
- Clothing Shops
- Hardware Stores
- Bookshops
- Stationery Shops
- Furniture Shops
- Mobile Phone Shops
- Computer Shops
- Agricultural Supply Stores
- Gift Shops
- Jewellery Shops

### Service Businesses

- Communication Centers
- Graphic Design Shops
- Photo Studios
- Printing Presses
- Travel Agencies
- Courier Services
- Event Management Companies
- Digital Marketing Agencies
- Educational Institutes

### Repair & Maintenance Businesses

- Electronic Repair Shops
- Vehicle Service Centers
- IT Support Companies
- CCTV & Security Companies

### Hospitality & Food

- Restaurants
- Cafés
- Bakeries

### Healthcare & Wellness

- Pharmacies
- Beauty Salons
- Barber Shops
- Veterinary Services

### Hybrid Businesses

Businesses selling products while offering services.

Examples:

- Mobile phone shops with repair services
- Computer shops with IT support
- Hardware stores with installation services

## 1.3 Definitions & Acronyms

| Term | Definition |
|---|---|
| SME | Small and Medium Enterprise |
| RBAC | Role-Based Access Control |
| POS | Point of Sale |
| GRN | Goods Received Note |
| SKU | Stock Keeping Unit |
| UOM | Unit of Measure |
| VAT | Value Added Tax |
| SSCL | Social Security Contribution Levy |
| NBT | Nation Building Tax (abolished, legacy) |
| WHT | Withholding Tax |
| EPF | Employees' Provident Fund |
| ETF | Employees' Trust Fund |
| APIT | Advance Personal Income Tax (PAYE) |
| PDPA | Personal Data Protection Act (Sri Lanka, No. 9 of 2022) |
| PII | Personally Identifiable Information |
| KOT | Kitchen Order Ticket |
| 3-Way Match | PO + GRN + Invoice matching |
| COGS | Cost of Goods Sold |

---

# 2. System Objectives

## 2.1 Primary Objectives

- Centralize business operations
- Improve sales efficiency
- Improve inventory accuracy
- Track finances accurately
- Maintain auditability
- Support tax compliance
- Enable business intelligence reporting
- Reduce manual paperwork
- Support multiple concurrent users
- Operate reliably during network/server outages
- Comply with Sri Lanka data privacy laws

## 2.2 Key Success Factors

The system should:

- Be simple enough for small businesses
- Be powerful enough for growing SMEs
- Require minimal training
- Work fully offline within a LAN environment
- Continue POS operations when the server is unavailable
- Support Sri Lankan tax and accounting practices
- Scale without requiring system redesign

---

# 3. Hardware & Network Requirements

## 3.1 Server Requirements

| Component | Minimum | Recommended |
|---|---|---|
| CPU | 4 cores | 8 cores |
| RAM | 8 GB | 16 GB |
| Storage | 256 GB SSD | 512 GB SSD |
| OS | Ubuntu 22.04 LTS / Windows Server 2019 | Latest stable |
| Java | JDK 17+ | JDK 21 |
| PostgreSQL | 16+ | 18.x with streaming replication |

## 3.2 Client Requirements

| Component | Minimum | Recommended |
|---|---|---|
| CPU | 2 cores | 4 cores |
| RAM | 4 GB | 8 GB |
| Display | 1366×768 | 1920×1080 (touch-enabled) |
| Connectivity | 100 Mbps LAN | 1 Gbps LAN |
| Peripherals | USB barcode scanner, receipt printer | Touch screen, cash drawer |

## 3.3 Network Architecture

```text
Server IP:        192.168.1.100 (configurable)
API Port:         8888 (HTTP) / 8443 (HTTPS)
Database Port:    5432 (PostgreSQL, server-local or backend-only)
Max Clients:      50 concurrent
Network:          1 Gbps LAN recommended
Firewall:         Ports 8888/8443 open for LAN clients; 5432 restricted to backend/server only
```

## 3.4 Peripheral Integration

### Receipt Printers

- Interface: USB (serial emulation), Network (TCP/IP)
- Paper width: 80mm or 58mm thermal
- ESC/POS command set support
- Auto-cut support (partial or full)
- Cash drawer kick via printer relay

### Barcode Scanners

- Mode: Keyboard wedge (USB HID)
- Auto-suffix: Enter/Return key after scan
- Supported symbologies: EAN-13, UPC-A, CODE-128, CODE-39, QR Code
- No driver installation required (plug-and-play)

### Cash Drawer

- Connection: Via receipt printer relay or USB
- Kick command via printer driver
- No direct USB control in MVP

---

# 4. System Architecture

## 4.1 Deployment Model

Bizco supports two deployment modes:

1. Single-PC mode: JavaFX client, Spring Boot API, PostgreSQL, and file storage run on the same computer.
2. LAN mode: JavaFX clients connect to a central Spring Boot API server over the LAN.

JavaFX clients must communicate with the backend API only. Direct PostgreSQL access from client machines is not allowed.

```text
+---------------------------------------------------+
|                  Server Computer                   |
|---------------------------------------------------|
| Spring Boot API Server (REST)                     |
| PostgreSQL Database                                    |
| File Storage (Documents, Receipt Images)          |
+-----------------------+---------------------------+
                        |
                        | LAN
                        |
        +---------------+---------------+
        |               |               |
+-------v-------+ +-------v-------+ +-------v-------+
|  JavaFX POS   | |  JavaFX POS   | |  JavaFX POS   |
|  Client #1    | |  Client #2    | |  Client #N    |
|  + SQLite     | |  + SQLite     | |  + SQLite     |
|  Offline Cache| |  Offline Cache| |  Offline Cache|
+---------------+ +---------------+ +---------------+

Cashier      Manager      Owner      Accountant      Store Keeper
```

## 4.2 Architecture Pattern

```text
Presentation Layer
(JavaFX)

        | REST API (HTTP/HTTPS)

Application Layer
(Spring Boot)

        | JDBC / JPA / Hibernate

Data Layer
(PostgreSQL)
```

## 4.3 Offline Resilience Mode

### 4.3.1 Local SQLite Database per Client

Each JavaFX client maintains a local SQLite database containing:

- Product cache (SKU, barcode, name, price, stock count)
- Customer cache (code, name, phone, credit limit, balance)
- Tax configuration cache
- Offline transaction queue

### 4.3.2 Offline Transaction Queue

When the server is unreachable, the POS client:

1. Queues all sales transactions locally in SQLite
2. Generates offline invoice numbers (`OFF-{CLIENT_ID}-YYYYMMDD-NNNN`)
3. Continues full POS operations (barcode scan, payment, receipt printing)
4. Tracks queue status: PENDING, SYNCING, SYNCED, FAILED

### 4.3.3 Sync Protocol

Three-phase sync when server is restored:

**Phase 1 — Pull (Server → Client):**
- Download updated product catalog, prices, customers, tax config
- Filter by `changed_since` timestamp to minimize data transfer

**Phase 2 — Push (Client → Server):**
- Upload all queued transactions as a batch
- Server validates, processes, and assigns permanent invoice numbers
- Server validates taxes against the tax snapshot captured at sale time
- Server checks stock availability and flags conflicts

**Phase 3 — Acknowledge:**
- Client receives per-transaction sync result (SYNCED / FAILED / CONFLICT)
- Warnings reported (e.g., price changed, stock oversold)
- Failed transactions flagged for manual review

### 4.3.4 Conflict Resolution Rules

| Conflict | Resolution |
|---|---|
| Product price changed | Preserve original offline sale price; flag variance for manager review |
| Tax configuration changed | Preserve tax snapshot printed on the offline receipt; flag if tax period correction is required |
| Stock oversold across clients | Allow synced sale, mark stock as negative/oversold, and notify manager for purchase or adjustment |
| Customer credit limit exceeded offline | Accepted with Manager PIN override; flagged on sync |
| Duplicate customer created offline | Server suggests merge candidates; manager confirms merge |

### 4.3.5 Client Registration & Heartbeat

- Each client generates a unique `client_id` (UUID) on first launch
- Client registers with server on first connect
- Heartbeat `GET /api/health` every 30 seconds
- Timeout or 5xx response triggers offline mode
- Retry backoff: 30s → 60s → 120s → 300s (max)

### 4.3.6 Offline Invoice Numbering

```text
Online:  INV-20260401-0001     (sequential, server-managed)
Offline: OFF-C3-20260401-0042  (prefixed with client ID)

On sync: Server reassigns permanent number; both numbers retained on record.
```

## 4.4 Multi-Branch Readiness

The architecture supports future multi-branch deployment:

- Branch-wise inventory
- Branch-wise users
- Branch-wise pricing (optional)
- Consolidated reporting across branches
- Inter-branch stock transfers

---

# 5. User Roles & Access Control (RBAC)

## 5.1 Permission Model (Action-Based)

### 5.1.1 Permission Format

Permissions follow the pattern: `module.action`

```text
module.action
│          │
│          └── Action: create, read, update, delete, approve, void, export, etc.
└── Module: user, role, customer, product, invoice, inventory, appointment, jobcard, etc.
```

### 5.1.2 Permission Definitions

#### User Management

| Permission | Description |
|---|---|
| `user.create` | Create new user accounts |
| `user.read` | View user list and details |
| `user.update` | Edit user information (name, email, phone) |
| `user.delete` | Deactivate/reactivate user accounts |
| `user.grant_role` | Grant primary or secondary role to user |
| `user.revoke_role` | Revoke role from user |
| `user.reset_password` | Reset user password |
| `user.lock` | Manually lock user account |
| `user.unlock` | Manually unlock user account |
| `user.view_login_history` | View login history for any user |

#### Role Management

| Permission | Description |
|---|---|
| `role.create` | Create new roles |
| `role.read` | View role list and permissions |
| `role.update` | Modify role permissions |
| `role.delete` | Delete custom roles (system roles protected) |
| `role.assign_permission` | Add permissions to a role |
| `role.remove_permission` | Remove permissions from a role |

#### Customer Management

| Permission | Description |
|---|---|
| `customer.create` | Create new customer profiles |
| `customer.read` | View customer list and details |
| `customer.update` | Edit customer information |
| `customer.delete` | Anonymize/delete customer data |
| `customer.view_pii` | View sensitive PII (NIC, full phone) |
| `customer.export_pii` | Export customer data (logged) |
| `customer.view_ledger` | View customer transaction ledger |
| `customer.view_purchase_history` | View customer purchase history |
| `customer.apply_credit` | Apply credit limit to customer |

#### Product Management

| Permission | Description |
|---|---|
| `product.create` | Create new products |
| `product.read` | View product list and details |
| `product.update` | Edit product information and pricing |
| `product.delete` | Deactivate/discontinue products |
| `product.import` | Import products from CSV/Excel |
| `product.export` | Export product data |
| `product.view_cost` | View cost price (sensitive) |

#### Category Management

| Permission | Description |
|---|---|
| `category.create` | Create product categories |
| `category.read` | View category tree |
| `category.update` | Edit category names/descriptions |
| `category.delete` | Delete categories (must be empty) |

#### Invoice & POS

| Permission | Description |
|---|---|
| `invoice.create` | Create sales invoices |
| `invoice.read` | View invoice list and details |
| `invoice.void` | Void/cancel invoices |
| `invoice.reprint` | Reprint receipts |
| `invoice.payment.create` | Record payments against invoices |
| `invoice.payment.refund` | Process refunds |
| `invoice.discount.apply` | Apply discounts ≤10% |
| `invoice.discount.approve_25` | Approve discounts 10–25% |
| `invoice.discount.approve_50` | Approve discounts >25% |
| `invoice.override_price` | Override product price |
| `invoice.sell_below_cost` | Allow selling below cost |
| `invoice.hold_bill` | Hold/resume bills |
| `invoice.credit_note.create` | Issue credit notes |
| `invoice.quotation.create` | Create quotations |

#### Inventory Management

| Permission | Description |
|---|---|
| `inventory.read` | View stock on hand |
| `inventory.grn.create` | Create Goods Received Notes |
| `inventory.grn.read` | View GRN history |
| `inventory.adjustment.create` | Create stock adjustments |
| `inventory.adjustment.approve` | Approve stock adjustments |
| `inventory.return.create` | Create stock returns to supplier |
| `inventory.stock_count.create` | Create physical stock count sessions |
| `inventory.stock_count.approve` | Approve stock count variances |
| `inventory.view_cost` | View stock valuation |

#### Appointment & Scheduling

| Permission | Description |
|---|---|
| `appointment.create` | Create new appointments |
| `appointment.read` | View appointment calendar |
| `appointment.update` | Edit/reschedule appointments |
| `appointment.cancel` | Cancel appointments |
| `appointment.convert_to_job` | Convert appointment to job card |

#### Job Card Management

| Permission | Description |
|---|---|
| `jobcard.create` | Create job cards |
| `jobcard.read` | View job card details |
| `jobcard.update` | Edit job card information |
| `jobcard.status_change` | Change job card status |
| `jobcard.parts.add` | Add parts to job card |
| `jobcard.estimate.create` | Create job estimates |
| `jobcard.estimate.approve` | Approve estimates on behalf of customer |
| `jobcard.complete` | Mark job as completed |

#### Supplier Management

| Permission | Description |
|---|---|
| `supplier.create` | Create new suppliers |
| `supplier.read` | View supplier list and details |
| `supplier.update` | Edit supplier information |
| `supplier.delete` | Deactivate suppliers |
| `supplier.payment.create` | Record supplier payments |

#### Finance & Accounting

| Permission | Description |
|---|---|
| `finance.view_receivables` | View accounts receivable |
| `finance.view_payables` | View accounts payable |
| `finance.expense.create` | Create expense entries |
| `finance.expense.approve` | Approve expenses |
| `finance.journal_entry.create` | Create manual journal entries |

#### Reporting

| Permission | Description |
|---|---|
| `report.sales.view` | View sales reports |
| `report.inventory.view` | View inventory reports |
| `report.finance.view` | View financial reports |
| `report.customer.view` | View customer reports |
| `report.tax.view` | View tax reports |
| `report.export` | Export reports (PDF, CSV) |
| `report.view_audit_logs` | View audit trail |

#### System Administration

| Permission | Description |
|---|---|
| `system.config` | Access system configuration |
| `system.backup.create` | Create backups |
| `system.backup.restore` | Restore from backup |
| `system.user_management` | Full user management access |

### 5.1.3 Permission Resolution

When checking if a user has a permission:

```text
1. Check primary role → if permission exists → GRANTED
2. Check all active secondary roles → if permission exists → GRANTED
3. Check if any secondary role has the permission → GRANTED
4. Otherwise → DENIED
```

Multiple roles are additive — a user's effective permissions are the **union** of all their active roles' permissions.

## 5.2 Role Definitions

### 5.2.1 System Roles (Pre-defined, Non-deletable)

| Role | Description |
|---|---|
| **Super Administrator** | System-wide access, user/role management, security |
| **Business Owner** | Full business access, financial reports, high-level approvals |
| **Branch Manager** | Sales/inventory supervision, mid-level approvals |
| **Accountant** | Accounting, tax management, financial reporting |
| **Cashier** | POS operations, invoice generation, basic customer management |
| **Store Keeper** | Inventory control, goods receiving, stock adjustments |
| **Service Officer** | Service orders, job cards, customer service management |
| **Auditor** | Read-only access to all modules, audit reports |

### 5.2.2 Custom Roles

- Admin/Owner can create custom roles
- Custom roles are assigned specific permissions from the permission list
- Custom roles can be deleted (unless assigned to active users)
- Custom role names must be unique

### 5.2.3 Role Permission Matrix

#### Super Administrator

```text
user.create, user.read, user.update, user.delete,
user.grant_role, user.revoke_role, user.reset_password,
user.lock, user.unlock, user.view_login_history,
role.create, role.read, role.update, role.delete,
role.assign_permission, role.remove_permission,
system.config, system.backup.create, system.backup.restore,
system.user_management
```

#### Business Owner

```text
customer.create, customer.read, customer.update, customer.delete,
customer.view_pii, customer.export_pii, customer.view_ledger,
customer.view_purchase_history, customer.apply_credit,
product.create, product.read, product.update, product.delete,
product.view_cost, product.import, product.export,
category.create, category.read, category.update, category.delete,
invoice.create, invoice.read, invoice.void, invoice.reprint,
invoice.payment.create, invoice.payment.refund,
invoice.discount.apply, invoice.discount.approve_25, invoice.discount.approve_50,
invoice.override_price, invoice.sell_below_cost,
invoice.hold_bill, invoice.credit_note.create, invoice.quotation.create,
inventory.read, inventory.grn.create, inventory.grn.read,
inventory.adjustment.create, inventory.adjustment.approve,
inventory.return.create, inventory.stock_count.create, inventory.stock_count.approve,
inventory.view_cost,
appointment.create, appointment.read, appointment.update, appointment.cancel,
appointment.convert_to_job,
jobcard.create, jobcard.read, jobcard.update, jobcard.status_change,
jobcard.parts.add, jobcard.estimate.create, jobcard.estimate.approve, jobcard.complete,
supplier.create, supplier.read, supplier.update, supplier.delete, supplier.payment.create,
finance.view_receivables, finance.view_payables,
finance.expense.create, finance.expense.approve,
report.sales.view, report.inventory.view, report.finance.view,
report.customer.view, report.tax.view, report.export, report.view_audit_logs
```

#### Branch Manager

```text
customer.create, customer.read, customer.update, customer.view_pii,
product.create, product.read, product.update,
invoice.create, invoice.read, invoice.void, invoice.reprint,
invoice.payment.create, invoice.payment.refund,
invoice.discount.apply, invoice.discount.approve_25,
invoice.override_price, invoice.hold_bill,
inventory.read, inventory.grn.create, inventory.grn.read,
inventory.adjustment.create, inventory.adjustment.approve,
inventory.return.create, inventory.stock_count.create, inventory.stock_count.approve,
appointment.create, appointment.read, appointment.update, appointment.cancel,
appointment.convert_to_job,
jobcard.create, jobcard.read, jobcard.update, jobcard.status_change,
jobcard.parts.add, jobcard.estimate.create, jobcard.estimate.approve, jobcard.complete,
supplier.read,
finance.expense.create, finance.expense.approve,
report.sales.view, report.inventory.view, report.customer.view
```

#### Accountant

```text
customer.read, customer.view_pii,
product.read, product.view_cost,
invoice.read, invoice.payment.create, invoice.payment.refund,
inventory.read, inventory.view_cost, inventory.grn.read,
finance.view_receivables, finance.view_payables,
finance.expense.create, finance.expense.approve,
report.sales.view, report.inventory.view, report.finance.view,
report.customer.view, report.tax.view, report.export
```

#### Cashier

```text
customer.create, customer.read, customer.update,
product.read,
invoice.create, invoice.read, invoice.reprint,
invoice.payment.create,
invoice.discount.apply,
invoice.hold_bill,
jobcard.read, jobcard.status_change,
appointment.read
```

#### Store Keeper

```text
product.read, product.update,
inventory.read, inventory.grn.create, inventory.grn.read,
inventory.adjustment.create, inventory.stock_count.create,
supplier.read,
jobcard.read, jobcard.parts.add
```

#### Service Officer

```text
customer.create, customer.read, customer.update, customer.view_pii,
appointment.create, appointment.read, appointment.update,
appointment.convert_to_job,
jobcard.create, jobcard.read, jobcard.update, jobcard.status_change,
jobcard.parts.add, jobcard.estimate.create, jobcard.complete,
product.read
```

#### Auditor

```text
customer.read, customer.view_pii,
product.read,
invoice.read,
inventory.read,
appointment.read,
jobcard.read,
supplier.read,
report.sales.view, report.inventory.view, report.finance.view,
report.customer.view, report.tax.view, report.export,
report.view_audit_logs
```

## 5.3 Multi-Role System

### 5.3.1 Role Types

| Type | Description | Max Count |
|---|---|---|
| Primary Role | Permanent, assigned at user creation | 1 per user |
| Secondary Role | Temporary, granted for a specific period | Multiple per user |

### 5.3.2 User Role Data Model

```text
User
  ├── user_id (UUID)
  ├── username
  ├── password_hash
  ├── first_name / last_name
  ├── email / phone
  ├── primary_role_id → Role (permanent, always active)
  ├── is_active
  └── ...

UserRole (Secondary Roles)
  ├── user_role_id (UUID)
  ├── user_id → User
  ├── role_id → Role
  ├── granted_by → User (who granted this role)
  ├── granted_at (timestamp)
  ├── expires_at (timestamp) — NULL means never expires
  ├── is_active (boolean) — soft revoke flag
  ├── revoked_at (timestamp)
  ├── revoked_by → User
  └── revoke_reason (text)
```

### 5.3.3 Granting Secondary Roles

```text
Workflow:
1. Authorized user (with user.grant_role permission) opens user management
2. Selects target user → Actions → Grant Secondary Role
3. Selects role from dropdown (excluding user's primary role)
4. Sets expiry period:
   - Default: 1 day (from now)
   - Options: 1 hour, 6 hours, 1 day, 7 days, 30 days, custom
   - Custom: User enters specific date/time
5. System creates UserRole record with expires_at
6. Secondary role is immediately active
7. Audit log recorded: who granted, which role, to whom, expiry
```

### 5.3.4 Revoking Secondary Roles

#### Automatic Revocation

```text
Background Job (runs every minute):
1. Query: SELECT * FROM user_roles
   WHERE is_active = true
   AND expires_at IS NOT NULL
   AND expires_at <= NOW()

2. For each expired role:
   a. Set is_active = false
   b. Set revoked_at = NOW()
   c. Set revoke_reason = 'Automatic expiry'
   d. Log audit trail
   e. If user is currently logged in:
      - Recalculate effective permissions
      - If current action requires revoked permission → show warning
      - Do NOT force logout (permission change applies to next action)
```

#### Manual Revocation

```text
Workflow:
1. Authorized user (with user.revoke_role permission) opens user management
2. Selects target user → View Roles
3. Shows primary role (cannot revoke) + secondary roles
4. Clicks "Revoke" on a secondary role
5. Optional: Enter revocation reason
6. Secondary role marked as inactive immediately
7. Audit log recorded: who revoked, which role, from whom, reason
```

### 5.3.5 Effective Permissions Calculation

```text
function getEffectivePermissions(user):
    permissions = Set()

    // Add primary role permissions
    primaryRole = user.primary_role_id
    permissions.addAll(primaryRole.permissions)

    // Add active secondary role permissions
    secondaryRoles = user.user_roles
        .filter(ur => ur.is_active == true)
        .filter(ur => ur.expires_at == null OR ur.expires_at > NOW())

    for each role in secondaryRoles:
        permissions.addAll(role.permissions)

    return permissions  // Union of all permissions
```

### 5.3.6 Role Display

In the user management screen:

```text
┌─────────────────────────────────────────────────────────────┐
│  User: John Perera                                          │
├─────────────────────────────────────────────────────────────┤
│  Primary Role: Cashier                                      │
│  Status: Active                                             │
├─────────────────────────────────────────────────────────────┤
│  Secondary Roles:                                           │
│  ┌──────────┬───────────┬─────────────┬──────────────────┐ │
│  │ Role     │ Granted By│ Expires At  │ Status           │ │
│  ├──────────┼───────────┼─────────────┼──────────────────┤ │
│  │ Manager  │ Owner     │ Apr 2, 10AM │ Active (23h left)│ │
│  │ Store K. │ Manager   │ Apr 1, 6PM  │ Expired          │ │
│  └──────────┴───────────┴─────────────┴──────────────────┘ │
│                                                             │
│  [+ Grant Secondary Role]                                   │
└─────────────────────────────────────────────────────────────┘
```

### 5.3.7 Permission Check at Runtime

```text
When user attempts an action:
1. System calculates effective permissions (primary + active secondary roles)
2. Checks if required permission is in effective permissions
3. If GRANTED → allow action
4. If DENIED → show "Access Denied" message

When secondary role expires:
5. Next permission check recalculates effective permissions
6. If action requires expired permission → denied
7. User sees: "Your temporary Manager role has expired. Contact admin to extend."
```

## 5.4 Default Role Assignments

### 5.4.1 Default Roles with Permissions

On first launch, system creates roles with these permissions:

```text
SUPER_ADMIN:
  system.user_management, system.config, system.backup.create, system.backup.restore,
  user.create, user.read, user.update, user.delete,
  user.grant_role, user.revoke_role, user.reset_password,
  user.lock, user.unlock, user.view_login_history,
  role.create, role.read, role.update, role.delete,
  role.assign_permission, role.remove_permission

OWNER:
  [All permissions except system.user_management and role management]

MANAGER:
  [Subset of owner permissions - sales, inventory, appointment supervision]

CASHIER:
  customer.create, customer.read, customer.update,
  product.read,
  invoice.create, invoice.read, invoice.reprint,
  invoice.payment.create, invoice.discount.apply, invoice.hold_bill

STORE_KEEPER:
  product.read, product.update,
  inventory.read, inventory.grn.create, inventory.adjustment.create

SERVICE_OFFICER:
  appointment.create, appointment.read, appointment.update,
  jobcard.create, jobcard.read, jobcard.update, jobcard.status_change

ACCOUNTANT:
  finance.view_receivables, finance.view_payables,
  report.sales.view, report.finance.view, report.tax.view

AUDITOR:
  [Read-only access to all modules]
```

### 5.4.2 Custom Role Creation

Admin can create roles with any combination of permissions:

```text
Example: "Junior Cashier" role
  - customer.read (no create/update)
  - product.read
  - invoice.create (no void)
  - invoice.payment.create
  - invoice.discount.apply (only ≤10%)

Example: "Inventory Manager" role
  - product.create, product.read, product.update
  - inventory.read, inventory.grn.create, inventory.adjustment.create, inventory.adjustment.approve
  - supplier.create, supplier.read, supplier.update
```

## 5.5 PII Access Restrictions

| Action | Owner | Manager | Cashier | Accountant | Auditor | Store Keeper |
|---|---|---|---|---|---|---|
| View full NIC | ✅ | ✅ | ❌ (masked) | ❌ | ✅ | ❌ |
| View full phone | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| View address | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| Edit customer data | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Export raw data | ✅ | ✅ | ❌ | ✅ (tax only) | ✅ (logged) | ❌ |
| Delete/anonymize | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

All PII view/export actions are logged in the PII Access Audit.

## 5.6 Session Management

- Session timeout after 15 minutes of inactivity (configurable per role)
- Maximum 3 concurrent sessions per user
- Force logout from admin panel
- Login history with IP, timestamp, client ID

## 5.7 Account Locking

- Lock after 5 failed login attempts
- Auto-unlock after 30 minutes (configurable)
- Manual unlock by Super Administrator
- Locked status visible in user management screen

## 5.8 Audit Trail for Role Changes

Every role-related action is logged:

| Action | Audit Log Entry |
|---|---|
| Grant secondary role | user_id, role_id, granted_by, expires_at |
| Revoke secondary role | user_id, role_id, revoked_by, reason, revoked_at |
| Role expiry (auto) | user_id, role_id, expires_at, revoked_at |
| Permission change on role | role_id, permission, old_value, new_value, changed_by |
| Primary role change | user_id, old_role_id, new_role_id, changed_by |

---

# 6. Functional Modules

---

# 6.1 Authentication & Security

## 6.1.1 Login Management

- Username/password login
- Password reset (self-service via security questions or admin-initiated)
- First login forces password change
- Password history (last 5 passwords blocked)

## 6.1.2 Password Policies

- Minimum length: 8 characters
- Must contain: uppercase, lowercase, digit, special character
- Expiry: 90 days (configurable)
- Hashed with BCrypt (cost factor 12)
- Never stored in plain text

## 6.1.3 Login History & Failed Attempt Tracking

- Record: user, timestamp, IP, client_id, success/failure
- Failed attempt reason recorded (wrong password, locked account)
- Failed login report available to Super Administrator

## 6.1.4 Session Timeout

- Idle timeout: 15 minutes (configurable per role)
- Warning dialog at 2 minutes before timeout
- Unsaved POS transactions held until re-login
- Timeout during offline mode: re-authentication required

---

# 6.2 Customer Management

## 6.2.1 Customer Profiles

### Customer Information

- Customer Code (auto-generated)
- Name
- NIC / BR Number (AES-256 encrypted at rest)
- Contact Information (phone, email)
- Address
- Customer Category
- Price Tier Override (optional)
- Photo (optional)
- Consent flags (see 5.2.3)

### PII Data Sensitivity Classification

| Level | Fields | Protection |
|---|---|---|
| Sensitive | NIC, Passport, BR Number, Bank account | Encrypted at rest (AES-256), masked in standard views |
| Personal | Name, DOB, Phone, Email, Address | Access-controlled, logged on view |
| Business | Company name, business address, business phone | Standard protection |

## 6.2.2 Customer Categories → Price Tier Mapping

| Category | Default Price Tier | Allow Credit | Notes |
|---|---|---|---|
| Retail | Selling Price | No | Walk-in customers |
| Wholesale | Wholesale Price | Configurable | Requires credit application |
| Corporate | Contract Price | Yes (per contract) | Requires signed agreement |
| Government | Tender Price | Yes (per PO) | Requires valid purchase order |

Price Resolution Order at POS:

```text
1. Customer-specific Price List or Contract (if active)
2. Customer Category's Default Price Tier
3. Product's default Selling Price
```

Cashier can switch price tier with `ACTION_CHANGE_PRICE_TIER` permission. Override reason is mandatory and logged.

## 6.2.3 Consent Management (PDPA)

### Customer Consent Fields

- `consent_marketing` — Receive promotional offers (SMS/email)
- `consent_data_sharing` — Share data with third-party partners
- `consent_retention_period` — 1yr / 3yr / 5yr / Indefinite
- `consent_date` — Date consent was given
- `consent_withdrawn_date` — Date consent was revoked (if applicable)

### Consent Workflow

- Consent captured at customer registration (checkboxes)
- Customer cannot be registered without acknowledging Privacy Policy
- Consent auto-renewal prompt at anniversary date
- Consent withdrawal immediately stops marketing communications
- All consent changes logged in ConsentLog

### Consent Log

```text
ConsentLog
  ├── customer_id
  ├── action (GIVEN | WITHDRAWN | RENEWED)
  ├── consent_type (MARKETING | DATA_SHARING | RETENTION)
  ├── ip_address
  ├── timestamp
  └── recorded_by
```

## 6.2.4 Right to Erasure / Anonymization

### Workflow

1. User initiates: Customer → Actions → Request Erasure
2. System checks eligibility:
   - Outstanding invoices? → Cannot delete until settled + 7 years
   - Pending service jobs? → Cannot delete until completed
3. If eligible:
   - Name → "Deleted Customer #{id}"
   - NIC, Phone, Email, Address, DOB → NULL
   - Transaction history retained (de-identified)
   - `is_anonymized = true`, `anonymized_at = NOW()`
4. If ineligible:
   - Scheduled erasure date = last_transaction_date + 7 years + 1 day
   - Customer notified of expected deletion date

## 6.2.5 Credit Limits

### Enforcement

| Type | Behavior |
|---|---|
| Soft limit | Warning displayed, allows override (Manager PIN required) |
| Hard limit | Blocks new credit sales (Owner override required) |

### Aging-Based Blocking

| Days Outstanding | Action |
|---|---|
| 0–30 | Normal operations |
| 31–60 | Warning on POS at customer selection |
| 61–90 | Block credit sales (cash-only allowed) |
| 91+ | Block all sales until payment received, escalate to Owner |

## 6.2.6 Customer Ledger

- Chronological list of all transactions (invoices, payments, credit notes, deposits)
- Running balance
- Filterable by date range, transaction type
- Aging summary at top: 0–30 / 31–60 / 61–90 / 90+

## 6.2.7 Purchase History

- All invoices for the customer
- Product-level detail
- Searchable by invoice number, date, product
- Average spend, visit frequency, last purchase date

## 6.2.8 Loyalty Points

### 6.2.8.1 Earning Rules

| Method | Points | Configurable |
|---|---|---|
| Per spend | 1 point per LKR 100 | Yes (ratio) |
| Birthday bonus | 100 points | Yes |
| First purchase | 200 points | Yes (one-time) |
| Product-specific multiplier | 2x on high-margin items | Yes (per product) |

Points are earned on taxable value (excluding VAT).

### 6.2.8.2 Redemption Rules

| Redemption Type | Value | Configurable |
|---|---|---|
| Discount | 1 point = LKR 1 | Yes |
| Free product | Points = Selling Price | Yes |
| Max invoice coverage | 50% of invoice paid with points | Yes |

### 6.2.8.3 Expiry & Clawback

- Points expire 12 months after earning (configurable)
- Notification sent 30 days before bulk expiry
- If invoice is returned, points earned on that invoice are clawed back
- If points were already redeemed, cash-equivalent deducted from refund
- Cannot earn points on tax amount

### 6.2.8.4 Accounting Treatment

| Event | Debit | Credit |
|---|---|---|
| Points earned | Marketing Expense (est. redemption cost) | Loyalty Liability |
| Points redeemed | Loyalty Liability | Revenue (discount reduction) |
| Points expired | Loyalty Liability | Other Income |

---

# 6.3 Supplier Management

## 6.3.1 Supplier Records

- Supplier Code
- Supplier Name
- Contact Person
- Address
- Phone / Email
- Tax Registration Number (TIN / VAT number)
- Payment Terms (Net 30, Net 60, etc.)
- Has Consignment Agreement (boolean)
- Status (Active / Suspended / Inactive)

## 6.3.2 Supplier Ledger

- Chronological list of transactions (purchase invoices, payments, debit/credit notes, returns)
- Running balance
- Aging summary

## 6.3.3 Supplier Payments & Outstanding Balances

- Record payments against specific invoices
- Track outstanding payables with aging
- Payment method tracking (Cash, Cheque, Bank Transfer)
- Cheque management (cheque number, date, clearing status)

## 6.3.4 Consignment Agreements

### 6.3.4.1 Agreement Creation

```text
ConsignmentAgreement
  ├── supplier_id
  ├── agreement_number (auto)
  ├── effective_date / end_date
  ├── commission_rate (%) — business's margin on sale
  ├── minimum_margin (%) — floor price below which product cannot be sold
  ├── settlement_terms (Monthly | Bi-Weekly | Per-Sale)
  └── status (Active | Suspended | Terminated)
```

### 6.3.4.2 Consignment GRN

- Separate GRN type: "Consignment GRN"
- Goods added to consignment stock (tracked separately from owned stock)
- No payment to supplier at this stage
- No liability recorded (goods not yet owned)

### 6.3.4.3 Consignment Sale at POS

- Barcode scan identifies product as consignment (visual indicator in POS)
- Sale proceeds normally
- At sale completion:
  - Deduct from consignment stock
  - Record consignment sale for supplier settlement
  - Accounting:
    - Debit: Cash / Receivable (full sale price)
    - Credit: Consignment Payable (supplier's contracted price)
    - Credit: Commission Income (business margin)

### 6.3.4.4 Consignment Settlement

- Periodic settlement report (per agreement terms)
- Amount payable = sum(sold_items × consignment_unit_price)
- Deduct any returned consignment goods
- Process payment via standard supplier payment workflow
- Accounting:
  - Debit: Consignment Payable
  - Credit: Cash

### 6.3.4.5 Unsold Consignment Returns

- Return goods to supplier from consignment stock
- No financial transaction (goods were never paid for)
- Both parties acknowledge return quantity

### 6.3.4.6 Damaged/Theft on Consignment

- Per agreement: supplier bears cost or shared
- If supplier bears: stock adjustment only (no financial impact)
- If shared: commission income reduced

## 6.3.5 Purchase History

- All purchase invoices and POs by supplier
- Product-level purchase detail
- Price trend analysis
- Lead time tracking (avg days from PO to GRN)

---

# 6.4 Product Management

## 6.4.1 Product Types

| Type | Description | Stock Tracking |
|---|---|---|
| Inventory Product | Physical stock item | Full inventory tracking |
| Service Product | Non-stock service item | No stock tracking |
| Rental Product | Rentable equipment/item | Availability tracking |
| Custom Product | Made-to-order product | No stock (project-based) |

## 6.4.2 Product Variants

### 6.4.2.1 Variant Model

```text
Product (parent)
  ├── is_variant_group: boolean
  └── variant_attributes: [Color, Size, Material, ...]

ProductVariant
  ├── product_id (parent)
  ├── sku (unique — e.g., TSH-RD-M)
  ├── barcode
  ├── variant_label ("Red / Medium")
  ├── variant_values: {"Color": "Red", "Size": "M"}
  ├── cost_price (overrides parent if set)
  ├── selling_price (overrides parent if set)
  ├── wholesale_price (overrides parent if set)
  ├── stock_qty (per-variant stock)
  ├── low_stock_threshold
  ├── image_url
  ├── weight / dimensions
  └── is_active
```

### 6.4.2.2 Barcode Scanning

- Scanning the parent product barcode → shows variant selection popup
- Scanning a variant barcode → auto-selects specific variant and adds to cart
- Multiple barcodes per variant allowed (supplier barcode + own barcode)

### 6.4.2.3 POS Variant Selection Flow

```text
1. Scan barcode → lookup Product
2. IF is_variant_group:
   → Show first attribute selector (e.g., "Select Color")
   → Show second attribute selector (e.g., "Select Size")
   → Confirm → add variant line item
3. ELSE:
   → Add to cart directly
```

## 6.4.3 Combo / Bundle Products

```text
ProductCombo
  ├── combo_product_id → Product (virtual combo item)
  ├── component_product_id → Product
  ├── component_variant_id → ProductVariant (nullable)
  ├── qty (how many of this component per combo)
  └── override_price (nullable — component priced differently in combo)

Rules:
  - Combo price ≤ sum of component prices (must offer savings)
  - Inventory deduction: components deducted individually
  - Combo "product" is virtual — no stock tracking
```

## 6.4.4 Product Information

- SKU (auto-generated or manual entry)
- Barcode (EAN-13, UPC-A, CODE-128, or custom)
- Product Name
- Description
- Category (hierarchical, tree-based)
- Brand
- Unit of Measure
- Tax Category (Standard-rated, Zero-rated, Exempt, Mixed)
- Is variant group (boolean)
- Is serial tracked (boolean)
- Is batch tracked (boolean)
- Has expiry (boolean)
- Image
- Status (Active / Discontinued / Inactive)

## 6.4.5 Pricing

### 6.4.5.1 Price Tiers

| Tier | Purpose |
|---|---|
| Cost Price | Purchase cost from supplier |
| Selling Price | Default retail price |
| Wholesale Price | Price for wholesale customers |
| Dealer Price | Price for dealer/reseller customers |

### 6.4.5.2 Customer-to-Price-Tier Mapping

Per customer:
- Default tier inherited from Customer Category
- Individual override allowed (for contract pricing)
- Resolution: Customer override > Category default > System default

### 6.4.5.3 Price Lists

```text
PriceList
  ├── name ("Summer 2026 Wholesale")
  ├── valid_from / valid_to
  ├── status (Draft → Active → Expired)
  └── is_default (boolean)

PriceListItem
  ├── price_list_id
  ├── product_id / variant_id
  ├── price (override tier price)
  ├── min_qty (volume threshold)
  └── discount_percent (alternative to flat price)

CustomerPriceList
  ├── customer_id
  ├── price_list_id
  └── effective_date
```

Resolution order at POS:
1. Customer-specific price list
2. Customer's category default price list
3. Default price list (is_default = true)
4. Product's base price tier

### 6.4.5.4 Contract Pricing

For Corporate/Government customers:
- Special `ProductContractPrice` with contract_id
- Linked to Contract record with validity dates
- Overrides standard tier pricing while active

### 6.4.5.5 Price History

- Every price change creates `PriceHistory` record
- Fields: `product_id, price_tier, old_price, new_price, changed_by, changed_at, reason`
- Price history viewable from product screen

### 6.4.5.6 Promotional Pricing

- Date-range based (with optional time window, e.g., happy hour 2–5 PM)
- Can be buyer-specific (customer group, specific customer)
- Configurable stacking: replaces base price OR applies additional discount
- Min/max quantity restrictions
- Promo code support (text-based discount codes)

### 6.4.5.7 Volume Discounts

- Discount tiers by quantity: e.g., 5% off for qty ≥ 50, 10% off for qty ≥ 100
- Applied automatically at POS when quantity threshold met
- Can be combined with other discounts per business rules

## 6.4.6 Unit of Measure & Conversions

### 6.4.6.1 UOM Model

```text
UOM
  ├── code (PCS, KG, LTR, CASE, DOZ, BTL, BOX, MTR)
  └── name (Piece, Kilogram, Liter, Case, Dozen, Bottle, Box, Meter)

UOMCategory
  ├── name (Quantity, Weight, Volume, Length, Area)
  └── base_uom_id → UOM (e.g., Weight → KG)
```

### 6.4.6.2 UOM Conversions

```text
UOMConversion
  ├── from_uom_id → UOM
  ├── to_uom_id → UOM
  ├── conversion_factor (e.g., 12 for CASE → PCS)
  └── uom_category_id → UOMCategory
```

### 6.4.6.3 Per-Product UOM Configuration

```text
ProductUOM
  ├── product_id
  ├── purchase_uom_id (unit bought from supplier — e.g., CASE)
  ├── sales_uom_id (unit sold to customer — e.g., PCS)
  ├── stock_uom_id (internal tracking unit — normally PCS)
  └── allow_partial_unit (boolean — e.g., sell 0.5 KG)
```

Conversion at transaction time:
```text
Purchase: 10 CASE → internal stock = 10 × 12 = 120 PCS
              Cost per PCS = total_cost / 120

Sale: 3 PCS → deduction = 3 PCS
          If UOM different (e.g., sell by BOX of 6):
          deduction = 3 × 6 = 18 PCS
```

## 6.4.7 Batch Tracking

```text
ProductBatch
  ├── product_id / variant_id
  ├── batch_number (supplier's lot no.)
  ├── internal_batch_number (auto if supplier batch not available)
  ├── manufacturing_date
  ├── expiry_date
  ├── received_date (GRN date)
  ├── quantity_received
  ├── quantity_remaining
  ├── cost_price (batch-specific)
  ├── supplier_id
  ├── grn_id
  └── status (Active | Expired | Depleted | Quarantined)
```

FIFO (First-Expiry-First-Out) at POS:
- Sort available batches by expiry_date ASC
- Deduct from earliest-expiring batch first
- Record batch allocation on invoice line item

## 6.4.8 Expiry Tracking

### 6.4.8.1 Warning Levels

| Days Until Expiry | Level | Behavior |
|---|---|---|
| ≤ 90 days | Warning (yellow) | Flag in inventory reports |
| ≤ 30 days | Alert (orange) | Notify Store Keeper, suggest markdown |
| ≤ 0 days (expired) | Blocked (red) | Cannot sell at POS |

### 6.4.8.2 Auto Markdown

- Configurable: days before expiry to auto-apply markdown [default: 14]
- Configurable: discount percentage for expiring stock [default: 25%]
- Markdown applied at POS automatically
- Store Keeper can override markdown price

## 6.4.9 Serial Number Tracking

### 6.4.9.1 Data Model

```text
ProductSerialNumber
  ├── product_id / variant_id
  ├── serial_number (unique across product)
  ├── batch_id (nullable)
  ├── status (In_Stock | Sold | Returned | Warranty_Claim | Damaged)
  ├── cost_price
  ├── selling_price
  ├── purchase_date (GRN date)
  ├── sale_date
  ├── sold_to_customer_id
  ├── sold_invoice_id
  ├── warranty_start_date (= sale_date)
  ├── warranty_end_date (= sale_date + warranty_period_months)
  ├── warranty_period_months
  └── notes
```

### 6.4.9.2 Serial Lifecycle

```text
GRN: Scan/receive serials → In_Stock
POS: Scan serial → Sold (linked to customer + invoice)
Return: Scan serial → Returned
Warranty: Scan serial → Warranty_Claim (linked to job card)
```

### 6.4.9.3 POS Serial Scanning Flow

```text
IF product.serial_tracked = true:
  → Prompt "Scan serial number or select from available"
  → Show list of In_Stock serials
  → Validate serial belongs to this product and is In_Stock
  → Add to cart with serial assigned
```

### 6.4.9.4 Multi-Tracking by Product Category

| Product Type | Batch | Expiry | Serial | Example |
|---|---|---|---|---|
| Medicine | ✅ | ✅ | ❌ | Paracetamol batch |
| Electronics | ❌ | ❌ | ✅ | Laptop S/N |
| Food (bakery) | ✅ | ✅ | ❌ | Bread batch |
| Vehicle | ❌ | ❌ | ✅ | Chassis number |
| Paint/chemical | ✅ | ✅ | ✅ | Paint batch + can |
| Appliance | ❌ | ❌ | ✅ | Washing machine S/N |

## 6.4.10 Reorder Rules

### 6.4.10.1 Reorder Point Calculation

```text
Reorder Point = (avg_daily_sales × lead_time_days) + safety_stock
Reorder Quantity = max(EOQ, reorder_point - current_stock)
```

Where:
- `avg_daily_sales` = moving average of last 90 days
- `lead_time_days` = supplier delivery time
- `safety_stock` = buffer for demand variability
- `EOQ` = Economic Order Quantity (if configured)

### 6.4.10.2 Configuration Per Product

```text
ProductInventoryConfig
  ├── product_id
  ├── reorder_point (manual override of calculated value)
  ├── reorder_quantity (manual override)
  ├── lead_time_days
  ├── safety_stock
  ├── max_stock_level (warehouse capacity limit)
  └── preferred_supplier_id
```

### 6.4.10.3 Auto-Purchase Suggestion

- Daily background job evaluates all products
- Product with `current_stock ≤ reorder_point` → generate suggestion
- Suggestions grouped by preferred supplier → Draft Purchase Order
- Store Keeper notified: "5 items need reordering. Generate POs?"
- Store Keeper reviews, adjusts quantities, submits for approval

## 6.4.11 Bill of Materials / Manufacturing

**Added by the MVP.md v1.4 scope-expansion decision (Section 1.2a) — new to this SRS, not part of the original product vision.** For a business that assembles, customizes, or manufactures a finished product from other stocked items rather than purchasing it ready-made (a framing studio building a framed photo from paper + frame + ink allocation; a gift shop assembling a hamper from individual items).

### 6.4.11.1 Model

```text
BillOfMaterials
  ├── finished_variant_id → ProductVariant (the product this recipe builds)
  ├── name
  └── status (Active | Inactive)

BomItem
  ├── bom_id → BillOfMaterials
  ├── component_variant_id → ProductVariant
  ├── quantity (required amount per unit produced)
  ├── wastage_qty (optional — expected loss/offcut per unit produced)
  └── estimated_cost (rolled up from component cost × quantity)
```

- A finished product has at most one active Bill of Materials
- A component may itself appear in multiple other products' Bills of Materials (the same raw material used by several finished goods)
- Circular references are rejected: a component cannot be, directly or transitively, built from the finished product it is a component of

### 6.4.11.2 Production Transaction

```text
1. Store Keeper/Manager selects a Bill of Materials and a quantity to produce
2. System locks every component variant (stable order, same as a multi-line sale)
3. System validates available stock for every component at the required quantity
4. If any component is short, the whole production is rejected — never partially produced
5. System posts:
   - one PRODUCTION_OUT stock movement per component (negative)
   - one PRODUCTION_IN stock movement for the finished variant (positive)
   atomically, in one transaction
6. Production event recorded for traceability (which components, quantities, and cost went into this batch)
```

### 6.4.11.3 Stocked vs Made-to-Order

- **Stocked finished product**: produced ahead of demand via the Production Transaction above; sits in finished-goods stock like any purchased product until sold
- **Made-to-order product**: production happens at time of sale — the Bill of Materials still records what was consumed (components deducted, cost captured), but the finished item is not held in stock beforehand

### 6.4.11.4 Cost Roll-Up

A Bill of Materials' `estimated_cost` is the sum of `component.cost_price × bom_item.quantity` across its items, refreshed whenever a component's cost price changes (the same "cost_price is the current default, cost history is the audit trail" pattern the rest of Section 6.9.5 already uses). Actual production cost (captured per production event, using each component's cost at the time of production) may differ from the estimate and is what feeds the BOM Production report (Section 6.16).

---

# 6.5 Service Management

## 6.5.1 Service Catalog

- Service Code
- Service Description
- Standard Pricing (base price + time-based increments)
- Estimated Duration (minutes)
- Category (Repair, Consultation, Maintenance, etc.)
- Warranty Period (days)
- Requires Approval (boolean — some services need estimate sign-off)

## 6.5.2 Job Card Management

### 6.5.2.1 Full Job Lifecycle

```text
                     Job Created
                         │
                  ┌──────┴──────┐
                  │             │
             Walk-in        Appointment
                  │             │
                  └──────┬──────┘
                         │
                Customer Sign-off on Estimate
                         │
                  ┌──────┴──────┐
                  │             │
             Approve       Reject → Pick up device
                  │
            Technician Assigned
                  │
            In Progress
                  │
         Parts Required? ──Yes──→ Inventory Deduction
                  │                      │
                  ↓                Wait for Parts
            Quality Check               │
                  │               Parts Received
                  │                      │
                  └──────────────────────┘
                         │
                  Ready for Pickup
                         │
                  ┌──────┴──────┐
                  │             │
            Customer collects → Invoice + Payment
                  │
            Warranty Period Begins
```

### 6.5.2.2 Data Model

```text
JobCard
  ├── job_number (JC-YYYYMM-NNNN)
  ├── customer_id
  ├── device_type (Phone/Laptop/TV/Vehicle — configurable list)
  ├── brand / model
  ├── serial_number
  ├── reported_issue
  ├── customer_notes
  ├── accessories_received ("charger, case, box")
  ├── device_condition ("scratched screen, dented corner")
  ├── status (Created | Awaiting_Estimate | Estimate_Approved | Assigned
               | In_Progress | Waiting_Parts | Ready_For_Pickup
               | Completed | Picked_Up | Cancelled)
  ├── technician_id → User
  ├── estimated_completion_date
  ├── actual_completion_date
  ├── pickup_date
  └── warranty_end_date

JobService
  ├── job_card_id
  ├── service_catalog_id
  ├── estimated_cost / actual_cost
  ├── estimated_duration_minutes
  └── notes

JobPart
  ├── job_card_id
  ├── product_id / variant_id
  ├── qty_used
  ├── unit_price (sale price to customer)
  ├── cost_price
  ├── is_warranty_covered (free under warranty)
  └── serial_number (if serial-tracked)

JobEstimate
  ├── job_card_id
  ├── estimated_total
  ├── created_by / created_at
  ├── customer_response (Accepted | Declined | Countered)
  ├── customer_response_at
  └── countered_amount
```

### 6.5.2.3 Status Transition Validation

| From | To | Rule |
|---|---|---|
| Created | In_Progress | Must have Estimate_Approved OR Manager override |
| In_Progress | Ready_For_Pickup | At least one JobService must be completed |
| Ready_For_Pickup | Picked_Up | Must have fully paid invoice |
| Any | Cancelled | Reason required |
| Created | Awaiting_Estimate | Auto-transition after 7 days inactivity |

## 6.5.3 Appointments & Scheduling

- Calendar view for service appointments
- Appointment can be converted to Job Card on walk-in
- Time-slot management (configurable slot duration)
- Technician calendar (view all assigned jobs)
- Daily schedule printout

## 6.5.4 Customer Deposits / Prepayments

### 6.5.4.1 Data Model

```text
CustomerDeposit
  ├── customer_id
  ├── amount
  ├── payment_method (Cash/Card/Transfer/QR)
  ├── reference (deposit slip #, transaction ID)
  ├── status (Unallocated | Partially_Allocated | Fully_Allocated | Refunded)
  ├── deposit_date
  └── notes

DepositAllocation
  ├── deposit_id
  ├── invoice_id
  ├── allocated_amount
  └── allocated_date
```

### 6.5.4.2 Workflow

```text
1. Customer pays deposit → CustomerDeposit(status = Unallocated)
   Accounting: Dr Cash, Cr Customer Deposits (Liability)

2. Service/Job completed → Invoice generated for full amount

3. Invoice payment → select [Apply Deposit]
   → System shows available deposits for this customer
   → Select deposit amount
   → DepositAllocation created
   → Invoice balance = Total - Allocated deposits - Other payments
   → Deposit status updates

4. Service cancelled:
   → Calculate cancellation fee (configurable %)
   → Refund net amount to customer
   → Status = Refunded
   Accounting: Dr Customer Deposits, Cr Cash / Cr Cancellation Fee Income
```

### 6.5.4.3 Default Deposit Rules by Business Type

| Business Type | Typical Deposit | Cancellation Fee |
|---|---|---|
| Repair Shop | 50% of estimate | 10% of deposit |
| Travel Agency | 25–50% of booking | Sliding scale by days before travel |
| Photo Studio | 30% of package | 15% of deposit (if within 7 days) |
| Event Management | 50% upfront | 25% if cancelled within 14 days |

---

# 6.6 Inventory Management

## 6.6.1 Stock Operations

### 6.6.1.1 Goods Received Note (GRN)

Two types:

| GRN Type | Behavior |
|---|---|
| Standard GRN | Receipt of owned stock from supplier |
| Consignment GRN | Receipt of consignment stock (no payment, separate tracking) |

GRN process:
```text
1. Select Purchase Order (optional — if PO exists, pre-populate)
2. Enter or scan items: product, qty_received, batch/serial if tracked
3. If PO-linked: system checks ordered_qty vs received_qty
4. If qty > ordered_qty: over-delivery tolerance check
5. If qty < ordered_qty: partial delivery — remaining qty tracked as pending
6. System updates inventory
7. Accounting:
   Standard: Dr Inventory, Cr Goods Received Pending Invoice (GRPI)
   Consignment: Dr Consignment Stock (off-balance-sheet), No liability
```

### 6.6.1.2 Stock Transfer (Inter-Branch)

```text
StockTransfer
  ├── transfer_number (ST-YYYYMM-NNNN)
  ├── source_warehouse_id → Branch
  ├── destination_warehouse_id → Branch
  ├── requested_by / approved_by
  ├── status (Draft | Pending_Approval | Dispatched | In_Transit | Received | Cancelled)
  ├── dispatch_date / received_date
  └── notes

StockTransferItem
  ├── transfer_id
  ├── product_id / variant_id
  ├── batch_id (if tracked)
  ├── serial_numbers (JSON array)
  ├── quantity
  ├── unit_cost
  └── received_quantity (set on receipt — may differ due to damage)
```

Transfer with damage:
- `quantity` = 100 dispatched, `received_quantity` = 98
- Source deducted 100, receiver adds 98
- 2 units → written off to transfer loss account

### 6.6.1.3 Stock Adjustment

| Type | Description | Approval Required |
|---|---|---|
| Positive (+) | Stock found more than system | Store Keeper + Manager |
| Negative (−) | Stock lost / short | Store Keeper + Manager |
| Damage | Goods damaged in storage | Store Keeper |
| Theft | Goods missing (suspected theft) | Manager + Owner |
| Expiry | Expired goods disposal | Store Keeper |

Adjustment accounting:
- Debit/Credit: Inventory (variance amount)
- Contra: COGS (variance)

### 6.6.1.4 Stock Return (to Supplier)

- Return goods to supplier
- Linked to original GRN or Purchase Invoice
- Generates Debit Note
- Accounting:
  - Accounts Payable decreases
  - Inventory decreases
- Restocking fee (if applicable) deducted from return amount

### 6.6.1.5 Damaged Stock Recording

- Dedicated transaction type for damaged goods
- Requires: product, qty, damage reason (photo attachment optional)
- Status: Pending_Review → Approved / Rejected
- Approved: stock deducted, loss recorded
- Accounting: Dr Damaged Goods Expense, Cr Inventory

## 6.6.2 Physical Stock Count

### 6.6.2.1 Workflow

```text
1. Create Stock Count Session
   → Select location/branch
   → Select products (all / category / zone)
   → Status: Draft

2. Count Phase
   → Counters enter actual quantities
   → Products can be frozen during count (no sales/adjustments)
   → Status: In_Progress

3. Variance Review
   → System Qty vs Counted Qty calculated
   → Small variance (≤ 1%): Auto-approve
   → Medium variance (1–5%): Manager approval
   → Large variance (> 5%): Investigation + Owner approval
   → Status: Under_Review

4. Post Adjustments
   → Approved variances → auto-generate Stock Adjustment records
   → Accounting entries posted
   → Status: Completed
```

### 6.6.2.2 Reports

- Count Sheet (pre-populated with system quantities, blank for count entry)
- Variance Report (by product, by value, by %)
- Count Accuracy Trend (period-over-period comparison)

## 6.6.3 Inventory Tracking

### Stock Metrics

```text
Current Stock    = Physical stock on hand
Reserved Stock   = Stock allocated to open orders / held bills
Available Stock  = Current Stock − Reserved Stock (what can be sold)
Pending Receipt  = Stock on order but not yet received (PO − GRN)
```

### Stock Status Indicators

| Status | Condition | Color |
|---|---|---|
| In Stock | Available > Reorder Point | Green |
| Low Stock | Available ≤ Reorder Point | Yellow |
| Critical | Available ≤ Safety Stock | Orange |
| Out of Stock | Available = 0 | Red |
| Overstock | Available > Max Stock Level | Blue |

## 6.6.4 Consignment Stock (see 5.3.4)

## 6.6.5 Batch/Expiry/Serial (see 5.4.7–5.4.9)

---

# 6.7 Point of Sale (POS)

## 6.7.1 Sales Features

### 6.7.1.1 Barcode Scanning

- Support for USB barcode scanners (keyboard wedge mode)
- Supports EAN-13, UPC-A, CODE-128, CODE-39, QR (for product lookup)
- Scan speed: recognition in < 500ms
- Invalid barcode: visual and audio feedback
- Multiple barcodes per product (supplier + own)

### 6.7.1.2 Product Search

- Search by: SKU, barcode, product name (partial), category
- Real-time search as user types (debounced 300ms)
- Results prioritized: exact match > starts-with > contains
- Supports search in offline cache when server is unavailable

### 6.7.1.3 Variant Selection

- Scanning variant-group product → variant selection popup
- Dropdown/button selectors for each attribute (Color, Size, etc.)
- Current stock shown per variant
- Quick-select by scanning variant barcode

### 6.7.1.4 Touch Screen Support

- Large touch-friendly buttons (minimum 48×48dp)
- Numeric keypad for quantity/price entry
- Swipe gestures for hold/resume/void line items
- Landscape orientation optimized

## 6.7.2 Discounts

### 6.7.2.1 Discount Types

| Type | Scope | Example |
|---|---|---|
| Percentage | Line item or invoice total | 10% off |
| Fixed Amount | Line item or invoice total | LKR 500 off |
| Promotional | Line item (auto-applied) | Happy hour 15% off |
| Volume | Line item (auto-applied) | 5% off for qty ≥ 50 |

### 6.7.2.2 Discount Application Sequence

```text
Gross Price
  → Promotional Discount (auto, if applicable)
  → Volume Discount (auto, if qty threshold met)
  → Line-level Manual Discount (percentage or fixed)
  → Subtotal
  → Invoice-level Discount (% or fixed)
  → Taxable Amount
  → VAT (18%)
  → Net Payable
```

### 6.7.2.3 Discount Approval Tiers

| Discount | Approver | System Behavior |
|---|---|---|
| ≤ 10% | Cashier (self-service) | Applied immediately |
| 10–25% | Manager | Requires Manager PIN or card swipe |
| > 25% | Owner | Requires Owner approval code |
| Sell below cost | Owner + override permission | Warning displayed; reason mandatory |

### 6.7.2.4 Cannot Sell Below Cost

- System blocks any transaction where selling price < cost price
- Exception: user with `ALLOW_SELL_BELOW_COST` permission
- Override reason is mandatory and logged

## 6.7.3 Payment Methods

### Supported Methods

| Method | Online | Offline |
|---|---|---|
| Cash | ✅ | ✅ |
| Credit Card | ✅ | ✅ (swipe, record reference) |
| Debit Card | ✅ | ✅ |
| Bank Transfer | ✅ | ❌ |
| QR Payments (LankaQR) | ✅ | ❌ |
| Customer Deposit | ✅ | ✅ |
| Loyalty Points | ✅ | ✅ |
| Multi-Currency (future) | ❌ | ❌ |

### 6.7.3.1 Split Payments

- Invoice can be paid using multiple payment methods
- Example: LKR 5,000 cash + LKR 12,000 card + 500 points
- Each split recorded as separate payment line
- Remaining balance must equal zero before invoice finalizes

### 6.7.3.2 Customer Deposit Application

At payment screen:
- System auto-detects unallocated deposits for the customer
- Shows: "Available Deposits: LKR 5,000 — [Apply]"
- Apply deposit → reduces amount due
- Remaining balance paid via other methods

## 6.7.4 POS Operations

### 6.7.4.1 Hold / Resume Bill

- Hold: saves current cart as held bill (status = Held)
- All held bills visible under "Held Bills" screen
- Resume: select held bill → cart restored
- Held bills auto-release after configurable timeout (default: end of day)
- Stock deducted at hold time (reserved) → released if bill is cancelled

### 6.7.4.2 Refund Processing

| Refund Type | Description |
|---|---|
| Full Refund | Entire invoice reversed (qty match required) |
| Partial Refund | Select line items to refund |
| Exchange | Refund + new sale in one transaction |

Return Validation Rules:

| Business Type | Return Window | Restocking Fee |
|---|---|---|
| General Retail | 7 days | 0% |
| Electronics | 14 days | 10% (opened) |
| Perishables | No returns | N/A |
| Custom/MTO | No returns (unless defective) | Up to 25% |
| Service | Before delivery | Varies per contract |

Tax Handling on Returns:
- Same tax period: Credit Note adjusts VAT
- Cross-period: Manual VAT adjustment required
- Full return → full VAT reversal
- Partial return → proportional VAT reversal

Accounting Impact:

| Scenario | Debit | Credit |
|---|---|---|
| Return to stock | Inventory (COGS reversal) | Sales Returns (P&L) |
| Cash refund | Customer Payable / Cash | Sales Returns |
| Restocking fee | Cash | Other Income |

### 6.7.4.3 Reprint Receipts

- Reprint any invoice from history
- Search by invoice number, date, customer
- Reprint reason logged (customer request, lost receipt, printer error)
- Reprint shows "REPRINT" watermark (configurable)

## 6.7.5 Offline Mode

- When server heartbeat fails → auto-switch to offline mode
- Full POS functionality maintained
- Transactions queued locally in SQLite
- Offline invoice numbers: `OFF-{CLIENT_ID}-{DATE}-{SEQ}`
- Status indicator bar at top of POS: "● OFFLINE — Queue: 12"
- Queue management screen: view pending, retry failed, cancel stuck items
- On reconnect: auto-sync prompt, manual or auto-sync

## 6.7.6 Pricing Resolution

At POS, the system resolves sale price in this order:

```text
1. Customer-specific Contract/Price List (if active)
2. Customer Category default Price List
3. System default Price List
4. Product Price Tier (based on customer category mapping)
5. Manual override (requires permission + reason)
```

---

# 6.8 Invoice Management

## 6.8.1 Document Types

| Type | Description | Tax Implication |
|---|---|---|
| Sales Invoice | Standard sale of goods | Output VAT (if applicable) |
| Service Invoice | Service fee invoice | Output VAT (if applicable) |
| Tax Invoice | Full VAT-compliant invoice | VAT mandatory fields |
| Credit Note | Reduce invoice (return/discount) | Reduces VAT liability |
| Debit Note | Increase invoice (additional charge) | Increases VAT liability |
| Quotation | Price quote (non-binding) | No tax impact |
| Delivery Note | Goods delivery document | No tax impact |
| Receipt | Payment acknowledgement | No tax impact |
| Purchase Invoice | Supplier invoice for goods/services | Input VAT (if applicable) |
| Purchase Order | Order to supplier | No tax impact |

## 6.8.2 Tax Invoice Requirements (VAT-Compliant)

A Tax Invoice must contain:

```text
1. Header: "TAX INVOICE" in bold
2. Supplier: Name, Address, TIN (VAT Registration No.)
3. Customer: Name, Address, TIN (if registered)
4. Invoice number: unique, sequential
5. Invoice date
6. Line items:
   - Description, Quantity, Unit Price
   - Taxable Value (price excl. VAT)
   - VAT Rate (18%)
   - VAT Amount
   - Line Total (incl. VAT)
7. Total Taxable Value
8. Total VAT (sum of per-line VAT)
9. Total Amount (incl. VAT, rounded to nearest cent)
10. QR Code (containing invoice reference, seller registration, total, digital signature)
```

## 6.8.3 Invoice Numbering

| Mode | Format | Example |
|---|---|---|
| Online | INV-{YYYYMMDD}-{NNNN} | INV-20260401-0042 |
| Offline (before sync) | OFF-{CLIENT_ID}-{YYYYMMDD}-{NNNN} | OFF-C3-20260401-0015 |
| Offline (after sync) | INV-{YYYYMMDD}-{NNNN} (server reassigns) | INV-20260402-0057 |

On sync, the server assigns a permanent sequential number. Both the temporary offline number and the permanent number are retained on the invoice record for cross-reference.

## 6.8.4 Returns & Credit Notes

### 6.8.4.1 Return Windows

| Business Type | Default Window | Configurable |
|---|---|---|
| General Retail | 7 days | Yes |
| Electronics | 14 days | Yes |
| Perishables | No returns | N/A |
| Custom/MTO | No returns (unless defective) | Per agreement |
| Service | Before service delivery | Per contract |

### 6.8.4.2 Restocking Fees

| Product Category | Restocking Fee |
|---|---|
| General | 0% |
| Electronics (sealed) | 0% |
| Electronics (opened) | 10% |
| Custom products | Up to 25% (per product config) |

### 6.8.4.3 Return-to-Stock vs Refund

```text
Return Requested
  → (Approval if outside window / above value threshold)
  → Goods Inspected (Store Keeper)
    → Pass: Item Restocked OR Damaged (if customer-damaged, separate workflow)
    → Fail: Rejected (sent back to customer)
  → Refund Approved (Accountant / Manager)
  → Payment processed

If credit invoice: Credit Note issued → offsets customer balance
If cash refund: Receipt issued → cash drawer deduction
```

### 6.8.4.4 Tax Treatment of Returns

| Scenario | VAT Treatment |
|---|---|
| Return within same tax period | Credit Note adjusts output VAT |
| Cross-period return | Manual VAT adjustment required |
| Full return | Full VAT reversal |
| Partial return | Proportional VAT reversal |
| Restocking fee | Not subject to VAT (administrative fee) |

### 6.8.4.5 Loyalty Point Clawback

- Points earned on returned invoice are deducted from customer's balance
- If points already redeemed, cash-equivalent deducted from refund amount
- Points enter negative balance until covered by future earnings

### 6.8.4.6 Accounting Impact of Returns

| Scenario | Debit | Credit |
|---|---|---|
| Return to stock | Inventory (COGS reversal) | Sales Returns (P&L) |
| Cash refund | Sales Returns | Cash |
| Credit Note | Sales Returns | Accounts Receivable |
| Restocking fee income | Cash | Other Income |

---

# 6.9 Purchasing Management

## 6.9.1 Purchasing Workflow

```text
Purchase Request → Approval → Purchase Order → GRN → Supplier Invoice → Payment
```

### Purchase Request (PR)

- Created by Store Keeper / Manager
- Items: product, requested qty, suggested supplier, required date
- Auto-suggestion from reorder report (see 5.4.10.3)
- Approval: Manager or Owner (depending on total value)

### Purchase Order (PO)

- Created from PR or directly
- Status: Draft → Approved → Sent → Partially_Received → Fully_Received → Closed
- Sent to supplier (print/email)
- Supplier confirmation entry (order number, confirmed delivery date)
- Valid until date (PO expires if not received by this date)

## 6.9.2 3-Way Matching

### 6.9.2.1 Matching Rules

```text
Match Condition: PO Qty ≟ GRN Qty ≟ Invoice Qty
                 PO Price ≟ Invoice Price

| Scenario | PO | GRN | Invoice | Action |
|---|---|---|---|---|
| Exact match | 100 | 100 | 100 | Auto-approve for payment |
| Under-delivered (partial) | 100 | 70 | 70 | Approve partial delivery |
| Over-delivered (within tolerance) | 100 | 105 | 105 | Auto-approve |
| Over-delivered (exceeds tolerance) | 100 | 120 | 120 | Block — Manager approval or return |
| Short-delivered but invoiced full | 100 | 80 | 100 | Block — dispute invoice |
| Price diff (within tolerance) | LKR 100 | — | LKR 102 | Auto-approve |
| Price diff (exceeds tolerance) | LKR 100 | — | LKR 120 | Block — Debit Note or approval |
```

### 6.9.2.2 Tolerance Configuration

```text
Over-delivery tolerance:  10%
Under-delivery tolerance:  5%
Price tolerance:           5%

Action on over-tolerance:
  □ Block entirely
  ☑ Flag for review only
  □ Auto-adjust to received qty
```

### 6.9.2.3 Partial Receipt Tracking

- Single PO can have multiple GRNs
- System tracks: `ordered_qty` | `received_qty` | `pending_qty`
- PO auto-closes when `total_received_qty ≥ ordered_qty`
- Remaining balance can be cancelled (PO line adjustment)

### 6.9.2.4 Discrepancy Resolution

```text
Qty Mismatch:
  → If supplier invoiced for goods not received: Debit Note
  → If goods received but not invoiced: track in GRPI (Goods Received Pending Invoice)

Price Mismatch:
  → If invoiced price > PO price AND within tolerance: accept at invoiced price
  → If invoiced price > PO price AND exceeds tolerance: Debit Note to supplier
  → If invoiced price < PO price: Credit Note from supplier
```

### 6.9.2.5 Accounting Impact

| Step | Debit | Credit |
|---|---|---|
| GRN | Inventory | GRPI (Goods Received Pending Invoice) |
| Invoice matched | GRPI | Accounts Payable |
| Price variance | Inventory Variance (P&L) | Inventory (adjustment) |
| Payment | Accounts Payable | Cash |

## 6.9.3 Consignment Purchasing (see 5.3.4)

## 6.9.4 Supplier Comparison & Purchase History

- Compare suppliers by price for the same product
- Track lead time per supplier (avg days PO→GRN)
- Quality tracking (return rate per supplier)
- Preferred supplier ranking

## 6.9.5 Cost Tracking

- Per-product cost history (by batch, by GRN)
- Landed cost = purchase price + freight + import duties + insurance
- Weighted average cost or FIFO cost calculation
- Cost trend chart

---

# 6.10 Finance & Accounting

## 6.10.1 General Ledger

### 6.10.1.1 Chart of Accounts

Pre-configured Sri Lanka standard COA with these major categories:

| Code Range | Category | Examples |
|---|---|---|
| 1000–1999 | Assets | Cash, Bank, AR, Inventory, Fixed Assets |
| 2000–2999 | Liabilities | AP, VAT Payable, WHT Payable, Loans |
| 3000–3999 | Equity | Owner's Capital, Retained Earnings |
| 4000–4999 | Income | Sales Revenue, Service Revenue, Other Income |
| 5000–5999 | COGS | Cost of Goods Sold, Freight, Purchases |
| 6000–6999 | Expenses | Rent, Salaries, Utilities, Marketing |

Special accounts:

```text
Customer Deposits (Liability)    — 2100
Loyalty Liability                — 2101
GRPI (Goods Received Pending Inv)— 1500
Consignment Liability (off-BS)   — 2999 (memo account)
Inventory Variance               — 5100
Damaged Goods Expense            — 6100
```

### 6.10.1.2 Journal Entries

- Double-entry accounting enforced
- Every transaction generates minimum 2 entries
- Manual journal entry for adjustments
- Searchable by date, account, amount, reference
- Approval workflow for manual entries (Manager + Accountant)

## 6.10.2 Accounts Receivable

- Customer balances tracked per invoice
- Credit sales automatically create AR entries
- Payment allocation: pay specific invoice(s) or oldest first
- Aging reports: 0–30, 31–60, 61–90, 90+ days
- Dunning/reminder letters (print or email)
- Overdue escalation (see 5.2.5 for blocking rules)

## 6.10.3 Accounts Payable

- Supplier balances tracked per purchase invoice
- Payment scheduling (based on payment terms)
- Payment allocation to specific invoices
- Aging reports
- Payment approval workflow (value-based thresholds)

## 6.10.4 Banking

- Multiple bank accounts
- Deposits, withdrawals, transfers
- Cheque management (issue date, clearing date, status)
- Bank reconciliation (match system transactions to bank statement)
- Reconciliation report with uncleared items

## 6.10.5 Cash Management

### 6.10.5.1 Cashbook

- Daily cash register per POS terminal
- Opening balance, sales, expenses, payouts, closing balance
- Cash count verification (expected vs actual)
- Cash short/over tracking

### 6.10.5.2 Petty Cash

- Petty cash fund management
- Expenses recorded against petty cash
- Top-up requests (approval workflow)
- Monthly reconciliation

## 6.10.6 Accounting Treatment of Special Transactions

### 6.10.6.1 Returns & Refunds

(see 5.8.4.6)

### 6.10.6.2 Loyalty Points

(see 5.2.8.4)

### 6.10.6.3 Customer Deposits

| Event | Debit | Credit |
|---|---|---|
| Deposit received | Cash | Customer Deposits (Liability) |
| Deposit applied to invoice | Customer Deposits | AR / Revenue |
| Deposit refunded | Customer Deposits | Cash |
| Cancellation fee retained | Customer Deposits | Cancellation Fee Income |

### 6.10.6.4 Consignment Stock

| Event | Debit | Credit |
|---|---|---|
| Goods received (consignment) | Memo: Consignment Stock | No entry (off-BS) |
| Goods sold (consignment) | Cash | Consignment Payable + Commission Income |
| Settlement to supplier | Consignment Payable | Cash |
| Goods returned unsold | Memo: Consignment Stock reduced | No entry |

### 6.10.6.5 Stock Adjustments & Variances

| Event | Debit | Credit |
|---|---|---|
| Positive adjustment | Inventory | Inventory Variance / COGS reversal |
| Negative adjustment | Inventory Variance / COGS | Inventory |
| Damaged stock write-off | Damaged Goods Expense | Inventory |
| Stock count variance (< 1%) | Inventory Variance | Inventory (or vice versa) |

### 6.10.6.6 3-Way Match Variances

(see 5.9.2.5)

---

# 6.11 Tax Management (Sri Lanka)

## 6.11.1 Tax Configuration Panel

Centralized configuration screen:

```text
++ Tax Types ++
[VAT]         Active: ON    Rate: 18%     Default: true
[SSCL]        Active: OFF   Rate: 2.5%    Default: false
[NBT]         Active: OFF   Rate: 2%      Default: false (legacy)

++ WHT Rates ++
| Payment Type         | Rate | Threshold  | Active |
| Rent (Land/Building) | 10%  | 50,000.00  | YES    |
| Rent (Machinery)     | 5%   | 50,000.00  | YES    |
| Interest             | 5%   | 5,000.00   | YES    |
| Dividends            | 15%  | 0.00       | YES    |
| Royalties            | 14%  | 0.00       | YES    |
| Service Fees (Res)   | 5%   | 50,000.00  | YES    |
| Director Fees        | 10%  | 0.00       | YES    |

++ Thresholds ++
VAT Registration:        60,000,000.00
SSCL Turnover:           60,000,000.00
e-Invoice Mandate:      100,000,000.00

++ Payroll ++
EPF Employee Rate: 8%
EPF Employer Rate: 12%
ETF Rate: 3%

APIT Slabs:
  0 - 100,000         : 0%
  100,001 - 150,000   : 8%
  150,001 - 200,000   : 14%
  200,001 - 250,000   : 20%
  Above 250,000       : 24%
```

All changes to tax configuration are logged (old value, new value, user, timestamp).

## 6.11.2 VAT (Value Added Tax)

### 6.11.2.1 VAT Rate

- Standard rate: 18% (configurable)
- Applied on taxable value (after discounts, before rounding)
- Tax rates, thresholds, exempt categories, and filing rules must be effective-dated because Sri Lankan tax rules change over time.

### 6.11.2.2 Registration Threshold

- LKR 60M annual turnover or LKR 15M per quarter (configurable, effective-dated)
- System auto-monitors: if trailing 12-month turnover exceeds threshold:
  - Flag: "VAT registration may be required"
  - Enable VAT collection once registration is confirmed

### 6.11.2.3 Auto-Enable VAT

```text
IF business_taxable_supplies > threshold (trailing 12 months or quarter) OR registration_voluntary:
  → Enable VAT on invoices
  → Generate Tax Invoice instead of standard invoice
  → Auto-calculate 18% on taxable supplies
```

### 6.11.2.4 Exempt Supplies (No VAT)

- Basic food items (rice, milk, vegetables, fruits, eggs)
- Agricultural products (unprocessed)
- Medical services (hospital, clinic)
- Educational services
- Financial services (insurance, loans)
- Residential rent
- Passenger transport

### 6.11.2.5 Zero-Rated Supplies (0% VAT, Input Credits Allowed)

- Exported goods
- Exported services

### 6.11.2.6 Input VAT Credit Rules

- Available only on purchases directly related to taxable supplies
- Blocked input credit on:
  - Motor vehicles (except qualifying commercial vehicles)
  - Entertainment expenses
  - Medical/educational expenses
- Capital goods: input credit claimed over 3 years (proportionate)

### 6.11.2.7 VAT on Imports

- 18% on CIF value + customs duty + other levies
- Recoverable as input VAT if registered

## 6.11.3 SSCL (Social Security Contribution Levy)

### 6.11.3.1 Rate

- 2.5% when active (configurable rate + toggle ON/OFF)
- Applied on liable turnover according to the configured SSCL category rules

### 6.11.3.2 Threshold

- LKR 60M annual turnover or LKR 15M per quarter (configurable, effective-dated)
- Auto-collection triggered when threshold exceeded

### 6.11.3.3 Exempt Categories

- Mirrors VAT exempt categories (configurable list)

### 6.11.3.4 SSCL + VAT Stacking

```text
Taxable Value = Line Total − Discounts = LKR 1,000.00
  + SSCL @ 2.5%        = LKR 25.00
  + VAT @ 18%          = LKR 184.50
  Total Tax Burden     = LKR 209.50
  Net Payable          = LKR 1,209.50
```

## 6.11.4 NBT (Legacy — Abolished)

- NBT was abolished in 2022, replaced by SSCL
- System provides toggle ON/OFF for legacy reporting only
- NBT data from historical periods can be re-printed
- No new NBT collection

## 6.11.5 Withholding Tax (WHT)

### 6.11.5.1 Rates & Thresholds

Configurable per payment type (see 5.11.1 for defaults).

### 6.11.5.2 WHT Calculation at Payment

```text
WHEN payment_type IN (rent, interest, dividend, royalty, service_fee):
  IF payment_amount > threshold:
    withheld_amount = payment_amount × rate
    net_payment = payment_amount − withheld_amount
```

Accounting at payment:

```text
Debit:  Expense (full amount)
Credit: Cash (net payment)
Credit: WHT Payable (withheld amount)
```

### 6.11.5.3 T-10 Certificate Generation

- Generated per supplier per payment
- Contains: supplier TIN, name, payment type, amount, WHT rate, WHT amount, period
- Batch printing available at month end

### 6.11.5.4 Filing Data

- Monthly return data export
- Annual statement data export (March 31 deadline)
- WHT schedule per supplier per month

## 6.11.6 e-Invoicing (In Progress — IRD Digitalization)

### 6.11.6.1 QR Code on Tax Invoices

- QR code encodes: invoice number, seller TIN, total amount, verification URL, digital signature
- Generated at time of invoice creation
- Embedded in printed/PDF invoice

### 6.11.6.2 IRD Schema Transformation

- Pluggable adapter pattern (core system unchanged when IRD updates schema)
- Adapter converts internal invoice to IRD XML/JSON format
- Supports multiple schema versions

### 6.11.6.3 Submission to IRD Gateway

```text
Real-time mode:
  Invoice → eInvoice Adapter → transform to IRD format
  → submit to IRD API → receive validation token/ID
  → store IRD reference ID on invoice record
  → print invoice with IRD reference

Queue mode (when IRD gateway is unavailable):
  → Queue invoice for later submission
  → Retry with exponential backoff
  → Flag failed submissions for manual retry
```

### 6.11.6.4 IRD Validation Token Storage

- IRD reference ID saved on each invoice
- Resubmission logic for failed transmissions
- Status tracking: PENDING_SUBMISSION | SUBMITTED | VALIDATED | FAILED

## 6.11.7 Tax Reports

| Report | Content | Period |
|---|---|---|
| **VAT Summary** | Output VAT, Input VAT, Net Payable, Input Carry Forward | Monthly |
| **VAT Detail** | Per invoice: no, date, customer, taxable value, VAT, rate | Monthly |
| **SSCL Summary** | Total SSCL collected vs paid | Monthly |
| **WHT Summary** | Per supplier: TIN, name, amount, rate, WHT | Monthly / Annual |
| **APIT Schedule** | Per employee: NIC, name, gross, EPF, APIT | Monthly |
| **EPF/ETF Return** | Per employee + totals | Monthly |
| **Tax Collected vs Paid** | Output vs input reconciliation | Monthly |
| **Tax Liability** | Net tax due per period | Monthly |
| **e-Invoice Status** | Invoices submitted, pending, failed | On demand |

---

# 6.12 Expense Management

## 6.12.1 Expense Categories

| Category | Description |
|---|---|
| Rent | Building/equipment rental |
| Utilities | Electricity, water, internet, phone |
| Salaries | Employee wages and benefits |
| Fuel | Vehicle and generator fuel |
| Maintenance | Equipment and building repairs |
| Marketing | Advertising, promotions |
| Travel | Business travel and accommodation |
| Office Supplies | Stationery, consumables |
| Professional Fees | Legal, accounting, consultancy |
| Insurance | Business insurance premiums |
| Miscellaneous | Other expenses |

## 6.12.2 Expense Approvals

- Expense entry → Approval workflow based on amount
- Thresholds (configurable):
  - ≤ LKR 10,000: Manager auto-approve
  - LKR 10,000–100,000: Manager approval
  - > LKR 100,000: Owner approval
- Rejection reason required if denied

## 6.12.3 Receipt Attachments

- Each expense can have one or more receipt images/PDFs
- Accepted formats: JPG, PNG, PDF (max 5MB each)
- Receipts stored on server file system with encrypted filenames
- Receipt preview from expense list screen

## 6.12.4 Expense Reporting

- Expense by category (monthly/yearly)
- Expense vs budget (if budget configured per category)
- Expense trend (month-over-month comparison)
- Expense by approver/cost center

---

# 6.13 Payroll Management (Optional)

## 6.13.1 Employee Records

- Employee ID (auto)
- Name, NIC, address, phone
- Date of birth, date of hire
- Department / designation
- Bank account details
- EPF number, ETF number
- Tax code (APIT declaration)
- Status: Active / Suspended / Terminated

## 6.13.2 Salary Structures

- Basic salary
- Allowances (housing, transport, medical)
- Deductions (loan repayment, advances)
- Overtime rate (1.0x / 1.5x / 2.0x of hourly rate)
- Frequency: Monthly

## 6.13.3 EPF / ETF

### 6.13.3.1 Contribution Rates

| Fund | Employee | Employer | Total |
|---|---|---|---|
| EPF | 8% of basic | 12% of basic | 20% |
| ETF | — | 3% of basic | 3% |

### 6.13.3.2 Calculation Sequence

```text
GROSS SALARY = Basic + Allowances
  − EPF Employee (8% of basic)
  − APIT (PAYE)
  = NET SALARY (paid to employee)

EMPLOYER COST = GROSS SALARY + EPF Employer (12%) + ETF (3%)
```

### 6.13.3.3 Monthly Return

- Per-employee contribution breakdown
- Consolidated totals for submission
- EPF/ETF return form printout

## 6.13.4 APIT (PAYE)

### 6.13.4.1 Tax Slabs

Configurable slabs (defaults per current SL tax law):

```text
Monthly taxable income:
  First LKR 100,000        → 0%
  LKR 100,001 – 150,000    → 8%
  LKR 150,001 – 200,000    → 14%
  LKR 200,001 – 250,000    → 20%
  Above LKR 250,000        → 24%
```

### 6.13.4.2 Monthly Deduction

- Calculated on taxable income = Gross salary − EPF employee contribution
- Cumulative monthly calculation (accounting for deductions to date)
- Tax-free threshold: LKR 100,000/month (1,200,000/year)

### 6.13.4.3 Annual Reconciliation

- End-of-year: compare total APIT deducted vs annual tax liability
- Balance payable by employee or refund due
- Generate annual tax certificate (similar to T-10 for employees)

## 6.13.5 Leave Tracking

| Leave Type | Allocation (Days/Year) | Carry Forward |
|---|---|---|
| Annual | 14 | Max 7 days |
| Sick | 21 | No |
| Casual | 7 | No |
| Maternity | 84 (per SL law) | N/A |
| Paternity | 5 | No |

- Leave request → approval workflow
- Balance tracking per employee
- Encashment calculation for unused annual leave (on termination)

## 6.13.6 Overtime Tracking

- Daily overtime entry (by employee, date, hours, rate)
- Overtime approval by Manager
- Overtime paid in next payroll cycle
- Overtime cap: max 2 hours/day, 10 hours/week (configurable)

---

# 6.14 Asset Management

## 6.14.1 Asset Categories

| Category | Examples |
|---|---|
| Computers & IT | Laptops, desktops, printers, servers |
| Furniture & Fixtures | Desks, chairs, shelves, counters |
| Vehicles | Delivery vans, company cars |
| Machinery | Manufacturing/repair equipment, kitchen equipment |
| Office Equipment | AC units, CCTV, phone systems |

## 6.14.2 Asset Register

```text
Asset
  ├── asset_code (auto: AST-YYYY-NNNN)
  ├── name
  ├── category
  ├── purchase_date
  ├── purchase_cost
  ├── supplier_id
  ├── invoice_reference
  ├── location (branch / department)
  ├── custodian_id → Employee
  ├── useful_life_years
  ├── depreciation_method (StraightLine | ReducingBalance)
  ├── depreciation_rate
  ├── residual_value
  ├── current_book_value
  ├── status (Active | Under_Maintenance | Disposed | Sold)
  └── disposal_date / disposal_value
```

## 6.14.3 Asset Depreciation

- Straight-line: `depreciation = (cost − residual) / useful_life_years`
- Reducing balance: `depreciation = book_value × rate`
- Monthly depreciation journal entry auto-generated
- Accumulated depreciation tracked per asset
- Partial-year depreciation for mid-year acquisitions

## 6.14.4 Asset Maintenance

```text
AssetMaintenance
  ├── asset_id
  ├── maintenance_date
  ├── maintenance_type (Routine | Repair | Inspection)
  ├── description
  ├── cost
  ├── vendor (external) OR performed_by (internal)
  ├── next_scheduled_date
  └── notes
```

- Scheduled maintenance reminders
- Maintenance cost history per asset
- Total maintenance cost vs asset value report

---

# 6.15 Industry Extensions

## 6.15.1 Repair Shops

(see 5.5.2 for full job card lifecycle)

Additional features:

- **Repair Tickets**: Simplified walk-in ticket (phone/device model + issue)
- **Warranty Management**: Warranty period tracking per serial number; warranty claims linked to job cards; manufacturer warranty vs store warranty distinction
- **Parts Catalog**: Common repair parts with compatibility by brand/model

## 6.15.2 Restaurants

### 6.15.2.1 Table Layout

```text
TableLayout
  ├── name ("Main Hall", "VIP Room", "Outdoor")
  └── capacity

Tables
  ├── table_number
  ├── seating_capacity
  ├── status (Available | Occupied | Reserved | Cleaning)
  ├── current_waiter_id → User
  └── current_occupants
```

### 6.15.2.2 KOT (Kitchen Order Ticket)

```text
1. Waiter opens table → set occupant count + waiter assignment
2. Items ordered → each item added to order
3. KOT auto-generated when items "sent to kitchen"
   → Food items → Kitchen printer A
   → Beverages → Bar printer B
4. Multiple KOTs per order (starters → mains → desserts)
5. KOT items individually marked as Served
6. KOT completed only when ALL items served
```

### 6.15.2.3 Bill Splitting

| Method | Behavior |
|---|---|
| Equal split | Total ÷ N (equal per diner) |
| By item | Each diner pays specific items |
| Custom amount | Manual amounts summing to total |
| Pro-rata SC/VAT | Service charge and VAT applied proportionally |

### 6.15.2.4 Recipe → Inventory Link

```text
MenuItemRecipe
  ├── menu_item_id
  ├── product_id (ingredient)
  ├── quantity (in stock UOM)
  └── waste_percent (allowed variance)

When menu item is ordered:
  → Deduct each ingredient from inventory
  → Example: "Chicken Curry": Chicken 200g, Curry powder 10g, Oil 15ml
```

## 6.15.3 Educational Institutes

### 6.15.3.1 Student & Course Management

```text
Student
  ├── student_number
  ├── name / NIC / DOB
  ├── guardian_name / guardian_contact / guardian_nic
  ├── enrollment_date
  └── status (Active | Suspended | Graduated | Withdrawn)

Course
  ├── course_code / course_name
  ├── duration_weeks / fee / max_students
  ├── start_date / end_date
  ├── instructor_id → User
  └── status (Upcoming | Ongoing | Completed | Cancelled)

CourseEnrollment
  ├── student_id / course_id
  ├── fee_paid / fee_balance
  ├── payment_plan (Lump_Sum | Monthly | Per_Session)
  ├── attendance_count
  └── status (Enrolled | In_Progress | Completed | Dropped)

CourseInstallment
  ├── enrollment_id / installment_number
  ├── due_date / amount
  ├── paid_date / paid_amount
  └── status (Pending | Paid | Overdue)
```

### 6.15.3.2 Attendance Tracking

```text
CourseSession → date, start/end time, topic, instructor
CourseAttendance → session_id, student_id, status (Present/Absent/Late), late_minutes
```

### 6.15.3.3 Fee Reminders

- Auto-generate notification (SMS/print) X days before installment due
- Overdue fees flagged in student profile
- Payment plans supported (monthly / per-session)

## 6.15.4 Travel Agencies

### 6.15.4.1 Booking Management

```text
TravelBooking
  ├── booking_number
  ├── customer_id
  ├── booking_type (Flight | Hotel | Tour | Transport | Visa | Combo)
  ├── travel_date_from / travel_date_to
  ├── status (Pending_Confirmation | Confirmed | In_Progress | Completed | Cancelled)
  ├── supplier_id
  ├── supplier_reference
  ├── total_amount / cost_price / commission_amount
  ├── payment_status
  └── notes

TravelBookingPassenger
  ├── booking_id / passenger_name
  ├── passport_number / nationality / DOB
  └── special_requests
```

### 6.15.4.2 Commission Tracking

- Each booking: sell_price − cost_price = commission
- Commission % configurable per supplier
- Commission payout tracking (received from supplier vs pending)

### 6.15.4.3 Cancellation Rules

```text
> 30 days before travel: 100% refund (minus admin fee)
15–30 days:              75% refund
7–14 days:               50% refund
< 7 days:                No refund

Supplier cancellation fee (non-refundable portion) applied first.
```

## 6.15.5 Photo Studios

```text
PhotoBooking
  ├── booking_number / customer_id
  ├── event_type (Wedding | Portrait | Event | Product | Passport)
  ├── event_date / location / duration_hours
  ├── status (Booked | Confirmed | Shoot_Completed | Editing | Delivered | Cancelled)
  ├── photographer_id → User
  ├── package_id → Service Product (service + deliverables)
  └── total_amount / deposit_id

PhotoAlbum
  ├── booking_id / album_name
  ├── album_type (Digital | Printed | Both)
  ├── total_photos / selected_photos / pages
  ├── delivery_date
  └── status (Pending_Selection | In_Design | Approved | Printing | Delivered)

PhotoAddOn
  ├── booking_id
  ├── add_on_type (Extra_Hour | Additional_Album | Digital_Copies | Framed_Print)
  ├── product_id → Service Product
  ├── qty / price
```

## 6.15.6 Courier Services

```text
Parcel
  ├── tracking_number (auto: C-YYYYMMDD-NNNN)
  ├── sender_name / sender_address / sender_phone
  ├── recipient_name / recipient_address / recipient_phone
  ├── parcel_type (Document | Small_Packet | Box | Pallet)
  ├── weight (kg) / dimensions (cm)
  ├── declared_value
  ├── service_type (Standard | Express | Overnight | Same_Day)
  ├── shipping_cost
  ├── payment_type (Sender_Pays | Recipient_Pays | COD)
  ├── cod_amount
  ├── status (Collected | In_Transit | Out_For_Delivery | Delivered | Failed | Returned)
  ├── current_location
  ├── estimated_delivery_date / actual_delivery_date
  ├── receiver_name (who signed)
  └── signature (future: image capture)

ParcelTrackingEvent
  ├── parcel_id / timestamp / location / status / handler_id / remarks

ParcelDeliveryAttempt
  ├── parcel_id / attempt_number / attempt_date
  ├── result (Successful | No_Answer | Address_Not_Found | Refused)
  └── next_attempt_date
```

COD workflow:

```text
1. Parcel created with payment_type = COD, cod_amount set
2. Delivery completed → driver collects COD amount
3. System creates pending COD settlement for the driver
4. Driver deposits cash at end of day
5. COD reconciled against delivery manifest
6. COD remittance to sender (minus courier fee)
```

---

# 6.16 Reporting & Analytics

## 6.16.1 Sales Reports

| Report | Description | Filters |
|---|---|---|
| Daily Sales | Summary of all sales for a day | Date, branch, cashier |
| Monthly Sales | Aggregate by month | Month, product category |
| Product Sales | Sales volume and value by product | Date range, category, brand |
| Service Sales | Sales volume by service type | Date range, technician |
| Sales Trend | Daily/weekly/monthly trend chart | Rolling period |
| Sales by Payment Method | Cash vs card vs other split | Date range |
| Hourly Sales | Sales distribution by hour of day | Date range |

## 6.16.2 Inventory Reports

| Report | Description |
|---|---|
| **Stock Valuation** | Owned stock value + consignment stock value (segregated) |
| **Stock Valuation (consignment)** | Consignment stock value by supplier |
| **Reorder Report** | Products at/below reorder point with suggested order qty |
| **Expiry Report** | Products expiring in N days with value at risk |
| **Dead Stock Report** | Products with zero sales in N days (default: 180), with suggested action |
| **Stock Turnover Ratio** | COGS ÷ Average Inventory |
| **Days of Inventory Outstanding** | 365 ÷ Turnover Ratio |
| **Inventory Accuracy %** | (Products with matched count ÷ Total Products) × 100 |
| **Shrinkage %** | (System Stock − Physical Stock) ÷ System Stock × 100 |
| **Gross Margin ROI** | (Revenue − COGS) ÷ Avg Inventory Cost |

## 6.16.3 Financial Reports

| Report | Description |
|---|---|
| Profit & Loss | Revenue − COGS − Expenses = Net Profit (period) |
| Balance Sheet | Assets = Liabilities + Equity (as of date) |
| Cash Flow Statement | Operating / Investing / Financing cash flows |
| Trial Balance | All GL account balances |
| General Ledger Detail | All transactions for an account or account range |
| Account Aging | AR and AP aging schedules |

All financial reports support: period selection, branch filter, comparison with prior period.

## 6.16.4 Customer Reports

| Report | Description |
|---|---|
| Outstanding Balances | All customers with unpaid balances |
| Customer Aging | AR aging by customer |
| Top Customers | Ranked by revenue / visit frequency |
| Customer Purchase History | Per-customer detailed purchase log |
| New Customers | Customers registered in a period |

## 6.16.5 Supplier Reports

| Report | Description |
|---|---|
| Outstanding Payables | All suppliers with unpaid invoices |
| Supplier Aging | AP aging by supplier |
| Purchase History | Per-supplier purchase log |
| Supplier Performance | Lead time, return rate, pricing comparison |

## 6.16.6 Tax Reports

(see 5.11.7)

## 6.16.7 PII-Safe Report Export

- Standard CSV/PDF exports auto-mask PII fields
- Full-data export requires `EXPORT_PII` permission (logged in PII Access Audit)
- Masked formats:
  - Name: "John D." (first name + last initial)
  - Phone: "077 XXX XXXX"
  - NIC: "XXXXXX123V" (last 4 digits only)
  - Email: "j***@domain.com"

---

# 6.17 Dashboard

## 6.17.1 Business KPIs

| KPI | Calculation | Refresh |
|---|---|---|
| Today's Sales | Sum of today's invoice totals | Real-time |
| Monthly Revenue | Month-to-date invoice total | Real-time |
| Gross Profit (MTD) | Revenue − COGS | Real-time |
| Net Profit (MTD) | Gross Profit − Expenses | Daily |
| Outstanding Receivables | Total unpaid customer balances | Real-time |
| Outstanding Payables | Total unpaid supplier invoices | Real-time |
| Low Stock Items | Count of products below reorder point | Real-time |
| Today's Transactions | Count of invoices today | Real-time |

## 6.17.2 Visual Components

- Daily sales line chart (last 30 days)
- Revenue by category (pie/donut chart)
- Top 10 products (bar chart)
- Receivables aging (stacked bar)
- Cash flow (last 12 months, line chart)
- Inventory health gauge (green/yellow/red)

## 6.17.3 Trend Analysis

- Month-over-month growth %
- Year-over-year comparison
- Moving average (7-day / 30-day)
- Forecast based on historical trend

---

# 6.18 Audit & Activity Logging

## 6.18.1 Audit Log

Every Create, Update, Delete operation on critical data is logged:

```text
AuditLog
  ├── entity_type (Product, Customer, Invoice, etc.)
  ├── entity_id
  ├── action (CREATE | UPDATE | DELETE)
  ├── user_id
  ├── timestamp
  ├── changed_fields (JSON — {"field": {"old": "X", "new": "Y"}})
  └── ip_address / client_id
```

## 6.18.2 Activity Log

Non-data events:

```text
ActivityLog
  ├── event_type (LOGIN | LOGOUT | REPORT_ACCESS | EXPORT | PRINT | CONFIG_CHANGE)
  ├── user_id
  ├── timestamp
  ├── details (e.g., "Exported VAT Report for April 2026")
  └── ip_address / client_id
```

## 6.18.3 PII Access Log

Every view of sensitive PII data:

```text
PIIDataAccess
  ├── accessed_by (user_id)
  ├── accessed_at
  ├── customer_id
  ├── access_type (VIEW | EDIT | EXPORT | DELETE)
  ├── fields_accessed (JSON — "name, phone, nic")
  ├── purpose (POS_TRANSACTION | REPORTING | DATA_CLEANUP)
  └── ip_address
```

## 6.18.4 Tax Configuration Change Log

All changes to tax settings are logged separately:

```text
TaxConfigLog
  ├── changed_by
  ├── changed_at
  ├── setting_name (VAT_RATE | WHT_RENT_RATE | EPF_EMPLOYEE_RATE, etc.)
  ├── old_value
  ├── new_value
  └── reason
```

---

# 6.19 Backup & Restore

## 6.19.1 Manual Backup

- One-click backup via admin panel
- Backs up: PostgreSQL database, uploaded documents, configuration
- Encrypted backup file (AES-256)
- User selects target: local server path, network share, or external drive

## 6.19.2 Scheduled Backup

| Frequency | Default | Retention |
|---|---|---|
| Daily | 02:00 AM | 7 days |
| Weekly | Sunday 03:00 AM | 4 weeks |
| Monthly | 1st of month 03:00 AM | 12 months |

- Configurable schedule per business
- Email notification on success/failure

## 6.19.3 Backup Targets

- Local Storage (server hard drive)
- External Storage (USB drive, NAS)
- Network Storage (SMB/CIFS share)

Multiple targets supported (e.g., local + NAS simultaneously).

## 6.19.4 Full Restore

- Select backup file → Full Restore
- System goes offline during restore
- All data replaced with backup contents
- Integrity check before restore (checksum verification)
- Restore log generated

## 6.19.5 Selective Restore

- Restore specific tables or date range
- Table-level: e.g., restore only products and customers
- Date-range: e.g., restore transactions for a specific day that was corrupted

## 6.19.6 Encrypted Backup

- All backups encrypted with AES-256
- Encryption key stored separately (key management system)
- Restore requires encryption key
- Key backup recovery process documented

---

# 6.20 Document Management

## 6.20.1 Supported Documents

| Category | Examples |
|---|---|
| Invoices | Sales invoices, purchase invoices, credit notes |
| Receipts | Payment receipts, expense receipts |
| Agreements | Supplier contracts, customer contracts, lease agreements |
| Customer Files | NIC copies, registration forms |
| Images | Product photos, asset photos |

## 6.20.2 Features

- Upload: drag-and-drop or file browser; single or batch upload
- Search: by filename, tags, customer/supplier name, date range
- Preview: inline viewer for PDFs and images; thumbnail gallery
- Archive: move to archive folder (soft-deleted); searchable in archive
- Versioning: replace document with newer version (old kept for audit)

---

# 6.21 Notification Center

## 6.21.1 Alert Types

| Alert | Trigger | Priority |
|---|---|---|
| Low Stock | Stock ≤ reorder point | Medium |
| Expiring Products | Product expiry ≤ 30 days | Medium |
| Overdue Invoices | Invoice overdue ≥ 7 days | High |
| Failed Backup | Backup did not complete | Critical |
| Large Expense | Expense > LKR 100,000 (configured threshold) | Medium |
| Stock Count Due | No count in last 30 days (configured interval) | Low |
| VAT Filing Due | Within 5 days of filing deadline | High |
| WHT Filing Due | Within 5 days of filing deadline | High |
| EPF/ETF Filing Due | Within 5 days of filing deadline | High |
| e-Invoice Sync Failed | Unsent e-invoices > 10 | High |

## 6.21.2 Notification Channels

| Channel | Status |
|---|---|
| In-app (notification bell icon) | Available |
| Email | Phase 2 |
| SMS | Phase 2 |
| WhatsApp | Phase 2 |

## 6.21.3 Configurable Alert Thresholds

Each alert type has configurable thresholds accessible from Settings:

```text
Low Stock Alert:
  □ Enable
  Threshold: [Reorder Point]

Expiry Alert:
  □ Enable
  Threshold: [30] days

Overdue Invoice Alert:
  □ Enable
  Threshold: [7] days overdue

Backup Failure:
  □ Enable  (always critical when enabled)

Large Expense Alert:
  □ Enable
  Threshold: LKR [100,000.00]
```

---

# 6.22 Data Privacy (PDPA Compliance)

## 6.22.1 Consent Management

(see 5.2.3)

## 6.22.2 Right to Erasure / Anonymization

(see 5.2.4)

## 6.22.3 PII Access Controls

(see 4.3)

## 6.22.4 Data Retention Schedules

| Data Type | Retention Required | Legal Basis |
|---|---|---|
| Sales Invoices | 6 years from end of assessment year | Inland Revenue Act |
| Purchase Invoices | 6 years | Inland Revenue Act |
| Employee Records | 6 years post-employment | Employment Law |
| Tax Records | 6 years | Inland Revenue Act |
| Customer NIC Copies | Business relationship + 6 months | Anti-Money Laundering |
| Marketing Consent | Until withdrawn + 1 year | PDPA |

## 6.22.5 Data Breach Detection & Notification

| Detection Method | Trigger | Action |
|---|---|---|
| Failed login monitoring | > 5 failed attempts, same user | Lock account, notify Owner |
| Unusual export volume | > 100 customer exports in 1 hour | Alert admin |
| Off-hours PII access | PII view outside business hours | Flag for review |
| New device/IP login | Login from unrecognized device/network | Email notification |

Breach notification workflow (PDPA Art. 28):

```text
1. System detects potential breach → Alert Owner
2. Owner investigates: what data? how many records? authorized?
3. If breach likely to cause harm:
   a. Notify Data Protection Authority (within 72 hours)
   b. Notify affected data subjects
4. System provides:
   → Full audit log of affected data
   → List of affected customers
   → Timeline of events
   → Export for DPA submission
```

## 6.22.6 Privacy Policy Display

- On first login and annually thereafter
- User must acknowledge to proceed
- Acknowledgment logged

## 6.22.7 PII Encryption Standards

| Data State | Measure |
|---|---|
| At rest (database) | NIC, phone, email encrypted (AES-256 column-level) |
| In transit | TLS 1.3 |
| In backup | Encrypted backup, separate key |
| In log files | PII never in plain-text logs (masked or excluded) |
| On client (offline SQLite) | SQLCipher encrypted database |

---

# 7. Non-Functional Requirements

## 7.1 Performance

- Support 50+ concurrent users
- Invoice generation < 2 seconds
- Product search < 1 second
- Dashboard load < 3 seconds
- Report generation < 5 seconds (standard reports)
- Sync 100 offline transactions in < 10 seconds

## 7.2 Security

- Password hashing (BCrypt, cost 12)
- TLS 1.3 for client-server communication
- Column-level encryption for sensitive PII (AES-256)
- Role-based access control (screen + action level)
- Session timeout and management
- Audit trails for all data changes

## 7.3 Reliability

- Automatic scheduled backups
- Data recovery mechanisms (full + selective)
- Heartbeat monitoring for server health
- Offline POS queue ensures zero data loss during outages
- Transaction atomicity (all-or-nothing)

## 7.4 Scalability

- Up to 100 concurrent users
- Millions of transactions
- Multi-branch without architecture redesign
- Horizontal scaling: add more clients without server changes

## 7.5 Maintainability

- Modular architecture (separate modules per business domain)
- Plugin support for industry extensions
- Flyway database migrations
- Structured logging (Logback)

## 7.6 Offline Resilience

- SQLite local store per client
- Transaction queue with exponential backoff retry
- Three-phase sync protocol (Pull → Push → Acknowledge)
- Conflict resolution rules for price/stock/customer data
- Client registration and heartbeat monitoring

## 7.7 Data Privacy

- PDPA compliance (Act No. 9 of 2022)
- Consent management and right to erasure
- PII access controls and audit logging
- Data retention schedules enforced
- Breach detection and notification capability

---

# 8. Recommended Technology Stack

## 8.1 Frontend

- JavaFX (UI Framework)
- ControlsFX (Enhanced JavaFX Components)
- JFoenix (Material Design for JavaFX — optional)
- SQLite (via JDBC) — offline cache
- SQLCipher — encrypted local database
- ZXing ("Zebra Crossing") — barcode/QR generation
- Apache PDFBox — PDF generation (receipts, invoices)

## 8.2 Backend

- Spring Boot 3.x (REST API)
- Spring Security (Authentication + RBAC)
- Spring Data JPA / Hibernate (ORM)
- Flyway (Database Migration)
- Logback (Logging)
- Jackson (JSON processing)
- Spring Mail (Email notifications — Phase 2)

## 8.3 Database

- PostgreSQL 18.x recommended / PostgreSQL 16+ supported (Server - production database)
- SQLite + SQLCipher (Client - offline cache)
- Testcontainers with PostgreSQL for integration tests; H2 only for fast unit tests that do not validate SQL dialect behavior

## 8.4 Reporting

- JasperReports (PDF, CSV, HTML reports)
- Apache POI (Excel export)

## 8.5 Security

- Spring Security
- BCrypt (password hashing)
- AES-256 (column-level encryption)
- TLS 1.3

## 8.6 Developer Tools

- Maven / Gradle
- JUnit 5 + Mockito (Testing)
- Git (Version Control)
- Docker (Optional — containerized deployment)

---

# 9. Future Enhancements

## 9.1 Phase 2

- Mobile Application (Android/iOS — companion app for reporting, basic POS)
- SMS Integration (customer notifications, marketing)
- WhatsApp Invoice Sharing
- Email notifications

## 9.2 Phase 3

- Customer Portal (view invoices, make payments, track jobs)
- Supplier Portal (submit invoices, view PO status)
- E-Commerce Integration (online store sync with inventory)

## 9.3 Phase 4

- AI Sales Forecasting (predict demand per product)
- Inventory Forecasting (auto-optimize reorder points)
- Business Intelligence Engine (advanced analytics, trend detection)
- Automated anomaly detection in transactions

---

# 10. UI/UX Guidelines

## 10.1 Navigation Structure

```text
Main Navigation (Left Sidebar):
├── Dashboard
├── POS (Point of Sale)
├── Sales
│   ├── Invoices
│   ├── Credit Notes
│   ├── Quotations
│   └── Receipts
├── Customers
├── Products
├── Inventory
│   ├── Stock on Hand
│   ├── GRN
│   ├── Stock Adjustments
│   └── Stock Count
├── Services
│   ├── Service Catalog
│   ├── Appointments
│   ├── Job Cards
│   └── Technician Calendar
├── Purchasing
│   ├── Purchase Orders
│   ├── Suppliers
│   └── GRN
├── Finance
│   ├── Accounts Receivable
│   ├── Accounts Payable
│   └── Expenses
├── Reports
├── Settings
│   ├── Business Profile
│   ├── Tax Configuration
│   ├── User Management
│   └── System Settings
└── Help
```

## 10.2 Screen Layout Standards

- Left sidebar: 240px width, collapsible
- Top bar: 48px height, contains logo, search, notifications, user menu
- Content area: fluid width, min 960px
- Forms: two-column layout for desktop, single column for POS
- Tables: sortable columns, pagination (25/50/100 per page)
- Modals: centered, max-width 600px for forms, 900px for details

## 10.3 Color Scheme

| Element | Color | Usage |
|---|---|---|
| Primary | #1976D2 | Buttons, links, active states |
| Secondary | #424242 | Text, icons |
| Success | #4CAF50 | Positive indicators, in-stock |
| Warning | #FF9800 | Low stock, warnings |
| Error | #F44336 | Errors, out-of-stock |
| Background | #F5F5F5 | Page background |
| Surface | #FFFFFF | Cards, dialogs |

## 10.4 Typography

- Font family: System default (Segoe UI on Windows, SF Pro on macOS, Ubuntu on Linux)
- Base font size: 14px
- H1: 24px bold
- H2: 20px semibold
- H3: 16px semibold
- Body: 14px regular
- Small: 12px regular
- Monospace: 13px (for codes, amounts)

## 10.5 Responsive Behavior

- Minimum supported width: 1024px
- POS screen: optimized for 1920×1080 landscape
- Sidebar collapses to icons at < 1200px
- Touch targets: minimum 48×48dp

## 10.6 Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| F1 | Help |
| F2 | Focus product search (POS) |
| F3 | Focus customer search (POS) |
| F4 | Hold bill |
| F8 | Payment screen |
| F9 | Complete sale |
| F12 | Void item |
| Ctrl+N | New record |
| Ctrl+S | Save |
| Ctrl+P | Print |
| Ctrl+F | Search |
| Escape | Close modal / Cancel |

---

# 11. Error Handling Strategy

## 11.1 Error Categories

| Category | Example | User Action |
|---|---|---|
| Validation Error | Missing required field, invalid format | Inline message below field |
| Business Rule Error | Sell below cost, credit limit exceeded | Dialog with explanation + override option |
| Network Error | Server unreachable, timeout | Retry button, offline mode indicator |
| Database Error | Constraint violation, connection lost | Error dialog, contact admin |
| Permission Error | Insufficient role permissions | "Access Denied" message |

## 11.2 Error Message Format

```text
[Error Icon] Error Title
Description of what went wrong.
Suggested action to fix it.
```

## 11.3 Error Logging

- All errors logged with: timestamp, user, module, error message, stack trace
- Critical errors: logged to file + displayed to admin
- Non-critical: logged to file only
- Log rotation: daily, 30-day retention

## 11.4 Graceful Degradation

- Server unreachable → POS continues in offline mode
- Printer offline → Show preview, allow save as PDF
- Database connection lost → Queue operations, retry on restore

---

# 12. Logging Strategy

## 12.1 Log Levels

| Level | Usage |
|---|---|
| ERROR | System errors, exceptions |
| WARN | Degraded functionality, recoverable issues |
| INFO | Business events (invoice created, payment received) |
| DEBUG | Detailed tracing (API calls, SQL queries) |
| TRACE | Ultra-detailed (variable values, method entry/exit) |

## 12.2 Log Categories

| Category | Content | Retention |
|---|---|---|
| Application | Business logic events | 90 days |
| Security | Login attempts, permission denials | 1 year |
| Audit | Data changes (who, what, when) | 7 years |
| Performance | Slow queries, API response times | 30 days |
| System | Server startup/shutdown, config changes | 90 days |

## 12.3 Log Format

```text
[2026-04-01 10:30:45.123] [INFO] [POS] [user:john] [client:C3] Invoice INV-20260401-0042 created - Total: LKR 5,900.00
```

Format: `[timestamp] [level] [module] [user] [client] message`

---

# 13. Deployment Architecture

## 13.1 Server Installation

```text
1. Install JDK 17+ on server
2. Install PostgreSQL 18.x recommended / PostgreSQL 16+ supported on server
3. Create database: bizco
4. Run Flyway migrations
5. Configure application.properties
6. Start Spring Boot application (jar or Windows service)
7. Open firewall ports 8888/8443
```

## 13.2 Client Installation

```text
1. Install JDK 17+ on client machine
2. Copy JavaFX application JAR + lib folder
3. Configure server IP in config.properties
4. Run application
5. First launch: client registers with server
```

## 13.3 Configuration Files

```text
Server:
├── application.properties (DB config, port, TLS)
├── logging.properties (log levels, rotation)
└── backup.properties (schedule, paths)

Client:
├── config.properties (server IP, client ID)
└── logging.properties (log levels)
```

## 13.4 Database Migrations

- Flyway manages all schema changes
- Migrations run automatically on server startup
- Versioned: V1__init.sql, V2__add_loyalty.sql, etc.
- Rollback scripts provided for each migration

---

# 14. Testing Strategy

## 14.1 Test Types

| Type | Scope | Tools | Coverage Target |
|---|---|---|---|
| Unit Tests | Service layer, business logic | JUnit 5, Mockito | 80% line coverage |
| Integration Tests | API endpoints, DB operations | Spring Boot Test, Testcontainers | All endpoints |
| UI Tests | JavaFX screens | TestFX | Critical paths |
| End-to-End | Full workflows | Manual + scripted | All user stories |

## 14.2 Test Data

- Seed data script for demo/test environment
- Test customers, products, invoices pre-loaded
- Separate test database (H2 or PostgreSQL test schema)

## 14.3 Acceptance Testing

- Each module tested against SRS acceptance criteria
- UAT with actual SME business owner (beta testing)
- Bug tracking via GitHub Issues

---

# 15. Timezone & Currency

## 15.1 Timezone

- System timezone: Asia/Colombo (UTC+5:30)
- All timestamps stored in UTC, displayed in local timezone
- Configurable per-branch (for future multi-branch)

## 15.2 Currency

- Currency: Sri Lankan Rupee (LKR / Rs.)
- Format: Rs. 1,234,567.89
- Thousand separator: comma
- Decimal separator: period
- Negative amounts: (1,234.56) or -1,234.56
- All amounts stored as DECIMAL(15,2)

---

# 16. Data Validation Rules

## 16.1 Field-Level Validation

| Field | Rule | Error Message |
|---|---|---|
| Phone | 10 digits, starts with 0 | "Phone must be 10 digits starting with 0" |
| Email | Valid email format | "Invalid email address" |
| NIC | 12 digits or 9 digits + V/X | "Invalid NIC format" |
| TIN | 12 digits | "TIN must be 12 digits" |
| Barcode | EAN-13 (12+1 check digit) | "Invalid barcode" |
| SKU | Alphanumeric, 3-20 chars | "SKU must be 3-20 alphanumeric characters" |
| Quantity | Positive integer or decimal | "Quantity must be positive" |
| Price | Non-negative decimal | "Price cannot be negative" |
| Discount % | 0-100 | "Discount must be between 0% and 100%" |
| VAT Rate | 0-100 | "VAT rate must be between 0% and 100%" |

## 16.2 Business Rule Validation

| Rule | Condition | Action |
|---|---|---|
| Unique SKU | New product SKU exists | Reject with error |
| Unique Barcode | New barcode exists | Reject with error |
| Unique Invoice # | Invoice number exists | Auto-generate next |
| Stock Not Negative | Sale qty > available | Block or warn |
| Credit Limit | Balance + new sale > limit | Block or warn |
| Return Window | Return date > invoice date + window | Block or require approval |
| Discount Approval | Discount > tier limit | Require approval |

---

# 17. Search Functionality

## 17.1 Global Search

- Accessible from top bar (Ctrl+F)
- Searches across: Products, Customers, Invoices, Suppliers
- Results grouped by entity type
- Minimum 2 characters to trigger

## 17.2 Module-Specific Search

### Product Search (POS)
- Real-time as user types (debounced 300ms)
- Fields: SKU, barcode, name, category
- Prioritization: exact > starts-with > contains

### Customer Search
- Fields: code, name, phone, email
- Partial match supported
- Results limited to 20 for performance

### Invoice Search
- Fields: invoice number, customer name, date range
- Filter by: status (paid/unpaid), date range, amount range

## 17.3 Search Performance

- Indexed columns for all search fields
- Results in < 1 second for datasets up to 100,000 records
- Pagination for large result sets

---

# 18. Data Migration Strategy

## 18.1 Import Sources

| Source | Format | Data |
|---|---|---|
| Excel/CSV | Template provided | Products, Customers, Suppliers |
| Existing POS | Custom adapter | Products, Customers, Transaction history |
| QuickBooks | CSV export | Chart of Accounts, Customers, Products |

## 18.2 Import Process

```text
1. User downloads template (CSV/Excel)
2. Fills in data
3. Uploads file
4. System validates:
   - Required fields present
   - Data types correct
   - No duplicates
   - References valid (e.g., category exists)
5. Validation report shown (errors + warnings)
6. User confirms import
7. Data imported with audit trail
```

## 18.3 Import Rules

- Products: SKU or Name used as dedup key
- Customers: Phone or NIC used as dedup key
- Existing records updated if dedup key matches (configurable: skip/update/ask)
- All imports logged with source file, user, timestamp

---

# 19. Versioning & Upgrade Path

## 19.1 Version Numbering

- Format: MAJOR.MINOR.PATCH (e.g., 1.2.3)
- MAJOR: breaking changes, major features
- MINOR: new features, backward compatible
- PATCH: bug fixes

## 19.2 Database Migrations

- Flyway manages all schema changes
- Each release includes migration scripts
- Backward-compatible migrations preferred
- Rollback scripts for each migration

## 19.3 Upgrade Process

```text
1. Backup current database
2. Stop server
3. Replace application JAR
4. Start server (Flyway runs migrations automatically)
5. Verify version in admin panel
```

---

# Appendix A — Business Rules Summary

## A.1 Discount Approval Tiers

| Discount Range | Approver | Policy |
|---|---|---|
| ≤ 10% | Cashier (self) | Applied immediately |
| 10–25% | Manager | Requires Manager PIN |
| > 25% | Owner | Requires Owner approval code |
| Below cost | Owner + permission | Mandatory reason, logged |

## A.2 Return Windows by Business Type

| Business Type | Window | Restocking Fee |
|---|---|---|
| General Retail | 7 days | 0% |
| Electronics | 14 days | 10% (opened) |
| Perishables | No returns | N/A |
| Custom/MTO | Defects only | Up to 25% |
| Service | Before delivery | Per contract |

## A.3 Credit Limit Enforcement by Aging

| Days Outstanding | Action |
|---|---|
| 0–30 | Normal |
| 31–60 | Warning at POS |
| 61–90 | Cash-only for credit sales |
| 91+ | Block all sales, escalate |

## A.4 Loyalty Points Earning & Redemption

- Earn: 1 point per LKR 100 spent
- Redeem: 1 point = LKR 1
- Max redemption: 50% of invoice
- Expiry: 12 months
- Clawback on returns

## A.5 Price Resolution Order

```text
1. Customer-specific contract / price list
2. Customer category default
3. System default price list
4. Product price tier (by customer category)
5. Manual override (with permission)
```

## A.6 3-Way Match Tolerance

| Parameter | Default |
|---|---|
| Over-delivery tolerance | 10% |
| Under-delivery tolerance | 5% |
| Price tolerance | 5% |

---

# Appendix B — Accounting Treatment Matrix

## B.1 Sales, Returns, Discounts

| Event | Debit | Credit |
|---|---|---|
| Cash sale | Cash | Sales Revenue + Output VAT |
| Credit sale | Accounts Receivable | Sales Revenue + Output VAT |
| Cash received (AR) | Cash | Accounts Receivable |
| Sales return (to stock) | Inventory (COGS reversal) | Returns & Allowances |
| Cash refund | Returns & Allowances | Cash |
| Credit Note issued | Returns & Allowances | Accounts Receivable |
| Restocking fee | Cash | Other Income |
| Discount given | Discount Expense | Revenue (reduces net sales) |

## B.2 Purchases, GRN, Invoice Matching

| Event | Debit | Credit |
|---|---|---|
| GRN (goods received) | Inventory | GRPI (Goods Received Pending Invoice) |
| Supplier invoice received | GRPI | Accounts Payable |
| Payment made | Accounts Payable | Cash |
| Price variance (invoice > PO) | Inventory Variance | Inventory (adjustment) |
| Debit Note to supplier | Accounts Payable | Inventory |

## B.3 Loyalty Points

| Event | Debit | Credit |
|---|---|---|
| Points earned (est. redemption cost) | Marketing Expense | Loyalty Liability |
| Points redeemed | Loyalty Liability | Revenue (discount) |
| Points expired | Loyalty Liability | Other Income |

## B.4 Customer Deposits

| Event | Debit | Credit |
|---|---|---|
| Deposit received | Cash | Customer Deposits (Liability) |
| Deposit applied to invoice | Customer Deposits | AR / Revenue |
| Deposit refunded | Customer Deposits | Cash |
| Cancellation fee earned | Customer Deposits | Cancellation Fee Income |

## B.5 Consignment Stock

| Event | Debit | Credit |
|---|---|---|
| Goods received | Memo: Consignment Stock | No entry (off-BS) |
| Goods sold | Cash | Consignment Payable + Commission Income |
| Settlement to supplier | Consignment Payable | Cash |
| Unsold return | Memo: Consignment Stock reduction | No entry |

## B.6 Stock Adjustments & Counts

| Event | Debit | Credit |
|---|---|---|
| Positive stock adjustment | Inventory | Inventory Variance / COGS reversal |
| Negative stock adjustment | Inventory Variance / COGS | Inventory |
| Damaged stock write-off | Damaged Goods Expense | Inventory |
| Theft write-off | Theft Loss Expense | Inventory |
| Stock count variance (< 1%) | Inventory Variance | Inventory (or reverse) |

---

# Appendix C — Tax Configuration Defaults

## C.1 VAT

| Parameter | Default Value | Configurable |
|---|---|---|
| Rate | 18% | Yes |
| Registration threshold | LKR 60,000,000 or LKR 15,000,000 per quarter | Yes |
| Filing frequency | Monthly | No (per SL law) |
| Filing deadline | 20th of following month | No |
| Rounding | Round to nearest cent | No |

## C.2 SSCL

| Parameter | Default Value | Configurable |
|---|---|---|
| Rate | 2.5% | Yes |
| Active | OFF | Yes (toggle) |
| Turnover threshold | LKR 60,000,000 or LKR 15,000,000 per quarter | Yes |

## C.3 WHT

| Payment Type | Rate | Threshold |
|---|---|---|
| Rent (Land/Building) | 10% | LKR 50,000 |
| Rent (Machinery/Plant) | 5% | LKR 50,000 |
| Interest | 5% | LKR 5,000 |
| Dividends | 15% | 0 |
| Royalties | 14% | 0 |
| Service Fees (Resident) | 5% | LKR 50,000 |
| Service Fees (Non-Resident) | 14% | 0 |
| Director Fees | 10% | 0 |

All configurable.

## C.4 EPF/ETF Rates

| Contribution | Rate | Payer |
|---|---|---|
| EPF Employee | 8% | Employee |
| EPF Employer | 12% | Employer |
| ETF Employer | 3% | Employer |

## C.5 APIT (PAYE) Slabs

| Monthly Income Range | Rate |
|---|---|
| LKR 0 – 100,000 | 0% |
| LKR 100,001 – 150,000 | 8% |
| LKR 150,001 – 200,000 | 14% |
| LKR 200,001 – 250,000 | 20% |
| Above LKR 250,000 | 24% |

---

# Appendix D — PDPA Compliance Checklist

| Requirement | Implemented | Section Reference |
|---|---|---|
| Consent capture at data collection | ✅ | 5.2.3 |
| Consent withdrawal mechanism | ✅ | 5.2.3 |
| Right to access personal data | ✅ | 5.2.1 (Customer Profile Export) |
| Right to erasure (anonymization) | ✅ | 5.2.4 |
| Data retention schedules enforced | ✅ | 5.22.4 |
| PII access controls (role-based) | ✅ | 4.3 |
| PII access audit logging | ✅ | 5.18.3 |
| Data breach detection | ✅ | 5.22.5 |
| Data breach notification workflow | ✅ | 5.22.5 |
| Encryption at rest (AES-256) | ✅ | 5.22.7 |
| Encryption in transit (TLS 1.3) | ✅ | 5.22.7 |
| Privacy policy display & acknowledgement | ✅ | 5.22.6 |
| PII-safe report export | ✅ | 5.16.7 |

---

# Appendix E — Key Success Factors

The system should:

- Be simple enough for small businesses
- Be powerful enough for growing SMEs
- Require minimal training
- Work fully offline within a LAN environment
- Continue POS operations when the server is unavailable
- Support Sri Lankan tax and accounting practices
- Scale without requiring system redesign
- Protect customer privacy per Sri Lanka PDPA

---

# Appendix F — Acceptance Criteria (Per Module)

## F.1 Authentication & Security

1. User can log in with valid username and password
2. User is redirected to appropriate dashboard based on role
3. Invalid credentials display error after 3 attempts
4. Account locks after 5 failed attempts
5. Session times out after configured idle period
6. Password change forces re-login

## F.2 Customer Management

1. Customer can be created with minimum fields (name, phone)
2. Customer search returns results in < 1 second
3. Credit limit enforcement blocks sales when exceeded
4. Loyalty points are calculated correctly per spend
5. Customer anonymization removes all PII fields within 1 second
6. Consent withdrawal stops marketing notifications immediately

## F.3 Product Management

1. Product can be created with SKU, barcode, name, price
2. Variant group product shows selection popup at POS
3. Variant barcode scan auto-selects correct variant
4. Batch-tracked product deductions follow FIFO by expiry
5. Serial-tracked product requires serial scan at POS
6. Expired products are blocked from sale
7. UOM conversion calculates correctly (e.g., CASE → PCS)
8. Combo product deducts all component stock on sale

## F.4 POS

1. Barcode scan adds product to cart in < 500ms
2. Discount percentage calculates correctly on line total
3. Discount approval prompts for Manager PIN above 10%
4. Sell-below-cost is blocked without override permission
5. Hold bill saves and restores cart correctly
6. Split payment with 3 methods completes correctly
7. Refund creates Credit Note and reverses stock
8. Offline mode queues transactions and syncs on reconnect
9. Offline invoice renumbered on sync
10. Price resolution follows defined priority order

## F.5 Inventory

1. GRN increases stock and creates correct accounting entries
2. Stock transfer decrements source and increments destination
3. Stock adjustment with approval workflow functions correctly
4. Consignment GRN does not create accounts payable
5. Consignment sale records commission correctly
6. Physical count session workflow completes correctly
7. Auto-reorder suggestions are generated daily
8. Batch/serial scanning at GRN creates correct records

## F.6 Tax

1. VAT is calculated at 18% on taxable supplies
2. Tax Invoices include all mandatory fields
3. SSCL is applied when toggled ON and threshold exceeded
4. WHT is calculated per payment type rules
5. VAT Summary report matches invoice totals
6. Tax configuration changes are logged
7. e-Invoice QR code is generated and verifiable

## F.7 Reporting & Dashboard

1. Daily Sales report shows correct totals for selected date
2. Profit & Loss statement balances (Revenue − COGS − Expenses = Net)
3. Stock Valuation report shows owned and consignment separately
4. Dashboard KPIs refresh in real-time
5. PII-safe export masks all sensitive fields

## F.8 Offline Resilience

1. POS continues operating when server is disconnected
2. Transactions are queued and visible in queue management
3. On reconnect, all queued transactions sync within 10 seconds
4. Conflict resolution (price change) is flagged for review
5. Client re-registers if client_id is missing

## F.9 Data Privacy

1. Customer creation requires consent acknowledgment
2. Customer anonymization removes all PII within 1 second
3. PII access without permission returns masked data
4. PII export without `EXPORT_PII` permission is blocked
5. Data retention schedule flags records past retention period

---

*(End of Document)*
