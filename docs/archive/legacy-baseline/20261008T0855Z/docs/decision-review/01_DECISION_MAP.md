# StyleMind Phase 0.5 Decision Map

Status: HISTORICAL REVIEW MAP + BASELINE RECONCILIATION. The original clustering and options remain preserved as Phase 0.5 review evidence. DR-001, DR-002, DR-005 through DR-010 and DR-012 were subsequently approved by the human owner and are recorded as DEC-026 through DEC-034; the nine remaining clusters remain deferred.

## How to use the map

Each of the 62 OPEN records has exactly one primary cluster. Dependencies refer to other clusters' required outcomes, not duplicate OPEN ownership. The cluster purpose, grouping, urgency and dependency analysis are DERIVED/PROPOSED review guidance. Source facts, rules and decisions retain their Phase 0 classifications. Original references are to docs/discovery/02_REQUIREMENTS_INVENTORY.md, 03_BUSINESS_RULES_INVENTORY.md and 06_OPEN_QUESTIONS.md unless stated otherwise.

NOW originally meant prepare a human choice in the first review meeting. Human review has now been completed for DR-001, DR-002, DR-005 through DR-010 and DR-012. Technical or mixed-cluster subdetails explicitly left TBD in the approved records remain unresolved. BEFORE PRD and BEFORE SAD are deadlines for the specified decision layer, not permission to start those documents in this phase.

There are 18 clusters: 9 resolved and 9 scheduled later. The remaining clusters comprise 6 BEFORE PRD and 3 BEFORE SAD. Technical subdetails may deliberately wait until Security, AI Specification or BEFORE IMPLEMENTATION as recorded below. These are proposed schedules, not approved deferrals.

## DR-001 Business ownership and success baseline

Purpose: identify the accountable business approver and the outcomes by which the existing commerce/styling goals will be judged. Decision type: product/business governance.

Primary OPEN IDs: OPEN-001.

Dependencies: None.

Affected requirements/business rules: BUS-001, BUS-002, BUS-003, BUS-004, ARCH-008. No new business invariant is proposed.

Blocked future artifacts: BRD; PRD; Test Strategy; Roadmap.

Urgency: NOW. Status: RESOLVED BY DEC-026. The Solo Owner, approval roles, acceptance baseline and KPI policy are recorded in the authoritative decision record. Business outcome KPI values remain TBD.

## DR-002 Target segment and first milestone boundary

Purpose: decide the target segment and first milestone's capability boundary while preserving women-first and the broader scope. Decision type: product.

Primary OPEN IDs: OPEN-002, OPEN-003.

Dependencies: DR-001 for accountable approval and business outcomes.

Affected requirements/business rules: BUS-001, BUS-002, BUS-003, FR-009, FR-037, FR-038, AI-003, UX-002, BR-032, BR-033.

Blocked future artifacts: BRD; PRD; AI Specification; Roadmap.

Urgency: NOW. Status: RESOLVED BY DEC-027. Female/women-first diverse-style targeting and the V1 boundary are recorded. Detailed feature policies and technical choices remain later work.

## DR-003 Identity and authorization lifecycle

Purpose: review registration/reset/account-state behavior, admin safeguards and trust/permission expectations together. Decision type: product/security, followed by architecture/security detail.

Primary OPEN IDs: OPEN-004, OPEN-005, OPEN-029, OPEN-057, OPEN-058, OPEN-059.

Dependencies: DR-002 for actors/capabilities in the milestone; DR-015 for sensitive-data access and lifecycle decisions.

Affected requirements/business rules: FR-001, FR-002, FR-003, FR-034, SEC-001 through SEC-008, BR-001 through BR-005, BR-009, BR-038.

Blocked future artifacts: PRD; Domain Model; Security; API Contracts; Test Strategy.

