# Open Implementation Decisions

Only current decisions that affect the owner-directed project scope are listed here. Existing `OPEN-*` references retain their original meanings and point into the [archived question register](../archive/legacy-baseline/20261008T0855Z/docs/discovery/06_OPEN_QUESTIONS.md). No new DEC/OPEN/DR identifier is created or closed by this document. Any remaining proposal is explicitly **NOT APPROVED**.

## US-02.05 — Manage Profile

The owner approved the Phase A contract groups A/B/C in the explicit instruction dated 2026-10-08. Those decisions supersede all `PROPOSED — NOT APPROVED` options and TBD notes in the historical Phase A analysis and are the active baseline for this story. Backlog `Backlog` rows 19–21 remain unchanged in the workbook; no workbook status was edited.

The approved API is `GET/PATCH /api/v1/me` and `GET/PUT /api/v1/me/style-profile`, protected for JWT roles `CUSTOMER` and `ADMIN`, self-only by JWT `sub`. GETs do not create rows. PATCH accepts only required nonblank `display_name`; PUT is full replacement with all eight properties present, nullable scalar fields and required non-null arrays. Unknown fields are rejected. Missing JWT-subject account is `404 RESOURCE_NOT_FOUND`.

Approved validation uses Unicode trimming/code-point limits for names and free-form size labels, syntax-only uppercase canonicalization for fit/style/color codes, exact `BigDecimal` height from 50–250 cm with at most one fractional digit, and at most 50 unique values per collection. `brand_ids` uses hyphenated UUID syntax and uniqueness only. A valid UUID does not prove a Commerce brand exists or is active; Identity makes no Commerce call. This story adds no finite vocabulary, seed data, migration, table, column or cross-service FK.

The approved aggregate write contract locks `account` first, then upserts and replaces all preference rows in one transaction; GET style uses a consistent read snapshot. Concurrent writes serialize with last serialized writer wins. Local implementation and verification evidence is recorded in [the implementation report](../qa/identity-profile/US-02.05_IMPLEMENTATION_REPORT.md). Historical `OPEN-035/036/037/038/046/051` identifiers and archived records are retained unchanged; this US-scoped approval is not a global closure of those historical questions.

## US-02.06 — Manage Addresses

The owner provided a story-specific implementation contract for US-02.06. It authorizes the existing `customer_address` table only, self-only JWT-sub ownership for CUSTOMER/ADMIN, account-row locking for writes, the default-address rules, the eight API routes, request validation, pinned local Vietnam administrative data, and Vietnamese phone validation. The full behavior is captured in [the US-02.06 contract](../planning/identity/US-02.06_MANAGE_ADDRESSES_CONTRACT.md). This approval adds no migration/schema and does not close or reinterpret other open decisions. Current verification and any remaining gates are recorded in [the implementation report](../qa/identity-address/US-02.06_IMPLEMENTATION_REPORT.md).

## US-02.07 — Manage Users

The owner explicitly approved the Manage Users API, live Admin actor checks, `ACTIVE`/`SUSPENDED` transition rules, complete `CUSTOMER`/`ADMIN` replacement, no-op/self-change behavior, last qualifying Admin guard, session effects, and operator-controlled first-Admin provisioning. The owner later approved a separate local Compose-only exception: an explicitly enabled first-Admin bootstrap may create a new `ACTIVE`, email-verified `ADMIN` from ignored environment variables, without OTP, while never changing an existing account. The complete API contract is [US-02.07_MANAGE_USERS_CONTRACT.md](../planning/identity/US-02.07_MANAGE_USERS_CONTRACT.md); first-Admin procedures are documented in [FIRST_ADMIN_PROVISIONING.md](../operations/identity/FIRST_ADMIN_PROVISIONING.md); local implementation/test evidence is in [the implementation report](../qa/identity-users/US-02.07_IMPLEMENTATION_REPORT.md). This story adds no Identity migration. The workbook’s rows 25–27 remain unchanged; this story-scoped approval does not close or reinterpret historical `OPEN-*` items or other cross-story questions.

## Current cross-story decisions — not US-02.05, US-02.06 or US-02.07 blockers

- Cancellation of an already-paid Order when current model excludes refund, and handling late/mismatched/duplicate payment exceptions remain TBD under the current owner scope. Historical references include `OPEN-018`, `OPEN-019` and `OPEN-021`; see [the archived register](../archive/legacy-baseline/20261008T0855Z/docs/discovery/06_OPEN_QUESTIONS.md). A cancellation record does not itself authorize changing Order state or moving money.
- Production database topology, key provisioning/rotation, service authentication, abuse controls and email delivery provider/operations remain TBD. Local Mailpit and owner-provided workstation SMTP configuration are not production approval or external-delivery evidence. No identifier is added for these questions.
- A provider-specific production setup must not be inferred from Mailpit configuration or from historical local SMTP instructions. No application code, credential or configuration was changed to record these items.

## US-04.02 scoped acceptance ? 2026-10-26

The explicit completion request authorizes the SePay-only inbox/transport and terminal protection in [the current contract](../planning/US-04.02_CONFIRM_PAYMENT_CONTRACT.md), including Commerce V7. It supersedes the earlier removal only for US-04.02 and does not restore the COD SHIPPED prerequisite. Manual reconciliation resolution, refunds/paid-order cancellation, fresh attempts after expiry and production provisioning remain TBD.

## COD two-step acceptance

The owner explicitly chose the existing-schema option: pending COD setup uses PENDING_PAYMENT and automatic Order confirmation uses PLACED; cash collection is an independent Admin action. No CONFIRMED migration or SHIPPED-only collection guard is approved/added by this task. Notification, best-effort cart redesign, COD event delivery, refunds and manual reconciliation remain separate scope/TBD. [Contract](../planning/COD_TWO_STEP_CONTRACT.md).

## US-04.03 COD collection delivery - 2026-10-27

The owner requested completion of COD PaymentConfirmed delivery and explicitly approved Commerce V8 to expand the existing inbox payment_method CHECK to COD/SEPAY_VIETQR, preserving V7 and existing data. [Scoped contract](../planning/US-04.03_COD_COLLECTION_CONTRACT.md). A committed, settled ledger/outbox is delivered without Notification; Commerce records COD collection without changing fulfilment/history. This supersedes the earlier COD-delivery exclusion only for this story. No public payment-state column/API, refund or paid-cancellation workflow is approved. Local tests use disposable databases only.
