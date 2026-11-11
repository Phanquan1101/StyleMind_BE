# Checklist demo StyleMind (CHỈ DÙNG LOCAL)

Tài liệu đi kèm: [TEST_ACCOUNTS.md](TEST_ACCOUNTS.md) (tài khoản, seed, webhook giả lập). Mọi lệnh chạy trong thư mục `StyleMind_BE` (PowerShell), project Compose tên `stylemind`.

## A. Thứ tự chuẩn bị trước demo

| # | Việc | Lệnh / hành động |
|---|---|---|
| 1 | Giải phóng cổng 5433 nếu bị dự án khác giữ | `docker stop <container-postgres-khác>` (chỉ dừng, không xóa). Sau demo `docker start` lại |
| 2 | Dọn stack cũ và **reset volume DB** | `docker compose -p stylemind down -v` (xóa volume `stylemind_postgres-data`: mất toàn bộ tài khoản/đơn cũ) |
| 3 | Dựng stack **kèm Mailpit** (OTP vào Mailpit) | `docker compose -p stylemind -f docker-compose.yml -f docker-compose.mailpit.yml up -d --build` |
| 4 | Chờ các service lên (30–90 giây) | `docker compose -p stylemind ps` rồi `curl http://localhost:8080/actuator/health` |
| 5 | **Seed** tài khoản và dữ liệu mẫu | `node scripts/seed-test-data.cjs` (idempotent, in bảng tài khoản + link outfit công khai) |
| 6 | **Bỏ Mailpit, quay về SMTP Gmail** | `docker compose -p stylemind -f docker-compose.yml up -d notification-service` (không kèm file mailpit; chỉ tạo lại notification-service, đọc `STYLEMIND_SMTP_*` trong `.env`) |
| 7 | Chạy FE | `cd ..\StyleMind_FE; npm run dev` → http://localhost:3000 |
| 8 | **Khởi động nóng** (lần gọi đầu rất chậm) | xem mục B |
| 9 | Bật tunnel webhook (nếu demo thanh toán QR thật) | xem mục C |

Nếu bước 3 báo `additional privileges requested: pass "--allow=fs.read=..."` (buildx bake chặn đường dẫn tương đối trên Windows), chạy lại với đường dẫn tuyệt đối: `docker compose -p stylemind -f C:/MSS_PROJECT/StyleMind_BE/docker-compose.yml -f C:/MSS_PROJECT/StyleMind_BE/docker-compose.mailpit.yml up -d --build`.

`.env` phải có: `STYLEMIND_PAYMENT_EVENTS_ENABLED=true`, `STYLEMIND_SEPAY_WEBHOOK_SECRET`, `STYLEMIND_SEPAY_QR_*` (ngân hàng/số tài khoản/tên), `STYLEMIND_SEPAY_PAYMENT_CODE_PREFIX=SM`, và SMTP Gmail (`STYLEMIND_SMTP_HOST=smtp.gmail.com`, `PORT=587`, `AUTH=true`, `STARTTLS=true`, tài khoản + App Password, `STYLEMIND_EMAIL_FROM` = đúng địa chỉ Gmail đó).

## B. Khởi động nóng (làm 1 lượt trước khi bắt đầu)

Mỗi service JVM chạy nguội nên lần gọi đầu mất vài giây đến 15 giây. Thực hiện trên trình duyệt:

1. Đăng nhập `an@sm.test` / `Stylemind@123` (login đầu tiên ~3 giây).
2. Mở `/shop`, một trang chi tiết sản phẩm, thêm giỏ (không cần đặt), mở `/wardrobes`, `/friends`, `/messages`.
3. Đăng nhập `admin@sm.test` ở cửa sổ khác → `/admin` → bấm lần lượt **Đơn hàng**, **Sản phẩm**, **Yêu cầu** (admin tải cả catalog nên chậm ~5 giây lần đầu).
4. Gửi thử **một lời mời kết bạn** (gửi email lần đầu qua notification mất ~15 giây) rồi hủy hoặc bỏ qua.
5. Nếu có QR: tạo thử một checkout VietQR để Payment/Commerce chạy qua một lượt (đừng để lại QR đang chờ nếu không cần).

Khi demo: đăng nhập mới ngay trước giờ trình bày. Access token sống 15 phút; để tab quá lâu có thể bị đẩy về `/login`.

## C. Webhook SePay thật qua ngrok

### C1. Tunnel

```powershell
ngrok http 8080 --url=<ten-mien-tinh>.ngrok-free.app
```

- Trỏ vào **Gateway :8080** (không phải 8083).
- URL webhook dán vào SePay:

```
https://<ten-mien-tinh>.ngrok-free.app/payment/internal/v1/sepay/webhooks
```

(Gateway bỏ tiền tố `/payment` rồi chuyển sang payment-service.)

### C2. Cấu hình bên SePay (my.sepay.vn → Tích hợp Webhooks)

