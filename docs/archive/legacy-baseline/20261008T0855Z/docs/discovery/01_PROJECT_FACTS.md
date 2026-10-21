# StyleMind Phase 0 Project Facts

Discovery baseline: 12 September 2026. This package records evidence for human review; it is not an approved BRD, PRD, SAD, API contract or implementation plan. Source-supported statements retain their original conditions. A confirmed direction is not a complete workflow or a first-release commitment.

## Source register and reading coverage

The project inspected was C:/Users/FPTSHOP/Desktop/StyleMind. Its complete available source set consists of the four files below in docs/input. No other project documents, implementation files or Git metadata were found in that directory tree. No external business or vendor documentation was used to fill requirements.

| Source | File and locator convention | Evidence role |
| --- | --- | --- |
| S01 | docs/input/01-current-business-description.docx | Original Vietnamese business description, sections 1 through 20. Read all 565 body paragraphs, including the final empty paragraph. P001 through P565 are document-order paragraph locators, counting every w:p descendant of w:body in word/document.xml. These are paragraph locators, not page numbers. |
| S02 | docs/input/02-instructor-feedback.md | Product Focus and Interpretation Constraint. Read completely. |
| S03 | docs/input/03-confirmed-project-decisions.md | Explicit confirmed decisions, sections 1 through 13. Read completely. |
| S04 | docs/input/README.md | Input provenance and preservation instructions. Read completely. |
| S05 | User Phase 0 request, attachment 8f9212de-dc21-483f-80d9-2f5dbc5a3639/pasted-text.txt | Explicit confirmations in section 2 and section 7C; discovery rules elsewhere. Examples and lists of topics to audit are not adopted product requirements. Read completely. |
| S06 | User instruction to resume Phase 0 in this conversation | Complete all 12 files, record questions without interrupting discovery, read back every output, and audit consistency. |

S01 was extracted structurally from the complete Word package. It contains no tables, drawings, tracked insertions/deletions, comments, headers, footers or footnotes. No text-bearing supplementary parts were omitted. No page-layout conclusions are made. The bundled environment did not expose LibreOffice; source analysis used the complete text and package structure. S03 section 13 has damaged arrow characters, but the ordered stage names are readable. The input files were not repaired or edited.

| Input | SHA256 at discovery start |
| --- | --- |
| S01 | 092390921EF64340E99653B4679213843C8251666B0EFA62607C8BFF88C034CA |
| S02 | A40FEB19886F933AC9B66F2EAC49EB1368A1FB882E4217E2D7FDC04F7944A54D |
| S03 | 3BC5DCC9874B10DE118193CFC97972C28AF74BCA631FE7DA74401EB18E4F7483 |
| S04 | 3FF3C0E36629F39E28110A4B2AEEA481CF0D0827DB9A7549714B60982CB15E2E |

## Classification and identifier convention

CONFIRMED means the stated assertion is directly supported. DERIVED identifies a logical consequence and names its premises. PROPOSED requires human approval. TBD means the effective behavior, rule or choice is not established. A CONFIRMED row describing an original workflow establishes source evidence, not approval of every named service as a final boundary. Modal wording such as may, should and if is preserved; unsettled policy is recorded as TBD.

BUS, ACT, FR, BR, NFR, SEC, AI, DATA, ARCH, INT, UX, OPEN, CONFLICT and DEC use the prefixes requested by the user. Supplemental FCT, DOM and GAP identifiers index facts, candidate domains and analytical gaps only. They do not add requirements. IDs are stable within this first baseline and must be retained in later revisions. BR identifiers are defined only in file 03; inventory identifiers only in file 02; repeated IDs elsewhere are references, not additional records.

## Confirmed facts

Every row below is CONFIRMED at the exact level expressed.

