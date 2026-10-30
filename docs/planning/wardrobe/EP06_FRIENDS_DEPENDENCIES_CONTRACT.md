# EP06 — Identity và Notification hỗ trợ lời mời kết bạn

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser. [Báo cáo verification](../../qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md). Các khối v4.8/v4.9 bên dưới là checkpoint trước verification, không là trạng thái hiện tại.


**2026-10-28 — v4.9, SOURCE WRITTEN / VERIFICATION PENDING.** Owner đã trả lời “Duyệt scope V5” và “Duyệt thiết kế API”. Đã viết V5/JPA, lookup email nội bộ Identity và public send/resend/incoming/accept; chưa coi Task 02/03 Done vì chưa build/test. Docker để sau theo Owner. Các checkpoint phía dưới không thay evidence của lượt code mới. [Contract hiện hành](EP06_TASK02_INVITATION_API_DESIGN.md), [scope đã duyệt](EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md), [báo cáo](../../qa/wardrobe/EP06_TASK02_INVITATIONS_IMPLEMENTATION_REPORT.md).


**Cập nhật chốt nghiệp vụ 2026-10-28 — v4.8:** [quyết định Owner ngày 28](EP06_FRIENDS_INVITATION_RULES_2026-10-28.md) đã chốt mời chéo, pending cũ khi unfriend, repeated accept và 30 giây; bỏ cap 3/20 mỗi giờ. PENDING còn hạn đã SMTP tiếp nhận không gửi lại; expired/failed được thử lại bằng link mới/hạn mới. [scope V5](EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md) và [thiết kế API](EP06_TASK02_INVITATION_API_DESIGN.md) là đề xuất chưa duyệt, chưa tạo V5 hoặc public mutations. Các đoạn checkpoint trước bên dưới không thay quy tắc mới.


**2026-10-28.** Owner giao đọc source Quân/Vi và tự bổ sung phần còn thiếu. Đây là mở rộng nội bộ trong đúng service owner; không chuyển dữ liệu Identity sang Wardrobe hoặc sửa schema. JPA/layout interface → impl → repository theo source hiện tại. API cũ giữ nguyên.

## Identity: thông tin email xác minh

POST /internal/v1/accounts/email-verification-lookup, JSON:
```json
{"account_ids":["20000000-0000-0000-0000-000000000001"]}
```

Header X-StyleMind-Service-Token dùng cơ chế hiện có, thiếu/sai trả 401. Request 1–100 UUID theo validator nội bộ hiện có, collapse duplicate và bỏ qua ID không tồn tại; query read-only và sort account ID.

Response 200, Cache-Control no-store:
```json
{"items":[{"account_id":"20000000-0000-0000-0000-000000000001","email":"friend@example.test","account_status":"ACTIVE","email_verified_at":"2026-10-28T01:02:03Z"}]}
```

email_verified_at null nghĩa chưa có bằng chứng xác minh; ACTIVE riêng lẻ không thay timestamp. Trả status hiện tại độc lập với verification để consumer tự kiểm tra cả hai. Query chỉ chọn bốn facts từ Account, không join UserProfile nên tài khoản chưa có profile vẫn có mặt. Không đọc password/OTP/refresh token và không thêm email vào JWT.

Giữ nguyên /internal/v1/accounts/batch-lookup, bốn trường và semantics cũ của Quân/Outfit. Route mới hidden trong public Identity OpenAPI theo convention /internal hiện tại; không thêm Gateway route hoặc API lookup email cho browser. Postman nội bộ dùng direct backend test URL; không gửi service token từ frontend.

Wardrobe IdentityEmailVerificationClient đọc route mới, current account ID do server xác định. Unknown ID là Optional.empty; lỗi/malformed/wrong account/missing config → 503 IDENTITY_UNAVAILABLE. Facts helper chỉ activeAndEmailVerified nếu status ACTIVE và timestamp khác null. Consumer tương lai cần so email đã chuẩn hóa với recipient_email và không gọi network trong row lock. Chưa implement accept/incoming ở bước dependency.

## Notification: gửi thư mời kết bạn

POST /internal/v1/friend-invitations, cùng service-token boundary, body:
```json
{"recipientEmail":"friend@example.test","senderName":"Linh Trần","invitationUrl":"https://app.example.test/friends/invitations#fixture-link","expiresAt":"2026-11-04T01:02:03Z"}
```

- Recipient @Email/nonblank, sender name nonblank ≤100; URL HTTP(S), không whitespace/control, ≤2048; expiresAt cần OffsetDateTime.
- Wardrobe sở hữu token/hash/PENDING/7 ngày/resend. Notification không tự tạo lời mời/quan hệ hoặc quyết định deadline; render expiresAt caller gửi bằng UTC.
- Subject cố định, plain text + HTML UTF-8; escape tên/URL trong HTML, không đưa tên vào header. Hướng dẫn đúng email, login/register/verify và xác nhận chủ động; mở link chưa tự kết bạn.
- 202 chỉ sau JavaMailSender SMTP acceptance, không phải inbox delivery hoặc ACCEPTED invitation.
- 503 EMAIL_DELIVERY_FAILED khi SMTP không nhận; 504 EMAIL_DELIVERY_UNKNOWN khi timeout/không xác định. Lỗi được sanitize không đính kèm recipient/link/provider exception.
- Không auto retry; SMTP timeout có thể đã gửi. Không lưu token/link/mail body vào DB/log; DTO/template toString redacted.
- Swagger Notification có serviceToken scheme và các trạng thái trên. Existing Outfit/verification/reset senders và contract giữ nguyên.

Wardrobe FriendInvitationMailClient riêng, không dùng best-effort ShareInvitationMailClient nuốt lỗi. Kết quả ACCEPTED chỉ cho HTTP 202; FAILED chỉ cho 503 có code EMAIL_DELIVERY_FAILED; timeout/response không xác định là UNKNOWN; thiếu config là UNAVAILABLE. Feign NEVER_RETRY hiện có. Lời mời vẫn PENDING khi delivery chưa thành công; public orchestration/resend chưa có và chưa có persistence delivery-state mới.

## Phạm vi đã giao và phần tiếp theo

Không migration, không Compose/DB lâu dài, không gửi email tới người dùng thật. Regression ba service, PostgreSQL và SMTP recorder test cô lập; counts/pass lấy từ báo cáo sau verify.

Dependencies kỹ thuật được bổ sung ở bước này. Task 02/03 vẫn PARTIAL vì còn public send/resend/accept/incoming chưa có; nghiệp vụ đã chốt theo notice v4.8, scope/thiết kế bổ sung đang đề xuất. Contract này không duyệt ngầm các rule đó, frontend invitation route hoặc browser resume. US-06.02/Task 04–07 vẫn ON_HOLD.
