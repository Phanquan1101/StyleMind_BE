# StyleMind Requirements Inventory

Source keys and classifications are defined in [Project Facts](01_PROJECT_FACTS.md). This inventory captures evidence and unresolved requirements, not a final PRD. CONFIRMED does not remove qualifications in the Requirement or Notes column. Original service names describe S01 evidence; final ownership remains TBD under ARCH-003. TBD rows record missing specification, not newly authorized functionality. Business rules have their own canonical inventory in file 03.

## Business objectives and actors

| ID | Type | Requirement | Classification | Source | Notes |
| --- | --- | --- | --- | --- | --- |
| BUS-001 | Business objective | Combine fashion commerce with AI styling connected to purchasable products. | CONFIRMED | S01 P564; S03 section 1 | Revenue/conversion targets TBD; OPEN-001. |
| BUS-002 | Business objective | Specialize primarily in women's styling, wardrobe, fit, color and travel needs. | CONFIRMED | S03 section 2 | Age segment and commercial territory TBD; OPEN-002. |
| BUS-003 | Business objective | Retain the full purchase, payment, post-purchase and administration lifecycle. | CONFIRMED | S03 section 1 | First-release scope TBD; OPEN-003. |
| BUS-004 | Business objective | Enable operational investigation and reconciliation through important-action traceability. | CONFIRMED | S01 P517-528 | No retention target or audit schema specified. |
| ACT-001 | Actor | CUSTOMER uses shopping, profile, order, return, notification and stylist functions. | CONFIRMED | S01 P067, P530-541 | Ownership and individual permissions require detailed mapping. |
| ACT-002 | Actor | ADMIN manages users, catalog, orders, returns/refunds, notifications and AI data. | CONFIRMED | S01 P068, P542-551 | No additional admin hierarchy inferred. |
| ACT-003 | Actor | Guest uses a cart without a userId. | CONFIRMED | S01 P159-167 | Checkout still requires login. |
| ACT-004 | Actor | The system performs verification, totals, webhook reconciliation, expiry, logging and conditional AI sync. | CONFIRMED | S01 P552-562 | Automation actor, not a new user role or service. |

## Functional requirements

