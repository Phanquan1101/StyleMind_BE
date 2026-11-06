# EP06 Task 08 — Chat persistence implementation report

**2026-10-27, Asia/Bangkok — DONE_LOCAL trong scope DB/JPA đã duyệt.**

Owner trả lời **“Duyệt scope DB Chat Task 08”** cho [scope DDL](../../planning/wardrobe/EP06_TASK08_CHAT_DATABASE_SCOPE.md). Đã thêm V4 và bốn Entity/Repository JPA cho hội thoại theo Friendship, cùng 22 kiểm thử PostgreSQL. Gate toàn service: **153 tests, 14 classes, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**. Không triển khai API Chat/WebSocket/upload hoặc áp migration vào DB lâu dài.

## 1. Thay đổi và phạm vi

- [V4__create_friend_chat_tables.sql](../../../wardrobe-community-service/src/main/resources/db/migration/V4__create_friend_chat_tables.sql) tạo `chat_session`, `chat_participant`, `chat_message`, `chat_attachment`. DDL khớp từng statement trong scope đã duyệt sau khi bỏ comment/chuẩn hóa line ending.
- Hội thoại unique theo `friendship_id`, không tham chiếu Wardrobe/Outfit. Tái kết bạn giữ cùng Friendship/session; không thêm cờ quyền truy cập riêng trong Chat.
- Participant unique session/account và session/slot, slot 1–2; sender có FK kép tới đúng participant trong session. Counter/sequence và client idempotency key có constraint theo scope.
- Text nullable cho tin chỉ có ảnh, tối đa 4.000 Unicode code points ở DB; metadata ảnh có vị trí 1–5, 1–5.242.880 bytes và MIME JPG/JPEG, PNG, WebP. Chỉ lưu storage key nội bộ, chưa lưu/đọc file.
- FK đều nội bộ và không cascade DELETE. Không sửa V1–V3, schema/entity/service/controller Outfit, Friends production code, cấu hình, dependencies, Gateway hoặc Postman.
- Không thêm service/controller placeholder. Khi triển khai nghiệp vụ Chat, tiếp tục interface → impl → internal theo quyết định Owner.

## 2. Requirement → schema/transaction/evidence

| Yêu cầu/nguồn | Cách bảo vệ và kiểm chứng |
| --- | --- |
| B5/B11: một hội thoại cho cặp bạn bè, dùng lại sau kết bạn lại | Unique local Friendship FK; test cùng cặp bị trùng, hai cặp độc lập, concurrent create có một winner; unfriend/refriend giữ fingerprint bốn bảng Chat |
| B6: membership của sender | FK session/sender → participant; test outsider không thuộc session và account thuộc session khác bị chặn. Chưa phải API/JWT authorization |
| B7/B11: không mất lịch sử khi unfriend | FK không cascade; đổi trạng thái Friendship giữ toàn bộ Chat; xóa parent có row tham chiếu bị FK chặn |
| Ordering/idempotency trong scope Task 08 | Unique session/sequence; unique session/sender/client key; test scope key, concurrent duplicate, exclusive cursor và tách session |
| Transaction/counter | Repository pessimistic write lock; test writer thứ hai đợi commit và thấy counter mới; tám fixture writers khóa Friendship → Session cấp số 1–8; lỗi ảnh rollback counter/message/ảnh |
| B18: 4.000 ký tự | Test 4.000 code points gồm tiếng Việt/emoji supplementary, Java UTF-16 dài 6.000 nhưng PostgreSQL đếm 4.000; 4.001 và empty string bị chặn |
| B15/B17: 5 ảnh, 5 MB, JPG/PNG/WebP | Test đủ năm vị trí theo đúng thứ tự, duplicate/0/6; đúng size limit/quá 1 byte/zero/negative; MIME sai; storage key blank/trim/1.024 boundary/duplicate |
| B14: Spring Data JPA | Hibernate `ddl-auto=validate` trên PostgreSQL thật, CRUD/query round-trip bốn Entity/Repository |
| Giữ lịch sử migration/data của thành viên khác | Fresh V1–V4; upgrade V3→V4 có Outfit/Item/Share/public-link, lời mời và active/inactive Friendship; checksum V1–V3 và fingerprint năm bảng cũ không đổi |

