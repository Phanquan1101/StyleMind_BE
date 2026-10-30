# EP06 của Tín — Plan và tiến độ v4.13

**v4.13 — Share Outfit DONE_LOCAL, 2026-10-29 (backend/local).** Owner duyệt S1–S4, API cutover và V6; mời email giữ PRIVATE, Owner tự chọn FRIENDS. FRIENDS cho toàn bộ current friends; PRIVATE chỉ Owner; PUBLIC guest, OFF/ON cùng link. Email-grant/feed cũ trả 410; legacy rows giữ nguyên. V1–V5 không đổi.

Wardrobe regression **314/314, 33 classes, 0 failures/errors/skipped**. Bốn JAR thật + Mailpit/PostgreSQL disposable: invitation → Register/verify/login → accept → PRIVATE vẫn ẩn → Owner chọn FRIENDS → unfriend/refriend pass; native WS/ảnh regression pass. Newman **6 collections / 8 runs / 155 requests / 380 assertions**, gồm Share Outfit **32 requests / 55 assertions**, không failures. Commerce catalog và image storage dùng HTTP fixture trong lượt runtime này; không là Commerce/cloud end-to-end mới.

| Task | Hiện hành | Phạm vi |
| --- | --- | --- |
| 01/02/03 | DONE_LOCAL — backend | Friends DB/API/verified email/invitation/SMTP/tests |
| 04 | DONE_LOCAL | V6 + Entity/Repository, fresh/seeded upgrade/constraints/preflight |
| 05/06 | DONE_LOCAL — backend | Owner settings, friend read, public aliases, legacy 410, authorization/recheck, Swagger |
| 07 | DONE_LOCAL — backend | Unit/HTTP/PostgreSQL/concurrency/regression + actual Gateway/Newman |
| 08/09/10 | DONE_LOCAL — backend/cloud | Friends-based Chat; cloud B2 evidence giữ checkpoint riêng |
| 11 | PARTIAL | Còn browser/UI acceptance và phối hợp frontend |

[Báo cáo/files/commands](../../qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md), [Postman](../../../postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md), [cutover](../../operations/wardrobe/EP06_OUTFIT_SHARING_CUTOVER.md). Không còn quyết định nghiệp vụ Share Outfit chờ duyệt. Không deploy/DB lâu dài/Git push; còn browser/frontend và 15 lỗi baseline Identity từ checkpoint trước (không sửa trong lượt này). Không chạy mixed old/new Wardrobe reader. Các bảng/status/NOT APPROVED bên dưới là checkpoint trước approval/implementation.

---
## Checkpoint trước implementation — giữ nguyên để truy vết

# EP06 của Tín — Plan và tiến độ v4.12

**2026-10-29 — v4.12: mở lại planning Task 04–07 theo Owner “oke”.** [Share Outfit contract proposal](EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md) và [scope V6/DDL](EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md) đã được soạn để review, **NOT APPROVED**, chưa có migration/API implementation mới. Cần chốt S1 (default/luồng mời), S2–S3 (dữ liệu/API email-share cũ), S4 (public link) và scope V6. Thứ tự sau approval: 04 → 05+06 → 07. Không suy approval schema hoặc thay đổi quyền cũ từ việc mở lại planning.

**2026-10-29 — Task 10 DONE_LOCAL trong phạm vi backend/cloud.** Owner chọn Backblaze B2; IDE/Docker wiring đã bổ sung. Wardrobe 294/294 pass; default fake runtime và live B2 runtime đều pass (mỗi mode 1 JUnit + Newman 123 requests/325 assertions). B2 upload/read/private/retry/WS/unfriend/refriend và cleanup exact 8 keys/9 versions pass; 26 anonymous GET probes bị chặn, không phải 26 ảnh khác nhau. [Báo cáo cloud](../../qa/wardrobe/EP06_BACKBLAZE_VERIFICATION_2026-10-29.md), [setup IDE/Docker](../../operations/wardrobe/EP06_BACKBLAZE_B2_SETUP.md).

Task 11 còn browser/UI acceptance và phối hợp frontend; không deploy/áp DB lâu dài. Identity 15 lỗi baseline vẫn mở. Các kết quả ghi ngày 28 bên dưới là checkpoint trước live cloud, được bổ sung bởi evidence ngày 29 này; business rules giữ nguyên.

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser.

