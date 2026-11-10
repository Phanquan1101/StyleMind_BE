# Kiểm tra phiên VietQR trước khi tạo đơn

Chỉ dùng tài khoản và môi trường kiểm thử. Import `qr-checkout.postman_collection.json`, đặt `baseUrl` là Gateway (ví dụ `http://localhost:8080`), `accessToken`, `addressId` và `variantId` của người dùng kiểm thử. Không đưa token hoặc thông tin ngân hàng thật vào collection.

1. Gọi **Chuẩn bị phiên** với `checkout_key` duy nhất. Lưu `checkoutId` vào biến `checkoutId`. Phiên phải ở `PREPARING` và chưa có đơn hàng.
2. Gọi **Tạo mã VietQR** để nhận thông tin từ Payment. Có thể gọi **Hủy thanh toán** ngay cả khi yêu cầu tạo mã còn đang chạy. Chỉ coi là đã hủy khi API xác nhận `CANCELLED`.
3. Gọi **Xem phiên**: khi nhận thanh toán hợp lệ, `ORDER_CREATED` cho biết đơn đã được tạo; mã đơn là `checkoutId`. Khi hủy hoặc hết hạn, hàng được giải phóng. Không chuyển tiền vào mã đã hủy hoặc hết hạn.
4. Dùng **Khôi phục phiên** với cùng `checkout_key` nếu không nhận được kết quả bước 1. Với thông báo đến muộn hoặc sau hủy, kiểm tra ngoại lệ Payment và đối soát thủ công; không xác nhận hoàn tiền khi chưa thực hiện.

Việc xem mã QR hoặc chạy collection không chứng minh đã có chuyển tiền. Kiểm tra xác nhận thực cần môi trường SePay/webhook test do nhóm cấu hình. Luồng trong collection dành cho tài khoản đã đăng nhập.

Khách phải đăng nhập để tạo đơn hoặc phiên thanh toán mới. Với phiên khách đã tạo trước khi thay đổi này, các API xem, thanh toán hoặc hủy phiên và xem đơn cũ vẫn cần header `X-Guest-Session-Key`; không lưu khóa phiên vào collection chia sẻ.
