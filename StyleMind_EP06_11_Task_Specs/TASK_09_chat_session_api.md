# EP06 — Task 09: Create/find/list Chat theo cặp — v4.4

**Cập nhật 2026-10-27. Trạng thái: DONE_LOCAL — session APIs theo Friendship hiện tại.**

Mỗi cặp bạn bè một hội thoại dùng lại, membership đúng cặp và friendship hiện còn hiệu lực. Không cần owner/viewer theo Wardrobe hay visibility/share grants. Sau unfriend cả hai không đọc/gửi, giữ dữ liệu. Kết bạn lại thành công dùng lại session và khôi phục toàn bộ lịch sử theo B11; Owner đã chốt B20: ẩn hội thoại khỏi list của cả hai khi unfriend. Đã có [contract Task 09](../docs/planning/wardrobe/EP06_TASK09_CHAT_SESSION_API_CONTRACT.md), ba APIs POST open / GET detail / GET list, cấu trúc interface → impl → internal, Swagger và [Postman](../postman/EP06_CHAT_SESSIONS_RUN_GUIDE.md).

Đọc [plan hiện hành](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [index 11 task](00_START_HERE.md). Task 01 DB/JPA DONE_LOCAL; Task 02/03 PARTIAL với ba HTTP APIs list friends/outgoing/unfriend, Swagger/Postman và 29 tests mới. Gate Task 09 là 181 test pass, 0 skipped (28 mới + 153 regression); [báo cáo Task 09](../docs/qa/wardrobe/EP06_TASK09_IMPLEMENTATION_REPORT.md). Task 08 V4/JPA đã DONE_LOCAL. [Báo cáo Task 01](../docs/qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md) và [Task 02](../docs/qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md) ghi rõ scope; invitation mutations và messages/WebSocket/upload chưa triển khai. Nội dung v4.1 bên dưới được giữ nguyên làm traceability; các rule/dependency cũ không phải hướng dẫn implementation.

---

## Baseline v4.1 — superseded

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