Urgency: BEFORE PRD. User-visible account/reset/lock/permission behavior is needed then. Exact JWT key rotation, token transport/custody and service-token implementation wait for Security and BEFORE IMPLEMENTATION. The already confirmed RSA/JWT/internal-token constraints are retained; no mechanism is approved here. Queue B.

## DR-004 Commerce data semantics and historical records

Purpose: settle the meaning and lifecycle of catalog selections and customer address/order snapshots used throughout shopping. Decision type: product/domain. This groups reference-data semantics and public/history behavior because checkout must interpret the same records.

Primary OPEN IDs: OPEN-006, OPEN-007, OPEN-008, OPEN-013, OPEN-020, OPEN-050.

Dependencies: DR-002 for supported segment/market and scope.

Affected requirements/business rules: FR-005 through FR-011, FR-022, DATA-001, DATA-002, DATA-007, BR-005 through BR-008, BR-014.

Blocked future artifacts: PRD; Domain Model; Context Map; API Contracts; Test Strategy.

Urgency: BEFORE PRD. Product/Variant/SKU meanings, activity/deletion, address default and historical display need shared business definitions. Dataset version/refresh integration details can follow in contracts. This does not establish service ownership. Queue B.

## DR-005 Guest and user cart behavior

Purpose: choose session/quantity and merge outcomes using the approved catalog meanings. Decision type: product with later consistency/security details.

Primary OPEN IDs: OPEN-009, OPEN-010.

Dependencies: DR-004 for variant/activity/price meanings.

Affected requirements/business rules: FR-012, FR-013, FR-014, FR-015, BR-009, BR-010, BR-011, BR-039.

Previously blocked artifacts: PRD; Domain Model; API Contracts; Security; Test Strategy. PRD blocker removed by DEC-030; residual session/security/contract details remain at their later gates.

Urgency: BEFORE PRD. Status: RESOLVED BY DEC-030. Atomic merge, conflict, price-change notice, retry and Guest Cart clear/retire outcomes are approved. Guest-session lifecycle, quantity bounds and add-versus-set semantics remain TBD and must be handled with DR-003/later contracts. Queue B historically.

## DR-006 Stock and checkout completion guarantees

Purpose: determine business outcomes for stock allocation, partial checkout failure, retries and compensation before allocating technical responsibility. Decision type: product/domain, then architecture.

Primary OPEN IDs: OPEN-011, OPEN-014, OPEN-054.

Dependencies: DR-004 for product/variant/snapshot meanings; DR-005 for cart outcomes; DR-007 for amount semantics.

Affected requirements/business rules: FR-016, FR-040, ARCH-007, NFR-001, BR-010, BR-012 through BR-017, BR-039, BR-040.

Previously blocked artifacts: PRD; Domain Model; Context Map; SAD; API Contracts; Test Strategy. PRD blocker removed by DEC-031; stock ownership, compensation and technical recovery remain later-gate work.

Urgency: BEFORE PRD. Status: RESOLVED BY DEC-031. Atomic product-level checkout, revalidation, price reconfirmation, no partial checkout and recoverable failure outcomes are approved. Stock owner/reservation/mutation and compensation mechanisms remain deferred to later artifacts; no saga, broker or new service is selected. Queue B historically.

## DR-007 Money and amount interpretation

Purpose: agree currency, effective prices, rounding and applicable fee/tax/refund calculation meanings. Decision type: business/product.

Primary OPEN IDs: OPEN-012.

Dependencies: DR-002 for commercial scope.

Affected requirements/business rules: FR-016, FR-030, AI-004, BR-008, BR-014, BR-015, BR-030, BR-034.

Previously blocked artifacts: PRD; Domain Model; API Contracts; AI Specification; Test Strategy. PRD blocker removed by DEC-032; unrelated detailed formulas remain at their owning later gates.

Urgency: BEFORE PRD. Status: RESOLVED BY DEC-032. VND, whole-đồng rounding, backend authority, zero shipping fee, no separate application tax/VAT line, no V1 promotions and refund basis are approved. Queue B historically.

