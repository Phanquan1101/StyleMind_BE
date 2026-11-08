# EP-07 — Dependency contracts

**Historical dependency proposal:** Owner Prompt 08 (2026-10-29) supersedes all slot examples and P03-A approval gates below. Current Stylist product scope is exactly one women’s TOP plus one BOTTOM per outfit. P03-A session/history only is approved; live category mapping and owned-item producer contracts remain open.


Revision: 2026-10-27, Asia/Bangkok. Branch feature/ep-07-ai-stylist; source HEAD e6cbaee0ab289e32b1e1f564db9f20a425a5e607 sau pull origin/dev a2a650747246d931ff9fa058a0d3027b263f3d15.

Đọc [implementation plan](EP-07_IMPLEMENTATION_PLAN.md) và [Phase A memo](EP-07_PHASE_A_CONTRACT_MEMO.md). **Producer approval không được suy từ consumer DTO dưới đây.** Không endpoint downstream mới nào được tự tạo trong lượt này.

## 1. Provider inventory

| Dependency / owner | Status từ bằng chứng repo | Cần cho task / giới hạn |
|---|---|---|
| Identity style profile, JWT/JWKS | OBSERVED implementation, existing owner-approved profile contract | R95; chưa chạy live consumer Stylist hoặc Identity trong lượt này |
| Commerce product list/detail + variant batch + images | OBSERVED source/test/migrations V1–V8; đã mới hơn roadmap/governance cũ | R98/101; source hiện có không tự là dedicated snapshot/event contract đã duyệt |
| Commerce catalog snapshot/change events | TBD producer endpoint/schema/version/delivery; không thấy publisher/consumer trong source/config khảo sát | R99 và bootstrap/rebuild R98; không thay bằng DB read hoặc tự gọi catalog fixture là live |
| Wardrobe outfit/save/share | OBSERVED Outfit/OutfitItem/OutfitShare V1–V2 và HTTP APIs; target owned context chưa có | Existing shop save dùng được; R95 owned và requirement vẫn thiếu producer |
| Qdrant / Neo4j deployment | Không có trong repository Compose/POM/client; external deployment chưa có bằng chứng | R98/99; operator cung cấp address/server version/TLS/ACL/data ownership sau approval, không cung cấp secrets trong memo |
| Knowledge reviewer/ingestion | Hai target entities, không approved ingestion/review workflow hiện hành | R98; không tự thêm portal/crawler/reviewer role |
| AI/embedding provider | PROPOSED — NOT APPROVED, chưa call | R100/102; provider/model/cost/privacy tại Phase A gate |
| Gateway | OBSERVED app + prefix routes | R94 E2E tại09; routing không thay auth/owner checks |

Không kiểm tra infrastructure bên ngoài bằng suy đoán từ từ “existing”. Không đọc ignored .env để phán deployment thật.

## 2. Identity — reuse existing contract

Nguồn: [ProfileController](../../../identity-service/src/main/java/com/stylemind/identity/controller/ProfileController.java), [response DTO](../../../identity-service/src/main/java/com/stylemind/identity/dto/profile/StyleProfileResponse.java), [validator](../../../identity-service/src/main/java/com/stylemind/identity/validation/profile/ProfileRequestValidator.java), [security](../../../identity-service/src/main/java/com/stylemind/identity/security/SecurityConfiguration.java), [JWT decoder](../../../identity-service/src/main/java/com/stylemind/identity/security/jwt/JwtDecoderConfiguration.java). [Profile Phase A](../identity/US-02.05_MANAGE_PROFILE_PHASE_A.md) là analysis có proposal đã được supersede; owner-approved rules hiện hành nằm tại [open decisions](../../governance/OPEN_IMPLEMENTATION_DECISIONS.md).

### Existing API và payload

Service-local GET /api/v1/me/style-profile; Compose URL đề xuất cho consumer là http://identity-service:8081/api/v1/me/style-profile. Bearer phải là access JWT của caller; /me lấy account từ JWT sub, requires CUSTOMER hoặc ADMIN, self-only. Không dùng service email token thay bearer, không Admin Users lookup, không caller-chosen account ID.

Existing success200: style_profile:null nếu chưa có; GET không tạo rows. Present profile có đủ sáu nullable scalar (có weight_kg từ V4) và ba nonnull arrays; array order deterministic theo contract. Synthetic example dùng đúng existing DTO:

