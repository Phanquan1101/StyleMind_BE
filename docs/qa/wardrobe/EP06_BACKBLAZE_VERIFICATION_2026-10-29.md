# EP06 — Backblaze B2 verification, 2026-10-29

**PASS — local backend với cloud thật. Task 10 DONE_LOCAL (backend/cloud); Task 11 còn browser/UI.** Owner chọn bucket Private `stylemind-chat`, endpoint `https://s3.us-east-005.backblazeb2.com`, region `us-east-005`, và tự lưu key trong `.env` được ignore. Live test dùng PNG tổng hợp nhỏ, không dữ liệu/recipient thật.

## Scope

- Additive Docker environment wiring cho riêng Wardrobe; `.env.example` disabled.
- IDE helper xuất sáu biến ảnh ra `.env.chat.local` được ignore, không in secret/chạy app.
- Opt-in runtime `-LiveBackblaze`, default fake, regression trước smoke, DB/SMTP/JWT disposable, key chỉ cấp cho JVM Wardrobe.
- Exact-key/version cleanup cho ảnh test; không đổi adapter production, public contract, schema/migration, Outfit/service khác.

## Evidence

- PowerShell AST ba scripts: PASS.
- `docker compose config --quiet`: PASS, không khởi động container.
- IDE helper: PASS, sáu biến có mặt sau khi Owner lưu; không hiển thị giá trị.
- Wardrobe regression: **294 tests / 31 classes, 0 failures/errors/skipped**, PASS.
- Default fake runtime: **1 JUnit scenario**, PASS; 5 Newman collections, 7 runs, **123 requests / 325 assertions / 0 failures**. Evidence: `wardrobe-community-service/target/ep06-chat-runtime-fake-20261029011842/`.
- Live Backblaze: **1 JUnit scenario, 0 failures/errors/skipped**, 130.3 s; **123 requests / 325 assertions / 0 failures**. Command `./scripts/verify-ep06-chat-runtime.ps1 -LiveBackblaze -SkipRegression`, gate trên vừa pass cùng production source. Evidence: `wardrobe-community-service/target/ep06-chat-runtime-backblaze-20261029012436/`.
- SDK probe: Put/Get/so bytes/anonymous denied/Delete marker PASS. Production adapter + Gateway/JWT/STOMP: private image upload/read, caption/idempotency response, replay giữ cùng cloud object version, outsider C bị chặn; cả A/B mất đọc/gửi sau unfriend; refriend dùng lại chat/history/ảnh PASS.
- Năm Newman collections / bảy runs: Friends **28/89**, Sessions **30/81**, Messages **27/69**, Images **28/66**, Invitations main **8/16**, retry **1/2**, link **1/2** (requests/assertions). SMTP failure/retry chờ thật 31 giây; actual mail link acceptance, Register/verify/login/invitation/refriend E2E vẫn pass với cloud mode.
- Cleanup: **8 exact test keys, 9 data/delete-marker versions removed, 0 failed keys**. Harness kiểm tra không còn version/marker của từng key; **26 anonymous GET probes bị từ chối**, có kiểm tra lặp lại ảnh hiện hữu, không phải 26 ảnh riêng. JVM đã dừng trước cleanup; DB/SMTP test được dispose. Giữ Maven cache, không xóa tài nguyên khác.

Kết quả cấu trúc và runtime tách riêng: PowerShell AST/Compose quiet/Markdown links/example blank không thay actual HTTP/cloud execution. Safe consolidated evidence: [verification-results.json](../../../wardrobe-community-service/target/ep06-backblaze-20261029/verification-results.json). Không đưa raw request, token, key hoặc full Newman export vào bàn giao.

## File thay đổi trong lượt cloud này

- Runtime configuration: [.env.example](../../../.env.example), [docker-compose.yml](../../../docker-compose.yml). Chỉ thêm image config/credential mapping cho Wardrobe, default disabled.
- IDE/config reader: [configure-ep06-chat-ide.ps1](../../../scripts/configure-ep06-chat-ide.ps1), [Read-ChatImageEnvironment.ps1](../../../scripts/ep06-chat-runtime/Read-ChatImageEnvironment.ps1). File `.env.chat.local` được ignore, không tính vào source/handover secrets; Owner `.env` được đọc allowlist, agent không sửa.
- Verification: [wrapper](../../../scripts/verify-ep06-chat-runtime.ps1), [harness pom](../../../scripts/ep06-chat-runtime/pom.xml), [runtime scenario](../../../scripts/ep06-chat-runtime/src/test/java/com/stylemind/verification/ChatRuntimeIntegrationTests.java), [B2 helper](../../../scripts/ep06-chat-runtime/src/test/java/com/stylemind/verification/BackblazeRuntimeStorage.java), [runner guide](../../../scripts/ep06-chat-runtime/README.md).
- Operations/QA: [B2 setup](../../operations/wardrobe/EP06_BACKBLAZE_B2_SETUP.md), [cloud overview](../../operations/wardrobe/EP06_CHAT_IMAGES_CLOUD_SETUP.md), [cloud plan](../../planning/wardrobe/EP06_TASK10_CLOUD_IMAGE_STORAGE_PLAN.md), báo cáo này, [Postman image guide](../../../postman/EP06_CHAT_IMAGES_RUN_GUIDE.md). Không đổi JSON collection/environment vì contract không đổi; chính collection cũ đã chạy qua cloud thật.
- Progress/spec notice: [plan v4.11](../../planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md), [master root](../../../StyleMind_EP06_Coding_Spec_By_Task_v4.md), [master copy](../../../StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md), [index](../../../StyleMind_EP06_11_Task_Specs/00_START_HERE.md), [Task 10](../../../StyleMind_EP06_11_Task_Specs/TASK_10_chat_messages_api.md), [Task 11](../../../StyleMind_EP06_11_Task_Specs/TASK_11_chat_tests.md). Phần spec lịch sử được giữ nội dung; notices mới có ưu tiên trạng thái.

## Giới hạn

Full Identity gate trước đó có 15 lỗi baseline/161 tests; không rerun/sửa/coi gate đó pass trong lượt cloud. Notification/Identity/Gateway được package và dùng trong runtime, không gọi đó là full regression mới của chúng. Native runtime không thay browser/UI. IDE đã có file ảnh local nhưng Run/Debug vẫn cần nạp envFile và cấu hình DB/JWT local đúng; không tự khởi chạy IDE/ứng dụng trên DB dùng lâu dài.

Không deploy, Compose up, DB lâu dài hoặc email tới người thật; không chạy Git/commit/push. Không đổi business/schema/public API. B2 production compensation chỉ tạo delete marker, chưa là physical cleanup; worker/quy trình orphan reconciliation không được thêm trong lượt này. Không thay bucket lifecycle. Cloud I/O/commit outcome không rõ mà thiếu metadata không được xóa object lạ theo suy đoán; live run này không gặp lỗi I/O/cleanup. Browser/production acceptance và Share Outfit 04–07 vẫn ngoài scope.

[Hướng dẫn vận hành](../../operations/wardrobe/EP06_BACKBLAZE_B2_SETUP.md).
