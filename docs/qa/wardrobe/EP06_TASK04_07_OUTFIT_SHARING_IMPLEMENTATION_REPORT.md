# EP06 Tasks 04–07 — Share Outfit implementation

**2026-10-29 — Tasks 04/05/06/07 DONE_LOCAL trong phạm vi backend; regression và actual runtime PASS.** Owner đã duyệt [S1–S4/API cutover](../../planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md) và [scope V6](../../planning/wardrobe/EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md). S1: **“Giữ PRIVATE, tôi tự đổi sang FRIENDS”**. Không suy duyệt từ source cũ hoặc status backlog. Workbook tại đường dẫn AGENTS không có; không sửa workbook.

## Kết quả thực hiện

- V6 thêm Outfit.visibility mặc định PRIVATE, backfill public link có sẵn, hai CHECK và partial page index. V1–V5 giữ nguyên; legacy grants/items/Friends/Chat không bị xóa hoặc tự mở quyền.
- Owner GET/PATCH sharing; current-friend list/detail theo FRIENDS/PUBLIC; public guest reader kiểm tra mode hiện tại. PRIVATE không có trong items/count. Owner summary/detail thêm visibility, CRUD rules giữ nguyên.
- Public token được giữ khi PRIVATE/FRIENDS; bật PUBLIC lại dùng cùng token; PUBLIC no-op giữ timestamp. DELETE public-link khi FRIENDS không đổi mode.
- API grant/feed email cũ trả 410 rõ ràng, không tạo/revoke grant hoặc gửi email. Friends invitations dùng API riêng, không đổi Outfit; không gắn Chat vào Outfit.
- Layer Controller → Service interface → impl → internal persistence/policy → JPA Repository. Chỉ sharing slice đổi layout; không refactor toàn CRUD Khánh hoặc framework của service khác.

## Requirement → implementation → evidence

| Rule đã duyệt | Model/endpoint/validation | Authorization/transaction/side effects | Evidence |
| --- | --- | --- | --- |
| S1 PRIVATE mặc định, Owner tự chọn mode | V6 + Entity default; GET/PATCH `/me/outfits/{id}/sharing`; strict raw JSON enum, reject duplicate/trailing/extra fields | JWT CUSTOMER, owner from sub; owned Outfit write lock; no mail/invite | Validator 20 cases; owner/validation HTTP; actual invitation runtime scenario |
| FRIENDS cho tất cả current friends | Active canonical Friendship + visibility FRIENDS/PUBLIC; friend list/detail DTO | Precheck và post-I/O check; list snapshot REPEATABLE_READ count/page; không network trong transaction | A/B/C + second friend; PRIVATE count/page; wrong parent; unfriend/refriend; mode/unfriend during I/O |
| PUBLIC guest, same link OFF/ON | Existing token/enabled_at + visibility; public-link aliases | Owner-only mutations; guest read current mode; giữ token inactive, không expiry/rotation | Lifecycle/no-op/alias/guest/deletion, safe error instance |
| Viewer chỉ VIEW | Owner CRUD/settings từ JWT sub, friend DTO không secrets | Foreign actor 404 trên rename/items/delete/public-link; active friend không thành owner | Valid-body negative HTTP CRUD; missing JWT/ADMIN-only; DTO checks |
| S2 backfill không mở FRIENDS từ email grant | V6 abort invalid existing token; preserve old rows/history/token/timestamps | DB-local migration, không Identity/Commerce/SMTP | Fresh + seeded V5→V6 + rollback/preflight; checksum và fingerprints |
| S3 legacy 410 | `/shares...` và `/me/shared-outfits...` deprecated Swagger | Owner check phù hợp trước 410; không ghi grant/mail | HTTP retired checks + Mockito no interactions; old row không bypass PRIVATE |
| Concurrent writes/rollback | Owned Outfit PESSIMISTIC_WRITE, atomic mode/token/time | No-op không ghi; last serialized writer; local rollback giữ toàn tuple | Actual row-lock blocking, concurrent writers, rollback assertions |
| Dependency không cấp quyền | Identity presentation / Commerce catalog lookup ngoài DB tx | 503 theo convention; recheck mode/friendship trước trả projection | Unavailable variant + downstream failure; actual JAR HTTP with catalog fixture |

