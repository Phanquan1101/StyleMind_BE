# STYLEMIND EP06 — NOTICE v4.13, 2026-10-29

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

Plan: `docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md`; report: `docs/qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md`; run guide: `postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md`. Còn Task11 browser/frontend; Identity baseline 15 lỗi riêng. Không persistent DB/deploy/Git. Các notice/status NOT APPROVED/PLANNING bên dưới là lịch sử trước approval, không phải trạng thái hiện hành.

---
## Checkpoint trước implementation — giữ nguyên để truy vết

# STYLEMIND EP06 — NOTICE v4.12, 2026-10-29

**Task 04–07 đã mở lại PLANNING; contract/DDL mới NOT APPROVED, chưa implementation.** Owner cho soạn proposal Share Outfit: PRIVATE/FRIENDS/PUBLIC theo từng Outfit, reuse Friends hiện có. Scope V6 cụ thể, backfill dữ liệu và API cutover cần duyệt riêng trước schema/code. Bản v4.12 không tự thông qua S1–S4 hoặc đổi quyền cũ.

Contract: `docs/planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md`; DDL scope: `docs/planning/wardrobe/EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md`. Thứ tự sau approval: 04 → 05+06 → 07. 01/02/03/08/09/10 vẫn DONE_LOCAL backend/cloud; 11 còn browser/UI; Identity baseline 15 lỗi vẫn mở. Các trạng thái ON_HOLD ở checkpoint trước được thay bằng PLANNING, không thay business đã chốt.

## Checkpoint v4.11 — sau cloud, trước Share Outfit planning

**Hiện hành: 01/02/03/08/09/10 DONE_LOCAL trong phạm vi backend; 04–07 ON_HOLD; 11 PARTIAL vì browser/UI.** Owner chọn Backblaze B2. Wardrobe 294/294 pass; default fake và live B2 runtime mỗi mode 1 JUnit + Newman 123 requests/325 assertions/0 failures. Live SDK/image privacy/retry/WS/unfriend/refriend pass; dọn exact 8 keys/9 versions, không cleanup lỗi. IDE/Docker image wiring đã bổ sung; không deploy, Compose up, DB persistent hoặc đổi schema/Outfit. Identity 15 baseline failures vẫn mở.

Evidence: `docs/qa/wardrobe/EP06_BACKBLAZE_VERIFICATION_2026-10-29.md`; setup: `docs/operations/wardrobe/EP06_BACKBLAZE_B2_SETUP.md`; plan v4.11: `docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md`. Các notice v4.10 và baseline dưới đây là checkpoint trước cloud thật, không phải trạng thái hiện hành.

## Checkpoint v4.10 — 2026-10-28

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser.

**01/02/03/08/09 DONE_LOCAL; 04–07 ON_HOLD; 10/11 PARTIAL.** Task 02/03 Done trong phạm vi backend và kiểm thử local, không phải production/browser/cloud readiness. Không gửi thêm khi pending thành công; retry failed/unknown/expired sau 30 giây, link mới/7 ngày mới; mời chéo chỉ chấp nhận; unfriend/cutoff ngăn link cũ tái kích hoạt.

Plan: `docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md`. Báo cáo: `docs/qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md`. Postman: `postman/EP06_FRIEND_INVITATIONS_RUN_GUIDE.md`. Runtime: `scripts/ep06-chat-runtime/README.md`. Identity 15 baseline failures vẫn mở dù các dependency Friends đã pass.

Chỉ dùng PostgreSQL/SMTP test cô lập, S3 HTTP fake; không Git commit/push, Compose, persistent DB, real cloud. Frontend invitation page và browser/live cloud acceptance còn lại; Share Outfit chưa sửa. Phần v4.1 giữ nguyên làm lịch sử.

---
## Baseline v4.1 — superseded

# STYLEMIND — EP-06 CODING SPEC v4.1 — 11 TASKS

Cập nhật 2026-10-21. Đồng bộ từ 00_START_HERE và 11 task; giữ ID backlog, chia phần A/B trong từng task. Các đường dẫn trong index tính từ root repository. Reference v3 và Functional Requirements giữ nguyên.

# STYLEMIND — EP-06: 11 TASK VÀ THỨ TỰ THỰC HIỆN v4.1

Cập nhật **2026-10-21** sau khi pull dev lên 418c421 theo log Owner cung cấp. **PIC: Tín.** Giữ nguyên 11 ID task để đối chiếu backlog; chữ A/B chỉ chia phần trong task, không tạo task thứ 12 hoặc giao lại việc của người khác.

Bản này thay thứ tự/prerequisite v4.0 và các notice 17/10 khi không còn khớp source mới. Functional Requirements và REFERENCE_EP06_Coding_Spec_v3_Original.md không bị sửa.

## Trạng thái thật

**Task 01 DONE review; Task 06 A có thể bắt đầu; Task 04/08 DEFERRED; các phần API/test còn lại chưa hoàn tất.** Source Outfit/OutfitShare đã có không đồng nghĩa task chia sẻ toàn Wardrobe/Chat của Tín đã Done. Kết quả test baseline cũ không chứng minh source vừa pull pass.

Chi tiết source, handoff, quyết định và evidence tại kế hoạch hiện hành (`docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md`).

## Bảng 11 task

| ID | Task/file | Trạng thái hiện tại | Điều kiện/thứ tự |
| --- | --- | --- | --- |
| 01 | Chốt email sharing thay Friendship (`StyleMind_EP06_11_Task_Specs/TASK_01_friendship_requirement_conflict.md`) | DONE_REVIEW | Đã chốt; giữ traceability, không làm lại review. |
| 02 | People with Access API (`StyleMind_EP06_11_Task_Specs/TASK_02_manage_friends_api.md`) | WAITING | Sau 04 + 05 A + guard Owner của 06 B. |
| 03 | Tests Manage Friends (`StyleMind_EP06_11_Task_Specs/TASK_03_manage_friends_tests.md`) | WAITING | Sau 02 + 05 A; không cần chờ send/history Chat. |
| 04 | Migration visibility và WardrobeShare (`StyleMind_EP06_11_Task_Specs/TASK_04_wardrobe_share_migration.md`) | DEFERRED | Chốt mô hình/key toàn Wardrobe, persistence và scope DDL theo source mới. |
| 05 | Visibility/invite/public/revoke APIs (`StyleMind_EP06_11_Task_Specs/TASK_05_wardrobe_sharing_api.md`) | WAITING | A: schema/identity/invitation/compatibility; B: public-link/content contracts. |
| 06 | Policy và authorization (`StyleMind_EP06_11_Task_Specs/TASK_06_authorization_all_resources.md`) | READY A; B WAITING | A: làm ngay policy/unit; B: tích hợp theo resource/guard khi đủ contract. |
| 07 | Sharing/security integration tests (`StyleMind_EP06_11_Task_Specs/TASK_07_wardrobe_sharing_tests.md`) | WAITING | Sau 05 A+B + 06 B trên resources cần test; 02 nếu test list. |
| 08 | Migration Chat (`StyleMind_EP06_11_Task_Specs/TASK_08_chat_migrations.md`) | DEFERRED | Sau mô hình Wardrobe/04 và scope DB; chưa phải đợi toàn 05/07. |
| 09 | Chat session/list APIs (`StyleMind_EP06_11_Task_Specs/TASK_09_chat_session_api.md`) | WAITING | Sau 08 + 05 core + verified identity + canChat của 06 B. |
| 10 | Send/history APIs (`StyleMind_EP06_11_Task_Specs/TASK_10_chat_messages_api.md`) | WAITING + DECISIONS | Sau 09 và chốt transport/types/history policy liên quan. |
| 11 | Chat integration/security tests (`StyleMind_EP06_11_Task_Specs/TASK_11_chat_tests.md`) | WAITING | Sau 09/10 + revoke/visibility của 05. |

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

---

# STYLEMIND — EP-06 — TASK 01/11: Friendship migration: phân tích xung đột trước khi tạo schema

**User Story:** US-06.01 – Manage Friends
**Task gốc:** DB: Apply Friendship migration with unique customer pairs.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** DONE_REVIEW — quyết định email sharing đã chốt; không phải migration Done
**Giai đoạn:** Đã hoàn tất review
**Tiền đề / thứ tự:** Không mở lại lựa chọn Friendship. Chỉ đối chiếu tác động source mới khi cần; migration nằm ở Task 04.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Giữ quyết định A và cập nhật traceability với source vừa pull; không tạo code/schema.
- **Chỉ làm sau:** Task 04 chờ contract toàn Wardrobe; không làm lại Task 01 vì dependency mới.
- **Nguồn tiến độ:** docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md. Chỉ báo phần đã có evidence; không coi source của người khác là toàn task EP06 đã hoàn tất.

## BỐI CẢNH VÀ QUYẾT ĐỊNH ÁP DỤNG

- Tín chỉ phụ trách 11 task EP-06 trong wardrobe-community-service. Khánh: Wardrobe/Outfit; Bình: Commerce; Quân: Identity/setup; Vi: Notification. Không tự làm CRUD hoặc sửa contract/schema của thành viên khác.
- Owner đã chốt: email sharing một chiều thay Friendship; mỗi account có một My Wardrobe mặc định PRIVATE, có thể chưa có Outfit; chia sẻ toàn bộ tủ, gồm Outfit tạo sau.
- PRIVATE chỉ Owner xem, giữ grants/history nhưng chặn Viewer; mời email khi PRIVATE tự chuyển PROTECTED. PROTECTED cần đúng email hiện tại đã verified và grant ACTIVE. PUBLIC token chỉ cấp VIEW; Chat vẫn cần người được mời hợp lệ. Bật PUBLIC trở lại dùng link cũ.
- Revoke không xóa Chat; reshare cùng email và stable Customer tiếp tục session cũ. Không cho Customer khác dùng lại email tự kế thừa history; policy xử lý cụ thể còn TBD.
- Source sau pull có Outfit và OutfitShare theo từng Outfit, KHÔNG có bảng wardrobe. Giữ nguyên các API/schema này cho tới khi chốt tương thích với toàn-Wardrobe sharing. Không coi OutfitShare là WardrobeShare hoặc tự dựng Wardrobe nền thay Khánh.
- Identity đã có batch lookup nhưng chưa trả email_verified/email_verified_at; account_status không tự thay thế verified-email contract. Notification có thư mời Outfit, chưa phải Wardrobe invitation. Commerce catalog đã có source/client; không bắt Chat chờ toàn bộ Commerce.
- Giữ Controller → Service → Repository, Spring MVC và service ownership. Source mới đang dùng JPA, còn AGENTS.md yêu cầu Spring JDBC: ghi nhận cần chốt trước persistence integration, không tự chuyển framework hoặc sửa code của Khánh. Task 06 phần A là policy thuần, không phụ thuộc lựa chọn persistence.
- Không SQL/FK xuyên service, không khôi phục EP06 V1/V2 cũ lên V1/V2 Outfit hiện tại. Migration mới phải có scope duyệt, khớp history thực tế; không auto-baseline, drop data hoặc dùng placeholder để né dependency.

## QUYẾT ĐỊNH CÒN MỞ

Chỉ hỏi khi bắt đầu phần liên quan: đại diện/key/settings toàn Wardrobe và mapping Outfit; song song/thay thế và precedence với OutfitShare; verified-email contract; Wardrobe invitation contract; public-token storage/create/copy/rotation; REST/WebSocket và text/media; Owner đọc history khi PRIVATE/revoked; persistence convention. Không hỏi lại email sharing một chiều, PRIVATE mời email → PROTECTED hoặc dùng lại link cũ đã duyệt. Không sao chép giới hạn/lifecycle của OutfitShare làm default EP06.

## QUY TRÌNH THỰC HIỆN

1. Đọc AGENTS.md, governance, spec/task và source/tests/contracts hiện tại. Kiểm tra Git status do Owner cung cấp; mọi lệnh Git do Owner tự chạy, giữ các thay đổi khác.
2. Chỉ làm phần đã đủ điều kiện ghi trong task. Dependency chưa có thì chuẩn bị handoff/test cases rồi chuyển sang phần độc lập; không tự làm task người khác để mở đường.
3. Phân biệt rule đã duyệt, source quan sát được và đề xuất NOT APPROVED/TBD. Thiếu trusted identity không được tin email client; unit fixture không trở thành principal production.
4. Test theo phần thay đổi: unit; HTTP/controller; repository/PostgreSQL; isolation, negative/concurrency và regression khi áp dụng. Unit pass không chứng minh dependency/runtime integration; skipped/deferred không là pass.
5. Story có HTTP cần Postman JSON và run guide; structural validation tách execution thật. Không chạy Compose hoặc sửa DB persistent/secrets khi chưa được yêu cầu. Không log token/OTP/password hoặc private payload.

