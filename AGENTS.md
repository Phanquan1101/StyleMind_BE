# Agent instructions — StyleMind_BE

Work only in this repository. Before changing files, inspect `git status` and preserve every existing staged, unstaged and untracked change, including T0–T4. Do not reset, checkout, revert, clean, commit or push unless the owner explicitly asks.

## Read first

- [Current project context](docs/governance/CURRENT_PROJECT_CONTEXT.md)
- [Current business rules](docs/governance/CURRENT_BUSINESS_RULES.md)
- [Open implementation decisions](docs/governance/OPEN_IMPLEMENTATION_DECISIONS.md)
- The requested row(s) in `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx`, sheet `Backlog` (read-only)
- The owning service migration/model, approved contract and tests

Source roles and conflict handling are defined in `CURRENT_PROJECT_CONTEXT.md`. Files in `docs/archive/` are historical references, never implementation instructions. Do not treat source/test behavior, a diagram, backlog status or file timestamp as owner approval.

## Current boundaries

| Service | Database | Current responsibility |
|---|---|---|
| `identity-service` | `stylemind_identity` | Identity and authentication |
| `commerce-service` | `stylemind_commerce` | Catalog, Cart, Checkout and Order |
| `payment-service` | `stylemind_payment` | Payment and payment exceptions |
| `wardrobe-community-service` | `stylemind_wardrobe_community` | Wardrobe, sharing and community |
| `stylist-service` | `stylemind_stylist` | AI Stylist |
| `notification-service` | `stylemind_notification` | Notification delivery |

No cross-service database access or foreign keys. The current project scope excludes stock/inventory, reservations, returns, refunds, QC/restock and a separate Social service. Branch and Cancellation Request remain in the model; cancellation/payment exception behavior is TBD. Do not create work because it appears in archived material.

Use Spring MVC REST and Controller → Service → Repository with Spring JDBC. Do not convert to JPA or add another persistence framework without approval. Identity V1–V3 are applied migration history: never edit them. A new schema change requires an explicitly approved migration scope. The other five databases have target models but no business schema migrations.

## Implementation and verification

- Implement only the requested story/task. Preserve service ownership and existing public contracts.
- Map each requirement to the owner-approved rule, model attributes, endpoint, validation, authorization, transaction and side effects. Mark proposed behavior `NOT APPROVED` and unresolved behavior `TBD`.
- Routine reversible technical choices may follow existing conventions without an approval round. Get an explicit owner decision before changing business behavior, schema, service boundaries, features, user-visible security guarantees, or secrets/state that may invalidate existing data.
- Test changed behavior at unit, controller/HTTP, repository/integration and regression levels as applicable. Include owner A/B isolation and negative authorization/input cases; cover transaction/concurrency when the rule depends on it. Run the existing regression gate before an optional Docker smoke. A skipped test is not a pass. Do not run Compose or mutate persistent data unless the task calls for it; use isolated resources for integration tests.
- Never log or expose passwords, password hashes, OTPs, tokens, authorization headers, private keys or secrets. Keep local secrets in ignored files/environment variables.
- Report exact files changed, evidence and commands/results, limitations, database/environment touched, and remaining decisions. Do not claim production readiness from local tests.

- For every User Story with HTTP APIs, hand over or update an importable Postman collection JSON and run guide. Postman JSON complements automated tests; report structural validation separately from actual HTTP/runtime execution.
