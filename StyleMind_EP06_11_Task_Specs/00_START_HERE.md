# STYLEMIND EP06 — Index v4.13

**v4.13 — Share Outfit DONE_LOCAL, 2026-10-29 (backend/local).** Owner duyệt S1–S4, API cutover và V6; mời email giữ PRIVATE, Owner tự chọn FRIENDS. FRIENDS cho toàn bộ current friends; PRIVATE chỉ Owner; PUBLIC guest, OFF/ON cùng link. Email-grant/feed cũ trả 410; legacy rows giữ nguyên. V1–V5 không đổi.

Wardrobe regression **314/314, 33 classes, 0 failures/errors/skipped**. Bốn JAR thật + Mailpit/PostgreSQL disposable: invitation → Register/verify/login → accept → PRIVATE vẫn ẩn → Owner chọn FRIENDS → unfriend/refriend pass; native WS/ảnh regression pass. Newman **6 collections / 8 runs / 155 requests / 380 assertions**, gồm Share Outfit **32 requests / 55 assertions**, không failures. Commerce catalog và image storage dùng HTTP fixture trong lượt runtime này; không là Commerce/cloud end-to-end mới.

| Task | Hiện hành | Phạm vi |
| --- | --- | --- |
| 01/02/03 | DONE_LOCAL — backend | Friends DB/API/verified email/invitation/SMTP/tests |
| 04 | DONE_LOCAL | V6 + Entity/Repository, fresh/seeded upgrade/constraints/preflight |
| 05/06 | DONE_LOCAL — backend | Owner settings, friend read, public aliases, legacy 410, authorization/recheck, Swagger |
| 07 | DONE_LOCAL — backend | Unit/HTTP/PostgreSQL/concurrency/regression + actual Gateway/Newman |
| 08/09/10 | DONE_LOCAL — backend/cloud | Friends-based Chat; cloud B2 evidence giữ checkpoint riêng |
| 11 | PARTIAL | Còn browser/UI acceptance và phối hợp frontend |

[Plan](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md), [báo cáo](../docs/qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md), [Postman](../postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md). Task IDs/tên file gốc giữ để đối chiếu; Task04–07 nay thực hiện theo Share Outfit đã duyệt. Không dùng status/requirements toàn Wardrobe bên dưới làm hướng dẫn mới.

---
## Checkpoint trước implementation — giữ nguyên để truy vết

# STYLEMIND EP06 — Index v4.12

**2026-10-29: Task 04–07 PLANNING — contract/DDL NOT APPROVED.** Owner mở lại review Share Outfit; [contract](../docs/planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md) và [scope V6](../docs/planning/wardrobe/EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md) cần duyệt trước code/migration. 01/02/03/08/09/10 DONE_LOCAL backend/cloud; 11 còn browser/UI. Status ON_HOLD phía dưới là checkpoint trước planning, không là tiến độ hiện tại.

**2026-10-29: 01/02/03/08/09/10 DONE_LOCAL — backend/cloud; 04–07 ON_HOLD; 11 PARTIAL — browser/UI.** B2 thật đã pass và dọn ảnh test. [Cloud verification](../docs/qa/wardrobe/EP06_BACKBLAZE_VERIFICATION_2026-10-29.md), [setup](../docs/operations/wardrobe/EP06_BACKBLAZE_B2_SETUP.md). Không production/deploy readiness; Identity 15 baseline failures vẫn mở. Nội dung ngày 28 bên dưới là checkpoint trước cloud.

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser.

01/02/03/08/09 DONE_LOCAL; 04–07 ON_HOLD; 10/11 PARTIAL.

[Plan](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md), [báo cáo](../docs/qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md), [Postman](../postman/EP06_FRIEND_INVITATIONS_RUN_GUIDE.md). Không coi full Identity gate pass; 15 failures trùng baseline. v4.1 dưới đây chỉ là lịch sử.

---
## Index v4.1 — superseded, chỉ giữ traceability

Các trạng thái và dependency dưới đây là lịch sử, không dùng triển khai hai User Story hiện hành.

# STYLEMIND — EP-06: 11 TASK VÀ THỨ TỰ THỰC HIỆN v4.1

Cập nhật **2026-10-21** sau khi pull dev lên 418c421 theo log Owner cung cấp. **PIC: Tín.** Giữ nguyên 11 ID task để đối chiếu backlog; chữ A/B chỉ chia phần trong task, không tạo task thứ 12 hoặc giao lại việc của người khác.

Bản này thay thứ tự/prerequisite v4.0 và các notice 17/10 khi không còn khớp source mới. Functional Requirements và REFERENCE_EP06_Coding_Spec_v3_Original.md không bị sửa.

## Trạng thái thật

**Task 01 DONE review; Task 06 A có thể bắt đầu; Task 04/08 DEFERRED; các phần API/test còn lại chưa hoàn tất.** Source Outfit/OutfitShare đã có không đồng nghĩa task chia sẻ toàn Wardrobe/Chat của Tín đã Done. Kết quả test baseline cũ không chứng minh source vừa pull pass.

Chi tiết source, handoff, quyết định và evidence tại [kế hoạch hiện hành](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md).

## Bảng 11 task

