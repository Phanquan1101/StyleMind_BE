# Dự thảo thiết kế hủy đơn và hoàn tiền

Trạng thái: DỰ THẢO, chưa phê duyệt migration hay triển khai. Phạm vi là dự án StyleMind hiện tại; tham khảo nghiệp vụ dự án mới, không sao chép nguyên service.

## Quy tắc đã thống nhất

- Đơn vừa đặt, chưa được admin xác nhận và chưa thanh toán: khách hủy ngay.
- Đơn COD được admin xác nhận nhưng chưa giao: khách gửi yêu cầu để admin duyệt.
- Đơn đã thanh toán được duyệt hủy: hoàn toàn bộ bằng chuyển khoản thủ công; admin xác nhận sau khi chuyển thực tế.
- Admin có thao tác xác nhận đơn riêng; khách được gửi lại yêu cầu hủy sau khi bị từ chối.
- Đơn cũ không đủ dữ liệu xác nhận: khách gửi yêu cầu để admin duyệt, không tự suy đoán là chưa xác nhận.
- Đợt đầu chỉ tài khoản đăng nhập được yêu cầu hủy và cung cấp thông tin hoàn tiền; khách vãng lai chưa thuộc phạm vi.

## Đề xuất nghiệp vụ cần chốt

- QR đã nhận tiền, hoặc đơn đang chuẩn bị: khách gửi yêu cầu hủy; admin duyệt hoặc từ chối có lý do. Đơn đang giao không hủy trực tiếp; đơn hoàn thành thuộc nghiệp vụ đổi/trả riêng.
- Tiền QR về sau khi hủy phải lưu lại và đưa vào đối soát; chỉ tạo hồ sơ hoàn khi xác định được giao dịch và người nhận hợp lệ. Không ghi đã hoàn chỉ vì đã tạo hồ sơ.
- Không tự chuyển khoản, không báo xác nhận của admin là bằng chứng ngân hàng đã chuyển tiền.

## Giới hạn cần xử lý trước khi chốt schema

- COD hiện tự chuyển tới PLACED sau bước thiết lập thanh toán; PLACED không tự chứng minh admin đã xác nhận. Lưu dấu xác nhận riêng và tránh coi đơn cũ là chưa xác nhận khi không có bằng chứng.
- cancellation_request hiện UNIQUE theo order_id; cần thay ràng buộc để cho phép gửi lại sau từ chối và chỉ có tối đa một yêu cầu PENDING mỗi đơn.
- Commerce và Payment sở hữu database riêng; không truy cập chéo hoặc tạo foreign key xuyên database. Payment outbox hiện chỉ cho PaymentConfirmed, Commerce đã có inbox và đối soát; không được giả định giao dịch hai service nguyên tử.
- Cần có giao thức điều phối bền vững, idempotency, retry, khôi phục sau lỗi và đối soát khi hủy và nhận tiền cạnh tranh. Khi trạng thái thanh toán không xác định phải thể hiện trạng thái xử lý, không kết luận chưa nhận tiền.
- Đảm bảo số tiền hoàn không vượt tiền đã nhận; chống hoàn trùng, bảo vệ dữ liệu tài khoản ngân hàng, giới hạn truy cập và không ghi log thông tin nhạy cảm.

## Dự kiến phạm vi dữ liệu — chưa duyệt

Payment: đề xuất bảng hồ sơ hoàn tiền chứa định danh, tham chiếu logic order/cancellation, tham chiếu giao dịch Payment trong cùng database khi có, số tiền VND, trạng thái, thông tin nhận tiền được bảo vệ, thời gian và người xử lý, mã tham chiếu chuyển khoản và lý do thất bại. Cần ràng buộc số tiền, trạng thái, tính duy nhất/idempotency, chỉ mục quản trị và phương án xử lý giao dịch đến trễ.

Commerce: xem xét thay đổi cancellation_request và/hoặc bổ sung trạng thái xác nhận admin, điều phối và lịch sử. Cần DDL, ràng buộc, cách chuyển dữ liệu cũ và tương thích API cụ thể trước khi xin duyệt migration. Không chỉnh sửa các migration đã có.

Không coi dự thảo này là scope migration đã được phê duyệt. Tên migration, cấu trúc DDL cuối cùng, dữ liệu cũ và quyền guest cần được đối chiếu trước khi trình chủ dự án duyệt.

## Thực hiện sau khi duyệt

1. Lập contract và scope DDL, bao gồm đơn cũ và phạm vi chỉ áp dụng cho tài khoản đăng nhập.
2. Hoàn thiện hủy COD/QR, phối hợp thanh toán đến trễ và xử lý lỗi giữa service.
3. Bổ sung hồ sơ, quyền và API hoàn tiền; không tự xác nhận chuyển khoản.
4. Nối FE khách hàng và admin; chỉ báo kết quả theo trạng thái BE thực tế.
5. Kiểm thử cô lập: COD/QR, đã/chưa trả, bấm trùng, webhook trùng/đến trễ, đồng thời duyệt và thanh toán, lỗi service, chủ sở hữu và quyền admin, dữ liệu cũ; bổ sung Postman và hướng dẫn.

AGENTS.md hiện loại trừ refund và yêu cầu quyết định chủ sở hữu cùng scope migration được duyệt; yêu cầu hiện tại là cơ sở mở rộng nghiệp vụ, nhưng chưa phải duyệt DDL cụ thể. Không chạy Compose hoặc thay đổi DB lâu dài trong giai đoạn thiết kế.
