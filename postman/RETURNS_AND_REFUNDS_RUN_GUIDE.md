# Kiểm thử trả hàng và hoàn tiền StyleMind

Import [collection Returns and Manual Refunds](StyleMind-Returns-and-Refunds.postman_collection.json). Gateway local dùng `http://localhost:8080`. Dùng tài khoản khách hàng và quản trị viên riêng, cùng dữ liệu test. Các bước ghi dữ liệu chỉ chạy khi environment có `allowRefundMutations=true`.

## Chuẩn bị backend

Commerce V16 mở rộng dữ liệu trả hàng và nghĩa vụ hoàn do hủy. Payment V3 lưu hồ sơ hoàn, nhật ký và sự kiện hoàn tất chờ gửi. Flyway chạy migration khi khởi động service theo cấu hình hiện tại.

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

1. Chọn đơn đã giao trong vòng 7 ngày và đã ghi nhận thu tiền COD hoặc nhận tiền VietQR. Điền `orderId`, `orderItemId`, `customerToken` và `adminToken` cho collection. Dùng ID dòng đơn hàng từ `available_items`, sau đó đính kèm một ảnh JPG hoặc PNG test vào từng request cần ảnh.
2. Chạy thư mục **01 Read return eligibility and queue**, rồi **02 Return and final inspection**. Khách chọn một món, gửi ảnh, bổ sung ảnh khi được yêu cầu. Shop duyệt đến lấy và đánh dấu kiểm tra đạt sau khi kiểm tra thực tế. Request tạo trả hàng tự lưu `returnId`, `refundSourceType=RETURN` và `refundSourceId` cho các bước sau.
3. Trong **03 Refund destination and transfer record**, khách nhập tài khoản test. Admin bắt đầu xử lý rồi mới xem thông tin nhận. Điền `transferReference` duy nhất và `proofReference` test để kiểm tra API ghi nhận chuyển khoản. Với dữ liệu thật, chỉ ghi nhận khi đã chuyển tiền và có chứng từ đối chiếu.
4. Đọc lại đơn sau khi Payment chuyển sự kiện hoàn tất sang Commerce. Đối chiếu số tiền với số lượng đã trả nhân đơn giá trong đơn. Đơn có nhiều dòng phải chỉ hoàn cho những dòng và số lượng đã chọn.

