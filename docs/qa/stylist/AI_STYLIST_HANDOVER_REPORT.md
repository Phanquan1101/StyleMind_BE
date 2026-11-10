# AI Stylist — handover report

**Verification date:** 2026-11-07<br>
**Branch / HEAD:** feature/ep-07-ai-stylist / c9f20d1d1fc1779eca735b3de3ac9f1745b43985<br>
**Overall status:** Shop-only pipeline verified locally, including one approved positive Gemini Gateway run. EP-07, US-07.01 and US-07.02 remain **PARTIAL / INCOMPLETE**.

This report records evidence for the current StyleMind workspace. The worktree already had staged, unstaged and untracked changes when this task began; they were preserved. No commit, push, pull, merge, workbook edit, external-service source edit or persistent database change was made.

## Delivered behavior

The implemented shop flow accepts a customer's needs and constraints. The customer does not select products or submit a prebuilt outfit. Stylist retrieves eligible candidates, hydrates them from Commerce, applies reviewed knowledge and the deterministic P02 engine, asks the selected model to choose only from backend-issued aliases, validates the response, rechecks current Commerce data, then atomically saves the recommendation and its two items.

Every generated outfit contains exactly one women's TOP and one women's BOTTOM. The engine retains exact VND budget checks, hard exclusions, unknown-metadata handling, product/variant whitelist checks, current status/price checks and request ownership. The model cannot choose a new product, slot, price, source or URL. The live run did not create an assistant chat message; the database contained zero assistant messages.

## APIs and service flow

The Gateway's existing /stylist/** route strips the first path segment. With the local Gateway at http://localhost:8080, the importable collection calls:

| Method | Gateway path | Purpose |
|---|---|---|
| POST | /stylist/api/v1/stylist/sessions | Create a session |
| GET | /stylist/api/v1/stylist/sessions/{sessionId}/messages | Read session history |
| POST | /stylist/api/v1/stylist/sessions/{sessionId}/requests | Validate/store needs and constraints; returns RECEIVED and generation_started=false |
| GET | /stylist/api/v1/stylist/requests/{requestId} | Read request lifecycle |
| POST | /stylist/api/v1/stylist/requests/{requestId}/generate | Run the recommendation pipeline |
| GET | /stylist/api/v1/stylist/requests/{requestId}/recommendations | Read request recommendation list |
| GET | /stylist/api/v1/stylist/recommendations/{recommendationId} | Read a recommendation detail with current Commerce semantics |

The request is owner-scoped from the validated JWT subject. CUSTOMER and ADMIN are both self-only; ADMIN is not a cross-customer bypass. Missing and foreign request/session reads use the same not-found behavior.

The sequence source is [AI_STYLIST_SEQUENCE.mmd](AI_STYLIST_SEQUENCE.mmd).

```mermaid
sequenceDiagram
    actor Customer
    participant Gateway
    participant Stylist as Stylist API and services
    participant PG as Disposable PostgreSQL
    participant Qdrant
    participant Commerce as Commerce HTTP contract fixture
    participant Neo4j
    participant Gemini as Gemini Developer API

    Customer->>Gateway: Create session
    Gateway->>Stylist: POST session
    Stylist->>PG: Persist session
    Customer->>Gateway: Submit needs and constraints
    Gateway->>Stylist: POST request
    Stylist->>PG: Persist request as RECEIVED
    Customer->>Gateway: Generate with empty body
    Gateway->>Stylist: POST generate
    Stylist->>PG: Claim generation; enforce owner and lifecycle
    Stylist->>Gemini: Embed query (1536 dimensions)
    Stylist->>Qdrant: Retrieve synthetic catalog candidates
    Stylist->>Commerce: Hydrate candidate product/variant data
    Stylist->>Neo4j: Read reviewed synthetic styling facts
    Stylist->>Stylist: P02 filter, budget, TOP+BOTTOM, whitelist
    Stylist->>Gemini: Count tokens and structured generation
    Gemini-->>Stylist: Structured response
    Stylist->>Stylist: Parse, validate aliases and hard rules
    Stylist->>Commerce: Final current price/status recheck
    Stylist->>PG: Atomically persist SUCCEEDED + recommendation + 2 items
    Customer->>Gateway: Read list/detail or replay generation
    Gateway->>Stylist: GET or repeated POST generate
    Stylist->>PG: Read persisted result; terminal replay makes no provider call
```