| ID | Confirmed assertion | Evidence |
| --- | --- | --- |
| FCT-001 | The project name is StyleMind. | S01 P001; S03 section 1 |
| FCT-002 | Current positioning is a women-first AI Personal Stylist and Smart Wardrobe commerce platform. | S03 sections 1-2; S05 section 2 |
| FCT-003 | Women-first does not permanently exclude other groups. Age range and the detailed target segment are not specified. | S03 section 2; S05 section 7A |
| FCT-004 | The existing e-commerce lifecycle remains in scope. | S03 section 1; S01 P564 |
| FCT-005 | CUSTOMER and ADMIN are the two main application roles; guests can use a cart. | S01 P003-005, P066-068, P159-167 |
| FCT-006 | Registration uses email and OTP before creating a real account. | S01 P019-034 |
| FCT-007 | Login checks account status and BCrypt password verification; JWTs are RSA-signed and verified by gateway and downstream services. | S01 P038-049 |
| FCT-008 | Password reset has OTP expiry, attempt limits, resend cooldown and a non-reusable reset token if that path is used. | S01 P050-064 |
| FCT-009 | Profile is linked to userId; S01 assigns identity to auth-service and profile to user-service, with no credentials in user-service. | S01 P075-082 |
| FCT-010 | Addresses support CRUD and default selection, Vietnam administrative data and Vietnam phone normalization. This does not establish an exclusive sales territory. | S01 P085-097 |
| FCT-011 | Orders retain the address and recipient snapshot used at purchase. | S01 P098-102, P225 |
| FCT-012 | Public catalog supports keyword, category, price and demographic filters and variant details. | S01 P118-128 |
| FCT-013 | Admin manages categories, products, variants and product images. Several activation/deletion restrictions are expressed conditionally. | S01 P104-143 |
| FCT-014 | Variant/SKU information includes size, color, material, SKU, stock, optional override price and active/inactive status. | S01 P144-156 |
| FCT-015 | Cart and order obtain variant snapshots and do not trust frontend prices. | S01 P154-156, P186-191, P223-236 |
| FCT-016 | Guest carts use a client session identifier; user carts use user identity; merging combines quantities of duplicate variants. | S01 P158-199 |
| FCT-017 | Checkout requires login, a nonempty cart, valid address, active product/variant and stock; prices are refreshed. | S01 P201-208 |
| FCT-018 | Checkout deduplicates by an idempotency key associated with the user; required-header policy and lifetime are not stated. | S01 P209-226, P510-516 |
| FCT-019 | Backend totals are authoritative, and order items store price_at_purchase. | S01 P219, P223-236 |
| FCT-020 | COD creates a pending payment transaction and confirms the order; cart clearing and notification are best-effort. | S01 P238-252 |
| FCT-021 | SePay/VietQR uses a payment transaction, transfer content, QR URL, verified webhook and callback to the order flow. | S01 P253-280 |
| FCT-022 | Online confirmation comes from the webhook, with amount and transfer-content matching and duplicate protection. | S01 P265-280, P511, P515 |
| FCT-023 | S01 explicitly lists ten order states, legal transitions and terminal states. | S01 P281-306 |
| FCT-024 | Customers request cancellation when eligible; admin cancellation requires a reason and the order state machine. Review and refund details are conditional. | S01 P307-329 |
| FCT-025 | Customers can inspect orders and upload receipt images subject to ownership, eligible state, image format and size limits. | S01 P330-343 |
| FCT-026 | Returns include eligibility, item quantities, reasons, evidence, admin review, QC and return-shipment information. | S01 P344-382 |
| FCT-027 | Refund operations include internal requests, lookup by order, admin complete/fail marking and a return-linked payout destination. | S01 P383-395 |
| FCT-028 | Notification supports customer read operations, admin logs/summary/retry, delivery status and fallback logs; notification failure does not roll back order/payment. | S01 P396-420 |
| FCT-029 | Admin dashboard/reporting and operational audit are in scope; exact report metrics are only partly specified. | S01 P421-435, P517-528 |
| FCT-030 | AI chat supports sessions and messages, with persisted conversation history. Preference memory is described as optional. | S01 P436-449 |
| FCT-031 | S01 describes intent extraction, Qdrant concept resolution, Neo4j rules, hybrid product search and real-product hydration. | S01 P450-460 |
| FCT-032 | AI output includes outfit, selection reasons, styling tips, images and prices; the source requires respecting a supplied budget and explainable recommendations. | S01 P452-465 |
| FCT-033 | AI recommendations must connect to real StyleMind catalog data and must not invent purchasable products. | S03 section 11; S01 P462-473 |
| FCT-034 | Product-index sync is described in the original source; mechanism, freshness and acceptance rules are not finalized. | S01 P466-473, P497; S03 section 11 |
| FCT-035 | Knowledge ingestion accepts text/URL, extracts concepts/rules, creates a draft and requires admin approval before production upsert. | S01 P474-487 |
| FCT-036 | Original integrations use internal APIs and internal tokens, prohibit direct access to another service's database, and prohibit cross-service foreign keys. | S01 P488-502 |
| FCT-037 | Retry must avoid repeating successful business effects; auxiliary failures require mandatory versus best-effort classification. | S01 P502-516 |
| FCT-038 | Smart Wardrobe considers both owned clothes and purchasable catalog items; its detailed feature set is unresolved. | S02 Product Focus; S03 section 3 |
| FCT-039 | Product brand information and appropriately applied brand preference are part of the direction. | S02 Product Focus; S03 section 4 |
| FCT-040 | Size/fit, usual sizes, preferred fit and optional body measurements are personalization dimensions; exact field selection and algorithms remain unresolved. | S03 section 5; S05 sections 2, 7C |
| FCT-041 | Height, bust, waist and hip measurements, if collected, are optional personalization; bust/waist/hip are explicitly not mandatory registration fields. | S03 section 5; S05 sections 2, 7C |
| FCT-042 | Color coordination is confirmed, while ownership among metadata, rules, fashion knowledge and AI reasoning is unresolved. | S03 section 6 |
| FCT-043 | Travel and trip-duration-aware planning are intended; destination, activities, occasion and wardrobe reuse are candidate context, with details unresolved. | S02 Product Focus; S03 section 7; S05 section 2 |
| FCT-044 | Weather integration and packing optimization are not automatically approved requirements. | S03 section 7 |
| FCT-045 | Strong outfit visuals and advanced live 3D are in overall scope, without an automatic first-milestone commitment. | S02 Product Focus; S03 section 8; S05 section 2 |
| FCT-046 | 2D composition, 3D avatar visualization and virtual try-on must remain distinct; the specified advanced techniques need separate approval/specification. | S02 Interpretation Constraint; S03 section 8 |
| FCT-047 | Java, Spring Boot, microservices and React are confirmed. Exact versions, supporting libraries and final service boundaries are not. | S03 sections 9-10 |
| FCT-048 | Final service boundaries must follow domain analysis and data ownership, not one service per domain. Existing service names remain source evidence. | S03 section 9; S05 section 3 rules 5-6 |
| FCT-049 | Existing LLM, Qdrant, Neo4j, retrieval and hydration ideas must be audited; no model/provider is specified. | S03 section 11; S01 section 15 |
| FCT-050 | Approved specifications govern coding-agent development; changes and assumptions must be explicit, rules traceable and source conflicts surfaced. | S03 sections 12-13; S05 section 3 |

## Stakeholder evidence

CONFIRMED: customers and admins are the principal user groups in S01 P003-005. The instructor provides product-direction feedback in S02. Coding agents are the intended primary development mechanism in S03 section 12; that does not make them business approvers. TBD: named product owner, sponsor, operating organization and approval authority are not identified in the supplied sources (OPEN-001).

## Navigation

Read [requirements](02_REQUIREMENTS_INVENTORY.md), [business rules](03_BUSINESS_RULES_INVENTORY.md), [domains](04_DOMAIN_INVENTORY.md), [scope](05_SCOPE_REGISTER.md), [open questions](06_OPEN_QUESTIONS.md), [contradictions](07_CONTRADICTIONS.md), [glossary](08_GLOSSARY.md), [decisions](09_DECISION_REGISTER.md), [risks and gaps](10_RISK_AND_GAP_REGISTER.md), [traceability](11_TRACEABILITY_SEED.md) and the [proposed documentation plan](12_PROPOSED_DOCUMENTATION_PLAN.md).