## MỤC TIÊU CỦA TASK

Xác định cách xử lý task gốc đòi bảng `Friendship(customer_a, customer_b)` trong khi nghiệp vụ duyệt là **email share một chiều**, có người nhận chưa đăng ký. Sản phẩm giao ở bước này là phân tích và ghi nhận quyết định A đã được owner duyệt, **không tạo Friendship migration**.

## PHẠM VI & YÊU CẦU CHI TIẾT

- Quét tất cả migration/entity/repository liên quan Friendship, FriendshipStatus, WardrobeShare, Wardrobe, Outfit.
- Xác định có bảng Friendship hiện hữu phục vụ chức năng khác không; không xóa nếu đang được dùng.
- Giải thích tại sao unique customer pairs không áp dụng cho lời mời email chưa đăng ký; đề xuất source of truth là `wardrobe_shares` UNIQUE `(wardrobe_id, recipient_email_normalized)` (Task 04).
- Hai phương án đã được trình bày: **A** đổi yêu cầu sang Email Sharing migration Task 04 — owner đã chọn; **B** giữ Friendship gốc — không được chọn.
- Ghi nhận quyết định A trong tài liệu task này và `docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md`. Decision note cũ còn trong stash là evidence lịch sử, không phải dependency phải khôi phục để mở lại quyết định.
- Ghi nhận owner duyệt A ngày 2026-10-16 và acceptance đã điều chỉnh: hoàn tất review/decision, không còn yêu cầu Friendship customer pairs. Trạng thái **BLOCKED – REQUIREMENTS MISMATCH** trước xác nhận được giữ trong lịch sử phân tích, không dùng làm trạng thái hiện tại và không gọi migration Task 04 đã hoàn tất.

## CÁC BƯỚC TRIỂN KHAI

1. Inspect schema/migration hiện tại (không mutate DB).
2. Đối chiếu business rules: một chiều; email recipient có thể chưa có Customer; revoke ≠ unfriend.
3. Giữ traceability A đã được duyệt; inventory mới phân biệt OutfitShare đang tồn tại với WardrobeShare chưa có.
4. Owner đã xác nhận A; không hỏi lại quyết định này. Kết thúc Task 01 theo acceptance đã điều chỉnh. Migration thuộc task tiếp theo với phạm vi được giao rõ, không gộp vào task review.

## API / DATABASE BOUNDARY

Không có API ở task này. Không tạo bảng, không đổi trạng thái friendship, không đụng auth.

## ACCEPTANCE CRITERIA (Definition of Done)

- Có báo cáo về tất cả tài nguyên Friendship hiện tại và kết luận ownership.
- Có document rõ xung đột gốc, phương án A đã được owner duyệt ngày 2026-10-16 và traceability sang Task 04.
- Không thêm bảng không được phê duyệt.
- Task 01 review/decision hoàn tất khi đã ghi nhận xác nhận A; không báo `Friendship migration DONE` hoặc `Task 04 migration DONE` vì không thực hiện migration trong task này.

## KHÔNG ĐƯỢC LÀM / OUT OF SCOPE

Không tự dựng FriendRequest/ACCEPTED/PENDING hoặc bảng Friendship 2 chiều; không gộp task này thành Task 04 mà không ghi nhận quyết định thay đổi.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.

---

# STYLEMIND — EP-06 — TASK 02/11: List shared people & relationship APIs

**User Story:** US-06.01 – Manage Friends
**Task gốc:** Backend: friend list/relationship APIs.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** WAITING_DEPENDENCIES — chưa có People with Access toàn Wardrobe
**Giai đoạn:** Sau schema và share/revoke core
**Tiền đề / thứ tự:** Task 04 tích hợp xong; Task 05 phần A share/revoke có trusted identity/invitation contract; Task 06 guard owner dùng được.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Chuẩn bị read-model, paging/isolation cases và handoff; không dựng endpoint bằng OutfitShare hiện tại.
- **Chỉ làm sau:** Làm API sau Task 05 phần A; dùng lại cùng revoke service, rồi chạy Task 03.
- **Nguồn tiến độ:** docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md. Chỉ báo phần đã có evidence; không coi source của người khác là toàn task EP06 đã hoàn tất.

## BỐI CẢNH VÀ QUYẾT ĐỊNH ÁP DỤNG

- Tín chỉ phụ trách 11 task EP-06 trong wardrobe-community-service. Khánh: Wardrobe/Outfit; Bình: Commerce; Quân: Identity/setup; Vi: Notification. Không tự làm CRUD hoặc sửa contract/schema của thành viên khác.
- Owner đã chốt: email sharing một chiều thay Friendship; mỗi account có một My Wardrobe mặc định PRIVATE, có thể chưa có Outfit; chia sẻ toàn bộ tủ, gồm Outfit tạo sau.
- PRIVATE chỉ Owner xem, giữ grants/history nhưng chặn Viewer; mời email khi PRIVATE tự chuyển PROTECTED. PROTECTED cần đúng email hiện tại đã verified và grant ACTIVE. PUBLIC token chỉ cấp VIEW; Chat vẫn cần người được mời hợp lệ. Bật PUBLIC trở lại dùng link cũ.
- Revoke không xóa Chat; reshare cùng email và stable Customer tiếp tục session cũ. Không cho Customer khác dùng lại email tự kế thừa history; policy xử lý cụ thể còn TBD.
- Source sau pull có Outfit và OutfitShare theo từng Outfit, KHÔNG có bảng wardrobe. Giữ nguyên các API/schema này cho tới khi chốt tương thích với toàn-Wardrobe sharing. Không coi OutfitShare là WardrobeShare hoặc tự dựng Wardrobe nền thay Khánh.
- Identity đã có batch lookup nhưng chưa trả email_verified/email_verified_at; account_status không tự thay thế verified-email contract. Notification có thư mời Outfit, chưa phải Wardrobe invitation. Commerce catalog đã có source/client; không bắt Chat chờ toàn bộ Commerce.
- Giữ Controller → Service → Repository, Spring MVC và service ownership. Source mới đang dùng JPA, còn AGENTS.md yêu cầu Spring JDBC: ghi nhận cần chốt trước persistence integration, không tự chuyển framework hoặc sửa code của Khánh. Task 06 phần A là policy thuần, không phụ thuộc lựa chọn persistence.
- Không SQL/FK xuyên service, không khôi phục EP06 V1/V2 cũ lên V1/V2 Outfit hiện tại. Migration mới phải có scope duyệt, khớp history thực tế; không auto-baseline, drop data hoặc dùng placeholder để né dependency.

## QUYẾT ĐỊNH CÒN MỞ

Chỉ hỏi khi bắt đầu phần liên quan: đại diện/key/settings toàn Wardrobe và mapping Outfit; song song/thay thế và precedence với OutfitShare; verified-email contract; Wardrobe invitation contract; public-token storage/create/copy/rotation; REST/WebSocket và text/media; Owner đọc history khi PRIVATE/revoked; persistence convention. Không hỏi lại email sharing một chiều, PRIVATE mời email → PROTECTED hoặc dùng lại link cũ đã duyệt. Không sao chép giới hạn/lifecycle của OutfitShare làm default EP06.

## QUY TRÌNH THỰC HIỆN

1. Đọc AGENTS.md, governance, spec/task và source/tests/contracts hiện tại. Kiểm tra Git status do Owner cung cấp; mọi lệnh Git do Owner tự chạy, giữ các thay đổi khác.
2. Chỉ làm phần đã đủ điều kiện ghi trong task. Dependency chưa có thì chuẩn bị handoff/test cases rồi chuyển sang phần độc lập; không tự làm task người khác để mở đường.
3. Phân biệt rule đã duyệt, source quan sát được và đề xuất NOT APPROVED/TBD. Thiếu trusted identity không được tin email client; unit fixture không trở thành principal production.
4. Test theo phần thay đổi: unit; HTTP/controller; repository/PostgreSQL; isolation, negative/concurrency và regression khi áp dụng. Unit pass không chứng minh dependency/runtime integration; skipped/deferred không là pass.
5. Story có HTTP cần Postman JSON và run guide; structural validation tách execution thật. Không chạy Compose hoặc sửa DB persistent/secrets khi chưa được yêu cầu. Không log token/OTP/password hoặc private payload.

## MỤC TIÊU CỦA TASK

Xây API Manage Friends dưới dạng **People with Access** của Owner trên Wardrobe; friend list lấy từ WardrobeShare, KHÔNG phải đồ thị friendship hai chiều.

## PHẠM VI & YÊU CẦU CHI TIẾT

- Owner-only GET danh sách share grants của Wardrobe mình; hỗ trợ paging theo convention, lọc ACTIVE và nếu cần REVOKED.
- Trả shareId, recipientEmail, status, permission=VIEW, sharedAt/revokedAt nếu có, tránh lộ profile nhạy cảm.
- Owner-only REMOVE/REVOKE một share **tái sử dụng cùng business service** của Task 05; không tạo logic revoke thứ hai.
- Re-share theo cùng email đi qua Share API của Task 05; không tạo Add Friend/Accept Friend endpoints.
- Lấy Wardrobe từ principal owner, không nhận ownerId tự do; tránh IDOR.
- Giữ tương thích route conventions/reponse envelope có sẵn.

## CÁC BƯỚC TRIỂN KHAI

1. Tìm `WardrobeShare` và grant service hiện hữu sau Task 04/05.
2. Tạo/hoàn thiện DTO read-model; repository query scoped by ownerWardrobeId.
3. Thêm GET shares và reuse DELETE shares route nếu đã được Task 05 làm.
4. Viết các test owner-list-only; no share; pagination/filter; authorization.
5. Kiểm tra Java build; không chạy migration mới.

## API / DATABASE BOUNDARY

Đề xuất (chỉ dùng nếu không trùng route hiện tại): `GET /api/v1/wardrobes/me/shares`; `DELETE /api/v1/wardrobes/me/shares/{shareId}` dùng chung Task 05. Không tạo `/friends/requests`.

## ACCEPTANCE CRITERIA (Definition of Done)

- Owner A chỉ thấy email share của Wardrobe A.
- Owner B không thể list/revoke các share của A (403/404 theo convention).
- Danh sách rỗng trả hợp lệ; pagination ổn định.
- Revoke cập nhật grant đúng một bản ghi; other grants không bị tác động.
- Không có API kết bạn hai chiều.

## KHÔNG ĐƯỢC LÀM / OUT OF SCOPE

Không thêm migration trùng Task 04; không tạo endpoint mới nếu Task 05 đã có; không thay đổi Chat schema.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.

---

# STYLEMIND — EP-06 — TASK 03/11: Tests: self-share, duplicate, unauthorized actions

**User Story:** US-06.01 – Manage Friends
**Task gốc:** Tests: self-friending, duplicates, unauthorized relationship changes.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** WAITING_DEPENDENCIES — chưa có API để chứng minh acceptance toàn task
**Giai đoạn:** Sau Task 02 và Task 05 phần A
**Tiền đề / thứ tự:** Task 04, Task 05 phần A và Task 02 hoạt động; contract Identity/Notification được thống nhất. Task 01 đã chốt, không mở lại.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Chuẩn bị case matrix; unit cho rule đã rõ có thể viết cùng Task 06 A nhưng không coi là toàn Task 03.
- **Chỉ làm sau:** HTTP/PostgreSQL tests cho API thật; không cần chờ Chat send/history để kiểm thử email grants.
- **Nguồn tiến độ:** docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md. Chỉ báo phần đã có evidence; không coi source của người khác là toàn task EP06 đã hoàn tất.

## BỐI CẢNH VÀ QUYẾT ĐỊNH ÁP DỤNG

