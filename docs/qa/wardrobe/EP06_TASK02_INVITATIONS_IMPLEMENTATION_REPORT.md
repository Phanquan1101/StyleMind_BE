# EP06 Task 02 — Implementation checkpoint trước verification

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser. [Kết quả verification hiện hành](EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md). Phần còn lại là báo cáo source/static trước khi Owner bật Docker; các dòng NOT RUN/PARTIAL và file-count bên dưới chỉ đúng ở checkpoint đó.


**2026-10-28, Asia/Bangkok. SOURCE WRITTEN / VERIFICATION PENDING. Task 02/03 vẫn PARTIAL.** Owner đã duyệt “Duyệt scope V5” và “Duyệt thiết kế API”. Khi Docker chưa chạy, Owner yêu cầu “xíu nữa bật được không bạn cứ code”; do đó lượt này không thực thi Maven/PostgreSQL/HTTP/Newman.

## 1. Kết quả trong source

- V5 bổ sung đúng ba delivery columns trên friend_invitation, INVALIDATED và cutoff trên Friendship; không thêm bảng, không sửa V1–V4.
- Identity thêm server-to-server lookup verified-email facts theo email, service-token/no-store, không cần profile hoặc đọc password; giữ các lookup cũ.
- Public Friends có send/resend/incoming và hai đường accept (incoming hoặc email credential); JWT CUSTOMER/current verified account, strict JSON, không nhận actor/email-verified flag do client khai.
- Pending đã SMTP_ACCEPTED và còn hạn không gửi thêm dù qua 30 giây. Failed/unknown/expired retry chủ động sau 30 giây, credential mới/hạn 7 ngày mới; không cap 3/20 mỗi giờ hoặc automatic retry.
- Mời chéo trả incoming action, không thêm email/implicit friendship. Accept ghi invitation + stable Friendship atomically. Unfriend ghi cutoff, giữ history/session, cutoff không bị clear khi refriend.
- Layer Controller → Service interface → impl → internal persistence → Repository JPA. Identity/name lookup trước transaction, SMTP sau commit; attempt UUID tránh completion cũ ghi đè lần mới.
- Swagger và Postman mới 10 requests/20 scripts đã viết; token ví dụ trống, mutation flags false. Collection list/unfriend cũ giữ các request, chỉ cập nhật mô tả và guide.

[Contract hiện hành](../../planning/wardrobe/EP06_TASK02_INVITATION_API_DESIGN.md), [scope/DDL đã duyệt](../../planning/wardrobe/EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md), [quyết định](../../planning/wardrobe/EP06_FRIENDS_INVITATION_RULES_2026-10-28.md), [plan](../../planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md), [Postman guide](../../../postman/EP06_FRIEND_INVITATIONS_RUN_GUIDE.md).

## 2. Mapping rule → authorization/transaction/evidence đã viết

| Rule | Source và transaction | Tests viết, chưa chạy |
| --- | --- | --- |
| PENDING đã gửi không thêm email | prepareCreate/prepareResend dưới mutation lock, SMTP_ACCEPTED/deadline | Duplicate sau 31 giây, blocked resend, digest/deadline/mail count giữ nguyên |
| Failed/unknown/expired retry, 30 giây | last-attempt theo sender/email qua các ID, commit digest/attempt trước remote call | Trước/đúng mốc, old-link rejection, new 7-day TTL, không auto retry, đổi ID không né |
| Crossed invitation | Transaction mutex chung cho cả absent pair/row, reverse recheck | Tuần tự và concurrent A→B/B→A, không implicit friend hoặc mail thứ hai |
| Đúng recipient | JWT UUID/CUSTOMER + ACTIVE/non-null verification + current email; email route kiểm tra digest | C/forwarded link/unverified/sender override/duplicate JSON/unknown fields/auth |
| Stable friend pair và cutoff | Atomic accept, unfriend cập nhật watermark, refriend giữ watermark | Concurrent accept, forced rollback, pending/used link bị chặn qua unfriend/refriend |
| Late completion | Compare attempt UUID sau HTTP; không giữ DB lock qua network | First request treo, retry mới thành công, old failure không ghi đè |
| Migration history/data | V4→V5 add metadata/backfill, old columns giữ nguyên | Seed Outfit/Item/Share/Friends/Chat/Attachment, checksums và old-field fingerprints; constraints |
| Remote errors trước DB | Sender facts/name/config trước prepare, sanitized errors | Missing page/directory failure không gọi persistence/mail |

