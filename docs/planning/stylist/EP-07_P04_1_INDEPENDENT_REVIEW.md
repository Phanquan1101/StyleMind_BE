# EP-07 / P04.1 — independent review

**Review date:** 2026-11-02 (Asia/Bangkok)

**Result:** P04.1 is a useful disposable retrieval plumbing POC. No critical issue was found in source review and the focused unit tests run here. The code has follow-up boundary and regression-test gaps before any live or shared use. This review does not approve a provider, live catalog, production Qdrant, P04/R98/R99 completion, or any source fingerprint refresh.

## Review boundary and workspace

- Branch: feature/ep-07-ai-stylist
- HEAD at review start: c9f20d1d1fc1779eca735b3de3ac9f1745b43985
- The workspace already had staged, unstaged and untracked files. They were preserved. No pull, reset, checkout, clean, stash, commit, push, source edit, migration, POM edit, Compose edit, database connection or workbook edit was performed.
- Only the three review handover files named in this task were added.
- Reviewed the root and Stylist instructions, planning index/plan/manifest, P04.1 report/proposal, seed queue/sample/catalog readiness report, P04.1 source/tests, P02 engine model/policy and Commerce Product/ProductVariant/VariantResponse/repository source. No archived file was used as an implementation instruction.

## Dataset verification

The current development seed SHA-256 is 07A0AEEBFAB18CC42DC00EE034E499FC02A188B41C9F4DBF114A88AF7C1A1263, matching the fixture’s recorded source hash. I compared all eight fixture product/variant pairs with the current seed; category ID, raw demographic, product name/description, variant color/size/material and seed price matched.

The default fixture is six WOMEN rows: three TOP and three BOTTOM. The other two are one WOMEN child-title signal marked NEEDS_HUMAN_REVIEW and one UNISEX item marked NEEDS_HUMAN_REVIEW. The retrieval service excludes the review row and non-WOMEN demographic before embedding/upsert. Missing material stays null; price is labeled SOURCE_SEED_SNAPSHOT_NOT_CURRENT; product and variant status are labeled NOT_IN_SEED. The source seed does not establish current runtime price/status.

The ao→TOP and quan→BOTTOM mapping is scoped in code to the two fixture category IDs and is labeled fixture-only. It is not a Commerce taxonomy decision. The checked-out Commerce source still has the drift listed by the planning guard; this POC does not invoke Commerce or establish live API serialization.

## Findings

No P0/P1 defect was found. Findings below distinguish the current reachable path from risks that matter if the POC is reused.

### F-01 — Medium, confirmed endpoint-boundary gap; no current public path

- **Files/lines:** QdrantHttpVectorStore.java:31-43 and 174-182; QdrantCandidateRetrievalIntegrationTests.java:32-46.
- **Condition:** Collection names are constrained to stylemind_p041_test plus a 32-character lowercase hex suffix, and the integration test points to a mapped local Testcontainers port. The adapter constructor itself accepts any HTTP(S) origin with no path; it does not restrict the host to loopback/Testcontainers.
- **Impact:** A future caller can construct this adapter with a remote Qdrant origin. Index/query text and fixture payload would then be sent to that destination. This is not currently reachable from a controller or Spring bean: the retrieval classes have no application wiring and current references are confined to the retrieval implementation/tests.
- **Evidence:** normalizeBaseUri checks scheme, host and URI shape only. No main-source configuration or controller constructs the adapter.
- **Recommendation:** Before reuse, move the disposable adapter and orchestration into test scope, or require an explicit approved endpoint policy and enforce loopback/test-container destinations for P04.1. Do not make a remote endpoint configurable until the owner approves data fields and destination.
- **Classification:** Confirmed defensive-boundary gap; not a demonstrated production exposure.

### F-02 — Low, confirmed collection-ownership check gap

- **Files/lines:** QdrantHttpVectorStore.java:118-121 and 184-187; QdrantCandidateRetrievalIntegrationTests.java:39-52 and 111-115.
- **Condition:** deleteTestCollection accepts any name matching the P04.1 naming pattern; the adapter does not record which names it created. The integration test itself is careful: it generates a random name, records it after creation and deletes only that recorded name.
- **Impact:** A different caller using a shared Qdrant origin could delete another caller’s collection if it knows a matching name. The current isolated Testcontainers test creates only its own randomly named collections, and no shared Qdrant is contacted.
- **Recommendation:** Keep the method test-scoped, or return a creation handle and allow deletion only through a registry of collections created by that adapter instance. Preserve the integration test’s owned-name tracking.
- **Classification:** Confirmed API-level guard gap; current test cleanup is scoped correctly.

### F-03 — Low, confirmed malformed-JSON error classification gap

- **Files/lines:** QdrantHttpVectorStore.java:124-149, especially 142-148; QdrantHttpVectorStoreFailureTests.java:21-78.
- **Condition:** readTree parses the HTTP response inside a try whose IOException catch maps all I/O and JSON parse failures to RetrievalUnavailableException. A malformed JSON response is therefore reported like a connection failure/timeout rather than VectorStoreProtocolException. Existing tests cover connection failure, timeout and collection-name rejection, but not malformed HTTP JSON or a malformed successful response body.
- **Impact:** Internal diagnostics/metrics cannot distinguish server/protocol corruption from a transient network outage. There is no public API mapping today and no automatic retry in this adapter.
- **Recommendation:** Separate transport/read failures from JSON decoding failures, classify malformed response bodies as protocol errors, and add loopback HTTP tests for malformed JSON, missing result/points and malformed point payload.
- **Classification:** Confirmed code-path behavior; not dynamically reproduced in this review.

