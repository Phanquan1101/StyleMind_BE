# EP-07 Wave 2 — Contract triển khai Generation và Recommendations

**Trạng thái:** phạm vi thực thi được chủ dự án duyệt trong attachment “STYLEMIND — ACCELERATED DELIVERY 2” ngày 2026-11-05. Contract này ghi lại đúng phạm vi đã duyệt và hiện trạng code; nó không đánh dấu P03, R95, R99 hoặc EP-07 hoàn tất.

## 1. Phạm vi và quy tắc giữ nguyên

Wave 2 bổ sung runtime embedding/retrieval, truy vấn knowledge đã review, generation API đồng bộ có deadline, recommendation read APIs, persistence và kiểm thử trong `stylist-service`. Giữ Spring MVC → Service interface → `impl` → Spring Data JPA Repository, Qdrant và Neo4j. Không sửa service khác, workbook, V1/V2, Compose chung, database persistent hoặc remote store dùng chung.

Outfit chỉ gồm sản phẩm shop dành cho WOMEN, đúng một `TOP` và một `BOTTOM`. Không có `ONE_PIECE`, giày, phụ kiện, đồ sở hữu hoặc dữ liệu saved wardrobe trong Wave 2. Không đọc Identity profile. USER message và request text là dữ liệu đầu vào không tin cậy, không phải system instruction.

Model đã được duyệt cho implementation: `text-embedding-3-small` với 1536 dimensions và `gpt-5.4-mini-2026-03-17` với native Structured Outputs. Việc duyệt model không có nghĩa là đã thực hiện paid call hoặc xác nhận account runtime có quyền dùng model.

## 2. API contract

Gateway public prefix là `/stylist`; Gateway bỏ prefix đó trước khi gọi Stylist. Các path trong cột service là path nhận tại `stylist-service`.

| Method | Gateway path | Service path | Hành vi |
|---|---|---|---|
| POST | `/stylist/api/v1/stylist/sessions/{sessionId}/requests` | `/api/v1/stylist/sessions/{sessionId}/requests` | Intake hiện có. Lưu request, có thể lưu USER message; trả 201, `RECEIVED`, `generation_started=false`. Không tự generate. |
| POST | `/stylist/api/v1/stylist/requests/{requestId}/generate` | `/api/v1/stylist/requests/{requestId}/generate` | Body phải chính xác `{}`. Chạy một bounded attempt hoặc phát lại kết quả terminal đã lưu. Trả 200 cho `SUCCEEDED`/`NO_RESULT`. |
| GET | `/stylist/api/v1/stylist/requests/{requestId}/recommendations` | `/api/v1/stylist/requests/{requestId}/recommendations` | Owner-scoped read; không gọi provider, generate hoặc ghi state. |
| GET | `/stylist/api/v1/stylist/recommendations/{recommendationId}` | `/api/v1/stylist/recommendations/{recommendationId}` | Owner-scoped detail; hydrate dữ liệu Commerce hiện tại; không gọi provider hoặc ghi state. |

Mọi request dùng JWT hợp lệ. CUSTOMER và ADMIN chỉ xem/tạo theo chính JWT `sub`; ADMIN không có quyền vượt owner. Missing và foreign resource trả cùng kiểu 404. Các response/error gắn user data dùng `Cache-Control: no-store`. GET không nhận body hoặc query parameter. Body JSON dùng parser strict, từ chối field thừa, duplicate keys, sai type và trailing JSON. Gateway runtime/Newman chưa được chạy trong lượt triển khai này.

### Generate thành công

```json
{
  "request_id": "<uuid>",
  "request_status": "SUCCEEDED",
  "recommendations": [
    {
      "recommendation_id": "<uuid>",
      "explanation": "Hai món phối theo màu trung tính trong dữ liệu sản phẩm.",
      "styling_tips": null,
      "generated_at": "<instant>",
      "items": [
        {"source_type":"SHOP_VARIANT","shop_variant_id":"<uuid>","slot_name":"TOP","selection_reason":"Phù hợp tiêu chí đã chọn.","display_order":1},
        {"source_type":"SHOP_VARIANT","shop_variant_id":"<uuid>","slot_name":"BOTTOM","selection_reason":"Hoàn thiện bộ phối theo yêu cầu.","display_order":2}
      ]
    }
  ],
  "limitations": []
}
```

`SUCCEEDED` có 1–3 outfit hoàn chỉnh. Nếu engine bounded không tìm được outfit, trả 200 `NO_RESULT`, `recommendations: []`, limitation `BOUNDED_SEARCH_NO_OUTFIT`; không gọi LLM để bịa sản phẩm. Không đảm bảo tính exhaustive trên toàn catalog.

