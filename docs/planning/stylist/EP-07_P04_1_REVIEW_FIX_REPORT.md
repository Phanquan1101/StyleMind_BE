# EP-07 / P04.1 — review findings follow-up

**Date:** 2026-11-02 (Asia/Bangkok)
**Status:** The four requested review findings are fixed in P04.1 code/tests. P04 remains PARTIAL; R98 and R99 remain incomplete. No production endpoint, live Commerce, provider, LLM, Neo4j or event sync was added.

## Finding outcomes

### F-01 — Qdrant endpoint boundary: FIXED

- QdrantHttpVectorStore now accepts only an origin with no user-info, path, query or fragment; only exact localhost or a numeric IP literal is considered. Every resolved address must be loopback. The selected address is pinned into the request URI, preventing later DNS re-resolution from redirecting the connection to a non-loopback destination.
- The client explicitly uses HttpClient.Redirect.NEVER. Unexpected 3xx is a protocol error; no redirect destination is called.
- Tests cover local localhost/loopback acceptance, IPv6 loopback construction, remote HTTP/HTTPS rejection, hostname spoofing, user-info, path/query/fragment, invalid port and a redirect to a second loopback HTTP server whose request count remains zero.
- The actual Testcontainers mapped-host path could not be rerun because Docker Engine is unavailable in this environment. If a Docker context returns a non-loopback host, the adapter fails closed; this follow-up does not widen the allowed host set. The current integration test uses QDRANT.getHost() and its random mapped port.
- This is a disposable P04.1 guard, not a production endpoint policy.

### F-02 — Collection cleanup ownership: FIXED

