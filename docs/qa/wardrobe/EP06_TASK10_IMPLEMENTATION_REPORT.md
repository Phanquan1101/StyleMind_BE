# EP06 Task 10 A/B — Text/history/WebSocket implementation report

**2026-10-27, Asia/Bangkok — text/history/WebSocket DONE_LOCAL; toàn Task 10/11 PARTIAL.** Chủ task: Tín. Owner yêu cầu tiếp tục phần độc lập, để các bước tạo account/bucket/cấu hình cloud cần Owner thực hiện sang sau. Không chọn provider hoặc thay bằng local file storage.

## 1. Kết quả và giới hạn

- REST POST/GET `/api/v1/chats/{chatId}/messages`: text, cursor history, client-key idempotency, atomic server sequence/counter và no-store.
- Native STOMP `/ws/chat`: JWT CUSTOMER ở CONNECT, SEND/subscribe theo quyền, private ACK/errors và current outbound delivery guard. Không có token trong query.
- Spring Data JPA, Controller → Service interface → impl → internal transaction → Repository; DTO/validator/model riêng. Dùng V4 hiện có, không migration/ALTER mới.
- Swagger HTTP, Postman JSON + guide và WebSocket/frontend/Gateway handoff đã có.
- **Gate cuối: 212 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS; 20 test classes.** 31 tests mới Task 10 + 181 regression của checkpoint Task 09.
- Chưa có upload/download ảnh, cloud provider/bucket/credential/cleanup, Gateway WS/browser E2E hoặc actual Postman/Newman runtime. Invitation integration vẫn thiếu theo handoff; không đánh dấu hai User Story hoàn thành.

[Contract](../../planning/wardrobe/EP06_TASK10_MESSAGES_AND_WEBSOCKET_CONTRACT.md), [cloud plan](../../planning/wardrobe/EP06_TASK10_CLOUD_IMAGE_STORAGE_PLAN.md), [plan v4.5](../../planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md).

## 2. Requirement → implementation → evidence

| Căn cứ Owner | Dữ liệu/endpoint/validation/transaction/side effects | Evidence |
| --- | --- | --- |
| B5/B6 một chat/cặp, actual friends | Giữ session Task 09; REST/WS đều cần active Friendship, actor trong canonical pair và đủ hai members; foreign/missing/malformed membership không lộ resource | HTTP outsider/membership/retry và native WS destination tests |
| B7/B20 unfriend chặn/ẩn, giữ dữ liệu | Send khóa canonical Friendship → Session, recheck active/membership rồi insert+counter atomically; history snapshot; outbound kiểm tra DB ở executor dispatch; list theo Task 09 | Completed/waiting unfriend, rollback, current subscription và queue guard tests |
| B11 refriend khôi phục cùng history | Không xóa Chat; refriend fixture chỉ cập nhật quan hệ trong disposable DB, dùng lại session/sequence/history; dropped outbound phải release ordered queue | HTTP và WS before/unfriend/refriend; guard queue advancement |
| B13 WebSocket | Native STOMP, trusted origin, query rejected, CONNECT JWT/UUID/CUSTOMER/expiry; SEND app destination, topic + per-connection ACK/error subscriptions; direct broker SEND/user impersonation denied | Sáu real native WebSocket + PostgreSQL tests |
| B18 text 4.000 ký tự | 4.000 Unicode code points; whitespace-only, NUL/lone surrogate, wrong type/fields/UUID/duplicate keys/trailing JSON/invalid bytes rejected; giữ text nguyên bản | Sáu validator tests + HTTP boundaries |
| Ordering/idempotency của Task 10 | Unique session/sender/client key theo V4; cùng key/text trả original 200, không update/rebroadcast; conflict 409; atomic sequence/counter, rollback toàn bộ | Real HTTP concurrent distinct keys/identical retry, fault injection, post-commit publish assertion |
| History và B7 | GET cursor bigint dương exclusive, limit 1–100/default30, chronological window, limit+1/has_more/next cursor, read-only repeatable-read, no-store | Cursor complete pages/no duplicate/no writes; outsider/current auth |
| B16 Swagger/Postman | Tag Chat Messages, JSON request/errors/safe DTO/cursor; HTTP collection và native WS guide riêng | OpenAPI integration test; JSON/script/guard structural checks tách runtime |
| B15/B17 ảnh | V4 metadata đã có, history chỉ ID/order/MIME/size; text endpoint không nhận attachment fields; không key/URL | Metadata safety test; upload/download/cloud còn DEFERRED |

