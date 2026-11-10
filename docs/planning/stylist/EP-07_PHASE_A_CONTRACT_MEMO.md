# EP-07 — Phase A contract memo

**Owner persistence decision, 2026-10-28:** Use Spring Data JPA/Hibernate for forthcoming Stylist persistence, superseding previous JDBC instructions. Retain Controller -> Service interface/impl -> Repository. Schema/API/P03 implementation are not approved by this framework decision. Do not refactor other services.


**Historical owner correction (2026-10-28):** Its ONE_PIECE slot and earlier phase gates were superseded in part by Prompt 08 on 2026-10-29. The active product rule is exactly one TOP plus one BOTTOM; all other slots are excluded. Prompt 08 approves P03-A only; see [P03-A handover](EP-07_P03A_SESSION_HISTORY_REPORT.md) for the exact API and two-table schema contract. Other memo proposals remain NOT APPROVED.


Revision: 2026-10-27, Asia/Bangkok. Branch feature/ep-07-ai-stylist; HEAD e6cbaee0ab289e32b1e1f564db9f20a425a5e607 sau pull origin/dev a2a650747246d931ff9fa058a0d3027b263f3d15. Workbook R93–102 đã đọc lại trực tiếp ngày 27/10, không sửa file.

**Planning hoàn tất để review; contract dưới đây là PROPOSED — NOT APPROVED.** Không có endpoint/migration/AI adapter mới được triển khai. Đọc [implementation plan](EP-07_IMPLEMENTATION_PLAN.md) để xem nguyên văn R93–102 và provenance; [dependency memo](EP-07_DEPENDENCY_CONTRACTS.md) phân biệt producer hiện có với consumer proposal.

## 1. Source roles và ranh giới

| Nhãn | Áp dụng tại đây |
|---|---|
| CONFIRMED | Quyết định trực tiếp của owner: sáu business services; service-owned DB; exclusions; AGENTS hiện hành yêu cầu Spring Data JPA; Identity contract được duyệt giữ nguyên |
| EXISTING REQUIREMENT | Workbook Backlog R93–102 và target attributes của [05_stylist.mmd](../../../database/six-service-model/05_stylist.mmd), không tự phê duyệt DDL/API |
| OBSERVED | Source/test/POM/Compose tại baseline; không đồng nghĩa đã chạy runtime |
| PROPOSED — NOT APPROVED | API, limits, rules, schema constraints, lifecycle, security mở rộng cho Stylist và provider decisions trong memo |
| TBD | Producer/review/deployment hoặc quyết định owner chưa có |

Đối chiếu [governance](../../governance/CURRENT_PROJECT_CONTEXT.md), [business rules](../../governance/CURRENT_BUSINESS_RULES.md), [open decisions](../../governance/OPEN_IMPLEMENTATION_DECISIONS.md), [layout](../../architecture/SERVICE_ARCHITECTURE_AND_LAYOUT.md) và chỉ thị owner mới trong chat. Không dùng docs/archive làm execution source. Không tạo/đóng DEC/OPEN/DR; các BA-R*/AC-* trong roadmap cũ vẫn chỉ là nhãn phân tích của roadmap.

Stylist chỉ sở hữu sessions/messages/requests/recommendations/knowledge trong stylemind_stylist. Target Wardrobe sở hữu wardrobe/items/criteria/shares/saved outfits; current implementation chỉ có shop-variant Outfit/OutfitItem/OutfitShare, chưa có owned garment/requirement producer; Commerce sở hữu shop variants/prices/catalog/Cart/Checkout. STYLIST_SESSION khác CHAT_SESSION; không đọc friend chat, không tạo stock/reservation/refund, avatar/3D/fit guarantee, live weather/geolocation, auto checkout, notification “AI done” hoặc Admin knowledge portal/crawler.

Persistence decision 2026-10-28: owner selected JPA/Hibernate, superseding previous JDBC instructions. Retain Stylist JPA starter; no other service refactor. Schema and API remain separate approvals.

Đọc [review mới](EP-07_REVIEW_2026-10-27.md), [flows](EP-07_FLOWS.md), [technical design](EP-07_TECHNICAL_DESIGN.md) và [execution manifest](EP-07_EXECUTION_MANIFEST.json). Những giới hạn/numeric rules ở memo vẫn là proposal, chưa được owner duyệt bởi yêu cầu update plan.

## 2. Một phương án thống nhất đề xuất

Synchronous bounded: validate → resolve owner/context → bounded retrieval → deterministic compose/rank → một AI call chọn/diễn đạt trong candidate whitelist → final authoritative checks → atomic local persist. Chỉ trả outfit đủ mandatory slots và thỏa hard rules. Ít kết quả hợp lệ thì trả ít; không đủ bộ nào thì NO_RESULT. Lỗi provider không tự chuyển thành AI-success từ rule baseline.

Catalog-first là thứ tự POC; release vẫn giữ OWNED_WARDROBE + SHOP_VARIANT theo R95/R97. Không thể hoàn thành live R95 bằng catalog fixture. Qdrant và Neo4j đều giữ trong R98; benchmark đo đóng góp chứ không tự loại một thành phần.

Không queue/worker/cancellation/SSE/polling subsystem. GET request là read resource/history và phục hồi khi response bị mất; không phải job scheduler. Không gọi model/DB owner khác để thay service hiện có.

## 3. Mười use flows

Mọi hành vi tương lai ở phần này là **PROPOSED — NOT APPROVED**. Actor mặc định là customer đã xác thực; ADMIN nếu được chấp thuận cũng chỉ thao tác dữ liệu của JWT sub. No service-side global account-status gate mới được suy ra từ Login.

### Flow 1 — Tạo session và đưa request

- Actor/trigger/input: customer mở tư vấn; POST session với optional title, sau đó POST request với session ID và context. Precondition: JWT phù hợp và session chưa đóng.
- Happy path: tạo session có customer_id từ JWT → xác thực owner/source message và resolve context theo flow2 ngoài transaction → admission transaction lock session, kiểm lại owner/closed_at, tạo USER message nếu có, request với effective context/constraints/owned links.
- Alternate/error: sai JWT 401, thiếu role 403; UUID lỗi 400; foreign/missing session 404 giống nhau; closed session hoặc đang có generation hợp lệ 409; input không có ý nghĩa 400.
- Owner/read/write/transaction: Stylist; chỉ local rows trong transaction ngắn. Không remote call hoặc BCrypt/LLM trong lock.
- Side effects/test/live: mới có scaffold, chưa có flow; test A/B, invalid body, foreign source message, rollback và double admission. Không phát JWT/email.

### Flow 2 — Đọc preferences và permitted wardrobe context