~~~json
{
  "style_profile": {
    "fit_preference": "REGULAR",
    "height_cm": 165.0,
    "weight_kg": 55.0,
    "usual_top_size": "M",
    "usual_bottom_size": "M",
    "shoe_size": "38",
    "style_codes": ["MINIMAL"],
    "color_codes": ["BLACK", "WHITE"],
    "brand_ids": ["40000000-0000-4000-8000-000000000001"]
  }
}
~~~

REGULAR/MINIMAL/BLACK/WHITE là ví dụ cú pháp, không finite taxonomy được Identity xác nhận. Height50–250cm, at most one fractional digit; size labels1–32 code points; codes syntax1–64 uppercase canonical; arrays<=50 unique; brand UUID không kiểm Commerce tồn tại/active. Không suy body shape/size fit hoặc giới tính từ các values này.

Existing errors: 401 JWT missing/invalid; 403 thiếu role; 404 RESOURCE_NOT_FOUND khi subject account không tồn tại. Không nhầm200 style_profile:null với404/account deleted.

### Consumer proposal — NOT APPROVED

- Stylist IdentityProfileFeignClient dùng explicit Authorization request header argument nhận từ trusted request context; Feign client URL internal cấu hình allowlist, không client-supplied base URL. Không global interceptor tự forward bearer sang mọi downstream/provider.
- Reuse [Feign convention](../../../identity-service/src/main/java/com/stylemind/identity/client/notification/NotificationFeignClient.java) và [no retry](../../../identity-service/src/main/java/com/stylemind/identity/client/notification/NotificationFeignConfiguration.java); scoped config, log level NONE, no auth header/body logging. Email X-StyleMind-Service-Token không phải business-profile credential.
- use_style_profile=true:200/null thành empty soft preferences,200/empty arrays vẫn success; malformed shape503 CONTEXT_UNAVAILABLE, 5xx/timeout503; propagated caller authentication401/403, account404 generic RESOURCE_NOT_FOUND. No silent downgrade nếu requested profile read lỗi.
- use_style_profile=false: không call; user có thể chọn explicit opt-out trong request mới khi Identity outage. Không PUT profile để lưu AI inference.
- Cache chỉ per-generation runtime; không share across subjects hoặc persistent snapshot ngoài target model. Validate downstream bounded strings/arrays/code syntax; unknown semantic codes → soft warning như Phase A, không invent taxonomy.
- Issuer identifier https://identity.stylemind.local; audience stylemind-api; RS256; JWKS URL internal http://identity-service:8081/.well-known/jwks.json. Stylist current decoder chỉ cấu hình key URI/algorithm và timestamps thông thường; chưa có Identity-equivalent issuer/audience/UUID/role mapping.

## 3. Commerce — existing operations, remaining consumer needs

Nguồn: [ProductController](../../../commerce-service/src/main/java/com/stylemind/commerce/controller/product/ProductController.java), [ProductService](../../../commerce-service/src/main/java/com/stylemind/commerce/service/product/ProductService.java), [validator](../../../commerce-service/src/main/java/com/stylemind/commerce/validation/product/ProductRequestValidator.java), [security](../../../commerce-service/src/main/java/com/stylemind/commerce/security/SecurityConfiguration.java), [batch DTO](../../../commerce-service/src/main/java/com/stylemind/commerce/dto/product/VariantBatchLookupResponse.java), [catalog integration tests](../../../commerce-service/src/test/java/com/stylemind/commerce/ProductCatalogPostgresIntegrationTests.java).

### 3.1 OBSERVED service-local API

**Observed-source correction, 2026-11-05:** Current `ProductController` and `SecurityConfiguration` expose `GET /api/v1/products/{productId}` and `POST /api/v1/variants/batch-lookup` as public reads. Neither call requires a bearer token or `X-StyleMind-Service-Token`; the separate `/internal/v1/variants/batch-lookup` is a different endpoint used by other consumers and is not used by Stylist. The batch `VariantResponse` includes `available_quantity`; Stylist deliberately ignores it. `ProductServiceImpl.getPublicProduct` applies product ACTIVE plus category/brand active checks and requires an active variant; the batch service returns only ACTIVE variants whose product/category/brand pass those same checks, with unavailable IDs omitted. Product detail supplies product/category/brand IDs, demographic and product status; batch supplies current variant price/status/color/size/material/images. These separate reads are not a transactional snapshot, so the Stylist consumer validates IDs/parent relationships and repeats the same bounded reads for selected variants immediately before accepting an internal result. This is source-observed behavior, not a new Commerce owner contract.

