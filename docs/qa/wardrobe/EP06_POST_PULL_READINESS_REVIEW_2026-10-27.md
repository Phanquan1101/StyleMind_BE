# EP06 — Readiness sau pull và khôi phục code

**2026-10-27, Asia/Bangkok — rà source hiện tại của Tín; chưa triển khai cloud.**

## 1. Kết luận

**Có thể tiếp tục phần Chat và thiết kế ảnh cloud; chưa đủ đầu vào để hoàn tất luồng lời mời Friends thực tế.** Code EP06 đã khôi phục tương thích với snapshot đang có: Wardrobe regression 212/212 pass. Gateway hiện có đã forward WebSocket qua prefix Wardrobe; không cần mặc định giao Quân thêm route mới.

| Phần của Tín | Tình trạng và khả năng tiếp tục |
| --- | --- |
| 01 Friends DB/JPA | DONE_LOCAL. V3, Invitation/Friendship, token/policy/repositories/tests sẵn sàng; không làm lại |
| 02 list friends/outgoing/unfriend | Đã có. Có thể tích hợp/kiểm thử tiếp; display name dùng Identity lookup sau transaction |
| 02 send/resend invitation | Chưa có HTTP mutation; chờ Notification friend-invitation contract và chốt các edge cases, không dùng template mời Outfit thay thế |
| 02 incoming/accept | Chưa có; cần current verified email của account từ Identity, bao gồm account chưa có profile |
| 03 invitation/email E2E | PARTIAL; phần tương ứng chỉ hoàn tất khi 02 mutations + contracts thật có |
| 08 Chat DB/JPA | DONE_LOCAL. V4 có session/participant/message/attachment; không chờ schema Wardrobe của Khánh |
| 09 sessions | DONE_LOCAL. Mở/lấy/list, unique pair, current friendship/membership, ẩn khi unfriend và reuse khi refriend |
| 10 text/history/WebSocket | DONE_LOCAL ở phạm vi text/backend; đã verify lại sau pull. Existing Gateway route có thể dùng để kiểm thử tích hợp |
| 10 images / 11 toàn Chat | Có thể chuyển sang thiết kế upload/download/private cloud tiếp theo; chưa triển khai. Còn full Gateway → real Chat/browser E2E và Postman runtime |
| 04–07 Share Outfit | ON_HOLD theo Owner. Source sharing hiện tại của Khánh chưa phải friends-only rule mới; không sửa trong đợt này |

Không cần chờ Catalog/Cart/Order/Payment/AI hoặc thay đổi Outfit để tiếp tục Chat.

## 2. Phạm vi đọc và bản đồ source

Đã kiểm kê **586 files trong src của bảy ứng dụng**, gồm **486 production Java files**; rà class/layer/endpoint/persistence toàn production Java, migration/resource/test inventory và đọc chi tiết luồng Friends/Chat cùng API tích hợp Identity/Notification/Gateway, Outfit/catalog liên quan. Đây là review điều kiện EP06, không phải kiểm toán từng business rule của mọi Epic hoặc tuyên bố toàn bộ service đã pass.

| App | Production Java | Trách nhiệm thực có trong source / liên quan EP06 |
| --- | ---: | --- |
| identity-service | 163 | Register/verify/login/session/password reset/profile/address/users/internal account lookup; JPA. Cung cấp JWT và directory |
| commerce-service | 128 | Category/Brand/Product/Variant/images, Branch/Cart/Order/Payment event; JPA và một số repository JDBC. Cấp variant lookup cho Outfit, không chặn Chat |
| payment-service | 46 | Attempts/COD/SePay webhook/outbox/reconciliation, repository JDBC; không phải dependency Friends/Chat |
| wardrobe-community-service | 117 | Outfit/share cũ, Friends và Chat; JPA, controllers/services/repositories/DTO/validators, STOMP |
| notification-service | 21 | Verification/reset/Outfit invitation SMTP; chưa có friend invitation |
| stylist-service | 10 | Bootstrap/security/OpenAPI/logging/errors; chưa có business controller AI trong snapshot |
| api-gateway | 1 | Spring Cloud Gateway WebFlux, routes trong application.properties; route probe dùng Gateway 4.3.5 |

