# EP-07 / P04.0 Follow-up — Offline Seed Data Review

**Status: offline source review only.** This report reads the checked-in Commerce development seed as static input. It does not establish that the records exist in a running Commerce service or database, and it does not approve a live category/demographic mapping. No P04.1 implementation, R98/R99 completion, or recommendation-quality claim is made.

## 1. Scope and source evidence

- Branch: `feature/ep-07-ai-stylist`; HEAD at review start: `c9f20d1d1fc1779eca735b3de3ac9f1745b43985`.
- Seed source: `commerce-service/src/main/resources/db/seed/development-catalog.json`.
- Source size: 1,157,470 bytes. SHA-256: `07A0AEEBFAB18CC42DC00EE034E499FC02A188B41C9F4DBF114A88AF7C1A1263`.
- Parsed read-only at 2026-11-02 01:07 +07:00. Seed manifest version: 1.
- Supporting sources reviewed: `commerce-service/src/main/java/com/stylemind/commerce/seed/DevelopmentCatalogSeedRunner.java`; Commerce Product/Variant response DTOs; `AGENTS.md`; `stylist-service/AGENTS.md`; `docs/planning/stylist/EP-07_IMPLEMENTATION_PLAN.md`; `docs/planning/stylist/EP-07_DATASET_READINESS_GUIDE.md`; and the existing P04 catalog readiness report.
- The worktree already had staged, unstaged, and untracked work before this follow-up. That work was preserved.

No Docker or Compose command was run; no HTTP API or database was queried; no seed runner, migration, Qdrant, embedding, LLM, image download, or provider call was run. Image URLs and inventory quantities were not exported or evaluated. The Excel workbook was not changed.

## 2. Static seed inventory

| Seed entity | Count |
|---|---:|
| Categories | 2 |
| Brands | 60 |
| Products | 600 |
| Variants | 2,400 |

Each product has exactly four variants. The seed contains one category labelled Áo (`ao`, `fbb973c7-c888-53ba-8648-76a4700c3757`) and one labelled Quần (`quan`, `397b5054-84f4-5cc6-8047-56787826b9bf`). Each category has 300 products and 1,200 variants.

| Category | WOMEN products / variants | UNISEX products / variants | Description labels and counts |
|---|---:|---:|---|
| Áo | 292 / 1,168 | 8 / 32 | Tops 156; Tshirts 121; Shirts 23 |
| Quần | 299 / 1,196 | 1 / 4 | Jeans 97; Leggings 80; Shorts 32; Trousers 42; Track Pants 34; Jeggings 15 |
| Total | 591 / 2,364 | 9 / 36 | — |

The raw demographic values present are WOMEN and UNISEX only; there are no null/empty demographic values in this file. This is a statement about the seed contents only, not the Commerce contract or live catalog.

## 3. Integrity and retrieval-field observations

- Category IDs (2), brand IDs (60), product IDs (600), variant IDs (2,400), product image-asset IDs (600), variant image IDs (2,400), and SKUs (2,400) are present and unique within their entity sets. Repeated category/brand references across products are expected and are not duplicate primary IDs.
- All product category and brand references resolve to an ID in the seed's category and brand lists. Orphan category references: 0; orphan brand references: 0.
- Product and variant identifiers are UUID-shaped. Product names, IDs, category/brand references, variant IDs/SKUs, size/color values, and prices are populated in all seed rows.
- All 2,400 `unitPriceVnd` values are nonnegative integers. Minimum: 199,000 VND; median: 249,000 VND; maximum: 449,000 VND. These are seed snapshots, not current prices, quotes, or price authority.
- All 600 descriptions are non-null, but are generic development labels (nine category/type labels listed above), not detailed garment descriptions. `material` is null for all 2,400 variants. Color and size are present in the seed, but were not validated against fashion ground truth or a controlled vocabulary.
- The JSON does not include category/brand active state or product/variant status. The seed runner sets categories and brands active and writes product/variant status as ACTIVE when the conditional runner executes; that is runner behavior, not a status observed in this JSON or a running database. The runner is enabled only by its configured property. It was not invoked here.

### Product-title review signal

A literal title screen for child-related terms (`Kids`, `Kid's`, `Girl`, `Girl's`, `Boy`, `Boy's`, `Teen`, `Children`) matches 25 names: 17 with raw demographic WOMEN and 8 with raw demographic UNISEX. Examples include “Levis Kids Girl's Denise Pink Teens Kidswear” (WOMEN), “Gini and Jony Girl's Scotia White Purple Kidswear” (WOMEN), “ADIDAS Kids Unisex YG 3S Core Ess Pink T-Shirt” (UNISEX), and “Levis Kids Blue Solid Jean” (UNISEX).

This is a review flag, not an automated eligibility decision: product names can be noisy and the title screen is not a classifier. Still, the WOMEN code alone is insufficient evidence that each flagged item is suitable for the women’s Stylist catalog. A human or Commerce-owned source rule must resolve those records before live eligibility is defined.

## 4. Category-to-slot mapping proposal — NOT APPROVED

The names and generic seed description labels suggest the following initial mapping for review:

| Seed category | Proposed Stylist slot | Mapping status | Review status | Evidence / ambiguity |
|---|---|---|---|---|
| Áo | TOP | PROPOSED | NEEDS_REVIEW | 300 products / 1,200 variants; description groups Tops, Tshirts, Shirts. Examples include Tokyo Talkies Women Printed White Top and Puma Women USP Chest Cat Fuchsia T-shirt. The title screen also finds WOMEN rows with Kids/Girl/Teens terms; category-level evidence does not prove every row is an eligible women’s top. |
| Quần | BOTTOM | PROPOSED | NEEDS_REVIEW | 300 products / 1,200 variants; description groups Jeans, Jeggings, Leggings, Shorts, Track Pants, Trousers. Examples include Aurelia Women Solid Purple Leggings and Lee Womens Blue Maxi Fit Jeans. It contains a UNISEX product titled Levis Kids Blue Solid Jean and WOMEN rows with child-related title terms. |

Neither mapping is approved. The CSV [EP-07_P04_SEED_CATEGORY_REVIEW.csv](EP-07_P04_SEED_CATEGORY_REVIEW.csv) is a human review queue, not a production mapping artifact. A mapping must be approved by the responsible business/Commerce reviewers and checked for category homogeneity before any record is eligible for Stylist retrieval. P08 still limits outfits to exactly one TOP and one BOTTOM; it does not itself approve these Commerce categories.

## 5. UNISEX decision options — all NOT APPROVED

The seed contains 9 UNISEX products / 36 variants: 8 products / 32 variants in Áo and 1 / 4 in Quần. All options below require an owner decision; none is currently the rule.

| Option | Proposed handling | Impact / trade-off |
|---|---|---|
| A — WOMEN only | Exclude every UNISEX product from live candidates. | Simple conservative boundary; removes 9 products / 36 variants from this seed and may reduce coverage. Does not resolve child-related title flags among WOMEN-coded items. |
| B — include UNISEX after definition approval | Include UNISEX only if Commerce defines the code and the owner confirms its meaning is suitable for this women’s catalog. | More coverage; a broad or inconsistent meaning can reduce wearer/style suitability. Child-related title flags still need review. |
| C — product-level review | Keep UNISEX excluded by default; include only individually approved product records, with evidence and a maintained review list. | Highest control; requires manual curation and an owner for ongoing review. |

Recommended for review: Option C, because the current seed’s UNISEX group contains child-oriented titles and the code’s meaning is not confirmed by this static file. This is a recommendation only, NOT APPROVED. Null/unknown handling also remains a separate contract decision even though this seed has no null demographic values.

## 6. P04.1 seed sample — reference only

[EP-07_P04_SEED_SAMPLE.csv](EP-07_P04_SEED_SAMPLE.csv) contains eight real identifiers copied from the checked-in seed: six WOMEN-coded example rows (three proposed TOP, three proposed BOTTOM) and two UNISEX review rows. Every row has `source=SOURCE_SEED`, `human_review_status=NOT_REVIEWED`, and a visible `mapping_status`; the UNISEX examples are marked `NEEDS_REVIEW`. Product and variant status columns say `NOT_IN_SEED` because the JSON does not carry those fields.

The sample includes raw category, demographic, product name, description, color, material, size, and `price_vnd_snapshot`. Null material is preserved as empty. No image URL is included. These IDs are seed identifiers, not proof of database presence; the values and prices are not current Commerce observations. Do not use this CSV as a live projection or recommendation fixture without retaining its source provenance and review state.

## 7. Preparation gates before live P04.1

1. Commerce/business reviewers approve or reject each category-to-slot mapping and identify any mixed or unsuitable products.
2. The owner chooses a UNISEX policy (A/B/C), resolves null/unknown demographic handling, and has Commerce document the accepted demographic vocabulary.
3. Review the WOMEN-coded child-title signals; decide whether to exclude/curate specific rows through an owner-approved source rule. Do not create a new Commerce field or mutate the seed as part of this offline review.
4. Obtain a safe, read-only runtime catalog snapshot through the API after the persistent environment is independently verified safe. Include capture time or source revision and keep the current seed-only sample separate from live evidence.
5. Keep Commerce as the authority for current product/variant state and price. The static seed runner’s status writes do not prove that it has run.
6. Resolve image access/rights separately before image retrieval or external provider use. No image processing is needed for the current offline review.
7. Obtain the approved retrieval-store, snapshot/change-event, embedding-provider, fields, privacy, budget, and Wardrobe ownership contracts before their respective implementation slices.

## 8. Verification and changed files

This was a documentation/data inspection task; no application test or runtime integration test was run. The JSON was parsed read-only, aggregate counts and references were checked, and both CSV files were structurally validated after creation. The existing live P04 report’s catalog state remains NOT_OBSERVED/UNKNOWN; this seed review does not change it.

Files created:

- `docs/planning/stylist/EP-07_P04_SEED_OFFLINE_REVIEW.md`
- `docs/planning/stylist/EP-07_P04_SEED_CATEGORY_REVIEW.csv`
- `docs/planning/stylist/EP-07_P04_SEED_SAMPLE.csv`

The existing P04 report contains a short link to this offline review. The execution manifest, fingerprints, phase statuses, Commerce source, seed JSON, migrations, Compose, environment files, workbook, and databases were not changed.

## P04.1 preparation review

See the [seed review queue](EP-07_P04_SEED_REVIEW_QUEUE.csv), [retrieval POC proposal](EP-07_P04_RETRIEVAL_POC_PROPOSAL.md), and [review sample](EP-07_P04_REVIEW_SAMPLE.csv). All mappings remain PROPOSED — NEEDS_HUMAN_REVIEW; SOURCE_SEED rows are not runtime evidence.
