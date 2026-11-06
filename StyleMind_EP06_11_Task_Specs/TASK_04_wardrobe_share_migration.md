# EP06 — Task 04: Share Outfit DONE_LOCAL

**v4.13 — Share Outfit DONE_LOCAL, 2026-10-29 (backend/local).** Owner duyệt S1–S4, API cutover và V6; mời email giữ PRIVATE, Owner tự chọn FRIENDS. FRIENDS cho toàn bộ current friends; PRIVATE chỉ Owner; PUBLIC guest, OFF/ON cùng link. Email-grant/feed cũ trả 410; legacy rows giữ nguyên. V1–V5 không đổi.

Wardrobe regression **314/314, 33 classes, 0 failures/errors/skipped**. Bốn JAR thật + Mailpit/PostgreSQL disposable: invitation → Register/verify/login → accept → PRIVATE vẫn ẩn → Owner chọn FRIENDS → unfriend/refriend pass; native WS/ảnh regression pass. Newman **6 collections / 8 runs / 155 requests / 380 assertions**, gồm Share Outfit **32 requests / 55 assertions**, không failures. Commerce catalog và image storage dùng HTTP fixture trong lượt runtime này; không là Commerce/cloud end-to-end mới.

**Exit của Task 04:** V6 thêm visibility PRIVATE/FRIENDS/PUBLIC; backfill public token, giữ grants/items/Friends/Chat, fresh/seeded-upgrade/preflight/constraints/JPA pass. Không sửa V1–V5 hoặc áp DB lâu dài.

[Báo cáo requirement mapping/files/evidence](../docs/qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md), [run guide](../postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md). Còn browser/frontend acceptance riêng; không production readiness. Các nội dung toàn Wardrobe/PLANNING/NOT APPROVED bên dưới là lịch sử đã superseded.

---
## Checkpoint trước implementation — giữ nguyên để truy vết

# EP06 — Task 04: Share Outfit DB — PLAN v4.2

**2026-10-29 — v4.12 hiện hành: PLANNING / PROPOSED, chưa duyệt migration.** Owner mở lại review Share Outfit. [Scope V6/DDL](../docs/planning/wardrobe/EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md) đề xuất Outfit.visibility PRIVATE/FRIENDS/PUBLIC, backfill public có sẵn, default PRIVATE, giữ email-grant rows nhưng không dùng cấp quyền. Không tạo wardrobe hoặc share-per-friend table, không sửa V1–V5. Chỉ code V6/Entity/Repository/tests sau scope approval; NOT APPROVED và không báo Task 04 Done. [Contract chung](../docs/planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md). Nội dung ON_HOLD/v4.1 dưới là checkpoint trước khi mở lại planning.

**Cập nhật 2026-10-24. Trạng thái: ON_HOLD — Owner yêu cầu chưa đụng phần share Outfit Khánh.**

Nghiệp vụ đã đổi sang từng Outfit và toàn bộ bạn bè. Chưa tạo wardrobe/settings/WardrobeShare hoặc sửa outfit/outfit_share/V1/V2. Không còn là prerequisite của Friends/Chat. Scope DB/API Outfit mới chỉ chốt khi Owner mở lại US-06.02.

