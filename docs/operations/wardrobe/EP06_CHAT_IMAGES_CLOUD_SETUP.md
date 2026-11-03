# EP06 Chat Images — cấu hình cloud khi Owner sẵn sàng

**2026-10-29 — hiện hành:** Owner đã chọn Backblaze B2 và cấu hình key/bucket Private. Dùng [hướng dẫn B2](EP06_BACKBLAZE_B2_SETUP.md) và [báo cáo xác minh](../../qa/wardrobe/EP06_BACKBLAZE_VERIFICATION_2026-10-29.md). Docker hiện đã truyền sáu biến ảnh vào riêng Wardrobe; IDE helper tạo `.env.chat.local` được ignore. Các mô tả chưa chọn cloud/chưa wire Compose bên dưới là checkpoint 2026-10-27/28, được thay bằng cập nhật này. Chỉ ghi live pass theo kết quả runner/cleanup.

**Đề xuất 2026-10-28:** ưu tiên Cloudflare R2 Standard để dùng adapter S3 hiện có; [hướng dẫn chi tiết và chi phí](EP06_CLOUDFLARE_R2_SETUP.md). Chưa chọn provider thay Owner hoặc tạo cloud resource.

**2026-10-27.** Backend có S3-compatible adapter; chưa chọn provider/account/bucket/region/billing, không tạo cloud resource trong đợt code/test này. Owner yêu cầu phần cần setup tài khoản để sau.

## Runtime variables

| Biến | Ý nghĩa |
| --- | --- |
| STYLEMIND_CHAT_IMAGES_ENABLED | true để dùng storage; mặc định false, không tự tạo bucket |
| STYLEMIND_CHAT_IMAGES_BUCKET | Bucket private dành riêng Chat |
| STYLEMIND_CHAT_IMAGES_REGION | Region provider; S3 region thực, R2 thường auto theo docs provider |
| STYLEMIND_CHAT_IMAGES_ENDPOINT | Bỏ trống cho AWS endpoint mặc định; HTTPS endpoint tương ứng cho provider S3-compatible |
| AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY | Credential môi trường theo SDK standard, hoặc dùng backend role phù hợp; không commit/in ra log |
| AWS_SESSION_TOKEN | Chỉ khi dùng temporary credential cần session token |

Thiết lập qua IDE/process/deployment environment hoặc secret store. Root .env không tự được Spring đọc; Compose chưa được sửa để inject biến mới. Không paste key vào chat. Adapter dùng default credential provider; thiếu/sai config hoặc provider lỗi trả 503, không public/local fallback. Existing text/session vẫn chạy khi images chưa cấu hình.

Endpoint cloud cần HTTPS; HTTP chỉ cho localhost/127.0.0.1/host.docker.internal phục vụ test. Backend chỉ Put/Get/Delete object prefix chat/v1/, không CreateBucket/list-all/signed URL/ACL publish. Quyền tối thiểu phải tương ứng prefix/bucket Chat.

## Việc làm sau khi chọn provider

1. Xem giá/quota tài liệu hiện hành rồi Owner chọn provider và account; hướng dẫn console cụ thể sẽ theo lựa chọn đó.
2. Tạo bucket test private, tắt public access và không public CDN/domain; credentials backend giới hạn vào prefix Chat.
3. Cấu hình environment/role ở nơi chạy backend, dùng account A/B/C test.
4. Live upload/read/retry/unfriend/refriend và negative authorization; kiểm tra object không public; ghi live provider evidence riêng.
5. Chốt cấu hình deploy/Compose với owner phụ trách nếu cần; không tự thay topology/service khác.

Không đặt lifecycle xóa ảnh chat được message tham chiếu. Unfriend giữ ảnh/history. Upload fail hoặc concurrent losing retry có best-effort compensation tối đa ba delete attempts. Commit outcome không rõ giữ private objects để đối soát, không xóa theo tuổi mù; xử lý orphan chỉ sau xác minh DB references và không còn writer có thể commit key đó.

[Cloudflare Java v2](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/) mô tả endpoint/region và yêu cầu tắt chunked encoding cho R2; adapter đã cấu hình chunkedEncodingEnabled(false), path style và checksum khi required. SDK HTTP fake pass không chứng minh một provider cụ thể đã tương thích live.

[Block Public Access S3](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html) là bước cấu hình private theo provider; backend không thể thu hồi file client đã tải. Download qua API backend có JWT/current Friendship, no-store/nosniff; không phát signed/public download URL.
