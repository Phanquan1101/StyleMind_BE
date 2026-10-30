# EP06 — Bổ sung Identity/Notification cho Friends

**2026-10-28, Asia/Bangkok.** Owner giao đọc source Quân/Vi và tự bổ sung các dependency còn thiếu; đồng thời đề xuất cloud ảnh. Đã triển khai nội bộ đúng service owner, giữ contract cũ và không đổi schema. **Phần bổ sung đã có code/test; full Identity regression chưa pass** do 15 test baseline dưới đây.

## 1. Thay đổi thực tế

| Phần | Implementation | Giữ nguyên |
| --- | --- | --- |
| Identity | POST /internal/v1/accounts/email-verification-lookup, service-token auth, no-store; account_id/email/account_status/email_verified_at; không join UserProfile, không chọn password | Directory batch-lookup cũ bốn field; Register/Login/OTP/JWT/profile/public APIs và migration history |
| Notification | POST /internal/v1/friend-invitations; subject cố định, text + escaped HTML UTF-8, đúng email/login/register/verify/confirm và deadline UTC; service interface → impl → template nội bộ | Outfit share/verification/password-reset mail và SMTP settings |
| Wardrobe | IdentityEmailVerificationClient riêng, active + timestamp; FriendInvitationMailClient riêng trả ACCEPTED/FAILED/UNKNOWN/UNAVAILABLE; Feign methods bổ sung, NEVER_RETRY | Không dùng Outfit mail client nuốt lỗi; existing Friends/Outfit/Chat endpoints |
| Cloud | Đề xuất R2 Standard và hướng dẫn account/private bucket/token/runtime/config/live smoke | Không tạo cloud resource, chọn billing hoặc đọc secret thay Owner |

[Contract chi tiết](../../planning/wardrobe/EP06_FRIENDS_DEPENDENCIES_CONTRACT.md), [R2 setup](../../operations/wardrobe/EP06_CLOUDFLARE_R2_SETUP.md), [Postman nội bộ](../../../postman/EP06_INTERNAL_FRIENDS_DEPENDENCIES_RUN_GUIDE.md).

Identity route mới hidden trong public Swagger theo convention nội bộ; Notification OpenAPI có serviceToken và 202/400/401/503/504. Không đưa internal service token vào browser hoặc thêm Gateway route.

## 2. Requirement mapping

| Yêu cầu | Model / endpoint / validation | Quyền, transaction, side effect / evidence |
| --- | --- | --- |
| Trusted current email + verification, không suy ACTIVE | Account.email/status/emailVerifiedAt projection, batch 1–100 IDs theo validator hiện có | Service-token guard; read-only JPA; unknown bỏ qua, duplicate collapse; không tạo profile |
| Account mới chưa profile vẫn nhận diện được | Query chỉ Account; response không cần display_name | PostgreSQL test xóa profile rồi lookup vẫn trả đúng null/verified facts; directory cũ giữ semantics |
| Không lộ dữ liệu auth | Response đúng bốn fields; DTO/snapshot redacted toString | Không select password/hash/OTP/refresh token; no-store; test whitelist/missing token |
| Thư mời Friends riêng | DTO recipientEmail/senderName/invitationUrl/expiresAt; @Email, tên ≤100, HTTP(S) URL ≤2048 không control, deadline OffsetDateTime bắt buộc | Notification không tạo invitation/Friendship và không tự tính 7 ngày; Wardrobe còn sở hữu token/TTL/resend |
| 202/503/504 delivery | Chỉ 202 sau JavaMailSender; SMTP reject → 503 EMAIL_DELIVERY_FAILED; timeout → 504 EMAIL_DELIVERY_UNKNOWN | Không auto retry, không attach provider cause/link; SMTP recorder test thực |
| Template an toàn | Fixed subject, HTML escape, text fallback, UTC expiry | Tên không thành mail header; BCC injection/HTML tests; không log content/link |
| Caller không nuốt lỗi | ACCEPTED chỉ 202; FAILED chỉ explicit 503/code; response/network không xác định UNKNOWN; config thiếu UNAVAILABLE | Feign HTTP tests kiểm body timestamp/camelCase/service token và đúng một call khi 503/504 |
| Ownership/layer | Identity Controller → service interface/impl → JPA repository; Notification Controller → service interface/impl → template | Không cross-service DB/FK, không persistence framework mới hoặc migration |
| Postman | Collection nội bộ 6 requests/12 scripts và env token blank, opt-ins false | Structural compile pass; collection mới chưa thực chạy Newman; Java HTTP runtime báo riêng |

