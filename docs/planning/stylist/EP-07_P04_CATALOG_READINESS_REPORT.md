# EP-07 / P04.0 — Commerce Catalog Readiness Audit

**State:** analysis and documentation only. P04.1 has not been implemented or approved by this report. No application code, API contract, database, migration, seed, Compose, FE, or workbook was changed.

## 1. Review boundary and evidence

- Branch: feature/ep-07-ai-stylist
- HEAD: c9f20d1d1fc1779eca735b3de3ac9f1745b43985 (2026-11-01 14:50:23 +07:00)
- The working tree already contained staged, unstaged, and untracked changes across governance, docs, Compose, Stylist, tests, scripts, and Postman. Those changes were preserved. This audit adds only the two files listed in §10.
- Backlog workbook read-only: C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx, sheet Backlog, rows R93–R102. Read window: 2026-11-02 00:39:20.124 +07:00 to 00:39:20.344 +07:00. SHA-256: 1F9657C9E6DF2992572E86338CB4C985C21451ED4FCCA08D338FB00C6B7CDE73. All ten backlog rows remain To Do; workbook was not changed.
- EP-07 rules read: repo and Stylist instructions; current governance; implementation plan, manifest, technical design, flows, dependency contracts, verification matrix, dataset guide, P02/P03-A reports, P03-B proposal, and templates. P08 limits the engine to one TOP plus one BOTTOM; all other slots are out of scope. This does not approve a Commerce category mapping or Unisex policy.
- No persistent database was read. No Compose service was started or restarted. No token, secret, or customer data was read or printed.

### Backlog items in scope

| Row | Backlog requirement | Source status |
|---|---|---|
| R93 | Apply Stylist session/message/request, constraint and request-item migrations | To Do |
| R94 | Create styling request and conversation-history APIs with owner validation | To Do |
| R95 | Read preferences and permitted wardrobe items from their owning services | To Do |
| R96 | Test invalid context, conversation ownership, and unavailable context services | To Do |
| R97 | Apply recommendation/item migrations with owned or shop-variant provenance | To Do |
| R98 | Connect Qdrant/Neo4j retrieval to valid catalog and approved knowledge inputs | To Do |
| R99 | Update catalog projections from change events; handle duplicate/stale events | To Do |
| R100 | Generate outfits with reasons and styling tips using selected provider | To Do |
| R101 | Recheck shop variants, prices, and hard constraints before saving | To Do |
| R102 | Test fake variants, wrong prices, hard-budget violations, unavailable AI results | To Do |

The row text is backlog scope, not proof that Qdrant/Neo4j or a catalog event producer already exists in this checkout.

## 2. Commerce source inventory

Gateway routes use StripPrefix=1; for example, /commerce/api/v1/products is forwarded to Commerce as /api/v1/products (api-gateway/src/main/resources/application.properties, route 2). Commerce authorization is determined by SecurityConfiguration, not Swagger annotations.

