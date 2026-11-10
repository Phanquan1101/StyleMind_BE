# EP-07 Wave 2 — completion and verification report

**Verification date:** 2026-11-06 (Asia/Bangkok)
**Branch / HEAD:** `feature/ep-07-ai-stylist` / `c9f20d1d1fc1779eca735b3de3ac9f1745b43985`
**Disposition:** Wave 2 implementation passed local disposable verification. Wave 2 is not live/production accepted; P03/P04/P05/P06/P07 and EP-07 remain partial/incomplete.
**Changes outside the approved Stylist/docs scope:** none. **Persistent database or shared store:** none touched. **Commit/push:** none.

This report supplements the original [Wave 2 implementation report](EP-07_WAVE_2_IMPLEMENTATION_REPORT.md). It preserves that report's 2026-11-05 results as historical evidence and records the 2026-11-06 fixes and runtime tests.

## Delivered corrections

### F-01 — nullable Commerce catalog metadata

`RuntimeCatalogImportRunner` now accepts absent optional product description and variant color/size/material while retaining required-field, blank-text, type, and length validation. A missing field is retained as unknown; it is not inferred from product text, category, images, or another variant. The new `RuntimeCatalogImportRunnerIntegrationTests` exercises the importer with local Commerce and embedding HTTP fixtures, disposable PostgreSQL, Qdrant and actual index publication. It also covers invalid optional metadata and partial-import failure behavior.

### F-02 — token budget uses the exact provider token count

`OpenAiStructuredRecommendationModel` builds the generation request once and sends that exact input to the approved Responses API input-token count operation. It rejects generation when the returned exact count exceeds 6,000 and allows a 2,000-token output cap. A byte limit or local tokenizer estimate is not used as a substitute. The same frozen structured request is sent only after the count passes. `OpenAiAdapterHttpContractTests` verifies request shape, Vietnamese/emoji and large input, over-limit suppression, response validation, refusal/truncation and failure behavior against an embedded HTTP server. The test server is a protocol fixture, not OpenAI.

The hard-color exclusion regression was also closed: absent or unmapped current color is unknown and cannot satisfy a hard excluded color. It does not add a new color vocabulary or silently infer a mapping.

## API and execution trace

The service contract remains in [the Wave 2 contract](EP-07_WAVE_2_CONTRACT.md). Through the existing Gateway route, the verified flow is:

1. Create an owner-scoped session and submit the existing manual request intake. Intake stores `RECEIVED` and does not start generation.
2. Call the generation operation with exactly `{}`. The service verifies owner, session, reviewed runtime policy and dependencies, then claims the request with a short PostgreSQL transaction.
3. Outside that transaction it embeds the request, retrieves TOP and BOTTOM separately from Qdrant, hydrates the catalog through the Commerce client contract, reads reviewed-shaped knowledge from Neo4j, and runs the deterministic TOP+BOTTOM engine.
4. If a bounded complete outfit exists, the model receives the frozen aliases and strict JSON schema after exact input-token counting. The result is validated and the current Commerce values are checked again.
5. Recommendations, both item rows and terminal request status are written atomically. Owner-scoped list/detail reads return persisted results without another model call; detail's product/price values are current Commerce-view values, not a historical snapshot.

No owned wardrobe or profile data enters the flow, no Identity profile call is made, no ASSISTANT message is created, and no stock/reservation logic is added. The system still cannot recover an interrupted `PROCESSING` request automatically.

## Verification evidence

| Check | Result | Evidence boundary |
|---|---|---|
| `mvn -B -ntp -f stylist-service/pom.xml verify` | **BUILD SUCCESS; 165 tests, 0 failures, 0 errors, 0 skipped; 19 suites** | Full Stylist regression, PostgreSQL/Qdrant/Neo4j Testcontainers and local HTTP fixtures; V3 only on disposable PostgreSQL. Final output: `stylist-service/target/wave2-disposable-gateway/stylist-verify-final.log`; Surefire XML is under ignored `stylist-service/target/surefire-reports/`. |
| `mvn -B -ntp -f api-gateway/pom.xml test` | **11 tests, 0 failures, 0 errors, 0 skipped** | Existing Gateway route/security regression; no Gateway source changes. |
| `node stylist-service/scripts/wave2-provider-smoke.test.cjs` | **7 passed, 0 failed** | Budget reservation, restart safety, refusal without a key, timeout reservation retention and exact-count suppression tested with stub fetches. No paid call. |
| `node stylist-service/scripts/wave2-disposable-gateway-harness.cjs` | **Newman: 10 requests, 20 assertions, 0 failures** | Actual Gateway and Stylist processes, actual HTTP requests, disposable PostgreSQL/Qdrant/Neo4j. Local Commerce/OpenAI/JWKS fixtures and synthetic-only catalog/knowledge. |
| Same-request HTTP concurrency | **one 409 loser, one 200 winner** | Both callers used the same disposable PostgreSQL-backed request. One execution reached generation; database ended with two successful requests total, two recommendations, four recommendation items and zero assistant messages. |
| Collection/environment structure | **Valid JSON; 10 requests; environment uses placeholders** | Structural validation is separate from the actual Newman fixture run. |
| `git diff --check` / cached equivalent | **PASS** | Run again after writing this report (final result recorded below). |