| ID | Type | Requirement | Classification | Source | Notes |
| --- | --- | --- | --- | --- | --- |
| FR-001 | Functional | Register through email, a temporary registration and OTP verification before real account creation. | CONFIRMED | S01 P019-034 | Registration fields beyond email/password TBD; BR-001; OPEN-004. |
| FR-002 | Functional | Authenticate email/password after account status checks and issue a JWT. | CONFIRMED | S01 P035-049 | SEC-003 through SEC-005. |
| FR-003 | Functional | Reset password after OTP verification, using the source's reset-token or direct-reset alternative. | CONFIRMED | S01 P050-064 | Alternative is not selected; OPEN-005. |
| FR-004 | Functional | View/update basic profile linked to auth userId. | CONFIRMED | S01 P073-082 | Exact editable fields TBD. |
| FR-005 | Functional | List, add, update, delete and select a default shipping address for the current user. | CONFIRMED | S01 P083-097 | Default cardinality enforcement TBD; BR-006. |
| FR-006 | Functional | Provide Vietnam province/ward lookup and validate/normalize address phone data. | CONFIRMED | S01 P090-097 | Administrative dataset/version and normalization rules TBD; OPEN-007. |
| FR-007 | Functional | Show categories publicly and use them for product filtering. | CONFIRMED | S01 P104-115 | Category hierarchy is conditional. |
| FR-008 | Functional | Let admin create/update/delete categories, with parent/child management if present. | CONFIRMED | S01 P108-115 | Deletion policy TBD; OPEN-008. |
| FR-009 | Functional | List/search/filter products by keyword, category, price and demographic; show product and variant details. | CONFIRMED | S01 P116-128 | Active-only policy wording and price presentation unresolved. |
| FR-010 | Functional | Let admin manage products, statuses, variants and uploaded product images. | CONFIRMED | S01 P129-143 | Activation and deletion safeguards TBD; BR-007. |
| FR-011 | Functional | Supply variant snapshots for cart validation and checkout. | CONFIRMED | S01 P154-156, P216 | Field evidence in DATA-001; AI hydration is separately recorded in AI-006; final contracts TBD. |
| FR-012 | Functional | Maintain a guest cart using client guest_session_id and X-Guest-Session-Id. | CONFIRMED | S01 P158-167 | Session lifecycle and protection TBD; OPEN-009. |
| FR-013 | Functional | Maintain the authenticated user's active cart using user context. | CONFIRMED | S01 P168-177 | BR-009. |
| FR-014 | Functional | Add/update/remove items and clear cart; validate variant, activity, stock and quantity when adding an item. | CONFIRMED | S01 P178-191 | Quantity limits, update semantics and checks on update remain TBD. |
| FR-015 | Functional | Merge guest cart after login, combine duplicate variant quantities and clear or retire guest cart. | CONFIRMED; DEC-030 | S01 P192-199 | Atomic merge, conflict identification, price-change notice, retry and clear/retire outcome approved; guest-session and quantity semantics remain TBD (OPEN-009). |
| FR-016 | Functional | Coordinate checkout using current address/cart/variant data, calculate totals, create order/items and initiate payment. | CONFIRMED; DEC-031, DEC-032 | S01 P200-236 | Product-level atomic checkout, price reconfirmation and recoverable failure approved; transaction/compensation details remain TBD (OPEN-011, OPEN-014). |
| FR-017 | Functional | Create COD payment transaction, confirm order and attempt cart clearing and notification. | CONFIRMED; DEC-033 | S01 P238-252 | COD remains unpaid/pending until collection; failed collection does not mark paid; detailed operational lifecycle remains TBD (OPEN-015). |
| FR-018 | Functional | Create SePay transaction and QR/transfer information for a PAYMENT_PENDING order. | CONFIRMED; DEC-033 | S01 P253-263 | Payment attempt expires 15 minutes after transaction/QR generation; a new attempt may be started when eligible. |
| FR-019 | Functional | Authenticate, record, deduplicate and reconcile SePay webhooks, then callback the order flow. | CONFIRMED; DEC-033 | S01 P271-280 | Exact-match authoritative confirmation, abnormal/late exception handling and no terminal-order revival approved; webhook protocol remains TBD (OPEN-016, OPEN-018). |
| FR-020 | Functional | Enforce the explicit order state machine and return a business error on invalid transitions. | CONFIRMED; DEC-033 | S01 P281-306 | Terminal-state protection against later payment revival approved; trigger/actor matrix remains TBD. |
| FR-021 | Functional | Expire orders waiting for payment. | CONFIRMED; DEC-033 | S01 P292, P296, P560 | Payment attempt timeout is 15 minutes from transaction/QR generation; separate Order timeout coordination remains TBD (OPEN-019). |
| FR-022 | Functional | Let customers see their orders, items, purchase prices and current state; state history is conditional on exposure. | CONFIRMED | S01 P330-337 | History exposure TBD; OPEN-020. |
| FR-023 | Functional | Accept eligible customer cancellation requests and reasons; apply review if the chosen flow requires it. | CONFIRMED; DEC-034 | S01 P307-319 | Whole-order cancellation states, mandatory reason, paid-order review and refund eligibility approved; request lifecycle/concurrency remains TBD (OPEN-021). |
| FR-024 | Functional | Let admin cancel for operational reasons through the state machine with corresponding payment handling. | CONFIRMED | S01 P320-329 | Exact refund/cancel-payment mapping TBD. |
| FR-025 | Functional | Support owner-uploaded receipt images for eligible completed-order use and admin dispute review. | CONFIRMED | S01 P337-343 | Exact states, formats and limits TBD; OPEN-022. |
| FR-026 | Functional | Check return order existence/ownership, eligible state, return window and remaining returnable quantity. | CONFIRMED; DEC-034 | S01 P344-351 | Return only after COMPLETED and within seven calendar days; repeated/concurrent accounting remains TBD (OPEN-023). |
| FR-027 | Functional | Create an item-level return request with quantity, reason, description and optional evidence for admin review. | CONFIRMED; DEC-034 | S01 P352-363 | Item-level, partial/full quantity, mandatory request fields, optional evidence and admin review approved. |
| FR-028 | Functional | Let admin inspect, approve/reject and record return QC, leading to refund/restock decisions. | CONFIRMED; DEC-034 | S01 P364-374 | QC is required before refund acceptance; exchange/replacement is out of scope; detailed QC/restock rules remain TBD (OPEN-024). |
| FR-029 | Functional | Accept carrier, tracking and notes for an eligible return shipment. | CONFIRMED; DEC-034 | S01 P375-382 | Optional evidence/admin review direction approved; shipment state and carrier integration remain TBD (OPEN-025). |
| FR-030 | Functional | Create internal refund requests, retrieve refunds by order and let admin mark complete/fail. | CONFIRMED; DEC-032, DEC-034 | S01 P383-394 | Paid-amount item basis, cap, manual/admin process, states and retry direction approved (OPEN-026). |
| FR-031 | Functional | Store a payout destination linked to the corresponding return request. | CONFIRMED; DEC-034 | S01 P390, P395 | Verified destination is required; access/change/retention details remain TBD (OPEN-027). |
| FR-032 | Functional | Show user notifications/unread count/details and mark one or all read. | CONFIRMED | S01 P407-412 | Channels and ownership enforcement details TBD. |
| FR-033 | Functional | Provide admin notification log, summary and failed-delivery retry; store status and fallback logs. | CONFIRMED | S01 P413-420 | Trigger matrix and retry policy TBD; OPEN-028. |
| FR-034 | Functional | Let admin manage users, including role/status actions tracked for audit. | CONFIRMED | S01 P068-071, P519, P544 | Self-lock/last-admin guards are conditional; OPEN-029. |
| FR-035 | Functional | Provide an admin operational dashboard and reports. | CONFIRMED | S01 P421-435 | Listed metrics are qualified as typical; formulas/selection TBD; OPEN-030. |
| FR-036 | Functional | Trace important admin, order, webhook, notification, return and refund actions. | CONFIRMED | S01 P517-528 | Storage, retention and detail TBD; OPEN-031. |
| FR-037 | Functional | Include owned clothes and purchasable catalog products in Smart Wardrobe styling. | CONFIRMED | S03 section 3 | Detailed operations not specified; FR-039. |
| FR-038 | Functional | Support travel and trip-duration-aware outfit planning as product direction. | CONFIRMED | S02 Product Focus; S03 section 7; S05 section 2 | Destination/activities/reuse details TBD; OPEN-045. |
| FR-039 | Functional gap | Wardrobe adding/editing/removing, images, metadata, purchase source, favorites and outfit history behavior. | TBD | S03 section 3; S05 section 7B | No implicit CRUD or image-recognition requirement; OPEN-032 through OPEN-034. |
| FR-040 | Functional gap | Inventory adjustments, reservation, deduction, release and restock lifecycle. | TBD | S01 P150, P189, P207, P374 | Stock checks exist, but stock mutation policy is absent; OPEN-011. |

