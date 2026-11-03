# EP06 Task 10 C — Ảnh Chat và Gateway runtime

**Bàn giao: 2026-10-28, Asia/Bangkok. Kiểm thử functional/runtime: 2026-10-27.**

Backend ảnh private đã hoàn thành local theo scope Owner giao, bổ sung cho text/history/WebSocket Task 10 A/B. **Toàn Task 10/11 vẫn PARTIAL**: live cloud, browser và invitation/re-friend E2E còn thiếu. Không dùng kết quả local để khẳng định production readiness.

## 1. Kết quả và căn cứ

Owner duyệt Chat text + ảnh, WebSocket, Spring Data JPA và layout theo dự án; 5 ảnh/tin, 5 MiB/ảnh, JPG/JPEG/PNG/WebP, tối đa 4.000 code points/tin. Ngày 2026-10-27 Owner chốt “Đồng ý tối đa 40 megapixel/ảnh” (B21). Setup cần Owner tạo account/provider/bucket được để sau theo yêu cầu.

[Plan v4.6](../../planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) và [contract ảnh](../../planning/wardrobe/EP06_TASK10_CHAT_IMAGES_CONTRACT_PROPOSAL.md) là phần hiện hành; tên file proposal giữ để truy vết. Không thêm feature Outfit/sharing, incoming invitation hoặc API giả thay dependency chưa có.

| Evidence | Kết quả thực chạy |
| --- | --- |
| Wardrobe `mvn -B -ntp verify` | **248 tests / 24 classes / 0 failures / 0 errors / 0 skipped**, BUILD SUCCESS, 1:31 |
| Ảnh mới | **36 tests**: validator 9, orchestration 9, real HTTP/PostgreSQL 14, real SDK storage 4 |
| Regression trước ảnh | **212 tests**, gồm Outfit/share, Friends, sessions, text/history/WS và clients |
| Packaged runtime harness | **1 JUnit scenario / 0 failures/errors/skipped**, BUILD SUCCESS, 1:25; ba JAR Identity/Wardrobe/Gateway thật |
| Newman Friends | **28 requests / 89 assertions**, zero failures |
| Newman Sessions | **30 requests / 81 assertions**, zero failures |
| Newman Text Messages | **27 requests / 69 assertions**, zero failures |
| Newman Images | **28 requests / 66 assertions**, zero failures |
| Tổng Newman | **113 requests / 305 assertions**, zero failures |
| Structural validation 2026-10-28 | Bốn JSON collection hợp lệ; **223 event scripts** compile; examples blank credentials/false opt-ins; hai master bằng nhau |
| Đối chiếu tài liệu/source 2026-10-28 | **124 local links hợp lệ**, 6 khối lịch sử giữ nguyên so với snapshot khi tiếp tục; **478 file service khác/migration không đổi** so với baseline trước ảnh |

Java tests, một runtime scenario, HTTP requests và assertions là các đơn vị khác nhau; không cộng chúng thành một số “tests”. Runtime dùng S3 HTTP fake, chưa gọi bucket provider thật. Ngày 2026-10-28 chỉ hoàn tất review/handover/structural checks; không ghi nhận một lượt Maven mới. Docker engine không chạy tại lúc đọc trạng thái hôm nay; điều này không đổi kết quả đã lưu của ngày 27.

## 2. Requirement → implementation

