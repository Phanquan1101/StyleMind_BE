# EP06 Manage Friends — Postman run guide

**Verification hiện hành 2026-10-28:** Friends 28 requests/89 assertions pass; companion [invitation](EP06_FRIEND_INVITATIONS_RUN_GUIDE.md) 10/20 pass. Tổng 5 collections 123/325, Wardrobe regression 294 pass. [Báo cáo](../docs/qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md). Các thông báo PARTIAL/chưa có API ở checkpoint cũ phía dưới đã được thay thế trong phạm vi backend.


**Cập nhật 2026-10-28:** đã viết V5/API lời mời theo contract Owner duyệt; Java/PostgreSQL/HTTP mới chưa chạy vì Docker để sau. Dùng thêm [Friend Invitations collection/run guide](EP06_FRIEND_INVITATIONS_RUN_GUIDE.md) cho send/resend/incoming/accept. Collection list/unfriend hiện tại giữ nguyên 28 requests; có thể dùng sau khi tạo quan hệ bằng invitation thật. Các đoạn checkpoint ngày 24/27 dưới đây mô tả phạm vi cũ, không phủ định source mới hoặc chứng minh API mới đã pass.

**2026-10-24 — Task 02 A, PARTIAL User Story.** Import [collection v2.1](StyleMind-Wardrobe-Manage-Friends.postman_collection.json) và [environment mẫu](StyleMind-Friends.postman_environment.example.json). Contract tại [API Friends](../docs/planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md).

## API hiện có

| Method | Qua Gateway | Mục đích |
| --- | --- | --- |
| GET | `/wardrobe-community/api/v1/me/friends` | Current active friends của JWT sub |
| GET | `/wardrobe-community/api/v1/me/friend-invitations` | Chỉ lời mời actor đã gửi |
| DELETE | `/wardrobe-community/api/v1/me/friends/{friendAccountId}` | Đánh dấu pair inactive, không xóa row |

Chưa có public API tạo/gửi lại/nhận lời mời hoặc incoming-list; collection không gọi stub để giả các chức năng đó. Chat có collections/session/text/ảnh và WS guide riêng.

## Chuẩn bị và biến môi trường

- Dùng Identity, Wardrobe và Gateway của môi trường test; Wardrobe áp các migration hiện có V1–V4 trong fixture disposable; V3 là schema Friends. Friend list lấy display name qua Identity directory/service-token hiện có; outgoing list và unfriend chỉ dùng local DB.
- Chuẩn bị ba CUSTOMER test account riêng A/B/C và JWT đăng nhập hợp lệ từ Identity. Không tự tạo hoặc chỉnh nội dung JWT. Collection không đăng ký, verify OTP, Login, gửi email hoặc tạo friendship fixture.
- Trong environment Postman local, đặt `baseUrl` (mặc định `http://localhost:8080`) và `accessTokenA`, `accessTokenB`, `accessTokenC`. Ba token mẫu để trống và có type secret. Giữ nguyên token trong suốt một lần chạy; nếu token hết hạn/thay đổi, bắt đầu lại từ folder 00.
- Không ghi password/JWT vào source, log, screenshot hoặc export environment đã điền. Request-local `runtimeBearer` lấy trực tiếp token của actor từ environment, không để collection token cũ ghi đè.
- Folder 00 gọi API Identity `/api/v1/me` hiện có, lưu account ID thực tế vào collection variables `runAccountIdA/B/C`, kiểm tra ba tài khoản khác nhau. Không lấy các account ID này từ environment override.

## Chạy phần đọc và validation

Chọn **folder 00–03**, bỏ chọn folder 04. Tổng **17 requests**:

| Folder | Số request | Kiểm tra |
| --- | ---: | --- |
| 00 — Identify dedicated accounts | 3 | Identity của A/B/C qua bearer hiện tại |
| 01 — Read Friends and sent invitations | 4 | Friends A/B, outgoing A, phân trang size 1 |
| 02 — Validation | 6 | Page/size sai, duplicate query, owner/sender override, UUID không hợp lệ |
| 03 — Authentication failures | 4 | Thiếu bearer trên cả ba API, malformed bearer |