- createCollection first calls Qdrant’s collection-exists endpoint. An existing collection is rejected before PUT. The official Qdrant API describes GET /collections/{collection_name}/exists as returning an exists boolean: [Qdrant collection existence API](https://api.qdrant.tech/api-reference/collections/collection-exists).
- Each adapter instance records ownership only after a successful HTTP response with the expected create result. Failed, timed-out, malformed or otherwise uncertain creates do not grant ownership. Upsert/search also require ownership by that instance.
- Cleanup refuses a collection owned by another instance. Successful deletion marks the local handle deleted, so a second delete is a no-op. A failed/uncertain delete retains ownership so the same adapter can retry.
- Tests cover an existing collection, foreign adapter, double delete, uncertain create timeout with no DELETE, and delete outage followed by retry. No test issues DELETE to an unowned collection.
- This registry is cleanup hygiene for the disposable POC, not a production security/ownership mechanism. A collection left after an uncertain create exists only in the disposable test server; the integration container is removed by Testcontainers when that test can run.

### F-03 — Malformed Qdrant responses and status classes: FIXED

- Transport connection/read/timeout failures and interruption map to RetrievalUnavailableException.
- Malformed JSON and successful HTTP responses with invalid operation/query shapes map to VectorStoreProtocolException.
- Create, upsert, query and delete success bodies are checked for their required result shape before ownership is recorded or returned as successful.
- HTTP 408, 429 and 5xx map to RetrievalUnavailableException. Redirects and other 4xx, including collection conflicts, map to VectorStoreProtocolException. Response bodies are never included in exception messages or logs.
- Loopback HTTP tests cover HTTP 200 with malformed JSON, HTTP 200 with valid JSON but an invalid points shape, 400, 503 and the existing connection/timeout cases. Assertions confirm response bodies are not exposed.

### F-04 — Seed fixture drift evidence: FIXED

- A test-only verifier locates the checked-out Commerce seed by walking parent directories from the test working directory; production source has no new filesystem dependency.
- Tests hash the actual seed bytes and compare them with the hash carried by fixture rows and the P04.1 evidence constant. They also compare fixture product/variant IDs, category/slug, demographic, product name/description, color, size, nullable material and seed price.
- A test changes only an in-memory copy of the seed bytes and proves that a hash mismatch fails with expected/actual evidence. Another test proves a missing fixture product ID fails. The seed file itself is read-only and was not changed.

## Test results and environment

- Executed: mvn -B -ntp -f stylist-service/pom.xml "-Dtest=CandidateRetrievalServiceTests,FakeTestOnlyDeterministicEmbeddingTests,QdrantHttpVectorStoreFailureTests,QdrantHttpVectorStoreEndpointBoundaryTests,QdrantHttpVectorStoreOwnershipTests,QdrantHttpVectorStoreProtocolTests,P04SeedFixtureVerifierTests" test
- Result: BUILD SUCCESS; **27 tests, 0 failures, 0 errors, 0 skipped**. Includes 7 retrieval service tests, 2 fake embedding tests, 3 seed verifier tests, 4 endpoint tests, 3 connection/timeout tests, 4 ownership tests and 4 protocol/status tests.
- Docker Engine check: NOT AVAILABLE. docker info could not open the Docker Desktop Linux Engine pipe. Therefore the Qdrant Testcontainers integration test and full command mvn -B -ntp -f stylist-service/pom.xml test were NOT EXECUTED. No old test result is represented as a current pass.
- The executed suite used local loopback HttpServer instances with ephemeral ports and read the local SOURCE_SEED file. No Docker container, Compose service, persistent database, shared Qdrant, external provider or LLM was started or contacted.
- No Maven dependency/POM, Compose, migration, Commerce/Identity/Wardrobe/Gateway/FE source, manifest, source fingerprint or reviewed_head was changed.

## Planning and hygiene checks

- node scripts/verify-stylist-planning.cjs — exit 1 due the existing Commerce drift listed below.
- node scripts/verify-stylist-planning.cjs --phase P04 — exit 1 for the same Commerce drift.
- Both report eight committed Commerce paths (Commerce POM, six Commerce Postman files and CommerceServiceApplication) and fingerprint drift in ProductController.java, VariantResponse.java and ProductVariantRepository.java. No hashes or reviewed_head were refreshed.
- git diff --check and git diff --cached --check: PASS after report update. New-file whitespace scan: PASS.

## Scope retained

The POC remains six approved WOMEN SOURCE_SEED fixture rows with test-only category mappings and deterministic fake 128-dimensional embeddings. There is still no live endpoint/API wiring, current Commerce price/status validation, public API, migration, real embedding call, LLM, Neo4j or catalog event sync. P04.1 fixes do not complete P04, R98, R99 or EP-07.

## Follow-up actual integration verification — 2026-11-04

The earlier Docker-unavailable result above is retained as the state of that earlier attempt. The new verification artifacts at `stylist-service/target/p041-verification-20261104/` record that Docker Desktop was started, Linux Engine 28.5.2 became available, and the full test command completed successfully:

```powershell
mvn -B -ntp -f stylist-service/pom.xml test
```

Result: **112 tests, 0 failures, 0 errors, 0 skipped**. Surefire XML totals match the Maven output. The two `QdrantCandidateRetrievalIntegrationTests` passed on actual Qdrant `qdrant/qdrant:v1.20.0` in a disposable Testcontainers container using a random host port (58289 mapped to 6333). They exercise Qdrant exists/create/upsert/query/delete with the fixture retrieval flow, TOP/BOTTOM filtering, candidate reference mapping, limits and empty results. Both tests clean their own UUID-named collection. Testcontainers removed its container; the recorded before/after Docker inventories are identical. The suite also ran P03-A's 16 PostgreSQL integration tests against disposable PostgreSQL 18.

### F-01–F-04 evidence boundary

| Finding | Unit evidence in the full run | Real Qdrant / integration evidence |
|---|---|---|
| F-01 endpoint boundary | 4 tests passed | Integration connected through `QDRANT.getHost()` and its mapped port. Remote-host/redirect rejection remains covered by loopback HTTP tests. |
| F-02 collection ownership | 4 tests passed; pre-existing collection and foreign-adapter deletion are covered by loopback tests | Integration created and deleted its own collections. It did not separately pre-create a foreign collection in Qdrant. |
| F-03 protocol/status | 4 protocol/status and 3 transport/timeout tests passed | Successful response shapes for exists/create/upsert/query/delete were exercised against Qdrant; malformed responses/statuses are covered by loopback tests. |
| F-04 seed drift | 3 seed verifier tests passed against the checked-out SOURCE_SEED | Integration indexed the approved fixture only; it did not call Commerce runtime. |

The ownership registry is an in-memory per-adapter cleanup guard. It does not coordinate distributed clients and is not authorization for a shared Qdrant deployment. No persistent database, shared Qdrant, Compose service, live Commerce, embedding provider or LLM was accessed. This establishes P04.1 only for the fixture/disposable scope; P04, R98, R99 and EP-07 remain incomplete.

The full log is `stylist-service/target/p041-verification-20261104/stylist-full-suite.log`; the suite summary is `VERIFICATION_SUMMARY.md`; the Qdrant integration XML is `stylist-service/target/surefire-reports/TEST-com.stylemind.stylist.retrieval.QdrantCandidateRetrievalIntegrationTests.xml`. The target directory is ignored local evidence.
