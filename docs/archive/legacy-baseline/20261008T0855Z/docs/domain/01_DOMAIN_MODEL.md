# StyleMind V1 Logical Domain Model

Status: HUMAN-APPROVED

Prepared: 2026-09-22

Authority basis: HUMAN-APPROVED BRD and PRD, Decision Baseline V1, DEC-026 through DEC-034, and the Phase 0 discovery evidence.

This is a logical, business-oriented model. It is not a database schema, API contract, event catalog, service catalog, bounded-context map or implementation design.

## 1. Purpose and scope

This document establishes a shared logical vocabulary for StyleMind V1 and records the business relationships and lifecycle concepts needed by later Context Map and SAD work.

The model covers:

- Identity, customer profile and address concepts.
- Catalog, Product/Variant, brand and inventory concepts.
- Smart Wardrobe and the distinction between owned items and purchasable catalog items.
- AI Stylist context, sessions, outfits and recommendation provenance.
- Cart, checkout, money, payment and Order concepts.
- Cancellation, return, QC, refund, notification, administration and audit concepts.

The model does not decide final first-milestone details outside the approved PRD, technical ownership, persistence, integration protocols or runtime providers.

## 2. Domain-modeling principles

1. Approved product behavior in the PRD and DEC-026 through DEC-034 is authoritative for V1 business outcomes.
2. A source-backed concept is not automatically a final aggregate, bounded context or service.
3. A Wardrobe Item is an owned-clothing concept and is not the same entity as a Catalog Product or Variant.
4. Product/Variant identity, SKU semantics, stock authority and final ownership remain explicit where the sources leave them open.
5. Order state and payment-transaction state are separate concepts even when both use the label `PAID`.
6. Logical relationships may be recorded without choosing tables, foreign keys, APIs, events or persistence technology.
7. `CONFIRMED` and `APPROVED` preserve source/decision authority; `DERIVED` identifies a modeling consequence; `PROPOSED` identifies a candidate grouping; `TBD` remains unresolved.
8. No relationship or transition is imported from generic e-commerce practice.

## 3. Core domain concepts

### 3.1 Identity and customer

| Concept | Classification | Logical meaning |
| --- | --- | --- |
| User / Customer account | CONFIRMED | The authenticated customer account created after email and OTP verification. Account status and role affect access. |
| Authentication Identity | CONFIRMED concept; final ownership TBD | Email/password, OTP and authentication status needed to establish an authenticated identity. Credential/session implementation is outside this model. |
| Profile | CONFIRMED | Basic customer information linked to the authenticated user identity. Exact field set remains partly TBD. |
| Address | CONFIRMED | Customer-owned shipping/recipient address with CRUD and default-selection behavior subject to unresolved detail. |
| Address Snapshot | CONFIRMED value-object candidate | Purchase-time address and recipient information retained on the Order despite later address changes. |
| Customer and Admin roles | CONFIRMED actors | Business actors with customer/admin permissions. Subroles and the complete permission matrix remain TBD. |
| Guest Session | CONFIRMED concept; lifecycle TBD | Client-side guest identity used for a guest Cart and not represented by a `userId`. Creation, expiry and security remain TBD. |

### 3.2 Catalog and inventory

| Concept | Classification | Logical meaning |
| --- | --- | --- |
| Category | CONFIRMED | Product classification and filtering concept. Hierarchy and deletion behavior remain TBD. |
| Brand | CONFIRMED direction; semantics TBD | Product brand information that may participate in styling preference. Vocabulary, ownership and sizing relationship remain TBD. |
| Product | CONFIRMED | Catalog item presented in discovery/details and associated with product-level information, images, status and selectable Variant concepts. |
| Variant | CONFIRMED | Selectable Product option carrying source-supported attributes such as size, color, material, SKU, stock, status and effective price. |
| SKU | CONFIRMED field presence; canonical identity TBD | Source-named identifier/field associated with Variant information. Uniqueness and relationship to Variant remain OPEN-050. |
| Product Image | CONFIRMED | Image associated with a Catalog Product and managed through the confirmed catalog capability. |
| Inventory / Stock Position | CONFIRMED concept; ownership/lifecycle TBD | Availability information used by Cart and Checkout and potentially affected by return/QC outcomes. Reservation, deduction, release, restock and concurrency are not decided. |
| Variant Snapshot | CONFIRMED value-object candidate | Product/Variant information used when validating or displaying a cart/order selection, including effective price and availability information where applicable. It is not a database schema. |

