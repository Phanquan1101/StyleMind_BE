# Tài khoản test và công cụ demo (CHỈ DÙNG LOCAL)

> Mọi thứ trong tài liệu này chỉ dành cho máy local/demo. Mật khẩu chung bên dưới là mật khẩu công khai, **không dùng ở môi trường thật**. Script gọi API qua Gateway, không chạm trực tiếp vào database.

## 1. Chạy stack

Yêu cầu: Docker Compose v2, file `.env` đã điền (copy từ `.env.example`), Node 18+ (script dùng `fetch` có sẵn).

**Khuyên dùng cho demo — thư gửi vào Mailpit (không gửi ra ngoài, đọc OTP trực tiếp):**

```powershell
cd StyleMind_BE
docker compose -f docker-compose.yml -f docker-compose.mailpit.yml --project-name stylemind-task4 up -d --build
```

**Không kèm Mailpit — dùng SMTP trong `.env` (ví dụ Mailjet thật):**

```powershell
docker compose --project-name stylemind-task4 up -d --build
```

Lưu ý khi dùng SMTP thật: thư OTP đi ra Internet, và gửi từ địa chỉ `@gmail.com` qua Mailjet rất dễ bị từ chối/vào spam (DMARC). Script seed cần đọc OTP nên **bắt buộc chạy kèm Mailpit**.

Xem thư (OTP, lời mời kết bạn): http://localhost:8025

Frontend:

```powershell
cd StyleMind_FE
npm install        # lần đầu
npm run dev        # http://localhost:3000 (cần file .env, copy từ .env.example)
```

Lần đầu mỗi service khởi động nguội mất 30–60 giây và lần gọi API đầu tiên chậm (vài giây). Nên mở thử `/shop`, đăng nhập và `/admin` một lượt trước khi demo.

Cổng 5433 (Postgres) trùng với dự án khác trên máy? Tạo file override riêng ngoài repo, ví dụ `ports: !override ["127.0.0.1:5434:5432"]` cho service `postgres`, rồi thêm `-f` file đó vào lệnh trên.

## 2. Seed tài khoản test

```powershell
cd StyleMind_BE
node scripts/seed-test-data.cjs
```

- Gọi API qua Gateway `:8080`, lấy mã OTP từ Mailpit API `:8025`.
- **Idempotent:** chạy lại nhiều lần không tạo trùng (đăng nhập tài khoản đã có, bỏ qua địa chỉ/outfit/bạn bè đã có).
- Không tạo đơn hàng và giỏ hàng.
- Biến môi trường (tùy chọn): `GATEWAY_URL`, `MAILPIT_URL`, `FRONTEND_URL`, `SEED_PASSWORD` (mặc định `Stylemind@123`, tối thiểu 12 ký tự).

### Bảng tài khoản (mật khẩu chung: `Stylemind@123`)

| Email | Vai trò | Dữ liệu seed |
|---|---|---|
| `an@sm.test` | Khách | Tên An, SĐT 0912345678, 165 cm / 52 kg, địa chỉ mặc định, bạn của Bình; outfit **"An - Riêng tư"** (PRIVATE, 3 món) và **"An - Bạn bè"** (FRIENDS, 3 món) |
| `binh@sm.test` | Khách | Tên Bình, SĐT 0923456789, 175 cm / 68 kg, địa chỉ mặc định; outfit **"Bình - Công khai"** (PUBLIC, 3 món) |
| `chi@sm.test` | Khách | Tên Chi, SĐT 0934567890, 158 cm / 48 kg, địa chỉ mặc định; không có bạn, không có outfit |
| `admin@sm.test` | Quản trị | Đăng ký như khách rồi được nâng lên ADMIN (xem bên dưới) |

Cuối script in link công khai của "Bình - Công khai" (dạng `http://localhost:3000/shared/outfits/<token>`); mở được khi chưa đăng nhập.

### Về tài khoản admin

BE không có API tạo admin từ đầu. Admin đầu tiên chỉ có qua **bootstrap của Compose** (Identity tự tạo khi khởi động nếu `.env` có `STYLEMIND_BOOTSTRAP_ADMIN_ENABLED=true`, `STYLEMIND_BOOTSTRAP_ADMIN_EMAIL`, `STYLEMIND_BOOTSTRAP_ADMIN_PASSWORD`; xem `docs/operations/identity/FIRST_ADMIN_PROVISIONING.md`). Script đọc thông tin đó từ `.env` **lúc chạy** (không in ra), đăng nhập bằng admin bootstrap rồi dùng API `PUT /api/v1/admin/users/{id}/roles` để nâng `admin@sm.test` lên `ADMIN`.

- Nếu `.env` không có thông tin bootstrap, hoặc đăng nhập admin bootstrap lỗi, script bỏ qua bước này và ghi `skipped` trong bảng kết quả. Khi đó đăng nhập bằng admin bootstrap trong `.env`.
- Mật khẩu admin bootstrap **không** đổi theo `.env` sau lần tạo đầu tiên.

## 3. Giả lập webhook thanh toán QR (SePay)

SePay không gọi được vào `localhost`, nên dùng script này để "báo đã nhận tiền":

```powershell
cd StyleMind_BE
node scripts/simulate-sepay-webhook.cjs --reference SMXXXXXXXXXXXX --amount 746000
```