## DR-008 Payment and order reconciliation

Purpose: agree COD settlement, SePay exception handling, order/payment state correspondence and cancellation/expiry races. Decision type: business/domain, then integration/security detail.

Primary OPEN IDs: OPEN-015, OPEN-016, OPEN-017, OPEN-018, OPEN-019.

Dependencies: DR-003 for authorized actors and credential expectations; DR-006 for completion/failure guarantees; DR-007 for money interpretation.

Affected requirements/business rules: FR-017 through FR-021, SEC-009, INT-002, BR-016 through BR-022, BR-039.

Previously blocked artifacts: PRD; Domain Model; SAD; API Contracts; Security; Test Strategy. PRD blocker removed by DEC-033; webhook protocol and detailed state mapping remain later-gate work.

Urgency: BEFORE PRD. Status: RESOLVED BY DEC-033. Strict authoritative exact-match confirmation, 15-minute payment-attempt expiry, abnormal/late exception handling, COD collection meaning and terminal-state protection are approved. Exact webhook credentials/acknowledgments/retries and detailed actor/event mapping remain deferred. Queue B historically.

## DR-009 Cancellation returns refunds and payout

Purpose: agree one coherent post-purchase policy from cancellation eligibility through return/QC, valid quantities and refund/payout outcomes. Decision type: business/product/domain.

Primary OPEN IDs: OPEN-021, OPEN-023, OPEN-024, OPEN-025, OPEN-026, OPEN-027.

Dependencies: DR-007 for calculation meanings; DR-008 for order/payment outcomes and allowed state interactions.

Affected requirements/business rules: FR-023, FR-024, FR-026 through FR-031, BR-023 through BR-031.

Previously blocked artifacts: PRD; Domain Model; Context Map; API Contracts; Security; Test Strategy. PRD blocker removed by DEC-034; detailed QC, shipment and payout controls remain later-gate work.

Urgency: BEFORE PRD. Status: RESOLVED BY DEC-034. Cancellation states/scope/reason/review, seven-day Completed-only returns, item/partial/full quantities, no exchange, admin review/QC and refund basis/destination direction are approved. Detailed QC/restock, carrier, payout access/change and concurrency rules remain deferred. Queue B historically.

## DR-010 Smart Wardrobe capability boundary

Purpose: decide the first approved level of owned-clothes styling and what definition work is still required for items and optional capabilities. Decision type: product/domain.

Primary OPEN IDs: OPEN-032, OPEN-033, OPEN-034.

Dependencies: DR-002 for first-milestone placement.

Affected requirements/business rules: FR-037, FR-039, DATA-006, AI-018, BR-032, BR-033.

Blocked future artifacts: PRD; Domain Model; AI Specification; Roadmap.

Urgency: NOW. Status: RESOLVED BY DEC-028. Wardrobe V1 CRUD, image upload, basic metadata, browsing, AI context and owned-versus-Shop distinction are approved. Final data model and optional operations remain TBD.

## DR-011 Style brand fit body and color semantics

Purpose: agree personalization vocabulary and responsibility expectations across explicit preferences, brands, optional measurements, sizing and color matching. Decision type: product/domain, followed by AI responsibility design.

Primary OPEN IDs: OPEN-035, OPEN-036, OPEN-037, OPEN-038, OPEN-046.

Dependencies: DR-004 for product/variant metadata meanings; DR-010 for the chosen owned-item capability boundary.

Affected requirements/business rules: AI-009 through AI-013, DATA-003 through DATA-005, BR-033, BR-042.

Blocked future artifacts: PRD; Domain Model; AI Specification; Context Map; Test Strategy.

Urgency: BEFORE PRD. Capture user-visible behavior and optionality first; algorithm details and graph/rule/metadata ownership are settled with DR-013/DR-016 before relevant architecture/specifications. A conditional brand preference is not a confirmed hard filter. Queue B.

