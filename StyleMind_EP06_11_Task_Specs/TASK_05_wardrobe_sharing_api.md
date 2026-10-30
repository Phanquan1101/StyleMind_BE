# EP06 — Task 05: Share Outfit DONE_LOCAL

**v4.13 — Share Outfit DONE_LOCAL, 2026-10-29 (backend/local).** Owner duyệt S1–S4, API cutover và V6; mời email giữ PRIVATE, Owner tự chọn FRIENDS. FRIENDS cho toàn bộ current friends; PRIVATE chỉ Owner; PUBLIC guest, OFF/ON cùng link. Email-grant/feed cũ trả 410; legacy rows giữ nguyên. V1–V5 không đổi.

Wardrobe regression **314/314, 33 classes, 0 failures/errors/skipped**. Bốn JAR thật + Mailpit/PostgreSQL disposable: invitation → Register/verify/login → accept → PRIVATE vẫn ẩn → Owner chọn FRIENDS → unfriend/refriend pass; native WS/ảnh regression pass. Newman **6 collections / 8 runs / 155 requests / 380 assertions**, gồm Share Outfit **32 requests / 55 assertions**, không failures. Commerce catalog và image storage dùng HTTP fixture trong lượt runtime này; không là Commerce/cloud end-to-end mới.

**Exit của Task 05:** Owner GET/PATCH sharing; friend list/detail; public guest; alias OFF/ON cùng link; retired email-share/feed 410. Swagger + Postman/runtime pass. Invitation không tự đổi PRIVATE.

[Báo cáo requirement mapping/files/evidence](../docs/qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md), [run guide](../postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md). Còn browser/frontend acceptance riêng; không production readiness. Các nội dung toàn Wardrobe/PLANNING/NOT APPROVED bên dưới là lịch sử đã superseded.

---
## Checkpoint trước implementation — giữ nguyên để truy vết

# EP06 — Task 05: Share Outfit APIs — PLAN v4.2

**2026-10-29 — v4.12 hiện hành: PLANNING / NOT APPROVED.** [Contract cụ thể](../docs/planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md) có owner settings GET/PATCH, friend Outfit list/detail, public reader/aliases, additive owner visibility DTO, đề xuất legacy email/share feed trả 410 và reuse Friends invitation. Chờ duyệt S1–S4/V6; chưa sửa API cũ hoặc coi schema/compatibility approved. Sau 04, triển khai cùng 06; Swagger/Postman mới kèm actual HTTP verification. ON_HOLD/v4.1 dưới là checkpoint.

**Cập nhật 2026-10-24. Trạng thái: ON_HOLD — giữ nguyên API share/public Outfit hiện tại.**

Nghiệp vụ mới là mỗi Outfit chọn chế độ; Bạn bè cấp VIEW cho toàn bộ bạn bè, không chọn email được xem riêng. Không sửa OutfitShareService/controller/public-link hoặc chuyển nghĩa email share thành friend invitation trong đợt Friends/Chat. Nội dung share toàn Wardrobe bên dưới đã superseded.

Đọc [plan hiện hành](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [index 11 task](00_START_HERE.md). Task 01 DB/JPA DONE_LOCAL; Task 02/03 PARTIAL với ba HTTP APIs list friends/outgoing/unfriend, Swagger/Postman và 29 tests mới. Gate hiện hành 131 test pass, 0 skipped. [Báo cáo Task 01](../docs/qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md) và [Task 02](../docs/qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md) ghi rõ scope; invitation integration/Chat chưa triển khai. Nội dung v4.1 bên dưới được giữ nguyên làm traceability; các rule/dependency cũ không phải hướng dẫn implementation.

---

## Baseline v4.1 — superseded

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
