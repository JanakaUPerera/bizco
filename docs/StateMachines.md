# Bizco MVP State Machines & Transition Rules

**Project:** SME Business Management System (Bizco)  
**Document:** State Machines & Transition Rules  
**Version:** 1.0  
**Status:** Pre-implementation design baseline  
**Based On:** `SRS.md` v2.1, `MVP.md` v1.3, `DevelopmentPlan.md` v2.0, `DomainModel.md` v1.0  
**Target:** JavaFX client + Spring Boot server + PostgreSQL  
**Scope Rule:** This document preserves the complete MVP scope. It does not remove or defer any requirement already included in MVP v1.3.

---

# 1. Purpose

This document defines the authoritative lifecycle rules for stateful Bizco MVP business objects.

It exists to ensure that:

- JavaFX screens expose only valid actions;
- Spring Boot application services enforce the same rules;
- PostgreSQL persistence cannot silently bypass critical state invariants;
- permissions are attached to business transitions rather than UI buttons;
- inventory and finance effects occur only on the correct transitions;
- audit events are predictable;
- automated tests can be derived directly from transition tables.

The state machines in this document are the implementation authority for MVP lifecycle behavior.

---

# 2. General State-Machine Rules

## 2.1 Server Authority

The JavaFX client may:

- hide unavailable actions;
- disable buttons;
- warn about invalid transitions;
- pre-check availability.

However, the Spring Boot server is authoritative.

A client request that attempts an invalid transition must be rejected even if the UI previously considered it valid.

## 2.2 Transition Atomicity

When a transition has business side effects, the state change and all required side effects occur in one PostgreSQL transaction.

Example:

```text
Invoice DRAFT → POSTED

must atomically include:

invoice status change
+ official number
+ stock movements
+ payment/receivable effects
+ cashbook effects
+ held-reservation release
+ audit
```

A transition is not considered successful until the entire transaction commits.

## 2.3 Posted Record Immutability

Once a transaction is posted/approved/completed in a way that creates ledger or financial effects:

- historical business values are not edited in place;
- corrections use explicit reversal, credit note, return, or other controlled corrective records;
- the original record remains available for audit.

## 2.4 Optimistic Concurrency

Mutable aggregates should carry a version.

Example:

```text
version = 7
```

A client editing version 7 must not overwrite a record already changed to version 8.

Recommended HTTP result:

```text
409 Conflict
CONCURRENT_MODIFICATION
```

## 2.5 Idempotency

Critical posting transitions require a request/idempotency key.

If a request was committed but the client did not receive the response, retrying the same key returns the existing result rather than re-running the transaction.

## 2.6 Permission Evaluation

Permissions are resolved from:

```text
primary role
+
all currently active secondary roles
```

Authorization is checked at transition execution time.

Expired/revoked secondary-role permissions do not remain valid merely because a screen was already open.

## 2.7 Audit

Every security-sensitive, financial, stock, approval, reversal, recovery, and role transition produces an audit event.

---

# 3. Transition Result Contract

Application services should conceptually return:

```text
TransitionResult
├── aggregateId
├── previousState
├── newState
├── documentNumber?
├── committedAt
├── warnings[]
└── version
```

Invalid transitions return a stable domain error such as:

```text
INVOICE_INVALID_STATE
APPOINTMENT_INVALID_TRANSITION
JOB_INVALID_TRANSITION
STOCK_ADJUSTMENT_ALREADY_DECIDED
```

---

# 4. Invoice State Machine

## 4.1 States

```text
DRAFT
POSTED
VOIDED
```

Payment status is separate:

```text
UNPAID
PARTIAL
PAID
CREDIT_NOTE
```

A posted invoice may therefore be:

```text
POSTED + UNPAID
POSTED + PARTIAL
POSTED + PAID
```

## 4.2 Diagram

```text
                 post
        ┌───────────────────┐
        │                   ▼
   ┌─────────┐         ┌─────────┐
   │  DRAFT  │         │ POSTED  │
   └─────────┘         └────┬────┘
                            │ authorized void
                            ▼
                       ┌─────────┐
                       │ VOIDED  │
                       └─────────┘
```

## 4.3 Allowed Transitions

| From | To | Command | Permission | Required Preconditions |
|---|---|---|---|---|
| DRAFT | POSTED | `PostInvoice` | `invoice.create` | valid lines, valid tax snapshots, stock available for product lines, discount approvals valid, credit rules valid, payment rule valid |
| POSTED | VOIDED | `VoidInvoice` | `invoice.void` | void allowed by business rule, reason required, reversal plan valid |

## 4.4 DRAFT → POSTED

### Preconditions

- invoice exists and status = DRAFT;
- at least one valid line;
- all quantities positive;
- customer required for credit sale;
- walk-in permitted only for allowed immediate-payment sale;
- product/service references active or otherwise valid for posting;
- product lines have sufficient available stock;
- discount approval tier satisfied;
- below-cost override permission/reason satisfied if needed;
- credit limit and aging policy passed for credit sale;
- immediate-payment sale reaches zero balance;
- VAT/tax configuration resolved;
- idempotency key valid;
- current version matches expected version.

### Atomic Effects

