# EP06 Task 10 C — Contract ảnh Chat

**2026-10-27 — Contract đã triển khai trong scope độc lập Owner giao; bàn giao hoàn tất ngày 2026-10-28.** Owner đã duyệt text + ảnh, 5 ảnh/tin, 5 MiB/ảnh, JPG/JPEG/PNG/WebP, 4.000 code points, JPA, WebSocket, current Friendship guards và tối đa 40 megapixel/ảnh. File giữ tên proposal để truy vết. Cloud account/chi phí chưa chọn, schema V4 giữ nguyên.

**Owner đã duyệt ngày 2026-10-27:** tối đa 40 megapixel/ảnh, qua câu trả lời “Đồng ý tối đa 40 megapixel/ảnh”. Tiếp tục implementation theo scope Owner giao; account/provider/bucket thật và chi phí vẫn chưa chọn, không tạo tài nguyên cloud.

## 1. HTTP giữ contract text hiện tại

| Route | Request/response |
| --- | --- |
| POST /api/v1/chats/{chatId}/messages, application/json | Text-only hiện có, giữ nguyên |
| POST cùng route, multipart/form-data | Part message: application/json chứa client_message_id UUID và content tùy chọn string/null. Parts images lặp 1–5 lần, thứ tự là thứ tự hiển thị |
| GET /api/v1/chats/{chatId}/attachments/{attachmentId}/content | JWT CUSTOMER, active Friendship, đúng hai canonical members, attachment thuộc đúng Chat; trả bytes private |
| GET messages/history | Giữ safe metadata ID/order/MIME/bytes hiện có, không object key/public URL |

201 mới, 200 retry cùng text/ảnh có thứ tự, 409 IDEMPOTENCY_CONFLICT cho key cũ/payload khác. Key unique theo chat + sender dùng cùng namespace text và multipart. Cùng key text-only/image payload khác nhau là conflict. Retry phải kiểm tra current authorization trước.

Multipart chỉ nhận message/images, reject unknown/duplicate message part, duplicate/trailing JSON và sender/owner/key client. content omitted/null là image-only; supplied string dùng cùng validation text hiện có. Không nhận empty/whitespace-only supplied caption; client bỏ content để gửi chỉ ảnh. Filename và MIME client không quyết định object key/format server.

## 2. Validate ảnh và resource bounds

