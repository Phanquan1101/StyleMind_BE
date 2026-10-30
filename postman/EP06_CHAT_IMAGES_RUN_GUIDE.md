# EP06 Chat Images — Postman run guide

**2026-10-29 — actual B2 PASS:** image collection **28 requests / 66 assertions / 0 failures** chạy qua Gateway/backend tới bucket Private thật; tổng năm collections 123/325. [Setup IDE/Docker](../docs/operations/wardrobe/EP06_BACKBLAZE_B2_SETUP.md), [verification](../docs/qa/wardrobe/EP06_BACKBLAZE_VERIFICATION_2026-10-29.md). Collection/API giữ nguyên. Opt-in `-LiveBackblaze` dùng fixture DB riêng; không chạy mutation collection trên dữ liệu dùng lâu dài. Counts/checkpoint fake ngày 27 bên dưới là lịch sử, không thay live cloud evidence.

**2026-10-27.** Import [collection](StyleMind-Wardrobe-Chat-Images.postman_collection.json) và [blank environment](StyleMind-Chat-Images.postman_environment.example.json). **28 requests / 56 scripts / 4 folders**. [Contract](../docs/planning/wardrobe/EP06_TASK10_CHAT_IMAGES_CONTRACT_PROPOSAL.md), [runtime harness](../scripts/ep06-chat-runtime/README.md).

## Fixture và biến

Ba CUSTOMER A/B/C khác nhau, A–B là active friends, C ngoài chat; dùng fixture có thể bỏ. Điền accessTokenA/B/C riêng trong Current Values; imagePath là absolute path tới một PNG nhỏ dành cho test. Shared file không chứa token/path cá nhân. Server cần storage private đã cấu hình, hoặc chạy harness dùng S3 HTTP fake; không tự chuyển sang lưu file local.

allowChatImageTest=false và allowUnfriendTest=false mặc định. Cờ đầu cho phép mở/tạo chat và upload; cờ thứ hai riêng cho DELETE friendship. Token sau Identify được capture cùng account ID; request dùng captured token. Không export collection/environment đã điền token.

## Thứ tự

1. Folder 00 xác nhận danh tính A/B/C qua Identity thật; state cũ được reset.
2. Bật allowChatImageTest=true, chạy 01 xác nhận Friendship và mở/reuse session.
3. Folder 02 gửi image-only, retry 200 cùng message, đổi caption/text mode conflict, A/B đọc bytes riêng, C/chat khác bị chặn; 5 ảnh thành một message, 6 ảnh/sai MIME/blank caption bị chặn. Retry cuối giữ nguyên message đầu.
4. Folder 03 tùy chọn: thêm allowUnfriendTest=true, fresh pair preflight rồi hủy kết bạn; hai bên không đọc lại ảnh và retry không bypass quyền. Collection không tự refriend.

imagePath dùng PNG vì collection cố ý kiểm tra PNG signature và case giả MIME image/jpeg. JPEG/WebP và size/40MP boundaries có automated backend tests; không dùng ảnh cá nhân. Thay imagePath trong script file part bằng path môi trường sau guards, không gắn cứng file máy tác giả.

## Multipart contract

POST /api/v1/chats/{chatId}/messages, multipart/form-data:

| Part | Giá trị |
| --- | --- |
| message | JSON, Content-Type application/json; client_message_id UUID và content string tùy chọn/null |
| images | File part lặp 1–5 lần; thứ tự upload là display order |

metadata không cần filename. Ảnh tối đa 5 MiB và 40 megapixel; JPG/JPEG/PNG/WebP được nhận dạng/decode từ bytes. Omit/null content để image-only; supplied caption phải nonblank và <=4.000 code points. Không nhận URL/key/sender từ client. JSON text endpoint vẫn giữ contract riêng; client key chung namespace theo chat + sender.

201 mới, 200 identical ordered retry, 409 IDEMPOTENCY_CONFLICT; 400 malformed image/metadata/count/40MP; 413 servlet multipart size; 401/403 auth; 404 inaccessible image/chat; 503 private storage unavailable hoặc processor busy.

GET /api/v1/chats/{chatId}/attachments/{attachmentId}/content trả bytes qua backend, đúng MIME, no-store và nosniff, filename server-generated. Không trả key/URL cloud. Recheck quyền sau storage read, unfriend chặn cả hai và giữ ảnh/history; refriend khôi phục.

## Verification

Collection runtime trong harness đã thực chạy qua Gateway → Wardrobe thật, Identity/JWKS thật, DB disposable và SDK S3 → HTTP fake; cloud provider thật chưa được kiểm thử. Tài liệu evidence cuối ghi counts/snapshot; không coi fake là live S3/R2 pass. Swagger JSON có cả JSON và multipart cùng POST, tag Chat Images và endpoint read.

Actual Newman result: **28 requests/66 assertions, 0 failures**, 2026-10-27. [Báo cáo ảnh/runtime](../docs/qa/wardrobe/EP06_TASK10_IMAGES_AND_RUNTIME_IMPLEMENTATION_REPORT.md). Gate backend **248 tests, 0 skipped**. Scripts đóng scope IIFE để chạy ổn định cả folder và collection; shared opt-ins/tokens giữ mặc định false/blank.
