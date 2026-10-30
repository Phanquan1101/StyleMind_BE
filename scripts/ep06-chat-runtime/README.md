## EP06 Share Outfit runtime — 2026-10-29

Wardrobe regression **314/314, 33 classes, 0 failures/errors/skipped**. Bốn JAR thật + Mailpit/PostgreSQL disposable: invitation → Register/verify/login → accept → PRIVATE vẫn ẩn → Owner chọn FRIENDS → unfriend/refriend pass; native WS/ảnh regression pass. Newman **6 collections / 8 runs / 155 requests / 380 assertions**, gồm Share Outfit **32 requests / 55 assertions**, không failures. Commerce catalog và image storage dùng HTTP fixture trong lượt runtime này; không là Commerce/cloud end-to-end mới.

Harness thêm catalog HTTP fixture có service-token check, real Invitation/PRIVATE/FRIENDS/unfriend/refriend assertions, collection Share Outfit. Không chạy Commerce JAR hoặc live B2 lần này. `-SkipRegression` dùng sau gate314 pass cùng production source; không coi package skipTests là Identity regression pass. [Báo cáo](../../docs/qa/wardrobe/EP06_TASK04_07_OUTFIT_SHARING_IMPLEMENTATION_REPORT.md), [run guide](../../postman/EP06_SHARE_OUTFITS_RUN_GUIDE.md).

---
## Checkpoint trước implementation — giữ nguyên để truy vết

# EP06 disposable runtime verification

## Backblaze opt-in — 2026-10-29

Default vẫn dùng HTTP fake. `./scripts/verify-ep06-chat-runtime.ps1 -LiveBackblaze` chạy cùng các scenario với bucket Owner đã chọn và credential từ sáu biến allowlist trong `.env`; không in secret hoặc truyền giá trị trên CLI. Output mới có timestamp/mode trong `target/ep06-chat-runtime-<fake|backblaze>-<timestamp>/`.

Gate Wardrobe mặc định chạy trước smoke và không nhận key cloud; chỉ dùng `-SkipRegression` nếu vừa có gate pass cùng production source. Trong bốn JVM chỉ Wardrobe được cấp key. B2 helper probe SDK Put/Get/anonymous-denied/Delete marker, so version khi replay, thu exact keys từ DB test trước reset, dừng các JVM rồi xóa data versions/delete markers của các test keys đó. Không quét/xóa cả prefix hoặc đổi bucket/lifecycle. Cleanup phải pass; manifest chỉ chứa object keys, không credential. Không coi native fake là live pass hoặc native live là browser/production acceptance.

[Setup B2/IDE/Docker](../../docs/operations/wardrobe/EP06_BACKBLAZE_B2_SETUP.md), [kết quả hiện hành](../../docs/qa/wardrobe/EP06_BACKBLAZE_VERIFICATION_2026-10-29.md). Phần dưới giữ checkpoint ngày 28/27.

**Verification mới 2026-10-28:** harness hiện chạy **4 JAR** (thêm Notification), Mailpit cùng network namespace private, hai PostgreSQL disposable; SMTP actual invitation/Register/verify/login/accept/chat/refriend pass, không dùng SQL cho refriend trong scenario mới. Native WS/images scenario A/B cũ vẫn có fixture SQL refriend riêng. **1 JUnit scenario + 5 collections/123 requests/325 assertions pass**. [Báo cáo](../../docs/qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md). Evidence mới ở `wardrobe-community-service/target/ep06-chat-runtime-20261028`; khối ngày 27 dưới đây giữ làm checkpoint.

Runner ghi shell script UTF-8/LF rồi gọi qua file, tránh mất quote PowerShell. `-SkipRegression` chỉ dùng khi Wardrobe 294 gate vừa pass cùng production source; Identity/Gateway/Notification package skipTests không được coi là regression. Gate Notification 40 pass, Identity 161 có 15 baseline failures đã đối chiếu tên lỗi không đổi.

