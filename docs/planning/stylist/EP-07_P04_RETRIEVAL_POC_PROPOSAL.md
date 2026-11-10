# EP-07 / P04.1 Preparation — Seed Review and Retrieval POC Proposal

**State: Historical preauthorization proposal.** The owner attachment dated 2026-11-02 authorizes the narrow disposable P04.1 slice described in this repository’s [implementation report](EP-07_P04_1_RETRIEVAL_POC_REPORT.md). That approval covers fixture-only mappings and test infrastructure; it does not approve live catalog policy, a production embedding provider, a shared Qdrant deployment or a production retrieval contract. SOURCE_SEED remains unverified at runtime.

**Owner decision update (2026-11-02):** P04.1 may use the six specified WOMEN SOURCE_SEED rows as test candidates, with `ao → TOP` and `quan → BOTTOM` only inside this fixture. The child-title and UNISEX rows remain excluded negative fixtures. The owner also authorized deterministic fake 128-dimensional embeddings and disposable Qdrant Testcontainers. See the implementation report for verified behavior and remaining gates.

## 1. Source reconciliation

Review was performed on branch feature/ep-07-ai-stylist at HEAD c9f20d1d1fc1779eca735b3de3ac9f1745b43985. The read-only Commerce seed is commerce-service/src/main/resources/db/seed/development-catalog.json, version 1, SHA-256 07A0AEEBFAB18CC42DC00EE034E499FC02A188B41C9F4DBF114A88AF7C1A1263. This matches the hash and base counts in the P04.0 offline review.

