# EP06 — Task 02: Friends APIs — DONE_LOCAL

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser.

Backend send/resend/incoming/accept/list/unfriend và V5 đã chạy verification. Còn frontend/browser/provider production thuộc bước sau.

[Plan](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md), [báo cáo](../docs/qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md), [Postman](../postman/EP06_FRIEND_INVITATIONS_RUN_GUIDE.md). Không coi full Identity gate pass; 15 failures trùng baseline. v4.1 dưới đây chỉ là lịch sử.

---
## Baseline v4.1 — superseded

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
