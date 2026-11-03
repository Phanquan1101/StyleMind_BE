# EP06 — Friend Invitations run guide

**Verification 2026-10-28 — PASS local:** collection đã chạy đủ **10 requests/20 assertions** thành 3 fixture: main 8/16, retry 1/2, email-link 1/2; không skipped request trong các lượt chọn. Gate Wardrobe 294 pass, actual SMTP/Register/verify/login/accept/chat/refriend đã pass. [Báo cáo](../docs/qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md). Phần mô tả NOT RUN dưới đây là checkpoint source trước verification; hướng dẫn fixture/opt-in vẫn áp dụng.


**2026-10-28. API source và test đã viết; Java/PostgreSQL/HTTP/Newman mới chưa chạy do Docker đang để sau theo Owner.** Không lấy checkpoint 28 requests/89 assertions của collection list/unfriend làm evidence cho invitation mới.

Import [collection](StyleMind-Wardrobe-Friend-Invitations.postman_collection.json) và [environment mẫu](StyleMind-Friend-Invitations.postman_environment.example.json). [Thiết kế đã được Owner duyệt](../docs/planning/wardrobe/EP06_TASK02_INVITATION_API_DESIGN.md), [nghiệp vụ](../docs/planning/wardrobe/EP06_FRIENDS_INVITATION_RULES_2026-10-28.md).

## Chuẩn bị

- Backend test áp V1–V5 trong PostgreSQL disposable; Identity/Notification/Gateway chạy với credential và SMTP recorder/Mailpit test. Không chạy collection này trên DB lâu dài hoặc với SMTP gửi đến người thật.
- Cấu hình runtime Wardrobe: STYLEMIND_FRIEND_INVITATION_PAGE_URL trỏ trang frontend nhận lời mời, ví dụ https://frontend.example.test/friends/invitations cho fixture. Frontend thực cần xử lý fragment invitation_id/token, login/register/verify rồi bấm Chấp nhận; backend không tạo trang hoặc tự accept khi mở link.
- Identity/Notification service URLs và shared service token dùng cấu hình hiện có. Không đặt service token trong frontend hoặc environment Postman public.
- Ba tài khoản CUSTOMER test A/B/C, email đã xác minh, token đăng nhập thực vào accessTokenA/B/C. Đặt friendEmailA/B đúng email của A/B; collection chỉ chấp nhận địa chỉ .test cho lượt fixture này.
- A/B chưa là bạn, chưa có pending A→B hoặc B→A; C khác cả hai. Dùng fixture mới nếu điều kiện này không đúng; không dùng tên collection để suy rằng fixture đã được chuẩn bị.

## Chạy

Folder 00 chỉ đọc incoming của B, dùng token B. Folder 01 có bảy request thay đổi dữ liệu: bật allowFriendInvitationMutations=true trong environment local rồi chọn riêng folder này. Nó tạo một pending A→B, kiểm tra duplicate/mời chéo/C không nhận thay, B chấp nhận chủ động, accept lặp lại và không mời lại bạn hiện có. Invitation ID chỉ là metadata fixture, không phải credential.

Các request có flag false bị skip; **skip không phải pass**. Nếu Postman runtime không hỗ trợ skipRequest, guard chuyển URL sang localhost:1 rồi báo lỗi, không gửi mutation tới backend. Không báo toàn collection pass khi chỉ chạy một folder.

Folder 02 là một fixture riêng: chuẩn bị retryInvitationId do A gửi, trạng thái hết hạn hoặc gửi thất bại/chưa biết kết quả, đã đủ 30 giây, A/B chưa là bạn. Bật allowRetryScenario=true và chạy riêng. PENDING đã SMTP_ACCEPTED và còn hạn không được resend, dù đã chờ 30 giây.

Folder 03 dùng một pending khác: điền linkInvitationId và invitationEmailToken lấy từ fragment của thư trong SMTP test inbox riêng, bật allowEmailLinkScenario=true, đăng nhập B đúng email đã xác minh. Không xuất/log/screenshot token. Sau khi accept thành công script xóa biến token trong environment local. Không chọn link đã được folder 01 accept.

Sau khi kết bạn bằng folder 01, có thể dùng [collection list/unfriend](EP06_MANAGE_FRIENDS_RUN_GUIDE.md) để thử unfriend A/B trên fixture riêng. Link đã dùng sẽ trả INVITATION_ALREADY_USED sau unfriend. Muốn refriend cần lời mời mới tạo sau cutoff; chờ đủ 30 giây kể từ lần thử gửi trước, sau đó nhận lời mời mới. Stable chat/history phải được kiểm tra riêng trong bộ integration/E2E; collection này không tự xác nhận Chat đã phục hồi.

## HTTP contract

| Route sau /wardrobe-community/api/v1/me | Input | Thành công |
| --- | --- | --- |
| POST /friend-invitations | recipient_email | 201 email accepted; 200 pending/incoming/already-friends, không tự gửi lại |
| POST /friend-invitations/{id}/resend | {} | 200 credential/deadline mới; chỉ sender và nhánh được phép |
| GET /friend-invitations/incoming | page/size | 200 current verified recipient; không expired/cutoff |
| POST /friend-invitations/{id}/accept | {} | 200 chấp nhận incoming chủ động, JWT/email đúng người |
| POST /friend-invitations/accept | friend_invitation_id/token | 200 credential hợp lệ và verified recipient |

Lỗi: 400 input, 401 bearer, 403 CUSTOMER/account verified, 404 sai actor/token, 409 đã gửi/đã dùng/vô hiệu hóa/replaced, 410 hết hạn, 429 cooldown (Retry-After), 503 dependency hoặc email failed, 504 email UNKNOWN. Email failure response có friend_invitation_id cho sender và retry_after_seconds; không lộ raw credential, hash hoặc URL.

30 giây áp theo sender/email qua cả việc tạo ID khác; không có cap 3/20 mỗi giờ. SMTP_ACCEPTED chỉ là SMTP tiếp nhận, không bảo đảm Inbox delivery. Timeout/SENDING treo có thể chủ động thử lại sau 30 giây, không tự retry; email lần cũ có thể đến muộn nhưng link cũ không khôi phục quan hệ.

Collection có **10 requests / 20 scripts**, dùng IIFE, token mẫu trống và ba mutation flags false. JSON/script structural validation tách riêng với HTTP/Newman execution. Các kịch bản được viết chưa phải test pass cho tới khi thực chạy; xem báo cáo implementation mới trước khi dùng làm handover Done.