- Actor/trigger/input: orchestrator sau validation/local owner read, trước admission; use_style_profile flag và optional wardrobe/requirement/item IDs. Precondition: local owner hợp lệ; bearer gốc còn hợp lệ cho call.
- Happy path: Feign delegated GET Identity /me/style-profile → null profile vẫn hợp lệ → Wardrobe owner contract đọc permitted items/criteria nếu được yêu cầu → resolve precedence.
- Alternate/error: use_style_profile=false không call Identity; Identity unavailable thì fail CONTEXT_UNAVAILABLE khi caller yêu cầu profile (không fallback âm thầm); Wardrobe 403/missing/revoked fail closed, không rơi sang catalog-only. Chọn explicit opt-out trong request mới để chạy không profile.
- Ownership/data/txn: Identity GET không ghi; Wardrobe kiểm read permission; Stylist chỉ ghi effective target context/constraints được model biểu diễn, không copy nguyên profile/wardrobe vào text/JSON. Remote calls ngoài DB txn.
- Side effects/test/live: Identity route/DTO có thật; chưa có Stylist Feign consumer hoặc Wardrobe owned-context API. Test absent/empty/null profile, unauthorized delegated bearer, unknown codes, timeouts, revoke giữa lần đọc và final check.

### Flow 3 — Truy xuất candidates và approved knowledge

- Actor/trigger/input: retrieval pipeline sau resolved context; bounded catalog filters, manual context, selected permitted owned IDs.
- Happy path: Qdrant lấy shop candidates hữu hạn → hydrate Commerce metadata → Neo4j lấy approved knowledge với rule provenance → tạo immutable candidate whitelist cho lượt generation.
- Preconditions/errors: indexes có approved input/version; unknown slot/taxonomy không đoán; empty candidates trả no-result sau deterministic feasibility check; malformed/downstream deadline fail 503, không tuyên bố retrieval đã chạy bằng fixture.
- Owner/data/txn: Stylist-owned projections; Commerce/Wardrobe authoritative. Không cross-DB SELECT, không txn qua network. Private owned data không tự index vào catalog Qdrant/Neo4j.
- Side effects/test/live: không thấy retrieval infrastructure trong repo; external deployment TBD. Test draft knowledge exclusion, filter completeness, malformed payload, injected instructions và bounded candidate count.

### Flow 4 — Ghép/rank outfit

- Actor/trigger/input: deterministic engine nhận whitelist và resolved constraints.
- Happy path: map approved slot/category vocabulary → enumerate bounded complete templates → hard-filter/budget → stable soft score → diversity ranking, tối đa result_count.
- Preconditions/errors: dữ liệu đủ phân loại slot; thiếu mandatory slot trả fewer/no result, không LLM tự tạo candidate; unsupported hard code trả 400, unsupported soft preference trả warning và bỏ qua riêng preference đó.
- Owner/data/txn: pure Stylist engine, runtime candidate views; không DB write/side effect.
- Tests/live: chưa có code; Prompt 02 test fixture cả shop/owned, duplicate/source XOR, overflow, deterministic tie-break và quality evaluation riêng.

### Flow 5 — AI chọn và diễn đạt

- Actor/trigger/input: một provider adapter sau feasible candidate composition; tối đa 3 complete outfits trong output, aliases thuộc frozen whitelist, evidence text tối thiểu đã sanitize.
- Happy path: structured schema request với complete-outfit aliases → provider chỉ chọn alias/reasons/tips, không tái ghép items → parser kiểm type/length/unknown fields → deterministic whitelist validator. Backend map aliases về source IDs; xem F05.
- Errors: refusal khác no-result; truncated/malformed/outside-whitelist output 502 AI_INVALID_RESULT; timeout 504 AI_TIMEOUT; 429/5xx 503 AI_UNAVAILABLE; không billable auto retry hoặc đổi model tự động.
- Owner/data/txn: Stylist calls provider ngoài transaction; chỉ synthetic data trước privacy approval; không token/email/password/full measurements/private chat. Provider output không có giá/name/image authority.
- Tests/live: chưa có adapter hoặc inference call; stub faults chưa là R100 live PASS. Test prompt injection ở user/catalog/knowledge content, unsupported options/native JSON/schema parser.

### Flow 6 — Final source/price/access/hard checks

- Actor/trigger/input: grounding validator nhận parsed result và whitelist.
- Happy path: batch Commerce đúng IDs shop → kiểm current sale eligibility/metadata mapping → Wardrobe recheck access cho owned → recompute checked whole-VND sum và constraints → loại mọi outfit không hợp lệ.
- Errors: real ID ngoài whitelist vẫn reject; giá/shop status đổi khiến bộ không hợp lệ thì loại bộ đó; owned access bị revoke fail toàn request409 CONTEXT_ACCESS_CHANGED, không chuyển catalog-only. Catalog outage không dùng giá projection. Với price/shop eligibility changes, không đủ bộ còn lại trả fewer hoặc NO_RESULT.
- Owner/data/txn: remote checks ngoài local write txn; không có distributed snapshot hoặc bảo đảm giá/quyền không đổi ngay sau check. Nếu còn quá ít thời gian deadline cho final commit thì fail, không lưu giá chưa recheck.
- Tests/live: batch source đã có nhưng consumer/final validation chưa implement; test price equality/+1/overflow, inactive/missing/duplicate result IDs, revoked source, malformed negative/fractional price. Commerce kiểm lại ở Cart/Checkout sau này.

### Flow 7 — Lưu recommendation và đọc lại

- Actor/trigger/input: finalizer sau grounded result; reader có recommendation/request ID.
- Happy path: final transaction lock session/request theo thứ tự cố định → recheck owner/state/deadline → insert toàn bộ recommendations/items và ASSISTANT message tổng kết chung chỉ có outcome/count, không chứa metadata nguồn private → set SUCCEEDED/NO_RESULT → commit → response.
- Errors: write bất kỳ item lỗi rollback tất cả recommendations/items/state của final txn; start transaction có thể đã commit request PROCESSING. Đọc foreign/missing ID đều 404. Wardrobe access bị revoke thì không trả stored explanation có thể nhắc private item.
- Data/txn: Stylist owns writes; GET rehydrates shop current data và revalidates permitted owned sources ngoài txn, không tạo row. No historical price snapshot có trong model.
- Tests/live: chưa có hai bảng recommendation; test rollback injected failure, no empty recommendation, coarse FAILED đọc lại, current price vs historical semantics, A/B và fixed-count repository reads.

### Flow 8 — Refine bằng request mới

- Actor/trigger/input: customer gửi POST request tiếp trong session, có effective context đầy đủ hoặc source_message_id đúng session.
- Happy path: client gửi structured constraints muốn giữ/đổi → request mới với UUID mới; prior rows/results không sửa. Có thể dùng runtime recent messages đã owner-check để diễn đạt, không suy hard rules từ free text.
- Errors: “rẻ hơn/bộ khác” trong message không tự đổi max_budget_vnd; omitted fields không inherit request trước. Muốn giữ budget/constraints cũ phải gửi lại các structured values. Locked-item feature chưa có trong model nên không thêm.
- Data/txn: Stylist admission/final transactions giống flow 1/7; parent_request_id không có, không nhét vào content.
- Tests/live: pending API; tests prior result unchanged, source message foreign, explicit budget change only, duplicate submit semantics; không hứa lineage/idempotency.

### Flow 9 — Handoff cho Wardrobe save

