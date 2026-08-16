---
name: ui-ux
description: Design Bizco's efficient JavaFX workflows for SME operations, including POS, scheduling, inventory, purchasing, finance, reporting, and administration.
---

# UI/UX Skill

## Purpose

Guide user interface and experience design for the Bizco desktop application.

## Guidelines

- Touch-friendly design (min 48x48dp interactive elements)
- Consistent look across all modules
- Keyboard shortcuts for power users (cashiers)
- Clear error messages with guidance
- Loading states for all async operations

## Design Principles

- Simplicity for small business users
- Minimal training required
- Progressive disclosure (show essentials first)
- Visual hierarchy for important actions
- Color coding per SRS §10.3's actual palette: Primary #1976D2, Success #4CAF50 (in-stock), Warning #FF9800 (low stock), Error #F44336 (out-of-stock) — SRS defines these by stock/status semantics, not by an appointment-status scale, so don't invent a "blue=scheduled/green=confirmed" mapping that isn't in the spec; extend the same palette's semantics to other modules' statuses

## POS Design

- Apply the same operational-density principles to GRN, supplier payment, cash closing, backup history, and restore confirmation workflows.
- Make irreversible or high-impact actions show clear totals, references, and confirmation states.
- Product grid with search
- Quick barcode scanning flow
- Clear cart summary with totals
- Prominent payment button
- Held bills accessible

## References

- SRS.md has no "Section 15: UI Screen Specifications" — SRS.md §15 is "Timezone & Currency". SRS's UI guidance is Section 10: UI/UX Guidelines (navigation, layout, color scheme, typography, keyboard shortcuts — SRS §10.6 lists concrete shortcuts)
- `docs/UI/` is currently empty — the wireframes that exist today are the ASCII mockups in `docs/MVP.md` Section 15: UI Screen Specifications (MVP) (Login, Dashboard, POS, Appointment Calendar)