- The file contains 2 categories, 60 brands, 600 products and 2,400 variants; each product has four variants.
- The exact screen used for the earlier title review (Kids/Kid's, Girl/Girl's, Boy/Boy's, Teen, Children) finds 17 WOMEN-coded products with child-related title terms and 9 UNISEX products. Eight UNISEX rows also match the title screen. The queue contains 26 distinct product IDs, not 17 + 9 duplicate rows.
- Category split: Áo has 9 WOMEN title-screen rows and 8 UNISEX products; Quần has 8 WOMEN title-screen rows and 1 UNISEX product.
- The seed has no product/variant status fields. Seed-runner behavior is not evidence the runner executed or that any local database contains these records.
- No Qdrant client/dependency or Qdrant service reference was found in the current Stylist POM/source or Compose configuration. This proposal would add a new retrieval dependency only after an owner approves an implementation slice.

### P02 model/source comparison

Current P02 source agrees with the binding TOP + BOTTOM behavior:

- EngineModel.Item rejects any slot other than TOP and BOTTOM. EngineModel.Outfit enforces exactly one item in each slot.
- CandidatePolicy considers only TOP and BOTTOM; OutfitComposer loops only over those two required slots.
- EngineModel.Slot still declares ONE_PIECE, SHOES and ACCESSORY as legacy enum values so an invalid upstream mapping can be rejected explicitly. Their presence in the enum does not make them usable.
- One description in the P02 handover calls OutfitComposer “core-template and optional-slot enumeration”. That phrase is stale against the current OutfitComposer source, which only composes the exact two-slot template. The source code behavior and the P08 scope rule agree.

The P02 engine accepts caller-supplied price, purchasable and women-catalog flags. Those values are not verified by P02 against Commerce. P02 vocabulary/category-to-slot mappings are synthetic fixture inputs, not a live Commerce category mapping. A vector hit cannot safely populate authoritative current values for a final recommendation.

## 2. Child-title and UNISEX review

The 17 WOMEN-coded title-screen rows and all 9 UNISEX rows are listed with source IDs, exact names, category IDs/names, raw demographic, variant count, evidence, seed description and review note in [EP-07_P04_SEED_REVIEW_QUEUE.csv](EP-07_P04_SEED_REVIEW_QUEUE.csv). Every row is marked NEEDS_HUMAN_REVIEW; none is approved, changed, or removed from Commerce.

The title screen is only a review signal. Names such as “Levis Kids Girl's Denise Pink Teens Kidswear” and “Doodle Girl Princess Pink Tshirt” raise an audience question, but the generic development descriptions do not prove the intended audience. Do not conclude from a single token that a product is definitely for children, and do not rewrite its WOMEN value. UNISEX is a separate open question: a raw code does not establish suitability for a women’s outfit.

The six default sample proposals avoid both the child-title screen and UNISEX. Two additional rows are labelled review-only test cases and must not enter the default sample before human review. These choices describe a small review corpus, not a WOMEN-only catalog policy.

### UNISEX options — NOT APPROVED

| Option | Seed coverage | Review effort | Suitability risk |
|---|---|---|---|
| A. WOMEN only | 591 of 600 products and 2,364 of 2,400 variants; excludes all 9 UNISEX products / 36 variants. | Low for the code rule, but the 17 WOMEN title signals still need resolution. | Lower ambiguity from UNISEX; does not guarantee all WOMEN-coded items suit the target wearer. |
| B. WOMEN + UNISEX | Up to 600 products / 2,400 variants if the raw codes are accepted. | Low ongoing effort after one global definition decision; review the child-title signals separately. | Higher chance of unsuitable fits if UNISEX is broad or includes child/sports items. |
| C. Review each UNISEX product | Starts at the same 591 / 2,364 as A; individually approved rows could be added later. | Highest manual and ongoing curation effort. | Strongest control over individual suitability, while still requiring review of WOMEN title signals. |

These are raw demographic counts, not approved-candidate counts. If the 17 WOMEN title-signal records are held out pending review, that temporarily leaves 574 WOMEN-coded products / 2,296 variants before category mapping approval. No option is selected. The seed provides no owner-approved meaning for UNISEX.

## 3. Category mapping review — PROPOSED — NEEDS_HUMAN_REVIEW

The current category labels and description groups support a tentative slot proposal, but do not prove every product is suitable or correctly classified.

| Category | Seed evidence | Proposed slot | Exceptions / reason it still needs review |
|---|---|---|---|
| ao / Áo | 300 products, 1,200 variants. Description groups are Tops (156), Tshirts (121), Shirts (23). The simple opposite-slot title screen found no obvious jeans/pants/shorts/trousers/leggings terms. | TOP | Nine WOMEN-coded products match the child-title screen; eight more products are UNISEX. Example: ADIDAS Kids Unisex YG 3S Core Ess Pink T-Shirt. Title/description screening is not a category homogeneity review. |
| quan / Quần | 300 products, 1,200 variants. Description groups are Jeans (97), Leggings (80), Shorts (32), Trousers (42), Track Pants (34), Jeggings (15). The simple opposite-slot title screen found no obvious top/shirt/blouse/tee terms. | BOTTOM | Eight WOMEN-coded products match the child-title screen and one product is UNISEX: Levis Kids Blue Solid Jean. The generic description does not resolve wearer suitability. |

The keyword screen is intentionally narrow and cannot prove zero miscategorization. Both category rows remain PROPOSED — NEEDS_HUMAN_REVIEW in the review files. No product is auto-approved because its category slug is ao or quan.

## 4. Review sample

[EP-07_P04_REVIEW_SAMPLE.csv](EP-07_P04_REVIEW_SAMPLE.csv) has eight rows:

- Six DEFAULT_SAMPLE_PROPOSAL records: three TOP and three BOTTOM. All happen to carry raw demographic WOMEN and avoid the child-title signal. This is a sample-selection precaution only, not a global demographic rule.
- Two REVIEW_ONLY_TEST_CASE records: one WOMEN-coded child-title signal and one UNISEX item. Both have selected_for_default_sample=NO.
- All rows are SOURCE_SEED and NOT_RUNTIME_VERIFIED. Category mapping is PROPOSED — NEEDS_HUMAN_REVIEW. Every price is labelled SOURCE_SEED_SNAPSHOT_NOT_CURRENT; product and variant statuses are NOT_IN_SEED.
- Material is empty and marked MISSING for all sample variants. No image, customer data or inventory field is included.

The seed examples are ready for a human to review. They are not ready for a real P04.1 index until the category mapping and applicable audience decisions have been approved. This review does not fabricate an “approved” mapping for any sample.

## 5. Proposed P04.1 pipeline — not implemented

### Projection and eligibility

1. Parse a versioned source snapshot into one immutable projection record per Commerce variant. Carry source provenance, seed hash, product/category/variant IDs and the raw catalog fields; do not infer status, ownership or current price.
2. Resolve slot only by an explicit category-ID mapping artifact. Require a reviewer, mapping version and approved state before an actual source row can enter the eligible index. Missing, pending, rejected or unknown mapping yields no candidate.
3. Preserve demographic_segment_raw unchanged. Eligibility requires an explicit owner-approved rule; unresolved or unknown values remain in review/excluded from an eligible search. Do not silently choose the WOMEN-only or WOMEN + UNISEX policy.
4. Validate required IDs/name/price and reject malformed prices. Preserve optional material as null and mark it MISSING; do not invent a material value.
5. Build retrieval text from present text fields only: product name, approved category label, description, color, size and material when present. Keep identifiers, price and status out of embedding text. Do not include image URLs or inventory.

### Embedding and Qdrant proposal

- Embedding boundary: a small CatalogEmbeddingPort accepts a list of text strings and returns fixed-dimension vectors plus an implementation/version label. The production adapter is not selected or implemented.
- Offline fake: DeterministicHashEmbedding for tests only. Proposed algorithm: Unicode normalization, lowercase tokenization, stable SHA-256 token hashing into a 128-dimensional signed vector, then L2 normalization. Equal text always produces equal vectors. A simple exact-text fixture query can prove vector serialization and ranking plumbing; this does not represent semantic similarity.
- Vector record: one point per Commerce variant, with the variant UUID as the stable point key. A duplicate variant ID within one import batch is rejected before upsert, rather than silently selecting conflicting metadata.
- Collection: create a unique disposable collection per test run, for example stylist_p04_test_<run-id>, cosine distance, vector size 128. Pin an approved Qdrant image/client version only in the implementation approval. Use an isolated Testcontainers instance with random port/no persistent volume and delete it at teardown; do not reuse project Compose or team volumes.
- Payload proposal: product_id, variant_id, category_id, category_name, proposed_slot, mapping_status, mapping_version, demographic_segment_raw, product_name, description_raw, color_raw, size_raw, material_raw/material_status, price_vnd_seed_snapshot, source, source_sha256 and runtime_status=NOT_RUNTIME_VERIFIED. No image URL, customer field, stock, or inferred product/variant status.
- Search input: query text, required slot TOP or BOTTOM, mapping version, explicit demographic eligibility decision and limit constrained to 1–20. Filter by slot and approved mapping state. A test-only fixture may contain an explicitly approved mapping solely to exercise the filter; it must use fixture IDs and cannot make a SOURCE_SEED row approved.
- Result: bounded Candidate records containing product_id, variant_id, slot, vector score and provenance/snapshot metadata. A result is only a retrieval candidate, not an outfit, purchase offer, current price, current status or fashion-quality judgment.
- Qdrant failure is distinct from an empty search. Proposed behavior is a typed retrieval-unavailable failure; do not convert an outage to “no products” or silently fall back to an unfiltered catalog. There is no new public HTTP contract in this proposal.

After an actual Commerce integration is approved, current product/variant status and price must be rechecked through Commerce before final eligibility, budget evaluation or saving. Qdrant’s price/status snapshot must never be presented as current authority. Current P02 engine input flags must be populated from authoritative, validated values, not from a vector payload alone.

### Expected test cases

| Case | Proposed check and expected behavior |
|---|---|
| 1. Find TOP | Query a fixture text with TOP mapping; return only bounded TOP candidates with variant IDs and provenance. |
| 2. Find BOTTOM | Same for BOTTOM; no TOP candidate passes the slot filter. |
| 3. Category not approved | Pending/unmapped mapping is filtered out; no candidate is promoted by category name alone. |
| 4. Demographic undecided | UNISEX/unknown remains unresolved and is not silently admitted; test uses a fixture decision boundary, not a selected owner policy. |
| 5. Missing material | Null material is retained as missing metadata; text construction omits it and does not invent a value. |
| 6. Empty result | Return an empty bounded set for that query/slot; do not claim catalog-wide absence or compose a partial outfit. |
| 7. Qdrant unavailable | Surface the typed retrieval failure, distinct from empty results; no unfiltered fallback. |
| 8. Duplicate variant ID | Reject duplicate/conflicting input before indexing; do not overwrite based on row order. |
| 9. Bad required metadata | Reject missing/invalid product, variant or category ID, missing product name, malformed/negative price; optional missing material remains allowed. |
| 10. Stale price/status | Treat payload values as seed snapshots only; require authoritative Commerce recheck before hard-budget/final/save decisions. A seed-only test cannot pass this live recheck. |

Use pure unit tests for text building, fake vectors, mapping filters and validation; Qdrant integration tests use only a disposable container and unique collection. Neither test type proves semantic retrieval or style quality.

## 6. Backlog boundary and decisions required

This proposal does not mark P04.1, R98 or R99 done. A one-time seed import is not R99 event synchronization. This Qdrant text-search design does not complete the broader R98 retrieval/approved-knowledge requirement; Neo4j, knowledge review and generation remain separate plan steps and are not removed from backlog.

Decision state after the 2026-11-02 owner authorization:

1. **Resolved for P04.1 only:** the six named WOMEN rows may enter this fixture; the 17 WOMEN title-signal and 9 UNISEX review populations still have no global demographic policy. The child-title and UNISEX rows remain negative/review fixtures.
2. **Resolved for P04.1 only:** the fixture uses `ao → TOP` and `quan → BOTTOM`. This does not approve a catalog-wide taxonomy or product exceptions.
3. **Resolved for P04.1 only:** use the six clean WOMEN sample rows as disposable technical candidates. This does not establish semantic, fashion or live-catalog quality.
4. **Resolved for P04.1 only:** use the pinned disposable Qdrant Testcontainers setup and collection lifecycle described in the report. Shared Compose and runtime deployment remain unapproved.
5. **Resolved for P04.1 only:** use deterministic `FAKE_TEST_ONLY` 128-dimensional vectors. Provider/model choice, permitted live data, privacy, cost, retention and model version remain unapproved.
6. **Still open for later work:** define a Commerce snapshot/revision and change-event contract before claiming projection refresh or R99. Current price/status must be authoritatively rechecked at the later integration boundary.

## 7. Source references

- Commerce seed and conditional writer: commerce-service/src/main/resources/db/seed/development-catalog.json; commerce-service/src/main/java/com/stylemind/commerce/seed/DevelopmentCatalogSeedRunner.java.
- Commerce response DTOs: commerce-service/src/main/java/com/stylemind/commerce/dto/product/ProductResponse.java and VariantResponse.java.
- P02 engine source: stylist-service/src/main/java/com/stylemind/stylist/model/recommendation/EngineModel.java; stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/CandidatePolicy.java; stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/OutfitComposer.java.
- P02 handover: docs/planning/stylist/EP-07_P02_IMPLEMENTATION_REPORT.md. P04.0 live/runtime and seed-only boundaries: docs/planning/stylist/EP-07_P04_CATALOG_READINESS_REPORT.md and EP-07_P04_SEED_OFFLINE_REVIEW.md.

## 8. Verification and limits

At the time this proposal was prepared, work was limited to read-only source analysis and documentation/CSV preparation. The later P04.1 implementation and disposable Qdrant test run are recorded in [the implementation report](EP-07_P04_1_RETRIEVAL_POC_REPORT.md). All sample identifiers remain `NOT_RUNTIME_VERIFIED`; no live Commerce API/database was read, and no row in the source CSVs was changed to APPROVED. P04/R98/R99 remain incomplete.
