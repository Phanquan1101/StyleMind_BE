# StyleMind Traceability Seed

This initial traceability maps business objective -> functional capability -> business rule -> candidate domain. The links are DERIVED analytical organization from the cited requirements, not new scope or final architecture. Requirements and rules retain the classifications of files 02 and 03. A TBD rule means no sufficient invariant exists in the source; the mapping does not invent one. Every FR identifier from file 02 appears below.

No implementation mapping is made. All Service, API and Test columns remain TBD. S01's existing service names are evidence in file 04, not assignments made by this matrix.

| Business objective | Functional requirements and related AI/data constraints | Business rules | Candidate domains | Service | API | Test |
| --- | --- | --- | --- | --- | --- | --- |
| BUS-003 | FR-001, FR-002, FR-003; SEC-001 through SEC-005 | BR-001 through BR-004 | DOM-001 | TBD | TBD | TBD |
| BUS-003 | FR-004, FR-005, FR-006; DATA-002 | BR-005; BR-006 is TBD; BR-014 | DOM-001, DOM-002, DOM-009 | TBD | TBD | TBD |
| BUS-001, BUS-003 | FR-007, FR-008, FR-009, FR-010; DATA-003, DATA-007 | BR-007 is TBD | DOM-003, DOM-004, DOM-005, DOM-006 | TBD | TBD | TBD |
| BUS-001, BUS-003 | FR-011; DATA-001 | BR-008 | DOM-004, DOM-006, DOM-007, DOM-008, DOM-009 | TBD | TBD | TBD |
| BUS-003 | FR-012, FR-013, FR-014 | BR-008, BR-009, BR-010 | DOM-008, DOM-004, DOM-006, DOM-007 | TBD | TBD | TBD |
| BUS-003 | FR-015 | BR-011, BR-039 | DOM-008, DOM-007 | TBD | TBD | TBD |
| BUS-003 | FR-016; DATA-002; NFR-001 | BR-008, BR-012 through BR-015, BR-039 | DOM-009, DOM-002, DOM-008, DOM-004, DOM-006, DOM-010 | TBD | TBD | TBD |
| BUS-003 | FR-017; INT-001 | BR-016, BR-017, BR-040 | DOM-009, DOM-010, DOM-008, DOM-014 | TBD | TBD | TBD |
| BUS-003 | FR-018, FR-019; INT-002; SEC-009 | BR-018 through BR-020, BR-022, BR-039 | DOM-010, DOM-009, DOM-013 | TBD | TBD | TBD |
| BUS-003, BUS-004 | FR-020, FR-021 | BR-021, BR-022; expiry duration TBD | DOM-009, DOM-010, DOM-016 | TBD | TBD | TBD |
| BUS-003 | FR-022, FR-025; SEC-007, SEC-010 | BR-014, BR-025 | DOM-009, DOM-002 | TBD | TBD | TBD |
| BUS-003 | FR-023, FR-024 | BR-021 through BR-024; request lifecycle TBD | DOM-011, DOM-009, DOM-010, DOM-013 | TBD | TBD | TBD |
| BUS-003 | FR-026, FR-027, FR-028 | BR-026 through BR-028; QC/state details TBD | DOM-012, DOM-009, DOM-007, DOM-013 | TBD | TBD | TBD |
| BUS-003 | FR-029 | BR-029; eligible return states TBD | DOM-012 | TBD | TBD | TBD |
| BUS-003, BUS-004 | FR-030, FR-031 | BR-030, BR-031; eligibility/formula/state details TBD | DOM-013, DOM-010, DOM-012, DOM-009 | TBD | TBD | TBD |
| BUS-003, BUS-004 | FR-032, FR-033; NFR-002; INT-001 | BR-039, BR-040 | DOM-014, DOM-001, DOM-009, DOM-010 | TBD | TBD | TBD |
| BUS-003, BUS-004 | FR-034, FR-035; SEC-006 | Specific admin guard/report formula TBD | DOM-015, DOM-001, DOM-009, DOM-010 | TBD | TBD | TBD |
| BUS-004 | FR-036 | Audit detail/failure policy TBD; BR-021 gives the order-transition validation context | DOM-016 and participating domains | TBD | TBD | TBD |
| BUS-001, BUS-002 | FR-037; AI-003, AI-009 through AI-013, AI-018; DATA-003 through DATA-006 | BR-032, BR-033; owned-item rules TBD | DOM-019, DOM-017, DOM-002, DOM-004, DOM-005 | TBD | TBD | TBD |
| BUS-001, BUS-002 | FR-038; AI-004, AI-012, AI-017 | BR-032, BR-034; travel/reuse rules TBD | DOM-020, DOM-017, DOM-019 | TBD | TBD | TBD |
| BUS-001, BUS-002 | FR-039 is TBD | TBD; no wardrobe capture/history/favorite invariant supplied | DOM-019 | TBD | TBD | TBD |
| BUS-003 | FR-040 is TBD | BR-010, BR-012 cover stock checks only; stock mutation/restock rules TBD | DOM-007, DOM-009, DOM-012 | TBD | TBD | TBD |
| BUS-001, BUS-002 | AI-001, AI-014; DATA-008 | BR-041; deletion/memory rules TBD | DOM-017, DOM-002 | TBD | TBD | TBD |
| BUS-001, BUS-002 | AI-002 through AI-006, AI-009 through AI-013, AI-017 | BR-032 through BR-034, BR-042 | DOM-017, DOM-018, DOM-004, DOM-005, DOM-006, DOM-019 | TBD | TBD | TBD |
| BUS-001, BUS-004 | AI-007, AI-008; INT-003 | BR-035, BR-036; sync freshness/retry rules TBD | DOM-018, DOM-017, DOM-004 | TBD | TBD | TBD |
| BUS-001, BUS-002 | UX-001, UX-002, UX-003 | Visualization-specific business rules TBD; conceptual constraints DEC-013 through DEC-015 | DOM-017, DOM-019, DOM-020; visualization owner TBD | TBD | TBD | TBD |