## Commands và kết quả

```powershell
.\scripts\verify-ep06-outfit-sharing.ps1 -Mode Focused
.\scripts\verify-ep06-outfit-sharing.ps1 -Mode Regression
.\scripts\verify-ep06-chat-runtime.ps1 -SkipRegression
```

- Focused checkpoint: **77 tests, 0 failures/errors/skipped**, Java 21/Maven/PostgreSQL 18.6. Evidence `wardrobe-community-service/target/ep06-outfit-sharing-focused-20261029092628/`.
- Final regression: **314 tests / 33 classes, 0 failures/errors/skipped; BUILD SUCCESS; 01:24**. Evidence `wardrobe-community-service/target/ep06-outfit-sharing-regression-20261029093417/`. Bao gồm Outfit CRUD, new sharing, Friends/invitation, Chat sessions/text/images/WebSocket. Số 77 là focused checkpoint trước bổ sung chi tiết Swagger; final 314 bao gồm các assertion mới.
- Actual runtime: **1 JUnit, 0 failures/errors/skipped, 83.52 s; BUILD SUCCESS** tại `wardrobe-community-service/target/ep06-chat-runtime-fake-20261029093705/`. Bốn JAR thật + Mailpit và PostgreSQL disposable. S1 được assert bằng lời mời/Register/verify/login/accept thật: PRIVATE vẫn bị chặn cho tới khi Owner chọn FRIENDS; unfriend/refriend chặn/khôi phục cùng Outfit.
- Newman: **6 collections / 8 runs / 155 requests / 380 assertions, 0 failures**. Share Outfit mới: **32 requests / 55 assertions**; Friends28/89, Sessions30/81, Messages27/69, Images28/66, Invitation ba fixture tổng10/20. Không tính JSON structural check hoặc package skipTests là HTTP pass; gate314 đã pass trước `-SkipRegression` cùng production source.
- Static handover: Collection mới **32 requests / 33 scripts**, JSON parse và JavaScript compilation pass; environment secrets trống/opt-in false. **97 local links**, **8 historical suffixes**, master copies identical và SHA256 V1–V5 unchanged pass. Đây là static check riêng với actual HTTP/Newman nêu trên.

Các lần chạy đầu có lỗi compile suy luận JsonNode, Mockito restub fixture null, request thiếu Content-Type và assertion Chat version list cũ. Đã sửa đúng nguyên nhân rồi chạy gate lại. Không skip/disable test hoặc thay đổi business để bỏ lỗi. Các evidence folder thất bại được giữ để truy vết.

## Mapping test cũ → contract mới

`OutfitSharePostgresIntegrationTests` trước có 17 tests theo email grant, public disable xóa token. Theo approval S3/S4, thay bằng 14 scenarios HTTP/database rộng hơn. Snapshot nguyên bản ở `target/ep06-outfit-sharing-20261029/before/.../OutfitSharePostgresIntegrationTests.java`.

| Nhóm test cũ | Coverage hiện tại |
| --- | --- |
| Create/normalize/reuse/regrant email, share-self, cap20/cap30, mail failure, list/revoke grant | `legacyEmailGrantsAndFeedAreRetiredWithoutMailOrMutations`: 410, giữ rows, không SMTP, legacy grant không cấp quyền; lời mời kết bạn thuộc bộ Friends riêng, không kế thừa cap grant cũ |
| Shared viewer, revoked/foreign/owner denial, grant-based pagination | Friends all-current-friends + PRIVATE isolation/count/page + parent isolation + unfriend/refriend; owner CRUD có gate riêng |
| Body/UUID/ownership | Strict visibility validator 20 cases + HTTP malformed body/UUID/Owner A/B/C, JWT/role matrix |
| Identity/Commerce failures | Downstream 503 + unavailable item; live mode/friendship recheck sau I/O, không network trong tx |
| New token after disable | Owner-approved same token OFF/ON, no-op activation time, alias và guest denied khi inactive |
| Delete cascade/link | Owner delete chặn cả friend/public readers; CRUD regression hiện có giữ nguyên |

