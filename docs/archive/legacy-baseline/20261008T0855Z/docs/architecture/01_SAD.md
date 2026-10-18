# StyleMind System Architecture Description — V1 Baseline

Document status: HUMAN-APPROVED

Document type: System Architecture Description (SAD) baseline

Decision authority: Phan Đoàn Thế Quân — Solo Owner of StyleMind

Architecture basis: [DEC-035](../decisions/DEC-035.md), the human-approved BRD/PRD baseline, and the logical [Domain Model](../domain/01_DOMAIN_MODEL.md).

This document describes approved architecture responsibilities and integration principles. It is not a database schema, API specification, event contract, final bounded-context map or implementation plan.

## 1. Purpose and scope

The SAD establishes how the approved V1 infrastructure components participate in StyleMind and how authoritative business data is separated from transient support, retrieval indexes, knowledge relationships and asynchronous integration.

It covers:

- authority and ownership principles;
- the approved PostgreSQL, Redis, Kafka, Qdrant, Neo4j and Docker Compose responsibilities;
- the high-level hybrid REST/Kafka communication model;
- logical interaction slices for Commerce Core, Smart Wardrobe, AI Stylist and supporting capabilities;
- data-authority and integration-flow rules;
- reliability and grounding constraints;
- explicit deferred architecture work.

It does not cover:

- final service count, service names or bounded contexts;
- SQL tables, columns, indexes, migrations or database-per-service allocation;
- REST endpoints, request/response schemas, Kafka topics or event payloads;
- authentication, authorization, secrets, consent, retention or threat controls;
- AI model/provider, prompt/runtime, latency, cost or evaluation thresholds;
- production hosting, Kubernetes, cloud topology or SLO/SLA targets;
- implementation code.

## 2. Architectural authority and constraints

The following constraints are human-approved through DEC-035 and must be preserved in later specifications:

1. PostgreSQL is the authoritative transactional system of record for core business data.
2. A service owns and mutates its own persistence boundary; services do not directly mutate another service's database.
3. Redis is ephemeral or distributed-support infrastructure only. It is not authority for Orders, Payments, Products, Inventory, Returns, Refunds or Wardrobe.
4. Kafka is the approved asynchronous integration/event backbone. Its presence does not justify publishing every internal state change.
5. REST and Kafka are complementary. Immediate user-facing validation and request/response behavior may remain synchronous; integration side effects and propagation may be asynchronous.
6. A transactional outbox is the preferred pattern when a state change and Kafka publication must be made reliable together. Unsafe dual-write behavior is not the baseline.
7. Qdrant is a vector retrieval/index layer, not authoritative Catalog storage.
8. Neo4j is a fashion relationship/knowledge-graph layer, not authoritative Catalog storage.
9. AI output that recommends purchasable products must be deterministically grounded against authoritative Catalog data.
10. Docker Compose is the approved local-development baseline. Kubernetes and production deployment choices are deferred.

## 3. Logical architecture view

The following are logical responsibility slices, not an approved final service decomposition. A later Context Map and Data Ownership Matrix must decide whether a slice is one service, several services or a responsibility shared through an explicitly owned contract.

### 3.1 Identity, Customer and access-sensitive context

This slice supports account, customer profile, addresses, role/access-sensitive behavior and user context needed by Commerce and AI. Identity lifecycle, permission matrices, token/session behavior and sensitive-data controls remain deferred under DR-003 and DR-015.

### 3.2 Catalog and Inventory context

This slice covers authoritative Products, Variants and inventory-related transactional information used by browsing, cart validation and checkout. Exact Product/Variant/SKU semantics, stock mutation authority, reservation behavior and ownership remain deferred under DR-004, OPEN-008, OPEN-011, OPEN-050, OPEN-052 and OPEN-054.

Qdrant may provide retrieval candidates and Neo4j may provide relationship context, but authoritative Catalog validation remains mandatory before a product is treated as purchasable.

### 3.3 Cart, Checkout and Order context