```text
DRAFT invoice
→ freeze commercial/tax snapshots
→ allocate official invoice number
→ status = POSTED
→ posted_at set
→ create SALE stock movements for PRODUCT lines
→ record payments and/or receivable effect
→ create source-linked cashbook entries for monetary receipts
→ release held-sale reservation if this originated from held bill
→ update payment status
→ audit INVOICE_POSTED
→ commit
```

### Failure Behavior

Any failure rolls back:

- invoice posting;
- number allocation transaction result;
- stock movements;
- payments;
- cashbook;
- reservation release;
- audit event.

### Postconditions

- invoice is immutable;
- official number exists;
- historical snapshots exist;
- stock and finance reconcile to invoice.

## 4.5 POSTED → VOIDED

### Preconditions

- invoice status = POSTED;
- user has `invoice.void`;
- void reason non-empty;
- invoice is not already voided;
- corrective/reversal implications are determined;
- any returned/refunded/credited history is validated to avoid duplicate economic reversal.

### Atomic Effects

Implementation may use linked reversal records rather than deleting prior entries.

```text
POSTED
→ reversal/corrective postings as required
→ mark invoice VOIDED
→ voided_by / voided_at / reason
→ audit INVOICE_VOIDED
→ commit
```

### Forbidden

```text
VOIDED → DRAFT
VOIDED → POSTED
POSTED → DRAFT
```

No endpoint may directly edit posted totals/lines.

---

# 5. Invoice Payment Status Machine

This machine is calculated from the posted invoice and valid payments/credits.

## 5.1 States

```text
UNPAID
PARTIAL
PAID
CREDIT_NOTE
```

## 5.2 Rules

```text
amountPaid = 0 and balance > 0
→ UNPAID

0 < amountPaid < payable balance
→ PARTIAL

balance = 0
→ PAID

credit-note-specific terminal/reference condition
→ CREDIT_NOTE where used by MVP display semantics
```

Payment status must not be manually set by JavaFX.

## 5.3 Payment Command

`RecordInvoicePayment`

Permission:

```text
invoice.payment.create
```

Preconditions:

- invoice POSTED;
- invoice not VOIDED;
- amount > 0;
- payment method supported;
- payment does not exceed valid payable amount unless explicit refund/change logic applies;
- required card/bank/cheque reference present;
- idempotency key unique.

Atomic effects:

```text
payment
→ invoice receivable reduction
→ cashbook IN
→ payment status recompute
→ audit PAYMENT_RECORDED
```

---

# 6. Credit Note State Machine

## 6.1 States

```text
ISSUED
APPLIED
```

Creation/posting itself should be treated as a controlled command because the credit note has stock/tax/finance effects.

## 6.2 Diagram

```text
POST CREDIT NOTE
      │
      ▼
  ┌────────┐
  │ ISSUED │
  └────┬───┘
       │ apply/settle effect
       ▼
  ┌─────────┐
  │ APPLIED │
  └─────────┘
```

## 6.3 Post Credit Note

Command:

```text
PostCreditNote
```

Permission:

```text
invoice.credit_note.create
```

Preconditions:

- original invoice exists and is POSTED/eligible;
- return window rule validated;
- requested returned quantity <= eligible remaining quantity;
- reason required;
- VAT reversal calculated from original line snapshots;
- stock restock eligibility known;
- refund/receivable treatment selected.

Atomic effects:

```text
allocate CN number
→ create immutable credit note
→ create CUSTOMER_RETURN stock movement where restocked
→ reduce receivable OR create refund
→ cashbook OUT if refund
→ store tax reversal
→ audit CREDIT_NOTE_POSTED
```

## 6.4 ISSUED → APPLIED

Use when the issued credit is applied to the customer's balance/document.

Preconditions:

- credit not already applied;
- application amount valid;
- original/customer relationship valid.

Forbidden:

```text
APPLIED → ISSUED
```

Correction uses explicit reversal/adjustment design.

---

# 7. Held Sale State Machine

## 7.1 States

```text
HELD
RESUMED
CANCELLED
EXPIRED
CONVERTED
```

## 7.2 Diagram

```text
              resume
        ┌───────────────┐
        ▼               │
   ┌─────────┐      ┌─────────┐
   │  HELD   │◀────▶│ RESUMED │
   └──┬──┬───┘      └────┬────┘
      │  │               │ complete sale
cancel│  │expire          ▼
      │  │           ┌───────────┐
      │  └──────────▶│ CONVERTED │
      ▼              └───────────┘
┌───────────┐
│ CANCELLED │
└───────────┘

HELD ──timeout──▶ EXPIRED
```

## 7.3 Hold Sale

Command:

```text
HoldSale
```

Permission:

```text
invoice.hold_bill
```

Preconditions:

- cart has at least one product/item;
- reservation quantities are available;
- no invalid pricing/quantity values.

Effects:

```text
persist held sale/items
→ create reservation effect
→ no SALE stock movement
→ audit HELD_SALE_CREATED
```

## 7.4 HELD → RESUMED

- permission `invoice.hold_bill`;
- held sale still active;
- not expired/cancelled/converted;
- load cart;
- reservation remains active while resumed.

## 7.5 RESUMED → HELD

Allowed when user holds the cart again.

Reservation is updated atomically to current held quantities.

## 7.6 HELD/RESUMED → CONVERTED

Occurs only as part of successful invoice posting.

Effects:

```text
release reservation
+ post SALE stock
+ link converted invoice
```

## 7.7 HELD/RESUMED → CANCELLED

