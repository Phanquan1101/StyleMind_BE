# Payment API cho Frontend

Tài liệu đối chiếu source hiện tại, ngày 2026-10-30. Đây là hướng dẫn tích hợp API đã có, không phê duyệt thêm business rule. Chưa chạy HTTP/runtime trong lần bàn giao này.

## URL và xác thực

- Gateway mặc định: `http://localhost:8080`.
- Swagger UI: `http://localhost:8080/swagger-ui.html`, chọn Payment Service hoặc Commerce Service.
- Payment OpenAPI: `http://localhost:8080/payment/v3/api-docs`.
- Gửi `Authorization: Bearer <access_token>`; request có JSON gửi `Content-Type: application/json`.
- Phải giữ prefix `/commerce` hoặc `/payment`. Gateway hiện chuyển `/api/**` không prefix sang Identity.
- Khi FE chạy khác origin, có thể dùng dev-server proxy tới Gateway để giữ cùng origin. Chưa kiểm thử CORS/browser trong lần bàn giao này.

## API dùng cho màn hình thanh toán

| Method | Path qua Gateway | Quyền / kết quả |
| --- | --- | --- |
| POST | `/commerce/api/v1/orders` | Checkout từ cart của người dùng; trả Order, HTTP 201 |
| POST | `/commerce/api/v1/orders/{orderId}/payment-attempt` | CUSTOMER/ADMIN, chỉ Order của chính JWT subject; trả attempt, HTTP 200 |
| GET | `/commerce/api/v1/orders/{orderId}` | Đọc Order của người dùng; dùng theo dõi trạng thái Order |
| POST | `/payment/api/v1/admin/cod-collections` | ADMIN ghi nhận tiền COD đã thực thu, HTTP 200 |
| GET | `/payment/api/v1/admin/payment-exceptions?limit=50&offset=0` | ADMIN xem danh sách lỗi, HTTP 200 |

### 1. Checkout và lấy QR

Chuẩn bị cart có sản phẩm và địa chỉ thuộc tài khoản. Body checkout ví dụ:

```json
{
  "checkout_key": "checkout-unique-key",
  "shipping_address_id": "00000000-0000-4000-8000-000000000001",
  "payment_method": "SEPAY_VIETQR"
}
```

Dùng `order_id` trong response để gọi POST `/commerce/api/v1/orders/{orderId}/payment-attempt`. Request này **không có body**, kể cả `{}` cũng bị từ chối. Không gửi số tiền hoặc customer ID; backend lấy từ Order đã lưu. Cả Admin cũng chỉ lấy được attempt của Order do chính mình sở hữu. Order không tồn tại hoặc thuộc người khác cùng trả 404.

Response attempt có các field sau (giá trị minh họa):

```json
{
  "id": "00000000-0000-4000-8000-000000000002",
  "orderId": "00000000-0000-4000-8000-000000000003",
  "method": "SEPAY_VIETQR",
  "amountVnd": 125000,
  "reference": "SM0123456789AB",
  "status": "PENDING",
  "expiresAt": "2026-10-30T08:15:00Z",
  "transferContent": "SM0123456789AB",
  "qrPayload": "https://vietqr.app/img/..."
}
```

- `qrPayload` là URL ảnh QR, FE có thể gán vào `img.src`. URL ảnh/provider chưa được kiểm tra live trong lần bàn giao này.
- Hiển thị `amountVnd`, `transferContent` và đếm ngược tới `expiresAt`. Dùng nguyên giá trị backend trả về.
- SePay attempt có thời hạn 15 phút kể từ lúc tạo. Recovery dùng lại key `initial-<orderId>`, không tạo QR mới mỗi lần gọi.
- Attempt API dùng camelCase; Order API dùng các field như `order_id`, `order_status`, `accepted_total_vnd`.
- Trạng thái attempt được map ra API: `PENDING`, `PAID`, `EXPIRED`, `CANCELLED`, `EXCEPTION`. Đây là vocabulary, không có nghĩa mọi trạng thái đều đọc được qua recovery ở mọi trạng thái Order.

### 2. Xác nhận chuyển khoản

FE không gọi webhook hoặc tự đánh dấu đã trả tiền. SePay callback hợp lệ được backend xử lý; PaymentConfirmed được gửi bất đồng bộ sang Commerce khi dispatcher được cấu hình bật.

FE có thể đọc lại GET `/commerce/api/v1/orders/{orderId}` để cập nhật `order_status`. Với SePay, xác nhận được áp dụng khi Order còn `PENDING_PAYMENT`, chuyển thành `PAID`. Việc chuyển trạng thái phụ thuộc callback và event delivery; đóng cửa sổ QR hoặc hết countdown không chứng minh kết quả giao dịch.

Không dùng POST recovery làm API polling trạng thái tổng quát: khi SePay Order đã PAID, recovery trả `409 ORDER_NOT_PAYABLE`. Không có GET `/api/v1/payments/attempts/{id}` dù Location từ API tạo attempt có dạng đường dẫn đó. Luồng tạo QR mới sau expiry còn **TBD**.

### 3. COD

Checkout dùng `payment_method: "COD"`. Sau khi Payment xác nhận đã lưu nghĩa vụ thanh toán, COD Order chuyển `PENDING_PAYMENT` thành `PLACED`; đây là xác nhận đơn hàng. Attempt vẫn `PENDING`, `expiresAt`, `transferContent`, `qrPayload` là null.

Chỉ Admin ghi nhận đã thực thu qua POST `/payment/api/v1/admin/cod-collections`:

```json
{
  "payment_attempt_id": "00000000-0000-4000-8000-000000000002",
  "collected_amount_vnd": 125000,
  "collection_reference": "receipt-demo"
}
```

`payment_attempt_id` và `collected_amount_vnd` bắt buộc. Số tiền phải là JSON integer dương, bằng đúng nghĩa vụ thanh toán. `collection_reference` tùy chọn/null, tối đa 255 ký tự, không chứa control characters. `collected_at` tùy chọn/null, ISO instant và không được ở tương lai; bỏ qua thì dùng thời điểm server. Unknown fields bị từ chối.

Response dùng cùng cấu trúc attempt, `status: "PAID"`. Gọi lại bị 409; không dùng thao tác thu tiền làm retry đọc trạng thái. COD Order tiếp tục trạng thái fulfilment riêng, không đổi thành PAID. Chưa có API public đọc trạng thái thu COD tổng quát hoặc tìm attempt của mọi Order cho Admin; không suy diễn `PLACED` là đã thu tiền.

### 4. Danh sách lỗi cho Admin

GET `/payment/api/v1/admin/payment-exceptions?status=OPEN&limit=50&offset=0`. `status` tùy chọn, `limit` từ 1–100, `offset` >= 0. Response: `{ "items": [], "limit": 50, "offset": 0 }` khi rỗng; không có total.

Mỗi item có `exception_id`, `payment_attempt_id`, `provider_name`, `provider_event_id`, `received_reference`, `received_amount_vnd`, `exception_reason`, `reconciliation_status`, `reviewed_by_user_id`, `detected_at`. Một số field có thể null. Chưa có API resolve exception/refund.

## Lỗi và giới hạn tích hợp

Payment trả Problem Details với `status`, `title`, `detail`, `code`, `instance`, `timestamp` và có thể có `correlationId`. FE nên xử lý theo HTTP status và `code`.

- 400 `INVALID_REQUEST`: input sai, amount không phải integer hoặc thời điểm thu ở tương lai.
- 401: thiếu hoặc sai access JWT. 403 `ACCESS_DENIED`: không có quyền Admin khi gọi API Admin.
- 409 khi thu COD: `PAYMENT_METHOD_MISMATCH`, `PAYMENT_ATTEMPT_NOT_PENDING`, `PAYMENT_AMOUNT_MISMATCH`, `PAYMENT_ALREADY_CONFIRMED`.
- Commerce recovery: 400 nếu có body, 404 nếu thiếu/khác chủ Order, 409 `ORDER_NOT_PAYABLE` nếu Order không còn trong trạng thái được hỗ trợ. Lỗi kết nối Payment cần xử lý như upstream failure, không checkout lại một Order đã lưu.

Endpoint cũ POST `/payment/api/v1/payments/attempts` tồn tại nhưng phụ thuộc GET `/internal/v1/orders/{orderId}/payment-snapshot` chưa được Commerce triển khai. Luồng FE hiện dùng Commerce recovery ở trên. `/internal/v1/payments/order-attempts` và `/internal/v1/sepay/webhooks` dành cho backend/provider; không đưa service credential vào FE.

## Postman và cách chạy

Import các collection hiện có:

- [SePay checkout/recovery](StyleMind-Commerce-Payment-Handoff.postman_collection.json), [hướng dẫn](US-04.01_CREATE_SEPAY_PAYMENT_RUN_GUIDE.md).
- [SePay confirmation](StyleMind-US-04.02-Confirm-Payment.postman_collection.json), [hướng dẫn](US-04.02_CONFIRM_PAYMENT_RUN_GUIDE.md).
- [COD và Admin exceptions](StyleMind-COD-Two-Step.postman_collection.json), [hướng dẫn hiện tại](COD_TWO_STEP_RUN_GUIDE.md).

Điền token/fixture IDs vào local values. Dùng `commerceUrl=http://localhost:8080/commerce`, `paymentUrl=http://localhost:8080/payment`. Chạy checkout rồi recovery; dùng đúng loại fixture SePay/COD. Các request thu COD ghi dữ liệu tài chính, chỉ chạy với fixture phát triển khi thử nghiệm. Các hướng dẫn story cũ mô tả phạm vi ở thời điểm bàn giao; scope COD delivery hiện tại theo contract US-04.03.

Kiểm tra cấu trúc collection từ repository root:

```powershell
node scripts/validate-handoff-postman.cjs
node scripts/validate-us0402-postman.cjs
node scripts/validate-cod-postman.cjs
git diff --check
```

Các lệnh validator kiểm JSON/template và cú pháp script, không gửi HTTP. Tài liệu này không xác nhận service đang chạy, migrations đã áp dụng, live bank/provider, CORS hay production readiness. Lần bàn giao chỉ thêm file hướng dẫn này; không đổi source/schema/env/database. Workbook `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx` không tồn tại ở môi trường hiện tại nên chưa đối chiếu được hàng Backlog.

Nguồn: các controller/DTO/security/service trong `payment-service`, Commerce `OrderPaymentController`, Gateway `application.properties`; contract [SePay](../docs/planning/US-04.02_CONFIRM_PAYMENT_CONTRACT.md), [COD](../docs/planning/COD_TWO_STEP_CONTRACT.md), [COD delivery](../docs/planning/US-04.03_COD_COLLECTION_CONTRACT.md). Source mô tả hành vi quan sát được; quyết định business còn thiếu giữ nguyên TBD.