The disposable Gateway run used a fixture JWKS and fixture-signed JWTs, not tokens issued by the live Identity service. Commerce was also an HTTP contract fixture. PostgreSQL, Qdrant and Neo4j were real disposable containers; Gemini was the only live external dependency in the positive run.

### Main classes

- HTTP and validation: StylistSessionController, StylingRequestController, StylistGenerationController, the session/request HTTP converters and parsers, and SessionRequestPolicyFilter.
- Use cases: StylistSessionServiceImpl, StylingRequestServiceImpl, StylistGenerationServiceImpl, GenerationPersistenceServiceImpl, and StylingRecommendationServiceImpl.
- Retrieval and catalog: CandidateRetrievalServiceImpl, CandidateTextBuilder, RuntimeQdrantVectorStore, QdrantHttpVectorStore, CommerceCatalogClient, and CommerceCatalogFeignClient.
- Rules and allowed output: OutfitEngine, CandidatePolicy, BudgetPolicy, OutfitComposer, OutfitScorer, OutfitWhitelist, GenerateRequestParser, and StructuredRecommendationContract.
- Knowledge/provider: Neo4jReviewedKnowledgeService, KnowledgeArtifactImporter, GeminiRuntimeEmbeddingAdapter, GeminiStructuredRecommendationModel, and GeminiDeveloperApiClient.
- Persistence: StylistSessionEntity, StylistMessageEntity, StylingRequestEntity, StylingConstraintEntity, WardrobeRecommendationEntity, RecommendationItemEntity, and their Spring Data repositories.

Flyway V1–V3 remain unchanged. V1 holds sessions/messages, V2 requests/constraints and V3 recommendations/items. They were exercised by PostgreSQL Testcontainers only. This report is not evidence that the persistent stylemind_stylist database has these migrations applied.

## Gemini model and configuration

| Operation | Model and contract |
|---|---|
| Query/document embedding | gemini-embedding-001, synchronous :batchEmbedContents, 1536 dimensions, RETRIEVAL_QUERY / RETRIEVAL_DOCUMENT, adapter normalization |
| Structured generation | gemini-3.5-flash-lite, explicit token-count preflight, MINIMAL thinking, JSON response format/schema, Java parser and whitelist |

Gemini is selected explicitly. Merely having an API key does not switch the application from its default provider. The default live-call flag remains false.

    STYLEMIND_AI_PROVIDER=gemini
    STYLEMIND_GEMINI_GENERATION_MODEL=gemini-3.5-flash-lite
    STYLEMIND_GEMINI_LIVE_CALLS_ENABLED=false

The embedding model and dimension are fixed by the adapter; no embedding-model override is needed. Keep the API key in a local secret source/environment only. It is absent from this report, Postman files and source examples. A live run also requires the bounded local meter proxy; do not set the live flag in an ordinary application or IntelliJ run.

## Verification evidence

### Automated Stylist suite

Command: mvn -B -ntp -f stylist-service/pom.xml verify<br>
Result: **BUILD SUCCESS: 191 tests, 0 failures, 0 errors, 1 skipped**. The single skipped test is live-only and is not counted as a pass. The separate live Gateway evidence below is not a reason to relabel that test as executed.

The suite used disposable PostgreSQL 18.6, Qdrant 1.20.0 and Neo4j 5.26 Testcontainers with random host ports. It did not use Compose, the persistent PostgreSQL database or a shared vector/graph store.

