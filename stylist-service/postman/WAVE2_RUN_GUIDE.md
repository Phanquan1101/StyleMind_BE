# Wave 2 Postman run guide

## Current handover profile — 2026-11-07

Current verified generation model: gemini-3.5-flash-lite. Embedding model: gemini-embedding-001 at 1536 dimensions through synchronous batchEmbedContents. Gemini must be selected explicitly; a key by itself does not switch the provider. STYLEMIND_GEMINI_LIVE_CALLS_ENABLED defaults to false.

The 2026-11-07 positive live Gateway run passed with synthetic input. Its cumulative meter is now at the approved cap: 12/12 embeddings, 23/23 countTokens and 6/6 generations. Do not repeat live calls with this profile. Any new live attempt needs a new owner-approved budget and a reconciled meter. Live use requires the bounded local meter proxy; do not enable it in an ordinary IntelliJ/application run.

The 2026-11-06 and earlier Gemini sections below are retained as historical snapshots. Where they name gemini-2.5-flash, say live is blocked, or show earlier counters, this current section and the 2026-11-07 handover supersede them.

## What this collection exercises

The collection follows the Gateway route and covers session creation, P03-B1 request intake, the new generation operation, persisted recommendation reads, the current Commerce detail view, a foreign-owner negative case, strict body validation and terminal replay. The customer submits needs and constraints (such as occasion, style, budget and exclusions); they do not choose a TOP, a BOTTOM, products or an outfit. The generate request body is `{}`. The backend retrieves and hydrates candidates, applies reviewed knowledge and the P02 TOP+BOTTOM engine, then sends its bounded whitelist to the model adapter. The Gateway prefix `/stylist` is stripped before forwarding, so use `http://localhost:8080` as `gatewayBaseUrl`; keep `/stylist` in each request URL.

## Import and set variables

1. In Postman choose **Import** and import both `StyleMind-Stylist-Wave2.postman_collection.json` and `StyleMind-Stylist-Wave2.postman_environment.json`.
2. Select **StyleMind Stylist Wave 2 Local** in the environment selector.
3. Set `customerAToken` and `customerBToken` to real Identity access tokens for two distinct accounts. Keep them in Postman's local environment, not in exported files.
4. Confirm `gatewayBaseUrl` is `http://localhost:8080` and the Gateway/Stylist routes are reachable.
5. Send request 1, then 2. The tests capture `sessionId` and `requestId` automatically. Intake must return 201, `RECEIVED`, and `generation_started=false`.
6. Request 7 is a negative check and can be run after intake. It must return 400 before provider work. Request 6 must use Customer B and return the same 404 shape as a missing request.
7. Before request 3, confirm the runtime has an approved reviewed catalog policy, a complete 1536-dimensional runtime Qdrant index, reachable Commerce and approved/reachable Neo4j knowledge, plus an available OpenAI key/model permission. Without these prerequisites, a safe 503/configuration error is expected and is not a generation PASS. In the current handover environment, live-provider smoke was not run and these prerequisites were not asserted.
8. A configured generate call returns 200 with `SUCCEEDED` (1–3 recommendations) or `NO_RESULT` (empty recommendations and `BOUNDED_SEARCH_NO_OUTFIT`). Only after `SUCCEEDED`, use request 5; the collection stores the first `recommendationId` automatically. Request 4 reads stored status/results without generating. Request 8 is only meaningful after a terminal 200 response and replays stored output without another model call.

## Routes and safe expectations

- Generation body: exactly `{}`. Do not send a client model, provider, URL, item, price or owner field.
- `GET /requests/{id}/recommendations` does not call generation. Detail GET uses current Commerce information and says `CURRENT_COMMERCE_DATA_NOT_HISTORICAL`; the schema has no historical price snapshot.
- Every owner-specific response uses `Cache-Control: no-store`. Customer B cannot read Customer A's request; ADMIN is also self-only.
- A request in `PROCESSING` returns 409 for another generation attempt. A `FAILED` request is not automatically retried; create a new request to try again. Successful/no-result terminal replay reads saved results.
- If a server/database failure interrupts failure recording, `PROCESSING` may remain. There is no automatic startup recovery or reset API. Preserve request ID and correlation ID for an operator; inspect application/database health. Do not manually change the status or rerun a paid attempt without an approved recovery procedure.