`FriendsPersistencePostgresTests` và `ChatPersistencePostgresTests` fresh history mong đợi thêm V6; giữ toàn bộ FK/check/concurrency assertions. Historical V4→V5 test pin target5 thay vì chạy latest để tiếp tục kiểm tra **chỉ V5**; không thay V1–V5 migrations.

## Files và tài nguyên

Exact file inventory ở cuối báo cáo; checksum và static/runtime totals tại `target/ep06-outfit-sharing-20261029/verification-results.json`. Các file thay đổi từ Friends/Chat/cloud trước lượt này không được tính vào inventory Share Outfit.

Production groups: V6/Outfit Entity/Repository; owner summary/detail mappers; sharing/friend controllers + legacy/public adapters; sharing DTOs/snapshots/validator; two Service interfaces/impl và three internal policy/persistence/read classes; scoped exception advice; ApiProblemDetails che public token khỏi error instance. Public route/security Gateway sẵn có được reuse, không thêm route hoặc sửa service khác.

Handover: [collection](../../../postman/StyleMind-EP06-Share-Outfits.postman_collection.json), [environment](../../../postman/StyleMind-Outfit-Sharing.postman_environment.example.json), [run guide](../../../postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md), [cutover](../../operations/wardrobe/EP06_OUTFIT_SHARING_CUTOVER.md). Task/index/master/progress cập nhật cùng trạng thái hiện hành, giữ checkpoint cũ như lịch sử.

Chỉ Maven cache volume và disposable PostgreSQL/Mailpit/runner resources do test tạo bị ghi. Repo source mount read-only trong runner, copy build vào /tmp; formatter chỉ chạm sharing slice mới. Không dùng DB lâu dài, không Compose, không SMTP thật bên ngoài, không chạm B2 bucket/credential trong lượt này. Không chạy Git/commit/push. Test resources/JVMs dọn khi kết thúc.

## Giới hạn và phần còn lại

Browser/frontend acceptance Task11 và tích hợp UI sharing chưa chạy. Bốn JAR runtime dùng Commerce HTTP fixture và storage HTTP fake; không là Commerce end-to-end hoặc cloud pass mới. Backblaze đã có evidence riêng ở [checkpoint trước](EP06_BACKBLAZE_VERIFICATION_2026-10-29.md), không cần ghi cloud lại vì Share Outfit không đổi Chat storage.

Identity regression trước còn **15 baseline failures**, đợt này không sửa Identity hay báo full Identity pass; package skipTests phục vụ runtime không thay regression của Identity/Notification/Gateway. Không có quyết định nghiệp vụ Share Outfit còn chờ. Deploy phải phối hợp cutover; không trộn old/new binary hoặc downgrade reader cũ. Local evidence không phải production readiness.

## Exact file inventory

Danh sách chỉ gồm file đã sửa/tạo cho Share Outfit trong đợt triển khai này; không phải toàn bộ working tree. File target/cache/evidence không phải source bàn giao.

### Production

- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/Outfit.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/Outfit.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/outfit/OutfitRepository.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/outfit/OutfitRepository.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/OutfitService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/OutfitService.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/OutfitShareService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/OutfitShareService.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/SharedOutfitService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/SharedOutfitService.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/impl/OutfitShareServiceImpl.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/impl/OutfitShareServiceImpl.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/impl/SharedOutfitServiceImpl.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/impl/SharedOutfitServiceImpl.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/internal/OutfitSharingAccessPolicy.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/internal/OutfitSharingAccessPolicy.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/internal/OutfitSharingReadService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/internal/OutfitSharingReadService.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/internal/OutfitSharingPersistenceService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/outfit/internal/OutfitSharingPersistenceService.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/outfit/OutfitViewerSnapshot.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/outfit/OutfitViewerSnapshot.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/outfit/FriendOutfitPageSnapshot.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/outfit/FriendOutfitPageSnapshot.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/OutfitSharingController.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/OutfitSharingController.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/FriendOutfitController.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/FriendOutfitController.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/OutfitShareController.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/OutfitShareController.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/SharedOutfitController.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/outfit/SharedOutfitController.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/OutfitSharingResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/OutfitSharingResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/OutfitVisibilityRequest.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/OutfitVisibilityRequest.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/FriendOutfitListResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/FriendOutfitListResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/FriendOutfitSummaryResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/FriendOutfitSummaryResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/OutfitDetailResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/OutfitDetailResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/OutfitSummaryResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/outfit/OutfitSummaryResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/validation/outfit/OutfitVisibilityRequestValidator.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/validation/outfit/OutfitVisibilityRequestValidator.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/errors/OutfitSharingExceptionHandler.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/errors/OutfitSharingExceptionHandler.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/security/ApiProblemDetails.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/security/ApiProblemDetails.java)
- [wardrobe-community-service/src/main/resources/db/migration/V6__add_outfit_visibility.sql](../../../wardrobe-community-service/src/main/resources/db/migration/V6__add_outfit_visibility.sql)