- OBSERVED: Wardrobe có POST /api/v1/me/outfits, body name/creation_source/items[].variant_id, caller-owned, 1–10 shop variants; save không có source_recommendation_id, wardrobe_id hoặc owned refs. Xem flow F09 và dependency memo.
- Proposed: customer chủ động chọn shop recommendation → client lấy validated variant IDs → gọi existing Wardrobe create với creation_source=AI. Wardrobe tự recheck Commerce và atomic save; Stylist không ghi DB Wardrobe, không auto add Cart.
- Giới hạn: label AI do client gửi không chứng minh verified recommendation lineage. Mixed owned hoặc source recommendation link cần producer contract/schema decision; không tự gửi proposed fields vào API hiện hành.
- Errors/tests: source inactive/missing, B save/read A, duplicate click semantics, no owned-to-variant cast. Live flow chưa chạy trong lượt planning; existing Outfit code không hoàn thành R95 mixed-context.

### Flow 10 — Catalog projection update

- Actor/trigger/input: authenticated Commerce producer qua transport chưa chốt; event contract versioned.
- Happy path: schema/source validation → per-aggregate monotonic checks/dedup → upsert/delete/inactive/tombstone ở cả Qdrant/Neo4j → reconcile/rebuild từ approved snapshot.
- Errors: duplicate no duplicate object; stale no rollback/new resurrection; embedding stale completion không thắng newer version; partial target outage/redelivery/restart theo dependency contract.
- Owner/data/txn: Commerce source of truth; Stylist projection only. Không shared DB txn, không timestamp-as-order guarantee. Metadata durability/delivery gate chưa có.
- Tests/live: chưa có catalog producer/consumer; PaymentConfirmed delivery không thay catalog changes; offline event fixtures chỉ coverage deterministic, R99 chưa live DONE.

## 4. Proposed HTTP contract

### 4.1 Quy ước chung

