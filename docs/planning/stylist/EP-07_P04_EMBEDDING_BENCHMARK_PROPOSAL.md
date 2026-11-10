# EP-07 / P04 — proposed real-embedding benchmark

**Status: PROPOSAL ONLY — NOT APPROVED, NOT RUN.** This document prepares a small evaluation after separate owner approval. It does not select or configure a production model, approve external data transfer, or change P04/R98/R99 status.

## What is and is not being evaluated

The current P04.1 POC uses a deterministic hash fake. It verifies vector plumbing only; its scores are not semantic relevance, probability or fashion-quality scores. A real benchmark should answer the narrower question: for a reviewed Vietnamese request and an explicit TOP/BOTTOM slot, does embedding retrieval place human-labeled eligible SOURCE_SEED candidates in the top K without violating hard eligibility?

The benchmark must not use a provider to decide product eligibility, demographic policy, current price/status, ownership, budget validity or outfit completeness. Those remain deterministic service rules and owner-approved source contracts. The six reviewed fixture records are a tiny smoke set, not statistically representative catalog coverage.

## Proposed data set and split

- Source only: the eight-row snapshot represented by EP-07_P04_REVIEW_SAMPLE.csv and the checked development catalog seed, SHA-256 07A0AEEBFAB18CC42DC00EE034E499FC02A188B41C9F4DBF114A88AF7C1A1263. Current source-file matching was verified in this review, but the seed is still NOT_RUNTIME_VERIFIED.
- Development positives: four products (two TOP, two BOTTOM); holdout positives: two different products (one TOP, one BOTTOM). Split by product ID, not by query paraphrase, so one product cannot appear in both tuning and holdout.
- Keep the child-title WOMEN row and UNISEX row as negative/review cases. Do not put them in the accepted retrieval corpus unless Commerce/owner resolves the policy and the labels are explicitly reviewed.
- Do not add images, image URLs, customer data, user prompts, wardrobe data or any other service data. Query text should be authored from the approved seed attributes, not copied from real user requests.
- Keep source prices/status as labeled seed snapshots only. Never use these values to make a current purchase, budget or availability claim.
- The CSV at EP-07_P04_EVALUATION_REVIEW_TEMPLATE.csv is a starter label sheet. All targets and outcomes are Agent-proposed, unapproved labels; reviewers should edit/confirm them before any metric is reported.

## Query and label procedure

1. A catalog/business reviewer confirms category-to-slot and demographic treatment. A stylist reviewer independently checks positive relevance, no-result and ambiguity cases. Record reviewer name/role and date in the notes column; do not convert an Agent-proposed label into approval silently.
2. Freeze the product-level development/holdout split and the human labels before model runs. Tune text assembly, K or any threshold on development only. Run holdout once after choices are frozen; do not feed holdout failures back into tuning and then report the same set as independent evaluation.
3. Separate positive retrieval labels from safety cases. Blank/ambiguous requests, unsupported slots, wrong-slot requests, missing hard attributes, unknown demographics and review-only products must not be counted as ordinary positive recall queries.
4. For a query whose requested hard attribute is absent (for example, cotton when material is null), mark REVIEW_REQUIRED_MISSING_METADATA. A retrieved item with null material cannot be credited as matching that material. Do not infer the missing attribute from product name, image or a vector score.
5. Record all relevant variant IDs if reviewers identify multiple candidates. A candidate not in the six-row sample is not automatically irrelevant in a future full catalog; this pilot can only label the reviewed snapshot.

## Proposed measurements

