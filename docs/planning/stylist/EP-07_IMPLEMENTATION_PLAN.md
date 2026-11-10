# EP-07 — Implementation plan

**Owner persistence decision, 2026-10-28:** Use Spring Data JPA/Hibernate for forthcoming Stylist persistence, superseding previous JDBC instructions. Retain Controller -> Service interface/impl -> Repository. Schema/API/P03 implementation are not approved by this framework decision. Do not refactor other services.

**Latest owner decision, 2026-10-29 (Prompt 08):** The active product rule is exactly one women’s TOP plus one women’s BOTTOM. P03-A alone is authorized for session creation/history reads and two-table Flyway V1. This overrides the older slot/API/schema gates below only for P02 correction and P03-A; see [P03-A handover](EP-07_P03A_SESSION_HISTORY_REPORT.md). P03 remains partial and US-07.01/EP-07 are not complete.


**Current owner decision (2026-10-29, Prompt 08):** Exactly one women’s TOP plus one BOTTOM per outfit; all other slots are excluded. P02 correction is implemented. P03-A session/history only is authorized; see [handover](EP-07_P03A_SESSION_HISTORY_REPORT.md). Other P03 work remains gated.


Revision **2026-10-27**. Trạng thái **PLANNING; business proposals NOT APPROVED**. Đọc [index](README.md), [review](EP-07_REVIEW_2026-10-27.md), [memo](EP-07_PHASE_A_CONTRACT_MEMO.md), [flows](EP-07_FLOWS.md), [technical design](EP-07_TECHNICAL_DESIGN.md), [manifest](EP-07_EXECUTION_MANIFEST.json).

**Owner supplement (2026-11-05, Accelerated Delivery 1):** P03-A and P03-B1 remain separately approved scoped slices. This approval authorizes the P03-B1 findings follow-up, a Stylist-owned Feign client for current public Commerce catalog reads, and a non-public, non-persistent internal request→fixture retrieval→P02→Commerce final-recheck use case tested only with disposable PostgreSQL/Qdrant and an HTTP contract fixture. It does not approve any new API/schema/migration, persistent database use, full-catalog mapping, owned context, provider/LLM, recommendation persistence, catalog events, or completion of P03/P04/P07 or either US.

Accelerated Delivery 1 implementation and final disposable verification are recorded in the [consolidated report](EP-07_ACCELERATED_DELIVERY_1_REPORT.md). Its pipeline remains internal and fixture-bounded; the complete P03/P04/P07 work and owner decisions in the report are still open.

## 1. Mốc làm việc và điều chỉnh

Nhánh `feature/ep-07-ai-stylist`, trước pull `e3f7767e619e72e151fcf66d9cb66da80c3cf099`. Đã fetch/pull `origin/dev` tại `a2a650747246d931ff9fa058a0d3027b263f3d15`, không conflict; HEAD `e6cbaee0ab289e32b1e1f564db9f20a425a5e607`. Không đổi sang nhánh codex cũ hoặc push/dev/main. Merge commit là kết quả pull được yêu cầu, không chứa planning staged.

Ba file planning staged được cất tạm trong stash riêng `2bec4b283b098739eee67494c2248010381b9361`, apply `--index` sau pull; giữ nguyên bản staged và giữ stash bảo toàn. Stash cũ không bị đổi. Nội dung sửa lượt này ở worktree để owner review. Hash/verification của bản ngày 20/10 là lịch sử, không phải xác minh mới.

Các điều chỉnh:

- Commerce có catalog, public active batch, internal lookup, migrations V1–V8; Payment có settlement/delivery. Không cần Payment để sinh gợi ý.
- Wardrobe có Outfit CRUD/share/public link; chưa có WARDROBE/WARDROBE_ITEM/WARDROBE_REQUIREMENT. Không gọi nó là scaffold, không coi outfit là đồ đã sở hữu.
- Identity có V4 weight_kg; DTO hiện có sáu scalar và ba arrays. Không suy vóc dáng/fit hoặc quyền gửi cân nặng ra AI.
- Owner decision 2026-10-28 selects JPA/Hibernate for Stylist; preserve other service implementations and approve schema separately.
- Gateway có /stylist/** StripPrefix=1. Dùng prefix hiện hữu, tránh thêm routing change chỉ để đổi URL.
- Đã kiểm lại workbook đường dẫn cũ sau phản hồi owner: R93–102 khớp cả 10 task. Không sửa status/file Excel hoặc đánh dấu task/Epic DONE.

## 2. Task traceability — verified read-only 2026-10-27

Đã đọc trực tiếp sheet Backlog ngày 27/10, đối chiếu đủ 10 task. Workbook SHA-256 `1f9657c9e6df2992572e86338cb4c985c21451ed4fcca08d338fb00c6b7cde73`. R là Excel row có header, không Task ID mới.

| Row / US | Task đã chép | Bước / evidence cần |
|---|---|---|
| R93 / US-07.01 | DB: Apply Stylist session/message/request, constraint and request-item migrations. | P03; năm bảng, scope duyệt + PostgreSQL disposable |
| R94 / US-07.01 | BE: Create styling request and conversation-history APIs with owner validation. | P03/P07/P09; HTTP + owner isolation |
| R95 / US-07.01 | BE: Read customer preferences and permitted wardrobe items from their owning services. | P03 consumer, P09 live; saved outfit không thay owned item |
| R96 / US-07.01 | BE: Test invalid context, conversation ownership and unavailable context services. | P02/P03/P09; negative + outage |
| R97 / US-07.02 | DB: Apply recommendation/item migrations with owned or shop-variant provenance. | P07; hai bảng + XOR/atomicity |
| R98 / US-07.02 | BE: Connect existing Qdrant/Neo4j retrieval to valid catalog and approved knowledge inputs. | P04/P05/P09; cả hai stores, không tự bỏ Neo4j |
| R99 / US-07.02 | BE: Update catalog projections from change events and handle duplicate or stale events. | P08/P09; catalog producer/version/delivery còn thiếu |
| R100 / US-07.02 | BE: Generate outfits with reasons and styling tips using the selected AI provider. | P06/P07/P09; real provider sau approval |
| R101 / US-07.02 | BE: Recheck shop variants, prices and hard constraints before saving recommendations. | P02/P07/P09; authoritative final lookup |
| R102 / US-07.02 | BE: Test fake variants, wrong prices, hard-budget violations and unavailable AI results. | P02/P06/P07/P10; zero invalid saves |

## 3. Kế hoạch mười prompt

P01 là lượt planning/harness này. P02 đã được owner giao triển khai ngày 28/10; P03–P10 chưa được cho phép code. Gate theo slice liên quan; không cần chốt broker/provider để làm engine.

| Bước | Kết quả và phạm vi | Phụ thuộc / quyết định | Verification và DoD |
|---|---|---|---|
| P01 — Reconcile + harness | Planning, Mermaid, source fingerprints, scoped AGENTS, validator | Owner giao ngày 27/10 | Readback/link/JSON/Mermaid/protection; không code nghiệp vụ |
| P02 — Pure engine | Context precedence, hard filter, budget, compose/rank/diversity, whitelist; fixtures cả hai source | Duyệt rules/templates; chưa cần DB/provider | Exact/+1/overflow, slots, duplicate, unknown code, XOR; offline evidence |
| P03 — Conversation + context | Security, session/history/request admission, Identity Feign; năm bảng R93 | API/lifecycle/schema approved; JPA instruction; owned chỉ stub khi producer thiếu | HTTP + PostgreSQL/locks/rollback/A-B; Postman; không expose fake generation success |
| P04 — Catalog retrieval | Qdrant/embedding, trusted import, per-slot filtering | Embedding/data/cost/deployment approved | Disposable Qdrant, model epoch/filters/Recall@K; sample import chưa R99 |
| P05 — Reviewed knowledge | Neo4j/curated rules, controlled import/provenance | Reviewer/graph contract; hai knowledge tables cần scope riêng | Draft/rejected/injection/contradiction/outage; không tự bỏ graph |
| P06 — One model adapter | Native structured output, strict parser, outfit aliases | Provider/model/privacy/budget approved; capability spike | HTTP faults rồi paid synthetic benchmark có cap; no retry/failover |
| P07 — Recommendation use case | Orchestrator, final source checks, atomic save/read; hai bảng R97 | P02–P06, approved schema/lifecycle | Zero invalid saves, all/zero, final price/status/access, bounded reads, Postman |
| P08 — Catalog projections | Dedup/version/tombstone/replay/rebuild Qdrant/Neo4j | Commerce catalog events/transport/durability confirmed | Duplicate/stale/delete/crash/partial stores; PaymentConfirmed không phải catalog event |
| P09 — Live integration | Gateway prefix, actual Identity/Commerce; save shop outfits; owned khi có đúng producer hoặc approved revision | Capability của team + gates liên quan | Real JWT/Gateway/price/access tests; stub/live tách riêng |
| P10 — Acceptance | R93–102 traceability, quality eval, Postman/Newman, isolated Docker, report | Workbook reverified + approved scope/delivery | Không che task thiếu bằng unit tests xanh; local ≠ production |

## 4. Allowed scope cho từng prompt khi được giao

| Bước | Allowed files đề xuất | Không tự lan sang |
|---|---|---|
| P02 | Stylist model/recommendation, service/recommendation/internal và tests/fixtures | POM, controllers, persistence, remote calls |
| P03 | Stylist controller/dto/session/request, interfaces/impl/internal, JPA repository, security, context client ports, approved migrations/tests/Postman/README | Identity/Wardrobe implementation hoặc table chưa duyệt |
| P04–P06 | Stylist retrieval/knowledge/AI adapters, scoped config/POM, tests và measured reports | AI service mới, auto paid calls, shared Compose |
| P07 | Stylist recommendation controllers/services/repos, approved migrations/tests/Postman | Wardrobe DB, auto Cart/Checkout, tự thêm trace/snapshot attrs |
| P08 | Stylist projection coordinator/adapters/tests, approved durability/config | Commerce producer/DB, mượn Payment inbox/outbox, tự chọn broker |
| P09 | Stylist integration config/tests; Gateway hoặc disposable Compose override chỉ khi được giao | Shared persistent resources, sửa nghiệp vụ của team khác |
| P10 | Meaningful test gaps, run guide và evidence/report | Workbook status, unrelated implementation, auto merge/push |

Mỗi prompt phải ghi: Pxx, US/rows, source HEAD, approved rule references, allowed paths, API/schema delta (hoặc none), happy/error flow, transaction/side effects, tests, Postman, protected resources và handover. Không để Agent tự chọn scope từ mọi file trong repo.

## 5. Thứ tự triển khai

1. Xử lý các khác biệt sớm: mixed-source target/backlog khác Wardrobe hiện hành; persistence framework resolved as JPA; catalog events/approved knowledge chưa có producer. Review nêu bằng chứng/phương án.
2. Duyệt offline rules P02 rồi code engine. Synthetic owned coverage không thay live producer.
3. Duyệt API/lifecycle/schema P03. Có thể hoàn thành session/history trước; chỉ mở generation route khi orchestration P07 thực sự hoạt động, không endpoint thành công giả.
4. P04/P05 chuẩn bị retrieval/knowledge với nguồn được duyệt. P06 spike/benchmark một provider; chọn model theo measured quality/cost.
5. P07 ghép pipeline/final checks. P08 chỉ live khi catalog events có contract; test ordering offline trước được nhưng chưa R99 DONE.
6. P09 cho customer chủ động save shop result bằng API Outfit hiện có. Save path này không giải quyết owned requirement. P10 nghiệm thu theo scope đã duyệt và evidence thật.

## 6. Gates và giới hạn model

Năm nhóm quyết định tại [memo mục 9](EP-07_PHASE_A_CONTRACT_MEMO.md#9-decision-gate--toi-da-nam-nhom), máy đọc ở manifest; chưa có owner approval được ghi cho chúng. P02 chỉ cần nhóm rules, không schema/provider/events. Không yêu cầu phê duyệt lại quyết định JPA mới nhất; cần quyết định scope migration và điều chỉnh scaffold trong task triển khai.

Model chưa có historical-price/deadline/provider-trace/idempotency columns. Không nhét chúng vào content/rule_text/JSON để lách schema. Budget kiểm được ở lần final source check; giá/quyền có thể đổi ngay sau đó. GET/Cart/Checkout phải xử lý current data theo contract.

Lượt planning chỉ kiểm tài liệu/harness/diagrams. Không application tests, Compose, migration hoặc paid API; evidence thực tế nằm trong review report.

P02 correction 2026-10-29: [offline report](EP-07_P02_IMPLEMENTATION_REPORT.md). Final combined verification and counts are in the [P03-A handover](EP-07_P03A_SESSION_HISTORY_REPORT.md). P03-A is partial evidence only; P03–P10 and both User Stories remain incomplete.

The owner’s Prompt 08 supersedes the earlier [P03 preparation memo](EP-07_P03_DECISION_MEMO.md) for P03-A only. The session/history slice is approved and recorded separately; this does not approve or complete the rest of P03.

## Owner supplement — Accelerated Delivery 2 (2026-11-05)

The owner-approved Prompt 2 authorizes a scoped implementation of generation, approved-model adapters, runtime retrieval/knowledge adapters, two recommendation tables and request lifecycle. It supersedes earlier planning-only statements only for that scope. See [Wave 2 contract](EP-07_WAVE_2_CONTRACT.md) and [implementation report](EP-07_WAVE_2_IMPLEMENTATION_REPORT.md).

- P05 `PARTIAL`: fixed-query Neo4j adapter and disposable tests exist; a reviewed production knowledge artifact is not available.
- P06 `PARTIAL`: exact approved model adapters and native strict-schema HTTP fixture tests exist; no live provider smoke was run.
- P07 `PARTIAL`: generation/read APIs, V3, lifecycle and final grounding code exist and the disposable suite passes. Live catalog policy, provider, Gateway/Newman and end-to-end live dependencies are unverified.
- P08 catalog event projection remains planned and unapproved. P09/P10 remain planned. R95 owned/profile producers and R99 Commerce event feed remain missing.

The final `mvn -B -ntp -f stylist-service/pom.xml verify` result was 152 tests, 0 failures, 0 errors and 0 skipped. The test evidence is local/disposable only; it is not semantic fashion-quality or production evidence.

## Current handover status — 2026-11-07

This current section supersedes earlier status snapshots without deleting their historical decision/evidence record. The selected current handover profile is Gemini Developer API with generation model gemini-3.5-flash-lite and embedding model gemini-embedding-001 at 1536 dimensions. Live calls remain explicitly opt-in and the approved cumulative profile is exhausted at 12 embeddings / 23 countTokens / 6 generations.

| Row | Stylist implementation/evidence | External API or producer | Remaining evidence/status |
|---|---|---|---|
| R93 | V1–V3 source; PostgreSQL disposable migrations/regression pass | None for current schema | Persistent database deployment not performed |
| R94 | Session, request intake, generation and read APIs; offline Newman and positive live Gateway pass | Real Identity-issued JWT and deployed Gateway route | Local slice verified; cross-service deployment not verified |
| R95 | No profile/owned-item consumer implemented | Identity self-only style-profile endpoint plus missing Wardrobe owned-item API | Incomplete; requires approved data-use/authorization contract and owned-item contract |
| R96 | Validation, ownership and dependency-fault coverage for implemented clients; A/B denial before provider | Identity/Wardrobe clients do not exist in the current Stylist contract | Partial; cannot claim source-context failure integration tests |
| R97 | V3 and atomic recommendation/item persistence pass on disposable PostgreSQL; live run saved one recommendation + 2 items | No external writer | Persistent migration and owned provenance remain unverified |
| R98 | Qdrant and Neo4j runtime adapters pass with synthetic disposable data | Real Commerce catalog/category mapping and approved Neo4j artifact | Partial; production data readiness is missing |
| R99 | No catalog event consumer or producer implementation is claimed | Commerce Product/Variant event contract and producer are absent | Incomplete; event identity/version/tombstone/delivery semantics must be supplied |
| R100 | Selected Gemini model passed one structured live generation through the pipeline/parser/whitelist | Gemini Developer API; synthetic data only | Local/live synthetic pass; human styling-quality evaluation is not done |
| R101 | Commerce final recheck code and fixture tests; live flow rechecked fixture price/status | Real Commerce catalog/variant APIs | Partial; live Commerce call remains unverified |
| R102 | Offline unit/integration/Newman cases cover constraints, invalid output, rollback, concurrency and replay | Provider/Commerce fixtures for negative scenarios | Live positive only; negative provider cases remain fixture evidence |

Overall: the shop-only implementation is locally verified and one positive Gemini Gateway flow passed. R95 and R99 remain incomplete; R96/R98/R101 are partial; R93/R97 are disposable-only for migration deployment. No workbook status, production deployment or EP-07 completion is implied. See [the final Stylist handover report](../../qa/stylist/AI_STYLIST_HANDOVER_REPORT.md).

The current planning guard and --phase P07 both exit 1 because of the eight pre-existing Commerce committed-range drift paths and six source-fingerprint drift entries listed in the handover report. No fingerprint/reviewed_head refresh was made; local link, JSON and Mermaid structure checks reported no error.

## Historical owner supplement — Gemini Free Tier adapters and verification (2026-11-06; superseded by the current handover status)

The owner’s latest attachment authorizes a zero-dollar Gemini Developer API local/test slice with exact models `gemini-2.5-flash` and `gemini-embedding-001` at 1536 dimensions. It supersedes the OpenAI-only local provider choice and paid OpenAI smoke plan for this slice only; keep OpenAI code/reports as history. Implement/test the same pipeline abstraction and keep the current public API, V1–V3, WOMEN TOP+BOTTOM rules, Commerce final checks and service scope unchanged. Gemini vectors must have isolated Qdrant alias and provider/model/dimension/configuration metadata. Use synthetic-only inputs, no full catalog import, retry or fallback.

External smoke and Gateway run are gated on the specific AI Studio project being proven Free Tier with the selected model/quota available, plus a rotated key in local environment. This workspace had no Gemini key configured and no project-tier evidence, so those steps are BLOCKED; no external request may be inferred from local HTTP fixture or disposable integration tests. See [the Gemini report](EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md) and [run guide](../../../stylist-service/postman/WAVE2_RUN_GUIDE.md). P03/P04/P05/P06/P07, R95/R99, P08 and EP-07 stay incomplete.

## Final current status — 2026-11-07

The newer status table above and [the final handover report](../../qa/stylist/AI_STYLIST_HANDOVER_REPORT.md) supersede all earlier status snapshots in this plan. The selected model is gemini-3.5-flash-lite with gemini-embedding-001 at 1536 dimensions. Full Stylist verification passed 191 tests with 0 failures, 0 errors and 1 live-only skip; offline Newman passed 10 requests / 20 assertions; one approved positive live Gateway run passed at cumulative meter totals 12/23/6. Commerce and JWKS were fixtures, the JWTs were fixture-signed, and all data sent to Gemini was synthetic. R95/R99 remain incomplete, R96/R98/R101 remain partial, and no persistent database migration, production deployment, real Commerce catalog acceptance, or styling-quality evaluation is claimed. The planning guard exits 1 for the documented Commerce committed-range/source-fingerprint drift; no reviewed_head or fingerprint was changed.
