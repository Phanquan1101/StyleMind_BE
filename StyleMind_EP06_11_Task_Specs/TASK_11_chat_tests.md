# EP06 — Task 11: Chat tests/handover — PLAN v4.6

**2026-10-29 — hiện hành: PARTIAL vì browser/UI (v4.11).** Native Gateway/JWT/STOMP/SMTP/actual invitation/Register/verify/login/refriend, Newman và ảnh B2 thật đã pass. Gate Wardrobe 294; fake/live mỗi mode 1 JUnit + 123 requests/325 assertions, live exact cleanup 8 keys/9 versions. [Báo cáo](../docs/qa/wardrobe/EP06_BACKBLAZE_VERIFICATION_2026-10-29.md). Còn acceptance frontend/browser, không là production readiness; Task 02/03/10 DONE_LOCAL backend, 04–07 ON_HOLD. Các kết luận chưa invitation/cloud ở checkpoint ngày 27 bên dưới đã được bổ sung bởi cập nhật này.

**2026-10-27. Trạng thái: PARTIAL toàn task.** Text/history/WS và backend ảnh private đã hoàn thành local; live cloud, browser và real invitation/re-friend E2E còn chờ. Task 01/08/09 DONE_LOCAL; 02/03 PARTIAL; 04–07 ON_HOLD.

Gate mới nhất **248 tests, 0 failed/error/skipped** (36 ảnh mới + 212 regression). Runtime harness thêm 1 JUnit scenario: Identity Login/JWKS + Gateway + Wardrobe JAR thật, native WS text/ảnh và current authorization sau unfriend/refriend. Newman Friends 28/89, Sessions 30/81, Messages 27/69, Images 28/66, tổng **113 requests/305 assertions**, zero failures. S3 backend trong runtime là HTTP fake, refriend là SQL fixture; chưa live provider hoặc accept invitation E2E.

Backend C: POST messages multipart, image-only/caption optional, 1–5 ảnh, 5 MiB/ảnh, JPG/PNG/WebP, **40 megapixel Owner đã duyệt B21**, private GET attachment, S3-compatible adapter default disabled, ordered SHA-256 retry dùng chung namespace text, atomic DB/recheck/cleanup, Swagger cả JSON và multipart. Network storage ngoài DB transaction; uncertain commit không xóa mù object, reconciliation thủ công. Giữ V1–V4 và interface → impl → internal JPA.

[Contract ảnh](../docs/planning/wardrobe/EP06_TASK10_CHAT_IMAGES_CONTRACT_PROPOSAL.md), [báo cáo ảnh/runtime](../docs/qa/wardrobe/EP06_TASK10_IMAGES_AND_RUNTIME_IMPLEMENTATION_REPORT.md), [HTTP ảnh guide](../postman/EP06_CHAT_IMAGES_RUN_GUIDE.md), [HTTP text guide](../postman/EP06_CHAT_MESSAGES_RUN_GUIDE.md), [WS guide](../postman/EP06_CHAT_WEBSOCKET_RUN_GUIDE.md), [runner](../scripts/ep06-chat-runtime/README.md).

Owner yêu cầu setup account/provider/bucket sau: [cloud setup](../docs/operations/wardrobe/EP06_CHAT_IMAGES_CLOUD_SETUP.md). Chưa có tài nguyên/chi phí cloud; không local fallback, Compose hoặc DB persistent. Gateway hiện có đã chạy được full native WS, không sửa route. Chưa sửa source Khánh/Quân/Vi/Gateway.

Đọc [plan](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [index](00_START_HERE.md). Phần v4.1 bên dưới giữ nguyên để truy vết; dependency Wardrobe sharing cũ không áp dụng.

---

## Baseline v4.1 — superseded

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