Auth trước khi trả retry cũ. HTTP commit chỉ xác nhận lưu tin, chưa phải peer-delivered/read receipt. Realtime publish sau commit; broker thất bại không rollback tin đã lưu, client phục hồi bằng history và dedupe theo ID/sequence. Không thêm outbox/distributed broker; không tuyên bố exactly-once fanout.

Snapshot read đã được cấp quyền trước unfriend có thể hoàn tất; request mới sau completed unfriend bị chặn. Outbound guard kiểm tra ngay trước handler; không thể thu hồi bytes đã đến client.

## 3. Files thuộc phạm vi Task 10

Danh sách so với checkpoint Task 09; tổng **31 files thêm, 11 files cập nhật**. Các file đã tạo trước lần tạm dừng được tiếp tục hoàn thiện, không tạo lại hoặc xóa phần khác.

### ADD — production (20)

- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/chat/ChatTextCommand.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/chat/ChatTextCommand.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/ChatMessageResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/ChatMessageResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/ChatAttachmentMetadataResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/ChatAttachmentMetadataResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/ChatHistoryCriteria.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/ChatHistoryCriteria.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/ChatHistoryResponse.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/ChatHistoryResponse.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/SendChatTextRequest.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/SendChatTextRequest.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/chat/ChatMessageSendResult.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/chat/ChatMessageSendResult.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/validation/chat/ChatMessageRequestValidator.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/validation/chat/ChatMessageRequestValidator.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/chat/ChatMessageService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/chat/ChatMessageService.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/chat/impl/ChatMessageServiceImpl.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/chat/impl/ChatMessageServiceImpl.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/chat/internal/ChatMessagePersistenceService.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/chat/internal/ChatMessagePersistenceService.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/chat/ChatMessageController.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/chat/ChatMessageController.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatSocketSessions.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatSocketSessions.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatSocketAccess.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatSocketAccess.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatSocketInboundInterceptor.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatSocketInboundInterceptor.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatSocketOutboundInterceptor.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatSocketOutboundInterceptor.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatRealtimePublisher.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatRealtimePublisher.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatStompErrorHandler.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/websocket/ChatStompErrorHandler.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/configuration/ChatWebSocketConfiguration.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/configuration/ChatWebSocketConfiguration.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/chat/ChatSocketController.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/chat/ChatSocketController.java)

### ADD — automated tests (4)

- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatMessageRequestValidatorTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatMessageRequestValidatorTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatMessageApiPostgresIntegrationTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatMessageApiPostgresIntegrationTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatWebSocketPostgresIntegrationTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatWebSocketPostgresIntegrationTests.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatSocketDeliveryGuardTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatSocketDeliveryGuardTests.java)

### ADD — contract, cloud plan, handover, evidence (7)

- [docs/planning/wardrobe/EP06_TASK10_CLOUD_IMAGE_STORAGE_PLAN.md](../../../docs/planning/wardrobe/EP06_TASK10_CLOUD_IMAGE_STORAGE_PLAN.md)
- [docs/planning/wardrobe/EP06_TASK10_MESSAGES_AND_WEBSOCKET_CONTRACT.md](../../../docs/planning/wardrobe/EP06_TASK10_MESSAGES_AND_WEBSOCKET_CONTRACT.md)
- [docs/qa/wardrobe/EP06_TASK10_IMPLEMENTATION_REPORT.md](../../../docs/qa/wardrobe/EP06_TASK10_IMPLEMENTATION_REPORT.md)
- [postman/StyleMind-Wardrobe-Chat-Messages.postman_collection.json](../../../postman/StyleMind-Wardrobe-Chat-Messages.postman_collection.json)
- [postman/StyleMind-Chat-Messages.postman_environment.example.json](../../../postman/StyleMind-Chat-Messages.postman_environment.example.json)
- [postman/EP06_CHAT_MESSAGES_RUN_GUIDE.md](../../../postman/EP06_CHAT_MESSAGES_RUN_GUIDE.md)
- [postman/EP06_CHAT_WEBSOCKET_RUN_GUIDE.md](../../../postman/EP06_CHAT_WEBSOCKET_RUN_GUIDE.md)

### UPDATE — existing files (11)

- [wardrobe-community-service/pom.xml](../../../wardrobe-community-service/pom.xml)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/configuration/ChatConfiguration.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/configuration/ChatConfiguration.java)
- [wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/security/SecurityConfiguration.java](../../../wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/security/SecurityConfiguration.java)
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatSessionApiPostgresIntegrationTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatSessionApiPostgresIntegrationTests.java)
- [postman/README.md](../../../postman/README.md)
- [docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md](../../../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md)
- [StyleMind_EP06_11_Task_Specs/00_START_HERE.md](../../../StyleMind_EP06_11_Task_Specs/00_START_HERE.md)
- [StyleMind_EP06_11_Task_Specs/TASK_10_chat_messages_api.md](../../../StyleMind_EP06_11_Task_Specs/TASK_10_chat_messages_api.md)
- [StyleMind_EP06_11_Task_Specs/TASK_11_chat_tests.md](../../../StyleMind_EP06_11_Task_Specs/TASK_11_chat_tests.md)
- [StyleMind_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_Coding_Spec_By_Task_v4.md)
- [StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md)

