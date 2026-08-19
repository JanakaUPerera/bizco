# SME Business Management System (Bizco)

## Minimum Viable Product (MVP) Specification

**Version:** 1.4
**Based On:** SRS v2.2
**Target Platform:** Java Desktop Application (JavaFX)
**Architecture:** LAN-based client-server with optional single-PC deployment

**Revision Purpose:** Requirements consistency and implementation-readiness update. Version 1.3 preserves all MVP functionality from v1.2 and clarifies document lifecycles, credit sales, transaction immutability, stock reservation, supplier-payment allocation, session management, concurrency, tax snapshots, numbering, auditability, and realistic SME business scenarios. **Version 1.4 (Week 12 scope-expansion decision)** pulls Brands, Dynamic Attributes, Product Variants, Bill of Materials/Manufacturing, Packages/Bundles, Promotions & Discount Campaigns, Loyalty Points, and a richer Purchase Order + Goods Receipt purchasing flow (with a per-supplier product catalog) into MVP scope from SRS.md's already-specified vision (§6.2.8, §6.4.2–6.4.5, §6.9) plus a newly-added Bill of Materials section (SRS.md §6.4.11, absent from the prior SRS vision). This extends the approved delivery schedule from 20 to 31 weeks — see `DevelopmentPlan.md` §9 for the revised milestone plan. Full details of this decision are in the project's scope-decision record; see Section 1.2a below for what changed and why.

**Scope Protection Rule:** No requirement listed as IN the MVP in v1.2 is removed or deferred by this revision. Where an ambiguity exists, this document resolves the ambiguity while retaining the stronger MVP capability. Version 1.4 additionally never removes a v1.3 in-scope item — it only adds.

**Requirements Authority:** `SRS.md` defines the full product vision; this `MVP.md` defines the authoritative MVP functional scope; `DevelopmentPlan.md` defines the implementation schedule and engineering execution baseline.


---

# 1. MVP Scope Overview

The MVP delivers the two core facilities requested — **Invoicing** and **Event Scheduling** — along with the minimum supporting modules required to make them functional. Advanced features, offline resilience, multi-branch support, and industry-specific extensions are deferred to subsequent phases.

## 1.1 MVP Goals

- Enable a small business to create customers, manage products, generate invoices, and schedule service events
- Provide a working POS with barcode scanning, basic discounts, and multiple payment methods
- Deliver basic reporting for sales and scheduling
- Lay the architectural foundation (Spring Boot + JavaFX + PostgreSQL) for future expansion

## 1.2 What's IN the MVP

| Module | Scope |
|---|---|
| Authentication & Security | Login, password policy, action-based RBAC and audit trail |
| Customer Management | CRUD, basic search, credit limits, loyalty points (earn/redeem) |
| Product Management | CRUD, categories, brands, dynamic per-category attributes, product variants, pricing tiers, basic stock tracking |
| Bill of Materials / Manufacturing | Component recipes for assembled/manufactured products; production transaction consumes component stock |
| Supplier & Purchasing | Supplier CRUD, per-supplier product catalog, Purchase Orders, Goods Receipts (partial/multi-delivery, damaged/rejected qty), supplier payments, cost history, outstanding balances |
| Invoicing Facility | Sales invoices, service invoices, credit notes, receipts, tax invoice |
| Event Scheduling Facility | Appointments, calendar view, basic job cards, technician assignment |
| Point of Sale (POS) | Cart, barcode scan (variant-aware), discounts, promotions/coupons, split payment, hold/resume, package/bundle sale |
| Merchandising | Packages/bundles, rule-driven date-bound promotions and discount campaigns |
| Inventory (Basic) | Stock on hand (variant granularity), stock adjustments, low-stock alerts |
| Finance (Basic) | Cashbook, receivables, payables, daily cash closing |
| Tax (Basic) | VAT 18% calculation, tax invoice format |
| Reporting (Basic) | Daily sales, stock summary, appointment summary, plus variant/BOM/package/promotion/loyalty reports |
| Backup & Restore | Manual database backup, restore, verification and audit logging |

## 1.2a Version 1.4 Scope-Expansion Decision

Approved during Phase 5 (Week 12) after a comparative review against an alternative product/inventory/manufacturing data-model reference. Full record of what was compared and why is in the session that made this decision; the outcome is:

**Pulled into MVP scope from SRS.md's existing vision** (already specified there, previously deferred by MVP.md v1.3):

- Brands (SRS.md §6.4.4)
- Dynamic per-category attributes/specifications (SRS.md §6.4.2, generalizing the variant-attribute mechanism)
- Product variants (SRS.md §6.4.2) — the one addition that isn't purely additive; see §4.6 below
- Packages/bundles (SRS.md §6.4.3 "Combo/Bundle Products")
- Promotions, discount campaigns, price lists (SRS.md §6.4.5.6/6.4.5.7 "Promotional Pricing", "Volume Discounts")
- Loyalty points (SRS.md §6.2.8)
- Purchase Orders + a per-supplier product catalog (SRS.md §6.9.1/§6.9.4), replacing the single-step GRN model — **without** the 3-way-matching/tolerance-auto-approval/GRPI machinery in SRS.md §6.9.2, which depends on full double-entry GL and stays deferred (see §1.3)

**Newly specified, not previously in SRS.md's vision at all:**

- Bill of Materials / Manufacturing (new SRS.md §6.4.11) — Bizco had no production/assembly concept before this decision.

**Deliberately still deferred** (part of the reference model considered but not adopted): batch/serial tracking and expiry dates stay out of MVP — see §1.3. Full 3-way purchase matching and GRPI accounting stay out because they need full GL, also still deferred.

See `DevelopmentPlan.md` §9 for the revised phase/week plan (Phases 6–8 inserted, original Phase 5 grew by one week, total 20 → 31 weeks) and `DatabaseDesign.md` §§55–61 for the schema.

## 1.3 What's OUT of the MVP (Future Phases)

| Feature | Deferred To |
|---|---|
| Offline resilience (SQLite cache, sync) | Phase 2 |
| Multi-branch support | Phase 2 |
| Batch/serial tracking, expiry dates | Phase 2 |
| Consignment management | Phase 2 |
| Full double-entry accounting / GL | Phase 2 |
| Full purchase 3-way matching, tolerance auto-approval, GRPI accounting | Phase 2 |
| SSCL, WHT, e-Invoicing | Phase 2 |
| Payroll | Phase 2 |
| Asset management | Phase 3 |
| Advanced reporting & analytics | Phase 2 |
| Notification center (email/SMS) | Phase 2 |
| Document management | Phase 3 |
| Mobile app / customer portal | Phase 3 |
| Industry extensions (restaurant, travel, etc.) | Phase 3 |

---

# 2. User Roles & Access Control (MVP)

## 2.1 Permission Model (Action-Based)

Permissions follow the pattern: `module.action`

### MVP Permission List

#### User Management
| Permission | Description |
|---|---|
| `user.create` | Create new user accounts |
| `user.read` | View user list and details |
| `user.update` | Edit user information |
| `user.delete` | Deactivate/reactivate users |
| `user.grant_role` | Grant primary or secondary role |
| `user.revoke_role` | Revoke role from user |
| `user.reset_password` | Reset user password |
| `user.lock` | Lock user account |
| `user.unlock` | Unlock user account |

#### Role Management
| Permission | Description |
|---|---|
| `role.create` | Create new roles |
| `role.read` | View roles and permissions |
| `role.update` | Modify role permissions |
| `role.delete` | Delete custom roles |
| `role.assign_permission` | Add permissions to role |
| `role.remove_permission` | Remove permissions from role |

#### Customer Management
| Permission | Description |
|---|---|
| `customer.create` | Create customers |
| `customer.read` | View customers |
| `customer.update` | Edit customers |
| `customer.delete` | Anonymize/delete customers |
| `customer.view_pii` | View sensitive PII |
| `customer.export_pii` | Export customer data |

#### Product Management
| Permission | Description |
|---|---|
| `product.create` | Create products |
| `product.read` | View products |
| `product.update` | Edit products and pricing |
| `product.delete` | Deactivate products |
| `product.view_cost` | View cost price |

#### Invoice & POS
| Permission | Description |
|---|---|
| `invoice.create` | Create invoices |
| `invoice.read` | View invoices |
| `invoice.void` | Void invoices |
| `invoice.reprint` | Reprint receipts |
| `invoice.payment.create` | Record payments |
| `invoice.payment.refund` | Process refunds |
| `invoice.discount.apply` | Apply discounts ≤10% |
| `invoice.discount.approve_25` | Approve discounts 10–25% |
| `invoice.discount.approve_50` | Approve discounts >25% |
| `invoice.override_price` | Override price |
| `invoice.sell_below_cost` | Sell below cost |
| `invoice.hold_bill` | Hold/resume bills |
| `invoice.credit_note.create` | Issue credit notes |

#### Inventory
| Permission | Description |
|---|---|
| `inventory.read` | View stock |
| `inventory.adjustment.create` | Create adjustments |
| `inventory.adjustment.approve` | Approve adjustments |
| `inventory.view_cost` | View stock valuation |

#### Suppliers & Purchasing
| Permission | Description |
|---|---|
| `supplier.create` | Create suppliers |
| `supplier.read` | View suppliers and balances |
| `supplier.update` | Edit suppliers |
| `supplier.deactivate` | Deactivate suppliers |
| `purchasing.grn.create` | Receive goods and post supplier liability |
| `purchasing.payment.create` | Record supplier payments |
| `purchasing.return.create` | Return received goods and reduce supplier liability |
| `purchasing.cost_history.read` | View product purchase cost history |

#### Finance
| Permission | Description |
|---|---|
| `finance.cashbook.read` | View cashbook entries |
| `finance.cashbook.create` | Record non-invoice cash receipts and payments |
| `finance.receivables.read` | View customer receivables |
| `finance.payables.read` | View supplier payables |
| `finance.cash_closing.create` | Complete daily cash closing |
| `finance.cash_closing.approve` | Approve a cash closing variance |

#### Appointments
| Permission | Description |
|---|---|
| `appointment.create` | Create appointments |
| `appointment.read` | View calendar |
| `appointment.update` | Edit appointments |
| `appointment.cancel` | Cancel appointments |
| `appointment.convert_to_job` | Convert to job card |

#### Job Cards
| Permission | Description |
|---|---|
| `jobcard.create` | Create job cards |
| `jobcard.read` | View job cards |
| `jobcard.update` | Edit job cards |
| `jobcard.status_change` | Change status |
| `jobcard.parts.add` | Add parts |
| `jobcard.estimate.create` | Create estimates |
| `jobcard.estimate.approve` | Approve estimates |
| `jobcard.complete` | Complete jobs |

#### Reporting
| Permission | Description |
|---|---|
| `report.sales.view` | View sales reports |
| `report.inventory.view` | View inventory reports |
| `report.finance.view` | View financial reports |
| `report.customer.view` | View customer balance reports |
| `report.tax.view` | View tax reports |
| `report.export` | Export reports |
| `report.view_audit_logs` | View audit logs |

#### System
| Permission | Description |
|---|---|
| `system.config` | System configuration |
| `system.backup.create` | Create backups |
| `system.backup.restore` | Restore backups |
| `system.user_management` | Full user management |

## 2.2 Role Definitions (MVP)

| Role | Description |
|---|---|
| **Super Administrator** | System-wide access, user/role management, security |
| **Business Owner** | Full business access, financial reports, approvals |
| **Manager** | Sales/inventory supervision, mid-level approvals |
| **Accountant** | Accounting, tax, financial reporting |
| **Cashier** | POS operations, invoice generation |
| **Store Keeper** | Inventory control, GRN, adjustments |
| **Service Officer** | Appointments, job cards |
| **Auditor** | Read-only access |

## 2.3 Role Permission Matrix (MVP)

### Super Administrator
```text
All registered MVP permissions.

Implementation rule:
- SUPER_ADMIN receives every permission code registered by the MVP.
- Authorization still uses the same permission checks as every other role; there is no hidden client-side bypass.
```

### Business Owner
```text
All permissions except system.user_management and role management
```

### Manager
```text
customer.create, customer.read, customer.update, customer.view_pii,
product.create, product.read, product.update,
invoice.create, invoice.read, invoice.void, invoice.reprint,
invoice.payment.create, invoice.payment.refund,
invoice.discount.apply, invoice.discount.approve_25,
invoice.override_price, invoice.hold_bill,
inventory.read, inventory.adjustment.create,
inventory.adjustment.approve,
supplier.create, supplier.read, supplier.update,
purchasing.grn.create, purchasing.payment.create, purchasing.return.create,
finance.cashbook.read, finance.receivables.read, finance.payables.read,
finance.cash_closing.create, finance.cash_closing.approve,
appointment.create, appointment.read, appointment.update,
jobcard.create, jobcard.read, jobcard.update, jobcard.status_change,
jobcard.parts.add, jobcard.estimate.create, jobcard.estimate.approve,
report.sales.view, report.inventory.view, report.customer.view
```

### Cashier
```text
customer.create, customer.read, customer.update,
product.read,
invoice.create, invoice.read, invoice.reprint,
invoice.payment.create, invoice.discount.apply, invoice.hold_bill,
jobcard.read, jobcard.status_change,
appointment.read
```

### Store Keeper
```text
product.read, product.update,
inventory.read, inventory.adjustment.create,
supplier.create, supplier.read, supplier.update,
purchasing.grn.create, purchasing.return.create, purchasing.cost_history.read,
jobcard.read, jobcard.parts.add
```

### Service Officer
```text
customer.create, customer.read, customer.update, customer.view_pii,
appointment.create, appointment.read, appointment.update,
appointment.convert_to_job,
jobcard.create, jobcard.read, jobcard.update, jobcard.status_change,
jobcard.parts.add, jobcard.estimate.create, jobcard.complete,
product.read
```

### Accountant
```text
customer.read, customer.view_pii,
product.read, product.view_cost,
invoice.read, invoice.payment.create, invoice.payment.refund,
inventory.read, inventory.view_cost,
supplier.read, purchasing.payment.create, purchasing.cost_history.read,
finance.cashbook.read, finance.cashbook.create,
finance.receivables.read, finance.payables.read,
finance.cash_closing.create, finance.cash_closing.approve,
report.sales.view, report.finance.view, report.tax.view, report.export
```

