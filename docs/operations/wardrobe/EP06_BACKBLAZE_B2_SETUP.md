# EP06 — Backblaze B2 cho ảnh Chat

**2026-10-29. Owner đã chọn B2, tự tạo bucket/key và lưu sáu biến trong `.env` được ignore.** Kết quả live nằm trong [báo cáo cloud](../../qa/wardrobe/EP06_BACKBLAZE_VERIFICATION_2026-10-29.md); tạo key chưa chứng minh upload thành công.

## Cấu hình đã chọn

| Thuộc tính | Giá trị |
| --- | --- |
| Bucket | `stylemind-chat` — Private |
| Endpoint | `https://s3.us-east-005.backblazeb2.com` |
| Region | `us-east-005` |
| Object Lock / Encryption | Disabled / Enabled với key do Backblaze quản lý |
| Lifecycle | Keep all versions; không auto xóa ảnh Chat theo tuổi |
| Application key | Standard, Read and Write, bucket trên, prefix `chat/v1/` |

`keyID` → `AWS_ACCESS_KEY_ID`; `applicationKey` → `AWS_SECRET_ACCESS_KEY`. B2 cấp applicationKey một lần sau khi tạo. Cặp key này không cần tài khoản AWS/session token. [Hướng dẫn B2](https://www.backblaze.com/docs/en/cloud-storage-get-started-with-a-backblaze-integration), [application keys](https://www.backblaze.com/docs/cloud-storage-application-keys).

```dotenv
# Private Chat images (Wardrobe Community); actual keys only in ignored .env
STYLEMIND_CHAT_IMAGES_ENABLED=true
STYLEMIND_CHAT_IMAGES_BUCKET=stylemind-chat
STYLEMIND_CHAT_IMAGES_ENDPOINT=https://s3.us-east-005.backblazeb2.com
STYLEMIND_CHAT_IMAGES_REGION=us-east-005
AWS_ACCESS_KEY_ID=replace-with-your-B2-keyID
AWS_SECRET_ACCESS_KEY=replace-with-your-B2-applicationKey
```

Không đưa key vào source/chat/image. Spring không tự nạp root `.env`.

## IDE và Docker

Từ root, tạo file ảnh local:

```powershell
.\scripts\configure-ep06-chat-ide.ps1
```

Script đọc allowlist sáu tên biến, không thực thi dotenv, không in giá trị, không lấy secret DB/service khác. Output `.env.chat.local` được `.gitignore` loại trừ. Nếu đã tồn tại, script dừng; cập nhật sáu biến trong file đó khi đổi key, không tự ghi đè.

**VS Code:** trong cấu hình Java Wardrobe tại `.vscode/launch.json`, thêm:

```json
"envFile": "${workspaceFolder}/.env.chat.local"
```

File này chỉ cung cấp ảnh; các biến DB/JWT/service URL vẫn cần cấu hình IDE bình thường. Nếu đã có envFile chứa DB/JWT, thêm sáu biến ảnh vào file local đang dùng thay vì thay đường dẫn. [Java debugger env/envFile](https://code.visualstudio.com/docs/java/java-debugging).

**IntelliJ:** Run → Edit Configurations → Wardrobe → Environment variables, thêm sáu biến. Restart tiến trình sau khi đổi cấu hình. Không khởi động app/áp Flyway vào DB dùng lâu dài chỉ để kiểm thử cloud.

**Docker:** `docker-compose.yml` truyền sáu biến vào riêng Wardrobe; `.env.example` mặc định disabled/credential rỗng. Nơi deploy phải cung cấp environment, không đóng gói `.env` vào image. `docker compose config --quiet` kiểm tra nội suy mà không chạy container. Tránh in/chia sẻ toàn bộ `docker compose config` vì có secret đã nội suy. Đợt wiring này không tự deploy hoặc xác minh DB production.

## Live verification

Docker Desktop sẵn sàng, chạy:

```powershell
.\scripts\verify-ep06-chat-runtime.ps1 -LiveBackblaze
```

Mặc định Wardrobe regression chạy trước; `-SkipRegression` chỉ khi gate vừa pass cùng production source. Runtime package bốn JAR, dùng hai PostgreSQL test/Mailpit/Identity JWT tạm và Newman. Root `.env` chỉ được đọc sáu biến ảnh, pin bucket/endpoint/region trên; truyền secret bằng process environment, không đưa giá trị vào CLI. Regression không nhận key thật; trong bốn JVM chỉ Wardrobe nhận key. PowerShell environment được khôi phục sau khi kết thúc. Không Compose up, DB/service credential thật khác hoặc email tới người thật.

Probe dùng PNG rất nhỏ, real SDK Put/Get/byte comparison/anonymous denied/Delete marker. Runtime dùng production adapter với bucket thật cho upload/read/retry/WS metadata, A/B/C isolation và unfriend/refriend. Năm collection chạy như default fake mode. Native transport không thay browser/UI acceptance.

Trước khi reset DB test, lưu exact keys từ metadata; cuối test dừng JVM rồi dọn **chỉ versions của keys do probe/test DB sở hữu**. Không quét/xóa cả prefix bucket. Safe counts/logs/manifest ở `wardrobe-community-service/target/ep06-chat-runtime-backblaze-<timestamp>/`. Cleanup lỗi thì run không pass. Upload/commit kết quả không xác định mà chưa có metadata đối chiếu không cho phép xóa object lạ; cần reconciliation.

## Versioning

B2 DeleteObject không có versionId chỉ tạo delete marker; dữ liệu cũ vẫn chiếm quota. Adapter production hiện dùng thao tác này cho best-effort compensation; không coi đó là xóa vật lý/giải phóng hết dung lượng. Harness dùng versionId để dọn ảnh test của nó, không phải worker dọn orphan production. [Delete Object](https://www.backblaze.com/apidocs/s3-delete-object), [List Object Versions](https://www.backblaze.com/apidocs/s3-list-object-versions).

Không đổi lifecycle hoặc xóa ảnh đang có message tham chiếu. Unfriend giữ ảnh/history cho refriend; cleanup production phải xác minh DB references và writer. Bucket Private + API proxy JWT/current Friendship là đường đọc, không public CDN/presigned URL. Deploy và vận hành production cần kiểm chứng riêng.
