# EP-07 — Verification matrix và nghiệm thu

**Current owner decision (2026-10-29, Prompt 08):** exactly one women’s TOP plus one BOTTOM; no other product slots. P02 correction and P03-A session/history are separately authorized. P03-A verification is in [its handover](EP-07_P03A_SESSION_HISTORY_REPORT.md); the remaining matrix is not evidence of implemented or completed work.


Revision **2026-10-27**. Đây là kế hoạch kiểm chứng, **chưa có test nghiệp vụ Stylist nào được chạy hoặc tuyên bố PASS** trong lượt planning. [Flows](EP-07_FLOWS.md), [plan](EP-07_IMPLEMENTATION_PLAN.md), [contract](EP-07_PHASE_A_CONTRACT_MEMO.md) là đầu vào; expected behavior còn PROPOSED — NOT APPROVED cho tới owner duyệt.

## 1. Task → flow → tests → evidence

| Task | Flow | Meaningful tests / điều kiện fail | Evidence bắt buộc |
|---|---|---|---|
| R93 | F01/F02/F06 | Migration fresh/restart validate; PK/FK/XOR; source message same session; failed insert rollback | Disposable PostgreSQL + migration immutability |
| R94 | F01/F02/F07/F08 | Real HTTP validation/body limits/errors, owner A/B across session/message/request/result, GET/HEAD no writes, stable pagination | MVC + actual signed JWT + Postman runtime |
| R95 | F02/F06/F07 | Profile present/null/malformed/outage; permitted/foreign/revoked owned refs; no fake conversion from saved outfit | Actual clients to HTTP stubs, rồi true producer integration |
| R96 | F01/F02/F08 | Unknown code hard/soft, conflicting context, foreign source message, no-context/body overload, unavailable dependency | Unit + HTTP + real owner-scoped persistence |
| R97 | F06/F07 | Nonempty complete outfit, source XOR, duplicate/order, all/zero insert on failure, no stale finalization | PostgreSQL rollback/concurrency/HTTP |
| R98 | F03/F04 | Per-slot retrieval/Recall@K; inactive/missing/draft data; wrong dimensions; graph/vector outage; approved provenance | Actual Qdrant/Neo4j then real catalog/knowledge inputs |
| R99 | F10 | Duplicate/stale/conflicting version/delete/resurrection/partial stores/late embedding/restart/rebuild | Durable live producer+delivery, không snapshot-only PASS |
| R100 | F05 | Native schema capability, refusal/truncation/429/5xx/timeout; selected model facts and usage | Stub fault tests + separately approved real-model benchmark |
| R101 | F04/F06/F07 | Current sale eligibility/price/metadata, budget exact/+1/overflow, access revoke, final source outage | Fake-source deterministic tests rồi true Commerce/context source |
| R102 | F04/F05/F06 | Fake/outside-whitelist ID, alias duplicates, partial composition, injected URL/price, hard-rule violation, model unavailable | Zero invalid persisted outfits trong tested corpus; không absolute guarantee |

## 2. Ma trận nghiệp vụ chi tiết