## 1. Trạng thái task

| Task | Trạng thái | Bằng chứng / còn lại |
| --- | --- | --- |
| 01 | DONE_LOCAL | V3/Friends nền; V5 bổ sung Task 02 đã kiểm thử cô lập |
| 02 | DONE_LOCAL — backend | List/outgoing/unfriend + send/resend/incoming/hai đường accept, V5/JPA, Identity/Notification, Swagger/Postman thực |
| 03 | DONE_LOCAL — backend | Unit/HTTP/PostgreSQL/transaction/concurrency và actual invitation/auth/SMTP/Chat/refriend E2E + Newman |
| 04–07 | PLANNING — chờ duyệt contract/DDL | Mỗi Outfit PRIVATE/FRIENDS/PUBLIC; concrete proposal S1–S4 + V6, chưa code/migrate |
| 08/09 | DONE_LOCAL | V4/session, regression 294 hiện hành giữ behavior |
| 10 | DONE_LOCAL — backend/cloud | Text/WS/private images + B2 thật/IDE/Docker wiring; xem cloud report, không là production readiness |
| 11 | PARTIAL toàn task | Native WS/Gateway/JWT/SMTP/invitation/refriend/Newman/live B2 đã chạy; còn browser/UI acceptance |

## 2. Nghiệp vụ đã chốt — không hỏi lại

| ID | Quyết định của Owner | Áp dụng |
| --- | --- | --- |
| B1 | Lời mời kết bạn được gửi theo email; người nhận có thể chưa có tài khoản | US-06.01 |
| B2 | Lời mời ở PENDING trước khi người nhận hoàn tất chấp nhận; sau đó ACCEPTED và hai tài khoản trở thành bạn bè | US-06.01 |
| B3 | Người nhận có tài khoản đăng nhập đúng email; chưa có thì đăng ký/xác minh email qua Identity hiện có rồi tiếp tục nhận lời mời | US-06.01 |
| B4 | Không tự nhận thay lời mời bằng email client khai; cần ràng buộc đúng người nhận, link được chuyển tiếp không cấp quyền cho người khác | US-06.01 |
| B5 | Một cặp bạn bè có một hội thoại 1–1 chung; mở lại dùng cùng hội thoại; Chat độc lập với Wardrobe/Outfit | US-06.03 |
| B6 | Hai thành viên đã ACCEPTED mới có quyền Chat; biết chatId không đủ để truy cập | US-06.03 |
| B7 | Hủy kết bạn: **cả hai không đọc lịch sử và không gửi tin mới**, hệ thống giữ hội thoại và tin nhắn | US-06.01 + US-06.03 |
| B8 | Share ở cấp từng Outfit; chế độ Bạn bè cho tất cả bạn bè xem, không có danh sách chọn/loại trừ riêng; Private vẫn chỉ chủ sở hữu xem (Owner xác nhận 28/10) | US-06.02, **đã mở planning; contract/DDL mới chưa duyệt** |
| B9 | Lời mời kết bạn có hạn **7 ngày** | US-06.01 |
| B10 | Gửi trùng khi còn PENDING trả lời mời hiện có; có thao tác gửi lại email riêng. Gửi mail lỗi giữ PENDING, báo chưa gửi thành công và cho thử lại | US-06.01 |
| B11 | Sau khi kết bạn lại thành công, dùng lại hội thoại cũ và khôi phục quyền đọc **toàn bộ lịch sử** | US-06.01 + US-06.03 |
| B12 | Chat hỗ trợ **văn bản và ảnh**. Giới hạn văn bản theo B18, ảnh theo B15/B17; storage private theo contract Task 10 C; chưa có yêu cầu file đính kèm khác | US-06.03 |
| B13 | Owner chọn **WebSocket** cho Chat thời gian thực | US-06.03 |
| B14 | Owner chọn **Spring Data JPA**, theo convention source hiện tại và Controller → Service → Repository | Friends/Chat |
| B15 | Owner chọn **tối đa 5 ảnh trong một lần bấm Gửi/một tin nhắn**. Gửi tiếp vẫn ở cùng hội thoại của cặp bạn bè; đây không phải tổng số ảnh của cả hội thoại | US-06.03 |
| B16 | Owner yêu cầu **Swagger/OpenAPI** cho các HTTP API Friends/Chat: JWT Authorize, request/response mẫu, validation/mã lỗi, phân trang và upload ảnh theo contract. WebSocket có thêm contract/hướng dẫn kết nối và gửi/nhận riêng | US-06.01 + US-06.03 |
| B17 | Owner duyệt **tối đa 5 MB/ảnh**, hỗ trợ **JPG/JPEG, PNG, WebP**; kết hợp B15 là tối đa 5 ảnh/tin | US-06.03 |
| B18 | Owner duyệt **tối đa 4.000 ký tự cho một tin nhắn văn bản** | US-06.03 |
| B19 | **Gửi lại email mời tạo link mới, vô hiệu hóa link cũ và tính lại hạn 7 ngày từ lúc gửi lại** | US-06.01 |
| B20 | **Hủy kết bạn ẩn hội thoại khỏi danh sách của cả hai; kết bạn lại hiện cùng hội thoại cũ** | US-06.03 — Owner chốt 2026-10-27 |
| B21 | Owner duyệt **tối đa 40 megapixel/ảnh** ngày 2026-10-27; kích thước dương và width × height ≤ 40.000.000 | US-06.03 |
| B22 | A đã mời B còn hiệu lực: B chỉ Chấp nhận, không mời ngược/gửi thêm email hoặc tự động kết bạn | US-06.01 |
| B23 | Unfriend vô hiệu mọi pending cũ của cặp; muốn kết bạn lại cần lời mời mới | US-06.01 |
| B24 | Accept lại khi còn bạn: báo đã là bạn, không ghi; sau unfriend: báo đã sử dụng, không khôi phục quan hệ | US-06.01 |
| B25 | Cùng sender/email: tối thiểu 30 giây giữa các lần thử gửi; tính cả lần đầu/lỗi/timeout, không tự mở quyền resend | US-06.01 |
| B26 | Bỏ hai cap đề xuất 3 lần/giờ và 20 lần/giờ; hạn mức provider vẫn áp dụng | US-06.01 |
| B27 | PENDING còn hạn và email đã SMTP tiếp nhận thì không gửi thêm/gửi lại, kể cả qua 30 giây | US-06.01 |
| B28 | Hết hạn 7 ngày hoặc gửi thất bại được chủ động gửi lại, link mới/hạn mới theo B19 và điều kiện B23/B25 | US-06.01 |