POM chỉ thêm Spring Boot WebSocket starter. ChatConfiguration thêm clock riêng cho socket expiry. SecurityConfiguration chỉ mở HTTP upgrade GET /ws/chat; mọi dữ liệu yêu cầu STOMP auth. Test Task 09 đổi assertion placeholder messages đã lỗi thời thành kiểm tra chưa có cloud-upload route, không bỏ regression case. Postman README và các phần hiện hành spec/plan cập nhật v4.5.

Giữ nguyên migration V1–V4, source Outfit/share của Khánh, service Identity/Notification/Commerce/Gateway, Compose, .env và Functional Requirements/reference. Không chạy Git hoặc chỉnh workbook; workbook AGENTS trỏ tới chưa tồn tại trên máy.

## 4. Commands và kết quả thực chạy

Maven/Java 21: `maven:3.9.11-eclipse-temurin-21-alpine`; PostgreSQL disposable Testcontainers: `postgres:18.6`. Maven gate:

| Suite | Tests | Failure/error/skipped |
| --- | ---: | --- |
| ChatMessageRequestValidatorTests | 6 | 0/0/0 |
| ChatMessageApiPostgresIntegrationTests | 13 | 0/0/0 |
| ChatWebSocketPostgresIntegrationTests | 6 | 0/0/0 |
| ChatSocketDeliveryGuardTests | 6 | 0/0/0 |
| Regression Task 09 và trước đó | 181 | 0/0/0 |
| **Tổng** | **212** | **0/0/0** |

**BUILD SUCCESS — 49.676 s.** Các HTTP concurrency và native WebSocket chạy thật với embedded service random port, signed test JWT và PostgreSQL cô lập. Identity/Notification không được gọi bằng tài khoản thật. Các fixture refriend/image metadata không thay acceptance invitation/cloud thực.

Lần chạy trước tạm dừng (`messages-final-maven.log`) còn 4 WS failures, không phải pass. Sau sửa inbound, focused run còn một refriend delivery failure: outbound drop làm hàng đợi giữ frame không được advance. Đã sửa và thêm tests bảo vệ log/queue; gate cuối bên dưới là evidence hiện hành. Các lỗi compile trung gian cũng đã sửa trước gate cuối.

Evidence ignored target:

- [Gate log](../../../wardrobe-community-service/target/ep06-chat-messages-20261027/messages-verified-maven.log)
- [Surefire reports](../../../wardrobe-community-service/target/ep06-chat-messages-20261027/messages-verified-surefire-reports/)
- [HTTP/PG XML](../../../wardrobe-community-service/target/ep06-chat-messages-20261027/messages-verified-surefire-reports/TEST-com.stylemind.wardrobecommunity.ChatMessageApiPostgresIntegrationTests.xml)
- [Native WS XML](../../../wardrobe-community-service/target/ep06-chat-messages-20261027/messages-verified-surefire-reports/TEST-com.stylemind.wardrobecommunity.ChatWebSocketPostgresIntegrationTests.xml)

PowerShell thực chạy từ root repository:

```powershell
$ErrorActionPreference = 'Stop'
$taskEvidence = Join-Path (Get-Location).Path 'wardrobe-community-service/target/ep06-chat-messages-20261027'
$taskRunner = @'
set -u
mkdir -p /tmp/ep06-project
cp /source/pom.xml /tmp/ep06-project/pom.xml
cp -R /source/src /tmp/ep06-project/src
cd /tmp/ep06-project
mvn -B -ntp verify > /evidence/messages-verified-maven.log 2>&1
build_result=$?
if [ -d target/surefire-reports ]; then
  cp -R target/surefire-reports /evidence/messages-verified-surefire-reports
fi
grep -E '^\[INFO\] (Tests run:|BUILD SUCCESS|BUILD FAILURE|Total time:)|^\[ERROR\]' /evidence/messages-verified-maven.log
exit "$build_result"
'@
docker run --rm --name stylemind-ep06-chat-messages-verify-20261027 --mount 'type=bind,source=E:\mss\BE\StyleMind_BE\wardrobe-community-service,target=/source,readonly' --mount "type=bind,source=$taskEvidence,target=/evidence" --mount 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock' --mount 'type=volume,source=stylemind-ep06-maven-cache-20261024,target=/root/.m2' -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.11-eclipse-temurin-21-alpine sh -c $taskRunner
exit $LASTEXITCODE
```

