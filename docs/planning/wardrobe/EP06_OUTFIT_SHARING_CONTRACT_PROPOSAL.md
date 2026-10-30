# EP06 Task 04–07 — Share Outfit contract proposal

**APPROVED — 2026-10-29.** Owner trả lời “Duyệt contract và cách chuyển đổi”, “Duyệt scope V6”, sau đó chốt S1 “Giữ PRIVATE, tôi tự đổi sang FRIENDS”. S1 default PRIVATE và không tự đổi mode khi gửi mời; S2–S4/API cutover đã duyệt. Bắt đầu implementation 04 → 05+06 → 07, chỉ DB test cô lập. Các nhãn NOT APPROVED phía dưới là checkpoint proposal trước câu trả lời này, không phải trạng thái hiện hành.

**2026-10-29 — PROPOSED / NOT APPROVED.** Owner đã mở lại phần planning sau Friends/Chat. “Oke” cho phép soạn contract/DDL để review; chưa phải approval cho migration, cutover API hoặc thu hồi quyền cũ. Không có source/migration/DB thay đổi trong lượt planning này.

## 1. Căn cứ và phần đã chốt

- [Plan hiện hành, B8](EP06_WORK_PLAN_AND_PROGRESS.md): chia sẻ từng Outfit; FRIENDS cho toàn bộ bạn bè hiện tại, không chọn/loại trừ từng người. PRIVATE chỉ Owner xem; kết bạn không mở Outfit PRIVATE.
- Friends invitation/verified-email/Notification đã hoàn tất backend. Mời email không phải cấp grant Outfit riêng; người chưa có tài khoản Register/verify/login rồi chấp nhận. Không sửa lại Friendship/Chat.
- Một cặp bạn bè có một Chat, độc lập visibility Outfit. Đổi chế độ Outfit không hủy friendship, ẩn Chat hoặc xóa tin nhắn.
- Owner từng chọn dùng lại public link sau khi tắt/bật. Proposal áp quy tắc đó cho từng Outfit; không sao chép hành vi xóa token hiện tại làm quy tắc mới.
- Baseline quan sát: [V1](../../../wardrobe-community-service/src/main/resources/db/migration/V1__create_outfit_tables.sql), [V2](../../../wardrobe-community-service/src/main/resources/db/migration/V2__add_outfit_sharing.sql), [OutfitShareService](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/OutfitShareService.java), [SharedOutfitService](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/SharedOutfitService.java). Hiện chỉ có grant email và public token, chưa có ba-mode visibility. Source/test cũ không duyệt thay Owner.
- [Logical diagram hiện có](../../../database/six-service-model/04_wardrobe_community.mmd) còn Wardrobe/WardrobeShare/Chat gắn Wardrobe và không phản ánh V1–V5/decisions mới. Ghi nhận conflict, không dùng diagram để tạo lại bảng Wardrobe; không sửa diagram chung của nhóm trong lượt proposal này. Latest Owner decisions điều khiển nghiệp vụ; migration là baseline physical.

## 2. Quyết định đề xuất cần duyệt trước code

| ID | Đề xuất — NOT APPROVED cho tới khi Owner trả lời |
| --- | --- |
| S1 | Outfit mới mặc định PRIVATE. Chỉ thao tác visibility của Owner đổi chế độ; Friends invitation không tự đổi Outfit vì API Friends không có outfitId. Luồng UI: chọn FRIENDS rồi mời người chưa là bạn. Nếu Owner muốn gửi mời từ PRIVATE tự chuyển FRIENDS, cần chốt API/context riêng trước phần đó. |
| S2 | Backfill Outfit có public_token hiện hữu → PUBLIC, giữ nguyên token; các Outfit còn lại → PRIVATE. Email-grant-only không tự chuyển FRIENDS vì sẽ mở cho cả nhóm bạn. Không tạo friendship/lời mời hoặc gửi email trong migration. |
| S3 | Giữ nguyên toàn bộ bảng/bản ghi outfit_share để đối chiếu, nhưng không còn dùng grant email cấp quyền trong ba-mode model. Retire API grant/list-grant/revoke và feed shared-outfits cũ bằng 410; public-link URLs được giữ theo alias ở mục 4. Đây là thay đổi contract cần Owner duyệt, không silent compatibility. |
| S4 | PUBLIC đọc qua public token không cần tài khoản; chỉ VIEW. PRIVATE/FRIENDS vô hiệu đường public ngay dù DB giữ token. PUBLIC bật lại dùng đúng token cũ; không thêm TTL, rotation API hoặc link mới khi chỉ đổi mode. |

