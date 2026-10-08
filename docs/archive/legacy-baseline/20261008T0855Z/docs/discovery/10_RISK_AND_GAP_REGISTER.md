# StyleMind Specification Risks and Gaps

Each gap is a DERIVED analysis of missing detail in the cited sources. Severity is an analyst assessment of its effect on later specifications: CRITICAL blocks a coherent scope, money/state invariant or architectural responsibility; HIGH affects core correctness, access or acceptance; MEDIUM affects bounded behavior or source quality. These are specification risks, not assertions that a deployed system has defects. No severity grants approval for a proposed solution.

| ID | Description and source evidence | Severity | Affected domains | Why it matters | Required next decision |
| --- | --- | --- | --- | --- | --- |
| GAP-001 | Business outcomes, approver and initial milestone are absent despite clear positioning; S03 sections 1-2, 8, 12. | CRITICAL | All | Later authors cannot distinguish a complete release from broad direction. | OPEN-001 through OPEN-003: agree business ownership, success and scope baseline. |
| GAP-002 | OTP controls exist but values, registration fields and reset branch are missing; S01 section 2. | HIGH | Identity | Different agents could implement incompatible account-creation/reset policy. | OPEN-004, OPEN-005: define identity lifecycle and control parameters. |
| GAP-003 | JWT/internal token mechanisms exist without full lifecycle, trust or resource permission matrix; S01 P047-072. | HIGH | Identity/All | RSA/JWT alone does not specify expiry, revocation, service authorization or ownership checks for all surfaces. | OPEN-057 through OPEN-059: define security lifecycle and access matrix. |
| GAP-004 | Address defaults, catalog activation/deletion and Product/Variant/SKU semantics are conditional or incomplete; S01 sections 3-4. | HIGH | Customer/Catalog/Product/Variant | Data and UI rules could diverge across checkout, admin and public views. | OPEN-006 through OPEN-008, OPEN-050: resolve policy and canonical terms. |
| GAP-005 | Stock is exposed/checked but reservation, deduction, release, restock and concurrency ownership are absent; S01 P150, P189, P207, P374. | CRITICAL | Inventory/Order/Return | Availability checks alone do not define what happens when concurrent orders consume the same stock. | OPEN-011: approve stock lifecycle and owner. |
| GAP-006 | Guest identity and merge lack expiry/security, over-stock and partial-failure policy; S01 section 5. | HIGH | Cart/Identity/Inventory | Repeated merge and quantity conflicts have no approved outcome. | OPEN-009, OPEN-010: settle cart/session/merge behavior. |
| GAP-007 | Authoritative totals and valid refund amount lack formulas, currency and rounding; S01 sections 6.3, 12. | CRITICAL | Order/Payment/Refund | Payment matching and refund caps cannot be evaluated consistently. | OPEN-012, OPEN-026: define monetary calculations. |
| GAP-008 | Checkout sequencing and deduplication lack durable boundary, concurrency, key expiry and crash-recovery definitions; S01 sections 6, 16-17. | CRITICAL | Order/Payment/Cart | The one-order-per-successful-request invariant has no complete failure contract. | OPEN-014, OPEN-054: agree ownership, idempotency and compensation semantics. |
| GAP-009 | COD source ends at pending payment/confirmed order; cash collection and failed-delivery handling are unspecified; S01 section 7.1. | HIGH | Payment/Order/Refund | Paid versus owed money and later refund decisions remain ambiguous. | OPEN-015: define COD collection/settlement lifecycle. |
| GAP-010 | SePay mismatch, unmatched/late money and callback retry outcomes are incomplete; S01 section 7.2-7.3. | CRITICAL | Payment/Order/Refund | Preserving terminal order states does not decide disposition of incoming funds or failed callbacks. | OPEN-016 through OPEN-019: define reconciliation and race policy. |
| GAP-011 | The order state graph is explicit, but event/actor guards and payment/return side effects are missing; S01 sections 8-12. | CRITICAL | Order/Payment/Cancellation/Return | A valid edge alone is insufficient to authorize or sequence a business operation. | OPEN-018, OPEN-021, OPEN-024: define trigger and lifecycle mappings. |
| GAP-012 | Return windows, request/QC states, cumulative amounts, payout and refund settlement are absent or conditional; S01 sections 11-12. | CRITICAL | Return/Refund/Inventory/Payment | Partial returns, exchange scope and repeated refund/restock effects cannot be evaluated. | OPEN-023 through OPEN-027: approve post-purchase policy and state ownership. |
| GAP-013 | Receipt/product image limits and handling are incomplete, and wardrobe image functionality is unconfirmed; S01 P138, P337-343; S03 section 3. | HIGH | Product/Order/Wardrobe | Agents could apply an invented common upload policy or assume wardrobe image processing. | OPEN-022, OPEN-034: define each approved image surface. |
| GAP-014 | Notification delivery/retry/ownership and audit/reporting definitions are incomplete; S01 sections 13-14, 18. | HIGH | Notification/Audit/Administration | Best-effort behavior is known, but observability, revenue figures and retry outcomes need explicit rules. | OPEN-028, OPEN-030, OPEN-031: agree event, reporting and audit policies. |
| GAP-015 | Wardrobe capability, provenance, metadata and owner are unspecified; S03 section 3. | CRITICAL | Wardrobe/AI/Product | The product's expanded identity lacks a defined owned-item workflow and data relationship. | OPEN-032 through OPEN-034, OPEN-052: approve Wardrobe scope/ownership. |
| GAP-016 | Style, brand and optional fit profile lack vocabulary, units, validation, chart coverage and algorithm; S03 sections 4-5. | HIGH | Customer/Brand/Product/AI | The system cannot claim consistent personalized size/fit behavior from the current evidence. | OPEN-035 through OPEN-038: resolve personalization semantics. |
| GAP-017 | Chat history, deletion and optional memory lack a consistent data lifecycle; S01 P443-449. | HIGH | AI/Customer | Preserved consultation history and delete capability need defined retention and user-visible outcomes. | OPEN-039, OPEN-051: define conversation and memory policy. |
| GAP-018 | Source AI pipeline/tool responsibilities remain under audit; S01 section 15; S03 section 11. | CRITICAL | AI/Fashion Knowledge/Product | Adopting names as final architecture could entrench unreviewed responsibilities. | OPEN-040, OPEN-047: approve retained pipeline responsibilities and runtime constraints. |
| GAP-019 | Budget interpretation, current product checks, sync freshness and infeasible-outfit behavior are missing; S01 P463-473. | CRITICAL | AI/Product/Inventory/Wardrobe | Grounding and budget invariants can be stated but not fully judged at response time. | OPEN-041 through OPEN-043, OPEN-049: define constraint priority and freshness behavior. |
| GAP-020 | Approval-gated knowledge ingestion lacks source metadata, review rubric, version/deletion behavior and hostile-input handling; S01 P474-487. | HIGH | Fashion Knowledge/AI/Admin | Approval is explicit, but provenance, trust and index repair are not sufficiently specified. | OPEN-044, OPEN-060: define knowledge governance and threat behavior. |
| GAP-021 | AI evaluation datasets, rubrics and thresholds are absent; S01 section 15; S03 section 11. | CRITICAL | AI/All styling domains | Human review has no approved way to accept grounding, fit/color, explanation and recommendation quality. | OPEN-048: approve evaluation acceptance definition. |
| GAP-022 | Travel context, color ownership and advanced visualization meaning/milestone are unresolved; S03 sections 6-8. | HIGH | Travel/AI/Product/UX | Broad goals could become invented weather/packing algorithms or photorealistic try-on commitments. | OPEN-003, OPEN-045, OPEN-046, OPEN-062: specify intended behavior and ownership. |
| GAP-023 | Final boundaries, storage/deployment, integration semantics and non-functional targets remain open; S03 sections 9-10; S01 section 16. | CRITICAL | Architecture/All | A final SAD/service catalog would rest on unapproved responsibility and operating assumptions. | OPEN-052 through OPEN-056, OPEN-061: establish architecture inputs. |
| GAP-024 | Sensitive personalization/payout/image/history data lacks an approved access, consent and retention policy; S03 sections 3, 5; S01 P390, P448. | HIGH | Customer/Wardrobe/AI/Refund/Audit | Data collection and reuse cannot be specified consistently across experiences. | OPEN-051, OPEN-058, OPEN-060: define data-use and access constraints. |
| GAP-025 | S03 section 13 has damaged arrow characters; no approval metadata or source-version policy is supplied for future revisions. | MEDIUM | Documentation governance | Stage names remain readable, but future changes need traceable versions and human approval records. | OPEN-001 and ARCH-008: identify approval/version responsibility; preserve current source hashes. |

