# EP06 Task 02/03 — Verification backend local 2026-10-28

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser.

## 1. Kết quả thực chạy

| Bộ kiểm thử | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Focused Identity lookup | 12 | 0 | 0 | 0 |
| Focused Wardrobe | 44 | 0 | 0 | 0 |
| Regression Notification | 40 (8 classes) | 0 | 0 | 0 |
| Regression Wardrobe | 294 (31 classes) | 0 | 0 | 0 |
| Regression Identity | 161 (22 classes) | **15 baseline** | 0 | 0 |
| Packaged runtime harness | 1 | 0 | 0 | 0 |

Focused là subset, không cộng thêm vào regression. Runtime là một JUnit scenario ngoài các application gates. **Không claim toàn bộ regression xanh**: Identity vẫn 15 failures. Tập tên lỗi so với probe baseline trước thay đổi là giống hệt, 0 khác biệt; [baseline dependency report](EP06_FRIENDS_DEPENDENCIES_IMPLEMENTATION_REPORT.md) mô tả chứng minh source cũ và lỗi test trước đó. Không sửa/skip 15 test này trong phạm vi hiện tại.

## 2. HTTP/Postman runtime

| Collection/scenario | Requests | Assertions | Failures |
| --- | ---: | ---: | ---: |
| Friends list/outgoing/unfriend | 28 | 89 | 0 |
| Chat Sessions | 30 | 81 | 0 |
| Chat Messages | 27 | 69 | 0 |
| Chat Images | 28 | 66 | 0 |
| Friend Invitations — main | 8 | 16 | 0 |
| Friend Invitations — retry | 1 | 2 | 0 |
| Friend Invitations — email-link | 1 | 2 | 0 |
| **Tổng 5 collection** | **123** | **325** | **0** |

Invitation collection đủ 10 requests/20 assertions qua 3 lượt fixture chọn riêng. Java assert chính xác số request mỗi lượt 8/1/1, không lấy skipped request làm pass. Main tạo/duplicate/reverse/outsider/explicit accept/repeated accept/already friend. Retry có Mailpit SMTP Recipient 451 thực, backend giữ PENDING, reset chaos rồi chờ **31 giây**, resend bằng link mới; email-link nhận credential từ SMTP inbox test để B accept thật. JSON/script structural checks là evidence riêng.

## 3. Luồng thực và phạm vi

- Notification/Identity/Wardrobe/Gateway JAR thật, JWT Login và JWKS thật; 2 PostgreSQL 18.6 disposable, Mailpit v1.31.4 và Newman 6.2.1 trên Node 22.
- A mời email chưa có account; duplicate không thêm email; người chưa login bị 401, forwarded link với C bị 404.
- Recipient Register → OTP từ SMTP test inbox → verify → Login thật; GET me chứng minh chưa có profile vẫn dùng được verified facts.
- Accept credential mời → mở Chat/gửi/đọc → REST unfriend chặn history và old-used link. Recipient gửi lời mời mới ngược chiều → A accept → mở lại cùng Chat và thấy tin cũ. Scenario này không dùng SQL để refriend.
- Native WebSocket qua Gateway: actual JWT, peer/private ACK, no duplicate broadcast, outsider denial; multipart image qua real S3 SDK tới HTTP fake, metadata/read/unfriend. Scenario WS A/B cũ vẫn dùng SQL fixture refriend riêng; không nhầm với actual invitation scenario mới.
- V5 fresh/upgrade seed Outfit/Item/Share/Friendship/Chat/Attachment pass; old-field fingerprints/checksums giữ nguyên. Chỉ DB disposable đã migrate, không áp DB project lâu dài.
- Production Friends source không cần đổi trong lượt verification. Các sửa cần thiết là runner/fixture/test expectation và mở rộng harness; vẫn giữ các rule Owner duyệt.

## 4. Commands và điều chỉnh kiểm thử

```powershell
.\scripts\verify-ep06-friend-invitations.ps1 -Mode Focused
.\scripts\verify-ep06-friend-invitations.ps1 -Mode Regression
.\scripts\verify-ep06-friend-invitations.ps1 -Mode Regression -Services wardrobe-community-service
.\scripts\verify-ep06-chat-runtime.ps1 -SkipRegression
```

Focused cuối exit 0. Regression tổng exit 1: Identity 15 baseline failures; lượt Wardrobe đầu còn 2 expectation/fixture cũ. Sau cập nhật test Wardrobe, chạy riêng service đó: **294 pass, exit 0**; không lặp Notification/Identity không đổi. Runtime cuối exit 0. `-SkipRegression` chỉ tránh lặp Wardrobe gate vừa pass cùng production source; -DskipTests package các JAR không được tính là test pass.

Runner đầu lỗi quoting khi gửi multiline qua PowerShell: đổi sang shell file. Fixture migration inactive row có updated_at trước unfriended_at: sửa seed, không nới constraint. Test rollback dựa vào clock lùi trở nên không còn tạo lỗi sau monotonic timestamp: thay bằng trigger PostgreSQL lỗi thật và giữ fingerprint rollback; thêm test clock lùi. Test đòi API chưa tồn tại đổi thành negative-input contract hiện hành. SMTP fixture forwarding timeout được loại khỏi path bằng Mailpit cùng private namespace và reset chaos; actual SMTP send/mail receipt sau đó được kiểm chứng, không tắt kiểm tra delivery.

## 5. File sửa trong lượt verification

