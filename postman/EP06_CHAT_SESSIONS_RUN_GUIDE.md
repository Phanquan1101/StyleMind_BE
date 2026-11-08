# EP06 Chat Sessions — Postman run guide

**2026-10-27 — Task 09.** Import [collection](StyleMind-Wardrobe-Chat-Sessions.postman_collection.json) và [environment trống](StyleMind-Chat-Sessions.postman_environment.example.json).

Collection có **30 requests / 60 scripts / 6 folders**. Đây là handover để chạy trên môi trường test cô lập. **Newman runtime đã pass 30 requests/81 assertions**, zero failures, qua Gateway/Identity/Wardrobe thật trong harness disposable; structural checks và Java HTTP/PostgreSQL tests vẫn báo riêng.

## 1. Môi trường và dữ liệu cần có

- Gateway ở `baseUrl` (mặc định `http://localhost:8080`), route Identity `/identity`, Wardrobe Community `/wardrobe-community`.
- Ba CUSTOMER test riêng biệt A/B/C có access token thật từ Identity. Điền `accessTokenA/B/C` vào Current Values local của environment; shared example để trống.
- Folder 00 xác nhận account ID bằng Identity GET /me. Các request sau chỉ dùng đúng token đã được capture từ request xác nhận thành công, không lấy token env khác để thay thế giữa run.
- Khi chạy folder 04: A–B phải có Friendship đang hoạt động trong **fixture/database dùng một lần**. Friends invitation APIs chưa đầy đủ nên collection không tạo Friendship, không tự đăng ký/verify/accept và không giả token.
- C là outsider đối với session A–B, kể cả C có là bạn của A hoặc B. Dùng tối đa 100 hội thoại mỗi account trong fixture để các kiểm tra complete-list đủ dữ liệu.
- Không cần Commerce/Notification cho ba session APIs; Identity cần cho folder xác nhận account và Friends preflight đang dùng contract hiện có.
- Không chạy trên cặp bạn bè/account quan trọng hoặc DB lâu dài. Session tạo ra được giữ; chưa có API xóa hội thoại hoặc tái kết bạn để collection tự cleanup.
- Không export collection/environment đã có token runtime hoặc gửi token/response Auth vào log. Run mới từ folder 00 khi token hết hạn.

## 2. Chạy theo folder

| Folder | Requests | Thao tác |
| --- | ---: | --- |
| 00 — Identify | 3 | GET Identity để xác nhận A/B/C riêng biệt và capture token/account ID của run; A reset marker cũ |
| 01 — Read metadata | 4 | Ba account list hội thoại, unknown session 404 |
| 02 — Validation | 3 | Invalid size, owner override và abbreviated UUID đều 400 |
| 03 — Authentication | 2 | Missing/malformed bearer đều 401 |
| 04 — Open/isolate | 12 | Existing friendship preflight, A open 201/200, B reuse 200, A/B read, C 404, list, self/owner/duplicate-JSON rejection và metadata không đổi |
| 05 — Unfriend | 6 | Fresh friendship preflight, A unfriend B, cả hai detail 404 và session bị ẩn khỏi list |

**Mặc định chạy 00–03** với hai flags đều `false`. Các folder này chỉ gửi GET, không tạo dữ liệu.

Để chạy **04**, đặt `allowChatSessionTest=true` và chạy đầy đủ 00→04 trên fixture riêng. Mở session là thao tác ghi nếu chưa có; rerun có thể trả 200 và phải giữ cùng ID/timestamps. `runChatId`, account IDs và authorization đều lấy từ thông tin server đã xác nhận trong run, không lấy ID client nhập để chọn hội thoại của người khác.

Để chạy **05**, cần cả `allowChatSessionTest=true` và `allowUnfriendTest=true`, cùng run 00→04 đã thành công. Folder 05 recheck A–B đang là bạn và ràng buộc marker theo A/B/C + chat ID **trước DELETE**. DELETE chỉ có Authorization sau khi các guard đạt. Thiếu flags/preflight phải skip/stop, không coi là test API pass.

Hủy kết bạn chặn cả hai xem metadata/lịch sử/gửi tin và ẩn session khỏi list theo rule B20. Collection này chỉ kiểm chứng metadata; WebSocket/history/ảnh đã có ở Task 10 và được kiểm thử bằng collection/harness riêng. Không có bước tái kết bạn tự động; fixture DB test được teardown bên ngoài collection. Automated integration test dùng persistence fixture cho re-friend/retention, không chứng minh invitation E2E.

## 3. API và Swagger

- `POST /wardrobe-community/api/v1/me/chats`, JSON duy nhất `friend_account_id`. 201 + Location nếu tạo, 200 khi dùng lại.
- `GET /wardrobe-community/api/v1/chats/{id}`.
- `GET /wardrobe-community/api/v1/me/chats?page=0&size=20`.
- Resource có đúng `chat_session_id`, `friend_account_id`, `created_at`, `updated_at`; không có message/image/Identity private fields.
- Success có `Cache-Control: no-store`. List sắp `updated_at DESC, chat_session_id DESC`, page từ 0, size 1–100.
- Location tính theo service root `/api/v1/chats/{id}`; khi gọi qua Gateway phải giữ thêm prefix `/wardrobe-community`.

Swagger UI của Gateway hiện có: chọn **Wardrobe Community Service**, group **Chat Sessions**, Authorize bằng bearer JWT. OpenAPI JSON: `/wardrobe-community/v3/api-docs`. Swagger JSON được automated test; Gateway runtime đã chạy, browser Try it out chưa chạy.

## 4. Evidence và giới hạn

Checkpoint Task 09 `mvn -B -ntp verify`: **181 tests pass, 0 failures/errors/skipped**, gồm 7 validator tests và 21 Chat session PostgreSQL/HTTP tests mới, cùng 153 regression. Có real embedded HTTP/JWT, concurrent reverse open, lock-wait sau unfriend và rollback; không chỉ MockMvc.

Xem [contract](../docs/planning/wardrobe/EP06_TASK09_CHAT_SESSION_API_CONTRACT.md) và [implementation report](../docs/qa/wardrobe/EP06_TASK09_IMPLEMENTATION_REPORT.md). Không có collection request messages/upload/WebSocket hoặc đổi Share Outfit. Skipped folder, JSON parse/syntax check và simulated guard checks không phải Postman runtime pass.


**Runtime mới 2026-10-27:** [harness](../scripts/ep06-chat-runtime/README.md) đã chạy Identity/JWKS, Gateway và Wardrobe JAR thật với PostgreSQL disposable. Bốn collection Newman **113 requests/305 assertions, 0 failures**; gate Wardrobe **248 tests, 0 failed/error/skipped**. S3 HTTP fake chỉ phục vụ test, chưa live cloud hoặc browser pass. [Báo cáo](../docs/qa/wardrobe/EP06_TASK10_IMAGES_AND_RUNTIME_IMPLEMENTATION_REPORT.md).
