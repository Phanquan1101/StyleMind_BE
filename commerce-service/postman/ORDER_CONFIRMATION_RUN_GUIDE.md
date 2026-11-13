# Kiểm tra xác nhận và yêu cầu hủy

Import `order-confirmation.postman_collection.json`, cấu hình `base_url`, `order_id`, `admin_token`, `customer_token` và `cancellation_request_id`. Dùng đơn test và tài khoản riêng; không chia sẻ token.

1. Tạo đơn COD mới bằng collection checkout hiện có. Xác nhận `confirmation_state` là `UNCONFIRMED`.
2. Gọi **Admin xác nhận**. Kết quả là `CONFIRMED`; gọi với token khách phải bị từ chối.
3. Khách gọi hủy trực tiếp và nhận 409 `CANCELLATION_REVIEW_REQUIRED`. Khách tạo yêu cầu hủy, admin từ chối có lý do. Gửi lại yêu cầu hủy nhận 409 `CANCELLATION_ALREADY_REQUESTED`. Mỗi đơn chỉ có một yêu cầu hủy trong suốt vòng đời.
   Với đơn COD chưa thu tiền đang `PROCESSING`, admin có thể duyệt yêu cầu để hủy và giải phóng tồn kho; duyệt lại cùng yêu cầu phải trả lỗi, không giải phóng tồn kho lần nữa.
4. Khi đơn `SHIPPED`, không thể tạo hoặc duyệt yêu cầu hủy. Không dùng đơn có thanh toán thật để thử.
5. Hủy trực tiếp đơn online chưa được xác nhận trả `PAYMENT_CHECK_UNAVAILABLE`. Duyệt hủy VietQR hoặc VNPay đã trả tiền cần sự kiện Payment `APPLIED` khớp chủ đơn, phương thức và toàn bộ số tiền. Khi thiếu hoặc sai bằng chứng, kết quả là 409 `PAYMENT_RECONCILIATION_REQUIRED`.
6. Với đơn VietQR đang chờ thanh toán, dùng request **Kiem tra chan huy QR qua trang thai**: phải trả 409 `PAYMENT_RECONCILIATION_REQUIRED`, đơn và tồn kho giữ nguyên. Collection chỉ hỗ trợ thao tác; kết quả phải được đối chiếu trong môi trường test.

Đơn trước V11 có `LEGACY_UNKNOWN` và phải yêu cầu admin duyệt. Migration chỉ được áp dụng sau khi có kế hoạch triển khai, sao lưu và xác nhận của người vận hành.
# Duyệt hủy VietQR hoặc VNPay đã thanh toán

V13 được chủ dự án duyệt ngày 11/11/2026. Với VietQR, tạo phiên QR bằng collection QR Checkout và nhận sự kiện thanh toán hợp lệ để có đơn PAID. Với VNPay, dùng collection VNPAY Sandbox và chờ IPN được xác minh, sau đó dùng mã đơn cho các request dưới đây.

1. Khách gửi yêu cầu hủy và lưu `cancellation_request_id`.
2. Admin gọi request duyệt yêu cầu hủy. Kết quả `APPROVED` có `refund_status=PENDING_REFUND`.
3. Khách đọc lại đơn. Đơn có trạng thái CANCELLED và `cancellation_request.refund_status=PENDING_REFUND`. Khoản chờ hoàn lấy số tiền từ Payment đã xác nhận.
4. Gọi duyệt lần nữa nhận 409. Khách gọi API duyệt nhận 403. Đơn đã SHIPPED nhận 409 khi gửi hoặc duyệt yêu cầu hủy.
5. Khi thiếu hoặc sai bằng chứng thanh toán, admin nhận 409 `PAYMENT_RECONCILIATION_REQUIRED` và yêu cầu tiếp tục chờ duyệt.

PENDING_REFUND ghi nhận khoản cần hoàn. Việc chuyển khoản và xác nhận hoàn tiền thuộc phần hoàn tiền tiếp theo. Kiểm thử tự động sử dụng PostgreSQL cô lập. Cần rebuild Commerce để Flyway áp dụng V13 trước khi kiểm tra trên môi trường của bạn.

## Chặn xử lý khi yêu cầu hủy đang chờ

Với đơn test còn `PLACED` hoặc `PAID`, tạo yêu cầu hủy rồi chạy **Pending cancellation blocks confirmation**. Kết quả là 409 `CANCELLATION_PENDING`. Với đơn đã xác nhận hoặc đang `PROCESSING` có yêu cầu hủy chờ quyết định, chạy request xử lý hoặc giao hàng tương ứng và đối chiếu cùng lỗi. Admin phải quyết định yêu cầu hủy trước khi tiếp tục đơn. Sau khi từ chối, đơn có thể tiếp tục xử lý, khách gửi lại yêu cầu hủy nhận 409 `CANCELLATION_ALREADY_REQUESTED`.

Các request lỗi là ca kiểm tra riêng theo trạng thái dữ liệu mô tả, mỗi ca dùng đơn test phù hợp. Collection đã kiểm tra cấu trúc JSON. Việc gọi HTTP trong Postman do người vận hành thực hiện trên môi trường test.