- Tín chỉ phụ trách 11 task EP-06 trong wardrobe-community-service. Khánh: Wardrobe/Outfit; Bình: Commerce; Quân: Identity/setup; Vi: Notification. Không tự làm CRUD hoặc sửa contract/schema của thành viên khác.
- Owner đã chốt: email sharing một chiều thay Friendship; mỗi account có một My Wardrobe mặc định PRIVATE, có thể chưa có Outfit; chia sẻ toàn bộ tủ, gồm Outfit tạo sau.
- PRIVATE chỉ Owner xem, giữ grants/history nhưng chặn Viewer; mời email khi PRIVATE tự chuyển PROTECTED. PROTECTED cần đúng email hiện tại đã verified và grant ACTIVE. PUBLIC token chỉ cấp VIEW; Chat vẫn cần người được mời hợp lệ. Bật PUBLIC trở lại dùng link cũ.
- Revoke không xóa Chat; reshare cùng email và stable Customer tiếp tục session cũ. Không cho Customer khác dùng lại email tự kế thừa history; policy xử lý cụ thể còn TBD.
- Source sau pull có Outfit và OutfitShare theo từng Outfit, KHÔNG có bảng wardrobe. Giữ nguyên các API/schema này cho tới khi chốt tương thích với toàn-Wardrobe sharing. Không coi OutfitShare là WardrobeShare hoặc tự dựng Wardrobe nền thay Khánh.
- Identity đã có batch lookup nhưng chưa trả email_verified/email_verified_at; account_status không tự thay thế verified-email contract. Notification có thư mời Outfit, chưa phải Wardrobe invitation. Commerce catalog đã có source/client; không bắt Chat chờ toàn bộ Commerce.
- Giữ Controller → Service → Repository, Spring MVC và service ownership. Source mới đang dùng JPA, còn AGENTS.md yêu cầu Spring JDBC: ghi nhận cần chốt trước persistence integration, không tự chuyển framework hoặc sửa code của Khánh. Task 06 phần A là policy thuần, không phụ thuộc lựa chọn persistence.
- Không SQL/FK xuyên service, không khôi phục EP06 V1/V2 cũ lên V1/V2 Outfit hiện tại. Migration mới phải có scope duyệt, khớp history thực tế; không auto-baseline, drop data hoặc dùng placeholder để né dependency.

## QUYẾT ĐỊNH CÒN MỞ

Chỉ hỏi khi bắt đầu phần liên quan: đại diện/key/settings toàn Wardrobe và mapping Outfit; song song/thay thế và precedence với OutfitShare; verified-email contract; Wardrobe invitation contract; public-token storage/create/copy/rotation; REST/WebSocket và text/media; Owner đọc history khi PRIVATE/revoked; persistence convention. Không hỏi lại email sharing một chiều, PRIVATE mời email → PROTECTED hoặc dùng lại link cũ đã duyệt. Không sao chép giới hạn/lifecycle của OutfitShare làm default EP06.

## QUY TRÌNH THỰC HIỆN

1. Đọc AGENTS.md, governance, spec/task và source/tests/contracts hiện tại. Kiểm tra Git status do Owner cung cấp; mọi lệnh Git do Owner tự chạy, giữ các thay đổi khác.
2. Chỉ làm phần đã đủ điều kiện ghi trong task. Dependency chưa có thì chuẩn bị handoff/test cases rồi chuyển sang phần độc lập; không tự làm task người khác để mở đường.
3. Phân biệt rule đã duyệt, source quan sát được và đề xuất NOT APPROVED/TBD. Thiếu trusted identity không được tin email client; unit fixture không trở thành principal production.
4. Test theo phần thay đổi: unit; HTTP/controller; repository/PostgreSQL; isolation, negative/concurrency và regression khi áp dụng. Unit pass không chứng minh dependency/runtime integration; skipped/deferred không là pass.
5. Story có HTTP cần Postman JSON và run guide; structural validation tách execution thật. Không chạy Compose hoặc sửa DB persistent/secrets khi chưa được yêu cầu. Không log token/OTP/password hoặc private payload.

## MỤC TIÊU CỦA TASK

Viết và chạy tests chứng minh chính sách quản lý email-share một chiều: no self-share, no duplicates (cả concurrent), owner-only, revoke độc lập, re-share chuẩn.

## PHẠM VI & YÊU CẦU CHI TIẾT

- Self-share Owner→email Owner (kể cả khác hoa/thường) phải bị từ chối.
- UNIQUE `(wardrobe_id, normalized_email)` chặn duplicate kể cả request đồng thời.
- Không phát sinh share thứ hai khi share đang ACTIVE; reactivation từ REVOKED dùng record cũ.
- Non-owner không list/revoke/share thay Wardrobe khác.
- Revoke Mai không làm mất quyền Hoa. Nếu ChatSession/ChatMessage chưa triển khai (Task 08–10), chỉ kiểm tra revoke không có delete side effect và **defer** kiểm tra dữ liệu chat thật sang Task 11; không tự tạo bảng Chat để làm Task 03.
- Thử edge: email recipient chưa đăng ký, email malformed; thay đổi email account không được bypass email-verification.
- Dùng JUnit/Spring Boot Test/MockMvc/Testcontainers nếu repo đã có; không thêm tool vô cớ.

## CÁC BƯỚC TRIỂN KHAI

1. Đọc tests hiện tại và conventions; xác định DB thật PostgreSQL cần cho concurrency.
2. Viết unit tests cho validators/policies, MVC security tests cho endpoints.
3. Integration tests xác thực DB unique, transaction và revoke/re-share.
4. Run tests thật; report cases skipped vì thiếu Identity integration.
5. Không implement chức năng ngoài phạm vi để chỉ khiến test pass.

## API / DATABASE BOUNDARY

Không có API mới. Test những endpoint/contract Task 02 + Task 05.

## ACCEPTANCE CRITERIA (Definition of Done)

- Self-share denied và không tạo row.
- Case-insensitive duplicate và concurrent duplicate không tạo hai grants.
- Cross-owner read/write denied.
- Revoke/re-share giữ cùng ShareID, không tác động recipients khác.
- Test báo thật pass/fail/skip.

## KHÔNG ĐƯỢC LÀM / OUT OF SCOPE

Không tạo social graph để test self-friending; Task 01 đã đổi acceptance sang self-share/email grants. Không báo lại conflict đã giải quyết như blocker mới.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.

---

# STYLEMIND — EP-06 — TASK 04/11: PostgreSQL migration: WardrobeShare + Visibility

**User Story:** US-06.02 – Share Wardrobe
**Task gốc:** DB: WardrobeShare migration, visibility PRIVATE/PUBLIC/PROTECTED.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** DEFERRED — cần chốt mô hình toàn Wardrobe trên schema Outfit mới
**Giai đoạn:** Sau handoff mô hình/schema; trước API sharing và Chat DB
**Tiền đề / thứ tự:** Khánh/Owner thống nhất đại diện/key/settings Wardrobe, mapping Outfit, tương thích share cũ và persistence convention; duyệt scope migration theo history hiện có.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Đối chiếu V1/V2 Outfit, soạn gap/handoff và DDL đề xuất của Tín; chưa ghi migration deploy được.
- **Chỉ làm sau:** Khi đủ contract/scope: migration forward-only tiếp theo và PostgreSQL fresh/upgrade/concurrency; rồi Task 08/05.
- **Nguồn tiến độ:** docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md. Chỉ báo phần đã có evidence; không coi source của người khác là toàn task EP06 đã hoàn tất.

## BỐI CẢNH VÀ QUYẾT ĐỊNH ÁP DỤNG

- Tín chỉ phụ trách 11 task EP-06 trong wardrobe-community-service. Khánh: Wardrobe/Outfit; Bình: Commerce; Quân: Identity/setup; Vi: Notification. Không tự làm CRUD hoặc sửa contract/schema của thành viên khác.
- Owner đã chốt: email sharing một chiều thay Friendship; mỗi account có một My Wardrobe mặc định PRIVATE, có thể chưa có Outfit; chia sẻ toàn bộ tủ, gồm Outfit tạo sau.
- PRIVATE chỉ Owner xem, giữ grants/history nhưng chặn Viewer; mời email khi PRIVATE tự chuyển PROTECTED. PROTECTED cần đúng email hiện tại đã verified và grant ACTIVE. PUBLIC token chỉ cấp VIEW; Chat vẫn cần người được mời hợp lệ. Bật PUBLIC trở lại dùng link cũ.
- Revoke không xóa Chat; reshare cùng email và stable Customer tiếp tục session cũ. Không cho Customer khác dùng lại email tự kế thừa history; policy xử lý cụ thể còn TBD.
- Source sau pull có Outfit và OutfitShare theo từng Outfit, KHÔNG có bảng wardrobe. Giữ nguyên các API/schema này cho tới khi chốt tương thích với toàn-Wardrobe sharing. Không coi OutfitShare là WardrobeShare hoặc tự dựng Wardrobe nền thay Khánh.
- Identity đã có batch lookup nhưng chưa trả email_verified/email_verified_at; account_status không tự thay thế verified-email contract. Notification có thư mời Outfit, chưa phải Wardrobe invitation. Commerce catalog đã có source/client; không bắt Chat chờ toàn bộ Commerce.
- Giữ Controller → Service → Repository, Spring MVC và service ownership. Source mới đang dùng JPA, còn AGENTS.md yêu cầu Spring JDBC: ghi nhận cần chốt trước persistence integration, không tự chuyển framework hoặc sửa code của Khánh. Task 06 phần A là policy thuần, không phụ thuộc lựa chọn persistence.
- Không SQL/FK xuyên service, không khôi phục EP06 V1/V2 cũ lên V1/V2 Outfit hiện tại. Migration mới phải có scope duyệt, khớp history thực tế; không auto-baseline, drop data hoặc dùng placeholder để né dependency.

## QUYẾT ĐỊNH CÒN MỞ

Chỉ hỏi khi bắt đầu phần liên quan: đại diện/key/settings toàn Wardrobe và mapping Outfit; song song/thay thế và precedence với OutfitShare; verified-email contract; Wardrobe invitation contract; public-token storage/create/copy/rotation; REST/WebSocket và text/media; Owner đọc history khi PRIVATE/revoked; persistence convention. Không hỏi lại email sharing một chiều, PRIVATE mời email → PROTECTED hoặc dùng lại link cũ đã duyệt. Không sao chép giới hạn/lifecycle của OutfitShare làm default EP06.

## QUY TRÌNH THỰC HIỆN

1. Đọc AGENTS.md, governance, spec/task và source/tests/contracts hiện tại. Kiểm tra Git status do Owner cung cấp; mọi lệnh Git do Owner tự chạy, giữ các thay đổi khác.
2. Chỉ làm phần đã đủ điều kiện ghi trong task. Dependency chưa có thì chuẩn bị handoff/test cases rồi chuyển sang phần độc lập; không tự làm task người khác để mở đường.
3. Phân biệt rule đã duyệt, source quan sát được và đề xuất NOT APPROVED/TBD. Thiếu trusted identity không được tin email client; unit fixture không trở thành principal production.
4. Test theo phần thay đổi: unit; HTTP/controller; repository/PostgreSQL; isolation, negative/concurrency và regression khi áp dụng. Unit pass không chứng minh dependency/runtime integration; skipped/deferred không là pass.
5. Story có HTTP cần Postman JSON và run guide; structural validation tách execution thật. Không chạy Compose hoặc sửa DB persistent/secrets khi chưa được yêu cầu. Không log token/OTP/password hoặc private payload.

## MỤC TIÊU CỦA TASK

Bổ sung persistence cho visibility và email sharing TOÀN Wardrobe sau khi thống nhất mô hình với phần Outfit của Khánh. Task này không tự tạo Wardrobe nền thay Khánh.

## HIỆN TRẠNG SAU PULL VÀ PHẠM VI

- V1__create_outfit_tables.sql tạo outfit/outfit_item và ghi rõ My Wardrobe là tập Outfit của account; không có bảng wardrobe. V2__add_outfit_sharing.sql thêm outfit_share/public link cho từng Outfit.
- Baseline EP06 cũ từng tạo wardrobe/wardrobe_share và Chat chỉ chạy test cô lập, đã được Owner yêu cầu rút. Các kết quả cũ không làm task này Done trên source mới.
- Chốt đại diện toàn tủ, key và vị trí lưu visibility/grants; cách liên kết Outfit hiện tại/tương lai. Tên bảng/cột/FK cụ thể là TBD trước khi duyệt DDL, không mặc định parent wardrobe tồn tại.
- Phần của Tín cần grant ID, Wardrobe key đã thống nhất, normalized recipient email, nullable stable Customer ID, ACTIVE/REVOKED, created/updated/revoked timestamps; unique Wardrobe-key + normalized email; lookup/index phù hợp.
- Giữ default PRIVATE và uniqueness/lifecycle đã duyệt trên mô hình được thống nhất; normalization theo Identity trim/lowercase Locale.ROOT, không thêm quy tắc Gmail dot/plus. Recipient chưa có account vẫn hợp lệ.
- Không đổi OutfitShare, không xóa share/link cũ, không đặt visibility trên mỗi Outfit để giả làm chia sẻ toàn tủ. Nếu cần thay đổi mapping/schema Khánh, bàn giao và duyệt scope rõ trước khi sửa.
- Không public-token schema, Chat, provisioning Register hoặc business HTTP trong task này trừ scope riêng được duyệt.