## Cross-cutting traceability and gaps

| Constraint group | Linked objectives/domains | Remaining traceability gap |
| --- | --- | --- |
| ARCH-001 through ARCH-010; BR-037, BR-038 | All objectives and candidate domains | Final context/service/data owner mappings TBD; OPEN-052 through OPEN-056. |
| SEC-006 through SEC-012 | BUS-003, BUS-004; customer/admin and AI data surfaces | Complete permission, data lifecycle and AI threat behavior TBD; OPEN-051, OPEN-058 through OPEN-060. |
| NFR-001 through NFR-003 | BUS-003, BUS-004; transactional and operating behavior | Measurable targets, retry guarantees and recovery acceptance TBD. |
| AI-015, AI-016, AI-017 | BUS-001, BUS-002; DOM-017 through DOM-020 | Provider/runtime constraints, evaluation and constraint precedence TBD; OPEN-047 through OPEN-049. |
| INT-004, INT-005, INT-006 | BUS-002, BUS-003; DOM-002, DOM-012, DOM-013, DOM-020 | Address provider TBD; weather/carrier/payout integrations remain unapproved or unresolved. |

This is a seed, not a final requirements-to-test matrix. BR identifiers are not copied into new requirement IDs, and TBD entries are not mapped to fictitious APIs/tests. Future traceability must retain original IDs and add approved decisions rather than renumbering the baseline.

## Human Decision Baseline V1 overlay

This overlay adds the approved decision layer without converting candidate domains into services or filling the Service/API/Test columns.