Phần này không hủy một pair hợp lệ. Friend responses được kiểm tra đúng whitelist, không có email/token/hash của peer; outgoing chỉ có metadata an toàn. Empty lists là trạng thái hợp lệ nếu chưa có fixture. Các script không tự ghi nhận empty result là bằng chứng full ownership isolation; Java integration tests cung cấp evidence A/B/C với seed dữ liệu.

## Chạy hủy kết bạn trên fixture riêng

**Folder 04 có 11 requests và thay đổi pair A–B.** Chỉ chạy khi A/B là bạn bè trong database test dùng riêng, C không là bạn của B, mỗi list có tối đa 100 row và không có thao tác khác thay đổi fixture trong lúc chạy.

Hiện chưa có API create/accept nên collection không tự dựng hoặc phục hồi friendship. Bộ Java tự tạo fixture trong Testcontainers; để chạy Postman, cần fixture test đã được chuẩn bị riêng. Không dùng một quan hệ cá nhân để thử xóa, không thêm endpoint seed vào production.

1. Chạy folder 00 để xác định A/B/C từ server. Đặt `allowUnfriendTest` thành chuỗi `true` ở environment local, rồi chạy toàn bộ folder 04 theo thứ tự.
2. Ba preflight lấy toàn bộ page size 100, kiểm tra A↔B, C không nối B, snapshot IDs/thời điểm của A/B/C. Flag fixture chỉ được bật sau khi cả ba preflight đúng; mutation requests kiểm tra lại flag/cohort, không tiếp tục với fixture chưa xác nhận.
3. Self-unfriend trả 400; C nhắm B trả 404 và A giữ nguyên. Sau đó A hủy B trả 204; A và B mất nhau trong list, mọi pair khác và list C giữ nguyên. Request xóa lặp lại trả 404.
4. Tắt `allowUnfriendTest` về `false` khi xong. Không có API phục hồi pair trong collection hiện tại. Chạy lại mutation cần chuẩn bị fixture mới hoặc nhận lời mời lại khi API đó được triển khai.

Default flag là false. Chọn chạy tất cả folders khi chưa bật flag sẽ báo lỗi ở folder 04, **không được tính là full collection Pass**. Một script bị chặn hoặc một folder bỏ chọn không phải test đã chạy thành công.

## Swagger và kiểm thử

Swagger UI dùng entrypoint Gateway hiện có, chọn **Wardrobe Community Service**, tag **Manage Friends**, dùng nút Authorize với JWT CUSTOMER. OpenAPI JSON: `GET /wardrobe-community/v3/api-docs`; trong schema, các path là path nội bộ service. Ba route có response mẫu, pagination, bearer auth, validation và error descriptions; không có request body để Swagger giả API send/accept chưa triển khai.

Collection có **28 requests, 53 pre-request/test scripts**. Kiểm tra cấu trúc JSON/v2.1, biến và cú pháp JavaScript không tương đương thực thi HTTP.

**Runtime mới 2026-10-27:** [harness](../scripts/ep06-chat-runtime/README.md) đã chạy Identity/JWKS, Gateway và Wardrobe JAR thật với PostgreSQL disposable. Bốn collection Newman **113 requests/305 assertions, 0 failures**; gate Wardrobe **248 tests, 0 failed/error/skipped**. S3 HTTP fake chỉ phục vụ test, chưa live cloud hoặc browser pass. [Báo cáo](../docs/qa/wardrobe/EP06_TASK10_IMAGES_AND_RUNTIME_IMPLEMENTATION_REPORT.md).

Friends collection thực chạy **28 requests/89 assertions, 0 failures**. Account/profile/Friendship là fixture, token lấy Login thật; collection không tạo invitation/email/accept. Báo cáo Task 02 giữ checkpoint cũ; lượt này bổ sung evidence Gateway/Identity/Newman thật. Không gửi collection tới DB persistent.