This slice coordinates the approved product behavior: atomic Guest-to-User Cart merge, product-level checkout revalidation, authoritative totals and recoverable failure behavior. It must use authoritative Catalog and inventory information according to the later ownership/consistency specification. Exact transaction and compensation boundaries remain TBD.

### 3.4 Payment context

This slice supports SePay/VietQR and COD behavior within the approved payment rules. Payment confirmation must be authoritative and exact-match for online payment; a late or abnormal payment must not silently revive a terminal Order. Payment/order race ownership, webhook contract, expiry coordination and operational details remain TBD.

### 3.5 Return, Refund and post-purchase context

This slice supports the approved whole-order cancellation and Completed-only return/refund behavior, including admin review and QC. Return ownership, refund settlement, verified destination controls, inventory restock behavior and concurrency details remain deferred.

### 3.6 Smart Wardrobe context

This slice supports manual Wardrobe CRUD, personal clothing image upload, basic metadata, visual/category browsing and AI context integration. It must preserve the distinction between `YOU ALREADY OWN` and `RECOMMENDED TO BUY`. Image lifecycle, sensitive data, deletion, ownership and access policy remain deferred.

### 3.7 AI Stylist and fashion knowledge context

This slice assembles user context, Wardrobe context, authoritative Catalog filters, Qdrant retrieval and Neo4j fashion relationships for structured AI reasoning. The detailed AI specification must define vocabulary, precedence, freshness, synchronization, safety, evaluation and failure behavior.

### 3.8 Notification, Administration and Audit context

This slice supports user/admin notifications, administration, operational evidence and auditability. Delivery guarantees, retry evidence, authorized readers, retention and exact triggers remain deferred.

## 4. Data authority matrix

| Component | Approved role | Authority boundary | Must not be used as |
| --- | --- | --- | --- |
| PostgreSQL | Authoritative transactional persistence for core business data | The owning service's committed business state | A reason for direct cross-service database mutation |
| Redis | Ephemeral/distributed support: OTP TTL, rate limiting, cache, useful idempotency keys, short-lived reservation if later required, payment/session expiry | Temporary support state only | Authority for Orders, Payments, Products, Inventory, Returns, Refunds or Wardrobe |
| Kafka | Asynchronous integration/event backbone | Transport and propagation of approved integration events | A system of record or a replacement for transactional ownership |
| Qdrant | Vector retrieval/index for semantic Catalog retrieval, style similarity, embeddings and AI retrieval | Search/retrieval candidates | Authoritative Catalog or purchasable truth |
| Neo4j | Fashion relationship and knowledge graph | Relationship/knowledge context for garment types, styles, colors, occasions, materials, silhouettes, coordination and compatibility | Authoritative transactional Catalog |
| LLM/runtime | Structured reasoning over supplied context | Non-authoritative candidate generation | Invented Product, Variant, price, stock or purchase truth |
| Transactional outbox | Reliable handoff from owned transactional state to Kafka publication | Pending publication evidence owned with the relevant transaction | An independent business source of truth |

The exact database allocation, consistency model, index synchronization and lifecycle controls are not decided by this matrix.

## 5. Communication model

### 5.1 Synchronous REST responsibilities

REST is the default candidate for:

- user-facing queries requiring an immediate response;
- immediate input and business validation;
- commands where the caller needs an immediate accepted/rejected result;
- critical request/response coordination such as checkout validation and payment initiation;
- authoritative reads needed before a user-visible commerce decision.

The endpoint set, payloads, status/error model and authentication requirements remain contract work.

### 5.2 Kafka responsibilities

Kafka is the default candidate for:

- meaningful domain/integration events;
- asynchronous side effects;
- cross-service state propagation;
- notification triggers;
- Catalog and AI retrieval-index updates;
- Wardrobe and other downstream integrations where eventual consistency is acceptable and explicitly specified.

