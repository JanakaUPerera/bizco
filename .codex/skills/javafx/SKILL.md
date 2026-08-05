---
name: javafx
description: Build Bizco JavaFX client workflows for POS, scheduling, inventory, purchasing, finance, reporting, and system administration.
---

# JavaFX Skill

## Purpose

Guide JavaFX desktop client development for the Bizco POS and management screens.

## Guidelines

- Use JavaFX 17+ with ControlsFX for enhanced controls
- FXML for layout definitions, CSS for styling
- MVC/MVVM pattern for screen organization
- Touch-friendly UI (min 48x48dp buttons)
- Keyboard shortcuts for common POS actions
- Consistent UI across all modules

## Screen Design

- Supplier, GRN, supplier return, and supplier payment screens
- Cashbook, receivable/payable, daily-closing, and backup/restore administration screens
- Login Screen
- Dashboard with KPIs
- POS Screen (product grid, cart, payment)
- Customer/Product CRUD screens
- Appointment Calendar (daily/weekly/monthly)
- Job Card management
- Inventory (GRN, adjustments)
- Reports with export

## Best Practices

- Keep financial posting and validation on the server; JavaFX presents results and actionable errors.
- Include loading, empty, denied-permission, validation, and recovery/error states for every operational screen.
- Use properties for data binding
- Separate FXML layout from controller logic
- Responsive layouts for different screen sizes
- Error messages with clear guidance
- Loading indicators for API calls
- Keyboard shortcuts per SRS §10.6: F1 Help, F2 New, F3 Search, F5 Refresh, Ctrl+N New record, Ctrl+S Save, Ctrl+P Print, Ctrl+F Find

## References

- Use MVP Section 16 for the current MVP wireframes and screen specifications.
- SRS.md has no "Section 15: UI Screen Specifications" — SRS.md §15 is "Timezone & Currency". SRS's actual UI guidance is Section 10: UI/UX Guidelines (navigation structure, layout standards, color scheme, typography, keyboard shortcuts)
- See `docs/MVP.md` Section 15: UI Screen Specifications (MVP) for the actual ASCII wireframes (Login, Dashboard, POS, Appointment Calendar) — `docs/UI/` is currently empty, so MVP.md §15 is the only wireframe source that exists today
