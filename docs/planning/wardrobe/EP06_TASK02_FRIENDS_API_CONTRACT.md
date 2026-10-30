# EP06 Task 02 — Manage Friends API contract

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser. [Báo cáo verification](../../qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md). Các khối v4.8/v4.9 bên dưới là checkpoint trước verification, không là trạng thái hiện tại.


**2026-10-28 — v4.9, SOURCE WRITTEN / VERIFICATION PENDING.** Owner đã trả lời “Duyệt scope V5” và “Duyệt thiết kế API”. Đã viết V5/JPA, lookup email nội bộ Identity và public send/resend/incoming/accept; chưa coi Task 02/03 Done vì chưa build/test. Docker để sau theo Owner. Các checkpoint phía dưới không thay evidence của lượt code mới. [Contract hiện hành](EP06_TASK02_INVITATION_API_DESIGN.md), [scope đã duyệt](EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md), [báo cáo](../../qa/wardrobe/EP06_TASK02_INVITATIONS_IMPLEMENTATION_REPORT.md).


**Cập nhật chốt nghiệp vụ 2026-10-28 — v4.8:** [quyết định Owner ngày 28](EP06_FRIENDS_INVITATION_RULES_2026-10-28.md) đã chốt mời chéo, pending cũ khi unfriend, repeated accept và 30 giây; bỏ cap 3/20 mỗi giờ. PENDING còn hạn đã SMTP tiếp nhận không gửi lại; expired/failed được thử lại bằng link mới/hạn mới. [scope V5](EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md) và [thiết kế API](EP06_TASK02_INVITATION_API_DESIGN.md) là đề xuất chưa duyệt, chưa tạo V5 hoặc public mutations. Các đoạn checkpoint trước bên dưới không thay quy tắc mới.


**Cập nhật 2026-10-28 — Owner giao bổ sung Identity/Notification:** đã có lookup email-verification cho account chưa có profile, endpoint gửi thư mời kết bạn và Wardrobe clients. [Contract dependency](EP06_FRIENDS_DEPENDENCIES_CONTRACT.md), [báo cáo](../../qa/wardrobe/EP06_FRIENDS_DEPENDENCIES_IMPLEMENTATION_REPORT.md). Notification 40 tests pass; Identity lookup 9 tests pass, nhưng full regression có lỗi cũ đã tái hiện trên baseline. Public send/resend/accept/incoming và rule chi tiết chưa triển khai. Cloud đề xuất [R2/setup](../../operations/wardrobe/EP06_CLOUDFLARE_R2_SETUP.md), chưa chọn account hoặc tạo bucket.

**Các đoạn ngày 24/27 bên dưới là checkpoint trước khi dependency được triển khai; dùng contract mới cho tích hợp hiện hành.**

**2026-10-24 — Phần A đã triển khai và kiểm thử local theo yêu cầu Owner “rồi tiếp tục”; phần B là PROPOSED/TBD.** Nghiệp vụ căn cứ B1–B4/B7/B9–B11/B19 trong [plan hiện hành](EP06_WORK_PLAN_AND_PROGRESS.md), persistence theo [scope Task 01 đã duyệt](EP06_TASK01_FRIENDS_DATABASE_SCOPE.md). User đã chọn Spring Data JPA, Swagger và giữ ownership của từng service.

## A. API độc lập triển khai trước

Base path nội bộ: `/api/v1/me`. Qua Gateway: `/wardrobe-community/api/v1/me`. Dùng route Gateway và Identity directory contract hiện có; không đổi service contract/schema của thành viên khác.

**Layout cập nhật 2026-10-25 theo Owner:** Controller phụ thuộc interface `FriendshipService`; `impl/FriendshipServiceImpl` điều phối và gọi Identity; `internal/FriendshipPersistenceService` xử lý JPA/transaction qua Spring proxy. Policy ở `internal`, snapshot/canonical pair ở `model/friendship`, theo mẫu service Address của Identity. Refactor giữ nguyên API/DTO/quyền và không đổi schema; [evidence 131 tests](../../qa/wardrobe/EP06_SERVICE_LAYOUT_REFACTOR_2026-10-25.md).