## DR-012 Outfit promises and AI acceptance approach

Purpose: decide how humans will judge outfit constraints and AI readiness, separating product promises from provider/runtime choices. Decision type: AI product/evaluation, followed by runtime constraints.

Primary OPEN IDs: OPEN-041, OPEN-047, OPEN-048, OPEN-049.

Dependencies: DR-002 for milestone outcomes; DR-010 for owned-versus-catalog scope.

Affected requirements/business rules: AI-004, AI-005, AI-015, AI-016, AI-017, BR-032, BR-033, BR-034, BR-042.

Blocked future artifacts: PRD; AI Specification; Test Strategy; Roadmap.

Urgency: NOW. Status: RESOLVED BY DEC-029. V1 AI promises, hard-constraint precedence, product grounding and scenario-based Pass/Fail acceptance are approved. Provider/runtime, subjective thresholds and detailed constraint semantics remain TBD.

## DR-013 AI retrieval synchronization and knowledge authority

Purpose: audit the existing pipeline and define authoritative product/knowledge data, hydration, freshness and approval/deletion responsibilities. Decision type: AI/domain architecture with knowledge governance.

Primary OPEN IDs: OPEN-040, OPEN-042, OPEN-043, OPEN-044.

Dependencies: DR-004 for product metadata; DR-011 for personalization/color semantics; DR-012 for product promises; DR-015 for knowledge/data-use constraints.

Affected requirements/business rules: AI-002, AI-003, AI-006, AI-007, AI-008, INT-003, BR-032, BR-035, BR-036, BR-042.

Blocked future artifacts: Context Map; SAD; API Contracts; Security; AI Specification; Test Strategy.

Urgency: BEFORE SAD. Product grounding and knowledge approval already constrain the system. The specific allocation to Qdrant, Neo4j, product hydration and synchronization must follow those business meanings; it is not a BRD blocker. Knowledge source metadata and review rules must also be ready before the affected knowledge feature's detailed specification. Queue C.

## DR-014 Travel and visual experience boundary

Purpose: refine the contextual/visual styling promises within the milestone boundary and separate them from weather/packing or 3D technology choices. Decision type: product/UX.

Primary OPEN IDs: OPEN-045, OPEN-062.

Dependencies: DR-002 for scope/timing; DR-010 for owned-clothes scope; DR-011 for personalization semantics; DR-012 for outfit promises.

Affected requirements/business rules: FR-038, UX-001, UX-002, UX-003, INT-005, BR-032, BR-033, BR-034.

Blocked future artifacts: PRD; AI Specification; Test Strategy; Roadmap.

Urgency: BEFORE PRD. This applies to any included travel/visual slice. Exact 3D technology and unapproved weather/packing details may be LATER if the human milestone decision defers those capabilities; no automatic exclusion follows. Live 3D remains overall scope. Queue B.

## DR-015 Sensitive data conversation and AI trust

Purpose: agree permitted collection/use, history/deletion/memory behavior, access expectations and boundaries for chat/URL knowledge input. Decision type: product/data governance, then security.

Primary OPEN IDs: OPEN-039, OPEN-051, OPEN-060.

Dependencies: DR-010 for owned-item scope; DR-011 for approved personalization data; DR-012 for AI promises and context use.

Affected requirements/business rules: AI-001, AI-014, DATA-008, SEC-011, SEC-012, FR-031, BR-031, BR-033, BR-035, BR-041.

Blocked future artifacts: PRD; Domain Model; Security; AI Specification; API Contracts; Test Strategy.

Urgency: BEFORE PRD. Data-use/deletion behavior is a product choice, including what optional data can be omitted. Concrete hostile-input controls follow in Security/AI Specification BEFORE IMPLEMENTATION; no technical mitigation is silently adopted. Queue B.

## DR-016 Final ownership and integration architecture

Purpose: allocate approved domain responsibilities and interaction semantics, including frontend constraints beyond React. Decision type: architecture.