Facts là snapshot ở Identity; không hứa distributed transaction với accept trong Wardrobe. HTTP 202 chỉ chứng minh SMTP nhận, chưa chứng minh inbox/đọc thư hoặc invitation ACCEPTED. Public orchestration phải giữ PENDING khi delivery không thành công/không xác định theo business rule đã chốt.

## 3. Kết quả verification thực chạy

Runner Maven 3.9.11/Java 21, PostgreSQL 18.6/Testcontainers và SMTP test riêng; source mount read-only rồi copy vào /tmp. Notification verify/package trước Identity vì Register/Reset tests cần Notification JAR sibling.

| Gate ngày 28 | Tests | Failures | Errors | Skipped | Kết luận |
| --- | ---: | ---: | ---: | ---: | --- |
| Notification verify | 40 | 0 | 0 | 0 | BUILD SUCCESS, 8 classes, 36.248s; 29 regression + 11 mới |
| Wardrobe verify | 263 | 0 | 0 | 0 | BUILD SUCCESS, 27 classes, 2:12; 248 regression + 15 mới |
| Identity verify tổng | 158 | 15 | 0 | 0 | BUILD FAILURE, 22 classes; **không gọi full regression pass** |
| Identity lookup class trong gate trên | 9 | 0 | 0 | 0 | Gồm 4 tests cũ + 5 mới, PostgreSQL/MockMvc |
| Identity baseline probe, 5 classes | 60 | 15 | 0 | 0 | Tái hiện đúng cùng 15 failures trước thay đổi |

Có **31 tests mới**: Identity 5, Notification 11, Wardrobe 15. Không cộng scoped lookup 9 vào tổng 158 hoặc coi số test subset là một gate tổng mới. Không skip test để làm xanh.

Notification có actual embedded HTTP → JavaMailSender → SMTP recorder:
- UTF-8 subject/plain/HTML, đúng recipient/link/deadline; 202 và no-store.
- Missing/wrong service token, encoded path, method khác và malformed request không gửi.
- SMTP reject thực → 503, ACK timeout thực → 504; body lỗi sanitize.
- Swagger scheme/mã lỗi.

Wardrobe có hai nhóm unit client và ba Feign HTTP tests dùng server test riêng: serialize IDs/OffsetDateTime, parse verification timestamp và không retry khi 503/504. Không tuyên bố đây là full public invitation E2E.

Lỗi đợt đầu đã xử lý: runner chưa stage/build Notification sibling; SMTP test server xử lý một connection gây timeout ca sau (đổi test handler song song); assertion ApiOperationException phải dùng code thay vì detail; copy Surefire vào thư mục có sẵn cần copy contents để tránh nested/stale reports. Source production không đổi để làm test giả pass.

## 4. 15 lỗi Identity có sẵn

Đã dựng source baseline trong container từ source read-only: bỏ sáu file Identity bổ sung trong copy tạm và khôi phục hai file repository/test về nội dung **khớp SHA-256 baseline trước sửa**. Không dùng Git hoặc sửa/reset source repo.