| Chức năng | Method/route | Kết quả |
| --- | --- | --- |
| Danh sách bạn bè hiện còn hiệu lực | `GET /api/v1/me/friends` | `200 FriendListResponse` |
| Danh sách lời mời do actor gửi | `GET /api/v1/me/friend-invitations` | `200 FriendInvitationListResponse`; outgoing only |
| Hủy kết bạn với peer | `DELETE /api/v1/me/friends/{friendAccountId}` | `204`, giữ row và lịch sử lời mời |

### Actor, quyền và validation

- JWT được xác thực qua resource-server configuration hiện có; ba route mới yêu cầu `ROLE_CUSTOMER`, theo convention Outfit. Có thêm ADMIN nhưng vẫn có CUSTOMER được dùng; ADMIN-only không có quyền Friends. Không thêm quyền Admin quản lý bạn bè người khác.
- Actor luôn lấy từ JWT `sub` và phải parse được account UUID. Không nhận owner/sender từ body/query. Không bổ sung global live account-state gate hay thay validation JWT của route cũ.
- List chỉ nhận `page`, `size`, mỗi tham số đúng một giá trị; default 0/20, page từ 0 đến 2147483647, size 1–100. Sai kiểu/range, duplicate hoặc tham số lạ trả `400 INVALID_REQUEST`. Offset quá giới hạn JPA trả page rỗng với tổng đúng, không ép kiểu tràn số.
- DELETE nhận UUID đầy đủ có dấu gạch nối; không phân biệt hex hoa/thường. Không nhận query. Tự hủy kết bạn với chính mình trả 400. Body không dùng để xác định actor hoặc peer.
- Missing/invalid bearer trả 401 theo convention hiện có; subject không phải UUID trả `401 UNAUTHENTICATED`; thiếu CUSTOMER trả 403.
- Không có pair còn hiệu lực cho actor+peer, kể cả đã unfriend hoặc pair của người khác, cùng trả `404 RESOURCE_NOT_FOUND` như convention resource của service. DELETE lặp lại không viết lần nữa.
- Success response có `Cache-Control: no-store`. Error shape dùng Problem Details hiện có với `code`, `correlationId`, timestamp; không tự gán delivery/status từ service khác.

### Danh sách bạn bè

Chỉ lấy Friendship có actor ở một trong hai đầu và `unfriended_at IS NULL`. Sort `established_at DESC, friendship_id DESC` để ổn định khi trùng thời gian. Count/page đọc trong một read-only repeatable-read snapshot; sau khi kết thúc transaction mới gọi Identity batch lookup cho các peer trong page (tối đa 100).

```json
{
  "items": [
    {
      "friendship_id": "10000000-0000-0000-0000-000000000001",
      "friend_account_id": "20000000-0000-0000-0000-000000000002",
      "display_name": "Minh",
      "established_at": "2026-10-24T09:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 1,
  "total_pages": 1
}
```

Không trả email, status tài khoản, invitation credential hoặc token/hash của peer. Tên hiển thị lấy từ Identity hiện tại; thiếu profile/account trong kết quả directory thì `display_name: null`, không tự xóa Friendship hoặc suy ra trạng thái tài khoản. Identity unavailable trả `503 IDENTITY_UNAVAILABLE` theo client hiện có. Page rỗng không gọi Identity. Kết quả quan hệ phản ánh snapshot DB; không tuyên bố snapshot tên hiển thị phân tán cùng transaction với Identity.

### Lời mời đã gửi

Chỉ trả row có `sender_account_id = JWT sub`, sort `created_at DESC, friend_invitation_id DESC`, cùng envelope phân trang như Friends. Chưa mở incoming-list vì cần verified current email contract. Không diễn giải route này thành People with Access/OutfitShare.

```json
{
  "items": [
    {
      "friend_invitation_id": "30000000-0000-0000-0000-000000000003",
      "recipient_email": "friend@example.test",
      "status": "PENDING",
      "created_at": "2026-10-24T09:00:00Z",
      "expires_at": "2026-10-31T09:00:00Z",
      "accepted_at": null
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 1,
  "total_pages": 1
}
```