- user confirmation;
- reservation released;
- no physical stock movement.

## 7.8 HELD → EXPIRED

- timeout/end-of-day configured rule;
- reservation released;
- audit expiration/release event.

Terminal states:

```text
CANCELLED
EXPIRED
CONVERTED
```

---

# 8. Appointment State Machine

## 8.1 States

```text
SCHEDULED
CONFIRMED
IN_PROGRESS
COMPLETED
NO_SHOW
CANCELLED
```

## 8.2 Diagram

```text
                  confirm
SCHEDULED ─────────────────▶ CONFIRMED
   │  │                         │  │
   │  ├──── start ──────────────┘  │
   │  │                            │ start
   │  ▼                            ▼
   │ IN_PROGRESS ◀─────────────────┘
   │      │
   │      │ complete
   │      ▼
   │  COMPLETED
   │
   ├──────── no-show ───────▶ NO_SHOW
   └──────── cancel ────────▶ CANCELLED

CONFIRMED ──no-show──────────▶ NO_SHOW
CONFIRMED ──cancel───────────▶ CANCELLED
```

## 8.3 Create Appointment

Permission:

```text
appointment.create
```

Preconditions:

- customer exists;
- service active;
- start/end valid;
- within configured business rules;
- assigned technician eligible if specified;
- no conflicting active appointment for assigned technician.

Effect:

```text
SCHEDULED
```

Walk-in may be created and immediately progressed/converted according to workflow.

## 8.4 SCHEDULED → CONFIRMED

Permission:

```text
appointment.update
```

Preconditions:

- appointment not terminal;
- time/technician still valid.

Audit:

```text
APPOINTMENT_CONFIRMED
```

## 8.5 SCHEDULED/CONFIRMED → IN_PROGRESS

Permission:

```text
appointment.update
```

or conversion-specific workflow where appropriate.

Preconditions:

- customer arrived/service beginning;
- no terminal state.

## 8.6 IN_PROGRESS → COMPLETED

Preconditions:

- service appointment work finished or linked job workflow permits appointment completion.

## 8.7 SCHEDULED/CONFIRMED → NO_SHOW

Permission:

```text
appointment.update
```

Rules:

- appointment time has reasonably arrived/passed according to UI/business rule;
- reason/note may be captured.

## 8.8 SCHEDULED/CONFIRMED → CANCELLED

Permission:

```text
appointment.cancel
```

Reason recommended/required by implementation rule for audit.

## 8.9 Rescheduling

Command:

```text
RescheduleAppointment
```

Permission:

```text
appointment.update
```

Allowed only for active appointment states that can still be scheduled.

Mandatory:

```text
server conflict check
+
PostgreSQL concurrency protection
```

If conflicting:

```text
409 APPOINTMENT_CONFLICT
```

## 8.10 Terminal States

```text
COMPLETED
NO_SHOW
CANCELLED
```

No normal transition returns these to active states.

A data correction, if ever necessary, is an administrative corrective workflow, not standard MVP rescheduling.

---

# 9. Appointment-to-Job Conversion

This is a one-time transition relationship rather than a replacement of appointment status.

Command:

```text
ConvertAppointmentToJob
```

Permission:

```text
appointment.convert_to_job
```

Preconditions:

- appointment exists;
- appointment not CANCELLED/NO_SHOW;
- no existing `converted_to_job_card_id`;
- customer/service references valid.

Atomic effects:

```text
create JobCard
→ link appointment.converted_to_job_card_id
→ set job initial state
→ update appointment to appropriate active state if required
→ audit APPOINTMENT_CONVERTED_TO_JOB
```

Invariant:

```text
one appointment → at most one converted job card
```

Retry with same idempotency key returns same job card.

---

# 10. Job Card State Machine

## 10.1 States

```text
CREATED
ESTIMATE_PENDING
ESTIMATE_APPROVED
IN_PROGRESS
READY_FOR_PICKUP
COMPLETED
CANCELLED
```

## 10.2 Diagram

```text
                    ┌──────────────────────┐
                    │       CREATED        │
                    └──────────┬───────────┘
                         estimate? \
                         yes       \ authorized skip
                          │         \
                          ▼          ▼
               ┌────────────────┐  IN_PROGRESS
               │ESTIMATE_PENDING│      │
               └──────┬─────┬───┘      │
                    accept decline      │
                      │       │         │
                      ▼       ▼         │
             ESTIMATE_APPROVED CANCELLED│
                      │                 │
                      └──── start ──────┘
                              │
                              ▼
                         IN_PROGRESS
                              │
                              ▼
                      READY_FOR_PICKUP
                              │
                      payment + pickup
                              ▼
                          COMPLETED
```

## 10.3 CREATED → ESTIMATE_PENDING

Automatic/command when selected service requires estimate.

Preconditions:

- job has estimate-required service;
- job not cancelled.

## 10.4 CREATED → IN_PROGRESS

Permission:

```text
jobcard.status_change
```

Preconditions:

- no mandatory unapproved estimate;
- OR authorized manager override rule has been satisfied and audited.

## 10.5 ESTIMATE_PENDING → ESTIMATE_APPROVED

Trigger:

```text
customer accepts estimate
```

Permission to record/approve according to MVP:

```text
jobcard.estimate.approve
```

or authorized service workflow that records customer acceptance.

Effects:

- estimate response = ACCEPTED;
- response timestamp recorded;
- job status = ESTIMATE_APPROVED;
- audit estimate acceptance.

## 10.6 ESTIMATE_PENDING → CANCELLED

Trigger:

```text
customer declines / business cancels
```

Preconditions:

- reason recorded;
- estimate response = DECLINED when customer declined.

## 10.7 ESTIMATE_APPROVED → IN_PROGRESS

Permission:

```text
jobcard.status_change
```

Technician begins work.

## 10.8 IN_PROGRESS → READY_FOR_PICKUP

Permission:

```text
jobcard.status_change
```

Preconditions:

- required job services completed;
- no unresolved rule preventing readiness;
- actual completion date set as appropriate.

## 10.9 READY_FOR_PICKUP → COMPLETED

Permission:

```text
jobcard.complete
```

Preconditions:

- customer pickup confirmed;
- required linked invoice exists where applicable;
- payment/authorized credit condition satisfied;
- pickup date set;
- warranty dates calculated.

Effects:

```text
status COMPLETED
→ pickup date
→ warranty start/end
→ audit JOB_CARD_COMPLETED
```

## 10.10 Any Permitted Active State → CANCELLED

Reason mandatory.

Cancellation must validate any already-posted parts/financial effects; those cannot simply disappear.

If parts were already consumed, correction requires appropriate stock/business reversal logic.

---

# 11. Job Service State Machine

## 11.1 States

```text
PENDING
IN_PROGRESS
COMPLETED
```

## 11.2 Transitions

| From | To | Preconditions |
|---|---|---|
| PENDING | IN_PROGRESS | parent job permits work |
| IN_PROGRESS | COMPLETED | service work completed |
| PENDING | COMPLETED | not normally allowed; use explicit start/complete or controlled shortcut if implementation defines it |

Job-card readiness validates job-service completion.

---

# 12. Job Estimate State Machine

## 12.1 States

```text
PENDING
ACCEPTED
DECLINED
```

## 12.2 Transitions

```text
PENDING → ACCEPTED
PENDING → DECLINED
```

Terminal:

```text
ACCEPTED
DECLINED
```

A changed estimate should normally create a new estimate/version rather than rewrite a customer decision silently.

## 12.3 Commands

```text
CreateEstimate
AcceptEstimate
DeclineEstimate
```

Permissions:

```text
jobcard.estimate.create
jobcard.estimate.approve
```

---

# 13. Job Part Usage Lifecycle

JobPart does not require a complex status enum in MVP if it is created only when usage is posted.

## 13.1 Add Job Part

Command:

```text
AddJobPart
```

Permission:

```text
jobcard.parts.add
```

Preconditions:

- job status permits parts usage;
- product is inventory product;
- quantity positive;
- available stock sufficient;
- customer price snapshot resolved;
- cost snapshot resolved;
- idempotency key valid where exposed as critical command.

Atomic effects:

```text
persist JobPart
→ create JOB_PART stock movement (-qty)
→ audit JOB_PART_CONSUMED
```

A posted JobPart must not be silently edited/deleted.

Correction uses an explicit reversal/removal business command if implemented for the MVP workflow.

---

# 14. GRN State Machine

## 14.1 States

```text
DRAFT
POSTED
REVERSED
```

## 14.2 Diagram

```text
DRAFT ──post──▶ POSTED ──controlled reversal──▶ REVERSED
```

## 14.3 DRAFT → POSTED

Permission:

```text
purchasing.grn.create
```

Preconditions:

- supplier active/valid;
- at least one item;
- all products valid inventory products;
- quantities positive;
- unit costs non-negative;
- totals server-calculated;
- GRN date valid;
- no duplicate supplier/reference rule violation;
- idempotency key valid.

Atomic effects:

```text
allocate GRN number
→ status POSTED
→ freeze items/cost values
→ GRN stock movements (+)
→ product cost history
→ supplier payable increase
→ audit GRN_POSTED
```

## 14.4 POSTED → REVERSED

Only when a controlled reversal is required.

Normal correction is preferably through supplier return because MVP explicitly supports that.

Rules:

- never edit/delete POSTED GRN;
- reversal permission/business rule must be explicit;
- reverse stock/payable effects consistently;
- reason mandatory;
- audit.

---

# 15. Supplier Return Lifecycle

A supplier return can be treated as a posted immutable document with no editable post-state in MVP.

## 15.1 Post Supplier Return

Command:

```text
PostSupplierReturn
```

Permission:

```text
purchasing.return.create
```

Preconditions:

- supplier valid;
- original GRN/item exists;
- quantity > 0;
- cumulative returned quantity does not exceed eligible received quantity;
- cost uses original GRN item basis;
- stock sufficient to return where required;
- reason required;
- idempotency key valid.

Atomic effects:

```text
supplier return document
→ SUPPLIER_RETURN stock movement (-)
→ supplier payable reduction
→ audit SUPPLIER_RETURN_POSTED
```

Posted return is immutable.

---

# 16. Supplier Payment Lifecycle

Supplier payment is a posted immutable monetary document.

## 16.1 Post Supplier Payment

Command:

```text
RecordSupplierPayment
```

Permission:

```text
purchasing.payment.create
```

Preconditions:

- supplier valid;
- amount > 0;
- payment method valid;
- reference supplied when required;
- allocations belong to same supplier;
- allocation amount > 0;
- allocation does not exceed outstanding GRN amount;
- allocation sum does not exceed total payment;
- idempotency key valid.

Atomic effects:

```text
supplier payment
→ payment allocations
→ payable reduction
→ cashbook OUT
→ audit SUPPLIER_PAYMENT_POSTED
```

Posted payment is immutable.

---

# 17. Stock Adjustment State Machine

## 17.1 States

```text
PENDING
APPROVED
REJECTED
```

## 17.2 Diagram

```text
             approve
PENDING ─────────────▶ APPROVED
   │
   └──── reject ─────▶ REJECTED
```

## 17.3 Create

Command:

```text
CreateStockAdjustment
```

Permission:

```text
inventory.adjustment.create
```

Preconditions:

- product valid;
- quantity positive;
- adjustment type valid;
- reason required.

Effect:

```text
PENDING
```

No stock movement yet.

## 17.4 PENDING → APPROVED

Permission:

```text
inventory.adjustment.approve
```

Preconditions:

- still PENDING;
- approver authorized;
- any negative adjustment stock/business constraints satisfied.

Atomic effects:

```text
status APPROVED
→ approved_by/at
→ create signed ADJUSTMENT stock movement exactly once
→ audit STOCK_ADJUSTMENT_APPROVED
```

## 17.5 PENDING → REJECTED

Permission:

```text
inventory.adjustment.approve
```

Reason recommended/required.

No stock movement.

## 17.6 Terminal Rules

```text
APPROVED → PENDING prohibited
REJECTED → PENDING prohibited
APPROVED → REJECTED prohibited
```

Correction of APPROVED adjustment uses explicit reversal transaction.

---

# 18. Customer Credit Eligibility State Evaluation

This is a policy state, not a persisted workflow state.

## 18.1 Outcomes

```text
NORMAL
WARNING
CASH_ONLY
BLOCK_ALL
LIMIT_EXCEEDED
```

## 18.2 Evaluation

| Condition | Outcome |
|---|---|
| no problematic aging and within limit | NORMAL |
| 31–60 day oldest outstanding | WARNING |
| 61–90 days | CASH_ONLY |
| 91+ days | BLOCK_ALL |
| requested new credit exceeds allowed limit | LIMIT_EXCEEDED |

Sales posting must evaluate this immediately before commit.

A JavaFX warning cannot override the server decision unless an explicit MVP permission/rule allows it.

---

# 19. Cash Closing State Machine

## 19.1 States

```text
PENDING_APPROVAL
APPROVED
```

For zero-variance closing, implementation may set `APPROVED` immediately under the configured MVP rule.

## 19.2 Diagram

```text
Create Closing
     │
     ├── variance = 0 ───────────────▶ APPROVED
     │
     └── variance != 0
                │
                ▼
        PENDING_APPROVAL
                │ manager approval
                ▼
            APPROVED
```

## 19.3 Create Cash Closing

Permission:

```text
finance.cash_closing.create
```

Preconditions:

- no existing closing for same business date/cashier;
- expected cash computed from authoritative cashbook sources;
- counted cash provided;
- variance computed server-side;
- if variance != 0, reason required;
- idempotency key valid.

## 19.4 PENDING_APPROVAL → APPROVED

Permission:

```text
finance.cash_closing.approve
```

Effects:

- approver/timestamp;
- audit;
- immutable finalized closing.

## 19.5 Forbidden

Approved closing cannot be edited/reopened through normal CRUD.

Correction must be explicit and audited.

---

# 20. Secondary Role Assignment State Machine

## 20.1 States

```text
ACTIVE
EXPIRED
REVOKED
```

## 20.2 Diagram

```text
               time reaches expiresAt
ACTIVE ──────────────────────────────▶ EXPIRED
   │
   └──────── manual revoke ──────────▶ REVOKED
```

## 20.3 Grant

Command:

```text
GrantSecondaryRole
```

Permission:

```text
user.grant_role
```

Preconditions:

- target user exists;
- role exists;
- role != primary role;
- expiry valid;
- granter authorized.

Effects:

```text
ACTIVE
→ audit SECONDARY_ROLE_GRANTED
```

## 20.4 ACTIVE → EXPIRED

Trigger:

```text
expiresAt <= now
```

Effects:

- secondary role ceases to contribute permissions immediately;
- background process may mark inactive/revoked metadata;
- audit `SECONDARY_ROLE_EXPIRED`.

Correctness must not depend solely on the one-minute cleanup job. Runtime permission resolution also checks `expiresAt`.

## 20.5 ACTIVE → REVOKED

Permission:

```text
user.revoke_role
```

Effects:

- active false;
- revokedBy/At;
- optional reason;
- audit.

Terminal:

```text
EXPIRED
REVOKED
```

A new grant creates a new assignment rather than reactivating old audit history.

---

# 21. User Account Lock State

This can remain fields on User rather than a separate enum, but behavior is stateful.

## 21.1 Effective States

```text
ACTIVE_UNLOCKED
ACTIVE_LOCKED
INACTIVE
```

## 21.2 Failed Login

```text
failed attempts < 5
→ increment

5th failed attempt
→ lock
→ lockedUntil = now + 30 minutes
```

## 21.3 Unlock

Automatic:

```text
now >= lockedUntil
```

or manual permission:

```text
user.unlock
```