### 3.3 Smart Wardrobe

| Concept | Classification | Logical meaning |
| --- | --- | --- |
| Wardrobe | DERIVED logical grouping | A customer's owned-clothing context containing Wardrobe Items. It is not automatically a service or a separate approved aggregate. |
| Wardrobe Item | APPROVED V1 concept | A clothing item the customer says they already own. It supports CRUD, basic metadata, optional personal clothing image and AI context use. |
| Wardrobe Item Image | APPROVED V1 capability concept | Optional image attached to a Wardrobe Item; limits, retention, access and deletion remain TBD. |
| Wardrobe Metadata | APPROVED V1 | Basic category, primary color and occasion and/or style metadata. Exact vocabularies remain TBD. |
| Catalog provenance | APPROVED distinction | A Wardrobe Item is owned data and is not made purchasable merely because it exists. |

### 3.4 AI and styling

| Concept | Classification | Logical meaning |
| --- | --- | --- |
| Chat Session | CONFIRMED | A customer/user-context conversation containing stored messages. Deletion, retention and memory policy remain TBD. |
| Message | CONFIRMED | A persisted input/output item within a Chat Session. Exact content lifecycle and sensitive-data treatment remain TBD. |
| Styling Request | DERIVED candidate concept | A logical request containing the user's occasion, intended use, style, optional general weather/context, budget and restrictions. It is not confirmed as a persisted entity. |
| Styling Context | DERIVED grouping | The context supplied to or selected by the AI Stylist. It must not imply that every potential profile field is approved. |
| Hard Constraint | APPROVED V1 concept | A constraint that must be obeyed, such as an excluded category, prohibited color, excluded product/type or supplied hard maximum budget. |
| Soft Preference | APPROVED V1 concept | A preference used for relevance but not represented as guaranteed hard compliance. |
| Outfit | CONFIRMED result concept; structure TBD | AI-selected outfit result with reasons, styling tips, images/prices where supported and item provenance. Required slots/count, persistence and completeness remain TBD. |
| Outfit Item | DERIVED candidate concept | A member of an Outfit with a source classification of `OWNED_WARDROBE` or `CATALOG_RECOMMENDATION`. |
| Owned Item Reference | APPROVED provenance concept | Reference from an Outfit Item to a Wardrobe Item. |
| Catalog Recommendation Reference | APPROVED provenance concept | Reference from a purchasable Outfit Item to a valid Catalog Product/Variant. |
| Product Hydration | CONFIRMED source operation; responsibility TBD | The source-described operation that fills real product information for an AI result. It is not modeled as a provider or implementation component here. |
| Fashion Knowledge | CONFIRMED direction; lifecycle TBD | Approved fashion concepts/rules derived from text/URL input through draft and admin approval. Provenance, versioning and deletion behavior remain TBD. |

Travel Styling and Live 3D remain broader directions with unresolved experience and milestone meaning. They are not introduced as mandatory V1 entities.

### 3.5 Cart, checkout and Order

| Concept | Classification | Logical meaning |
| --- | --- | --- |
| Cart | CONFIRMED | A guest- or user-associated collection of Cart Items. An authenticated user has one active Cart. |
| Cart Item | CONFIRMED | A reference to a selected Variant and a requested quantity. Cart price is not price-locked. |
| Cart Merge Attempt | DERIVED candidate concept | A business operation with an atomic success/failure outcome. It does not decide transaction implementation or persistence. |
| Checkout Context | DERIVED candidate concept | The current address, Cart contents, Product/Variant validity, stock and authoritative prices evaluated before Order creation. |
| Order | CONFIRMED | The purchase record created after successful Checkout validation and governed by the source-supported Order lifecycle. |
| Order Item | CONFIRMED | A purchased Product/Variant selection with purchase-time item information. |
| Purchase Price Snapshot | CONFIRMED value-object candidate | The `price_at_purchase` value retained for an Order Item. |
| Order Total | APPROVED V1 | Backend-authoritative total in whole VND. Shipping fee is 0 VND, no separate application tax/VAT component is introduced and V1 has no discount/promotion engine. |

### 3.6 Money and payment