| Decision | Resolved OPEN IDs | Approved traceability effect | Remaining TBD/deferred detail |
| --- | --- | --- | --- |
| DEC-026 from DR-001 | OPEN-001 | Establishes accountable approval and the commerce/stylist acceptance baseline for BUS-001 through BUS-004. | Business outcome KPIs and detailed evaluation evidence. |
| DEC-027 from DR-002 | OPEN-002, OPEN-003 | Establishes female/women-first diverse-style target and V1 capability boundary: Commerce Core, Smart Wardrobe and AI Stylist. | Detailed PRD acceptance, later milestone placement and all unrelated open policies. |
| DEC-028 from DR-010 | OPEN-032, OPEN-033, OPEN-034 | Makes V1 Wardrobe CRUD, personal clothing image upload, basic metadata, visual/category browsing and owned-vs-Shop provenance traceable to FR-037/FR-039, DATA-006 and AI-018. | Final data model/owner, image policy, optional operations and architecture. |
| DEC-029 from DR-012 | OPEN-041, OPEN-047, OPEN-048, OPEN-049 | Establishes V1 AI promises, real Catalog grounding, hard-constraint precedence and scenario-based Pass/Fail acceptance. | Provider/runtime, subjective thresholds, detailed outfit/budget semantics and technical pipeline. |
| DEC-030 from DR-005 | OPEN-010; OPEN-009 partial | Establishes atomic Guest-to-User Cart merge, complete failure on conflict, price-change notice, retry and clear/retire only after success. | Guest-session lifecycle/security, quantity bounds and add-versus-set/update semantics. |
| DEC-031 from DR-006 | OPEN-011, OPEN-014 partial | Establishes atomic product-level checkout, mandatory revalidation, no silent partial removal, price reconfirmation and recoverable Order-creation failure. | Stock owner/reservation/mutation, transaction boundaries, compensation and detailed recovery. |
| DEC-032 from DR-007 | OPEN-012 | Establishes VND-only whole-đồng money, backend-authoritative amounts, zero V1 shipping fee, no separate application tax/VAT line, no promotions and paid-eligible-item refund basis. | Unrelated reporting formulas and detailed AI budget interpretation. |
| DEC-033 from DR-008 | OPEN-017; OPEN-015, OPEN-018, OPEN-019 partial; OPEN-016 deferred | Establishes strict authoritative exact-match payment confirmation, 15-minute payment-attempt expiry, abnormal/late exception handling, terminal-order protection and COD pending/collection meaning. | Webhook protocol, detailed COD operations, complete actor/event matrix and separate Order-timeout coordination. |
| DEC-034 from DR-009 | OPEN-026; OPEN-021, OPEN-023, OPEN-024, OPEN-025, OPEN-027 partial | Establishes cancellation states/scope/reason/review, seven-day Completed-only returns, item/partial/full return, no exchange, admin review/QC and paid-amount/manual refund behavior. | Request/concurrency details, QC/restock checklist, shipment/carrier behavior and payout access/change controls. |

## Human-approved architecture overlay

DEC-035 adds an approved infrastructure and integration-responsibility layer. It does not convert candidate domains into final services and does not fill the Service, API or Test columns.

| Decision | Approved traceability effect | Remaining TBD/deferred detail |
| --- | --- | --- |
| DEC-035 | PostgreSQL is the authoritative transactional system of record; Redis is non-authoritative transient support; Kafka is the asynchronous integration/event backbone in a hybrid REST/Kafka model; Qdrant and Neo4j are retrieval/knowledge layers; AI purchasable recommendations require deterministic Catalog grounding; Docker Compose is the local-development baseline. | Final contexts and owners, exact database allocation, REST/event contracts, outbox details, security, operating targets, retrieval synchronization/freshness and implementation evidence. |
| DEC-036 | Domain Model and SAD are human-approved; implementation may proceed with reversible technical choices and traceability preserved. | Detailed contracts, security, AI, tests, service implementation and production operations remain implementation artifacts rather than new business decisions. |

All other traceability gaps remain TBD. The logical Domain Model is recorded separately in [docs/domain/01_DOMAIN_MODEL.md](../domain/01_DOMAIN_MODEL.md); it does not fill Service/API/Test columns or resolve final ownership. The authoritative consolidated baseline is [Decision Baseline V1](../decisions/00_DECISION_BASELINE_V1.md), and the full architecture record is [DEC-035](../decisions/DEC-035.md).