Đọc [plan hiện hành](../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [index 11 task](00_START_HERE.md). Task 01 DB/JPA DONE_LOCAL; Task 02/03 PARTIAL với ba HTTP APIs list friends/outgoing/unfriend, Swagger/Postman và 29 tests mới. Gate hiện hành 131 test pass, 0 skipped. [Báo cáo Task 01](../docs/qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md) và [Task 02](../docs/qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md) ghi rõ scope; invitation integration/Chat chưa triển khai. Nội dung v4.1 bên dưới được giữ nguyên làm traceability; các rule/dependency cũ không phải hướng dẫn implementation.

---

## Baseline v4.1 — superseded

# STYLEMIND — EP-06 — TASK 04/11: PostgreSQL migration: WardrobeShare + Visibility

**User Story:** US-06.02 – Share Wardrobe
**Task gốc:** DB: WardrobeShare migration, visibility PRIVATE/PUBLIC/PROTECTED.
**Spec phiên bản:** v4.1 — thứ tự theo dependency, cập nhật 2026-10-21.
**Target:** wardrobe-community-service — Java 21 / Spring MVC / PostgreSQL.
**Trạng thái:** DEFERRED — cần chốt mô hình toàn Wardrobe trên schema Outfit mới
**Giai đoạn:** Sau handoff mô hình/schema; trước API sharing và Chat DB
**Tiền đề / thứ tự:** Khánh/Owner thống nhất đại diện/key/settings Wardrobe, mapping Outfit, tương thích share cũ và persistence convention; duyệt scope migration theo history hiện có.

## LÀM TRƯỚC / LÀM SAU

- **Có thể làm trước:** Đối chiếu V1/V2 Outfit, soạn gap/handoff và DDL đề xuất của Tín; chưa ghi migration deploy được.
- **Chỉ làm sau:** Khi đủ contract/scope: migration forward-only tiếp theo và PostgreSQL fresh/upgrade/concurrency; rồi Task 08/05.
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

Bổ sung persistence cho visibility và email sharing TOÀN Wardrobe sau khi thống nhất mô hình với phần Outfit của Khánh. Task này không tự tạo Wardrobe nền thay Khánh.

## HIỆN TRẠNG SAU PULL VÀ PHẠM VI

- V1__create_outfit_tables.sql tạo outfit/outfit_item và ghi rõ My Wardrobe là tập Outfit của account; không có bảng wardrobe. V2__add_outfit_sharing.sql thêm outfit_share/public link cho từng Outfit.
- Baseline EP06 cũ từng tạo wardrobe/wardrobe_share và Chat chỉ chạy test cô lập, đã được Owner yêu cầu rút. Các kết quả cũ không làm task này Done trên source mới.
- Chốt đại diện toàn tủ, key và vị trí lưu visibility/grants; cách liên kết Outfit hiện tại/tương lai. Tên bảng/cột/FK cụ thể là TBD trước khi duyệt DDL, không mặc định parent wardrobe tồn tại.
- Phần của Tín cần grant ID, Wardrobe key đã thống nhất, normalized recipient email, nullable stable Customer ID, ACTIVE/REVOKED, created/updated/revoked timestamps; unique Wardrobe-key + normalized email; lookup/index phù hợp.
- Giữ default PRIVATE và uniqueness/lifecycle đã duyệt trên mô hình được thống nhất; normalization theo Identity trim/lowercase Locale.ROOT, không thêm quy tắc Gmail dot/plus. Recipient chưa có account vẫn hợp lệ.
- Không đổi OutfitShare, không xóa share/link cũ, không đặt visibility trên mỗi Outfit để giả làm chia sẻ toàn tủ. Nếu cần thay đổi mapping/schema Khánh, bàn giao và duyệt scope rõ trước khi sửa.
- Không public-token schema, Chat, provisioning Register hoặc business HTTP trong task này trừ scope riêng được duyệt.

## CÁC BƯỚC TRIỂN KHAI

1. Đối chiếu source/history V1/V2 hiện tại và contract toàn Wardrobe; xác định phần cần thêm, không dựa vào DDL EP06 đã rút.
2. Soạn DDL/data compatibility plan; chốt scope migration và persistence convention trước implementation.
3. Dùng version tiếp theo phù hợp history khi bắt đầu; không cố định V1/V2 hoặc sửa migrations Outfit. Không IF NOT EXISTS/auto-baseline để che schema lệch.
4. Kiểm chứng PostgreSQL mới và upgrade từ schema Outfit seeded: bảo toàn Outfit/Item/Share/public token và checksum baseline.
5. Test FK/unique/status, concurrent duplicate và migrate no-op; chạy regression hiện hành. Không DB persistent/Compose.

## API / DATABASE BOUNDARY

Chỉ schema và persistence tối thiểu trong scope được duyệt. Customer là logical reference; không cross-service FK hoặc SQL. Task 05 phụ trách API.

## ACCEPTANCE CRITERIA (Definition of Done)

- Có contract Wardrobe và migration scope được duyệt; đủ ba visibility, PRIVATE default trên mô hình thật.
- Grants toàn Wardrobe hỗ trợ unregistered email, ACTIVE/REVOKED và unique đúng key kể cả cạnh tranh.
- Không giả định OutfitShare là WardrobeShare; giữ dữ liệu và behavior public contract cũ theo phương án tương thích được duyệt.
- Fresh/upgrade/no-op/regression có kết quả thực chạy; forward-fix strategy rõ ràng.
- Chưa đủ contract/DDL approval thì trạng thái vẫn DEFERRED, không báo schema tích hợp Done.

## KHÔNG ĐƯỢC LÀM / OUT OF SCOPE

Không tự triển khai Wardrobe/Outfit CRUD, thay schema thuộc Khánh, phục hồi V1/V2 EP06 cũ, drop/clean/history rewrite hoặc làm API Task 05.

## BÁO CÁO KHI KẾT THÚC PHẦN ĐƯỢC GIAO

Báo bằng tiếng Việt: phần task đã thực hiện, file thay đổi, requirement → validation/authorization/transaction/side effect, commands và kết quả thực chạy, scope/environment chạm tới, phần còn chờ và bên phụ trách. Chỉ đánh dấu toàn task Done khi đủ acceptance của mọi phần; không báo Task 06 Done chỉ vì xong phần A, hoặc báo Task 05 Done bằng public metadata/danh sách giả. Không tự chuyển task kế tiếp nếu Owner chưa giao.
