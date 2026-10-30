# EP06 — Task 01: Invitation + Friendship migration — PLAN v4.2

**Cập nhật 2026-10-24. Trạng thái: DONE_LOCAL — Owner đã duyệt scope hai bảng Friends; V3 + Entity/Repository Spring Data JPA và PostgreSQL tests đã pass. Chưa áp DB lâu dài; API Task 02 A hiện đã có, invitation mutations còn chờ contracts.**

Theo nghiệp vụ mới phải có lời mời email PENDING→ACCEPTED và quan hệ bạn bè hai chiều unique cặp tài khoản. Recipient có thể chưa có account lúc mời. Invitation có hạn 7 ngày; resend tạo link mới, vô hiệu link cũ và tính lại 7 ngày theo B9/B19. DONE_REVIEW bỏ Friendship bên dưới chỉ là lịch sử; không còn áp dụng cho task DB hiện tại. Không chờ Wardrobe/Task 04. [DDL scope đã duyệt](../docs/planning/wardrobe/EP06_TASK01_FRIENDS_DATABASE_SCOPE.md).

Đọc [plan hiện hành](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [index 11 task](00_START_HERE.md). Task 01 DB/JPA DONE_LOCAL; Task 02/03 PARTIAL với ba HTTP APIs list friends/outgoing/unfriend, Swagger/Postman và 29 tests mới. Gate hiện hành 131 test pass, 0 skipped. [Báo cáo Task 01](../docs/qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md) và [Task 02](../docs/qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md) ghi rõ scope; invitation integration/Chat chưa triển khai. Nội dung v4.1 bên dưới được giữ nguyên làm traceability; các rule/dependency cũ không phải hướng dẫn implementation.

---

## Baseline v4.1 — superseded

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