- Candidate recall@K for K=1, 3 and 5: among queries with at least one reviewer-approved relevant candidate, count a hit when any approved variant ID appears in the top K. Report per slot and overall with numerator/denominator. Do not include no-result, clarification or review-required cases in the recall denominator.
- Hard eligibility violations: count any returned review-only, wrong-slot, unsupported-demographic, or otherwise disallowed candidate. Proposed acceptance for this small safety set is zero; the owner must approve the gate.
- No-result behavior: report expected-no-match queries that return results, and positive queries that return empty, separately. A vector store returning a nearest neighbor does not prove a relevant match.
- Metadata safety: verify missing material is not asserted as known and seed price/status are not presented as current. A separate final hydration policy is needed before live use.
- Failure behavior: record embedding provider errors, Qdrant errors/timeouts, malformed responses and any retry count separately. Proposed policy for the benchmark is no automatic billable retry; report failures instead of silently falling back.
- Latency: capture embedding, Qdrant and total elapsed time, with p50/p95 only after enough repeated runs. Mark cold/warm conditions and region/network. This small set is not enough for an SLA claim.
- Cost: record actual provider-reported usage for each request and the price schedule verified at run time. Do not present vector score as a probability, confidence or fashion score. Human reviewers should record why each top result is or is not suitable.

## Provider and model gate

The current planning documents mention text-embedding-3-small as a PROPOSED candidate. They do not approve it, prove that the project account can use it, or establish current region availability, dimensions, privacy terms, retention or cost. The proposed GPT generation models in the technical design are not embedding candidates and should not be conflated with this test.

Immediately before an authorized run, verify from current official provider documentation and the project account:

- Exact model identifier/version, supported input size, returned dimension and whether a requested dimension is supported. The fake POC uses 128 dimensions; do not truncate/pad a real vector. Create a separately versioned collection if the real model dimension differs.
- SDK/API compatibility and response usage fields; embedding normalization and Qdrant distance must be explicitly compatible.
- Data region, processing/retention/logging/training terms, deletion behavior, account permissions, rate limits and whether the approved product text may leave StyleMind.
- Current billing unit and rate for the specific account/region/tier. Do not use a stale estimate as actual cost.

Before any external request, the owner must approve the exact fields sent. The proposed minimal text-only allowlist is product name, reviewed short description, color, size, material only when present, and synthetic reviewer-written query text. Exclude product/variant IDs, image URLs, customer/profile/wardrobe data and secrets unless a later explicit decision says otherwise.

Set an owner-approved hard budget cap and maximum input/query counts before configuring credentials. Estimate a maximum from the frozen corpus/query set and the currently verified billing schedule, then enforce the lower of the approved dollar cap and approved request/token cap. Disable automatic retries and model fallback. Stop before the first paid call if usage reporting, cap enforcement, current pricing evidence or request-count limits are missing. No numeric budget is approved in this proposal.

## Live Commerce verification gate

Before using this evaluation to claim anything about current catalog data, use an owner-approved Commerce API contract and a disposable local/test environment. Resolve the reviewed product/variant IDs through Commerce’s API (through the approved service route), compare returned category, product name, description, demographic, variant color/size/material, current unit price and status, and record the response timestamp/revision. Do not query Commerce’s database directly from Stylist. Treat missing, inactive, mismatched or incomplete API records as ineligible/review-required; do not substitute the seed snapshot.

Commerce source drift remains open in the planning guard: eight additional committed paths and fingerprints for ProductController, VariantResponse and ProductVariantRepository differ from the recorded review baseline. Re-review that source and approve the current DTO/filter contract before relying on it. available_quantity remains outside this Stylist benchmark because inventory/stock is not in the approved scope. Images remain out of scope; image hosting/rights/access and any image-processing decision are separate.

## Decisions required before execution

1. Owner approval of external provider, exact model/version, allowed text fields, data region/retention, and where credentials are stored.
2. Owner approval of the hard spend/request cap and evidence required to stop or continue.
3. Commerce/business reviewer approval of fixture category mapping and live API fields/current-price/status contract.
4. Owner decision for UNISEX and unknown/null demographic handling. The current UNISEX item remains a negative/review case.
5. Owner decision for missing material and any other unknown required attribute. No inferred values are allowed.
6. Reviewed query-to-candidate labels and a frozen split before any model tuning.
7. Separate approval for a live Qdrant endpoint, retention, access and cleanup policy. The disposable P04.1 test collection is not that deployment.

Until these decisions are recorded, this remains a proposal. Do not call an external embedding API, wire a provider, update the P04 approval, or mark R98/R99/P04 complete.
