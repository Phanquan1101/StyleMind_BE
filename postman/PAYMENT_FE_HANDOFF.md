# Yêu cầu triển khai luồng Payment trên FE

Tài liệu bàn giao ngày 03/11/2026, đối chiếu code BE đã push:

- Guest checkout: nhánh `feature/guest-checkout`, commit `52622ea`.
- Payment JPA/mapper: nhánh `refactor/payment-jpa-mapper`, commit `f5c8130`.
- Nhánh guest kế thừa commit Payment. BE phục vụ luồng guest phải có code nhánh guest và các migration tương ứng.

Hãy triển khai trong repository FE dựa trên các API dưới đây. Tận dụng cấu trúc API client, auth, router và component đang có. Không sửa BE, không dùng dữ liệu demo thay kết quả thanh toán thật, không tự thêm API hoặc business rule còn thiếu.

## 1. Luồng tổng thể

```text
Giỏ hàng đã lưu trên BE
  → lấy giá hiện tại
  → chọn thông tin giao hàng và COD/SePay
  → POST tạo Order
  → lưu order_id
  → COD: hiển thị kết quả xác nhận đơn
  → SePay: lấy Payment attempt → hiển thị QR → theo dõi Order
```

Order và Payment attempt là hai dữ liệu khác nhau. Order dùng `snake_case`; Payment attempt dùng `camelCase`.

## 2. URL và xác thực

Gateway mặc định: `http://localhost:8080`. Cho phép cấu hình base URL theo môi trường của FE.

| Thao tác | Đơn tài khoản | Đơn guest |
| --- | --- | --- |
| Lấy giá | `GET /commerce/api/v1/checkout/prices` | `GET /commerce/api/v1/guest/checkout/prices` |
| Tạo đơn | `POST /commerce/api/v1/orders` | `POST /commerce/api/v1/guest/orders` |
| Lấy attempt/QR hoặc khôi phục handoff | `POST /commerce/api/v1/orders/{orderId}/payment-attempt` | `POST /commerce/api/v1/guest/orders/{orderId}/payment-attempt` |
| Đọc đơn/theo dõi trạng thái | `GET /commerce/api/v1/orders/{orderId}` | `GET /commerce/api/v1/guest/orders/{orderId}` |

- Đơn tài khoản: gửi `Authorization: Bearer <access_token>` theo cơ chế auth hiện có.
- Đơn guest: gửi `X-Guest-Session-Key: <guest_key>`.
- Chọn API theo chủ sở hữu của đơn. Đơn guest vẫn dùng API guest khi người mua đăng nhập sau đó; không có tự động gắn đơn vào tài khoản.
- Khi gửi JSON, dùng `Content-Type: application/json`.
- Giữ prefix `/commerce` và `/payment`. Không gọi các API Commerce/Payment bằng `/api/...` thiếu prefix qua Gateway.
- Không ghi token, guest key, địa chỉ hoặc payload chứa dữ liệu riêng tư vào console/analytics.

## 3. Giỏ guest và session key

- Sinh key một lần bằng `crypto.randomUUID()`, lưu và dùng lại qua refresh. Không sinh key mới mỗi lần render hoặc mở checkout.
- Key phải dài 16–128 ký tự, chỉ gồm chữ, số và `._:-`.
- Dùng cùng key khi thêm món vào giỏ và khi checkout. Checkout cần giỏ đã có trên BE; chỉ có localStorage chứa danh sách món là chưa đủ.
- Giỏ guest dùng API `/commerce/api/v1/cart` và `/commerce/api/v1/cart/items` hiện có. GET/DELETE hỗ trợ header `X-Guest-Session-Key`; POST/PUT giữ body có `guest_session_key` và các field bắt buộc của API giỏ.
- Không gửi Bearer token từ interceptor vào thao tác giỏ guest: khi có JWT hợp lệ, BE ưu tiên giỏ tài khoản. Tránh thêm món vào giỏ tài khoản rồi gọi checkout guest.
- Không đưa guest key vào URL trang, log hoặc analytics. Ưu tiên header khi đọc/xóa giỏ.
- Lưu `order_id` cùng loại chủ sở hữu đơn; guest giữ key để xem lại đơn/lấy lại thanh toán. Không lưu toàn bộ địa chỉ giao hàng lâu dài chỉ để khôi phục màn hình.

## 4. Lấy giá và tạo Order

Lấy giá từ endpoint tương ứng. Response chứa `items` và `total_vnd`; hiển thị giá do server trả về, không lấy tổng tiền từ cart demo/local.

