# Open Implementation Decisions

**Owner persistence decision, 2026-10-28:** Use Spring Data JPA/Hibernate for forthcoming Stylist persistence, superseding previous JDBC instructions. Retain Controller -> Service interface/impl -> Repository. Schema/API/P03 implementation are not approved by this framework decision. Do not refactor other services.

**Latest EP-07 owner decision, 2026-10-29 (Prompt 08):** TOP+BOTTOM only; one of each in every outfit. P03-A is explicitly authorized for create-session/read-history APIs and Flyway V1 containing only session and message tables. See [P03-A handover](../planning/stylist/EP-07_P03A_SESSION_HISTORY_REPORT.md). This later scoped approval supersedes the older P03-not-approved line below for P03-A only. P03 overall, request admission, live context clients, provider/retrieval/recommendation and persistent database application remain open or unapproved.


Only current decisions that affect the owner-directed project scope are listed here. Existing `OPEN-*` references retain their original meanings and point into the [archived question register](../archive/legacy-baseline/20261008T0855Z/docs/discovery/06_OPEN_QUESTIONS.md). No new DEC/OPEN/DR identifier is created or closed by this document. Any remaining proposal is explicitly **NOT APPROVED**.

## EP-07 â€” AI Stylist planning, reviewed 2026-10-27

