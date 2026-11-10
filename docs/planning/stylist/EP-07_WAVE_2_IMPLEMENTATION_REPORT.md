# EP-07 Wave 2 — Implementation report

**Branch:** `feature/ep-07-ai-stylist`
**Initial HEAD for this task:** `c9f20d1d1fc1779eca735b3de3ac9f1745b43985`
**Owner scope:** attachment “STYLEMIND — ACCELERATED DELIVERY 2”, 2026-11-05.
**Persistent services/data:** none accessed or migrated.
**Commit/push:** none.

## 1. Delivered scope

Implemented the scoped generation and recommendation slice in `stylist-service`: runtime model adapters, separate 1536-dimensional Qdrant runtime index, reviewed catalog and knowledge adapters, generation lifecycle and API, V3 recommendation schema, owner-scoped recommendation read APIs, bounded/atomic persistence, tests, and Postman handover. Earlier P03-A/P03-B1 contracts remain intact. V1/V2 were not changed.

### Request-to-result trace

1. Gateway receives a request at `/stylist/api/v1/stylist/requests/{id}/generate`; it strips `/stylist` before forwarding to the Stylist service. `StylistGenerationController` accepts only strict `{}` and obtains owner from validated JWT `sub`.
2. `StylistGenerationServiceImpl.generate` verifies owner, status, open session, reviewed catalog policy and required runtime readiness before attempting a claim.
3. `GenerationPersistenceServiceImpl.claim` uses a short transaction and consistent session/request locks to move one owned request from `RECEIVED` to `PROCESSING`. Concurrent attempts have one winner.
4. The service loads bounded request/message context, calls `OpenAiRuntimeEmbeddingAdapter` once, and runs TOP and BOTTOM filtered searches through `RuntimeQdrantVectorStore` / `CandidateRetrievalServiceImpl`.
5. `CommerceCatalogClient` hydrates current product/variant data. `Neo4jReviewedKnowledgeService` returns only scoped approved, non-revoked facts. P02 `OutfitEngine` applies hard constraints, exact VND budget arithmetic, complete TOP+BOTTOM composition and bounded ranking. If no complete outfit exists, the service persists `NO_RESULT` without calling the chat model.
6. `OpenAiStructuredRecommendationModel` sends the frozen alias whitelist and strict native JSON Schema to the one selected chat model. It rejects refusal, truncation, extra fields, duplicate/unknown aliases, missing item reasons and malformed output. It has no retry or model fallback.
7. After model output, Commerce is called again. The pipeline checks current eligibility, price, category/demographic and hard constraints, recomputes exact VND totals and drops invalid complete outfits. It does not recompose or fill gaps.
8. `GenerationPersistenceServiceImpl.complete` writes each recommendation, both item rows and the terminal request state in one transaction. On failure, a separate conditional failure transaction records `FAILED` without overwriting a committed success. No ASSISTANT history row is created.
9. The API returns the persisted result. The two GET endpoints only read owner-scoped persistence; detail hydrates current Commerce view and labels price semantics as current, not historical.

## 2. Main implementation classes

| Layer | Classes | Responsibility |
|---|---|---|
| HTTP | `StylistGenerationController`, existing `StylingRequestController` | Strict generate DTO, owner JWT, paths, no-store, status/body |
| Use case | `StylistGenerationService`, `StylistGenerationServiceImpl` | Single generation orchestration, deadline, lifecycle, final grounding |
| Persistence | `GenerationPersistenceService`, `GenerationPersistenceServiceImpl` | Short transactions: claim, owned reads, atomic complete, conditional failure |
| Provider | `OpenAiRuntimeEmbeddingAdapter`, `OpenAiStructuredRecommendationModel`, `OpenAiModelConfiguration` | Exact approved models, 1536 embedding checks, strict native schema, no retry |
| Retrieval | `CandidateRetrievalServiceImpl`, `RuntimeQdrantVectorStore`, `ReviewedCatalogPolicy` | Slot/category filtering, index compatibility and reviewed mappings |
| Commerce | `CommerceCatalogFeignClient`, `CommerceCatalogClient`, `CommerceCatalogContracts` | Bounded product listing, variant hydration and authoritative recheck |
| Knowledge | `ReviewedKnowledgeService`, `Neo4jReviewedKnowledgeService`, `KnowledgeArtifactImporter` | Fixed-query, approved/non-revoked facts and controlled import |
| Engine/map/persistence | `OutfitEngine`, `RecommendationMapper`, recommendation entities/repositories | Deterministic rules and conversion into JPA aggregate rows |
| API/error/config | generation DTOs, `ErrorCode`, `ApiExceptionHandler`, `application.properties` | Stable response/problem shape and externalized runtime settings |

