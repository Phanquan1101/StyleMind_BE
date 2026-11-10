# AI Stylist — điểm bắt đầu triển khai

**Owner persistence decision, 2026-10-28:** Use Spring Data JPA/Hibernate for forthcoming Stylist persistence, superseding previous JDBC instructions. Retain Controller -> Service interface/impl -> Repository. Schema/API/P03 implementation are not approved by this framework decision. Do not refactor other services.

**Latest owner decision, 2026-10-29 (Prompt 08):** Product scope is women’s TOP and BOTTOM only, exactly one of each per outfit. This supersedes earlier slot approvals, including optional ONE_PIECE, SHOES and ACCESSORY in offline P02. P03-A session/history is approved and implemented as a partial slice; the exact API, migration and test evidence are in [P03-A handover](EP-07_P03A_SESSION_HISTORY_REPORT.md). P03, US-07.01 and EP-07 remain incomplete.


Historical 2026-10-28 rules and the 2026-10-29 earlier P02 approval are superseded by Prompt 08: Stylist supports women’s TOP+BOTTOM only, exactly one of each; no ONE_PIECE, SHOES or ACCESSORY.


Cập nhật **2026-10-27**, Asia/Bangkok. Nhánh `feature/ep-07-ai-stylist`. Đã pull `origin/dev` tại `a2a650747246d931ff9fa058a0d3027b263f3d15`; HEAD sau merge `e6cbaee0ab289e32b1e1f564db9f20a425a5e607`.

Khách nhập nhu cầu, nhận bộ phối dựa trên sản phẩm hợp lệ của shop, có lý do/mẹo phối, xem lại lịch sử và chủ động lưu outfit. Model mục tiêu còn đường dùng đồ đã sở hữu; chưa có producer tương ứng trong code hiện hành. Bộ này giữ rõ khoảng trống để owner quyết định, không tự sửa model hoặc bỏ task.

**Đây là kế hoạch và đề xuất để review, chưa phải business contract đã duyệt.** Yêu cầu ngày 27/10 cho phép pull, rà soát và sửa tài liệu/harness; chưa phê duyệt API, migration, provider, paid benchmark hoặc thay đổi scope EP-07.

| Thứ tự | Tài liệu | Mục đích |
|---|---|---|
| 1 | [Review hiện trạng](EP-07_REVIEW_2026-10-27.md) | Code thực tế, nguồn thiếu, khác biệt model |
| 2 | [Kế hoạch 10 bước](EP-07_IMPLEMENTATION_PLAN.md) | Phạm vi từng prompt, phụ thuộc, DoD |
| 3 | [Contract memo](EP-07_PHASE_A_CONTRACT_MEMO.md) | API/validation/ownership/budget/schema/lifecycle và quyết định |
| 4 | [Workflow và use flows](EP-07_FLOWS.md) | Sơ đồ tổng, sequence, state, lỗi, save/refine/events |
| 5 | [Thiết kế kỹ thuật](EP-07_TECHNICAL_DESIGN.md) | Package, thuật toán, model và dữ liệu AI |
| 6 | [Dependency contracts](EP-07_DEPENDENCY_CONTRACTS.md) | API có thật và contract còn thiếu |
| 7 | [Ma trận kiểm chứng](EP-07_VERIFICATION_MATRIX.md) | Nghiệp vụ, bảo mật, concurrency, chất lượng AI |
| 8 | [Execution manifest](EP-07_EXECUTION_MANIFEST.json) | Mốc nguồn, decisions, phase gates, allowed scope |

Quyết định owner → backlog theo công việc → target model theo ownership → migration theo physical schema → contract được duyệt. Source/test chứng minh hiện trạng. Không dùng ghi chú model, phân tích cũ hay timestamp làm approval. US/DEC/DR/OPEN giữ nguyên; F01/P02 chỉ là nhãn flow/bước trong bộ này.

Workbook đã được kiểm tra lại trực tiếp ngày **2026-10-27** tại `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx`, sheet `Backlog`. Hai US và 10 task vẫn ở R93–102, nội dung khớp bản chép. SHA-256 `1f9657c9e6df2992572e86338cb4c985c21451ed4fcca08d338fb00c6b7cde73`. Đọc read-only, không sửa hoặc export workbook.

Trước mỗi slice:

```powershell
git status --short --branch
node scripts/verify-stylist-planning.cjs
node scripts/verify-stylist-planning.cjs --phase P02
```

Harness kiểm tài liệu/nguồn, không gọi DB/provider/Docker. `--phase` kiểm decision prerequisites; exit 2 là thiếu quyết định, không phải lỗi ứng dụng. Script không thể chứng minh một approval là thật; người thực thi phải dẫn đúng lời owner. Prompt tiếp theo nêu rõ bước/allowed files/rules/tests/DoD; không giao “làm toàn bộ AI” một lần.