GET danh sách trả `request_id`, trạng thái thật và các recommendation/item đã lưu. Với `RECEIVED`, `PROCESSING` hoặc `FAILED`, danh sách có thể rỗng. GET detail trả explanation/reasons đã lưu cùng thời điểm generate và metadata/giá Commerce ở thời điểm đọc. `price_semantics` là `CURRENT_COMMERCE_DATA_NOT_HISTORICAL`; không có price snapshot. Nếu variant hiện tại không còn hoạt động, giữ source ID và trả item unavailable; Commerce outage là dependency error, không giả inactive.

### Lỗi ổn định

| HTTP | Error code / trường hợp |
|---|---|
| 400 | `INVALID_REQUEST`, `INVALID_CONTEXT` |
| 401 / 403 | JWT không hợp lệ / role không được hỗ trợ |
| 404 | request hoặc recommendation không tồn tại hay không thuộc owner |
| 409 | `SESSION_CLOSED`, `GENERATION_IN_PROGRESS`, `GENERATION_ALREADY_ATTEMPTED` |
| 422 | `GENERATION_PROVIDER_REFUSED` |
| 502 | `GENERATION_PROVIDER_PROTOCOL_ERROR`, `DEPENDENCY_PROTOCOL_ERROR` |
| 503 | runtime chưa cấu hình hoặc dependency/provider unavailable |
| 504 | `GENERATION_PROVIDER_TIMEOUT` |

Problem Details không chứa upstream response body, prompt, token hay credential. Nếu request đã claim, lỗi được trả an toàn kèm `request_id` khi code có thể xác định ID.

## 3. Lifecycle và transaction

```text
RECEIVED -> PROCESSING -> SUCCEEDED | NO_RESULT | FAILED
```

Mỗi request chỉ được claim một lần. Hai lượt generate cạnh tranh trên cùng request chỉ có một claim thắng. Request khác trong cùng session vẫn độc lập. Terminal replay đọc lại dữ liệu đã lưu và không gọi model lần nữa. `PROCESSING` trả 409; `FAILED` không tự retry, khách tạo request mới nếu muốn thử lại.

Claim là transaction ngắn: kiểm tra owner/session và chuyển `RECEIVED` sang `PROCESSING`. Qdrant, embedding, Commerce, Neo4j và LLM chạy ngoài transaction. Finalize dùng transaction riêng để insert recommendation và toàn bộ items rồi chuyển status; aggregate chỉ commit đủ cả TOP và BOTTOM. Failure recording chạy riêng, có điều kiện từ `PROCESSING`, không ghi đè `SUCCEEDED`. Nếu DB không sẵn sàng để ghi failure, `PROCESSING` có thể tồn tại; không có startup reset, worker, scheduler hoặc lease tự phục hồi. Xem run guide về cách xử lý vận hành, không sửa trực tiếp DB khi chưa có quy trình/duyệt riêng.

Deadline tổng mặc định 30 giây, cấu hình qua `STYLEMIND_STYLIST_GENERATION_DEADLINE_MS` và giới hạn tối đa 30 giây. Chỉ caller kiểm tra deadline rồi finalize; remote worker không ghi DB. Timeout phía client không chứng minh provider ngừng tính phí.

## 4. V3 migration và persistence

V1/V2 không đổi. V3 chỉ:

1. Mở rộng `styling_request.request_status` sang `RECEIVED`, `PROCESSING`, `SUCCEEDED`, `NO_RESULT`, `FAILED`.
2. Tạo `wardrobe_recommendation` và `recommendation_item`, FK local trong database Stylist.
3. Ràng buộc source XOR, slot TOP/BOTTOM, reason có nội dung, display order 1/2, mỗi recommendation có tối đa một slot/order/variant; service bảo đảm đủ hai item trước commit.

`wardrobe_item_id` vẫn nullable logical remote reference theo schema, nhưng Wave 2 không tạo owned item. Không thêm giá snapshot, provider trace, retry/idempotency table, knowledge SQL tables, error/deadline columns hay cross-service FK. Hibernate `ddl-auto` không tạo hoặc sửa schema.

V3 được áp dụng và xác minh chỉ trên PostgreSQL Testcontainers disposable. Không có evidence migration lên `stylemind_stylist` persistent local hoặc shared database.

## 5. Retrieval, model và knowledge