- Sự kiện: **Có tiền vào**. Tài khoản ngân hàng: đúng tài khoản trong `STYLEMIND_SEPAY_QR_ACCOUNT_NUMBER` và đã liên kết SePay.
- Kiểu chứng thực: **HMAC-SHA256**, Secret Key **trùng với `STYLEMIND_SEPAY_WEBHOOK_SECRET`**. SePay chỉ hiện secret một lần khi tạo; nếu SePay tự sinh secret thì chép vào `.env` rồi `docker compose -p stylemind -f docker-compose.yml up -d payment-service`.
- Kiểu dữ liệu: **JSON** (nếu chọn form-data/urlencoded, payment-service trả `415`).
- Nhận diện mã thanh toán (Công ty → Cấu hình → Cấu trúc mã thanh toán): tiền tố **`SM`**, phần đuôi gồm **12 ký tự chữ và số** (mã của StyleMind dạng `SM` + 12 ký tự hex in hoa, ví dụ `SMEDA9EC9A7C56`). Nếu SePay không nhận ra mã thì trường `code` rỗng và payment-service ghi `MISSING_PAYMENT_REFERENCE`, đơn **không** sang PAID.
- Đúng **nội dung chuyển khoản** hiển thị trên trang QR và đúng **số tiền**; chuyển trong thời gian đếm ngược (khoảng 20 phút), nếu trễ sẽ bị ghi `LATE_PAYMENT`.

### C3. Kiểm tra webhook đã tới chưa

| Cách | Lệnh / nơi xem | Ý nghĩa |
|---|---|---|
| ngrok inspector | http://127.0.0.1:4040 | Thấy request/response của SePay gọi vào |
| Log payment-service | `docker logs stylemind-payment-service-1 --since 15m 2>&1 \| Select-String "sepay/webhooks"` | `status=200` đã nhận; `401` sai chứng thực (secret/kiểu auth lệch); `415` không phải JSON; `400` body không hợp lệ |
| Nhật ký SePay | my.sepay.vn → Webhooks → Nhật ký | Mã HTTP và phản hồi SePay nhận được |
| Ngoại lệ thanh toán | xem lệnh bên dưới | Webhook đã tới nhưng không khớp đơn: `UNMATCHED_REFERENCE`, `AMOUNT_MISMATCH`, `LATE_PAYMENT`, `MISSING_PAYMENT_REFERENCE`... |
| Đơn | trang QR tự chuyển sang "Thanh toán thành công"; `/orders` hiện `PAID` | Sau webhook khoảng 10 giây (outbox) |

Webhook trả `200 {"success":true}` cả khi chỉ ghi ngoại lệ. Nếu log là 200 mà đơn không PAID thì xem bảng ngoại lệ:

```powershell
docker exec stylemind-postgres-1 sh -c 'psql -U "$POSTGRES_USER" -d stylemind_payment -c "select detected_at, exception_reason, received_reference, received_amount_vnd from payment_exception order by detected_at desc limit 10"'
```

Chạy ngrok làm lộ Gateway ra Internet (kể cả các đường `/internal/**` chỉ được bảo vệ bằng token/chữ ký). Chỉ bật trong lúc cần nhận webhook và tắt sau demo.

## D. Phương án dự phòng

### D1. SePay thật lỗi → giả lập webhook

Tạo QR trên trang thanh toán, lấy mã tham chiếu và số tiền hiển thị, rồi:

```powershell
node scripts/simulate-sepay-webhook.cjs --reference SMXXXXXXXXXXXX --amount <so-tien> --checkout <checkoutId> --email an@sm.test
```

Trang QR tự chuyển sang thành công như webhook thật. Cần `.env` có `STYLEMIND_SEPAY_WEBHOOK_SECRET` (script đọc lúc chạy, không in). Chi tiết tùy chọn ở `TEST_ACCOUNTS.md`.

### D2. Gmail SMTP lỗi → chuyển về Mailpit

```powershell
docker compose -p stylemind -f docker-compose.yml -f docker-compose.mailpit.yml up -d notification-service
```

OTP/lời mời hiện ở http://localhost:8025 (mở sẵn một tab). Quay lại Gmail: chạy lại lệnh bước 6 (không kèm file mailpit).

Nguyên nhân thường gặp khi Gmail không gửi: chưa bật xác minh 2 bước/App Password sai hoặc có khoảng trắng; `STYLEMIND_EMAIL_FROM` khác tài khoản Gmail đăng nhập; mạng chặn cổng 587; Gmail giới hạn số thư/ngày. Xem log: `docker logs stylemind-notification-service-1 --since 10m`.

### D3. Dữ liệu hỏng giữa chừng

Làm lại từ bước 2 (reset volume → up kèm Mailpit → seed). Mất khoảng 5–10 phút, đã có sẵn trong checklist.

### D4. Không demo được

- **AI Stylist:** hiện chưa chạy được trên máy này (thiếu cấu hình runtime), không đưa vào kịch bản.
- **Guest checkout:** BE đang tắt; khách chưa đăng nhập sẽ bị chuyển sang `/login`. Dùng tài khoản `an@sm.test` / `binh@sm.test` / `chi@sm.test`.

## E. Sau demo

```powershell
docker compose -p stylemind down          # giữ dữ liệu; thêm -v nếu muốn xóa
docker start <container-postgres-khác>    # bật lại dự án đã dừng ở bước A1
```

Tắt ngrok và đổi/thu hồi App Password Gmail nếu đã dùng cho demo.