Source bind read-only, copy pom/src vào /tmp rồi build; không dùng host target/JAR cũ. Runner --rm và Testcontainers tự dọn; chỉ Maven cache volume được dùng lại. Không Compose, persistent DB hay cloud resource. Không có optional persistent smoke.

## 5. WebSocket fix và nguồn kỹ thuật

Spring 6.2.19 [ordered inbound decorator](https://github.com/spring-projects/spring-framework/blob/v6.2.19/spring-messaging/src/main/java/org/springframework/messaging/simp/broker/OrderedMessageChannelDecorator.java) bắt channel exception nên protocol handler không nhận auth failure để trả ERROR. Không bật preserve-receive-order; ordering vẫn ở transaction/DB sequence. [Protocol handler](https://github.com/spring-projects/spring-framework/blob/v6.2.19/spring-websocket/src/main/java/org/springframework/web/socket/messaging/StompSubProtocolHandler.java) cũng advance ordered outbound khi gửi; khi guard drop phải release callback để re-friend nhận tin tiếp. Đây là đối chiếu source framework và kết quả tests, không thay nghiệp vụ Owner.

Inbound exception không gắn original frame; Authorization xóa khỏi native headers ngay khi CONNECT được đọc, kể cả JWT bị từ chối. Error trả client không phản chiếu exception/header/payload. Guard test kiểm tra failedMessage null và message không chứa credential/text marker.

## 6. Postman, Swagger và kiểm tra tĩnh

[Collection](../../../postman/StyleMind-Wardrobe-Chat-Messages.postman_collection.json), [blank environment](../../../postman/StyleMind-Chat-Messages.postman_environment.example.json), [HTTP guide](../../../postman/EP06_CHAT_MESSAGES_RUN_GUIDE.md), [WebSocket/Gateway handoff](../../../postman/EP06_CHAT_WEBSOCKET_RUN_GUIDE.md).

- Collection v2.1 parse được: **27 requests / 54 scripts / 4 folders**; toàn bộ scripts compile bằng V8.
- **10 simulated pre-request guard checks pass**: default off, accepted exact-pair/session state, captured token binding/no environment fallback, peer token, separate unfriend opt-in, stale pair marker, required key, completed unfriend marker và identify-state reset.
- Mẫu token/runtime values rỗng; only baseUrl có default. Requests noauth và chỉ gắn Authorization sau guards; không xuất token đã điền.
- **Postman/Newman runtime NOT EXECUTED**. JSON/script/guard checks không phải actual HTTP collection success.
- Swagger JSON đã được automated HTTP test kiểm tra; Swagger UI/Gateway Try it out chưa chạy.
- Sáu historical spec/plan blocks giữ nguyên SHA-256; hai master giống nhau. Các file ngoài manifest và service khác được đối chiếu baseline khi tiếp tục để bảo toàn thay đổi hiện có.
- Đối chiếu 597 files có sẵn lúc tiếp tục: 585 giữ nguyên, 12 cập nhật đúng manifest, thêm ba guide/report files; không thiếu/xóa file hoặc thay file ngoài manifest. Source/config service khác và migration V1–V4 giữ nguyên. Chỉ bổ sung dòng evidence này sau đối chiếu, không sửa code sau gate.
- Kiểm tra 152 local links trong 12 tài liệu hiện hành: không link hỏng. Environment token rỗng, cờ opt-in mặc định false; gate log không chứa Bearer JWT hoặc private Chat body markers đã kiểm tra. Không còn runner có tên stylemind-ep06-chat đang chạy.

## 7. Phần còn lại và môi trường

Task 10/11 **PARTIAL**. Tiếp theo theo đúng phạm vi đã giao:

1. Khi Owner sẵn sàng: hướng dẫn chọn/tạo cloud account/private bucket và credential ngoài source; duyệt upload/download/cleanup contract, triển khai ảnh và boundary/authorization/cloud tests. Không cần cung cấp key lúc này.
2. Owner Gateway tích hợp WS route; frontend/browser/Gateway E2E theo guide. Không tự sửa code Quân.
3. Invitation APIs/tests còn chờ current verified-email Identity và friend-invitation Notification contract; thực hiện sau khi dependencies có, không đổi nghĩa Outfit invitation.
4. Postman actual runtime trên fixture dedicated khi được chuẩn bị; Share Outfit 04–07 tiếp tục ON_HOLD.

Không tuyên bố production readiness từ bộ test local.