- Runtime embedding dùng model đã duyệt và 1536 chiều, kiểm tra vector shape/giá trị hữu hạn. Qdrant runtime cosine collection tách biệt với P04.1 fixture 128 dimensions; kiểm tra cấu hình/manifest trước query. Không tự xóa/recreate collection khi mismatch.
- Catalog import là thao tác operator có profile/policy rõ ràng, có bounds/pagination/duplicate checks và không tự chạy lúc startup. Live import giữ gate đến khi có category/color policy đã review. Category ID mapping rõ ràng sang WOMEN TOP/BOTTOM; không suy slot từ tên category hoặc LLM.
- Chỉ text allowlist sản phẩm được phép đưa vào embedding; không đưa ID, URL ảnh, secret hay thông tin customer vào embedding text. ID/provenance giữ trong payload riêng.
- Neo4j dùng fixed parameterized Cypher; chỉ entry từ artifact có source/reviewer/approved status và chưa revoked. Graph khỏe nhưng không có match trả evidence rỗng; graph lỗi trả dependency error. Import chỉ qua profile operator và artifact review, không có Admin CRUD hoặc crawler. Fixture/synthetic test không phải fashion knowledge đã được người review phê duyệt.

## 6. Generation pipeline

1. Xác minh JWT owner, request, session và trạng thái; kiểm tra runtime readiness trước claim.
2. Claim một lần bằng transaction ngắn.
3. Load manual request context và USER message tối thiểu.
4. Embed query một lần; retrieve riêng TOP/BOTTOM từ Qdrant trong bounds.
5. Hydrate candidate từ Commerce; load reviewed facts từ Neo4j.
6. Chạy P02 deterministic engine: hard constraints, exact integer VND, composition, ranking/diversity.
7. Freeze outfit/item aliases cho đúng execution. Nếu không có outfit hoàn chỉnh, finalize `NO_RESULT` và không gọi LLM.
8. Gọi một adapter OpenAI với native strict JSON Schema; model chỉ chọn outfit alias đã cho và viết explanation/reasons/tips. Không tools, browsing, call retry hoặc fallback.
9. Strictly parse/refusal/truncation/schema/alias/reason checks.
10. Recheck current Commerce sau LLM; tính lại giá/hard budget/slot/category/demographic/status. Không còn outfit hợp lệ thì `NO_RESULT`.
11. Ghi aggregate và terminal status atomically.

LLM không chọn/đổi variant, slot, nguồn, giá, URL hoặc quantity. Không dùng chain-of-thought. Nội dung catalog/user/knowledge được xử lý như data, không phải instruction. Không tự lưu ASSISTANT message.

## 7. Provider, data và giới hạn smoke

Provider calls mặc định tắt nếu chưa có cấu hình. Môi trường hiện được kiểm tra không có `STYLEMIND_OPENAI_API_KEY`; chỉ kiểm tra có/không, không đọc giá trị secret. Không có approved live catalog policy hoặc knowledge artifact. Vì vậy không thực hiện paid provider smoke. Các test model dùng HTTP fixture nội bộ; không phải provider thật.

Prompt duyệt giới hạn smoke tổng 2 USD, query do nhóm soạn, catalog/knowledge synthetic, input tối đa 6000 token, output 2000 token, một LLM call mỗi attempt, không retry/fallback. Timeout/unknown delivery phải tính reservation; chỉ chạy khi conservative upper bound và journal local ignored được chứng minh. Lượt này không có API key và không thiết lập/chi tiêu budget journal; không được hiểu là smoke đã pass hay cap spend được triển khai như billing control.

Provider input tuyệt đối không gồm JWT, bearer, account/request/session ID, email, phone, password/OTP, số đo, full profile/history, share token hoặc URL/image fetch. Free text có thể chứa PII, không khẳng định regex có thể bảo vệ hoàn toàn. Không ghi raw prompt/response/provider body.

## 8. Trạng thái acceptance theo backlog

| Backlog | Phần đã triển khai/kiểm thử | Trạng thái còn lại |
|---|---|---|
| R93 DB migrations | V1/V2 regression; V3 recommendation schema trên DB disposable | Không áp dụng V3 lên DB persistent |
| R94 APIs/context | P03-A/P03-B1 giữ nguyên; generation/read APIs owner-scoped | Producer profile/owned context vẫn thiếu, R95 chưa xong |
| R95 Identity/Wardrobe context | Không đọc Identity/owned Wardrobe trong Wave 2 | Thiếu producer contract; chưa hoàn thành |
| R96 context/ownership tests | P03 tests chạy trong regression | R95 integration cases chưa thể PASS |
| R97 recommendation schema | V3 local disposable + atomic save/read tests | Live generation not verified |
| R98 Qdrant/Neo4j/catalog | Runtime adapters và container tests | Reviewed live taxonomy/catalog/knowledge chưa có |
| R99 catalog events | Không triển khai | NOT APPROVED, producer/event contract thiếu |
| R100 selected AI | Exact model adapter, native strict schema tested by local HTTP stub | Paid provider smoke NOT EXECUTED |
| R101 final price/rules check | Commerce HTTP fixture regression covers authoritative changed prices/status/category/demographic | Live Commerce not run |
| R102 invalid provider/data cases | Unit/HTTP fixture and full verify coverage | No live-provider quality/latency/cost evaluation |

