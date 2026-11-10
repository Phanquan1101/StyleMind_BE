# EP-07 Gemini Free-Tier Adapter and Verification Report

**Date:** 2026-11-06<br>
**Branch / starting HEAD:** `feature/ep-07-ai-stylist` / `c9f20d1d1fc1779eca735b3de3ac9f1745b43985`<br>
**Scope:** Stylist local/test provider adapters and disposable verification only. No commit or push.

## Result

Gemini adapters are implemented behind the existing embedding and structured-generation ports. Provider choice is explicit (`STYLEMIND_AI_PROVIDER=gemini`); a Gemini key never selects Gemini implicitly. The extra live-call gate defaults to off. Existing OpenAI adapters remain available and covered by regression tests.

The offline and disposable regression suite passed before this live attempt. The original 2026-11-06 run was blocked and made zero external calls. On 2026-11-07 the owner confirmed the selected AI Studio project is Free Tier with billing off, and the required variables were present in local `.env`. Real model-list and embedding calls then succeeded, but the smoke stopped at the first adapter vector: the provider returned 3,072 dimensions while the approved contract and adapter require 1,536. No generation or Gateway pipeline call was made. During later local diagnostics, a broad environment search accidentally emitted the `.env` key line into a tool result; that key must be treated as exposed and rotated before any further live call. See the dated evidence below. No secret value is recorded here.

