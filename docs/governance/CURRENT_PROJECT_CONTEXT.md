# Current StyleMind Project Context

**Owner persistence decision, 2026-10-28:** Use Spring Data JPA/Hibernate for forthcoming Stylist persistence, superseding previous JDBC instructions. Retain Controller -> Service interface/impl -> Repository. Schema/API/P03 implementation are not approved by this framework decision. Do not refactor other services.

**Latest EP-07 owner decision, 2026-10-29 (Prompt 08):** Product scope is women’s TOP and BOTTOM only; each outfit is exactly one TOP plus one BOTTOM. P03-A is authorized for the two session/history APIs and a V1 migration containing only `stylist_session` and `stylist_message`, with tests and Postman handover. This scoped approval supersedes the older P02 slot and P03 approval statements below only for those items. It does not authorize persistent DB migration, remaining P03, US-07.01 completion, or later EP-07 phases.


**As of:** 2026-10-12<br>
**Workspace:** `C:\Users\FPTSHOP\Desktop\StyleMind_BE`<br>

This is the active context for implementation agents in this repository. Read it with [the business rules](CURRENT_BUSINESS_RULES.md), [open implementation decisions](OPEN_IMPLEMENTATION_DECISIONS.md), and the requested rows of the workbook. Earlier discovery/specification packages are preserved under [the legacy archive](../archive/legacy-baseline/20261008T0855Z/README.md); they are not current execution baselines.

**Observed-source update, 2026-10-27 (historical):** For EP-07 use [the reviewed planning index](../planning/stylist/README.md) and [source reconciliation](../planning/stylist/EP-07_REVIEW_2026-10-27.md). Several implementation observations below describe the October 12 snapshot: Identity now has V4/weight_kg; Commerce has V1â€“V8, Payment V1 and Wardrobe V1â€“V2 business migration files. Wardrobe currently saves shop-variant outfits/shares rather than the target owned-garment/wardrobe/requirement model. Gateway has per-service prefix routes. File existence does not prove persistent deployment. Its planning-only approval status was superseded for P02 correction and P03-A by the 2026-10-29 Prompt 08 decision above.

## Source of truth and conflict handling

Use sources for the role they were approved to serve:

1. **Latest explicit owner decisions** define current scope and business behavior.
2. **Current task list** identifies the Epic, User Story and work items being implemented; it does not replace a missing business contract.
3. **Current six-service logical models** define intended data ownership and target entities/attributes.
4. **Applied/versioned migrations** define physical schema actually present.
5. **Approved/current API and business contracts** define request/response, validation, states, authorization, transactions and side effects.
6. **Source, tests and audit reports** demonstrate observed implementation and verification; by themselves, they do not approve new requirements.

When these conflict, record the precise sources and distinguish current, historical, proposed and unresolved statements. Do not repair code/database to match a historical file, infer approval from implementation, infer supersession from timestamps, or give old decision IDs new meanings. The archive preserves decisions and OPEN/DR identifiers without closing them.

## Confirmed current scope

The owner has confirmed six applications and one logical database per owner:

| Service | Database | Responsibility |
|---|---|---|
| `identity-service` | `stylemind_identity` | Accounts, authentication and Identity-owned profile/address data |
| `commerce-service` | `stylemind_commerce` | Catalog, Cart, Checkout and Order are one service boundary |
| `payment-service` | `stylemind_payment` | Payment and reconciliation exceptions |
| `wardrobe-community-service` | `stylemind_wardrobe_community` | Wardrobe, sharing and community, including friend chat |
| `stylist-service` | `stylemind_stylist` | AI Stylist sessions, requests and recommendations |
| `notification-service` | `stylemind_notification` | Notification delivery |

No service may read or mutate another service's database directly; logical remote references are not cross-database foreign keys. Do not restore Catalog, Cart or Checkout as separate applications, or add a Social service. Current scope excludes stock/inventory management, stock reservation, returns, refunds, QC and restock. Branch and Cancellation Request remain in the model. Cancellation after payment and payment-exception behavior remain TBD.

The broader women-first AI Personal Stylist, Smart Wardrobe and real-commerce direction remains a non-conflicting project goal in earlier owner-approved records preserved in the archive. It does not authorize an implementation feature absent from the current backlog and owner decisions.

## Observed repository and local runtime