## AI requirements and audit items

| ID | Type | Requirement | Classification | Source | Notes |
| --- | --- | --- | --- | --- | --- |
| AI-001 | AI | Create/list/read/delete chat sessions, read/send messages and persist consultation history. | CONFIRMED | S01 P436-448 | Deletion versus retention semantics TBD; OPEN-039. |
| AI-002 | AI | Audit the original intent-to-outfit flow using Qdrant concepts, Neo4j rules and hybrid product search. | CONFIRMED | S01 P452-460; S03 section 11 | Evidence of existing design; effective algorithm/technology commitment TBD; OPEN-040. |
| AI-003 | AI | Ground purchasable recommendations in real StyleMind product/catalog data. | CONFIRMED | S03 section 11 | BR-032; no invented buyable products. |
| AI-004 | AI | Respect budget when supplied by the user. | CONFIRMED | S01 P463 | Budget scope, currency and constraint conflicts TBD; OPEN-041. |
| AI-005 | AI | Return selection reasons, style/body/occasion/rule explanation, styling tips, images and prices. | CONFIRMED | S01 P460, P464-465 | Explanation depth and response form TBD. |
| AI-006 | AI | Use product retrieval/hydration so returned products contain information for viewing/buying. | CONFIRMED | S01 P459, P465; S03 section 11 | Current availability and stale-result behavior TBD; OPEN-042. |
| AI-007 | AI | Audit catalog create/update/delete synchronization and Qdrant product indexing. | CONFIRMED | S01 P466-473, P497; S03 section 11 | Sync activation/freshness/repair contract TBD; OPEN-043. |
| AI-008 | AI | Ingest text/URL knowledge through draft and admin approval before production upsert; propagate approved-source deletion. | CONFIRMED | S01 P474-487 | Approval is mandatory; ingestion quality/versioning TBD; OPEN-044. |
| AI-009 | AI | Consider personal style preferences in recommendations. | CONFIRMED | S03 sections 1-2; S01 P452 | Vocabulary and source precedence TBD; OPEN-035. |
| AI-010 | AI | Consider product brands and user brand preference where appropriate. | CONFIRMED | S03 section 4 | Preference is conditional, not a mandatory hard filter; OPEN-036. |
| AI-011 | AI | Personalize recommendations for size and preferred fit. | CONFIRMED | S03 section 5 | Algorithm and brand charts TBD; OPEN-037 through OPEN-038. |
| AI-012 | AI | Coordinate outfit colors. | CONFIRMED | S03 section 6 | Owner and rules TBD; OPEN-046. |
| AI-013 | AI | Consider optional body information for body-aware styling. | CONFIRMED | S03 sections 2, 5; S05 section 7C | Fields remain potential; BR-033. |
| AI-014 | AI gap | Persistent preference memory beyond stored message history. | TBD | S01 P449 | Source says may; no confirmed retention or memory extraction behavior. |
| AI-015 | AI gap | Model/provider, fallback and failure behavior, cost/latency targets and deployment choices. | TBD | S01 section 15; S03 section 11 | No provider selected; OPEN-047. |
| AI-016 | AI gap | Evaluation datasets, criteria and thresholds for grounding, budget, fit, color and usefulness. | TBD | S01 section 15; S05 pass 5 | No evaluation specification; OPEN-048. |
| AI-017 | AI gap | Outfit structure and constraint precedence across wardrobe/catalog, budget, fit, brand, availability and occasion. | TBD | S03 sections 3-7; S01 P452-465 | OPEN-049; no ranking algorithm inferred. |
| AI-018 | AI consequence | An owned item and a purchasable recommendation need distinguishable provenance in the stylist experience. | DERIVED | FR-037 + AI-003 | Logical separation; representation and UX are TBD; OPEN-033. |