Primary OPEN IDs: OPEN-052, OPEN-053, OPEN-055.

Dependencies: DR-003 for trust/access expectations; DR-004 for concepts; DR-006 for completion guarantees; DR-008 and DR-009 for lifecycle outcomes; DR-013 for AI responsibilities; DR-015 for data constraints; DR-017 for operating/quality requirements.

Affected requirements/business rules: ARCH-001 through ARCH-010, SEC-008, BR-037, BR-038, BR-039, BR-040.

Blocked future artifacts: Context Map; SAD; API Contracts; Security; AI Specification; Test Strategy.

Urgency: BEFORE SAD. Domain semantics and non-functional constraints must precede final service/data owners and sync/async choices. This cluster does not select boundaries now. Queue C.

## DR-017 Quality and operating constraints

Purpose: establish the operating and UX quality envelope before architecture, then defer detailed topology until that design exists. Decision type: product NFRs followed by architecture/operations.

Primary OPEN IDs: OPEN-056, OPEN-061.

Dependencies: DR-002 for intended milestone/use context; DR-012 for AI acceptance expectations.

Affected requirements/business rules: NFR-001, NFR-002, NFR-003, ARCH-010, UX-001. No new numerical target or recovery policy is inferred.

Blocked future artifacts: SAD; Security; Test Strategy; Roadmap; PRD quality acceptance for the selected slice.

Urgency: BEFORE SAD. Human quality/operating constraints must inform the design. Before PRD approval, record which quality dimensions need acceptance criteria; exact latency targets and deployment topology are not forced NOW. If a quality target changes first-milestone feasibility, reopen the affected DR-002 choice explicitly rather than silently modifying scope. Queue C.

## DR-018 Operational evidence communications and reports

Purpose: define the information users/admins can submit, receive and inspect for operational verification, including receipt evidence, notifications, revenue reports and audit. Decision type: product/operations, then storage/delivery detail.

Primary OPEN IDs: OPEN-022, OPEN-028, OPEN-030, OPEN-031.

Dependencies: DR-002 for scope; DR-008 and DR-009 for lifecycle/reportable outcomes; DR-015 for evidence access/retention constraints.

Affected requirements/business rules: FR-025, FR-032, FR-033, FR-035, FR-036, SEC-010, NFR-002, BR-025, BR-039, BR-040.

Blocked future artifacts: PRD; API Contracts; Security; Test Strategy.

Urgency: BEFORE PRD. Agree operational actions, visible evidence and report meaning; detailed file limits, mail provider, delivery/log retention and storage can follow in Security/contracts. Potential wardrobe images remain conditional on DR-010. Queue B.

## Dependency interpretation and meeting order

Only the Dependencies fields define prerequisite edges. Later compatibility checks and design feedback are not hidden reverse edges. A prerequisite means the named product/constraint outcome is available; it does not require every implementation subdetail of the upstream OPEN records to be closed.

Recommended first meeting order: DR-001 -> DR-002 -> DR-010 -> DR-012. No chosen option is prefilled. Compatible option choices across DR-002/DR-010/DR-012 must be recorded together; an inconsistent choice needs explicit reconsideration.

A dependency-respecting later review order is DR-003, DR-004, DR-011, DR-014, DR-015, DR-018, DR-013, DR-017, DR-016. Independent clusters may be reviewed in parallel. This is a proposed decision-review order, not a project implementation roadmap.

Use [decision packages](02_DECISION_PACKAGES.md), [blocker queues](03_BLOCKER_REVIEW.md), [decision log template](04_DECISION_LOG_TEMPLATE.md), [proposed governance](05_BASELINE_GOVERNANCE.md) and the [authoritative Decision Baseline V1](../decisions/00_DECISION_BASELINE_V1.md). The approved decision effects and partial OPEN statuses are reconciled in discovery file 06; all unrelated OPEN records remain TBD/DEFERRED.
