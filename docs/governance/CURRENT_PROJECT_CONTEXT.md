# Current StyleMind Project Context

**As of:** 2026-10-12<br>
**Workspace:** `C:\Users\FPTSHOP\Desktop\StyleMind_BE`<br>

This is the active context for implementation agents in this repository. Read it with [the business rules](CURRENT_BUSINESS_RULES.md), [open implementation decisions](OPEN_IMPLEMENTATION_DECISIONS.md), and the requested rows of the workbook. Earlier discovery/specification packages are preserved under [the legacy archive](../archive/legacy-baseline/20261008T0855Z/README.md); they are not current execution baselines.

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
- Identity is the only service with business Flyway migrations: V1 creates nine tables, V2 adds `email_verification_challenge`, and V3 adds `password_reset_challenge`. The last persistent-local audit recorded V1–V3 successful for `stylemind_identity`; this documentation reconciliation did not connect to or change that database.
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
