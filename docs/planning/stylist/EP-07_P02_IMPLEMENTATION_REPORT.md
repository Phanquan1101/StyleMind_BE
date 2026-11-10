# EP-07 P02 — Pure recommendation engine handover

Date: 2026-10-29, Asia/Bangkok. Branch: `feature/ep-07-ai-stylist`.

**Scope:** P02 product-scope correction: offline Java recommendation engine, synthetic test fixtures, focused tests and this P02 handover. This is evidence for the P02 correction; it does not complete a User Story or establish live ownership/catalog mapping/provider behavior.

## Owner scope

Owner Prompt 08 on 2026-10-29 supersedes all earlier EP-07 slot rules. The only supported product slots are women’s `TOP` and `BOTTOM`; every outfit contains exactly one of each. `ONE_PIECE`, `SHOES`, `ACCESSORY`, and unknown slots are rejected, including in P02 synthetic fixtures. This correction is limited to the pure engine and synthetic fixtures. It does not establish live Commerce category mapping or live ownership evidence. The same Prompt 08 separately authorizes P03-A, documented in [its handover](EP-07_P03A_SESSION_HISTORY_REPORT.md).

Approved P02 rules implemented:

| Area | P02 behavior |
|---|---|
| Context | Explicit scalar presence, including explicit `null`, overrides caller-selected saved criteria; if neither is supplied the runtime value is absent. Profile preferences only affect ranking. Constraints and selected-owned IDs are complete request sets, with no implicit previous-request union. Object/fixture behavior only; no public HTTP contract. |
| Source identity | Closed typed source union: `SHOP_VARIANT` or `OWNED_WARDROBE`. Variant and wardrobe IDs remain distinct; no `outfit_item_id` or `variant_id` is cast to `wardrobe_item_id`. Owned source inputs are synthetic trusted fixtures only. |
| Budget | Per outfit; one unit per unique shop variant; exact whole VND with checked overflow. Owned anchors add zero; optional shop items are charged. Shipping, tax and discounts are excluded. Hard budget is inclusive at equality, rejects totals above it, requires an amount, and accepts zero. Soft budget affects ranking only. |
| Templates and anchors | Exactly one `TOP` and one `BOTTOM`, no other slots. Each source item appears once. Selected permitted owned items are retained as anchors in every result. Conflicting or hard-excluded anchors invalidate the context. Missing candidates may yield fewer or no results; two owned anchors do not trigger cross-sell. |
| Exclusions and mapping | Known hard exclusions cannot be overridden by soft preferences. Unknown hard kinds/values are rejected without guessing; unknown soft preferences are ignored with warnings. Fixture category/slot/style/color mappings are labeled `SYNTHETIC_TEST_ONLY`; they are not live catalog taxonomy. |
| Score and bounds | Weights: context 3, style 2, color 2, brand 1, soft-budget fit 1, reviewed compatibility 3. Missing evidence contributes zero. Context and compatibility are curated binary fixture evidence; no LLM. Scores use exact rational arithmetic and deterministic tie-breaks. Candidate cap 60; at most 20 candidates per mandatory slot; at most 1,000 combinations (the two-slot cap currently yields at most 400); at most 3 results. Results differ by at least one non-anchor item. Hard rules are never relaxed to fill results. Empty output means no result in the bounded search, not catalog-wide absence. |

## Source and backlog review

Read the current project context/business rules/open decisions, Stylist instructions and planning set, owning Wardrobe/Commerce models and migrations, and workbook `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx` sheet `Backlog` (read-only). Workbook SHA-256: `1f9657c9e6df2992572e86338cb4c985c21451ed4fcca08d338fb00c6b7cde73`, matching the recorded digest.

Relevant backlog rows:

| Row | Backlog item | P02 contribution |
|---|---|---|
| R96 | US-07.01 — Test invalid context, conversation ownership and unavailable context services. | Covers pure object-level context precedence, invalid anchors/constraints and no hidden request inheritance; no conversation owner or unavailable HTTP service is implemented. |
| R101 | US-07.02 — Recheck shop variants, prices and hard constraints before saving recommendations. | Exercises deterministic fixture inputs, exact cost and hard constraints; no live recheck or save path. |
| R102 | US-07.02 — Test fake variants, wrong prices, hard-budget violations and unavailable AI results. | Exercises typed synthetic references, exact prices, hard-budget rejection and bounded no-result; no live Commerce/provider integration. |

The current Wardrobe implementation stores saved shop outfit items with Commerce variant references; the reviewed model has no owned-garment evidence or owned wardrobe item reference. The P02 `OWNED_WARDROBE` values therefore remain synthetic and are not proof of live ownership support. Commerce is not changed. Do not map an outfit-item or variant ID to a wardrobe-item ID. P02 evidence must not be used to mark R96/R101/R102 or their User Stories complete.

## Implemented files

