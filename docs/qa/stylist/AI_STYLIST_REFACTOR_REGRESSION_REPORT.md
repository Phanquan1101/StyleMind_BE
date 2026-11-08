# AI Stylist Refactor Regression Report

**Date:** 2026-11-08

**Branch / HEAD:** `feature/ep-07-ai-stylist` / `c9f20d1d1fc1779eca735b3de3ac9f1745b43985`

## Scope and result

This follow-up addressed the approved null-price defect, Java reference cleanup, generation-service responsibility split, and isolation of the P04.1 fixture pipeline. It did not change API routes or DTOs, migrations/schema, generation lifecycle, provider settings, or P02 ranking rules. No source outside `stylist-service` was changed. The existing dirty/staged/untracked worktree was preserved; this task did not stage, commit, push, switch branches, reset, clean, or pull.

## 1. Null Commerce price

`CommerceCatalogClient.exactPrice` now rejects a null price as the existing `DEPENDENCY_PROTOCOL_ERROR` (HTTP 502) before numeric conversion. It does not substitute a price. Existing exact conversion remains in place for non-null values.

`CommerceCatalogClientNullPriceTests` covers a null batch price, a null in one of several batch variants, null product-detail price, zero and positive whole-VND prices, and negative, fractional, and overflowing prices. The two batch tests also prove that no product-detail request is made after detecting an invalid batch price.

`StylistSessionApiIntegrationTests.nullCurrentCommercePriceReturnsSanitizedBadGatewayProblemDetails` verifies the detail API returns 502 Problem Details with the existing error code, `no-store`, and correlation header. `StylistGenerationServiceTests.finalCommerceProtocolFailureMarksRequestFailedWithoutPersistingRecommendations` verifies the final Commerce protocol failure marks the claimed request `FAILED`, does not call persistence completion, and leaves the recommendation list empty. Both cases use test fixtures/mocks at the Commerce boundary.

## 2. Fully qualified references

Unnecessary `com.stylemind...` references in Java bodies were replaced by explicit imports in the affected production and test code. The follow-up scan found none. The intentional package-scan string in `StylistServiceApplication` (`@EnableFeignClients(basePackages = "com.stylemind.stylist.client.commerce")`) remains configuration, not a Java type reference. Unnecessary `java.util...` qualifiers in the changed files were also replaced with imports. No wildcard imports were introduced.

## 3. Generation responsibilities after refactor

`StylistGenerationServiceImpl` remains the public runtime service implementation and orchestration entry point. `generate()` still checks owner/state, claims once, runs provider work before persistence finalization, and lets only the caller thread complete or mark the request. The sequence is now easier to follow through named steps: retrieve/hydrate, load approved knowledge facts, run the unchanged P02 engine, freeze and validate the model whitelist, recheck current Commerce data, then return the persistence result.

- `GenerationEngineInputMapper` converts already validated Commerce data to the existing P02 input shape; it has no network or persistence behavior.
- `GenerationOutputValidator` freezes aliases and validates structured output against that whitelist; the existing hard rules remain in the service/engine.
- `RecommendationMapper` handles entity/runtime read data to API DTO mapping for generate, request-list, and detail responses. The distinct response DTO contracts remain distinct.
- `GenerationDeadlineExecutor` owns the bounded worker pool, deadline wait and cancellation. `StylistGenerationServiceImpl` closes it on bean shutdown. The persistence boundary remains `GenerationPersistenceServiceImpl`; workers do not persist.

The service implementation is 513 lines after the split, down from approximately 633. The extraction is responsibility-based; no new generic framework or forwarding layer was added. Existing generation-service and runtime integration regressions passed.

## 4. Fixture/POC isolation

Reference inventory showed the following nine classes were used only by the P04.1/internal fixture path. Each moved from `src/main/java` to the same package path under `src/test/java`:

- `service/recommendation/StylingRecommendationService.java`
- `service/impl/recommendation/StylingRecommendationServiceImpl.java`
- `service/retrieval/CandidateRetrievalService.java`
- `service/impl/retrieval/CandidateRetrievalServiceImpl.java`
- `service/retrieval/CandidateTextBuilder.java`
- `service/retrieval/CandidateEmbeddingPort.java`
- `repository/retrieval/CandidateVectorStore.java`
- `repository/retrieval/QdrantHttpVectorStore.java`
- `model/retrieval/RetrievalModel.java`

The fixture recommendation implementation no longer has `@Service`. `InternalRecommendationPipelineIntegrationTests` registers it with an explicit, test-local `@TestConfiguration` bean; it is not a global component-scan bean. `StylistServiceApplicationTests.productionComponentScanDoesNotRegisterFixturePipelineBeans` checks that the production application context has no bean of either fixture service interface. This test passed.