### Auditor
```text
customer.read, customer.view_pii,
product.read, invoice.read, inventory.read,
appointment.read, jobcard.read, supplier.read,
report.sales.view, report.inventory.view, report.finance.view,
report.customer.view, report.tax.view, report.export, report.view_audit_logs
```

## 2.4 Multi-Role System (MVP)

### Role Types

| Type | Description | Max Count |
|---|---|---|
| Primary Role | Permanent, assigned at user creation | 1 per user |
| Secondary Role | Temporary, granted for a specific period | Multiple per user |

### User Role Data Model

```text
User
  ├── user_id (UUID)
  ├── username
  ├── password_hash
  ├── first_name / last_name
  ├── email / phone
  ├── primary_role_id → Role (permanent)
  └── ...

UserRole (Secondary Roles)
  ├── user_role_id (UUID)
  ├── user_id → User
  ├── role_id → Role
  ├── granted_by → User
  ├── granted_at (timestamp)
  ├── expires_at (timestamp)
  ├── is_active (boolean)
  ├── revoked_at (timestamp)
  ├── revoked_by → User
  └── revoke_reason (text)
```

### Granting Secondary Roles

```text
1. Authorized user (user.grant_role) opens user management
2. Selects user → Actions → Grant Secondary Role
3. Selects role from dropdown (excluding primary role)
4. Sets expiry period:
   - Default: 1 day
   - Options: 1 hour, 6 hours, 1 day, 7 days, 30 days, custom
5. System creates UserRole with expires_at
6. Secondary role is immediately active
7. Audit log recorded
```

### Revoking Secondary Roles

#### Automatic Revocation
```text
Background Job (every minute):
1. Find all user_roles where is_active=true AND expires_at <= NOW()
2. For each expired role:
   - Set is_active = false
   - Set revoked_at = NOW()
   - Set revoke_reason = 'Automatic expiry'
   - Log audit trail
```

#### Manual Revocation
```text
1. Authorized user (user.revoke_role) opens user management
2. Selects user → View Roles
3. Shows primary role (cannot revoke) + secondary roles
4. Clicks "Revoke" on a secondary role
5. Optional: Enter reason
6. Role marked inactive immediately
7. Audit log recorded
```

### Effective Permissions Calculation

```text
function getEffectivePermissions(user):
    permissions = Set()

    // Add primary role permissions
    permissions.addAll(user.primary_role.permissions)

    // Add active secondary role permissions
    secondaryRoles = user.user_roles
        .filter(ur => ur.is_active AND (ur.expires_at IS NULL OR ur.expires_at > NOW()))

    for each role in secondaryRoles:
        permissions.addAll(role.permissions)

    return permissions  // Union of all permissions
```

### Role Display

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
│  [+ Grant Secondary Role]                                   │
└─────────────────────────────────────────────────────────────┘
```

## 2.5 Session Management

- Login with username/password
- Session timeout after 15 minutes of inactivity
- Account lock after 5 failed login attempts (auto-unlock after 30 minutes)
- Password policy: min 8 chars, uppercase + lowercase + digit + special char, BCrypt hashed

## 2.6 Audit Trail for Role Changes

Every role-related action is logged:

| Action | Audit Log Entry |
|---|---|
| Grant secondary role | user_id, role_id, granted_by, expires_at |
| Revoke secondary role | user_id, role_id, revoked_by, reason |
| Role expiry (auto) | user_id, role_id, expires_at |
| Permission change | role_id, permission, old_value, new_value, changed_by |
| Primary role change | user_id, old_role_id, new_role_id, changed_by |

---

# 3. Customer Management (MVP)

## 3.1 Customer Profile

| Field | Required | Notes |
|---|---|---|
| Customer Code | Auto-generated | Format: CUST-NNNN |
| Name | Yes | |
| Phone | Yes | Primary contact |
| Email | No | |
| Address | No | |
| NIC / BR Number | No | Encrypted at rest |
| Customer Category | Yes | Retail / Wholesale / Corporate |
| Credit Limit | No | LKR amount, 0 = no credit |
| Current Balance | Calculated | Sum of unpaid invoices |
| Status | Auto | Active / Blocked (if overdue > 90 days) |

## 3.2 Customer Search

- Search by: code, name, phone (partial match)
- Results in < 1 second
- Minimum 2 characters to trigger search

## 3.3 Credit Limit Enforcement

| Days Outstanding | Action |
|---|---|
| 0–30 | Normal |
| 31–60 | Warning displayed at POS |
| 61–90 | Block credit sales (cash only) |
| 91+ | Block all sales until payment received |

## 3.4 Loyalty Points (v1.4)

Per SRS.md §6.2.8.

- Earn rate configurable (e.g., 1 point per LKR 100 spent), applied on posted sale invoices only — never on DRAFT or VOIDED
- Redemption: customer redeems points at POS for a discount, subject to a configurable minimum-points threshold and a max-redemption-per-invoice cap
- Points balance is derived from a `loyalty_transactions` ledger (earn/redeem/expire/clawback rows), the same "ledger is authoritative, cached balance is a projection" rule the rest of MVP already follows for stock and receivables
- Clawback: points earned on an invoice that is later voided or has a full credit note issued are reversed
- Points expiry: configurable (e.g., 12 months from earn date); a background job expires stale points and logs a `loyalty_transactions` expiry row
- No cash-out of points (redemption against a sale only)

---

# 4. Product Management (MVP)

## 4.1 Product Types

| Type | Stock Tracking | Example |
|---|---|---|
| Inventory Product | Yes | Phones, groceries, hardware |
| Service Product | No | Repair service, consultation |

Rental and Custom product types are deferred.

## 4.2 Product Information

**v1.4 change:** SKU, barcode, and per-tier pricing move to the **variant** level (§4.6 below) — every product has at least one variant, even one with no real variation, so nothing below is a special case. `products` itself becomes the style/parent record.

| Field | Required | Notes |
|---|---|---|
| Product Name | Yes | |
| Description | No | |
| Category | Yes | Hierarchical (tree) |
| Brand | No | See §4.6.1 |
| UOM | Yes | PCS, KG, LTR, BOX, etc. |
| Tax Category | Yes | Standard-rated / Exempt / Zero-rated |
| Status | Yes | Active / Inactive |

Each variant carries: SKU (required), barcode, cost price, selling price, wholesale price, stock qty (calculated), reorder point, status.

## 4.3 Pricing Tiers

| Tier | Purpose |
|---|---|
| Cost Price | Purchase cost from supplier |
| Selling Price | Default retail price |
| Wholesale Price | For wholesale customers |

Price resolution at POS:
1. Customer category default tier
2. Any active promotion/price-list applicable to the variant (§5.6a)
3. Variant's base price for that tier
4. Manual override (Admin/Manager only, with reason)

## 4.4 Categories

- Hierarchical tree structure (parent → child)
- Example: Electronics → Mobile Phones → Smartphones
- Product assigned to leaf category only
- A category may have admin-defined attributes assigned to it (§4.7); a product in that category exposes those attributes on its variant form

## 4.5 Basic Stock Tracking

- The stock movement ledger is the authoritative source of stock on hand, tracked at **variant** granularity (§4.6)
- Stock qty = sum of signed posted Goods Receipt, sale, customer-return, supplier-return, job-part, production, and adjustment movements
- Any cached stock balance is a performance optimization and must reconcile to the ledger
- Stock adjustments (positive/negative) with approval
- Low-stock alert when qty ≤ reorder point
- No batch, serial, or expiry tracking in MVP

## 4.6 Product Variants (v1.4)

Per SRS.md §6.4.2, pulled into MVP scope. This is the one v1.4 addition that changes an already-built foundation rather than adding a new table: every table that references a product today (invoice lines, credit note lines, held-sale items, job parts, stock movements, stock adjustments) switches to referencing a variant instead.

### 4.6.1 Model

```text
Product (parent/style)
  ├── name, description, category, brand
  └── is_variant_group: whether this product exposes variant selection at POS