Bộ unit/HTTP/PG mới dùng mock Identity/mail ở API suite; trường hợp “register later” chỉ đổi trusted fixture facts, **không phải actual Register/verify/email E2E**. Đã thêm test riêng cho endpoint Identity và Feign client; service Notification không đổi trong lượt này.

## 3. Kiểm tra và commands

| Kiểm tra đã chạy | Kết quả |
| --- | --- |
| Java 21 syntax parse trên JDK host 25 | 380 file, 0 lỗi cú pháp; không type-check/compile/test |
| PowerShell AST / Wardrobe pom XML | 0 lỗi cú pháp / XML parse thành công; không chạy runner |
| Postman JSON/JavaScript | 10 requests, 20 scripts parse thành công; token mẫu trống, mutation flags false |
| Source V5 so với DDL Owner đã duyệt | Khớp sau chuẩn hóa whitespace/comments; chưa execute SQL |
| Hash migration V1–V4 | Không đổi |
| Legacy/master/link checks | 6 suffix lịch sử giữ nguyên; hai master đồng nhất; 205 local links hợp lệ |
| Manifest baseline | 34 file cập nhật, 29 file mới, không mất file |
| Docker engine check cuối lượt | Chưa chạy; không khởi động Docker/container |
| Java/Maven/JUnit/PostgreSQL/HTTP/Newman | **NOT RUN — Docker để sau theo Owner**, không coi là pass |

Đã viết **33 test methods mới**: 28 trong bốn class Wardrobe mới, 2 bổ sung client Wardrobe và 3 bổ sung Identity lookup. Đây là số case trong source, không phải số test đã chạy/pass. Full gate hiện tại chưa được xác minh.

Đã chuẩn bị [runner](../../../scripts/verify-ep06-friend-invitations.ps1) với Maven/Java 21, source bind read-only và copy vào /tmp, Testcontainers PostgreSQL. Khi Docker sẵn sàng:

```powershell
.\scripts\verify-ep06-friend-invitations.ps1 -Mode Focused
.\scripts\verify-ep06-friend-invitations.ps1 -Mode Regression
```

**Hai commands này chưa chạy.** Focused chọn Identity lookup cùng Friends API/migration/validator/service/client tests. Regression lần lượt Notification→Identity→Wardrobe; tiếp tục thu evidence khi một gate lỗi và exit khác 0. Không chạy Compose hoặc dùng DB lâu dài.

Static commands đã thực hiện hoặc có evidence riêng trong target:

```powershell
java --source 21 wardrobe-community-service/target/ep06-invitations-20261028/ParseJavaSources.java
node wardrobe-community-service/target/ep06-invitations-20261028/validate.cjs
node wardrobe-community-service/target/ep06-invitations-20261028/audit.cjs
```

Java harness dùng JavacTask.parse với --release 21 trên JDK host 25; **chỉ cú pháp**, không resolve type/dependency, Lombok processing hoặc compile application. Runner PowerShell được parse AST, pom được parse XML; không thực thi runner. Helpers/manifest/structural-validation nằm trong ignored target, không chứa secrets.

## 4. Tài nguyên, hạn chế và việc còn lại

- Lượt này chỉ sửa file trong repo; không tạo/chạy PostgreSQL/container/SMTP, không đọc .env hoặc private credentials; không Git commit/push, Compose, persistent DB, real email hoặc cloud resource.
- Advisory lock chung chỉ serialize các transaction Friend mutations; transaction ngắn, không network. Đây là lựa chọn đơn giản cho project/local scope, hạn chế parallel writes và cần đánh giá throughput trước triển khai lớn.
- SMTP+DB không có transaction phân tán hoặc durable outbox; process crash có thể để SENDING và thư cũ có thể tới muộn. Người gửi chủ động retry sau 30 giây, rotate token và compare attempt UUID; không cam kết exactly-once email.
- Cutoff chặn old pending theo cặp account ngay khi accept/incoming. Row theo email chưa resolve được account có thể chưa materialize INVALIDATED ngay; không dùng trạng thái vật lý PENDING thay permission check.
- Cần runtime STYLEMIND_FRIEND_INVITATION_PAGE_URL hợp lệ; frontend phải làm trang parse fragment/login/register/verify/resume/explicit accept. Backend không tự tạo frontend page hoặc auto-accept GET.
- Chưa build/test nên vẫn có thể có compile/runtime/regression issues; không gọi source written là task Done hoặc production ready.
- Full Identity gate cũ có 15 baseline failures đã được tái hiện trước lượt này; chưa sửa, chưa rerun hoặc đánh dấu pass. Các checkpoint 263 Wardrobe/40 Notification/9 Identity lookup và Newman 113/305 là evidence trước code mới.
- Sau focused/regression còn actual invitation/auth/email/Chat/unfriend/refriend E2E, Postman thực và browser acceptance. Cloud thật để sau theo Owner. Share Outfit Task 04–07 vẫn ON_HOLD.