Tài khoản dùng địa chỉ đã lưu của người dùng:

```json
{
  "checkout_key": "b5f1d5ce-85e0-477a-943b-53c5bd9014a7",
  "shipping_address_id": "cb9fa53c-47fc-46a8-af91-cf82cb982354",
  "payment_method": "SEPAY_VIETQR"
}
```

Guest nhập thông tin giao hàng trực tiếp, không gọi Identity để tạo tài khoản/địa chỉ:

```json
{
  "checkout_key": "b5f1d5ce-85e0-477a-943b-53c5bd9014a7",
  "payment_method": "SEPAY_VIETQR",
  "shipping_address": {
    "recipient_name": "Nguyen Van A",
    "recipient_phone": "0912345678",
    "address_line": "1 Nguyen Trai, Ha Noi"
  }
}
```

- `payment_method` chỉ nhận `COD` hoặc `SEPAY_VIETQR`.
- `checkout_key`: chuỗi 8–128 ký tự gồm chữ, số, `._:-`; UUID phù hợp. Giữ cùng key cho cùng một lần đặt hàng.
- Tên guest: bắt buộc, trim khoảng trắng Unicode, 1–100 code point.
- SĐT guest: bắt buộc, trim, 1–64 code point; giữ định dạng người dùng nhập. Không kiểm tra đầu số/quốc gia, không đổi sang E.164 và không OTP.
- Địa chỉ guest: bắt buộc, trim, 1–500 code point.
- Các chuỗi giao hàng không chứa control characters. Không gửi field dư như `customer_id`, `amount`, `order_status` hoặc `source_address_id`.
- Không gửi giá/tổng tiền trong body checkout; BE tự tính.
- Khóa nút đặt hàng khi đang gửi request để tránh gửi trùng.

Tạo đơn thành công trả HTTP `201` với Order, gồm `order_id`, `order_status`, `payment_method`, `accepted_total_vnd`, địa chỉ và các dòng hàng. Guest có `customer_id: null`.

Ngay khi nhận Order, lưu `order_id`, chuyển sang trang kết quả/thanh toán và tải lại giỏ từ BE. Nếu Payment tạm lỗi, Order vẫn có thể đã được lưu với trạng thái `PENDING_PAYMENT`.

## 5. Lấy Payment attempt

Gọi endpoint `POST .../{orderId}/payment-attempt` ở bảng API.

**Không gửi body, kể cả `{}`.** Không gửi số tiền hoặc customer ID. BE lấy snapshot từ Order và dùng request key cố định `initial-<orderId>`.

Ví dụ response HTTP `200` của SePay, các giá trị chỉ minh họa:

```json
{
  "id": "797af430-cc64-4571-b112-8eb6f57e7046",
  "orderId": "20db2ac7-36cc-430a-8a48-3cdc45eb2b15",
  "method": "SEPAY_VIETQR",
  "amountVnd": 125000,
  "reference": "SM0123456789AB",
  "status": "PENDING",
  "expiresAt": "2026-11-03T10:15:00Z",
  "transferContent": "SM0123456789AB",
  "qrPayload": "https://vietqr.app/img?acc=demo&bank=demo&amount=125000&des=SM0123456789AB"
}
```

Lưu `id` của attempt khi cần sử dụng tiếp. Không nhầm `id` của attempt với `orderId` hoặc `order_id`.

## 6. COD

- Chọn `payment_method: "COD"` khi checkout.
- COD Order bắt đầu `PENDING_PAYMENT`; sau khi Payment xác nhận đã lưu attempt, Order chuyển sang `PLACED`.
- Với `PLACED`, hiển thị “Đặt hàng thành công — thanh toán khi nhận hàng”. Đây chưa phải xác nhận đã thu tiền.
- Attempt vẫn `PENDING`; `expiresAt`, `transferContent`, `qrPayload` là `null`. Không hiển thị QR/countdown.
- Nếu Order còn `PENDING_PAYMENT`, hiển thị trạng thái đang chờ xác nhận và nút thử lại endpoint lấy attempt. Sau thành công, GET lại Order để cập nhật `PLACED`.
- Khách hàng không có nút tự đánh dấu COD đã thanh toán.

## 7. SePay/VietQR