| API | Gateway path / access | Input and behavior confirmed from source |
|---|---|---|
| Product list | GET /commerce/api/v1/products; public | Query whitelist: keyword, category_id, min_price_vnd, max_price_vnd, demographic_segment, page, size. Defaults are page 0 / size 20; size 1–100. Unknown/repeated or malformed parameters are rejected. Returns items, page, size, total_elements, total_pages. |
| Product detail | GET /commerce/api/v1/products/{productId}; public | Returns only an active product with active category and brand and at least one active variant. |
| Categories | GET /commerce/api/v1/categories and /{categoryId}; public | List and detail return active category references. Response fields are id, name, slug, is_active. Admin routes can inspect inactive references but require ROLE_ADMIN. |
| Brands | GET /commerce/api/v1/brands and /{brandId}; public | Same active-only behavior/response shape as categories. Admin routes can inspect inactive references but require ROLE_ADMIN. |
| Public variant recheck | POST /commerce/api/v1/variants/batch-lookup; public | Accepts 1–100 distinct variant UUIDs. Returns the current active variant details for variants whose product/category/brand are also active; response includes current price, attributes, images, and available_quantity. |
| Internal variant lookup | POST /commerce/internal/v1/variants/batch-lookup; service token required | Accepts 1–100 UUIDs. Commerce requires X-StyleMind-Service-Token in InternalServiceTokenFilter. Response is intentionally narrower; see below. This is not an anonymous public API. |
| Legacy image content | GET /commerce/api/v1/variant-images/{imageId}/content; public by SecurityConfiguration | Described by controller as legacy local image content. Current product DTOs expose image URLs directly; runtime URL availability was not checked. |
| Admin catalog writes | /commerce/api/v1/admin/**; ROLE_ADMIN | Product/variant/category/brand/image create/update/delete paths are outside this read-only audit. No write request was made. |

Source references:
- Product routes and controller methods: commerce-service/src/main/java/com/stylemind/commerce/controller/product/ProductController.java (lines 48–69, 81–159).
- Category/brand routes: commerce-service/src/main/java/com/stylemind/commerce/controller/catalog/CategoryController.java (lines 38–72) and BrandController.java (lines 37–71).
- Internal route: commerce-service/src/main/java/com/stylemind/commerce/controller/internal/InternalVariantController.java (lines 14–27); token header/filter: commerce-service/src/main/java/com/stylemind/commerce/security/InternalServiceTokenFilter.java.
- Actual allow/deny rules: commerce-service/src/main/java/com/stylemind/commerce/security/SecurityConfiguration.java (lines 32–57).
- Gateway prefix: api-gateway/src/main/resources/application.properties (lines 31–35).
- Query and batch validation: commerce-service/src/main/java/com/stylemind/commerce/validation/product/ProductRequestValidator.java (lines 33–105), and validation/internal/InternalLookupRequestValidator.java (lines 14–38).
- Response, filtering, image and schema sources: commerce-service/src/main/java/com/stylemind/commerce/dto/product/ProductResponse.java, VariantResponse.java, VariantImageResponse.java, dto/catalog/CatalogReferenceResponse.java, service/product/impl/ProductServiceImpl.java (lines 83–103, 151–163, 244–311), repository/product/ProductRepository.java (lines 15–43), repository/product/ProductVariantRepository.java (lines 16–32), service/product/impl/VariantImageStorageServiceImpl.java (lines 21–68), and db/migration/V1__create_catalog_category_brand_tables.sql, V2__create_product_variant_and_image_tables.sql, V9__create_shared_image_and_inventory_tables.sql.

### Response shape and lifecycle

Public ProductResponse contains product/category/brand IDs and names, product name, nullable description and demographic segment, product status, and nested variants. Product variants contain variant/product IDs, SKU, nullable size/color/material, non-null nonnegative unit_price_vnd, variant status, available_quantity, and image list. Each image has image/variant IDs, image URL, nullable alt text, display order, and primary flag. The schema makes product name, IDs, status, SKU, price, and image URL non-null; description, demographic segment, size, color, material, and image alt text nullable. Product and variant status are constrained to ACTIVE/INACTIVE in Commerce V1/V2.

Public reads omit inactive products and variants; product list also filters inactive categories and brands. Public category/brand endpoints omit inactive reference rows. Admin detail/list can include inactive data but are not available without an authorized admin credential. Commerce soft-disables product, variant, category, and brand records; image deletion is a separate operation.

ProductRepository.searchPublic now checks that a single ACTIVE variant satisfies both price bounds, and requires active category and brand. This differs from the reviewed baseline, which used separate variant existence checks for min and max price. Public list sorting is name then product UUID. The page API provides counts but no snapshot watermark or source revision; pagination across concurrent edits cannot be represented as a consistent export.

The two batch lookup contracts differ:

- Public batch lookup returns full VariantResponse-shaped data and filters out invalid/inactive catalog context. It includes available_quantity, but this audit excludes that field from Stylist eligibility rules.
- Internal lookup returns variant/product IDs, product name, product/variant status, color, size, price, primary image URL, and a purchasable boolean. Its SQL defines purchasable as variant ACTIVE + product ACTIVE + category active + brand active. It does not include category/brand IDs or names, demographic segment, material, all images, a revision, or inventory quantity. Its purchasable result does not encode available quantity.

Image storage changed: Commerce V9 adds image_asset(image_url, created_at) and makes variant images reference the asset; the current upload service uses Cloudinary. Public Product DTOs expose the stored URL and image metadata. V9 also adds inventory tables and the public DTO adds available_quantity; neither creates a catalog revision or a Stylist stock policy. Inventory, reservation, and quantity behavior remain out of AI scope. The source does not establish that Stylist must exclude a variant at zero available quantity; that specific eligibility contract needs Commerce/owner confirmation.

### Commerce changes since the planning review

Both planning guards still flag source drift. The differences were inspected; hashes/status were not refreshed:

- ProductController adds Swagger @SecurityRequirements annotations for public routes and updates the image-upload description to Cloudinary. The effective public access is confirmed separately in SecurityConfiguration.
- SecurityConfiguration changes product/category/brand GET, legacy image GET, and public variant batch POST from authenticated to permitAll; branch reads remain authenticated.
- VariantResponse adds available_quantity.
- ProductVariantRepository reads image URL by joining image_asset; internal purchasable uses active status of variant/product/category/brand only.
- ProductRepository changes active-reference filtering and min/max semantics as described above.
- Commerce V9 introduces image asset and inventory schema. Those are source changes, not evidence V9 was applied to the persistent local database.
- The broader committed drift also includes Commerce POM, Postman files, application bootstrap, catalog seed runner/resource, and Commerce service/repository changes. Most are outside the Stylist read contract, but their presence explains why the guard requires a wider re-review. No Commerce code was edited in this task.

## 3. Runtime catalog survey

**NOT EXECUTED / BLOCKED — no HTTP status was received.** At 2026-11-02 00:40:41.161 +07:00 to 00:40:45.218 +07:00, read-only GET requests to:

- http://localhost:8080/actuator/health
- http://localhost:8080/commerce/api/v1/products?page=0&size=100

both timed out after a two-second client timeout. Category/brand requests and image URL checks were not attempted after the Gateway/catalog probe failed. No product or variant record was read: 0 pages succeeded; catalog total is **unknown**, not zero. No runtime Unisex count is available. The FE report of 600 products and Unisex products remains unverified in this environment.

The repository contains commerce-service/src/main/resources/db/seed/development-catalog.json and a conditional DevelopmentCatalogSeedRunner. The runner executes only when STYLEMIND_CATALOG_SEED_ENABLED=true; application.properties and Compose default this flag to false. Its code checks that a seed manifest has 600 products and 2,400 variants, but that is a seed precondition—not an observation of the running database or proof those records are loaded locally. This report does not count the seed file or claim those records are loaded locally.

### Follow-up environment check — 2026-11-02

At 00:50:24.881 +07:00, docker compose ls showed no active Compose project. Read-only container inventory identified the existing containers as Compose project stylemind, working directory C:\Users\FPTSHOP\Desktop\StyleMind_BE, using docker-compose.yml. Before the API probe, Gateway was Exited (137), Commerce Exited (143), Identity/Payment/Wardrobe/Notification/Stylist Exited (143), and Postgres/Mailpit Exited (0). The Postgres container label also names the pre-existing IntelliJ override file. No container was running.

I inspected only the non-secret STYLEMIND_CATALOG_SEED_ENABLED setting stored in the existing Commerce container configuration; it is true. I did not read .env or expose any secret. Starting this exact container enables DevelopmentCatalogSeedRunner; if application startup reaches its ApplicationRunner, it performs transactional upserts that set categories/brands active, overwrite product and variant fields/status, update image assets/links, and insert missing inventory rows. That would write to the existing catalog, which this follow-up expressly may not do. In addition, Commerce includes Flyway dependencies and does not explicitly disable Flyway in application.properties; the persistent database migration history is unknown and direct DB inspection is forbidden. Starting Commerce therefore cannot be shown safe.

I did not start Commerce, Postgres, Gateway, or any other container. Starting Gateway alone would not provide the stopped Commerce API. No Compose command that could recreate containers, alter the project, or create a volume was run.

At 00:50:43.972 +07:00 to 00:50:48.093 +07:00, read-only GET probes to Gateway health and the first product page both returned curl exit 28, HTTP status 000, connection timed out at two seconds. After the probe, every StyleMind container remained stopped with the same exit status; docker compose ls still showed no active project. Category/brand GETs and image checks were not attempted because the Gateway was unavailable. No product or variant was read: zero pages succeeded; total catalog size and Unisex count remain UNKNOWN, not zero. The FE report of 600 products remains unverified.

No persistent database was read, no seed ran, no container was started/restarted, and no volume/image/network/container was removed or changed.

## 4. Category-to-slot review and Unisex

No live categories were returned, so no category IDs/names/slugs/counts can be honestly entered. The accompanying [category review CSV](EP-07_P04_CATEGORY_REVIEW.csv) contains one clearly marked NOT_OBSERVED sentinel row, not a fabricated category.

No source field or approved mapping assigns Commerce categories to Stylist slots. Category names alone can be ambiguous. No category can be treated as TOP or BOTTOM until a reviewer checks the actual category definition and confirms its products are homogeneous enough for that slot. Any unknown or mixed category remains excluded from live retrieval pending review. No extra slot is proposed.

The FE report says Unisex exists, but current runtime is unavailable and source does not provide a controlled demographic enum. Commerce stores nullable free-form text; request validation uppercases and syntax-checks it but does not restrict the value set. Therefore Unisex presence/count is NOT OBSERVED here. The binding handling requirement is NEEDS_REVIEW: do not silently map it to WOMEN, include it live, or rewrite the source.

Options for owner/business review only; none is approved:

| Option | Candidate rule | Likely catalog/quality impact |
|---|---|---|
| A | Accept only WOMEN | Simple and conservative if the code has a stable meaning; excludes Unisex and may reduce candidate coverage. Must still decide null/unknown values. |
| B | Accept WOMEN and UNISEX after Commerce confirms the Unisex definition is appropriate for this women’s shop and the owner approves it | Broader candidate coverage, but can reduce fit/style precision if the label spans items unsuitable for the intended wearer. Requires an explicit code and validation contract. |
| C | Review Unisex by category or product and include only individually approved records | Most precise and keeps uncertain items out, but requires ongoing manual curation and a durable review source. |

Null/unknown demographic needs a separate decision; until decided, the conservative proposal is to keep those records out of live candidates. This remains a proposal, not a rule. Category-to-slot mapping and demographic eligibility are independent: a category mapped to TOP/BOTTOM does not approve every product’s demographic. Product/variant ACTIVE status is a separate current-Commerce check.

## 5. Proposed P04 catalog projection (design only)

No field is added to a Commerce or Stylist DTO, schema, or database in P04.0.

| Projection field | Current source / type / nullability | Purpose and classification | Missing/stale handling proposal | Embedding permission |
|---|---|---|---|---|
| product_id | Commerce ProductResponse; UUID, required | Commerce reference; Current | Reject record if absent/invalid | TBD |
| variant_id | Commerce VariantResponse; UUID, required | Retrieval point/reference; Current | Reject record if absent/invalid | TBD |
| category_id | ProductResponse; UUID, required | Filter/reference; Current | Reject if absent; keep out until mapping approved | TBD |
| proposed_slot | Not in Commerce; human-reviewed category mapping | TOP/BOTTOM filter; Proposed, not approved | Unmapped/mixed category excluded | TBD |
| demographic_segment | ProductResponse; nullable string | Eligibility metadata; Current raw value | Preserve null/unknown; apply only after owner-approved policy | TBD |
| product_status | ProductResponse; required string | Eligibility metadata; Current raw Commerce status | Only current ACTIVE products eligible; recheck at Commerce | TBD |
| variant_status | VariantResponse; required string | Eligibility metadata; Current raw Commerce status | Only current ACTIVE variants eligible; recheck at Commerce | TBD |
| product_name | ProductResponse; required string | Display and retrieval text; Current | Reject if source violates required field | TBD |
| category_name / slug | Category API; name/slug required strings | Display and retrieval text; Current | No label-based slot inference; exclude unmapped category | TBD |
| brand_name / slug | Brand API; name/slug required strings | Display/retrieval text; Current | Omit optional retrieval contribution only if contract permits; preserve ID | TBD |
| description | ProductResponse; nullable string | Retrieval text; Current raw field | Null means omit from derived text; never invent | TBD |
| color | VariantResponse; nullable string | Variant attribute/retrieval text; Current | Null stays unknown; omit from derived text | TBD |
| size | VariantResponse; nullable string | Variant attribute/filter; Current | Null stays unknown; do not infer fit/size | TBD |
| material | VariantResponse; nullable string | Variant attribute/retrieval text; Current | Null stays unknown; omit from derived text | TBD |
| unit_price_vnd | VariantResponse; long, required, nonnegative by schema | Observed price metadata/filter; Current snapshot only | Reject invalid value; re-read Commerce before any save/claim | TBD |
| image metadata | VariantResponse.images list; image UUID/variant UUID/URL required per image, alt nullable, display order int, primary bool | Display/reference only; Current | Empty list is allowed; no image download or image embedding in P04.1 proposal | TBD |
| available_quantity | VariantResponse; int | Observed Commerce field, expressly out of Stylist scope | Do not use for Stylist eligibility, scoring, or stock/reservation behavior without a separate decision | Not sent |
| search_text | Derived from approved name/brand/category/description and allowed attributes | Candidate retrieval text; Proposed | Omit null fields; rebuild from current Commerce values | TBD |
| observed_at | Not returned by Commerce; local projection observation time | Freshness diagnostics; Proposed client timestamp, not Commerce update time | Record ingestion time only; it cannot prove source data age | Not sent |
| source_revision | No revision/version in these Commerce read DTOs/schema | Stale-event protection; TBD | Cannot safely order updates or deletes until Commerce supplies a version contract | Not sent |

A retrieval result is only a candidate reference. It is not authoritative for current product status, eligibility, or price. R101 requires a fresh Commerce check before recommendation persistence. The inspected public batch lookup can return current active variants/prices for at most 100 IDs; the internal lookup lacks several fields needed to explain a candidate. No cross-service database access is allowed.

## 6. P04.1 proposal — small, isolated candidate retrieval

**PROPOSED — NOT APPROVED; no implementation in this audit.**

No actual product/variant IDs can be supplied for a P04.1 sample: the Gateway and product endpoint were unavailable, and the running database cannot be inspected directly. Do not substitute IDs from a static seed file or create synthetic IDs and describe them as live catalog data. The CSV remains NOT_OBSERVED. A real small sample must be read through Commerce after the environment can be made safe to start and the category/demographic review is completed. The separate [offline source-seed review](EP-07_P04_SEED_OFFLINE_REVIEW.md) and its sample are clearly labelled SOURCE_SEED; they do not change the live catalog state above.

1. First obtain a real read-only Commerce sample through its APIs and human-reviewed category mapping. Include only reviewed TOP/BOTTOM categories and demographics expressly allowed by the owner. Unisex, null demographic, and unmapped category remain out of the live set until decided.
2. Build one disposable, project-specific Qdrant collection from a small, traceable set of product variants. Keep Commerce IDs/status/price/category/slot as payload metadata; derive text from only approved catalog fields. Do not share the team's collection or volume.
3. Keep retrieval bounded and slot-filtered. Return variant IDs and explanation metadata only; do not produce an outfit, call an LLM, save a recommendation, or claim fashion quality. Do not connect to Commerce DB, upload images, use image embeddings, or add stock/reservation rules.
4. Tests should separate synthetic unit fixtures from a disposable Qdrant integration. Cover TOP/BOTTOM filtering, unreviewed/Unisex exclusion, empty results, malformed/duplicate catalog records, unavailable Qdrant, and candidate ID validation. A mock/deterministic embedder can verify plumbing only; it cannot prove semantic retrieval quality.
5. A real embedding run requires a separate approval for provider/model, allowed fields sent outside the service, data rights, dimension/normalization/index version, budget, and secret/config handling. The technical-design document lists text-embedding-3-small as a candidate only; no provider call or benchmark occurred. Do not call it optimal.
6. The checked-out Stylist POM, source, and Compose do not currently show Qdrant/Neo4j clients or services. Workbook R98 wording about “existing” retrieval is not confirmed by this checkout. Neo4j/knowledge input should remain a separate gate until the knowledge source, review process, and contract are approved.
7. This small retrieval slice does not complete R98, R99, R101, R102, P04, US-07.02, or EP-07. R99 needs a later Commerce-owned change-event contract and Stylist projection consumer. At minimum that contract needs aggregate identity/version, event identity/schema, ordering/idempotency/stale handling, soft-delete/tombstone semantics, durable delivery, and a snapshot watermark. A one-time export or page polling is not event synchronization.

## 7. Relationship to P03-B and Wardrobe

P03-A is session/history only. P03-B remains a proposal, not an approved API contract. If separately approved, request-intake work can proceed alongside category review and catalog projection preparation because it does not require embeddings. It must not claim successful generation or accept unverified owned context.

The inspected Wardrobe V1 and source store saved outfit items by variant_id; creation_source says MANUAL/AI. They do not provide a trusted OWNED/SAVED discriminator or wardrobe_item_id ownership resolver. R95 owned/permitted-item context therefore needs a Wardrobe-owned contract. A saved outfit or an AI label is not proof of ownership, and only a trusted confirmed-owned item may be a zero-cost anchor under the approved business rule.

See [P03-B contract proposal](EP-07_P03B_CONTRACT_PROPOSAL.md), [P02 report](EP-07_P02_IMPLEMENTATION_REPORT.md), and [P03-A report](EP-07_P03A_SESSION_HISTORY_REPORT.md).

## 8. Decisions needed before live P04.1

1. Commerce/business reviewer supplies category IDs and signs off which homogeneous categories map to TOP or BOTTOM.
2. Owner chooses among Unisex options A/B/C and resolves null/unknown demographic handling; Commerce documents the accepted demographic code set.
3. Owner/Commerce decides whether available_quantity has any role in candidate eligibility. Current Stylist scope does not include inventory, stock, or reservation; no rule is inferred here.
4. Owner approves which Commerce text fields may be sent to an external embedding provider, and selects provider/model, dimension, normalization, budget, secret handling, and retention.
5. Commerce clarifies image URL access and data rights. P04.1 proposal excludes image processing.
6. Commerce supplies a source revision/event and snapshot contract before claiming ongoing synchronization (R99).
7. Wardrobe owner defines the trusted owned-item producer contract before R95 owned context or zero-cost anchors are implemented.
8. Team confirms whether Qdrant/Neo4j are available outside this checkout and who owns their disposable environments. No service/dependency was added.

## 9. Verification performed

- Source and documentation review only; no application tests were run because this was a documentation-only audit.
- Runtime GET probes: NOT EXECUTED successfully; the initial requests timed out, then follow-up requests returned curl exit 28 / HTTP 000 after timeout. Categories, brands, and image URL were not separately probed.
- Container inventory was read before/after. No StyleMind container was started because the stored Commerce seed flag is true and Flyway migration state is unknown.
- Both planning commands were run and exit 1:
  - node scripts/verify-stylist-planning.cjs
  - node scripts/verify-stylist-planning.cjs --phase P04
- Guard output lists committed-source drift in commerce-service/pom.xml; commerce-service/postman/US-03.01_RUN_GUIDE.md; commerce-service/postman/US-03.01_categories_brands.postman_collection.json; commerce-service/postman/US-03.02-03.04_RUN_GUIDE.md; commerce-service/postman/US-03.02-03.04_products_variants_images.postman_collection.json; commerce-service/postman/US-03.05-03.10_RUN_GUIDE.md; commerce-service/postman/US-03.05-03.10_branch_cart_order.postman_collection.json; and commerce-service/src/main/java/com/stylemind/commerce/CommerceServiceApplication.java. It separately reports source fingerprint drift in commerce-service/src/main/java/com/stylemind/commerce/controller/product/ProductController.java, dto/product/VariantResponse.java, and repository/product/ProductVariantRepository.java. The catalog changes listed in §2 were inspected. No fingerprints, manifest status, or reviewed head were changed. Guard failure remains.
- No PASS is claimed for runtime catalog data, category mapping, Unisex counts, paid embeddings, Qdrant/Neo4j, Commerce live integration, recommendation quality, or R98/R99.
- git diff --check and new-file/CSV validation are reported after final artifact validation below.

## 10. Files updated

- docs/planning/stylist/EP-07_P04_CATALOG_READINESS_REPORT.md
- docs/planning/stylist/EP-07_P04_CATEGORY_REVIEW.csv

The category CSV retains a NOT_OBSERVED sentinel row because no category was returned by the live API. The optional planning README was not edited, and the pre-existing worktree changes were left intact.
