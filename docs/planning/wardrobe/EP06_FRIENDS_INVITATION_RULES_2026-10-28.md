# EP06 — Quyết định lời mời kết bạn ngày 2026-10-28

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser. [Báo cáo verification](../../qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md). Các khối v4.8/v4.9 bên dưới là checkpoint trước verification, không là trạng thái hiện tại.


**Trạng thái nghiệp vụ: OWNER CONFIRMED. Trạng thái triển khai: đã viết source, chưa build/test public send/resend/accept/incoming.** Tài liệu này ghi các câu trả lời mới nhất của Owner, thay đề xuất giới hạn và resend trước đó trong đúng phạm vi dưới đây. Không đánh dấu Task 02/03 Done.

## 1. Quyết định đã chốt

| ID | Quy tắc được Owner xác nhận | Tình huống và kết quả |
| --- | --- | --- |
| B22 | Không gửi lời mời chéo và không tự động kết bạn | A đã mời B, lời mời còn hiệu lực; B chọn mời A thì chỉ có thao tác Chấp nhận lời mời của A, không tạo lời mời B → A và không gửi thêm email |
| B23 | Hủy kết bạn vô hiệu hóa các lời mời PENDING cũ giữa hai người | Không dùng lời mời có trước lần hủy kết bạn để khôi phục quan hệ; cần lời mời mới và người nhận chủ động chấp nhận |
| B24 | Xử lý mở/chấp nhận lại lời mời đã dùng | Đúng người đã nhận, hai người còn là bạn: báo đã là bạn, không ghi thêm quan hệ. Đã hủy kết bạn: báo lời mời đã sử dụng, không kích hoạt lại Friendship |
| B25 | Cùng người gửi → cùng email: mỗi lần thực sự thử gửi cách nhau ít nhất 30 giây | Tính lần gửi đầu, gửi lại và lần đã thử gửi nhưng lỗi/timeout. Đây là điều kiện tối thiểu cho lần thử tiếp theo, không tự cấp quyền resend |
| B26 | Không áp hai hạn mức từng đề xuất: 3 lần/giờ và 20 lần/giờ | Không thêm giới hạn số bạn bè hay tổng số lời mời từ các con số này. Hạn mức của nhà cung cấp email vẫn áp dụng |
| B27 | Lời mời PENDING còn hạn và email đã được tiếp nhận thì không gửi tiếp | Owner: “A gửi cho B thì không được gửi tiếp nữa”. Bấm lại chỉ báo Đã gửi lời mời, không gửi email, không đổi token hay deadline; chờ đủ 30 giây cũng không mở lại quyền gửi |
| B28 | Hết hạn 7 ngày hoặc email gửi thất bại thì được gửi lại | Owner trả lời “có chứ” khi được hỏi riêng hai trường hợp. Thao tác gửi lại hợp lệ tạo link mới, vô hiệu link cũ và tính lại 7 ngày theo B19; vẫn kiểm tra B23/B25 |
| B8 bổ sung | Kết bạn chỉ cho xem Outfit có chế độ Bạn bè/Public | Owner chọn “Chỉ xem Outfit Bạn bè/Public”; Outfit Private vẫn chỉ chủ sở hữu xem. Không đổi thành chia sẻ toàn bộ tủ; Task 04–07 vẫn ON_HOLD |

Nguồn: câu trả lời trực tiếp của Owner trong hội thoại ngày 28/10. B1–B21 tiếp tục áp dụng theo [plan](EP06_WORK_PLAN_AND_PROGRESS.md). Các đề xuất 60 giây, 3 lần/giờ, 20 lần/giờ và resend một lời mời còn hạn đã gửi thành công bị thay thế bởi B25–B28.

## 2. Phân biệt gửi email và chấp nhận kết bạn

- `friend_invitation.status = PENDING` nghĩa chưa chấp nhận kết bạn; không chứng minh email đã gửi được.
- Notification `202` chỉ chứng minh máy chủ SMTP tiếp nhận email, không chứng minh thư đã vào Inbox hay người nhận đã đọc.
- Notification `503 EMAIL_DELIVERY_FAILED` là lần gửi thất bại; `504 EMAIL_DELIVERY_UNKNOWN` hoặc lỗi mạng là chưa biết kết quả. Không biến timeout thành đã gửi thành công hoặc chắc chắn chưa gửi.
- B25 không cho backend tự động retry. Người gửi chủ động thử lại khi được phép; lần thử mới phải xoay token đúng B19.
- Sau khi `202` đã được lưu, không có chức năng “gửi lại vì chưa thấy trong Inbox” trong thời gian lời mời còn hiệu lực. Người nhận có thể xử lý lời mời đến khi đăng nhập đúng tài khoản.

## 3. Scope/thiết kế đã duyệt, verification còn chờ

Baseline V3 chỉ lưu trạng thái lời mời, digest token và các mốc thời gian. Chưa lưu kết quả gửi email, mã lần gửi hoặc mốc vô hiệu hóa lời mời của cặp bạn bè. Vì vậy B22–B28 là nghiệp vụ đã chốt nhưng không tự duyệt một migration mới.

[Scope V5](EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md) và [thiết kế API](EP06_TASK02_INVITATION_API_DESIGN.md) đã được Owner trả lời “Duyệt scope V5” và “Duyệt thiết kế API”. Đã viết source, chưa chạy test/migration. UNKNOWN/SENDING treo cho phép chủ động retry sau 30 giây bằng link mới, không background resend; completion cũ không ghi đè lần mới. Không có cam kết SMTP+DB exactly-once.

## 4. Phạm vi được giữ nguyên

Controller → Service interface → impl → internal persistence → Repository JPA. Actor lấy từ JWT; email hiện tại đã xác minh lấy từ Identity, không tin email actor do client khai. Không SQL/FK xuyên service. Không sửa schema Outfit/Chat/Identity/Notification, Register/Login/OTP hoặc các migration V1–V4.

Ba API Friends cũ giữ nguyên; đã viết năm route gửi/gửi lại/incoming/accept và lookup email nội bộ. Các test mới đã viết nhưng chưa chạy; actual invitation E2E còn thiếu. Cloud ảnh/browser và 15 lỗi Identity regression baseline vẫn là công việc riêng, không được coi là đã hoàn tất do chốt nghiệp vụ.