- Chọn `payment_method: "SEPAY_VIETQR"` khi checkout.
- Sau khi có `order_id`, lấy attempt bằng endpoint Commerce.
- Gán `qrPayload` vào ảnh QR; đây là URL ảnh do BE cung cấp. Không tự tính số tiền, nội dung chuyển khoản hoặc dựng QR bằng dữ liệu local.
- Hiển thị `amountVnd`, `transferContent` và countdown theo `expiresAt`. Attempt online có thời hạn 15 phút từ lúc tạo; dùng timestamp BE trả về.
- Nếu attempt trả `EXPIRED`, không hiển thị QR như QR còn hiệu lực. `PAID` thì GET lại Order để đồng bộ kết quả; `CANCELLED`/`EXCEPTION` thì dừng hướng dẫn thanh toán và hiển thị thông báo phù hợp.
- Nếu cần nút “Tôi đã chuyển khoản”, nút đó chỉ tải lại trạng thái Order; không tự đổi trạng thái thanh toán.

Theo dõi bằng **GET Order**, có thể polling mỗi 3–5 giây khi trang đang mở:

| `order_status` | Cách hiển thị/xử lý |
| --- | --- |
| `PENDING_PAYMENT` | Chờ xác nhận; tiếp tục polling trong khoảng chờ hợp lý |
| `PAID` | Thanh toán đã được xác nhận; dừng polling thanh toán, hiển thị thành công |
| `PROCESSING`, `SHIPPED`, `COMPLETED` | Đơn đã chuyển sang xử lý/giao hàng; dừng màn hình QR và mở chi tiết đơn |
| `CANCELLED` | Dừng thanh toán/polling, hiển thị đơn đã hủy |

Hủy timer/request khi rời trang hoặc đổi Order, tránh nhiều vòng polling và request chồng nhau. Có giới hạn thời gian chờ để tránh polling vô hạn. Hết thời gian chờ chỉ có nghĩa chưa nhận được xác nhận, không chứng minh giao dịch thất bại.

FE không gọi webhook SePay hoặc tự ghi nhận đã thanh toán. Backend phải cấu hình callback và bật event delivery Payment → Commerce thì trạng thái Order mới được cập nhật. Có thể có độ trễ giữa Payment nhận tiền và Commerce cập nhật Order.

QR hết hạn không chứng minh người mua chưa chuyển tiền. Hiện chưa có luồng đã duyệt để tạo QR mới sau expiry; gọi recovery vẫn dùng request key/attempt ban đầu. Không tự tạo attempt mới hoặc tạo Order mới để thay QR hết hạn.

## 8. Reload, lỗi mạng và lỗi API

- Reload trang: đọc lại Order bằng `order_id` và đúng credential. Chỉ lấy attempt khi Order còn ở trạng thái hỗ trợ thanh toán; không dựa hoàn toàn vào trạng thái lưu local.
- Payment/recovery lỗi mạng: giữ Order và cho phép thử lại trên cùng `order_id`; không checkout lại.
- POST tạo Order timeout khi FE chưa nhận được `order_id`: giữ checkout key và báo chưa xác định kết quả. Guest hiện chưa có API tìm Order bằng checkout key. Không tự sinh key mới rồi gửi thêm Order.
- Không dùng POST recovery làm API polling tổng quát. Khi SePay Order đã `PAID`, recovery có thể trả `409 ORDER_NOT_PAYABLE`; GET lại Order để xử lý.

Problem Details có các field như `status`, `title`, `detail`, `code`, `instance`, `timestamp`, `correlationId`. Xử lý theo status/code, hiển thị thông báo tiếng Việt phù hợp.

| Lỗi | FE xử lý |
| --- | --- |
| `400 INVALID_REQUEST` | Kiểm tra input/body; recovery không được có body |
| `401` với API tài khoản | Dùng luồng refresh/login hiện có |
| `401 UNAUTHENTICATED` với API guest | Guest key thiếu/sai định dạng; kiểm tra key đã lưu, không tự thay bằng key mới |
| `403` | Không đủ quyền; không lặp request như lỗi mạng |
| `404 RESOURCE_NOT_FOUND` | Đơn không tồn tại hoặc không thuộc credential hiện tại |
| `409 EMPTY_CART` | Tải lại giỏ BE, kiểm tra đã có món và cùng key/chủ sở hữu |
| `409 DUPLICATE_CHECKOUT` | Không sinh key mới để đặt lại; tải Order đã biết nếu có, còn không thì báo kết quả chưa xác định |
| `409 ORDER_NOT_PAYABLE` | GET lại Order, cập nhật UI theo trạng thái thật |
| `409 INSUFFICIENT_STOCK` | Tải lại giỏ/giá và yêu cầu người dùng điều chỉnh số lượng |
| `PAYMENT_UNAVAILABLE`, `PAYMENT_DELIVERY_UNKNOWN`, lỗi mạng/5xx | Giữ Order đã biết và cho phép thử lại thanh toán; không tạo Order mới |

