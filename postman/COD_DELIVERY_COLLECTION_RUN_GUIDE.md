# Thu COD khi giao hàng và mở quyền trả hàng

Nghiệp vụ đã chốt: shop tự giao; admin xác nhận giao thành công nghĩa là đã thu đủ COD.

## Thao tác

- FE quản trị → Đơn hàng: nút **Giao thành công và thu COD** lưu trạng thái COMPLETED, sau đó gọi Payment ghi nhận thu trên chính đơn đó.
- Đơn cũ đã COMPLETED nhưng chưa ghi nhận thu: admin kiểm tra đã nhận tiền rồi bấm **Xác nhận đã thu COD** tại danh sách đơn hàng.
- Nếu bước thu bị lỗi sau khi giao hàng đã lưu, FE báo rõ kết quả từng bước; tải lại danh sách và dùng nút xác nhận thu. Không tạo đơn hay khoản thanh toán khác.
- Payment dùng khoản COD hiện có, kiểm tra lại số tiền/chủ đơn/phương thức/trạng thái từ Commerce, ghi người admin thực hiện, lưu giao dịch và outbox trong cùng transaction. Bấm lại trả cùng kết quả, không thu trùng.
- Sau khi outbox PaymentConfirmed được Commerce tiếp nhận (thường khoảng 10 giây nếu dịch vụ đang chạy), khách tải lại trang. Đơn đủ điều kiện trong 7 ngày sẽ có **Yêu cầu trả hàng**.

## API và giới hạn

Import `StyleMind-COD-Delivery-Collection.postman_collection.json`. Điền gateway_url, order_id và token của admin/khách trong môi trường riêng.

`POST /payment/api/v1/admin/orders/{orderId}/cod-collection`: không body; admin bắt buộc. Trả 200 với status PAID; gọi lại không ghi thêm giao dịch. 401 chưa đăng nhập, 403 không phải admin, 409 chưa giao/chưa có khoản COD hoặc snapshot không khớp.

`GET /commerce/api/v1/orders/{orderId}/returns`: bổ sung `ineligible_reason`: PAYMENT_NOT_CONFIRMED, NOT_DELIVERED, RETURN_WINDOW_EXPIRED, NO_RETURNABLE_ITEMS hoặc null khi đủ điều kiện.

Không thay schema, không tự ghi thu cho các đơn cũ, không sửa DB đang dùng. Gọi trực tiếp API Commerce chuyển COMPLETED bên ngoài FE vẫn cần gọi API thu COD nêu trên. Hai dịch vụ không có transaction chung; bước thu có thể thử lại an toàn. Không bỏ kiểm tra khoản thu để cho hoàn tiền khi chưa nhận tiền.

## Kiểm thử

- PaymentWorkflowServiceTests: dùng lại khoản gốc, replay, từ chối chưa giao/sai tiền/thiếu khoản thanh toán.
- OrderPaymentPostgresIntegrationTests: nhiều lệnh xác nhận đồng thời chỉ có một giao dịch và một outbox.
- PaymentCreationHttpPostgresTests: HTTP thật với PostgreSQL cô lập; 401/403/409, thu thành công, replay và audit admin.
- ReturnPostgresIntegrationTests: quyền truy cập, lý do thiếu khoản thu, hạn 7 ngày và trả hàng.
- FE: AdminCancellationActions, OrderReturnLive, paymentApi; typecheck/build.
- Collection được kiểm tra JSON; chưa chạy trên khoản tiền thật.

## File thay đổi

BE Payment: PaymentAttemptRepository.java, PaymentAttemptService.java, PaymentAttemptServiceImpl.java, PaymentAttemptController.java và ba lớp test Payment nêu trên.

BE Commerce: ReturnResponse.java, ReturnServiceImpl.java, ReturnPostgresIntegrationTests.java.

FE: AdminOrdersPanel.tsx, paymentApi.ts, paymentClient.ts, returnsApi.ts, CustomerReturns.tsx và ba suite test FE nêu trên.

Rebuild Payment và Commerce bằng cấu hình Docker hiện tại trước khi test. Không cần migration mới.