## Data and architecture

| ID | Type | Requirement | Classification | Source | Notes |
| --- | --- | --- | --- | --- | --- |
| DATA-001 | Data | Variant snapshot contains product name, SKU, size, color, material, effective price, stock, status and image. | CONFIRMED | S01 P156 | Data concepts, not a generated schema. |
| DATA-002 | Data | Preserve purchase-time item price, address and recipient information on orders. | CONFIRMED | S01 P219, P223-225 | Other snapshot fields/versioning TBD; OPEN-013. |
| DATA-003 | Data | Products support brand information. | CONFIRMED | S03 section 4 | Brand catalog/normalization and cardinality TBD; OPEN-036. |
| DATA-004 | Data | Potential fit profile includes height, bust, waist, hip, usual top/bottom sizes, shoe size and preferred fit. | CONFIRMED | S03 section 5 | Potential fields, not approved mandatory collection; OPEN-037. |
| DATA-005 | Data gap | Measurement units, validation, product measurements and size-chart conventions. | TBD | S03 section 5 | No numbers or standards assumed. |
| DATA-006 | Data gap | Wardrobe item identity, catalog relationship, provenance and metadata model. | TBD | S03 section 3 | No table/entity schema created; OPEN-033. |
| DATA-007 | Data gap | Precise Product versus Variant versus SKU definitions, uniqueness and cardinalities. | TBD | S01 P145-156 | Source uses Variant/SKU jointly; OPEN-050. |
| DATA-008 | Data gap | Data retention, deletion, history preservation and migration rules. | TBD | S01 P443, P448, P517-528 | Tension is unresolved behavior, not proven contradiction; OPEN-039, OPEN-051. |
| ARCH-001 | Architecture | Backend uses Java and Spring Boot. | CONFIRMED | S03 section 9 | Versions/dependencies TBD. |
| ARCH-002 | Architecture | Use microservices architecture. | CONFIRMED | S03 section 9 | Does not confirm source service list as final. |
| ARCH-003 | Architecture | Determine final service boundaries from domain analysis and data ownership. | CONFIRMED | S03 section 9; S05 rules 5-6 | OPEN-052; no new microservices created. |
| ARCH-004 | Architecture | Frontend uses React; detailed architecture and libraries remain undecided. | CONFIRMED | S03 section 10 | OPEN-053. |
| ARCH-005 | Architecture | Services do not directly access each other's databases or use cross-service foreign keys. | CONFIRMED | S01 P489, P499-500 | Database products and physical layout TBD. |
| ARCH-006 | Architecture evidence | Preserve S01's named internal API calls and data assignments for later boundary audit. | CONFIRMED | S01 P081-082, P488-502 | Candidate ownership evidence only; file 04. |
| ARCH-007 | Architecture | Classify auxiliary failures into mandatory rollback/compensation versus best-effort log/continue. | CONFIRMED | S01 P502 | Full classification and transactions TBD; OPEN-054. |
| ARCH-008 | Architecture governance | Specifications govern agent implementation; changes, assumptions and rules must be explicit and traceable. | CONFIRMED | S03 sections 12-13 | No business behavior derived from code. |
| ARCH-009 | Architecture gap | Per-interaction synchronous/asynchronous choices, ordering, delivery guarantees and failure handling. | TBD | S01 section 16 | Internal API evidence does not settle every interaction; OPEN-055. |
| ARCH-010 | Architecture gap | Availability, deployment, storage, observability and recovery architecture. | TBD | S03 sections 9-11 | No hosting/cloud/runtime stack assumed; OPEN-056. |

