# EP-07 / P04.1 — Disposable Qdrant Retrieval POC

**Status: P04.1 implemented and verified. P04 is PARTIAL. R98, R99, US-07.02 and EP-07 are not complete.**

## Scope and owner approval

The owner’s 2026-11-02 attachment authorizes a disposable P04.1 retrieval test in `stylist-service` using Qdrant, a deterministic fake 128-dimensional embedding and the reviewed `SOURCE_SEED` sample. It permits the fixture mappings `ao → TOP` and `quan → BOTTOM` only for this test. The six default rows are WOMEN records outside the child-title review queue. The child-title and UNISEX rows are negative/review fixtures and cannot enter the default indexed set.

Backlog row 98 asks to connect Qdrant/Neo4j retrieval to valid catalog and approved knowledge inputs. This POC demonstrates the Qdrant fixture path only. It does not implement Neo4j, live Commerce input, an HTTP endpoint, outfit composition or recommendation generation. It does not change P02/P03-A, a migration, another service, shared Compose or a persistent database.

## Dataset and source check

The fixture at `stylist-service/src/test/resources/stylist/retrieval/p04-review-sample.synthetic-test-only.json` was created from the eight rows in `docs/planning/stylist/EP-07_P04_REVIEW_SAMPLE.csv`. Before fixture creation, the source seed’s SHA-256 was checked as `07A0AEEBFAB18CC42DC00EE034E499FC02A188B41C9F4DBF114A88AF7C1A1263`. All eight product/variant pairs were found in `commerce-service/src/main/resources/db/seed/development-catalog.json`; category ID, product name, raw demographic, color, size, nullable material and seed price matched the CSV rows. The six selected default product IDs are absent from `EP-07_P04_SEED_REVIEW_QUEUE.csv`.

The fixture contains six index-eligible rows: three TOP and three BOTTOM. It retains one child-title WOMEN row and one UNISEX row as excluded cases. Category mapping and fixture eligibility are explicitly tagged `APPROVED_TEST_ONLY` or `NEEDS_HUMAN_REVIEW`. These tags are for this fixture only and do not change the source CSV or establish a Commerce taxonomy policy.

The source seed has no product or variant status fields. Its sample prices are labeled `SOURCE_SEED_SNAPSHOT_NOT_CURRENT`; status sources are `NOT_IN_SEED`; runtime provenance is `NOT_RUNTIME_VERIFIED`. The fixture does not copy image URLs, inventory, customer data, or secrets. A missing material stays `null` and is marked `MISSING` in Qdrant payload.

## Implementation

- `RetrievalModel` contains the internal fixture candidate, bounded query/result, vector point and vector hit records. These are not API DTOs or persistence entities.
- `CandidateEmbeddingPort` is the replaceable embedding boundary. The only implementation is `FakeTestOnlyDeterministicEmbedding` in test sources, labeled `FAKE_TEST_ONLY`. It normalizes text with Unicode NFKC and lowercase, hashes tokens with SHA-256 into signed buckets, emits 128 dimensions and L2-normalizes. It performs no HTTP, model or provider calls. Its vector similarity is plumbing evidence only.
- `CandidateRetrievalServiceImpl` validates the fixture’s source hash and provenance, required IDs and slot, rejects duplicate variant IDs before writing, excludes pending mappings and review-only records, and only indexes explicitly eligible WOMEN fixture rows. It retains a registry of the exact records indexed and rejects Qdrant hits outside that registry.
- `QdrantHttpVectorStore` uses Java 21 `HttpClient` and the project’s existing Jackson dependency; no Qdrant Java SDK or runtime dependency was added. It is restricted to collection names matching `stylemind_p041_test_<32 hex chars>`. Collections use 128-dimensional cosine vectors. Search filters require the requested slot, `APPROVED_TEST_ONLY` mapping and eligibility, `SYNTHETIC_TEST_ONLY`, `SOURCE_SEED`, the checked seed hash, `NOT_RUNTIME_VERIFIED`, and raw demographic `WOMEN`.
- Qdrant errors/timeouts raise `RetrievalUnavailableException`; an empty result remains an empty result. The adapter makes no retry and has no unfiltered fallback. Malformed or out-of-scope hits raise `VectorStoreProtocolException`.
- Results preserve the original Commerce `productId`, `variantId`, `categoryId` and TOP/BOTTOM slot. The service orders by vector score descending and uses variant ID as a stable tie-break. It returns a candidate reference with source/snapshot provenance, never an outfit or recommendation.

