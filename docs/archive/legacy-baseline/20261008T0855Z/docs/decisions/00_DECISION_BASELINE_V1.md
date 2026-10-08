# StyleMind Human Decision Baseline V1

Human Decision Baseline: COMPLETE

Status: APPROVED

Decision Authority: Phan Đoàn Thế Quân — Solo Developer & Product Owner

Recorded: 2026-09-21

## Approved decision records

The human-approved decisions are persisted under unique repository identifiers:

| Source decision review | Authoritative DEC ID | Title | Resolved OPEN IDs |
| --- | --- | --- | --- |
| DR-001 | DEC-026 | Business Ownership and Success Baseline | OPEN-001 |
| DR-002 | DEC-027 | Target Segment and First Milestone Boundary | OPEN-002, OPEN-003 |
| DR-010 | DEC-028 | Smart Wardrobe Capability Boundary | OPEN-032, OPEN-033, OPEN-034 |
| DR-012 | DEC-029 | Outfit Promises and AI Acceptance Approach | OPEN-041, OPEN-047, OPEN-048, OPEN-049 |
| DR-005 | DEC-030 | Atomic Guest-to-User Cart Merge | OPEN-010; OPEN-009 partial |
| DR-006 | DEC-031 | Atomic Product-Level Checkout | OPEN-011, OPEN-014 partial |
| DR-007 | DEC-032 | Minimal V1 Money Policy | OPEN-012 |
| DR-008 | DEC-033 | Strict Authoritative Payment Confirmation | OPEN-017; OPEN-015, OPEN-018, OPEN-019 partial; OPEN-016 deferred |
| DR-009 | DEC-034 | Conservative V1 Cancellation, Return and Refund Policy | OPEN-026; OPEN-021, OPEN-023, OPEN-024, OPEN-025, OPEN-027 partial |
| Human architecture approval | DEC-035 | V1 Architecture Infrastructure and Hybrid Integration Baseline | Architecture component roles approved; exact contexts, contracts and operating details remain deferred |
| Fast-track implementation approval | DEC-036 | Human Approval of Domain Model and SAD; Implementation Authorization | Domain Model and SAD approved; reversible technical delivery choices authorized |

See [DEC-026](DEC-026.md) through [DEC-036](DEC-036.md) for the complete records.

## Baseline authority

These records are the active human-approved baseline for the assertions they explicitly decide. The existing Phase 0 Decision Register entries DEC-001 through DEC-025 remain historical confirmed decisions with their original meanings. The new records were allocated sequentially after the existing highest ID, DEC-025. This is an identifier collision resolution, not semantic supersession; no historical record was modified, deleted or overwritten.

The baseline does not authorize an agent to answer unrelated OPEN questions, promote recommendations, create final service boundaries or infer technical mechanisms beyond the explicit architecture responsibilities in DEC-035.

## Approved V1 architecture overlay

DEC-035 is a human-approved architecture baseline. PostgreSQL is the authoritative transactional system of record for core business data; persistence boundaries are owned by the relevant service and cross-service database mutation is prohibited. Redis is non-authoritative ephemeral/distributed support. Kafka is the asynchronous integration/event backbone in a hybrid REST/Kafka model, with a preferred transactional outbox for database-plus-event reliability. Qdrant is a vector retrieval/index layer and Neo4j is a fashion relationship/knowledge-graph layer; neither is authoritative Catalog storage. AI recommendations must resolve deterministically against authoritative Catalog data. Docker Compose is the local-development baseline for the approved infrastructure components and StyleMind services as they are implemented; Kubernetes and production deployment are not introduced at this stage.

This overlay approves responsibilities, not final bounded contexts, service names, database schemas, API/event contracts, security controls, provider choices or operating targets. Those remain explicit downstream gates.

## Approved commerce policy overlay

The approved V1 commerce behavior is now governed by DEC-030 through DEC-034: atomic cart merge, atomic product-level checkout, VND-only backend-authoritative money, strict payment confirmation/expiry handling, and conservative cancellation/return/refund rules. Residual technical or mixed-cluster details explicitly listed as TBD in those records remain deferred and do not change the approved customer-visible outcomes.