## Security, integration and experience

| ID | Type | Requirement | Classification | Source | Notes |
| --- | --- | --- | --- | --- | --- |
| SEC-001 | Security | Do not persist passwords or OTP in plaintext; OTP is hashed. | CONFIRMED | S01 P024, P034, P059 | Parameters TBD. |
| SEC-002 | Security | Registration/reset OTP have expiry, attempt limits and resend cooldown. | CONFIRMED | S01 P031-033, P061-063 | Values and lock behavior TBD; OPEN-004. |
| SEC-003 | Security | Verify password with BCrypt and reject locked/invalid accounts. | CONFIRMED | S01 P040-046 | Reset and lock/session interaction TBD. |
| SEC-004 | Security | JWT includes user id, email/subject and role and is signed with the auth RSA private key. | CONFIRMED | S01 P047-048 | Key custody/rotation and claims policy TBD; OPEN-057. |
| SEC-005 | Security | Gateway/downstream verify JWT with RSA public key; requests carry bearer token and gateway injects user context. | CONFIRMED | S01 P044, P049, P173, P554-555 | Frontend token storage mechanism unspecified. |
| SEC-006 | Security | Admin endpoints and dashboard require admin role. | CONFIRMED | S01 P071, P433 | Granular permission matrix TBD; OPEN-058. |
| SEC-007 | Security | Enforce explicit ownership checks on addresses, active carts, receipt images and return eligibility. | CONFIRMED | S01 P092-093, P177, P339, P348 | Do not infer an already specified universal endpoint matrix. |
| SEC-008 | Security | Restrict internal endpoints to services through an internal token. | CONFIRMED | S01 P072, P501, P556 | Issuance, scoping, rotation and replay controls TBD; OPEN-059. |
| SEC-009 | Security | Check SePay webhook API key or credential before processing. | CONFIRMED | S01 P273 | Exact supported credential mechanism TBD; OPEN-016. |
| SEC-010 | Security | Validate receipt image format and size. | CONFIRMED | S01 P341-342 | Actual values and other upload surfaces TBD; OPEN-022. |
| SEC-011 | Security gap | Consent, access, retention and deletion for measurements, wardrobe images, conversation and payout data. | TBD | S03 sections 3, 5; S01 P390, P448 | No legal jurisdiction or standard imposed; OPEN-051. |
| SEC-012 | Security gap | AI prompt/knowledge injection handling, URL ingestion restrictions and sensitive-data exposure controls. | TBD | S01 P477-487; S03 section 11 | Audit gaps, not invented adopted controls; OPEN-060. |
| NFR-001 | Non-functional | Avoid duplicate successful checkout/payment effects and avoid repeating already successful work on retry. | CONFIRMED | S01 P510-516 | Mechanisms partially known; retention/races TBD. |
| NFR-002 | Non-functional | Notification failure does not roll back main order/payment business processing. | CONFIRMED | S01 P418-420 | No automatic guarantee for every ancillary operation. |
| NFR-003 | Non-functional gap | Quantitative latency, throughput, uptime, recovery, accessibility and capacity acceptance targets. | TBD | S01-S03 contain no targets | OPEN-056, OPEN-061. |
| INT-001 | Integration | Email delivery supports OTP and business notifications. | CONFIRMED | S01 P025, P055, P397 | Email provider and delivery contract TBD; OPEN-028. |
| INT-002 | Integration | SePay/VietQR integration supplies QR/transfer data and webhook reconciliation. | CONFIRMED | S01 P253-280 | No vendor API/version imported from outside sources. |
| INT-003 | Integration evidence | Audit the Qdrant and Neo4j integrations in the original AI flow. | CONFIRMED | S01 P454-455, P470, P482; S03 section 11 | Final retained responsibilities TBD; OPEN-040. |
| INT-004 | Integration | Use available Vietnam province/ward data for address validation. | CONFIRMED | S01 P090, P097 | Provider, version and refresh ownership TBD. |
| INT-005 | Integration gap | Weather integration for travel. | TBD | S03 section 7 | Explicitly not automatically approved; OPEN-045. |
| INT-006 | Integration gap | Carrier integration or bank payout automation. | TBD | S01 P375-395 | Manual shipment fields/admin refund marking do not confirm external integrations. |
| UX-001 | Experience | Provide strong visual outfit presentation. | CONFIRMED | S02 Product Focus; S03 section 8 | Detailed display and interaction choices TBD. |
| UX-002 | Experience | Include advanced live 3D in broader scope without making it automatically first milestone. | CONFIRMED | S03 section 8; S05 section 2 | Milestone and acceptance criteria TBD; OPEN-062. |
| UX-003 | Experience gap | Define separate meanings and desired experience for 2D composition, 3D avatar and virtual try-on. | TBD | S03 section 8 | No photorealism, scanning or garment simulation assumed. |