## CÁC BƯỚC TRIỂN KHAI

1. Đối chiếu source/history V1/V2 hiện tại và contract toàn Wardrobe; xác định phần cần thêm, không dựa vào DDL EP06 đã rút.
2. Soạn DDL/data compatibility plan; chốt scope migration và persistence convention trước implementation.
3. Dùng version tiếp theo phù hợp history khi bắt đầu; không cố định V1/V2 hoặc sửa migrations Outfit. Không IF NOT EXISTS/auto-baseline để che schema lệch.
4. Kiểm chứng PostgreSQL mới và upgrade từ schema Outfit seeded: bảo toàn Outfit/Item/Share/public token và checksum baseline.
5. Test FK/unique/status, concurrent duplicate và migrate no-op; chạy regression hiện hành. Không DB persistent/Compose.

## API / DATABASE BOUNDARY

Chỉ schema và persistence tối thiểu trong scope được duyệt. Customer là logical reference; không cross-service FK hoặc SQL. Task 05 phụ trách API.

## ACCEPTANCE CRITERIA (Definition of Done)

- Có contract Wardrobe và migration scope được duyệt; đủ ba visibility, PRIVATE default trên mô hình thật.
- Grants toàn Wardrobe hỗ trợ unregistered email, ACTIVE/REVOKED và unique đúng key kể cả cạnh tranh.
- Không giả định OutfitShare là WardrobeShare; giữ dữ liệu và behavior public contract cũ theo phương án tương thích được duyệt.
- Fresh/upgrade/no-op/regression có kết quả thực chạy; forward-fix strategy rõ ràng.
- Chưa đủ contract/DDL approval thì trạng thái vẫn DEFERRED, không báo schema tích hợp Done.

## KHÔNG ĐƯỢC LÀM / OUT OF SCOPE

Không tự triển khai Wardrobe/Outfit CRUD, thay schema thuộc Khánh, phục hồi V1/V2 EP06 cũ, drop/clean/history rewrite hoặc làm API Task 05.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.

---

# STYLEMIND — EP-06 — TASK 05/11: APIs: visibility, email invite, public link, revoke

**User Story:** US-06.02 – Share Wardrobe
**Task gốc:** Backend: Wardrobe visibility/share/revoke APIs.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** WAITING_DEPENDENCIES — chưa có API chia sẻ toàn Wardrobe
**Giai đoạn:** Phần A core sau contracts; phần B public/content khi đủ điều kiện riêng
**Tiền đề / thứ tự:** Task 04 tích hợp; Task 06 A và guard owner; Identity verified-email contract, Notification Wardrobe invitation và phương án tương thích OutfitShare. Public link có contract/scope riêng.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Lập API/transaction/handoff và cases; tận dụng inventory client có sẵn nhưng không tự coi contract Outfit là contract Wardrobe.
- **Chỉ làm sau:** A: visibility/share/revoke khi schema + identity + invitation sẵn sàng; B: public-link/shared content khi storage/lifecycle/mapping được chốt. Chỉ Done khi A+B và authorization liên quan đủ.
- **Nguồn tiến độ:** docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md. Chỉ báo phần đã có evidence; không coi source của người khác là toàn task EP06 đã hoàn tất.

## BỐI CẢNH VÀ QUYẾT ĐỊNH ÁP DỤNG

- Tín chỉ phụ trách 11 task EP-06 trong wardrobe-community-service. Khánh: Wardrobe/Outfit; Bình: Commerce; Quân: Identity/setup; Vi: Notification. Không tự làm CRUD hoặc sửa contract/schema của thành viên khác.
- Owner đã chốt: email sharing một chiều thay Friendship; mỗi account có một My Wardrobe mặc định PRIVATE, có thể chưa có Outfit; chia sẻ toàn bộ tủ, gồm Outfit tạo sau.
- PRIVATE chỉ Owner xem, giữ grants/history nhưng chặn Viewer; mời email khi PRIVATE tự chuyển PROTECTED. PROTECTED cần đúng email hiện tại đã verified và grant ACTIVE. PUBLIC token chỉ cấp VIEW; Chat vẫn cần người được mời hợp lệ. Bật PUBLIC trở lại dùng link cũ.
- Revoke không xóa Chat; reshare cùng email và stable Customer tiếp tục session cũ. Không cho Customer khác dùng lại email tự kế thừa history; policy xử lý cụ thể còn TBD.
- Source sau pull có Outfit và OutfitShare theo từng Outfit, KHÔNG có bảng wardrobe. Giữ nguyên các API/schema này cho tới khi chốt tương thích với toàn-Wardrobe sharing. Không coi OutfitShare là WardrobeShare hoặc tự dựng Wardrobe nền thay Khánh.
- Identity đã có batch lookup nhưng chưa trả email_verified/email_verified_at; account_status không tự thay thế verified-email contract. Notification có thư mời Outfit, chưa phải Wardrobe invitation. Commerce catalog đã có source/client; không bắt Chat chờ toàn bộ Commerce.
- Giữ Controller → Service → Repository, Spring MVC và service ownership. Source mới đang dùng JPA, còn AGENTS.md yêu cầu Spring JDBC: ghi nhận cần chốt trước persistence integration, không tự chuyển framework hoặc sửa code của Khánh. Task 06 phần A là policy thuần, không phụ thuộc lựa chọn persistence.
- Không SQL/FK xuyên service, không khôi phục EP06 V1/V2 cũ lên V1/V2 Outfit hiện tại. Migration mới phải có scope duyệt, khớp history thực tế; không auto-baseline, drop data hoặc dùng placeholder để né dependency.

## QUYẾT ĐỊNH CÒN MỞ

Chỉ hỏi khi bắt đầu phần liên quan: đại diện/key/settings toàn Wardrobe và mapping Outfit; song song/thay thế và precedence với OutfitShare; verified-email contract; Wardrobe invitation contract; public-token storage/create/copy/rotation; REST/WebSocket và text/media; Owner đọc history khi PRIVATE/revoked; persistence convention. Không hỏi lại email sharing một chiều, PRIVATE mời email → PROTECTED hoặc dùng lại link cũ đã duyệt. Không sao chép giới hạn/lifecycle của OutfitShare làm default EP06.

## QUY TRÌNH THỰC HIỆN

1. Đọc AGENTS.md, governance, spec/task và source/tests/contracts hiện tại. Kiểm tra Git status do Owner cung cấp; mọi lệnh Git do Owner tự chạy, giữ các thay đổi khác.
2. Chỉ làm phần đã đủ điều kiện ghi trong task. Dependency chưa có thì chuẩn bị handoff/test cases rồi chuyển sang phần độc lập; không tự làm task người khác để mở đường.
3. Phân biệt rule đã duyệt, source quan sát được và đề xuất NOT APPROVED/TBD. Thiếu trusted identity không được tin email client; unit fixture không trở thành principal production.
4. Test theo phần thay đổi: unit; HTTP/controller; repository/PostgreSQL; isolation, negative/concurrency và regression khi áp dụng. Unit pass không chứng minh dependency/runtime integration; skipped/deferred không là pass.
5. Story có HTTP cần Postman JSON và run guide; structural validation tách execution thật. Không chạy Compose hoặc sửa DB persistent/secrets khi chưa được yêu cầu. Không log token/OTP/password hoặc private payload.

## MỤC TIÊU CỦA TASK

Xây các API Owner điều khiển Wardrobe Visibility và chia sẻ **toàn bộ Wardrobe** qua email/Public Link, sử dụng transaction đúng, không báo email đã delivered nếu mới queued.

## PHẦN A VÀ PHẦN B

- **A — Core:** visibility, email invite/create/reactivate/revoke và guard tương ứng; cần schema Task 04, trusted identity/verified email, Wardrobe invitation và compatibility. Khi A có thể bàn giao cho Task 02/03 và nhánh Chat, báo PART_A_DONE, không đánh dấu toàn task Done.
- **B — Public/shared content:** public-link storage/create/copy và lifecycle đúng quyết định Owner; read-only DTO với Outfit thật, mapping toàn Wardrobe và nested resource policy. Chỉ bắt đầu phần liên quan khi contract/storage scope được chốt; phối hợp Task 06 B.
- **Toàn Task 05 Done:** A+B và mọi acceptance bên dưới đạt, có evidence. Không mở API core thiếu authorization hoặc dùng thư mời Outfit/danh sách giả để báo thành công.

## PHẠM VI & YÊU CẦU CHI TIẾT

- Owner-only `PATCH visibility` với PRIVATE/PROTECTED/PUBLIC; change visibility ≠ revoke grants. Khi PRIVATE giữ ACTIVE grants và chat history. Mời email khi PRIVATE tự chuyển PROTECTED theo quyết định Owner; không suy rộng thành đổi PUBLIC sang PROTECTED.
- `POST shares` validate Owner/email/verified Owner email/no self-share; create ACTIVE, restore REVOKED cùng row, existing ACTIVE no duplicate (200/no-op hoặc 409 theo convention).
- Lời mời chứa direct Wardrobe URL, gửi qua Notification sau commit. Contract hiện có là /internal/v1/outfit-share-invitations với outfitName/outfitUrl, chưa đủ cho Wardrobe; phối hợp Vi để chốt contract. Chỉ báo SMTP accepted/failed/unknown đúng evidence; không tự thêm queue hoặc retry kết quả không chắc chắn.
- Người nhận **không cần Customer account ngay**; Identity verified-email lookup khi sử dụng invite (không tin query email do client cung cấp).
- `DELETE shares/{shareId}` revoke riêng, không xóa session/history, không ảnh hưởng share khác.
- Public token khó đoán ≥128-bit, chỉ VIEW khi Wardrobe PUBLIC; không chat/edit/private fields. Bật PUBLIC trở lại dùng cùng link cũ đã chốt. Storage/create/copy/rotate/disable cụ thể còn TBD; không sao chép behavior xóa token của OutfitShare hoặc tự chọn schema.
- Public read-only DTO tách owner DTO. API không phá share Outfit cũ.

## CÁC BƯỚC TRIỂN KHAI

1. Kiểm tra controller/DTO/service/notification client đã có, route collision và config Frontend URL.
2. Implement owner-only visibility + create/re-share/revoke theo Task 04 schema.
3. Tích hợp Wardrobe invitation qua contract được Vi/Owner chốt; không gửi SMTP trong Wardrobe hoặc sửa service khác thay chủ task.
4. Thiết kế public link minimal theo lựa chọn được user/nhóm duyệt, không tạo rotation tự phát.
5. Unit/MVC tests cho mỗi route, log không lộ email token/private payload.
6. Note endpoints chờ Task 06 enforce nested-resource authorization.

## API / DATABASE BOUNDARY

Routes gợi ý: `PATCH /api/v1/wardrobes/me/visibility` `{ "visibility":"PROTECTED" }`; `POST /api/v1/wardrobes/me/shares` `{ "email":"mai@example.com" }`; `DELETE /api/v1/wardrobes/me/shares/{shareId}`; `GET /api/v1/public/wardrobes/{token}` (hoặc hiện có); owner get/generate public-link nếu đã chốt lifecycle.

## ACCEPTANCE CRITERIA (Definition of Done)

- Owner thay Visibility; non-owner không thể.
- Email chưa đăng ký được lưu share và yêu cầu notification.
- Re-share REVOKED cập nhật record; ACTIVE duplicate không thêm row.
- PRIVATE không revoke; public-only không có Chat. Người được mời ACTIVE, đúng verified identity vẫn có Chat khi PROTECTED/PUBLIC theo Task 06.
- Email notification status đúng, token không bị log.
- Revoke riêng lẻ không phát sinh thao tác xóa ChatSession/ChatMessage; kiểm tra persistence thực tế của history sau Task 08–11.
- Tests chứng minh owner-only và lỗi Notification không làm sai trạng thái chia sẻ.

## KHÔNG ĐƯỢC LÀM / OUT OF SCOPE

Không tự tạo notification-service mới, không trust email client khi đọc nội dung PROTECTED, không để public route truy cập owner DTO.

## BLOCKER / CHỜ XÁC NHẬN

