# StyleMind Confirmed Decision Register

Only explicit confirmed project decisions and user confirmations are recorded here. All rows are CONFIRMED. The register is not a human approval of this new discovery package. Described original service assignments, AI tools, optional behavior and analytical recommendations are recorded elsewhere without being promoted into new final decisions.

| ID | Confirmed decision | Source | Consequence and linked requirements |
| --- | --- | --- | --- |
| DEC-001 | The project is named StyleMind. | S03 section 1 | Preserve the product name; FCT-001. |
| DEC-002 | Position the product as an AI Personal Stylist and Smart Wardrobe commerce platform. | S03 section 1 | BUS-001; retain the relationship between styling and commerce. |
| DEC-003 | Focus primarily on women's fashion, without permanently excluding other groups. | S03 section 2 | BUS-002; target segment details remain TBD. |
| DEC-004 | Retain the existing e-commerce lifecycle. | S03 section 1 | BUS-003; authentication through post-purchase/admin remains in scope. |
| DEC-005 | Include styling with both already owned clothes and purchasable catalog products. | S03 section 3 | FR-037; precise Smart Wardrobe features remain TBD. |
| DEC-006 | Support product brand information and consider brand preference where appropriate. | S03 section 4 | DATA-003, AI-010; detailed rules remain TBD. |
| DEC-007 | Treat size and fit as important personalization dimensions. | S03 section 5 | AI-011, DATA-004; algorithm/field selection not chosen. |
| DEC-008 | Body measurements are optional personalization; bust/waist/hip are not mandatory registration fields. S05 also expressly treats height as optional. | S03 section 5; S05 sections 2, 7C | BR-033; no compulsory measurement onboarding. |
| DEC-009 | Include color coordination while leaving its ownership unresolved. | S03 section 6 | AI-012; OPEN-046. |
| DEC-010 | Include travel-aware and trip-duration-aware outfit planning in the product direction. | S02 Product Focus; S03 section 7; S05 section 2 | FR-038; context details remain TBD. |
| DEC-011 | Weather integration and packing optimization require separate approval before being treated as requirements. | S03 section 7 | INT-005; OPEN-045. |
| DEC-012 | Provide strong visual outfit presentation. | S03 section 8 | UX-001; interaction/acceptance remains TBD. |
| DEC-013 | Keep advanced live 3D in overall scope without automatic first-milestone inclusion. | S03 section 8 | UX-002; no approved release assignment. |
| DEC-014 | Keep 2D composition, 3D avatar visualization and virtual try-on conceptually distinct until formally decided. | S03 section 8 | UX-003; no photorealistic try-on inference. |
| DEC-015 | Instructor direction does not authorize computer vision, photorealistic try-on, garment physics, diffusion try-on, body scanning or clothing segmentation. | S02 Interpretation Constraint | Separate approval/specification is required for those techniques. |
| DEC-016 | Use Java and Spring Boot for the backend. | S03 section 9 | ARCH-001; versions remain TBD. |
| DEC-017 | Use microservices architecture. | S03 section 9 | ARCH-002; exact boundaries are not automatically approved. |
| DEC-018 | Determine service boundaries through domain analysis and data ownership; do not create one service just because a domain exists. | S03 section 9 | ARCH-003; OPEN-052. |
| DEC-019 | Use React for the frontend, leaving architecture and supporting libraries for later decisions. | S03 section 10 | ARCH-004; OPEN-053. |
| DEC-020 | Audit the existing LLM/vector/Qdrant/Neo4j/retrieval/knowledge/hydration ideas without automatically expanding them. | S03 section 11 | AI-002, AI-007, AI-008, INT-003; future effective responsibilities remain TBD. |
| DEC-021 | Ground AI purchasable recommendations in real StyleMind product/catalog data. | S03 section 11 | AI-003; BR-032. |
| DEC-022 | Develop primarily with coding agents, using approved specifications as source of truth and marking missing behavior TBD. | S03 section 12 | ARCH-008; inferred implementation is not authority. |
| DEC-023 | Make scope/architecture/API changes explicit, preserve traceable business rules and distinguish assumptions; surface documentation conflicts. | S03 sections 12-13 | Classification and change control across this package. |
| DEC-024 | Follow discovery, business specification, product specification, domain analysis, architecture, contracts, security/AI specifications, test strategy, agent harness and implementation. | S03 section 13 | File 12 proposes dependent documentation only; no subsequent phase started. |
| DEC-025 | Current commerce scope expressly includes guest/user carts, COD and SePay/VietQR alongside the named business domains. | S05 section 2 | FR-012, FR-013, FR-017, FR-018; no payment provider expansion. |

