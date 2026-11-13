# Bàn giao điều chỉnh đơn hàng, thanh toán, trả hàng và hoàn tiền

Ngày 13/11/2026.

## Phần đã triển khai

- Mỗi đơn có tối đa một yêu cầu hủy và một yêu cầu trả hàng trong suốt vòng đời. Yêu cầu bị từ chối vẫn giữ lịch sử và khóa gửi lần nữa. Bổ sung ảnh chỉ được thực hiện khi admin yêu cầu.
- Yêu cầu hủy đang chờ duyệt chặn xác nhận và chuyển bước giao hàng ở cả BE và FE.
- Khoản thanh toán VNPay hợp lệ được dùng cho hủy đơn có hoàn tiền và trả hàng, với đối chiếu chủ đơn, phương thức, số tiền và sự kiện đã áp dụng.
- Admin xác nhận hoàn tiền bằng một ảnh JPG/PNG tối đa 5 MB và xác nhận đã chuyển khoản. BE lưu ảnh, lịch sử, kết quả và outbox trong một giao dịch. Gửi lại cùng ảnh được xử lý an toàn.
- COD dùng thao tác Đã giao hàng. Commerce lưu trạng thái cùng yêu cầu thu tiền, gọi Payment sau commit và tự thử lại mỗi 15 giây khi cần. Payment dùng sổ giao dịch và cơ chế chống ghi nhận trùng hiện có.
- Đơn COD cũ đã hoàn tất trước đợt này tiếp tục sử dụng API ghi nhận thu tiền thủ công hiện có. Chỉ các thao tác giao hàng mới có dấu yêu cầu thu tiền bền vững.
- Admin gom thanh toán, yêu cầu hủy và trả hàng/hoàn tiền vào mục Đơn hàng. Có số việc cần xử lý, nhãn ưu tiên hủy, nhãn trả hàng/hoàn tiền và lọc VNPay. Danh sách tự tải lại mỗi 30 giây khi đang hiển thị, chi tiết COD đang đồng bộ tự kiểm tra mỗi 5 giây.
- Khách có thể bắt đầu lần mua khác khi phiên QR trước đã được xác định. Kết quả chưa rõ của cùng lần gửi vẫn dùng mã cũ để phục hồi. Thanh toán phiên QR cũ giữ đúng bản nháp mới hơn.
- Trạng thái khách hiển thị Đã giao hàng. QR chưa thanh toán cho hủy phiên trực tiếp, trạng thái hủy và hết hạn được phân biệt.
- Các đường trừ hàng khi tạo đơn, hoàn hàng khi hủy và không tự nhập kho khi trả hàng đã được rà cùng các kiểm thử hiện có.

## Kiểm chứng

| Bộ kiểm thử | Kết quả |
|---|---|
| Commerce CartOrder + OrderConfirmation | 63 ca qua |
| Commerce Return | 12 ca qua |
| Commerce COD delivery + Payment client HTTP | 9 ca qua |
| Payment RefundWorkflow | 14 ca qua |
| Payment COD/order HTTP | 14 ca qua |
| Payment regression có loại trừ hai lớp lỗi có sẵn | 104 ca qua |
| FE admin, return, QR, checkout selection | 57 ca qua |
| QR bảo toàn bản nháp account/guest | 11 ca qua, có giao với nhóm 57 |
| FE customer routes | 3 ca qua |
| FE TypeScript và Vite build | Qua, còn cảnh báo kích thước bundle |
| Hai Postman collection refund và COD | JSON hợp lệ |

Các nhóm kiểm thử có giao nhau. Không cộng thành tổng ca duy nhất.

Log BE nằm trong commerce-service/target/alignment-regression-verified.log, return-alignment-verified.log, cod-delivery-verification.log và payment-service/target/refund-photo-verified.log, payment-alignment-regression-verified.log, cod-delivery-verification.log. Log FE nằm tại E:/mss/du-an/fe-verified-results.txt và fe-qr-verified.txt.