### Offline Gateway and Newman

The Wave 2 collection/environment JSON parsed as Postman v2.1 JSON. All six Postman JSON artifacts in the directory parsed successfully; the Wave 2 collection has 10 requests and its environment contains placeholders only.

The offline disposable harness executed the checked-in Wave 2 collection through the real local Gateway/Stylist HTTP processes using local Gemini, Commerce and JWKS fixtures: **10 requests, 20 assertions, 0 failures**. It exercised generation, separate list/detail DTO validation, strict request validation, budget rejection, replay, foreign-owner denial and the concurrency claim. Same-request concurrency produced one 200 winner and one 409 loser; replay and foreign-owner denial added zero fixture provider calls. These are offline fixture results, not Gemini-live or real Identity/Commerce results.

### Approved positive Gemini Gateway run

The owner had confirmed that the selected Google project was Free Tier with billing disabled. The test did not independently inspect billing-console records. All inputs sent to Gemini in this run were synthetic. No OpenAI call, retry or fallback occurred.

| Metered operation | Before | Added in this run | Final / cap | Live result |
|---|---:|---:|---:|---|
| Embedding | 9 | 3 | 12 / 12 | 3 HTTP 200; each returned 1536 dimensions; query and document task types were correct |
| countTokens | 19 | 4 | 23 / 23 | 4 HTTP 200; generation preflight counted 304 input tokens |
| Generation | 5 | 1 | 6 / 6 | HTTP 200; 3.774 s; 304 prompt tokens, 157 output tokens |

The meter recorded **0 blocked attempts** and did not stop on 429. For all three live vectors the production adapter accepted the response and the pipeline continued; the safe meter artifact records dimension and request metadata, not raw vector values. Finite-value/normalization checks are also covered by adapter tests. Before the successful run, one local TLS-certificate setup attempt failed before provider dispatch and one harness invocation used the offline fixture mode; neither reached Gemini or changed the cumulative meter. No provider operation was retried. The meter's transport diagnostics include one local TLS handshake error, while the eight metered provider operations all returned HTTP 200.

The positive run went through the local Gateway and real Stylist. It made seven Gateway HTTP requests:

- Session creation: 201.
- Request intake: 201, state RECEIVED.
- Generation: 200, state SUCCEEDED, model gemini-3.5-flash-lite, one recommendation with exactly TOP and BOTTOM.
- Recommendation list: 200; comparator validated the list DTO by its own contract.
- Recommendation detail: 200; current Commerce fixture total was 550,000 VND.
- Terminal replay: 200 and **0 additional provider calls**.
- Owner B access attempt: 404 and **0 additional provider calls**.

PostgreSQL contained one succeeded request, one recommendation and two recommendation items; assistant-message rows remained zero. The comparator recorded known DTO-shape paths $.limitations and $.recommendations[*].items[*].wardrobe_item_id as shape differences between distinct contracts; its business-invariant failure-path list was empty. No production DTO or public API was changed to force the response shapes to match.

The live run used the actual Gateway, Stylist, PostgreSQL 18, Qdrant v1.20.0 and Neo4j 5.26 disposable resources. Commerce and JWKS remained local fixtures. The API subject tokens were fixture-signed. The exact containers were removed by the run; a subsequent read-only docker ps -a check found no containers with the run's stylemind-wave2- prefix. No volume was created or removed and no persistent database was accessed.

Safe local evidence is in the ignored target directory:

- stylist-service/target/ai-stylist-handover-offline-20261107/gateway-newman-summary.json
- stylist-service/target/ai-stylist-handover-live-20261107/live-run-report-safe.json

The meter now stands at its approved profile cap: **12/12 embeddings, 23/23 countTokens and 6/6 generations**. Do not rerun live calls under this profile. Another live run requires a new owner-approved budget/profile and a newly reconciled meter.

## Postman handover

Import these two files in Postman:

- [Wave 2 collection](../../../stylist-service/postman/StyleMind-Stylist-Wave2.postman_collection.json)
- [Placeholder environment](../../../stylist-service/postman/StyleMind-Stylist-Wave2.postman_environment.json)
- [Run guide](../../../stylist-service/postman/WAVE2_RUN_GUIDE.md)

Choose the environment named StyleMind Stylist Wave 2 Local, set gatewayBaseUrl to http://localhost:8080, and provide access tokens for two distinct local Identity accounts in Postman's local environment. Do not export real tokens. The collection runs session → request intake → generate → list/detail → ownership/validation → replay checks. Postman GUI was not used in this verification; the offline runtime evidence is from Newman through the Gateway, and the live positive flow used the bounded HTTP harness, not Newman.

For a repeatable no-provider offline Gateway run, with Docker Engine available:

    $env:STYLEMIND_AI_PROVIDER = 'gemini'
    $env:STYLEMIND_GEMINI_MOCK_ENABLED = 'true'
    $env:STYLEMIND_GEMINI_MOCK_POSITIVE_FLOW_ONLY = 'false'
    $env:STYLEMIND_GEMINI_LIVE_CALLS_ENABLED = 'false'
    $env:STYLEMIND_GEMINI_GENERATION_MODEL = 'gemini-3.5-flash-lite'
    $env:STYLEMIND_WAVE2_EVIDENCE_DIR = 'stylist-service/target/ai-stylist-offline'
    Remove-Item Env:STYLEMIND_GEMINI_API_KEY -ErrorAction SilentlyContinue
    node .\stylist-service\scripts\wave2-disposable-gateway-harness.cjs

This harness uses fixture services and disposable data. For future live use, do not invoke the application directly with a key: the current approved meter profile is exhausted and the live path requires an approved call plan, exact Free Tier project, protected local key injection, and the bounded meter proxy. No live rerun command is provided by this handover.

## External dependencies and data members must provide

| Dependency | Existing source/API | What Stylist used or still needs |
|---|---|---|
| Commerce catalog | Public GET /api/v1/products?page&size&demographic_segment, GET /api/v1/products/{productId}, and POST /api/v1/variants/batch-lookup. Stylist's Feign client and HTTP contract tests exist. | The live test used a fixture, not Commerce. Provide real active WOMEN products and variants with product/category/brand IDs and names, demographic, product/variant status, variant ID/SKU/size/color/material/current unit price, and image metadata/URLs if approved. A reviewer must map Commerce category IDs to TOP or BOTTOM and approve canonical color aliases. A real catalog seed is not present in this evidence. |
| Identity style profile | Existing self-only GET /api/v1/me/style-profile; response fields are fit preference, height, weight, usual top/bottom size, shoe size, style codes, color codes and brand IDs. | Stylist has no Identity profile client and the endpoint was not called. Owner/member must provide an approved service/ownership contract, field allowlist, consent/data-use and retention rules, and a policy for unavailable profile data. Do not send height/weight, profile data or other PII to Gemini by default. Possible styling fields require an explicit owner allowlist; they are not approved for AI use by this report. |
| Wardrobe owned items | Current Wardrobe source has owner-scoped saved outfits and share APIs. Outfit items reference purchasable Commerce variants; there is no confirmed owned-garment read API in this source. | Do not treat a saved outfit or shop variant as a user's owned garment. The owning member must supply an approved lookup contract with owner-bound access, stable item ID, explicit owned provenance/status, TOP/BOTTOM classification, usable attributes and either a version/current-state check or defined stale-data semantics. Include denied/missing/unavailable behavior. No wardrobe images are used here. |
| Reviewed Neo4j knowledge | Importer requires an artifact with APPROVED status, artifact ID/version, reviewer, review timestamp and source reference; each entry needs stable ID, approved rule text, tags, reviewer and review time. | The runs used synthetic reviewed-shaped facts. Provide a human-reviewed artifact and its provenance/revocation process before using shop knowledge as real advice. |
| Commerce catalog-change events (R99) | No confirmed Product/Variant change-event producer or delivery contract was found. The observed order/payment request event is not a catalog-change event. | Commerce owner must define stable event ID and version, product/variant IDs, changed fields, deletion/inactive tombstones, monotonic ordering/version, duplicate/replay/durability semantics, authentication and delivery contract. This report does not choose a broker or add a producer. |
| Real Identity JWT/JWKS and deployment routing | Stylist validates issuer/audience/signature/roles from configured JWKS. This Gateway run used a fixture JWKS and fixture-signed tokens. | Before cross-service acceptance, test actual Identity-issued RS256 tokens, deployed issuer/audience/JWKS reachability and Gateway routing. Local fixture success is not that evidence. |
| Recommendation quality dataset | No real catalog or approved human evaluation set was loaded. | Provide reviewer-labeled synthetic/approved cases with request criteria, eligible alternatives, expected TOP+BOTTOM, disallowed items/constraints and a scoring rubric. Valid JSON and hard-rule compliance do not prove fashion quality. |

