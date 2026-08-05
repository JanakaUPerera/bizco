---
name: java
description: Implement Bizco Java and Spring Boot code using project conventions, modular business boundaries, DTOs, transaction safety, and maintainable tests.
---

# Java Skill

## Purpose

Guide Java development practices for the Bizco backend and shared modules.

## Guidelines

- Use the project-approved LTS JDK; do not introduce a second Java baseline without an explicit build decision.
- Keep code inside feature modules: identity, customer, catalog, sales, scheduling, inventory, purchasing, finance, reporting, and system.
- Put business posting rules in application services and use one transaction for each posted business event.
- Target Java 17+ (JDK 21 recommended)
- Follow Java naming conventions (camelCase for methods/variables, PascalCase for classes)
- Use records for DTOs where applicable
- Leverage Spring Boot 3.x features
- Use Lombok for boilerplate reduction where appropriate
- Maximum method length: 50 lines
- Meaningful variable and method names

## Code Style

- Use `final` for immutable parameters
- Prefer immutability where possible
- Use Optional for nullable returns
- Follow SOLID principles
- No commented-out code in production

## References

- Spring Boot 3.x documentation
- See `docs/SRS.md` Section 8.2 (Backend stack) and Section 3.1 (Server Requirements — JDK 17+/21)