| Concept | Classification | Logical meaning |
| --- | --- | --- |
| Money | APPROVED V1 value-object candidate | Whole Vietnamese đồng only, with zero decimal places. The backend is authoritative for payable amounts. |
| Payment | CONFIRMED concept | The payment business record associated with an Order. Its state is distinct from the Order state. |
| Payment Method | CONFIRMED concept | COD or SePay/VietQR as the named V1 payment choices where available. |
| Payment Transaction / Payment Attempt | CONFIRMED concept; decomposition TBD | A COD or online payment attempt/transaction carrying amount and payment status. An online attempt has a 15-minute expiry from transaction/QR generation. |
| Payment Confirmation | APPROVED V1 concept | Authoritative exact-match confirmation that may mark an online payment paid. Frontend/user action is not authoritative. |
| Payment Exception | DERIVED candidate concept | Underpayment, overpayment, unmatched and late funds requiring admin reconciliation without marking or reviving an Order. |

### 3.7 Post-purchase and operations

| Concept | Classification | Logical meaning |
| --- | --- | --- |
| Cancellation Request | CONFIRMED/APPROVED V1 concept | Whole-Order customer cancellation request with mandatory reason. Eligibility and paid-order review follow DEC-034; request-state details remain TBD. |
| Return Request | APPROVED V1 concept | Item-level return request after Order completion and within the seven-calendar-day window, subject to ownership, quantity, reason, description and admin review. |
| Return Item | APPROVED V1 concept | The Order Item and approved quantity covered by a Return Request. Partial and full return quantities are supported. |
| Return Evidence | CONFIRMED/APPROVED V1 concept | Optional evidence at submission; admin may require or review available evidence. |
| QC Result | APPROVED V1 concept | Admin result required before a physical returned item is finally accepted for refund. Detailed checklist and restock outcomes remain TBD. |
| Refund | APPROVED V1 concept | Refund eligible after approved paid cancellation or approved return receipt/QC processing. It is based on actual paid eligible amounts and has cumulative caps. |
| Refund Destination | APPROVED V1 concept | Verified destination used by the approved admin refund process. COD uses a verified user-provided bank destination; access/change rules remain TBD. |
| Notification | CONFIRMED | Customer-readable notification and admin delivery/log/retry concept. Exact event/channel matrix remains TBD. |
| Audit Record | CONFIRMED concept | Traceability record for important admin, order, payment, notification, return and refund actions. Retention and detailed fields remain TBD. |
| Admin Review Action | DERIVED candidate concept | A logical record of admin review, approval/rejection, QC or refund action. It does not define an audit schema or permission matrix. |

## 4. Entity and value-object candidates

The following candidates are logical modeling aids, not database tables or final aggregate definitions.

| Candidate | Kind | Identity/lifecycle guidance |
| --- | --- | --- |
| User, Authentication Identity, Profile, Address | Entity candidates | Their exact ownership split and lifecycle remain subject to identity/security decisions. |
| Product, Variant, Category, Brand, Product Image | Entity candidates | Product/Variant/SKU cardinality, identity and activation/deletion semantics remain TBD. |
| Inventory / Stock Position | Entity or record candidate | Stock is observable and validated; mutation, reservation and restock semantics are TBD. |
| Wardrobe, Wardrobe Item, Wardrobe Item Image | Entity candidates | Wardrobe Item belongs to a customer-owned context and remains distinct from Catalog. Image policy remains TBD. |
| Chat Session, Message | Entity candidates | Source confirms persistence of chat history; deletion/memory behavior remains TBD. |
| Styling Request, Styling Context, Outfit, Outfit Item | Entity/result candidates | Some are derived groupings; persistence and complete outfit structure are TBD. |
| Cart, Cart Item | Entity candidates | Cart ownership and merge outcomes are approved; guest-session lifecycle and quantity semantics remain TBD. |
| Checkout Context | Ephemeral/value-object candidate | Represents validation context, not a persisted record commitment. |
| Order, Order Item | Entity candidates | Order state and purchase snapshots are business concepts; final aggregate boundary is not decided. |
| Money, Address Snapshot, Variant Snapshot, Purchase Price Snapshot | Value-object candidates | Their business meaning is supported; representation and persistence are later concerns. |
| Payment, Payment Attempt, Payment Exception | Entity/record candidates | Payment/order state separation is required; exact transaction decomposition and reconciliation lifecycle remain partly TBD. |
| Cancellation Request, Return Request, Return Item, QC Result, Refund, Refund Destination | Entity/record candidates | Product outcomes are approved; detailed request, QC, payout and concurrency states remain partly TBD. |
| Notification, Audit Record, Admin Review Action | Entity/record candidates | Logical evidence and user-visible behavior are supported; delivery, retention, access and audit structure remain TBD. |

