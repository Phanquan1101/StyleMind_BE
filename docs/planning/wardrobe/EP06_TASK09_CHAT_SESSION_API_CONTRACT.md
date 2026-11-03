# EP06 Task 09 — Chat session API contract

**2026-10-27 — DONE_LOCAL theo yêu cầu Owner “rồi tiếp tục”.** Owner đã chốt thêm: **hủy kết bạn ẩn hội thoại khỏi danh sách; kết bạn lại hiện cùng hội thoại cũ**. Đây là B20, bổ sung B5–B7/B11 trong [plan](EP06_WORK_PLAN_AND_PROGRESS.md). Dùng [Chat V4 đã duyệt](EP06_TASK08_CHAT_DATABASE_SCOPE.md), không thêm schema hoặc thay nghiệp vụ Friends/Outfit. Gate 181 test pass, 0 skipped; [báo cáo Task 09](../../qa/wardrobe/EP06_TASK09_IMPLEMENTATION_REPORT.md), [Postman/run guide](../../../postman/EP06_CHAT_SESSIONS_RUN_GUIDE.md). Collection runtime chưa chạy.

## HTTP contract

Gateway prefix hiện có: `/wardrobe-community`. Routes bên trong service:

| Route | Input/kết quả |
| --- | --- |
| `POST /api/v1/me/chats` | JSON chỉ có `friend_account_id` UUID; 201 nếu tạo, 200 nếu đã có; cùng response resource |
| `GET /api/v1/chats/{chatSessionId}` | 200 metadata hội thoại hiện actor được phép xem |
| `GET /api/v1/me/chats?page=0&size=20` | 200 page hội thoại của actor với Friendship đang hoạt động và đúng cả hai participants |

Resource chỉ có `chat_session_id`, `friend_account_id` (peer của actor), `created_at`, `updated_at`. Không trả email, tên/role/status Identity, Friendship internals, counter, nội dung tin nhắn, ảnh hoặc storage key. Frontend có thể dùng Friends API hiện có để lấy tên; ba API session không cần gọi Identity/Notification.

```json
{
  "chat_session_id": "30000000-0000-0000-0000-000000000003",
  "friend_account_id": "20000000-0000-0000-0000-000000000002",
  "created_at": "2026-10-27T09:00:00Z",
  "updated_at": "2026-10-27T09:00:00Z"
}
```

POST 201 có `Location: /api/v1/chats/{id}` tính theo root service; client đi qua Gateway giữ thêm prefix `/wardrobe-community` khi dùng Location. POST 200 khi mở lại không cập nhật timestamps/participants/counter. Success responses có `Cache-Control: no-store`. GET không tạo conversation.

List envelope giống Friends: `items`, `page`, `size`, `total_elements`, `total_pages`. Sort `updated_at DESC, chat_session_id DESC`, count/page từ cùng read-only repeatable-read snapshot. Default page 0/size 20, size 1–100, page 0–2147483647; offset vượt giới hạn JPA trả page rỗng với tổng đúng. Không tự tạo session cho mọi bạn bè khi đọc danh sách.

## Principal, validation và authorization

- JWT hợp lệ có CUSTOMER, actor UUID từ principal; CUSTOMER+ADMIN vẫn có quyền, ADMIN-only 403. Không thêm global live-account gate hoặc sửa JWT contract route cũ.
- POST không nhận query; JSON phải là object đúng một field textual `friend_account_id`. Reject missing/null/blank/non-string, unknown fields, duplicate JSON keys, trailing JSON. Peer/path UUID phải đầy đủ có dấu gạch nối, hex không phân biệt hoa/thường.
- GET detail không nhận query; list chỉ nhận một giá trị cho mỗi page/size. Owner/actor/sender query không được override principal.
- Self-open 400. Non-friend, PENDING-only invite, inactive Friendship, missing peer, unknown session hoặc outsider cùng nhận `404 RESOURCE_NOT_FOUND` khi cần trả resource. List chỉ bỏ các hội thoại actor không có quyền.
- Membership phải có đúng slot 1=account_low và slot 2=account_high của Friendship, đồng thời actor thuộc cặp và Friendship active. Session thiếu/sai participants bị ẩn/404; không tự sửa dữ liệu bất thường.
- Unfriend chặn open/detail của cả A và B, ẩn khỏi list của cả hai; giữ DB rows. Re-friend hợp lệ khôi phục cùng ID, participants và history; API này không tự accept lời mời hoặc tái kích hoạt Friendship.
- Missing/invalid token 401; subject không parse được UUID 401 UNAUTHENTICATED; role không đủ 403; malformed input 400 INVALID_REQUEST; media type sai 415. Dùng Problem Details/error conventions hiện có, không lộ SQL/body/token.
- Các tham số paging, route, DTO/status/error ở đây là cách triển khai kỹ thuật theo convention hiện có; không thêm block/read receipt/group chat hoặc quyền xem dữ liệu mới.

## Transaction và concurrency

POST khóa canonical Friendship bằng cùng repository PESSIMISTIC_WRITE như unfriend, kiểm tra active **sau khi khóa**, rồi tìm session theo stable friendship_id. Session chưa có: tạo session + đúng hai participants trong cùng transaction; bất kỳ lỗi nào rollback toàn bộ. Session có rồi: kiểm tra membership/current Friendship và trả lại, không rotate ID hoặc sửa thời gian.

Concurrent A→B/B→A open dùng cùng row lock, chỉ một 201, các request còn lại 200 cùng ID. Không bắt unique violation rồi tiếp tục dùng transaction đã failed. Unique FK/index V4 là guard cuối cùng; không đổi migration.

GET detail kiểm tra điều kiện quyền trong một query. List áp toàn bộ điều kiện trong count và page query, tránh trả sai total hoặc lọt dữ liệu qua membership không hợp lệ. Requests bắt đầu sau unfriend đã commit phải bị chặn/ẩn. Read đã lấy snapshot được phép trước commit có thể hoàn tất theo snapshot đó; không tuyên bố thu hồi dữ liệu đã tải về hoặc invalidation WebSocket trong scope session API.

## Structure và boundaries

Controller → `service/chat/ChatSessionService` interface → `impl/ChatSessionServiceImpl` → `internal/ChatSessionPersistenceService` → JPA repositories. Immutable local projections ở `model/chat`, HTTP DTO/validator ở package riêng, clock UTC có qualifier riêng. Không gọi service ngoài hoặc SMTP trong DB transaction.

Scope này không gửi/đọc messages, upload/download ảnh, cung cấp WebSocket hoặc chặn push/subscriptions; đó là Task 10/11 với contract riêng. Friends invitation mutations vẫn đợi contract Identity/Notification. Không làm Share Outfit hoặc source/schema của Khánh.

## Verification và handover

- Unit validator: JSON ambiguity, UUID, paging, unknown/duplicate query.
- PostgreSQL/HTTP: JWT/roles, A/B/C isolation, current Friendship, incomplete/wrong membership, empty list/no writes, reuse/no timestamp mutation, stable paging/count, rollback whole aggregate, concurrent open và unfriend lock ordering, refriend/history retention.
- Swagger/OpenAPI có đủ ba routes, request/resource/page/error schemas, bearer security, paging và 200/201/Location.
- Importable Postman collection + run guide; kiểm tra cấu trúc tách khỏi collection runtime. Chạy `mvn -B -ntp verify` với Java 21/PostgreSQL Testcontainers, không Compose/DB lâu dài. Chỉ ghi Done khi có evidence thật.

