# Báo cáo bàn giao EP06 — Thay đổi liên quan Quân, Vi và Khánh

**Ngày cập nhật:** 29/10/2026, múi giờ Asia/Bangkok.  
**Phần việc tích hợp:** EP06 của Tín — Manage Friends, Share Outfit và Chat with Friends.  
**Người đọc:** Quân phụ trách Identity; Vi phụ trách Notification; Khánh phụ trách Wardrobe/Outfit.

Tài liệu ghi lại các thay đổi đã thực hiện để tích hợp EP06 vào source hiện tại. Phân công ở đây theo thông tin nhóm đã thống nhất, không phải kết luận tác giả từng dòng code từ Git. Phần bổ sung Identity/Notification và thay đổi Share Outfit đã được Tín cho phép thực hiện trong các lượt làm việc trước.

## 1. Tổng quan phạm vi

| Phần | Đã thay đổi | Tác động database |
| --- | --- | --- |
| Quân — `identity-service` | Thêm hai API nội bộ cung cấp thông tin email đã xác minh; bổ sung query, validation, DTO, service và test | Không thêm/sửa migration hoặc schema Identity |
| Vi — `notification-service` | Thêm API và template email mời kết bạn; mở rộng service-token guard cho endpoint mới | Không thêm/sửa migration hoặc schema Notification |
| Khánh — module Outfit trong `wardrobe-community-service` | Đổi chia sẻ theo từng Outfit sang PRIVATE/FRIENDS/PUBLIC; thêm API/quyền xem mới; ngừng cấp quyền theo email grant cũ | Thêm V6; giữ nguyên V1/V2 và dữ liệu legacy share |
| Tín — các module EP06 trong cùng Wardrobe service | Thêm Friends, lời mời, Chat 1–1, WebSocket, ảnh Chat và client gọi Identity/Notification | V3/V4/V5 thuộc EP06, tách với thay đổi Outfit ở V6 |

**Trạng thái bàn giao:** các thay đổi đang ở workspace local, chưa push và chưa được agent áp migration lên database dùng lâu dài. Các test database dùng tài nguyên cô lập. Báo cáo này không đánh dấu toàn bộ task của Quân, Vi hoặc Khánh đã hoàn thành.

## 2. Quân — Identity

### 2.1. Lý do bổ sung

Luồng chấp nhận lời mời cần biết email hiện tại của tài khoản và bằng chứng email đó đã được xác minh. Chỉ có `account_status = ACTIVE` chưa thay thế được `email_verified_at`. Tài khoản vừa đăng ký, xác minh email nhưng chưa có UserProfile vẫn phải dùng được luồng lời mời.

### 2.2. API nội bộ mới

| Method / path | Request | Kết quả |
| --- | --- | --- |
| POST `/internal/v1/accounts/email-verification-lookup` | `account_ids` | Tra thông tin xác minh theo account ID |
| POST `/internal/v1/accounts/email-verification-lookup-by-email` | `email` | Tra thông tin xác minh theo email người được mời |

Response chứa `items`, mỗi item chỉ gồm `account_id`, `email`, `account_status`, `email_verified_at`. Query chỉ đọc dữ liệu Account, không phụ thuộc UserProfile và không chọn password/hash/OTP/refresh token. Tra cứu không tự xác minh, kích hoạt hoặc tạo tài khoản.

Hai endpoint được bảo vệ bằng `X-StyleMind-Service-Token`, dùng giữa backend services, không mở qua Gateway cho frontend. Response dùng `Cache-Control: no-store`. API directory `/internal/v1/accounts/batch-lookup` hiện có được giữ nguyên contract.

### 2.3. File đã sửa và bổ sung

Các đường dẫn dưới đây nằm trong `identity-service`.