The exact event set is not approved by this document. Candidate names carried forward from DEC-035 are `ProductCreated`, `ProductUpdated`, `VariantUpdated`, `InventoryChanged`, `InventoryReserved`, `InventoryReleased`, `OrderCreated`, `OrderConfirmed`, `OrderCancelled`, `OrderCompleted`, `PaymentConfirmed`, `PaymentExpired`, `PaymentFailed`, `ReturnApproved`, `RefundRequested`, `RefundCompleted`, `WardrobeItemCreated` and `WardrobeItemUpdated`.

Each candidate requires a later integration-need analysis. Payload, version, key, ordering, delivery, retry, idempotency, replay, dead-letter and consumer ownership are TBD.

### 5.3 Database plus event reliability

When a business transaction must also publish an integration event, the preferred pattern is:

`owned transactional state change + outbox record in the same PostgreSQL transaction -> relay/publisher -> Kafka -> idempotent consumers`

The pattern is an architectural reliability direction, not an approved schema or implementation. Relay failure, outbox retention, replay and operational evidence require later specification.

## 6. High-level flow narratives

These narratives describe responsibility and authority, not endpoint or event contracts.

### 6.1 Catalog retrieval to cart and checkout

1. A user browses or searches for a Product/Variant through a synchronous user-facing path.
2. Retrieval may use authoritative Catalog reads and, where appropriate, Qdrant candidates.
3. Any Qdrant candidate is resolved against authoritative Catalog data before it is presented as purchasable.
4. Cart and checkout revalidate authoritative product, variant, activity, quantity, stock and price information according to DEC-030 and DEC-031.
5. The owning transaction commits the accepted change in its PostgreSQL authority. Any required downstream propagation uses an approved integration event and does not mutate another service's database directly.

### 6.2 Checkout to Order and Payment

1. Checkout performs the approved product-level revalidation and returns an immediate success, failure or reconfirmation outcome.
2. Order creation and payment initiation use their authoritative transactional responsibilities; exact ownership and transaction boundaries remain TBD.
3. Online payment confirmation is accepted only from the later-defined authoritative confirmation mechanism and exact-match rules in DEC-033.
4. Asynchronous downstream effects such as notification or indexing may use Kafka after reliable publication from owned state.
5. Terminal Order protection, duplicate delivery handling and payment/order race behavior must be specified before implementation.

### 6.3 Cancellation, Return and Refund

1. Cancellation and return eligibility are checked synchronously against authoritative Order state and the approved DEC-034 policy.
2. Admin review and QC produce the required decision/evidence before refund or restock effects.
3. Refund and inventory effects use their owning transactional authorities; no Redis, Qdrant, Neo4j or Kafka record replaces the authoritative business state.
4. Notifications and audit propagation may be asynchronous when the later contract defines acceptable eventual consistency.

### 6.4 Wardrobe and AI Stylist

1. User context and Wardrobe items are read according to the later identity, access, consent and lifecycle rules.
2. Authoritative Catalog filters are applied before or alongside semantic retrieval.
3. Qdrant supplies vector retrieval candidates and Neo4j supplies fashion relationships where available.
4. The system builds a structured AI context; the LLM produces a structured outfit candidate rather than authoritative product facts.
5. Deterministic Catalog grounding resolves every purchasable candidate to a real Product/Variant and validates current commerce data before display.
6. The result preserves owned Wardrobe items versus products recommended to buy and obeys approved hard constraints.

### 6.5 Notifications and audit

Meaningful business events may trigger notifications and audit evidence through Kafka-based integration. The authoritative transaction remains in its owning PostgreSQL boundary. Delivery, retry, retention, access and failure evidence are deferred to contracts/security/operations specifications.

## 7. Consistency and reliability principles

- No service directly writes another service's database.
- PostgreSQL remains authoritative for core transactional facts.
- Redis entries may expire, be evicted or be rebuilt without becoming business truth.
- Kafka consumers must be designed for duplicate and delayed delivery where the later contract permits asynchronous processing.
- The outbox direction must prevent an accepted transactional state change from being silently lost before its required integration publication.
- Event publication must be driven by an identified integration need, not by infrastructure availability.
- Search/index/graph staleness must never bypass authoritative Catalog validation for purchasable display or purchase.
- LLM output is untrusted until deterministic Catalog grounding and the approved business constraints validate it.
- Failure handling, compensation, idempotency keys, retry limits and operational evidence remain explicit downstream work.