## Architecture readiness audit

The following are confirmed starting constraints: Java/Spring Boot, React, microservices, no direct cross-service database access or foreign keys, JWT/RSA verification, internal token protection, source-described internal API calls, selected best-effort behavior and duplicate prevention. They narrow the design space but do not supply a final architecture.

The SAD is blocked on final bounded contexts/data owners (OPEN-052), inventory ownership (OPEN-011), Wardrobe/style data ownership (OPEN-032 through OPEN-038), order/payment/return state and transaction consistency (OPEN-014 through OPEN-026, OPEN-054), product-to-AI synchronization (OPEN-040 through OPEN-044), interaction semantics (OPEN-055), service trust/token lifecycle (OPEN-057 through OPEN-059) and operating targets (OPEN-056). Each is recorded rather than resolved here.

## Seven pass completion record

| Analysis pass | Evidence of completion in this package |
| --- | --- |
| 1 Evidence extraction | File 01 source register/facts; file 02 requirements; file 03 explicit invariants/state graph |
| 2 Domain analysis | File 04 candidate domains and source-owner versus final-owner distinction |
| 3 Consistency audit | File 07 source comparison; this gap register and file 06 missing lifecycles/ownership/error behavior |
| 4 Scope analysis | File 05 separate core/initial, advanced, overall, proposed, out-of-scope and unresolved sections |
| 5 AI analysis | File 02 AI/data/security entries; file 06 OPEN-032 through OPEN-049 and OPEN-060; files 04, 08 |
| 6 Architecture readiness | This section and file 04 ownership dependencies, file 06 architecture questions |
| 7 Self audit | File 12 validation checklist and readback record; file 11 traceability without implementation mapping |

