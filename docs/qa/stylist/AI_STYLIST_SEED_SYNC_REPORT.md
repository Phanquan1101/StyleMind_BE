# AI Stylist seed synchronization report

**Date:** 2026-11-08
**Scope:** Reconcile the P04.1 test-only fixture with the current Commerce development seed and verify the integrated AI Stylist backend offline.
**Status:** Seed reconciliation PASS; backend regression PASS with one intentional live-only skip. EP-07 remains incomplete.

## Seed source and reconciliation

Source checked: `commerce-service/src/main/resources/db/seed/development-catalog.json` in the integrated repository tree. It declares catalog manifest version 2, contains 381 products, and has SHA-256:

`7390703932057DB9C1547837A177BE19F7A86356F4B401AF2D28FB0EC6AAD9C0`

The fixture is `stylist-service/src/test/resources/stylist/retrieval/p04-review-sample.synthetic-test-only.json`. All eight rows were checked against the source seed for product/variant identity and parent relationship, category ID and slug, existing category-to-slot mapping, demographic, product name and description, color, size, material, and exact VND price snapshot.

The existing test mapping remains `ao -> TOP` and `quan -> BOTTOM`; no mapping was added. Seven product/variant pairs remain in the seed. Their source snapshots were updated where the seed changed. One BOTTOM pair was removed from the seed and was replaced with an existing women's BOTTOM item under the same `quan -> BOTTOM` mapping:

| | Removed pair | Replacement |
|---|---|---|
| Product ID | `3bda56f8-980d-557e-a508-c87a030ddc1f` | `07a9212d-3ac0-5de1-ac11-d0005ba7e4bd` |
| Variant ID | `2389bc86-ad6b-5dc8-87c1-29d2251633ad` | `ab7f39f8-4a68-5539-a8d3-c81e06bb6656` |
| Seed item | Aurelia Women Solid Purple Leggings | Hanes - Quần legging nữ - Mẫu 50020 |
| Slot / demographic | BOTTOM / WOMEN | BOTTOM / WOMEN |
| Exact seed price | 249,000 VND | 249,000 VND |

The replacement preserves the existing price boundary used by budget coverage. The fixture verifier confirms it is an actual seed product and variant with the stated parent, category, demographic, and snapshot.

Names, descriptions, colors, sizes, materials, and prices now match the current seed. The fixture SHA is the current seed SHA above. The test-support seed checksum constant was updated. The Qdrant integration expectation and hard-color exclusion test were updated for the replacement ID and current color value `Hồng`. The fixture verifier gained regression cases for parent-product, category ID, category slug, demographic, name, description, color, size, material, and price mismatches; bad checksum and nonexistent product/variant rejection remain covered.

All eight rows retain their existing eligibility labels and review signals. In particular, the child-title review row and unisex-for-women's-stylist review row remain review-only; no row was promoted or relabeled. Every row remains `SYNTHETIC_TEST_ONLY`, records its seed source and checksum, and states `NOT_RUNTIME_VERIFIED` with seed-snapshot price semantics. The source snapshot does not prove that the development seed has been loaded into a runtime Commerce database, approved as live AI catalog, or verified for current availability.

The additional manual Postman handover files from the original worktree were also checked structurally: the collection and environment parse as JSON, the collection contains 11 requests, and token variables are empty/placeholder values. That manual collection was not separately executed by Newman; Newman runtime evidence below is from the checked-in Wave 2 collection.

## Files changed for this reconciliation

- `stylist-service/src/test/resources/stylist/retrieval/p04-review-sample.synthetic-test-only.json`
- `stylist-service/src/test/java/com/stylemind/stylist/model/retrieval/RetrievalModel.java`
- `stylist-service/src/test/java/com/stylemind/stylist/retrieval/P04SeedFixtureVerifierTests.java`
- `stylist-service/src/test/java/com/stylemind/stylist/retrieval/QdrantCandidateRetrievalIntegrationTests.java`
- `stylist-service/src/test/java/com/stylemind/stylist/retrieval/InternalRecommendationPipelineIntegrationTests.java`

