# EP06 Task 02 A — Manage Friends local APIs

**Ngày: 2026-10-24. Trạng thái: phần A DONE_LOCAL; toàn Task 02/03 PARTIAL.** Owner yêu cầu “rồi tiếp tục” sau Task 01 và cung cấp Git status hiện tại. Nghiệp vụ không thay đổi: dùng JWT actor, quan hệ hai chiều, unfriend giữ dữ liệu; gửi/nhận lời mời và Chat còn dependency riêng.

## Kết quả và giới hạn phạm vi

**Ghi chú layout 2026-10-25:** `FriendshipService.java` hiện là interface; implementation và persistence tách sang `impl`/`internal` theo yêu cầu Owner. File list/kết quả dưới đây mô tả checkpoint triển khai 2026-10-24; [báo cáo refactor](EP06_SERVICE_LAYOUT_REFACTOR_2026-10-25.md) ghi vị trí mới và lần regression tiếp theo, không đổi scope API hay trạng thái PARTIAL.

Theo [contract API](../../planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md), đã triển khai:

| Method | Path nội bộ | Chức năng |
| --- | --- | --- |
| GET | `/api/v1/me/friends` | Active friends, stable pagination, Identity display name |
| GET | `/api/v1/me/friend-invitations` | Chỉ lời mời do chính actor gửi; effective expiry |
| DELETE | `/api/v1/me/friends/{friendAccountId}` | Unfriend, giữ canonical pair và invitation history |

Qua Gateway dùng prefix `/wardrobe-community` hiện có. Không sửa Gateway, Identity, Notification, Outfit controllers/services/entities hoặc migration V1–V3.

- Controller → Service → Repository JPA, DTO/validator tách package; theo source hiện có và lựa chọn JPA của Owner.
- CUSTOMER-only cho ba route mới, subject UUID từ JWT; ADMIN-only không có quyền, CUSTOMER+ADMIN vẫn được dùng như customer.
- List mặc định page 0/size 20, size tối đa 100, chặn query lạ/duplicate/owner override và UUID rút gọn. Offset vượt int không tràn JPA.
- Read-only repeatable-read snapshot cho count/page. Chỉ gọi Identity cho peer IDs sau khi transaction đã kết thúc; không trả email/status tài khoản của peer. Unknown profile trả display name null; Identity failure trả 503 theo client hiện có.
- Outgoing chỉ đọc theo sender actor; không gửi mail, không trả token/hash/link hoặc tự khẳng định email delivery. Expired PENDING được biểu diễn EXPIRED trong DTO, không ghi DB khi GET.
- Unfriend khóa đúng canonical pair, kiểm tra còn active rồi ghi server UTC time ở microsecond. Pair không có/đã inactive cùng 404; tự unfriend 400; concurrent A/B có một 204, một 404; write failure rollback.
- Swagger/OpenAPI cho ba route: bearer auth, query bounds, safe DTO schemas, response examples và errors. Existing Gateway Swagger entry `Wardrobe Community Service` được giữ nguyên; UI/Gateway integration thực chưa được chạy trong đợt này.

Không triển khai send/accept/resend/incoming hoặc stub 503 giả integration. Chat, revoke WebSocket và giữ/phục hồi lịch sử Chat chưa có runtime evidence. Unfriend ở đây chỉ cung cấp trạng thái Friendship cho các guard Chat tương lai.

## Verification thực tế

| Gate | Test | Failures | Errors | Skipped | Kết quả |
| --- | ---: | ---: | ---: | ---: | --- |
| Task 01 checkpoint trước đợt này | 102 | 0 | 0 | 0 | BUILD SUCCESS ở báo cáo Task 01 |
| Task 02 lần đầu | 131 | 0 | 1 | 0 | FAIL do fixture thay mock Identity gọi callback với null |
| Task 02 final | 131 | 0 | 0 | 0 | BUILD SUCCESS |

Lỗi lần đầu được sửa bằng Mockito `doThrow(...).when(...)` khi thay stub; không bỏ test hoặc nới assertion. Lần cuối có **13 test class**, tổng **131**, gồm **29 tests mới**: 5 validator + 8 service + 16 API/PostgreSQL integration.

Các case mới đã chạy:
- Thiếu/malformed/expired JWT, role không phù hợp, subject không là UUID; CUSTOMER+ADMIN.
- Actor A/B/C, active/inactive pair, query/owner override, self/malformed/missing/foreign unfriend, private DTO whitelist.
- Empty lists, stable tie pagination, huge offset, unknown Identity profile, failure response.
- Hạn lời mời before/at expiry và GET không ghi; scope outgoing theo actor.
- Unfriend hai chiều giữ row, không đổi pair khác/invitation, rollback write failure, concurrent unfriend.
- Identity client mock xác nhận không có local transaction trong lookup.
- OpenAPI JSON qua HTTP: ba route, pagination, auth scheme và safe response properties.
- **Embedded HTTP thật:** cả ba route với signed test JWT + PostgreSQL, và hai request unfriend đồng thời. Identity được mock theo contract directory hiện có, không gọi Identity thật.
- Toàn bộ 102 test hiện có chạy lại, gồm DB V3 fresh/upgrade và Outfit regression.

Lệnh trong Maven container: `mvn -B -ntp verify`. Image `maven:3.9.11-eclipse-temurin-21-alpine`, PostgreSQL `postgres:18.6` qua Testcontainers; không dùng Java 25 của host để tuyên bố pass.

Runner `docker run --rm` mount source read-only, copy chỉ `pom.xml`/`src` vào `/tmp/ep06-project`, không dùng `target` cũ. Docker socket phục vụ Testcontainers; `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`; Maven cache `stylemind-ep06-maven-cache-20261024`.