## 5. Aggregate and lifecycle candidates

These are candidate business lifecycle groupings only. They do not assign database ownership or microservice boundaries.

### 5.1 Catalog lifecycle grouping

Product exposes Product/Variant information to discovery, Cart, Checkout and AI. Product/Variant/SKU cardinality, canonical identity, activation/deletion and stock ownership are not finalized. The model therefore retains the relationship without choosing a final aggregate boundary.

### 5.2 Cart lifecycle grouping

Cart contains Cart Items and is identified by either a guest session or authenticated user context. The authenticated user has one active Cart. A merge combines duplicate Variant quantities and either succeeds completely or fails completely. On failure the Guest Cart remains available; it is cleared/retired only after complete success. A price change does not itself invalidate merge.

### 5.3 Checkout and Order lifecycle grouping

Checkout Context is evaluated before Order creation. Product/Variant validity, activity, quantity, stock and current authoritative price must pass together. No partial checkout or silent item removal is allowed. An Order contains purchase-time item and address/recipient snapshots and follows the source-supported state graph in Section 9.

### 5.4 Payment lifecycle grouping

Payment is associated with an Order but has its own status. COD remains unpaid/pending collection until collection is confirmed. SePay/VietQR uses an online Payment Attempt that expires 15 minutes after transaction/QR generation; an expired attempt is not revived. Exact-match authoritative confirmation is required for online PAID. Late or abnormal money becomes a Payment Exception and never revives a terminal Order.

### 5.5 Cancellation, Return and Refund lifecycle grouping

Cancellation Request, Return Request, QC Result and Refund are related post-purchase concepts with distinct eligibility and outcomes. Customer cancellation is whole-Order only in V1. Return is item-level after COMPLETED within seven calendar days; admin review and QC precede refund acceptance. Refund amount is bounded by the actual paid eligible amount. The detailed request/QC/concurrency state machines remain TBD.

### 5.6 Wardrobe and AI lifecycle grouping

Wardrobe Items are customer-owned inputs. A Chat Session or derived Styling Request may use Wardrobe Items and Catalog references. An Outfit Item must preserve whether it came from `OWNED_WARDROBE` or `CATALOG_RECOMMENDATION`. A catalog recommendation intended for purchase must resolve to a real Catalog Product/Variant. The model does not require Outfit persistence or define an AI runtime lifecycle.

## 6. Logical relationships

1. A User has a Profile and customer-owned Addresses; a purchase records an Address Snapshot.
2. A Guest Session identifies a guest Cart; the guest Cart has no `userId`.
3. An authenticated User has one active Cart; a Cart contains Cart Items.
4. A Cart Item references a selected Variant and requested quantity. Cart price is not a price lock.
5. A Product exposes selectable Variant concepts, while exact cardinality and SKU identity remain TBD.
6. Product/Variant information is used by Cart validation, Checkout revalidation, Order Item snapshots and Catalog-grounded AI recommendations.
7. A User's Wardrobe contains Wardrobe Items. A Wardrobe Item is not a Catalog Product/Variant and is not purchasable merely by being referenced in an Outfit.
8. A Styling Request/Context may reference User context, Wardrobe Items, hard constraints, soft preferences and Catalog candidates.
9. An Outfit contains Outfit Items. Every Outfit Item identifies either `OWNED_WARDROBE` or `CATALOG_RECOMMENDATION`.
10. A purchasable Catalog Recommendation Reference resolves to a valid Catalog Product/Variant with Catalog-sourced identity, SKU, brand, price and availability information.
11. Checkout creates an Order and Order Items only after the complete product-level validation succeeds.
12. An Order has Order Items, purchase price snapshots, an Address Snapshot, a total and an Order state.
13. An Order is associated with Payment records/Attempts, but Order state and Payment state are not the same state machine.
14. A Cancellation Request references a whole Order. A Return Request references eligible Order Items and quantities.
15. A Return Request may have evidence, shipment information, admin review, QC result, Refund and Refund Destination according to approved policy and remaining TBD details.
16. Notification and Audit Record concepts may reference the relevant customer, Order, Payment, Return, Refund or admin action without defining a transport or storage mechanism.

## 7. Business-level ownership semantics