Java 21 / Spring Boot 3.5.16; service ownership và DB riêng vẫn giữ. Tài liệu governance JDBC/“chỉ Identity có migrations” là baseline cũ ở các đoạn chưa cập nhật; quyết định Owner chọn JPA cho Friends/Chat và migrations hiện tại là căn cứ scope này.

Workbook path trong AGENTS không hiện có ở các lần kiểm tra; task mapping dựa bộ task/spec Owner cung cấp và plan hiện hành, không chỉnh workbook.

## 3. Friends: các dependency còn thiếu được xác nhận từ code

### Identity — Quân

[InternalAccountItem](../../../identity-service/src/main/java/com/stylemind/identity/dto/internal/InternalAccountItem.java#L6) chỉ trả account_id, email, display_name, account_status. [Lookup service](../../../identity-service/src/main/java/com/stylemind/identity/service/internal/InternalAccountLookupService.java#L21) dựng đúng bốn giá trị này. GET /me chỉ có account_id/email/profile; JWT không chứa current verified-email fact.

[Policy của Tín](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendInvitationPolicy.java#L30) yêu cầu email hiện tại **đã xác minh** từ server. Không suy ACTIVE/JWT đã đăng nhập tương đương proof email verified hiện tại; không dùng request email của client để accept.

**Phát hiện bổ sung:** [AccountRepository](../../../identity-service/src/main/java/com/stylemind/identity/repository/account/AccountRepository.java#L33) dùng inner join UserProfile. [CreateRegistration](../../../identity-service/src/main/java/com/stylemind/identity/service/emailverification/internal/EmailVerificationPersistence.java#L66) chỉ tạo account/role/challenge, không tạo profile mặc định. Suy ra account mới chưa tạo profile có thể không được lookup trả về. Đây là phân tích query/luồng source, chưa thêm runtime test Identity cho trường hợp này; existing lookup tests tạo profile cho fixtures. List Friends hiện có fallback display_name null; future accept/incoming cần facts account đầy đủ và không được buộc user tạo profile mới dùng được lời mời.

Cần thống nhất với Quân một API/extension tương thích trả account_id, current email, email_verified hoặc email_verified_at; hỗ trợ account không có profile, định nghĩa missing/unavailable/auth. Quân chọn endpoint/left join/projection phù hợp. Không yêu cầu sửa DB hoặc tạo profile giả, không tự sửa Identity.

### Notification — Vi

[Controller](../../../notification-service/src/main/java/com/stylemind/notification/controller/InternalOutfitShareInvitationController.java) chỉ có /internal/v1/outfit-share-invitations; các controller còn lại gửi verification/reset. [Template](../../../notification-service/src/main/java/com/stylemind/notification/service/OutfitShareInvitationSender.java) và DTO gắn với outfitName/outfitUrl, không phải mời kết bạn.

Cần friend-invitation API/template riêng với recipient/sender/link/hạn và service auth + accepted/failed/unknown contract. [ShareInvitationMailClient hiện có](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/notification/ShareInvitationMailClient.java) là best effort, nuốt lỗi: không tái dùng nguyên hành vi này cho Friends vì Owner yêu cầu trạng thái delivery trung thực, giữ PENDING và resend riêng.

### Công việc local còn thuộc Tín

[FriendshipController](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/friendship/FriendshipController.java#L54) hiện chỉ GET friends, GET outgoing invitations, DELETE friend. Entity/repository có không đồng nghĩa send/accept đã hoạt động. Cần bổ sung DTO/validator/service/transaction/controller/Swagger/Postman và tests cho mutations/incoming sau khi contract tương ứng sẵn sàng.

Owner vẫn cần chốt các edge cases trước code: lời mời chéo, pending invitation khi unfriend, repeated accept và giới hạn resend. Hạn 7 ngày, resend xoay link/hạn mới, tối đa text/ảnh, JPA/WebSocket và unfriend/refriend đã chốt; không hỏi lại.

Có thể soạn contract/test matrix và core policy trước; không mở production API với fake Identity facts hoặc giả mail success.

## 4. Gateway: đính chính kết luận cũ bằng kiểm thử

[Route 4 hiện tại](../../../api-gateway/src/main/resources/application.properties#L43) match /wardrobe-community/**, StripPrefix=1, uri HTTP tới Wardrobe. Probe chạy **nguyên Gateway source/config**, chỉ override upstream sang server test random port và thêm test class vào bản copy /tmp:

- Client kết nối ws://<gateway-random-port>/wardrobe-community/ws/chat.
- Upgrade thành công, upstream nhận /ws/chat.
- Origin http://localhost:3000 được forward.
- STOMP subprotocol v12.stomp được negotiate; text echo hai chiều thành công.
- 11 Gateway regression và một probe pass.

**Vì vậy không còn cơ sở coi “thiếu route WS riêng” là blocker mặc định.** Frontend có thể kiểm thử với ws://localhost:8080/wardrobe-community/ws/chat ở môi trường local tương ứng; HTTPS dùng WSS. Không sửa Gateway hoặc Compose.

Probe dùng upstream echo, **chưa phải full Gateway → Wardrobe thật → JWT/STOMP/DB → peer E2E**. Sáu native WS/backend tests có JWT/PG riêng đã pass trong gate Wardrobe. Vẫn cần ghép acceptance frontend/Gateway/real service; không gộp hai nhóm evidence thành E2E đã hoàn tất.

## 5. Upload cloud: đủ nền để chuyển sang thiết kế sau audit

V4 và ChatAttachment đã giữ metadata/key/order/MIME/size; message/session/friendship guards hiện có dùng được cho ảnh. Commerce chỉ có [VariantImageStorageService local](../../../commerce-service/src/main/java/com/stylemind/commerce/service/product/VariantImageStorageService.java), không phải cloud adapter/private Chat storage dùng sẵn.

Chưa có Chat upload/download controller, storage adapter, SDK/provider/bucket, file-byte validation, retry/cleanup/live cloud tests. Không triển khai cloud trong đợt đọc source này. Bước tiếp theo là chốt contract multipart, image idempotency và cleanup, chọn provider/hướng dẫn Owner setup; nếu cần digest/lifecycle columns thì soạn scope migration mới xin duyệt, không sửa V4.

Giữ tối đa 5 ảnh/tin, 5 MiB/ảnh, JPG/JPEG/PNG/WebP; read qua backend theo current membership/friendship, không public URL bypass unfriend. Không phụ thuộc verified-email API mới khi Chat đã có active Friendship đúng cặp.

## 6. Verification mới sau pull

| Gate | Kết quả |
| --- | --- |
| Wardrobe Community mvn -B -ntp verify | **212 tests, 0 failure/error/skipped, BUILD SUCCESS, 52.129 s** |
| Gateway mvn -B -ntp verify trên copy với probe | **12 tests: 11 regression + 1 WS probe, 0 failure/error/skipped, BUILD SUCCESS, 18.151 s** |
| Identity/Notification real delivery/register-resume E2E | Chưa chạy |
| Postman/Newman runtime / browser / live cloud | Chưa chạy |

Evidence trong ignored target:

- [Wardrobe log](../../../wardrobe-community-service/target/ep06-post-pull-readiness-20261027/wardrobe-post-pull-maven.log), [Surefire](../../../wardrobe-community-service/target/ep06-post-pull-readiness-20261027/wardrobe-post-pull-surefire-reports/)
- [Gateway log](../../../wardrobe-community-service/target/ep06-post-pull-readiness-20261027/gateway-route-probe-maven.log), [Surefire](../../../wardrobe-community-service/target/ep06-post-pull-readiness-20261027/gateway-route-probe-surefire-reports/)
- [Diagnostic source](../../../wardrobe-community-service/target/ep06-post-pull-readiness-20261027/GatewayWebSocketRouteProbeTests.java) — ignored evidence, không thêm vào api-gateway/src

Commands thực chạy từ root repo:

```powershell
$ErrorActionPreference='Stop'
$auditEvidence=Join-Path (Get-Location).Path 'wardrobe-community-service/target/ep06-post-pull-readiness-20261027'
New-Item -ItemType Directory -Path $auditEvidence -Force | Out-Null
$auditRunner=@'
set -u
mkdir -p /tmp/audit-wardrobe
cp /wardrobe/pom.xml /tmp/audit-wardrobe/pom.xml
cp -R /wardrobe/src /tmp/audit-wardrobe/src
cd /tmp/audit-wardrobe
mvn -B -ntp verify > /evidence/wardrobe-post-pull-maven.log 2>&1
result=$?
if [ -d target/surefire-reports ]; then cp -R target/surefire-reports /evidence/wardrobe-post-pull-surefire-reports; fi
grep -E '^\[INFO\] (Tests run:|BUILD SUCCESS|BUILD FAILURE|Total time:)|^\[ERROR\]' /evidence/wardrobe-post-pull-maven.log
exit "$result"
'@
docker run --rm --name stylemind-ep06-post-pull-audit-20261027 --mount 'type=bind,source=E:\mss\BE\StyleMind_BE\wardrobe-community-service,target=/wardrobe,readonly' --mount "type=bind,source=$auditEvidence,target=/evidence" --mount 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock' --mount 'type=volume,source=stylemind-ep06-maven-cache-20261024,target=/root/.m2' -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.11-eclipse-temurin-21-alpine sh -c $auditRunner
exit $LASTEXITCODE
```

```powershell
$ErrorActionPreference='Stop'
$auditEvidence=Join-Path (Get-Location).Path 'wardrobe-community-service/target/ep06-post-pull-readiness-20261027'
$auditRunner=@'
set -u
mkdir -p /tmp/audit-gateway
cp /gateway/pom.xml /tmp/audit-gateway/pom.xml
cp -R /gateway/src /tmp/audit-gateway/src
cp /evidence/GatewayWebSocketRouteProbeTests.java /tmp/audit-gateway/src/test/java/com/stylemind/gateway/
cd /tmp/audit-gateway
mvn -B -ntp verify > /evidence/gateway-route-probe-maven.log 2>&1
result=$?
if [ -d target/surefire-reports ]; then cp -R target/surefire-reports /evidence/gateway-route-probe-surefire-reports; fi
grep -E '^\[INFO\] (Tests run:|BUILD SUCCESS|BUILD FAILURE|Total time:)|^\[ERROR\]' /evidence/gateway-route-probe-maven.log
find /root/.m2/repository/org/springframework/cloud -name '*gateway*jar' -print
exit "$result"
'@
docker run --rm --name stylemind-ep06-gateway-route-probe-20261027 --mount 'type=bind,source=E:\mss\BE\StyleMind_BE\api-gateway,target=/gateway,readonly' --mount "type=bind,source=$auditEvidence,target=/evidence" --mount 'type=volume,source=stylemind-ep06-maven-cache-20261024,target=/root/.m2' maven:3.9.11-eclipse-temurin-21-alpine sh -c $auditRunner
exit $LASTEXITCODE
```

Maven/Java 21 Docker runner, source mounts read-only và copy pom/src vào /tmp. PostgreSQL 18.6 Testcontainers riêng cho Wardrobe; Gateway upstream là ephemeral echo server. Không Compose, persistent DB, cloud resources, email thật, Git/commit/push. Không đọc .env/secrets. Không tuyên bố production readiness.

## 7. Files thay đổi và thứ tự tiếp theo

Chỉ ghi evidence vào ignored target và cập nhật năm tài liệu:

- ADD báo cáo readiness này.
- UPDATE [Friends handoff](../../planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md): bổ sung lookup/profile finding.
- UPDATE [Task 10 contract](../../planning/wardrobe/EP06_TASK10_MESSAGES_AND_WEBSOCKET_CONTRACT.md): đính chính Gateway transport.
- UPDATE [WebSocket guide](../../../postman/EP06_CHAT_WEBSOCKET_RUN_GUIDE.md): URL prefix và phân biệt transport probe/full E2E.
- UPDATE [work plan](../../planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md): checkpoint sau pull và blocker đã kiểm chứng.

Không sửa production/test source, pom, properties, migration, collection JSON hoặc source thành viên khác. Đối chiếu SHA-256 của **586 source/test/resource files** trước/sau audit: toàn bộ giữ nguyên, không thêm/xóa file trong src. **71 local links** ở năm tài liệu hiện hành hợp lệ; không còn hai Maven runner audit/probe đang chạy. Chỉ bổ sung dòng evidence này sau lần đối chiếu, không sửa code hoặc chạy lại tests không cần thiết.

**Thứ tự:** audit đã xong → thiết kế phần ảnh/cloud và hướng dẫn setup ở bước kế tiếp → triển khai/test ảnh theo scope được chốt; song song Owner chuyển handoff Identity/Notification cho Quân/Vi. Sau khi có contracts thật, Tín hoàn tất invitation APIs/tests và full integration.

