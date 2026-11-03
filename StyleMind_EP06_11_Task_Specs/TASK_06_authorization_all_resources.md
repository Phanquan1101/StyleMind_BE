# EP06 — Task 06: Share Outfit DONE_LOCAL

**v4.13 — Share Outfit DONE_LOCAL, 2026-10-29 (backend/local).** Owner duyệt S1–S4, API cutover và V6; mời email giữ PRIVATE, Owner tự chọn FRIENDS. FRIENDS cho toàn bộ current friends; PRIVATE chỉ Owner; PUBLIC guest, OFF/ON cùng link. Email-grant/feed cũ trả 410; legacy rows giữ nguyên. V1–V5 không đổi.

Wardrobe regression **314/314, 33 classes, 0 failures/errors/skipped**. Bốn JAR thật + Mailpit/PostgreSQL disposable: invitation → Register/verify/login → accept → PRIVATE vẫn ẩn → Owner chọn FRIENDS → unfriend/refriend pass; native WS/ảnh regression pass. Newman **6 collections / 8 runs / 155 requests / 380 assertions**, gồm Share Outfit **32 requests / 55 assertions**, không failures. Commerce catalog và image storage dùng HTTP fixture trong lượt runtime này; không là Commerce/cloud end-to-end mới.

**Exit của Task 06:** Owner A/B/C/guest/role matrix; all-friends visibility, PRIVATE counts/parent/item mutation isolation; current state pre/post downstream I/O; unfriend/refriend; no network trong DB transaction.

[Báo cáo requirement mapping/files/evidence](../docs/qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md), [run guide](../postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md). Còn browser/frontend acceptance riêng; không production readiness. Các nội dung toàn Wardrobe/PLANNING/NOT APPROVED bên dưới là lịch sử đã superseded.

---
## Checkpoint trước implementation — giữ nguyên để truy vết

# EP06 — Task 06: Outfit sharing authorization — PLAN v4.2

**2026-10-29 — v4.12 hiện hành: PLANNING / NOT APPROVED.** [Contract mục 3–5](../docs/planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md) mô tả Owner-only writes, current Friendship + mode cho friend reader, PUBLIC/token/marker cho guest, parent isolation, no PRIVATE count leak và recheck sau downstream I/O. Không giữ DB lock qua HTTP; không dùng grant email cũ hoặc biết UUID làm quyền. Guard triển khai cùng 05 sau scope/API approval; không thay quyền Chat. ON_HOLD/v4.1 dưới là checkpoint.

**Cập nhật 2026-10-24. Trạng thái: ON_HOLD cùng US-06.02; không chặn guard của Friends/Chat.**

Chưa gắn policy mới vào Outfit/Item/media/share APIs của Khánh. Authorization friend invitation/relationship nằm trong Task 02; current friendship + đúng hai thành viên Chat được triển khai/test cùng Task 09/10. Không dùng policy Wardrobe PRIVATE/PROTECTED/PUBLIC bên dưới để quyết định quyền Chat theo cặp.

Đọc [plan hiện hành](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [index 11 task](00_START_HERE.md). Task 01 DB/JPA DONE_LOCAL; Task 02/03 PARTIAL với ba HTTP APIs list friends/outgoing/unfriend, Swagger/Postman và 29 tests mới. Gate hiện hành 131 test pass, 0 skipped. [Báo cáo Task 01](../docs/qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md) và [Task 02](../docs/qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md) ghi rõ scope; invitation integration/Chat chưa triển khai. Nội dung v4.1 bên dưới được giữ nguyên làm traceability; các rule/dependency cũ không phải hướng dẫn implementation.

---

## Baseline v4.1 — superseded

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