Current shop-only execution does not call Identity or Wardrobe, so there was no denied owner-context call to silently downgrade. The future owned/profile path must not treat a denied or unavailable context as a successful shop-only result unless an explicit request contract says otherwise.

## Planning guard and worktree

Both node scripts/verify-stylist-planning.cjs and node scripts/verify-stylist-planning.cjs --phase P07 exited 1 after this report was added. The guard counted 21 sources, 22 documents, 126 links, 10 JSON examples and 15 Mermaid blocks. It reported committed-range drift in these eight Commerce paths: commerce-service/pom.xml; commerce-service/postman/US-03.01_RUN_GUIDE.md; commerce-service/postman/US-03.01_categories_brands.postman_collection.json; commerce-service/postman/US-03.02-03.04_RUN_GUIDE.md; commerce-service/postman/US-03.02-03.04_products_variants_images.postman_collection.json; commerce-service/postman/US-03.05-03.10_RUN_GUIDE.md; commerce-service/postman/US-03.05-03.10_branch_cart_order.postman_collection.json; commerce-service/src/main/java/com/stylemind/commerce/CommerceServiceApplication.java.

It also reported stale source fingerprints for stylist-service/pom.xml, stylist-service/src/main/resources/application.properties, stylist-service/src/main/java/com/stylemind/stylist/security/SecurityConfiguration.java, commerce-service/src/main/java/com/stylemind/commerce/controller/product/ProductController.java, commerce-service/src/main/java/com/stylemind/commerce/dto/product/VariantResponse.java and commerce-service/src/main/java/com/stylemind/commerce/repository/product/ProductVariantRepository.java. The application.properties generation-model default was intentionally changed to gemini-3.5-flash-lite for this handover; the other dirty files predated this task. No fingerprint or reviewed_head was refreshed. The validator did not report a missing document/link/JSON/Mermaid structural error.

Both git diff --check and git diff --cached --check exited 0; Git printed only line-ending normalization warnings for pre-existing files. The initial staged, unstaged and untracked working-tree changes remain preserved and unstaged by this task.

## EP-07 backlog traceability (Backlog rows 93–102)

The workbook was read-only. “Local evidence” below is not a workbook status update and does not claim deployed completion.