| Nhóm | Happy cases | Negative/boundary/race | Kỳ vọng về side effects |
|---|---|---|---|
| Authentication | Actual Identity-issued JWT, CUSTOMER/ADMIN self-only | Missing/expired/future-iat/wrong key/alg/issuer/aud/UUID/jti/role; forged ownership field; wrong method/encoded route | Reject before private reads/writes; log safe correlation only |
| Session/history | Empty/new history, next page, equal timestamps | A reads B, cursor cross-session, huge/duplicate query, HEAD and invalid method | No cross-owner data, GET no writes/provider |
| Request | Valid structured context, source message same session | Unknown/duplicate fields, null vs omitted/[], fractional/exponent/negative/overflow amount, closed session | Validation/preflight failures no admission writes |
| Profile context | use_style_profile true+present/null, explicit opt-out | Null arrays/bad shape, unknown soft codes, service failure, A/B cache leakage | No false absence fallback, no profile write, no measurement upload |
| Owned context target | Permitted selected items with mapped metadata | Wrong wardrobe, revoked share, foreign item, no producer, arbitrary IDs | No silent catalog-only success, no conversion saved variant to owned |
| Composition | Complete templates, zero-cost genuine owned anchors if approved | Missing slot, duplicate item, incompatible anchors, no curated slot mapping, bounded combinations | No model call when no feasible outfits; no hard-rule relaxation |
| Budget | Zero/exact/soft/absent; whole VND | +1, negative/fractional/malformed upstream, sum overflow, optional items, saved shop item priced as owned | Recomputed server-side; invalid outfits never saved |
| Model grounding | Valid aliases/reasons/tips, fewer outputs | Real but non-whitelisted ID, unlisted outfit alias, duplicate alias, extra price/URL/name, mismatched item reasons | Reject structural/whitelist output as a whole; no repairing IDs |
| Prompt/data attacks | Trusted reviewed rules + plain customer context | User/product/rule says ignore constraints or leak another account; content with PII/control chars | Treat text as data, no tools/cross-owner retrieval; privacy policy enforced before real inputs |
| Final source checks | All variants still eligible and affordable | Price changed, category/brand inactive, missing variant, metadata changed, context revoke, source outage | No cached-price success; remove invalid complete outfit only under approved rule |
| Persistence | Commit full result aggregate and request state | Inject failure on second child, finalization after stale mark/deadline, DB disconnect at commit | No half result; distinguish committed admission from rolled-back final txn |
| Concurrency | Different sessions independent | Same-session simultaneous POST, two instances, GET during final commit, stale admission vs late provider completion | One allowed admission policy; terminal state not overwritten by old execution |
| Crash/retry | Read result after response loss if ID known | Crash after admission, failure recorder down, structured request ID lost, duplicate sequential POST | Honest coarse state/no exactly-once billing claim; no automatic re-billing |
| Read result | Current metadata/price, owner only | Revoke then read, inactive source, source outage, old result no current price | No private explanation/history leak; current total unavailable distinct from zero |
| Save/handoff | Explicit save via existing Wardrobe API, current shop variants | Duplicate click, inactive variant, another owner, mixed-source cast, forged creation_source | Wardrobe owns transaction; no automatic Cart/Order/Payment, AI label not verified linkage |
| Events | New upsert, valid newer reactivation | Duplicate/stale/delete/conflicting version, partial index apply, delayed embedding, rebuild vs live event | No stale resurrection, retain tombstone, repair with durable approved contract |

Không dùng Thread.sleep dài rồi đoán race xảy ra. Khi logic dựa lock/ordering, test dùng deterministic barriers và xác nhận DB wait/serialized outcome, response lẫn final DB state. Không log real tokens/OTP/provider input để debug.

## 3. Các cấp evidence và lệnh chạy khi có code

| Level | Được chứng minh | Không được suy ra |
|---|---|---|
| Planning validation | Source links/fingerprints, consistent phase metadata, Mermaid parse | API/business implementation hoặc owner approval |
| Pure fixtures | Engine rules, budget/source/slot algorithms với synthetic inputs | Catalog đầy đủ, permission thật hoặc fashion quality tổng quát |
| HTTP stubs | Serialization/auth-header scope/errors/timeouts của actual adapter | Producer chấp thuận contract, provider model thực |
| Disposable infrastructure | SQL/locks/atomicity, Qdrant/Neo4j integration | Persistent deployment hoặc live source correctness |
| Live services | Actual Identity/Commerce và đúng context producer/model | Cross-service atomicity hoặc production readiness |
| Gateway E2E | Real Login→session→request→result→save/read negatives qua client URL | Không thay quality evaluation/production operations |

Commands là runbook cho **bước triển khai sau**, không lệnh đã chạy lượt planning:

```powershell
node scripts/verify-stylist-planning.cjs
node scripts/verify-stylist-planning.cjs --phase P03
mvn -f stylist-service/pom.xml verify
```

Khi sửa Gateway/Feign integration, chạy regression Identity/Gateway và owning service liên quan theo change impact. Không chạy lại sáu service nhiều vòng cho một pure engine/docs slice. Lần fail giữ log redacted riêng; không overwrite rồi chỉ báo lần xanh. Skip/container missing là NOT EXECUTED/BLOCKED, không PASS.

Postman mỗi HTTP story: A/B fixtures qua Auth thật → tạo session → validate happy/error → history → generated result → owner isolation → current-source changes → refine → explicit save nếu dependency có. Collection importable, env placeholders, không hard-coded token/secret, main/fresh prerequisite rõ; collection JSON structure và Newman actual HTTP report riêng. Paid-provider scenarios không nằm default collection runner.

Docker smoke sau regression được giao: inventory trước; project name riêng; disposable DB user/volume và Qdrant collection/Neo4j database hoặc containers riêng; test data synthetic; restart/health/deadline cases; cleanup đúng labels/paths tạo bởi run; inventory sau và bảo toàn stack/volumes của team. Branch riêng không cô lập runtime. Không migration/seed vào persistent DB trong test.

## 4. Evaluation model — proposal để duyệt

Chốt dataset/version trước khi benchmark: ví dụ 60 shop variants, 20 synthetic owned items, 40 contexts (20 dev/20 holdout), 12 reviewed rules và 20 adversarial/fault cases. Các số này là proposal, không acceptance đã duyệt. Không lấy code-style unit fixtures làm fashion ground truth; slot mapping/rules phải có reviewer.

So cùng input/candidate eligibility qua: engine baseline; retrieval+engine; retrieval+engine+selected LLM. Đo graph contribution trên subset có reviewed facts. Mỗi model được run theo cùng budget/options/corpus; không đổi threshold sau khi nhìn result. Nếu làm repeated model calls, số lần và paid cap phải được duyệt, không default CI inference.

| Metric | Cách đo / diễn giải |
|---|---|
| Hard validity | Source membership/permissions/budget/complete slots của saved outputs; zero violations trên tested set là gate đề xuất |
| Retrieval recall@K | Ground-truth relevant candidates theo slot; no-result không được xem là catalog exhaustive |
| Relevance/compatibility | Hai human reviewers, rubric1–5, disagreements ghi lại; model tự chấm chỉ bổ sung |
| Diversity | Unique outfit sets và non-anchor variation; không tăng diversity bằng vi phạm hard rule |
| Grounded explanation | Reasons bám metadata/rule, không invented material/fit/discount/price/URL |
| Failure behavior | Safe refusal/no-result/unavailable phân biệt, zero invalid saves, không silent fallback |
| Latency/cost | p50/p95 end-to-end, USD/request và USD/valid result tính cả failures; không chỉ generation time |

Chưa có measured quality/cost/latency, chưa đặt production SLA. Dữ liệu khách thật chỉ sau data-use/consent approval. Model winner phải dựa cả hard-validity, human quality, latency và cost; không chọn vì tên model mới.

## 5. Báo cáo cuối mỗi slice

Ghi US/rows/rules và revision được duyệt; files changed; command/environment/counts; offline/stub/live levels; failures còn mở theo mức ảnh hưởng; DB/resources touched/cleanup; Postman structural/runtime; migration/source hashes; exact remaining decisions. P0/P1 security/data loss/invalid persisted hard rules chặn nghiệm thu. Missing producer/provider/knowledge phải được ghi PARTIAL/BLOCKED task liên quan, không che bằng tổng test xanh.

P02 correction 2026-10-29: [offline report](EP-07_P02_IMPLEMENTATION_REPORT.md). P03-A tests and evidence: [session/history handover](EP-07_P03A_SESSION_HISTORY_REPORT.md). All other matrix items remain planned until their own scope and verification are complete.