B14 là approval JPA cho **Friends/Chat** thay JDBC cũ đúng phạm vi này. B21 đã được Owner trả lời “Đồng ý tối đa 40 megapixel/ảnh”; không còn NOT APPROVED. 5 MB được triển khai là 5 × 1024 × 1024 bytes. Văn bản/caption theo contract là 4.000 Unicode code points.

PENDING/ACCEPTED thuộc lời mời. Quyết định share toàn Wardrobe/People with Access/Chat theo Wardrobe ở v4.1 đã bị thay thế; Functional Requirements/reference cũ giữ làm nguồn lịch sử, không phủ định B1–B28. Xem [quyết định Owner ngày 28](EP06_FRIENDS_INVITATION_RULES_2026-10-28.md), [scope V5](EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md) (APPROVED_SCOPE, chưa kiểm thử source mới) và [thiết kế API](EP06_TASK02_INVITATION_API_DESIGN.md) để đối chiếu implementation mới.



## 3. Verification hiện hành

- Focused: Identity lookup 12 + Wardrobe 44, không failure/error/skip.
- Regression: Notification 40/8 classes pass; Wardrobe 294/31 classes pass. Identity 161/22 classes có 15 failures, 0 errors/skips; so tên với baseline probe: 0 khác biệt. Không sửa hoặc che lỗi cũ.
- Runtime: 4 JAR Notification/Identity/Wardrobe/Gateway, 2 PostgreSQL và Mailpit disposable, 1 JUnit scenario pass. Người nhận mới Register/verify/login thật và chưa có profile vẫn accept được; old-used link sau unfriend bị chặn; lời mời mới khôi phục cùng Chat/history.
- Newman 5 collections: 123 requests/325 assertions, 0 failures. Invitation 10 requests/20 assertions chạy đủ thành 3 fixture main/retry/link; SMTP rejected → retry sau 31 giây → nhận link thật.
- Storage ảnh dùng real S3 SDK tới HTTP fake; không là cloud provider pass. Browser chưa chạy.

