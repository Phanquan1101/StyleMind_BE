# EP-07 — Owner business correction and P02 baseline

Effective 2026-10-28. **CONFIRMED OWNER INSTRUCTION**, scoped to corrected business context and P02 offline implementation. Source: owner message titled “BUSINESS CONTEXT CORRECTION”, requesting source review, planning updates and implementation of P02. This supersedes contrary 27/10 statements in this planning package; API, migrations, providers and live integrations remain unapproved.

- The business is women's clothing commerce. Every wardrobe item is a shop product/Commerce variant. There is no upload of arbitrary external garments, image analysis, shoes, hats or accessories.
- Valid complete outfits are exactly TOP + BOTTOM (2 items), or ONE_PIECE (1 item). Slots are mapped explicitly, never guessed from names. No optional slots.
- Wardrobe may contain owned or merely saved shop items. Only explicitly confirmed OWNED items cost 0 VND of additional purchase; SAVED does not prove ownership and uses the current shop price. All runtime items retain variant_id; wardrobeReference is separate, never a cast of another ID.
- Selected confirmed owned items are anchors, preserved in every result. With an owned TOP, search a purchasable BOTTOM; with an owned BOTTOM, search a purchasable TOP. A selected owned ONE_PIECE or two already-owned complementary anchors leaves no missing clothing slot: no cross-sell result under these templates. This is the P02 interpretation of completing missing slots; no hidden extra clothing or purchase is invented.
- Runtime membership/ownership is supplied by trusted context upstream, not a future public client boolean. P02 uses synthetic fixtures only. Current producer cannot yet establish this ownership distinction.
- Exact nonnegative whole VND, checked arithmetic, quantity 1, per-outfit hard budget inclusive at equality; only items to buy count. No tax/shipping/discount. Source price is Commerce-owned; offline fixtures are not live validation.
- Retain P02 hard exclusions, soft preferences, deterministic scoring/diversity. Hard filters always win. Unknown hard color rejects; unknown soft mapped codes warn/ignore. UUID exclusions compare exact identifiers and do not prove resource existence.
- Retain bounded tuning from the P02 plan: 20 candidates/slot, 60 total, 1,000 search ceiling, 3 results. With three clothing slots only, actual enumeration is at most 20*20+20 = 420 complete combinations. Truncation is stable by variant UUID, anchors first; search is not catalog exhaustive.
- Soft weights remain context 3, style 2, color 2, brand 1, soft-budget 1, reviewed compatibility 3. Binary context evidence is present if at least one item matches; compatibility matches the full variant set. Item preference fractions are exact over outfit size (1 or 2). Soft exclusion penalty is one point per matching item/constraint, a documented offline tuning convention. Missing evidence is neutral. Ties use sorted variant UUIDs. Do not claim these scores measure fashion quality.

## Source review on current branch

Actual folder is `wardrobe-community-service`, not a separate wardrobe-service. V1/outfit_item contains outfit_item_id, outfit_id, variant_id, display_order, created_at. OutfitService reads Commerce and saves variants; create accepts 1–10 items. No per-item owned/saved state or purchase-proof field exists. This is a producer capability gap, not evidence of external garments or permission to change that service. Existing 1–10-item wardrobe storage is not modified to enforce Stylist's 1/2-item templates.

Commerce V2 defines product_variant.variant_id, product_id, bigint unit_price_vnd >= 0 and ACTIVE/INACTIVE status; images belong to variants. Product has category_id, brand_id and free-text demographic_segment. InternalVariantItem returns purchase eligibility and price but not category/brand/demographic. Therefore live integration still needs mapped clothing slot/women scope and trusted owned status from owning contracts. No live proof is inferred from enum/fixture values.

## Model and execution implications

Prior target model source_type/XOR and owned-item references are unchanged historical/target shapes; this owner correction establishes that BOTH sources belong to Commerce's ecosystem. P02 uses runtime Holding NONE/SAVED/OWNED plus required Commerce variantId and a separate wardrobe reference when applicable. No new column or migration is approved. Reconcile persistence/API representation in P03/P07 before implementing it; no fake external-item category or schema workaround.

P02 is authorized. The remaining phase gates stay separate. Existing 27/10 review evidence remains historical; its old domain interpretation and optional-slot proposals are superseded here. Synthetic tests do not complete R95 live context or R99 events, nor the entire US-07.01/02.
# Historical correction — superseded by Prompt 08 (2026-10-29)

This document records the 2026-10-28 baseline. Its `ONE_PIECE` slot and broader product statements are superseded by the owner’s 2026-10-29 decision: TOP+BOTTOM only, exactly one of each. See the current [planning index](README.md) and [P02 handover](EP-07_P02_IMPLEMENTATION_REPORT.md).