## 5. Exact file list so với baseline trước lượt code

User đã cung cấp Git status trước đợt triển khai; không tự chạy Git. Baseline hash giữ mọi staged/unstaged/untracked file đã có; manifest không ghi nhận file mất. File riêng tư/target được loại khỏi scan. Sáu suffix lịch sử v4.1 giữ nguyên, hai master hiện hành đồng nhất.

### Updated (34)

- [docs/planning/wardrobe/EP06_FRIENDS_DEPENDENCIES_CONTRACT.md](../../../docs/planning/wardrobe/EP06_FRIENDS_DEPENDENCIES_CONTRACT.md)
- [docs/planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md](../../../docs/planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md)
- [docs/planning/wardrobe/EP06_FRIENDS_INVITATION_RULES_2026-10-28.md](../../../docs/planning/wardrobe/EP06_FRIENDS_INVITATION_RULES_2026-10-28.md)
- [docs/planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md](../../../docs/planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md)
- [docs/planning/wardrobe/EP06_TASK02_INVITATION_API_DESIGN.md](../../../docs/planning/wardrobe/EP06_TASK02_INVITATION_API_DESIGN.md)
- [docs/planning/wardrobe/EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md](../../../docs/planning/wardrobe/EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md)
- [docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md](../../../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md)
- [identity-service/src/main/java/com/stylemind/identity/controller/internal/InternalAccountEmailVerificationController.java](../../../identity-service/src/main/java/com/stylemind/identity/controller/internal/InternalAccountEmailVerificationController.java)
- [identity-service/src/main/java/com/stylemind/identity/repository/account/AccountRepository.java](../../../identity-service/src/main/java/com/stylemind/identity/repository/account/AccountRepository.java)
- [identity-service/src/main/java/com/stylemind/identity/service/internal/AccountEmailVerificationLookupService.java](../../../identity-service/src/main/java/com/stylemind/identity/service/internal/AccountEmailVerificationLookupService.java)
- [identity-service/src/main/java/com/stylemind/identity/service/internal/impl/AccountEmailVerificationLookupServiceImpl.java](../../../identity-service/src/main/java/com/stylemind/identity/service/internal/impl/AccountEmailVerificationLookupServiceImpl.java)
- [identity-service/src/test/java/com/stylemind/identity/InternalAccountLookupPostgresIntegrationTests.java](../../../identity-service/src/test/java/com/stylemind/identity/InternalAccountLookupPostgresIntegrationTests.java)
- [postman/EP06_MANAGE_FRIENDS_RUN_GUIDE.md](../../../postman/EP06_MANAGE_FRIENDS_RUN_GUIDE.md)
- [postman/README.md](../../../postman/README.md)
- [postman/StyleMind-Wardrobe-Manage-Friends.postman_collection.json](../../../postman/StyleMind-Wardrobe-Manage-Friends.postman_collection.json)
- [StyleMind_EP06_11_Task_Specs/00_START_HERE.md](../../../StyleMind_EP06_11_Task_Specs/00_START_HERE.md)
- [StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md)
- [StyleMind_EP06_11_Task_Specs/TASK_02_manage_friends_api.md](../../../StyleMind_EP06_11_Task_Specs/TASK_02_manage_friends_api.md)
- [StyleMind_EP06_11_Task_Specs/TASK_03_manage_friends_tests.md](../../../StyleMind_EP06_11_Task_Specs/TASK_03_manage_friends_tests.md)
- [StyleMind_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_Coding_Spec_By_Task_v4.md)
- [wardrobe-community-service/pom.xml](../../../wardrobe-community-service/pom.xml)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/identity/IdentityEmailVerificationClient.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/identity/IdentityEmailVerificationClient.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/identity/IdentityFeignClient.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/identity/IdentityFeignClient.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendInvitationSummaryResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendInvitationSummaryResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/FriendInvitation.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/FriendInvitation.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/Friendship.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/Friendship.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/errors/ApiExceptionHandler.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/errors/ApiExceptionHandler.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/friendship/FriendInvitationRepository.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/friendship/FriendInvitationRepository.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendshipPersistenceService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendshipPersistenceService.java)
- [wardrobe-community-service/src/main/resources/application.properties](../../../wardrobe-community-service/src/main/resources/application.properties)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatPersistencePostgresTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatPersistencePostgresTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipServiceTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipServiceTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsPersistencePostgresTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsPersistencePostgresTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/IdentityEmailVerificationClientTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/IdentityEmailVerificationClientTests.java)