Manual lock requires:

```text
user.lock
```

Inactive user remains unable to authenticate regardless of lock expiry.

---

# 22. Backup State Machine

## 22.1 States

MVP storage states:

```text
STARTED
VERIFIED
FAILED
RESTORED
```

For clearer process reasoning, restore execution may also produce audit events without requiring extra persistent enum states.

## 22.2 Diagram

```text
CREATE BACKUP
     │
     ▼
  STARTED
   │    │
   │    └──── failure ─────▶ FAILED
   │
   └──── verification succeeds ─────▶ VERIFIED
                                          │
                                          │ successful restore operation
                                          ▼
                                      RESTORED
```

## 22.3 Create Backup

Permission:

```text
system.backup.create
```

Preconditions:

- backup storage available;
- sufficient space;
- PostgreSQL backup tooling available;
- server has permission to write.

Effects:

```text
BackupRecord STARTED
→ execute backup
→ checksum/size
→ verify according to defined procedure
→ VERIFIED or FAILED
→ audit
```

## 22.4 VERIFIED → RESTORED

Command:

```text
RestoreBackup
```

Permission:

```text
system.backup.restore
```

Preconditions:

- backup VERIFIED;
- Super Administrator;
- explicit confirmation;
- maintenance mode / required client session disconnection;
- supported PostgreSQL environment;
- schema/version checks possible;
- destination/restore strategy safe.

Effects:

```text
restore
→ verify DB readability
→ verify required schema version
→ smoke/recovery checks
→ record restore success
→ audit RESTORE_COMPLETED
```

Failure:

```text
audit RESTORE_FAILED
```

and actionable error.

A failed restore must not be reported as RESTORED.

---

# 23. Business Profile and Tax Configuration Change Lifecycle

These are mutable configuration aggregates rather than workflow status machines.

## 23.1 Change Rule

Command:

```text
UpdateBusinessProfile
UpdateTaxConfiguration
```

Permission:

```text
system.config
```

Rules:

- validate values;
- audit old/new values;
- new configuration affects future postings;
- historical posted invoices retain snapshots.

No change operation may trigger recalculation of posted invoice VAT.

---

# 24. Product Lifecycle

## 24.1 States

```text
ACTIVE
INACTIVE
```

MVP terminology uses active/inactive rather than deletion for normal product retirement.

## 24.2 ACTIVE → INACTIVE

Permission:

```text
product.delete
```

Effect:

- cannot be selected for new normal sale/GRN where business rule disallows;
- historical references remain intact.

## 24.3 INACTIVE → ACTIVE

Permission:

```text
product.update
```

or product-management authorization as defined in API policy.

Historical records unaffected.

---

# 25. Supplier Lifecycle

## 25.1 States

```text
ACTIVE
INACTIVE
```

Permission to deactivate:

```text
supplier.deactivate
```

Historical GRNs/payments/returns remain available.

No destructive delete.

---

# 26. Customer Lifecycle

MVP storage:

```text
ACTIVE
BLOCKED
```

Blocking can arise from overdue policy/business action.

## 26.1 ACTIVE → BLOCKED

Effect:

- sales eligibility responds according to rule;
- transaction history retained.

## 26.2 BLOCKED → ACTIVE

Occurs when business conditions allow reactivation.

The financial aging policy is still evaluated independently at sale posting.

---

# 27. Cross-State Dependency Rules

The following dependencies are mandatory.

## 27.1 Invoice ↔ Held Sale

```text
HeldSale CONVERTED
requires successful Invoice POSTED
```

Neither should commit alone when converting held bill to sale.

## 27.2 Appointment ↔ JobCard

```text
appointment.converted_to_job_card_id
```

is assigned atomically with JobCard creation.

## 27.3 JobPart ↔ StockMovement

A JobPart is considered posted only if corresponding `JOB_PART` stock movement commits.

## 27.4 GRN ↔ Stock/Cost/Payable

A GRN is POSTED only if all three effects succeed.

## 27.5 SupplierPayment ↔ Payable/Cashbook

A supplier payment is posted only if allocations/payable and cashbook effect commit.

## 27.6 CreditNote ↔ Stock/Finance/Tax

Credit note posting is not complete if one of required stock/refund/receivable/tax effects fails.

---

# 28. Permission-to-Transition Matrix

| Transition/Command | Permission |
|---|---|
| Post invoice | `invoice.create` |
| Void invoice | `invoice.void` |
| Record invoice payment | `invoice.payment.create` |
| Refund payment | `invoice.payment.refund` |
| Hold/resume bill | `invoice.hold_bill` |
| Create/post credit note | `invoice.credit_note.create` |
| Discount ≤10% | `invoice.discount.apply` |
| Discount >10–25% | `invoice.discount.approve_25` |
| Discount >25% | `invoice.discount.approve_50` |
| Override price | `invoice.override_price` |
| Sell below cost | `invoice.sell_below_cost` |
| Create appointment | `appointment.create` |
| Read calendar | `appointment.read` |
| Reschedule/update appointment | `appointment.update` |
| Cancel appointment | `appointment.cancel` |
| Convert appointment to job | `appointment.convert_to_job` |
| Create job | `jobcard.create` |
| Update job | `jobcard.update` |
| Change job status | `jobcard.status_change` |
| Add job part | `jobcard.parts.add` |
| Create estimate | `jobcard.estimate.create` |
| Approve/record accepted estimate | `jobcard.estimate.approve` |
| Complete job | `jobcard.complete` |
| Create stock adjustment | `inventory.adjustment.create` |
| Approve/reject adjustment | `inventory.adjustment.approve` |
| Post GRN | `purchasing.grn.create` |
| Post supplier return | `purchasing.return.create` |
| Record supplier payment | `purchasing.payment.create` |
| Manual cashbook | `finance.cashbook.create` |
| Create cash closing | `finance.cash_closing.create` |
| Approve cash variance/closing | `finance.cash_closing.approve` |
| Grant secondary role | `user.grant_role` |
| Revoke secondary role | `user.revoke_role` |
| Create backup | `system.backup.create` |
| Restore backup | `system.backup.restore` |