Unresolved questions and proposed terminology/documentation order are intentionally excluded from this register. They do not become decisions merely by being written in a discovery output.

## Human Decision Baseline V1 records

The following four decisions were approved after Phase 0.5. They use new unique identifiers because DEC-001 through DEC-025 already exist with historical meanings. Existing rows above are preserved exactly; these records are not semantic supersessions.

| ID | Confirmed decision | Source | Consequence and linked requirements |
| --- | --- | --- | --- |
| DEC-026 | Human-approved business ownership and success baseline: Phan Đoàn Thế Quân is the Solo Owner and final product/business and technical/architecture decision maker; commerce correctness, stylist usefulness, 100% purchasable product grounding and hard-constraint compliance are the acceptance baseline; business outcome KPIs remain TBD. | DR-001; OPEN-001 | BUS-001 through BUS-004, FR-036, AI-003, BR-032, ARCH-008. |
| DEC-027 | Human-approved target and V1 boundary: female customers, women-first and diverse styles; V1 is Commerce Core, Smart Wardrobe and AI Stylist using real Catalog data. | DR-002; OPEN-002, OPEN-003 | BUS-001 through BUS-003, FR-009, FR-016 through FR-018, FR-037, FR-038, AI-003, BR-032, BR-033. |
| DEC-028 | Human-approved Smart Wardrobe V1: CRUD, personal clothing image upload, basic metadata, visual/category browsing, AI context integration and clear owned-versus-Shop distinction; advanced vision and wear/analytics capabilities are excluded. | DR-010; OPEN-032 through OPEN-034 | FR-037, FR-039, DATA-006, AI-003, AI-018, BR-032, BR-033. |
| DEC-029 | Human-approved V1 AI promises and acceptance: outfit recommendation, Wardrobe reuse, 100% Catalog grounding, hard-constraint compliance and scenario-based Pass/Fail; fit is advisory, color is general coordination, and real-time weather/travel packing are not required. | DR-012; OPEN-041, OPEN-047 through OPEN-049 | FR-037, AI-003 through AI-005, AI-015 through AI-017, BR-032 through BR-034, BR-042. |

The following five decisions were approved after the four-record baseline. They use new unique identifiers after DEC-029; existing records remain unchanged.

