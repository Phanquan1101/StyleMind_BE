# EP-07 P03-B1 follow-up — findings and verification

**Date:** 2026-11-05
**Branch:** `feature/ep-07-ai-stylist`
**HEAD:** `c9f20d1d1fc1779eca735b3de3ac9f1745b43985` (unchanged)
**Status:** P03-B1 follow-up findings are closed for the approved shop/manual intake and read-back scope. P03, US-07.01, and EP-07 remain **PARTIAL**.

This follow-up fixes review findings without changing the approved APIs, response DTOs, `RECEIVED` state, `generation_started=false`, V1/V2, or business scope. It adds no generation, owned context, provider/LLM, P04.3, service integration, or persistent database changes.

## Findings

### F-01 — encoded route bypassed GET-body policy

**Before:** `SessionRequestPolicyFilter` compared the raw `getRequestURI()` string. The independent embedded-server probe showed canonical GET with a body returned `400` with zero service reads, while `/api/v1/stylist/%72equests/{id}` and `/api/v1/%73tylist/requests/{id}` returned `200` and called the service. Those encoded paths were accepted by Spring MVC as the request-resource route.

**Change:** The filter now builds exact route matchers with `PathPatternRequestMatcher` and the `PathPatternParser` from the qualified Spring MVC `requestMappingHandlerMapping`. It recognizes only the existing session collection, session history, intake, and request-resource paths. It does not decode arbitrary strings or change authorization rules. Filter registration/order and the existing security chain are unchanged.

**Evidence:** `StylistSessionApiIntegrationTests.embeddedServerAppliesSameBodyPolicyToCanonicalAndMvcEncodedRoutes` sends requests through the embedded Tomcat server to the canonical route and both encoded aliases. Each GET body returns `400 INVALID_REQUEST` with `Cache-Control: no-store`; the spy confirms the service read handler was not called. Bodyless GETs to all three routes return `200`. Database row counts remain unchanged. Additional embedded HTTP cases verify missing, malformed, and expired JWTs return `401`, unsupported roles return `403`, and denied requests do not call service handlers or write rows. Customer/Admin A/B self-only behavior is also exercised over HTTP and in the pre-existing MockMvc suite.

### F-02 — Unicode whitespace-only text was accepted

**Before:** `String.strip()` did not remove NBSP (`U+00A0`) or narrow NBSP (`U+202F`); the review probe showed those values were accepted as message/occasion content.

**Change:** Parser trimming now removes boundary code points recognized by either `Character.isWhitespace` or `Character.isSpaceChar`. It does not normalize or rewrite non-whitespace content. Code-point limits remain 2,000 for message, 128 for occasion/intended-use/style-context, 1,000 for weather context, and 64 for constraint text.

**Evidence:** Four parser tests reject Unicode-only whitespace, verify Unicode trimming around real text, preserve internal weather newlines, reject NUL, and test the existing limits at and beyond their boundaries with emoji (supplementary code points).

### F-03 — NUL was accepted in weather context

**Before:** The review probe showed `U+0000` accepted in weather context. PostgreSQL text cannot store NUL, so this input could reach persistence and fail as a server error.

**Change:** Optional text parsing rejects NUL explicitly for all text fields. Weather context still permits its existing multiline input and is not subjected to a new blanket control-character ban.

**Evidence:** An embedded HTTP request against the disposable PostgreSQL-backed application includes a message, valid occasion, a constraint, and JSON-escaped NUL in weather context. It returns `400 INVALID_REQUEST` with `Cache-Control: no-store`, not `500`; the service create handler is not called, and message/request/constraint counts do not change.

### F-04 — incomplete service transaction and concurrency evidence

**Before:** Existing rollback coverage opened an outer `TransactionTemplate` and raised an exception after the service returned. It did not prove rollback when persistence failed inside the service transaction. No intake-concurrency or close/admit serialization test existed.

**Change and evidence:** All new persistence-failure cases use the actual Spring service transaction through the request handler, without an outer transaction:

1. A test spy injects failure at request persistence after the USER message was flushed. It asserts the message is visible inside the active service transaction and all message/request/constraint rows are absent after rollback.
2. A test spy injects failure while constraints are being saved after the message and request have been flushed. It asserts both earlier writes are visible inside the active service transaction and all three tables are empty after rollback.
3. A temporary PostgreSQL trigger fails constraint insertion during flush/commit. A test-only sequence proves the trigger ran; all message/request/constraint rows are absent afterward. The trigger, function, and sequence are removed in `finally`; no production fault hook or migration was added.
4. Two concurrent valid POSTs to one open session both return `201` with distinct request IDs. No single-flight or idempotency rule was introduced.
5. A fixture-closed session returns `409 SESSION_CLOSED` with no new rows.
6. For intake-first ordering, a temporary trigger on message insert waits on a PostgreSQL advisory transaction lock after the service has selected the session `FOR UPDATE`. The test observes the intake's database lock wait, starts a fixture close UPDATE and observes its PostgreSQL lock wait/blocker, then releases the advisory lock. Intake commits `201`; the close transaction commits afterward. No `sleep`-only ordering assertion is used. The trigger/function are removed in `finally`.