## What this guide does not prove

The checked-in collection and environment contain placeholders only. Their JSON structure was validated locally. A later isolated Gateway/Newman run is recorded below, but it used fixture-signed JWTs, local Commerce/OpenAI/JWKS contract fixtures and synthetic reviewed-shaped data; it did not use Identity-issued tokens, live Commerce, a human-approved live catalog/knowledge set or a paid provider call. Embedded HTTP fixtures and Testcontainers do not establish live cross-service acceptance.

## Disposable Gateway/Newman verification — 2026-11-06

The checked-in collection was executed with Newman through the existing API Gateway route using the disposable harness `node stylist-service/scripts/wave2-disposable-gateway-harness.cjs`. It ran **10 requests / 20 assertions, 0 failures**. The flow covered session creation, request intake, successful generation, recommendation list/detail, foreign-owner denial, strict-body rejection, terminal replay, Unicode intake and an over-budget generation that returned 413 before chat generation. The harness also sent two actual concurrent HTTP generate requests for one request: one returned 409, one returned 200, with one generation execution. PostgreSQL recorded the expected atomic result rows.

The harness created only no-volume, random-port `--rm` containers for PostgreSQL 18 (`postgres:18-alpine`), Qdrant v1.20.0 and Neo4j 5.26. Commerce, OpenAI-compatible embedding/token-count/generation endpoints and JWKS were local fixtures. The API JWTs were signed by a local fixture key, not issued by Identity. The containers/processes were removed after the run. This verification does not prove live Identity/Gateway/Commerce interoperability, production data quality, actual OpenAI access or semantic styling quality. Full details and counts are in [the completion report](../../docs/planning/stylist/EP-07_WAVE_2_COMPLETION_REPORT.md).

## Historical Gemini local/test configuration — 2026-11-06 (superseded by the current profile above)

The Gemini adapter uses `gemini-2.5-flash` and `gemini-embedding-001` (1536 dimensions). It does not change API paths, request bodies, migrations, or the existing Gateway route. A synthetic local HTTP fixture verifies serialization; that is not a live provider run.

Set configuration only in the Stylist process environment or a local ignored secret file:

```text
STYLEMIND_AI_PROVIDER=gemini
STYLEMIND_GEMINI_API_KEY=<replace-with-a-locally-stored-key>
STYLEMIND_GEMINI_LIVE_CALLS_ENABLED=false
```

The live-call switch defaults to `false`; keep it false until all checks below are complete. Do not paste a key into Postman, a collection, chat, source control or this guide. A key exposed in chat should be revoked/rotated before local use.

Before any live call, open Google AI Studio **Projects**, select the exact project bound to the replacement API key, and verify its **Billing Tier** is **Free**. Also verify that `gemini-2.5-flash` and `gemini-embedding-001` are available to that project and review the project’s active quota. Do not use a paid project, billing account, trial credit or another model. Public pricing pages and an API key do not prove the selected project is Free Tier. The owner later confirmed that the selected project is Free Tier with billing off; numeric quota ceilings were not retrieved by the smoke process.

On 2026-11-07, `models.list` and two synthetic embedding calls returned HTTP 200. The provider returned 3,072 embedding dimensions even though the adapter requests 1,536, so it stopped with a safe protocol error. No generation or Gateway request was made. A later local environment search accidentally emitted the API-key line into a tool result. Treat that key as exposed: revoke/rotate it before any more calls, then update only the ignored local `.env`. The checked-in default and `.env` live-call flag remain `false`; never put a key or live opt-in in this Postman environment. Keep the smoke and Gateway flow **BLOCKED** until a replacement key is installed and a metered synthetic embedding returns the approved 1,536-dimensional vector. The failed attempt and exact safe counters are recorded in the [Gemini verification report](../../docs/planning/stylist/EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md).