| ID | Confirmed decision | Source | Consequence and linked requirements |
| --- | --- | --- | --- |
| DEC-030 | Human-approved atomic Guest-to-User Cart merge: duplicate quantities combine; any invalid, unavailable or over-stock conflict fails the complete merge; price changes do not invalidate merge but must be shown; Guest Cart remains on failure and is cleared/retired only after success; no partial merge in V1. | DR-005; OPEN-009 partial, OPEN-010 | FR-015; PRD-CART-005; BR-009 through BR-011, BR-039. |
| DEC-031 | Human-approved atomic product-level checkout: backend revalidates Product/Variant, activity, quantity, stock and price; any failure aborts Order creation and partial checkout; changed price requires reconfirmation; failed creation is recoverable; no fixed reservation promise in ordinary checkout. | DR-006; OPEN-011, OPEN-014 partial | FR-016; PRD-CHK-001 through PRD-CHK-006; BR-010, BR-012 through BR-017, BR-039, BR-040. |
| DEC-032 | Human-approved VND-only minimal money policy: whole đồng, backend-authoritative amounts, no V1 shipping fee, no separate application tax/VAT line, no V1 promotions, and refunds based on actually paid eligible item quantities with cumulative cap. | DR-007; OPEN-012 | FR-016, FR-030; PRD-PAY-006, PRD-REF-001; BR-008, BR-014, BR-015, BR-030. |
| DEC-033 | Human-approved strict payment confirmation: only authoritative exact-match confirmation marks SePay/VietQR paid; 15-minute payment-attempt expiry; abnormal and late payments become exceptions and never revive terminal Orders; COD remains unpaid/pending until collection is confirmed. | DR-008; OPEN-015, OPEN-018, OPEN-019 partial, OPEN-016 deferred, OPEN-017 | FR-017 through FR-021; PRD-PAY-001 through PRD-PAY-008; BR-016 through BR-022, BR-039. |
| DEC-034 | Human-approved conservative V1 cancellation/return/refund: whole-order cancellation only in PENDING, PAYMENT_PENDING, PAID or CONFIRMED; returns only after COMPLETED within 7 calendar days, item-level/partial/full allowed, exchange out of scope, admin review and QC required, and refund follows verified-destination/manual processing rules. | DR-009; OPEN-021, OPEN-023, OPEN-024, OPEN-025, OPEN-027 partial, OPEN-026 | FR-023, FR-024, FR-026 through FR-031; PRD-ORD-004, PRD-RET-001 through PRD-RET-003, PRD-REF-001, PRD-REF-002; BR-023 through BR-031. |

The nine new records DEC-026 through DEC-034 were allocated sequentially after the old highest ID DEC-025. Their complete authoritative records are in `docs/decisions/DEC-026.md` through `DEC-034.md` and the consolidated [Decision Baseline V1](../decisions/00_DECISION_BASELINE_V1.md).

## Human-approved architecture baseline

The following decision was approved after the commerce baseline. It uses the next unique identifier; no historical decision is overwritten or semantically superseded.

| ID | Confirmed decision | Source | Consequence and linked requirements |
| --- | --- | --- | --- |
| DEC-035 | Human-approved V1 architecture baseline: PostgreSQL is authoritative for core transactional data; service-owned persistence boundaries prohibit cross-service database mutation; Redis is ephemeral/non-authoritative; Kafka is the asynchronous integration/event backbone in a hybrid REST/Kafka model; transactional outbox is preferred for reliable publication; Qdrant and Neo4j are retrieval/knowledge layers rather than Catalog authority; AI output requires deterministic Catalog grounding; Docker Compose is the local-development baseline; Kubernetes and production topology remain out of scope at this stage. | Human approval: `ARCHITECTURE CORRECTION — HUMAN APPROVED` | Extends ARCH-001 through ARCH-010, AI-002/003/007/008, INT-003 and the logical Domain Model; exact contexts, data ownership, contracts, security and operating targets remain TBD. |

The complete authoritative record is [DEC-035](../decisions/DEC-035.md). DEC-035 is an architecture responsibility baseline, not a final service map or implementation specification.

## Human-approved implementation authorization

| ID | Confirmed decision | Source | Consequence and linked requirements |
| --- | --- | --- | --- |
| DEC-036 | Human-approved Domain Model and SAD; implementation preparation and implementation authorized. Reversible technical choices may be made without another ordinary decision round, while approved business behavior, scope, money/refund semantics, payment semantics, security guarantees, AI promises and major architecture direction remain protected. | Human approval: `FAST-TRACK HUMAN APPROVAL — APPROVE SAD AND START IMPLEMENTATION` | Authorizes contracts, security/AI/test foundations, local infrastructure, service scaffolds and the Catalog-first then Cart implementation sequence. |

The complete authoritative record is [DEC-036](../decisions/DEC-036.md). It extends DEC-035 and does not supersede earlier records.