The checked API surface is Qdrant collection creation, point upsert with `wait=true`, and point query. The official [Qdrant v1.20 collection API](https://api.qdrant.tech/api-reference/collections/create-collection), [upsert API](https://api.qdrant.tech/api-reference/points/upsert-points) and [query API](https://api.qdrant.tech/api-reference/search/query-points) were checked before implementation. The disposable server image is pinned to `qdrant/qdrant:v1.20.0`; the actual server/client REST compatibility is exercised by Testcontainers tests.

There is no Spring bean, controller or runtime configuration wiring this POC into the application. The service implementation is exercised directly by tests, keeping this slice out of public and live runtime paths.

## Actual candidate examples

The Qdrant integration test creates an embedding from each candidate’s indexed text and confirms an exact-text query places its matching fixture first within the requested slot:

| Slot | Product | Product ID | Variant ID |
|---|---|---|---|
| TOP | Tokyo Talkies Women Printed White Top | `01ec9297-370b-5096-8851-94331a0bcb60` | `55db1c3c-7dce-5a5e-a3ac-a11bc3a8245f` |
| BOTTOM | Aurelia Women Solid Purple Leggings | `3bda56f8-980d-557e-a508-c87a030ddc1f` | `2389bc86-ad6b-5dc8-87c1-29d2251633ad` |

The same integration test confirms TOP results do not contain BOTTOM references, BOTTOM results do not contain TOP references, and neither review-only ID appears in the default TOP result. This proves vector serialization, Qdrant filtering and reference mapping for the fixture. It does not prove semantic relevance or fashion quality.

## Tests and resources

Command:

```powershell
mvn -B -ntp -f stylist-service/pom.xml test
```

Result: **BUILD SUCCESS — 97 tests, 0 failures, 0 errors, 0 skipped.** This includes 52 P02 engine tests, 14 P04.1 retrieval/embedding/Qdrant tests, 16 P03-A PostgreSQL integration tests, 2 P03-A parser tests and 13 application tests.

P04.1 coverage includes deterministic 128-D fake vectors, null/blank query rejection, TOP/BOTTOM filtering, limits 1 and 20 plus rejected 0 and 21, empty result, duplicate variant rejection before writes, pending mapping exclusion, child-title and UNISEX exclusion, nullable material, missing candidate IDs/slot, unsupported slot, wrong vector dimension, malformed payload IDs/slot, stable tie-break, unknown point rejection, Qdrant connection failure and timeout. The timeout test uses a delayed loopback HTTP server and verifies a typed failure without retry. The existing P02 and P03-A suites passed in the same full run.

The P04.1 integration tests used `qdrant/qdrant:v1.20.0` in Testcontainers 1.21.4, an ephemeral container with port 6333 randomly mapped to localhost, no persistent volume, and a unique collection per test. Each test deletes only its own collection; Testcontainers/Ryuk removed its container. The full suite also used a disposable `postgres:18-alpine` container for P03-A. No project Compose service or persistent database was connected or migrated. The downloaded Qdrant image remains in Docker’s local image cache; no test container or named Qdrant volume remains.

No Postman collection was added because P04.1 adds no HTTP API. No paid embedding, LLM or other provider was called.

## Commerce source drift review

The planning guard still reports source drift from the recorded review baseline. The eight additional committed Commerce paths are the Commerce POM, six Commerce Postman guides/collections, and `CommerceServiceApplication`. Their observed changes are a Cloudinary dependency, Postman documentation/examples, and `@EnableScheduling`. They do not affect this POC because it never calls Commerce runtime.

The three fingerprinted Commerce sources were inspected:

- `ProductController` changes Swagger security annotations and operation descriptions for soft-delete/image behavior. No controller is called by this fixture POC.
- `VariantResponse` now exposes `available_quantity`. This POC deliberately does not copy stock/availability, consistent with the current project boundary excluding inventory.
- `ProductVariantRepository` joins `image_asset` when reading images and adds an existence query. This POC does not use Commerce repositories or image fields.

The POC therefore proves only the checked-in seed projection. It does not prove that the seed runner has run, that catalog data is present in a database, how the current Commerce API serializes all product data, or how current price/status should be rechecked. The production/live Commerce contract is still a separate gate. `reviewed_head` and Commerce source fingerprints were not changed to suppress the guard.

## Remaining gates

- **P04/R98 remain incomplete:** Neo4j and approved knowledge inputs are untouched. This fixture-only Qdrant slice does not connect to a valid live catalog.
- **R99 remains incomplete:** no Commerce event producer, transport, deduplication, stale-event handling, durable projection or rebuild was implemented.
- **Embedding provider remains unapproved:** select a real model only after owner approval of data fields/privacy, cost limits, model/version and evaluation method.
- **Live Commerce remains unverified:** establish the approved catalog projection and authoritative price/status lookup contract before using a live candidate for budgeting or saving.
- **Demographic/catalog policy remains unresolved globally:** only these six fixture rows are accepted for P04.1; child-title and UNISEX records remain excluded/review-only.
- No claim is made about recommendation quality, current purchase availability, production readiness or runtime deployment.

The next work must be separately scoped and authorized. This report does not start P04.2, P05, P06, P07, P08 or P09.

## Follow-up review fixes

The follow-up addressing endpoint restriction, per-adapter collection ownership, malformed Qdrant response classification and checked-out seed drift verification is recorded in [EP-07_P04_1_REVIEW_FIX_REPORT.md](EP-07_P04_1_REVIEW_FIX_REPORT.md). The earlier reports retain their original Docker-blocked attempts; the final 2026-11-04 verification below adds evidence for the disposable scope.

## Final fixture-scope integration verification — 2026-11-04

The local verification record at `stylist-service/target/p041-verification-20261104/` was checked against the full Maven log and all 12 Surefire XML reports. The earlier Docker-blocked attempts remain part of the history; this run was performed after Docker Desktop started and `docker info` returned Linux Engine 28.5.2.

Command:

```powershell
mvn -B -ntp -f stylist-service/pom.xml test
```

Result: **BUILD SUCCESS — 112 tests, 0 failures, 0 errors, 0 skipped.** Surefire XML totals match the Maven summary. Both `QdrantCandidateRetrievalIntegrationTests` ran against Qdrant in Testcontainers and are included in 112.

| Area | Tests | Result |
|---|---:|---|
| P02 outfit engine | 52 | PASS |
| P03-A session/history and parser | 18 | PASS (16 disposable PostgreSQL integration, 2 parser) |
| P04.1 retrieval, seed verifier, adapter tests and Qdrant integration | 29 | PASS (including 2 Qdrant integration tests) |
| Application/security/OpenAPI | 13 | PASS |

The Qdrant integration tests used `qdrant/qdrant:v1.20.0`, container `55c17f9f5e9e0d8c662439fd62c803ed1bfe77f0749d94aa7e1df0f8ec28ee31`, with container port 6333 mapped to ephemeral host port 58289. The adapter connected through the Testcontainers host and mapped port. Each of the two tests created a UUID-named disposable collection and its `@AfterEach` cleanup deleted only the collection recorded by that test. The summary reports the before/after Docker container inventory as identical, with no Testcontainers-labeled container or named Qdrant volume remaining. The Qdrant image remains in the local image cache. The suite also used disposable PostgreSQL 18 for P03-A; no persistent project database was accessed.

Finding-specific evidence:

- **F-01:** four endpoint-boundary unit tests passed; the two real Qdrant integration tests also exercised the Testcontainers host/mapped-port path. Remote endpoint and redirect rejection remain covered by loopback HTTP tests.
- **F-02:** four ownership unit tests passed, including pre-existing collection rejection, foreign-adapter deletion rejection, idempotent deletion and retry after delete failure. The real Qdrant tests verified cleanup of their own collections; they did not separately pre-create a foreign collection in Qdrant. The registry remains in-memory and per adapter instance, not distributed ownership or Qdrant authorization.
- **F-03:** four protocol/status tests and three transport/timeout tests passed. Real Qdrant integration exercised successful exists/create/upsert/query/delete response paths; malformed JSON and negative HTTP status classifications are covered by loopback HTTP tests.
- **F-04:** three seed checksum/drift verifier tests passed against the checked-out SOURCE_SEED. The Qdrant integration path indexed the approved fixture; no Commerce runtime was contacted.

P02 and P03-A regressions passed in the same full suite. No embedding/LLM provider, live Commerce, Compose service, persistent database, shared Qdrant or external API was used. Verification is limited to the six-row SOURCE_SEED fixture, deterministic fake embeddings and disposable Testcontainers. **P04 remains PARTIAL; R98, R99 and EP-07 remain incomplete.** The known Commerce planning/source drift remains unresolved and was not fingerprinted away.

Evidence files: `stylist-service/target/p041-verification-20261104/VERIFICATION_SUMMARY.md`, `stylist-full-suite.log`, `suite-results.json`, and `stylist-service/target/surefire-reports/TEST-com.stylemind.stylist.retrieval.QdrantCandidateRetrievalIntegrationTests.xml` plus the other Surefire XML reports. These are local target artifacts; this section records the durable result without changing application source.