Google's current model catalog lists `gemini-2.5-flash` and `gemini-embedding-001`. Google also notes that access to Gemini 2.5 models is limited for projects that have not used them before; project-specific availability is therefore still unverified. The Free Tier pricing page lists Gemini 2.5 Flash standard input/output as free, but does not prove this API key's project is Free Tier. [Models](https://ai.google.dev/gemini-api/docs/models?hl=en), [pricing](https://ai.google.dev/gemini-api/docs/pricing?hl=en).

## Implementation

- `GeminiDeveloperApiClient` calls the fixed Gemini Developer API origin with `x-goog-api-key`, redirects disabled, bounded response reads, finite request deadlines, and no adapter retry or provider/model fallback. Provider error bodies and credentials are not logged or returned.
- `GeminiStructuredRecommendationModel` uses `gemini-2.5-flash`, countTokens preflight, a 6,000 prompt-token ceiling, a 2,000 output-token ceiling, zero thinking budget, JSON structured output, and the existing strict Java parser/whitelist checks. The model name is in the GenerateContent URL; the nested `generateContentRequest` sent to countTokens carries its required model identifier. [GenerateContent API](https://ai.google.dev/api/generate-content?hl=en), [token counting](https://ai.google.dev/gemini-api/docs/generate-content/tokens?authuser=110).
- The countTokens preflight covers the prompt input supplied to it, including system instruction and contents. It does not count output/thinking tokens or the response JSON schema; this preflight is not a total-token or billing estimate. [Google AI Developers Forum: countTokens scope](https://discuss.ai.google.dev/t/gemini-api-counttokens/188451).
- `GeminiRuntimeEmbeddingAdapter` uses `gemini-embedding-001`, `RETRIEVAL_QUERY` / `RETRIEVAL_DOCUMENT`, 1,536 output dimensions and an explicit countTokens input-limit preflight. It rejects over-limit text, non-finite/zero/wrong-sized vectors; reduced-dimension vectors are normalized locally, as required for this model. The Developer API request does not send `autoTruncate`; current evidence does not establish provider-side truncation behavior. [Embeddings guide](https://ai.google.dev/gemini-api/docs/embeddings), [EmbedContent API](https://ai.google.dev/api/embeddings?hl=en).
- Runtime Qdrant identity includes provider, model, dimensions and preprocessing revision. Gemini receives a separate alias namespace. Existing aliases are not overwritten; OpenAI vectors cannot satisfy Gemini readiness even though both profiles are 1,536-dimensional.
- No public endpoint, DTO, lifecycle, V1–V3 migration, Commerce/Wardrobe/Identity integration, other service, or persistent data was changed for this slice. Tests use synthetic fixtures only.

## Verification evidence

| Command | Result |
|---|---|
| `mvn -B -ntp -f stylist-service/pom.xml -Dtest=RuntimeCatalogImportRunnerIntegrationTests test` | 2 tests; 0 failures, 0 errors, 0 skipped. Importer fixture owns and removes only its exact test alias and collection. |
| `mvn -B -ntp -f stylist-service/pom.xml -Dtest=GeminiAdapterHttpContractTests,AiProviderSelectionTests,RuntimeQdrantGenerationIntegrationTests,OpenAiAdapterHttpContractTests,RuntimeCatalogImportRunnerIntegrationTests test` | 27 tests; 0 failures, 0 errors, 0 skipped. Gemini/OpenAI endpoints were local HTTP fixtures. |
| `mvn -B -ntp -f stylist-service/pom.xml verify` | **BUILD SUCCESS; 179 tests in 21 suites, 0 failures, 0 errors, 0 skipped.** Surefire XML totals agree. |

The full run used Docker Linux Engine 28.5.2 and disposable Testcontainers resources with random mapped ports, including PostgreSQL 18 (`postgres:18-alpine`), Qdrant `qdrant/qdrant:v1.20.0`, and Neo4j `neo4j:5.26-community`. Testcontainers/Ryuk cleaned the containers. `docker ps --filter label=org.testcontainers=true` returned no remaining test containers afterward. No Compose project, persistent database, shared Qdrant, or live Commerce was accessed. The import regression uses a local HTTP Commerce and OpenAI-compatible embedding fixture; it does not send a live catalog.

Focused checks included provider-selection/missing-key failure, Gemini generation/embedding serialization, model/dimension/task type, vector validation/normalization, structured output parsing and refusal/truncation/error paths, timeout/quota/error no-fallback behavior, existing OpenAI contract regression, real Qdrant alias/provider separation, and disposable catalog import. These are local fixture or disposable-container results, not provider availability or live pipeline evidence.

The first full-suite attempt exposed a fixture setup collision: the `catalog-import` profile automatically ran the importer when the Spring context started, then the test explicitly ran it again against the same alias. Production correctly rejected alias overwrite. The fixture now disables startup import, invokes the runner explicitly, and removes only its own exact alias and collection. The focused importer test and subsequent full suite both passed.

## Historical external-call state — 2026-11-06

| Operation | Real Gemini calls | Usage / latency |
|---|---:|---|
| `countTokens` | 0 | N/A |
| `embedContent` | 0 | N/A |
| `generateContent` | 0 | N/A |

That run made no external calls. It did not verify billing for a later run. The checked-in Postman collection remained structurally usable for existing routes; no Postman/Newman runtime was run in that task. A fixture-signed JWT or previous OpenAI-compatible Gateway fixture does not establish Identity-issued-token/Gemini integration.

## Live attempt — 2026-11-07 (partial; stopped safely)

The owner confirmed Free Tier and billing disabled for the project. The selected key returned HTTP 200 for model listing, and `models.list` advertised `generateContent`/`countTokens` for `gemini-2.5-flash` and `embedContent`/`countTokens` for `gemini-embedding-001`. The exact project rate-limit ceilings were not available from the API process; Google says active quotas are project/model dependent and are viewed in AI Studio. No 429 occurred before stopping. [Google rate limits](https://ai.google.dev/gemini-api/docs/rate-limits), [Google billing tiers](https://ai.google.dev/gemini-api/docs/billing).

| Operation | Real calls | Provider result / usage |
|---|---:|---|
| `models.list` metadata preflight | 1 | HTTP 200, 525 ms; both selected models and required methods listed |
| Embedding `countTokens` | 2 | HTTP 200; 16 and 13 input tokens; 134 ms and 586 ms |
| Embedding `embedContent` | 2 | HTTP 200; 427 ms and 454 ms; diagnostic response had 3,072 finite numeric values, raw L2 norm about 0.999999936 |
| Generation `countTokens` | 0 | Not reached |
| Generation `generateContent` | 0 | Not reached |

Both embedding requests used synthetic garment/query text. The adapter smoke failed with `GENERATION_PROVIDER_PROTOCOL_ERROR` because it correctly rejects a vector whose length differs from 1,536. At that time the client source and its local fixture used `embedContentConfig.taskType`, `outputDimensionality: 1536`, and `autoTruncate: false`; the meter recorded status and response metadata, not a captured upstream request body. The response was 3,072 dimensions. That establishes the observed mismatch, but does not establish whether the backend ignored the dimension, the unsupported field affected processing, or another runtime/protocol factor caused it. Do not read this historical observation as a proven provider defect. [Google embedContent REST schema](https://ai.google.dev/api/embeddings?hl=en), [Google embeddings guide](https://ai.google.dev/gemini-api/docs/embeddings).

The standalone response-shape diagnostic command `mvn -B -ntp -o -f stylist-service/pom.xml -Dtest=GeminiLiveSmokeTests#probesOneAdditionalSyntheticEmbeddingResponseShape test` passed 1 test (0 failures, errors, or skips) and confirmed 3,072 dimensions. The end-to-end live adapter smoke command `mvn -B -ntp -o -f stylist-service/pom.xml -Dtest=GeminiLiveSmokeTests#verifiesProjectModelAccessSyntheticEmbeddingsAndWhitelistGeneration test` ran 1 test (0 failures, 1 error, 0 skips); it stopped at embedding validation with `GENERATION_PROVIDER_PROTOCOL_ERROR`. Generation and Java structured-output parsing were not reached. The offline package command `mvn -B -ntp -o -f stylist-service/pom.xml -DskipTests package` succeeded as a build-only check; it did not run tests. A subsequent environment scan emitted the local API-key line in a tool result. The proxy was stopped immediately, and no further provider calls were made. Rotate/revoke the key before resuming; the replacement must remain only in ignored local configuration. The local `.env` and default application live flag remain `false`; live opt-in was limited to the completed test process.

The Gateway pipeline was **NOT RUN**. No PostgreSQL, Qdrant, Neo4j, Gateway, Commerce fixture, or Stylist runtime containers were started for this attempt; no persistent database or shared Compose resource was accessed. Therefore generation, database persistence, result read-back, terminal replay, foreign-owner-before-provider behavior, and gateway integration are **NOT VERIFIED**. Full offline verification was not rerun because no production code was changed. `git diff --check` completed without whitespace errors (Git only reported existing line-ending conversion warnings).

Resume gate: owner rotates the exposed key, confirms the replacement is installed locally, and confirms the same project remains Free Tier with billing off and model quota available. Then run one metered synthetic embedding probe using the corrected direct-endpoint payload. If it returns a valid 1,536 vector and the adapter accepts/normalizes it, generation smoke may proceed, followed by the separately gated disposable Gateway pipeline. Do not change model, truncate vectors, enable billing, or proceed to Gateway unless the embedding adapter passes its dimension/finite/normalization checks.

## Wire-contract review and offline correction (2026-11-07)

**Protocol in use:** `GeminiDeveloperApiClient` sends `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent` to the Gemini Developer API (`v1beta`). Its single-request REST body is `EmbedContentRequest`: `model`, `content`, and nested `embedContentConfig`. The official live `v1beta` discovery document defines `embedContentConfig` on this request, and that config contains `taskType` and `outputDimensionality`. The embeddings guide documents `gemini-embedding-001` output sizes 128–3072, recommends 1536, and states the model defaults to 3072. Therefore the direct endpoint's nested config shape and 1536 value are consistent with the REST contract; flattening them based on a different SDK operation would be incorrect. [Gemini Developer API v1beta discovery schema](https://generativelanguage.googleapis.com/$discovery/rest?version=v1beta), [EmbedContent REST API](https://ai.google.dev/api/embeddings?hl=en), [Embeddings guide](https://ai.google.dev/gemini-api/docs/embeddings).

**SDK/backend distinction:** This project does not include `com.google.genai:google-genai`; the client is a direct REST transport. The official Google Gen AI Java SDK source at tag `v1.79.0` (release commit `823bfe6`) chooses `:batchEmbedContents` for its Gemini Developer API / MLDV path. Its `embedContentConfigToMldev` converter places `taskType` and `outputDimensionality` directly on each `requests[]` item. That is a different endpoint and body shape from this project's single `:embedContent` request. For `autoTruncate`, the MLDV converter has no serialization mapping and rejects a nonzero setting (`true`); a `false` default is not emitted as a Developer API wire guarantee. The project therefore stopped sending `autoTruncate=false`; the existing countTokens preflight remains the input-length guard. No provider-side truncation guarantee is claimed. [Google Java SDK Models.java v1.79.0](https://github.com/googleapis/java-genai/blob/v1.79.0/src/main/java/com/google/genai/Models.java), [SDK v1.79.0 release](https://github.com/googleapis/java-genai/releases/tag/v1.79.0).

**Proxy check:** The live meter proxy reads the incoming request bytes into a `Buffer` and now passes that same buffer to `fetch` without parsing or serializing JSON. `node --test stylist-service/scripts/gemini-meter-proxy-forwarding.test.cjs` verifies the unchanged buffer identity/bytes, target URL/query and selected headers using a synthetic payload. This is a local forwarding test; it does not prove what a previous live process sent or what Google's server applied.

**Implementation and regression:** The adapter still uses `gemini-embedding-001`, `v1beta`, the `:embedContent` endpoint, `RETRIEVAL_QUERY` / `RETRIEVAL_DOCUMENT`, and 1,536 dimensions. Only the unsupported/unverified `autoTruncate` request field was removed. `GeminiAdapterHttpContractTests` now compares query and document requests against an independent golden fixture based on the Developer API v1beta discovery schema, checks the exact field locations and absence of other fields, accepts and normalizes a 1,536-value response, and rejects both 1,535 and default-sized 3,072 vectors. Provider errors, invalid vectors and oversize inputs still result in one attempt with no retry/fallback. The offline regression is not a live Gemini confirmation.

**Correction to earlier inference:** The 3,072 response remains an observed symptom, not a demonstrated cause. The old report correctly recorded that the response did not match the 1,536 contract, but its suggestion that Google failed to honor a correctly wired direct request was too strong. The direct endpoint schema agrees with the nested configuration shape; the official SDK's flattened shape applies to its separate batch endpoint. Removing `autoTruncate` addresses the unsupported field, but cannot itself explain the dimension discrepancy. A post-correction live probe is still required to determine whether the single `:embedContent` endpoint returns 1,536 when this payload is used. No live call was made in this follow-up because the previous key was exposed and owner rotation is pending.

**Verification — 2026-11-07:** `mvn -B -ntp -f stylist-service/pom.xml -Dtest=GeminiAdapterHttpContractTests test` passed **8 tests, 0 failures, 0 errors, 0 skipped**. `node --test stylist-service/scripts/gemini-meter-proxy-forwarding.test.cjs` passed **1 test, 0 failures, 0 skipped**. After changing production request serialization, `mvn -B -ntp -f stylist-service/pom.xml verify` completed **BUILD SUCCESS: 181 tests across 22 suites, 0 failures, 0 errors, 2 skipped** (confirmed by Surefire XML). The 2 skips are the gated live Gemini smoke tests; they are not counted as live or full acceptance passes. Disposable PostgreSQL 18, Qdrant `v1.20.0`, and Neo4j `5.26-community` Testcontainers ran and were cleaned by Testcontainers/Ryuk. No Compose, persistent database, shared Qdrant, live Gemini provider, Gateway or Commerce runtime was used. No key or `.env` was read.

**Current status:** Offline wire-contract correction and regression are verified. Gemini embedding live verification remains **PENDING rotated key**; generation and Gateway pipeline remain **NOT RUN**. Do not query Qdrant with an index whose embedding profile does not pass the live dimension check. The local live-call default remains `false`.

## Planning guard — 2026-11-07

`node scripts/verify-stylist-planning.cjs` and `node scripts/verify-stylist-planning.cjs --phase P07` both exited 1. The guard reports committed-range drift in eight Commerce paths, fingerprint drift in `stylist-service/pom.xml`, `stylist-service/src/main/resources/application.properties`, `stylist-service/src/main/java/com/stylemind/stylist/security/SecurityConfiguration.java`, and three Commerce product paths. These warnings predate this Gemini verification; no Commerce source, manifest fingerprint, `reviewed_head`, or workbook was changed to hide them.

## Planning guard and status

Both `node scripts/verify-stylist-planning.cjs` and `node scripts/verify-stylist-planning.cjs --phase P07` exit **1** after registering this report: 21 sources, 22 documents, 118 links, 10 JSON examples and 15 Mermaid blocks. They report committed-range drift in eight Commerce files (`pom.xml`, three US-03 Postman guides, three collections and `CommerceServiceApplication.java`), Stylist fingerprint drift in `pom.xml`, `application.properties` and `SecurityConfiguration.java`, and Commerce fingerprint drift in `ProductController.java`, `VariantResponse.java` and `ProductVariantRepository.java`. No broken local link remains. No fingerprint, `reviewed_head`, Commerce source or workbook was updated to hide the warnings. The planning guard is not a provider/live acceptance check.

This work does **not** mark R95, R99, P08 or EP-07 complete. Recommendation quality, live Commerce data, live Identity-issued JWT/Gateway acceptance, production provisioning and all remaining backlog gates remain outstanding.

## Security note

An API key pasted into chat should be revoked/rotated at its provider. The replacement belongs only in a local ignored secret store or process environment as `STYLEMIND_GEMINI_API_KEY`; never place it in source, Postman exports, logs, documentation or commit history. Keep `STYLEMIND_GEMINI_LIVE_CALLS_ENABLED=false` until Free Tier, model access and quota are independently confirmed for that exact project.

## Owner-confirmed key rotation and bounded follow-up — 2026-11-07

The owner confirmed that the old key was revoked, a replacement was installed in local `.env`, and it remains on the same Free Tier project with billing off. The smoke process read only `STYLEMIND_GEMINI_API_KEY`; its value was not printed or saved. Live opt-in existed only in the temporary Maven process. The project/quota confirmation is owner-provided; no numeric Google quota ceiling was queried in this follow-up.

### Embedding A/B and adapter result

| Probe | Endpoint / request | Result |
|---|---|---|
| A | Developer API `v1beta`, `gemini-embedding-001:embedContent`; `embedContentConfig.taskType=RETRIEVAL_DOCUMENT`, `outputDimensionality=1536`, no `autoTruncate` | HTTP 200, 3,072 finite values, raw L2 norm ~0.999999936, 451 ms. This reproduces the dimension mismatch. |
| B | Synchronous `v1beta`, `gemini-embedding-001:batchEmbedContents`; same model, exact synthetic text/task/dimension; SDK MLDV wire shape, not the asynchronous Batch API | HTTP 200, one response, 1,536 finite values, raw L2 norm ~0.6893323513, 1,370 ms. |
| Post-switch document/query | Production adapter using the synchronous batch endpoint | Both HTTP 200, 1,536 values, finite and normalized by the adapter. Document raw norm ~0.6905947485 (917 ms); query raw norm ~0.6974925762 (427 ms). Their input token counts were 16 and 14. |

This proves different observed behavior between the two Developer API routes for the same A/B input; it does not establish Google's internal cause. The approved model, dimension, task types, port, local normalization, public API, and business rules remain unchanged. The production client now sends the explicit synchronous batch request and maps its single returned embedding into the existing adapter shape. No retry or runtime fallback was added.

The official current `v1beta` discovery schema describes the batch request as `requests[]` entries with `model`, `content`, `taskType`, and `outputDimensionality`; it defines `outputDimensionality` as an integer. The batch wire fixture is tagged with the independently checked Google Gen AI Java SDK v1.79.0 MLDV shape. [Gemini Developer API discovery schema](https://generativelanguage.googleapis.com/$discovery/rest?version=v1beta), [Google Java SDK v1.79.0 Models.java](https://github.com/googleapis/java-genai/blob/v1.79.0/src/main/java/com/google/genai/Models.java).

### Generation preflight and remaining blocker

The first generation `countTokens` attempt, made after successful synthetic document/query embeddings, returned HTTP 400 in 104 ms. The meter did not retain response bodies. An offline comparison against the official `v1beta` discovery schema found that the client sent `responseFormat.text.mimeType="application/json"`, while the schema's enum for that field is `APPLICATION_JSON` (or `TEXT_PLAIN`). The client and HTTP contract assertion were corrected to `APPLICATION_JSON`; the discrepancy is established from the request and schema, but the first 400's exact provider error was not captured, so it is not proven to be the sole cause.

One isolated post-fix synthetic generation test then also received HTTP 400 during `countTokens`; the client mapped it to `GENERATION_PROVIDER_PROTOCOL_ERROR`. The local meter did not record this second attempt, although the test received an HTTP response. Its destination/error body therefore cannot be independently confirmed. It is conservatively reserved as one additional token-count attempt. No `generateContent` request was made, and no automatic retry or fallback occurred. **Generation and strict Java output parsing have not passed live verification.** [GenerateContent API reference](https://ai.google.dev/api/generate-content?hl=en).

### Verification and bounded-call accounting

- `mvn -B -ntp -o -f stylist-service/pom.xml -Dtest=GeminiAdapterHttpContractTests test`: **8 tests passed, 0 failures/errors/skips**.
- `mvn -B -ntp -f stylist-service/pom.xml verify`: **183 tests, 0 failures, 0 errors, 4 skipped**. The four skips are the opt-in live smoke methods, not passes. Docker Linux Engine 28.5.2 ran disposable PostgreSQL 18, Qdrant `qdrant/qdrant:v1.20.0`, and Neo4j `5.26-community` Testcontainers; those containers were owned by the test run and cleaned by Testcontainers/Ryuk. No Compose, persistent database, shared Qdrant, Commerce runtime, or Gateway was used.
- `mvn -B -ntp -o -f stylist-service/pom.xml -Dtest=GeminiLiveSmokeTests#verifiesSingleSyntheticWhitelistGenerationWithCorrectJsonMimeEnum test`: **1 test, 0 failures, 1 error, 0 skipped**. It stopped at generation `countTokens` HTTP 400; no `generateContent` call occurred. The prior post-switch end-to-end model smoke also stopped at its metered generation `countTokens` HTTP 400 after the document/query checks.
- The local meter's final saved snapshot records `models.list=1`, embeddings=6, token counts=6, generations=0, blocked=0, and no 429. Reserve one more token-count attempt for the post-fix HTTP 400 that the meter missed: conservative total token-count attempts are at least 7 of the 10-call local limit. The safe metadata snapshot is `stylist-service/target/gemini-live-diagnostic-20261107/meter-final-summary.json`; it contains no key, headers, prompt text, response body, or vector values.
- The implementation and regression cover the Gemini transport/response mapping, `GeminiRuntimeEmbeddingAdapter`, structured generation MIME enum, HTTP contract tests, independent batch fixture, opt-in smoke tests, and bounded local meter forwarding. Full `verify` was rerun after production transport and MIME-enum changes.

The disposable Gateway harness needs, at minimum, three live embeddings for the two synthetic catalog records and one request query, plus one generation `countTokens` preflight: four token-count calls. Only three remain under the existing cap after conservatively accounting for the unmetered HTTP 400 attempt. Therefore the Gateway pipeline was **NOT RUN**; no partial run was started. This preserves the cumulative cap rather than exceeding it. Gateway persistence, generation, read-back, terminal replay, and foreign-owner-before-provider remain **NOT VERIFIED**. No database persistent or shared service was touched.

The bounded meter was stopped after the run, its temporary certificate was removed from the current-user certificate store, and generated PFX/truststore files were removed. The safe counter snapshot and non-secret seed/log metadata remain under ignored `target/`. Live calls remain disabled by default. No source/migration outside Stylist, workbook, commit, or push was changed by this follow-up.

**Status at the previous 10-call token-count cap (historical; superseded by the later follow-up below):** embedding passes live only through the synchronous batch route and the post-switch production adapter. Generation was blocked at `countTokens` HTTP 400. Gateway was not run because it could not fit within the remaining cumulative count-token cap. Do not mark P04, R98/R99, or EP-07 done from this evidence.

## Bounded token-count follow-up — 2026-11-07

The owner confirmed the rotated key is installed in local `.env`, belongs to the same Free Tier project, and billing remains off. The key was read only into the temporary Maven process; it was not printed, saved, or passed to the meter proxy. `STYLEMIND_GEMINI_LIVE_CALLS_ENABLED` remains `false` in the local `.env` and application defaults. No general model/quota preflight was repeated.

Before the call, the local meter regression passed and the cumulative counters were reconciled from the safe prior snapshot: metadata 1, embeddings 6, token count 7, generation 0. The token-count total includes six attempts from the prior meter plus the previously unmetered post-fix HTTP 400 attempt. The local cap is now 20 token-count attempts; embedding and generation caps remain 10 and 5. The meter reserves an attempt before forwarding and retains HTTP failures and transport failures. The prior unmapped HTTP 400 is explicitly represented as a historical attempt without inventing unavailable response details.

Probe A made one synthetic request to `POST /v1beta/models/gemini-2.5-flash:countTokens` with the Developer API `CountTokensRequest.contents` envelope (one user content with one text part). The provider returned HTTP 404, code `404`, status `NOT_FOUND`, in 495 ms. The bounded diagnostic contained no field paths. The local Java client returned only its existing generic provider-protocol error; no upstream diagnostic was included in public Problem Details. Because this minimal request failed, the production-payload Probe B, `generateContent`, and the Gateway pipeline were not sent. No retry or fallback occurred, and no 429 was observed.

The response proves only that this request attempt received `404 NOT_FOUND`. It does not identify whether the cause is the model/method availability, the backend route, or another provider-side condition. The request path and minimal body match the Developer API contract already checked offline; there is no field-path evidence for a justified code change. Do not send another probe or start Gateway until a specific cause or changed hypothesis is established.

### Current bounded-call state

| Operation | Cumulative attempts | Cap | Result in this follow-up |
|---|---:|---:|---|
| Embedding | 6 | 10 | No new calls; the previously verified synchronous `batchEmbedContents` route remains the live result. |
| `countTokens` | 8 | 20 | One new minimal request returned 404 `NOT_FOUND`; seven prior attempts are retained. |
| Generation | 0 | 5 | Not called. |

Status remains separate: **embedding PASS** through the synchronous batch route; **token preflight BLOCKED** at Probe A; **structured generation NOT RUN**; **Gateway persistence/read/replay NOT RUN**. Although remaining local caps would numerically permit more calls, the failed preflight is the functional stop condition.

### Verification and cleanup

- `mvn -B -ntp -f stylist-service/pom.xml verify`: **184 tests, 0 failures, 0 errors, 1 skipped**. The sole skip was the opt-in live smoke with the live switch off during the offline suite. The earlier failing provider-selection fixture was updated to supply only a test-local loopback meter address; it does not contact a provider.
- `node --test stylist-service/scripts/gemini-meter-core.test.cjs stylist-service/scripts/gemini-meter-proxy-forwarding.test.cjs`: **8 passed, 0 failed, 0 skipped**. Coverage includes pre-dispatch reservation, HTTP 400 accounting, timeout/connection failures, all routed operation categories, bounded/redacted provider diagnostics, request metadata without prompt values, cap enforcement, and unchanged proxy body forwarding.
- `mvn -B -ntp -f stylist-service/pom.xml -Dtest=GeminiLiveSmokeTests test`: **1 test, 0 assertion failures, 1 error, 0 skipped** because the live provider returned the recorded 404 on Probe A. This is a blocked live acceptance result, not a pass.
- `node scripts/verify-stylist-planning.cjs --phase P07`: **exit 1**. It still reports committed-range drift in eight Commerce paths, source fingerprint drift in `stylist-service/pom.xml`, `stylist-service/src/main/resources/application.properties`, and `stylist-service/src/main/java/com/stylemind/stylist/security/SecurityConfiguration.java`, plus three Commerce product paths. No Commerce source, `reviewed_head`, or fingerprint was changed in this follow-up.
- No Gateway/Newman runtime, no `generateContent`, and no provider retry were run. Full-suite Testcontainers used disposable PostgreSQL 18, Qdrant `v1.20.0`, and Neo4j `5.26-community`; Testcontainers/Ryuk cleaned them. No Compose, persistent database, shared Qdrant, live Commerce, or live Identity token was used.
- The local meter stopped after the attempt. Its temporary self-signed certificate, private key, truststore and OpenSSL config were removed. The safe summary and Maven output are retained under ignored `stylist-service/target/gemini-followup-20261107/`; they contain no API key, authorization header, raw provider body, prompt or vector.
- `git diff --check` and `git diff --cached --check` both exited 0 (Git emitted only existing line-ending warnings). The local `.env`, live defaults, schema, migrations and persistent databases were not changed. No commit or push was made.

**Current status:** the cumulative local budget is preserved (embedding 6/10, token count 8/20, generation 0/5). The only new live attempt returned 404 `NOT_FOUND`; no cause is proven. Do not mark generation, Gateway verification, P04, R98/R99, or EP-07 complete.

## Owner key change retry — 2026-11-07

After the owner changed `STYLEMIND_GEMINI_API_KEY` in local `.env`, one bounded retry of Probe A was run through a newly started loopback meter seeded from the prior safe snapshot. The key value was not printed, logged, or copied into evidence. Starting cumulative counts were embedding 6, token count 8, generation 0; no counters were reset.

The same minimal synthetic `CountTokensRequest.contents` call to `POST /v1beta/models/gemini-2.5-flash:countTokens` again returned HTTP 404, code `404`, status `NOT_FOUND`, in 476 ms, with no field path. The meter charged the attempt. A key change alone therefore did not change this response, but the response still does not prove whether the cause is current model/method availability, project permissions, or another provider-side routing condition. No provider message or raw body was retained.

The live smoke stopped at Probe A again. Probe B, the production preflight, structured generation, Gateway, and Newman were not run; no `generateContent` request or retry/fallback occurred. Current cumulative counts are **embedding 6/10, token count 9/20, generation 0/5**, with one prior unmetered HTTP 400 conservatively included. The retry meter reported no 429 and zero blocked calls. Its process was stopped and only its newly generated certificate, private key, truststore, and config were removed; the safe summary/logs remain under ignored `stylist-service/target/gemini-key-retry-20261107/`.

**Latest status:** embedding remains PASS from the earlier synchronous batch verification; token preflight remains BLOCKED by repeated 404 `NOT_FOUND`; generation and Gateway remain NOT RUN. Do not issue more provider calls until there is an evidence-backed explanation or a targeted diagnostic that can distinguish the cause. EP-07 remains incomplete.

## Owner-requested alternate generation model probe — 2026-11-07

The owner requested trying `gemini-3.5-flash-lite`, `gemini-3.8-flash` and one additional current Flash model after changing the local key. Google’s model list uses those exact IDs; its pricing page lists Free Tier rates for the tested model families ([model list](https://ai.google.dev/gemini-api/docs/models), [pricing](https://ai.google.dev/gemini-api/docs/pricing)). The owner’s earlier confirmation that this remains the same Free Tier project with billing off was reused. The key was read from the ignored local `.env` only into the temporary probe process and was never printed or retained.

The probe built the synthetic TOP+BOTTOM request with the existing `GeminiStructuredRecommendationModel.requestFor` method and performed one minimal and one production-shaped `countTokens` call per model through the cumulative local meter. The production-shaped count used the same system instruction, candidate whitelist and structured schema as the current adapter. Each model accepted the production-shaped preflight at **304 tokens**; each minimal probe returned **8 tokens**. No application model setting was changed.

| Model | Minimal / production-shaped countTokens | One generation attempt | Result |
|---|---|---|---|
| `gemini-3.5-flash-lite` | HTTP 200 / 8 tokens; HTTP 200 / 304 tokens | HTTP 400, `INVALID_ARGUMENT` (1,019 ms); no safe field path | Generation not verified; exact invalid field/cause remains unknown. |
| `gemini-3.8-flash` | HTTP 200 / 8 tokens; HTTP 200 / 304 tokens | Local meter returned HTTP 502 after 15,015 ms | Meter uses `AbortSignal.timeout(15_000)` and records `TimeoutError`; this is not evidence of a model rejection or an upstream response. |
| `gemini-3.7-flash` | HTTP 200 / 8 tokens; HTTP 200 / 304 tokens | Local meter returned HTTP 502 after 15,003 ms | Same local 15-second transport timeout; upstream result is unknown. |

No successful `generateContent` response was received, so the Java response/output parser did not run and no candidate is accepted for Stylist generation. The model list and count-token availability alone are insufficient to select a production model. The service remains configured for its existing `gemini-2.5-flash`; no configuration or source change was made. Because generation preflight/generation did not succeed end to end, Gateway, persistence, read-back and replay were not run.

Call accounting is cumulative from the existing safe snapshot; no counter was reset. At the start of this probe the meter had embedding 6, tokenCount 9 and generation 0. This turn added six `countTokens` attempts (three minimal plus three production-shaped) and three generation attempts. Final local counts: **embedding 6/10, countTokens 15/20, generation 3/5, blocked 0; no 429**. No retry/fallback, OpenAI, database, Compose, or persistent resource was used. The meter was stopped; its temporary TLS key/certificate/truststore/config were removed. Safe counts/results remain under ignored `stylist-service/target/gemini-model-compare-20261107/`.

No Maven suite was run because no production source changed. The parser, Gateway, Postman/Newman runtime and end-to-end generation remain unverified. This comparison does not change the prior planning-guard drift or mark P07, P04, R98/R99, or EP-07 complete.

## Offline-ready pipeline follow-up — 2026-11-07

**Result: OFFLINE READY for the current synthetic local contract.** This status is limited to the offline pipeline, not Gemini live availability, recommendation quality, live Identity/Commerce interoperability, or EP-07 completion.

### Business flow exercised

The customer submits needs and constraints (occasion, style context, budget and exclusions); the customer does not choose products, TOP/BOTTOM items, or an outfit. Request intake stores the criteria as `RECEIVED`; a separate generation call has an empty `{}` body. The backend then runs the existing production flow:

1. Embed the customer request and retrieve candidates from disposable Qdrant.
2. Hydrate candidate details from the local Commerce fixture.
3. Read reviewed knowledge from disposable Neo4j.
4. Run P02 to construct complete outfit candidates with exactly one TOP and one BOTTOM.
5. Send the bounded backend whitelist to the Gemini adapter; the model fixture selects only an allowed outfit alias.
6. Parse and validate the structured result with the production Java parser and whitelist checks.
7. Recheck current Commerce price/status through the fixture and atomically persist recommendation plus both items in disposable PostgreSQL.

This reuses the current service pipeline and Gateway/Newman harness. It does not introduce a demo business path. Only Gemini’s HTTP boundary is mocked; the real client, adapters and Java parser execute.

### Disposable Gateway and Postman evidence

The successful run used `node stylist-service/scripts/wave2-disposable-gateway-harness.cjs` with the Gemini mock flag and live flag false. It started the production Stylist and existing Gateway processes, plus disposable random-port containers: PostgreSQL 18.6 (`postgres:18-alpine`), Qdrant v1.20.0 (`qdrant/qdrant:v1.20.0`), and Neo4j 5.26 (`neo4j:5.26-community`). The containers had no mounted volumes and were removed by the harness `finally` cleanup; `docker ps -a --filter name=stylemind-wave2-` was empty after the successful run. No Compose project, persistent database, shared store, external provider, or `.env` was used.

Commerce returned synthetic fixture data. JWKS signed synthetic JWTs; those tokens were not issued by Identity. Catalog policy and knowledge were synthetic. The Gemini HTTP fixture answered the actual adapter’s `batchEmbedContents`, `countTokens`, and `generateContent` requests. The Postman collection/environment JSON was parsed by the harness, then Newman executed through Gateway: **10 requests, 20 assertions, 0 request/assertion failures**.

| Check | Evidence |
|---|---|
| Customer request and admission | Criteria-only intake returned `RECEIVED`; no outfit was supplied by the client. |
| Complete outfit | Generation returned one successful recommendation containing exactly one TOP and one BOTTOM selected from backend-generated allowed candidates. |
| Read-back and replay | Saved recommendation was readable; terminal replay matched the original generate response and added zero provider-fixture calls. |
| A/B ownership | Customer B received 404 for Customer A’s request before any embedding, token-count or generation fixture call. |
| Concurrency claim | Two HTTP generation requests against one request yielded one 200 winner and one 409 loser; only one reached the model fixture. |
| Persistence | Disposable PostgreSQL contained 2 successful requests, 2 recommendations and 4 recommendation items for the primary and concurrency paths. |
| Mock operations | 5 embedding, 8 token-count and 2 generation HTTP operations total. These are local fixture calls, not live Gemini calls. |

The primary success path and the concurrency winner account for the two mock generation calls. Replay and the foreign-owner request added none. No real Gemini usage budget was consumed. The temporary evidence is under ignored `stylist-service/target/gemini-offline-flow-20261107-attempt-06/`.

### Replay timestamp defect found and fixed

An initial harness assertion incorrectly compared the generation POST DTO against the separate GET read-back DTO. Those DTOs are intentionally different; the harness now compares replay against the original generation response and verifies read-back separately. That corrected check exposed a production response mismatch in `generated_at`: Java supplied nanoseconds while PostgreSQL `TIMESTAMPTZ` retains microseconds. The first generation response could therefore differ from a replay after database reload. Stylist now truncates `generated_at` to microseconds before persistence. A fixed-clock unit regression verifies the precision, and the disposable Gateway replay verifies the response now matches exactly. No API, DTO, schema, migration, lifecycle, or non-Stylist source changed.

### Regression gate

| Command | Result |
|---|---|
| `mvn -B -ntp -f stylist-service/pom.xml -Dtest=StylistGenerationServiceTests test` | 4 tests; 0 failures, 0 errors, 0 skipped. |
| `mvn -B -ntp -f stylist-service/pom.xml verify` | BUILD SUCCESS; 188 tests, 0 failures, 0 errors, 1 skipped. Surefire XML totals agree. The single skipped test is the opt-in live Gemini smoke; it is not a pass. |
| Newman via the disposable Gateway harness | 10 requests, 20 assertions; 0 failures. |
| `node --check stylist-service/scripts/wave2-disposable-gateway-harness.cjs` | Pass. |

The full suite exercised disposable Postgres/Qdrant/Neo4j integration coverage, including hard-budget boundary, changed current price/status/category/demographic and hard-exclusion checks. `StylistGenerationServiceTests` covers terminal replay without another model invocation; `StylistSessionApiIntegrationTests` covers generation claim and finalization rollback behavior. These are synthetic/disposable checks, not fashion quality evaluation.

### Live status and next bounded proposal

No `.env` or Gemini key was read for offline phases. The child Stylist process had no `STYLEMIND_GEMINI_API_KEY`; `STYLEMIND_GEMINI_LIVE_CALLS_ENABLED=false`. The previous cumulative live meter state remains **embedding 6/10, `countTokens` 15/20, generation 3/5**. This run did not alter it. The earlier production-model `countTokens` 404 remains unresolved, so live generation was not called and live Gateway acceptance is not verified.

Proposed next live experiment, subject to the separate approval gate in the original task: after the production preflight blocker is resolved, use one synthetic Gateway generation as the next Probe B, allowing that single generation call to cover the existing adapter/parser/persistence path; replay must add no call. This proposal was not executed. No automatic retry/fallback or paid API was used.

### Planning guard

Both `node scripts/verify-stylist-planning.cjs` and `node scripts/verify-stylist-planning.cjs --phase P07` exit 1 (21 sources, 22 documents, 120 links, 10 JSON examples, 15 Mermaid blocks). The guard still reports committed-range drift in eight Commerce paths plus fingerprint drift in Stylist `pom.xml`, `application.properties`, `SecurityConfiguration.java`, and Commerce `ProductController.java`, `VariantResponse.java`, and `ProductVariantRepository.java`. No source in Commerce, fingerprint, or `reviewed_head` was changed to clear the warning. This is a planning guard, not an application-test result. P04, R98/R99 and EP-07 remain incomplete.

## Offline readiness stabilization — 2026-11-07 (latest)

This follow-up was offline-only. It made no Gemini request, read no `.env` or API key, and did not change the cumulative live meter. The meter remains at the prior reconciled values: embedding **6/10**, `countTokens` **15/20**, generation **3/5**. The production default remains `gemini-2.5-flash`; `gemini-3.5-flash-lite` is available only as an explicit local/test setting.

### Gemini request and failure diagnosis

- The client uses Gemini Developer API `v1beta`, `POST /v1beta/models/{model}:countTokens` and `:generateContent`, JSON bodies, and the `x-goog-api-key` header. Credentials and provider response bodies are not logged or copied into public Problem Details.
- For `gemini-3.5-flash-lite`, the offline request test verifies `generationConfig.thinkingConfig.thinkingLevel=MINIMAL` and `generationConfig.responseFormat.text` with `mimeType=APPLICATION_JSON` and the TOP/BOTTOM whitelist schema. It confirms `thinkingBudget`, `responseMimeType`, and `responseSchema` are absent from that model-specific request. Google’s current model page lists structured outputs and thinking for Flash-Lite; the GenerateContent reference defines `responseFormat.text.mimeType/schema` and the `MINIMAL` thinking enum. [Model capabilities](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite), [GenerateContent API](https://ai.google.dev/api/generate-content), [thinking levels](https://ai.google.dev/gemini-api/docs/thinking).
- That contract alignment does **not** prove why the earlier live 3.5 request returned `400 INVALID_ARGUMENT`: the retained diagnostic had no safe field path. Unsupported schema-subset details, project/model behavior, or another provider validation condition remain unproven. No request was sent to distinguish them.
- The prior `gemini-3.8-flash` and `gemini-3.7-flash` HTTP 502 responses came from the local meter’s then-15-second cutoff, not an observed Google response. The meter now has a 30-second upstream cap, and the client’s full request/response-body wait is bounded by the same 30-second generation deadline. An embedded HTTP regression stalls after headers and verifies timeout mapping. This fixes local deadline enforcement; it does not establish live model latency.
- HTTP 400/404 and other non-timeout 4xx responses map to the generic provider protocol error (502); 408/504 map to timeout (504); 401/403/429/5xx responses map to dependency unavailable (503). Upstream message/body details are not returned. A provider 502 is treated as unavailable (503), while the earlier meter-generated 502 from its own timeout is historical transport evidence only.

The Java parser requires one candidate with `finishReason=STOP`, text-only non-thought parts, valid JSON with exactly the expected fields, and outfit/item aliases drawn from the backend whitelist. It rejects refusal/truncation, duplicate or unknown aliases, malformed JSON, and any result that is not one complete TOP plus one BOTTOM. HTTP fixtures exercise these checks; no live model output has passed them.

### Offline production-pipeline verification

The customer submits needs/criteria at request intake; they do not choose products or a prebuilt TOP+BOTTOM outfit. Intake remains `RECEIVED`; the explicit generate request has body `{}`. The existing Gateway → Stylist pipeline performs retrieval, Commerce fixture hydration, reviewed Neo4j knowledge lookup, P02 candidate construction, the actual Gemini HTTP client, Java parsing/whitelist validation, final Commerce fixture recheck, and atomic recommendation/item persistence. Only the Gemini HTTP boundary is mocked; this is not a separate demo path. This Gateway run used the unchanged default `gemini-2.5-flash`; `gemini-3.5-flash-lite` has focused request-contract tests only and was not the pipeline fixture model.

The latest disposable run is recorded at `stylist-service/target/gemini-offline-ready-20261107/gateway-newman-summary.json`:

- Newman ran the checked-in Postman collection through the actual local Gateway: **10 requests, 20 assertions, 0 failures**. The collection parses as Postman v2.1 with 10 requests.
- The local Gemini HTTP fixture observed **5 embeddings, 8 token-count calls and 2 generations**; these are fixture calls, not external quota usage. The second generation is the PostgreSQL concurrency winner.
- The primary generated recommendation contains one backend-selected TOP and one BOTTOM. Read-back returned persisted output; terminal replay returned the same result with **0 additional fixture calls**.
- Customer B’s attempt to generate Customer A’s request returned 404 before any embedding, token-count or generation call.
- Two concurrent generate requests against the same request returned one 200 winner and one 409 loser. PostgreSQL held 2 successful requests, 2 recommendations and 4 recommendation items across the primary and concurrency flows.
- Runtime components: Gateway and Stylist were application processes; PostgreSQL 18.6, Qdrant `v1.20.0`, and Neo4j `5.26-community` were disposable no-volume containers. Commerce, JWKS, and catalog/knowledge data were synthetic fixtures. JWTs were fixture-signed, not Identity-issued. Newman runtime evidence is not live cross-service acceptance.

### Regression and current boundary

- Focused Maven tests: `GeminiAdapterHttpContractTests`, `GeminiProviderConfigurationTests`, and `StylistGenerationServiceTests`: **19 passed, 0 failures/errors/skips**.
- Node script syntax checks passed; meter core/proxy tests: **8 passed, 0 failures/skips**.
- `mvn -B -ntp -f stylist-service/pom.xml verify`: **BUILD SUCCESS; 191 tests across 23 suites, 0 failures, 0 errors, 1 skipped**. The one skipped test is the opt-in live Gemini smoke and is not a live pass. PostgreSQL 18.6, Qdrant `v1.20.0`, and Neo4j `5.26-community` Testcontainers were used and cleaned by Testcontainers/Ryuk.
- `git diff --check` and `git diff --cached --check` passed (only existing line-ending warnings). The planning guard remains exit 1 (21 sources, 22 documents, 121 links, 10 JSON examples, 15 Mermaid blocks) for the recorded Commerce committed-range drift and Stylist/Commerce source fingerprints; no fingerprint or reviewed head was changed.

**Status:** Mock Gemini, parser, Stylist pipeline, Gateway, disposable persistence, owner isolation, and replay are PASS for the tested synthetic contract. Gemini live generation remains **NOT VERIFIED**; the earlier 3.5 `INVALID_ARGUMENT` and 2.5 token-count 404 remain unresolved. No API, DTO, lifecycle, schema, migration, other service, or persistent database changed. P04, R98/R99, and EP-07 remain incomplete.

### Next bounded live proposal — not run

The previous safe meter snapshot leaves two generation calls. If separately approved, Probe A can use the existing minimal plain-text smoke with `gemini-3.5-flash-lite`, one `generateContent`, no schema/tools/media, and the current 30-second client/deadline cap. No additional token-count call is needed for this minimal probe; this model already passed prior synthetic minimal and production-shaped `countTokens` probes. If A does not return a valid text candidate, stop without Probe B.

Only if A passes, Probe B can use the positive Gateway path in the existing disposable harness, with the structured TOP+BOTTOM pipeline and synthetic catalog/knowledge. It needs 3 embeddings, 4 token-count calls, and 1 generation. From the prior cumulative 6/10 embeddings, 15/20 token counts, and 3/5 generations, the planned total after A+B would be **9/10, 19/20, and 5/5**, respectively. Replay and the foreign-owner check must add no provider calls. Do not run Newman, negative/concurrency calls, retry, fallback, OpenAI, or any other provider call during this bounded live plan. Use the existing 30-second generation deadline; do not increase it for the probe.

No live call is authorized by this offline verification result. Wait for explicit owner approval before Probe A; Probe B remains conditional on A passing.

### Final model-specific readiness check — 2026-11-07

This follow-up ran the existing disposable Gateway/Newman harness with the selected model pinned to `gemini-3.5-flash-lite`. It did not send a Gemini request, read `.env` or the API key, or use the local live meter. The mock fixture rejected any other generation model and asserted the model-specific request shape.

- Runtime `countTokens` used endpoint model `gemini-3.5-flash-lite` and nested request model `models/gemini-3.5-flash-lite` on all 3 generation-token preflight calls.
- Runtime `generateContent` used endpoint model `gemini-3.5-flash-lite` on both calls (main flow and the PostgreSQL concurrency winner).
- Both request types used `thinkingLevel=MINIMAL`, `responseFormat.text.mimeType=APPLICATION_JSON`, and a structured schema. The fixture rejected legacy `thinkingBudget`, `responseMimeType`, and `responseSchema` fields.
- The exact safe evidence is under ignored `stylist-service/target/gemini35-gateway-offline-20261107-final/gateway-newman-summary.json`. It records 10 Newman requests / 20 assertions with no failures; 5 embedding, 8 total token-count (including embedding preflights), and 2 generation fixture calls; replay added no calls; Customer B was denied before provider work; concurrent generation returned one 200 and one 409; disposable PostgreSQL held 2 successful requests, 2 recommendations and 4 items.
- Gateway and Stylist were local application processes. PostgreSQL `18-alpine`, Qdrant `v1.20.0`, and Neo4j `5.26-community` were random-port, no-volume disposable containers. Commerce, JWKS, catalog and knowledge were synthetic fixtures. The harness removed its containers and temporary runtime JWT files. This is not live Commerce, live Identity, or Gemini acceptance.

### Timeout and cumulative meter check

The Stylist generation deadline is 30 seconds; the Gemini Java client maximum is 30 seconds; and the local metering proxy's upstream cutoff is 30 seconds. In the Gateway path, each client operation receives the remaining end-to-end deadline, so its deadline is below the proxy's per-call cap (the generation call also follows token preflight). The direct minimal Probe A now passes a 25-second request timeout to the client, leaving a five-second margin below the proxy cap. Thus the planned caller deadline precedes the local proxy timeout; no timeout was exercised against a provider in this task.

The latest saved cumulative meter snapshot remains **6/10 embeddings, 15/20 token-count attempts, and 3/5 generations**, with 0 blocked calls and no recorded 429. The offline Gateway fixture does not update that meter. Re-read and validate the live meter immediately before any separately approved live probe; a saved snapshot is not a substitute for that preflight.

### Readiness and final bounded live plan — not executed

**Status: LIVE READY for a separately approved, bounded verification; Gemini live generation is still NOT VERIFIED.** Offline model selection, request shape, Gateway flow, persistence, replay and timeout ordering now pass. This readiness status is not a provider-success claim. The earlier live 3.5 structured request still has an unresolved `400 INVALID_ARGUMENT` cause.

1. **Probe A — one minimal generation:** `gemini-3.5-flash-lite`, short synthetic text only, `MINIMAL`, no schema/tools/media, one generation call, no countTokens call, 25-second client request timeout. If it fails, stop and do not run B.
2. **Probe B — one positive Gateway generation, only after A passes:** use the existing disposable Gateway harness and real Gemini HTTP adapter, with synthetic Commerce/JWKS/catalog/knowledge and disposable PostgreSQL/Qdrant/Neo4j. Budget is 3 embeddings, 4 token-count attempts and 1 generation. Verify successful TOP+BOTTOM parsing, persistence/read-back, terminal replay with no new call, and foreign-owner denial before provider calls. Do not run Newman, concurrency, additional negative calls, retries, fallback, or another provider in the live window.

If both probes complete, cumulative usage is projected at **9/10 embeddings, 19/20 token-count attempts, and 5/5 generations**. Each dispatched failure still consumes the local budget; stop on 429 or any failed preflight/generation and do not retry. Reconcile the meter immediately before Probe A. No live call was authorized or sent in this readiness task.

The latest regression command, `mvn -B -ntp -f stylist-service/pom.xml verify`, completed **BUILD SUCCESS: 191 tests, 0 failures, 0 errors, 1 skipped**. The skipped case is the opt-in live Gemini smoke and is not a pass. The planning guard still reports the pre-existing Commerce committed-range drift and source fingerprint drift; no manifest fingerprint or `reviewed_head` was refreshed. P04, R98/R99 and EP-07 remain incomplete.

## Read-back assertion correction — 2026-11-07

**Offline result: PASS for the synthetic Gateway flow.** This section records a new fixture run and clarifies the earlier live attempt. It does not change that live attempt into a passing end-to-end Probe B.

### Confirmed response shapes and original mismatch

The production DTOs and Wave 2 contract intentionally expose different response shapes:

- `POST .../generate` returns `request_id`, `request_status`, `recommendations`, and `limitations`. Generation items contain `source_type`, `shop_variant_id`, `slot_name`, `selection_reason`, and `display_order`.
- `GET .../requests/{requestId}/recommendations` returns `request_id`, `request_status`, and persisted recommendations. Its items also expose nullable `wardrobe_item_id`; for this shop-only fixture that value is `null`.
- `GET .../recommendations/{recommendationId}` returns its own detail contract, including current Commerce metadata, `current_availability`, `total_current_price_vnd`, and `price_semantics=CURRENT_COMMERCE_DATA_NOT_HISTORICAL`.

The fixture pipeline’s JSON path diagnostic found the expected structural differences only: `$.limitations` (generation-only) and `$.recommendations[0].items[0].wardrobe_item_id` / `$.recommendations[0].items[1].wardrobe_item_id` (GET-list-only). Both responses independently passed their contract checks; all shared business fields passed comparison, including request/status, recommendation ID, explanation, styling tips, generated timestamp, item count, source, variant, slot, selection reason, and display order. The report artifact contains field paths and statuses only, not response values, tokens, or credentials.

Replay is compared generation-response to generation-response, including the original timestamp and stored content. Detail is validated against its own DTO and the fixture’s current Commerce values/status. No production DTO or API contract was changed.

### Offline Gateway flow and evidence

The existing disposable Gateway harness ran with the Gemini HTTP fixture enabled and live calls disabled. Request intake remained criteria-only and `RECEIVED`; generation returned one `SUCCEEDED` recommendation with exactly one TOP and one BOTTOM. GET list and detail each returned 200; detail reported current prices totaling **550,000 VND**, both items `AVAILABLE`, and the contract’s current-data price semantics. Terminal replay returned 200 and added **zero** fixture calls. Customer B received the owner-concealing 404 and added **zero** fixture calls.

Disposable PostgreSQL ended with 1 succeeded request, 1 recommendation, 2 recommendation items, and 0 assistant messages. The Gemini HTTP fixture observed **3 embeddings, 4 countTokens calls, and 1 generation** across two synthetic catalog imports and the positive request flow. These are fixture calls, not external Gemini usage. Gateway and Stylist were local application processes; Commerce and JWKS were fixtures; PostgreSQL 18.6, Qdrant `v1.20.0`, and Neo4j `5.26-community` used random host ports and disposable containers. The harness cleanup removed them; no `stylemind-wave2-*` containers remained. No Compose service, shared store, persistent database, provider, or `.env` was used. Newman was not run; this was seven direct HTTP requests through the real local Gateway.

Evidence is preserved under ignored `stylist-service/target/gemini-readback-offline-20261107-9dcfa26ab0e64f088a5b40280b8a91b2/`, including `gateway-newman-summary.json`, `read-back-field-path-diagnostic.json`, and redacted Gateway/Stylist/container logs.

### Harness correction and tests

The first disposable attempt stopped after intake, before generation or provider-fixture requests, because the harness referenced an undefined `meterStart`. That test-harness reference now uses the flow’s `providerStart` snapshot. The rerun completed successfully and proves the positive path, reads, replay, and owner denial without external calls.

- `node --check stylist-service/scripts/wave2-recommendation-response-assertions.cjs` and `node --check stylist-service/scripts/wave2-disposable-gateway-harness.cjs`: PASS.
- `node --test stylist-service/scripts/wave2-recommendation-response-assertions.test.cjs`: **11 passed, 0 failed, 0 skipped**. The tests reject changed variant IDs, missing items, wrong slot/order/source, changed recommendation ID/timestamp/content, wrong/null-missing wardrobe references, bad replay, and mismatched current detail price.
- `node stylist-service/scripts/wave2-disposable-gateway-harness.cjs` with `STYLEMIND_AI_PROVIDER=gemini`, `STYLEMIND_GEMINI_MOCK_ENABLED=true`, `STYLEMIND_GEMINI_MOCK_POSITIVE_FLOW_ONLY=true`, and `STYLEMIND_GEMINI_LIVE_CALLS_ENABLED=false`: **PASS**, 7 Gateway HTTP requests; no Newman run.
- No Java production source changed, so the Maven suite was not rerun. `git diff --check` and `git diff --cached --check` both exit 0 (only existing line-ending warnings).

### Live evidence boundary and next approval proposal

The preceding live attempt did receive a successful Gemini generation, but its complete Probe B was not accepted because the harness used an invalid deep comparison between generation and GET-list DTOs. The new path-level diagnostic and passing Gateway flow are **fixture-only**; they do not prove live GET-list, current Commerce detail, replay, or owner-isolation behavior. Probe B remains **INCOMPLETE / not PASS**; no status is retroactively upgraded. The live meter was not read or changed in this offline task.

To re-run a complete positive live Gateway flow against fresh disposable state, the harness plan is **+3 embeddings, +4 countTokens attempts, and +1 generation**; replay, detail reads, and Customer B denial should add zero provider calls. Based on the last handed-off live meter of 9/19/5, that would require proposed cumulative ceilings of at least **12 embeddings / 23 countTokens / 6 generations**. This is only a proposal: reconcile the actual cumulative meter immediately before any future live run, obtain approval for the increased generation ceiling, and stop if the verified count exceeds the plan. No live call was made here.

EP-07 and its broader acceptance gates remain incomplete.

## Final positive Gateway run — 2026-11-07

This addendum supersedes earlier blocked/readiness snapshots for the specific approved run below while preserving them as history. The selected project and billing state were confirmed by the owner as Free Tier with billing disabled. No key value or .env contents are recorded here.

Generation used gemini-3.5-flash-lite; embeddings used gemini-embedding-001, synchronous batchEmbedContents, 1536 dimensions and the already verified normalization path. The live meter started at 9 embeddings / 19 countTokens / 5 generations, reserved each attempt, then finished at 12 / 23 / 6. All three embedding calls, four token-count calls and one generation returned HTTP 200. No retry, fallback, OpenAI call, blocked meter attempt or 429 occurred. Generation latency was 3774 ms with 304 prompt and 157 output tokens.

One positive direct HTTP flow passed through the local Gateway and real Stylist. Intake returned RECEIVED; generation returned SUCCEEDED with exactly one TOP and one BOTTOM; list and detail reads returned 200; the current Commerce fixture total was 550000 VND; terminal replay added zero provider calls; owner B received 404 before provider calls. Disposable PostgreSQL held one succeeded request, one recommendation, two recommendation items and zero assistant messages.

Gateway, Stylist, PostgreSQL 18, Qdrant v1.20.0 and Neo4j 5.26 were real local/disposable components. Commerce and JWKS were fixtures; JWTs were fixture-signed rather than Identity-issued. The live generation input and catalog/knowledge data were synthetic. Newman was not run for the live flow. Testcontainers and exact harness containers were removed; no persistent database or shared volume was touched.

The offline regression remains separate: Stylist verify passed 191 tests, 0 failures, 0 errors and 1 live-only skipped test. Offline Newman passed 10 requests / 20 assertions. The meter has reached the approved current cap of 12/12 embeddings, 23/23 countTokens and 6/6 generations; no more live call is authorized by this profile. R95/R99, real Commerce/Identity acceptance, human quality evaluation and EP-07 remain incomplete. See the [handover report](../../qa/stylist/AI_STYLIST_HANDOVER_REPORT.md) and ignored safe evidence under stylist-service/target/ai-stylist-handover-live-20261107/.