## 9. Admin ghi nhận thu COD

Chỉ triển khai nếu phạm vi FE có màn hình Admin thu tiền. API này dành cho tiền đã thực thu:

```http
POST /payment/api/v1/admin/cod-collections
Authorization: Bearer <admin_access_token>
Content-Type: application/json
```

```json
{
  "payment_attempt_id": "797af430-cc64-4571-b112-8eb6f57e7046",
  "collected_amount_vnd": 125000,
  "collection_reference": "receipt-demo"
}
```

- `payment_attempt_id` và `collected_amount_vnd` bắt buộc. Số tiền là JSON integer dương, khớp nghĩa vụ thanh toán.
- `collection_reference` tùy chọn/null, tối đa 255 ký tự, không có control characters.
- `collected_at` tùy chọn/null, ISO instant và không ở tương lai; bỏ qua thì dùng thời gian server.
- Response attempt thành công có `status: "PAID"`. Gọi lại có thể bị 409; không dùng API thu tiền để đọc trạng thái.
- COD Order vẫn giữ trạng thái xử lý/giao hàng riêng, không tự chuyển `order_status` sang `PAID` vì thu COD.
- Hiện chưa có API tổng quát cho Admin tìm attempt của mọi Order hoặc đọc trạng thái thu COD cho mọi đơn. Không suy diễn attempt ID từ order ID hoặc từ `PLACED`; nếu màn hình cần chức năng đó, ghi rõ phần BE còn thiếu.

## 10. API không dùng cho luồng FE này

- Không gọi `/internal/**` hoặc đưa service credential vào FE.
- Không gọi webhook để giả lập người mua đã chuyển tiền.
- Không có `GET /api/v1/payments/attempts/{id}` để polling, dù Location của API tạo attempt cũ có dạng đường dẫn đó.
- Endpoint cũ `POST /payment/api/v1/payments/attempts` tồn tại nhưng phụ thuộc một snapshot lookup chưa được Commerce triển khai. Luồng này dùng Commerce `.../payment-attempt` ở trên.
- Không tự thêm guest list/cancellation, gắn đơn guest vào tài khoản, refund, resolve payment exception hoặc tạo QR mới sau expiry.

## 11. Tiêu chí hoàn thành FE

- Checkout tài khoản và guest dùng đúng API/credential; guest không bị ép đăng nhập.
- Guest dùng cùng một session key từ giỏ đến xem đơn và lấy lại thanh toán.
- Tạo đơn thành công luôn lưu `order_id`; refresh và quay lại trang vẫn xem được đúng đơn.
- COD hiển thị xác nhận đơn nhưng không đánh dấu đã thu tiền.
- SePay hiển thị đúng QR/số tiền/nội dung/countdown từ BE và cập nhật theo GET Order.
- Trường hợp timeout/reload/expiry/401/404/409/5xx không gây tạo thêm đơn hoặc báo thanh toán thành công giả.
- Đơn guest vẫn xem lại bằng credential guest sau khi đăng nhập.
- Có test FE cho COD, SePay, recovery, polling cleanup, guest key không khớp và chống gửi trùng. Phân biệt test mock với HTTP/runtime thật khi báo kết quả.
- Báo file đã sửa, lệnh kiểm tra/kết quả và phần bị chặn bởi API chưa có. Không dùng dữ liệu mock để che lỗi kết nối BE trong luồng thật.

## 12. Tài liệu và Postman

Guest collection đã được push trong nhánh `feature/guest-checkout`:

- `postman/StyleMind-Guest-Checkout.postman_collection.json`
- `postman/GUEST_CHECKOUT_RUN_GUIDE.md`
- `docs/qa/GUEST_CHECKOUT_BRANCH_REPORT.md`

Collection Payment hiện có:

- `postman/StyleMind-Commerce-Payment-Handoff.postman_collection.json`
- `postman/StyleMind-US-04.02-Confirm-Payment.postman_collection.json`
- `postman/StyleMind-COD-Two-Step.postman_collection.json`

Các ví dụ ID/QR/thời gian trong tài liệu chỉ minh họa; FE dùng giá trị thật BE trả về. File này là hướng dẫn triển khai FE theo hợp đồng hiện có, không phải bằng chứng đã chạy FE/browser hay thanh toán live.