The built production JAR contains none of the nine POC classes, including nested class entries. The runtime `RuntimeQdrantVectorStore`, `RuntimeCandidateSearch`, and `RuntimeEmbeddingPort` remain in the JAR. P04.1's 128-dimensional fixture model stays test-only and is not mixed with the 1536-dimensional runtime embedding path. The Qdrant and internal POC regression tests remain present and passed.

## 5. API, schema, lifecycle, and ranking invariants

No controller route, HTTP method, response DTO, security rule, migration, or entity/schema was changed. Generate, list, and detail continue to use their own response shapes. Existing owner isolation, no-store, lifecycle/claim/replay, atomic persistence boundary, provider call boundary, TOP+BOTTOM whitelist, and final Commerce checks were exercised by the unchanged suite and offline Gateway flow. `OutfitScorer`, candidate policy, diversity, tie-breaks, limits, and ranking weights were not modified.

## 6. Verification evidence

Focused run before the final full gate:

```text
mvn -B -ntp -f stylist-service/pom.xml '-Dtest=CommerceCatalogClientNullPriceTests,StylistGenerationServiceTests,StylistSessionApiIntegrationTests,StylistServiceApplicationTests,CandidateRetrievalServiceTests,QdrantCandidateRetrievalIntegrationTests,InternalRecommendationPipelineIntegrationTests' test
81 tests; 0 failures; 0 errors; 0 skipped
```

Final regression gate, run after the last Java edits:

```text
mvn -B -ntp -f stylist-service/pom.xml verify
24 suites; 199 tests; 0 failures; 0 errors; 1 skipped; BUILD SUCCESS
```

The single skipped test is the opt-in live Gemini smoke. It is not counted as a pass. No Gemini or OpenAI provider was called.

The disposable Gateway/Newman harness was run with Gemini mock mode explicitly enabled and live calls disabled:

```text
node stylist-service/scripts/wave2-disposable-gateway-harness.cjs
10 requests; 20 assertions; 0 failures
```

The checked-in Postman collection and environment parsed as JSON (10 requests; 9 environment values), and the harness script passed `node --check`. Newman exercised the collection through the locally launched Gateway. The Java Gemini client/parser used a local HTTP fixture; Commerce and JWKS were also fixtures, and the JWT was fixture-signed, not issued by Identity. Fixture observations were 5 embeddings, 8 count-token calls, and 2 generations total (the Newman collection itself accounted for 4/6/1; the rest were harness preparation). Replay added zero provider calls. Customer B was denied with 404 before provider work. The concurrency check returned one 200 winner and one 409 loser. PostgreSQL recorded 2 succeeded requests, 2 recommendations, 4 items, and 0 assistant messages.

## 7. Environment, artifacts, and cleanup

Java 21 / Spring Boot 3.5.16 ran on Docker Desktop's Linux Engine. Automated integration tests used disposable Testcontainers, including PostgreSQL 18.6, Qdrant `v1.20.0`, and Neo4j `5.26-community`; the Gateway harness used random host ports and no persistent volumes. No Compose stack, persistent database, shared Qdrant/Neo4j, live Commerce, or live identity service was used. The harness's PostgreSQL/Qdrant/Neo4j container names were checked after completion and were absent. Testcontainers cleanup was handled by the test run. No existing member container or volume was removed.

## 8. Planning guard and final checks

Both `node scripts/verify-stylist-planning.cjs` and `node scripts/verify-stylist-planning.cjs --phase P07` still exit 1. Their findings match the pre-edit baseline: commit-range drift in eight Commerce paths and stale source fingerprints for three Stylist paths (`stylist-service/pom.xml`, `application.properties`, and `SecurityConfiguration.java`) plus three Commerce paths (`ProductController.java`, `VariantResponse.java`, and `ProductVariantRepository.java`). No manifest fingerprint or reviewed head was refreshed, and this task introduced no new planning-guard finding.

The Java body scans found no unnecessary `com.stylemind...` references and no unnecessary `java.util...` qualifiers in the changed files. The JAR inspection confirmed fixture isolation. Postman/Newman evidence is separate from live-provider evidence; no direct Postman UI run was performed.

## Final status

- Null-price defect: **FIXED**.
- Reference cleanup: **COMPLETE**.
- Generation refactor: **COMPLETE**.
- Fixture isolation: **VERIFIED**.
- Full regression: **PASS** — one live-only test remains skipped.
- API, schema, and ranking: **UNCHANGED**.
- EP-07: remains **PARTIAL**; no story or epic is marked done by this report.

No commit or push was made.