When the key is rotated and the embedding discrepancy is resolved, keep input synthetic and within the approved cap: at most 5 `generateContent`, 10 `embedContent`, and 10 `countTokens` calls, sequentially, stopping early when evidence is sufficient. Do not run the catalog importer against live Commerce or reindex the 600-product catalog; prepare a small synthetic-only disposable Qdrant fixture. Do not send customer/profile/wardrobe data, account identifiers, credentials, URLs or private history. A `429` stops the run; do not retry or fall back. Count-token and adapter fixtures are not Gemini-live evidence. Google states `gemini-embedding-001` has a separate 2048-token input limit and reduced dimensions require local normalization; generation `countTokens` reports prompt tokens, not output/thinking tokens or the JSON schema. Refer to the [Gemini verification report](../../docs/planning/stylist/EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md) for exact sources and current run status.

## Latest bounded Gemini verification — 2026-11-07

The owner confirmed key rotation, same Free Tier project, and billing off. The replacement key was read only by a temporary process and is not recorded here. Probe A (`:embedContent`) returned 3,072 values; Probe B (`:batchEmbedContents`, synchronous Developer API) returned 1,536 for the same synthetic input/task/model. The production adapter now uses the batch route; live synthetic document and query embeddings both returned 1,536 finite values and passed adapter normalization. This shows a route-level behavior difference, not Google's internal cause.

Generation did not reach `generateContent`: generation `countTokens` returned HTTP 400 before and after correcting the structured JSON `mimeType` to the official `APPLICATION_JSON` enum. The post-fix isolated live smoke ran 1 test with 1 error (the HTTP 400); the attempt was not observed by the local meter, so it is reserved conservatively as one additional token-count call. No further live requests were made. The saved meter counted 6 embeddings and 6 token-count calls; with the extra attempted call reserved, at least 7 of the local 10 token-count cap are used. The current Gateway harness needs four more count-token calls for its two synthetic catalog items, query, and generation preflight; therefore it was not run under the existing cap. Generation, Gateway persistence/read-back/replay, and owner-isolation live checks remain unverified. The latest `mvn -B -ntp -f stylist-service/pom.xml verify` passed 183 tests with 4 gated live skips; these skips are not live passes. The safe counter snapshot is under ignored `stylist-service/target/gemini-live-diagnostic-20261107/`.

At that earlier run, keep `STYLEMIND_GEMINI_LIVE_CALLS_ENABLED=false` by default and do not retry the failed generation preflight. The following dated addendum supersedes that run's call-limit snapshot and records the latest stop condition. See the [dated Gemini verification evidence](../../docs/planning/stylist/EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md) for request-shape, test, cleanup, and call-accounting details.

### Latest bounded token-count follow-up — 2026-11-07

The cumulative local meter cap for token-count was raised to 20 by owner instruction; the embedding and generation caps remain 10 and 5. The historical meter count of six token-count calls was reconciled with one prior unmetered HTTP 400 attempt, so the follow-up started at seven. One synthetic minimal Developer API `countTokens` request then returned HTTP 404 `NOT_FOUND`; the meter now records eight cumulative token-count attempts. No production-payload preflight, `generateContent`, or Gateway/Newman live run followed. The upstream cause is unknown because the bounded diagnostic had no field path; do not retry or treat remaining numeric budget as approval to bypass the failed preflight. Embedding remains verified only through synchronous `batchEmbedContents`; generation and Gateway are not verified. Keep the application and `.env` live-call switch false outside a temporary, metered process. See the verification report for exact test evidence and limitations.

### Retry after local key change — 2026-11-07

One further metered Probe A was made after the owner changed the local API key. It returned the same HTTP 404 `NOT_FOUND` (476 ms); the cumulative count is now 9/20, including the earlier unmetered HTTP 400. A key change alone did not resolve the preflight. No production preflight, generation, Gateway, or Newman live call was made. Stop here until a targeted diagnosis establishes the cause; do not rotate keys repeatedly or bypass `countTokens`.

### Alternate Gemini model probe — 2026-11-07