| Operation | Actual source contract | Consumer implications |
|---|---|---|
| GET /api/v1/products | Public; keyword/category_id/min_price_vnd/max_price_vnd/demographic_segment/page/size, size<=100, default20; response items/page/size/total_elements/total_pages | Không dùng trong bounded Stylist candidate hydration; không có snapshot revision/cursor-consistent export; không invent brand/style/occasion filters |
| GET /api/v1/products/{productId} | Public active product detail; category/brand IDs và names, description/demographic/product status, variants | Current source hides inactive product/category/brand as 404; no inventory count/fit chart |
| POST /api/v1/variants/batch-lookup | Public, body variant_ids array 1–100 unique UUID strings; response `items` of `VariantResponse` including `available_quantity` and images | Active variants only; missing/inactive IDs omitted; consumer ignores available_quantity and joins by ID, never by order |
| GET /api/v1/variant-images/{imageId}/content | Authenticated current image bytes | Không gửi arbitrary image URL cho provider; mặc định text-only benchmark |

ProductService.activeVariantLookup lọc variant_status ACTIVE và product ACTIVE với category/brand active. Missing/inactive variant hoặc inactive parent/category/brand bị omit khỏi items. Không có per-ID missing reason/version/event metadata. Returned items sorted bằng variant ID trong repository; consumer map bằng ID, không zip theo input order. Unknown-field rejection cho batch body chưa được kiểm chứng như Profile's strict JsonNode validator.

Existing batch example với synthetic IDs và giá:

~~~json
{
  "variant_ids": ["30000000-0000-4000-8000-000000000001"]
}
~~~

~~~json
{
  "items": [
    {
      "variant_id": "30000000-0000-4000-8000-000000000001",
      "product_id": "50000000-0000-4000-8000-000000000001",
      "sku": "SYNTH-TOP-M",
      "size": "M",
      "color": "BLACK",
      "material": "Cotton",
      "unit_price_vnd": 320000,
      "variant_status": "ACTIVE",
      "images": [
        {
          "image_id": "60000000-0000-4000-8000-000000000001",
          "variant_id": "30000000-0000-4000-8000-000000000001",
          "image_url": "/api/v1/variant-images/60000000-0000-4000-8000-000000000001/content",
          "alt_text": null,
          "display_order": 0,
          "is_primary": true
        }
      ]
    }
  ]
}
~~~

unit_price_vnd là Java long ở response; consumer vẫn reject malformed upstream negative/fractional/out-of-range JSON. Source validator dùng canConvertToLong/longValue; chưa coi đó là bằng chứng exhaustive fractional-input rejection ở producer. Không sửa Commerce trong workstream Stylist.

Source hiện có per-variant product lookup trong activeVariantLookup rồi category/brand checks; có nguy cơ N+1 ở producer. Đây là finding từ code, chưa đo query count. Stylist không sửa thay Commerce; đề nghị owner Commerce kiểm bounded/batch reads. Consumer cũng không copy pattern lazy relationship traversal.

### 3.1b Existing internal lookup mới — không nhầm với enriched batch

[InternalVariantController](../../../commerce-service/src/main/java/com/stylemind/commerce/controller/internal/InternalVariantController.java): POST /internal/v1/variants/batch-lookup, X-StyleMind-Service-Token, 1–100 raw IDs, duplicate input được collapse. [DTO](../../../commerce-service/src/main/java/com/stylemind/commerce/dto/internal/InternalVariantItem.java) trả variant_id/product_id/product_name/product_status/variant_status/color/size/unit_price_vnd/primary_image_url/purchasable. Missing IDs absent; lookup returns known inactive rows với purchasable=false. Repository computes purchasable từ variant/product ACTIVE và category/brand active.

Current Wardrobe đã dùng endpoint này; Stylist có thể reuse contract sau consumer auth/scope review, không mở endpoint ra Gateway. Token shared không chứng minh caller-owned permission. Internal DTO thiếu category_id/brand_id/material/full images/semantic slots/aggregate version; final hard exclusion theo category/brand cần public product metadata hoặc enriched producer contract. Không invent fields trong existing DTO. Recheck public/internal count/auth/eligibility riêng, không coi hai batch giống nhau.