Evidence ở ignored [target/ep06-friends-20261024](../../../wardrobe-community-service/target/ep06-friends-20261024):
- `task02-first-maven.log`, `task02-first-surefire-reports/`.
- `task02-final-maven.log`, `task02-final-surefire-reports/`.

Chạy lại với Java 21/Maven và Docker:
```powershell
mvn -B -ntp -f wardrobe-community-service/pom.xml verify
```

## Postman: cấu trúc khác với runtime

Bàn giao [collection](../../../postman/StyleMind-Wardrobe-Manage-Friends.postman_collection.json), [environment mẫu](../../../postman/StyleMind-Friends.postman_environment.example.json), [run guide](../../../postman/EP06_MANAGE_FRIENDS_RUN_GUIDE.md).

- Parse JSON, declaration Collection v2.1, 5 folder/28 requests; 53 JavaScript scripts compile trên V8, request variables resolve, token examples rỗng, không có credential logging/background HTTP.
- Năm kiểm tra offline cho guard mutation đều pass: chặn khi thiếu opt-in, thiếu xác nhận fixture, cohort đã đổi; cho fixture đã xác nhận; snapshot mới reset xác nhận cũ. Đây không phải thực thi Postman sandbox hoặc HTTP.
- Folder 00–03 có 17 requests đọc/validation/auth; folder 04 có 11 requests dùng fixture riêng và opt-in, kiểm tra snapshots trước/sau unfriend.
- **Postman/Newman runtime: NOT EXECUTED.** Không được tính static validation hoặc backend embedded HTTP thành collection E2E pass; chưa có real tokens/test fixture để chạy collection.

## Bảo toàn và môi trường

Đối chiếu SHA-256 của **451 file có từ trước** trong phạm vi source/resources/Postman: **448 giữ nguyên**, chỉ 3 file thay đổi có chủ đích: SecurityConfiguration (matcher ba route mới), FriendInvitationRepository (sender count), Postman README (link bàn giao). Không có thay đổi source Identity/Commerce/Notification/Gateway hoặc migration.

Không chạy Git; không đọc/sửa `.env`, không Compose, không email thật hoặc persistent DB. Các test chỉ ghi PostgreSQLContainer riêng và upgrade database bên trong container test của Task 01. Maven cache là build cache; runner đã thoát và được `--rm`.

Kiểm tra tài liệu cuối: 20 tài liệu/129 liên kết hợp lệ, 15 body v4.1 giữ nguyên SHA-256, hai bản master giống nhau. AGENTS/Functional Requirements/reference v3/báo cáo QA cũ giữ nguyên hash. Ba file cũ được bổ sung tái tạo đúng nội dung gốc sau khi bỏ phần thêm, có tính đến line endings; không có thay đổi ngoài các đoạn dự kiến.

Workbook theo đường dẫn AGENTS không tồn tại trên máy; không sửa workbook hoặc trạng thái thành viên khác. Tài liệu v4.1 gốc vẫn giữ dưới notice hiện hành.

## File chính xác

### Source/test mới — 12 file

- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/configuration/FriendshipConfiguration.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/configuration/FriendshipConfiguration.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/friendship/FriendshipController.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/friendship/FriendshipController.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendInvitationListResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendInvitationListResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendInvitationSummaryResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendInvitationSummaryResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendListResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendListResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendPageCriteria.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendPageCriteria.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendSummaryResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/friendship/FriendSummaryResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/FriendshipService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/FriendshipService.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/validation/friendship/FriendshipRequestValidator.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/validation/friendship/FriendshipRequestValidator.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsApiPostgresIntegrationTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsApiPostgresIntegrationTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipRequestValidatorTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipRequestValidatorTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipServiceTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipServiceTests.java)

### Source/bàn giao hiện có được bổ sung — 3 file

- [postman/README.md](../../../postman/README.md)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/friendship/FriendInvitationRepository.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/friendship/FriendInvitationRepository.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/security/SecurityConfiguration.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/security/SecurityConfiguration.java)

### Contract, report và Postman mới — 5 file

- [docs/planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md](../../../docs/planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md)
- [docs/qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md](../../../docs/qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md)
- [postman/EP06_MANAGE_FRIENDS_RUN_GUIDE.md](../../../postman/EP06_MANAGE_FRIENDS_RUN_GUIDE.md)
- [postman/StyleMind-Friends.postman_environment.example.json](../../../postman/StyleMind-Friends.postman_environment.example.json)
- [postman/StyleMind-Wardrobe-Manage-Friends.postman_collection.json](../../../postman/StyleMind-Wardrobe-Manage-Friends.postman_collection.json)

### Notice/tiến độ hiện hành — 16 file

Chỉ sửa prefix v4.2 của 15 spec/task/plan và tiến độ handoff; không sửa các body v4.1 gốc, Functional Requirements, reference v3 hoặc báo cáo Task 01.

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
- [docs/planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md](../../../docs/planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md)

## Công việc còn lại

Task 02 B/Task 03 toàn story chưa Done:
- Quân/Identity: current verified recipient email contract cho accept/incoming; không suy ACTIVE là verified.
- Vi/Notification: friend-invitation endpoint/template/result contract; không đổi nghĩa Outfit invitation.
- Chốt contract invitation mutations: lời mời chéo, pending invitation có sẵn khi unfriend, repeated accept/resend limits. Link mới/hủy link cũ/hạn 7 ngày đã duyệt, không hỏi lại.
- Send/accept/resend/incoming HTTP APIs, email/register-resume E2E, Postman runtime theo fixture thực.
- Chat Task 08–11 và DDL/storage/transport contract tương ứng; Share Outfit Task 04–07 ON_HOLD.

[Plan hiện hành](../../planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [handoff](../../planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md) phân biệt phần đã làm với dependency. Kết quả local không là chứng nhận production readiness.