| Loại | File |
| --- | --- |
| Sửa query repository | [AccountRepository.java](../../../identity-service/src/main/java/com/stylemind/identity/repository/account/AccountRepository.java) |
| Mở rộng integration test | [InternalAccountLookupPostgresIntegrationTests.java](../../../identity-service/src/test/java/com/stylemind/identity/InternalAccountLookupPostgresIntegrationTests.java) |
| Controller mới | [InternalAccountEmailVerificationController.java](../../../identity-service/src/main/java/com/stylemind/identity/controller/internal/InternalAccountEmailVerificationController.java) |
| Service interface mới | [AccountEmailVerificationLookupService.java](../../../identity-service/src/main/java/com/stylemind/identity/service/internal/AccountEmailVerificationLookupService.java) |
| Service implementation mới | [AccountEmailVerificationLookupServiceImpl.java](../../../identity-service/src/main/java/com/stylemind/identity/service/internal/impl/AccountEmailVerificationLookupServiceImpl.java) |
| DTO mới | [AccountEmailLookupRequest.java](../../../identity-service/src/main/java/com/stylemind/identity/dto/internal/AccountEmailLookupRequest.java), [AccountEmailVerificationItem.java](../../../identity-service/src/main/java/com/stylemind/identity/dto/internal/AccountEmailVerificationItem.java), [AccountEmailVerificationLookupResponse.java](../../../identity-service/src/main/java/com/stylemind/identity/dto/internal/AccountEmailVerificationLookupResponse.java) |
| Projection mới | [AccountEmailVerificationSnapshot.java](../../../identity-service/src/main/java/com/stylemind/identity/model/internal/AccountEmailVerificationSnapshot.java) |
| Validator mới | [EmailLookupRequestValidator.java](../../../identity-service/src/main/java/com/stylemind/identity/validation/internal/EmailLookupRequestValidator.java) |

Register, Login, OTP, JWT, Profile và các migration Identity giữ nguyên. Quân cần lưu ý phần bổ sung này cung cấp dữ liệu xác minh; quyết định cho chấp nhận lời mời vẫn thuộc Wardrobe Community.

## 3. Vi — Notification

### 3.1. API và luồng email mới

Thêm `POST /internal/v1/friend-invitations`, nhận thông tin người nhận, tên người mời, URL lời mời và thời điểm hết hạn. Template có subject cố định, nội dung tiếng Việt UTF-8, bản text và HTML đã escape.

Wardrobe Community sở hữu lời mời, token, hạn 7 ngày, điều kiện gửi lại và trạng thái kết bạn. Notification chỉ gửi email theo payload đã được backend cung cấp; không tự tạo Friendship hoặc chuyển lời mời sang ACCEPTED.

| HTTP result | Ý nghĩa |
| --- | --- |
| 202 | SMTP đã tiếp nhận; chưa chứng minh thư vào inbox, được đọc hoặc người nhận đã chấp nhận kết bạn |
| 400 | Request không hợp lệ |
| 401 | Thiếu hoặc sai internal service token |
| 503 | `EMAIL_DELIVERY_FAILED` — gửi thất bại |
| 504 | `EMAIL_DELIVERY_UNKNOWN` — chưa xác định kết quả do timeout |

Endpoint mới được bổ sung vào service-token guard hiện có. Không tự retry email khi kết quả chưa xác định; không đưa nội dung link/token hoặc lỗi provider chứa thông tin nhạy cảm ra response/log.

### 3.2. File đã sửa và bổ sung

Các đường dẫn dưới đây nằm trong `notification-service`.

| Loại | File |
| --- | --- |
| Sửa security matcher | [SecurityConfiguration.java](../../../notification-service/src/main/java/com/stylemind/notification/security/SecurityConfiguration.java) |
| Mở rộng token filter | [InternalServiceTokenFilter.java](../../../notification-service/src/main/java/com/stylemind/notification/security/InternalServiceTokenFilter.java) |
| Controller mới | [InternalFriendInvitationController.java](../../../notification-service/src/main/java/com/stylemind/notification/controller/InternalFriendInvitationController.java) |
| DTO mới | [FriendInvitationEmailRequest.java](../../../notification-service/src/main/java/com/stylemind/notification/dto/FriendInvitationEmailRequest.java) |
| Service interface mới | [FriendInvitationEmailService.java](../../../notification-service/src/main/java/com/stylemind/notification/service/friendinvitation/FriendInvitationEmailService.java) |
| Service implementation mới | [FriendInvitationEmailServiceImpl.java](../../../notification-service/src/main/java/com/stylemind/notification/service/friendinvitation/impl/FriendInvitationEmailServiceImpl.java) |
| Template mới | [FriendInvitationMailTemplate.java](../../../notification-service/src/main/java/com/stylemind/notification/service/friendinvitation/internal/FriendInvitationMailTemplate.java) |
| Test mới | [FriendInvitationEmailServiceTests.java](../../../notification-service/src/test/java/com/stylemind/notification/FriendInvitationEmailServiceTests.java), [InternalFriendInvitationHttpSmtpTests.java](../../../notification-service/src/test/java/com/stylemind/notification/InternalFriendInvitationHttpSmtpTests.java) |