Toàn bộ mục 4 là **PROPOSED — NOT APPROVED**. Năm service-local routes dưới đây dùng JSON UTF-8, direct DTO success và Problem Details errors theo proposal; chưa có approval đổi global response envelope. Current Gateway /stylist/** strips một prefix, nên URL client hiện tương ứng /stylist/api/v1/stylist/.... /api/v1/stylist/... trực tiếp qua Gateway hiện bị catch-all chuyển Identity; chưa phải route Stylist hoạt động. Kế hoạch dùng prefix hiện hữu; không cần sửa Gateway để có path mới. Chỉ trình routing change nếu owner yêu cầu riêng.

- JWT: RS256 only, issuer https://identity.stylemind.local, audience stylemind-api; Compose JWKS http://identity-service:8081/.well-known/jwks.json là network endpoint, không phải issuer. Validate signature/algorithm/exp/nbf/iat/UUID sub/UUID jti; future iat tolerance 60 seconds theo Identity. Map roles CUSTOMER/ADMIN → ROLE_*. Unknown roles không được cấp quyền.
- Proposed authorization: CUSTOMER hoặc ADMIN đều self-only; lấy owner từ UUID JWT sub ở service/repository criteria, không customer_id trong input. Không global live-status/account DB call mới hoặc introspection guarantee; JWT cũ có thể còn hợp lệ tới exp theo policy hiện tại.
- User-specific responses và Problem Details dùng Cache-Control: no-store; không cache shared CDN. No sensitive values trong Location/log/error. Correlation header theo [filter](../../../stylist-service/src/main/java/com/stylemind/stylist/logging/CorrelationIdFilter.java), safe 1–64 ASCII pattern, giữ qua approved clients.
- Unknown body/query fields, duplicate JSON keys, duplicate query keys và wrong JSON types → 400 INVALID_REQUEST. Null/omitted semantics theo bảng field; UUID hyphenated canonical; không trim password/token (không nhận chúng ở các DTO này).
- Unicode-trim user text, đếm code points, reject control characters; blank không đồng nghĩa null. Message tối đa 2.000, context text 1.000, occasion/intended_use/style_context 128, title 100; body tối đa 32 KiB (413 PAYLOAD_TOO_LARGE). Giới hạn là đề xuất để bound prompt/DB/latency, không là rule đã duyệt.
- Mọi unknown/foreign resource trả 404 RESOURCE_NOT_FOUND qua owner-scoped lookup. Sai method 405 nếu đã auth và map framework; không mutation. 415 cho non-JSON POST; không mở toàn bộ /api/** public.

### 4.2 API matrix

| Method / service path | Request / semantics | Success / response | Transaction/downstream | Tests tối thiểu |
|---|---|---|---|---|
| POST /api/v1/stylist/sessions | Body {} hoặc title omitted/null/string 1–100; unknown field invalid | 201 + Location service resource; session_id, title, opened_at, closed_at:null; owner field không do client quyết định | Một local insert; no downstream; title không AI-generate | role/JWT, unknown/customer_id, Unicode/limit, A/B ownership persisted |
| GET /api/v1/stylist/sessions/{session_id}/messages | Optional limit 1–50 default20; before cursor gồm sent_at+UUID encoded, owner-bound; invalid cursor400; không body | 200 items, next_cursor:null/string; stable sent_at DESC then message UUID DESC; each message_id/speaker_role/content/sent_at và derived request_ids | Read bounded local query + batch request-ID lookup; no provider; owner check ở service/repo | empty, equal timestamp tie, pagination overlap, foreign session/cursor, no writes/N+1 |
| POST /api/v1/stylist/sessions/{session_id}/requests | Fields mục4.3; có meaningful context sau resolution | 201 + Location request nếu SUCCEEDED hoặc NO_RESULT; request_id, request_status, requested_at, recommendation_ids, recommendations, limitations | Admission commit → remote pipeline → atomic final commit; failures mục4.4 có request_id nếu đã tạo | tất cả validation, concurrency/deadline, faults, no partial invalid result, both sources |
| GET /api/v1/stylist/requests/{request_id} | Không body/query | 200 request_id/session_id/source_message_id + effective persisted context/budget/constraints/owned IDs, request_status, requested_at, recommendation_ids; failure_details:null; deadline_exceeded runtime | Read-only local owner-join; không generation hoặc retry; không giả replay runtime profile/provider trace | foreign/missing, all states, coarse failure, requested_at not changed, no-write |
| GET /api/v1/stylist/recommendations/{recommendation_id} | Không body/query; delegated bearer allowed cho Wardrobe consumer cùng subject | 200 recommendation_id/request_id/explanation/styling_tips/generated_at/items + current source views và check time; không historical price claim | Read snapshot local → remote hydration/access; no writes/provider; unavailable nguồn chưa chắc là owner error | A/B, revoke, current/missing/inactive price, provider-independent read, upstream timeout |

speaker_role values USER/ASSISTANT là model vocabulary. closed_at có trong model nhưng chưa có close/title CRUD endpoint; create luôn null. GET history không tự ghi recovery state.

### 4.3 Request fields và persistence mapping

| Field | Logical type / omitted / null / empty | Validation / effect | Persisted hoặc runtime |
|---|---|---|---|
| message | optional string; omitted/null không tạo message; blank invalid | 1–2.000 chars; XOR source_message_id | STYLIST_MESSAGE.content, USER; source ID được server gán vào request |
| source_message_id | optional UUID; omitted/null không reuse | Nếu có phải USER message của cùng session, owner scoped; mismatch/missing404, không lộ session khác | STYLING_REQUEST.source_message_id |
| wardrobe_id | optional UUID; null/omitted không lấy wardrobe | Item IDs yêu cầu wardrobe_id; chỉ selected permitted items của wardrobe đó ở version đầu (proposal restriction) | STYLING_REQUEST.wardrobe_id remote reference |
| requirement_wardrobe_id | optional UUID; null/omitted không đọc saved criteria | Nếu supplied phải bằng wardrobe_id trong proposal đầu; Wardrobe kiểm access; không auto dùng criteria chỉ vì có wardrobe_id | STYLING_REQUEST.requirement_wardrobe_id remote reference |
| occasion/intended_use/style_context | optional string; omitted inherits saved requirement nếu có; null masks inherited scalar; blank invalid | 1–128; syntax text, không enum WORK/MINIMAL tự phát | Effective values vào corresponding STYLING_REQUEST attributes |
| general_weather_context | optional manually supplied string; omitted inherits approved criteria; null clears; blank invalid | 1–1.000, không live weather API hoặc inference | Effective STYLING_REQUEST.general_weather_context |
| max_budget_vnd | optional exact integer 0..9223372036854775807; omitted inherits saved criterion; null clears | Reject string/fractional/exponent notation/negative/overflow; canonical whole VND | Effective STYLING_REQUEST.max_budget_vnd |
| budget_is_hard | optional bool, null invalid; omitted inherits cùng saved budget hoặc false khi không budget | Effective true + amount null →400 INVALID_CONTEXT; explicit null amount mà inherited true phải gửi false | STYLING_REQUEST.budget_is_hard |
| constraints | optional array default []; null invalid; [] no constraints | <=20, complete set cho request này; duplicate canonical entries invalid, không merge request trước | STYLING_CONSTRAINT rows |
| constraints[].constraint_kind | required string đề xuất EXCLUDE_CATEGORY/COLOR/PRODUCT/VARIANT | Không thêm kind style preference/budget vào cột này. Vocabulary cần duyệt | constraint_kind |
| constraints[].catalog_entity_id / text_value | exactly one nonnull; optional field còn lại có thể omitted/null | CATEGORY/PRODUCT/VARIANT dùng UUID; COLOR dùng text mapped approved color code 1–64; unknown hard color reject, unknown soft ignore+warning | catalog_entity_id / text_value; không JSON metadata |
| constraints[].is_hard | required bool, null invalid | Hard luôn thắng rank/LLM; soft exclusion chỉ penalty | is_hard |
| wardrobe_item_ids | optional array default []; null invalid | <=2 unique UUID theo tối đa2 items/outfit (API vẫn là proposal); [] no owned input; any inaccessible ID fails whole request | REQUEST_WARDROBE_ITEM composite rows |
| use_style_profile | optional bool default true; null invalid | true: require successful Identity read, profile null okay; false: no call | Runtime-only, không model column |
| result_count | optional integer1–3 default3; null invalid | Output fewer allowed, zero request count invalid | Runtime-only; DB actual result count derived |

client customer_id/price/provider/model/status/timestamps/item ownership fields đều unknown và bị reject. Không có mode, parent_request_id, quantity, locked_items hoặc idempotency_key field trong proposal này.

Meaningful context: sau approved resolution phải có ít nhất một nonblank message/source message hoặc occasion/intended_use/style_context/general_weather_context hoặc ít nhất một permitted owned item. Chỉ budget hoặc profile chứa height/size không đủ xác định nhu cầu; trả 400 INVALID_CONTEXT, không gọi AI. Message là ngữ cảnh mềm; không được parse thành hard price hoặc đổi structured constraints âm thầm.

### 4.4 Status, errors và result examples

| Tình huống | Proposed HTTP / code | Persistence/result |
|---|---|---|
| Malformed input/unsupported hard taxonomy | 400 INVALID_REQUEST / INVALID_CONTEXT / UNKNOWN_CONSTRAINT_VALUE | Resolve input/context trước admission; lỗi ở giai đoạn này không tạo request |
| Foreign/missing owner resource | 404 RESOURCE_NOT_FOUND | Không đọc nguồn private |
| Closed session / another live generation | 409 SESSION_CLOSED / GENERATION_IN_PROGRESS | Không tạo request trùng |
| Valid execution, zero eligible outfits | 201 request_status NO_RESULT | Zero recommendations/items, safe assistant summary |
| Fewer valid outfits | 201 request_status SUCCEEDED | Chỉ lưu các bộ đầy đủ; limitations runtime nêu fewer results |
| Profile/Wardrobe unavailable | 503 CONTEXT_UNAVAILABLE | No fake context fallback; request FAILED nếu admission đã commit |
| Final catalog unavailable | 503 CATALOG_UNAVAILABLE | Zero new recommendations |
| Provider 429/5xx/refusal/timeout | 503 AI_UNAVAILABLE / 422 AI_REFUSED / 504 AI_TIMEOUT | FAILED; không provider auto retry/model failover |
| Invalid/truncated/unlisted provider output | 502 AI_INVALID_RESULT | Zero invalid recommendation, không text-scrape sửa IDs |
| Overall deadline / stale process | 504 GENERATION_DEADLINE_EXCEEDED | FAILED hoặc INTERRUPTED theo thời điểm local finalization |
| Owned access revoke giữa generation hoặc khi đọc result đã lưu | 409 CONTEXT_ACCESS_CHANGED | Không lưu mới/trả private explanation/items |
| DB write failure | 500 INTERNAL_ERROR | Final transaction rollback; PROCESSING có thể còn sau admission |

Problem Details giữ type/title/status/detail/instance/code/timestamp/correlationId. Sau admission có thể thêm request_id an toàn để client đọc lại; không expose upstream body, prompt, token, credential hoặc stacktrace. Structural/schema/source-type/whitelist violation làm reject toàn bộ provider response với 502; chỉ shop source/price/status thay đổi sau một output hợp lệ mới được loại từng complete outfit để trả fewer/NO_RESULT. Owned permission change dùng409 và fail closed toàn request.

JSON dưới đây chỉ minh họa proposal; UUID synthetic, không ID có thật.

~~~json
{
  "message": "Gợi ý bộ đi làm tối giản",
  "occasion": "Đi làm",
  "max_budget_vnd": 900000,
  "budget_is_hard": true,
  "constraints": [
    {"constraint_kind": "EXCLUDE_COLOR", "text_value": "RED", "is_hard": true}
  ],
  "wardrobe_item_ids": [],
  "use_style_profile": true,
  "result_count": 2
}
~~~

~~~json
{
  "request_id": "10000000-0000-4000-8000-000000000001",
  "request_status": "SUCCEEDED",
  "requested_at": "2026-10-20T03:00:00Z",
  "recommendation_ids": ["20000000-0000-4000-8000-000000000001"],
  "recommendations": [
    {
      "recommendation_id": "20000000-0000-4000-8000-000000000001",
      "generated_at": "2026-10-20T03:00:05Z",
      "explanation": "Bộ phối dùng hai món trong danh sách đã kiểm tra.",
      "styling_tips": "Có thể cân nhắc phụ kiện sẵn có.",
      "items": [
        {
          "source_type": "SHOP_VARIANT",
          "shop_variant_id": "30000000-0000-4000-8000-000000000001",
          "wardrobe_item_id": null,
          "slot_name": "TOP",
          "display_order": 0,
          "selection_reason": "Màu trung tính theo metadata đã cung cấp.",
          "current_unit_price_vnd": 320000
        },
        {
          "source_type": "SHOP_VARIANT",
          "shop_variant_id": "30000000-0000-4000-8000-000000000002",
          "wardrobe_item_id": null,
          "slot_name": "BOTTOM",
          "display_order": 1,
          "selection_reason": "Bổ sung slot BOTTOM.",
          "current_unit_price_vnd": 450000
        }
      ],
      "current_purchase_total_vnd": 770000,
      "current_data_checked_at": "2026-10-20T03:00:05Z"
    }
  ],
  "limitations": ["Chỉ có một bộ đủ điều kiện; giá có thể thay đổi sau kiểm tra.", "Không bảo đảm size vừa hoặc tồn kho vật lý."]
}
~~~

price/check time/limitations và hydrated source views là runtime-only. GET sau này không thể cam kết trả lại số tiền 770000 tại lúc generate vì model không lưu price snapshot. Khi Commerce missing/inactive: giữ source ID lịch sử, current_catalog_item=null/availability=UNAVAILABLE, current_purchase_total_vnd=null nếu không tính đủ; không được mua từ kết quả cũ. Catalog outage khác inactive: trả 503 thay vì giả current view. Tất cả owned access phải còn hợp lệ trước khi trả explanation.

## 5. Business rules đề xuất

### Context, precedence và taxonomy

PROPOSED — NOT APPROVED: explicit scalar request (kể cả null) > saved Wardrobe requirement khi caller chỉ định requirement_wardrobe_id > null. Identity preferences chỉ soft ranking; không tự trở thành wardrobe criteria hoặc hard constraint. constraints và wardrobe_item_ids là full set theo request; omitted/[] không kế thừa request cũ. Không union arrays âm thầm. Một explicit hard exclusion luôn thắng mọi preference/style text; caller phải thay structured input để nới.

Identity codes chỉ được validate cú pháp/uppercase, không finite taxonomy. Stylist giữ mapping review được từ code/label → approved meaning; unknown soft style/color/fit/brand reference không invent meaning, trả warning và bỏ riêng soft preference. Unknown hard exclusion/slot mapping phải reject hoặc không lấy candidate đó; không match tùy ý bằng LLM. Brand UUID hợp lệ cú pháp chưa chứng minh Commerce brand active.

### Budget và quantity

PROPOSED — NOT APPROVED: budget là mỗi outfit, tổng giá SHOP_VARIANT cần mua với implicit quantity=1 mỗi unique source item. OWNED_WARDROBE cost=0, dù origin_shop_variant_id có giá trong shop; không gửi món owned vào Cart. Không quantity column nên version đầu không hỗ trợ 2 chiếc cùng variant trong một outfit. Không cộng shipping/discount/tax.

Exact integer long input; sum bằng checked long arithmetic (Math.addExact hoặc BigInteger kiểm bound), không float/round/wrap. amount=0 hợp lệ; hard=true+null invalid; hard=false có amount là target mềm. total==budget hợp lệ, total>budget hard invalid; nếu sum overflow reject bộ đó. Giá cuối lấy Commerce, không LLM/projection; model-returned price là field không được chấp nhận. Recheck sau AI, không giữ “giá chốt” xuyên services. Later GET dùng current price, Cart/Checkout revalidate riêng.

### Templates, ranking, diversity

PROPOSED — NOT APPROVED: templates tối thiểu TOP+BOTTOM hoặc ONE_PIECE; không có optional slot. Category→slot mapping curated cần owner review, không đoán từ tên category. Mandatory slots phải đầy đủ; TOP/BOTTOM không được xuất hiện cùng ONE_PIECE trong template đầu. Một source item chỉ một lần/outfit, một slot tối đa một item, tối đa2 items/outfit; chỉ tính giá các món cần mua.

Permitted selected owned items là anchors cần xuất hiện trong mỗi kết quả của request; mâu thuẫn slot/exclusion/budget thì 400 INVALID_CONTEXT hoặc NO_RESULT nếu chỉ thiếu candidates. Không bịa owned metadata/fit; không tự dùng toàn bộ wardrobe khi IDs rỗng. Anchor semantics là đề xuất cần duyệt, không suy từ tên link table.

Ranking đề xuất thứ tự hard-valid trước, sau đó sum bounded soft weights: context match3, style2, color2, brand1, soft-budget1, compatibility reviewed rule3; unknown evidence=0, không mặc định match. Tie-break theo sorted source IDs; weights chỉ fixture benchmark chưa fashion truth. Candidate cap60 (tối đa20/mandatory slot), bounded search1000 combinations, trả1–3 results. Giữa hai result cần khác ít nhất một non-anchor item; không đủ diversity thì fewer. Không dùng variation seed/parent IDs persisted ngoài model.

Đề xuất scoring cụ thể cho02: context/compatibility là0 hoặc1 từ curated rule evidence; style/color/brand là tỷ lệ items khớp ít nhất một preference đã map, trong0..1; không có preference hợp lệ thì feature=0. Soft-budget feature=1 khi có soft amount và total<=amount, còn lại0. Dùng exact decimal/rational score, không LLM tự chấm hard validity; pin fixture mapping/reviewer revision trước so sánh.

### Provenance và explanation

EXISTING REQUIREMENT: nguồn OWNED_WARDROBE và SHOP_VARIANT theo model; proposed checks: owned có wardrobe_item_id, shop_variant_id=null; shop ngược lại. ID có thật nhưng ngoài whitelist vẫn invalid. Final owned permission check không thể thay bằng cached profile hoặc request link.

Explanation <=2.000 code points, tips nullable<=1.000, per-item selection_reason1–500; assistant summary<=2.000. Nội dung chỉ dựa approved context/knowledge và metadata đã đọc; không claim chất liệu/giảm giá/ảnh/giá ngoài evidence; không fit/body effects hoặc perfect-fit. Không để LLM xuất arbitrary URL. Stored text có thể nhắc private source nên GET phải recheck owned access trước trả.

Structured JSON chỉ chứng minh shape; whitelist/source/price/access/hard validation vẫn deterministic. Chất lượng thời trang do reviewer chấm riêng, không gọi parser PASS là “phối đẹp”.

## 6. Schema mapping — không DDL

Model hiện không có business migration ở Stylist. Toàn bộ local PK/FK/unique/check bên dưới là **PROPOSED — NOT APPROVED** để duyệt migration; không execute schema. Attribute names giữ đúng target model; API request_id map styling_request_id, session_id map stylist_session_id.

| Requirement/flow → entity | Tất cả target attributes | Proposed local constraints và remote refs | API / validation / tests |
|---|---|---|---|
| R93/94, flow1 → STYLIST_SESSION | stylist_session_id, customer_id, title, opened_at, closed_at | UUID PK; owner logical Identity ref không FK; closed_at>=opened_at nếu có | POST sessions/history; subject ownership/closed; A/B + title |
| R93/94, flow1/7 → STYLIST_MESSAGE | stylist_message_id, stylist_session_id, speaker_role, content, sent_at | PK, FK session; USER/ASSISTANT; bounded text; unique(session,id) hỗ trợ same-session FK | history/request; no arbitrary speaker input; message rollback/order |
| R93/94, flow1/8 → STYLING_REQUEST | styling_request_id, stylist_session_id, source_message_id, wardrobe_id, requirement_wardrobe_id, occasion, intended_use, style_context, general_weather_context, max_budget_vnd, budget_is_hard, request_status, requested_at | PK/FK session; source optional composite FK(session,source_message) → message(session,id); nonnegative budget; hard implies amount; lifecycle vocabulary proposed; remote wardrobe refs không FK | POST/GET requests; meaningful/precedence/source belongs same session; ownership + budget + concurrent admission |
| R93/96, flow4 → STYLING_CONSTRAINT | constraint_id, styling_request_id, constraint_kind, catalog_entity_id, text_value, is_hard | PK/FK request; exclusive value; kind/value type check, bounded text; catalog ref logical không FK; duplicates canonical app check dưới request txn | constraints input; hard cannot relax, unknown code, XOR, rollback |
| R93/95, flow2 → REQUEST_WARDROBE_ITEM | styling_request_id, wardrobe_item_id | composite PK; FK request; wardrobe_item remote không FK, access owner-authoritative | request owned IDs; <=4 unique/permitted; B/revoked source |
| R97/100/101, flow7 → WARDROBE_RECOMMENDATION | recommendation_id, styling_request_id, explanation, styling_tips, generated_at | PK/FK request; nonblank explanation; no new snapshot/metadata column | GET recommendation/request results; safe text + atomic rollback |
| R97/101/102, flow6/7 → RECOMMENDATION_ITEM | recommendation_item_id, recommendation_id, source_type, wardrobe_item_id, shop_variant_id, slot_name, selection_reason, display_order | PK/FK recommendation; source enum+reference XOR; order>=0/UQ(rec,order); source-duplicate checks app/approved partial unique; remote refs no FK | response/handoff; whitelist+current hydration+complete slots; fake/outside-ID and source XOR tests |
| R98 dependency, flow3 → KNOWLEDGE_SOURCE | knowledge_source_id, source_type, source_text, source_url, submitted_by_user_id, submitted_at | PK; TEXT/URL exactly one matching nonblank content; user logical ref, no FK; no arbitrary URL fetching | Không public ingestion endpoint đề xuất ở scope này; curated fixture/review provenance tests |
| R98 dependency, flow3 → KNOWLEDGE_ENTRY | knowledge_entry_id, knowledge_source_id, rule_text, review_status, reviewed_by_user_id, reviewed_at, review_note | PK/FK source; proposed DRAFT/APPROVED/REJECTED; approved needs reviewer/time; submitter/reviewer refs logical | Knowledge retrieval internal; only approved entries; draft/rejected/injection excluded |

R93 chỉ năm bảng conversation/request đầu; R97 hai bảng recommendation tiếp. Hai bảng knowledge là review/ingestion dependency cần explicit scope approval riêng; không tự gộp vào R93. Nonempty recommendation và template completeness phải validate aggregate trước commit; FK đơn thuần không bảo đảm có child row. Không auto-cascade xóa history; retention/delete policy TBD trước real user content.

Ownership được kiểm ở service và repo owner-scoped join: request → session.customer_id, recommendation → request → session; không chỉ check controller. Batch load constraints/items/roles liên quan theo tập IDs, không lazy ORM traversal. Theo current owner JPA instruction: entity mapping/truy vấn nằm trong feature-grouped JPA repositories; transaction/row locks ở helper bean riêng. Không auto DDL; JPA được owner duyệt tường minh, không suy ra từ scaffold. Flyway mechanism đề xuất theo Identity/Commerce11.15.0, chỉ thêm dependency sau scope approval.

### Gaps: bảo đảm nào model chưa đáp ứng?

| Bảo đảm | Vì sao chưa đủ | Không mở rộng schema: phương án khuyến nghị đầu tiên | Nếu cần mạnh hơn / task |
|---|---|---|---|
| Historical quoted price / audit exact budget at generation | Không price snapshot/check-time fields | API current prices, recheck trước save; later GET không replay historical total | Approve snapshot attrs riêng trước migration; R97/101 |
| Multiple quantity per variant | Không quantity | Implicit1, duplicate source invalid | Quantity schema/rules riêng; R97/101 |
| Durable request retry idempotency / exactly-once billing | Không idempotency key/digest state | Sequential repeat tạo request mới; disclose duplicate charge risk, no automatic client retries | Approve idempotency persistence/retention; R94/100 |
| Refine lineage | Không parent_request_id | New independent request trong session; client resubmits effective structured values | Approve lineage column/constraints; R94 |
| Exact provider/prompt/model/input snapshot audit | Không trace/version columns; profile context runtime | Safe aggregate metrics/eval artifacts với synthetic data; GET không reconstruct provider input/failure detail | Explicit trace/schema/privacy scope; R100/102 |
| Durable ordered event processing/delete protection across replicas | Không event/version/tombstone/checkpoint model | Offline fixture hoặc serialized ingestion với giới hạn được công bố, vẫn không live R99 DONE | Projection metadata/delivery durability approval, không nhét vào request content; R99 |

## 7. Transactions, concurrency và crash

PROPOSED — NOT APPROVED: request_status = PROCESSING, SUCCEEDED, NO_RESULT, FAILED, INTERRUPTED. Chưa có vocabulary này trong migration. SUCCEEDED chỉ khi ít nhất một valid recommendation, NO_RESULT không có child recommendation.

1. HTTP handler bắt đầu runtime deadline30 giây cho toàn lượt, validate/auth/local owner read rồi resolve Identity/Wardrobe context ngoài transaction. Lỗi context trước admission không ghi request. Admission transaction lock session SELECT ... FOR UPDATE qua JPA pessimistic write locking, kiểm lại owner/closed_at và request PROCESSING đang có. Request chưa hết admission-age deadline thì409; request cũ quá hạn được mark INTERRUPTED trước request mới. requested_at là DB UTC admission time; durable stale threshold đề xuất requested_at+30 giây, immutable.
2. Insert message/request với effective resolved scalar/budget values, constraints và permitted owned IDs atomic rồi commit. Profile preferences chỉ giữ runtime, không persist snapshot. Không giữ connection/row lock khi remote calls chạy. Kiểm lại source-message membership ở transaction để tránh TOCTOU.
3. Retrieve/compose/generate/final authoritative rechecks trong phần còn lại của runtime deadline từ HTTP entry; context preflight cũng tiêu thời gian đó, không được cộng thêm30 giây sau admission. Timeout cancellation best-effort, không rollback provider bill. Không retry tự động.
4. Final transaction lock cùng session trước request, kiểm status còn PROCESSING, runtime deadline còn và DB clock chưa quá durable admission-age deadline; final state CAS bằng khóa và condition được kiểm lại, không chấp nhận finalization khi đã thấy INTERRUPTED/deadline expired. Insert toàn bộ valid recommendations/items + generic safe assistant outcome/count + terminal status cùng commit. Không trả success trước commit.
5. Fault handler sau admission dùng transaction ngắn chuyển PROCESSING→FAILED khi có thể. DB outage khiến fault handler cũng thất bại thì PROCESSING còn tồn tại; không giả đã rollback admission. GET resource read-only hiển thị coarse state + deadline_exceeded từ admission-age threshold; next POST mới xử lý stale status. Ngưỡng này bảo thủ vì không có persisted HTTP-entry/deadline column; không resume request cũ hoặc repeat AI bill.

Session lock chỉ serialize local admission/finalization; không giữ khi network. Partial unique index “one PROCESSING/session” có thể là additional guard nếu approved, không thay transactional check. Một session có thể có nhiều completed requests; các session khác không bị global serialization. Duplicate same-content POST sau completed tạo request mới; chưa có idempotency guarantee.

Crash sau final commit trước response: client đã biết session có thể đọc messages và derived request_ids để tìm request, GET lại không regenerate. Structured-only request không có USER source message nên response request_id bị mất không bảo đảm discover được từ năm API tối thiểu; không giả mọi request đều có history link. Nếu cần discover đầy đủ, trình additional session-request list operation và acceptance riêng. Crash trước final commit: không half-saved recommendation, stale PROCESSING recovery như trên. Không có strong deterministic replay vì runtime-only profile/result_count/provider trace không được persist.

Final check không atomic với Commerce/Wardrobe: công bố checked-at instant, không hứa remote access/price giữ mãi; đọc/save/checkout phải revalidate. Abort kết quả khi không đủ remaining deadline cho ghi an toàn, không dùng transaction timeout làm distributed guarantee.

Lock timeout đề xuất1s, local write transaction timeout2s trong remaining runtime budget. Driver/DB commit có thể chậm hoặc response bị mất sau commit; không hứa mọi side effect dừng đúng wall-clock30s. Commit outcome không chắc chắn trả safe error kèm request_id nếu có, client GET kiểm tra thay vì auto POST/provider retry; fault handler chỉ chuyển status nếu vẫn PROCESSING. Tests phải phân biệt timeout trước commit với committed-success/mất response.

## 8. Technology, QA và evaluation

### 8.1 Technology memo

Tất cả lựa chọn mới dưới đây **PROPOSED — NOT APPROVED**; không cài dependency hay gọi inference.

| Thành phần | Đề xuất cụ thể / compatibility | Gate và giới hạn |
|---|---|---|
| Java21 / Boot3.5.16 MVC / PostgreSQL18 | Giữ nền tảng observed | No WebFlux rewrite; JPA theo owner decision 28/10; scaffold dependency adjustment trong03 |
| Persistence/Flyway | Spring Data JPA theo chỉ thị hiện hành + Flyway11.15.0 candidate theo repository convention | Chỉ approved tables; ddl-auto=none, không V1–V3 Identity |
| OpenFeign | Spring Cloud2025.0.3 theo Identity/Commerce; explicit delegated bearer parameter; Retryer.NEVER_RETRY | Không forward bearer ra provider, không global interceptor rò token |
| Spring AI | BOM1.1.8, compatible Boot3.4/3.5 theo tagged official docs; chưa xác nhận dependency resolution trên repo | Không dùng API Spring AI2.x/Boot4 từ latest docs; compile/dependency gate ở06 |
| Generation | Một OpenAI adapter; current recommendation benchmark gpt-6-luna, comparator gpt-5.4-mini-2026-03-17 nếu cần; xem technical design | Snapshot/model access/options support cần capability test sau approval; không tự switch model/cost khi fail |
| Embedding | TextEmbedding adapter, initial text-embedding-3-small, version/dimension gắn collection epoch | Không coi same vector dimension là same semantics; model/dimension đổi phải reindex |
| Qdrant | Official Java client trực tiếp sau retrieval port; payload filters/index | Pin client/server exact version khi operator cung cấp deployment; chưa invent version compatibility |
| Neo4j | Java Driver6.1.0 candidate, Java17+ nên phù hợp Java21; server4.4/5/2025/2026 theo official docs | Server/license/ACL thực tế TBD; không Spring Data graph thay relational ownership |
| Tests | JUnit nền tảng; HTTP stubs và Testcontainers sau approved test dependencies; Postman/Newman | Offline, stub, live, Gateway evidence tách bốn mức |

Nguồn official dưới đây từ memo ngày 20/10; đã đối chiếu lại compatibility/model strategy ngày 27/10 tại technical design. Không dùng model proposal cũ làm approval:

- [Spring AI1.1.8 getting-started](https://raw.githubusercontent.com/spring-projects/spring-ai/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/getting-started.adoc) xác nhận Boot3.4/3.5; [tagged POM](https://raw.githubusercontent.com/spring-projects/spring-ai/v1.1.8/pom.xml) dùng Boot3.5.15. Đây là compatibility rationale, chưa compile integration bằng Boot3.5.16.
- [Spring AI1.1.8 OpenAI options](https://raw.githubusercontent.com/spring-projects/spring-ai/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat/openai-chat.adoc) có JSON_SCHEMA/schema/strict và maxCompletionTokens; [latest getting-started](https://docs.spring.io/spring-ai/reference/getting-started.html) là2.x/Boot4, không copy snippets của nhánh đó.
- [GPT5.4 mini](https://developers.openai.com/api/docs/models/gpt-5.4-mini), [GPT5.4](https://developers.openai.com/api/docs/models/gpt-5.4) liệt kê snapshots và Structured Outputs/Chat Completions. Đây là benchmark proposal, chưa chứng minh fashion quality hoặc account access.
- [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=chat): native schema constraint khác Java deserialization; refusal/truncated/unsupported schema vẫn phải xử lý. Không copy auto-correction/retry defaults của SDK.
- [Embedding model](https://developers.openai.com/api/docs/models/text-embedding-3-small), [Qdrant Java client](https://github.com/qdrant/java-client), [Qdrant payload](https://qdrant.tech/documentation/manage-data/payload/), [Neo4j driver](https://neo4j.com/docs/java-manual/current/install/).
- [OpenAI data controls](https://developers.openai.com/api/docs/guides/your-data): API data-use/retention phụ thuộc endpoint và tổ chức; default abuse monitoring có thể giữ content tới30 ngày, zero-retention cần eligibility/approval. Không coi store=false là zero-retention bảo đảm.

Native strict JSON_SCHEMA tại provider + strict Jackson/schema validation tại consumer + deterministic grounding là ba lớp khác nhau. Không assume converter .entity() tự bật provider-native mode. Dùng explicit response format/options đúng1.1.8; unsupported options/schema fail capability gate, không silently degrade sang prose parsing. Astra/Codex dùng viết code trong chat không phải model inference của sản phẩm.

Proposed caps để owner duyệt: overall30s; HTTP downstream connect1s/read3s mỗi call trong remaining deadline; provider connect2s/read15s; Qdrant/Neo4j mỗi retrieval2s; provider input<=8000 tokens, maxCompletionTokens<=4096 (kể cả reasoning), one attempt; candidate<=60, results<=3, combinations<=1000; benchmark monetary stop USD10 và tối đa USD0.10/request theo worst-case configured rates. Preflight worst-case + usage accounting; không nhận provider usage thiếu rồi báo cap đã enforce. Không claim những số này là SLA. Gateway timeout phải tương thích sau approval09.

Synthetic/public licensed catalog và curated knowledge trước; không gửi raw JWT/account email/phone/password/private image/full profile/free-text PII ra AI. Height/size chỉ local contextual guidance nếu cần; không gửi mặc định. Owner phải duyệt external data-use, consent/minimization/retention và content quyền chia sẻ trước người dùng thật. Không log full exception có thể chứa raw provider body: [Stylist handler hiện tại](../../../stylist-service/src/main/java/com/stylemind/stylist/errors/ApiExceptionHandler.java) log exception stack; cần typed sanitized client exceptions ở implementation, không claim hiện đã bảo vệ provider payload.

### 8.2 QA matrix và evidence levels

| Requirement / API / rule | Offline deterministic | HTTP consumer/controller | Repository/integration / live gate |
|---|---|---|---|
| POST sessions, JWT/roles | Canonical UUID/subject | Missing/expired/wrong signature/algorithm/issuer/aud/iat/role/UUID; wrong method/encoded route | Actual Identity-issued JWT+JWKS tại09, không test-token-only |
| GET messages/request/result ownership | Owner criterion tests | A session/request/rec IDs bị B thử đọc; admin self-only; missing same404 | Query owner join, no writes, batch history/children fixed count; Gateway E2E |
| POST request validation | null/omitted/empty/unknown/duplicate/range | before/after admission failures; invalid source session; body limit | Composite same-session FK + rollback; no network locks |
| R95 context/constraints | Precedence, unknown soft/hard, null profile | Identity/Wardrobe success/missing/403/timeout/malformed; no unsafe fallback | Real permitted sources, revoke race; catalog fixture không đủ |
| R97/101 budget/provenance | 0/exact/+1/null hard/negative/fractional/overflow; quantity1/source XOR/duplicates | fake IDs, real outside whitelist, wrong provider price, changed price/status/access | Final txn atomic all/zero, no empty rec, cross-owner refs denied |
| R98 knowledge/retrieval | recall set, approved rule IDs | Qdrant/Neo4j unavailable/wrong response/filter | Disposable stores then true source catalog/knowledge; draft never retrieved |
| R100/102 provider | Schema/length/grounding, injection | 429/5xx/timeout/refusal/truncated/unknown field; no auto retry | Paid real benchmark only after approval; result API fail not partial save |
| R99 events | duplicate/stale/delete/resurrection/epoch | Producer auth/malformed version | partial two-index failure, delayed embedding, crash redelivery/rebuild/live producer |
| Lifecycle/concurrency | Deadline boundaries | Duplicate POST fresh PROCESSING409, later new request no idempotency | two instances admission, deadline vs final commit, rollback and crash after commit |
| Privacy/regression | Injection fixtures/catalog/knowledge | Assert logs exclude credentials/content/tokens | Existing Stylist regression before optional disposable Docker; Identity/Gateway compatibility at09 |

Postman plan khi có HTTP implementation: stylist-service/postman/EP-07_AI_STYLIST.postman_collection.json, environment placeholders và run guide; Login A/B bằng API thật → create session A/B → valid/invalid context → history → request/result owner checks → source/price changes → refine → Wardrobe handoff khi producer có. Cleanup chỉ tài nguyên tạo bởi run trong disposable environment; không persistent cleanup. Structural JSON/schema check và HTTP runtime/Newman results báo riêng; Prompt01 chưa tạo collection giả cho API chưa tồn tại.

Evaluation **PROPOSED — NOT APPROVED**: 60 synthetic/licensed variants, 20 owned fixtures, 40 contexts (20dev/20holdout), 12 curated approved rules và20 adversarial/fault cases; ba repeated provider runs cùng seed settings nếu provider cho phép, không giả deterministic LLM. Giữ cùng dataset/candidate eligibility/context qua rule-only, retrieval+rules, retrieval+rules+LLM; graph ablation trên subset có reviewed ground truth.

Đo candidate recall@20; valid complete outfit rate; reviewer relevance/compatibility1–5; soft preference satisfaction; pairwise non-anchor diversity; reasons of fewer/no-result; p50/p95 latency và USD/valid result (tính cả failures). Hai human reviewers, rubric và disagreements recorded. Đề xuất zero saved hard-invalid cases, recall>=0.80 trên labeled subset, median human>=3/5, latency p95<=30s và mean cost<=USD0.10/valid result; tất cả threshold/dataset counts chưa approved, chưa có kết quả đo. Không tự giảm threshold sau chạy để gọi đạt.

## 9. Decision gate — toi da nam nhom

Năm nhóm này là review labels, không DEC/OPEN/DR mới. Chưa nhóm nào owner-approved chỉ vì memo/planning tồn tại. Approval của slice chỉ cần các nhóm nó phụ thuộc, không chờ mọi hạ tầng.

1. **API/context/security — api_context_security:** duyệt năm routes/self-only/CUSTOMER+ADMIN/input-null-array/response/error semantics; use existing Gateway /stylist prefix. Khuyến nghị synchronous bounded, không global live-account gate mới. Session/history có thể làm trước; generation route không trả fake success khi pipeline chưa có. Ảnh hưởng P03/P07/P09.
2. **Outfit rules và source scope — outfit_rules_and_source_scope:** duyệt templates, anchors, quantity1, budget per-outfit, current-price reads và numeric/candidate limits. Khuyến nghị P02 shop-first + synthetic mixed coverage; giữ target OWNED_WARDROBE requirement chờ producer. Nếu owner muốn release shop-only theo Outfit hiện hành, cần quyết định scope/model/backlog revision riêng, không tự bỏ R95/R97. Ảnh hưởng P02 và downstream.
3. **Schema/lifecycle — schema_lifecycle:** duyệt năm bảng R93 + hai R97 theo target, proposed states/locks/deadline/recovery; knowledge hai bảng riêng. JPA đã là current instruction, không cần phê duyệt lại lựa chọn đó; dependency/config adjustment phải nằm trong persistence task được giao. Khuyến nghị không thêm trace/price snapshot/idempotency attrs; công bố đúng model limitations. Ảnh hưởng P03/P07; migration chưa được tạo.
4. **AI/data/budget — ai_data_budget:** provider/model/synthetic-data policy, privacy/minimization, token/time/money caps, benchmark rubric. Khuyến nghị một adapter, benchmark Luna và optional mini comparator, chưa real customer data. Không paid calls trước owner cho phép; các caps mục8 vẫn proposal, không SLA. Ảnh hưởng P04/P06/P09.
5. **Producer/events/knowledge — producer_events_knowledge:** owner/team xác nhận owned-vs-saved-outfit discrepancy, required source capabilities, Commerce catalog events/version/snapshot, transport/durability, Qdrant/Neo4j deployment và knowledge reviewer. Khuyến nghị dùng existing APIs cho shop slice và labeled stubs cho missing target; PaymentConfirmed không thay catalog events. Không tự thêm broker/producer/portal/crawler. Ảnh hưởng P04/P05/P08/P09; live gate task liên quan giữ mở.

Sau group2 được duyệt, P02 làm pure engine/fixtures/meaningful negative tests, không DB/network/provider. Manifest ghi decision evidence sau lời duyệt thật; script không tự cấp approval. Xem kế hoạch hiện hành để giới hạn files từng prompt.
> **Owner decision 2026-10-29 (Prompt 08):** This memo is a historical proposal where it conflicts with the approved P03-A slice. The approved P03-A contract is recorded in [the handover report](EP-07_P03A_SESSION_HISTORY_REPORT.md): create session and read owner-scoped history only, with only `stylist_session` and `stylist_message` in V1. This does not approve request admission, context clients, the remainder of P03, or the rest of this memo’s proposed contracts.