| Row | Stylist code/evidence | External API or producer needed | Remaining test evidence/status |
|---|---|---|---|
| R93 — migrations | V1–V3 and disposable PostgreSQL migration/regression tests pass | None for this schema slice | Persistent database migration/deployment not performed |
| R94 — APIs and ownership | Session, history, intake, generation, status and recommendation APIs; offline Newman and positive live Gateway passed | Real Identity-issued JWT and deployed Gateway route | Local slice verified; deployed cross-service routing not verified |
| R95 — profile and permitted wardrobe context | No Identity/Wardrobe consumer in Stylist | Identity self-only GET /api/v1/me/style-profile; Wardrobe owned-item API is absent | **Incomplete**; requires approved data-use/authorization and owned-item contracts plus integration |
| R96 — invalid context, ownership, source outage | Validation, A/B ownership and implemented-client dependency faults covered; foreign owner denied before provider | Identity/Wardrobe clients are not available under an approved contract | **Partial**; source-context failure integration tests await those APIs |
| R97 — recommendation/item persistence | V3 and atomic persistence pass on disposable PostgreSQL; live run saved one recommendation and two items | No external writer | Persistent migration and owned provenance remain unverified |
| R98 — Qdrant/Neo4j retrieval | Runtime adapters and disposable-store tests pass with synthetic catalog/knowledge data | Real Commerce catalog/category mapping and approved Neo4j artifact | **Partial**; real data readiness remains |
| R99 — catalog change events | No event consumer or producer implementation is claimed | Commerce Product/Variant event contract and producer are absent | **Incomplete**; event identity/version/tombstone/delivery semantics required |
| R100 — selected AI generation | One structured live generation passed parser, whitelist and persistence using synthetic input | Gemini Developer API | Synthetic live path passed; human fashion-quality evaluation remains |
| R101 — final current-price/status check | Commerce client and recheck code have HTTP fixture tests; live path rechecked fixture data | Real Commerce catalog and variant APIs | **Partial**; no live Commerce call |
| R102 — invalid data/provider cases | Offline unit/integration/Newman cover budget, constraints, malformed output, rollback, concurrency and replay | Commerce/provider fixtures for negative cases | Live positive only; external negative cases remain fixture evidence |

## Changed files and boundaries

Stylist source, tests and harness files changed for this handover:

- stylist-service/src/main/java/com/stylemind/stylist/service/generation/GeminiDeveloperApiClient.java
- stylist-service/src/main/java/com/stylemind/stylist/config/GeminiProviderConfiguration.java
- stylist-service/src/main/resources/application.properties
- stylist-service/src/test/java/com/stylemind/stylist/config/GeminiAdapterHttpContractTests.java
- stylist-service/src/test/resources/contracts/gemini-developer-v1beta-count-tokens-generate-request.json
- stylist-service/scripts/gemini-meter-core.cjs
- stylist-service/scripts/gemini-meter-proxy.cjs
- stylist-service/scripts/gemini-meter-core.test.cjs
- stylist-service/scripts/wave2-disposable-gateway-harness.cjs

Stylist-only documentation updated or added:

- docs/qa/stylist/AI_STYLIST_HANDOVER_REPORT.md
- docs/qa/stylist/AI_STYLIST_SEQUENCE.mmd
- stylist-service/postman/WAVE2_RUN_GUIDE.md
- stylist-service/AGENTS.md
- docs/planning/stylist/README.md
- docs/planning/stylist/EP-07_IMPLEMENTATION_PLAN.md
- docs/planning/stylist/EP-07_EXECUTION_MANIFEST.json
- docs/planning/stylist/EP-07_WAVE_2_CONTRACT.md
- docs/planning/stylist/EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md

No public endpoint, DTO, Flyway migration or business rule changed in this handover. No other service source, workbook, .env, persistent database or shared container/volume was changed. Existing unrelated worktree changes remain untouched and unstaged.

## Remaining status

- Shop-only recommendation path: **local disposable + one positive Gemini live Gateway run verified**.
- Real Commerce catalog/final-price integration: **pending**.
- Identity profile context and Wardrobe owned items: **pending owner-approved contracts and source APIs**.
- Catalog change events / R99: **pending Commerce producer contract**.
- Human-reviewed production knowledge and fashion-quality evaluation: **pending data/review**.
- Persistent database migration, production deployment and full real-service acceptance: **not performed**.
- EP-07 / US-07.01 / US-07.02: **PARTIAL / INCOMPLETE**.