## V1 product boundary

V1 is a women-first StyleMind product for female customers with diverse fashion styles. It includes:

- Commerce Core: Product/Variant Catalog, Cart, Checkout, Payment and Order.
- Smart Wardrobe: manual CRUD, personal clothing image upload, basic metadata, visual/category browsing and AI context integration.
- AI Stylist: outfit recommendations using user context, Wardrobe and real Catalog products; clear `YOU ALREADY OWN` versus `RECOMMENDED TO BUY` distinction; 100% product grounding for purchasable recommendations; hard-constraint compliance.

The full commerce lifecycle remains applicable, including existing confirmed account, address, catalog, payment, order, post-purchase, administration, notification and audit requirements. The baseline does not remove those requirements.

## V1 acceptance baseline

- Commerce correctness is evaluated through Product → Variant → Cart → Checkout → Payment → Order.
- Every AI-presented purchasable product must resolve to a real StyleMind Catalog Product/Variant.
- Owned Wardrobe items and Shop items must be represented correctly.
- Applicable hard constraints must be obeyed; hard constraints override aesthetic preference.
- Size/fit guidance is advisory, without exact-fit guarantee.
- Color coordination is general fashion coordination, not professional Personal Color Analysis.
- Manual user-provided general weather/context may be used; real-time weather, automatic multi-day travel planning and packing optimization are not V1 requirements.
- Acceptance uses scenario-based Pass/Fail tests. Subjective quality percentages and arbitrary AI-accuracy thresholds remain TBD.

## Approved commerce acceptance overlay

- Cart merge is complete-or-fail; no partial merge is supported in V1, and failed merge leaves the Guest Cart available for correction/retry.
- Checkout is atomic at the product level; invalid or changed items do not silently disappear, and changed totals require reconfirmation.
- Money uses whole VND; shipping is 0 VND, application-level tax/VAT lines and promotions are out of scope for V1, and backend amounts are authoritative.
- SePay/VietQR payment requires authoritative exact-match confirmation; a payment attempt expires after 15 minutes, and late/abnormal money never revives a terminal Order.
- Customer cancellation is whole-order and state-limited; returns are available after COMPLETED for seven calendar days with admin review/QC; exchange/replacement is out of scope; refunds use the approved paid-amount and verified-destination rules.

## Explicit V1 exclusions

V1 does not include sophisticated computer vision, complex background extraction, garment segmentation, selfie wardrobe extraction, automatic clothing recognition from body photos, wear-history/laundry analytics, cost-per-wear analytics, exact-fit guarantee, deep Personal Color Analysis, mandatory real-time weather integration, automatic multi-day travel planning or automatic packing optimization.

## Deferred decision state

The following nine Phase 0.5 clusters remain DEFERRED and are not promoted into approved requirements by this baseline:

DR-003, DR-004, DR-011, DR-013, DR-014, DR-015, DR-016, DR-017 and DR-018.

DR-005 through DR-009 have approved product policies in DEC-030 through DEC-034. Their residual OPEN records retain only the explicitly deferred subdetails listed in those records, including guest-session mechanics, stock ownership/reservation, failure compensation, webhook protocol, exact actor/event matrices, COD operational details, cancellation/return concurrency, detailed QC/restock, shipment/carrier behavior and payout access controls.

The nine deferred clusters retain their existing TBD/deferred details, including identity lifecycle, catalog semantics, style/fit semantics, AI retrieval/synchronization, travel/visual detail, sensitive-data policy, final ownership, integration architecture and operating constraints.

## Traceability and governance

The approved records resolve only the OPEN IDs explicitly listed in the table above. Technical subdetails explicitly described as deferred remain TBD. Existing requirement, business-rule, fact, domain, risk, gap, DR and historical DEC identifiers remain stable. The BRD, PRD, logical Domain Model and SAD are human-approved. DEC-036 authorizes implementation under the approved baseline and permits reversible technical choices, but does not silently change business behavior, security guarantees, AI promises, service ownership or major architecture direction.