Ownership here means business authority or access meaning, not physical data ownership.

| Concept | Business-level ownership/authority | Remaining boundary question |
| --- | --- | --- |
| User/Profile/Address | Customer-owned, subject to authenticated access and admin rules. | Identity lifecycle, permissions and sensitive-data access remain TBD. |
| Guest Cart | Associated with a guest session until successful merge or later guest-session policy. | Session expiry/security and exact mutation semantics remain TBD. |
| User Cart | One active Cart in the authenticated user's context. | Final technical owner remains TBD. |
| Catalog Product/Variant/price/status | Catalog information is the business source for purchasable identity, price and availability checks. | Product/Variant/SKU semantics and final data owner remain TBD. |
| Inventory/stock | Stock availability constrains Cart and Checkout; successful Order creation must not commit unavailable stock. | Mutation, reservation, release, restock and concurrency owner remain TBD. |
| Wardrobe Item | Belongs to the customer's owned Wardrobe context and must remain distinguishable from Shop data. | Image/data lifecycle and final owner remain TBD. |
| Order | Order holds the purchase record, snapshots and Order lifecycle. | Final context/data owner and transaction responsibilities remain TBD. |
| Payment | Payment records/attempts hold payment status and reconciliation meaning; frontend does not own confirmation. | Protocol, credentials, actor matrix and integration ownership remain TBD. |
| Cancellation/Return/Refund | Requests and outcomes are tied to the customer's Order/Items and subject to admin review/processing. | Detailed state ownership, payout access and operational controls remain TBD. |
| AI Stylist | AI may compose and explain results, but does not own or invent purchasable Catalog identity. | Retrieval, knowledge, freshness and runtime responsibility remain TBD. |
| Admin/Audit | Admin acts on authorized operational records; Audit records important actions. | Role matrix, retention and access controls remain TBD. |

## 8. Key domain invariants

### Approved or source-supported invariants

1. A purchasable AI recommendation must reference a valid StyleMind Catalog Product/Variant.
2. A Wardrobe Item is not purchasable merely because it exists in a customer's Wardrobe.
3. Every Outfit Item identifies `OWNED_WARDROBE` or `CATALOG_RECOMMENDATION`.
4. Hard constraints override soft aesthetic preferences.
5. A customer makes the final size/Variant choice before adding a purchasable item to Cart; fit guidance is advisory.
6. An authenticated user has one active Cart; a guest Cart has no `userId`.
7. Cart merge combines duplicate Variant quantities atomically; a conflict fails the complete merge and does not partially change the User Cart.
8. A failed Cart merge retains the Guest Cart; successful complete merge is required before clear/retire.
9. Checkout revalidates Product/Variant validity, active status, quantity, current stock and authoritative price before Order creation.
10. Checkout does not silently remove invalid items or create a partial Order.
11. V1 money uses whole VND, backend-authoritative amounts, 0 VND shipping fee, no separate application tax/VAT component and no V1 discount/promotion engine.
12. Frontend/user action cannot authoritatively mark an online Payment PAID.
13. Online Payment confirmation requires valid matching confirmation and exact required amount; duplicate confirmations do not repeat business effects.
14. An online Payment Attempt expires 15 minutes after transaction/QR generation; a late payment cannot revive an expired/cancelled/terminal Order.
15. COD is not financially PAID until collection is confirmed.
16. Order transitions follow the source-supported state graph; COMPLETED, CANCELLED, EXPIRED and FAILED are terminal.
17. Customer cancellation is whole-Order only and requires a reason in the DEC-034 eligible states.
18. Return is only after COMPLETED and within seven calendar days in V1; item-level, partial and full quantities are supported; exchange/replacement is excluded.
19. Admin review and QC are required before a physical returned item is finally accepted for refund.
20. Cumulative refund cannot exceed the actually paid eligible amount; a failed retry cannot create additional refund value.
21. Notification failure does not roll back the primary Order/Payment business effect where the approved rule applies.

### Invariants intentionally not completed

Stock reservation/deduction/release, canonical SKU uniqueness, exact cancellation/return request states, QC/restock criteria, payout access/change behavior, actor/event guards, and complete compensation/recovery rules remain TBD and are not silently inferred.

## 9. Important state concepts

### 9.1 Order states

The following source-supported Order states and allowed next-state edges are modeled without adding transitions:

