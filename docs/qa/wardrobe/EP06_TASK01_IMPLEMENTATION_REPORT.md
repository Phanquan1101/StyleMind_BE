# EP06 Task 01 — Báo cáo triển khai Friends DB/JPA

**Ngày: 2026-10-24. Trạng thái: DONE_LOCAL ở phạm vi Task 01 được duyệt.** Owner giao bắt đầu code Friends và trả lời “duyệt” cho [scope hai bảng Friends](../../planning/wardrobe/EP06_TASK01_FRIENDS_DATABASE_SCOPE.md). Chưa hoàn thành User Story Manage Friends hoặc Chat; chưa áp migration vào DB lâu dài.

## Kết quả

**Ghi chú layout 2026-10-25:** Owner yêu cầu chuẩn hóa interface/`impl`/`internal`; policy và canonical pair đã chuyển package. Các link file bên dưới trỏ vị trí hiện tại; kết quả 2026-10-24 vẫn là checkpoint gốc. Xem [báo cáo refactor](EP06_SERVICE_LAYOUT_REFACTOR_2026-10-25.md).

- V3 thêm `friend_invitation` và `friendship`, chính xác DDL đã duyệt; không sửa V1/V2 hoặc bảng Outfit/Item/Share.
- Entity/Repository theo convention Controller → Service → Repository và Spring Data JPA của source đã pull. Chưa thêm Controller/HTTP route.
- Canonical pair không phân biệt chiều, không tự kết bạn; Java dùng unsigned UUID order trùng PostgreSQL.
- Lời mời có thể tồn tại khi recipient chưa có account; digest-only, hạn 168 giờ, unique PENDING theo sender/email; trạng thái ACCEPTED yêu cầu recipient khác sender và timestamp hợp lệ.
- Policy thực thi hạn 7 ngày và matching verified current email từ facts server-trusted. Token 32 byte random, Base64URL, digest SHA-256 ràng buộc invitation ID/purpose, so sánh constant-time; object credential redacts `toString`.
- Repository hỗ trợ lookup owner, incoming email, active pair hai chiều, phân trang và pessimistic row locks. Caller vẫn phải thực thi JWT/current Identity/credential authorization; repository không phải endpoint bảo mật.
- Unfriend/re-friend ở persistence giữ cùng pair row; chưa có Chat nên chưa thể chứng minh giữ/phục hồi lịch sử Chat qua runtime.

## Verification đã thực hiện

| Lần chạy | Phạm vi | Test | Failed | Errors | Skipped | Kết quả |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| baseline | Source trước đợt code | 62 | 0 | 0 | 0 | Maven verify BUILD SUCCESS |
| foundation | Pair/policy/token + regression | 85 | 0 | 0 | 0 | Maven verify BUILD SUCCESS |
| task01 | V3, JPA, PostgreSQL và toàn regression | 102 | 0 | 0 | 0 | Maven verify BUILD SUCCESS |

**40 tests Friends mới:** 6 pair + 9 policy + 8 token + 17 PostgreSQL persistence/integration. Không cộng dồn 62+85+102 thành tổng khác; lần cuối có 102 test trong 10 class.

Evidence Task 01 gồm fresh V1→V2→V3 với Hibernate schema validation, upgrade V2 có seed Outfit/Item/Share/public link và checksum V1/V2, database constraints, owner A/B/C isolation ở query, accepted tuple/deadline, partial uniqueness, canonical UUID order, unfriend/re-friend cùng row, rollback accept+pair, rollback/commit digest+deadline resend, concurrent duplicate/pair races và pessimistic write lock.

Các test transaction/persistence dùng trực tiếp repository để kiểm tra cơ chế DB, chưa kiểm tra orchestration HTTP nhận lời mời hoặc SMTP. Existing controller/HTTP regression thuộc Outfit/scaffold, không chứng minh HTTP Friends đã triển khai.

