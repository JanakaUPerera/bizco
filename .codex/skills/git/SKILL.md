---
name: git
description: Apply Bizco's Git workflow, branch rules, commit conventions, release tags, and Flyway migration safeguards.
---

# Git Skill

## Purpose

Guide Git version control practices and branching strategy.

## Branching Strategy

- `main`: Production-ready, tagged releases only
- `feature/*`: Short-lived branch for one accepted outcome; merge to main via review
- `release/*`: Release preparation, bug fixes only
- `hotfix/*`: Emergency fixes, merge to main and tag

## Commit Message Format

```
<type>(<scope>): <description>

Types: feat, fix, docs, style, refactor, test, chore
Scope: pos, invoice, appointment, inventory, auth, etc.
Example: feat(invoice): add VAT calculation logic
```

## Rules

- No direct commits to main
- Use review before merging feature work; run compile, unit tests, PostgreSQL integration tests, and Flyway validation first
- Tag releases with semantic versioning (MAJOR.MINOR.PATCH — SRS §19.1 Version Numbering)
- Never commit secrets or credentials

## References

- See `docs/DevelopmentPlan.md` Appendix B: Git Branching Strategy (branching model, commit format)
- See `docs/SRS.md` Section 19: Versioning & Upgrade Path (release numbering, DB migration versioning)