The disposable Gateway/Newman summary is `stylist-service/target/wave2-disposable-gateway/gateway-newman-summary.json`; redacted process logs and the Newman report/JUnit output are adjacent in that ignored evidence folder. The checked-in [Postman run guide](../../../stylist-service/postman/WAVE2_RUN_GUIDE.md) distinguishes this isolated run from live-service testing.

### Disposable resources

The Gateway harness created only these random-port, named `--rm` containers, without volume mounts:

| Image | Container name | Random host port |
|---|---|---:|
| `postgres:18-alpine` | `stylemind-wave2-22868-cbdfc13a-pg` | 64447 |
| `qdrant/qdrant:v1.20.0` | `stylemind-wave2-22868-6e2fda77-qdrant` | 64448 |
| `neo4j:5.26-community` | `stylemind-wave2-22868-1823f18c-neo4j` | 64450 |

All exact-name test containers and their child processes were removed after the run; the disposable Docker check showed no `stylemind-wave2-*` containers remaining. Maven's Testcontainers/Ryuk resources were independently disposable and cleaned by their test runs. No shared Compose project, mounted volume, persistent PostgreSQL database, shared Qdrant/Neo4j, Commerce runtime, Identity runtime, or other service source was used or changed.

## Fixtures versus live evidence

- Gateway and Stylist were actual local application processes, routed through the existing Gateway `/stylist` route. Newman sent real HTTP requests to that route.
- The test JWT/JWKS was locally fixture-signed. It was **not** issued by Identity.
- Commerce Feign, OpenAI embedding/token-count/Responses and JWKS were local HTTP fixtures. The two shop products and knowledge artifact were synthetic test data. No human-approved production catalog policy or knowledge dataset was present.
- `STYLEMIND_OPENAI_API_KEY` was absent; only its presence was checked. No external OpenAI request, provider availability check, billable request or smoke-budget reservation was made. The $2 smoke tool is opt-in and its seven tests passed; actual provider smoke remains **NOT EXECUTED**.
- No live cross-service authentication, live Commerce data, end-to-end fashion quality, customer data, production deployment, or persistent schema migration has been verified.

## Planning and remaining gates

Both `node scripts/verify-stylist-planning.cjs` and `node scripts/verify-stylist-planning.cjs --phase P07` still exit 1. After registering this completion report, they report 21 sources, 22 documents, 111 links, 10 JSON examples and 15 Mermaid blocks, then flag:

- committed-range Commerce drift in eight files: `commerce-service/pom.xml`, the three US-03 Postman guides/collections, and `CommerceServiceApplication.java`;
- Stylist fingerprint drift in `stylist-service/pom.xml`, `application.properties` and `SecurityConfiguration.java`;
- Commerce fingerprint drift in `ProductController.java`, `VariantResponse.java` and `ProductVariantRepository.java`.

These are recorded planning/source-review warnings, not failures in the passing application tests. No fingerprint, `reviewed_head`, Commerce file, workbook or other service was changed to make the guard pass.

Still outstanding: approved/live Commerce category and color policy, human-reviewed production knowledge and dataset evaluation, Identity-issued token/Gateway acceptance, owned Wardrobe and Identity-profile producer contracts (R95), catalog events (R99), paid provider smoke, P08/P09/P10 acceptance and remaining P03/P04/P05/P06/P07 work. The harness proves protocol, persistence, ownership, replay and same-request claim behavior with disposable fixtures; it does not prove recommendation relevance or that the whole EP-07 is finished.

## Final state

The full Stylist `verify` exit code was **0**. After the documentation updates, `git diff --check` and `git diff --cached --check` both exited **0** (Git only printed its existing LF-to-CRLF notices). Branch and HEAD stayed `feature/ep-07-ai-stylist` / `c9f20d1d1fc1779eca735b3de3ac9f1745b43985`. No changes were staged, committed, pushed, or merged. Existing staged, unstaged and untracked work was preserved.