**Lệnh thực tế trong mỗi Maven container:** `mvn -B -ntp verify`. Java 21/Maven 3.9.11 từ image `maven:3.9.11-eclipse-temurin-21-alpine`; PostgreSQL 18.6 qua Testcontainers. Host Java 25/Maven PATH không được dùng để kết luận build pass.

Runner dùng `docker run --rm`, mount service source read-only, chỉ copy `pom.xml` và `src` vào `/tmp/ep06-project`; không copy hoặc chạy lại `target` cũ. Mount Docker socket cho Testcontainers, `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`, Maven cache riêng `stylemind-ep06-maven-cache-20261024`. Source không được build inplace trong container.

Artifacts nằm trong thư mục ignored [target/ep06-friends-20261024](../../../wardrobe-community-service/target/ep06-friends-20261024):
- `baseline-maven.log`, `baseline-surefire-reports/`.
- `foundation-maven.log`, `foundation-surefire-reports/`.
- `task01-maven.log`, `task01-surefire-reports/`.

Không trích raw test request/token vào báo cáo. Log hiện có có thể chứa fixture của test; không coi đó là log production hoặc dữ liệu người dùng.

Để chạy lại khi máy có Java 21/Maven và Docker:
```powershell
mvn -B -ntp -f wardrobe-community-service/pom.xml verify
```
Không cần Compose hay database service đang chạy lâu dài. Tất cả integration classes cấu hình datasource từ PostgreSQLContainer của chính test.

## Bảo toàn source và môi trường

- Đã đối chiếu SHA-256: **420/420 file source/test/SQL/config/pom có từ trước giữ nguyên** trong Wardrobe, Identity, Notification, Commerce và Gateway; 12 file code/SQL/test mới chỉ thuộc Wardrobe.
- Kiểm tra tài liệu: 15 body v4.1 giữ nguyên theo SHA-256, 18 tài liệu/91 liên kết hợp lệ, hai bản master giống nhau và V3 khớp chính xác SQL trong scope đã duyệt. AGENTS, Functional Requirements, reference v3 và báo cáo QA cũ giữ nguyên hash.
- Không chạy Git, không sửa ignored `.env`, không đổi dependency/security/config/public contracts của thành viên khác, không gửi email hay gọi service thật.
- Chỉ các PostgreSQLContainer tạm thời và database `friends_upgrade_test` bên trong container test được ghi. Không kết nối DB lâu dài, không Compose. Maven cache là build cache, không phải database.
- V3 sẽ được Flyway áp khi service chạy với DB tương ứng; lần verify này không tự triển khai vào DB persistent.
- Không sửa workbook tại đường dẫn AGENTS vì file không tồn tại trên máy; không đổi status của thành viên khác.

## Danh sách file chính xác

### Code, SQL và test mới — 12 file

- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/FriendInvitation.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/FriendInvitation.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/Friendship.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/Friendship.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/friendship/FriendInvitationRepository.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/friendship/FriendInvitationRepository.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/friendship/FriendshipRepository.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/friendship/FriendshipRepository.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/security/FriendInvitationTokenService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/security/FriendInvitationTokenService.java)
- [FriendInvitationPolicy.java — vị trí hiện tại](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendInvitationPolicy.java)
- [FriendshipPair.java — vị trí hiện tại](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/FriendshipPair.java)
- [wardrobe-community-service/src/main/resources/db/migration/V3__create_friend_invitation_and_friendship_tables.sql](../../../wardrobe-community-service/src/main/resources/db/migration/V3__create_friend_invitation_and_friendship_tables.sql)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationPolicyTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationPolicyTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationTokenServiceTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationTokenServiceTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipPairTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipPairTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsPersistencePostgresTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsPersistencePostgresTests.java)

### Tài liệu mới — 3 file

- [docs/planning/wardrobe/EP06_TASK01_FRIENDS_DATABASE_SCOPE.md](../../../docs/planning/wardrobe/EP06_TASK01_FRIENDS_DATABASE_SCOPE.md)
- [docs/planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md](../../../docs/planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md)
- [docs/qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md](../../../docs/qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md)