## Original AI workflow evidence

CONFIRMED as the sequence described in S01 P452-460: the customer supplies occasion, style, budget, gender, preferences and restrictions; AI extracts intent, resolves fashion concepts from Qdrant, reads Neo4j rules, generates search terms per needed item, retrieves candidates with hybrid search and selects an outfit. Product-service then hydrates real product information, and the response returns outfit, reasons, tips, images and prices. AI-002 and AI-006 preserve this evidence. S03 section 11 requires auditing its effective future responsibilities; OPEN-040 and OPEN-049 remain unanswered. This paragraph does not select an AI provider or approve a new architecture.

CONFIRMED as described in S01 P477-487: the input is text or a URL; a URL input is scraped. The flow then extracts concepts/rules, creates a draft source, obtains admin review and upserts approved data to graph/vector stores. Approved-source deletion requires related updates. Source metadata is recommended by S01 P486 but its required fields are TBD. AI-008, BR-035 and BR-036 capture the known behavior; OPEN-044 and OPEN-060 capture unresolved quality/security responsibilities.

All unresolved inventory entries have review paths in file 06. Cross-cutting invariants are tracked in file 03, and scope/milestone interpretation in file 05. No requirement above selects a new service, API shape or schema.

## Human Decision Baseline V1 overlay

The later approved baseline makes a bounded V1 interpretation of selected TBD/gap rows without pretending that the discovery-era inventory contained those decisions. DEC-028 approves the V1 subset represented by FR-039: Wardrobe CRUD, personal clothing image upload, basic metadata, visual/category browsing and AI context integration. Final data modeling, image policy, access/retention details and optional operations remain TBD. DEC-029 supplies V1 product promises and acceptance boundaries for AI-015 through AI-017; provider/runtime, subjective thresholds and detailed constraint semantics remain TBD. See `docs/decisions/00_DECISION_BASELINE_V1.md` and `docs/decisions/DEC-028.md` through `DEC-029.md`.
