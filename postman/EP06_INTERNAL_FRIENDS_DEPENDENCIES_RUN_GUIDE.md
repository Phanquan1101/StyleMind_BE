# EP06 internal Friends dependencies — Postman

**2026-10-28.** Import [collection](StyleMind-EP06-Internal-Friends-Dependencies.postman_collection.json) và [environment trống](StyleMind-Internal-Friends.postman_environment.example.json). [Contract](../docs/planning/wardrobe/EP06_FRIENDS_DEPENDENCIES_CONTRACT.md).

Đây là service-to-service API cho backend, không phải API frontend của người dùng. Collection có **6 requests / 12 scripts**. JSON/script validation tách HTTP runtime: Java tests gọi HTTP/SMTP/PostgreSQL thật trong môi trường disposable; collection mới chưa chạy Newman.

## Môi trường

Chạy Identity/Notification bằng IDE hoặc môi trường test có direct backend URLs. Defaults localhost:8081/8086; Compose không publish backend ports và đợt này không sửa Compose. Không chuyển service token sang browser hoặc public frontend environment.

Điền internalServiceToken riêng trong Postman local Current Values, khớp STYLEMIND_SERVICE_TOKEN của hai process, ≥32 bytes. Không export environment đã điền. fixtureAccountId là tài khoản test có thể bỏ; không dùng account người thật. Không cần profile để route lookup mới trả Account facts.

## Thứ tự và opt-ins

1. Folder 00 kiểm thiếu service token: hai route trả 401, request Notification body rỗng nên không gửi mail ngay cả nếu test thất bại.
2. Đặt allowInternalLookup=true để folder 01 đọc facts theo account ID và reject ID sai. Check verified timestamp/null và status hiện tại, không suy ACTIVE là đã xác minh.
3. Trước folder 02, xác nhận Notification dùng Mailpit/SMTP recorder cô lập. Chỉ khi đó đặt allowInternalMailTest=true. Body fixture gửi tới friend@example.test; không thay bằng email người dùng thật.
4. URL sai phải 400; request hợp lệ trả 202 và no-store. Xem thư trong SMTP test inbox để kiểm nội dung; HTTP 202 chỉ chứng minh SMTP nhận, chưa phải inbox hoặc ACCEPTED invitation.

Requests không retry tự động, không tạo Friendship/tủ đồ và không thay email/password/verification của Identity. Chưa gọi public send/resend/accept/incoming vì các API đó còn bước triển khai tiếp theo. Một SMTP acceptance test có side effect gửi thư test; guard mặc định false.

## Swagger và lỗi

Notification OpenAPI /v3/api-docs có route friend-invitations, serviceToken scheme và 202/400/401/503/504. Identity route nội bộ hidden theo convention hiện có, contract/request examples ở tài liệu riêng.

503 EMAIL_DELIVERY_FAILED và 504 EMAIL_DELIVERY_UNKNOWN có test Java và SMTP recorder thực, không dùng SMTP lỗi production để thử collection. Schema/history DB không thay đổi.