### 3.2 Consumer contract tối thiểu — PROPOSED, NOT APPROVED

Consumer CatalogVariantView cần variant_id/product_id/category_id/brand_id; product/category/brand names và eligibility; SKU/size/color/material; current unit_price_vnd; variant/product status; images với primary/order; producer version nếu muốn event-consistent projection. Category/style/occasion/slot mapping phải có nguồn review, không dùng label tùy ý như enum chuẩn.

Batch existing chỉ có variant fields/product_id/images; category/brand/name metadata phải qua product detail hoặc producer-approved enriched batch. Đề xuất ưu tiên enriched batch contract do Commerce owner xác nhận, chưa đặt URL mới. Trước khi có nó, consumer có thể dùng một existing variant batch và deduplicated GET mỗi distinct selected product (tối đa6 theo3 results×2 items, bound remaining deadline); phải báo đây là bounded fanout, chưa có one-call metadata batch guarantee. Không giữ local DB txn trong fanout.

Nếu category/brand có hard exclusion hoặc product đổi metadata trong lúc generation, stale index metadata không đủ final check. GET product current hoặc approved enriched response phải xác nhận hard attributes. Một lookup trả variant giá hiện tại không tự chứng minh taxonomy mapping hiện hành.

Current ACTIVE/INACTIVE vocabulary là OBSERVED code; producer phải xác nhận sale-eligibility contract cho Stylist, gồm parent/category/brand policy và delete cases. ACTIVE không physical stock hoặc reserved quantity. No discounts/tax/shipping, quantity mặc định1 cho mỗi unique shop source trong proposal.

Return IDs thiếu khi đối chiếu requested set → ineligible source; duplicate/unrequested IDs trong response → malformed contract, reject whole batch. Provider-returned prices không trusted; sum recomputed from DTO. Unknown additive non-critical response fields có thể ignore, missing required fields invalid; closed enum/status/version semantics không silently normalize.

### 3.3 Snapshot/events chưa existing

Không thấy dedicated complete catalog snapshot, stable revision/event watermark, change publisher/transport, versioned event types hoặc tombstone state trong source khảo sát. Paged product list không có consistent snapshot guarantee khi catalog thay đổi, không thay reliable bootstrap/rebuild. Exporting một bounded sample chỉ offline POC evidence.

Producer contract cần chốt dataset completeness, deleted/inactive inclusion, cutover watermark, pagination stability, rate/payload caps, schema/aggregate versions và mutation events ở mục7. Không đọc Commerce database từ Stylist để né API.

## 4. Wardrobe — actual saved-outfit API và target gap

OBSERVED sources: [V1](../../../wardrobe-community-service/src/main/resources/db/migration/V1__create_outfit_tables.sql), [V2](../../../wardrobe-community-service/src/main/resources/db/migration/V2__add_outfit_sharing.sql), [OutfitController](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/OutfitController.java), [validator](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/validation/outfit/OutfitRequestValidator.java), [share](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/OutfitShareController.java), [shared views](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/SharedOutfitController.java).

| Existing service-local API | Source contract / AI implication |
|---|---|
| GET/POST /api/v1/me/outfits | Caller list/create. Create body exact name/creation_source/items, 1–10 unique variant IDs; source MANUAL or AI; no target wardrobe ID |
| GET/PATCH/DELETE /api/v1/me/outfits/{outfitId} | Own detail/rename/delete; cannot read another owner's resource through /me |
| POST /api/v1/me/outfits/{outfitId}/items | Add currently purchasable variant |
| PATCH/DELETE /api/v1/me/outfits/{outfitId}/items/{outfitItemId} | Switch same-product variant/remove; cannot remove last item |
| POST/GET /api/v1/me/outfits/{outfitId}/shares | Grant by email/list active shares |
| DELETE /api/v1/me/outfits/{outfitId}/shares/{shareId} | Revoke share |
| PUT/DELETE /api/v1/me/outfits/{outfitId}/public-link | Enable/disable tokenized public access |
| GET /api/v1/me/shared-outfits and /{outfitId} | Authenticated caller's shared views |
| GET /api/v1/public/outfits/{publicToken} | Public link view; token is a capability, never put in provider/log |

