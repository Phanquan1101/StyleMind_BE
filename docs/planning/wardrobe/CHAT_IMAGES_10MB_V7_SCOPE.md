# Chat image upload limit V7

Ngày 12/11/2026, chủ dự án duyệt 10 MB/ảnh, tối đa 5 ảnh/tin và request multipart 52 MB.

## Phạm vi đã duyệt

- Thêm `V7__increase_chat_attachment_size_limit.sql`.
- Thay CHECK `public.chat_attachment.chat_attachment_size_check` bằng `size_bytes BETWEEN 1 AND 10485760`.
- Dữ liệu ảnh hiện có tiếp tục hợp lệ. V1–V6 giữ nguyên checksum.
- Validator, multipart servlet, private storage và FE cùng nhận tối đa 10 × 1024 × 1024 byte/ảnh.
- Giới hạn 5 ảnh, định dạng JPEG/PNG/WebP, 40 megapixel, quyền đọc ảnh riêng tư và idempotency tiếp tục áp dụng.

## Thực hiện và kiểm chứng

- [x] Viết kiểm thử biên 10 MB và trường hợp vượt 1 byte.
- [x] Chạy kiểm thử thất bại trên giới hạn cũ, cập nhật các lớp và V7, chạy lại.
- [x] Kiểm tra PostgreSQL cô lập nâng từ V6 lên V7, ảnh cũ và giới hạn mới.
- [x] Chạy regression Wardrobe và FE, typecheck/build.

Migration được thực thi trên DB cô lập trong kiểm thử. Khi chủ dự án build/chạy lại Wardrobe, Flyway áp dụng V7 trên DB của môi trường đó.

## Các file BE cập nhật

Trong `wardrobe-community-service/src/main/` gồm `resources/db/migration/V7__increase_chat_attachment_size_limit.sql`, `resources/application.properties`, `java/com/stylemind/wardrobecommunity/validation/chat/ChatImageRequestValidator.java`, `storage/chat/S3ChatImageStorage.java` và `controller/chat/ChatImageMessageController.java` cùng package gốc.

Trong `wardrobe-community-service/src/test/java/com/stylemind/wardrobecommunity/` gồm `ChatImageRequestValidatorTests.java`, `ChatImageLimitMigrationPostgresTests.java`, `ChatPersistencePostgresTests.java`, `FriendsPersistencePostgresTests.java`, `OutfitVisibilityMigrationPostgresTests.java`, `S3ChatImageStorageTests.java` và `ChatImagesApiPostgresIntegrationTests.java`.

Handover cập nhật `postman/EP06_CHAT_IMAGES_RUN_GUIDE.md` và `postman/StyleMind-Wardrobe-Chat-Images.postman_collection.json`. V6 migration test được ghim target 6 để tiếp tục kiểm chứng riêng hành vi V6.

## Bằng chứng kiểm thử 12/11/2026

- Toàn Wardrobe chạy 319 tests, 0 failures, 0 errors, 0 skipped.
- Test HTTP nhận multipart ảnh 10 MiB và trả size metadata đúng. File vượt 1 byte trả 413.
- Private storage được kiểm tra bằng AWS SDK với HTTP fixture cô lập, gồm đọc ảnh 10 MiB và từ chối vượt giới hạn.
- Migration V6 lên V7 giữ nguyên row ảnh cũ và checksum V1–V6, nhận đúng biên 10 MiB, chặn 0 byte và vượt 1 byte.
- FE chạy 22 tests cho lời mời, nhãn người chat, file ảnh, chat HTTP và realtime. Typecheck và production build đạt.
- Postman JSON parse thành công. File lớn trên cloud của môi trường người dùng cần kiểm tra sau khi rebuild Wardrobe.

Lệnh BE dùng Maven 3.9.16 cài sẵn với `-o -f wardrobe-community-service/pom.xml -Dmaven.compiler.proc=full test`. Máy đang dùng JDK 25 nên bật annotation processing qua tham số chạy test. Lệnh FE gồm `npm run test --` các suite liên quan, `npm run typecheck` và `npm run build`.