Giữ nguyên các luồng email xác minh, reset mật khẩu, endpoint/template Outfit-share cũ và cấu hình SMTP hiện có. Không thêm schema hoặc lưu trạng thái lời mời trong Notification. Vi cần lưu ý template Friends mới được gọi qua endpoint riêng, không đổi nghĩa endpoint Outfit-share cũ.

## 4. Khánh — Outfit và chia sẻ

### 4.1. Thay đổi nghiệp vụ đã duyệt

| Trước thay đổi EP06 | Hiện tại |
| --- | --- |
| Cấp quyền riêng cho từng email bằng `outfit_share` | Chọn PRIVATE / FRIENDS / PUBLIC trên từng Outfit |
| Viewer theo email grant | FRIENDS cho toàn bộ bạn bè hiện tại xem; không chọn/loại trừ từng người |
| Tắt public link xóa token | Giữ token khi tắt; bật lại dùng cùng link |
| API grant/feed email hoạt động | Trả `410 OUTFIT_EMAIL_SHARING_RETIRED` theo contract mới |

Outfit mới mặc định PRIVATE. Gửi hoặc chấp nhận lời mời kết bạn **không tự đổi Outfit**. Owner muốn cho bạn bè xem phải tự chọn FRIENDS. PRIVATE chỉ Owner xem; PUBLIC cho khách xem qua public link.

Unfriend chặn đường xem qua bạn bè; kết bạn lại khôi phục quyền xem FRIENDS/PUBLIC. Public link của Outfit PUBLIC hoạt động độc lập với Friendship. Viewer không được rename, thêm/xóa/đổi item hoặc xóa Outfit.

### 4.2. API và tương thích client

Các path dưới đây thuộc Wardrobe service; qua Gateway thêm prefix `/wardrobe-community`.

| API | Hành vi |
| --- | --- |
| GET/PATCH `/api/v1/me/outfits/{outfitId}/sharing` | Owner đọc/đổi visibility và trạng thái public link; PATCH chỉ nhận field `visibility` |
| GET `/api/v1/me/friends/{friendAccountId}/outfits` | Current friend xem danh sách FRIENDS/PUBLIC; PRIVATE bị loại cả khỏi items và total count |
| GET `/api/v1/me/friends/{friendAccountId}/outfits/{outfitId}` | Xem đúng Outfit của người bạn, kiểm tra owner, mode và Friendship |
| GET `/api/v1/public/outfits/{token}` | Giữ URL public reader; chỉ xem được khi mode hiện tại là PUBLIC |
| PUT `/api/v1/me/outfits/{outfitId}/public-link` | Alias chọn PUBLIC; dùng lại token cũ nếu đã có |
| DELETE `/api/v1/me/outfits/{outfitId}/public-link` | Nếu PUBLIC thì về PRIVATE; nếu đã PRIVATE/FRIENDS thì no-op, không xóa token |
| POST/GET `/api/v1/me/outfits/{outfitId}/shares` và DELETE `/shares/{shareId}` | Sau kiểm tra quyền phù hợp, trả 410; không ghi grant hoặc gửi thư mời Outfit cũ |
| GET `/api/v1/me/shared-outfits` và `/{outfitId}` | Trả 410; client chuyển sang friend-outfit APIs |

Owner summary/detail có thêm field `visibility`. Token còn trong response Owner khi inactive không có nghĩa link đang mở; client dùng visibility hoặc `public_enabled` từ sharing settings. Viewer DTO không trả public token, email grants hoặc owner account ID.

**API tạo/sửa/xóa Outfit và các quy tắc CRUD hiện có giữ nguyên.** `OutfitService` có sửa mapper response để trả thêm visibility; không phải toàn file không thay đổi. Các payload CRUD không được dùng để tự chọn owner/token/visibility.

### 4.3. File Outfit có sẵn đã sửa trực tiếp

Các đường dẫn dưới đây nằm trong `wardrobe-community-service`.

| File | Nội dung sửa |
| --- | --- |
| [Outfit.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/Outfit.java) | Thêm visibility, Java default PRIVATE |
| [OutfitRepository.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/outfit/OutfitRepository.java) | Query count/page theo FRIENDS/PUBLIC |
| [OutfitService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/OutfitService.java) | Mapper summary/detail thêm visibility |
| [OutfitShareService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/OutfitShareService.java) | Chuyển thành interface cho owner sharing/public-link và adapter legacy |
| [SharedOutfitService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/SharedOutfitService.java) | Chuyển thành interface cho friend/public reader và adapter legacy |
| [OutfitShareController.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/OutfitShareController.java) | Legacy email APIs trả 410; public-link aliases theo lifecycle mới |
| [SharedOutfitController.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/SharedOutfitController.java) | Retire email-grant feed/detail bằng 410 |
| [OutfitDetailResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/OutfitDetailResponse.java), [OutfitSummaryResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/OutfitSummaryResponse.java) | Thêm field visibility |
| [OutfitSharePostgresIntegrationTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/OutfitSharePostgresIntegrationTests.java) | Chuyển coverage email-grant cũ sang ba mode, retired APIs, lifecycle và quyền mới theo approval |