Đã chốt PRIVATE mời email → PROTECTED và bật PUBLIC lại dùng link cũ; không hỏi lại. Còn chờ mô hình Wardrobe, compatibility với share Outfit, verified-email contract, Wardrobe invitation và public-link storage/create/copy/rotation. Xong core không đồng nghĩa xong public/shared nội dung hoặc toàn Task 05.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.

---

# STYLEMIND — EP-06 — TASK 06/11: Authorization across Wardrobe / Outfit / Item / Image / Chat context

**User Story:** US-06.02 – Share Wardrobe
**Task gốc:** Backend: authorization for Wardrobe/Outfits/Items/Images/shared Chat context.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** READY_PART_A — policy/unit có thể làm trước; integration chưa sẵn sàng
**Giai đoạn:** A làm ngay; B tích hợp theo từng resource khi đủ dependency
**Tiền đề / thứ tự:** Phần A chỉ cần rule đã duyệt và facts server-trusted trong unit fixture. Phần B cần mô hình/Task 04, Task 05 tương ứng, verified-email và compatibility/resource contracts thật.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Thực hiện phần A policy thuần và unit matrix; không thêm HTTP, migration, JPA/JDBC repository hoặc gọi service giả production.
- **Chỉ làm sau:** B tích hợp guard owner/view/chat và resource coverage; phần canChat có thể hoàn tất trước mọi nested resource. Toàn Task 06 chỉ Done khi A+B đủ.
- **Nguồn tiến độ:** docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md. Chỉ báo phần đã có evidence; không coi source của người khác là toàn task EP06 đã hoàn tất.

## BỐI CẢNH VÀ QUYẾT ĐỊNH ÁP DỤNG

- Tín chỉ phụ trách 11 task EP-06 trong wardrobe-community-service. Khánh: Wardrobe/Outfit; Bình: Commerce; Quân: Identity/setup; Vi: Notification. Không tự làm CRUD hoặc sửa contract/schema của thành viên khác.
- Owner đã chốt: email sharing một chiều thay Friendship; mỗi account có một My Wardrobe mặc định PRIVATE, có thể chưa có Outfit; chia sẻ toàn bộ tủ, gồm Outfit tạo sau.
- PRIVATE chỉ Owner xem, giữ grants/history nhưng chặn Viewer; mời email khi PRIVATE tự chuyển PROTECTED. PROTECTED cần đúng email hiện tại đã verified và grant ACTIVE. PUBLIC token chỉ cấp VIEW; Chat vẫn cần người được mời hợp lệ. Bật PUBLIC trở lại dùng link cũ.
- Revoke không xóa Chat; reshare cùng email và stable Customer tiếp tục session cũ. Không cho Customer khác dùng lại email tự kế thừa history; policy xử lý cụ thể còn TBD.
- Source sau pull có Outfit và OutfitShare theo từng Outfit, KHÔNG có bảng wardrobe. Giữ nguyên các API/schema này cho tới khi chốt tương thích với toàn-Wardrobe sharing. Không coi OutfitShare là WardrobeShare hoặc tự dựng Wardrobe nền thay Khánh.
- Identity đã có batch lookup nhưng chưa trả email_verified/email_verified_at; account_status không tự thay thế verified-email contract. Notification có thư mời Outfit, chưa phải Wardrobe invitation. Commerce catalog đã có source/client; không bắt Chat chờ toàn bộ Commerce.
- Giữ Controller → Service → Repository, Spring MVC và service ownership. Source mới đang dùng JPA, còn AGENTS.md yêu cầu Spring JDBC: ghi nhận cần chốt trước persistence integration, không tự chuyển framework hoặc sửa code của Khánh. Task 06 phần A là policy thuần, không phụ thuộc lựa chọn persistence.
- Không SQL/FK xuyên service, không khôi phục EP06 V1/V2 cũ lên V1/V2 Outfit hiện tại. Migration mới phải có scope duyệt, khớp history thực tế; không auto-baseline, drop data hoặc dùng placeholder để né dependency.

## QUYẾT ĐỊNH CÒN MỞ

Chỉ hỏi khi bắt đầu phần liên quan: đại diện/key/settings toàn Wardrobe và mapping Outfit; song song/thay thế và precedence với OutfitShare; verified-email contract; Wardrobe invitation contract; public-token storage/create/copy/rotation; REST/WebSocket và text/media; Owner đọc history khi PRIVATE/revoked; persistence convention. Không hỏi lại email sharing một chiều, PRIVATE mời email → PROTECTED hoặc dùng lại link cũ đã duyệt. Không sao chép giới hạn/lifecycle của OutfitShare làm default EP06.

## QUY TRÌNH THỰC HIỆN

1. Đọc AGENTS.md, governance, spec/task và source/tests/contracts hiện tại. Kiểm tra Git status do Owner cung cấp; mọi lệnh Git do Owner tự chạy, giữ các thay đổi khác.
2. Chỉ làm phần đã đủ điều kiện ghi trong task. Dependency chưa có thì chuẩn bị handoff/test cases rồi chuyển sang phần độc lập; không tự làm task người khác để mở đường.
3. Phân biệt rule đã duyệt, source quan sát được và đề xuất NOT APPROVED/TBD. Thiếu trusted identity không được tin email client; unit fixture không trở thành principal production.
4. Test theo phần thay đổi: unit; HTTP/controller; repository/PostgreSQL; isolation, negative/concurrency và regression khi áp dụng. Unit pass không chứng minh dependency/runtime integration; skipped/deferred không là pass.
5. Story có HTTP cần Postman JSON và run guide; structural validation tách execution thật. Không chạy Compose hoặc sửa DB persistent/secrets khi chưa được yêu cầu. Không log token/OTP/password hoặc private payload.

## MỤC TIÊU CỦA TASK

Tách policy logic độc lập khỏi việc nạp facts và gắn quyền vào API. Giữ một task gốc 06 với hai phần A/B, không tạo thêm backlog task hay đổi số 11 task.

## PHẦN A — LÀM TRƯỚC: POLICY LOGIC VÀ UNIT TESTS

- Xây policy dùng facts server-trusted được truyền vào: Owner ID, actor ID, visibility, current verified normalized email, grant status/stable Customer, token validity và session participants khi cần. Đây là model nội bộ đề xuất, không phải HTTP/Identity contract mới.
- canViewWardrobe: Owner được xem; PRIVATE chặn người khác; PROTECTED cần signed-in, email verified khớp grant ACTIVE; PUBLIC token hợp lệ chỉ cấp VIEW. Người mời hợp lệ có thể xem theo authenticated shared access khi visibility cho phép.
- canEditWardrobe/Outfit: chỉ đúng Owner; người có link hoặc grant không sửa/xóa/AI-alter thay Owner.
- canChat: đúng Owner/Viewer của hội thoại, current verified identity/grant hợp lệ và PROTECTED/PUBLIC. Public-only/guest/outsider/wrong email/REVOKED/PRIVATE không được Chat; không lấy membership làm quyền vĩnh viễn.
- Unit matrix có Owner A/B, Viewer hợp lệ/sai email/unverified, ACTIVE/REVOKED, guest/public token valid/invalid, PRIVATE/PROTECTED/PUBLIC, participant đúng/sai và dữ liệu thiếu/không đáng tin. Không chỉ test happy path.
- Tách xem Wardrobe khỏi đọc suspended Chat history; không tự cho Owner đọc khi PRIVATE/revoked vì rule này TBD. Không tự xử lý Customer khác dùng lại email bằng cách cho kế thừa session.
- Không DB/HTTP/Feign, không cần bảng wardrobe hoặc lựa chọn JPA/JDBC. Fixture chỉ nằm trong test; production không có fallback principal/email/token giả.

## PHẦN B — LÀM SAU: FACT LOADING VÀ ENDPOINT INTEGRATION

- Bắt đầu từng resource khi có mô hình Wardrobe, schema/Task 04, Task 05 liên quan, verified-email contract, persistence convention và compatibility với OutfitShare được duyệt.
- Nạp visibility/grants/owner hiện tại mỗi request từ đúng nguồn, recheck dưới transaction/lock cho writes; dependency lỗi fail closed. Client không tự khai verification/owner/sender.
- Guard Wardrobe/share APIs khi Task 05 triển khai; canChat guard khi Task 09/10 bắt đầu. Không bắt Chat đợi mọi resource integration nếu canChat đã được kiểm chứng riêng.
- Áp dụng lên Wardrobe detail, Outfit list/detail, nested Item, private media và shared human-chat context theo endpoint coverage thực tế. Hiện có /api/v1/me/outfits, /api/v1/me/shared-outfits và /api/v1/public/outfits; không thay behavior của chúng trước khi duyệt compatibility.
- Phân biệt ảnh public Catalog với private Wardrobe media. DTO shared/public không có Height/Weight, Address, Cart, Order, Payment, AI history/feedback hoặc private Owner fields.
- Test HTTP trực tiếp bằng guessed outfitId/itemId/media URL, copied link, completed revoke và owner A/B isolation. Unit fixture không thay thế kiểm thử guard/repository/query thật.

## THỨ TỰ VÀ ĐIỂM DỪNG

1. Làm A và chạy unit gate; báo PART_A_DONE nếu đủ acceptance A, toàn Task 06 vẫn PARTIAL.
2. Khi schema + identity/contracts sẵn sàng, làm B theo Wardrobe/share/canChat/resources tương ứng, cùng các task API liên quan.
3. Resource hoặc business rule chưa có contract thì ghi WAITING cho đúng phần; không hoàn thiện CRUD của Khánh hoặc sửa Identity/Notification thay người phụ trách.

## API / DATABASE BOUNDARY

Phần A không thêm endpoint/schema và không đổi public security behavior. Phần B chỉ wiring trong scope được duyệt; không nhân bản policy ở nhiều controller, không đọc DB service khác hoặc đổi Stylist AI history.

## ACCEPTANCE CRITERIA (Definition of Done)

**A hoàn tất khi:** policy quyết định đúng các case đã duyệt; unit matrix có negative/isolation; không production I/O hoặc fixture bypass. Không kết luận HTTP authorization an toàn từ A.

**B hoàn tất khi:** current trusted facts được kiểm chứng; không bypass bằng ID/link/media; revoke/PRIVATE chặn Viewer requests tiếp theo và giữ history; shared DTO an toàn; có endpoint coverage + HTTP/repository/transaction tests và regression cho các đường bị ảnh hưởng.

**Toàn Task 06 Done khi A và B đều đạt.** canChat guard có thể được bàn giao riêng để Task 09 tiếp tục, nhưng không đánh dấu toàn task Done khi nested resources còn chờ.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.

---

# STYLEMIND — EP-06 — TASK 07/11: Tests: PRIVATE / PROTECTED / PUBLIC, revoke, notification

**User Story:** US-06.02 – Share Wardrobe
**Task gốc:** Tests: sharing modes, permissions, revoke, privacy and edge cases.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** WAITING_DEPENDENCIES — sharing/resource integration chưa đủ
**Giai đoạn:** Sau Task 05 A+B và Task 06 resource integration
**Tiền đề / thứ tự:** Task 04/05; Task 06 B trên resources cần kiểm thử; Task 02 nếu case list shares. Verified-email/invitation contracts và compatibility OutfitShare rõ.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Chuẩn bị matrix; unit policy thuộc Task 06 A, không tính là acceptance tích hợp của Task 07.
- **Chỉ làm sau:** HTTP/PostgreSQL/security/notification tests sau feature thật; không cần đợi Chat send/history để chứng minh canChat policy.
- **Nguồn tiến độ:** docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md. Chỉ báo phần đã có evidence; không coi source của người khác là toàn task EP06 đã hoàn tất.

## BỐI CẢNH VÀ QUYẾT ĐỊNH ÁP DỤNG

