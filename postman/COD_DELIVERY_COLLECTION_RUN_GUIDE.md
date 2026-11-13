# Thu COD khi giao hàng và mở quyền trả hàng

Shop tự giao. Admin xác nhận giao thành công nghĩa là đã thu đủ COD.

## Thao tác

- FE quản trị → Đơn hàng → **Giao thành công và thu COD** gọi Commerce chuyển từ SHIPPED sang COMPLETED. Commerce lưu thời điểm giao và ý định ghi nhận thu, kèm admin, trong cùng transaction.
- Sau commit, Commerce gọi Payment ngay. Nếu Payment tạm gián đoạn, kết quả giao vẫn được lưu và `payment_status` tiếp tục là PENDING. Commerce tự quét lại các ý định đã lưu mỗi 15 giây, kể cả khi tiến trình khởi động lại.
- Payment dùng khoản COD hiện có, kiểm tra lại số tiền, chủ đơn, phương thức và trạng thái từ Commerce. Giao dịch, admin thực hiện và PaymentConfirmed outbox được lưu trong cùng transaction. Lệnh đồng thời và replay trả cùng khoản thanh toán.
- Commerce tiếp nhận PaymentConfirmed qua inbox rồi hiển thị `payment_status` PAID. `order_status` COMPLETED thể hiện giao hàng. Khách tải lại trang để thấy trạng thái đồng bộ mới nhất.
- Sau khi khoản thu được xác nhận, khách đủ điều kiện trong 7 ngày từ ngày giao có thể tạo yêu cầu trả hàng.

## API

Import `StyleMind-COD-Delivery-Collection.postman_collection.json`. Điền gateway_url, order_id và token admin/khách trong môi trường riêng. Dùng đơn COD đã SHIPPED, có khoản thanh toán COD ban đầu. Chỉ xác nhận khi đã giao và thực thu đủ tiền.

`PUT /commerce/api/v1/admin/orders/{orderId}/status` với body `{"order_status":"COMPLETED","reason":"Delivered and cash received"}` trả 200 với order_status COMPLETED và payment_status PENDING hoặc PAID. Replay COD COMPLETED trả cùng kết quả và giữ nguyên lịch sử giao. 401 khi chưa đăng nhập, 403 khi thiếu quyền admin, 409 khi trạng thái chuyển không hợp lệ hoặc có yêu cầu hủy PENDING với mã CANCELLATION_PENDING.

`GET /commerce/api/v1/orders/{orderId}` cho khách xem đơn của mình. Dùng token khách khác phải nhận 404. Sau phục hồi, tải lại đến khi payment_status PAID. Trong lúc Payment gián đoạn, đơn vẫn COMPLETED và PENDING.

`GET /commerce/api/v1/orders/{orderId}/returns` thể hiện quyền trả hàng sau xác nhận khoản thu. Các lý do bao gồm PAYMENT_NOT_CONFIRMED, NOT_DELIVERED, RETURN_WINDOW_EXPIRED và NO_RETURNABLE_ITEMS.

Transport nội bộ dùng `POST /internal/v1/payments/orders/{orderId}/cod-collection`, không body, với X-StyleMind-Service-Token theo cấu hình handoff-token hiện có. Payment lấy snapshot được xác thực từ Commerce và yêu cầu có admin/thời điểm của ý định giao đã commit. Thiếu token hoặc token sai trả 401, thiếu ý định giao hoặc snapshot không khớp trả 409. Thư mục internal trong collection chỉ dùng với runtime cô lập và địa chỉ Payment thực tế. Điều chỉnh payment_base_url theo cổng runtime của bạn, điền handoff_token trong môi trường riêng.

API Payment admin cũ `POST /payment/api/v1/admin/orders/{orderId}/cod-collection` vẫn tương thích cho các client hiện có. Đơn COD COMPLETED lịch sử thiếu ý định giao mới chỉ được thu qua luồng xác nhận admin cũ sau khi đã kiểm tra thực nhận tiền.

## Cơ chế phục hồi

Lịch sử chuyển SHIPPED → COMPLETED có reason bắt đầu bằng `COD delivery and collection requested` là ý định bền vững. Nội dung reason của admin được giữ phía sau dấu phân cách. Scanner chọn những đơn COD COMPLETED có ý định này và còn thiếu inbox APPLIED khớp chủ đơn, phương thức và số tiền. Scanner tiếp tục qua từng trang để lỗi của một đơn vẫn cho phép các đơn sau được xử lý. Payment dùng khóa theo order và khoản COD ban đầu để ngăn thu trùng. Outbox Payment hiện có tiếp tục giao sự kiện sau lỗi hoặc khởi động lại.

Tham số `stylemind.commerce.cod.collection-scan-ms` mặc định 15000. Commerce dùng cấu hình payment.base-url/payment.handoff-token hiện có. Payment dùng Commerce snapshot endpoint và credential hiện có. Chỉ áp dụng các migration nguồn hiện có cho database test dùng một lần.

## Kiểm thử và môi trường

- CodDeliveryPostgresTests dùng PostgreSQL cô lập để kiểm tra giao hàng khi Payment lỗi, tái tạo worker rồi phục hồi từ DB, trạng thái PENDING → PAID, đồng thời, rollback, chặn yêu cầu hủy và phân biệt đơn lịch sử/online.
- PaymentClientHttpTests kiểm tra transport HTTP, header dịch vụ, lỗi và replay từ dispatcher.
- OrderPaymentPostgresIntegrationTests kiểm tra nhiều lệnh thu đồng thời chỉ ghi một giao dịch và một outbox, bao gồm luồng internal và snapshot sai chủ đơn.
- PaymentCreationHttpPostgresTests kiểm tra HTTP thật với PostgreSQL cô lập, service token, admin API cũ, ý định giao, replay và audit admin.
- Collection được kiểm tra cấu trúc JSON riêng. Kết quả automated HTTP nằm trong các suite nêu trên. Thực thi Postman trên runtime của người dùng là bước riêng.

Các lần kiểm thử của thay đổi này chỉ dùng database PostgreSQL tạm trong target/isolated-postgres và server HTTP test. Cấu hình Compose và database đang dùng do chủ dự án quản lý.