Current physical rows: outfit(owner_account_id,name,creation_source,...), outfit_item(variant_id,display_order,...), outfit_share(recipient_email,...). No wardrobe/wardrobe_item/requirement/friendship/chat tables; no source_recommendation_id, source_type or slot_name on saved item. Names/prices/images hydrated from Commerce. Source behavior is evidence, not authority to discard target model/backlog. Existing `/me/outfits` and `/me/shared-outfits` security requires ROLE_CUSTOMER; ADMIN-only caller is not automatically authorized to save. Do not add a role or weaken producer rules to repair a handoff test.

### Existing shop-result save handoff

Customer chooses shop-only recommendation and sends its current IDs to existing POST /api/v1/me/outfits. Synthetic body:

~~~json
{"name":"Bộ đi làm","creation_source":"AI","items":[{"variant_id":"30000000-0000-4000-8000-000000000001"}]}
~~~

Wardrobe independently validates current purchasability and saves its own aggregate. The AI label is client-supplied, not verified recommendation lineage. No DB/API source link exists; do not claim exactly-once save, immutable historical price or genuine owned garment provenance. Stylist does not auto-save or call Cart/Payment.

### Target context and provenance — NOT IMPLEMENTED / NOT APPROVED CONTRACT

Target [model](../../../database/six-service-model/04_wardrobe_community.mmd) still requires permitted WARDROBE_ITEM and optional saved criteria; verified backlog R72–81/R95/R97 retains that requirement. Current outfit IDs/variant IDs cannot be substituted. Proposed capability: read permitted selected owned items/criteria and recheck access before return/save; producer must define DTO/auth/ownership/revoke semantics. No invented route in this memo.

Owner correction 28/10 confirms the entire wardrobe/catalog ecosystem is shop-linked. The remaining live dependency is trusted owned-versus-saved status and access, not external garment ingestion. Runtime holding/reference models do not authorize rewriting the unchanged target persistence schema. Fixtures do not complete R95 live integration. Reading a public/shared shop outfit does not prove a garment is owned or consent to external AI. Do not send recipient emails/public tokens to the model.

## 5. HTTP client reliability/security và fixtures

Tất cả policy mới **PROPOSED — NOT APPROVED**. Mọi URL do config/allowlist quyết định, không URL trong provider/user text. Explicit delegated bearer chỉ tới Identity/Commerce/Wardrobe trusted internal endpoints; strip trước AI/embedding. Không dùng global Feign logging hoặc interceptor gây token leak. Service token chỉ dùng endpoint nội bộ đã xác nhận; current Commerce internal lookup có contract riêng bên dưới. Không nhầm email sender permission với user-owned data access.

Timeouts đề xuất connect1s/read3s mỗi business client, bounded remaining30s generation deadline; no retry. Response cap512KiB/client call; count/schema limits không tùy ý tăng theo raw body. Catalog fail không dùng cache như “fresh”; permission fail không fallback. Mapping401/403 giữ authorization meaning,404 xử lý theo capability,5xx/timeout mapped safe context/catalog errors; không log response body.

Fixture/stub schema phải cùng shape consumer sẽ đọc, với synthetic IDs và evidence label rõ:

| Dependency | Success/null/empty | Missing/forbidden | Timeout/malformed/change cases |
|---|---|---|---|
| Identity | present full9 fields; style_profile:null; scalars null/arrays[] | subject404, missing bearer401, role403 | timeout/5xx; string height hoặc null array/overlong fields; unknown code semantics |
| Commerce existing | exact VariantBatchLookupResponse + ProductResponse fixtures | partial items missing/inactive parent/category/brand; auth401/403 | timeout/503; fractional/negative price; duplicate/unrequested IDs; price/status/category change giữa retrieval và final |
| Wardrobe proposed | read_allowed true, requirement null/present, permitted selected items | foreign wardrobe/item, public/shared cases approved by owner, revoked share/friendship | timeout/503; mismatched wardrobe IDs; malformed source; revoke sau generation/trước read/save |
| Provider proposed | native JSON schema object outputs in whitelist | refusal distinct from empty result | 429/5xx/timeout; truncation/extra field/fake/outside-ID/unsafe URL/prompt injection |

Contract test chạy HTTP server stub + actual client serialization/auth forwarding/timeout; không chỉ Mockito DTO return. Stubs không JWT token logs; fixture tokens synthetic, không owner secrets. Trước live gate cần producer runtime/schema revision được owner xác nhận; chưa xác nhận thì fixture tests không chứng minh provider implemented contract.

## 6. AI, embeddings và approved knowledge boundary