Collection invitation chạy main 8/16, retry 1/2 và link 1/2 với fixture riêng; Java assert số request thực chạy để không tính skip thành pass. Retry dùng SMTP rejection thật và chờ 31 giây. Extra token/ID đưa qua file private tạm, không ghi full Newman JSON hoặc credentials vào source/summary. Không Compose/persistent DB/live cloud/browser.


Harness này là công cụ kiểm thử, không phải service mới. Chạy Identity, Wardrobe Community và Gateway JAR thật; PostgreSQL 18.6 hai container riêng, fixture account/profile/Friendship do test tạo; JWT lấy từ Login thật và verify bằng JWKS thật.

```powershell
.\scripts\verify-ep06-chat-runtime.ps1
```

Docker Desktop phải chạy. Script mặc định chạy Wardrobe regression trước packaged runtime; Identity/Gateway chỉ package, không coi skipTests là regression pass của hai service đó. `-SkipRegression` chỉ dùng khi Wardrobe gate vừa pass trên cùng source; agent đã chạy gate riêng và dùng tùy chọn đó để tránh lặp.

Source repo bind read-only, copy pom/src sang /tmp; Maven/Java 21 và cache hiện có. Không Compose, không DB persistent/.env, không user JWT hoặc private key thật. Test tự tạo PKCS#8 key/HMAC/service token, account/password/DB credentials tạm; private key xóa khi kết thúc. Child JVMs và containers được dọn.

## Các luồng chạy thật

- Gateway /wardrobe-community/ws/chat → real Chat STOMP: CONNECT từ real Identity JWT, peer receive/private ACK, retry không broadcast trùng, outsider denied.
- Multipart upload qua Gateway → real S3 SDK → fake storage → DB commit → peer WebSocket metadata; private image read cho B, C bị chặn, retry không phát thêm event, unfriend chặn ảnh và refriend phục hồi.
- Unfriend qua REST chặn read/send trên sockets cũ; fixture refriend dùng lại session/history. Refriend ở đây là test SQL, chưa phải invitation accept E2E.
- Newman 6.2.1 trong node:22-alpine container cùng network namespace riêng của runner; chạy bốn importable collections: Friends 28, Chat Sessions 30, Chat Messages 27, Chat Images 28.
- Mỗi collection có fixture reset riêng, explicit opt-ins và tokens runtime; chỉ safe request/assertion counts và failure names được ghi. Không ghi full Newman JSON vì nó chứa Authorization.
- SDK S3 thật gọi HTTP fake ephemeral trong harness, Put/Get/Delete + image API qua Gateway; cloud account/bucket/provider thật chưa dùng. Fake chỉ là test, không có production local fallback.

Lượt chạy ngày 2026-10-27: **1 JUnit runtime scenario pass**, bốn collection **113 requests / 305 assertions / 0 failures**. Wardrobe regression trước đó: **248 tests / 0 failures/errors/skipped**. [Báo cáo bàn giao ngày 2026-10-28](../../docs/qa/wardrobe/EP06_TASK10_IMAGES_AND_RUNTIME_IMPLEMENTATION_REPORT.md) ghi commands, file và giới hạn. Các con số lấy từ actual summaries, không suy pass từ JSON compilation. Scripts được đóng scope IIFE để lexical const không va giữa request/folder khi Newman tái dùng sandbox.

## Evidence và giới hạn

Ignored output: wardrobe-community-service/target/ep06-chat-runtime-20261027. Gồm package logs, per-app logs, runtime Surefire, Newman safe summaries và PNG fixture. Không export test credentials/token; không lưu private key vào evidence.

Native WebSocket/JAR/HTTP runtime không phải browser CORS/UI acceptance. Không thử email thật, Register/OTP/resume hoặc friend invitation mutations còn thiếu. Chưa có live cloud pass, chưa production readiness.