S1/S2/S3/S4 được trình cụ thể để Owner review; chỉ B8 và các rule Friends/Chat đã duyệt là confirmed. Khi approval có điều kiện, cập nhật tài liệu đúng phần đã duyệt, không đánh dấu cả contract approved.

## 3. Ma trận quyền và vòng đời

| Người gọi / đường truy cập | PRIVATE | FRIENDS | PUBLIC |
| --- | --- | --- | --- |
| Owner, owner APIs | Xem/sửa theo CRUD hiện tại | Như PRIVATE | Như PRIVATE |
| Bạn hiện tại, friend-outfit APIs mới | 404, không lộ metadata | VIEW | VIEW |
| Chưa là bạn/đã unfriend, friend-outfit APIs | 404 | 404 | 404; có thể dùng public link hợp lệ |
| Guest hoặc người bất kỳ qua public token | 404 | 404 | VIEW |
| Viewer gọi rename/item mutation/delete/sharing | 404 theo owner check | 404 | 404 |

Owner và friend APIs giữ JWT ROLE_CUSTOMER convention hiện có; ADMIN không tự được xem PRIVATE người khác. Không tạo account-suspension rules/live Identity security guarantees ngoài scope. Identity chỉ cung cấp presentation; email hay display name không cấp quyền. FRIENDS dùng canonical UUID pair với `unfriended_at IS NULL` trong chính DB Wardrobe.

- Khi unfriend: friend listing/detail bị chặn; dữ liệu Outfit không bị xóa, PUBLIC link vẫn độc lập. Refriend: FRIENDS/PUBLIC của người bạn hiện lại, PRIVATE vẫn ẩn. Không cần re-grant từng Outfit.
- Đổi sang PRIVATE hoặc FRIENDS: giữ public_token, đặt public_enabled_at NULL. Đổi sang PUBLIC: tạo token nếu chưa từng có, hoặc dùng lại token cũ, đặt enabled_at khi chuyển vào PUBLIC. PUBLIC → PUBLIC là no-op, trả token/timestamp hiện tại. DELETE public-link khi đã non-PUBLIC là no-op, không đổi FRIENDS thành PRIVATE.
- Public reader luôn kiểm tra visibility PUBLIC + token match + enabled_at, không chỉ kiểm tra token tồn tại. UUID/URL biết trước không thay quyền hiện tại. Xóa Outfit theo CRUD hiện tại khiến mọi đường xem trả 404.
- Owner summary/detail giữ mọi field hiện tại và thêm `visibility` để UI hiển thị mode mà không phải gọi riêng mỗi card; các response create/rename/items dùng hai DTO này cũng nhận field mới. Đây là additive DTO change trong scope approval, không giả định mọi client strict đều tương thích. `public_token` vẫn owner-only và có thể còn khi link tắt; UI không suy active từ token non-null. Viewer DTO không có token/email grants/owner_account_id.

## 4. API proposal

Prefix service `/api/v1`; qua Gateway dùng `/wardrobe-community/api/v1` như hiện tại. Không thêm service hoặc cross-service DB access.