- `--reference`: mã tham chiếu hiển thị ở trang QR (hoặc trường `reference` của `POST .../payment-attempt`).
- `--amount`: số tiền đúng bằng số tiền cần thanh toán (VND). Sai số tiền → ghi `AMOUNT_MISMATCH`, đơn không chuyển PAID.
- `--duplicate`: gửi cùng một sự kiện hai lần (lần hai phải bị coi là trùng, không tạo thêm đơn).
- `--late`: ngày giao dịch sau khi QR hết hạn → ghi `LATE_PAYMENT`.
- `--event-id <n>`: dùng lại cùng id để thử chống trùng.
- `--checkout <id> --email <email>`: tự kiểm tra trạng thái checkout sau khi gửi (mật khẩu lấy từ `SEED_PASSWORD`, mặc định `Stylemind@123`).

Secret ký (`STYLEMIND_SEPAY_WEBHOOK_SECRET`) được đọc từ `.env` hoặc biến môi trường `SEPAY_WEBHOOK_SECRET` lúc chạy; script không in và không lưu secret.

Webhook luôn trả `200 {"success":true}` kể cả khi sự kiện bị ghi thành ngoại lệ, nên hãy nhìn kết quả ở trang QR/đơn. Sau khi webhook nhận, Payment ghi outbox và Commerce nhận trong khoảng 10 giây (cần `STYLEMIND_PAYMENT_EVENTS_ENABLED=true`), sau đó trang QR chuyển sang "Thanh toán thành công". QR chỉ có hiệu lực khoảng 20 phút.

## 4. Kịch bản thử nhanh

1. **Mua hàng COD:** đăng nhập `an@sm.test` → `/shop` → chọn sản phẩm, màu/size → thêm giỏ → `/checkout` → chọn địa chỉ mặc định, **Thanh toán khi nhận hàng** → xem đơn ở `/orders`.
2. **Mua hàng QR + webhook:** như trên nhưng chọn **VietQR** → trang QR hiện mã tham chiếu và số tiền → chạy `simulate-sepay-webhook.cjs` với đúng hai giá trị đó → trang tự chuyển sang "Thanh toán thành công" (đơn `PAID`).
3. **Admin duyệt đơn / hủy:** đăng nhập `admin@sm.test` → `/admin` → **Đơn hàng** → "Xác nhận đơn"; khách vào chi tiết đơn → "Yêu cầu hủy đơn" → admin vào **Yêu cầu** → "Duyệt yêu cầu".
4. **Admin sản phẩm:** `/admin` → **Sản phẩm** → "Thêm sản phẩm" (nhập cả Tồn kho, ảnh) → khách mở `/shop` thấy sản phẩm; sửa giá và kiểm tra giá mới trên trang chi tiết.
5. **Tủ đồ và chia sẻ:** `an@sm.test` → `/wardrobes` thấy 2 outfit; mở outfit → đổi chế độ chia sẻ; **Công khai** → nút "Sao chép liên kết công khai"; mở link ở cửa sổ ẩn danh.
6. **Bạn bè và chat:** `binh@sm.test` thấy `An` trong `/friends` → "Nhắn tin"; mở `an@sm.test` ở cửa sổ khác, tin nhắn đến ngay (WebSocket). `chi@sm.test` mời `an@sm.test` rồi xem thư ở Mailpit để thử luồng lời mời.

## 5. Làm sạch

`docker compose --project-name stylemind-task4 down` giữ lại dữ liệu. Muốn xóa toàn bộ dữ liệu test (kể cả tài khoản, đơn hàng) thì thêm `-v` — **lệnh này xóa volume Postgres**; admin bootstrap sẽ được tạo lại lần khởi động kế tiếp và cần chạy lại seed.

## 6. Dữ liệu mẫu cho dashboard Tổng quan (CHỈ DÙNG LOCAL)

Dashboard admin (`/admin`) cần đơn rải nhiều ngày mới có số liệu để xem. Script này tạo ~54 đơn mẫu trong 30 ngày qua cho `an@`, `binh@`, `chi@sm.test` (hoàn tất, đang giao, đang xử lý, chờ xác nhận, đã hủy; COD đã thu, VNPAY và VietQR đã trả; vài khoản hoàn tiền một phần):

```powershell
cd StyleMind_BE
node scripts/seed-test-data.cjs            # cần tài khoản seed trước
node scripts/seed-dashboard-demo.cjs       # tạo đơn mẫu (chạy lại thì bỏ qua, không tạo trùng)
node scripts/seed-dashboard-demo.cjs --reset   # xóa đúng các đơn mẫu rồi chạy lại lệnh trên nếu muốn tạo mới
```

- Vì phải lùi ngày tạo đơn / ngày nhận tiền (API không cho phép), script **ghi thẳng vào DB commerce** qua `docker exec <postgres> psql` (container mặc định `stylemind-postgres-1`, đổi bằng `POSTGRES_CONTAINER`). Không dùng cho môi trường thật.
- Đơn mẫu được đánh dấu bằng `checkout_key = 'demo-dashboard-NN'`. `--reset` chỉ xóa các đơn đó (kèm dòng hàng, địa chỉ, lịch sử trạng thái, sự kiện thanh toán, hoàn tiền của chúng); đơn thật không bị đụng.
- Không đổi tồn kho, giỏ hàng hay dữ liệu của payment-service. Ở trang Thanh toán/Đơn hàng của admin sẽ thấy các đơn mẫu lẫn với đơn thật.
- Muốn dashboard "sạch" như cửa hàng mới: chạy `--reset`.