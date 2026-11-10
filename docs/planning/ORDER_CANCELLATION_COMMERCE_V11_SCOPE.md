# Đề xuất scope Commerce V11 — xác nhận và yêu cầu hủy

Trạng thái: ĐỀ XUẤT CHỜ DUYỆT. Không chạy DDL trong tài liệu. Chỉ dành cho luồng hủy đơn của khách đăng nhập; hoàn tiền và trả hàng triển khai ở giai đoạn sau.

## Mục tiêu

- Phân biệt đơn mới chưa xác nhận, đơn đã được admin xác nhận, đơn cũ không có bằng chứng.
- Giữ lịch sử yêu cầu hủy và cho phép gửi lại sau từ chối, nhưng mỗi đơn chỉ có tối đa một yêu cầu chờ duyệt.
- Giữ dữ liệu và các migration V1–V10; không bổ sung bảng hoặc thay đổi database Payment trong scope này.

## DDL dự kiến

Tên constraint phải được đối chiếu trong môi trường cô lập trước khi thực thi. Đây là DDL đề xuất, không phải migration đã duyệt.

```sql
ALTER TABLE public.customer_order
    ADD COLUMN confirmation_state text NOT NULL DEFAULT 'LEGACY_UNKNOWN',
    ADD COLUMN confirmed_by_user_id uuid,
    ADD COLUMN confirmed_at timestamptz;

ALTER TABLE public.customer_order
    ADD CONSTRAINT customer_order_confirmation_state_check
        CHECK (confirmation_state IN ('LEGACY_UNKNOWN', 'UNCONFIRMED', 'CONFIRMED')),
    ADD CONSTRAINT customer_order_confirmation_details_check
        CHECK (
            (confirmation_state = 'CONFIRMED'
                AND confirmed_by_user_id IS NOT NULL AND confirmed_at IS NOT NULL)
            OR
            (confirmation_state IN ('LEGACY_UNKNOWN', 'UNCONFIRMED')
                AND confirmed_by_user_id IS NULL AND confirmed_at IS NULL)
        );

ALTER TABLE public.cancellation_request
    DROP CONSTRAINT cancellation_request_order_id_key;

CREATE UNIQUE INDEX cancellation_request_one_pending_per_order_uq
    ON public.cancellation_request (order_id)
    WHERE request_status = 'PENDING';

CREATE INDEX cancellation_request_order_history_idx
    ON public.cancellation_request (order_id, requested_at DESC, cancellation_request_id DESC);
```

Mọi đơn tồn tại khi áp dụng migration nhận `LEGACY_UNKNOWN`; không tự thay đổi trạng thái hay tạo yêu cầu hủy. Mặc định `LEGACY_UNKNOWN` cũng an toàn cho code cũ khi triển khai lệch phiên bản. Code tạo đơn mới phải ghi rõ `UNCONFIRMED`. Admin xác nhận ghi `CONFIRMED`, người và thời điểm trong cùng transaction. Đơn đã xác nhận trước migration không bị suy đoán thành chưa xác nhận.

## Quy tắc triển khai liên quan

- Mọi thao tác xác nhận, hủy, yêu cầu và duyệt khóa đơn và kiểm tra trạng thái hiện tại. Không tạo hoặc duyệt hủy khi SHIPPED/COMPLETED/CANCELLED; giữ lịch sử và không cho nhiều yêu cầu PENDING.
- Đơn COD mới, chưa xác nhận và chưa thu: hủy ngay. Đã xác nhận hoặc LEGACY_UNKNOWN: yêu cầu admin duyệt. Không suy ra PLACED là admin đã xác nhận.
- QR chưa xác nhận/chưa nhận tiền chỉ hủy ngay sau khi có kiểm tra phối hợp Payment đáng tin cậy. Nếu kết quả Payment không xác định, không tuyên bố chưa nhận tiền hoặc hủy thành công. Thanh toán đến sau khi hủy phải được ghi nhận để đối soát, không tự báo đã hoàn.
- Khi BE chưa có luồng hoàn tiền bền vững, cho phép ghi nhận yêu cầu hủy đơn đã thanh toán nhưng không duyệt hủy có trả tiền theo cách khiến mất dấu nghĩa vụ hoàn tiền. Lộ trình và giao diện cần báo rõ chưa thể xử lý tiếp; không báo đã hoàn tiền.
- Sau khi bỏ UNIQUE order_id, code tra cứu phải phân biệt yêu cầu đang PENDING với lịch sử và chọn bản ghi theo thứ tự xác định; không dùng repository trả Optional tùy ý trên nhiều hàng.
- Không tự ý thay đổi luồng guest, chính sách đã thỏa thuận hoặc trạng thái tài chính; cập nhật API, tests và Postman tương thích.

## Kiểm thử và khôi phục

- PostgreSQL cô lập: nâng cấp dữ liệu cũ với các trạng thái, kiểm tra backfill, constraint, hai yêu cầu PENDING đồng thời, PENDING sau REJECTED, thứ tự lịch sử; kiểm tra không mất bản ghi.
- API: chủ đơn/người khác/admin; xác nhận lặp, hủy/duyệt đồng thời với chuyển SHIPPED, yêu cầu lại sau từ chối, đơn cũ, COD và QR, thanh toán đến trễ và Payment timeout.
- Không rollback tự động bằng cách khôi phục UNIQUE trên order_id sau khi đã có nhiều dòng: cần sao lưu và kế hoạch xử lý dữ liệu lịch sử trước khi downgrade. Không sửa database lâu dài trong quá trình test.

Scope không bao gồm schema hoàn tiền, trả hàng, bổ sung UI hay áp migration thực tế. Thiết kế kỹ thuật phối hợp Payment/Commerce và bảo đảm chống đua giữa hủy và thanh toán phải được chứng minh qua test; scope này không tự phê duyệt các thay đổi schema Payment sau này.