Server-side permission mapping is mandatory even when JavaFX hides the action.

---

# 29. Audit Event Matrix

| Transition | Audit Event |
|---|---|
| Invoice DRAFT → POSTED | `INVOICE_POSTED` |
| Invoice POSTED → VOIDED | `INVOICE_VOIDED` |
| Payment recorded | `PAYMENT_RECORDED` |
| Refund | `REFUND_POSTED` |
| Credit note posted | `CREDIT_NOTE_POSTED` |
| Held bill created | `HELD_SALE_CREATED` |
| Held bill cancelled/expired | `HELD_SALE_RELEASED` |
| Appointment created | `APPOINTMENT_CREATED` |
| Appointment rescheduled | `APPOINTMENT_RESCHEDULED` |
| Appointment → Job | `APPOINTMENT_CONVERTED_TO_JOB` |
| Job estimate accepted/declined | `JOB_ESTIMATE_RESPONSE` |
| Job part consumed | `JOB_PART_CONSUMED` |
| Job completed | `JOB_CARD_COMPLETED` |
| GRN posted | `GRN_POSTED` |
| Supplier return posted | `SUPPLIER_RETURN_POSTED` |
| Supplier payment posted | `SUPPLIER_PAYMENT_POSTED` |
| Stock adjustment approved | `STOCK_ADJUSTMENT_APPROVED` |
| Stock adjustment rejected | `STOCK_ADJUSTMENT_REJECTED` |
| Cash closing created | `CASH_CLOSING_CREATED` |
| Cash closing approved | `CASH_CLOSING_APPROVED` |
| Secondary role granted | `SECONDARY_ROLE_GRANTED` |
| Secondary role revoked | `SECONDARY_ROLE_REVOKED` |
| Secondary role expires | `SECONDARY_ROLE_EXPIRED` |
| Backup verified | `BACKUP_VERIFIED` |
| Restore completed | `RESTORE_COMPLETED` |

---

# 30. Invalid Transition Examples

The following must be rejected.

## Invoice

```text
POSTED → DRAFT
VOIDED → POSTED
edit POSTED line price
edit POSTED VAT
delete POSTED invoice
```

## Credit Note

```text
return quantity > eligible original quantity
credit note for non-existent invoice
apply same credit twice
```

## Held Sale

```text
resume CANCELLED
convert EXPIRED
reserve stock exceeding availability
```

## Appointment

```text
CANCELLED → IN_PROGRESS
COMPLETED → SCHEDULED
overlapping active appointment for same technician
```

## Job

```text
ESTIMATE_PENDING → IN_PROGRESS without approval/authorized override
IN_PROGRESS → COMPLETED while bypassing READY_FOR_PICKUP workflow
READY_FOR_PICKUP → COMPLETED without required payment/pickup conditions
```

## GRN

```text
edit POSTED GRN items
POST empty GRN
receive service-only product as stock
```

## Stock Adjustment

```text
APPROVED → REJECTED
approve same adjustment twice
create stock movement while adjustment still PENDING
```

## Cash Closing

```text
approve same closing twice
edit APPROVED closing
second closing for same cashier/date
non-zero variance without reason
```

## Secondary Role

```text
revoke primary role via secondary-role endpoint
use expired role permission
reactivate old expired assignment by editing history
```

## Backup

```text
restore unverified backup
restore while active sessions violate maintenance rule
mark failed restore as successful
```

---

# 31. API Command Mapping

REST endpoints should express transitions explicitly.

## 31.1 Invoice

```text
POST /api/invoices
POST /api/invoices/{id}/post
POST /api/invoices/{id}/void
POST /api/invoices/{id}/payments
POST /api/invoices/{id}/reprint
```

Avoid:

```text
PUT /api/invoices/{id}
```

for posted business-state changes.

`PUT` may update DRAFT only.

## 31.2 Appointment

```text
POST /api/appointments
PUT  /api/appointments/{id}           # editable scheduling data while allowed
POST /api/appointments/{id}/status
POST /api/appointments/{id}/convert-to-job
```

The server validates requested status transition.

## 31.3 Job

```text
POST /api/job-cards/{id}/status
POST /api/job-cards/{id}/parts
POST /api/job-cards/{id}/estimate
POST /api/job-cards/{id}/estimate/accept
```

## 31.4 Inventory/Purchasing

```text
POST /api/stock-adjustments
POST /api/stock-adjustments/{id}/approve
POST /api/stock-adjustments/{id}/reject

POST /api/grn
POST /api/grn/{id}/post

POST /api/supplier-returns
POST /api/supplier-payments
```