| Current state | Allowed next states | Notes |
| --- | --- | --- |
| PENDING | PAYMENT_PENDING, CONFIRMED, CANCELLED | Newly created/waiting for confirmation or payment processing. |
| PAYMENT_PENDING | PAID, EXPIRED, FAILED, CANCELLED | Waiting for online payment. |
| PAID | CONFIRMED, PROCESSING, CANCELLED | Order state `PAID` is distinct from Payment transaction `PAID`. |
| CONFIRMED | PROCESSING, CANCELLED | Confirmed Order. |
| PROCESSING | SHIPPED, CANCELLED | Being processed; customer cancellation eligibility is separately limited by DEC-034. |
| SHIPPED | COMPLETED | Being delivered. |
| COMPLETED | None | Terminal. Return is a separate process allowed by DEC-034. |
| CANCELLED | None | Terminal; later payment must not revive it. |
| EXPIRED | None | Terminal; an expired Payment Attempt is not revived. |
| FAILED | None | Terminal. |

Exact actor/event guards, payment-to-Order mapping and races remain TBD under OPEN-018 and related residual questions.

### 9.2 Cart merge states

The business operation has two outcomes: complete success or complete failure. A failure identifies a conflict, leaves the User Cart unchanged by that attempt and leaves the Guest Cart available for correction/retry. No partial merge state is approved for V1.

### 9.3 Payment concepts

- Online Payment Attempt: pending/available for confirmation, `PAID` only after authoritative exact match, `EXPIRED` after 15 minutes, or an exception outcome for abnormal/unmatched/late money. The full Payment state machine is TBD.
- COD Payment: unpaid/pending collection until collection is confirmed; failed collection is not `PAID`.
- Payment Exception: administrative reconciliation concept for underpayment, overpayment, unmatched and late money.

### 9.4 Cancellation, return and refund concepts

- Cancellation: eligible Order states are PENDING, PAYMENT_PENDING, PAID and CONFIRMED; whole Order only; reason required; paid/confirmed requests require review; rejection includes a reason.
- Return: after COMPLETED and within seven calendar days; item/quantity/reason/description required; evidence optional; admin review and QC required; detailed request states remain TBD.
- Refund: `REFUND_PENDING`, `PROCESSING`, `COMPLETED` and `FAILED` are approved state concepts; failed refund requires admin attention and may be retried without excess amount.

### 9.5 Notification and audit concepts

Notification read/unread and delivery success/failure are confirmed concepts. Audit records important actions. Exact state names, delivery events, retention and reader permissions remain TBD.

## 10. Cross-domain interactions

These are logical business interactions, not API or event definitions.

1. Identity/Customer → Cart: a guest uses a Guest Session; login establishes an authenticated User context for atomic Cart merge.
2. Catalog/Product/Variant → Cart: Cart add and merge validate active purchasable Variant, quantity and stock according to approved policy.
3. Cart/Customer → Checkout: authenticated Checkout evaluates current address, Cart, Product/Variant, stock and price.
4. Checkout → Order/Money: a valid Checkout creates an Order with purchase snapshots and a whole-VND authoritative amount; partial creation is not a successful product outcome.
5. Order → Payment: Order and Payment are related but distinct lifecycles. COD and SePay/VietQR follow their different confirmation meanings.
6. Payment → Order: authoritative confirmation may advance relevant business state; duplicate/late/abnormal payment cannot repeat effects or revive a terminal Order.
7. Order → Cancellation/Return/Refund: post-purchase requests reference Order/Order Items and are constrained by ownership, state, window, quantity, review and QC.
8. Return/QC → Refund/Inventory: approved QC permits refund eligibility; restock and stock mutation behavior remain TBD.
9. Wardrobe/AI/Catalog: AI uses owned Wardrobe context and real Catalog references; output provenance remains explicit and purchasable results must be validated.
10. Business actions → Notification/Audit: important actions may create notifications and audit evidence; delivery/retention/access contracts remain TBD.

## 11. Traceability