- Tín chỉ phụ trách 11 task EP-06 trong wardrobe-community-service. Khánh: Wardrobe/Outfit; Bình: Commerce; Quân: Identity/setup; Vi: Notification. Không tự làm CRUD hoặc sửa contract/schema của thành viên khác.
- Owner đã chốt: email sharing một chiều thay Friendship; mỗi account có một My Wardrobe mặc định PRIVATE, có thể chưa có Outfit; chia sẻ toàn bộ tủ, gồm Outfit tạo sau.
- PRIVATE chỉ Owner xem, giữ grants/history nhưng chặn Viewer; mời email khi PRIVATE tự chuyển PROTECTED. PROTECTED cần đúng email hiện tại đã verified và grant ACTIVE. PUBLIC token chỉ cấp VIEW; Chat vẫn cần người được mời hợp lệ. Bật PUBLIC trở lại dùng link cũ.
- Revoke không xóa Chat; reshare cùng email và stable Customer tiếp tục session cũ. Không cho Customer khác dùng lại email tự kế thừa history; policy xử lý cụ thể còn TBD.
- Source sau pull có Outfit và OutfitShare theo từng Outfit, KHÔNG có bảng wardrobe. Giữ nguyên các API/schema này cho tới khi chốt tương thích với toàn-Wardrobe sharing. Không coi OutfitShare là WardrobeShare hoặc tự dựng Wardrobe nền thay Khánh.
- Identity đã có batch lookup nhưng chưa trả email_verified/email_verified_at; account_status không tự thay thế verified-email contract. Notification có thư mời Outfit, chưa phải Wardrobe invitation. Commerce catalog đã có source/client; không bắt Chat chờ toàn bộ Commerce.
- Giữ Controller → Service → Repository, Spring MVC và service ownership. Source mới đang dùng JPA, còn AGENTS.md yêu cầu Spring JDBC: ghi nhận cần chốt trước persistence integration, không tự chuyển framework hoặc sửa code của Khánh. Task 06 phần A là policy thuần, không phụ thuộc lựa chọn persistence.
- Không SQL/FK xuyên service, không khôi phục EP06 V1/V2 cũ lên V1/V2 Outfit hiện tại. Migration mới phải có scope duyệt, khớp history thực tế; không auto-baseline, drop data hoặc dùng placeholder để né dependency.

## QUYẾT ĐỊNH CÒN MỞ

Chỉ hỏi khi bắt đầu phần liên quan: đại diện/key/settings toàn Wardrobe và mapping Outfit; song song/thay thế và precedence với OutfitShare; verified-email contract; Wardrobe invitation contract; public-token storage/create/copy/rotation; REST/WebSocket và text/media; Owner đọc history khi PRIVATE/revoked; persistence convention. Không hỏi lại email sharing một chiều, PRIVATE mời email → PROTECTED hoặc dùng lại link cũ đã duyệt. Không sao chép giới hạn/lifecycle của OutfitShare làm default EP06.

## QUY TRÌNH THỰC HIỆN

1. Đọc AGENTS.md, governance, spec/task và source/tests/contracts hiện tại. Kiểm tra Git status do Owner cung cấp; mọi lệnh Git do Owner tự chạy, giữ các thay đổi khác.
2. Chỉ làm phần đã đủ điều kiện ghi trong task. Dependency chưa có thì chuẩn bị handoff/test cases rồi chuyển sang phần độc lập; không tự làm task người khác để mở đường.
3. Phân biệt rule đã duyệt, source quan sát được và đề xuất NOT APPROVED/TBD. Thiếu trusted identity không được tin email client; unit fixture không trở thành principal production.
4. Test theo phần thay đổi: unit; HTTP/controller; repository/PostgreSQL; isolation, negative/concurrency và regression khi áp dụng. Unit pass không chứng minh dependency/runtime integration; skipped/deferred không là pass.
5. Story có HTTP cần Postman JSON và run guide; structural validation tách execution thật. Không chạy Compose hoặc sửa DB persistent/secrets khi chưa được yêu cầu. Không log token/OTP/password hoặc private payload.

## MỤC TIÊU CỦA TASK

Viết và chạy bộ tests riêng cho Share Wardrobe gồm state transitions, email invitation, public link, viewer-only, isolation, nested resources và Docker/Postgres integration nếu có.

## PHẠM VI & YÊU CẦU CHI TIẾT

- PRIVATE deny viewer/public link, Owner xem Wardrobe; transition PROTECTED→PRIVATE→PROTECTED giữ ACTIVE/REVOKED. Thêm case mời email khi PRIVATE tự chuyển PROTECTED và PUBLIC→PRIVATE→PUBLIC cho link cũ dùng lại; không mở suspended Chat history chưa duyệt.
- PROTECTED correct verified invitee allowed, wrong email/copied link denied, unregistered invitee login only after verify; revoked before registration denied.
- PUBLIC valid token allows Guest VIEW but denies quyền canChat; ACTIVE invitee có canChat, revoked chỉ public VIEW, invalid/disabled token denied. **Không cần** send/history Chat API chưa có (Task 09–10); test Chat thực tế ở Task 11.
- No edit/delete/AI alter permissions for Viewer; direct Outfit/Item/private image secure; shared DTO redaction.
- Concurrent visibility/revoke/access behavior phù hợp transaction design; snapshot do not bypass completed revoke.
- Email invitation accepted/failed/unknown truthful; kiểm thử dependency lỗi/timeout và chỉ retry theo contract được duyệt, không tự retry unknown hoặc giả định có broker.
- PostgreSQL migrations persist across container restart if environment hỗ trợ.

## CÁC BƯỚC TRIỂN KHAI

1. Chọn unit/MVC/PostgreSQL integration tools đang dùng.
2. Viết matrix tests PRIVATE/PROTECTED/PUBLIC/ACTIVE/REVOKED/Guest.
3. Mock Identity verified email, Notification delivery boundaries.
4. Chạy tests và Docker smoke khi được phép; capture commands/results.
5. Report gaps cụ thể; link reuse đã chốt phải test, chỉ storage/create/copy/rotation và dependency chưa chốt mới là TBD.

## API / DATABASE BOUNDARY

Không thêm feature API; chỉ test Task 04–06 contracts và fix bug trong phạm vi.

## ACCEPTANCE CRITERIA (Definition of Done)

- All matrix cases asserted and reported.
- No unauthorized direct nested data or Chat.
- Revoke cập nhật trạng thái grant, không xóa grant row; kiểm thử dữ liệu Chat History thật sau khi Chat schema/API hoàn thiện ở Task 11.
- No false success for queued email.
- DB stays persistent after restart if run Docker integration.
- Actual test evidence recorded.

## KHÔNG ĐƯỢC LÀM / OUT OF SCOPE

Không tự chọn SMTP credentials hoặc thêm RabbitMQ/Kafka chỉ để cho test pass.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.

---

# STYLEMIND — EP-06 — TASK 08/11: PostgreSQL migration: ChatSession, ChatParticipant, ChatMessage

**User Story:** US-06.03 – Chat with Friends
**Task gốc:** DB: ChatSession, ChatParticipant, ChatMessage migrations.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** DEFERRED — Chat baseline cũ không phải migration hiện hành
**Giai đoạn:** Sau Task 04/mô hình Wardrobe và scope DB được duyệt
**Tiền đề / thứ tự:** Wardrobe key/model cùng Task 04 đã tích hợp; duyệt Chat DDL tương thích baseline Outfit và migration history. Task 06 A có thể làm trước, không cần đợi toàn bộ sharing API để làm DB.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Review thiết kế uniqueness/sequence/idempotency và DDL draft; không ghi FK tới parent Wardrobe chưa có.
- **Chỉ làm sau:** Migration version tiếp theo + PostgreSQL integration khi contract/schema rõ; không tạo bảng AI hoặc service Chat mới.
- **Nguồn tiến độ:** docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md. Chỉ báo phần đã có evidence; không coi source của người khác là toàn task EP06 đã hoàn tất.

## BỐI CẢNH VÀ QUYẾT ĐỊNH ÁP DỤNG

- Tín chỉ phụ trách 11 task EP-06 trong wardrobe-community-service. Khánh: Wardrobe/Outfit; Bình: Commerce; Quân: Identity/setup; Vi: Notification. Không tự làm CRUD hoặc sửa contract/schema của thành viên khác.
- Owner đã chốt: email sharing một chiều thay Friendship; mỗi account có một My Wardrobe mặc định PRIVATE, có thể chưa có Outfit; chia sẻ toàn bộ tủ, gồm Outfit tạo sau.
- PRIVATE chỉ Owner xem, giữ grants/history nhưng chặn Viewer; mời email khi PRIVATE tự chuyển PROTECTED. PROTECTED cần đúng email hiện tại đã verified và grant ACTIVE. PUBLIC token chỉ cấp VIEW; Chat vẫn cần người được mời hợp lệ. Bật PUBLIC trở lại dùng link cũ.
- Revoke không xóa Chat; reshare cùng email và stable Customer tiếp tục session cũ. Không cho Customer khác dùng lại email tự kế thừa history; policy xử lý cụ thể còn TBD.
- Source sau pull có Outfit và OutfitShare theo từng Outfit, KHÔNG có bảng wardrobe. Giữ nguyên các API/schema này cho tới khi chốt tương thích với toàn-Wardrobe sharing. Không coi OutfitShare là WardrobeShare hoặc tự dựng Wardrobe nền thay Khánh.
- Identity đã có batch lookup nhưng chưa trả email_verified/email_verified_at; account_status không tự thay thế verified-email contract. Notification có thư mời Outfit, chưa phải Wardrobe invitation. Commerce catalog đã có source/client; không bắt Chat chờ toàn bộ Commerce.
- Giữ Controller → Service → Repository, Spring MVC và service ownership. Source mới đang dùng JPA, còn AGENTS.md yêu cầu Spring JDBC: ghi nhận cần chốt trước persistence integration, không tự chuyển framework hoặc sửa code của Khánh. Task 06 phần A là policy thuần, không phụ thuộc lựa chọn persistence.
- Không SQL/FK xuyên service, không khôi phục EP06 V1/V2 cũ lên V1/V2 Outfit hiện tại. Migration mới phải có scope duyệt, khớp history thực tế; không auto-baseline, drop data hoặc dùng placeholder để né dependency.

## QUYẾT ĐỊNH CÒN MỞ

Chỉ hỏi khi bắt đầu phần liên quan: đại diện/key/settings toàn Wardrobe và mapping Outfit; song song/thay thế và precedence với OutfitShare; verified-email contract; Wardrobe invitation contract; public-token storage/create/copy/rotation; REST/WebSocket và text/media; Owner đọc history khi PRIVATE/revoked; persistence convention. Không hỏi lại email sharing một chiều, PRIVATE mời email → PROTECTED hoặc dùng lại link cũ đã duyệt. Không sao chép giới hạn/lifecycle của OutfitShare làm default EP06.

## QUY TRÌNH THỰC HIỆN

1. Đọc AGENTS.md, governance, spec/task và source/tests/contracts hiện tại. Kiểm tra Git status do Owner cung cấp; mọi lệnh Git do Owner tự chạy, giữ các thay đổi khác.
2. Chỉ làm phần đã đủ điều kiện ghi trong task. Dependency chưa có thì chuẩn bị handoff/test cases rồi chuyển sang phần độc lập; không tự làm task người khác để mở đường.
3. Phân biệt rule đã duyệt, source quan sát được và đề xuất NOT APPROVED/TBD. Thiếu trusted identity không được tin email client; unit fixture không trở thành principal production.
4. Test theo phần thay đổi: unit; HTTP/controller; repository/PostgreSQL; isolation, negative/concurrency và regression khi áp dụng. Unit pass không chứng minh dependency/runtime integration; skipped/deferred không là pass.
5. Story có HTTP cần Postman JSON và run guide; structural validation tách execution thật. Không chạy Compose hoặc sửa DB persistent/secrets khi chưa được yêu cầu. Không log token/OTP/password hoặc private payload.

## MỤC TIÊU CỦA TASK

Tạo persistence human Chat 1–1 của Tín trên mô hình toàn Wardrobe đã chốt. Bản Chat cũ chỉ từng verify trên baseline đã rút; không được đưa lại như V2 vì source mới đã có V2 Outfit sharing.

## PHẠM VI & YÊU CẦU CHI TIẾT

- Chốt Wardrobe key/reference theo Task 04 trước DDL; không giả định bảng wardrobe đã có và không tự tạo parent làm thay Khánh.
- Session: ID, Wardrobe key, Owner/Viewer stable Customer ID, next sequence counter và timestamps; unique Wardrobe key + Viewer, Owner khác Viewer.
- Participant: session local FK, Customer ID, joined time; unique session + Customer. Exactly-two đúng Owner/Viewer là invariant của Service.
- Message: session local FK, sender, client message key, sequence, content, created time; unique session + sequence và session + sender + client key; sender phải thuộc đúng session.
- Counter/sequence không âm; ordering writer và idempotent response là Task 10, không tự có từ unique index. Content nonblank; giới hạn 4.000 ký tự chưa duyệt.
- Customer IDs không có FK sang Identity. Không cascade từ revoke/grant/visibility sang session/message; giữ history và phân tách với AI Stylist.
- Scope logic ba bảng đã được duyệt trước đây, nhưng DDL/version/FK mới phải được review tương thích với schema thực tế trước triển khai. Transport/types/Owner suspended history không được chốt ngầm bằng DB.