[Báo cáo/commands/file list](../../qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md), [Postman](../../../postman/EP06_FRIEND_INVITATIONS_RUN_GUIDE.md), [runtime runner](../../../scripts/ep06-chat-runtime/README.md). Checkpoint 263/248 và Newman 113/305 giữ làm lịch sử.

## 4. Phạm vi và việc tiếp theo

Đã sửa runner truyền script thành file UTF-8/LF; fixture timestamp hợp lệ; rollback dùng lỗi ghi PostgreSQL thật; test cũ không còn đòi API chưa tồn tại. Các production business rules, V1–V4, DB persistent và code Outfit/Commerce/Payment/Stylist không đổi trong lượt verification. Không Git/Compose/real email/cloud resource; test resources đã dọn.

Backblaze B2 đã cấu hình và pass live smoke ngày 29 theo [báo cáo](../../qa/wardrobe/EP06_BACKBLAZE_VERIFICATION_2026-10-29.md). Task 11 còn phối hợp frontend invitation page/browser STOMP/upload/private-image/reconnect/unfriend/refriend. Share Outfit 04–07 đã mở planning; duyệt [contract](EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md)/[V6](EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md) trước triển khai 04 → 05+06 → 07. Identity baseline 15 lỗi là công việc riêng. B2 production compensation tạo delete marker; reconciliation hidden versions/orphans cần quy trình riêng, không tự xóa ảnh đang được tham chiếu.

Một transaction advisory lock chung serialize Friends writes, không giữ qua network; SMTP+DB không exactly-once/durable outbox. Giữ các giới hạn này khi đánh giá triển khai nhiều instance/throughput; không nhận production readiness từ local tests. Workbook Backlog vẫn không có tại đường dẫn AGENTS; không sửa workbook.

---
## Lịch sử v4.1 — superseded, không dùng làm hướng dẫn implementation

Nội dung ngày 2026-10-21 dưới đây được giữ nguyên để truy vết. Quyết định email sharing một chiều thay Friendship, share toàn Wardrobe và Chat gắn Wardrobe đã bị B1–B8 thay thế. Trạng thái/dependency cũ không phải tiến độ hiện hành.

# EP06 của Tín — Kế hoạch sau pull 2026-10-21

**Phiên bản: v4.1; ngày 2026-10-21 (Asia/Bangkok).** Owner yêu cầu sửa task/spec để làm phần độc lập trước, phần phụ thuộc sau. File này phản ánh source sau pull lên 418c421 theo log Owner cung cấp, thay kế hoạch chờ dependency ngày 17/10. Không duyệt ngầm business/schema/integration mới.

## 1. Phạm vi và tiến độ

Tín chỉ phụ trách 11 task EP-06. Khánh phụ trách Wardrobe/Outfit; Bình Commerce; Quân Identity/setup; Vi Notification. Cùng service không cho phép tự làm task của người khác. Bảng phân công Owner đã gửi là căn cứ; workbook ở đường dẫn AGENTS.md chưa có trên máy, không chỉnh workbook/status thành viên khác.

- **01:** DONE review/decision, đã chọn email sharing thay Friendship.
- **06 A:** READY để bắt đầu policy/unit; chưa có implementation trong đợt chỉnh docs này.
- **04/08:** DEFERRED, cần mô hình/key toàn Wardrobe và DDL scope tương thích baseline mới. Evidence DB cũ chỉ là lịch sử.
- **02/03/05/07/09/10/11 và 06 B:** WAITING theo từng dependency; chưa coi các API Outfit hiện có là tính năng toàn Wardrobe/Chat đã xong.

Chỉ 1 task đã hoàn tất review; 2 task DB chờ tích hợp lại; 8 task API/test chưa hoàn tất, trong đó phần 06 A làm được trước. Không báo 3/11 Done hoặc gán kết quả 13/35 test cũ cho code sau pull.

