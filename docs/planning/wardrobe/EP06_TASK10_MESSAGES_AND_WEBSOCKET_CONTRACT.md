# EP06 Task 10 A/B — Text messages, history và WebSocket

**2026-10-27 — Text/history/WebSocket và backend ảnh private DONE_LOCAL; toàn Task 10/11 PARTIAL.** Dùng V4, giữ API Task 09 và Spring Data JPA/interface → impl → internal; không thêm schema. Contract này giữ phần A/B, phần C tại [contract ảnh](EP06_TASK10_CHAT_IMAGES_CONTRACT_PROPOSAL.md). [Báo cáo ảnh/runtime](../../qa/wardrobe/EP06_TASK10_IMAGES_AND_RUNTIME_IMPLEMENTATION_REPORT.md): 248 tests pass, 0 failed/error/skipped; Gateway/JWT/WS thật và actual Newman đã chạy. Provider/bucket thật và browser để sau.

## A. HTTP text/history

| Route | Contract |
| --- | --- |
| POST /api/v1/chats/{chatId}/messages | JSON đúng hai fields client_message_id UUID, content string; 201 mới, 200 retry cùng payload |
| GET /api/v1/chats/{chatId}/messages | before_sequence tùy chọn, limit mặc định 30, 1–100; items theo sequence tăng dần trong window, has_more và next_before_sequence |

- Actor JWT sub + CUSTOMER; current Friendship và đủ hai canonical members như Task 09; không tin sender/owner từ body/query.
- Text giữ nguyên nội dung, cần ít nhất một ký tự không phải whitespace; tối đa 4.000 Unicode code points. Reject NUL/lone surrogate vì không biểu diễn hợp lệ trong DB text, duplicate JSON keys, trailing JSON, unknown fields và body sai kiểu.
- JSON Phase A chỉ gửi text; giữ reject attachment fields trong JSON. Cùng POST đã có multipart ảnh ở C; history trả safe metadata ID/order/MIME/size. Private content GET theo contract ảnh, không trả object key/public URL.
- Client key unique theo session + sender. Check quyền trước retry. Cùng key + đúng text và không có ảnh cũ → message cũ, không đổi timestamp/counter, không broadcast lại. Khác payload → 409 IDEMPOTENCY_CONFLICT.
- Khóa canonical Friendship → Session rồi kiểm tra lại membership/active; cấp sequence, insert message, update counter/time trong một transaction. Rollback toàn bộ khi lỗi.
- History dùng read-only repeatable-read; before_sequence exclusive, dương và trong bigint range; limit+1 xác định has_more, không dùng timestamp làm cursor. Success no-store.
- Request sau completed unfriend bị chặn kể cả retry; dữ liệu giữ và re-friend dùng cùng session/history. Read đã được cấp quyền theo snapshot trước commit có thể hoàn tất như contract Task 09.
- Sau commit mới publish realtime; HTTP thành công xác nhận lưu DB, không khẳng định peer đã nhận/đọc. Khi reconnect/mất event, client đọc lại history và dedupe theo message ID/sequence.

## B. WebSocket text

Triển khai kỹ thuật theo WebSocket Owner đã chọn bằng STOMP:

- Endpoint service `/ws/chat`, native WebSocket, không SockJS/cookie auth. CONNECT có `Authorization: Bearer ...` trong STOMP header; không đặt token trong query URL.
- Origin theo frontend URL cấu hình tin cậy; không wildcard. HTTP upgrade không cấp quyền đọc/gửi: bắt buộc CONNECT JWT/CUSTOMER/UUID/expiry, sau đó kiểm tra expiry ở frame và delivery.
- SEND chỉ `/app/chats/{chatId}/messages`, cùng strict JSON validator và writer HTTP.
- SUBSCRIBE chỉ `/topic/chats/{chatId}`, `/user/queue/chats/{chatId}/acks`, `/user/queue/chat-errors`. Chat-specific subscribe cần current Friendship + membership; không cho client gửi vào topic/queue hoặc đọc user queue người khác.
- Broadcast message mới sau commit; ACK cho connection gửi chứa message + replayed. Business error dùng private error queue, auth/protocol error được sanitize trước STOMP ERROR.
- Kiểm tra current quyền **khi delivery trên outbound executor**, không chỉ lúc subscribe. Subscription đang mở không nhận message khi đã unfriend, kể cả event xếp hàng trước đó; dữ liệu đã được cấp quyền/đang truyền không được hứa thu hồi.
- Unfriend không xóa session/history. Sau re-friend vẫn phải qua current guard; không dùng link invitation ACCEPTED cũ để tự khôi phục quan hệ.
- Sequence DB là thứ tự chuẩn. Client sort/dedupe theo sequence; thứ tự đến qua mạng giữa các sender/connection không được coi là một transaction toàn cục.
- Không dùng ordered inbound decorator vì nó bắt interceptor exception trước protocol error handler. Inbound rejection dùng exception không gắn original frame để tránh phản chiếu credential/text trong log. Ordered outbound được giữ; khi guard drop frame phải release queue để re-friend không bị kẹt giao tin.
- Broker nội bộ một application instance, chưa thêm RabbitMQ/Redis/outbox hay cross-instance fanout. DB history là nguồn phục hồi; không tuyên bố exactly-once delivery.
- Không gọi cloud/Identity/Notification trong transaction message. Frame không log token/header hoặc text payload.

[Spring token auth](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/authentication-token-based.html) mô tả CONNECT interceptor cho browser không có custom HTTP header. [Spring Security outbound](https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html) không tự bảo vệ outbound; vì B7 cần thêm delivery guard riêng. Đây là lựa chọn triển khai, không thay business rule đã chốt.

## C. Gateway/ảnh và scope chưa xong

**Đính chính sau kiểm thử 2026-10-27:** route /wardrobe-community/** hiện có đã upgrade và forward WebSocket, strip prefix tới /ws/chat, giữ Origin và negotiate v12.stomp trong probe. Không cần mặc định thêm route WS riêng hoặc sửa Gateway/Compose. [Readiness review](../../qa/wardrobe/EP06_POST_PULL_READINESS_REVIEW_2026-10-27.md) ghi 11 Gateway regression + 1 probe pass. URL local tương ứng: ws://localhost:8080/wardrobe-community/ws/chat. Probe cũ dùng echo upstream. Lượt bổ sung đã chạy real Gateway → Identity/JWKS/Chat/DB, native WS cả text và metadata ảnh/unfriend/refriend; browser/frontend E2E chưa chạy.

Backend upload/retry/cleanup/download và S3-compatible adapter đã có; SDK integration/runtime dùng HTTP fake. Cloud provider/bucket/credential thật và live smoke chưa chạy. Task 10/11 còn PARTIAL.

## D. Verification/handover

- Unit input/cursor/security dispatch; PostgreSQL/HTTP ownership/ordering/idempotency/rollback/race.
- Real WebSocket JWT connect, send/subscribe/acks/errors, outsider, expiry, active subscriptions sau unfriend, no push after commit, retry/no duplicate, history catch-up.
- Regression gate Java 21/PostgreSQL, Swagger HTTP routes, Postman HTTP collection và WS connect guide; structural validation tách runtime execution.
- Checkpoint A/B `mvn -B -ntp verify`: 212 tests, 0 failed/error/skipped (31 mới + 181 regression). Gate bổ sung ảnh mới nhất: **248 tests, 0 failed/error/skipped** (36 mới + 212 regression), BUILD SUCCESS, Java 21/PostgreSQL cô lập. [HTTP Postman guide](../../../postman/EP06_CHAT_MESSAGES_RUN_GUIDE.md), [WebSocket/Gateway handoff](../../../postman/EP06_CHAT_WEBSOCKET_RUN_GUIDE.md).
- Actual runtime: Identity/JWKS + Gateway + Chat JAR thật, native WS text/ảnh; bốn Newman collections 113 requests/305 assertions, zero failures. Không Compose/persistent DB/cloud account; browser và live cloud NOT EXECUTED. Refriend là fixture SQL, không invitation E2E.

