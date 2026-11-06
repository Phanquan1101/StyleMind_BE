# EP06 Task 02 — Thiết kế API gửi và nhận lời mời

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser. [Báo cáo verification](../../qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md). Các khối v4.8/v4.9 bên dưới là checkpoint trước verification, không là trạng thái hiện tại.


**2026-10-28 — v4.9, SOURCE WRITTEN / VERIFICATION PENDING.** Owner đã trả lời “Duyệt scope V5” và “Duyệt thiết kế API”. Đã viết V5/JPA, lookup email nội bộ Identity và public send/resend/incoming/accept; chưa coi Task 02/03 Done vì chưa build/test. Docker để sau theo Owner. Các checkpoint phía dưới không thay evidence của lượt code mới. Xem [báo cáo](../../qa/wardrobe/EP06_TASK02_INVITATIONS_IMPLEMENTATION_REPORT.md).

## 1. Boundary và authorization

Giữ Controller → Service interface → impl → internal persistence → Repository JPA. Các HTTP API public dùng `/api/v1/me`, Gateway thêm `/wardrobe-community`. JWT/CUSTOMER và UUID subject theo convention hiện có. Không nhận sender/account owner, trạng thái xác minh hay deadline do client khai.

Các thao tác mới đọc current account facts từ Identity trước transaction: đúng account, ACTIVE và email đã xác minh. Facts thiếu/lỗi không được suy thành đã verified. Request JSON strict: không field lạ, duplicate key hoặc trailing object; recipient email chuẩn hóa trim/lowercase như Identity và kiểm tra syntax/length; không chuẩn hóa token. Không GET nào tạo quan hệ hay gửi mail.

Ba API list friends/outgoing/unfriend và response hiện tại tiếp tục dùng được. `INVALIDATED` chỉ bổ sung biểu diễn lời mời cũ mất hiệu lực sau khi V5/API được triển khai; không sửa lịch sử ACCEPTED thành PENDING.

## 2. Routes đã viết trong source

| Route | Input | Kết quả/side effects |
| --- | --- | --- |
| `POST /api/v1/me/friend-invitations` | `{"recipient_email":"friend@example.test"}` | Mời mới nếu chưa có quan hệ/lời mời ngăn cản; commit digest/attempt trước Notification. PENDING còn hạn đã gửi thì trả hiện có, không gửi thêm. Có incoming hợp lệ từ người kia thì trả tham chiếu incoming, chỉ dẫn Chấp nhận; không tự kết bạn |
| `POST /api/v1/me/friend-invitations/{invitationId}/resend` | JSON `{}` | Chỉ người gửi; chỉ lời mời hết hạn hoặc lần gửi chưa thành công, và đủ 30 giây. Token/deadline mới theo B19. INVALIDATED hoặc đã dùng không được tái kích hoạt |
| `GET /api/v1/me/friend-invitations/incoming` | `page`, `size` theo validator hiện có | Dùng verified current email từ Identity; chỉ lời mời PENDING còn hạn, chưa bị cutoff vô hiệu hóa. Không phát hành token, không gửi email, không tự accept |
| `POST /api/v1/me/friend-invitations/{invitationId}/accept` | JSON `{}` | Chấp nhận chủ động từ danh sách incoming: JWT và verified current email phải đúng recipient; actor không phải sender |
| `POST /api/v1/me/friend-invitations/accept` | JSON có `friend_invitation_id`, `token` | Chấp nhận chủ động từ link email: kiểm tra credential digest đúng invitation context và verified current recipient; forwarded link không giúp account khác nhận thay |

Hai đường accept dùng cùng Service/transaction, không nhân đôi business logic. Đường incoming không đòi backend trả lại raw token chỉ lưu hash; biết invitation ID không cấp quyền nếu email đã xác minh không khớp. Hai route đã có source; chưa có evidence HTTP/runtime cho lượt mới.