### F-04 — Low, seed snapshot is not protected by a runtime test checksum

- **Files/lines:** RetrievalModel.java:13-18; P04FixtureLoader.java:15-29; CandidateRetrievalServiceImpl.java:138-146.
- **Condition:** The fixture and candidate validator compare the fixture’s source SHA string to a fixed constant, but P04FixtureLoader does not hash or parse the Commerce seed during tests. The independent comparison in this review confirms the current eight rows and hash match; a later seed edit could leave tests green while the fixture has become stale.
- **Impact:** Source lineage drift may be missed by ordinary future test runs.
- **Recommendation:** At the next authorized code change, add a local-only fixture verification test that recomputes the source file hash and checks selected product/variant fields, or generate the test fixture from the versioned seed. Do not read a database or add live Commerce access.
- **Classification:** Regression-coverage risk; no current seed mismatch.

### F-05 — Informational, fixture implementation is compiled from main source

- **Files/lines:** CandidateRetrievalServiceImpl.java:27-49; QdrantHttpVectorStore.java:26-43; source references found with rg across main/test.
- **Condition:** The embedding implementation is under test sources, but the Qdrant adapter and retrieval orchestration are under src/main/java. They are not annotated as Spring components and no controller/configuration wires them, so the application has no current public route to this POC.
- **Impact:** These classes are still compiled into the service artifact and could be wired accidentally by later work. This is a scope hygiene concern, not evidence that the POC currently runs at application startup.
- **Recommendation:** Keep this code isolated from live application wiring; consider moving the disposable orchestration/adapter to test scope or placing future live retrieval behind a separately reviewed implementation and configuration.
- **Classification:** Intentional current limitation with a packaging hygiene improvement.

## Confirmed behavior and intentional limits

- The fake embedder lives in test sources, normalizes with NFKC/lowercase, hashes tokens deterministically, emits 128 finite normalized dimensions and makes no provider call. Its similarities are plumbing-only and are not semantic relevance, probability or fashion quality.
- Candidate text includes reviewed name/description/color/size/material only; it excludes IDs and image URLs. Null material is omitted from the Qdrant payload and represented as MISSING.
- The Qdrant filter includes slot, APPROVED_TEST_ONLY mapping/eligibility, SYNTHETIC_TEST_ONLY, SOURCE_SEED, source hash, NOT_RUNTIME_VERIFIED and WOMEN. The service maps hits back through its indexed-candidate registry, rejects duplicate/unknown IDs and validates critical payload scope before returning the locally held candidate.
- Limits are 1–20, empty results remain distinct from transport errors, equal scores sort by variant UUID, and the adapter has no retry or unfiltered fallback. Stock/available_quantity is not used.
- P02’s TOP+BOTTOM engine rules and P03-A session/history contracts were not changed. Retrieval does not create outfits, enforce current price/status or permissions, or establish owned-item context.
- A real embedding adapter would need a separate owner-approved provider, data-field allowlist, exact dimension/normalization, model/version and reindex plan. No such adapter exists in the current code.

## Verification performed for this review

Docker Engine was not available: docker info could not open the Docker Desktop Linux Engine pipe. The full command below was therefore NOT EXECUTED in this review; no prior 97-test report is reused as current evidence.

- Executed: mvn -B -ntp -f stylist-service/pom.xml "-Dtest=CandidateRetrievalServiceTests,FakeTestOnlyDeterministicEmbeddingTests,QdrantHttpVectorStoreFailureTests" test — BUILD SUCCESS, 12 tests, 0 failures, 0 errors, 0 skipped. These tests use mocks/hash logic and a loopback HttpServer; they do not require Docker.
- Not executed: mvn -B -ntp -f stylist-service/pom.xml test. It includes Qdrant and PostgreSQL Testcontainers tests. Docker was unavailable; no Compose or persistent endpoint was substituted.
- The current fixture/source hash and eight candidate records were compared read-only; all eight matched.
- node scripts/verify-stylist-planning.cjs — exit 1.
- node scripts/verify-stylist-planning.cjs --phase P04 — exit 1.
- Both planning commands report the same open Commerce drift: eight committed paths (Commerce POM, six Commerce Postman files, CommerceServiceApplication) and fingerprints for ProductController.java, VariantResponse.java and ProductVariantRepository.java. These fingerprints and reviewed_head were not refreshed.
- No Docker container was started by this review. No persistent database, shared Qdrant, provider, LLM or external API was contacted.

## Result

This review verifies fixture loading/source correspondence by read-only comparison, deterministic vector plumbing through focused unit tests, filtering and defensive hit mapping by source/test review. The previous P04.1 handover records a Qdrant integration run, but Docker was unavailable here and that result is not reused as a current PASS. This review does not demonstrate semantic quality, live Commerce data, current status/price, provider privacy/cost, Neo4j/knowledge retrieval, event synchronization or production readiness. P04 remains PARTIAL; R98 and R99 remain incomplete.
