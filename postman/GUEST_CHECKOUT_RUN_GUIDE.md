# Guest checkout và hướng dẫn nối FE

Guest checkout đã được khôi phục. Không tạo tài khoản, không gọi Identity và không cần JWT cho bốn API guest.

## Chạy Postman

Import `StyleMind-Guest-Checkout.postman_collection.json`. Base URL mặc định: `http://localhost:8080/commerce`. Chọn môi trường local/test có Commerce, Payment, Gateway, service credentials và sản phẩm ACTIVE có số lượng khả dụng. Điền `variant_id`; collection tự sinh guest key và checkout key nếu để trống. Không dùng credential của khách thật hoặc ghi vào Git.

Chạy request 1–9 theo thứ tự. COD mặc định; đơn chuyển PENDING_PAYMENT → PLACED khi Payment xác nhận attempt, tiền vẫn chưa thu. Để thử SePay: đổi `payment_method` thành `SEPAY_VIETQR`, làm trống `checkout_key`, giữ `guest_session_key` và chạy lại từ request 1. Response payment dùng `id, orderId, method, amountVnd, reference, status, expiresAt, transferContent, qrPayload`. Sau khi SePay đã PAID, recovery trả 409 là đúng; bước 5 dành cho đơn còn PENDING_PAYMENT.

Collection không tự thu COD, gửi webhook giả, xóa dữ liệu hoặc đổi trạng thái đơn. Kiểm thử Admin collection/provider delivery bằng các collection Payment hiện có trong môi trường test. Chạy collection sẽ tạo giỏ/đơn/attempt trong môi trường được chọn.

## FE cần làm

1. Sinh và lưu key bằng `crypto.randomUUID()`. Dùng cùng key cho giỏ guest hiện có và header `X-Guest-Session-Key` của checkout. Giữ key qua refresh; không đưa lên URL, analytics hoặc log. Mất key thì không có luồng xác minh số điện thoại để lấy lại đơn.
2. Cho phép mở checkout khi chưa đăng nhập. Khách đăng nhập vẫn dùng API checkout và địa chỉ đã lưu hiện có. Guest nhập `recipient_name`, `recipient_phone`, `address_line` trực tiếp; không gọi API địa chỉ Identity. Phone là chuỗi bắt buộc 1–64 ký tự sau trim, không OTP/kiểm tra đầu số.
3. Gọi `GET /api/v1/guest/checkout/prices` để hiển thị giá server tính. Gọi `POST /api/v1/guest/orders` với body bên dưới; giữ `checkout_key` cho cùng lần gửi, tránh sinh key mới tự động khi timeout.
4. Lưu `order_id` và key. Khi tạo đơn thành công, chuyển sang trang kết quả rồi gọi `GET /api/v1/guest/orders/{order_id}`. PLACED của COD chưa có nghĩa đã thu tiền.
5. Gọi `POST /api/v1/guest/orders/{order_id}/payment-attempt` không có body để lấy attempt/QR hoặc thử lại handoff. COD cũng có thể cần recovery nếu Payment tạm lỗi. Guest không gọi public Payment API để tạo attempt.
6. Dùng GET detail để cập nhật trạng thái SePay. Xử lý 401 thiếu/key sai định dạng, 404 đơn không thuộc key hoặc không tồn tại, 409 checkout trùng/đơn không còn thanh toán được. Giữ key qua đăng nhập vì đơn guest không tự gắn vào tài khoản.

Các path trên là path Commerce; qua Gateway thêm `/commerce`. Không có API liệt kê hoặc hủy đơn guest. Không có guest role trong Identity.

```json
{
  "checkout_key": "a-new-random-uuid",
  "payment_method": "COD",
  "shipping_address": {
    "recipient_name": "Guest Buyer",
    "recipient_phone": "0912345678",
    "address_line": "1 Nguyen Trai, Ha Noi"
  }
}
```

Existing guest cart remains available without login through Gateway /commerce:

| Method and Commerce path | Existing behavior |
| --- | --- |
| GET /api/v1/cart?guest_session_key=... | View guest cart |
| POST /api/v1/cart/items | Add item using guest_session_key in the existing body |
| PUT /api/v1/cart/items/{cartItemId} | Update item using the existing body |
| DELETE /api/v1/cart/items/{cartItemId}?guest_session_key=... | Remove item |
| DELETE /api/v1/cart/items?guest_session_key=... | Clear cart |
| POST /api/v1/cart/merge | Merge guest cart after login; requires authentication |

Khách đăng nhập tiếp tục dùng GET /api/v1/checkout/prices, POST /api/v1/orders và /api/v1/orders/{orderId}/payment-attempt với shipping_address_id. Dùng collection StyleMind-Commerce-Payment-Handoff hiện có cho luồng tài khoản.

## Migration và bằng chứng

Chủ dự án xác nhận V9 guest đã chạy: giữ nguyên `V9__support_guest_checkout.sql`. Migration image/inventory chưa chạy được đổi từ V9 sang V10, SQL không đổi; Payment giữ `V2__support_guest_payment.sql`. Agent chỉ kiểm tra trên PostgreSQL dùng một lần, không chạy Compose hoặc migration vào database của chủ dự án.

Kiểm tra cấu trúc collection JSON/script được báo riêng với kiểm thử HTTP/integration. Không coi việc import JSON là đã chạy request. Kết quả và giới hạn runtime nằm trong `docs/qa/GUEST_CHECKOUT_RESTORATION_REPORT.md`.


## Guest cart headers (FE integration)

The owner approved header-based cart access. GET `/api/v1/cart`, DELETE `/api/v1/cart/items/{cartItemId}` and DELETE `/api/v1/cart/items` accept `X-Guest-Session-Key` without a query string. Legacy `guest_session_key` query clients remain compatible. Conflicting header/query keys on guest requests are rejected. Authenticated ownership is unchanged. POST/PUT retain their existing strict JSON body, including `guest_session_key`; FE also supplies the header. Never log keys. Use an actual ACTIVE variant UUID from the existing public catalog, not an FE demo ID. Restart/rebuild Commerce to activate this change; no migration or security matcher changes are needed.
