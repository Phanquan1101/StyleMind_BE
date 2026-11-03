# Ảnh Chat — đề xuất Cloudflare R2 và hướng dẫn setup

**Tra cứu 2026-10-28.** Mình đề xuất R2 cho dự án hiện tại vì backend đã có S3-compatible adapter và chỉ cần private Put/Get/Delete. Đây là đề xuất; chưa tạo account/bucket, bật billing hoặc cấu hình credential thay Owner.

## Vì sao chọn R2

| Lựa chọn | Khi phù hợp |
| --- | --- |
| Cloudflare R2 Standard — đề xuất | Dùng adapter S3 hiện có; mức dùng miễn phí hàng tháng gồm 10 GB-month lưu trữ, 1 triệu Class A, 10 triệu Class B; egress R2 miễn phí |
| Amazon S3 | Nhóm đã dùng AWS/IAM/EC2; vẫn dùng adapter, nhưng cần xem phí theo region/requests/data transfer và điều kiện Free Tier của account |

R2 vượt mức miễn phí có tính phí: Standard storage $0.015/GB-month, Class A $4.50/triệu, Class B $0.36/triệu theo đơn vị làm tròn của Cloudflare. Mức miễn phí không áp dụng Infrequent Access. [R2 pricing](https://developers.cloudflare.com/r2/pricing/). S3 xem [AWS pricing](https://aws.amazon.com/s3/pricing/); không coi free tier là miễn phí mọi usage.

Backend proxy ảnh về người dùng vẫn tiêu tốn băng thông của nơi chạy backend; “R2 không phí egress” không bảo đảm toàn bộ hệ thống miễn phí.

## 1. Account và bucket — Owner thực hiện khi sẵn sàng

1. Tạo/đăng nhập Cloudflare account. Dashboard → Storage & databases → R2 → Overview.
2. R2 yêu cầu thêm subscription qua checkout. Xem điều kiện thanh toán/usage trong account trước khi xác nhận; free monthly usage vẫn thuộc dịch vụ tính phí khi vượt mức. [Get started](https://developers.cloudflare.com/r2/get-started/).
3. Tạo bucket dành riêng cho Chat test, ví dụ stylemind-chat-dev. Chọn Standard, không Infrequent Access; chưa dùng dữ liệu thật.
4. Giữ bucket private: không bật Public Development URL/r2.dev và không gắn public custom domain. R2 mặc định private theo [public bucket docs](https://developers.cloudflare.com/r2/buckets/public-buckets/).
5. Backend đọc file qua S3 API; browser gọi API Wardrobe có JWT. Không cần public CDN hoặc browser-to-R2 CORS cho flow hiện tại.

## 2. Credential riêng backend

R2 Overview → Account Details → API Tokens → Manage → tạo Account hoặc User API token phù hợp quyền của account. Chọn Object Read and Write và scope **chỉ bucket Chat này**. Token scope theo bucket của R2, không khẳng định có prefix-only policy như AWS IAM; vì vậy bucket riêng không chứa ảnh Commerce.

Lưu Access Key ID và Secret Access Key tại máy/runtime/secret store của bạn; không gửi vào chat, source, Postman shared environment hoặc ảnh chụp. [R2 token guide](https://developers.cloudflare.com/r2/api/tokens/) mô tả quyền và cách lấy cặp key. SDK dùng S3 Access Key ID/Secret Access Key, không dùng chuỗi Cloudflare API token làm Authorization Bearer.

## 3. Đặt biến cho process Wardrobe Community

Trong Run Configuration của IDE hoặc deployment environment, cấu hình:

```dotenv
STYLEMIND_CHAT_IMAGES_ENABLED=true
STYLEMIND_CHAT_IMAGES_BUCKET=stylemind-chat-dev
STYLEMIND_CHAT_IMAGES_REGION=auto
STYLEMIND_CHAT_IMAGES_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
AWS_ACCESS_KEY_ID=<điền riêng ở máy bạn>
AWS_SECRET_ACCESS_KEY=<điền riêng ở máy bạn>
```

Lấy S3 API endpoint từ dashboard cho bucket/account đã chọn; endpoint jurisdiction riêng cần dùng đúng giá trị dashboard. Credential tạm có session token thì thêm AWS_SESSION_TOKEN; key thường không cần.

Đây là ví dụ biến, không phải file .env đã được tạo. Root .env không tự được Spring đọc, và Compose hiện chưa inject biến mới. Tài liệu này không sửa Compose/deploy. Restart riêng process backend sau khi đặt biến; không in toàn bộ environment ra log.

[Cloudflare Java S3 SDK](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/) dùng endpoint, region auto và lưu ý chunked encoding. Adapter hiện đã cấu hình endpoint/path style/chunked false; vẫn phải kiểm thử live provider sau setup.

## 4. Kiểm tra sau cấu hình

- Dùng bucket/account A/B/C test và Friendship fixture, không dùng quan hệ cá nhân.
- Import [Chat Images collection](../../../postman/StyleMind-Wardrobe-Chat-Images.postman_collection.json), đọc [run guide](../../../postman/EP06_CHAT_IMAGES_RUN_GUIDE.md); opt-in ghi dữ liệu chỉ bật cho môi trường test.
- A gửi ảnh, B xem được qua API; C/wrong chat bị chặn. Retry cùng payload không tạo message/file trùng. Ảnh không mở trực tiếp bằng public URL.
- Unfriend chặn cả hai đọc/gửi ảnh, dữ liệu vẫn giữ; refriend hợp lệ khôi phục. Flow refriend thật còn chờ public invitation API.
- Kiểm tra usage/billing, quyền token và private bucket thực tế. Trước khi dọn test objects, xác minh DB reference; không delete cả bucket hoặc đặt age lifecycle cho mọi ảnh.
- Chỉ báo live cloud pass sau khi thực chạy; tests S3 HTTP fake trước đó không thay kiểm thử R2 thật.

Các việc mình cần bạn làm về sau chỉ là account/subscription, bucket và đặt key riêng trên runtime. Chưa cần gửi secret hoặc làm ngay trong khi mình hoàn tất phần Identity/Notification.