P03, P04, P05, P06, P07, R95, R99, US-07.01, US-07.02 and EP-07 remain PARTIAL/INCOMPLETE. Prompt 2 does not approve P08 catalog event synchronization, owned context implementation or production deployment.

## Owner supplement — Gemini Developer API local/test (2026-11-06)

The owner approved the attached “STYLEMIND — GEMINI FREE-TIER ADAPTERS AND BOUNDED LIVE VERIFICATION” prompt for Stylist local/test only, with a strict **USD 0 budget**. This supplement supersedes the OpenAI-only model choice and the proposed paid OpenAI smoke for this local/test slice. It does not rewrite or invalidate the earlier OpenAI implementation/report evidence. It does not complete R95/R99, P08, or EP-07.

- Exact models remain `gemini-2.5-flash` for structured generation and `gemini-embedding-001` for retrieval embeddings at 1536 dimensions. Do not silently switch models.
- Provider selection is explicit with `STYLEMIND_AI_PROVIDER=gemini`; merely having a key never changes the selected provider. `STYLEMIND_GEMINI_LIVE_CALLS_ENABLED` defaults to `false` as an additional cost guard. A missing selected-provider key fails clearly. No retry, fallback, tools, grounding, batch endpoint or provider call from the default test suite is allowed.
- Gemini generation uses the current `generateContent` structured JSON format, an official `countTokens` preflight, 6000 counted input tokens and 2000 output tokens, with thinking disabled. Java still validates the output and frozen aliases. Google’s token-count operation covers the text/system instruction input; it does not count the response JSON schema or output/thought tokens, so the 6000 cap is the API-reported prompt count, not a total request-plus-schema budget.
- Embeddings use `RETRIEVAL_QUERY` and `RETRIEVAL_DOCUMENT`, output dimensionality 1536, an explicit preflight against the embedding model’s separate 2048-token limit, and local L2 normalization as required for reduced dimensions. Query and document metadata must match. **Wire correction 2026-11-07:** the Gemini Developer API request omits `autoTruncate`; the official Java SDK MLDV converter does not serialize it and rejects a nondefault `true` value. A `false` default does not prove provider-side truncation behavior. The existing token preflight rejects oversized input before embedding. See [Gemini wire-contract follow-up](EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md#wire-contract-review-and-offline-correction-2026-11-07).
- Gemini vectors use a distinct Qdrant alias namespace and manifest/payload identity for provider, model, dimensions and configuration revision. A same-dimension OpenAI vector is not compatible and must not be selected or overwritten.
- Synthetic-only input is allowed for this verification. Do not send live catalog/customer data or run the catalog importer against Commerce; do not reindex the 600-product catalog. Existing API, V1–V3 migrations, request lifecycle, WOMEN TOP+BOTTOM rules, price checks, service ownership and other services stay unchanged.

The selected models and public Free Tier documentation are not proof that this API key’s project is on Free Tier or has model quota. The 2026-11-06 workspace check found no `STYLEMIND_GEMINI_API_KEY` environment variable; the key pasted in chat was not read or used. Therefore all real provider/Gateway calls remain **BLOCKED** until the owner verifies the specific AI Studio project is Free Tier, confirms model access/quota, rotates the chat-exposed key and installs the replacement only in a local environment. Do not enable the live-call flag before that check. Adapter/HTTP-fixture and disposable Qdrant tests do not constitute a live smoke.

## Owner supplement — selected Gemini model and live Gateway evidence (2026-11-07)

The current approved handover run uses generation model gemini-3.5-flash-lite and embedding model gemini-embedding-001 at 1536 dimensions. This narrows the current verification profile and supersedes the earlier gemini-2.5-flash setting for this run; it does not remove the earlier adapter/report history or change public API, V1–V3, the request lifecycle, or TOP+BOTTOM rules.

The owner confirmed the same Google project remained Free Tier with billing disabled and authorized the final positive Gateway call plan. The safe meter advanced from 9/19/5 to 12/23/6 for embeddings/countTokens/generations. All planned calls returned HTTP 200. The one positive flow passed generation, Java parsing/whitelist, Commerce-fixture final recheck, PostgreSQL disposable persistence, list/detail read-back and terminal replay; foreign owner B was denied before provider calls. Commerce/JWKS remained fixtures and tokens were not Identity-issued. See the [AI Stylist handover report](../../qa/stylist/AI_STYLIST_HANDOVER_REPORT.md).

The live-call flag remains false by default. The approved cumulative profile is exhausted; do not issue more live calls under it. R95/R99, real catalog/knowledge acceptance, persistent migration/deployment, fashion-quality evaluation and EP-07 remain incomplete.