The owner requested a bounded comparison of `gemini-3.5-flash-lite`, `gemini-3.8-flash` and `gemini-3.7-flash`. Each accepted the synthetic minimal and production-shaped `countTokens` request (8 and 304 tokens). `3.5-flash-lite` then returned HTTP 400 `INVALID_ARGUMENT` with no safe field path. The one generation attempt for each 3.8 and 3.7 model timed out at the local meter’s fixed 15-second upstream timeout, which returned local HTTP 502; neither result says whether Google would have completed generation. No generation output reached the Java parser, so no alternate model is verified for use. The service configuration was unchanged. Final cumulative local counters: embedding 6/10, countTokens 15/20, generation 3/5; no 429 or retry. See the [Gemini verification report](../../docs/planning/stylist/EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md) and safe evidence under ignored `stylist-service/target/gemini-model-compare-20261107/`.

## Offline-ready pipeline verification — 2026-11-07

The pipeline is **OFFLINE READY** for the current synthetic local contract. This does not verify Gemini availability, stylistic quality, live Identity/Commerce integration, or completion of EP-07.

The disposable run reused the production Stylist service and existing Gateway/Newman harness. It submitted a customer request with needs and constraints, then exercised retrieval, Commerce candidate hydration, reviewed Neo4j knowledge, P02 outfit construction, the actual Gemini HTTP client and Java output parser, whitelist enforcement, final Commerce recheck, and PostgreSQL recommendation persistence. Gemini's three HTTP operations (`batchEmbedContents`, `countTokens`, `generateContent`) terminated at a local loopback fixture. The fixture returned synthetic responses; no Gemini key was read and no external provider was called. Qdrant/Neo4j/PostgreSQL were real disposable containers with synthetic inputs; Commerce and JWKS were local fixtures. Gateway and Stylist were running application processes.

To reproduce without `.env` or a provider key, run from the repository root in PowerShell:

```powershell
$env:STYLEMIND_AI_PROVIDER = 'gemini'
$env:STYLEMIND_GEMINI_MOCK_ENABLED = 'true'
$env:STYLEMIND_GEMINI_LIVE_CALLS_ENABLED = 'false'
Remove-Item Env:STYLEMIND_GEMINI_API_KEY -ErrorAction SilentlyContinue
node stylist-service/scripts/wave2-disposable-gateway-harness.cjs
```

The harness starts a loopback-only Gemini HTTP fixture and disposable, random-port `postgres:18-alpine`, `qdrant/qdrant:v1.20.0`, and `neo4j:5.26-community` containers. It removes its exact no-volume containers and temporary JWT environment/export files in `finally`. Do not point this mode at a shared Gateway, database, Qdrant, Neo4j, or external Gemini endpoint.

Latest run: Newman executed **10 requests / 20 assertions, 0 failures** through the Gateway. One request intake stayed `RECEIVED` until explicit generation. Generation succeeded with one backend-selected complete outfit containing exactly one TOP and one BOTTOM. Read-back returned the saved recommendation. Terminal replay matched the original generate response and added **0** Gemini-fixture calls. A Customer B generate attempt returned 404 before embedding, token-count, or generation calls. HTTP concurrency against one request produced one 200 winner and one 409 loser; only the winner reached the model fixture. PostgreSQL held 2 successful requests, 2 recommendations and 4 recommendation items for the primary and concurrency paths.

The fixture observed **5 embedding, 8 token-count and 2 generation operations** in total, including one successful main flow and the concurrency winner. These are mock operations and do not consume the separate live-call allowance. The Postman collection/environment JSON is also parsed by the harness; Newman runtime evidence is distinct from that structural check.

Regression gate after the timestamp persistence fix: `mvn -B -ntp -f stylist-service/pom.xml verify` completed BUILD SUCCESS with **188 tests, 0 failures, 0 errors, 1 skipped**. The one skip is the opt-in live Gemini test and is not a pass. The suite included disposable PostgreSQL/Qdrant/Neo4j checks. A targeted `StylistGenerationServiceTests` run passed 4 tests. Hard-budget, changed current price/status/category/demographic and hard-exclusion behavior remain covered by the existing disposable integration suite.