| File | Responsibility |
|---|---|
| `stylist-service/src/main/java/com/stylemind/stylist/model/recommendation/EngineModel.java` | Immutable offline values, typed source references, exclusions, request context, exact rational score and outfit invariants. |
| `stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/ContextResolver.java` | Scalar context precedence and explicit absence/null handling without request-history state. |
| `stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/BudgetPolicy.java` | Exact VND conversion and checked whole-VND total. |
| `stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/CandidatePolicy.java` | Source/anchor validation, exclusions, fixture eligibility, warnings and stable candidate caps. |
| `stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/OutfitComposer.java` | Bounded core-template and optional-slot enumeration. |
| `stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/OutfitScorer.java` | Exact weighted score from supplied preferences and reviewed fixture evidence. |
| `stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/ResultDiversity.java` | Deterministic score ordering, tie-break and non-anchor diversity. |
| `stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/OutfitEngine.java` | Stateless orchestration, checked cost, hard-budget filter, warnings and result cap. |
| `stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/OutfitWhitelist.java` | Freezes complete outfit aliases and rejects unknown/duplicate selections without recomposition. |
| `stylist-service/src/test/java/com/stylemind/stylist/engine/OutfitEngineTests.java` | Engine rule, limit, invariant and fixture execution tests. |
| `stylist-service/src/test/resources/stylist/fixtures/p02-commerce-examples.json` | Three executable `SYNTHETIC_TEST_ONLY` examples and synthetic mapping. |
| `docs/planning/stylist/EP-07_EXECUTION_MANIFEST.json`, `docs/planning/stylist/README.md` | Recorded 2026-10-29 scoped authorization and P02 handover pointer. |
| `docs/planning/stylist/EP-07_P02_IMPLEMENTATION_REPORT.md` | This evidence and limitations record. |

No HTTP endpoint, Spring use-case API, migration, provider call, network call or new configuration was added. There is no Postman collection because P02 creates no HTTP API to exercise. No other service source was changed by this P02 implementation. Existing staged planning versions and unrelated worktree changes were preserved.

## Fixture outcomes

The JSON fixture is loaded and executed by `publishedExamplesAreExecutableAndAllMappingsAreSynthetic`.

1. **Shop-only, exact hard-budget boundary:** synthetic TOP + BOTTOM equals the inclusive hard budget and is accepted; the stable ranking is deterministic.
2. **Synthetic owned anchor:** an owned TOP costs zero additional VND and remains in the result with a shop BOTTOM. A result still has exactly two items.
3. **Saved is not owned:** a saved shop TOP remains charged at its fixture price; the hard-budget violation yields no result.

## Verification evidence

P02 correction regression command:

```powershell
mvn -B -ntp -f stylist-service/pom.xml '-Dtest=OutfitEngineTests,StylistServiceApplicationTests' test
```

Result: **65 tests, 0 failures, 0 errors, 0 skipped** — 52 pure engine tests plus 13 existing application/security/OpenAPI regression tests. The final combined regression and PostgreSQL integration result is recorded in [P03-A handover](EP-07_P03A_SESSION_HISTORY_REPORT.md). The integration database is disposable; no persistent application database is targeted.

Test coverage includes context precedence and request isolation; typed source XOR and ID separation; saved versus owned costs; exact TOP+BOTTOM pair, missing slot and rejected slots; exact equality, one-VND-over, zero and overflow; hard/soft/unknown exclusions; score weights and rational fractions; anchors; candidate/composition/result bounds; stable ordering/diversity; immutable inputs; synthetic fixture execution; and existing app regression behavior.

The Prompt 09 planning review on 2026-10-31 corrected the two stale dependency links and refreshed only the fingerprints whose changed source was reviewed. The final validator still reports committed-source drift for the requested Identity files:

- `identity-service/src/main/java/com/stylemind/identity/bootstrap/LocalComposeAdminBootstrapService.java`
- `identity-service/src/main/java/com/stylemind/identity/client/notification/NotificationEmailClient.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/AddressController.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/AdminUserController.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/AuthController.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/InternalAccountController.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/JwksController.java`
- `identity-service/src/main/java/com/stylemind/identity/controller/ProfileController.java`

No fingerprint or broken-link failures remain after targeted review. The broader branch contains 155 changed Identity paths since the manifest’s recorded review head; that baseline was deliberately not advanced based on a partial review. The validator exits 1 on this remaining commit-range drift, unrelated to the P02 engine. See the [P03-A handover](EP-07_P03A_SESSION_HISTORY_REPORT.md) for the source review and final validator results.

## Limits and next work

- Candidate/category mapping and ownership are trusted fixture inputs only; live producers and authorization evidence remain unresolved.
- No live prices, revalidation before save, live ownership, LLM, embedding, RAG, or fashion-quality review was tested. P03-A adds session/history persistence and APIs only; it does not implement request admission or recommendation.
- The engine bounds candidate and combination search, so no result means no result in that bounded set. It does not prove no possible catalog outfit exists.
- Score weights and binary compatibility/context evidence are transparent offline ranking rules, not measured fashion-quality or conversion-rate results.
- The P02 correction is complete as an offline engine slice. P03-A is separately authorized and reported. P03, US-07.01 and EP-07 remain incomplete. No commit, push or merge was performed.