| Rule được Owner duyệt | Model / endpoint / validation | Authorization, transaction và side effect |
| --- | --- | --- |
| B12/B15/B17/B18/B21: text + ảnh, 5 ảnh/5 MiB/40 MP | POST /api/v1/chats/{chatId}/messages, multipart; part message JSON và 1–5 part images. ChatMessage.content nullable, ChatAttachment V4 | JWT CUSTOMER + session guard trước xử lý; reject input sai, không ghi tin nửa chừng |
| Image-only và caption | content omitted/null là image-only; supplied string nonblank ≤4.000 Unicode code points; strict JSON/UUID, không nhận sender/owner/key/URL | Caption dùng chung text validator, không tin actor client |
| File thật | Nhận dạng ImageIO JPEG/PNG/WebP, size bounded; width × height ≤40.000.000 trước pixel allocation; decode mẫu frame đầu ≤1024×1024 | Giới hạn một decode đồng thời; busy trả 503 retryable; giữ nguyên file gốc, không re-encode |
| Một lần gửi/retry không tạo trùng | Unique chat + sender + client_message_id V4; so exact caption + ordered SHA-256/size/MIME | Identical retry 200 không Put/broadcast thêm; mới 201; payload khác 409; cùng namespace JSON text/multipart |
| Ordering và atomicity | Message + attachments + sequence/counter/time trong một transaction | Khóa canonical Friendship → ChatSession và recheck membership/active trước ghi; không network trong transaction |
| Private images, B6/B7 | GET /api/v1/chats/{chatId}/attachments/{attachmentId}/content; safe metadata trong history/WS | Current Friendship, đúng hai members, attachment thuộc chat; recheck sau network read; C/wrong chat/inactive 404 |
| B7/B11/B20 retention | Unfriend không xóa message/attachment/object; refriend dùng lại session | Hai bên bị chặn read/send sau unfriend; refriend khôi phục history/ảnh, không auto-reactivate từ link cũ |
| Storage | ChatImageStorage + S3ChatImageStorage; key server-generated chat/v1/attempt/order-sha.ext, DB chỉ key/MIME/size/order | SDK Put/Get/Delete ngoài TX; backend credentials; không public/signed download URL hoặc local fallback |
| Failure/concurrency | Object key riêng mỗi attempt, cleanup chỉ object attempt thua | Partial Put/definitive rollback cleanup tối đa 3 lần/object; unknown DB commit giữ private để reconciliation, không xóa mù |
| Realtime | HTTP ảnh commit rồi publish metadata theo current outbound guard | WS không truyền file/base64; retry không broadcast; không cam kết exactly-once/read receipt |
| B16 Swagger/Postman | Swagger có cả application/json và multipart cùng POST, schema/encoding JSON part và private content route | Giữ auth/error descriptions; Postman guarded opt-ins, captured identity/token và guide riêng |