Bổ sung `OutfitSharingController`, `FriendOutfitController`, DTO/request validator/snapshots, hai implementation `OutfitShareServiceImpl`/`SharedOutfitServiceImpl`, và các lớp internal policy/persistence/read. Layer là **Controller → Service interface → impl → internal → Repository JPA**, giữ persistence framework của module hiện tại. Danh sách chính xác từng file ở [báo cáo Tasks 04–07](EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md#exact-file-inventory).

### 4.4. Database và dữ liệu cũ

[V6__add_outfit_visibility.sql](../../../wardrobe-community-service/src/main/resources/db/migration/V6__add_outfit_visibility.sql) thêm một column, hai CHECK và một partial index; không tạo bảng mới. V1/V2 của module Outfit và V3–V5 trước V6 được giữ nguyên checksum.

| Dữ liệu trước V6 | Sau chuyển đổi |
| --- | --- |
| Có public token hợp lệ | PUBLIC, giữ token; bổ sung enabled_at từ updated_at nếu thiếu |
| Không public token | PRIVATE, kể cả từng cấp quyền email |
| Bản ghi `outfit_share` hiện có | Giữ nguyên để đối chiếu, không còn được dùng cấp quyền |
| Item, Owner, Friends và Chat hiện có | Không bị migration tự xóa hoặc tạo quan hệ mới |

V6 dừng trước ALTER nếu token cũ blank hoặc dài hơn 128 ký tự; không tự thay token hoặc xóa dữ liệu để vượt lỗi. Không đổi email-only Outfit sang FRIENDS vì thao tác đó sẽ mở cho cả nhóm bạn.

Frontend phải chuyển sang contract mới và xử lý 410. Khi triển khai cần phối hợp V6 với reader mới; **không chạy lẫn binary Wardrobe cũ/mới hoặc downgrade về reader cũ**, vì code cũ không kiểm tra visibility. Xem [hướng dẫn cutover](../../operations/wardrobe/EP06_OUTFIT_SHARING_CUTOVER.md). Các bước persistent migration/deploy chưa được agent thực hiện.

## 5. Phần tích hợp EP06 bổ sung trong cùng Wardrobe service

Các thay đổi sau thuộc task Friends/Chat của Tín, dù cùng nằm trong service Khánh đang làm Outfit:

| Nhóm | Thay đổi |
| --- | --- |
| Friends | Invitation/Friendship entity, repository, controller, service interface/impl/internal, validation và token policy |
| Chat | Hội thoại 1–1 theo cặp bạn bè, text/history, WebSocket, private image APIs và storage adapter |
| Identity client | Bổ sung hai Feign methods lookup xác minh email; client và payload riêng cho Friends |
| Notification client | Bổ sung Feign method gửi Friends invitation; client phân biệt accepted/failed/unknown, không dùng client Outfit cũ nuốt lỗi |
| Cấu hình chung | Security cho Friends/Chat/WebSocket, exception handling, multipart/image config; dependencies WebSocket/S3/WebP phục vụ Chat |
| Public errors | `ApiProblemDetails` che phần token trong error instance của public Outfit; scoped advice bổ sung no-store cho sharing errors |
| Cấu hình ảnh local/Docker | `.env.example`, `docker-compose.yml`, script IDE và tài liệu cho ảnh Chat Backblaze B2; không đưa secret vào file bàn giao |

Các file có sẵn liên quan gồm `IdentityFeignClient.java`, `NotificationFeignClient.java`, `SecurityConfiguration.java`, `ApiExceptionHandler.java`, `ApiProblemDetails.java`, `application.properties` và `pom.xml` của Wardrobe service.

| Migration EP06 | Nội dung |
| --- | --- |
| V3 | Hai bảng `friend_invitation`, `friendship` |
| V4 | Bốn bảng `chat_session`, `chat_participant`, `chat_message`, `chat_attachment` |
| V5 | Bổ sung trạng thái/thời điểm thử gửi và vô hiệu hóa lời mời cũ |
| V6 | Visibility và consistency của public link trên `outfit` |

Không đọc trực tiếp database Identity/Notification từ Wardrobe, không có FK xuyên service. Ảnh Chat dùng storage riêng; ảnh sản phẩm của Outfit vẫn lấy theo catalog Commerce hiện có.

## 6. Bằng chứng kiểm thử và giới hạn

Đây là kết quả đã lưu từ các lượt chạy trước khi viết tài liệu này. Lượt tạo Markdown chỉ đối chiếu source/evidence và kiểm tra liên kết, không chạy lại application tests hoặc thay đổi code/config.

| Phạm vi | Kết quả đã chạy | Giới hạn |
| --- | --- | --- |
| Identity lookup | 12 tests pass ngày 28/10 | Là subset, không cộng vào tổng regression |
| Identity toàn service | 161 tests, 15 failures baseline đã tái hiện trước thay đổi | Chưa phải full Identity regression pass; 15 lỗi này chưa được sửa trong EP06 |
| Notification | 40 tests, 0 failures/errors/skipped ngày 28/10 | SMTP test riêng; không chứng minh gửi email production/inbox thật |
| Wardrobe toàn service | 314 tests / 33 classes, 0 failures/errors/skipped ngày 29/10 | Bao gồm CRUD Outfit, Friends, sharing, Chat và WebSocket |
| Runtime qua Gateway | 1 JUnit scenario pass với 4 JAR thật, PostgreSQL và Mailpit cô lập | Commerce là HTTP fixture; image storage fake trong lượt này |
| Newman | 6 collections / 8 runs, 155 requests / 380 assertions, 0 failures | Riêng Share Outfit là 32 requests / 55 assertions; cấu trúc JSON được kiểm tra riêng |

Runtime đã xác minh gửi lời mời → Register/verify/login → accept; Outfit PRIVATE vẫn bị chặn cho tới khi Owner chọn FRIENDS; unfriend chặn và lời mời mới/refriend khôi phục quyền xem. Ảnh Chat Backblaze B2 có [evidence cloud riêng](EP06_BACKBLAZE_VERIFICATION_2026-10-29.md), không dùng kết quả storage fake thay thế live cloud.

Nguồn kiểm chứng:

- [Báo cáo bổ sung Identity/Notification](EP06_FRIENDS_DEPENDENCIES_IMPLEMENTATION_REPORT.md) — manifest và baseline probe.
- [Verification Friends/Invitation ngày 28/10](EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md) — Identity lookup 12, Notification 40 và 15 lỗi baseline Identity.
- [Báo cáo Share Outfit Tasks 04–07](EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md) — requirement mapping, danh sách file và gate 314/runtime/Newman hiện hành.
- [Contract dependency](../../planning/wardrobe/EP06_FRIENDS_DEPENDENCIES_CONTRACT.md) và [contract Share Outfit đã duyệt](../../planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md).
- [Postman nội bộ Identity/Notification](../../../postman/EP06_INTERNAL_FRIENDS_DEPENDENCIES_RUN_GUIDE.md), [Friends invitations](../../../postman/EP06_FRIEND_INVITATIONS_RUN_GUIDE.md), [Share Outfit](../../../postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md).

Một số báo cáo nguồn giữ các checkpoint cũ như PARTIAL/NOT RUN/ON_HOLD để truy vết; đọc notice hiện hành và kết quả verification có ngày ở đầu tài liệu. Không suy trạng thái hiện tại từ một checkpoint lịch sử.

## 7. Nội dung nhóm cần phối hợp

| Người/phần | Nội dung review hoặc phối hợp |
| --- | --- |
| Quân | Review hai internal lookups, projection không cần profile, giới hạn thông tin trả về và service-token auth; xử lý riêng 15 lỗi baseline Identity trước khi kết luận regression service sạch |
| Vi | Review template Friends, payload/deadline, service-token matcher và semantics 202/503/504; xác minh SMTP provider bên ngoài khi nhóm triển khai môi trường thật |
| Khánh + frontend | Tích hợp visibility vào màn hình Outfit, đổi API viewer/feed, xử lý 410 và public-link reuse; phối hợp triển khai V6 với reader mới |
| Tín + frontend | Hoàn tất browser/UI acceptance còn lại của Task11: invitation page, Chat WebSocket, upload/read ảnh, reconnect và unfriend/refriend |

Không còn quyết định nghiệp vụ Share Outfit chờ duyệt trong scope hiện tại. Browser/frontend acceptance, phối hợp triển khai và xử lý lỗi baseline Identity là các phần còn lại; local evidence chưa phải production readiness.