Mọi dữ liệu và transaction creator/writer trong test là **fixture cô lập**, không phải Chat service production hoặc lời mời kết bạn thật. Fingerprint chỉ đưa digest vào assertion để tránh in public-link credential/lời mời. Repository constraints không thay current-friendship authorization.

## 3. Danh sách file

**Thêm source/migration:**

- [V4 migration](../../../wardrobe-community-service/src/main/resources/db/migration/V4__create_friend_chat_tables.sql)
- [ChatSession](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/ChatSession.java)
- [ChatParticipant](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/ChatParticipant.java)
- [ChatMessage](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/ChatMessage.java)
- [ChatAttachment](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/entity/ChatAttachment.java)
- [ChatSessionRepository](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/chat/ChatSessionRepository.java)
- [ChatParticipantRepository](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/chat/ChatParticipantRepository.java)
- [ChatMessageRepository](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/chat/ChatMessageRepository.java)
- [ChatAttachmentRepository](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/repository/chat/ChatAttachmentRepository.java)

**Test:**

- Thêm [ChatPersistencePostgresTests](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatPersistencePostgresTests.java): 22 tests mới.
- Cập nhật [FriendsPersistencePostgresTests](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsPersistencePostgresTests.java): fresh-schema expectation có V4/bốn bảng mới; giữ test upgrade Friends V2→V3 ở đúng target V3 để tiếp tục chứng minh bước đó. Không đổi rule/assertion Friends khác.

**Tài liệu cập nhật:**

- [Scope Task 08](../../planning/wardrobe/EP06_TASK08_CHAT_DATABASE_SCOPE.md): ghi approval và evidence DONE_LOCAL; SQL giữ nguyên.
- [Plan](../../planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md): Task 08 DONE_LOCAL, gate 153; Task 09–11 còn chờ contract/implementation.
- [Task 08](../../../StyleMind_EP06_11_Task_Specs/TASK_08_chat_migrations.md) và [index](../../../StyleMind_EP06_11_Task_Specs/00_START_HERE.md).
- [Root consolidated spec](../../../StyleMind_EP06_Coding_Spec_By_Task_v4.md) và [MASTER](../../../StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md): notice v4.3; giữ nguyên baseline v4.1.
- Báo cáo này. Functional Requirements/reference gốc và báo cáo cũ không bị thay để gán số test mới cho checkpoint cũ.

## 4. Verification thực chạy

Lệnh bên trong runner: **`mvn -B -ntp verify`**, Java 21, image `maven:3.9.11-eclipse-temurin-21-alpine`, PostgreSQL `postgres:18.6` qua Testcontainers. Tổng thời gian Maven: **37.461 s**.

| Nhóm | Tests | Failures / errors / skipped |
| --- | ---: | --- |
| Chat persistence mới | 22 | 0 / 0 / 0 |
| Regression hiện có: Friends, Outfit, sharing, security/OpenAPI, clients | 131 | 0 / 0 / 0 |
| **Tổng 14 classes** | **153** | **0 / 0 / 0** |

Evidence local, nằm trong ignored target:

- [Maven log](../../../wardrobe-community-service/target/ep06-chat-db-20261027/chat-db-maven.log)
- [Surefire reports](../../../wardrobe-community-service/target/ep06-chat-db-20261027/chat-db-surefire-reports/)
- [Chat XML report](../../../wardrobe-community-service/target/ep06-chat-db-20261027/chat-db-surefire-reports/TEST-com.stylemind.wardrobecommunity.ChatPersistencePostgresTests.xml)

Lệnh PowerShell thực chạy từ root repo:

```powershell
$ErrorActionPreference = 'Stop'
$evidenceDir = Join-Path (Get-Location).Path 'wardrobe-community-service/target/ep06-chat-db-20261027'
New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null
$runnerScript = @'
set -u
mkdir -p /tmp/ep06-project
cp /source/pom.xml /tmp/ep06-project/pom.xml
cp -R /source/src /tmp/ep06-project/src
cd /tmp/ep06-project
mvn -B -ntp verify > /evidence/chat-db-maven.log 2>&1
build_result=$?
if [ -d target/surefire-reports ]; then
  cp -R target/surefire-reports /evidence/chat-db-surefire-reports
fi
grep -E '^\[INFO\] (Tests run:|BUILD SUCCESS|BUILD FAILURE|Total time:)|^\[ERROR\]' /evidence/chat-db-maven.log
exit "$build_result"
'@
docker run --rm --name stylemind-ep06-chat-db-20261027 --mount 'type=bind,source=E:\mss\BE\StyleMind_BE\wardrobe-community-service,target=/source,readonly' --mount "type=bind,source=$evidenceDir,target=/evidence" --mount 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock' --mount 'type=volume,source=stylemind-ep06-maven-cache-20261024,target=/root/.m2' -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.11-eclipse-temurin-21-alpine sh -c $runnerScript
exit $LASTEXITCODE
```

