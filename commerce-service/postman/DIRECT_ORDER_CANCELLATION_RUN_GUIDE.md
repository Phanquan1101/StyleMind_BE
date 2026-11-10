# Hủy đơn trực tiếp

Import `direct_order_cancellation.postman_collection.json`. Đặt `baseUrl`, `customerToken`, `orderId` và `reason` theo môi trường test. Không commit token thật.

Với tài khoản sở hữu và đơn chưa được xử lý, `POST /api/v1/orders/{orderId}/cancel` trả đơn có `order_status=CANCELLED`. Gửi lại cùng yêu cầu trả trạng thái đã hủy; hàng tồn được hoàn một lần. Tài khoản khác không thể hủy đơn. Đơn đã thanh toán hoặc đang xử lý không được hủy trực tiếp. Yêu cầu hủy cũ đang chờ được đóng khi khách tự hủy thành công; lịch sử trạng thái ghi người thực hiện và lý do.

Chỉ chạy collection với dữ liệu test do bạn tạo. Không thực hiện thanh toán thật; nếu chuyển khoản xảy ra đồng thời hoặc sau khi hủy, hệ thống thanh toán có thể cần đối soát, không có hoàn tiền tự động. Giao diện khách đang đăng nhập gọi API này; đơn khách chưa đăng nhập chưa có luồng hủy tương ứng.