## 8. Local development baseline

Docker Compose is the local-development baseline. The intended environment, as implementation progresses, includes:

- PostgreSQL;
- Redis;
- Apache Kafka and the minimum supporting components required to run it locally;
- Neo4j;
- Qdrant;
- StyleMind services as they are implemented.

This list does not establish production topology, resource limits, network policy, secrets management, observability stack, backup policy or deployment automation. Those are TBD.

## 9. Deferred architecture work

The following remain explicit gates and are not silently answered here:

| Item | Status | Why it remains open |
| --- | --- | --- |
| DR-003 | SECURITY-SPEC-BLOCKING; CONTRACT-BLOCKING | Identity lifecycle, authorization, token/session and sensitive-resource behavior |
| DR-004 | CONTRACT-BLOCKING; SAD refinement | Product/Variant/SKU semantics, snapshots, history and ownership |
| DR-011 | AI-SPEC-BLOCKING | Style, brand, fit, body and color vocabulary and precedence |
| DR-013 | AI-SPEC-BLOCKING; SAD refinement | Retrieval, knowledge authority, hydration, freshness and synchronization |
| DR-014 | NONBLOCKING / MAY REMAIN TBD | Travel, visual and 3D depth not required for current approved baseline |
| DR-015 | SECURITY-SPEC-BLOCKING | Consent, retention, deletion, sensitive data and AI trust controls |
| DR-016 | SAD-BLOCKING for final map | Final contexts, data ownership and integration responsibilities |
| DR-017 | SAD/IMPLEMENTATION-BLOCKING | Operating, accessibility, recovery and measurable quality targets |
| DR-018 | CONTRACT-BLOCKING | Notification, audit, reporting, delivery and permission evidence |
| OPEN-052 | SAD-BLOCKING for final ownership | Context and data-owner decisions |
| OPEN-053 | SAD refinement | React frontend architecture and supporting libraries |
| OPEN-054 | SAD/consistency refinement | Failure and compensation matrix for stock, checkout and payment |
| OPEN-055 | CONTRACT-BLOCKING | Sync/async guarantees, retry, ordering and delivery semantics |
| OPEN-056 | SAD/operations-blocking | Availability, performance, recovery and operating targets |
| OPEN-057 through OPEN-060 | SECURITY/AI-SPEC-BLOCKING | Threat, consent, privacy, grounding and AI safety behavior |
| OPEN-040, OPEN-042 through OPEN-044 | AI-SPEC-BLOCKING | Knowledge authority, sync, freshness, deletion and graph responsibilities |

## 10. Traceability

- Human-approved architecture: [DEC-035](../decisions/DEC-035.md).
- Existing technology and architecture facts/decisions: ARCH-001 through ARCH-010 and DEC-016 through DEC-024 in [the discovery register](../discovery/09_DECISION_REGISTER.md).
- Product and acceptance baseline: [BRD](../requirements/01_BRD.md), [PRD](../requirements/02_PRD.md), DEC-026 through DEC-034.
- Logical domain basis: [Domain Model](../domain/01_DOMAIN_MODEL.md) and [Domain Model Review](../domain/01_DOMAIN_MODEL_REVIEW.md).
- Requirement-to-domain seed: [Traceability Seed](../discovery/11_TRACEABILITY_SEED.md).

All Service, API and Test mappings remain TBD until the relevant contracts and test strategy are approved.

## 11. Architecture status

This SAD was approved by the human owner through DEC-036 on 2026-09-22 as a constrained V1 architecture baseline. Approval does not silently answer the deferred service-map refinements, API/event contracts, security specification, AI specification, test strategy or production operations; those are now implementation work under the approved baseline.
