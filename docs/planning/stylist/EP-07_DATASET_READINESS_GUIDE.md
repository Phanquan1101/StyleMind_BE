# EP-07 — Dataset readiness guide for team members

**Status: planning guide; proposed metadata is not approved Commerce schema.** The sample rows in the linked CSVs are synthetic test examples only. They are not current catalog records, production data, owner-approved taxonomy, fashion ground truth, or permission to upload/use an image. This guide does not request a database or Commerce change.

## 1. What Commerce has today

The following are observed in the current Commerce response DTOs and controller routes:

| Data | Existing Commerce field/API | Owner and current limit |
|---|---|---|
| Category | `GET /api/v1/categories`; `id`, `name`, `slug`, `is_active` | Commerce owns IDs and active state. No Stylist slot exists. |
| Brand | `GET /api/v1/brands`; `id`, `name`, `slug`, `is_active` | Commerce owns IDs and active state. Identity profile brand IDs are logical refs and may be stale. |
| Product | `product_id`, `category_id`, `brand_id`, `category_name`, `brand_name`, `name`, `description`, `demographic_segment`, `product_status` from product APIs | `demographic_segment` is currently syntax-checked text, not a fixed women's-only enum. The real allowed values and meanings need Commerce confirmation. |
| Variant | `variant_id`, `product_id`, `sku`, `size`, `color`, `material`, `unit_price_vnd`, `variant_status` | Commerce owns current price/status. `unit_price_vnd` is whole VND. No stock quantity is provided. |
| Variant image | `image_id`, `variant_id`, `image_url`, `alt_text`, `display_order`, `is_primary` | The API stores a URL/path and display metadata, not usage rights or Stylist consent. |

Commerce creates product and variant IDs when records are created. Do not mint IDs in Stylist datasets or spreadsheets and treat them as live. In a pre-import CSV, leave `commerce_*_id` fields empty; after Commerce import, fill them from Commerce's response/export. SKU is a useful human cross-reference, not an authorization or database key.

Product descriptions, colors and image alt text are untrusted catalog data. They can contain mistakes or instruction-like text; retrieval/model code must treat them as data, never as system instructions.

## 2. Existing fields versus suggested metadata

| Field | Exists in current Commerce API? | Proposed location/use only |
|---|---:|---|
| Category ID/name/slug/active | Yes | Category-to-Stylist-slot mapping CSV, reviewed by a named person |
| Brand ID/name/active | Yes | Optional reviewer normalization for brand preference mapping |
| Product demographic code | Yes, as a string | Commerce owner must publish the allowed vocabulary and confirm which value means women's apparel |
| `TOP`/`BOTTOM` Stylist slot | **No** | Proposed category mapping artifact; only approved rows may enter live retrieval |
| Product/variant current price and status | Yes | Preserve source observation time/revision for analysis; final eligibility/price checks must call Commerce |
| Color/material/size/SKU | Yes | Human normalization may be proposed separately; never overwrite raw Commerce fields in a projection without provenance |
| Reviewed style/compatibility codes | **No** | Optional Stylist-evaluation metadata, with reviewer, codebook version and evidence |
| Image rights holder, license, consent, AI-use scope, evidence | **No** | Must be supplied/reviewed before using the image in indexing or sending it to a provider |
| Complete snapshot revision/change version | **No** | Producer contract needed before claiming complete catalog retrieval or durable event updates |

Do not infer `TOP` or `BOTTOM` with an AI model from category names. An unknown, pending, rejected, inactive or unmapped category must stay out of live Stylist candidate retrieval until a reviewer and Commerce owner confirm how it maps. Prompt 08 scope remains one women's TOP plus one women's BOTTOM per outfit; every other slot is out of scope.

## 3. Catalog CSV starter

Start from [`catalog_synthetic_template.csv`](templates/catalog_synthetic_template.csv). It includes the raw Commerce fields and proposed rights/provenance fields. The sample product and SKU labels are synthetic; the Commerce ID columns are intentionally blank until Commerce creates real records. Replace sample rows only in a separately reviewed working copy, never by silently re-labeling these rows as production.

Recommended preparation order:

1. Commerce owner exports/creates category and brand records and provides the exact returned IDs and active states.
2. Commerce owner provides product and variant fields plus their returned IDs and the allowed `demographic_segment` codes.
3. A named Stylist/business reviewer approves category mapping in the separate mapping file.
4. A data owner records source revision and capture time. Price in this export is only a snapshot, not a quote for a future request.
5. Images are included only after the rights/access fields in §6 are reviewed. Leave image IDs/URLs empty when no approved asset exists.

## 4. Category mapping review

Use [`category_mapping_synthetic_template.csv`](templates/category_mapping_synthetic_template.csv) as a review queue. Required review record: actual Commerce `category_id`, exact current category name, proposed slot `TOP` or `BOTTOM`, reviewer, review time, mapping version, approval state and evidence/notes.

The sample mapping rows say `PENDING` and are synthetic. A row is live-eligible only when the Commerce ID/name match current Commerce data and an authorized business reviewer marks the mapping `APPROVED`. `PENDING`, `REJECTED`, missing, conflicting or unknown rows map to no Stylist slot and are excluded. Do not train a text classifier to guess a missing mapping.