Bộ Payment rộng vẫn có lỗi có sẵn tại SepayContentReferenceTests do kỳ vọng parser khác source đã pull và PaymentOrderAttemptServiceTests do constructor cũ thiếu cấu hình VNPay sandbox. Hai lớp này được loại khỏi lượt 104 ca. Bộ toàn dự án chưa đạt trạng thái tất cả kiểm thử đều qua.

## Cấu hình và môi trường

### Bổ sung VietQR tự chuyển chuẩn bị hàng

Khi SePay xác nhận đã thu tiền, Commerce tạo đúng một đơn `PROCESSING` với `confirmation_state=AUTO_CONFIRMED` và không gán admin xác nhận. Lịch sử ghi lần lượt `PAID` rồi `PROCESSING`; FE hiển thị “Đã thanh toán” và “Đang chuẩn bị”. Admin tiếp tục bằng “Bắt đầu giao hàng”, không cần nút xác nhận đơn. Đơn VietQR cũ ở `PAID` giữ nguyên trạng thái và luồng xác nhận trước đây. COD và VNPAY không đổi. Phiên QR chưa thanh toán vẫn có thể hủy trực tiếp; sau khi đã thanh toán, yêu cầu hủy chờ admin xử lý và khoản hoàn tiền thực tế được xử lý riêng.

Commerce V18 chỉ mở rộng ràng buộc xác nhận đơn để chấp nhận `AUTO_CONFIRMED`. Kiểm thử PostgreSQL cô lập đã qua: CartOrder 62 ca trước khi thêm ca bảo vệ mới, ca bảo vệ yêu cầu hủy chặn giao hàng rồi cho giao sau khi từ chối, PaymentEvent 21 ca và migration V18 1 ca. FE đã qua 25 ca liên quan, TypeScript và Vite build. Chưa chạy giao dịch SePay thật hoặc cập nhật container đang chạy.

Đã bổ sung STYLEMIND_PAYMENT_EVENTS_ENABLED=true và hai token nội bộ còn thiếu trong file .env cục bộ. Giá trị token không đưa vào báo cáo. Cần tạo lại container Commerce và Payment để áp dụng code và cấu hình.

Kiểm thử BE dùng PostgreSQL 18 cô lập và HTTP server kiểm thử. Migration cũ giữ nguyên, không thêm schema trong đợt này. Chưa chạy giao dịch ngân hàng thật, luồng webhook công khai, ghép hai service đang chạy hay khởi động lại toàn JVM để kiểm chứng phục hồi. Kiểm thử worker phục hồi dùng trạng thái đã lưu trong database kiểm thử.

Luồng giao thất bại được để lại theo quyết định của chủ dự án.

## Danh sách file thay đổi

### Backend