### Added (29)

- [docs/qa/wardrobe/EP06_TASK02_INVITATIONS_IMPLEMENTATION_REPORT.md](../../../docs/qa/wardrobe/EP06_TASK02_INVITATIONS_IMPLEMENTATION_REPORT.md)
- [identity-service/src/main/java/com/stylemind/identity/dto/internal/AccountEmailLookupRequest.java](../../../identity-service/src/main/java/com/stylemind/identity/dto/internal/AccountEmailLookupRequest.java)
- [identity-service/src/main/java/com/stylemind/identity/validation/internal/EmailLookupRequestValidator.java](../../../identity-service/src/main/java/com/stylemind/identity/validation/internal/EmailLookupRequestValidator.java)
- [postman/EP06_FRIEND_INVITATIONS_RUN_GUIDE.md](../../../postman/EP06_FRIEND_INVITATIONS_RUN_GUIDE.md)
- [postman/StyleMind-Friend-Invitations.postman_environment.example.json](../../../postman/StyleMind-Friend-Invitations.postman_environment.example.json)
- [postman/StyleMind-Wardrobe-Friend-Invitations.postman_collection.json](../../../postman/StyleMind-Wardrobe-Friend-Invitations.postman_collection.json)
- [scripts/verify-ep06-friend-invitations.ps1](../../../scripts/verify-ep06-friend-invitations.ps1)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/identity/payload/AccountEmailLookupRequest.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/client/identity/payload/AccountEmailLookupRequest.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/friendship/FriendInvitationController.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/friendship/FriendInvitationController.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/AcceptFriendInvitationRequest.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/AcceptFriendInvitationRequest.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendInvitationActionResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendInvitationActionResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/IncomingFriendInvitationListResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/IncomingFriendInvitationListResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/IncomingFriendInvitationResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/IncomingFriendInvitationResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/SendFriendInvitationRequest.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/SendFriendInvitationRequest.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/errors/FriendInvitationFailure.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/errors/FriendInvitationFailure.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/IncomingInvitationPage.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/IncomingInvitationPage.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/InvitationActionResult.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/InvitationActionResult.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/InvitationPreparation.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/InvitationPreparation.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/FriendInvitationService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/FriendInvitationService.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/impl/FriendInvitationServiceImpl.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/impl/FriendInvitationServiceImpl.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendInvitationLinks.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendInvitationLinks.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendInvitationPersistenceService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendInvitationPersistenceService.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendshipMutationLock.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendshipMutationLock.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/validation/friendship/FriendInvitationRequestValidator.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/validation/friendship/FriendInvitationRequestValidator.java)
- [wardrobe-community-service/src/main/resources/db/migration/V5__track_friend_invitation_delivery_and_invalidation.sql](../../../wardrobe-community-service/src/main/resources/db/migration/V5__track_friend_invitation_delivery_and_invalidation.sql)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationRequestValidatorTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationRequestValidatorTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationServiceTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationServiceTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationStateMigrationPostgresTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationStateMigrationPostgresTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationsApiPostgresIntegrationTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationsApiPostgresIntegrationTests.java)
