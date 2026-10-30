# EP06 — Task 08: Chat migration theo cặp bạn bè — v4.3

**Cập nhật 2026-10-27. Trạng thái: DONE_LOCAL — scope DB/JPA đã duyệt.**

**Kết quả 2026-10-27:** Owner duyệt [DDL Chat theo Friendship](../docs/planning/wardrobe/EP06_TASK08_CHAT_DATABASE_SCOPE.md). Đã có V4, bốn Entity/Repository Session/Participant/Message/Attachment và 22 PostgreSQL tests. Gate **153 pass, 0 failed/error/skipped**, gồm 131 regression; [báo cáo triển khai](../docs/qa/wardrobe/EP06_TASK08_IMPLEMENTATION_REPORT.md). Chưa áp DB lâu dài; API/WS/upload thuộc 09–11 chưa Done.

ChatSession unique cặp tài khoản, đúng hai participants, Message membership/order/idempotency theo contract được duyệt. Không FK/key Wardrobe/Outfit hoặc phụ thuộc Task 04. Unfriend giữ session/messages. Không dùng lại version V1/V2 cũ; Owner đã chọn WebSocket, văn bản + ảnh và Spring Data JPA; giới hạn text/ảnh đã chốt theo B15/B17/B18, schema/metadata ảnh đã có V4; API payload, WebSocket và storage vẫn cần contract.

Đọc [plan hiện hành](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [index 11 task](00_START_HERE.md). Task 01 DB/JPA DONE_LOCAL; Task 02/03 PARTIAL với ba HTTP APIs list friends/outgoing/unfriend, Swagger/Postman và 29 tests mới. Checkpoint Task 02 A là 131 test pass; gate sau Task 08 là 153 pass, 0 skipped. [Báo cáo Task 01](../docs/qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md) và [Task 02](../docs/qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md) ghi rõ scope; invitation integration và API Chat chưa triển khai. Nội dung v4.1 bên dưới được giữ nguyên làm traceability; các rule/dependency cũ không phải hướng dẫn implementation.

---

## Baseline v4.1 — superseded

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
