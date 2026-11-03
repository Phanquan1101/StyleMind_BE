# EP06 — Task 03: Friends tests — DONE_LOCAL

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser.

Đã chạy unit/controller/HTTP/PostgreSQL/rollback/concurrency, actual SMTP/Register/verify/login/accept/chat/refriend và đủ 10 request Postman invitation.

[Plan](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md), [báo cáo](../docs/qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md), [Postman](../postman/EP06_FRIEND_INVITATIONS_RUN_GUIDE.md). Không coi full Identity gate pass; 15 failures trùng baseline. v4.1 dưới đây chỉ là lịch sử.

---
## Baseline v4.1 — superseded

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