Gợi ý lý do để khách mô tả gồm sản phẩm bị lỗi, giao sai mẫu hoặc size, thiếu sản phẩm và kích cỡ không phù hợp. Admin quyết định dựa trên ảnh và tình trạng thực tế khi đến lấy. [Tham khảo hướng dẫn đổi trả của UNIQLO Việt Nam](https://faq-vn.uniqlo.com/pkb_Home_UQ_VN?fs=Search&id=kA0Ie000000TOXc&l=vi&pn=1), [hướng dẫn khiếu nại kiện hàng](https://faq-vn.uniqlo.com/pkb_Home_UQ_VN?c=category_uq_th%3AUQVN_CAT07&fs=Search&id=kA0Ie000000TOSs&l=vi).

## Hoàn toàn bộ đơn VietQR đã duyệt hủy

Chọn một yêu cầu hủy đã duyệt có khoản chờ hoàn. Đặt `refundSourceType=CANCELLATION` và `refundSourceId` bằng ID yêu cầu hủy. Chạy riêng thư mục **03**. Tiền VietQR về muộn sau khi hủy phiên tiếp tục xuất hiện ở danh sách đối soát thanh toán và cần được xác minh riêng.

## Các trường hợp cần đối chiếu

### Ảnh biên nhận chuyển khoản (Payment V4)

Trong thư mục **05 Transfer receipt**, admin phụ trách chọn một ảnh JPG/PNG thật, tối đa 5 MiB/16 megapixel vào field `image`. Có thể lưu khi PROCESSING hoặc sau REFUNDED, kể cả hồ sơ cũ đã hoàn tiền. Ảnh là tùy chọn và không tự ghi nhận chuyển tiền. Chỉ lưu một ảnh mỗi hồ sơ; gửi lại cùng ảnh an toàn, ảnh khác trả 409. Khách chỉ đọc được ảnh sau REFUNDED; khách khác không đọc được. Ảnh được tải với Authorization, no-store/private; không có URL ảnh công khai. FE có chọn ảnh, xem trước, bỏ ảnh chưa lưu, lưu và xem lại.

Khi khoản hoàn mới là PENDING, tài khoản nhận tiền đã được khách gửi. Admin cần **Bắt đầu hoàn tiền → Xem tài khoản nhận tiền**; đây là bước nhận xử lý và kiểm tra quyền xem, không phải lỗi thất lạc thông tin.

### Trạng thái trên danh sách đơn

Commerce đọc các sự kiện đã tiếp nhận và bổ sung `payment_status` (PAID/PENDING), `refunded_amount_vnd`, `refund_status` (PENDING_REFUND/PARTIALLY_REFUNDED/REFUNDED hoặc null) cho API danh sách và chi tiết. Không thay order_status giao hàng. FE hiển thị nhãn hoàn tiền và số đã hoàn riêng, nút thu COD ẩn khi payment_status=PAID kể cả sau F5. Kết quả phụ thuộc sự kiện Payment→Commerce được chuyển thành công; dữ liệu chưa đồng bộ không được giả lập thành đã hoàn.

Rebuild `payment-service commerce-service` bằng bộ env/Compose hiện dùng. Flyway Payment áp dụng V4 khi khởi động; Commerce không cần migration mới. Trong quá trình triển khai, chỉ đọc DB đang dùng để chẩn đoán và chạy migration/test trên PostgreSQL cô lập. Collection đã được kiểm tra JSON/script, chưa chạy upload trên hồ sơ thật.

Chi tiết code: Payment `RefundTransferReceiptEntity`, `RefundTransferReceiptRepository`, `RefundReceiptValidator`, `RefundReceiptImage`, mở rộng `RefundService`, `RefundServiceImpl`, `RefundController`, `RefundResponse`, `RefundMapper`, `RefundErrorCode`, `ApiExceptionHandler`, multipart configuration và V4. Commerce mở rộng `OrderResponse`, `OrderRepository.paymentProgress`, `OrderServiceImpl.response`. FE thêm `RefundReceipt`, cập nhật `RefundPanel`, `returnsApi`, kiểu dữ liệu/adapter Order, `AccountOrdersPage`, `AdminOrdersPanel`, `orderRefundStatus`. Các test liên quan nằm trong `RefundWorkflowPostgresTests`, `GuestPaymentMigrationPostgresTests`, `ReturnPostgresIntegrationTests`, `RefundReceipt.test.tsx`, `AccountOrdersLive.test.tsx`, `AdminCancellationActions.test.tsx`, `commerceOrderPaymentStatus.test.ts`.

- Đơn đã quá 7 ngày, đơn của tài khoản khác và số lượng vượt số đã mua phải bị từ chối.
- Với cùng một đơn, thử hai yêu cầu có số lượng chồng nhau. Tổng số lượng được chấp nhận không vượt số đã mua.
- Với một yêu cầu riêng đã duyệt lấy hàng, dùng bước **Shop rejects a pickup after inspection** trong thư mục **04**. Trạng thái chuyển sang từ chối, khách không có khoản chờ hoàn.
- Lặp request tạo trả bằng cùng `returnRequestKey` và lặp ghi nhận hoàn với cùng mã giao dịch. Đối chiếu mỗi nguồn chỉ có một hồ sơ hoàn.
- Với một hồ sơ khác đang xử lý, thử bước **Admin records a failed transfer** trong thư mục **04**, sau đó kiểm tra trạng thái và luồng thử lại.
- Nếu ngân hàng chưa xác nhận kết quả chuyển khoản, giữ hồ sơ ở trạng thái đang xử lý để đối soát. Chỉ ghi nhận thất bại sau khi xác minh ngân hàng chưa chuyển tiền.
- Thử tài khoản khách khác đọc ảnh hoặc hồ sơ hoàn. Thử admin khác đọc thông tin ngân hàng khi hồ sơ đã được một admin nhận xử lý.
- Nếu Commerce tạm thời mất kết nối sau khi Payment ghi nhận hoàn, thử lại sau khi khôi phục để kiểm tra sự kiện được gửi tiếp và đơn hiển thị kết quả.