### Automated tests

- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/OutfitSharePostgresIntegrationTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/OutfitSharePostgresIntegrationTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/OutfitVisibilityRequestValidatorTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/OutfitVisibilityRequestValidatorTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/OutfitVisibilityMigrationPostgresTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/OutfitVisibilityMigrationPostgresTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsPersistencePostgresTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsPersistencePostgresTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatPersistencePostgresTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatPersistencePostgresTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationStateMigrationPostgresTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationStateMigrationPostgresTests.java)

### Runtime/Postman tooling

- [scripts/verify-ep06-outfit-sharing.ps1](../../../scripts/verify-ep06-outfit-sharing.ps1)
- [scripts/ep06-chat-runtime/src/test/java/com/stylemind/verification/ChatRuntimeIntegrationTests.java](../../../scripts/ep06-chat-runtime/src/test/java/com/stylemind/verification/ChatRuntimeIntegrationTests.java)
- [scripts/ep06-chat-runtime/run-newman.cjs](../../../scripts/ep06-chat-runtime/run-newman.cjs)
- [postman/StyleMind-EP06-Share-Outfits.postman_collection.json](../../../postman/StyleMind-EP06-Share-Outfits.postman_collection.json)
- [postman/StyleMind-Outfit-Sharing.postman_environment.example.json](../../../postman/StyleMind-Outfit-Sharing.postman_environment.example.json)

### Contracts/progress/handover

- [docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md](../../../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md)
- [StyleMind_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_Coding_Spec_By_Task_v4.md)
- [StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md)
- [StyleMind_EP06_11_Task_Specs/00_START_HERE.md](../../../StyleMind_EP06_11_Task_Specs/00_START_HERE.md)
- [StyleMind_EP06_11_Task_Specs/TASK_04_wardrobe_share_migration.md](../../../StyleMind_EP06_11_Task_Specs/TASK_04_wardrobe_share_migration.md)
- [StyleMind_EP06_11_Task_Specs/TASK_05_wardrobe_sharing_api.md](../../../StyleMind_EP06_11_Task_Specs/TASK_05_wardrobe_sharing_api.md)
- [StyleMind_EP06_11_Task_Specs/TASK_06_authorization_all_resources.md](../../../StyleMind_EP06_11_Task_Specs/TASK_06_authorization_all_resources.md)
- [StyleMind_EP06_11_Task_Specs/TASK_07_wardrobe_sharing_tests.md](../../../StyleMind_EP06_11_Task_Specs/TASK_07_wardrobe_sharing_tests.md)
- [docs/planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md](../../../docs/planning/wardrobe/EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md)
- [docs/planning/wardrobe/EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md](../../../docs/planning/wardrobe/EP06_TASK04_OUTFIT_VISIBILITY_DATABASE_SCOPE.md)
- [docs/qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md](../../../docs/qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md)
- [docs/operations/wardrobe/EP06_OUTFIT_SHARING_CUTOVER.md](../../../docs/operations/wardrobe/EP06_OUTFIT_SHARING_CUTOVER.md)
- [postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md](../../../postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md)
- [postman/README.md](../../../postman/README.md)
- [scripts/ep06-chat-runtime/README.md](../../../scripts/ep06-chat-runtime/README.md)

