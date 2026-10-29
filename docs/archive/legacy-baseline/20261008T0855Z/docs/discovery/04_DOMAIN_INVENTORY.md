# StyleMind Candidate Domain Inventory

This is domain analysis, not a service catalog or final bounded-context map. DOM identifiers are analytical grouping labels. Each grouping is DERIVED from the cited source concepts; its known responsibilities/data are source-supported unless marked TBD. S01's named owner is CONFIRMED as source evidence. Final ownership is TBD for every grouping because S03 section 9 explicitly requires boundary and data-ownership analysis. A candidate owner below is an existing source name or TBD, never a newly proposed microservice.

## Commerce and customer domains

| ID and name | Purpose and known responsibilities | Known data | Known integrations | Candidate owner from source | Ownership status |
| --- | --- | --- | --- | --- | --- |
| DOM-001 Identity | Register/verify OTP, authenticate/reset and assign roles/account status; S01 section 2 | Email, password hash, temporary registration, OTP hash, role/status, JWT claims | Email notification; gateway/downstream JWT verification; internal tokens | auth-service in S01 P025, P042, P048, P082 | TBD |
| DOM-002 Customer | Maintain basic profile, own shipping addresses and supply purchase address snapshot; S01 section 3 | userId, basic profile, recipient/address, phone, province/ward, default marker | Identity userId; order snapshot request; available Vietnam administrative data | user-service in S01 P079-099 | TBD |
| DOM-003 Catalog | Organize categories and support public category/search/filter access; S01 section 4 | Category name/slug; conditional hierarchy; filter values | Products; admin management | TBD; S01 does not explicitly allocate categories to a named service | TBD |
| DOM-004 Product | Manage products/status/images and provide product information; S01 sections 4, 15.3 | Name, price information, images, category, variants, status; S03 adds brand | Cart/order snapshots; AI hydration/index synchronization | product-service in S01 P155-156, P459, P467 | TBD |
| DOM-005 Brand | Support product brand information and conditional styling preference; S03 section 4 | Brand information/preference; vocabulary and charts TBD | Product and AI Stylist at product-direction level; interaction contracts TBD | TBD; no brand owner supplied | TBD |
| DOM-006 Variant | Represent selectable product option and expose validation/snapshot attributes; S01 section 4.4 | SKU, size, color, material, stock, optional override price, status | Product; cart/order variant validation | product-service in S01 P155-156, P186 | TBD |
| DOM-007 Inventory | Expose stock for cart/checkout and participate in potential post-QC restock; S01 P150, P189, P207, P374 | Variant stock; reservation/ledger model TBD | Variant snapshots; checkout validation; return QC dependency | TBD; snapshot exposure by product-service does not establish inventory mutation ownership | TBD |
| DOM-008 Cart | Maintain guest/user carts, items and merge; S01 section 5 | Client guest session id, userId, variant reference, quantity, display price | Product validation; order cart retrieval; frontend login/merge | cart-service in S01 P163, P174, P186, P197 | TBD |
| DOM-009 Order | Coordinate checkout, preserve snapshots, enforce states, expose order history and receipt-image capability; S01 sections 6, 8, 10 | Order, items, price_at_purchase, address/recipient snapshot, totals, state, receipt images | Cart, Customer, Product, Payment, Notification; cancellation/return relationships | order-service in S01 P211-225, P488-496 | TBD |
| DOM-010 Payment | Initiate COD/SePay transactions, provide QR data and reconcile webhook; S01 section 7 | Transaction, amount, transfer content, QR URL, provider event, gateway transaction id, status | SePay; Order callbacks; refund relationship | payment-service in S01 P243, P257-280 | TBD |
| DOM-011 Cancellation | Accept eligible customer requests/reasons and admin cancellation/review; S01 section 9 | Order reference, reason; request state/reviewer representation TBD | Order state machine; Payment/Refund effects | TBD; no independent cancellation owner is established | TBD |
| DOM-012 Return | Check eligibility, receive item requests/evidence, review/QC and shipment/payout information; S01 section 11 | Order/item reference, quantity, reason, description, evidence, QC, carrier/tracking, payout destination | Order/purchase data; Refund/Payment; restock decision | Source mentions return/order-service in S01 P384; exact meaning TBD | TBD |
| DOM-013 Refund | Create internal refund requests and record complete/fail outcomes with valid-amount constraints; S01 section 12 | Order link, amount, refund status, return-linked payout information | Payment and return/order-service named in S01 P384; actor admin | Source names payment-service and return/order-service jointly; owner TBD | TBD |
| DOM-014 Notification | Store/send notifications/email, expose customer read state and admin logs/summary/retry; S01 section 13 | Notification, read status, send log, success/failure | Auth/order requests; business notification events; mail delivery | notification-service in S01 P397, P496 | TBD |
| DOM-015 Administration and reporting | Provide admin operations and operational dashboard; S01 section 14, P542-551 | User/catalog/order/payment/return/notification summary views; formulas TBD | Source domains; no reporting transport or warehouse specified | TBD; ADMIN is an actor, not a service boundary | TBD |
| DOM-016 Audit | Trace important operations and reconciliation evidence; S01 section 18 | Role/status changes, product actions, order transitions, webhook receipt, delivery and return/refund actions | Participating domains; no centralized audit service or event schema specified | TBD; producers are named as business operations only | TBD |

