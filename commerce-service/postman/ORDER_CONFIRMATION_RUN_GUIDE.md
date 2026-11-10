# Kiểm tra xác nhận và yêu cầu hủy

Import `order-confirmation.postman_collection.json`, cấu hình `base_url`, `order_id`, `admin_token`, `customer_token` và `cancellation_request_id`. Dùng đơn test và tài khoản riêng; không chia sẻ token.

1. Tạo đơn COD mới bằng collection checkout hiện có. Xác nhận `confirmation_state` là `UNCONFIRMED`.
2. Gọi **Admin xác nhận**. Kết quả là `CONFIRMED`; gọi với token khách phải bị từ chối.
3. Khách gọi hủy trực tiếp: phải nhận 409 `CANCELLATION_REVIEW_REQUIRED`. Khách tạo yêu cầu hủy, admin từ chối có lý do, rồi khách có thể tạo lại yêu cầu mới.
   Với đơn COD chưa thu tiền đang `PROCESSING`, admin có thể duyệt yêu cầu để hủy và giải phóng tồn kho; duyệt lại cùng yêu cầu phải trả lỗi, không giải phóng tồn kho lần nữa.
4. Khi đơn `SHIPPED`, không thể tạo hoặc duyệt yêu cầu hủy. Không dùng đơn có thanh toán thật để thử.
5. Hủy trực tiếp VietQR tạm trả `PAYMENT_CHECK_UNAVAILABLE`; việc duyệt hủy giao dịch cần đối soát trả `PAYMENT_RECONCILIATION_REQUIRED` cho đến khi có đồng bộ Payment an toàn. Không coi việc này là hoàn tiền.
6. Với đơn VietQR đang chờ thanh toán, dùng request **Kiem tra chan huy QR qua trang thai**: phải trả 409 `PAYMENT_RECONCILIATION_REQUIRED`, đơn và tồn kho giữ nguyên. Collection chỉ hỗ trợ thao tác; kết quả phải được đối chiếu trong môi trường test.

Đơn trước V11 có `LEGACY_UNKNOWN` và phải yêu cầu admin duyệt. Migration chỉ được áp dụng sau khi có kế hoạch triển khai, sao lưu và xác nhận của người vận hành.