| Logical area | BRD/PRD traceability | Approved decisions and remaining questions |
| --- | --- | --- |
| Identity and Customer | BRD §§5, 8.1; PRD-AUTH-001 through PRD-AUTH-006; FCT-005 through FCT-010 | DEC-026, DEC-027; OPEN-004 through OPEN-007, OPEN-057 through OPEN-059 remain detailed policy/security work. |
| Catalog/Product/Variant/Inventory | BRD §§6.2, 8.2; PRD-CAT-001 through PRD-CAT-006; DOM-003 through DOM-007 | DEC-027, DEC-032; OPEN-008, OPEN-011, OPEN-050 and OPEN-052 remain semantics/ownership work. |
| Cart | BRD §8.3; PRD-CART-001 through PRD-CART-006; FR-012 through FR-015; BR-009 through BR-011 | DEC-030; OPEN-009 partial, OPEN-010 resolved for V1 product policy. |
| Checkout/Order | BRD §8.4; PRD-CHK-001 through PRD-CHK-006; PRD-ORD-001 through PRD-ORD-005 | DEC-031; OPEN-011/014 partial and OPEN-054 remain consistency/contract work. |
| Money/Payment | BRD §8.4; PRD-PAY-001 through PRD-PAY-008; BR-015 through BR-022 | DEC-032, DEC-033; OPEN-015/016/018/019 retain operational/protocol detail. |
| Cancellation/Return/Refund | BRD §8.5; PRD-ORD-004, PRD-RET-001 through PRD-RET-003, PRD-REF-001, PRD-REF-002 | DEC-034; OPEN-021/023/024/025/027 partial, OPEN-026 resolved for V1 product policy. |
| Wardrobe | BRD §§6.3, 8.6; PRD-WRD-001 through PRD-WRD-010; DOM-019 | DEC-028; OPEN-033/034 and OPEN-051/052 retain data/ownership/security detail. |
| AI Stylist | BRD §§6.4, 8.7; PRD-AI-001 through PRD-AI-015; DOM-017/018 | DEC-026, DEC-029; DR-011, DR-013, DR-014, DR-015 and OPEN-040 through OPEN-049 remain later work. |
| Notification/Admin/Audit | BRD §8.8; PRD-NOT-001/002, PRD-ADM-001 through PRD-ADM-004; DOM-014 through DOM-016 | Existing confirmed baseline; DR-018, OPEN-028, OPEN-030, OPEN-031 remain detailed contract/operational work. |

The candidate DOM identifiers are analytical references from discovery. They are not service names, bounded contexts or ownership approvals.

## 12. Open and TBD items

The following remain explicitly unresolved after the approved V1 baseline:

- DR-003: identity lifecycle, authorization matrix, token/session behavior and sensitive-resource access.
- DR-004: canonical Product/Variant/SKU semantics, category lifecycle, snapshots and history.
- DR-011: style, brand, fit, body and color vocabulary and precedence.
- DR-013: retrieval, knowledge authority, product synchronization, freshness and deletion behavior.
- DR-014: travel/visual/3D experience depth and milestone; not required to invent V1 entities.
- DR-015: consent, retention, deletion, memory and sensitive-data behavior.
- DR-016: final contexts, data ownership and integration architecture.
- DR-017: measurable quality, accessibility, recovery and operating targets.
- DR-018: operational evidence, notifications, reports and audit details.

Residual detail from resolved product clusters remains TBD: OPEN-009, OPEN-011, OPEN-014, OPEN-015, OPEN-016, OPEN-018, OPEN-019, OPEN-021, OPEN-023, OPEN-024, OPEN-025 and OPEN-027. These are not treated as answered by this model.

## 13. Explicit non-goals

This Domain Model does not create or decide:

- SQL tables, columns, foreign keys, indexes, normalization, JPA mappings, database engine or UUID strategy.
- Microservice decomposition, bounded-context finalization, database-per-service ownership or deployment topology.
- REST/GraphQL endpoints, request/response contracts, event contracts, Kafka/RabbitMQ or asynchronous delivery.
- JWT/key lifecycle, permission implementation, threat controls or security architecture.
- AI model/provider, prompt/runtime, vector/graph technology ownership, latency, cost or evaluation thresholds.
- Carrier/bank/webhook protocol, payout integration or persistence mechanism.
- Exact SKU/category/size/color vocabulary, QC checklist, request-state names or unapproved transitions.
- Automatic wardrobe recognition, segmentation, body scanning, photorealistic try-on, live weather or packing optimization.
- A one-domain-one-service design. The logical domains and candidate DOM identifiers do not authorize implementation boundaries.

The human owner approved this Domain Model through DEC-036 on 2026-09-22. It remains a logical business model and is not itself a database schema or service map. DEC-036 authorizes an implementation mapping of its slices, while preserving later contract and implementation traceability.
