# Hướng dẫn tự test AI Stylist bằng Postman

Bộ file gồm:

- `StyleMind-AI-Stylist-Manual.postman_collection.json` — collection chạy từng request qua Gateway.
- `StyleMind-AI-Stylist-Local.postman_environment.json` — environment local, chỉ có placeholder; không chứa JWT hoặc API key.

## Trước khi gửi request

1. Import cả collection và environment vào Postman, sau đó chọn environment **StyleMind AI Stylist — Local**.
2. Kiểm tra Gateway đang chạy tại `http://localhost:8080`. Collection dùng prefix `/stylist`; Gateway sẽ route request sang Stylist.
3. Lấy **access token** từ Identity cho hai tài khoản khác nhau. Điền token A vào `customerAToken`, token B vào `customerBToken` trong environment local của Postman. Không dùng refresh token và không export environment có token.
4. Đảm bảo tài khoản A/B có quyền CUSTOMER (hoặc role được Stylist cho phép). Dữ liệu test chỉ là nhu cầu tổng hợp; không dùng thông tin cá nhân thật.
5. Trước khi Generate, xác nhận Stylist, PostgreSQL, Qdrant, Neo4j, Gateway, Commerce/catalog fixture hoặc runtime đã được cấu hình và reachable. Nếu thiếu candidate/index/knowledge, Generate có thể trả lỗi cấu hình hoặc `NO_RESULT`; đó không phải bằng chứng model hoạt động.

## Cảnh báo trước bước Generate

Request 6 gọi endpoint Generate và có thể phát sinh request tới model provider tùy cấu hình runtime. Environment đặt `allowProviderCall=false`, vì vậy request bị chặn an toàn cho tới khi bạn chủ động đổi biến này thành `true`.

Theo bằng chứng hiện có trong `WAVE2_RUN_GUIDE.md`, bounded live smoke đã chạm trần ngân sách được duyệt (12 embedding / 23 countTokens / 6 generation). Collection này không reset meter, không dùng fixture tự động và không cấp thêm ngân sách. Để kiểm tra Generate trực tiếp, chỉ bật guard khi runtime đang dùng fixture/mock phù hợp hoặc đã có ngân sách live mới được duyệt. Nếu chưa chắc provider mode, hãy test các request 1–5 và 7 (sau khi có kết quả); đừng bật guard để thử vận may. Không gửi key vào Postman request/header.

Không chạy **Run collection**. Chạy từng request bằng nút **Send** theo thứ tự; request 6 chỉ gửi một lần. Sau timeout, 5xx hoặc response không terminal, dừng và ghi lại correlation ID; không gửi lại cùng request vì request có thể vẫn `PROCESSING` hoặc đã tiêu thụ một provider call.

## Luồng test chính

| Bước | Request | Mục đích | Kết quả cần thấy |
|---|---|---|---|
| 1 | Tạo Stylist session | Mở session thuộc A | `201`; collection tự lưu `sessionId` |
| 2 | Gửi nhu cầu/tiêu chí | Lưu request, chưa chạy AI | `201`, `request_status=RECEIVED`, `generation_started=false`; tự lưu `requestId` |
| 3 | Đọc lại request | Xác minh nội dung và ngân sách đã lưu | `200`; vẫn `RECEIVED`, chưa generation |
| 4 | Đọc message history | Kiểm tra API lịch sử riêng của session | `200`; trả `items` và `next_cursor`. Intake request không mặc định là chat message |
| 5 | Safety check | Chứng minh client không được chọn model | `400 INVALID_REQUEST`; đây là request âm, không gọi provider |
| 6 | Generate một lần | Backend xử lý retrieval, outfit và model | `200` terminal `SUCCEEDED` hoặc `NO_RESULT`; nếu thành công phải đúng 1 TOP + 1 BOTTOM mỗi recommendation |
| 7 | Đọc recommendation list | Kiểm tra dữ liệu đã lưu | `200`; các ID, nội dung, timestamp, variant, slot, lý do và thứ tự phải khớp Generate |
| 8 | Đọc detail | Xem thông tin item và Commerce hiện tại | Chỉ chạy khi `SUCCEEDED`; `price_semantics=CURRENT_COMMERCE_DATA_NOT_HISTORICAL`; có thể thiếu giá hiện tại nếu Commerce không cung cấp |
| 9 | Replay terminal Generate | Xác minh cùng kết quả được trả lại | Cùng status/ID/nội dung/timestamp/items như lần đầu. Postman không tự đo provider-call count; kiểm tra đó thuộc integration harness |
| 10–11 | A/B ownership | B thử đọc request và recommendation của A | Cả hai trả `404 RESOURCE_NOT_FOUND`, tránh tiết lộ tài nguyên của A |

### Luồng xử lý phía server

Khách mô tả nhu cầu và tiêu chí, không chọn sẵn sản phẩm hoặc outfit. Khi gửi request, backend chỉ lưu trạng thái `RECEIVED`. Khi chủ động gọi Generate, Stylist chạy pipeline hiện tại: retrieval → Commerce hydration/recheck → Neo4j knowledge → engine tạo đúng một TOP + một BOTTOM → model structured generation → parser/whitelist → Commerce final recheck → lưu recommendation/items nguyên tử vào PostgreSQL. Sau đó GET list/detail chỉ đọc dữ liệu; detail lấy giá/trạng thái Commerce hiện tại, không phải lịch sử tại thời điểm tạo.

## Cách xử lý các kết quả khác kỳ vọng

- **401**: token hết hạn/sai; đăng nhập lại và cập nhật access token A/B.
- **403**: token hợp lệ nhưng role không được phép.
- **404 khi A đọc ID của chính A**: kiểm tra environment đã chọn, ID tự lưu và Gateway route.
- **404 khi B đọc ID của A**: đây là kết quả bảo mật mong muốn.
- **400 ở request 5**: kết quả mong muốn; body có field ngoài contract.
- **`NO_RESULT`**: pipeline không tìm được outfit hợp lệ theo catalog/constraints/budget. Không phải model/provider PASS. Không replay lại để cố tìm kết quả khác; request terminal trả lại kết quả đã lưu.
- **503/configuration error**: provider, catalog, Qdrant, Neo4j hoặc dependency chưa sẵn sàng. Không retry liên tiếp; lưu status, `code` và `correlationId` để kiểm tra log đã khử nhạy cảm.
- **Timeout/5xx khi Generate**: dừng, không gửi lại request. Kiểm tra trạng thái bằng GET request/list trước; xử lý recovery theo vận hành hiện có.

## Giới hạn của Postman evidence

Postman chứng minh HTTP contract qua Gateway cho runtime bạn đang chạy. Nó không chứng minh Gemini/OpenAI đã được gọi nếu request dùng fixture, không đo chính xác số provider calls, không thay thế test transaction/concurrency và không chứng minh dữ liệu Commerce thật hay chất lượng phối đồ. Không đánh dấu AI flow live PASS chỉ từ các test script trong collection.