- 1–5 file; mỗi file 1–5.242.880 bytes; giới hạn file tổng 25 MiB. Multipart envelope giới hạn kỹ thuật riêng 2 MiB, max request 27 MiB.
- Nhận dạng JPEG/PNG/WebP từ bytes và ImageIO reader, đọc dimensions và decode lấy mẫu frame đầu tối đa 1024×1024 để kiểm tra định dạng mà không tạo toàn bộ pixel buffer; file gốc giữ nguyên. Server lấy canonical MIME từ reader; image/* khai sai format bị từ chối. Thiếu MIME/application/octet-stream có thể nhận nếu bytes hợp lệ.
- APPROVED: width và height dương, width*height <= 40.000.000. Tính long để không overflow; không tạo BufferedImage trước kiểm tra này.
- Giữ file gốc và thứ tự, không tự crop/nén/re-encode hoặc đổi ảnh người dùng. Chưa thêm loại attachment khác.
- Decode theo từng ảnh, giới hạn concurrency tài nguyên kỹ thuật; từ chối service-busy có thể retry, không tạo message nửa chừng.
- WebSocket chỉ mang message/metadata, không mang ảnh/base64.

Java 21 [ImageReader](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/imageio/ImageReader.html) kiểm tra dimensions/subsampling trước read. WebP dùng TwelveMonkeys 3.14.0, có [allocation guards và sửa subsampled WebP](https://github.com/haraldk/TwelveMonkeys/releases/tag/twelvemonkeys-3.14.0); fixture JPEG/PNG/WebP thật và PNG đúng 40 megapixel đã kiểm thử. SDK S3 pin 2.35.0; không suy live cloud pass từ local SDK HTTP fake.

## 3. Storage port và DB V4

Đã triển khai interface ChatImageStorage riêng; S3-compatible adapter có endpoint/region/bucket config để về sau chọn provider, không tạo tài nguyên hoặc chốt billing lúc này. Test storage chỉ nằm trong tests/emulator; không filesystem fallback production.

V4 không có digest column. Object key v1 do server tạo:
chat/v1/{random-attempt-uuid}/{position}-{sha256-original-bytes}.{derived-extension}

Khóa nội bộ có random attempt ID, hash SHA-256 do server tính và số thứ tự; không dùng filename/email/token. Dùng sha trong key + size/MIME/order để so retry bằng facts đã lưu; không dùng ETag làm SHA-256. Existing storage_object_key là text <=1024, không ALTER V4/V5. Format do application quản lý, không expose hoặc tin key từ client.

Payload identity: exact caption theo quy tắc null/string, ordered list (SHA-256 bytes, bytes length, MIME). Object key mỗi attempt riêng: hai retries đồng thời không overwrite hoặc cleanup file của nhau.

[Amazon S3 checksums](https://docs.aws.amazon.com/AmazonS3/latest/userguide/checking-object-integrity-upload.html) nêu ETag không luôn là MD5; application fingerprint ở đây là SHA-256 riêng. Nếu thiết kế cuối cần cột/lifecycle table mới thì phải soạn DDL và xin duyệt scope trước.

## 4. Transaction, retry và cleanup

1. Xác thực JWT và chat/membership/friendship trước xử lý/upload. Giới hạn multipart ở servlet trước khi giữ ảnh trong bộ nhớ.
2. Validate/hash toàn bộ request. Read preflight client key trong DB theo quyền: identical existing trả original, conflict không upload.
3. Upload file vào bucket private bằng keys riêng attempt; không giữ transaction/row lock trong network call.
4. Transaction canonical Friendship → Session, recheck current membership/active và client key, atomically insert message + toàn bộ attachments + tăng sequence/time.
5. New commit mới publish. Losing identical concurrent retry trả original và chỉ cleanup các objects thuộc attempt thua; different payload 409.
6. Upload fail giữa chừng: không có message thành công; cleanup keys attempt đã thử ghi, gồm Put timeout có outcome unknown khi DB chắc chắn chưa được gọi.
7. DB rollback chắc chắn/unfriend thắng: cleanup attempt ngoài transaction. DB commit outcome unknown: **không xóa mù**; kiểm tra tham chiếu sau khi DB truy cập được. Không xác định được thì giữ private để đối soát.
8. Cleanup fail: bounded attempts, ghi object IDs an toàn cho đối soát (không credential/body), không báo message thành công khi chưa commit. Không hứa atomic cloud+PostgreSQL.
9. Chưa có automatic age-based deletion; file được message tham chiếu giữ qua unfriend/refriend. Orphan không xác định chỉ cleanup sau xác minh không còn writer/DB reference theo procedure được review; không list/delete mù toàn bucket.

Storage fail/cấu hình thiếu trả service error theo contract, không dùng public/local fallback. Chưa mở endpoint ảnh như placeholder nếu storage/image processing chưa hoàn chỉnh.

## 5. Read authorization

- Attachment ID phải resolve qua message cùng chatId sau actor guard; guessed ID/outsider/inactive/wrong chat đều 404.
- Đọc bytes qua backend, không redirect/signed download URL; no-store + nosniff, MIME đã kiểm chứng, filename server-generated.
- Kiểm tra lại quyền sau network read trước trả bytes nếu request kéo dài; không giữ DB lock qua cloud.
- Unfriend chặn request mới, giữ file/history. Không hứa thu hồi bytes client đã tải hoặc response đang truyền.

## 6. Tests/handover và phần làm ngay

Unit: strict multipart/metadata, file count/size/bad MIME/dimensions/malformed image, SHA identity và ordered retry.
HTTP/PG: owner A/B/C, image-only/caption, duplicate/conflict/concurrent sends, rollback/unfriend race, private read, no orphan delete on unknown commit.
Adapter/emulator: Put/Get/Delete/error/timeout/metadata, bounded cleanup; emulator không tính là live cloud pass.
Swagger multipart và Postman importable + guide, actual runtime ghi riêng.

Trong lúc contract ảnh/provider chưa hoàn tất, tiếp tục full Gateway → Identity/JWKS → Wardrobe/Chat HTTP/WS và Newman trên fixture/DB disposable. Không thay code Quân/Vi/Khánh, không Compose/persistent DB, không gửi email thật.

