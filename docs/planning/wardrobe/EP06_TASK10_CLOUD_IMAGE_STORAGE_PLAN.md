# EP06 Task 10 — Kế hoạch ảnh Chat trên cloud

**2026-10-29 — cập nhật hiện hành:** Owner đã chọn Backblaze B2 để bắt đầu không dùng thẻ, tự tạo bucket Private/key và lưu `.env`. [Setup B2](../../operations/wardrobe/EP06_BACKBLAZE_B2_SETUP.md), [verification hiện tại](../../qa/wardrobe/EP06_BACKBLAZE_VERIFICATION_2026-10-29.md). IDE/Docker wiring đã bổ sung; live test đang xác minh, chưa được coi pass trước evidence. Các phần “để sau/chưa chọn cloud” và counts 248/113 bên dưới là checkpoint cũ; gate 2026-10-28 đã có 294 Wardrobe tests và 123 requests/325 assertions. Invitation/auth/SMTP/refriend E2E đã pass backend; browser/UI vẫn còn chờ. Không mở lại Share Outfit 04–07.

**2026-10-27 — Backend private image/S3 adapter DONE_LOCAL; cloud account/provider/bucket chưa chọn.** Owner yêu cầu phần setup cần Owner thực hiện để sau, làm task độc lập trước. Đợt này đã hoàn tất [contract ảnh](EP06_TASK10_CHAT_IMAGES_CONTRACT_PROPOSAL.md), adapter và kiểm thử; chưa tạo tài nguyên cloud, bật billing, sửa Compose hay dùng DB lâu dài.

## 1. Thiết kế đã triển khai

- Cloud bucket private lưu file gốc JPG/PNG/WebP; DB Wardrobe giữ message + metadata `chat_attachment` V4. Không thêm migration/digest column.
- `ChatImageStorage` tách nghiệp vụ; `S3ChatImageStorage` dùng AWS SDK Java 2.x, Put/Get/Delete, credential backend qua default provider. Mặc định disabled khi chưa cấu hình, không filesystem fallback.
- Object key server tạo chứa attempt UUID + vị trí + SHA-256 bytes. Ordered digest/MIME/size + caption xác định idempotent retry; không dùng ETag làm SHA-256.
- Network storage ngoài DB transaction. Writer khóa Friendship → Session và recheck, ghi message/attachments/counter atomically. Chỉ phát metadata WS sau commit.
- Failure chắc chắn rollback: cleanup attempt có giới hạn 3 lần/object. Unknown DB commit không xóa mù; object private chờ đối soát. Không auto age-delete ảnh đang được message tham chiếu.
- GET attachment qua backend, JWT CUSTOMER/current Friendship/canonical members, recheck sau cloud read, bounded bytes/digest/MIME/size, no-store/nosniff. Không trả cloud object key/public/signed download URL.

**Giới hạn Owner đã duyệt:** 5 ảnh/tin, 5 MiB/ảnh, 40 megapixel/ảnh, JPG/JPEG/PNG/WebP; caption optional, supplied text nonblank ≤4.000 code points. File được nhận dạng thật, dimensions kiểm tra trước pixel allocation, decode mẫu frame đầu ≤1024×1024 giữ nguyên file.

## 2. Những lựa chọn cloud để Owner xem ở bước sau

| Provider | Cần kiểm tra / cấu hình |
| --- | --- |
| Amazon S3 | Bucket test riêng, Block Public Access, region, role hoặc key giới hạn prefix Chat. [AWS Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html) |
| Cloudflare R2 | S3-compatible endpoint, region và SDK compatibility riêng. [R2 Java](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/), [S3 compatibility](https://developers.cloudflare.com/r2/api/s3/api/) |

Chưa chọn provider/region/gói; không cam kết giá/free tier. Khi Owner sẵn sàng sẽ xem giá hiện hành rồi hướng dẫn từng bước. Không dùng bucket ảnh sản phẩm Commerce.

Đọc qua backend giúp áp current Friendship mỗi request; signed URL còn có thể dùng trong thời hạn của nó theo [AWS presigned URL](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html). Đây là suy luận thiết kế từ yêu cầu chặn đọc sau unfriend. Dữ liệu đã tải/đang được cấp quyền truyền không thể thu hồi khỏi thiết bị.

## 3. Trình tự làm trước / sau

| Bước | Trạng thái |
| --- | --- |
| 10 A/B text/history/WS | DONE_LOCAL |
| 10 C multipart/private content + S3-compatible adapter | DONE_LOCAL; giữ V4 |
| Regression và actual Gateway/JWT/WS/Newman runtime | PASS local: 248 service tests, runtime 1 JUnit và 113 requests/305 assertions; S3 HTTP fake |
| Owner chọn account/provider/bucket | ĐỂ SAU theo Owner; chưa cần gửi key |
| Cấu hình runtime + live cloud smoke | Sau account/bucket private test và approval tài nguyên; chưa pass provider thật |
| Browser/frontend acceptance | Còn chờ; native JAR test không thay browser/UI |
| 11 tổng handover | PARTIAL vì live cloud/browser và invitation/refriend E2E còn thiếu |

## 4. Hướng dẫn sẽ dùng khi tới cloud thật

[Cloud setup](../../operations/wardrobe/EP06_CHAT_IMAGES_CLOUD_SETUP.md) mô tả enabled/bucket/region/endpoint, AWS credential provider và checklist live smoke. Không đưa secret vào chat/source. Root .env không tự được Spring nạp; runtime wiring/Compose chưa sửa.

[Image guide/collection](../../../postman/EP06_CHAT_IMAGES_RUN_GUIDE.md) và [runtime runner](../../../scripts/ep06-chat-runtime/README.md) đã có. [Báo cáo](../../qa/wardrobe/EP06_TASK10_IMAGES_AND_RUNTIME_IMPLEMENTATION_REPORT.md) tách structural checks, real HTTP/JWKS/WS/Newman, fake SDK storage và live provider chưa chạy.
