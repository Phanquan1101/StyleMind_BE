# EP06 Task 10 — Postman text messages/history

**2026-10-27.** Import [collection v2.1](StyleMind-Wardrobe-Chat-Messages.postman_collection.json) và [environment mẫu](StyleMind-Chat-Messages.postman_environment.example.json). Collection có **27 requests, 54 scripts, 4 folders**. HTTP API đã có automated evidence trong [báo cáo Task 10](../docs/qa/wardrobe/EP06_TASK10_IMPLEMENTATION_REPORT.md); **Newman runtime đã pass 27 requests/69 assertions, zero failures** qua Gateway và Identity thật trên DB disposable. WebSocket có [hướng dẫn riêng](EP06_CHAT_WEBSOCKET_RUN_GUIDE.md), không được kiểm thử bằng HTTP Collection Runner.

## Chuẩn bị fixture

Dùng ba tài khoản CUSTOMER chuyên kiểm thử A/B/C trong môi trường cô lập; A và B đã là bạn, C không thuộc hội thoại. Lấy access token thật từ Identity hiện có, điền riêng Current Values `accessTokenA/B/C`. Không xuất collection/environment đã điền token.

`baseUrl` mặc định `http://localhost:8080`, routes Identity qua `/identity`, Wardrobe qua `/wardrobe-community` theo Gateway hiện có. Token chỉ được capture sau khi GET Identity xác nhận account ID. Các request sau dùng token collection-scoped đã capture, không lấy token thay thế từ environment. Các cờ mặc định `false`:

| Biến | Tác dụng |
| --- | --- |
| allowChatMessageTest | Cho phép mở/tạo session, ghi tin và kiểm tra API bằng fixture A/B |
| allowUnfriendTest | Opt-in riêng cho DELETE quan hệ A–B trong folder 03 |

Chỉ bật cờ cho fixture có thể bỏ sau test. Collection không đăng ký/kết bạn giả, không gửi email, không tạo lại friendship và không chạy SQL. Nếu chưa có fixture Friendship từ luồng tích hợp, dùng automated PostgreSQL tests; không tính fixture test là API accept invitation đã xong.

## Thứ tự chạy

1. **00**: Identify A/B/C qua Identity. Identify A xóa run-state cũ; ba ID phải khác nhau.
2. Bật `allowChatMessageTest=true`, chạy **01**: xác nhận current Friendship A–B rồi mở/tạo session; lưu đúng session và marker cặp.
3. Chạy **02**: A gửi tin mới với UUID key mới; retry cùng payload trả 200 và cùng message; payload khác với key cũ trả 409; B được dùng cùng key của A. Kiểm tra lịch sử chronological/cursor exclusive, C bị chặn, body/ảnh sai shape bị từ chối. Lần retry cuối phải giữ nguyên message ban đầu.
4. **03 tùy chọn**: bật thêm `allowUnfriendTest=true`, chạy fresh preflight và DELETE trong cùng folder. Sau đó A/B không xem detail/list/history và retry cũ cũng 404. Không tự refriend/cleanup; khôi phục fixture thuộc luồng tích hợp được duyệt.

Collection dừng khi HTTP status không đúng. Nếu guard chặn, sửa fixture hoặc chạy lại từ 00; không bỏ guard để ép request. Token hết hạn cần token mới và chạy lại 00.

## HTTP contract

```http
POST /api/v1/chats/{chatId}/messages
Authorization: Bearer <current access token>
Content-Type: application/json

{"client_message_id":"40000000-0000-0000-0000-000000000004","content":"Chào bạn"}
```

201 là tin vừa commit, 200 là retry chính xác; 409 `IDEMPOTENCY_CONFLICT` khi tái dùng key với nội dung khác. Key unique theo session + sender. Retry luôn kiểm tra quyền hiện tại trước khi trả message cũ. POST không nhận query hoặc field ngoài hai field trên.

GET `/api/v1/chats/{chatId}/messages?limit=30&before_sequence=100`: cursor tùy chọn, exclusive và dương; limit 1–100, mặc định 30. `items` tăng dần theo sequence trong window mới nhất phù hợp cursor; `has_more` và `next_before_sequence` phục vụ lấy trang cũ hơn. Khi hết trang, cursor tiếp theo null. Success dùng `Cache-Control: no-store`.

Text tối đa 4.000 Unicode code points, giữ whitespace quanh text nhưng không nhận text hoàn toàn rỗng/whitespace. Cả sender và quyền chat lấy từ server; client không gửi sender/owner. JSON endpoint chỉ nhận text; cùng POST có multipart cho ảnh theo [guide ảnh](EP06_CHAT_IMAGES_RUN_GUIDE.md). History trả safe metadata, private content qua GET /api/v1/chats/{chatId}/attachments/{attachmentId}/content.

Swagger/OpenAPI dùng cấu hình service hiện có, tag **Chat Messages** ở `/v3/api-docs`; test backend kiểm tra route/schema/JWT/cursor. Swagger UI/Try it out chưa chạy browser; collection runtime đã chạy Newman. Swagger có cả JSON và multipart cùng POST messages.


**Runtime mới 2026-10-27:** [harness](../scripts/ep06-chat-runtime/README.md) đã chạy Identity/JWKS, Gateway và Wardrobe JAR thật với PostgreSQL disposable. Bốn collection Newman **113 requests/305 assertions, 0 failures**; gate Wardrobe **248 tests, 0 failed/error/skipped**. S3 HTTP fake chỉ phục vụ test, chưa live cloud hoặc browser pass. [Báo cáo](../docs/qa/wardrobe/EP06_TASK10_IMAGES_AND_RUNTIME_IMPLEMENTATION_REPORT.md).