Public response không trả raw token/hash/link hay facts xác minh của người nhận. Các outcome trong source: tạo mới 201, đã có pending/incoming/đã là bạn 200 với outcome rõ; resend bị chặn 409; chưa đủ 30 giây 429 kèm Retry-After; hết hạn 410; identity/notification không sẵn sàng 503, kết quả gửi chưa rõ 504. Actor khác/credential sai trả resource-not-found theo convention. DTO/Swagger và Postman đã viết; HTTP/Newman chưa chạy. Codes: INVITATION_ALREADY_SENT/ALREADY_USED/INVALIDATED/REPLACED/COOLDOWN, EMAIL_DELIVERY_FAILED/UNKNOWN, EMAIL_VERIFICATION_REQUIRED.

## 3. Identity và frontend cần cho orchestration

Lookup facts theo account ID đã có tại `/internal/v1/accounts/email-verification-lookup`. Để xác định một email đích có phải account đã là bạn/người có lời mời ngược, đã bổ sung lookup **server-to-server** theo email thay việc quét toàn bộ directory hoặc truy cập DB Identity từ Wardrobe:

```text
POST /internal/v1/accounts/email-verification-lookup-by-email
X-StyleMind-Service-Token: <runtime only>
{"email":"friend@example.test"}

200 {"items":[]} hoặc items chứa đúng một account fact theo DTO hiện có:
account_id, email, account_status, email_verified_at
```

Giữ nguyên hai lookup nội bộ hiện có; route mới không ra Gateway/Swagger public/frontend, cùng service-token guard, no-store và redacted diagnostics. Query Account trực tiếp, không cần profile, không đọc password. Public gửi lời mời không tiết lộ account đích đã đăng ký hay chưa: email chưa có account vẫn được mời theo B1. **Route đã có source/tests trong Identity; contract đã được Owner duyệt, test thực thi còn chờ.**

Frontend invitation page chưa có contract trong source backend. Đã cấu hình URL trang nhận lời mời qua STYLEMIND_FRIEND_INVITATION_PAGE_URL; không gửi email nếu URL chưa được cấu hình hợp lệ. Invitation ID/token nằm trong fragment; frontend login/register/verify đúng email, giữ context rồi yêu cầu người nhận bấm Chấp nhận. Không dùng GET tự động accept. Không hardcode URL production hoặc thêm email/token vào access JWT; test chỉ dùng URL/credential test.

## 4. State machine gửi và gửi lại

1. Normalize/validate request và đọc trusted Identity facts ngoài transaction. Chặn tự mời, đã là bạn, hoặc có incoming hợp lệ; incoming chỉ được chấp nhận chủ động.
2. Serialize sender/email và canonical account pair nếu đã resolve được recipient. Cần khóa transaction cho cả trường hợp row chưa tồn tại, sau đó recheck rows; không chỉ dựa vào preflight/network hoặc unique exception. Định nghĩa một lock order chung cho create/accept/unfriend để tránh deadlock. Không giữ lock qua Identity/Notification.
3. PENDING + còn hạn + SMTP_ACCEPTED: trả Đã gửi lời mời, không ghi/rotate/gửi. POST tạo trùng khi lần gửi trước FAILED/UNKNOWN cũng chỉ trả lời mời hiện có và trạng thái thử lại; không âm thầm gọi resend. Người gửi phải chủ động dùng thao tác gửi lại. Không có giới hạn 3/20 mỗi giờ. Đã đủ 30 giây riêng nó không cho phép gửi lại một pending đã SMTP_ACCEPTED.
4. Hết hạn hoặc kết quả chưa thành công và retry hợp lệ: kiểm tra lần thử gần nhất theo sender/email, không chỉ row ID. Ghi attempt UUID, SENDING, last-attempt, digest/deadline mới cùng transaction; commit trước HTTP.
5. Notification 202 → SMTP_ACCEPTED, explicit failure → FAILED, timeout/network/không biết → UNKNOWN. Finalization chỉ cập nhật khi attempt ID vẫn khớp, giữ các thay đổi accept/unfriend đã commit. Không tự retry hoặc khẳng định Inbox delivery.
6. Trường hợp UNKNOWN/tiến trình chết khi SENDING: Owner đã duyệt cho người gửi chủ động retry khi đủ 30 giây, không background resend. Kết quả lần trước có thể đến muộn; credential cũ bị thay và completion cũ không ghi đè attempt mới. Approval đã nhận; không giả rằng SMTP+DB có transaction phân tán hoặc exactly-once delivery.