Chạy baseline probe cho Login/Refresh/ManageAddress/ManageProfile/ManageUsers: 60 tests, 15 failures. Danh sách failure trùng toàn bộ 15 failures của gate hiện tại:
- Login: 2 ca revalidate status/password sau credential check.
- Refresh: 3 ca concurrent refresh/logout.
- Address: 3 ca concurrent create/default/patch-delete.
- Profile: 1 ca concurrent first create/put.
- Users: 6 ca authorization race/Admin role/qualification locks.

Log ghi MockitoException và IllegalStateException trong các ca spy/concurrency; nhiều test dùng invocation.callRealMethod trên repository đã chuyển sang JPA interface. Đây là hướng chẩn đoán; báo cáo không khẳng định đã sửa hay chứng minh mọi behavior production tương ứng đúng. Không đổi các test/flow đó trong đợt dependency.

Sau khi runner chuẩn bị đúng sibling JAR, Register 13 và PasswordReset 12 tests đều pass; hai lỗi init runner ban đầu không còn trong kết quả cuối. Gate tổng vẫn đỏ vì 15 lỗi baseline, cần xử lý riêng trước khi kết luận regression Identity sạch.

## 5. Commands và evidence

```powershell
.\scripts\verify-ep06-friends-dependencies.ps1
```

Runner mặc định verify Notification → Identity → Wardrobe và trả exit 1 nếu bất kỳ gate fail, vẫn thu đủ kết quả. Có thể chọn -Services cho phần kiểm tra cần thiết; không coi việc chọn subset là full pass. Runner stage tất cả source sibling, chỉ ghi artifact vào ignored target/cache, không Compose/persistent DB.

Trong [evidence directory](../../../wardrobe-community-service/target/ep06-friends-dependencies-20261028):
- identity-service-verify.log / identity-service-surefire-reports: 158/15/0/0 và lookup 9 pass.
- notification-service-verify.log / notification-service-surefire-reports: 40 pass.
- wardrobe-community-service-verify.log / wardrobe-community-service-surefire-reports: 263 pass.
- identity-baseline-probe.log / identity-baseline-probe-reports: baseline 60/15/0/0.
- baseline.json: checksums 707 files trước sửa; reconstruct-baseline.cjs bắt buộc checksum hai file Identity cũ khớp.
- validate-handover.cjs: compile JSON/12 scripts, blank token và false opt-ins; actual collection Newman NOT EXECUTED.
- validate-final.cjs: PASS trên 707 baseline files; 14 updated/29 added/0 missing, đúng 6 source files có sẵn được mở rộng; 4 khối spec lịch sử giữ nguyên, 56 local links hợp lệ, hai master bằng nhau và 12 Postman scripts compile. Kết quả tại handover-validation-summary.json.
- Gate hôm qua 248 và Newman 113/305 vẫn là checkpoint ngày 27, chưa chạy lại packaged Chat/Newman ở đợt này; không cộng hoặc đổi nghĩa evidence cũ.

## 6. File manifest

So với baseline 707 files, cập nhật **14 files**, bổ sung **29 files** gồm báo cáo này; không mất file baseline. Bảo toàn toàn bộ phần staged/unstaged/untracked đã có theo status Owner gửi.

### Files có sẵn được cập nhật

```text
postman/README.md
docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md
docs/planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md
docs/planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md
identity-service/src/test/java/com/stylemind/identity/InternalAccountLookupPostgresIntegrationTests.java
docs/operations/wardrobe/EP06_CHAT_IMAGES_CLOUD_SETUP.md
StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md
StyleMind_EP06_11_Task_Specs/00_START_HERE.md
identity-service/src/main/java/com/stylemind/identity/repository/account/AccountRepository.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/notification/NotificationFeignClient.java
notification-service/src/main/java/com/stylemind/notification/security/SecurityConfiguration.java
notification-service/src/main/java/com/stylemind/notification/security/InternalServiceTokenFilter.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/identity/IdentityFeignClient.java
StyleMind_EP06_Coding_Spec_By_Task_v4.md
```

