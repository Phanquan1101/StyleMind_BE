# Dự thảo phạm vi migration — hủy đơn và hoàn tiền

**Trạng thái:** chưa duyệt, chưa phải migration thực thi. Đây là thiết kế để rà soát, không áp dụng lên database. Commerce và Payment có database riêng; các UUID tham chiếu sang service khác chỉ là tham chiếu logic, không có foreign key xuyên database.

## Cơ sở nghiệp vụ

- Khách đăng nhập được hủy ngay đơn chưa xác nhận và chưa thanh toán; đơn cũ không đủ bằng chứng xác nhận đi qua admin duyệt.
- Admin xác nhận đơn riêng. COD đã xác nhận nhưng chưa giao cần yêu cầu admin duyệt. Yêu cầu bị từ chối có thể tạo yêu cầu mới; giữ toàn bộ lịch sử và không có hai yêu cầu PENDING cho cùng đơn.
- Hoàn toàn bộ số tiền thực nhận của đơn được duyệt hủy bằng chuyển khoản thủ công. Không bao gồm khách vãng lai hoặc đổi/trả.
- QR đến sau hủy cần đối soát và xử lý hoàn dựa trên khoản thực nhận. Không cho phép chỉ từ việc hủy suy ra đã hoàn tiền.

## Đề xuất Commerce V11

Các tên phiên bản phải kiểm tra lại ngay trước khi triển khai để tránh xung đột với migration mới của nhóm.

1. Thêm `customer_order.confirmation_state text`: `LEGACY_UNKNOWN`, `UNCONFIRMED`, `CONFIRMED`; thêm `confirmed_by_user_id uuid` và `confirmed_at timestamptz`. CHECK yêu cầu cả người xác nhận và thời điểm cho `CONFIRMED`, cả hai null với các trạng thái còn lại. Backfill toàn bộ dòng hiện tại `LEGACY_UNKNOWN` và đặt NOT NULL. Đơn mới dùng `UNCONFIRMED`; các luồng tạo đơn phải ghi rõ giá trị, không dựa vào giả định về trạng thái `PLACED` hay `PAID`.
2. Bỏ unique constraint `cancellation_request_order_id_key` theo tên thực tế trong schema, thay bằng unique partial index trên `(order_id) WHERE request_status = 'PENDING'`; thêm index `(order_id, requested_at DESC, cancellation_request_id DESC)`. Giữ các dòng và CHECK hiện tại, bổ sung ràng buộc idempotency sau khi contract định nghĩa khóa yêu cầu và phạm vi người dùng.
3. Đề xuất bảng `cancellation_operation` để lưu điều phối giữa service: `operation_id uuid PK`, `order_id uuid` FK nội bộ Commerce, `cancellation_request_id uuid` FK nội bộ nullable, `actor_user_id uuid`, `reason text`, `operation_status text`, `created_at`, `updated_at`, `last_error_code text` nullable. Status và index duy nhất cho thao tác chưa kết thúc cần khóa chặt sau khi xác định giao thức. Không lưu payload nhạy cảm.
4. Dự kiến bảng outbox Commerce bền vững cho các lệnh/sự kiện hủy được duyệt, với event ID duy nhất, operation ID, loại sự kiện, payload không chứa thông tin ngân hàng, thời gian tạo/gửi, số lần thử; unique idempotency theo operation và event type. Scope cuối phải chỉ rõ loại sự kiện và điều kiện retry.

## Đề xuất Payment V3

1. Đề xuất bảng `refund` với `refund_id uuid PK`, `order_id uuid NOT NULL`, `cancellation_request_id uuid` nullable, `source_key text NOT NULL UNIQUE`, `payment_attempt_id uuid` FK Payment nullable, `financial_transaction_id uuid` FK Payment nullable, `payment_exception_id uuid` FK Payment nullable, `amount_vnd bigint NOT NULL CHECK > 0`, `refund_status text NOT NULL`, thông tin ngân hàng được mã hóa, `provider_reference text` nullable, `proof_reference text` nullable, `failure_reason text` nullable, `created_at`, `updated_at`, `processed_by_user_id` và `processed_at` nullable. Ít nhất một nguồn tiền phải được xác định và kiểm tra trạng thái, số tiền thực nhận bằng transaction an toàn; quy tắc dữ liệu ngoại lệ cần làm rõ để không hoàn nhầm hoặc quá số tiền nhận.
2. Status đề xuất: `AWAITING_DETAILS`, `PENDING`, `REFUNDED`, `FAILED`. Định nghĩa ràng buộc trạng thái, retry thất bại, audit và lưu bằng chứng sau khi chốt contract; không để thay đổi thông tin chuyển khoản sau khi đã hoàn.
3. Bảng hoặc cột inbox điều phối Payment: event ID duy nhất, fingerprint payload, kết quả xử lý và thời gian để chống xử lý trùng; cập nhật trạng thái và ghi nhận việc cần xử lý cùng transaction nội bộ. Event IDs/cancellation ID là tham chiếu logic.
4. Không thay đổi migration V1/V2 hay xóa giao dịch, ngoại lệ và outbox có sẵn. Thông tin ngân hàng không ghi vào logs hoặc outbox; thiết kế mã hóa/quản lý khóa và phân quyền phải được duyệt trước triển khai.

## Tương thích và an toàn khi triển khai

- Các đơn cũ `LEGACY_UNKNOWN` không được mặc định cho hủy ngay; đơn đã hủy không thay đổi trạng thái, yêu cầu cũ được giữ nguyên. Không tự tạo hoàn tiền cho dữ liệu cũ khi chạy migration.
- Không tự coi giao dịch ngoại lệ là đủ điều kiện hoàn: chỉ xử lý sau đối soát và xác định số tiền/đơn/người nhận đáng tin cậy. Các khoản sai nội dung hoặc không khớp cần có đường xử lý thủ công.
- Xác nhận/hủy/duyệt phải khóa và kiểm tra lại trạng thái; phối hợp Payment phải xử lý trường hợp kết quả chưa biết, webhook đến trễ và service dừng giữa chừng. Không giả định một transaction bao trùm hai database.
- Nghiệp vụ API cũ, màn hình hiện tại, lịch sử đơn và phân quyền cần được kiểm tra tương thích trước khi chọn chính sách thay đổi.
- Test migration nâng cấp trên PostgreSQL cô lập với đơn cũ, yêu cầu cũ và giao dịch; test ràng buộc, đồng thời, retry, webhook trùng, số tiền và phân quyền.

## Phần còn phải hoàn thiện trước khi trình duyệt

Đây chưa phải DDL đầy đủ: cần thiết kế state machine và giao thức đồng bộ Payment/Commerce (bao gồm khóa trạng thái, kết quả timeout, recovery), mã hóa, xác định quyền truy cập, số tiền hoàn đối với giao dịch ngoại lệ, tên constraint thực tế và tương thích API. Sau đó lập DDL, backfill, rollback/khôi phục và dữ liệu kiểm thử cụ thể để người dùng phê duyệt trước khi viết migration thực thi.
