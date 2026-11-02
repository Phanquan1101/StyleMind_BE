# Documentation Reconciliation Report

**Run:** `20261008T0855Z`
**Workspace:** `C:\Users\FPTSHOP\Desktop\StyleMind_BE`

**Baseline:** branch `main`, HEAD `b46bd5bd1d77af83edd3de5969c9cc707f7c183c`

This pass changed documentation/governance only. The worktree was already dirty with T0–T4 and Identity implementation changes; those files were preserved. No business source, security code, tests, migrations, database, Compose, `.env`, signing key, workbook, service boundary, commit or push was changed. No migration/runtime/test command was run for this documentation task.

## Current source-of-truth map

1. Latest explicit owner decisions — current scope and business behavior.
2. `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx`, sheet `Backlog` — current story/task selection, read only.
3. `database/six-service-model/*.mmd` and `ALL_DATABASES.md` — six-service logical ownership and target models.
4. Versioned/applied migrations — physical schema.
5. Approved/current contracts — API behavior and side effects.
6. Source, tests and audits — observed implementation/verification evidence only.

When sources conflict, the current owner scope takes precedence. Archived material remains intact and retains all original decision identifiers, but is not execution guidance.

## Six service / database mapping

| Service | Database | Scope |
|---|---|---|
| `identity-service` | `stylemind_identity` | Accounts and authentication |
| `commerce-service` | `stylemind_commerce` | Catalog, Cart, Checkout and Order |
| `payment-service` | `stylemind_payment` | Payment and payment exceptions |
| `wardrobe-community-service` | `stylemind_wardrobe_community` | Wardrobe sharing and community |
| `stylist-service` | `stylemind_stylist` | AI Stylist |
| `notification-service` | `stylemind_notification` | Notification delivery |

Commerce is one application; do not restore Catalog/Cart/Checkout applications or create Social. Do not implement stock/inventory, reservation, return, refund, QC or restock. Branch and Cancellation Request remain in the current models. Paid cancellation and payment-exception rules remain TBD. No cross-service database access/FKs. Production topology remains TBD.

## Document dispositions

### Kept active

- Root/service READMEs; six-service Mermaid models; Identity/Notification implementation descriptions; current Identity audit/follow-up and QA evidence; Postman collection; application source/config/tests/migrations.
- All pre-existing T0–T4 and Identity implementation changes.

### Edited

- `README.md`: six services, local ports/security distinctions, current Identity migration counts, audit links, backlog source and archive pointer.
- `identity-service/README.md`: audit-backed US-02.01–US-02.04 local status; US-02.05–US-02.07 not implemented and EP-02 not complete; provider test boundary.
- `notification-service/README.md`: Mailpit local default, user-managed external SMTP boundary and no production-provider claim.
- `database/six-service-model/01_identity.mmd`: current migration-backed 11-table Identity diagram.
- `database/six-service-model/ALL_DATABASES.md`: six current owners, 48 logical tables, Identity physical schema status, five target-only schemas, no Redis OTP claim, current authority and validation limits.

### Added

- Root `AGENTS.md` as a short project harness.
- `CURRENT_PROJECT_CONTEXT.md`, `CURRENT_BUSINESS_RULES.md`, `OPEN_IMPLEMENTATION_DECISIONS.md` and this report.
- Archive README and hash manifest.

### Archived (55 files; original relative directories preserved)

All files were moved under `docs/archive/legacy-baseline/20261008T0855Z/`. The complete exact-path/classification/reason/SHA-256 record is in [MANIFEST.md](../archive/legacy-baseline/20261008T0855Z/MANIFEST.md). The archive includes:

- earlier raw input and discovery packs (`docs/input`, `docs/discovery`);
- Phase 0.5 proposed decision packages (`docs/decision-review`);
- historical decision records and IDs (`docs/decisions`);
- earlier broad domain, SAD and BRD/PRD packages (`docs/domain`, `docs/architecture`, `docs/requirements`);
- outdated security, AI and first-slice test specs (`docs/specifications`);
- static unconsumed OpenAPI and event schemas (`contracts`);
- the seven-application physical readiness review;
- the nine-table Identity SVG and stale 46-table validation snapshot.

The documents were inspected before classification. Archiving does not amend or close a historical decision. Static contracts had no production-source, build or script consumers; live service OpenAPI remains documented at `/v3/api-docs`. No file content was deleted. Every moved file's pre-move SHA-256 matched its archived SHA-256.

## Conflicts resolved and remaining

- DEC-036's seven-application mapping, old PRD/SAD return/refund/inventory baseline, `ROLE_USER` spec, Catalog-first Kafka/outbox test strategy and static Register-JWT OpenAPI were retained only as historical archive files.
- Root README no longer says Identity issuer/audience validation is unconfigured: Identity validates its issuer/audience and roles; the other resource servers are documented as not having equivalent integration.
- Identity is documented with 11 physical business tables: nine V1 tables, V2 `email_verification_challenge`, V3 `password_reset_challenge`. Five other service models are explicitly `TARGET LOGICAL MODEL / NOT YET MIGRATED`.
- US-02.05 API/field details, profile row lifecycle, PATCH/PUT/null behavior, preference vocabulary/ranges, Commerce brand-reference validation and profile data use remain unresolved. Existing `OPEN-035/036/037/038/046/051` references are preserved; no new IDs were invented.
- Paid cancellation/payment-exception behavior and production topology/provider operations remain TBD.

## Verification

- Read the workbook only. `Backlog` physical Excel rows 19–21 (US-02.05; one-based and including header row 1) remain `To Do`; these correspond to data-row indices 18–20 with the header excluded. Workbook SHA-256: `a4027b00a89a5e41135fca94140afdd81173ca7e79d63bbacce0072d16a92a42`.
- Archived manifest: 55 rows; all recorded before/after/archive hashes match, and original paths were absent after move. Archive files remain readable.
- Identity V1/V2/V3 SHA-256 values remain respectively `937A0F0B96858F73E55B5C7D5B74A001AB4BD66723139FB5E043F12324E5CF25`, `FC823A12C9B89F94ABD7E91A87906B841712DE310FC141EAD3DFB5846019703F`, and `57F226251E959E983213E014F6E279E176FCCCAADC4D350A06CA69E5A674BF48`.
- SHA-256 aggregate over 188 protected source, test, migration, configuration, Compose, scripts and Postman files was unchanged: `e07de1605b719bc46e5bbd7e9ef6e15e1d6a57afb7ce995c7644398b42b44285`.
- Active plus archived Markdown relative links were checked; all resolved at verification time.
- Identity model source and embedded ERD each contain 11 tables with matching attributes. The five remaining model counts are 13, 3, 10, 9 and 2, for 48 logical tables total.
- `git diff --check` completed without whitespace errors (Git emitted line-ending normalization notices). The extra staged-diff check still reports pre-existing blank lines at EOF in `00_service_databases.mmd`, `01_identity.mmd` and `06_notification.mmd`; the existing staged files were not altered to address those reports.
- No Mermaid CLI/parser is installed. Diagram counts/attributes were structurally cross-checked, but no fresh render or full parser validation was claimed.
- No runtime smoke, migration, Compose or test execution occurred. `.env` was not read; credential/secret files were not changed. External SMTP delivery and production readiness remain unverified.

## Phase A readiness

US-02.05 is **ready to begin Phase A (read-only contract and implementation planning)**. Its backlog scope and existing V1 attributes bound that analysis. It is **not ready for Phase B implementation** until the open profile decisions above are resolved. This reconciliation did not begin US-02.05 or add endpoints/tests.