## 3. Migration and environment

Added `stylist-service/src/main/resources/db/migration/V3__add_generation_and_recommendations.sql`. It changes the request lifecycle check and creates only `wardrobe_recommendation` and `recommendation_item` with local FK/uniqueness/source/slot/reason constraints. V1/V2 remain unchanged. V3 was applied only by disposable Testcontainers PostgreSQL for integration tests. No `stylemind_stylist` persistent database or shared volume was migrated.

Docker Linux Engine 28.5.2 ran disposable containers: PostgreSQL 18.6 (`postgres:18-alpine`), Qdrant v1.20.0 (`qdrant/qdrant:v1.20.0`), and Neo4j 5.26 (`neo4j:5.26`). Testcontainers/Ryuk owned cleanup. No shared Compose project, persistent PostgreSQL, shared Qdrant or Commerce service was used. Test fixtures use synthetic/review-shaped data and HTTP fixture servers; they do not establish a live Commerce catalog or human-approved production knowledge artifact.

## 4. Verification evidence

Final command:

```text
mvn -B -ntp -f stylist-service/pom.xml verify
BUILD SUCCESS
154 tests, 0 failures, 0 errors, 0 skipped
```

Surefire XML aggregate: 18 suites, 154 tests, zero failures/errors/skips. This final run includes two direct `StylistGenerationServiceImpl` orchestration tests for terminal replay and concurrent claim behavior.

| Test group | Count | Evidence type |
|---|---:|---|
| P02 deterministic outfit engine | 52 | Unit tests |
| Retrieval, fixture/policy, Commerce HTTP fixture pipeline, Qdrant and Neo4j adapters | 43 | Unit + PostgreSQL/Qdrant/Neo4j Testcontainers + local HTTP fixture |
| Session/request APIs, ownership, concurrency, transaction, strict JSON, Unicode and V3 | 42 | Disposable PostgreSQL + embedded HTTP/MockMvc |
| Generation orchestration, one complete persisted outfit and terminal/concurrency behavior | 2 | Service-level unit tests with deterministic mocks; no external provider |
| Application/security/OpenAPI | 13 | Spring tests |
| OpenAI adapter request/response contract | 2 | Actual Spring AI clients to local embedded HTTP stub, no provider network |
| **Total** | **154** | **0 skipped** |

The integration cases cover concurrent claim one-winner, terminal replay and rollback, finalization atomicity, failure not overwriting success, no network within DB transactions, deadline-bound service behavior, Qdrant actual protocol, Neo4j approved/revoked behavior, and Commerce price/status/category/demographic changes using a local HTTP fixture. P03-A/P03-B1/P02/P04.1 regression runs as part of the same suite.

### Failure evidence retained in this report

An initial verification run exposed stale V1/V2-only test expectations after approved V3, and an application test attempting the default Neo4j health probe at localhost. Assertions were updated to verify V3 on disposable PostgreSQL; test contexts explicitly disable external Neo4j health/runtime access and use a non-service local address. Qdrant integration initially caught an alias read-path mismatch; the GET alias request now uses the service’s `/aliases` endpoint, while alias update uses its distinct update endpoint. Focused tests and the final full verify passed after these fixes. Raw logs/provider content/secrets are not embedded here.

## 5. Separately reported runtime evidence

- **Automated HTTP/integration:** included in the 154 passing tests; provider and Commerce use embedded local HTTP fixtures; real PostgreSQL/Qdrant/Neo4j are disposable Testcontainers.
- **Postman structure:** collection and environment JSON were structurally validated after creation; placeholders only.
- **Postman/Newman through Gateway:** NOT RUN. Embedded HTTP or MockMvc does not prove Gateway runtime.
- **OpenAI provider smoke:** NOT EXECUTED. Safe environment check found `STYLEMIND_OPENAI_API_KEY` absent; no secret value was read. No paid call or budget reservation was made. No trusted live catalog mapping/knowledge artifact was available.
- **Live Commerce/Identity/Wardrobe:** NOT RUN. No other service was started or modified.
- **Semantic fashion quality:** NOT evaluated. Passing protocol/engine tests does not prove stylistic relevance or human acceptance.
- **Provider cost cap:** The approved $2 cap governs only a bounded smoke. Since no provider call occurred, there is no spend evidence. Any later smoke must first establish a conservative upper bound and a restart-safe local ignored reservation ledger; a timeout/unknown-delivery reservation must remain counted.