| Method / route | Contract |
| --- | --- |
| GET `/me/outfits/{outfitId}/sharing` | Owner-only. 200 `{outfit_id,visibility,public_enabled,public_token,public_enabled_at}`; token owner-only có thể còn khi inactive; public_enabled là visibility PUBLIC. |
| PATCH `/me/outfits/{outfitId}/sharing` | Owner-only, strict JSON duy nhất field visibility, ví dụ `{"visibility":"FRIENDS"}`; enum PRIVATE/FRIENDS/PUBLIC. 200 cùng DTO; no-op không thay timestamps. PUBLIC có thể khởi tạo token, trả Owner để copy link. |
| GET `/me/friends/{friendAccountId}/outfits?page=0&size=20` | Cần current active friend. Chỉ FRIENDS/PUBLIC của đúng người bạn; không leak PRIVATE qua items/count. Page zero-based, size 1..100 như OutfitRequestValidator hiện có, unknown/duplicate query bị từ chối; sort updated_at DESC, outfit_id DESC. |
| GET `/me/friends/{friendAccountId}/outfits/{outfitId}` | Current friend + đúng outfit owner + mode FRIENDS/PUBLIC. 200 dùng Viewer DTO hiện có; ngoài quyền trả 404. |
| GET `/public/outfits/{publicToken}` | Giữ URL/shape Viewer DTO, guest VIEW khi PUBLIC hiện tại. Bad/unknown/inactive token trả 404, không echo token. |
| PUT `/me/outfits/{outfitId}/public-link` | Giữ route/200 PublicLinkResponse; alias chuyển PUBLIC theo S4, dùng lại token khi bật lại. |
| DELETE `/me/outfits/{outfitId}/public-link` | Giữ route/204; nếu PUBLIC thì chuyển PRIVATE, giữ token; nếu non-PUBLIC no-op. |

Friend list response: `{items,page,size,total_elements,total_pages}`; mỗi item `{outfit_id,name,owner_display_name,item_count,visibility,updated_at}`. Dùng DTO mới, không gán sai `granted_at` từ friendship timestamp. `GET` list không gọi Commerce; detail giữ cách lấy item/giá/ảnh hiện tại từ Commerce và đánh dấu unavailable cho variant không trả về. Không thêm upload ảnh Outfit/Cart/Order.

Body missing/null/wrong type/unknown/duplicate fields, enum sai casing hoặc giá trị khác → 400 theo problem convention. UUID path strict như hiện có. Missing JWT 401; sai role 403; non-owner/not-friend/hidden/foreign-parent resource 404; retired 410 chỉ sau auth/owner check thích hợp. Downstream Identity/Commerce unavailable giữ convention 503, không fallback bypass authorization. HTTP response no-store; không log hoặc đưa credential/token vào diagnostics.

### Cutover API cũ — cần duyệt S3

| Existing route | Đề xuất |
| --- | --- |
| POST/GET `/me/outfits/{outfitId}/shares` | Auth + Owner check rồi 410 `OUTFIT_EMAIL_SHARING_RETIRED`. Không tạo/revoke grant hay gọi Notification. |
| DELETE `/me/outfits/{outfitId}/shares/{shareId}` | Auth + Owner check rồi cùng 410; không dùng revoke một người để giả chế độ tất cả bạn bè. |
| GET `/me/shared-outfits` và `/{outfitId}` | Auth CUSTOMER rồi 410 `OUTFIT_EMAIL_SHARING_RETIRED`, không đọc/trả dữ liệu theo grant email. Frontend chuyển sang friend-outfit routes. |
| Owner CRUD `/me/outfits`/items | Giữ routes/rules và các fields cũ; thêm visibility vào owner summary/detail theo mục 3, không cho create/rename/item payload tự chọn visibility. |

Lời mời dùng POST `/me/friend-invitations` hiện có, body recipient_email. Không tự đổi nghĩa POST `/shares` thành friend invitation; không gửi template Outfit invitation cũ trong flow mới. Existing Notification internal endpoint được giữ, không làm task Vi hoặc tự xóa code service khác.