- commerce-service/postman/ORDER_CONFIRMATION_RUN_GUIDE.md
- commerce-service/src/main/java/com/stylemind/commerce/client/payment/PaymentClient.java
- commerce-service/src/main/java/com/stylemind/commerce/dto/order/InternalPaymentSnapshot.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/order/CancellationRefundRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/order/CancellationRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/order/OrderRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/returns/RefundSourceRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/repository/returns/ReturnRepository.java
- commerce-service/src/main/java/com/stylemind/commerce/service/order/CodCollectionDispatcher.java
- commerce-service/src/main/java/com/stylemind/commerce/service/order/CodDeliveryRequested.java
- commerce-service/src/main/java/com/stylemind/commerce/service/order/InternalPaymentSnapshotService.java
- commerce-service/src/main/java/com/stylemind/commerce/service/order/impl/OrderServiceImpl.java
- commerce-service/src/main/java/com/stylemind/commerce/service/returns/impl/ReturnServiceImpl.java
- commerce-service/src/test/java/com/stylemind/commerce/CartOrderPostgresIntegrationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/CodDeliveryPostgresTests.java
- commerce-service/src/test/java/com/stylemind/commerce/PaymentClientHttpTests.java
- commerce-service/src/test/java/com/stylemind/commerce/ReturnPostgresIntegrationTests.java
- docs/superpowers/plans/2026-11-13-order-payment-return-alignment-spec.md
- docs/superpowers/plans/2026-11-13-order-payment-return-alignment.md
- payment-service/src/main/java/com/stylemind/payment/client/CommerceOrderPaymentSnapshot.java
- payment-service/src/main/java/com/stylemind/payment/controller/OrderPaymentController.java
- payment-service/src/main/java/com/stylemind/payment/controller/RefundController.java
- payment-service/src/main/java/com/stylemind/payment/dto/RefundRequests.java
- payment-service/src/main/java/com/stylemind/payment/dto/RefundResponse.java
- payment-service/src/main/java/com/stylemind/payment/entity/RefundCaseEntity.java
- payment-service/src/main/java/com/stylemind/payment/mapper/RefundMapper.java
- payment-service/src/main/java/com/stylemind/payment/security/CommerceHandoffSecurity.java
- payment-service/src/main/java/com/stylemind/payment/service/PaymentAttemptService.java
- payment-service/src/main/java/com/stylemind/payment/service/RefundService.java
- payment-service/src/main/java/com/stylemind/payment/service/impl/PaymentAttemptServiceImpl.java
- payment-service/src/main/java/com/stylemind/payment/service/impl/RefundServiceImpl.java
- payment-service/src/test/java/com/stylemind/payment/OrderPaymentPostgresIntegrationTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentCreationHttpPostgresTests.java
- payment-service/src/test/java/com/stylemind/payment/RefundWorkflowPostgresTests.java
- postman/COD_DELIVERY_COLLECTION_RUN_GUIDE.md
- postman/RETURNS_AND_REFUNDS_RUN_GUIDE.md
- postman/StyleMind-COD-Delivery-Collection.postman_collection.json
- postman/StyleMind-Returns-and-Refunds.postman_collection.json

### Frontend

- src/features/admin/components/AdminReturnsPanel.tsx
- src/features/admin/components/AdminOrderManagement.test.tsx
- src/app/router/CustomerRoutes.test.tsx
- src/features/admin/components/AdminCancellationActions.test.tsx
- src/features/admin/components/AdminCommerceActions.test.tsx
- src/features/admin/components/AdminOrderManagement.test.tsx
- src/features/admin/components/AdminOrdersPanel.tsx
- src/features/admin/components/AdminRequestsPanel.tsx
- src/features/admin/components/AdminReturnsFlow.test.tsx
- src/features/admin/components/AdminReturnsPanel.tsx
- src/features/admin/components/AdminShell.tsx
- src/features/admin/model/adminOrderAttention.test.ts
- src/features/admin/model/adminOrderAttention.ts
- src/features/admin/pages/AdminPage.test.tsx
- src/features/admin/pages/AdminPage.tsx
- src/features/checkout/QrCheckoutPage.test.tsx
- src/features/checkout/components/GuestPaymentView.tsx
- src/features/checkout/model/accountSelection.test.ts
- src/features/checkout/model/accountSelection.ts
- src/features/checkout/pages/QrCheckoutPage.tsx
- src/features/orders/AccountOrdersLive.test.tsx
- src/features/orders/CustomerDeliveryStatus.test.tsx
- src/features/orders/OrderDetailSections.test.tsx
- src/features/orders/OrderReturnLive.test.tsx
- src/features/orders/components/OrderDetailSections.tsx
- src/features/orders/model/orderStatus.ts
- src/features/orders/pages/AccountOrdersPage.tsx
- src/features/payments/PaymentPages.test.tsx
- src/features/returns/CustomerReturns.tsx
- src/features/returns/RefundPanel.tsx
- src/features/returns/returnsApi.ts
- src/types/index.ts

File .env cục bộ cũng được cập nhật và nằm ngoài danh sách source đưa lên Git.