## 6. Planning guard and remaining gates

Commands `node scripts/verify-stylist-planning.cjs` and `node scripts/verify-stylist-planning.cjs --phase P07` both exit 1. Each reports 21 sources, 21 documents, 105 links, 10 JSON examples and 15 Mermaid blocks. Findings are: relevant committed-range drift in eight Commerce files (`pom.xml`, three Postman collections/guides, `CommerceServiceApplication`); source fingerprint drift in the changed Stylist `pom.xml`, `application.properties` and `SecurityConfiguration.java`; and Commerce fingerprint drift in `ProductController`, `VariantResponse` and `ProductVariantRepository`. No `reviewed_head` or fingerprint was refreshed. Commerce was not edited for Wave 2. These planning-source warnings are recorded separately from the passing application suite.

P03, P04, P05, P06 and P07 remain partial. R95 lacks live Identity/owned-Wardrobe producer contracts; R99 catalog events are not implemented/approved; live Commerce category/color mapping and reviewed production knowledge do not exist; actual OpenAI smoke and Gateway/Newman flow were not run. P08 events, P09 target live integration, P10 acceptance, R95/R99 and EP-07 remain incomplete. No persistent migration, commit or push.

## 7. Verification follow-up — 2026-11-06

This addendum preserves the original 2026-11-05 evidence above and records the later fixes/runtime verification. For the current result, see [the Wave 2 completion report](EP-07_WAVE_2_COMPLETION_REPORT.md).

- Fixed F-01: nullable optional Commerce description/variant metadata remains unknown and is not inferred. `RuntimeCatalogImportRunnerIntegrationTests` exercises the actual importer, local Commerce/OpenAI HTTP fixtures, Qdrant and disposable PostgreSQL.
- Fixed F-02: generation now counts the exact serialized Responses input with the approved model endpoint and rejects only when the returned exact count exceeds 6,000. The count is taken before chat generation; there is no byte-size or local tokenizer estimate substituted for provider tokenization. Chat output remains capped at 2,000 tokens.
- Unknown/unmapped color cannot satisfy a hard color exclusion. The deterministic service regression covers this case.
- Final `mvn -B -ntp -f stylist-service/pom.xml verify`: **BUILD SUCCESS; 165 tests, 0 failures, 0 errors, 0 skipped across 19 suites**. The full suite includes disposable PostgreSQL/Qdrant/Neo4j integration and local HTTP contract fixtures.
- `mvn -B -ntp -f api-gateway/pom.xml test`: **11 tests, 0 failures/errors/skips**.
- Disposable runtime acceptance through the existing Gateway route and Newman: **10 requests, 20 assertions, 0 failures**. Two simultaneous HTTP generation calls against the same request yielded one 409 and one 200; only one execution for that request reached model generation. PostgreSQL showed two successful requests total (the other was the Postman flow), two recommendation rows, four recommendation items, and zero assistant-message rows.
- The runtime used named `--rm` PostgreSQL 18, Qdrant v1.20.0 and Neo4j 5.26 containers on random ports, with no mounted volumes. Commerce, OpenAI-compatible responses/embeddings, and JWKS were local contract fixtures. The JWTs were fixture-signed, not Identity-issued. All named containers and child processes were removed after the run. No shared Compose, persistent database/store, live Commerce, Identity, or provider was accessed.
- The Postman collection/environment JSON are placeholders and structurally valid; Newman actually ran the collection through the disposable Gateway. This is fixture-runtime evidence, not live-service acceptance.
- Provider smoke remains **NOT EXECUTED**: `STYLEMIND_OPENAI_API_KEY` was absent (presence-only check), so there was no external OpenAI call and no budget reservation/journal entry.
- Both planning guards still exit 1 because of the already recorded eight-file Commerce committed-range drift, Stylist fingerprints (`pom.xml`, `application.properties`, `SecurityConfiguration.java`), and three Commerce fingerprints (`ProductController`, `VariantResponse`, `ProductVariantRepository`). No `reviewed_head` or fingerprints were refreshed; no Commerce file was changed.

The exact commands, disposable resources, runtime counts, remaining gates, and redacted local evidence paths are listed in the completion report. Wave 2 has passed its local fixture/disposable verification; it does not complete P03/P04/R95/R99/EP-07, prove production data quality, or establish provider availability, billing, or semantic fashion quality.