PENDING có `now >= expires_at` được biểu diễn EXPIRED trong response theo hạn 7 ngày; GET không cập nhật row, phát hành token hoặc gửi email. ACCEPTED chỉ nói lời mời đã được nhận trước đó, không bảo đảm hai người hiện còn là bạn. Không trả `token_hash`, token/link, accepted account ID hoặc phỏng đoán trạng thái gửi mail. Đọc danh sách này không cần gọi Identity/Notification.

### Hủy kết bạn và transaction

Canonicalize actor+peer theo PostgreSQL UUID order, khóa pair bằng `PESSIMISTIC_WRITE`, kiểm tra còn hiệu lực, rồi ghi `unfriended_at` và `updated_at` bằng UTC server time ở độ chính xác microsecond, trong một transaction. Không DELETE pair, không đổi invitation hoặc Outfit và không gọi service khác.

Hai người unfriend đồng thời chỉ một request đổi trạng thái và trả 204; request còn lại thấy inactive và trả 404. Lỗi DB rollback toàn bộ. Không lấy owner từ input để chọn pair A–B.

Chat chưa triển khai. Phần này cung cấp trạng thái quan hệ hiện tại cho Chat guard về sau; chưa tuyên bố đã revoke WebSocket subscription hay chứng minh giữ/phục hồi Chat history.

### Swagger, Postman và evidence

Swagger/OpenAPI tài liệu hóa ba route, bearer auth, pagination, DTO mẫu và mã lỗi. Swagger UI tiếp tục qua cấu hình Gateway hiện có, chọn Wardrobe Community Service; service cung cấp `/v3/api-docs`. Collection/run guide bàn giao riêng tại [Postman Friends](../../../postman/StyleMind-Wardrobe-Manage-Friends.postman_collection.json) và [hướng dẫn](../../../postman/EP06_MANAGE_FRIENDS_RUN_GUIDE.md).

Kiểm tra JSON/script structure khác với chạy HTTP thực. Automated tests gồm unit validator/service, PostgreSQL + MockMvc, embedded HTTP với JWT ký bằng test key, isolation A/B/C, rollback, concurrent unfriend, schema OpenAPI và regression Outfit. Kết quả thực tế ghi trong [báo cáo Task 02](../../qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md).

## B. Phần chưa triển khai — PROPOSED/TBD

| Route/chức năng dự kiến | Còn cần |
| --- | --- |
| Gửi email mời kết bạn | Notification friend-invitation contract, sender identity/email validation và rate-limit policy |
| Gửi lại lời mời | Link mới/hạn mới đã chốt B19; còn cooldown/cap và concurrent delivery/retry semantics |
| Nhận lời mời sau login/register | Current verified recipient email contract từ Identity; token/state/pair orchestration |
| Danh sách lời mời đến | Verified current email, validation và pagination contract; không tin email do client khai |
| Lời mời chéo hoặc lời mời PENDING có trước unfriend | Owner chốt xử lý trước nhánh accept/create tương ứng; không tự auto-accept hay cấp lại quan hệ |
| Repeated accept sau unfriend | B11 cấm dùng link ACCEPTED cũ để tái kích hoạt; response/retry cụ thể cần hoàn thiện cùng accept contract |

Không tạo production stub 503 để giả endpoint gửi/accept đã có. Bản này không tự thêm block, reject request, giới hạn số bạn bè hoặc số lời mời. Handoff cụ thể cho Quân/Vi tại [integration handoff](EP06_FRIENDS_INTEGRATION_HANDOFF.md).

**Trạng thái tổng Task 02: PARTIAL.** Phần A đã hoàn thành local; Maven verify 131 test pass, 0 failed/error/skipped, gồm 29 test mới. Task 03 chưa Done toàn bộ User Story; runtime Postman chưa chạy. Share Outfit vẫn ON_HOLD; Chat có migration/contract riêng về sau.