Readiness ratings in file 12 are analyst assessments, not confirmed project decisions.

## Human Decision Baseline V1 overlay

The gap register above is preserved as the Phase 0 analytical baseline. DEC-026 through DEC-029 later bound or resolve parts of GAP-001, GAP-015, GAP-018, GAP-019 and GAP-021 at the business/product level:

- GAP-001: ownership, V1 target and V1 boundary are approved; business outcome KPIs remain TBD.
- GAP-015: V1 Wardrobe capability and owned-versus-Shop product distinction are approved; final data ownership and schema remain TBD.
- GAP-018: V1 AI promises and acceptance boundaries are approved; effective pipeline/runtime responsibilities remain TBD.
- GAP-019: hard budget/constraint and product-grounding acceptance are approved; freshness, synchronization and infeasible-outfit behavior remain TBD.
- GAP-021: scenario-based Pass/Fail properties are approved; subjective datasets, rubrics and thresholds remain TBD.

The remaining risks and the nine still-deferred decision clusters remain active for domain, SAD, security and AI specification work. Product-policy portions of DR-005 through DR-009 are now approved by DEC-030 through DEC-034; their residual technical or mixed-cluster risks remain active where explicitly listed. The authoritative baseline is `docs/decisions/00_DECISION_BASELINE_V1.md`.