All temporary triggers/functions/sequences run only against the Testcontainers PostgreSQL database.

## Files changed by this follow-up

- `stylist-service/src/main/java/com/stylemind/stylist/security/SessionRequestPolicyFilter.java`
- `stylist-service/src/main/java/com/stylemind/stylist/service/request/StylingRequestParser.java`
- `stylist-service/src/test/java/com/stylemind/stylist/session/StylistSessionApiIntegrationTests.java`
- `stylist-service/src/test/java/com/stylemind/stylist/session/StylingRequestParserUnicodeTests.java`
- `stylist-service/postman/StyleMind-Stylist-P03B1.postman_collection.json`
- `stylist-service/postman/P03B1_RUN_GUIDE.md`
- `docs/planning/stylist/EP-07_P03B1_FOLLOW_UP_REPORT.md`
- `docs/planning/stylist/EP-07_EXECUTION_MANIFEST.json` (appended follow-up execution evidence; P03 status remains `PARTIAL`)

V1 and V2 were not edited. No production source outside `stylist-service` was edited. Existing staged, unstaged, and untracked workspace changes were preserved; nothing was staged or committed.

## Verification

| Command | Result |
|---|---|
| `docker info --format '{{.ServerVersion}} {{.OSType}}'` | Docker Engine `28.5.2`, Linux |
| `mvn -B -ntp -f stylist-service/pom.xml '-Dtest=StylistSessionApiIntegrationTests,StylingRequestParserUnicodeTests' test` | **36 tests**, 0 failures, 0 errors, 0 skipped: 32 PostgreSQL/HTTP integration tests + 4 parser tests |
| `mvn -B -ntp -f stylist-service/pom.xml verify` | **BUILD SUCCESS; 132 tests**, 0 failures, 0 errors, 0 skipped |
| `git diff --check` | PASS |
| `git diff --cached --check` | PASS |
| Postman JSON parse and collection item count | Both collection/environment parse; collection is v2.1 with 9 requests |

The full 132-test run includes 52 P02 engine tests, 29 retrieval tests (including two real Qdrant integration tests), 32 PostgreSQL/HTTP session and request integration tests, four new parser Unicode tests, two existing session parser tests, and 13 application/security/OpenAPI tests.

Two intermediate focused runs exposed test/setup issues and were corrected before final verification: the filter initially had an ambiguous MVC handler-mapping dependency and now qualifies `requestMappingHandlerMapping`; a later fixture used an expiry still inside the default JWT clock skew and a DB `CURRENT_TIMESTAMP` preceding the application-generated `opened_at`. The final tests use a clearly expired token and set fixture `closed_at` after `opened_at`. The final targeted and full verification commands above pass.

## Disposable resources and delivery evidence

- Full verification used `postgres:18-alpine` / PostgreSQL `18.6` in Testcontainers at random host port `50635`. Flyway applied V1/V2 only to this disposable database.
- Two Qdrant Testcontainers integration tests used `qdrant/qdrant:v1.20.0` at random host port `50596` and their test-owned collections.
- Testcontainers/Ryuk cleanup completed. A post-run `docker ps -a --filter label=org.testcontainers=true` returned no containers. No Compose project, persistent project database, shared Qdrant, or persistent volume was used.
- **Postman structural validation:** collection and environment JSON parsed successfully; nine request items are present. Requests 7–9 cover encoded-path GET-body rejection, Unicode whitespace-only rejection, and safe NUL rejection.
- **Postman/Newman runtime through Gateway:** not run. The automated embedded HTTP server is not Gateway E2E evidence.
- **Planning guard:** `node scripts/verify-stylist-planning.cjs --phase P03` exits `1` due the existing Commerce source/fingerprint drift: the eight committed-range Commerce paths plus `ProductController.java`, `VariantResponse.java`, and `ProductVariantRepository.java`. No Commerce files, fingerprints, or `reviewed_head` were changed.

## Remaining boundary

The findings in this follow-up are addressed in the approved P03-B1 local/disposable scope. P03-B1 still stores only shop/manual requests as `RECEIVED` and does not start generation. P03, US-07.01, and EP-07 remain **PARTIAL**. No owned context, provider/LLM, P04.3, Gateway runtime, or production readiness is claimed.