## 5. Layer, transaction và authorization

Giữ JPA hiện có của module Outfit; không thêm framework. Controller → Service interface → impl → internal persistence/policy → Repository. Chỉ chỉnh/refactor sharing service liên quan sang convention interface/impl/internal; không refactor toàn CRUD/catalog.

- Model fields: Outfit.visibility + public_token/public_enabled_at; Entity khởi tạo PRIVATE trước persist (không trông chờ SQL default khi JPA gửi NULL). Không thêm wardrobe root hoặc per-friend share rows. DDL ở [scope Task 04](EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md).
- Write visibility/public alias: actor từ JWT sub, PESSIMISTIC_WRITE trên owned Outfit hiện có, đổi mode/link/timestamp trong một transaction. Không cho client chọn owner/token/IDs; timestamp server-side. Actual change cập nhật updated_at; no-op giữ nguyên. Concurrent writes serialize, last serialized write wins; không cần giữ Friends mutation lock cho visibility.
- Friend list/count dùng cùng current-friend/mode predicate; page/count snapshot ở DB-local transaction, không tính PRIVATE rồi lọc sau pagination. Không có network I/O trong transaction/row lock.
- Detail/public: authorize trước Identity/Commerce lookup và recheck current mode/Friendship sau I/O, trước khi trả projection. Nếu đã mất quyền thì 404 và bỏ dữ liệu. Không cam kết thu hồi bytes đã tải hoặc tính atomic giữa packet delivery và mutation sau check cuối.
- Không có direct Outfit item/image-content route riêng trong source hiện tại; kiểm tra lại mọi route thực tế, embedded items phải từ đúng outfit. Ảnh sản phẩm Commerce là catalog asset hiện có, contract này bảo vệ dữ liệu phối đồ, không chuyển ảnh sản phẩm sang storage Chat hoặc hứa thu hồi URL ảnh catalog.

## 6. Dữ liệu cũ và triển khai

V1–V5 bất biến. V6 chỉ thực hiện sau approval scope. Public tokens có sẵn được giữ; grant email cũ không tự tạo quan hệ hoặc tự mở thêm FRIENDS. Cần phối hợp frontend cutover cho 410/mode metadata. Không deploy mixed old/new Wardrobe instances: code cũ chỉ nhìn token/email grant sẽ bỏ qua visibility mới. Dừng old instances trước migration/new reader; không tự chạy Compose hay migrate persistent DB trong nhiệm vụ planning.

## 7. Task/test/handover

| Task | Exit criteria sau approval |
| --- | --- |
| 04 | V6 mới, Entity/Repository updates, fresh + V1–V5 seeded-upgrade + constraints/index/data preservation trên PostgreSQL cô lập. |
| 05 + 06 | Settings/friend-view APIs + legacy cutover/public aliases + guard đầy đủ trước khi mở route; Swagger/DTO/examples/error docs, layer theo trên. |
| 07 | Unit policy/validator, HTTP/JPA/PostgreSQL, Owner A/B/C/guest, PRIVATE counts, nested isolation, mode flip/unfriend during downstream I/O, concurrent writes, public OFF/ON same token, delete/no-op/rollback; regression Outfit/Friends/Chat. |

Giữ CRUD regressions; chỉ đổi expectation sharing đã bị contract mới thay thế, ghi mapping cũ→mới, không skip/xóa test để báo xanh. Bàn giao collection `postman/StyleMind-EP06-Share-Outfits.postman_collection.json` và run guide khi APIs có implementation; collection cũ giữ làm legacy reference, không báo nó pass semantics cũ. Structural JSON/links khác actual HTTP/Newman/runtime; browser acceptance cần frontend riêng.

Chưa có migration, test mới hoặc HTTP implementation của proposal này. Quyết định còn cần: S1–S4, scope V6 và API cutover; không suy approval từ file/spec đã lưu.