### Cập nhật notice/tiến độ — 15 file

Chỉ sửa phần v4.2 hiện hành; giữ nguyên toàn bộ body v4.1 gốc bên dưới.

- [docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md](../../../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md)
- [StyleMind_EP06_11_Task_Specs/00_START_HERE.md](../../../StyleMind_EP06_11_Task_Specs/00_START_HERE.md)
- [StyleMind_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_Coding_Spec_By_Task_v4.md)
- [StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md)
- [StyleMind_EP06_11_Task_Specs/TASK_01_friendship_requirement_conflict.md](../../../StyleMind_EP06_11_Task_Specs/TASK_01_friendship_requirement_conflict.md)
- [StyleMind_EP06_11_Task_Specs/TASK_02_manage_friends_api.md](../../../StyleMind_EP06_11_Task_Specs/TASK_02_manage_friends_api.md)
- [StyleMind_EP06_11_Task_Specs/TASK_03_manage_friends_tests.md](../../../StyleMind_EP06_11_Task_Specs/TASK_03_manage_friends_tests.md)
- [StyleMind_EP06_11_Task_Specs/TASK_04_wardrobe_share_migration.md](../../../StyleMind_EP06_11_Task_Specs/TASK_04_wardrobe_share_migration.md)
- [StyleMind_EP06_11_Task_Specs/TASK_05_wardrobe_sharing_api.md](../../../StyleMind_EP06_11_Task_Specs/TASK_05_wardrobe_sharing_api.md)
- [StyleMind_EP06_11_Task_Specs/TASK_06_authorization_all_resources.md](../../../StyleMind_EP06_11_Task_Specs/TASK_06_authorization_all_resources.md)
- [StyleMind_EP06_11_Task_Specs/TASK_07_wardrobe_sharing_tests.md](../../../StyleMind_EP06_11_Task_Specs/TASK_07_wardrobe_sharing_tests.md)
- [StyleMind_EP06_11_Task_Specs/TASK_08_chat_migrations.md](../../../StyleMind_EP06_11_Task_Specs/TASK_08_chat_migrations.md)
- [StyleMind_EP06_11_Task_Specs/TASK_09_chat_session_api.md](../../../StyleMind_EP06_11_Task_Specs/TASK_09_chat_session_api.md)
- [StyleMind_EP06_11_Task_Specs/TASK_10_chat_messages_api.md](../../../StyleMind_EP06_11_Task_Specs/TASK_10_chat_messages_api.md)
- [StyleMind_EP06_11_Task_Specs/TASK_11_chat_tests.md](../../../StyleMind_EP06_11_Task_Specs/TASK_11_chat_tests.md)

## Giới hạn và bước tiếp theo

- Task 01 DONE_LOCAL không đồng nghĩa Task 02/03 hoặc cả US-06.01 Done.
- Hoàn thiện contract API Friends (DTO/error/paging, lời mời chéo, pending invitation khi unfriend, repeated accept, resend limits) trước triển khai nhánh tương ứng. Route skeleton trong plan chưa là API đang chạy.
- [Handoff Identity/Notification](../../planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md): Quân cung cấp verified current email contract; Vi cung cấp friend-invitation template/endpoint. Không tự làm lại Register hay đổi nghĩa Outfit invitation.
- Khi thêm HTTP APIs phải có Swagger/OpenAPI, importable Postman JSON và run guide, controller/HTTP/authorization/concurrency phù hợp. Đợt này không có HTTP API Friends mới; chưa có runtime/Postman evidence cho Friends.
- Chat Task 08–11 và provider/storage ảnh chưa triển khai; DDL Chat cần scope riêng. Share Outfit Task 04–07 giữ ON_HOLD.
- Chưa kiểm thử E2E register-resume, email thật, WebSocket, upload ảnh, deployment hoặc production readiness.