- Six Java 21 / Spring Boot 3.5.16 business services use Spring MVC and Spring JDBC conventions. A separate Spring Cloud Gateway application listens on host-local port 8080 and currently routes implemented public Identity paths. Identity calls Notification through Spring Cloud OpenFeign over the internal Compose network. Compose does not publish backend service ports; direct ports are used only when running services from IntelliJ or `spring-boot:run`. Postman/client entry uses the Gateway. Production topology and Gateway deployment remain TBD.
- One local PostgreSQL 18 container provisions the six databases. Host PostgreSQL publishing is not required by the Compose path. Each service uses its own database credentials.
- Identity is the only service with business Flyway migrations: V1 creates nine tables, V2 adds `email_verification_challenge`, and V3 adds `password_reset_challenge`. The last persistent-local audit recorded V1â€“V3 successful for `stylemind_identity`; this documentation reconciliation did not connect to or change that database.
- The current Identity physical schema therefore has **11 business tables**. It does not count Flyway metadata. The other five services have target logical models only and **no business-schema migrations**. Compose database creation is not schema migration.
- Identity US-02.01 through US-02.04 have implementation and local verification evidence in the [release audit](../qa/identity-audit/20261008T1144-0700/IDENTITY_RELEASE_READINESS_AUDIT.md) and [follow-up](../qa/identity-audit/20261008T1144-0700/IDENTITY_AUDIT_FOLLOW_UP.md). US-02.05 Manage Profile is **DONE in local scope** with owner-approved API behavior and Identity regression evidence in the [implementation report](../qa/identity-profile/US-02.05_IMPLEMENTATION_REPORT.md). US-02.06 Manage Addresses is **DONE in local scope** under the explicit owner contract at [US-02.06 contract](../planning/identity/US-02.06_MANAGE_ADDRESSES_CONTRACT.md), with automated, disposable PostgreSQL/HTTP and Postman evidence in the [implementation report](../qa/identity-address/US-02.06_IMPLEMENTATION_REPORT.md). US-02.07 Manage Users is implemented in local scope under the explicit approval at [US-02.07 contract](../planning/identity/US-02.07_MANAGE_USERS_CONTRACT.md), with results in the [implementation report](../qa/identity-users/US-02.07_IMPLEMENTATION_REPORT.md). These statuses are not production readiness. Workbook rows remain `To Do`; the workbook was not edited. EP-02 is not complete based on the current evidence review.
- Registration/reset test delivery uses Mailpit. The audit did not verify external SMTP or a production provider. Do not inspect or repeat values from ignored `.env`; its runtime contents are not established by this documentation pass.
- Other service scaffolds/security/health checks are not business API completion. Use each service's current source and runtime OpenAPI for evidence.

The active target models are in [`database/six-service-model`](../../database/six-service-model/ALL_DATABASES.md). Identity physical schema is in [V1](../../identity-service/src/main/resources/db/migration/V1__create_identity_tables.sql), [V2](../../identity-service/src/main/resources/db/migration/V2__create_email_verification_challenge.sql) and [V3](../../identity-service/src/main/resources/db/migration/V3__create_password_reset_challenge.sql). The active backlog source is `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx`, sheet `Backlog`; read it only unless the owner specifically requests workbook changes.

## Confirmed, observed, proposed and TBD

Cross-story EP-02 evidence is reconciled in the [EP-02 matrix](../qa/identity-users/EP-02_RECONCILIATION.md).

- **CONFIRMED** means the owner explicitly stated the rule or scope. No new DEC ID is assigned by these governance docs.
- **OBSERVED** means supported by current migration/source/test/audit evidence, without asserting broader owner approval.
- **PROPOSED** means a recommendation clearly marked `NOT APPROVED`.
- **TBD** means no approved decision or contract supports implementation.

Current open decisions relevant to upcoming work are listed in [OPEN_IMPLEMENTATION_DECISIONS.md](OPEN_IMPLEMENTATION_DECISIONS.md). The US-02.05 API, lifecycle, validation and syntax-only brand-reference contract is explicitly owner-approved and recorded there; do not revive the superseded proposal or require finite vocabularies or Commerce lookup for this story. Unrelated historical questions remain unchanged.

## US-04.02 supplement ? 2026-10-26

The owner explicitly requested completion of SePay Confirm Payment. [The scoped contract](../planning/US-04.02_CONFIRM_PAYMENT_CONTRACT.md) reintroduces SePay-only PaymentConfirmed outbox/inbox delivery and Commerce V7, superseding the earlier removal for this story only. Commerce and Payment retain separate databases and service credentials. [Current local verification](../qa/US-04.02_CONFIRM_PAYMENT_REPORT.md) includes disposable PostgreSQL and two packaged JARs; it does not assert any persistent migration or production deployment. COD collection behavior is unchanged.

## COD two-step supplement

The owner chose COD PENDING_PAYMENT -> PLACED after Payment attempt acknowledgement, followed separately by Admin cash collection. [The contract](../planning/COD_TWO_STEP_CONTRACT.md) and [local verification](../qa/COD_TWO_STEP_REPORT.md) describe this schema-preserving extension. Earlier statements that COD begins directly at PLACED are superseded for new checkout only. Existing data and SePay event transport remain unchanged.

## US-04.03 COD collection delivery - 2026-10-27