If categories are too broad (for example a category contains both tops and bottoms), split/reconcile taxonomy with Commerce instead of assigning one misleading slot. Do not add an extra slot to solve it.

## 5. Human-reviewed evaluation cases

Use [`evaluation_synthetic_template.csv`](templates/evaluation_synthetic_template.csv) to begin an offline evaluation set. Keep a versioned corpus and include:

- User need/context, hard and soft constraints, exact VND budget and whether it is hard or soft.
- Expected complete TOP+BOTTOM pair or a documented no-result outcome.
- Whether each candidate pair is suitable/unsuitable and a short human rationale.
- No-result, inactive product/variant, changed price, hard-budget overage, unknown category mapping and missing/contradictory metadata cases.
- Reviewer identity, review result, review time and disagreements.

The example rows cover synthetic success and fault cases; they are not accepted fashion judgments. Use at least two human reviewers for quality comparisons when the evaluation plan is approved; preserve disagreement rather than averaging away a policy question. Keep hard-validity checks separate from subjective style-quality ratings. A JSON-valid answer is not fashion ground truth.

## 6. Image storage and usage rights

Observed Commerce image flow:

- Admin uploads through `POST /api/v1/admin/variants/{variantId}/images` as multipart `file`, optionally `alt_text`. Accepted image types are JPEG, PNG and WEBP; current validator caps a file at 5 MiB.
- `VariantImageStorageService` writes bytes under `stylemind.commerce.image-storage.local-directory`, default `target/variant-images`, and returns a relative path such as `/api/v1/variant-images/{imageId}/content`.
- `GET /api/v1/variant-images/{imageId}/content` serves the stored resource. The inspected Commerce security configuration requires authentication for that route.
- The Commerce Dockerfile copies the application JAR into `/app`; Compose does not mount a persistent volume for `target/variant-images`. In the container, the default path is under the writable `/app` working directory. Files therefore live in that container's filesystem layer and are not shared with other services; replacing/recreating a container can lose them. A local JVM run writes to its configured working directory. This is not shared or cloud storage.

Before Stylist indexes or sends an image, the owning team must decide: durable storage and backup; public/private access and signed-URL lifetime; a service-read mechanism for protected files; image MIME/size/scan rules; rights holder and allowed AI-processing scope; delete/replace/cache behavior; URL stability; and who audits consent. A relative protected Commerce URL is not directly usable by an external provider. Do not fetch arbitrary user/catalog URLs.

Supabase Storage and Cloudinary are options to evaluate only; no provider is selected, configured, registered, or approved here. A provider decision needs cost, data region, retention/deletion, access-control, signed URL, backup, and rights review.

## 7. What data is needed at each phase

| Phase | Minimum useful data | Evidence level and gate |
|---|---|---|
| P03-B request intake | Synthetic request/message/context/budget/constraint inputs; synthetic owner A/B fixtures | Enough for validation, persistence and isolation tests. No live catalog/profile/owned data is needed to store a request. |
| P04 catalog retrieval | Reviewed Commerce category/brand/product/variant sample, approved TOP/BOTTOM mapping, image access/rights if image retrieval is used | Synthetic corpus is enough to build algorithms/tests. Real catalog quality needs reviewer-approved data; live completeness still needs a producer snapshot/version contract. |
| P05 reviewed knowledge | Curated, licensed/approved styling rules with source, reviewer and version; adversarial/unapproved examples | Synthetic rules test the mechanics only. Do not index unreviewed web pages or customer content as approved knowledge. |
| P06 provider adapter | Synthetic prompts/candidates, output/fault cases, privacy-reviewed fields and a selected provider/model budget | Stub tests require no paid API. A real benchmark needs separate approval and measured caps; this project has no decision that it must train a model. |
| P07 recommendation persistence | Complete synthetic TOP+BOTTOM pairs first; then approved mapping, live current Commerce price/status checks, and producer-confirmed owned references for mixed-source cases | Synthetic fixtures prove rules only. Shop-only partial integration does not satisfy the owned-source target or prove live price/permission. |
| P09 live integration | Actual Identity-issued JWT/profile where approved, current Commerce calls, Wardrobe owned-context producer, approved image rights/storage and isolated test data | Must distinguish live service evidence from stubs and local fixtures. No cross-service database access. |
| P10 acceptance | Versioned holdout cases, two-person fashion review, hard-rule/failure metrics, Postman/Newman and Gateway results | Report structural, automated, disposable DB, stub, live and quality evidence separately. No synthetic result is production readiness. |

Never infer that the project needs to fine-tune/train a model. Model training is not approved or required by the current EP-07 contract. The first decision is whether reviewed catalog/knowledge plus deterministic rules and one approved provider meet the measured need.

## 8. Files and ownership reminder

This guide and templates are planning artifacts under `docs/planning/stylist/`. They do not change Commerce DTOs, Wardrobe ownership, live taxonomy, storage providers, source schemas or the EP-07 execution manifest. All example rows carry `SYNTHETIC_TEST_ONLY`; no real customer data or secrets belong in them.