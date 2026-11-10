# Đề xuất VietQR: thanh toán trước khi tạo đơn

Trạng thái: đề xuất để duyệt phạm vi migration; chưa thực thi. Chỉ áp dụng cho VietQR mới. COD và các đơn VietQR đã tồn tại tiếp tục dùng luồng cũ cho đến khi xử lý xong; không xóa hoặc backfill đơn cũ. Trước khi tạo migration kiểm tra lại số phiên bản với nhánh hiện tại.

## Luồng đã thống nhất

1. Khách chọn sản phẩm và địa chỉ, hệ thống tạo hồ sơ thanh toán tạm và giữ hàng tối đa 15 phút. Nếu không đủ hàng thì không tạo mã QR. Hồ sơ tạm không xuất hiện trong danh sách đơn hàng.
2. Trang QR có nút Hủy thanh toán, bao gồm lúc đang chờ tạo QR. FE chỉ báo đã hủy sau khi máy chủ xác nhận. Hủy thành công giải phóng hàng một lần, không tạo đơn, QR cũ không còn được hệ thống chấp nhận để tạo đơn.
3. Sau khi Payment xác nhận đã nhận đúng tiền, Commerce tạo đúng một đơn PAID từ ảnh chụp thông tin mua hàng. FE tra cứu bằng mã phiên thanh toán trong lúc chờ, và chỉ chuyển sang chi tiết đơn khi có mã đơn.
4. Hủy/nhận tiền/hết hạn cạnh tranh: các giao dịch khóa cùng hồ sơ; không suy đoán kết quả khi mất kết nối. Nếu khách chuyển tiền sau khi hủy hoặc hết hạn, hoặc khi kết quả không thể tạo đơn an toàn, ghi nhận khoản nhận và mở ngoại lệ đối soát; không tự tuyên bố đã hoàn tiền. Cần trang/admin xử lý thủ công sau.
5. Sau khi có đơn đã thanh toán, quy tắc hủy và hoàn tiền đã chốt trước đây áp dụng; đang giao không cho hủy. Phần thực hiện hoàn tiền thủ công vẫn là công việc riêng.

## Commerce V12 — phạm vi DDL đề xuất

Thêm các bảng sau; giữ nguyên V1–V11 và dữ liệu hiện tại:

```sql
CREATE TABLE public.qr_checkout (
    checkout_id uuid PRIMARY KEY,
    customer_id uuid,
    guest_session_hash varchar(64),
    checkout_key text NOT NULL,
    checkout_status text NOT NULL,
    total_vnd bigint NOT NULL CHECK (total_vnd > 0),
    shipping_snapshot jsonb NOT NULL CHECK (jsonb_typeof(shipping_snapshot) = 'object'),
    payment_reference_id uuid NOT NULL UNIQUE,
    order_id uuid UNIQUE REFERENCES public.customer_order(order_id),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK ((customer_id IS NOT NULL AND guest_session_hash IS NULL) OR
           (customer_id IS NULL AND guest_session_hash IS NOT NULL)),
    CHECK (checkout_status IN ('PREPARING','AWAITING_PAYMENT','CANCELLED','EXPIRED','ORDER_CREATED','RECONCILIATION_REQUIRED')),
    CHECK (expires_at > created_at),
    CHECK ((checkout_status = 'ORDER_CREATED' AND order_id IS NOT NULL) OR
           (checkout_status <> 'ORDER_CREATED' AND order_id IS NULL))
);
CREATE UNIQUE INDEX qr_checkout_customer_key_uq ON public.qr_checkout(customer_id, checkout_key) WHERE customer_id IS NOT NULL;
CREATE UNIQUE INDEX qr_checkout_guest_key_uq ON public.qr_checkout(guest_session_hash, checkout_key) WHERE guest_session_hash IS NOT NULL;
CREATE INDEX qr_checkout_expiry_idx ON public.qr_checkout(expires_at) WHERE checkout_status IN ('PREPARING','AWAITING_PAYMENT');

CREATE TABLE public.qr_checkout_item (
    checkout_id uuid NOT NULL REFERENCES public.qr_checkout(checkout_id),
    variant_id uuid NOT NULL REFERENCES public.product_variant(variant_id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price_vnd bigint NOT NULL CHECK (unit_price_vnd > 0),
    product_snapshot jsonb NOT NULL CHECK (jsonb_typeof(product_snapshot) = 'object'),
    reservation_status text NOT NULL CHECK (reservation_status IN ('ACTIVE','CONSUMED','RELEASED')),
    PRIMARY KEY (checkout_id, variant_id)
);
```

`payment_reference_id` là định danh logic được tạo trước, không phải bản ghi order; được dùng làm khóa liên kết Payment và mã đơn tương lai. Payment hiện lưu `order_id` là UUID logic, không có FK xuyên database: bổ sung hợp đồng để hiểu nó là checkout ID đối với phiên QR mới; không đổi dữ liệu Payment cũ. Trước khi triển khai cần kiểm tra các consumer và không dùng mã này trong API đơn hàng cho tới khi bản ghi đơn thật tồn tại. Hàng được giữ trong cùng giao dịch với hồ sơ bằng cập nhật có điều kiện `variant_inventory`; giải phóng/tiêu thụ cùng khóa hồ sơ và trạng thái item. Snapshot phải được tạo bởi BE, không tin giá hoặc địa chỉ do client gửi, và chỉ lấy dữ liệu cần thiết cho đơn.

## Xử lý kỹ thuật và giới hạn

- Payment outbox và Commerce inbox tiếp tục chống xử lý trùng theo event; cần bổ sung nhánh nhận event cho checkout mới, xác minh method, amount, owner và reference; tạo đơn, lịch sử, trừ tồn và ghi inbox trong một giao dịch Commerce.
- Payment tạo QR idempotent theo checkout ID; mất kết quả phải tra lại cùng khóa. Hủy trong lúc tạo QR cần lưu trạng thái hủy bền vững, kiểm tra lại trước khi trả QR, và không tạo phiên mới cho checkout đã hủy.
- Chuyển khoản thật không thể thu hồi chỉ bằng hủy QR. Giao dịch đến trễ cần lưu vào bảng giao dịch/ngoại lệ Payment hiện có và chuyển cho admin đối soát; không đánh dấu hoàn tiền hay tạo đơn giả.
- Bảo vệ guest bằng guest session, owner A/B isolation, rate limiting theo cơ chế hiện có; không lưu token, OTP hoặc thông tin ngân hàng trong snapshot/log. Kiểm tra cạnh tranh giữ hàng, hủy, webhook, hết hạn, chuyển khoản trùng, timeout và mất kết nối với PostgreSQL cô lập.
- Phạm vi này không thêm bảng Payment mới và chưa triển khai hoàn tiền; nếu phân tích/kiểm thử cho thấy cần đổi schema Payment hoặc DDL trên, phải đưa scope sửa đổi cụ thể trước khi viết migration. Không triển khai lên DB lâu dài trong bước phát triển.