The owner requested completion of COD PaymentConfirmed delivery and explicitly approved Commerce V8 to expand the existing inbox payment_method CHECK to COD/SEPAY_VIETQR, preserving V7 and existing data. [Scoped contract](../planning/US-04.03_COD_COLLECTION_CONTRACT.md). A committed, settled ledger/outbox is delivered without Notification; Commerce records COD collection without changing fulfilment/history. This supersedes the earlier COD-delivery exclusion only for this story. No public payment-state column/API, refund or paid-cancellation workflow is approved. Local tests use disposable databases only.

## Guest checkout supplement - 2026-11-02

The owner explicitly approved guest checkout without any Identity changes and the exact Commerce V9 / Payment V2 migrations in the [guest contract](../planning/GUEST_CHECKOUT_PROPOSAL.md). Guest orders store direct delivery snapshots and a Commerce-owned session digest; Order, Payment attempt and Payment inbox customer_id may be null for guests. Existing customer checkout and COD/SePay rules remain. No synthetic Identity accounts or cross-service database access is introduced. Source/migration changes do not imply application to any persistent database. The earlier Payment JPA and mapper refactors are also explicitly owner-requested and remain scoped to Payment. See the [implementation report](../qa/GUEST_CHECKOUT_IMPLEMENTATION_REPORT.md) for current evidence and limitations.

## Guest checkout temporarily removed - 2026-11-02

Latest explicit owner instruction supersedes the active guest-checkout contract: remove the newly added guest checkout only; keep the existing guest cart and Payment JPA/mapper work. The guest controller/service/validator and public security exceptions are removed; new Payment snapshots again require a customer UUID. Existing registered checkout remains. Commerce V9 and Payment V2 are retained unchanged because their application to an owner-run Docker database is unknown; do not erase migration history or rewrite/delete persisted guest data. Null-safe financial event/ledger handling is compatibility only, not a guest creation API. Historical guest documents are marked withdrawn. See [removal evidence](../qa/GUEST_CHECKOUT_REMOVAL_REPORT.md).


## Guest checkout restored — 2026-11-02

The owner explicitly requested restoring guest checkout after the temporary removal. The guest contract and simplified phone rule are active again; Identity remains unchanged. Guest order creation, owner-scoped detail and payment recovery use the existing guest cart credential and direct delivery snapshots. New credential-authenticated internal Payment snapshots permit explicit null customer_id, while omitted/malformed owners remain invalid. Payment JPA/mapper and all existing COD/SePay guards remain. The owner confirmed Commerce V9 guest checkout has already run; its SQL remains unchanged. The conflicting, unexecuted image/inventory migration is renumbered V10 with unchanged SQL. No persistent database mutation is authorized by this restoration. See [restoration report](../qa/GUEST_CHECKOUT_RESTORATION_REPORT.md).

**Owner correction 2026-10-28 (historical):** P02 is authorized, and owned differs from saved. Its slot rules were superseded by the 2026-10-29 Prompt 08 correction recorded at the top of this document: TOP+BOTTOM only. P03-A is separately approved; all later API/schema/provider/live gates remain open.

## EP-07 — Accelerated Delivery 2 scope (2026-11-05)

The owner approved scoped Stylist Wave 2 implementation for the exact model IDs, 1536-dimensional embedding, generation/read APIs, two recommendation tables plus lifecycle, runtime Qdrant and reviewed-only Neo4j. See [the Wave 2 contract](../planning/stylist/EP-07_WAVE_2_CONTRACT.md) and [report](../planning/stylist/EP-07_WAVE_2_IMPLEMENTATION_REPORT.md). This does not complete P03/P04/R95/R99 or EP-07. No owner/profile/owned-Wardrobe producer, catalog events, other-service code, persistent database migration or production deployment is approved by this supplement. Provider calls stay off unless the bounded $2 smoke prerequisites are met.

**Stylist migration supplement (2026-11-05):** Stylist source currently has V1 session/message, V2 request/constraint and V3 recommendation/item plus lifecycle. Wave 2 applied V3 only to disposable Testcontainers. No persistent Stylist database migration was performed or inferred.

## EP-07 Gemini local/test supplement — 2026-11-06

The owner’s latest Gemini prompt authorizes only Stylist-local/test adapters and a bounded Free-Tier verification at USD 0, superseding the OpenAI-only local provider choice and proposed paid OpenAI smoke for this slice. Keep OpenAI evidence as historical. Use only `gemini-2.5-flash` and `gemini-embedding-001` at 1536 dimensions; no fallback or other provider. See the [Wave 2 contract supplement](../planning/stylist/EP-07_WAVE_2_CONTRACT.md) and [verification report](../planning/stylist/EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md).

Gemini is never selected implicitly from key presence. `STYLEMIND_GEMINI_LIVE_CALLS_ENABLED` defaults false. The current workspace had no Gemini key in its process environment and no evidence of the specific project’s AI Studio billing tier/quota; no real Google, Gateway, Commerce or customer-data calls were made. A key pasted in chat is not runtime configuration and must not be copied to source/docs. Rotate it before storing a replacement in a local secret store. Offline adapters/tests and disposable services do not mark R100/R102, P08 or EP-07 done.