The replay run exposed a real timestamp precision mismatch: Java initially returned nanoseconds while PostgreSQL `TIMESTAMPTZ` stores microseconds. Stylist now truncates `generated_at` to microseconds before persistence, and a fixed-clock regression test covers it. No DTO, endpoint, lifecycle or migration changed.

This is fixture-backed contract readiness only. The live meter remains at the prior cumulative snapshot (embedding 6/10, `countTokens` 15/20, generation 3/5); this offline run did not change those counts. Live `countTokens` for the existing production model still has the earlier unresolved 404, so no live generation was attempted here. Proposed next live experiment, subject to the separate approval gate: once the preflight blocker is resolved, use one synthetic Gateway generation as the next Probe B, so the same bounded call covers the deployed adapter/parser/persistence path; terminal replay should add none. This is a proposal only; no live request was sent.

### Historical offline re-verification — 2026-11-07 (before final live verification)

The offline flow was re-run after the Gemini response-body timeout fix. No API key or `.env` was read and the live switch was explicitly false. The default model remains `gemini-2.5-flash`.

The run used the same disposable Gateway harness with `STYLEMIND_AI_PROVIDER=gemini` and `STYLEMIND_GEMINI_MOCK_ENABLED=true`. The customer submitted criteria; the request was stored as `RECEIVED`, and the later generate call used `{}`. The real Stylist pipeline retrieved and hydrated candidates, loaded synthetic reviewed Neo4j facts, constructed TOP+BOTTOM candidates, called the actual Gemini client against a loopback HTTP fixture, ran the Java parser and whitelist checks, rechecked fixture Commerce, and persisted the recommendation and both items atomically. This Gateway run used the unchanged default `gemini-2.5-flash`; `gemini-3.5-flash-lite` has focused request-contract tests only, not a 3.5 Gateway/live result.

Newman executed the checked-in Postman v2.1 collection through Gateway: **10 requests, 20 assertions, 0 failures**. The local Gemini fixture observed 5 embeddings, 8 token-count calls, and 2 generations. Customer B was denied before fixture calls; terminal replay added zero calls; concurrent generation returned one 200 winner and one 409 loser. Disposable PostgreSQL had 2 successful requests, 2 recommendations, and 4 recommendation items. PostgreSQL 18.6, Qdrant `v1.20.0`, and Neo4j `5.26-community` had no mounted volumes and were removed by the harness.

The latest regression gate is `mvn -B -ntp -f stylist-service/pom.xml verify`: **191 tests across 23 suites, 0 failures, 0 errors, 1 skipped**. The only skip is the opt-in live Gemini smoke; it is not a PASS. The full report is [EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md](../../docs/planning/stylist/EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md); safe Gateway evidence is under ignored `stylist-service/target/gemini-offline-ready-20261107/`.

Live status is unchanged: the earlier `gemini-3.5-flash-lite` structured request returned `400 INVALID_ARGUMENT` without a retained field path, so its precise cause is unknown. No live probe was run in this offline follow-up. No live Gemini result, Identity-issued JWT, live Commerce call, styling-quality acceptance, or EP-07 completion is implied.

#### Separate live approval gate

If live verification is approved later, use the existing `gemini-3.5-flash-lite` local/test setting without changing the production default (`gemini-2.5-flash`). Probe A is one minimal text-only `generateContent` call with no schema/tools/media, no retry, and the existing 30-second deadline. The model already passed prior synthetic count-token probes; the minimal smoke can use zero new count calls. Stop if A fails.

Only after A passes, run one positive Gateway flow using synthetic/disposable resources. Expected additional calls are 3 embeddings, 4 token-counts, and 1 generation. Based on the prior meter snapshot (6/10, 15/20, 3/5), the planned cumulative totals after both probes are 9/10 embeddings, 19/20 token-counts, and 5/5 generations. Replay and foreign-owner denial must add no calls. Do not run Newman, negative/concurrency variants, retry, or fallback in the live window. Use the current 30-second deadline; do not raise it for the probe. **This is a plan only; owner approval is still required before any live call.**

