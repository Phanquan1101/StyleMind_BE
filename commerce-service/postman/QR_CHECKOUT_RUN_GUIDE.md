# Kiểm tra phiên VietQR trước khi tạo đơn

Chỉ dùng tài khoản và môi trường kiểm thử. Import `qr-checkout.postman_collection.json`, đặt `baseUrl` là Gateway (ví dụ `http://localhost:8080`), `accessToken`, `addressId` và `variantId` của người dùng kiểm thử. Không đưa token hoặc thông tin ngân hàng thật vào collection.

1. Gọi **Chuẩn bị phiên** với `checkout_key` duy nhất. Lưu `checkoutId` vào biến `checkoutId`. Phiên phải ở `PREPARING` và chưa có đơn hàng.
2. Gọi **Tạo mã VietQR** để nhận thông tin từ Payment. Có thể gọi **Hủy thanh toán** ngay cả khi yêu cầu tạo mã còn đang chạy. Chỉ coi là đã hủy khi API xác nhận `CANCELLED`.
3. Gọi **Xem phiên**: khi nhận thanh toán hợp lệ, `ORDER_CREATED` cho biết đơn đã được tạo; mã đơn là `checkoutId`. Khi hủy hoặc hết hạn, hàng được giải phóng. Không chuyển tiền vào mã đã hủy hoặc hết hạn.
4. Dùng **Khôi phục phiên** với cùng `checkout_key` nếu không nhận được kết quả bước 1. Với thông báo đến muộn hoặc sau hủy, kiểm tra ngoại lệ Payment và đối soát thủ công; không xác nhận hoàn tiền khi chưa thực hiện.

Việc xem mã QR hoặc chạy collection không chứng minh đã có chuyển tiền. Kiểm tra xác nhận thực cần môi trường SePay/webhook test do nhóm cấu hình. Luồng trong collection dành cho tài khoản đã đăng nhập.

Khách phải đăng nhập để tạo đơn hoặc phiên thanh toán mới. Với phiên khách đã tạo trước khi thay đổi này, các API xem, thanh toán hoặc hủy phiên và xem đơn cũ vẫn cần header `X-Guest-Session-Key`; không lưu khóa phiên vào collection chia sẻ.

## Danh sách phiên VietQR trong trang Đơn hàng

`GET /commerce/api/v1/qr-checkouts/pending?page=0&size=10` dùng access token của khách. API trả `items`, `page`, `size`, `total_elements`, `total_pages`. Mỗi phần tử có `checkoutId`, `status`, `totalVnd`, `createdAt`, `expiresAt`.

Kết quả chỉ gồm phiên của chủ token đang `PREPARING` hoặc `AWAITING_PAYMENT` và chưa quá `expiresAt`, kể cả khi scheduler chưa đổi trạng thái phiên hết hạn. Thứ tự là ngày tạo mới nhất, rồi mã phiên giảm dần. Các tham số được phép là `page` từ 0 và `size` từ 1 đến 100. Query chỉ định khách khác, trạng thái hoặc tham số lặp bị từ chối.

Chạy request **Danh sách phiên QR chờ thanh toán của tôi** sau khi chuẩn bị phiên. Dùng tài khoản khách thứ hai để kiểm tra cô lập. Sau khi hủy, hết hạn hoặc nhận thanh toán và tạo đơn, phiên phải biến mất khỏi danh sách chờ. FE hiển thị phần này khi lọc **Tất cả trạng thái** hoặc **Chờ thanh toán** và mở lại `/checkout/qr/{checkoutId}` khi chọn **Tiếp tục thanh toán**.

### Tệp và kiểm chứng phần danh sách phiên, 2026-11-11

Các tệp BE được bổ sung hoặc cập nhật, tính từ gốc repository:

- `commerce-service/src/main/java/com/stylemind/commerce/entity/QrCheckout.java`
- `commerce-service/src/main/java/com/stylemind/commerce/repository/order/QrCheckoutRepository.java`
- `commerce-service/src/main/java/com/stylemind/commerce/dto/order/QrCheckoutListResponse.java`
- `commerce-service/src/main/java/com/stylemind/commerce/controller/order/QrCheckoutController.java`
- `commerce-service/src/main/java/com/stylemind/commerce/service/order/OrderService.java`
- `commerce-service/src/main/java/com/stylemind/commerce/service/order/impl/OrderServiceImpl.java`
- `commerce-service/src/main/java/com/stylemind/commerce/validation/order/OrderRequestValidator.java`
- `commerce-service/src/test/java/com/stylemind/commerce/CartOrderPostgresIntegrationTests.java`
- `commerce-service/postman/qr-checkout.postman_collection.json`
- `commerce-service/postman/QR_CHECKOUT_RUN_GUIDE.md`

Các tệp FE trong repository `StyleMind_FE`:

- `src/features/orders/pages/AccountOrdersPage.tsx`
- `src/features/orders/AccountOrdersLive.test.tsx`
- `src/features/payments/api/types.ts`
- `src/features/payments/api/paymentApi.ts`
- `src/features/payments/api/paymentApi.test.ts`
- `src/test/identityMockBackend.ts` (fixture kiểm thử).

Lệnh Maven `verify -Dtest=CartOrderPostgresIntegrationTests -Dmaven.compiler.proc=full -Dstylemind.test.postgres-bin="C:/Program Files/PostgreSQL/18/bin"` đạt 57 test, không lỗi và không bỏ qua, trên PostgreSQL cô lập. Test HTTP qua MockMvc bao gồm chủ tài khoản A/B, xác thực, phân trang, dữ liệu đầu vào và loại phiên hết hạn/hủy/đã tạo đơn. FE chạy `npm test -- src/features/orders/AccountOrdersLive.test.tsx src/features/orders/OrdersFlow.test.tsx src/features/payments/api/paymentApi.test.ts src/features/checkout/QrCheckoutPage.test.tsx`: 34 test đạt trong 4 suite. `npm run typecheck` và `npm run build` đạt; build còn cảnh báo kích thước bundle hiện có.

Collection được kiểm tra cấu trúc JSON và cú pháp script; chưa chạy request Postman trên Docker đang dùng. Thay đổi này chỉ đọc bảng `qr_checkout` hiện có, không thêm migration và không sửa dữ liệu DB đang dùng. Cần rebuild `commerce-service` để Docker phục vụ endpoint mới. Phiên QR hết hạn sẽ không xuất hiện; cần tạo phiên mới còn hạn để kiểm tra giao diện.

## Ghép cơ chế số lượng biến thể

Phiên QR trừ `product_variant.quantity` khi giữ hàng. Nhận tiền thành công tạo đơn từ số lượng đã giữ. Hủy thanh toán hoặc hết hạn cộng lại đúng một lần dưới khóa phiên. Duyệt hủy đơn đã thanh toán trả số lượng từ các món của đơn dưới khóa đơn và ghi khoản chờ hoàn tiền.

DB local được đối chiếu trong đợt ghép đã áp dụng Commerce V1–V13, bao gồm V11 xác nhận đơn, V12 phiên QR và V13 khoản chờ hoàn. Hai migration mới từ `dev` được đánh số V14 chuyển số lượng sang biến thể và V15 bỏ chi nhánh. Nội dung V1–V13 được giữ nguyên. Kiểm thử nâng cấp từ V13 chạy trên PostgreSQL cô lập.

Với database đã áp dụng V11 chuyển tồn kho hoặc V12 bỏ chi nhánh của lịch sử `dev`, cần đối chiếu lịch sử Flyway và lập phương án hội tụ trước khi triển khai bản ghép. Tên phiên bản V11/V12 của hai lịch sử mang nội dung khác nhau.

### Tệp và kiểm chứng của đợt ghép

- Xử lý conflict ở `repository/inventory/InventoryRepository.java` (xóa), `service/inventory/InventoryExpiryScheduler.java`, `service/order/impl/OrderServiceImpl.java`, `service/order/impl/PaymentEventServiceImpl.java` dưới `src/main/java/com/stylemind/commerce`.
- Bổ sung thao tác trả số lượng ở `service/order/VariantQuantityService.java`, `service/order/impl/VariantQuantityServiceImpl.java` và chuyển `service/order/QrCheckoutReservationService.java` sang số lượng biến thể.
- Đổi tên hai migration từ `dev` thành `V14__move_variant_stock_to_product_variant.sql` và `V15__drop_branch_module.sql` trong `src/main/resources/db/migration`.
- Cập nhật `CartOrderPostgresIntegrationTests.java`, `GuestCheckoutMigrationPostgresTests.java`, `QrCheckoutMigrationPostgresTests.java`, `CommerceServiceApplicationTests.java` và thêm `VariantQuantityMergeMigrationPostgresTests.java` trong `src/test/java/com/stylemind/commerce`. Các test đặt hàng khách vãng lai và tạo đơn QR trước thanh toán được điều chỉnh theo yêu cầu đăng nhập và nhận tiền trước khi tạo đơn đã chốt.
- Bộ hồi quy Commerce có 139 test thuộc 20 suite. Sau khi xử lý lỗi fixture và chạy lại 68 test của hai suite bị ảnh hưởng, báo cáo cuối có 139 test đạt, 0 lỗi, 0 bỏ qua. Test dùng PostgreSQL 18 cô lập và Java biên dịch với `release 21`. Nội dung 13 migration đã áp dụng được đối chiếu với merge index. Collection QR được kiểm tra JSON hợp lệ, phần này chưa phải chạy collection trên hệ thống đang sử dụng.
