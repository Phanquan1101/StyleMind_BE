# EP06 — Refactor service theo interface / impl / internal

**2026-10-25 (Asia/Bangkok). Trạng thái: DONE_LOCAL cho yêu cầu sửa cấu trúc service.** Owner chỉ ra mẫu `identity-service/service/address` và yêu cầu làm theo cấu trúc đó. Đã kiểm tra Git status do Owner cung cấp, giữ nguyên các thay đổi có sẵn; agent không chạy Git.

## Cấu trúc sau refactor

```text
service/friendship/
├── FriendshipService.java                 # interface
├── impl/
│   └── FriendshipServiceImpl.java          # orchestration
└── internal/
    ├── FriendshipPersistenceService.java   # JPA + transaction
    └── FriendInvitationPolicy.java         # internal policy

model/friendship/
├── FriendshipPair.java
├── FriendshipSnapshot.java
└── FriendshipPageSnapshot.java
```

Tham chiếu đúng mẫu [ManageAddressService](../../../identity-service/src/main/java/com/stylemind/identity/service/address/ManageAddressService.java), [impl](../../../identity-service/src/main/java/com/stylemind/identity/service/address/impl/ManageAddressServiceImpl.java) và [internal](../../../identity-service/src/main/java/com/stylemind/identity/service/address/internal/ManageAddressPersistenceService.java).

- Controller tiếp tục inject `FriendshipService` qua interface; chữ ký ba method và tất cả DTO/route giữ nguyên.
- `FriendshipServiceImpl` điều phối, nhận snapshot từ persistence rồi gọi Identity để lấy display name ngoài transaction.
- `FriendshipPersistenceService` quản lý Repository và các `@Transactional` qua Spring proxy: read-only/repeatable-read cho count/page; write transaction + pessimistic row lock cho unfriend.
- Pure snapshot/canonical pair thuộc `model`; policy nội bộ thuộc `internal`. Các import/test chuyển theo vị trí mới, không tạo thêm abstraction cho helper.
- Tiếp tục JPA đã được Owner duyệt B14; không đổi framework, schema, quyền, business rule, error code hoặc service boundary. Source Address chỉ được đọc làm mẫu.

## Kiểm thử và bằng chứng

**Lệnh thực tế:** `mvn -B -ntp verify` trong Maven/Java 21 container. Kết quả: **131 test, 13 class, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**. Giữ nguyên bộ test, không thêm test chỉ kiểm tra tên class/interface.

Regression bao gồm pair/policy/token, DB fresh/upgrade và repository constraints, HTTP/JWT authorization A/B/C, rollback, concurrent unfriend, read pagination và OpenAPI. Test Identity lookup tiếp tục xác nhận không có transaction DB khi gọi directory, nên việc tách bean/proxy không làm thay đổi ranh giới transaction.

Docker image: `maven:3.9.11-eclipse-temurin-21-alpine`; PostgreSQL 18.6 qua Testcontainers. Runner dùng `docker run --rm`, mount source read-only, copy chỉ `pom.xml`/`src` vào `/tmp/ep06-project`; không copy `target` cũ, tránh chạy nhầm bytecode ở package đã chuyển.

Evidence tại ignored [target/ep06-service-layout-20261025](../../../wardrobe-community-service/target/ep06-service-layout-20261025):
- `refactor-maven.log`.
- `refactor-surefire-reports/`.

Chạy lại với Java 21/Maven và Docker:
```powershell
mvn -B -ntp -f wardrobe-community-service/pom.xml verify
```

## Bảo toàn thay đổi và môi trường

Đối chiếu SHA-256 trong phạm vi **492 file source/resource/Postman** của bảy ứng dụng và Postman: **482 giữ nguyên**, 8 file sửa có chủ đích, 2 file chuyển package; 4 file Java mới và 2 đường dẫn mới của file chuyển. Các file M có sẵn `SecurityConfiguration.java` và `postman/README.md` giữ nguyên hash trong lần này.

Controller, DTO, Entity, Repository, migration V1–V3, API security configuration, cấu hình runtime và Postman JSON giữ nguyên. Source Outfit/Identity/Commerce/Notification/Gateway/Payment/Stylist không sửa. Link ở báo cáo cũ được trỏ lại file hiện tại và ghi chú ngày refactor, không viết lại evidence nghiệp vụ ngày 2026-10-24.

Không đọc/sửa `.env`, không Git/Compose, không gửi email và không chạm persistent DB. Chỉ PostgreSQLContainer test tạm thời, database upgrade bên trong container đó và Maven build cache được sử dụng. Đây là local verification, không phải deployment hoặc production readiness.

Postman collection/run guide giữ nguyên; không chạy lại collection/Newman hoặc tuyên bố collection runtime pass từ Java tests.

Kiểm tra tĩnh: 5 tài liệu/119 liên kết hợp lệ; không còn import hoặc constructor kiểu service class cũ. Hai helper chuyển chỗ chỉ đổi package, đối chiếu được hash nội dung cũ sau khi hoàn nguyên dòng package. AGENTS, Functional Requirements và reference v3 giữ nguyên hash. Root `service/friendship` chỉ còn `FriendshipService.java` interface.

## File chính xác

### Interface, import và unit fixture được cập nhật — 8 file

- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/security/FriendInvitationTokenService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/security/FriendInvitationTokenService.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/FriendshipService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/FriendshipService.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationPolicyTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationPolicyTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationTokenServiceTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationTokenServiceTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsApiPostgresIntegrationTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsApiPostgresIntegrationTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipPairTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipPairTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipServiceTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendshipServiceTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsPersistencePostgresTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsPersistencePostgresTests.java)

### Chuyển package — 2 file

- `wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/FriendshipPair.java` → [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/FriendshipPair.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/FriendshipPair.java)
- `wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/FriendInvitationPolicy.java` → [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendInvitationPolicy.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendInvitationPolicy.java)

### Implementation/persistence/snapshot mới — 4 file

- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/FriendshipPageSnapshot.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/FriendshipPageSnapshot.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/FriendshipSnapshot.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/friendship/FriendshipSnapshot.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/impl/FriendshipServiceImpl.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/impl/FriendshipServiceImpl.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendshipPersistenceService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/friendship/internal/FriendshipPersistenceService.java)

### Tài liệu — 5 file

- [docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md](../../../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md)
- [docs/planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md](../../../docs/planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md)
- [docs/qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md](../../../docs/qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md)
- [docs/qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md](../../../docs/qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md)
- [docs/qa/wardrobe/EP06_SERVICE_LAYOUT_REFACTOR_2026-10-25.md](../../../docs/qa/wardrobe/EP06_SERVICE_LAYOUT_REFACTOR_2026-10-25.md)

## Trạng thái chức năng

Refactor này không hoàn thành thêm business task. Task 01 DB/JPA vẫn DONE_LOCAL; Task 02/03 vẫn PARTIAL với ba API local. Send/accept/resend/incoming còn chờ contract Identity/Notification và rule tương ứng; Chat chưa triển khai. Share Outfit giữ ON_HOLD.

[Plan hiện hành](../../planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [contract Friends](../../planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md) đã ghi cấu trúc service theo yêu cầu Owner.