If MVP UI creates and posts GRN in one interaction, API may expose a single posting command while still preserving the internal DRAFT/POSTED lifecycle.

## 31.5 Finance

```text
POST /api/finance/cashbook
POST /api/finance/cash-closings
POST /api/finance/cash-closings/{id}/approve
```

## 31.6 Identity

```text
POST   /api/users/{id}/roles
DELETE /api/users/{id}/roles/{assignmentId}
POST   /api/users/{id}/lock
POST   /api/users/{id}/unlock
```

---

# 32. Database Constraint Implications

These state machines imply the following database protections.

## 32.1 Invoice

- enum/status constraint;
- `invoice_number` required for POSTED/VOIDED, null for DRAFT;
- posted timestamp required for POSTED/VOIDED;
- unique request/idempotency key;
- optimistic-lock version column.

## 32.2 Appointment

- active overlap protection for same technician;
- unique appointment number;
- version column.

## 32.3 Job Conversion

- unique nullable appointment reference on job card or equivalent one-to-one guard;
- appointment converted job reference consistent.

## 32.4 Stock Adjustment

- approval metadata consistent with APPROVED;
- no approved movement duplication through source uniqueness.

## 32.5 GRN

- number required for POSTED;
- request ID unique;
- status/timestamp consistency.

## 32.6 Supplier Payment Allocation

- allocation > 0;
- unique payment+GRN pair;
- transactional over-allocation guard.

## 32.7 Cash Closing

- unique `(business_date, cashier_id)`;
- status/approval metadata consistency;
- variance reason constraint where practical.

## 32.8 Sessions

- token hash unique;
- revocation timestamps;
- indexed active-session lookup.

---

# 33. Acceptance Test Derivation

Every transition should have at least:

1. happy-path test;
2. invalid-source-state test;
3. missing-permission test;
4. business-precondition failure test;
5. concurrency test where relevant;
6. idempotent retry test where relevant;
7. persistence/audit side-effect test.

Example:

```text
INV-POST-001
Given a DRAFT invoice with stock available
And valid cashier permission
And full cash payment
When PostInvoice is executed
Then status becomes POSTED
And official number is allocated
And SALE movements are created
And cashbook receipt is created
And audit event exists
And all effects commit together
```

Concurrency:

```text
SCH-CONFLICT-001
Given technician T has no booking at 10:00
When two clients concurrently book overlapping 10:00 slots
Then exactly one booking commits
And the other receives APPOINTMENT_CONFLICT
```

Idempotency:

```text
PAY-IDEMP-001
Given payment request ID X commits
When request ID X is retried after response loss
Then no second payment exists
And the original result is returned
```

---

# 34. State Machine Coverage Against MVP Scenarios

## SC-01 Retail

Uses:

```text
HeldSale
Invoice
Payment status
CreditNote
StockAdjustment
GRN
CashClosing
```

## SC-02 Wholesale/Credit

Uses:

```text
Invoice
Payment status
Customer credit policy
CreditNote
Supplier/finance state where applicable
```

## SC-03 Repair

Uses:

```text
Appointment
JobCard
JobEstimate
JobService
JobPart
Invoice
Payment
```

## SC-04 Appointment Service

Uses:

```text
Appointment
Appointment-to-Job conversion
JobCard where applicable
Invoice
```

## SC-05 Hybrid

Uses:

```text
Appointment
JobCard
JobPart
Invoice PRODUCT/SERVICE/CUSTOM lines
CreditNote
Inventory
Finance
```

## Recovery

Uses:

```text
UserSession
BackupRecord
maintenance guard
restore transition
```

All existing MVP business scenarios remain covered.

---

# 35. Decisions to Carry Into ERD Design

The state machines resolve lifecycle behavior but intentionally leave these physical design choices for `DatabaseDesign.md`:

1. exact PostgreSQL appointment-overlap implementation;
2. exact source-posting uniqueness keys for stock/cashbook entries;
3. whether customer payments are normalized into `customer_payments` + allocations while preserving invoice-payment APIs;
4. exact invoice/credit-note sequence locking SQL;
5. exact reversal tables/fields for invoice void and stock-adjustment correction;
6. whether GRN creation API persists a DRAFT first or posts directly while domain still recognizes a transient draft;
7. zero-variance cash closing stored directly as APPROVED vs a broader COMPLETE state;
8. precise DB check constraints tying state to approval/posting timestamps;
9. version columns and lock strategy for each mutable aggregate;
10. exact status set considered "active" for appointment overlap.

These are implementation-design decisions, not scope reductions.

---

# 36. Recommended Next Artifact

The next artifact is:

```text
DatabaseDesign.md
```

It should contain:

- final MVP ER model;
- table definitions;
- PK/FK relationships;
- normalized RBAC tables;
- invoice snapshots;
- held-sale reservations;
- customer/supplier payment allocation;
- stock source uniqueness;
- cashbook source uniqueness;
- state-related CHECK constraints;
- `TIMESTAMPTZ` strategy;
- indexes;
- appointment exclusion constraint;
- idempotency keys;
- optimistic-lock columns;
- document sequence algorithm;
- Flyway migration grouping.

After `DatabaseDesign.md`, the REST API contracts can be finalized against stable persistence and state rules.

---

*(End of Bizco MVP State Machines & Transition Rules)*