## CÁC BƯỚC TRIỂN KHAI

1. Đọc migration Outfit hiện tại, Task 04 schema/history và new Chat scope; xác định rõ owner/key references.
2. Soạn/duyệt DDL tương thích, cấp version tiếp theo khi bắt đầu; không sửa V1/V2 hoặc khôi phục migration số cũ.
3. Giữ entity/repository tối thiểu theo persistence convention được Owner chốt; không tự convert code người khác.
4. Verify PostgreSQL fresh + seeded upgrade: preserve Outfit/Item/Share/public token, checksums và Task 04 data.
5. Test duplicate/negative/orphan/same-session sender, competing session create, sequence reservation/rollback, revoke/visibility retention và no-op. Đây là DB evidence, chưa phải API Chat.

## API / DATABASE BOUNDARY

Chỉ DB/persistence trong wardrobe-community-service; không send/history/WebSocket API, cross-service SQL hoặc AI table/history migration.

## ACCEPTANCE CRITERIA (Definition of Done)

- Contract Wardrobe key và DDL scope tương thích đã duyệt; ba bảng được tạo bằng migration mới, giữ data/history/checksum baseline.
- Uniqueness, local membership FK, nonnegative counter/sequence và no-cascade behavior được verify bằng PostgreSQL.
- Regression/upgrade/no-op có evidence thật; không áp 4.000 ký tự, transport hoặc API rule chưa duyệt.
- Parent/key chưa thống nhất thì vẫn DEFERRED, không tạo placeholder để chạy test/migration cho qua.

## KHÔNG ĐƯỢC LÀM / OUT OF SCOPE

Không làm Wardrobe CRUD, group chat, AI Chat hoặc sửa schema/member identity của service khác; không coi DB fixture là quyền truy cập đã enforce.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.

---

# STYLEMIND — EP-06 — TASK 09/11: Create or find ChatSession & validate participants

**User Story:** US-06.03 – Chat with Friends
**Task gốc:** Backend: create chat sessions linked to Wardrobe; participant validation.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** WAITING_DEPENDENCIES — chưa có session API EP06
**Giai đoạn:** Sau Chat schema và canChat integration; có thể song song Task 07
**Tiền đề / thứ tự:** Task 08 tích hợp; Task 05 grant/visibility core, verified current identity và canChat guard thuộc Task 06 B đã được kiểm chứng. Không cần đợi toàn Task 06 nested coverage hoặc Catalog/AI.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Chuẩn bị find-or-create/race/identity-binding cases; không dùng OutfitShare membership để tạo Chat toàn Wardrobe.
- **Chỉ làm sau:** Session/list API và owner/viewer isolation trước Task 10; không bị chặn bởi toàn bộ feature của thành viên khác.
- **Nguồn tiến độ:** docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md. Chỉ báo phần đã có evidence; không coi source của người khác là toàn task EP06 đã hoàn tất.

## BỐI CẢNH VÀ QUYẾT ĐỊNH ÁP DỤNG

- Tín chỉ phụ trách 11 task EP-06 trong wardrobe-community-service. Khánh: Wardrobe/Outfit; Bình: Commerce; Quân: Identity/setup; Vi: Notification. Không tự làm CRUD hoặc sửa contract/schema của thành viên khác.
- Owner đã chốt: email sharing một chiều thay Friendship; mỗi account có một My Wardrobe mặc định PRIVATE, có thể chưa có Outfit; chia sẻ toàn bộ tủ, gồm Outfit tạo sau.
- PRIVATE chỉ Owner xem, giữ grants/history nhưng chặn Viewer; mời email khi PRIVATE tự chuyển PROTECTED. PROTECTED cần đúng email hiện tại đã verified và grant ACTIVE. PUBLIC token chỉ cấp VIEW; Chat vẫn cần người được mời hợp lệ. Bật PUBLIC trở lại dùng link cũ.
- Revoke không xóa Chat; reshare cùng email và stable Customer tiếp tục session cũ. Không cho Customer khác dùng lại email tự kế thừa history; policy xử lý cụ thể còn TBD.
- Source sau pull có Outfit và OutfitShare theo từng Outfit, KHÔNG có bảng wardrobe. Giữ nguyên các API/schema này cho tới khi chốt tương thích với toàn-Wardrobe sharing. Không coi OutfitShare là WardrobeShare hoặc tự dựng Wardrobe nền thay Khánh.
- Identity đã có batch lookup nhưng chưa trả email_verified/email_verified_at; account_status không tự thay thế verified-email contract. Notification có thư mời Outfit, chưa phải Wardrobe invitation. Commerce catalog đã có source/client; không bắt Chat chờ toàn bộ Commerce.
- Giữ Controller → Service → Repository, Spring MVC và service ownership. Source mới đang dùng JPA, còn AGENTS.md yêu cầu Spring JDBC: ghi nhận cần chốt trước persistence integration, không tự chuyển framework hoặc sửa code của Khánh. Task 06 phần A là policy thuần, không phụ thuộc lựa chọn persistence.
- Không SQL/FK xuyên service, không khôi phục EP06 V1/V2 cũ lên V1/V2 Outfit hiện tại. Migration mới phải có scope duyệt, khớp history thực tế; không auto-baseline, drop data hoặc dùng placeholder để né dependency.

## QUYẾT ĐỊNH CÒN MỞ

Chỉ hỏi khi bắt đầu phần liên quan: đại diện/key/settings toàn Wardrobe và mapping Outfit; song song/thay thế và precedence với OutfitShare; verified-email contract; Wardrobe invitation contract; public-token storage/create/copy/rotation; REST/WebSocket và text/media; Owner đọc history khi PRIVATE/revoked; persistence convention. Không hỏi lại email sharing một chiều, PRIVATE mời email → PROTECTED hoặc dùng lại link cũ đã duyệt. Không sao chép giới hạn/lifecycle của OutfitShare làm default EP06.

## QUY TRÌNH THỰC HIỆN

1. Đọc AGENTS.md, governance, spec/task và source/tests/contracts hiện tại. Kiểm tra Git status do Owner cung cấp; mọi lệnh Git do Owner tự chạy, giữ các thay đổi khác.
2. Chỉ làm phần đã đủ điều kiện ghi trong task. Dependency chưa có thì chuẩn bị handoff/test cases rồi chuyển sang phần độc lập; không tự làm task người khác để mở đường.
3. Phân biệt rule đã duyệt, source quan sát được và đề xuất NOT APPROVED/TBD. Thiếu trusted identity không được tin email client; unit fixture không trở thành principal production.
4. Test theo phần thay đổi: unit; HTTP/controller; repository/PostgreSQL; isolation, negative/concurrency và regression khi áp dụng. Unit pass không chứng minh dependency/runtime integration; skipped/deferred không là pass.
5. Story có HTTP cần Postman JSON và run guide; structural validation tách execution thật. Không chạy Compose hoặc sửa DB persistent/secrets khi chưa được yêu cầu. Không log token/OTP/password hoặc private payload.

## MỤC TIÊU CỦA TASK

Khi recipient hợp lệ mở Wardrobe và nhấn Chat, mở/khởi tạo một Session 1–1 với Owner. Owner có Messages để chọn Session của mình. Không tạo Session cho khách chỉ xem Public.

## PHẠM VI & YÊU CẦU CHI TIẾT

- `POST /wardrobes/{wardrobeId}/chat`: chỉ viewer đã đăng nhập, email verified + ACTIVE share, visibility PROTECTED/PUBLIC; Owner inferred Wardrobe, viewer from trusted JWT principal. Nếu đã có session cho wardrobe+viewer ID, trả session cũ.
- `GET /chats`: danh sách session thuộc user hiện tại; Owner có thể xem danh sách Chat từng recipient; Viewer chỉ danh sách của mình; metadata không lộ third-party.
- Create exactly 2 participants (Owner, Viewer), cùng transaction; DB uniqueness xử lý race khi hai request tạo đồng thời; session re-open dùng cùng ID.
- Chính chủ Wardrobe không được tự giả mạo email viewer tạo chat với chính mình; public-only không được.
- Verify shared grant theo **email đã xác thực** và Customer ID ổn định; nếu thay email phải recheck, không trust stale customer ID.
- Owner read-only suspended history là lựa chọn chưa duyệt; không mở send khi PRIVATE hoặc revoked.

## CÁC BƯỚC TRIỂN KHAI

1. Inventory Spring Security principal, share policy, chat entity từ Task 08.
2. Implement find-or-create transactional + row/unique race handling.
3. Implement owner/viewer ChatSession listing safely; no cross-user data leakage.
4. Add tests repeat opens, concurrent opens, unauthorized public, guessed Session ID, PRIVATE/REVOKED.
5. Verify service build and tests, không gửi message ở task này.

## API / DATABASE BOUNDARY

Đề xuất: `POST /api/v1/wardrobes/{wardrobeId}/chat` và `GET /api/v1/chats`; dùng route/response envelope repo nếu khác.

## ACCEPTANCE CRITERIA (Definition of Done)

- Exactly one ChatSession per `(wardrobe_id, viewerCustomerId)`.
- Exactly two correct participants.
- Guest/Public-only / wrong email / revoked / PRIVATE cannot create.
- Owner Messages list only own sessions; no cross-session data leak.
- Re-share same verified Customer opens existing session ID.

## KHÔNG ĐƯỢC LÀM / OUT OF SCOPE

Không tự tạo chat giữa hai viewers; không tạo group chat, không thêm message endpoints Task 10.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.

---

# STYLEMIND — EP-06 — TASK 10/11: Send message, history, deterministic ordering & idempotency

**User Story:** US-06.03 – Chat with Friends
**Task gốc:** Backend: send-message and message-history APIs.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** WAITING_DEPENDENCIES_AND_DECISIONS — chưa có send/history EP06
**Giai đoạn:** Sau Task 09 và chốt transport/content/Owner-history liên quan
**Tiền đề / thứ tự:** Tasks 08/09; Task 05 current grant/visibility và canChat phần Task 06 B; Owner duyệt transport/types. Owner suspended-history branch chỉ triển khai khi có quyết định.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Chuẩn bị ordering/idempotency/lock-order/paging/revoke-race cases; REST/text chỉ là đề xuất, không mở API/transport mặc định.
- **Chỉ làm sau:** Send/history với contract đã chọn; Task 11 kiểm chứng tích hợp cuối.
- **Nguồn tiến độ:** docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md. Chỉ báo phần đã có evidence; không coi source của người khác là toàn task EP06 đã hoàn tất.

## BỐI CẢNH VÀ QUYẾT ĐỊNH ÁP DỤNG

- Tín chỉ phụ trách 11 task EP-06 trong wardrobe-community-service. Khánh: Wardrobe/Outfit; Bình: Commerce; Quân: Identity/setup; Vi: Notification. Không tự làm CRUD hoặc sửa contract/schema của thành viên khác.
- Owner đã chốt: email sharing một chiều thay Friendship; mỗi account có một My Wardrobe mặc định PRIVATE, có thể chưa có Outfit; chia sẻ toàn bộ tủ, gồm Outfit tạo sau.
- PRIVATE chỉ Owner xem, giữ grants/history nhưng chặn Viewer; mời email khi PRIVATE tự chuyển PROTECTED. PROTECTED cần đúng email hiện tại đã verified và grant ACTIVE. PUBLIC token chỉ cấp VIEW; Chat vẫn cần người được mời hợp lệ. Bật PUBLIC trở lại dùng link cũ.
- Revoke không xóa Chat; reshare cùng email và stable Customer tiếp tục session cũ. Không cho Customer khác dùng lại email tự kế thừa history; policy xử lý cụ thể còn TBD.
- Source sau pull có Outfit và OutfitShare theo từng Outfit, KHÔNG có bảng wardrobe. Giữ nguyên các API/schema này cho tới khi chốt tương thích với toàn-Wardrobe sharing. Không coi OutfitShare là WardrobeShare hoặc tự dựng Wardrobe nền thay Khánh.
- Identity đã có batch lookup nhưng chưa trả email_verified/email_verified_at; account_status không tự thay thế verified-email contract. Notification có thư mời Outfit, chưa phải Wardrobe invitation. Commerce catalog đã có source/client; không bắt Chat chờ toàn bộ Commerce.
- Giữ Controller → Service → Repository, Spring MVC và service ownership. Source mới đang dùng JPA, còn AGENTS.md yêu cầu Spring JDBC: ghi nhận cần chốt trước persistence integration, không tự chuyển framework hoặc sửa code của Khánh. Task 06 phần A là policy thuần, không phụ thuộc lựa chọn persistence.
- Không SQL/FK xuyên service, không khôi phục EP06 V1/V2 cũ lên V1/V2 Outfit hiện tại. Migration mới phải có scope duyệt, khớp history thực tế; không auto-baseline, drop data hoặc dùng placeholder để né dependency.

