# EP06 — Task 07: Share Outfit DONE_LOCAL

**v4.13 — Share Outfit DONE_LOCAL, 2026-10-29 (backend/local).** Owner duyệt S1–S4, API cutover và V6; mời email giữ PRIVATE, Owner tự chọn FRIENDS. FRIENDS cho toàn bộ current friends; PRIVATE chỉ Owner; PUBLIC guest, OFF/ON cùng link. Email-grant/feed cũ trả 410; legacy rows giữ nguyên. V1–V5 không đổi.

Wardrobe regression **314/314, 33 classes, 0 failures/errors/skipped**. Bốn JAR thật + Mailpit/PostgreSQL disposable: invitation → Register/verify/login → accept → PRIVATE vẫn ẩn → Owner chọn FRIENDS → unfriend/refriend pass; native WS/ảnh regression pass. Newman **6 collections / 8 runs / 155 requests / 380 assertions**, gồm Share Outfit **32 requests / 55 assertions**, không failures. Commerce catalog và image storage dùng HTTP fixture trong lượt runtime này; không là Commerce/cloud end-to-end mới.

**Exit của Task 07:** Validator20 + migration3 + sharing HTTP14 scenarios; CRUD20 regression giữ nguyên; full Wardrobe314 pass; real Gateway/Newman Share Outfit32/55 pass. Không coi structural validation là HTTP runtime.

[Báo cáo requirement mapping/files/evidence](../docs/qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md), [run guide](../postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md). Còn browser/frontend acceptance riêng; không production readiness. Các nội dung toàn Wardrobe/PLANNING/NOT APPROVED bên dưới là lịch sử đã superseded.

---
## Checkpoint trước implementation — giữ nguyên để truy vết

# EP06 — Task 07: Share Outfit tests — PLAN v4.2

**2026-10-29 — v4.12 hiện hành: PLANNING / NOT RUN.** [Contract mục 7](../docs/planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md) + [V6 verification](../docs/planning/wardrobe/EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md) xác định fresh/seeded upgrade, negative authorization/enum/parent/privacy/counts, public OFF/ON reuse, legacy cutover, unfriend/refriend và concurrency/rollback. Giữ regression CRUD/Friends/Chat; mapping rõ expectation sharing cũ→mới, không skip để pass. API implementation mới chưa có, không gán 294 tests/123 requests trước đây thành Task 07 pass. ON_HOLD/v4.1 dưới là checkpoint.

**Cập nhật 2026-10-24. Trạng thái: ON_HOLD cùng phần share Outfit.**

Chưa viết/chạy test behavior Outfit mới hoặc dùng task này làm điều kiện mở Friends/Chat. Existing Outfit tests là regression cần giữ khi triển khai về sau, không được đổi expectation cho nghiệp vụ mới trong đợt planning này.

Đọc [plan hiện hành](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [index 11 task](00_START_HERE.md). Task 01 DB/JPA DONE_LOCAL; Task 02/03 PARTIAL với ba HTTP APIs list friends/outgoing/unfriend, Swagger/Postman và 29 tests mới. Gate hiện hành 131 test pass, 0 skipped. [Báo cáo Task 01](../docs/qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md) và [Task 02](../docs/qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md) ghi rõ scope; invitation integration/Chat chưa triển khai. Nội dung v4.1 bên dưới được giữ nguyên làm traceability; các rule/dependency cũ không phải hướng dẫn implementation.

---

## Baseline v4.1 — superseded

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