## Styling domains

| ID and name | Purpose and known responsibilities | Known data | Known integrations | Candidate owner from source | Ownership status |
| --- | --- | --- | --- | --- | --- |
| DOM-017 AI Stylist | Maintain chat, interpret needs, retrieve products, select/explain outfits and audit preference memory; S01 sections 15.1-15.3; S03 sections 3-7, 11 | Session/messages, needs/preferences, budget, outfit response; structured style/fit profile TBD | Qdrant, Neo4j and Product hydration in source; owned-item and travel relationships are directions | AI service in S01 P468, P497; exact scope/name TBD | TBD |
| DOM-018 Fashion Knowledge | Ingest text/URL, extract concepts/rules, stage draft, approve and synchronize deletion; S01 section 15.4 | Source, text/URL, concepts/rules, draft/approval; provenance fields suggested, not specified | Admin review; Qdrant and Neo4j upsert; URL scraping described | TBD; association with AI workflow does not prove storage/review ownership | TBD |
| DOM-019 Smart Wardrobe | Let styling consider clothes already owned and additional catalog products; S03 section 3 | Owned clothes and catalog relationship; item fields/images/history TBD | AI Stylist and catalog at product-direction level; no service contract | TBD | TBD |
| DOM-020 Travel Styling | Support travel and trip-duration-aware outfit planning; S02 Product Focus; S03 section 7; S05 section 2 | Trip duration; destination/activity/occasion/reuse are candidate context; plan model TBD | AI Stylist, Wardrobe and catalog relationships are intended; weather is unapproved | TBD | TBD |

## Cross-domain ownership audit

The following findings are DERIVED analytical gaps, not architecture decisions.

| Dependency | Confirmed evidence | Unresolved ownership or contract | Review path |
| --- | --- | --- | --- |
| Identity to Customer | userId link and explicit S01 identity/profile separation | Lifecycle propagation on lock/delete/role change, and final context ownership | OPEN-005, OPEN-052, OPEN-058 |
| Catalog to Product to Variant to Inventory | Variant snapshot contains stock, price and metadata | Aggregate boundaries, SKU uniqueness, inventory mutations, concurrency | OPEN-008, OPEN-011, OPEN-050, OPEN-052 |
| Order to Payment | Payment creation and callback in S01 | Atomicity, retries, payment/order race ownership, compensation | OPEN-014, OPEN-016, OPEN-018, OPEN-054 |
| Return to Refund to Inventory | QC decides refund/restock; refund spans payment and return/order names | Return state owner, payout settlement, repeated refund/restock prevention | OPEN-024, OPEN-026, OPEN-027 |
| Product to AI to Fashion Knowledge | Source vector sync, graph rules and product hydration | Source-of-truth versus index, update ordering, source deletion and stale-result behavior | OPEN-040, OPEN-042, OPEN-043, OPEN-044 |
| Customer to Wardrobe to AI | Style/fit, owned clothes and catalog personalization | Ownership of style/body data, owned-item schema and access/deletion policy | OPEN-032 through OPEN-039, OPEN-051 |
| Color across product metadata/rules/graph/reasoning | Explicitly unresolved in S03 section 6 | Canonical color vocabulary, authoritative matching rules and decision responsibility | OPEN-046 |
| Notifications and audit across commerce | Failure logging, retry and important-action traceability | Delivery/retention guarantees, authorized readers, participating transaction effects | OPEN-028, OPEN-031, OPEN-054, OPEN-055 |

No group in this inventory authorizes a one-domain-one-service design. Data tables, endpoints, event schemas and final bounded contexts remain absent by design.

## Human Decision Baseline V1 overlay

DEC-028 approves a bounded V1 Smart Wardrobe capability for DOM-019: CRUD, personal clothing image upload, basic metadata, visual/category browsing and AI context integration, with a clear owned-versus-Shop distinction. This does not assign DOM-019 to a service, finalize its data model or resolve OPEN-052. DEC-029 bounds the AI Stylist product promises and acceptance approach for DOM-017 while leaving runtime/provider and pipeline ownership TBD. All final ownership, context boundaries and technical integrations remain deferred.

## Logical Domain Model overlay

The logical model is recorded in [docs/domain/01_DOMAIN_MODEL.md](../domain/01_DOMAIN_MODEL.md) with review in [01_DOMAIN_MODEL_REVIEW.md](../domain/01_DOMAIN_MODEL_REVIEW.md). It uses these candidate domains as analytical evidence, preserves unresolved ownership and does not convert any DOM identifier into a service or bounded context.

DEC-035 approves infrastructure responsibilities that can support these domains, but it does not change this rule: final context boundaries, data owners and service assignments remain later architecture work.