- [scripts/verify-ep06-friend-invitations.ps1](../../../scripts/verify-ep06-friend-invitations.ps1): Ghi shell script UTF-8/LF và hỗ trợ chọn service để không lặp gate không đổi.
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationStateMigrationPostgresTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendInvitationStateMigrationPostgresTests.java): Seed inactive friendship có updated_at hợp lệ theo constraint V3.
- [wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsApiPostgresIntegrationTests.java](../../../wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/FriendsApiPostgresIntegrationTests.java): PG trigger tạo lỗi ghi thật cho rollback; unknown email field trả 400; thêm monotonic-cutoff test.
- [scripts/verify-ep06-chat-runtime.ps1](../../../scripts/verify-ep06-chat-runtime.ps1): Package 4 JAR, truyền Notification JAR, safe summaries, script file và evidence mới.
- [scripts/ep06-chat-runtime/src/test/java/com/stylemind/verification/ChatRuntimeIntegrationTests.java](../../../scripts/ep06-chat-runtime/src/test/java/com/stylemind/verification/ChatRuntimeIntegrationTests.java): Actual Mailpit/registration/refriend và chạy đủ 3 invitation scenarios; private network và fixture cleanup.
- [scripts/ep06-chat-runtime/run-newman.cjs](../../../scripts/ep06-chat-runtime/run-newman.cjs): Chọn fixture scenarios, private variables, redacted safe output.

Các tài liệu/metadata được cập nhật theo evidence mới:

- [docs/planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md](../../../docs/planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md)
- [docs/planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md](../../../docs/planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md)
- [docs/planning/wardrobe/EP06_FRIENDS_DEPENDENCIES_CONTRACT.md](../../../docs/planning/wardrobe/EP06_FRIENDS_DEPENDENCIES_CONTRACT.md)
- [docs/planning/wardrobe/EP06_TASK02_INVITATION_API_DESIGN.md](../../../docs/planning/wardrobe/EP06_TASK02_INVITATION_API_DESIGN.md)
- [docs/planning/wardrobe/EP06_FRIENDS_INVITATION_RULES_2026-10-28.md](../../../docs/planning/wardrobe/EP06_FRIENDS_INVITATION_RULES_2026-10-28.md)
- [docs/planning/wardrobe/EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md](../../../docs/planning/wardrobe/EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md)
- [docs/qa/wardrobe/EP06_TASK02_INVITATIONS_IMPLEMENTATION_REPORT.md](../../../docs/qa/wardrobe/EP06_TASK02_INVITATIONS_IMPLEMENTATION_REPORT.md)
- [docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md](../../../docs/planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md)
- [StyleMind_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_Coding_Spec_By_Task_v4.md)
- [StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md)
- [StyleMind_EP06_11_Task_Specs/00_START_HERE.md](../../../StyleMind_EP06_11_Task_Specs/00_START_HERE.md)
- [StyleMind_EP06_11_Task_Specs/TASK_02_manage_friends_api.md](../../../StyleMind_EP06_11_Task_Specs/TASK_02_manage_friends_api.md)
- [StyleMind_EP06_11_Task_Specs/TASK_03_manage_friends_tests.md](../../../StyleMind_EP06_11_Task_Specs/TASK_03_manage_friends_tests.md)
- [postman/EP06_FRIEND_INVITATIONS_RUN_GUIDE.md](../../../postman/EP06_FRIEND_INVITATIONS_RUN_GUIDE.md)
- [postman/EP06_MANAGE_FRIENDS_RUN_GUIDE.md](../../../postman/EP06_MANAGE_FRIENDS_RUN_GUIDE.md)
- [postman/README.md](../../../postman/README.md)
- [postman/StyleMind-Wardrobe-Friend-Invitations.postman_collection.json](../../../postman/StyleMind-Wardrobe-Friend-Invitations.postman_collection.json)
- [postman/StyleMind-Wardrobe-Manage-Friends.postman_collection.json](../../../postman/StyleMind-Wardrobe-Manage-Friends.postman_collection.json)
- [scripts/ep06-chat-runtime/README.md](../../../scripts/ep06-chat-runtime/README.md)

Báo cáo này là file mới. [Implementation checkpoint](EP06_TASK02_INVITATIONS_IMPLEMENTATION_REPORT.md) giữ báo cáo source batch trước; không dùng các trạng thái pending ở checkpoint đó làm trạng thái hiện hành.

## 6. Evidence, tài nguyên và phần còn lại

Safe JSON: `wardrobe-community-service/target/ep06-invitations-20261028/verification-results.json`; application Surefire/summary ở Focused và Regression. Runtime output: `wardrobe-community-service/target/ep06-chat-runtime-20261028`, safe Newman summaries và runtime Surefire. Không publish full Newman JSON chứa Authorization; raw diagnostic output ở ignored target không phải artifact để chia sẻ credentials.

Source bind read-only, copy vào /tmp. Test tự tạo account/password/RSA/HMAC/service token; SMTP chỉ Mailpit, Node private fixture file được xóa, child JVMs và containers dọn khi kết thúc. Không Git commit/push, Compose, persistent DB, real recipient/cloud hoặc sửa .env. Maven cache test được giữ, không xóa tài nguyên có sẵn của Owner.

**Task 02/03 DONE_LOCAL backend.** Còn cloud bucket/provider thật, frontend invitation page/browser flow/CORS/UI và live image acceptance; Task 10/11 vẫn PARTIAL, Share Outfit 04–07 ON_HOLD. Broker một instance, global Friends write mutex, không durable outbox/exactly-once SMTP vẫn là giới hạn đã ghi; không nhận production readiness từ local tests.
