# Agent instructions â€” StyleMind_BE

Work only in this repository. Before changing files, inspect `git status` and preserve every existing staged, unstaged and untracked change, including T0â€“T4. Do not reset, checkout, revert, clean, commit or push unless the owner explicitly asks.

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

Use Spring MVC REST and Controller -> Service -> Spring Data JPA Repository for Stylist under the owner decision of 2026-10-28. The previous JDBC instruction is superseded for forthcoming Stylist work. Existing JPA starter may be retained. Do not refactor other services. Existing versioned migrations are immutable: Identity now contains V1â€“V4; Commerce, Payment and Wardrobe also have business migration files. File existence does not establish persistent deployment. A new schema change requires an explicitly approved migration scope.

## AI Stylist execution entry

For EP-07, read [the planning index](docs/planning/stylist/README.md), [the service instructions](stylist-service/AGENTS.md), and [the machine-readable execution manifest](docs/planning/stylist/EP-07_EXECUTION_MANIFEST.json). The 2026-10-27 documentation/harness authorization was later expanded by owner Prompt 08 on 2026-10-29: exactly TOP+BOTTOM for P02 and P03-A session/history APIs with a two-table V1. No broader P03/provider/live contract is approved. Run `node scripts/verify-stylist-planning.cjs` before an EP-07 slice and `--phase Pxx` for its recorded prerequisites. The validator is a local guard, not proof of human approval or application correctness.

Work on the existing `feature/ep-07-ai-stylist` branch unless the owner requests another branch; do not create a second AI branch because an older prompt named `codex/ep-07-ai-stylist`. Preserve staged planning versions as well as worktree changes. The current Wardrobe implementation stores saved shop-variant outfits; it does not implement the target owned-garment/wardrobe/requirement model. Never map a saved outfit item to `wardrobe_item_id` or silently remove mixed-source requirements to conceal that gap.

## Implementation and verification

- Implement only the requested story/task. Preserve service ownership and existing public contracts.
- Map each requirement to the owner-approved rule, model attributes, endpoint, validation, authorization, transaction and side effects. Mark proposed behavior `NOT APPROVED` and unresolved behavior `TBD`.
- Routine reversible technical choices may follow existing conventions without an approval round. Get an explicit owner decision before changing business behavior, schema, service boundaries, features, user-visible security guarantees, or secrets/state that may invalidate existing data.
- Test changed behavior at unit, controller/HTTP, repository/integration and regression levels as applicable. Include owner A/B isolation and negative authorization/input cases; cover transaction/concurrency when the rule depends on it. Run the existing regression gate before an optional Docker smoke. A skipped test is not a pass. Do not run Compose or mutate persistent data unless the task calls for it; use isolated resources for integration tests.
- Never log or expose passwords, password hashes, OTPs, tokens, authorization headers, private keys or secrets. Keep local secrets in ignored files/environment variables.
- Report exact files changed, evidence and commands/results, limitations, database/environment touched, and remaining decisions. Do not claim production readiness from local tests.

- For every User Story with HTTP APIs, hand over or update an importable Postman collection JSON and run guide. Postman JSON complements automated tests; report structural validation separately from actual HTTP/runtime execution.

**Owner correction 2026-10-29 (Prompt 08):** The shop currently sells women’s TOP and BOTTOM only; every Stylist outfit has exactly one of each. ONE_PIECE, SHOES, ACCESSORY and other slots are out of scope, superseding earlier P02/live proposals. Owned and saved remain distinct; only trusted confirmed owned anchors cost zero. Prompt 08 also explicitly authorizes P03-A only: two-table session/message V1 plus create-session and read-history APIs, tests, Postman collection and guide. P03, US-07.01 and EP-07 remain incomplete; request admission, live owned context, retrieval, provider and recommendation work remain gated. Do not change other services or persistent databases.

## Java Spring Boot JPA and anti-over-engineering

- Organize Java code by technical layers: `controller`, `service`, `service/impl`, `repository`, `entity`, `dto`, `mapper`, `errors`, and `security`.
- Controllers accept validated DTOs, call service interfaces and return response DTOs. Do not call repositories, put transactions on controllers, or return entities.
- Keep business logic and transaction boundaries in service implementations behind service interfaces. Access the database through Spring Data JPA repositories; do not import database-library exceptions into services.
- Repositories extend `JpaRepository` and own query logic through derived methods or `@Query`.
- Use constructor injection with final fields, preferably Lombok `@RequiredArgsConstructor`; never use field `@Autowired`.
- Put DTO/entity conversions and substantial entity construction in dedicated mappers or builders; do not construct entities with long setter sequences in service implementations.
- Do not add intermediate `model`, `domain`, `*Snapshot`, or `*Commit` layers for CRUD/auth. Centralize error codes, HTTP statuses and default messages in `ErrorCode`, and handle them through `ApiExceptionHandler`.
- Extract repeated logic, avoid unnecessary `saveAndFlush()` and duplicate queries. Preserve necessary flushes, locking, transaction boundaries and externally visible contracts when refactoring.

## EP-07 owner supplement — Accelerated Delivery 2 (2026-11-05)

For the scoped Wave 2 request, the earlier “Prompt 08 authorizes P03-A only” statement remains historical for the then-current slice. The owner has since explicitly authorized additional Stylist-only work: generation and recommendation APIs, V3 with exactly recommendation/item plus request lifecycle, approved OpenAI embedding/generation adapters, runtime Qdrant/Neo4j adapters, tests and Postman handover. See `docs/planning/stylist/EP-07_WAVE_2_CONTRACT.md` and its implementation report.

This supplement does not authorize P08 catalog events, live Identity/profile or Wardrobe/owned context, changes to Commerce or another service, persistent migration, production deployment, paid calls outside the approved bounded smoke, commit or push. P03/P04/P05/P06/P07 and EP-07 stay partial/incomplete until remaining backlog and live acceptance gates are resolved.

**Stylist migration supplement (2026-11-05):** The Stylist source now contains V1 (session/message), V2 (request/constraint) and owner-approved V3 (recommendation/item plus request lifecycle). V1/V2 are immutable. V3 has been applied only to disposable Testcontainers in Wave 2 verification; this is not evidence that the persistent `stylemind_stylist` database was migrated.