## 2. Inventory sau pull: source khác giả định cũ

| Nguồn quan sát | Nội dung hiện tại | Tác động |
| --- | --- | --- |
| [Outfit V1](../../../wardrobe-community-service/src/main/resources/db/migration/V1__create_outfit_tables.sql) | outfit có owner_account_id; outfit_item tham chiếu outfit. Comment nêu My Wardrobe là tập Outfit, không có bảng wardrobe | Không thể mặc định FK wardrobe_id có parent; cần thống nhất root/key/settings của toàn tủ với Khánh/Owner |
| [Outfit sharing V2](../../../wardrobe-community-service/src/main/resources/db/migration/V2__add_outfit_sharing.sql) | outfit_share và public_token/public_enabled_at theo từng Outfit | Không phải WardrobeShare; giữ baseline và public contracts hiện tại cho tới khi chốt compatibility |
| [OutfitShareService](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/OutfitShareService.java) | Email grants/public link theo outfitId; disable xóa token | Không sao chép lifecycle/xóa token hay giới hạn share thành mặc định EP06; link toàn Wardrobe phải dùng lại khi PUBLIC bật lại theo Owner |
| [IdentityDirectoryClient](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/identity/IdentityDirectoryClient.java) và [Identity DTO](../../../identity-service/src/main/java/com/stylemind/identity/dto/internal/InternalAccountItem.java) | Batch lookup có ID/email/display name/account status, không verified flag/timestamp | Dependency đã có một phần; cần contract verification, không suy ACTIVE là đã verified |
| [Notification controller](../../../notification-service/src/main/java/com/stylemind/notification/controller/InternalOutfitShareInvitationController.java) | Thư mời Outfit, 202 SMTP accepted, 503/504 failures | Chưa là lời mời toàn Wardrobe; bàn giao Vi về template/URL/payload/result contract |
| [Wardrobe pom](../../../wardrobe-community-service/pom.xml) và [AGENTS.md](../../../AGENTS.md) | Source dùng Spring Data JPA/Lombok, instruction hiện yêu cầu Spring JDBC | Ghi nhận conflict trước persistence integration; policy thuần không bị chặn, không tự convert code Khánh |

Outfit CRUD, shared/public controllers, Commerce/Identity/Notification clients và integration test sources đã có sau pull. Đây là inventory source, không phải owner approval cho behavior EP06 hoặc bằng chứng tests đã chạy. Governance cũ còn mô tả scaffold/JDBC; các mô tả implementation đó cần được đối chiếu source, không dùng để xóa code mới.

## 3. Thứ tự làm việc

### Bước 1 — Làm ngay phần độc lập

**Task 06 A:** policy thuần nhận facts server-trusted, unit matrix Owner/Viewer, PRIVATE/PROTECTED/PUBLIC, verified/unverified, ACTIVE/REVOKED, token valid/invalid, participant isolation và dữ liệu thiếu. Không thêm HTTP/DB/repository hoặc đổi quyền các endpoint hiện tại. Unknown Owner suspended-history/identity-reuse branches không được tự cấp quyền.

Chuẩn bị handoff và cases cho các task khác; không viết stub service production để giả dependency sẵn sàng. Unit fixture không chứng minh integration/HTTP authorization. Toàn Task 06 vẫn PARTIAL sau A.

### Bước 2 — Khi mô hình Wardrobe và persistence sẵn sàng

Khánh/Owner thống nhất đại diện toàn tủ, key/settings, mapping Outfit hiện tại/tương lai và compatibility với share cũ. Chốt persistence convention và scope schema. Sau đó **04 → 08** với migration version tiếp theo phù hợp history, PostgreSQL fresh/seeded-upgrade/concurrency và regression.

Không mặc định phải có bảng wardrobe, không tự chọn virtual-owner model thay Owner, không dùng lại V1/V2 EP06 đã rút vì V1/V2 hiện tại thuộc Outfit Khánh. Không ALTER/drop/share migration cũ ngoài scope được duyệt.

### Bước 3 — Khi trusted identity và invitation contracts sẵn sàng