Khi refriend cần lời mời mới tạo sau cutoff; không resend một row trước cutoff để né B23. Request resend một row cũ không được thay thế một PENDING mới đã tồn tại cùng sender/email. Chính sách duplicate giữ nguyên credential của lời mời hiện hành.

## 5. Accept, unfriend và lời mời chéo

- Resolve Identity facts trước transaction; trong transaction kiểm tra lại invitation, actor/email, deadline/credential theo đường vào, status và canonical Friendship/cutoff. Không có network trong row locks.
- Chấp nhận PENDING hợp lệ: ghi ACCEPTED và insert hoặc reactivate cùng Friendship atomically. Unique pair, rollback và locking bảo vệ hai request đồng thời. Hai account chưa kết bạn không có quyền Chat chỉ nhờ đã gửi email.
- Hai người gửi chéo: sau serialization chỉ còn một lời mời có thể nhận; bên kia được tham chiếu incoming và Chấp nhận, không gửi thêm email và không auto-accept. Cần kiểm thử A→B/B→A đồng thời, không chỉ tuần tự.
- Lời mời đã ACCEPTED: đúng accepted actor và còn bạn thì trả đã là bạn, không ghi hoặc kéo dài hạn; sau unfriend trả đã sử dụng, không tự tái kích hoạt. Một account khác dùng lại email không trở thành accepted actor cũ.
- Unfriend giữ route/authorization hiện tại và cùng stable Friendship/history/attachments; cập nhật cutoff cùng transaction. Mọi accept/incoming của lời mời tạo trước hoặc đúng cutoff bị chặn, kể cả sau refriend clear `unfriended_at`. Materialize INVALIDATED chỉ cho lời mời chưa được dùng; không đổi ACCEPTED history.
- Chat đã có current Friendship guards: cả hai mất read/send/push và ẩn list khi unfriend, chỉ hồi phục sau accept lời mời mới. Không viết lại Chat/Outfit hoặc thêm FK tới chúng.

## 6. Acceptance và handover sau khi triển khai

| Case | Kết quả bắt buộc |
| --- | --- |
| Recipient chưa có account | PENDING; register/verify/login đúng email rồi chủ động accept |
| Forwarded link / sai người / chưa verified | Không có Friendship, không Chat, không lộ raw credential |
| Duplicate lúc pending đã SMTP_ACCEPTED, kể cả sau 30 giây | Không thêm email, không đổi digest/deadline |
| Failed hoặc expired retry | Trước 30 giây bị chặn; đúng/sau mốc được thử hợp lệ; credential cũ mất hiệu lực, TTL mới 7 ngày |
| SMTP timeout, crash, completion muộn | Không báo chắc chắn đã/chưa gửi; không auto retry; completion cũ không ghi đè lần mới |
| Crossed send / concurrent accept / accept-unfriend / rollback | Một Friendship, một lời mời có thể nhận; không implicit friend hoặc resurrection |
| Unfriend rồi dùng pending/accepted cũ; refriend rồi thử pending cũ | Không khôi phục từ lời mời cũ; current pair cutoff vẫn được giữ |
| Identity/Notification không sẵn sàng | Sanitized result, không giữ remote call trong transaction, không lộ service token/email credential |

Unit + HTTP/controller + PostgreSQL/repository + regression, rồi packaged runtime email/auth/register/accept/chat/unfriend/refriend với DB và SMTP cô lập. Cập nhật collection Friends và run guide hiện có, kiểm tra cấu trúc riêng với Newman thực; không dùng SQL refriend thay invitation E2E. Gate Identity baseline 15 failures phải báo riêng, không che bằng dependency tests pass.

Đã viết các case mới nhưng chưa chạy. V5/API/lookup email và UNKNOWN/SENDING đã được Owner duyệt. Chưa build/test Java/PostgreSQL/HTTP/Newman do Docker để sau; Share Outfit/cloud/browser vẫn riêng.