Theo [technology memo](EP-07_PHASE_A_CONTRACT_MEMO.md#81-technology-memo), proposal một OpenAI adapter (current benchmark recommendation gpt-6-luna, optional gpt-5.4-mini snapshot comparator) và text embedding adapter. Không dual-provider failover tự động. Spring AI1.1.8 compatible line theo official tagged docs; native response format phải explicit, BeanOutputConverter/Jackson không thay schema enforcement hoặc business validator.

Provider input: minimal manual request + selected soft preferences có mapping + sanitized candidate IDs/evidence + reviewed rule facts. Output: complete-outfit aliases + exact item-alias reasons, explanation/tips; backend maps frozen aliases to typed source IDs/slots. Không prices/names/URLs hoặc arbitrary tool/URL calls. Xem F05/technical design. Refusal/unsupported/truncation/malformed output giữ typed failure, không repair bằng unlimited retries. All caps/privacy approval ở memo; chưa gọi paid API.

Knowledge Source/Entry đều target chưa migrated. Đề xuất curated TEXT artifacts đầu tiên, URL chỉ provenance không fetch arbitrary URL. Trusted reviewer ghi source quyền sử dụng, rule text/status/reviewer/time ở approved input artifact; synthetic fixture review_status=APPROVED chỉ là test state, không nhân danh owner duyệt fashion knowledge. Proposed ingestion không có Admin HTTP endpoint. Nếu cần persistent2 bảng phải approval riêng và role/review lifecycle rõ.

Qdrant vectors/Neo4j graph phải gắn nguồn/version/approved status để filtering, không trở thành nguồn giá/quyền hoặc business source of truth. Revoke knowledge approval phải remove/filter kết quả mới; old recommendation explanation không tự được coi current approved advice. Projection metadata scope cũng cần review, không nhét vào rule_text.

## 7. Catalog events, ordering và freshness

R99 là existing backlog requirement. Mọi transport/schema/ordering dưới đây **PROPOSED — NOT APPROVED**, chưa catalog producer/broker/live catalog delivery; PaymentConfirmed transport hiện có là purpose khác.

### 7.1 Đề xuất event tối thiểu

Ưu tiên variant-centric full snapshot events, thay vì partial patch không thể khôi phục; product/category/brand changes cần producer fanout version mới cho affected variants hoặc approved equivalent protocol. Một logical variant ID, stable source version tăng đơn điệu từ producer; event timestamp chỉ diagnostics.

~~~json
{
  "event_id": "90000000-0000-4000-8000-000000000001",
  "producer": "commerce-service",
  "schema_version": 1,
  "aggregate_type": "PRODUCT_VARIANT",
  "aggregate_id": "30000000-0000-4000-8000-000000000001",
  "aggregate_version": 42,
  "operation": "UPSERT",
  "occurred_at": "2026-10-20T03:00:00Z",
  "payload": {
    "variant_id": "30000000-0000-4000-8000-000000000001",
    "product_id": "50000000-0000-4000-8000-000000000001",
    "category_id": "a0000000-0000-4000-8000-000000000001",
    "brand_id": "40000000-0000-4000-8000-000000000001",
    "product_name": "Synthetic shirt",
    "product_status": "ACTIVE",
    "category_is_active": true,
    "brand_is_active": true,
    "variant_status": "ACTIVE",
    "color": "BLACK",
    "size": "M",
    "material": "Cotton",
    "unit_price_vnd": 320000,
    "images": []
  }
}
~~~

UUID synthetic; aggregate_version logical signed-positive64 integer và schema_version positive integer. operation đề xuất UPSERT/DELETE; DELETE full envelope có payload:null, tombstone được giữ ở projection metadata. Missing/inactive status khác permanent delete; reactivation cần strictly newer authoritative ACTIVE event, deleted ID không tự reuse nếu chưa có producer-approved restore protocol.

### 7.2 Processing contract cần chốt

| Concern | Đề xuất cụ thể | Chưa được bảo đảm / approval cần |
|---|---|---|
| Event source/auth/ACL | Allowlist Commerce producer; schema/version/type/size checks; credentials ở env, transport ACL/TLS phù hợp deployment | Không thêm public unauthenticated webhook; chưa chọn Kafka/RabbitMQ |
| Duplicate | Stable event_id cùng payload hash và same aggregate version no-op; same version khác content conflict/quarantine | Durable dedup/conflict state chưa có |
| Stale/order | Apply chỉ newer version theo aggregate; serialized processing gồm cả async embedding completion | Read-then-upsert version check không tự atomic CAS; không assume Qdrant upsert chống stale |
| Delete/resurrection | Retain high-water tombstone sau xóa vector/graph searchable entry; stale UPSERT không làm sống lại | Xóa point cùng watermark khiến mất protection; retention và ID-reuse policy cần producer/owner duyệt |
| Metadata update | Re-embed khi text/model/dimension/approved mapping đổi; price/status-only update payload nếu embedding text không chứa price | Không để v42 embedding hoàn tất sau v43 rồi ghi v42; cancellation chỉ best-effort |
| Two stores | Versioned Qdrant payload và Neo4j projection metadata; partial failure báo unhealthy/degraded và retry/reconcile sau approved delivery | Không atomic distributed transaction; watermark của một index không đại diện index còn lại |
| Ack/redelivery/crash | Đề xuất at-least-once transport, acknowledge sau both-target applied; replay idempotent | Cần durable per-target state/order fence; chưa có giao thức để claim exactly-once |
| Rebuild | Producer consistent snapshot/cutover watermark; new collection/graph epoch, reconcile and atomic activation strategy theo từng index | Không auto drop active index; cross-index cutover có thể partial, phải gate readiness |
| Final freshness | Commerce hydrate trước persist và read; invalid/missing sources không được approved bởi projection | Không bounded global lag guarantee hoặc price lock xuyên service |

### 7.3 Model gap và lựa chọn không lách schema

Stylist9 target entities không có event ledger/high-water/delivery attempts/tombstone columns; Commerce model hiện cũng chưa định nghĩa aggregate version hoặc publisher delivery state. Không dùng requested_at, review_note, content hoặc rule_text làm ingestion state.

**Khuyến nghị cần owner/producer approve:** versioned full snapshot + explicit delivery/per-aggregate ownership/serialization contract, durable per-index watermarks/tombstones/epoch metadata và crash fence. Metadata có thể thuộc projection stores hoặc một coordinator được duyệt; phải thiết kế durability/atomicity trước live consumer. Nếu coordinator cần relational table hoặc producer migration, trình scope mới riêng. Không tự chọn inbox/outbox hay lease schema trong Prompt01.

Phương án không mở rộng khi chưa duyệt: deterministic offline event replay trong memory và controlled snapshot import/rebuild với published limitations. Nó giúp test algorithm/embedding change nhưng không bảo đảm cross-process stale prevention và không hoàn thành R99 live. Producer ordered delivery một mình không đủ nếu có concurrent consumers/rebuild hoặc delayed embedding; clock timestamp và event_id-only cũng không giải quyết.

Live producer owner phải xác nhận: source of monotonic version, DELETE payload policy, product fanout, redelivery duration/ordering, replay/snapshot access, payload evolution, authentication, operational quarantine/reconciliation owner và permissions. Stylist agent không code producer DB/event publisher thay Commerce team.

## 8. Acceptance và handover evidence

| Mức | Bằng chứng cần thu | Không được suy ra |
|---|---|---|
| Offline deterministic | Synthetic input/output, rule tests, budget/source/event algorithm cases, dataset revision | Không live catalog/permissions/provider |
| Consumer HTTP contract | Actual serialized Feign/AI client tới local HTTP stubs; timeout/auth/redaction/schema assertions | Không producer endpoint đã triển khai hoặc chấp thuận DTO |
| Live service integration | Identity JWT+profile thật; Commerce source hiện tại; Wardrobe permitted/revoked context thật; selected AI; Qdrant/Neo4j; disposable resources | Không cross-service atomicity/production readiness |
| E2E qua Gateway | Real Login A/B → sessions/requests/history/results → negative isolation → Wardrobe-owned save; actual client paths và Postman runtime | Fixture tokens/collection JSON validity không phải issued-token/E2E PASS |

Lượt 27/10 pull dev và cập nhật planning/harness; không Maven/Newman/HTTP runtime/Compose/database/provider calls hoặc migration. Chưa có live verification của consumer contracts này.

Next handoff: owner duyệt năm nhóm trong Phase A memo; mỗi producer xác nhận section tương ứng và revision/schema (không chỉ nói endpoint “có rồi”). Prompt02 có thể làm offline rules sau rule approval trong lúc chờ downstream; R95/98/99/100/101 vẫn cần evidence thật trước EP-07 closure.
