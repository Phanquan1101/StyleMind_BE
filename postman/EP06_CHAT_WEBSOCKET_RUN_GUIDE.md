# EP06 — WebSocket Chat / frontend handoff

**2026-10-27.** Native WebSocket + STOMP 1.2, backend `wardrobe-community-service`. [Contract](../docs/planning/wardrobe/EP06_TASK10_MESSAGES_AND_WEBSOCKET_CONTRACT.md), [evidence](../docs/qa/wardrobe/EP06_TASK10_IMPLEMENTATION_REPORT.md), [HTTP/history guide](EP06_CHAT_MESSAGES_RUN_GUIDE.md).

## Kết nối trực tiếp service

Khi service chạy local bằng IDE trên port 8084, URL là `ws://localhost:8084/ws/chat`; khi dùng HTTPS phải dùng WSS. Automated tests dùng random port. Không chạy Compose ở bước này; Compose hiện không publish port backend.

HTTP upgrade chỉ mở transport; cần CONNECT với JWT CUSTOMER, UUID subject và expiry hợp lệ. Với browser/STOMP client, token nằm trong STOMP CONNECT headers:

```javascript
// Pseudocode để ghép vào STOMP client mà frontend đã chọn.
const connectHeaders = { Authorization: "Bearer " + currentAccessToken };
const destination = "/app/chats/" + chatId + "/messages";
const body = JSON.stringify({
  client_message_id: crypto.randomUUID(),
  content: "Chào bạn"
});
```

Đây là snippet API contract, chưa thêm package frontend. Không đưa JWT vào URL/query/local log. Origin được phép là origin của `stylemind.frontend.base-url` hiện có (mặc định `http://localhost:3000`); không wildcard. Không có SockJS hoặc cookie authentication. Không gửi frame ứng dụng trước CONNECTED.

## Destinations và kết quả

| Thao tác | Destination | Dữ liệu |
| --- | --- | --- |
| SUBSCRIBE | /topic/chats/{chatId} | Message mới đã commit, cùng shape response HTTP |
| SUBSCRIBE | /user/queue/chats/{chatId}/acks | ACK riêng connection gửi: message và replayed |
| SUBSCRIBE | /user/queue/chat-errors | Business error riêng connection: code và detail |
| SEND | /app/chats/{chatId}/messages | JSON đúng client_message_id và content |

Subscription ID phải riêng cho từng subscription. Dùng STOMP client để tạo frame/terminator; không gửi chuỗi ký tự `\\0` thay byte NULL. Client không được SEND trực tiếp vào topic/queue, đọc queue người khác hoặc subscribe chat ngoài quyền.

Message response có `chat_message_id, chat_session_id, sender_account_id, client_message_id, sequence_number, content, created_at, attachments`. ACK ví dụ shape:

```json
{"message":{"chat_message_id":"<uuid>","chat_session_id":"<uuid>","sender_account_id":"<uuid>","client_message_id":"<uuid>","sequence_number":1,"content":"Chào bạn","created_at":"<ISO-8601>","attachments":[]},"replayed":false}
```

Retry giữ nguyên key + text: ACK `replayed=true`, cùng message cũ, không broadcast thêm. Key cũ với payload khác: private error `IDEMPOTENCY_CONFLICT`. Auth/destination/protocol rejection: STOMP ERROR đã sanitize và đóng connection; kết nối lại sau khi sửa nguyên nhân. Business validation error không đóng connection.

## Đồng bộ và quyền truy cập

- Sequence trong DB là thứ tự chuẩn. Sort/dedupe theo message ID/sequence, không giả định event từ nhiều sender đến đúng thứ tự toàn cục.
- ACK xác nhận lưu DB, chưa phải delivered/read receipt. Sau reconnect hoặc mất event, đọc HTTP history để catch up; dedupe event và history.
- Kiểm tra quyền tại subscribe, send và ngay trước outbound delivery. Khi unfriend, cả hai bị chặn đọc/gửi và old subscriptions không nhận event tiếp theo. Dữ liệu đã đến client không thể thu hồi.
- Re-friend khôi phục cùng session/history; current membership/expiry vẫn phải hợp lệ. Old live subscription chỉ nhận lại khi quan hệ thực sự active.
- Token hết hạn không còn được gửi hoặc nhận Chat data; lấy token mới qua Identity và CONNECT lại. Không có token-refresh frame.
- Text giới hạn 4.000 code points. WebSocket chỉ nhận/gửi metadata ảnh; file upload qua multipart HTTP, đọc private qua API theo [guide ảnh](EP06_CHAT_IMAGES_RUN_GUIDE.md). Live cloud chưa kiểm thử. Broker hiện chạy một instance, chưa có distributed fanout/outbox hoặc bảo đảm exactly-once.

## Kết nối qua Gateway và handoff tích hợp

**Đã kiểm tra transport và real Chat/JWT runtime, không sửa Gateway/Compose.** Route hiện có hỗ trợ `ws://localhost:8080/wardrobe-community/ws/chat` trong cấu hình local tương ứng; HTTPS dùng WSS. Probe qua Gateway random port đã xác minh Upgrade, StripPrefix tới `/ws/chat`, Origin và subprotocol `v12.stomp`; 11 regression + 1 probe pass. Không cần mặc định giao Quân thêm route riêng. JWT vẫn nằm trong STOMP CONNECT, không trong query. [Readiness review](../docs/qa/wardrobe/EP06_POST_PULL_READINESS_REVIEW_2026-10-27.md) ghi bằng chứng và giới hạn.

**Runtime mới:** [harness](../scripts/ep06-chat-runtime/README.md) chạy real Login/JWKS + Gateway + Wardrobe + PostgreSQL disposable. CONNECT/ACK/peer text và metadata ảnh, retry không broadcast trùng, outsider denied, unfriend chặn sockets cũ/read ảnh, fixture refriend khôi phục session/history/ảnh đã pass. Không còn chỉ echo probe. Token expiry và delivery guard cũng có backend regression tests. [Evidence](../docs/qa/wardrobe/EP06_TASK10_IMAGES_AND_RUNTIME_IMPLEMENTATION_REPORT.md).

**Còn acceptance browser/UI:** allowed origin, STOMP client thật, authenticated image fetch, token renewal/reconnect, history catch-up và presentation/unfriend/refriend. Native WebSocket/JAR test chưa chứng minh browser CORS/UI; refriend fixture SQL chưa chứng minh invitation accept E2E.

## Phần cloud thật còn chờ

Backend upload/download và cleanup đã có theo [contract ảnh](../docs/planning/wardrobe/EP06_TASK10_CHAT_IMAGES_CONTRACT_PROPOSAL.md). [Cloud setup](../docs/operations/wardrobe/EP06_CHAT_IMAGES_CLOUD_SETUP.md) tách bước provider/bucket/credential và live smoke. Owner yêu cầu để setup cần Owner thực hiện sang sau; chưa cần tạo tài khoản hoặc gửi key lúc này. Task 10/11 vẫn PARTIAL.