| ID | Task/file | Trạng thái hiện tại | Điều kiện/thứ tự |
| --- | --- | --- | --- |
| 01 | [Chốt email sharing thay Friendship](TASK_01_friendship_requirement_conflict.md) | DONE_REVIEW | Đã chốt; giữ traceability, không làm lại review. |
| 02 | [People with Access API](TASK_02_manage_friends_api.md) | WAITING | Sau 04 + 05 A + guard Owner của 06 B. |
| 03 | [Tests Manage Friends](TASK_03_manage_friends_tests.md) | WAITING | Sau 02 + 05 A; không cần chờ send/history Chat. |
| 04 | [Migration visibility và WardrobeShare](TASK_04_wardrobe_share_migration.md) | DEFERRED | Chốt mô hình/key toàn Wardrobe, persistence và scope DDL theo source mới. |
| 05 | [Visibility/invite/public/revoke APIs](TASK_05_wardrobe_sharing_api.md) | WAITING | A: schema/identity/invitation/compatibility; B: public-link/content contracts. |
| 06 | [Policy và authorization](TASK_06_authorization_all_resources.md) | READY A; B WAITING | A: làm ngay policy/unit; B: tích hợp theo resource/guard khi đủ contract. |
| 07 | [Sharing/security integration tests](TASK_07_wardrobe_sharing_tests.md) | WAITING | Sau 05 A+B + 06 B trên resources cần test; 02 nếu test list. |
| 08 | [Migration Chat](TASK_08_chat_migrations.md) | DEFERRED | Sau mô hình Wardrobe/04 và scope DB; chưa phải đợi toàn 05/07. |
| 09 | [Chat session/list APIs](TASK_09_chat_session_api.md) | WAITING | Sau 08 + 05 core + verified identity + canChat của 06 B. |
| 10 | [Send/history APIs](TASK_10_chat_messages_api.md) | WAITING + DECISIONS | Sau 09 và chốt transport/types/history policy liên quan. |
| 11 | [Chat integration/security tests](TASK_11_chat_tests.md) | WAITING | Sau 09/10 + revoke/visibility của 05. |

## Làm trước

1. **Task 06 phần A:** policy logic + unit matrix cho rule đã duyệt; không DB/HTTP/repository, không đổi quyền các API hiện có.
2. Chuẩn bị handoff mô hình Wardrobe, current verified identity và Wardrobe invitation với đúng người phụ trách. Đây là bước chuẩn bị trong các task liên quan, chưa phải tích hợp Done.
3. Chuẩn bị cases/schema draft/lock-order cho Task 04/08/03/07/11; chỉ đưa migration hoặc API vào source khi đủ điều kiện.

## Làm sau theo dependency

- **Có mô hình/key/settings toàn Wardrobe và scope DDL:** 04 → 08; review history Outfit hiện tại, không phục hồi EP06 V1/V2 cũ.
- **Có 04 + verified identity + Wardrobe invitation + compatibility:** 05 phần A cùng guard liên quan của 06 B → 02 → 03.
- **Có public-link storage/lifecycle và mapping/read contracts:** 05 phần B + phần resource của 06 B → 07. Nội dung Outfit đã có source, cần tích hợp đúng root/permission, không làm CRUD lại.
- **Có 08 + current grant/visibility + verified identity + canChat guard:** 09 → 10 (sau khi chốt transport/types) → 11. Nhánh Chat có thể song song với hoàn thiện Task 07, không phải đợi toàn bộ Commerce/AI/Payment.
- **Toàn task Done** chỉ khi đủ mọi phần và acceptance. Xong 06 A hoặc 05 core chưa phải xong toàn task.

## Source mới: phần có, phần chưa đủ

| Phần | Đã quan sát | Việc còn thiếu cho EP06 |
| --- | --- | --- |
| Khánh — Outfit | Outfit/Item CRUD, share/public link từng Outfit; V1/V2 riêng | Đại diện toàn Wardrobe/key/settings, mapping Outfit và compatibility share cũ; chưa có bảng wardrobe |
| Quân — Identity | /internal/v1/accounts/batch-lookup: account_id/email/display_name/account_status | Verified-email contract; không suy verified từ ACTIVE |
| Vi — Notification | /internal/v1/outfit-share-invitations | Wardrobe invitation contract; không đổi tên/URL payload một cách ngầm định |
| Bình — Commerce | Catalog/variant lookup, Feign client đã có source | Chủ yếu dependency của Outfit; không chặn policy/unit hoặc toàn Chat |
| Persistence | Source hiện dùng JPA; AGENTS.md vẫn yêu cầu Spring JDBC | Chốt convention trước persistence integration; không convert code thành viên khác |

## Các rule đã chốt — không hỏi lại

Email một chiều; một My Wardrobe/account mặc định PRIVATE; share toàn bộ tủ gồm Outfit tạo sau; PRIVATE mời email → PROTECTED; về PRIVATE giữ grants/history và chặn Viewer; PUBLIC bật lại dùng link cũ; public-only VIEW, không Chat/edit; revoke giữ Chat; reshare cùng stable Customer tiếp tục session cũ.

## Các quyết định chỉ hỏi khi bắt đầu phần liên quan

- Đại diện/key/settings của toàn Wardrobe và compatibility/precedence với share từng Outfit đang có.
- Verified-email và Wardrobe invitation contracts; persistence convention.
- Public-token storage/create/copy/rotation; việc dùng lại link cũ đã chốt.
- REST/WebSocket, text/media, giới hạn nội dung; Owner đọc history khi PRIVATE/revoked.
- Provisioning khi tạo account, lỗi/retry/tài khoản cũ và email được Customer khác dùng lại.

Không tự thay quyết định bằng default trong reference v3 hoặc behavior của OutfitShare. Chỉ làm EP06 Tín; Git do Owner tự chạy. Mỗi lần giao rõ task và phần A/B, báo phần còn chờ, không tự mở task kế tiếp.
