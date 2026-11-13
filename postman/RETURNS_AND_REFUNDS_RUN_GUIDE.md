# Kiểm thử trả hàng và hoàn tiền StyleMind

Import [collection Returns and Manual Refunds](StyleMind-Returns-and-Refunds.postman_collection.json). Gateway local dùng `http://localhost:8080`. Dùng tài khoản khách hàng và quản trị viên riêng, cùng dữ liệu test. Các bước ghi dữ liệu chỉ chạy khi environment có `allowRefundMutations=true`.

## Chuẩn bị backend

Commerce V16 mở rộng dữ liệu trả hàng và nghĩa vụ hoàn do hủy. Payment V3 lưu hồ sơ hoàn, nhật ký và sự kiện hoàn tất chờ gửi. Payment V4 lưu ảnh biên nhận riêng tư. Endpoint hoàn kèm ảnh tái sử dụng hai migration này. Flyway chạy migration khi khởi động service theo cấu hình hiện tại.

1. Đặt `STYLEMIND_REFUND_ENCRYPTION_KEY` trong `.env` của BE. Giá trị là 32 byte ngẫu nhiên mã hóa Base64. Sao lưu khóa riêng với bản sao database. Payment cần đúng khóa này để lưu và đọc thông tin ngân hàng.
2. Đảm bảo Commerce và Payment nhận cùng `STYLEMIND_COMMERCE_SERVICE_TOKEN`. Bật `STYLEMIND_PAYMENT_EVENTS_ENABLED=true` cho luồng ghi nhận thanh toán hiện có.
3. Từ thư mục gốc BE, build lại hai service. Với cấu hình Docker local đang dùng trong dự án này, chạy lệnh bên dưới.

```powershell
docker compose --env-file .env --env-file .env.chat.local -f docker-compose.yml -f "E:\mss\du-an\.superpowers\ep06-compose-local.yml" up -d --build --no-deps commerce-service payment-service
```

Để tạo khóa local lần đầu, chạy đoạn PowerShell sau trong thư mục gốc BE. Đoạn lệnh lưu khóa vào `.env` mà không in giá trị, đồng thời giữ khóa đã cấu hình.

```powershell
$envPath = (Resolve-Path -LiteralPath '.env').Path
$envText = [IO.File]::ReadAllText($envPath)
$keyLine = '(?m)^STYLEMIND_REFUND_ENCRYPTION_KEY=[^\r\n]*'
$existing = [regex]::Match($envText, $keyLine)
if (-not $existing.Success -or $existing.Value.Split('=', 2)[1].Trim() -eq '') {
    $keyBytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($keyBytes)
        $line = 'STYLEMIND_REFUND_ENCRYPTION_KEY=' + [Convert]::ToBase64String($keyBytes)
        if ($existing.Success) {
            $updated = $envText.Remove($existing.Index, $existing.Length).Insert($existing.Index, $line)
            [IO.File]::WriteAllText($envPath, $updated, [Text.UTF8Encoding]::new($false))
        } else {
            [IO.File]::AppendAllText($envPath, [Environment]::NewLine + $line + [Environment]::NewLine)
        }
    } finally {
        $rng.Dispose()
        [Array]::Clear($keyBytes, 0, $keyBytes.Length)
        Remove-Variable line, updated, envText -ErrorAction SilentlyContinue
    }
}
```

## Trả một phần đơn đã giao

1. Chọn đơn đã giao trong vòng 7 ngày và đã ghi nhận thu tiền COD hoặc nhận tiền VietQR/VNPay qua sự kiện đã xác minh. Điền `orderId`, `orderItemId`, `customerToken` và `adminToken` cho collection. Dùng ID dòng đơn hàng từ `available_items`, sau đó đính kèm một ảnh JPG hoặc PNG test vào từng request cần ảnh.
2. Chạy thư mục **01 Read return eligibility and queue**, rồi **02 Return and final inspection**. Khách chọn một món, gửi ảnh, bổ sung ảnh khi được yêu cầu. Shop duyệt đến lấy và đánh dấu kiểm tra đạt sau khi kiểm tra thực tế. Request tạo trả hàng tự lưu `returnId`, `refundSourceType=RETURN` và `refundSourceId` cho các bước sau.
3. Trong **03 Refund destination and transfer record**, khách nhập tài khoản test. Admin bắt đầu xử lý rồi mới xem thông tin nhận. Request prepare tự lưu `refundId`. Admin chọn đúng một ảnh biên nhận JPEG/PNG vào `image`, xác nhận `transfer_confirmed=true` sau khi chuyển tiền thành công, rồi chạy **Admin confirms transferred refund with one receipt photo**. Khoản phí chuyển tiền do shop chịu.
4. Đọc lại đơn sau khi Payment chuyển sự kiện hoàn tất sang Commerce. Đối chiếu số tiền với số lượng đã trả nhân đơn giá trong đơn. Đơn có nhiều dòng phải chỉ hoàn cho những dòng và số lượng đã chọn.

