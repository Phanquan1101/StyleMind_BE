# EP06 — Chốt nghiệp vụ Friends và chuẩn bị scope tiếp theo

**2026-10-28, Asia/Bangkok. Documentation only; Task 02/03 vẫn PARTIAL.** Owner đã cung cấp Git status hiện tại trước đợt sửa. Không tự chạy Git; giữ nguyên source/migration và mọi thay đổi trước đó trong Identity, Notification, Wardrobe, Postman.

## 1. Kết quả

Ghi nhận B22–B28 và xác nhận B8: không mời chéo/tự kết bạn; unfriend vô hiệu pending cũ; repeated accept không khôi phục quan hệ; cooldown 30 giây/sender/email; bỏ cap 3/20 mỗi giờ; pending đã SMTP tiếp nhận không gửi tiếp; hết hạn/gửi thất bại được gửi lại; bạn bè chỉ xem Outfit Bạn bè/Public. Xem [quyết định Owner](../../planning/wardrobe/EP06_FRIENDS_INVITATION_RULES_2026-10-28.md).

Schema V3 hiện không lưu kết quả gửi mail hoặc mốc vô hiệu hóa lời mời của pair. Đã soạn [scope V5 với DDL đầy đủ](../../planning/wardrobe/EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md) và [thiết kế API/orchestration](../../planning/wardrobe/EP06_TASK02_INVITATION_API_DESIGN.md). **Chưa duyệt schema/chi tiết contract, chưa thêm V5 vào migration directory và chưa implement public mutations.** Không dùng approval nghiệp vụ thay approval migration.

## 2. File thay đổi đúng phạm vi

Thêm bốn tài liệu:

- [Quyết định ngày 28](../../planning/wardrobe/EP06_FRIENDS_INVITATION_RULES_2026-10-28.md).
- [Scope V5](../../planning/wardrobe/EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md).
- [Thiết kế API](../../planning/wardrobe/EP06_TASK02_INVITATION_API_DESIGN.md).
- Báo cáo này.

Cập nhật chín tài liệu:

- [Plan](../../planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md).
- [Task 02 contract](../../planning/wardrobe/EP06_TASK02_FRIENDS_API_CONTRACT.md).
- [Handoff Friends](../../planning/wardrobe/EP06_FRIENDS_INTEGRATION_HANDOFF.md).
- [Dependency contract](../../planning/wardrobe/EP06_FRIENDS_DEPENDENCIES_CONTRACT.md).
- [Master root](../../../StyleMind_EP06_Coding_Spec_By_Task_v4.md).
- [Master trong bộ task](../../../StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md).
- [Index](../../../StyleMind_EP06_11_Task_Specs/00_START_HERE.md).
- [Task 02](../../../StyleMind_EP06_11_Task_Specs/TASK_02_manage_friends_api.md).
- [Task 03](../../../StyleMind_EP06_11_Task_Specs/TASK_03_manage_friends_tests.md).

Các prefix hiện hành lên v4.8; sáu suffix lịch sử v4.1 phải giữ nguyên. Functional Requirements/reference không sửa. Không đổi source, Postman JSON hoặc script kiểm thử đã có. Helpers/evidence mới chỉ nằm trong ignored `wardrobe-community-service/target/ep06-friends-rules-20261028`.

## 3. Kiểm tra thực hiện

Đã đọc governance/AGENTS, scope V3, Entity/Repository/Service/Controller Friends, policy/token tests và dependency contracts. Workbook Backlog tại đường dẫn AGENTS yêu cầu vẫn không tồn tại; dùng task/spec và quyết định Owner trong hội thoại, không sửa workbook.

Lệnh kiểm tra tài liệu:

```powershell
node wardrobe-community-service/target/ep06-friends-rules-20261028/verify-docs.cjs baseline
node wardrobe-community-service/target/ep06-friends-rules-20261028/verify-docs.cjs capture-legacy
node wardrobe-community-service/target/ep06-friends-rules-20261028/update-docs.cjs
node wardrobe-community-service/target/ep06-friends-rules-20261028/verify-docs.cjs
```

Kết quả kiểm tra tĩnh: **PASS — 9 tài liệu cập nhật, 4 tài liệu mới; không mất file, không có source/script/migration thay đổi; hai master giống hệt; 120 link hợp lệ; 6 suffix lịch sử giữ nguyên hash; không có lỗi kiểm tra.** Baseline trước sửa gồm 959 file ngoài target/thư mục riêng tư/secrets. Evidence chi tiết tại `target/ep06-friends-rules-20261028/summary.json`; không chứa giá trị secret/token.

Không chạy Java/Maven/Newman hoặc SQL trong lượt chỉ cập nhật tài liệu. DDL mới chưa được execute/verify trên PostgreSQL; không gọi đây là migration pass. Kiểm tra Docker chỉ cho thấy engine chưa chạy lúc kiểm tra, không khởi tạo container test.

## 4. Giới hạn và bước tiếp theo

Chờ duyệt scope V5 và thiết kế contract cụ thể, gồm lookup nội bộ theo email và cách retry khi kết quả gửi UNKNOWN/SENDING. Sau đó mới thêm migration/Entity/Repository, public orchestration, Swagger/Postman và test unit/HTTP/PostgreSQL/concurrency/regression/E2E bằng tài nguyên cô lập.

Bằng chứng gần nhất trước lượt tài liệu này vẫn là Wardrobe 263 pass, Notification 40 pass, Identity lookup 9 pass; full Identity 158 tests/15 failures baseline/0 errors/skips. Không sửa hoặc che lỗi baseline. Runtime Newman cũ 113 requests/305 assertions không chứng minh public invitation chưa triển khai. Không Compose, DB lâu dài, real email, cloud resource, commit/push; Share Outfit vẫn ON_HOLD.
