# Phạm vi đề xuất: duyệt hủy đơn VietQR đã thanh toán

**Phạm vi schema đã được chủ dự án duyệt ngày 11/11/2026.** Migration thực thi là `V13__record_paid_cancellation_refund_obligation.sql`. Phần này hoàn thiện quyết định admin duyệt hủy đơn QR đã nhận tiền. Hoàn tiền thủ công và trả hàng là giai đoạn tiếp theo.

## Thay đổi dữ liệu Commerce dự kiến

Tạo migration Commerce V13 (kiểm tra lại số phiên bản trước khi viết), giữ nguyên V1–V12 và dữ liệu cũ:

```sql
CREATE TABLE public.cancellation_refund_obligation (
    cancellation_request_id uuid PRIMARY KEY
        REFERENCES public.cancellation_request(cancellation_request_id),
    order_id uuid NOT NULL UNIQUE REFERENCES public.customer_order(order_id),
    payment_event_id uuid NOT NULL UNIQUE REFERENCES public.payment_event_inbox(event_id),
    amount_vnd bigint NOT NULL CHECK (amount_vnd > 0),
    obligation_status text NOT NULL CHECK (obligation_status = 'PENDING_REFUND'),
    created_at timestamptz NOT NULL
);
```

Không lưu số tài khoản, thông tin ngân hàng hoặc chứng từ. Không thêm bảng/cột ở Payment, không truy cập DB Payment từ Commerce, không tự tạo nghĩa vụ hoàn cho đơn cũ khi migrate. Bảng này chỉ ghi nhận khoản cần xử lý; thiết kế hoàn tiền thực tế sẽ có phạm vi riêng được duyệt sau.

## Quy tắc khi duyệt

- Khóa đơn và yêu cầu hủy; chỉ duyệt yêu cầu đang PENDING khi đơn QR đã thanh toán đang PAID hoặc PROCESSING. Đơn đang giao, hoàn tất hoặc đã hủy không được duyệt.
- Chỉ tạo nghĩa vụ khi có đúng sự kiện Payment đã được áp dụng (`APPLIED`) cho đơn, phương thức QR và số tiền trùng đơn; dùng số tiền từ sự kiện, không lấy từ client. Thiếu hoặc bất nhất thì giữ yêu cầu chờ và trả lỗi đối soát, không hủy.
- Trong cùng giao dịch Commerce: tạo một nghĩa vụ PENDING_REFUND, chuyển đơn CANCELLED, lưu lịch sử và duyệt yêu cầu. Ràng buộc duy nhất và khóa đơn chống tạo trùng khi bấm lại hoặc chạy đồng thời. Không coi PENDING_REFUND là đã hoàn tiền.
- Hoàn lại tồn kho đúng một lần cho đơn chưa giao, bao gồm đơn QR mới có `qr_checkout_item` đã CONSUMED và đơn lịch sử dùng `inventory_reservation`; xử lý trong cùng giao dịch, không áp cả hai lần cho cùng mặt hàng.
- Yêu cầu từ chối vẫn giữ lịch sử và khách được gửi lại; khi đơn đang SHIPPED không chấp nhận yêu cầu mới hoặc duyệt yêu cầu cũ.

## Kiểm thử và giới hạn

Test PostgreSQL cô lập: migrate từ V12 có dữ liệu cũ, duyệt QR đã trả tiền, thiếu/sai sự kiện, owner và quyền admin, trạng thái đang giao, duyệt đồng thời, hoàn tồn một lần cho đơn cũ và mới, transaction rollback, và hồi quy COD. Kiểm tra giao diện hiển thị “đang chờ hoàn tiền” sau duyệt; không báo chuyển tiền thành công. Không chạy migration trên DB đang sử dụng trong quá trình kiểm thử.

Phần giao diện và thao tác nhận thông tin hoàn tiền, chuyển khoản, chứng từ, đánh dấu hoàn thành và đối soát giao dịch đến trễ chưa thuộc migration này. Các khoản đến trễ sau hủy phiên QR không có đơn cũng không tự tạo nghĩa vụ hoàn theo bảng trên; tiếp tục lưu ngoại lệ để xử lý trong phần hoàn tiền.