Source mount read-only, chỉ copy `pom.xml` và `src` sang thư mục tạm trong runner; không dùng compile output/JAR cũ trong host `target`. Runner bị xóa sau build; chỉ evidence được copy về workspace. Không chạy optional Docker smoke/Compose sau regression.

Đối chiếu SHA-256 trước/sau trên 535 file source/config/Postman/docs đã ghi nhận: 529 giữ nguyên, sáu file cũ thay đúng phạm vi (test Friends và năm tài liệu), không mất file; thêm mười file implementation/test. Root consolidated spec được cập nhật notice riêng, ngoài tập 535 này. Báo cáo này được thêm sau đối chiếu. V1–V3 và production code của mọi module khác giữ nguyên; các thay đổi `SecurityConfiguration.java` và `postman/README.md` có sẵn từ trước cũng được bảo toàn.

## 5. Môi trường và giới hạn

- Kiểm tra tĩnh sau gate: bảy tài liệu có 69 liên kết hiện hành, không có link hỏng; root spec và MASTER giống nhau, phần baseline v4.1 của hai bản master/index giữ nguyên. Source sau gate không đổi; chỉ thêm báo cáo này. DDL V4 khớp scope đã duyệt sau chuẩn hóa comment/line ending.
- Chỉ chạm database Testcontainers `stylemind_chat_persistence_test`, database upgrade `chat_upgrade_test` trong cùng container dùng một lần và các DB test của regression hiện có. Các container DB được Testcontainers dọn; không connect DB lâu dài của ứng dụng.
- Maven dùng lại volume cache `stylemind-ep06-maven-cache-20261024`; đây là cache build, không phải dữ liệu nghiệp vụ.
- Không đọc `.env`, đổi secrets, gửi email thật, chạy Git hoặc sửa workbook.
- DB bảo vệ **tối đa** hai participant slots; creator Task 09 phải tạo **đủ hai account đúng cặp** atomically. DDL chưa tự enforce điều kiện liên bảng này.
- DB cho phép null text để hỗ trợ ảnh; Task 10 phải chặn không có cả text lẫn ảnh, chuỗi chỉ có whitespace và kiểm tra payload retry trong cùng transaction.
- Kiểm thử image chỉ kiểm tra **metadata**. Chưa sniff bytes, upload/download, private storage provider hoặc chặn ảnh sau unfriend ở runtime.
- Chưa có API Chat, JWT actor/membership/current Friendship guards, WebSocket handshake/send/subscribe/push và invalidation khi unfriend. Test retention không chứng minh các quyền này đã hoạt động.
- Swagger/OpenAPI/Postman Friends hiện có giữ nguyên và regression service pass. Task này không thêm HTTP API nên chưa có collection Chat; Postman/Newman runtime không được chạy trong đợt này.
- Kết quả local không phải production readiness.

## 6. Tiến độ sau đợt này

**Task 01, 08 DONE_LOCAL; 02/03 PARTIAL; 04–07 ON_HOLD; 09–11 chưa hoàn thành.**

Bước tiếp theo là contract Task 09 mở/lấy/list hội thoại theo Friendship hiện tại, gồm metadata/list/error khi unfriend và transaction/membership guards, rồi implementation theo interface → impl → internal. Task 10 còn contract WebSocket, upload/private storage, ordering/idempotency/acks; không tự chọn provider hoặc đổi Gateway trong scope DB.

Invitation send/accept/resend/incoming vẫn cần [Identity verified-email và Notification friend-invitation contract](../../planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md); không làm thay Quân/Vi. Share Outfit của Khánh tiếp tục ON_HOLD theo Owner.