Sau 04 và contracts: **05 A** visibility/share/revoke cùng guard của **06 B** → **02** People with Access → **03** tests. Owner checks và live authorization phải được gắn khi API mở, không chờ toàn 06 xong mới bảo vệ endpoint.

Identity lookup cần chứng minh email verified hiện tại. Notification cần Wardrobe invitation thật, direct Wardrobe URL và kết quả delivery trung thực. Không tự sửa Identity/Notification thay chủ task; reuse transport không có nghĩa reuse được business contract.

### Bước 4 — Hoàn tất public/shared nội dung và nested resources

Khi public-link storage/create/copy/lifecycle và resource mapping/compatibility đã chốt: **05 B + 06 B resource coverage → 07**. Outfit đã có CRUD/read source, không làm lại module Khánh. Kiểm tra direct outfit/item/media access, public DTO và grants toàn Wardrobe cho Outfit tạo thêm sau này.

### Bước 5 — Chat, có thể chạy song song bước 4

Khi có 08, 05 current grant/visibility, verified identity và canChat guard đã kiểm chứng trong 06 B: **09 → 10 → 11**. Chốt REST/WebSocket và text/media trước phần liên quan. Không bắt Chat chờ toàn bộ Task 07, Catalog, AI hoặc Payment; cũng không lấy unit policy thay live authorization.

## 4. Handoff tối thiểu

| Bên phụ trách | Cần nhận/chốt | Task phụ thuộc |
| --- | --- | --- |
| Khánh/Owner | Đại diện/key/settings Wardrobe, Outfit mapping, existing share/public compatibility, migration history | 04/05/06 B/08 và use cases dựa vào root |
| Quân | Current identity/email verification contract và service auth/error semantics; phối hợp Gateway routing khi mở API | 05/06 B/09/10, HTTP handover |
| Vi | Wardrobe invitation template/URL/payload, accepted/failed/unknown và retry semantics | 05 invite/07 delivery cases |
| Bình | Catalog/variant contract đã có source, phối hợp khi Outfit shared projection cần dữ liệu thật | Chủ yếu qua resource của Khánh, không blocker chung cho Chat |
| Owner | Persistence convention, compatibility, public-link recovery/rotation, Chat transport/types và Owner suspended-history | Chỉ phần implementation đụng quyết định tương ứng |

## 5. Quyết định đã duyệt và các phần còn mở

Giữ email một chiều thay Friendship; một My Wardrobe/account mặc định PRIVATE, có thể rỗng; share cả tủ và Outfit tương lai; PRIVATE mời email → PROTECTED; về PRIVATE giữ grant/history và chặn Viewer; bật PUBLIC lại dùng link cũ; public-only VIEW; revoke giữ Chat và reshare cùng stable Customer tiếp tục session.

Đại diện root và tương thích OutfitShare chưa có owner decision cho baseline mới. Verified-email/invitation/persistence contracts, storage/create/copy/rotation, transport/types/4.000-character proposal, Owner suspended-history, provisioning/failure/retry/account cũ và email-reuse policy còn TBD. Chỉ hỏi khi triển khai phần liên quan; không hỏi lại các rule đã chốt.

## 6. Bàn giao và bằng chứng

Owner đã khôi phục riêng task/spec và gửi Git status với đúng bộ tài liệu untracked; code mới tracked sạch. Đợt này chỉ sửa tài liệu và giữ runtime/pom/config/migrations của các thành viên khác. Không apply toàn stash, không bật lại cấu hình Flyway cũ, không Git/Compose/persistent DB.

Mỗi story HTTP sau này có Postman JSON/run guide, validation cấu trúc tách execution. Test theo phần thay đổi, regression trước smoke được phép; không tính skipped/deferred là pass. Bản docs này không tuyên bố source vừa pull đã build/test pass.

File/task chi tiết tại [00_START_HERE](../../../StyleMind_EP06_11_Task_Specs/00_START_HERE.md). Root spec và MASTER cùng chứa đủ 11 task v4.1. Kiểm chứng tài liệu trong [báo cáo cập nhật](../../qa/wardrobe/EP06_TASK_SPEC_REORGANIZATION_2026-10-21.md).