Hướng kỹ thuật đề xuất: **RAG có kiểm soát + engine ghép đồ + một LLM**. Backend quyết giá, nguồn, quyền, hard budget và bộ phối đầy đủ. LLM chọn trong outfit hợp lệ và diễn đạt theo evidence. Chất lượng phối đẹp cần người review riêng; JSON hợp lệ không chứng minh điều đó.

Ba bản SVG đã render để mở/zoom trực tiếp (đều **PROPOSED — NOT APPROVED**): [business workflow](diagrams/01-business-workflow.svg), [generation sequence](diagrams/02-generation-sequence.svg), [service structure](diagrams/03-service-structure.svg). Source Mermaid nằm trong flow/technical documents; render lại SVG khi source diagram thay đổi.

P02 product-scope correction: [offline report](EP-07_P02_IMPLEMENTATION_REPORT.md). Fixture mappings are `SYNTHETIC_TEST_ONLY`; P02 does not complete R96/R101/R102 or either User Story.

The owner’s 2026-10-29 Prompt 08 additionally authorizes P03-A, documented in [the handover](EP-07_P03A_SESSION_HISTORY_REPORT.md). This is a partial P03 delivery; no live ownership support, Commerce taxonomy, request admission, or recommendation path is approved or claimed.

The owner’s 2026-11-02 attachment authorizes **P04.1 only**: a disposable Qdrant fixture retrieval POC with deterministic fake 128-dimensional embeddings. It uses the six approved WOMEN SOURCE_SEED fixture rows and the `ao → TOP` / `quan → BOTTOM` mappings only inside that fixture. The Qdrant Testcontainers run and remaining gates are recorded in [the P04.1 report](EP-07_P04_1_RETRIEVAL_POC_REPORT.md). P04 remains partial; Neo4j, live Commerce, provider selection, R98 and R99 are not complete.

## Prompt 10 — P03-B request intake and dataset readiness

The owner approved **P03-B1** on 2026-11-04: shop/manual request intake and owner-scoped read-back, `RECEIVED` only, exactly two new V2 tables, disposable integration tests, and Postman handover. This does not include owned context, generation, provider/LLM, P04.3, or a persistent database migration. See [P03-B contract and approved scope](EP-07_P03B_CONTRACT_PROPOSAL.md), [P03-B1 implementation report](EP-07_P03B1_IMPLEMENTATION_REPORT.md), [dataset readiness guide](EP-07_DATASET_READINESS_GUIDE.md), and [synthetic CSV templates](templates/). P03 remains partial; US-07.01 and EP-07 remain incomplete. The Commerce drift warning remains unresolved.

## Owner supplement — Accelerated Delivery 2 (2026-11-05)

The owner’s “STYLEMIND — ACCELERATED DELIVERY 2” attachment supersedes prior planning-only gates only for the scoped Wave 2 implementation recorded in [the Wave 2 contract](EP-07_WAVE_2_CONTRACT.md). The scoped code adds generation and recommendation reads, V3 recommendation persistence, runtime OpenAI/Qdrant/Neo4j adapters, reviewed-policy catalog import, tests and Postman handover. It keeps women’s shop TOP+BOTTOM only, no owned context, no Identity profile consumer, no catalog event producer, and no changes to another service or shared Compose.

**Latest provider supplement (2026-11-06):** Owner-approved Gemini local/test adapters and USD 0 Free-Tier verification supersede the OpenAI-only smoke plan for this slice. The [Gemini verification report](EP-07_GEMINI_FREE_TIER_VERIFICATION_REPORT.md) records actual offline/disposable evidence and keeps live calls blocked until the specific project tier and quota are verified. The [Wave 2 contract](EP-07_WAVE_2_CONTRACT.md) retains the exact provider contract and status limits.

The original implementation report is [here](EP-07_WAVE_2_IMPLEMENTATION_REPORT.md). The 2026-11-06 follow-up records **165 Stylist tests passing**, Gateway/Newman fixture runtime passing through disposable resources, and the remaining planning/provider/live-data gates in [the Wave 2 completion report](EP-07_WAVE_2_COMPLETION_REPORT.md). V3 was exercised only on disposable PostgreSQL. No paid provider call, live Identity-issued JWT, live Commerce catalog, human-approved knowledge artifact, or persistent migration was used. P03/P04/P05/P06/P07, R95/R99, both User Stories and EP-07 remain partial/incomplete. Commerce source drift is unchanged.

## Current AI Stylist handover — 2026-11-07

The owner-approved positive Gemini Gateway run and current shop-only implementation status are summarized in [the final handover report](../../qa/stylist/AI_STYLIST_HANDOVER_REPORT.md). The report distinguishes local tests, offline Newman, the synthetic-data Gemini live run, and pending Identity/Wardrobe/Commerce data dependencies. R95/R99 and EP-07 remain incomplete; no persistent Stylist database migration is claimed.