Gợi ý lý do để khách mô tả gồm sản phẩm bị lỗi, giao sai mẫu hoặc size, thiếu sản phẩm và kích cỡ không phù hợp. Admin quyết định dựa trên ảnh và tình trạng thực tế khi đến lấy. [Tham khảo hướng dẫn đổi trả của UNIQLO Việt Nam](https://faq-vn.uniqlo.com/pkb_Home_UQ_VN?fs=Search&id=kA0Ie000000TOXc&l=vi&pn=1), [hướng dẫn khiếu nại kiện hàng](https://faq-vn.uniqlo.com/pkb_Home_UQ_VN?c=category_uq_th%3AUQVN_CAT07&fs=Search&id=kA0Ie000000TOSs&l=vi).

## Hoàn toàn bộ đơn VietQR/VNPay đã duyệt hủy

Chọn một yêu cầu hủy đã duyệt có khoản chờ hoàn. Đặt `refundSourceType=CANCELLATION` và `refundSourceId` bằng ID yêu cầu hủy. Chạy riêng thư mục **03**. Tiền VietQR về muộn sau khi hủy phiên tiếp tục xuất hiện ở danh sách đối soát thanh toán và cần được xác minh riêng.

## Các trường hợp cần đối chiếu

### Ảnh biên nhận chuyển khoản (Payment V4)

Luồng admin hiện tại dùng `POST /payment/api/v1/admin/refunds/{sourceType}/{sourceId}/complete-with-receipt`. Multipart gồm `refund_id` khớp hồ sơ của nguồn, `transfer_confirmed` là chuỗi `true`, đúng một field file `image` chứa JPEG/PNG hợp lệ tối đa 5 MiB và 16 megapixel. Chỉ admin đã nhận hồ sơ PROCESSING được hoàn tất. Ảnh, trạng thái REFUNDED, audit và outbox được ghi trong cùng transaction. `completion_id` là mã ghi nhận nội bộ do hệ thống cấp. `transfer_reference` trả null cho luồng này. Admin không nhập mã giao dịch/chứng từ.

Gửi lại cùng nguồn, refund_id, admin, xác nhận và bytes ảnh trả lại hồ sơ đã hoàn. Gửi ảnh khác trả 409 REFUND_RECEIPT_CONFLICT. Sai refund_id trả 409 REFUND_STATE_CONFLICT. Admin khác trả 403. Thiếu ảnh/xác nhận/ID, nhiều ảnh, ảnh rỗng/giả định dạng hoặc vượt giới hạn bị từ chối. Servlet có thể trả 413 trước khi gọi validator khi upload quá lớn. Dùng thư mục **06** để kiểm tra từng nhánh trên fixture PROCESSING và tự chọn file cho từng request cần ảnh. Các test rollback và đồng thời chạy tự động với PostgreSQL cô lập.

Khách sở hữu hồ sơ chỉ đọc được ảnh sau REFUNDED. Khách khác nhận 404. Receipt được tải qua Authorization với Cache-Control no-store/private và X-Content-Type-Options nosniff. Giữ ảnh trong hồ sơ riêng tư, tránh thông tin ngân hàng không liên quan và dữ liệu thật trong fixture, export hoặc ảnh chụp màn hình. Outbox gửi Commerce chỉ chứa định danh nguồn, đơn, khoản hoàn, số tiền và thời điểm hoàn.

Endpoint JSON `/complete` và upload riêng `/receipt` tiếp tục phục vụ client cũ. Request JSON cũ nằm trong thư mục **04**, chạy riêng trên fixture tương ứng. Upload riêng trong thư mục **05** giữ một ảnh bất biến và hỗ trợ bổ sung cho hồ sơ cũ PROCESSING/REFUNDED. Khách xem được ảnh sau REFUNDED.

Khi khoản hoàn mới là PENDING, tài khoản nhận tiền đã được khách gửi. Admin cần **Bắt đầu hoàn tiền → Xem tài khoản nhận tiền**; đây là bước nhận xử lý và kiểm tra quyền xem, không phải lỗi thất lạc thông tin.

### Trạng thái trên danh sách đơn

Commerce đọc các sự kiện đã tiếp nhận và bổ sung `payment_status` (PAID/PENDING), `refunded_amount_vnd`, `refund_status` (PENDING_REFUND/PARTIALLY_REFUNDED/REFUNDED hoặc null) cho API danh sách và chi tiết. Không thay order_status giao hàng. FE hiển thị nhãn hoàn tiền và số đã hoàn riêng, nút thu COD ẩn khi payment_status=PAID kể cả sau F5. Kết quả phụ thuộc sự kiện Payment→Commerce được chuyển thành công; dữ liệu chưa đồng bộ không được giả lập thành đã hoàn.

Rebuild `payment-service commerce-service` bằng bộ env/Compose hiện dùng. Flyway Payment áp dụng V4 khi khởi động; Commerce không cần migration mới. Trong quá trình triển khai, chỉ đọc DB đang dùng để chẩn đoán và chạy migration/test trên PostgreSQL cô lập. Collection đã được kiểm tra JSON/script, chưa chạy upload trên hồ sơ thật.

Chi tiết code: Payment `RefundTransferReceiptEntity`, `RefundTransferReceiptRepository`, `RefundReceiptValidator`, `RefundReceiptImage`, mở rộng `RefundService`, `RefundServiceImpl`, `RefundController`, `RefundResponse`, `RefundMapper`, `RefundErrorCode`, `ApiExceptionHandler`, multipart configuration và V4. Commerce mở rộng `OrderResponse`, `OrderRepository.paymentProgress`, `OrderServiceImpl.response`. FE thêm `RefundReceipt`, cập nhật `RefundPanel`, `returnsApi`, kiểu dữ liệu/adapter Order, `AccountOrdersPage`, `AdminOrdersPanel`, `orderRefundStatus`. Các test liên quan nằm trong `RefundWorkflowPostgresTests`, `GuestPaymentMigrationPostgresTests`, `ReturnPostgresIntegrationTests`, `RefundReceipt.test.tsx`, `AccountOrdersLive.test.tsx`, `AdminCancellationActions.test.tsx`, `commerceOrderPaymentStatus.test.ts`.

- Đơn đã quá 7 ngày, đơn của tài khoản khác và số lượng vượt số đã mua phải bị từ chối.
- Mỗi đơn chỉ tạo một yêu cầu trả và một yêu cầu hủy trong suốt vòng đời, kể cả sau từ chối. Return eligibility trả RETURN_ALREADY_REQUESTED và available_items=[] sau khi đã dùng. Tạo bằng khóa mới trả 409 RETURN_ALREADY_REQUESTED, hủy lần nữa trả 409 CANCELLATION_ALREADY_REQUESTED. Cùng khóa và payload ban đầu được replay. Chỉ bổ sung ảnh khi MORE_IMAGES_REQUESTED.
- Với một yêu cầu riêng đã duyệt lấy hàng, dùng bước **Shop rejects a pickup after inspection** trong thư mục **04**. Trạng thái chuyển sang từ chối, khách không có khoản chờ hoàn.
- Lặp request tạo trả bằng cùng `returnRequestKey` và lặp hoàn kèm cùng ảnh/xác nhận. Đối chiếu mỗi nguồn chỉ có một hồ sơ, một audit REFUNDED và một outbox.
- Với một hồ sơ khác đang xử lý, thử bước **Admin records a failed transfer** trong thư mục **04**, sau đó kiểm tra trạng thái và luồng thử lại.
- Nếu ngân hàng chưa xác nhận kết quả chuyển khoản, giữ hồ sơ ở trạng thái đang xử lý để đối soát. Chỉ ghi nhận thất bại sau khi xác minh ngân hàng chưa chuyển tiền.
- Thử tài khoản khách khác đọc ảnh hoặc hồ sơ hoàn. Thử admin khác đọc thông tin ngân hàng khi hồ sơ đã được một admin nhận xử lý.
- Nếu Commerce tạm thời mất kết nối sau khi Payment ghi nhận hoàn, thử lại sau khi khôi phục để kiểm tra sự kiện được gửi tiếp và đơn hiển thị kết quả.

- Khi yêu cầu hủy đang chờ quyết định, confirm/process/ship phải trả 409 CANCELLATION_PENDING.
- Nguồn hoàn VietQR/VNPay phải có sự kiện APPLIED đã xác minh khớp đơn, chủ sở hữu, phương thức, payment_attempt_id và số tiền. Payment vẫn kiểm tra ledger SETTLED và giới hạn tổng tiền đã phân bổ. Browser return VNPay không mở quyền hoàn.