## QUYẾT ĐỊNH CÒN MỞ

Chỉ hỏi khi bắt đầu phần liên quan: đại diện/key/settings toàn Wardrobe và mapping Outfit; song song/thay thế và precedence với OutfitShare; verified-email contract; Wardrobe invitation contract; public-token storage/create/copy/rotation; REST/WebSocket và text/media; Owner đọc history khi PRIVATE/revoked; persistence convention. Không hỏi lại email sharing một chiều, PRIVATE mời email → PROTECTED hoặc dùng lại link cũ đã duyệt. Không sao chép giới hạn/lifecycle của OutfitShare làm default EP06.

## QUY TRÌNH THỰC HIỆN

1. Đọc AGENTS.md, governance, spec/task và source/tests/contracts hiện tại. Kiểm tra Git status do Owner cung cấp; mọi lệnh Git do Owner tự chạy, giữ các thay đổi khác.
2. Chỉ làm phần đã đủ điều kiện ghi trong task. Dependency chưa có thì chuẩn bị handoff/test cases rồi chuyển sang phần độc lập; không tự làm task người khác để mở đường.
3. Phân biệt rule đã duyệt, source quan sát được và đề xuất NOT APPROVED/TBD. Thiếu trusted identity không được tin email client; unit fixture không trở thành principal production.
4. Test theo phần thay đổi: unit; HTTP/controller; repository/PostgreSQL; isolation, negative/concurrency và regression khi áp dụng. Unit pass không chứng minh dependency/runtime integration; skipped/deferred không là pass.
5. Story có HTTP cần Postman JSON và run guide; structural validation tách execution thật. Không chạy Compose hoặc sửa DB persistent/secrets khi chưa được yêu cầu. Không log token/OTP/password hoặc private payload.

## MỤC TIÊU CỦA TASK

Triển khai gửi tin nhắn và đọc lịch sử 1–1 theo transport/message types đã được Owner duyệt, giữ ordering/idempotency/current authorization. REST/text dưới đây chỉ là contract đề xuất, không mặc định được duyệt.

## PHẠM VI & YÊU CẦU CHI TIẾT

- `POST /chats/{chatId}/messages`: authenticated sender belongs to session; kiểm tra Wardrobe PROTECTED/PUBLIC, Viewer grant ACTIVE (verified email, same stable Customer), Owner gửi chỉ khi target share active; require nonblank content + clientMessageId.
- Idempotency UNIQUE `(session, sender, clientMessageId)`; cùng key và body cũ trả message cũ, key/body khác → 409; hai message giống text nhưng khác key đều hợp lệ.
- Per-session `sequence_number` tăng monotonic, cấp số an toàn trong transaction (lock ChatSession; verify authorized grant/wardrobe lock consistent để ngăn send sau completed revoke).
- `GET /chats/{chatId}/messages?beforeSequence=&limit=`: check session membership and **current** grant/visibility đối với Viewer, paging theo cursor, stable order `sequence_number`.
- Sau Revoke/PRIVATE Viewer không được read/send, messages vẫn lưu. Owner read-only history khi suspended/revoked là đề xuất chưa chốt, cần PO xác nhận.
- No AI Stylist history/metadata và no private member details in output. Prevent payload/script/log injection by validation/sanitization as fits frontend.
- Do not trust senderId from body; derive security context; no realtime transport claim.

## CÁC BƯỚC TRIỂN KHAI

1. Xác nhận REST/text-only MVP hoặc yêu cầu thật từ nhóm; review auth and schema.
2. Implement transactional message writer+idempotency and pagination query.
3. Implement read/send theo transport được duyệt; nếu chọn REST thì dùng consistent error handling và routes đề xuất đã chốt.
4. Add tests duplicate retry same/different body, concurrent sends, beforeSequence pagination, owner+viewer auth, revoke races.
5. Run build/test, note WebSocket not part of task unless explicitly requested.

## API / DATABASE BOUNDARY

Đề xuất: `POST /api/v1/chats/{chatId}/messages` `{ "clientMessageId":"uuid", "content":"..." }`; `GET /api/v1/chats/{chatId}/messages?beforeSequence=...&limit=30`. Return server-generated messageId, senderCustomerId, sequenceNumber, content, createdAt.

## ACCEPTANCE CRITERIA (Definition of Done)

- Allowed participant sends and reads chronological stable history.
- Different users cannot access other sessions.
- Same key+same content has one DB row; same key+new content rejects 409.
- Concurrent messages get distinct monotonic sequence.
- Viewer after revoke/PRIVATE denied new requests; history retained.
- No unapproved chat media/WebSocket claims.

## KHÔNG ĐƯỢC LÀM / OUT OF SCOPE

Không tạo group chat, không send Owner→revoked recipient, không bypass authorization qua caching.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.

---

# STYLEMIND — EP-06 — TASK 11/11: Tests: ordering, duplicates, unauthorized & revoked Chat

**User Story:** US-06.03 – Chat with Friends
**Task gốc:** Tests: message ordering, idempotency, outsider permissions, revoked access.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** WAITING_DEPENDENCIES — chưa có Chat API để chứng minh acceptance
**Giai đoạn:** Sau Task 09/10 và Task 05 revoke/visibility
**Tiền đề / thứ tự:** Tasks 08/09/10 hoạt động; Task 05 revoke/visibility và current canChat có contract/test; transport và Owner-history cases liên quan đã chốt.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Chuẩn bị service/HTTP/PostgreSQL/concurrency matrix; giữ fixture/unit evidence tách khỏi E2E.
- **Chỉ làm sau:** Chạy integration/race/history tests trên API thật; smoke chỉ bằng resources cô lập và phạm vi được phép.
- **Nguồn tiến độ:** docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md. Chỉ báo phần đã có evidence; không coi source của người khác là toàn task EP06 đã hoàn tất.

## BỐI CẢNH VÀ QUYẾT ĐỊNH ÁP DỤNG

- Tín chỉ phụ trách 11 task EP-06 trong wardrobe-community-service. Khánh: Wardrobe/Outfit; Bình: Commerce; Quân: Identity/setup; Vi: Notification. Không tự làm CRUD hoặc sửa contract/schema của thành viên khác.
- Owner đã chốt: email sharing một chiều thay Friendship; mỗi account có một My Wardrobe mặc định PRIVATE, có thể chưa có Outfit; chia sẻ toàn bộ tủ, gồm Outfit tạo sau.
- PRIVATE chỉ Owner xem, giữ grants/history nhưng chặn Viewer; mời email khi PRIVATE tự chuyển PROTECTED. PROTECTED cần đúng email hiện tại đã verified và grant ACTIVE. PUBLIC token chỉ cấp VIEW; Chat vẫn cần người được mời hợp lệ. Bật PUBLIC trở lại dùng link cũ.
- Revoke không xóa Chat; reshare cùng email và stable Customer tiếp tục session cũ. Không cho Customer khác dùng lại email tự kế thừa history; policy xử lý cụ thể còn TBD.
- Source sau pull có Outfit và OutfitShare theo từng Outfit, KHÔNG có bảng wardrobe. Giữ nguyên các API/schema này cho tới khi chốt tương thích với toàn-Wardrobe sharing. Không coi OutfitShare là WardrobeShare hoặc tự dựng Wardrobe nền thay Khánh.
- Identity đã có batch lookup nhưng chưa trả email_verified/email_verified_at; account_status không tự thay thế verified-email contract. Notification có thư mời Outfit, chưa phải Wardrobe invitation. Commerce catalog đã có source/client; không bắt Chat chờ toàn bộ Commerce.
- Giữ Controller → Service → Repository, Spring MVC và service ownership. Source mới đang dùng JPA, còn AGENTS.md yêu cầu Spring JDBC: ghi nhận cần chốt trước persistence integration, không tự chuyển framework hoặc sửa code của Khánh. Task 06 phần A là policy thuần, không phụ thuộc lựa chọn persistence.
- Không SQL/FK xuyên service, không khôi phục EP06 V1/V2 cũ lên V1/V2 Outfit hiện tại. Migration mới phải có scope duyệt, khớp history thực tế; không auto-baseline, drop data hoặc dùng placeholder để né dependency.

## QUYẾT ĐỊNH CÒN MỞ

Chỉ hỏi khi bắt đầu phần liên quan: đại diện/key/settings toàn Wardrobe và mapping Outfit; song song/thay thế và precedence với OutfitShare; verified-email contract; Wardrobe invitation contract; public-token storage/create/copy/rotation; REST/WebSocket và text/media; Owner đọc history khi PRIVATE/revoked; persistence convention. Không hỏi lại email sharing một chiều, PRIVATE mời email → PROTECTED hoặc dùng lại link cũ đã duyệt. Không sao chép giới hạn/lifecycle của OutfitShare làm default EP06.

## QUY TRÌNH THỰC HIỆN

1. Đọc AGENTS.md, governance, spec/task và source/tests/contracts hiện tại. Kiểm tra Git status do Owner cung cấp; mọi lệnh Git do Owner tự chạy, giữ các thay đổi khác.
2. Chỉ làm phần đã đủ điều kiện ghi trong task. Dependency chưa có thì chuẩn bị handoff/test cases rồi chuyển sang phần độc lập; không tự làm task người khác để mở đường.
3. Phân biệt rule đã duyệt, source quan sát được và đề xuất NOT APPROVED/TBD. Thiếu trusted identity không được tin email client; unit fixture không trở thành principal production.
4. Test theo phần thay đổi: unit; HTTP/controller; repository/PostgreSQL; isolation, negative/concurrency và regression khi áp dụng. Unit pass không chứng minh dependency/runtime integration; skipped/deferred không là pass.
5. Story có HTTP cần Postman JSON và run guide; structural validation tách execution thật. Không chạy Compose hoặc sửa DB persistent/secrets khi chưa được yêu cầu. Không log token/OTP/password hoặc private payload.

## MỤC TIÊU CỦA TASK

Xây bộ kiểm thử Chat end-to-end ở phạm vi service (Spring Boot/PostgreSQL) và Docker smoke tests để chứng minh session isolation, ordering/idempotency, revoke/resume và an toàn dữ liệu.

## PHẠM VI & YÊU CẦU CHI TIẾT

- Repeated/concurrent session open creates one session exactly two participants.
- Public-only/guest/wrong email cannot start Chat; outsider guessed chatId denied for get/send; Mai cannot access Hoa Session.
- Send legitimate message, stable `sequenceNumber`, cursor pagination, server timestamp.
- Same clientMessageId+same body returns one row; same key+different body → 409; concurrent send ordered.
- PRIVATE blocks Viewer, PROTECTED re-enables ACTIVE; REVOKED blocks Viewer read/send, PUBLIC revoked person view-only.
- Re-share same email+same stable Customer gives same session and full history; other Customer controlling the email later must not inherit chat without approved identity policy.
- Concurrent revoke vs send: verify transaction semantics, no message is newly authorized **after completed revoke**.
- Restart application/PostgreSQL containers retains data when persistent storage configured; report only if actually run.

## CÁC BƯỚC TRIỂN KHAI

1. Test plan with cases CH-01..CH-12 and INT-03/INT-04 from original spec.
2. Write unit/service tests, MockMvc/JWT resource tests, PostgreSQL/Testcontainers integration as available.
3. Run tests và báo commands/counts thật; optional restart/smoke dùng resources test cô lập. Có Docker/Compose không tự cho phép chạy deployment persistent.
4. Resolve scope-contained bugs only; no feature expansion.
5. Final report with evidence and open decisions.

## API / DATABASE BOUNDARY

Không thêm API mới; kiểm thử Task 09/10 contracts và ownership/visibility/share policy.

## ACCEPTANCE CRITERIA (Definition of Done)

- Unauthorized access always denied for outsider/public-only/revoked viewer.
- Ordering and idempotency proven under concurrent requests.
- Visibility transitions preserve history; same owner/viewer resume old Session.
- No data loss across restarts if smoke test ran.
- Test evidence truthful and known gaps clearly labeled.

## KHÔNG ĐƯỢC LÀM / OUT OF SCOPE

Không làm thay Task 07 Share tests hoặc âm thầm bật WebSocket.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.