### Files bổ sung

```text
postman/EP06_INTERNAL_FRIENDS_DEPENDENCIES_RUN_GUIDE.md
scripts/verify-ep06-friends-dependencies.ps1
postman/StyleMind-Internal-Friends.postman_environment.example.json
postman/StyleMind-EP06-Internal-Friends-Dependencies.postman_collection.json
docs/operations/wardrobe/EP06_CLOUDFLARE_R2_SETUP.md
docs/planning/wardrobe/EP06_FRIENDS_DEPENDENCIES_CONTRACT.md
notification-service/src/test/java/com/stylemind/notification/InternalFriendInvitationHttpSmtpTests.java
notification-service/src/test/java/com/stylemind/notification/FriendInvitationEmailServiceTests.java
wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/IdentityEmailVerificationClientTests.java
wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsDependencyFeignHttpTests.java
wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationMailClientTests.java
identity-service/src/main/java/com/stylemind/identity/controller/internal/InternalAccountEmailVerificationController.java
notification-service/src/main/java/com/stylemind/notification/dto/FriendInvitationEmailRequest.java
notification-service/src/main/java/com/stylemind/notification/service/friendinvitation/internal/FriendInvitationMailTemplate.java
notification-service/src/main/java/com/stylemind/notification/service/friendinvitation/impl/FriendInvitationEmailServiceImpl.java
notification-service/src/main/java/com/stylemind/notification/service/friendinvitation/FriendInvitationEmailService.java
notification-service/src/main/java/com/stylemind/notification/controller/InternalFriendInvitationController.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/identity/IdentityEmailVerificationClient.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/notification/FriendInvitationMailClient.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/identity/payload/AccountEmailVerificationLookupResponse.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/identity/payload/AccountEmailVerificationItem.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/notification/payload/FriendInvitationEmailRequest.java
identity-service/src/main/java/com/stylemind/identity/dto/internal/AccountEmailVerificationLookupResponse.java
identity-service/src/main/java/com/stylemind/identity/dto/internal/AccountEmailVerificationItem.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/FriendInvitationDelivery.java
identity-service/src/main/java/com/stylemind/identity/service/internal/impl/AccountEmailVerificationLookupServiceImpl.java
identity-service/src/main/java/com/stylemind/identity/service/internal/AccountEmailVerificationLookupService.java
identity-service/src/main/java/com/stylemind/identity/model/internal/AccountEmailVerificationSnapshot.java
docs/qa/wardrobe/EP06_FRIENDS_DEPENDENCIES_IMPLEMENTATION_REPORT.md
```

## 7. Giới hạn và bước tiếp theo

- Dependencies Identity/Notification và Wardrobe clients đã có; **Task 02/03 còn PARTIAL** vì public send/resend/accept/incoming chưa triển khai. Crossed invitation, old pending sau unfriend, repeated accept và resend cap/cooldown vẫn cần chốt cùng contract mutation.
- Toàn Identity regression chưa xanh như mục 4. Không đánh dấu task Quân/Vi hoặc toàn Friends Done chỉ từ phần bổ sung này.
- Frontend invitation link/resume và public accept/re-friend E2E chưa có; SMTP recorder không chứng minh provider email/inbox thật.
- R2 là **đề xuất**, chưa có Owner chọn account/bucket/billing hoặc live provider smoke. Hướng dẫn setup đã có, không cần gửi key vào chat.
- Không sửa migration, Register/Login/OTP/JWT/Outfit, Commerce/Payment/Stylist/Gateway; không DB lâu dài, .env/.aws, Compose, cloud resource hoặc email người dùng thật. Chỉ DB/container/SMTP test dùng một lần và Maven cache/ignored evidence được chạm.
- Không Git commit/push/reset/stash; mọi lệnh Git vẫn do Owner tự chạy. Plan/index/master v4.7 giữ nguyên khối v4.1 lịch sử.
