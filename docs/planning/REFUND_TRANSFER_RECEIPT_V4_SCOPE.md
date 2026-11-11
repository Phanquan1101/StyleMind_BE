# Ảnh biên nhận chuyển khoản hoàn tiền — thiết kế và scope Payment V4

Trạng thái: chủ dự án đã duyệt và yêu cầu triển khai ngày 2026-11-11. Chỉ mở rộng luồng hoàn tiền hiện có; không thay nghiệp vụ hủy đơn, trả hàng hay cách xác nhận đã chuyển tiền.

## Kết quả mong muốn

- Trong khung hoàn tiền admin, thêm chọn ảnh, xem trước, bỏ ảnh chưa lưu và nút lưu ảnh biên nhận.
- Một hồ sơ có tối đa một ảnh JPG/PNG, tối đa 5 MiB. Ảnh là tùy chọn, không chặn các hồ sơ cũ không có ảnh.
- Admin đang phụ trách hồ sơ được thêm ảnh lúc PROCESSING hoặc sau REFUNDED, bao gồm hồ sơ đã hoàn trước khi bổ sung tính năng.
- Sau khi đã lưu, ảnh không bị thay thế/xóa bởi giao diện này. Gửi lại cùng nội dung trả cùng ảnh; gửi nội dung khác báo xung đột.
- Khi REFUNDED, cả khách sở hữu đơn và admin xem được ảnh. Khách chưa được xem ảnh nháp khi PROCESSING.
- Upload ảnh không tự đổi trạng thái hoàn tiền, không tạo giao dịch mới, không có nghĩa ngân hàng đã xác minh chuyển khoản. Thao tác xác nhận hoàn tiền hiện tại tiếp tục yêu cầu mã giao dịch và mã chứng từ.

## Cách lưu

Đề xuất lưu bytes trong Payment PostgreSQL, theo cách Commerce đang lưu ảnh bằng chứng trả hàng. Ảnh nhỏ, mỗi hồ sơ một ảnh; không cần tài khoản Cloudinary/B2 mới và không công khai URL ảnh ngân hàng. Dùng dịch vụ ảnh ngoài sẽ thêm cấu hình và quyền truy cập riêng; chỉ lưu ảnh trong trình duyệt không đáp ứng lưu trữ/xem lại giữa khách và admin.

Không sửa V1–V3, không sửa cột proof_reference để nhét ảnh hay base64 vào chuỗi chứng từ. Không tạo thay đổi Commerce.

## Migration mới

Payment V4 `V4__create_refund_transfer_receipt.sql` chỉ tạo bảng `refund_transfer_receipt`:

```sql
CREATE TABLE public.refund_transfer_receipt (
    refund_id uuid PRIMARY KEY REFERENCES public.refund_case(refund_id),
    content_type text NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    content bytea NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 5242880),
    content_sha256 text NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    uploaded_by_user_id uuid NOT NULL,
    uploaded_at timestamptz NOT NULL
);
```

Không backfill, không xóa dữ liệu, không thay refund_case/refund_audit/outbox. PK đảm bảo một ảnh mỗi hồ sơ, không cần index bổ sung. FK chỉ trong database Payment. Thời gian/người tải là dữ liệu audit của ảnh.

## API bổ sung trên controller/service hiện tại

- POST `/api/v1/admin/refunds/{sourceType}/{sourceId}/receipt`: multipart field `image`, admin được phân công, PROCESSING hoặc REFUNDED; trả metadata ảnh. Khóa refund_case trước khi kiểm tra/thêm ảnh để chống upload trùng đồng thời.
- GET `/api/v1/refunds/{sourceType}/{sourceId}/receipt`: trả ảnh với đúng MIME, `Cache-Control: no-store, private`, `X-Content-Type-Options: nosniff`. Chỉ admin hoặc chủ hồ sơ; khách chỉ xem khi REFUNDED. Không công khai ảnh qua static path.
- RefundResponse bổ sung `has_receipt` boolean để FE biết ảnh đã lưu, giữ nguyên các trường/trạng thái cũ. Với khách trước REFUNDED, không quảng bá ảnh nháp.
- Kiểm tra MIME và nội dung ảnh thực, từ chối file giả/quá dung lượng/ảnh kích thước giải mã vượt giới hạn; không log bytes, thông tin ngân hàng hay ảnh. FE lấy ảnh bằng request có phiên đăng nhập rồi tạo object URL tạm, thu hồi khi đóng/đổi hồ sơ.

## Kiểm thử và triển khai sau duyệt

1. Kiểm thử migration PostgreSQL cô lập, quyền admin/khách A/B, trạng thái, ảnh giả/quá lớn, upload đồng thời và gửi lại.
2. Bổ sung JPA entity/repository, DTO và phương thức service/controller Payment hiện có; không dựng luồng refund thứ hai.
3. Nối RefundPanel, chọn/xem trước/lưu/xem ảnh; lỗi upload hiển thị rõ, giữ lại trạng thái hoàn tiền thật.
4. Test FE theo luồng upload và phân quyền hiển thị, typecheck/build; cập nhật Postman collection/run guide.
5. Người dùng rebuild Payment để Flyway áp dụng V4 theo cấu hình đang dùng. Trong quá trình code chỉ dùng DB test cô lập, chưa tự áp migration lên DB đang dùng.

Các lựa chọn giới hạn một ảnh, 5 MiB và không thay thế ảnh đã lưu đã được chủ dự án duyệt cùng scope này.