Không có migration V5; V4 đã có toàn bộ attributes cần thiết. Fingerprint SHA-256 do backend tính được encode trong object key do backend quản lý, không dùng ETag làm digest ứng dụng. [AWS về checksum/ETag](https://docs.aws.amazon.com/AmazonS3/latest/userguide/checking-object-integrity-upload.html).

TwelveMonkeys **3.14.0** hỗ trợ WebP reader; lựa chọn này sử dụng [allocation guards/subsampling fixes của release](https://github.com/haraldk/TwelveMonkeys/releases/tag/twelvemonkeys-3.14.0). AWS SDK **2.35.0** dùng S3 và URLConnection client. Adapter cấu hình chunked encoding false/path style/checksum when required theo hướng S3-compatible; [R2 Java SDK docs](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/) không thay live compatibility test.

## 3. Luồng runtime đã thực hiện

[Harness](../../../scripts/ep06-chat-runtime/README.md) chạy ba JAR thật từ bản copy source read-only; Identity/Wardrobe dùng hai PostgreSQL 18.6 disposable riêng. Test tạo account/profile/CUSTOMER/verified fixture A/B/C trong DB test; JWT lấy từ Login thật qua Gateway, Wardrobe verify bằng JWKS thật. Không giả một public API seed hoặc một accept invitation đã hoàn thành.

- Native WebSocket qua Gateway /wardrobe-community/ws/chat: JWT CONNECT, private ACK và peer receive text, identical retry không phát trùng, outsider subscribe bị chặn.
- Multipart ảnh qua Gateway → real S3 SDK → ephemeral HTTP fake → transaction PostgreSQL → peer WS attachment metadata.
- B đọc đúng bytes private/no-store/nosniff; C bị chặn; retry ảnh trả message cũ, không tăng objects hoặc broadcast.
- REST unfriend chặn read/send trên sockets cũ và read ảnh của cả hai.
- Test SQL refriend dùng lại session, khôi phục cùng history/ảnh. **Đây chưa phải invitation accept/re-friend E2E.**
- Bốn collection Newman 6.2.1 chạy trong Node 22 container trên network namespace riêng của test runner, reset fixture giữa collections. Chỉ safe counts/failure names được lưu; không xuất full Newman JSON có Authorization.

Không sửa source Gateway để chạy WS. Route HTTP hiện có đã proxy Upgrade/StripPrefix/Origin/subprotocol; lượt này bổ sung real Chat/JWT/DB cho echo probe cũ.

## 4. Test matrix và lỗi đã được xử lý

- **Validator (9):** JPEG/PNG/WebP thật, MIME giả/truncated/empty; file đúng 5 MiB và quá một byte; đúng 40 MP được chấp nhận, vượt cap bị chặn; 1/5/6 ảnh; caption/strict metadata/unknown part/query; defensive byte copies.
- **Orchestration (9):** retry không cloud, publish sau writer; partial Put compensation; unfriend thắng sau Put; concurrent loser cleanup; unknown commit không delete; bounded cleanup; read permission recheck và content integrity.
- **Real HTTP/PostgreSQL (14):** image-only/multipart metadata không filename, private A/B/C và wrong chat; ordered distinct-image retry, count/caption/text-mode conflict; rollback/counter fault, partial upload, race concurrent requests và unfriend; actual servlet size 413; OpenAPI cả hai media types; auth negative; revoke trong cloud read.
- **Real SDK (4):** SDK gọi local HTTP Put/Get/Delete, signed requests không ACL publish, config thiếu fail closed, sanitized provider errors, bounded oversized response.
- **Regression 212:** giữ source/test behavior đã có, gồm Outfit/share, session/text/WS/persistence và service clients.

Các lần đầu thất bại đã được sửa trước khi báo pass: runner thiếu Docker socket; Springdoc chỉ chọn một overloaded media type (thêm customizer); fixture BCrypt thiếu prefix encoder của Identity; Newman va global const giữa folder/request (IIFE scope). Không sửa Identity production để hợp fixture; không coi skipped tests là pass.

Bản annotation mô tả Swagger JSON text được sửa sau gate 248 để dẫn tới multipart cùng route; packaged runtime tiếp theo đã build source này thành công. Không đổi logic functional sau gate. Ngày 28 không đổi source Java/POM/properties.

## 5. Commands và evidence

Lệnh functional đã chạy trong disposable Maven/Java 21 runner:

```text
mvn -B -ntp verify
```

Sau gate cùng source, lệnh runtime đã chạy:

```powershell
.\scripts\verify-ep06-chat-runtime.ps1 -SkipRegression
```

Để chạy lại đầy đủ, dùng `.\scripts\verify-ep06-chat-runtime.ps1` khi Docker Desktop chạy: mặc định Wardrobe verify trước runtime; Identity/Gateway chỉ package với skipTests, **không** coi đó là regression pass mới cho hai service ấy.

Evidence ignored trong workspace:

- [Functional log](../../../wardrobe-community-service/target/ep06-chat-images-20261027/images-verified.log) và `images-verified-surefire-reports/TEST-*.xml`: 248 tests/24 classes.
- [Runtime log](../../../wardrobe-community-service/target/ep06-chat-runtime-20261027/runtime-maven.log) và `runtime-surefire-reports/`: markers WS images/history/unfriend/refriend và real services pass.
- `newman-friends-summary.txt`, `newman-sessions-summary.txt`, `newman-messages-summary.txt`, `newman-images-summary.txt` trong thư mục runtime: actual counts, zero failures.
- Structural check: `node wardrobe-community-service/target/ep06-chat-images-20261027/validate-handover.cjs`. Kết quả JSON/script/blank examples/master equality và scan app/runtime logs không thấy private-key/JWT Authorization/access-key patterns; scan này không phải cam kết phát hiện mọi secret.
- Docs/source check: `node wardrobe-community-service/target/ep06-chat-images-20261027/validate-docs.cjs` → PASS. Đã bỏ đoạn tiến độ cũ bị lặp trong phần hiện hành của plan; giữ đầy đủ B1–B21 và không sửa các khối lịch sử trong đợt tiếp tục ngày 28.

Raw app logs chỉ phục vụ local diagnosis, không đưa token/private key/full auth response vào báo cáo. Runtime tạo credentials/key test tạm; key xóa khi kết thúc, child JVMs/Testcontainers do harness teardown. Không chạy Compose.

## 6. Danh sách file thay đổi của đợt ảnh/runtime

So với baseline trước ảnh đã lưu (684 files), **21 file có sẵn được cập nhật**; không thiếu/mất file baseline. Những file untracked đã có trước thuộc Friends/Chat/Task specs được giữ. Manifest dưới đây chỉ liệt kê đợt ảnh/runtime, không gộp toàn bộ EP06 thành file mới của đợt này.

### File có sẵn cập nhật (21)

```text
wardrobe-community-service/pom.xml
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/chat/ChatMessageController.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/errors/ApiExceptionHandler.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/chat/internal/ChatMessagePersistenceService.java
wardrobe-community-service/src/main/resources/application.properties
postman/EP06_CHAT_MESSAGES_RUN_GUIDE.md
postman/EP06_CHAT_SESSIONS_RUN_GUIDE.md
postman/EP06_CHAT_WEBSOCKET_RUN_GUIDE.md
postman/EP06_MANAGE_FRIENDS_RUN_GUIDE.md
postman/README.md
postman/StyleMind-Wardrobe-Chat-Messages.postman_collection.json
postman/StyleMind-Wardrobe-Chat-Sessions.postman_collection.json
postman/StyleMind-Wardrobe-Manage-Friends.postman_collection.json
docs/planning/wardrobe/EP06_TASK10_CLOUD_IMAGE_STORAGE_PLAN.md
docs/planning/wardrobe/EP06_TASK10_MESSAGES_AND_WEBSOCKET_CONTRACT.md
docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md
StyleMind_EP06_11_Task_Specs/00_START_HERE.md
StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md
StyleMind_EP06_11_Task_Specs/TASK_10_chat_messages_api.md
StyleMind_EP06_11_Task_Specs/TASK_11_chat_tests.md
StyleMind_EP06_Coding_Spec_By_Task_v4.md
```

### File bổ sung (30)

```text
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/controller/chat/ChatImageMessageController.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/SendChatImageMetadata.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/dto/chat/SendChatImagesRequest.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/chat/ChatAttachmentContent.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/chat/ChatImage.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/chat/ChatImageCommand.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/chat/ChatImageMessageSnapshot.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/model/chat/ChatStoredImage.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/openapi/ChatImageOpenApiConfiguration.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/chat/ChatImageMessageService.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/service/chat/impl/ChatImageMessageServiceImpl.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/storage/chat/ChatImageKeys.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/storage/chat/ChatImageStorage.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/storage/chat/S3ChatImageStorage.java
wardrobe-community-service/src/main/java/com/stylemind/wardrobecommunity/validation/chat/ChatImageRequestValidator.java
wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatImageMessageServiceTests.java
wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatImageRequestValidatorTests.java
wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/ChatImagesApiPostgresIntegrationTests.java
wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/S3ChatImageStorageTests.java
scripts/verify-ep06-chat-runtime.ps1
scripts/ep06-chat-runtime/pom.xml
scripts/ep06-chat-runtime/README.md
scripts/ep06-chat-runtime/run-newman.cjs
scripts/ep06-chat-runtime/src/test/java/com/stylemind/verification/ChatRuntimeIntegrationTests.java
postman/StyleMind-Wardrobe-Chat-Images.postman_collection.json
postman/StyleMind-Chat-Images.postman_environment.example.json
postman/EP06_CHAT_IMAGES_RUN_GUIDE.md
docs/planning/wardrobe/EP06_TASK10_CHAT_IMAGES_CONTRACT_PROPOSAL.md
docs/operations/wardrobe/EP06_CHAT_IMAGES_CLOUD_SETUP.md
docs/qa/wardrobe/EP06_TASK10_IMAGES_AND_RUNTIME_IMPLEMENTATION_REPORT.md
```

Production code chỉ trong Wardrobe Community; runner là công cụ test, không phải service mới. Postman ba collection cũ chỉ sửa script scoping, không đổi contract HTTP. Plan/index/hai master/Task 10/11 cập nhật v4.6; các khối v4.1 giữ nguyên để truy vết. Functional Requirements/reference gốc không sửa.

## 7. Environment và giới hạn còn lại

- **Đã chạm:** DB/container dùng một lần cho test, Maven cache volume và ignored target evidence. Không DB lâu dài, .env/.aws, cloud account/bucket, Compose, SMTP/user email hoặc Git commit/push.
- **Giữ nguyên:** V1–V4, existing Outfit/Item/Share/public-link code, source các service Identity/Commerce/Payment/Notification/Stylist/Gateway so với baseline.
- **Cloud thật:** chưa chọn account/provider/region/bucket/chi phí, chưa live IAM/privacy/SDK compatibility/smoke. [Hướng dẫn setup](../../operations/wardrobe/EP06_CHAT_IMAGES_CLOUD_SETUP.md) đã có để dùng khi Owner sẵn sàng; không cần gửi secret trong chat.
- **Recovery:** compensation có giới hạn; cloud và DB không atomic chung. Unknown commit/cleanup fail có thể để private orphan, cần đối soát DB references và writer trước xóa; chưa có durable cleanup job/outbox.
- **Runtime:** chưa browser CORS/UI/Try it out hoặc load test; WS broker một instance, chưa cross-instance fanout/exactly-once. Image decoder kiểm frame đầu, không claim kiểm mọi frame animated WebP.
- **Friends:** Quân cần trusted current email + verification cho account cả khi chưa có profile; Vi cần friend invitation delivery/template/error contract. Send/resend/accept/incoming và các rule edge cases còn chờ; không sửa thay hai service.
- **Share Outfit:** Task 04–07 ON_HOLD; không triển khai scope của Khánh.

Backend độc lập đã có và đã kiểm thử local. Hoàn thành toàn story còn cần live cloud/browser và invitation integration thực tế theo [plan](../../planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md).