## Historical Gemini 3.5 Flash-Lite offline readiness — 2026-11-07 (before the approved live Gateway run)

The latest offline Gateway run pins `gemini-3.5-flash-lite` in the Stylist process while routing its actual Gemini HTTP client to the loopback fixture. The fixture accepts only that exact model and validates `countTokens`'s path and nested `models/gemini-3.5-flash-lite` value, plus the `generateContent` path. It also requires `thinkingLevel=MINIMAL`, `responseFormat.text.mimeType=APPLICATION_JSON`, and a schema, and rejects the legacy field layout. Evidence: `target/gemini35-gateway-offline-20261107-final/gateway-newman-summary.json`.

The disposable run passed Newman through the actual Gateway: 10 requests / 20 assertions, no failures. It exercised request intake as `RECEIVED`, later generation from customer criteria, retrieval/hydration, the P02 TOP+BOTTOM whitelist, Java parsing, final Commerce fixture recheck, atomic persistence, read-back, replay without extra calls, Customer B isolation, and the concurrency claim. The Gemini fixture saw 5 embedding, 8 total token-count, and 2 generation calls. No Gemini provider or live Commerce/Identity call was made. PostgreSQL 18, Qdrant v1.20.0, and Neo4j 5.26 were no-volume disposable containers and were removed by the run.

Timeout ordering for the next bounded smoke: the caller's standalone Probe A uses 25 seconds; the Java client's maximum is 30 seconds; the local meter proxy's upstream cap is 30 seconds. Gateway operations use the remaining 30-second end-to-end generation deadline, so the caller timeout precedes the meter cap. This prevents the proxy's shorter timeout from returning a synthetic 502 before the client deadline. This is configuration/code-path review, not a live timeout test.

The saved cumulative meter snapshot remains 6/10 embeddings, 15/20 token-count attempts, and 3/5 generations. The offline fixture run did not change it. Reconcile the actual local meter immediately before a separately approved live run.

**Readiness: LIVE READY for a separately approved verification, not live PASS.** Proposed order: Probe A is one minimal synthetic `generateContent` call with Flash-Lite, `MINIMAL`, no schema/tools/media, and no countTokens. Stop on failure. Only if it succeeds, run one positive live Gateway flow, budgeted at 3 embeddings + 4 token-counts + 1 generation. If both pass, projected cumulative totals are 9/10, 19/20, 5/5. Replay and foreign-owner denial should add no calls. No retry, fallback, Newman, concurrency test, or second provider is included. The previous live structured 3.5 response still has an unresolved `400 INVALID_ARGUMENT`; this plan is bounded to establish whether minimal generation works and then verify the production-shaped path once.

No live request was sent for this readiness check. The live smoke remains opt-in and skipped by the offline suite. See the full [Gemini verification report](../../docs/planning/stylist/EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md) and the ignored Gateway evidence directory above.

## Current positive live Gateway result — 2026-11-07

The approved run used gemini-3.5-flash-lite for structured generation and gemini-embedding-001 for all three 1536-dimensional embeddings. Meter changed from 9/19/5 to 12/23/6 (embedding/countTokens/generation); the final limits were 12/23/6. All eight provider operations returned HTTP 200. The Gateway flow completed intake RECEIVED, generation SUCCEEDED, TOP+BOTTOM output, list/detail read-back, terminal replay with zero extra calls and Customer B denial with zero extra calls.

Gateway and Stylist were real local processes; PostgreSQL 18, Qdrant v1.20.0 and Neo4j 5.26 were disposable; Commerce and JWKS were fixtures and the JWTs were fixture-signed. The live run was direct HTTP through Gateway, not Newman. The automated suite separately reports 191 tests with 1 live-only test skipped. Safe evidence is in stylist-service/target/ai-stylist-handover-live-20261107/live-run-report-safe.json and the summary handover is ../../docs/qa/stylist/AI_STYLIST_HANDOVER_REPORT.md. No persistent database was touched.