No production Java, public API/DTO, lifecycle, ranking, engine limits, schema, migration, Commerce source/seed, or other service code was changed for this reconciliation.

## Verification

Focused test runs:

- `mvn -B -ntp -f stylist-service/pom.xml -Dtest=P04SeedFixtureVerifierTests,CandidateRetrievalServiceTests,QdrantCandidateRetrievalIntegrationTests test` — 13 tests, 0 failures, 0 errors, 0 skipped.
- `mvn -B -ntp -f stylist-service/pom.xml -Dtest=InternalRecommendationPipelineIntegrationTests,QdrantCandidateRetrievalIntegrationTests,P04SeedFixtureVerifierTests test` — 17 tests, 0 failures, 0 errors, 0 skipped.

Final Stylist regression:

- `mvn -B -ntp -f stylist-service/pom.xml verify` — BUILD SUCCESS; 200 tests across 24 suites, 0 failures, 0 errors, 1 skipped. The skip is the opt-in live Gemini test; it is not counted as a pass.

Gateway regression:

- `mvn -B -ntp -f api-gateway/pom.xml verify` — BUILD SUCCESS; 12 tests, 0 failures, 0 errors, 0 skipped.

Disposable Gateway/Newman flow:

- `node stylist-service/scripts/wave2-disposable-gateway-harness.cjs` — PASS. The checked-in Postman collection ran through the real local Gateway and Stylist processes: 10 requests, 20 assertions, 0 failures.
- Gemini was mocked at its HTTP boundary. Commerce, JWKS, and catalog/knowledge were local fixtures; the JWT was fixture-signed and was not issued by Identity.
- The positive flow stored the request as RECEIVED, generated a backend-selected TOP+BOTTOM result, validated it through the Java parser/whitelist, performed the Commerce fixture recheck, persisted recommendation and both items, and read them back.
- Concurrency produced one 200 winner and one 409 loser. Terminal replay returned 200 and added 0 model-fixture calls. A foreign-owner generate request returned 404 before provider calls.
- Total Gemini fixture operations recorded for the harness run: 5 embedding, 8 token-count, and 2 generation operations. PostgreSQL contained 2 succeeded requests, 2 recommendations, 4 recommendation items, and 0 assistant messages.

The harness used disposable PostgreSQL `postgres:18-alpine`, Qdrant `v1.20.0`, and Neo4j `5.26-community` containers with random host ports and no persistent volumes. The harness removed its named containers on exit; a subsequent container-name check found none. Testcontainers handled its own disposable resources. No Compose stack, persistent database, shared Qdrant/Neo4j, provider, or paid API was used. No live Gemini or OpenAI call was made.

## Planning guard and known drift

Both `node scripts/verify-stylist-planning.cjs` and `node scripts/verify-stylist-planning.cjs --phase P07` exited 1. After the preview was attached to a temporary review branch, each run warned that this temporary branch differs from the reviewed `feature/ep-07-ai-stylist` branch. The remaining reported drift was: relevant committed Commerce source/docs (`commerce-service/pom.xml`, Commerce US-03 Postman guides/collections, `CommerceServiceApplication.java`); fingerprint drift in `database/six-service-model/02_commerce.mmd`, Stylist POM/application/SecurityConfiguration, and Commerce ProductController/VariantResponse/ProductVariantRepository. No fingerprints or reviewed head were refreshed to suppress these warnings.

The Commerce drift was not changed as part of this fixture-only synchronization. Planning guard is a documentation/source review guard; it is not an application-test result or an approval record.

The historical document `docs/analysis/AI_STYLIST_BA_TECHNICAL_ROADMAP.md` was retained with its historical notice because `docs/planning/stylist/EP-07_REVIEW_2026-10-27.md` references it. It remains marked as historical analysis, not current approval or implementation instructions.

## Boundaries and remaining status

Seed fixture reconciliation and the listed local verification gates passed. This is offline fixture evidence only. It does not establish live catalog readiness, user-facing styling quality, Gemini live acceptance, or completion of EP-07/P04/R98/R99. No persistent database was migrated or modified.
