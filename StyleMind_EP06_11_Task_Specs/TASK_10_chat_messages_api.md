# EP06 — Task 10: Messages/history/WebSocket/ảnh private — PLAN v4.6

**2026-10-29 — hiện hành: DONE_LOCAL — backend/cloud (v4.11).** B2 đã được Owner chọn, IDE/Docker wiring đã bổ sung; gate 294 Wardrobe pass, live runtime 1 JUnit + Newman 123 requests/325 assertions pass. Private PNG/upload/read/retry/WS/unfriend/refriend và exact cleanup 8 keys/9 versions được kiểm chứng. [Báo cáo](../docs/qa/wardrobe/EP06_BACKBLAZE_VERIFICATION_2026-10-29.md), [setup](../docs/operations/wardrobe/EP06_BACKBLAZE_B2_SETUP.md). Task 02/03 DONE_LOCAL backend; browser/UI nằm ở Task 11 còn PARTIAL; Share Outfit 04–07 ON_HOLD. Nội dung v4.6 ngày 27 bên dưới giữ làm checkpoint, không dùng kết luận chưa chọn cloud/chưa có invitation hiện tại.

**2026-10-27. Trạng thái: PARTIAL toàn task.** Text/history/WS và backend ảnh private đã hoàn thành local; live cloud, browser và real invitation/re-friend E2E còn chờ. Task 01/08/09 DONE_LOCAL; 02/03 PARTIAL; 04–07 ON_HOLD.

Gate mới nhất **248 tests, 0 failed/error/skipped** (36 ảnh mới + 212 regression). Runtime harness thêm 1 JUnit scenario: Identity Login/JWKS + Gateway + Wardrobe JAR thật, native WS text/ảnh và current authorization sau unfriend/refriend. Newman Friends 28/89, Sessions 30/81, Messages 27/69, Images 28/66, tổng **113 requests/305 assertions**, zero failures. S3 backend trong runtime là HTTP fake, refriend là SQL fixture; chưa live provider hoặc accept invitation E2E.

Backend C: POST messages multipart, image-only/caption optional, 1–5 ảnh, 5 MiB/ảnh, JPG/PNG/WebP, **40 megapixel Owner đã duyệt B21**, private GET attachment, S3-compatible adapter default disabled, ordered SHA-256 retry dùng chung namespace text, atomic DB/recheck/cleanup, Swagger cả JSON và multipart. Network storage ngoài DB transaction; uncertain commit không xóa mù object, reconciliation thủ công. Giữ V1–V4 và interface → impl → internal JPA.

[Contract ảnh](../docs/planning/wardrobe/EP06_TASK10_CHAT_IMAGES_CONTRACT_PROPOSAL.md), [báo cáo ảnh/runtime](../docs/qa/wardrobe/EP06_TASK10_IMAGES_AND_RUNTIME_IMPLEMENTATION_REPORT.md), [HTTP ảnh guide](../postman/EP06_CHAT_IMAGES_RUN_GUIDE.md), [HTTP text guide](../postman/EP06_CHAT_MESSAGES_RUN_GUIDE.md), [WS guide](../postman/EP06_CHAT_WEBSOCKET_RUN_GUIDE.md), [runner](../scripts/ep06-chat-runtime/README.md).

Owner yêu cầu setup account/provider/bucket sau: [cloud setup](../docs/operations/wardrobe/EP06_CHAT_IMAGES_CLOUD_SETUP.md). Chưa có tài nguyên/chi phí cloud; không local fallback, Compose hoặc DB persistent. Gateway hiện có đã chạy được full native WS, không sửa route. Chưa sửa source Khánh/Quân/Vi/Gateway.

Đọc [plan](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [index](00_START_HERE.md). Phần v4.1 bên dưới giữ nguyên để truy vết; dependency Wardrobe sharing cũ không áp dụng.

---

## Baseline v4.1 — superseded

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