ProductVariant
  ├── product_id → Product
  ├── sku (unique)
  ├── barcode
  ├── variant_label ("Red / Medium")
  ├── variant attribute values (via §4.7's category attributes, e.g. Color=Red, Size=M)
  ├── cost_price, selling_price, wholesale_price
  ├── stock_qty (calculated, from the stock ledger)
  ├── reorder_point
  ├── image
  └── status
```

A product with no real variation still has exactly one `ProductVariant` row (its default variant) — there is no separate "simple product" code path.

### 4.6.2 POS Behavior

- Scanning the parent product's barcode, if it has more than one variant, opens a variant-picker (attribute dropdowns, e.g. "Select Color" → "Select Size")
- Scanning a variant's own barcode adds that variant directly
- A product with exactly one (default) variant behaves exactly like MVP v1.3's flat product — scan, add, done

## 4.7 Dynamic Attributes (v1.4)

Per SRS.md §6.4.2, generalized. Different categories need different specifications (a phone needs RAM/Storage, a picture frame needs Material/Width/Height) — rather than a fixed column per possible spec, an admin defines attributes and assigns them to categories.

```text
Attribute
  ├── name (e.g., "Color", "RAM", "Material")
  └── data_type (text | number | boolean | enum)

AttributeValue
  ├── attribute_id → Attribute
  └── value (for enum-type attributes, e.g., "Red", "Blue")

CategoryAttribute
  ├── category_id → Category
  ├── attribute_id → Attribute
  └── is_required (boolean)
```

A product's variant-creation form renders the attributes assigned to its category; attribute values selected per variant become that variant's `variant_label` and searchable filters.

## 4.8 Brands (v1.4)

Per SRS.md §6.4.4.

```text
Brand
  ├── name
  ├── description
  ├── logo
  └── status
```

A product's brand is optional (products without a brand remain valid). Products are filterable/searchable by brand at POS and in the catalog screen.

## 4.9 Packages / Bundles (v1.4)

Per SRS.md §6.4.3 "Combo/Bundle Products".

```text
Package
  ├── name
  ├── package_type
  ├── price
  └── status

PackageItem
  ├── package_id → Package
  ├── item_type (PRODUCT_VARIANT | SERVICE)
  ├── product_variant_id (nullable)
  ├── service_id (nullable)
  └── quantity
```

Rules:
- Package price should offer a saving relative to the sum of its components' standalone prices (validated, not silently allowed to exceed it)
- Selling a package is one invoice line, but stock is deducted individually for every PRODUCT_VARIANT component
- A package containing only services has no stock effect

## 4.10 Bill of Materials / Manufacturing (v1.4 — new)

Per SRS.md §6.4.11 (newly added by this decision; not previously part of SRS.md's vision). For a product the business assembles/manufactures from other stocked items rather than purchasing as a finished good.

```text
BillOfMaterials
  ├── finished_variant_id → ProductVariant
  ├── name
  └── status

BomItem
  ├── bom_id → BillOfMaterials
  ├── component_variant_id → ProductVariant
  ├── quantity
  ├── wastage_qty (optional)
  └── estimated_cost
```

- A **Produce** transaction consumes the required quantity of every component variant and increases the finished variant's stock, atomically, in one posted transaction (locking every component variant in stable order, the same pattern the stock ledger already uses for a multi-line sale)
- Insufficient component stock rejects the whole production, not a partial one
- Supports both a stocked finished product (produced ahead of demand) and a made-to-order product (produced at time of sale — the BOM still records what was consumed, but nothing sits in finished-goods stock beforehand)
- Circular BOM references (a component that is itself built from the finished product) are rejected

---

# 5. Invoicing Facility (MVP)

This is a core MVP module. Full invoice lifecycle from creation to payment.

## 5.1 Document Types in MVP

| Type | Description | Tax Implication |
|---|---|---|
| Sales Invoice | Standard sale of goods | Output VAT |
| Service Invoice | Service fee invoice | Output VAT |
| Tax Invoice | VAT-compliant invoice (auto-generated when VAT registered) | VAT mandatory |
| Credit Note | Return / discount adjustment | Reduces VAT |
| Receipt | Payment acknowledgement | No tax impact |
| Quotation | Price quote (non-binding) | No tax impact |

Debit Notes, Delivery Notes, Purchase Invoices, and Purchase Orders are deferred to Phase 2.

## 5.2 Invoice Data Model

Posted business documents are immutable. Draft documents may be edited until posting. Historical invoices store the commercial and tax values that were true when the document was posted.

```text
Invoice
  ├── invoice_number (nullable while DRAFT; allocated at POST: INV-YYYYMMDD-NNNN)
  ├── invoice_date
  ├── due_date
  ├── invoice_type (SALES | SERVICE | TAX)
  ├── invoice_status (DRAFT | POSTED | VOIDED)
  ├── customer_id → Customer (nullable for permitted walk-in immediate sales)
  ├── cashier_id → User
  ├── business_name_snapshot
  ├── business_address_snapshot
  ├── business_tin_snapshot
  ├── customer_name_snapshot
  ├── customer_address_snapshot
  ├── customer_tin_snapshot
  ├── subtotal (before tax)
  ├── discount_type (NONE | PERCENTAGE | FIXED)
  ├── discount_value
  ├── discount_amount (calculated)
  ├── taxable_amount
  ├── vat_rate_snapshot
  ├── vat_amount
  ├── total_amount
  ├── amount_paid (derived/cached from posted payments)
  ├── balance_due (derived/cached from posted payments/credit notes)
  ├── payment_status (UNPAID | PARTIAL | PAID | CREDIT_NOTE)
  ├── notes
  ├── posted_at
  ├── voided_at
  ├── voided_by → User
  ├── void_reason
  └── created_at

InvoiceLineItem
  ├── invoice_id → Invoice
  ├── line_type (PRODUCT | SERVICE | CUSTOM)
  ├── product_id → Product (nullable)
  ├── service_id → ServiceCatalog (nullable)
  ├── sku_snapshot
  ├── description_snapshot
  ├── uom_snapshot
  ├── quantity
  ├── unit_price
  ├── discount_type (NONE | PERCENTAGE | FIXED)
  ├── discount_value
  ├── discount_amount
  ├── tax_category_snapshot
  ├── vat_rate_snapshot
  ├── taxable_amount
  ├── vat_amount
  └── line_total_incl_vat

InvoicePayment
  ├── invoice_id → Invoice
  ├── payment_method (CASH | CARD | BANK_TRANSFER | CHEQUE)
  ├── amount
  ├── reference_number (card auth, cheque no, etc.)
  ├── payment_date
  └── received_by → User

CreditNote
  ├── credit_note_number (auto at posting: CN-YYYYMMDD-NNNN)
  ├── original_invoice_id → Invoice
  ├── customer_id → Customer
  ├── reason
  ├── subtotal
  ├── vat_amount
  ├── total_amount
  ├── status (ISSUED | APPLIED)
  ├── issued_date
  └── issued_by → User

CreditNoteLineItem
  ├── credit_note_id → CreditNote
  ├── invoice_line_item_id → InvoiceLineItem
  ├── quantity_returned
  ├── unit_price_snapshot
  ├── line_total
  └── vat_amount
```

### 5.2.1 Invoice Lifecycle Rules

| Status | Editable | Stock/Finance Effect | Official Number | Reporting |
|---|---|---|---|---|
| DRAFT | Yes | None | Not allocated | Excluded |
| POSTED | No | Posted atomically | Required | Included |
| VOIDED | No | Reversal/void effects recorded | Retained | Included with void status |

- A posted invoice is never directly edited or deleted.
- Corrections use a credit note, refund, or controlled void/reversal according to the transaction.
- Tax and commercial snapshots on a posted invoice never recalculate when master data or tax configuration later changes.
- `payment_status` is independent from `invoice_status`; for example, an invoice may be `POSTED` and `PARTIAL`.

## 5.3 Invoice Creation Workflow

### 5.3.1 From POS (Quick Sale)

```text
1. Cashier scans products → cart populated
2. Select customer (optional — walk-in allowed)
3. Apply discounts (line-level or invoice-level)
4. Review totals (subtotal, VAT, total)
5. Select payment method(s) — split payment supported
6. Confirm → Invoice created, receipt printed
7. Stock deducted automatically
```

### 5.3.2 Manual Invoice (Back Office)

```text
1. Manager/Admin creates invoice from Invoice screen
2. Select customer (required)
3. Add line items (product or free-text service)
4. Apply discounts
5. Set payment terms (due date)
6. Save as Draft → Review → Send/Print
7. Payment recorded separately when received
```

### 5.3.3 Service Invoice

```text
1. Linked to a Job Card (see Section 6)
2. Auto-populate: service charges + parts used
3. Add additional charges if needed
4. Generate invoice for customer
5. Payment collected at pickup
```

## 5.4 Tax Invoice Requirements

When the business is VAT-registered, the system generates a Tax Invoice containing:

1. Header: "TAX INVOICE" in bold
2. Business: Name, Address, TIN
3. Customer: Name, Address, TIN (if registered)
4. Invoice number (sequential, unique)
5. Invoice date
6. Line items: Description, Qty, Unit Price, Taxable Value, VAT Rate (18%), VAT Amount, Line Total
7. Total Taxable Value
8. Total VAT
9. Total Amount (incl. VAT, rounded to nearest cent)

MVP includes an internal Bizco QR code containing the invoice reference. IRD e-Invoice gateway submission, digital signatures, and statutory e-Invoicing integration remain deferred to Phase 2.

## 5.5 Invoice Numbering

| Mode | Format | Example |
|---|---|---|
| Online | INV-YYYYMMDD-NNNN | INV-20260401-0042 |
| Credit Note | CN-YYYYMMDD-NNNN | CN-20260401-0003 |

Sequential numbering resets daily. Gap-free numbering is enforced for POSTED documents.

Implementation rules:
- Drafts do not consume official invoice numbers.
- Number allocation occurs on the server inside the posting transaction.
- A concurrency-safe `document_sequences` mechanism is used; `MAX(number)+1` is prohibited.
- A failed posting does not leave a posted business document without its required sequence identity.

## 5.6 Discounts on Invoicing

| Discount Level | Scope | Approval |
|---|---|---|
| Line discount ≤ 10% | Per line item | Cashier self-service |
| Line discount 10–25% | Per line item | Manager PIN required |
| Line discount > 25% | Per line item | Admin/Owner approval |
| Invoice discount ≤ 10% | Invoice total | Cashier self-service |
| Invoice discount 10–25% | Invoice total | Manager PIN required |
| Invoice discount > 25% | Invoice total | Admin/Owner approval |
| Sell below cost | Any line | Admin/Owner + mandatory reason |

## 5.6a Promotions & Discount Campaigns (v1.4)

Per SRS.md §6.4.5.6/6.4.5.7, pulled into MVP scope. Distinct from the manual discount tiers in §5.6 above: a promotion is a pre-configured, rule-driven, date-bound campaign, not a per-transaction manual decision.

```text
Promotion
  ├── name
  ├── promo_type (PERCENTAGE | FIXED | BOGO | BUY_X_GET_Y | FREE_ITEM)
  ├── coupon_code (nullable — text-based code entry at POS)
  ├── applies_to (PRODUCT_VARIANT | CATEGORY | BRAND | CUSTOMER_GROUP | INVOICE)
  ├── min_qty / min_value (threshold to qualify)
  ├── start_date / end_date (optional time-of-day window)
  ├── stacking_rule (REPLACES_BASE_PRICE | ADDITIONAL_DISCOUNT)
  └── status
```

Rules:
- A promotion only applies within its configured validity window and targeting rule; expired or not-yet-active promotions never apply
- `stacking_rule` determines whether a promotion can combine with the manual discount tiers in §5.6, or replaces the line price outright
- The §5.6 manager-approval tiers still govern any *manual* discount on top of whatever a promotion already applied — a promotion itself does not require cashier/manager approval, since it was already approved when configured
- A promotion's performance (redemption count, discount value given) is reportable (§10.2)

## 5.7 Payment Methods

| Method | Supported | Notes |
|---|---|---|
| Cash | Yes | |
| Credit/Debit Card | Yes | Record card auth reference |
| Bank Transfer | Yes | Record transfer reference |
| Cheque | Yes | Record cheque number, date |

Split payments are supported: an invoice can be paid with multiple methods.

For an immediate-payment sale, the remaining balance must be zero before finalizing. For an authorized credit sale, a non-zero remaining balance is permitted and becomes a customer receivable. Credit sale posting requires an identified customer and must pass credit-limit and aging rules.

## 5.8 Credit Notes & Returns

### 5.8.1 Return Windows

| Business Type | Default Window |
|---|---|
| General Retail | 7 days |
| Electronics | 14 days |

### 5.8.2 Return Workflow

```text
1. Customer requests return
2. Cashier/Manager initiates Credit Note from invoice
3. Select items to return + quantities
4. System validates return window
5. Credit Note issued:
   - Stock increased (if item returnable)
   - VAT reversed proportionally
   - Customer balance reduced OR cash refund issued
6. Original invoice marked with credit note reference
```

### 5.8.3 Accounting Impact

| Scenario | Debit | Credit |
|---|---|---|
| Return to stock | Inventory | Sales Returns |
| Cash refund | Sales Returns | Cash |
| Credit Note | Sales Returns | Accounts Receivable |

## 5.9 Hold / Resume Bill (POS)

- Hold: persists the current cart and reserves stock
- Resume: select held bill, cart restored
- Held bills visible under "Held Bills" screen
- Auto-release at end of day (configurable)
- Cancel held bill releases reserved stock
- Holding a bill does **not** create a posted `SALE` stock movement
- Physical stock remains derived from `stock_movements`
- Reserved stock is derived from active held-sale items
- Available stock = physical stock − reserved stock
- On successful sale posting, the reservation is released and the normal `SALE` stock movement is posted atomically

## 5.10 Reprint Receipts

- Reprint any invoice from history
- Search by invoice number, date, customer
- Reprint marked with "REPRINT" watermark

---

# 6. Event Scheduling Facility (MVP)

This is the second core MVP module. Covers appointment scheduling, calendar management, and basic job card workflow for service businesses.

## 6.1 Service Catalog

| Field | Required | Notes |
|---|---|---|
| Service Code | Yes | Auto-generated: SVC-NNNN |
| Service Name | Yes | e.g., "Phone Screen Repair" |
| Description | No | |
| Category | Yes | Repair, Consultation, Maintenance, etc. |
| Base Price | Yes | Starting price |
| Estimated Duration | Yes | In minutes |
| Requires Estimate | Yes | If true, customer must approve estimate before work begins |
| Status | Yes | Active / Inactive |

## 6.2 Appointment Management

### 6.2.1 Appointment Data Model

```text
Appointment
  ├── appointment_number (auto: APT-YYYYMMDD-NNNN)
  ├── customer_id → Customer
  ├── service_id → ServiceCatalog
  ├── appointment_date
  ├── start_time
  ├── end_time (calculated: start + estimated duration)
  ├── technician_id → User (Manager/Cashier role)
  ├── status (SCHEDULED | CONFIRMED | IN_PROGRESS | COMPLETED | NO_SHOW | CANCELLED)
  ├── notes (customer request, special instructions)
  ├── is_walk_in (boolean)
  ├── converted_to_job_card_id → JobCard (nullable)
  ├── created_by → User
  ├── created_at
  └── reminder_sent (boolean)
```

### 6.2.2 Appointment Workflow

```text
1. Customer calls / walks in → Cashier/Manager creates appointment
2. Select customer, service, date/time
3. Assign technician (optional — can be assigned later)
4. Confirm appointment → Status: CONFIRMED
5. Day of appointment:
   a. If customer arrives → Status: IN_PROGRESS → Convert to Job Card
   b. If customer doesn't show → Status: NO_SHOW
6. After service completed → Status: COMPLETED
7. Invoice generated for service
```

### 6.2.3 Walk-in Conversion

```text
Walk-in customer → Create Appointment (is_walk_in = true)
  → Immediately convert to Job Card
  → Skip scheduling, go directly to service
```

## 6.3 Calendar View

### 6.3.1 Calendar Features

- **Daily view**: All appointments for selected date, grouped by technician
- **Weekly view**: 7-day overview with appointment density
- **Monthly view**: High-level calendar with appointment counts per day
- **Technician filter**: View one or all technicians
- **Service filter**: View appointments by service type

### 6.3.2 Calendar Interactions

- Click on time slot → Create new appointment
- Click on appointment → View/edit details
- Drag appointment → Reschedule (changes date/time/technician)
- Color coding by status:
  - Blue: Scheduled
  - Green: Confirmed
  - Orange: In Progress
  - Gray: Completed
  - Red: Cancelled/No Show

### 6.3.3 Time Slot Management

- Configurable slot duration (15 / 30 / 60 minutes)
- Configurable business hours (e.g., 8:00 AM – 6:00 PM)
- Buffer time between appointments (configurable, default: 15 min)
- Double-booking prevention per technician
- Double-booking is enforced authoritatively by the server and PostgreSQL transaction/constraint strategy, not only by JavaFX validation
- Concurrent appointment requests for overlapping active time ranges must not both succeed

## 6.4 Job Cards (Basic)

### 6.4.1 Job Card Data Model

```text
JobCard
  ├── job_number (auto: JC-YYYYMM-NNNN)
  ├── appointment_id → Appointment (nullable for walk-ins)
  ├── customer_id → Customer
  ├── device_type (configurable list: Phone, Laptop, TV, etc.)
  ├── brand / model
  ├── serial_number
  ├── reported_issue
  ├── customer_notes
  ├── accessories_received
  ├── device_condition
  ├── status (CREATED | ESTIMATE_PENDING | ESTIMATE_APPROVED
  │          | IN_PROGRESS | READY_FOR_PICKUP | COMPLETED | CANCELLED)
  ├── technician_id → User
  ├── estimated_completion_date
  ├── actual_completion_date
  ├── pickup_date
  └── created_at

JobService
  ├── job_card_id → JobCard
  ├── service_id → ServiceCatalog
  ├── estimated_cost
  ├── actual_cost
  ├── estimated_duration_minutes
  ├── status (PENDING | IN_PROGRESS | COMPLETED)
  └── notes

JobPart
  ├── job_card_id → JobCard
  ├── product_id → Product
  ├── qty_used
  ├── unit_price (charged to customer)
  └── is_warranty_covered (boolean)

JobEstimate
  ├── job_card_id → JobCard
  ├── estimated_total
  ├── description (breakdown of services + parts)
  ├── created_by → User
  ├── created_at
  ├── customer_response (PENDING | ACCEPTED | DECLINED)
  ├── customer_response_at
  └── notes
```

### 6.4.2 Job Card Lifecycle (Simplified MVP)

```text
                    Job Created
                        │
                 ┌──────┴──────┐
                 │             │
            Walk-in        Appointment
                 │             │
                 └──────┬──────┘
                        │
              Estimate Required? ───No──→ In Progress
                        │
                       Yes
                        │
                Estimate Created
                        │
                Customer Reviews
                        │
                 ┌──────┴──────┐
                 │             │
            Accept        Decline → Job Cancelled
                 │
          In Progress
                 │
         Parts Required? ──Yes──→ Deduct from Inventory
                 │                      │
                 ↓                Wait for Parts
          Ready for Pickup              │
                 │               Parts Received
                 │                      │
                 └──────────────────────┘
                        │
                Customer Pickup
                        │
                 Invoice + Payment
                        │
                 Job Completed
```

### 6.4.3 Status Transitions

| From | To | Rule |
|---|---|---|
| CREATED | ESTIMATE_PENDING | Auto if service requires estimate |
| CREATED | IN_PROGRESS | Manager override (skip estimate) |
| ESTIMATE_PENDING | ESTIMATE_APPROVED | Customer accepts |
| ESTIMATE_APPROVED | IN_PROGRESS | Technician starts work |
| IN_PROGRESS | READY_FOR_PICKUP | All services completed |
| READY_FOR_PICKUP | COMPLETED | Customer pays and collects |
| Any | CANCELLED | Reason required |

### 6.4.4 Parts Usage

- When job requires parts, technician selects products from inventory
- Stock deducted through a posted `JOB_PART` stock movement at time of parts usage
- Part cost recorded on JobPart
- Charge to customer at selling price (margin captured)

### 6.4.5 Warranty Tracking

- Warranty period configurable per service (default: 30 days)
- Warranty start date = pickup date
- Warranty end date = pickup date + warranty period
- Warranty claims linked to original job card

## 6.5 Technician Calendar

- Each technician sees their assigned jobs for the day/week
- Status updates from technician view
- Daily schedule printout available

---

# 7. Point of Sale (MVP)

## 7.1 POS Screen Layout

```
┌─────────────────────────────────────────────────────┐
│  Bizco POS                          [Cashier: John]  │
├──────────────────────┬──────────────────────────────┤
│                      │  Customer: [Search/Select]    │
│  Barcode: [________] │  Category: [All ▼]           │
│                      │                              │
│  ┌──────────────────┐│  ┌──────────────────────┐   │
│  │ Product Grid     ││  │  Cart                │   │
│  │ (search results) ││  │  ┌───┬─────┬───┬───┐ │   │
│  │                  ││  │  │ # │Item │Qty│Tot│ │   │
│  │                  ││  │  ├───┼─────┼───┼───┤ │   │
│  │                  ││  │  │ 1 │Item1│ 2 │...│ │   │
│  │                  ││  │  │ 2 │Item2│ 1 │...│ │   │
│  │                  ││  │  └───┴─────┴───┴───┘ │   │
│  └──────────────────┘│  │                      │   │
│                      │  │  Subtotal:  5,000.00 │   │
│                      │  │  VAT (18%):   900.00 │   │
│                      │  │  Total:      5,900.00│   │
│                      │  └──────────────────────┘   │
│                      │                              │
│                      │  [Hold] [Discount] [Pay]     │
├──────────────────────┴──────────────────────────────┤
│  Status: ● ONLINE          Held Bills: 3            │
└─────────────────────────────────────────────────────┘
```

## 7.2 Barcode Scanning

- USB barcode scanner (keyboard wedge mode)
- Supports EAN-13, UPC-A, CODE-128, CODE-39
- Scan recognition < 500ms
- Invalid barcode: audio + visual feedback

## 7.3 Product Search

- Search by: SKU, barcode, product name (partial)
- Real-time search as user types (debounced 300ms)
- Results prioritized: exact match > starts-with > contains

## 7.4 Cart Operations

| Action | Behavior |
|---|---|
| Add item | Scan barcode or click product |
| Change qty | Click qty field, enter new qty |
| Remove item | Swipe or click remove button |
| Line discount | Click discount on line item |
| Clear cart | Confirmation dialog required |

## 7.5 Discounts

### Line-Level Discount

- Percentage or fixed amount
- ≤ 10%: Cashier self-service
- 10–25%: Manager PIN required
- > 25%: Admin/Owner approval

### Invoice-Level Discount

- Applied after all line items
- Same approval tiers as line discount

## 7.6 Payment Processing

```text
1. Click [Pay] → Payment screen opens
2. Display: Total Due = LKR 5,900.00
3. Select payment method:
   - Cash: Enter amount tendered → Calculate change
   - Card: Enter reference → Confirm
   - Bank Transfer: Enter reference → Confirm
   - Cheque: Enter cheque number + date → Confirm
4. Split payment: Add multiple payment lines
5. Remaining balance must reach zero
6. Confirm → Invoice created → Receipt printed → Stock deducted
```

## 7.7 Receipt Printing

- Thermal printer support (80mm/58mm)
- Receipt includes: business name, address, TIN, invoice #, date, items, totals, VAT, payment method
- Reprint from invoice history

---

# 8. Inventory Management (MVP)

## 8.1 Stock Operations

### Purchase Order + Goods Receipt (v1.4 — replaces the single-step GRN)

Per SRS.md §6.9.1, pulled into MVP scope without the 3-way-matching/GRPI machinery in §6.9.2 (that needs full GL, still deferred). `goods_receipts.purchase_order_id` stays nullable — a small/ad-hoc purchase can still be received directly with no formal PO, matching how Bizco's own SME scenarios (SC-01/SC-03) actually buy day to day.

```text
1. (Optional) Create a Purchase Order against a supplier, pre-fillable from that supplier's product catalog (§8.4a)
2. PO status: Draft → Approved → Sent → Partially_Received → Fully_Received → Closed/Cancelled
3. Create a Goods Receipt — against a PO (one PO may have multiple receipts for partial/staged delivery) or standalone
4. Add received items: variant, qty received, qty damaged, qty rejected, unit cost
5. Post the Goods Receipt atomically
6. System creates stock movements (for the usable received qty) and updates product cost history
7. System creates or increases the supplier payable
8. If paid immediately, record a supplier payment against the payable
9. A PO auto-closes once total received qty across all its receipts reaches the ordered qty
```

### Stock Adjustment

| Type | Description | Approval |
|---|---|---|
| Positive (+) | Found more stock than system | Manager |
| Negative (−) | Stock lost / short | Manager |
| Damage | Goods damaged in storage | Manager |

### Stock Return to Supplier

- Return goods → post negative stock movement → reduce supplier payable using a supplier return adjustment
- Linked to the original goods receipt line

### Bill of Materials Production (v1.4)

- See §4.10 for the Bill of Materials model
- A Produce transaction posts a `PRODUCTION_IN` movement on the finished variant and matching `PRODUCTION_OUT` movements on every component variant, atomically

## 8.2 Low Stock Alerts

- Variants at or below reorder point flagged in dashboard
- Alert list viewable from inventory screen
- No auto-PO generation in MVP (manual ordering, even though the Purchase Order document itself is now in scope)

## 8.3 Basic Reports

- Stock on hand by variant
- Stock value (qty × cost price)
- Low stock items list
- Package, promotion, loyalty, and BOM production reports (§10.2)

## 8.4 Supplier Management

- Create, search, update, and deactivate suppliers
- Maintain contact details, TIN, payment terms, and opening balance
- View supplier purchase-order/goods-receipt history, payments, and outstanding balance
- Record full or partial supplier payments by cash, bank transfer, or cheque
- Preserve product cost history per goods receipt; the latest cost may update the variant's default cost price
- A posted goods receipt cannot be edited or deleted; corrections use a supplier return or reversing entry

## 8.4a Supplier Product Catalog (v1.4)

Per SRS.md §6.9.4, pulled into MVP scope.

```text
SupplierProduct
  ├── supplier_id → Supplier
  ├── product_variant_id → ProductVariant
  ├── supplier_sku
  ├── purchase_price
  ├── min_order_qty
  ├── lead_time_days
  ├── last_purchase_price (updated on each posted goods receipt)
  └── is_preferred
```

- A variant may be sourced from multiple suppliers; a supplier may supply many variants (many-to-many)
- Creating a Purchase Order line for a known supplier-variant pair pre-fills price, min order qty, and lead time
- Supplier comparison by price/lead-time for the same variant is reportable

---

# 9. Tax Management (MVP)

## 9.1 VAT

- Standard rate: 18% (configurable)
- Applied on taxable value (after discounts)
- Tax categories: Standard-rated (18%), Exempt (0%), Zero-rated (0%)
- Tax Invoice auto-generated when business is VAT-registered

## 9.2 Exempt Supplies (Hardcoded in MVP)

- Basic food items (rice, milk, vegetables, fruits, eggs)
- Medical services
- Educational services

## 9.3 Tax Configuration

- Toggle VAT ON/OFF
- Set VAT rate
- Set business TIN number
- All changes logged

SSCL, WHT, e-Invoicing, and payroll taxes are deferred.

---

# 10. Reporting, Dashboard & Basic Finance (MVP)

## 10.1 Dashboard KPIs

| KPI | Calculation | Refresh |
|---|---|---|
| Today's Sales | Sum of today's invoice totals | Real-time |
| Monthly Revenue | Month-to-date total | Real-time |
| Outstanding Receivables | Total unpaid customer balances | Real-time |
| Outstanding Payables | Total unpaid supplier balances | Real-time |
| Low Stock Items | Count below reorder point | Real-time |
| Today's Appointments | Count of today's appointments | Real-time |
| Pending Job Cards | Count of in-progress jobs | Real-time |

## 10.2 Reports Available in MVP

| Report | Description | Export |
|---|---|---|
| **Daily Sales** | All sales for a selected date | PDF, CSV |
| **Sales by Product** | Top selling products by qty and value | PDF, CSV |
| **Sales by Payment Method** | Cash vs card vs other | PDF |
| **Stock on Hand** | Current stock qty and value | PDF, CSV |
| **Low Stock Report** | Products below reorder point | PDF |
| **Customer Balances** | Outstanding receivables by customer | PDF, CSV |
| **Supplier Balances** | Outstanding payables by supplier | PDF, CSV |
| **Cashbook** | Cash and bank movements by date and type | PDF, CSV |
| **Daily Cash Closing** | Expected, counted, and variance by cashier | PDF |
| **Appointment Summary** | Appointments by date range, technician | PDF |
| **Job Card Status** | Open/closed jobs by status | PDF |
| **Tax Summary** | VAT collected for a period | PDF |
| **Variant Sales/Stock** | Sales and stock on hand by variant, brand, attribute | PDF, CSV |
| **BOM Production** | Production cost and component yield per finished variant | PDF |
| **Package Sales** | Package/bundle sales by package | PDF, CSV |
| **Promotion Performance** | Redemption count and discount value per promotion | PDF, CSV |
| **Loyalty Points** | Points liability, earned, and redeemed by period | PDF, CSV |
| **Purchase Order / Goods Receipt Aging** | Open POs and outstanding receipts | PDF |

## 10.3 Basic Finance

- Customer receivables are derived from posted invoices, receipts/payments, refunds, and credit notes; any stored balance field is a rebuildable cache/projection, not the accounting source of truth
- Supplier payables are derived from posted goods receipts, supplier returns, supplier-payment allocations, and opening balances; any stored balance field is a rebuildable cache/projection
- Cashbook entries are generated automatically for invoice payments, refunds, and supplier payments
- Authorized users may record non-invoice cash receipts and expenses with a category, reference, and reason
- Each cashier completes a daily closing with expected cash, counted cash, and variance
- Cash-closing variances require a reason and manager approval
- Posted financial transactions are immutable; corrections use reversal records
- Full chart of accounts, journals, trial balance, profit and loss, and balance sheet remain Phase 2

---

# 11. Non-Functional Requirements (MVP)

## 11.1 Performance

- Product search < 1 second
- Invoice generation < 2 seconds
- Dashboard load < 3 seconds
- Support 10+ concurrent users

## 11.2 Security

- BCrypt password hashing (cost 12)
- TLS for client-server communication
- Action-based RBAC with multi-role support
- Session timeout (15 min idle)
- Server-side session tracking supports concurrent-session limits, force logout, client ID, inactivity, and revocation
- Every protected API action is authorized on the server; permission-based JavaFX visibility is usability only, not a security boundary
- Effective permissions are recalculated from the primary role plus currently active secondary roles
- Audit trail for all data changes, approvals, postings, security events, and role changes

## 11.3 Data Integrity

- Invoice numbering: gap-free and sequential for posted documents, allocated concurrency-safely by the server
- Posted transactional documents are immutable; corrections use controlled reversal/credit/return records
- Stock atomicity: all-or-nothing deductions
- `stock_movements` is authoritative for physical stock
- Receivable, payable, and cashbook outputs reconcile from immutable posted source records
- Critical posting commands are idempotent so a network retry cannot duplicate a committed sale, payment, GRN, return, adjustment approval, or cash closing
- Historical invoices and tax reports use stored document/line snapshots rather than current master-data values
- Referential integrity enforced at DB level
- Financial amounts stored as DECIMAL(15,2)
- Business instants are stored using timezone-aware PostgreSQL timestamps; business-only dates use `DATE`

## 11.4 Usability

- Touch-friendly POS (min 48×48dp buttons)
- Keyboard shortcuts for common POS actions
- Consistent UI across modules
- Error messages with clear guidance

## 11.5 Backup & Restore

- An administrator can create an on-demand PostgreSQL backup from the application
- The system records backup time, file name, size, checksum, status, and initiating user
- Restore is restricted to the Super Administrator and requires explicit confirmation
- Restore runs only when active client sessions are disconnected or the server is in maintenance mode
- A restore verification procedure must confirm database readability and required schema version
- Backup and restore attempts are audit logged; failed operations show actionable errors

---

# 12. Technology Stack (MVP)

| Layer | Technology |
|---|---|
| Frontend | JavaFX + ControlsFX |
| Backend | Spring Boot 3.x |
| ORM | Spring Data JPA / Hibernate |
| Database | PostgreSQL 18.x recommended / PostgreSQL 16+ supported |
| Integration Testing | Testcontainers with the supported PostgreSQL version |
| Migration | Flyway |
| Auth | Spring Security + BCrypt |
| PDF | Apache PDFBox / JasperReports |
| Barcode | ZXing |
| Build | Maven |
| VCS | Git |

---

# 13. Database Schema Summary (MVP Tables)

```
Core Tables:
├── users
├── roles
├── permissions
├── role_permissions
├── user_roles
├── user_sessions
├── customers
├── loyalty_transactions
├── products
├── product_variants
├── product_categories
├── category_attributes
├── attributes
├── attribute_values
├── brands
├── bill_of_materials
├── bom_items
├── packages
├── package_items
├── promotions
├── suppliers
├── supplier_products
├── uom
└── document_sequences

Invoicing Tables:
├── invoices
├── invoice_line_items
├── invoice_payments
├── credit_notes
├── credit_note_line_items
├── held_sales
└── held_sale_items

Event Scheduling Tables:
├── services (service catalog)
├── appointments
├── job_cards
├── job_services
├── job_parts
├── job_estimates

Inventory Tables:
├── stock_movements
├── stock_adjustments
├── purchase_orders
├── purchase_order_items
├── goods_receipts
├── goods_receipt_items
├── supplier_payments
├── supplier_payment_allocations
├── supplier_returns
├── supplier_return_items
└── product_cost_history

Finance Tables:
├── cashbook_entries
└── cash_closings

Tax Tables:
├── tax_config

Reporting Tables:
├── audit_logs

Config Tables:
├── system_config
├── business_profile
└── backup_history
```

---

# 14. Development Phases (MVP - Detailed)

**Revised by the Week 12 scope-expansion decision** — see `DevelopmentPlan.md` §9 for the full weekly breakdown.

| Phase | Duration | Deliverables | Milestone |
|---|---|---|---|
| **Phase 1: Foundation** | 3 weeks | Project setup, DB schema, auth, user management, business profile | System boots, admin can login |
| **Phase 2: Core Master Data** | 2 weeks | Customer, product, category, and supplier CRUD | Master data manageable |
| **Phase 3: Invoicing** | 3 weeks | POS screen, invoice creation, payments, credit notes, receipts, VAT | End-to-end sale possible |
| **Phase 4: Event Scheduling** | 3 weeks | Service catalog, appointments, calendar view, job cards, technician mgmt | Appointments and jobs work |
| **Phase 5: Inventory & Purchasing** | 4 weeks | Stock ledger, adjustments, supplier product catalog, Purchase Orders, Goods Receipts (partial/multi-delivery), supplier returns/payments | Stock, PO, and supplier balances reconcile |
| **Phase 6: Catalog Expansion** | 4 weeks | Brands, dynamic attributes, product variants (schema, retrofit cutover, variant-aware POS) | Every stock-affecting module operates at variant granularity |
| **Phase 7: Manufacturing (BOM)** | 2 weeks | Bill of Materials, production/assembly transaction | BOM production atomically consumes component stock |
| **Phase 8: Merchandising** | 2 weeks | Packages/bundles, promotions/discount campaigns | Packages and promotions sell and reconcile correctly |
| **Phase 9: Basic Finance & Backup** | 2 weeks | Cashbook, receivables, payables, daily closing, loyalty points, backup and restore | Daily finances and loyalty reconcile; recovery is verified |
| **Phase 10: Reporting** | 3 weeks | Dashboard KPIs, all MVP reports (incl. variant/BOM/package/promotion/loyalty), export functionality | Reports generate correctly |
| **Phase 11: Polish & Test** | 3 weeks | UI refinement, PostgreSQL integration testing, recovery test, bug fixes, UAT | Production-ready MVP |
| **Total** | **31 weeks** | | |

---

# 15. User Management Details (MVP)

## 15.1 User Data Model

```text
User
  ├── user_id (auto: UUID)
  ├── username (unique, alphanumeric)
  ├── password_hash (BCrypt cost 12)
  ├── first_name
  ├── last_name
  ├── email
  ├── phone
  ├── role_id → Role
  ├── is_active (boolean)
  ├── is_locked (boolean)
  ├── failed_login_attempts
  ├── locked_until (timestamp, nullable)
  ├── password_changed_at (timestamp)
  ├── last_login_at (timestamp)
  ├── created_at
  └── updated_at

Role
  ├── role_id (auto)
  ├── role_name
  ├── description
  ├── is_system_role
  └── permissions → resolved through RolePermission

Permission
  ├── permission_code (PK, e.g. invoice.void)
  ├── module
  ├── action
  └── description

RolePermission
  ├── role_id → Role
  └── permission_code → Permission

UserSession
  ├── session_id
  ├── user_id → User
  ├── token_hash
  ├── client_id
  ├── ip_address
  ├── created_at
  ├── last_activity_at
  ├── expires_at
  └── revoked_at / revoked_reason
```

## 15.2 Default Users

On first launch, the system creates:

| Username | Password | Role | Notes |
|---|---|---|---|
| admin | (set on first login) | ADMIN | Must change password on first login |
| manager | (set on first login) | MANAGER | Must change password on first login |
| cashier | (set on first login) | CASHIER | Must change password on first login |

## 15.3 Password Reset

- Admin can reset any user's password
- User can reset own password (requires current password)
- Reset link not available in MVP (email not integrated)
- Temporary password: random 8-char string, must change on next login

## 15.4 User CRUD

- Admin can: create, edit, deactivate, reactivate, reset password
- Manager can: view user list (read-only)
- Cashier cannot access user management

## 15.5 Login History

| Field | Notes |
|---|---|
| user_id | Who logged in |
| timestamp | When |
| ip_address | Client IP |
| client_id | Client machine identifier |
| success | true/false |
| failure_reason | Wrong password, locked account, etc. |

---

# 16. UI Screen Specifications (MVP)

## 16.1 Login Screen

```
┌─────────────────────────────────────────┐
│                                         │
│           Bizco Logo                     │
│                                         │
│  Username: [________________]           │
│  Password: [________________]           │
│                                         │
│  [  Login  ]                            │
│                                         │
│  Version 1.0.0                          │
└─────────────────────────────────────────┘
```

## 16.2 Dashboard Screen

```
┌─────────────────────────────────────────────────────────┐
│  Dashboard                    [Today: 2026-04-01]       │
├─────────────────────────────────────────────────────────┤
│ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│ │Today Sales│ │ Monthly  │ │ Outstanding│ │ Low Stock│   │
│ │ Rs. 45,000│ │ Rs. 1.2M│ │ Rs. 230K │ │ 12 items │   │
│ └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
│                                                         │
│ ┌──────────────────────┐ ┌──────────────────────┐      │
│ │  Daily Sales Chart   │ │  Top Products        │      │
│ │  [Line Chart]        │ │  [Bar Chart]         │      │
│ └──────────────────────┘ └──────────────────────┘      │
│                                                         │
│ ┌──────────────────────┐ ┌──────────────────────┐      │
│ │  Recent Invoices     │ │  Today's Appts       │      │
│ │  [Table List]        │ │  [Table List]        │      │
│ └──────────────────────┘ └──────────────────────┘      │
└─────────────────────────────────────────────────────────┘
```

## 16.3 POS Screen

```
┌─────────────────────────────────────────────────────────┐
│  Bizco POS                         [Cashier: John]  [●]  │
├────────────────────────────┬────────────────────────────┤
│                            │ Customer: [Search...    ▼] │
│  Barcode: [_____________]  │                            │
│                            │ ┌────────────────────────┐ │
│  ┌──────────────────────┐  │ │ #  │ Item    │ Qty │Tot│ │
│  │ Product Grid         │  │ │────│─────────│─────│───│ │
│  │ ┌────┐ ┌────┐ ┌────┐│  │ │ 1  │ Item A  │  2  │...│ │
│  │ │ P1 │ │ P2 │ │ P3 ││  │ │ 2  │ Item B  │  1  │...│ │
│  │ └────┘ └────┘ └────┘│  │ │ 3  │ Item C  │  3  │...│ │
│  │ ┌────┐ ┌────┐ ┌────┐│  │ └────────────────────────┘ │
│  │ │ P4 │ │ P5 │ │ P6 ││  │                            │
│  │ └────┘ └────┘ └────┘│  │ Subtotal:      Rs. 5,000.00│
│  └──────────────────────┘  │ Discount:        (500.00)  │
│                            │ VAT (18%):        810.00   │
│                            │ Total:          Rs. 5,310.00│
│                            │                            │
│                            │ [Hold] [Disc] [Pay] [Void] │
├────────────────────────────┴────────────────────────────┤
│  Status: ● ONLINE              Held Bills: [3]          │
└─────────────────────────────────────────────────────────┘
```

## 16.4 Appointment Calendar Screen

```
┌─────────────────────────────────────────────────────────┐
│  Appointments Calendar                                  │
├─────────────────────────────────────────────────────────┤
│  [Today] [Week] [Month]     Technician: [All ▼]        │
│                                                         │
│  ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┐         │
│  │ Mon │ Tue │ Wed │ Thu │ Fri │ Sat │ Sun │         │
│  ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤         │
│  │     │  1  │  2  │  3  │  4  │  5  │  6  │         │
│  │     │ 2apt│ 3apt│ 1apt│ 4apt│ 2apt│     │         │
│  └─────┴─────┴─────┴─────┴─────┴─────┴─────┘         │
│                                                         │
│  Today's Schedule (April 1, 2026):                      │
│  ┌──────┬────────┬──────────┬──────────┬────────┐     │
│  │ Time │ Customer│ Service  │ Technician│ Status │     │
│  ├──────┼────────┼──────────┼──────────┼────────┤     │
│  │ 09:00│ Kumara │ Phone Fix│ Perera   │ Conf.  │     │
│  │ 10:30│ Silva  │ Laptop   │ Fernando │ Sched. │     │
│  │ 14:00│ Perera │ TV Repair│ Perera   │ Conf.  │     │
│  └──────┴────────┴──────────┴──────────┴────────┘     │
│                                                         │
│  [+ New Appointment]  [View Job Cards]                  │
└─────────────────────────────────────────────────────────┘
```

---

# 17. API Contracts (MVP)

## 17.1 Authentication

```text
POST   /api/auth/login          → { token, user, expiresAt }
POST   /api/auth/logout         → 200 OK
GET    /api/auth/me             → { user profile with effective permissions }
POST   /api/auth/change-password → 200 OK
```

## 17.2 User Management

```text
GET    /api/users               → { list, pagination }
GET    /api/users/{id}          → { user details with primary + secondary roles }
POST   /api/users               → { user created }
PUT    /api/users/{id}          → { user updated }
PUT    /api/users/{id}/status   → { activate/deactivate }
POST   /api/users/{id}/reset-password → { password reset }
POST   /api/users/{id}/lock     → { user locked }
POST   /api/users/{id}/unlock   → { user unlocked }
```

## 17.3 Role Management

```text
GET    /api/roles               → { list of roles with permissions }
GET    /api/roles/{id}          → { role details with permissions }
POST   /api/roles               → { role created }
PUT    /api/roles/{id}          → { role updated }
DELETE /api/roles/{id}          → 200 OK
POST   /api/roles/{id}/permissions → { permissions assigned }
DELETE /api/roles/{id}/permissions/{permission} → { permission removed }
GET    /api/permissions         → { list of all available permissions }
```

## 17.4 Secondary Roles (Multi-Role)

```text
GET    /api/users/{id}/roles    → { primary role + secondary roles list }
POST   /api/users/{id}/roles    → { secondary role granted }
  Body: { role_id, expires_at, reason }
DELETE /api/users/{id}/roles/{user_role_id} → { secondary role revoked }
  Body: { reason }
GET    /api/users/{id}/effective-permissions → { merged permissions list }
```

## 17.5 Customers

```text
GET    /api/customers           → { list, pagination }
GET    /api/customers/{id}      → { customer details }
POST   /api/customers           → { customer created }
PUT    /api/customers/{id}      → { customer updated }
GET    /api/customers/search?q= → { search results }
GET    /api/customers/{id}/ledger → { transaction history }
```

## 17.6 Products

```text
GET    /api/products            → { list, pagination }
GET    /api/products/{id}       → { product details }
POST   /api/products            → { product created }
PUT    /api/products/{id}       → { product updated }
GET    /api/products/search?q=  → { search results }
GET    /api/products/barcode/{code} → { product by barcode }
```

## 17.7 Categories

```text
GET    /api/categories          → { tree structure }
POST   /api/categories          → { category created }
PUT    /api/categories/{id}     → { category updated }
DELETE /api/categories/{id}     → 200 OK
```

## 17.8 Invoices

```text
GET    /api/invoices            → { list, pagination, filters }
GET    /api/invoices/{id}       → { invoice with line items }
POST   /api/invoices            → { draft invoice created }
POST   /api/invoices/{id}/post   → { invoice posted with official number }
POST   /api/invoices/{id}/void   → { invoice voided/reversed }
GET    /api/invoices/{id}/pdf   → PDF file
POST   /api/invoices/{id}/reprint → 200 OK
```

## 17.9 Credit Notes

```text
POST   /api/credit-notes        → { credit note created }
GET    /api/credit-notes/{id}   → { credit note details }
```

## 17.10 Payments

```text
POST   /api/invoices/{id}/payments → { payment recorded }
GET    /api/invoices/{id}/payments → { payment history }
GET    /api/held-sales           → { active held bills }
POST   /api/held-sales           → { held bill created/reserved }
POST   /api/held-sales/{id}/resume → { held bill resumed }
POST   /api/held-sales/{id}/cancel → { reservation released }
```

## 17.11 Appointments

```text
GET    /api/appointments        → { list, pagination, filters }
GET    /api/appointments/{id}   → { appointment details }
POST   /api/appointments        → { appointment created }
PUT    /api/appointments/{id}   → { appointment updated }
POST   /api/appointments/{id}/status → { status changed }
POST   /api/appointments/{id}/convert-to-job → { job card created }
```

## 17.12 Job Cards

```text
GET    /api/job-cards           → { list, pagination, filters }
GET    /api/job-cards/{id}      → { job card with services, parts }
POST   /api/job-cards           → { job card created }
PUT    /api/job-cards/{id}      → { job card updated }
POST   /api/job-cards/{id}/status → { status changed }
POST   /api/job-cards/{id}/parts → { part added }
POST   /api/job-cards/{id}/estimate → { estimate created }
POST   /api/job-cards/{id}/estimate/accept → { estimate accepted }
```


## 17.13 Inventory

```text
GET    /api/inventory           → { stock on hand }
POST   /api/stock-adjustments   → { adjustment created }
GET    /api/stock-adjustments   → { adjustment history }
```

## 17.14 Suppliers & Purchasing

```text
GET    /api/suppliers           → { list, pagination, balances }
GET    /api/suppliers/{id}      → { supplier details, GRNs, payments }
POST   /api/suppliers           → { supplier created }
PUT    /api/suppliers/{id}      → { supplier updated }
POST   /api/suppliers/{id}/deactivate → { supplier deactivated }
POST   /api/grn                 → { posted GRN and stock movements }
POST   /api/supplier-payments   → { supplier payment recorded with one or more GRN allocations }
GET    /api/supplier-payments/{id}/allocations → { allocation breakdown }
POST   /api/supplier-returns    → { supplier return, stock movements, payable adjustment }
GET    /api/products/{id}/cost-history → { purchase cost history }
```

## 17.15 Finance

```text
GET    /api/finance/receivables → { customer balances }
GET    /api/finance/payables    → { supplier balances }
GET    /api/finance/cashbook    → { cashbook entries }
POST   /api/finance/cashbook    → { manual cashbook entry }
POST   /api/finance/cash-closings → { daily cash closing created }
POST   /api/finance/cash-closings/{id}/approve → { variance approved }
```

## 17.16 Reports

```text
GET    /api/reports/daily-sales?date= → { sales summary }
GET    /api/reports/sales-by-product?date= → { product sales }
GET    /api/reports/stock-on-hand → { stock report }
GET    /api/reports/customer-balances → { receivables }
GET    /api/reports/supplier-balances → { payables }
GET    /api/reports/cashbook?from=&to= → { cash and bank movements }
GET    /api/reports/cash-closings?from=&to= → { daily closing variances }
GET    /api/reports/appointments?from=&to= → { appointment summary }
GET    /api/reports/tax-summary?from=&to= → { VAT summary }
```


## 17.17 Dashboard

```text
GET    /api/dashboard/kpis      → { KPI data }
GET    /api/dashboard/sales-chart?days= → { chart data }
GET    /api/dashboard/top-products?limit= → { top products }
```

## 17.18 Settings & Backup

```text
GET    /api/settings/business   → { business profile }
PUT    /api/settings/business   → { profile updated }
GET    /api/settings/tax        → { tax config }
PUT    /api/settings/tax        → { tax config updated }
POST   /api/system/backups      → { backup job and verification status }
GET    /api/system/backups      → { backup history }
POST   /api/system/backups/{id}/restore → { restore job status }
```

---

## 17.19 Critical Command Idempotency

Critical posting APIs accept a client-generated idempotency/request key. Repeating the same committed request after a timeout or lost response returns the existing posting result rather than creating a duplicate transaction.

Applies at minimum to:
- invoice posting
- invoice payment/refund
- credit note posting
- GRN posting
- supplier payment
- supplier return
- stock-adjustment approval
- cash closing

---

# 18. Database Schema (MVP - CREATE TABLE)

The schema below is PostgreSQL-native. UUID identifiers are generated by PostgreSQL, enum types are declared explicitly, and `updated_at` values are maintained by the application or a shared Flyway-managed trigger.

**v1.4 note:** this section predates the Week 12 scope-expansion decision and does not yet contain CREATE TABLE statements for brands, attributes, product_variants, bill_of_materials, packages, promotions, loyalty_transactions, supplier_products, purchase_orders, or goods_receipts. `DatabaseDesign.md` §§55–61 has the authoritative entity design for all of these; this section's DDL will be brought in line with it as each new phase (`DevelopmentPlan.md` Phases 6–9) is actually implemented, the same way every table below was added incrementally as its own phase landed rather than pre-written in full on day one.

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE customer_category AS ENUM ('RETAIL', 'WHOLESALE', 'CORPORATE');
CREATE TYPE customer_status AS ENUM ('ACTIVE', 'BLOCKED');
CREATE TYPE product_type AS ENUM ('INVENTORY', 'SERVICE');
CREATE TYPE tax_category AS ENUM ('STANDARD', 'EXEMPT', 'ZERO_RATED');
CREATE TYPE supplier_status AS ENUM ('ACTIVE', 'INACTIVE');
CREATE TYPE invoice_type AS ENUM ('SALES', 'SERVICE', 'TAX');
CREATE TYPE invoice_status AS ENUM ('DRAFT', 'POSTED', 'VOIDED');
CREATE TYPE invoice_line_type AS ENUM ('PRODUCT', 'SERVICE', 'CUSTOM');
CREATE TYPE held_sale_status AS ENUM ('HELD', 'RESUMED', 'CANCELLED', 'EXPIRED', 'CONVERTED');
CREATE TYPE grn_status AS ENUM ('DRAFT', 'POSTED', 'REVERSED');
CREATE TYPE cash_closing_status AS ENUM ('PENDING_APPROVAL', 'APPROVED');
CREATE TYPE discount_type AS ENUM ('NONE', 'PERCENTAGE', 'FIXED');
CREATE TYPE payment_status AS ENUM ('UNPAID', 'PARTIAL', 'PAID', 'CREDIT_NOTE');
CREATE TYPE payment_method AS ENUM ('CASH', 'CARD', 'BANK_TRANSFER', 'CHEQUE');
CREATE TYPE credit_note_status AS ENUM ('ISSUED', 'APPLIED');
CREATE TYPE appointment_status AS ENUM ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW', 'CANCELLED');
CREATE TYPE job_card_status AS ENUM ('CREATED', 'ESTIMATE_PENDING', 'ESTIMATE_APPROVED', 'IN_PROGRESS', 'READY_FOR_PICKUP', 'COMPLETED', 'CANCELLED');
CREATE TYPE work_status AS ENUM ('PENDING', 'IN_PROGRESS', 'COMPLETED');
CREATE TYPE customer_response AS ENUM ('PENDING', 'ACCEPTED', 'DECLINED');
CREATE TYPE stock_movement_type AS ENUM ('GRN', 'SALE', 'CUSTOMER_RETURN', 'SUPPLIER_RETURN', 'JOB_PART', 'ADJUSTMENT');
CREATE TYPE adjustment_type AS ENUM ('POSITIVE', 'NEGATIVE', 'DAMAGE');
CREATE TYPE approval_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
CREATE TYPE audit_action AS ENUM ('CREATE', 'UPDATE', 'DELETE', 'POST', 'VOID', 'APPROVE', 'REJECT', 'REVERSE', 'LOGIN', 'LOGOUT', 'GRANT_ROLE', 'REVOKE_ROLE', 'BACKUP', 'RESTORE');
CREATE TYPE cashbook_direction AS ENUM ('IN', 'OUT');
CREATE TYPE cashbook_source AS ENUM ('CUSTOMER_PAYMENT', 'REFUND', 'SUPPLIER_PAYMENT', 'MANUAL');

-- Users & Roles
CREATE TABLE permissions (
    permission_code VARCHAR(100) PRIMARY KEY,
    module VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE roles (
    role_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission_code),
    FOREIGN KEY (role_id) REFERENCES roles(role_id),
    FOREIGN KEY (permission_code) REFERENCES permissions(permission_code)
);

CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100),
    email VARCHAR(150),
    phone VARCHAR(20),
    primary_role_id BIGINT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    is_locked BOOLEAN DEFAULT FALSE,
    failed_login_attempts INT DEFAULT 0,
    locked_until TIMESTAMP NULL,
    password_changed_at TIMESTAMP,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (primary_role_id) REFERENCES roles(role_id)
);

-- Secondary Roles (Temporary Role Assignments)
CREATE TABLE user_roles (
    user_role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    role_id BIGINT NOT NULL,
    granted_by UUID NOT NULL,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    is_active BOOLEAN DEFAULT TRUE,
    revoked_at TIMESTAMP NULL,
    revoked_by UUID,
    revoke_reason TEXT,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (role_id) REFERENCES roles(role_id),
    FOREIGN KEY (granted_by) REFERENCES users(user_id),
    FOREIGN KEY (revoked_by) REFERENCES users(user_id)
);

-- Server-side Sessions
CREATE TABLE user_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    client_id VARCHAR(100),
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(200),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Business Profile
CREATE TABLE business_profile (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    business_name VARCHAR(200) NOT NULL,
    address_line1 VARCHAR(200),
    address_line2 VARCHAR(200),
    city VARCHAR(100),
    province VARCHAR(100),
    postal_code VARCHAR(10),
    phone VARCHAR(20),
    email VARCHAR(150),
    website VARCHAR(200),
    tin_number VARCHAR(20),
    vat_registered BOOLEAN DEFAULT FALSE,
    vat_rate DECIMAL(5,2) DEFAULT 18.00,
    logo_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Customers
CREATE TABLE customers (
    customer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(150),
    address_line1 VARCHAR(200),
    address_line2 VARCHAR(200),
    city VARCHAR(100),
    nic_number VARCHAR(20),
    br_number VARCHAR(20),
    category customer_category DEFAULT 'RETAIL',
    credit_limit DECIMAL(15,2) DEFAULT 0,
    current_balance DECIMAL(15,2) DEFAULT 0,
    status customer_status DEFAULT 'ACTIVE',
    consent_marketing BOOLEAN DEFAULT FALSE,
    consent_data_sharing BOOLEAN DEFAULT FALSE,
    consent_date TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Product Categories
CREATE TABLE product_categories (
    category_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT NULL,
    description VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES product_categories(category_id)
);

-- UOM
CREATE TABLE uom (
    uom_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    category VARCHAR(50)
);

-- Products
CREATE TABLE products (
    product_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(20) NOT NULL UNIQUE,
    barcode VARCHAR(50) UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category_id BIGINT,
    uom_id BIGINT,
    product_type product_type DEFAULT 'INVENTORY',
    tax_category tax_category DEFAULT 'STANDARD',
    cost_price DECIMAL(15,2) NOT NULL DEFAULT 0,
    selling_price DECIMAL(15,2) NOT NULL,
    wholesale_price DECIMAL(15,2),
    reorder_point INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    image_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES product_categories(category_id),
    FOREIGN KEY (uom_id) REFERENCES uom(uom_id)
);

-- Suppliers
CREATE TABLE suppliers (
    supplier_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    contact_person VARCHAR(100),
    address VARCHAR(300),
    phone VARCHAR(20),
    email VARCHAR(150),
    tin_number VARCHAR(20),
    payment_terms VARCHAR(50),
    opening_balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    status supplier_status DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Services (Service Catalog)
CREATE TABLE services (
    service_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    base_price DECIMAL(15,2) NOT NULL,
    estimated_duration_minutes INT NOT NULL,
    requires_estimate BOOLEAN DEFAULT FALSE,
    warranty_days INT DEFAULT 30,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Document Sequences (official numbers allocated on posting)
CREATE TABLE document_sequences (
    document_type VARCHAR(30) NOT NULL,
    business_date DATE NOT NULL,
    last_number INT NOT NULL DEFAULT 0 CHECK (last_number >= 0),
    PRIMARY KEY (document_type, business_date)
);

-- Invoices
CREATE TABLE invoices (
    invoice_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    invoice_number VARCHAR(30) UNIQUE,
    invoice_date DATE NOT NULL,
    due_date DATE,
    invoice_type invoice_type NOT NULL DEFAULT 'SALES',
    invoice_status invoice_status NOT NULL DEFAULT 'DRAFT',
    customer_id UUID,
    user_id UUID NOT NULL,
    business_name_snapshot VARCHAR(200),
    business_address_snapshot VARCHAR(500),
    business_tin_snapshot VARCHAR(30),
    customer_name_snapshot VARCHAR(200),
    customer_address_snapshot VARCHAR(500),
    customer_tin_snapshot VARCHAR(30),
    subtotal DECIMAL(15,2) NOT NULL DEFAULT 0,
    discount_type discount_type NOT NULL DEFAULT 'NONE',
    discount_value DECIMAL(15,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    taxable_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    vat_rate DECIMAL(5,2) NOT NULL DEFAULT 18.00,
    vat_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    amount_paid DECIMAL(15,2) NOT NULL DEFAULT 0,
    balance_due DECIMAL(15,2) NOT NULL DEFAULT 0,
    payment_status payment_status NOT NULL DEFAULT 'UNPAID',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at TIMESTAMPTZ,
    voided_at TIMESTAMPTZ,
    voided_by UUID,
    void_reason TEXT,
    CHECK (
      (invoice_status = 'DRAFT' AND invoice_number IS NULL AND posted_at IS NULL)
      OR
      (invoice_status IN ('POSTED','VOIDED') AND invoice_number IS NOT NULL AND posted_at IS NOT NULL)
    ),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (voided_by) REFERENCES users(user_id)
);

-- Invoice Line Items
CREATE TABLE invoice_line_items (
    line_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL,
    line_type invoice_line_type NOT NULL,
    product_id UUID,
    service_id UUID,
    sku_snapshot VARCHAR(50),
    description_snapshot VARCHAR(250) NOT NULL,
    uom_snapshot VARCHAR(20),
    quantity DECIMAL(10,3) NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(15,2) NOT NULL CHECK (unit_price >= 0),
    discount_type discount_type NOT NULL DEFAULT 'NONE',
    discount_value DECIMAL(15,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    tax_category_snapshot tax_category NOT NULL,
    vat_rate_snapshot DECIMAL(5,2) NOT NULL DEFAULT 0,
    taxable_amount DECIMAL(15,2) NOT NULL,
    vat_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    line_total_incl_vat DECIMAL(15,2) NOT NULL,
    CHECK (
      (line_type = 'PRODUCT' AND product_id IS NOT NULL)
      OR (line_type = 'SERVICE' AND service_id IS NOT NULL)
      OR (line_type = 'CUSTOM')
    ),
    FOREIGN KEY (invoice_id) REFERENCES invoices(invoice_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (service_id) REFERENCES services(service_id)
);

-- Invoice Payments
CREATE TABLE invoice_payments (
    payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    invoice_id UUID NOT NULL,
    payment_method payment_method NOT NULL,
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    reference_number VARCHAR(100),
    payment_date TIMESTAMPTZ NOT NULL,
    received_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (invoice_id) REFERENCES invoices(invoice_id),
    FOREIGN KEY (received_by) REFERENCES users(user_id)
);

-- Held POS Sales / Reservations
CREATE TABLE held_sales (
    held_sale_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    held_number VARCHAR(30) NOT NULL UNIQUE,
    customer_id UUID,
    cashier_id UUID NOT NULL,
    status held_sale_status NOT NULL DEFAULT 'HELD',
    held_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    converted_invoice_id UUID,
    notes TEXT,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (cashier_id) REFERENCES users(user_id),
    FOREIGN KEY (converted_invoice_id) REFERENCES invoices(invoice_id)
);

CREATE TABLE held_sale_items (
    held_sale_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    held_sale_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity DECIMAL(15,3) NOT NULL CHECK (quantity > 0),
    unit_price_snapshot DECIMAL(15,2) NOT NULL,
    discount_type discount_type NOT NULL DEFAULT 'NONE',
    discount_value DECIMAL(15,2) NOT NULL DEFAULT 0,
    FOREIGN KEY (held_sale_id) REFERENCES held_sales(held_sale_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- Credit Notes
CREATE TABLE credit_notes (
    credit_note_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_note_number VARCHAR(30) NOT NULL UNIQUE,
    original_invoice_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    reason TEXT,
    subtotal DECIMAL(15,2) NOT NULL DEFAULT 0,
    vat_amount DECIMAL(15,2) DEFAULT 0,
    total_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    status credit_note_status DEFAULT 'ISSUED',
    issued_date TIMESTAMP NOT NULL,
    issued_by UUID NOT NULL,
    FOREIGN KEY (original_invoice_id) REFERENCES invoices(invoice_id),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (issued_by) REFERENCES users(user_id)
);

-- Credit Note Line Items
CREATE TABLE credit_note_line_items (
    cn_line_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_note_id UUID NOT NULL,
    invoice_line_item_id UUID NOT NULL,
    quantity_returned DECIMAL(10,3) NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    line_total DECIMAL(15,2) NOT NULL,
    vat_amount DECIMAL(15,2) DEFAULT 0,
    FOREIGN KEY (credit_note_id) REFERENCES credit_notes(credit_note_id),
    FOREIGN KEY (invoice_line_item_id) REFERENCES invoice_line_items(line_item_id)
);

-- Appointments
CREATE TABLE appointments (
    appointment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_number VARCHAR(30) NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    service_id UUID NOT NULL,
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    technician_id UUID,
    status appointment_status DEFAULT 'SCHEDULED',
    notes TEXT,
    is_walk_in BOOLEAN DEFAULT FALSE,
    converted_to_job_card_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (service_id) REFERENCES services(service_id),
    FOREIGN KEY (technician_id) REFERENCES users(user_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- Job Cards
CREATE TABLE job_cards (
    job_card_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_number VARCHAR(30) NOT NULL UNIQUE,
    appointment_id UUID,
    customer_id UUID NOT NULL,
    device_type VARCHAR(50),
    brand VARCHAR(100),
    model VARCHAR(100),
    serial_number VARCHAR(100),
    reported_issue TEXT,
    customer_notes TEXT,
    accessories_received TEXT,
    device_condition TEXT,
    status job_card_status DEFAULT 'CREATED',
    technician_id UUID,
    estimated_completion_date DATE,
    actual_completion_date DATE,
    pickup_date DATE,
    warranty_end_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (appointment_id) REFERENCES appointments(appointment_id),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (technician_id) REFERENCES users(user_id)
);

-- Job Services
CREATE TABLE job_services (
    job_service_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_card_id UUID NOT NULL,
    service_id UUID NOT NULL,
    estimated_cost DECIMAL(15,2),
    actual_cost DECIMAL(15,2),
    estimated_duration_minutes INT,
    status work_status DEFAULT 'PENDING',
    notes TEXT,
    FOREIGN KEY (job_card_id) REFERENCES job_cards(job_card_id),
    FOREIGN KEY (service_id) REFERENCES services(service_id)
);

-- Job Parts
CREATE TABLE job_parts (
    job_part_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_card_id UUID NOT NULL,
    product_id UUID NOT NULL,
    qty_used DECIMAL(10,3) NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    cost_price DECIMAL(15,2),
    is_warranty_covered BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (job_card_id) REFERENCES job_cards(job_card_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- Job Estimates
CREATE TABLE job_estimates (
    estimate_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_card_id UUID NOT NULL,
    estimated_total DECIMAL(15,2) NOT NULL,
    description TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    customer_response customer_response DEFAULT 'PENDING',
    customer_response_at TIMESTAMP,
    notes TEXT,
    FOREIGN KEY (job_card_id) REFERENCES job_cards(job_card_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- Stock Movements
CREATE TABLE stock_movements (
    movement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    movement_type stock_movement_type NOT NULL,
    quantity DECIMAL(15,3) NOT NULL CHECK (quantity <> 0),
    reference_type VARCHAR(50),
    reference_id UUID,
    notes TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- Stock Adjustments
CREATE TABLE stock_adjustments (
    adjustment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    adjustment_type adjustment_type NOT NULL,
    quantity DECIMAL(15,3) NOT NULL,
    reason TEXT,
    status approval_status DEFAULT 'PENDING',
    approved_by UUID,
    approved_at TIMESTAMP,
    created_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (approved_by) REFERENCES users(user_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- GRN (Goods Received Notes)
CREATE TABLE grn (
    grn_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    grn_number VARCHAR(30) UNIQUE,
    status grn_status NOT NULL DEFAULT 'DRAFT',
    supplier_id UUID NOT NULL,
    grn_date DATE NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    paid_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    balance_due DECIMAL(15,2) NOT NULL DEFAULT 0,
    notes TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at TIMESTAMPTZ,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

-- GRN Items
CREATE TABLE grn_items (
    grn_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grn_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity_received DECIMAL(15,3) NOT NULL,
    unit_cost DECIMAL(15,2) NOT NULL,
    total_cost DECIMAL(15,2) NOT NULL,
    FOREIGN KEY (grn_id) REFERENCES grn(grn_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- Product Purchase Cost History
CREATE TABLE product_cost_history (
    cost_history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    grn_item_id UUID NOT NULL UNIQUE,
    unit_cost DECIMAL(15,2) NOT NULL,
    effective_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (grn_item_id) REFERENCES grn_items(grn_item_id)
);

-- Supplier Payments
CREATE TABLE supplier_payments (
    supplier_payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    supplier_id UUID NOT NULL,
    payment_date TIMESTAMPTZ NOT NULL,
    payment_method payment_method NOT NULL,
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    reference_number VARCHAR(100),
    notes TEXT,
    paid_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (paid_by) REFERENCES users(user_id)
);

CREATE TABLE supplier_payment_allocations (
    supplier_payment_allocation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_payment_id UUID NOT NULL,
    grn_id UUID NOT NULL,
    allocated_amount DECIMAL(15,2) NOT NULL CHECK (allocated_amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (supplier_payment_id, grn_id),
    FOREIGN KEY (supplier_payment_id) REFERENCES supplier_payments(supplier_payment_id),
    FOREIGN KEY (grn_id) REFERENCES grn(grn_id)
);

-- Supplier Returns
CREATE TABLE supplier_returns (
    supplier_return_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    return_number VARCHAR(30) NOT NULL UNIQUE,
    supplier_id UUID NOT NULL,
    grn_id UUID,
    total_amount DECIMAL(15,2) NOT NULL CHECK (total_amount >= 0),
    reason TEXT NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (grn_id) REFERENCES grn(grn_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE TABLE supplier_return_items (
    supplier_return_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_return_id UUID NOT NULL,
    grn_item_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity_returned DECIMAL(15,3) NOT NULL CHECK (quantity_returned > 0),
    unit_cost DECIMAL(15,2) NOT NULL,
    line_total DECIMAL(15,2) NOT NULL,
    FOREIGN KEY (supplier_return_id) REFERENCES supplier_returns(supplier_return_id),
    FOREIGN KEY (grn_item_id) REFERENCES grn_items(grn_item_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- Basic Finance
CREATE TABLE cashbook_entries (
    cashbook_entry_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_date TIMESTAMP NOT NULL,
    direction cashbook_direction NOT NULL,
    source cashbook_source NOT NULL,
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    payment_method payment_method NOT NULL,
    category VARCHAR(100),
    reference_type VARCHAR(50),
    reference_id UUID,
    reason TEXT,
    created_by UUID NOT NULL,
    reversed_entry_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (reversed_entry_id) REFERENCES cashbook_entries(cashbook_entry_id)
);

CREATE TABLE cash_closings (
    cash_closing_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID UNIQUE,
    business_date DATE NOT NULL,
    cashier_id UUID NOT NULL,
    expected_cash DECIMAL(15,2) NOT NULL,
    counted_cash DECIMAL(15,2) NOT NULL,
    variance DECIMAL(15,2) NOT NULL,
    variance_reason TEXT,
    status cash_closing_status NOT NULL DEFAULT 'PENDING_APPROVAL',
    closed_by UUID NOT NULL,
    closed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_by UUID,
    approved_at TIMESTAMP,
    UNIQUE (business_date, cashier_id),
    FOREIGN KEY (cashier_id) REFERENCES users(user_id),
    FOREIGN KEY (closed_by) REFERENCES users(user_id),
    FOREIGN KEY (approved_by) REFERENCES users(user_id)
);

-- Tax Configuration
CREATE TABLE tax_config (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    setting_name VARCHAR(50) NOT NULL UNIQUE,
    setting_value VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    changed_by UUID,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (changed_by) REFERENCES users(user_id)
);

-- Audit Logs
CREATE TABLE audit_logs (
    log_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action audit_action NOT NULL,
    user_id UUID NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    changed_fields JSONB,
    ip_address VARCHAR(45),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- System Config
CREATE TABLE system_config (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value TEXT,
    description VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Login History
CREATE TABLE login_history (
    history_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id UUID,
    attempted_username VARCHAR(50) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    client_id VARCHAR(50),
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(100),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Backup History
CREATE TABLE backup_history (
    backup_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT,
    checksum_sha256 VARCHAR(64),
    status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'VERIFIED', 'FAILED', 'RESTORED')),
    initiated_by UUID NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    FOREIGN KEY (initiated_by) REFERENCES users(user_id)
);
```

---

# 19. Error Handling (MVP)

## 19.1 Error Response Format

```json
{
  "timestamp": "2026-04-01T10:30:45",
  "status": 400,
  "error": "Validation Failed",
  "message": "Phone number must be 10 digits starting with 0",
  "path": "/api/customers",
  "fieldErrors": [
    {
      "field": "phone",
      "message": "Must be 10 digits starting with 0"
    }
  ]
}
```

## 19.2 HTTP Status Codes

| Code | Usage |
|---|---|
| 200 | Success |
| 201 | Created |
| 400 | Validation error |
| 401 | Unauthorized (not logged in) |
| 403 | Forbidden (insufficient permissions) |
| 404 | Resource not found |
| 409 | Conflict (duplicate SKU, etc.) |
| 500 | Internal server error |

## 19.3 Client-Side Error Handling

- Network timeout: Show "Server unreachable" with retry button
- Validation errors: Inline messages below each field
- Business rule errors: Dialog with explanation
- Unknown errors: Generic error dialog with error ID

---

# 20. Data Validation (MVP)

| Field | Rule | Error Message |
|---|---|---|
| Phone | 10 digits, starts with 0 | "Phone must be 10 digits starting with 0" |
| Email | Valid format | "Invalid email address" |
| NIC | 12 digits or 9 digits + V/X | "Invalid NIC format" |
| SKU | 3-20 alphanumeric | "SKU must be 3-20 alphanumeric characters" |
| Barcode | EAN-13 (12+1 check digit) | "Invalid barcode format" |
| Quantity | Positive number | "Quantity must be positive" |
| Price | Non-negative | "Price cannot be negative" |
| Discount | 0-100% | "Discount must be between 0% and 100%" |
| VAT Rate | 0-100% | "VAT rate must be between 0% and 100%" |
| Date | Valid date, not future (for GRN) | "Date cannot be in the future" |
| Required fields | Not empty | "This field is required" |

---

# 21. Barcode Generation (MVP)

## 21.1 Barcode Types

| Type | Length | Usage |
|---|---|---|
| EAN-13 | 13 digits | Products (international standard) |
| CODE-128 | Variable | Internal use, invoices, GRN |
| QR Code | Variable | Receipts, invoices (URL to digital copy) |

## 21.2 Barcode Generation

- Products: System can generate EAN-13 barcodes if not provided
- Invoices: QR code generated containing invoice reference
- Receipts: QR code for digital receipt link (future)

## 21.3 Barcode Rules

- Each product can have one primary barcode
- Barcode must be unique across all products
- System validates check digit for EAN-13
- Barcode printed on product labels and receipts

---

# 22. Receipt Template (MVP)

```text
┌─────────────────────────────┐
│      BUSINESS NAME          │
│      Address Line 1         │
│      Address Line 2         │
│      Tel: 011-XXXXXXX       │
│      TIN: XXXXXXXXXXXX      │
├─────────────────────────────┤
│         TAX INVOICE         │
├─────────────────────────────┤
│ Invoice: INV-20260401-0042  │
│ Date: 2026-04-01 10:30      │
│ Cashier: John               │
│ Customer: Kumara (Optional)  │
├─────────────────────────────┤
│ Item          Qty  Price Tot │
│ ─────────────────────────── │
│ Phone Case     2  1,500 3,000│
│ Screen Prot.   3    500 1,500│
│ ─────────────────────────── │
│ Subtotal:         4,500.00  │
│ Discount:         (500.00)  │
│ Taxable:          4,000.00  │
│ VAT (18%):          720.00  │
│ ═══════════════════════════ │
│ TOTAL:           4,720.00   │
├─────────────────────────────┤
│ Payment: Cash      5,000.00 │
│ Change:              280.00 │
├─────────────────────────────┤
│     Thank you!              │
│     [QR Code]               │
└─────────────────────────────┘
```

---

# 23. Audit Logging (MVP)

## 23.1 What Gets Logged

| Action | Entity | Details |
|---|---|---|
| CREATE | Invoice | Invoice #, amount, customer |
| UPDATE | Invoice | Field changes, reason |
| VOID | Invoice | Invoice #, reason, user |
| CREATE | Credit Note | CN #, linked invoice, amount |
| CREATE | GRN | GRN #, supplier, items |
| ADJUST | Stock | Product, qty change, reason |
| LOGIN | User | Success/failure, IP |
| APPROVE | Discount | Discount %, amount, approver |
| APPROVE | Credit Override | Customer, amount, approver |

## 23.2 Log Format

```json
{
  "entityType": "INVOICE",
  "entityId": "INV-20260401-0042",
  "action": "CREATE",
  "userId": "john-user-id",
  "timestamp": "2026-04-01T10:30:45",
  "changedFields": {
    "total_amount": {"old": null, "new": 4720.00}
  },
  "ipAddress": "192.168.1.50"
}
```

---

# 24. Initial Setup & Onboarding (MVP)

## 24.1 First Launch Wizard

```text
Step 1: Business Profile
  → Business name, address, phone, TIN
  → VAT registered? (Yes/No)
  → Upload logo (optional)

Step 2: Tax Configuration
  → VAT rate (default: 18%)
  → Enable/disable VAT

Step 3: Admin Account
  → Set admin password
  → Confirm business details

Step 4: Ready!
  → "System is ready. Login with admin account."
```

## 24.2 Seed Data

On first launch, the system creates:
- 3 default users (admin, manager, cashier) with temporary passwords
- Default UOM list (PCS, KG, LTR, BOX, DOZ, BTL, CASE, MTR)
- Default tax config (VAT 18%, disabled)
- Empty business profile

## 24.3 Sample Data (Optional)

- 5 sample products
- 5 sample customers
- 3 sample services
- 1 sample invoice
- For demo/training purposes

---

# 25. Configuration Management (MVP)

## 25.1 System Config Keys

| Key | Default | Description |
|---|---|---|
| business.name | "" | Business name |
| business.tin | "" | TIN number |
| tax.vat_enabled | false | VAT collection on/off |
| tax.vat_rate | 18.0 | VAT rate % |
| pos.default_payment_method | CASH | Default payment method |
| pos.hold_bill_timeout | END_OF_DAY | Auto-release held bills |
| pos.receipt_printer | DEFAULT | Printer name |
| pos.cash_drawer | false | Cash drawer enabled |
| appointment.slot_duration | 30 | Minutes per slot |
| appointment.buffer_time | 15 | Minutes between appointments |
| appointment.business_hours_start | 08:00 | Start time |
| appointment.business_hours_end | 18:00 | End time |
| job_card.default_warranty_days | 30 | Warranty period |
| return.default_window_days | 7 | Return window |
| return.electronics_window_days | 14 | Electronics return window |
| backup.directory | ./backups | Server-local backup directory |
| backup.retention_count | 14 | Number of verified backups retained |
| finance.cash_variance_approval_required | true | Require manager approval for cash differences |

## 25.2 Config UI

- Settings screen accessible to Admin only
- Grouped by category (Business, Tax, POS, Appointments, Returns)
- Changes saved immediately with audit trail

---

# 26. Testing Strategy (MVP)

## 26.1 Test Types

| Type | Scope | Target |
|---|---|---|
| Unit Tests | Service layer, business logic | 80% line coverage |
| Integration Tests | API endpoints and PostgreSQL operations using Testcontainers | All critical endpoints |
| UI Tests | Critical POS flows | Happy path |
| UAT | Full workflows | Business owner sign-off |
| Recovery Test | Backup creation, verification, and restore | Successful clean-environment restore |

## 26.2 Critical Test Scenarios

1. Login → Dashboard → POS → Sale → Receipt
2. Customer creation → Invoice → Payment → Balance check
3. Product creation → GRN → Stock update → Sale → Stock deduction
4. Appointment → Job Card → Parts usage → Invoice → Pickup
5. Credit Note → Stock return → Balance adjustment
6. Discount approval → Manager PIN → Sale completion
7. Low stock alert → Dashboard indicator
8. Tax calculation → VAT summary report match
9. GRN → Stock ledger → Supplier payable → Partial payment → Balance reconciliation
10. POS payments and supplier payments → Cashbook → Daily closing variance
11. Backup → Checksum verification → Restore → Login and reconciliation checks

## 26.3 Test Data

- Seed script for demo environment
- Pre-loaded test customers, products, services
- Testcontainers starts an isolated supported PostgreSQL instance for integration tests

---

# 27. Success Criteria (MVP)

The MVP is considered successful when:

1. A cashier can scan a product, add to cart, apply discount, process payment, and print receipt
2. A manager can create a tax invoice with correct VAT calculation
3. A customer can be searched and linked to an invoice
4. An appointment can be scheduled on a calendar with technician assignment
5. A job card can be created from an appointment, tracked through completion, and invoiced
6. Stock is accurately tracked through GRN → Sale → Adjustment lifecycle
7. Daily sales report matches all invoices created that day
8. Supplier GRNs, payments, returns, and outstanding balances reconcile
9. Invoice totals, payments, credit notes, cashbook entries, and stock movements reconcile
10. A cashier can complete daily cash closing and a manager can resolve a variance
11. A verified backup can be restored into a clean supported PostgreSQL instance
12. The system runs in single-PC mode and on a LAN with 5+ concurrent POS terminals

---

# 28. MVP Business Scenario Catalogue

These scenarios are acceptance-level business narratives for realistic Sri Lankan SMEs. They do not add or remove MVP scope; they demonstrate how existing MVP capabilities must work together.

**v1.4 note:** SC-06 (Variant/Brand Retail, e.g. a clothing or electronics shop) and SC-07 (Manufacturing/BOM, e.g. a framing studio) referenced by `DevelopmentPlan.md` Week 31 (SC-06-001..002, SC-07-001) will be authored here once Phases 6–7 reach their own design gate, matching how each scenario below was written alongside its own phase.

## 28.1 SC-01 — Retail / Trading SME

**Examples:** mini supermarket, hardware shop, stationery shop, mobile phone shop.

### Primary Flow

```text
Business setup
→ Create users and roles
→ Create suppliers/categories/products
→ Receive stock through GRN
→ Product stock and cost history update
→ Customer/walk-in arrives
→ Scan/search product at POS
→ Select pricing tier/customer where applicable
→ Apply permitted discount
→ Pay by cash/card/bank/cheque or authorized credit
→ Post invoice
→ Deduct stock
→ Generate receipt/tax invoice
→ Create cashbook/receivable effects
→ Daily cash closing
→ Daily sales/stock/VAT reports
```

### Acceptance Rules

- Walk-in immediate sales may proceed without a registered customer where permitted.
- Credit sales require an identified eligible customer.
- A held bill reserves available stock without posting a sale.
- Sale posting is atomic across invoice, stock, payment/receivable, cashbook and audit effects.
- Returns use credit notes and restore stock only when the returned item is returnable.
- Low-stock alerts use available/physical stock rules consistently.

## 28.2 SC-02 — Wholesale / Credit Trading SME

**Examples:** small distributor, building-material supplier, office-supply wholesaler.

### Primary Flow

```text
Create wholesale/corporate customer
→ Set credit limit
→ Receive stock from supplier
→ Create sale using wholesale pricing
→ Part payment or no immediate payment
→ Post credit invoice
→ Receivable created
→ Later customer payment recorded
→ Outstanding balance updated from posted records
→ Customer-balance report reconciles
```

### Acceptance Rules

- Customer aging warnings/blocks are enforced at sale posting.
- Partial payment produces `PARTIAL`; zero payment may remain `UNPAID` when credit is authorized.
- A payment cannot make an invoice balance negative without an explicit refund/credit workflow.
- Receivable totals must reconcile to invoice, payment and credit-note source records.

## 28.3 SC-03 — Repair & Maintenance SME

**Examples:** phone repair, computer repair, electronics repair, small vehicle-service operation.

### Primary Flow

```text
Customer arrives / appointment exists
→ Create or select customer
→ Create appointment or walk-in appointment
→ Convert to job card
→ Record device/item condition and reported issue
→ Create estimate if required
→ Customer accepts
→ Technician starts job
→ Add service work
→ Consume inventory parts
→ Complete services
→ Mark ready for pickup
→ Generate service invoice from services + parts
→ Receive payment / authorized credit
→ Customer pickup
→ Job completed
→ Warranty dates retained
```

### Acceptance Rules

- Job-part consumption creates a posted `JOB_PART` stock movement exactly once.
- Estimate-required services cannot enter normal work without approval unless an authorized override rule applies.
- Job completion and invoice/pickup conditions follow the defined state machine.
- Historical job-part cost and customer charge values remain traceable.

## 28.4 SC-04 — Appointment-Based Service SME

**Examples:** salon, consultation business, photography/service studio, maintenance provider.

### Primary Flow

```text
Create service catalog
→ Customer books service
→ Select date/time
→ Assign technician/staff member
→ Server checks overlap
→ Appointment confirmed
→ Reschedule through calendar if needed
→ Customer arrives
→ Convert to job/service workflow where applicable
→ Service completed
→ Service invoice
→ Payment
→ Appointment/job reports
```

### Acceptance Rules

- Two concurrent users cannot successfully create overlapping active appointments for the same technician.
- Cancelled/no-show appointments do not permanently block the time slot.
- Drag-to-reschedule uses the same server-side conflict rules as normal appointment editing.
- Calendar views and appointment reports show the authoritative server state.

## 28.5 SC-05 — Hybrid Product + Service SME

**Examples:** mobile shop with repair, computer shop with IT support, hardware shop with installation.

### Primary Flow

```text
Products + services configured
→ Customer purchases product
→ Same customer may book service
→ Job may use stocked parts
→ Invoice can contain PRODUCT + SERVICE + CUSTOM lines
→ Split payment or authorized credit
→ Stock, receivable, cashbook and VAT effects post
→ Customer history shows connected commercial activity
```

### Acceptance Rules

- A single invoice may legally contain different line types supported by the MVP.
- Product lines affect stock; service/custom lines do not unless a separate job-part movement exists.
- Tax is calculated from each posted line's stored tax snapshot.
- Reports reconcile regardless of whether revenue originated from products, services, or both.

## 28.6 Cross-Scenario Recovery Scenario

```text
Normal business transactions
→ Administrator creates backup
→ Backup metadata/checksum recorded
→ Backup verified
→ Restore initiated under maintenance-safe conditions
→ Clean supported PostgreSQL instance restored
→ Application login succeeds
→ Flyway/schema version verified
→ Seeded reconciliation checks pass
```

The MVP is not production-ready until this scenario passes.

---

# 29. MVP Business Invariants

These invariants are mandatory implementation rules for the existing MVP requirements.

| ID | Invariant |
|---|---|
| INV-001 | Posted invoices are immutable. |
| INV-002 | Draft invoices have no stock, receivable, cashbook, or VAT-reporting effect. |
| INV-003 | Official invoice numbers are allocated only during posting using a concurrency-safe server mechanism. |
| INV-004 | Historical invoice values are based on stored document/line snapshots. |
| INV-005 | Credit sales require an identified customer who passes credit and aging rules. |
| PAY-001 | Posted payments are immutable; corrections use controlled refund/reversal behavior. |
| PAY-002 | A payment cannot be duplicated by retrying the same committed request. |
| STK-001 | `stock_movements` is authoritative for physical stock. |
| STK-002 | Posted stock movements are not edited or deleted. |
| STK-003 | Held bills reserve available stock but do not deduct physical stock. |
| STK-004 | Stock adjustments affect stock only after approval. |
| PUR-001 | A posted GRN is immutable. |
| PUR-002 | GRN posting atomically creates stock movements, cost history and supplier-payable effects. |
| PUR-003 | Supplier payments support full/partial allocation across one or more GRNs. |
| PUR-004 | Supplier returns are linked to original received items and cannot return more than eligible quantity. |
| FIN-001 | Receivables are derived/reconcilable from posted source records. |
| FIN-002 | Payables are derived/reconcilable from posted source records. |
| FIN-003 | Cashbook entries remain traceable to their monetary source transaction. |
| FIN-004 | An approved daily cash closing is immutable. |
| TAX-001 | Current tax configuration affects new postings only; historical posted tax snapshots do not change. |
| SEC-001 | Authorization is enforced by Spring Boot/server-side checks for every protected action. |
| SEC-002 | JavaFX permission-based hiding/disabling is not a security boundary. |
| SEC-003 | Effective permissions are the union of the primary role and active secondary roles. |
| SEC-004 | Expired/revoked secondary-role permissions cease to authorize the next protected action without requiring logout. |
| SEC-005 | `SUPER_ADMIN` owns all registered MVP permissions. |
| SCH-001 | A technician cannot have overlapping active appointments. |
| SCH-002 | Appointment conflict checks are concurrency-safe. |
| SYS-001 | Backup and restore attempts are audited. |
| SYS-002 | Restore is allowed only under maintenance-safe session conditions. |
| SYS-003 | Critical posting endpoints are idempotent. |
| AUD-001 | Historical posted records are corrected through explicit reversal/credit/return actions rather than silent edits. |
| AUD-002 | Security, approval, posting and recovery actions are attributable to the acting user/system event. |

---

# 30. Transaction Boundaries

The following operations must execute as one PostgreSQL transaction.

## 30.1 Sale Posting

```text
Validate user/customer/discount/credit/stock
→ Allocate official invoice number
→ Persist posted invoice + snapshots
→ Post SALE stock movements
→ Persist payments or receivable
→ Generate source-linked cashbook effects
→ Release held-sale reservation if applicable
→ Write audit event
→ COMMIT
```

Any failure rolls back the whole posting.

## 30.2 Credit Note / Customer Return Posting

```text
Validate original invoice + return eligibility
→ Allocate credit-note number
→ Persist credit note
→ Post eligible CUSTOMER_RETURN stock movements
→ Reduce receivable and/or post refund
→ Post cashbook effect if money leaves
→ Store tax reversal snapshot
→ Audit
→ COMMIT
```

## 30.3 Goods Receipt Posting (v1.4 — was "GRN Posting")

```text
Validate supplier/PO (if any)/items/date
→ Allocate goods-receipt number
→ Persist posted goods receipt/items (received/damaged/rejected qty)
→ Post stock movements for the usable received qty
→ Persist cost history
→ Create supplier-payable effect
→ Auto-close the PO if fully received
→ Audit
→ COMMIT
```

## 30.4 Supplier Return Posting

```text
Validate original goods-receipt quantities
→ Persist return
→ Post SUPPLIER_RETURN stock movements
→ Reduce supplier payable
→ Audit
→ COMMIT
```

## 30.5 Supplier Payment Posting

```text
Validate supplier + payment
→ Persist payment
→ Persist one or more GRN allocations
→ Reduce outstanding payable
→ Create source-linked cashbook OUT effect
→ Audit
→ COMMIT
```

## 30.6 Job Part Posting

```text
Validate job status/product/available stock
→ Persist JobPart
→ Post JOB_PART stock movement
→ Audit
→ COMMIT
```

## 30.7 Stock Adjustment Approval

```text
Lock pending adjustment
→ Validate approver
→ Mark APPROVED
→ Create ADJUSTMENT stock movement exactly once
→ Audit
→ COMMIT
```

## 30.8 Daily Cash Closing

```text
Calculate expected cash from posted cashbook sources
→ Capture counted cash
→ Calculate variance
→ Require reason when variance != 0
→ Persist closing
→ Approve where required
→ Audit
→ COMMIT
```

## 30.9 BOM Production Posting (v1.4)

```text
Lock every component variant in stable order (same pattern as a multi-line sale)
→ Validate available stock for every component
→ Post PRODUCTION_OUT stock movements for components
→ Post PRODUCTION_IN stock movement for the finished variant
→ Audit
→ COMMIT
```

Insufficient stock on any single component rolls back the whole production — never a partial one.

Package sales, promotion redemption, and loyalty earn/redeem do not get their own transaction boundary — they ride inside §30.1 Sale Posting (a package explodes to its component stock movements within the same posting transaction; a promotion adjusts the line/invoice total before the same snapshot/post step; loyalty points are earned/redeemed as part of the same commit).

---

# 31. Time, Concurrency & Historical Data Rules

## 31.1 Time

- Business timezone is configured as `Asia/Colombo` for Sri Lankan deployments.
- Business-only concepts such as invoice date, due date, GRN date and cash-closing business date use `DATE`.
- Actual moments such as created, posted, paid, granted, revoked, logged-in, backed-up and restored timestamps use timezone-aware timestamps (`TIMESTAMPTZ` in PostgreSQL).
- Java uses types that preserve the distinction (`LocalDate` for business dates; `Instant`/`OffsetDateTime` for instants).

## 31.2 Concurrency

The server/database is the authority for:
- document number allocation
- appointment overlap prevention
- duplicate SKU/barcode prevention
- stock posting
- adjustment approval
- supplier payment allocation
- cash closing uniqueness
- critical idempotency keys

A JavaFX pre-check improves usability but cannot replace the authoritative server/database check.

## 31.3 Historical Data

Changing any of the following must not silently rewrite a posted historical transaction:
- product name/SKU/UOM
- product price
- customer name/address/TIN
- business name/address/TIN
- tax category/rate
- discount rule
- supplier/product cost master values

Posted documents retain their original snapshots for printing, reporting and audit.

---

*(End of MVP Document)*