The owner requested an updated plan/harness, not implementation approval. [The current planning index](../planning/stylist/README.md), [contract decision groups](../planning/stylist/EP-07_PHASE_A_CONTRACT_MEMO.md#9-decision-gate--toi-da-nam-nhom) and [execution manifest](../planning/stylist/EP-07_EXECUTION_MANIFEST.json) record proposed API/context rules, outfit/source rules, schema/lifecycle, AI/data/budget and producer/events/knowledge prerequisites. Existing JDBC instructions remain in force despite the scaffold JPA dependency. The verified backlog/target owned-item requirement conflicts with current Wardrobe shop-only saved-outfit persistence; neither side is silently treated as a new scope decision. Do not invent a DEC/OPEN ID, close old questions, approve a model/provider or create migrations from this planning update.

## US-02.05 â€” Manage Profile

The owner approved the Phase A contract groups A/B/C in the explicit instruction dated 2026-10-08. Those decisions supersede all `PROPOSED â€” NOT APPROVED` options and TBD notes in the historical Phase A analysis and are the active baseline for this story. Backlog `Backlog` rows 19â€“21 remain unchanged in the workbook; no workbook status was edited.

The approved API is `GET/PATCH /api/v1/me` and `GET/PUT /api/v1/me/style-profile`, protected for JWT roles `CUSTOMER` and `ADMIN`, self-only by JWT `sub`. GETs do not create rows. PATCH accepts only required nonblank `display_name`; PUT is full replacement with all eight properties present, nullable scalar fields and required non-null arrays. Unknown fields are rejected. Missing JWT-subject account is `404 RESOURCE_NOT_FOUND`.

Approved validation uses Unicode trimming/code-point limits for names and free-form size labels, syntax-only uppercase canonicalization for fit/style/color codes, exact `BigDecimal` height from 50â€“250 cm with at most one fractional digit, and at most 50 unique values per collection. `brand_ids` uses hyphenated UUID syntax and uniqueness only. A valid UUID does not prove a Commerce brand exists or is active; Identity makes no Commerce call. This story adds no finite vocabulary, seed data, migration, table, column or cross-service FK.

The approved aggregate write contract locks `account` first, then upserts and replaces all preference rows in one transaction; GET style uses a consistent read snapshot. Concurrent writes serialize with last serialized writer wins. Local implementation and verification evidence is recorded in [the implementation report](../qa/identity-profile/US-02.05_IMPLEMENTATION_REPORT.md). Historical `OPEN-035/036/037/038/046/051` identifiers and archived records are retained unchanged; this US-scoped approval is not a global closure of those historical questions.

## US-02.06 â€” Manage Addresses

The owner provided a story-specific implementation contract for US-02.06. It authorizes the existing `customer_address` table only, self-only JWT-sub ownership for CUSTOMER/ADMIN, account-row locking for writes, the default-address rules, the eight API routes, request validation, pinned local Vietnam administrative data, and Vietnamese phone validation. The full behavior is captured in [the US-02.06 contract](../planning/identity/US-02.06_MANAGE_ADDRESSES_CONTRACT.md). This approval adds no migration/schema and does not close or reinterpret other open decisions. Current verification and any remaining gates are recorded in [the implementation report](../qa/identity-address/US-02.06_IMPLEMENTATION_REPORT.md).

## US-02.07 â€” Manage Users

The owner explicitly approved the Manage Users API, live Admin actor checks, `ACTIVE`/`SUSPENDED` transition rules, complete `CUSTOMER`/`ADMIN` replacement, no-op/self-change behavior, last qualifying Admin guard, session effects, and operator-controlled first-Admin provisioning. The owner later approved a separate local Compose-only exception: an explicitly enabled first-Admin bootstrap may create a new `ACTIVE`, email-verified `ADMIN` from ignored environment variables, without OTP, while never changing an existing account. The complete API contract is [US-02.07_MANAGE_USERS_CONTRACT.md](../planning/identity/US-02.07_MANAGE_USERS_CONTRACT.md); first-Admin procedures are documented in [FIRST_ADMIN_PROVISIONING.md](../operations/identity/FIRST_ADMIN_PROVISIONING.md); local implementation/test evidence is in [the implementation report](../qa/identity-users/US-02.07_IMPLEMENTATION_REPORT.md). This story adds no Identity migration. The workbookâ€™s rows 25â€“27 remain unchanged; this story-scoped approval does not close or reinterpret historical `OPEN-*` items or other cross-story questions.

## Current cross-story decisions â€” not US-02.05, US-02.06 or US-02.07 blockers

- Cancellation of an already-paid Order when current model excludes refund, and handling late/mismatched/duplicate payment exceptions remain TBD under the current owner scope. Historical references include `OPEN-018`, `OPEN-019` and `OPEN-021`; see [the archived register](../archive/legacy-baseline/20261008T0855Z/docs/discovery/06_OPEN_QUESTIONS.md). A cancellation record does not itself authorize changing Order state or moving money.
- Production database topology, key provisioning/rotation, service authentication, abuse controls and email delivery provider/operations remain TBD. Local Mailpit and owner-provided workstation SMTP configuration are not production approval or external-delivery evidence. No identifier is added for these questions.
- A provider-specific production setup must not be inferred from Mailpit configuration or from historical local SMTP instructions. No application code, credential or configuration was changed to record these items.

## US-04.02 scoped acceptance ? 2026-10-26

The explicit completion request authorizes the SePay-only inbox/transport and terminal protection in [the current contract](../planning/US-04.02_CONFIRM_PAYMENT_CONTRACT.md), including Commerce V7. It supersedes the earlier removal only for US-04.02 and does not restore the COD SHIPPED prerequisite. Manual reconciliation resolution, refunds/paid-order cancellation, fresh attempts after expiry and production provisioning remain TBD.

## COD two-step acceptance

The owner explicitly chose the existing-schema option: pending COD setup uses PENDING_PAYMENT and automatic Order confirmation uses PLACED; cash collection is an independent Admin action. No CONFIRMED migration or SHIPPED-only collection guard is approved/added by this task. Notification, best-effort cart redesign, COD event delivery, refunds and manual reconciliation remain separate scope/TBD. [Contract](../planning/COD_TWO_STEP_CONTRACT.md).

## US-04.03 COD collection delivery - 2026-10-27

The owner requested completion of COD PaymentConfirmed delivery and explicitly approved Commerce V8 to expand the existing inbox payment_method CHECK to COD/SEPAY_VIETQR, preserving V7 and existing data. [Scoped contract](../planning/US-04.03_COD_COLLECTION_CONTRACT.md). A committed, settled ledger/outbox is delivered without Notification; Commerce records COD collection without changing fulfilment/history. This supersedes the earlier COD-delivery exclusion only for this story. No public payment-state column/API, refund or paid-cancellation workflow is approved. Local tests use disposable databases only.

**Owner correction 2026-10-28 (historical):** P02 was authorized, and owned differs from saved. The 2026-10-29 Prompt 08 decision at the top of this document supersedes its slot rule and separately approves P03-A only. Remaining context, request admission, provider and live integration decisions remain open.

## EP-07 Wave 2 — scoped decisions recorded 2026-11-05

The owner attachment “STYLEMIND — ACCELERATED DELIVERY 2” resolves the prior implementation gates only for the implementation recorded in the [Wave 2 contract](../planning/stylist/EP-07_WAVE_2_CONTRACT.md): Spring AI 1.1-compatible line, exact OpenAI model IDs, generation/recommendation APIs, V3's two recommendation tables/lifecycle, runtime Qdrant/Neo4j adapters, reviewed catalog/knowledge gates, and scoped verification. It does not resolve P08 catalog event transport, R95 owner-source producers, live Commerce mapping or production deployment. No persistent V3 migration is approved.

Current evidence is in [the implementation report](../planning/stylist/EP-07_WAVE_2_IMPLEMENTATION_REPORT.md). Provider smoke was not executed because the configured API key was absent; no API key value was read. Gateway/Newman and live cross-service acceptance remain unverified. EP-07 remains incomplete.

## VNPAY sandbox integration — approved 2026-11-13

The owner explicitly requested an additive VNPAY sandbox method and confirmed Payment V5 plus Commerce V17 as the exact migration scope. Callback, amount, lifecycle and configuration rules are recorded in [VNPAY_SANDBOX_CONTRACT.md](../planning/VNPAY_SANDBOX_CONTRACT.md). Sandbox credentials remain operator-supplied secrets. Persistent migration, production configuration, refunds and paid-cancellation behavior remain out of scope/TBD.
