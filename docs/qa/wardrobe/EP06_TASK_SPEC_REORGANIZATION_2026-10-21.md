# EP06 — Cập nhật task/spec v4.1 sau pull

Ngày **2026-10-21 (Asia/Bangkok)**. Thực hiện yêu cầu sửa task/spec theo phần làm trước và phần chờ dependency, dựa trên source mới Owner đã pull lên dev 418c421. Đây là thay đổi tài liệu, không phải implementation hoặc phê duyệt schema/behavior mới.

## Phạm vi và dữ liệu đầu vào

- Owner tự chạy Git: stash toàn bộ công việc local, pull fast-forward, rồi khôi phục riêng task/spec. Git status được cung cấp có bộ tài liệu untracked; code tracked mới không có local modifications. Agent không chạy Git hoặc apply toàn stash.
- Đã đọc AGENTS.md, governance, 11 task, schema Outfit V1/V2, OutfitShare/SharedOutfit services, Identity batch contract và Notification invitation contract. Workbook đường dẫn cũ không có; dùng bảng phân công Owner đã gửi, không sửa workbook.
- Bảo toàn Functional Requirements, reference v3 và code/config/migration mới. Không phục hồi Flyway-disabled baseline cũ hoặc sửa phần của Khánh/Bình/Quân/Vi.

## Các file thêm/sửa

| Nhóm | File | Thay đổi |
| --- | --- | --- |
| Task riêng | StyleMind_EP06_11_Task_Specs/TASK_01_friendship_requirement_conflict.md đến TASK_11_chat_tests.md, giữ đúng 11 filename hiện có | Version 4.1, status, giai đoạn, điều kiện, làm trước/sau; cập nhật common rules và acceptance còn áp dụng |
| Index | [00_START_HERE.md](../../../StyleMind_EP06_11_Task_Specs/00_START_HERE.md) | Bảng 11 task và thứ tự theo dependency; phần đã có/chưa đủ sau pull |
| Root master | [StyleMind_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_Coding_Spec_By_Task_v4.md) | Đồng bộ đủ 11 task v4.1; tên file giữ nguyên để không mất đường dẫn cũ |
| Package master | [MASTER_EP06_Coding_Spec_By_Task_v4.md](../../../StyleMind_EP06_11_Task_Specs/MASTER_EP06_Coding_Spec_By_Task_v4.md) | Cùng nội dung với root master, index dùng path từ repo root |
| Kế hoạch | [EP06_WORK_PLAN_AND_PROGRESS.md](../../planning/wardrobe/EP06_WORK_PLAN_AND_PROGRESS.md) | Inventory có dẫn chứng source, thứ tự, handoff, quyết định và evidence limits mới |
| Báo cáo | File này | Phạm vi edit và kết quả kiểm chứng tài liệu |

Các file task được sửa trong StyleMind_EP06_11_Task_Specs:

- TASK_01_friendship_requirement_conflict.md
- TASK_02_manage_friends_api.md
- TASK_03_manage_friends_tests.md
- TASK_04_wardrobe_share_migration.md
- TASK_05_wardrobe_sharing_api.md
- TASK_06_authorization_all_resources.md
- TASK_07_wardrobe_sharing_tests.md
- TASK_08_chat_migrations.md
- TASK_09_chat_session_api.md
- TASK_10_chat_messages_api.md
- TASK_11_chat_tests.md

## Điều chỉnh có ý nghĩa

- Giữ nguyên ID backlog và PIC Tín, không tạo task mới/renumber. Task 01 đã DONE review; Task 06 A READY nhưng chưa code; Task 04/08 DEFERRED; các API/tests khác chưa hoàn tất.
- Tách Task 06 A policy/unit không I/O để làm ngay, B fact loading/endpoint integration để sau. Xong A chưa phải toàn task Done; canChat guard có thể bàn giao riêng khi được kiểm chứng.
- Tách Task 05 A core và B public/shared content. A cùng owner guard mở đường cho Task 02/03; B và resource coverage mở đường Task 07. Không giả lập Wardrobe invitation hay public contents để nhận Done.
- Task 04/08 không còn hướng dẫn tự tạo Wardrobe nền hoặc tái sử dụng V1/V2 EP06 đã rút; V1/V2 hiện tại thuộc Outfit. Chờ chốt đại diện/key/settings toàn tủ và compatibility, rồi duyệt scope/version phù hợp.
- Ghi đúng dependency có một phần: Outfit/Catalog đã có source; Identity lookup chưa có verification; Notification mới mời từng Outfit; source JPA và instruction JDBC cần chốt trước persistence. Không coi source là owner approval.
- Giữ hai rule đã chốt: PRIVATE mời email → PROTECTED; PUBLIC bật lại dùng link cũ. Không hỏi lại hoặc áp default token/cap/lifecycle từ OutfitShare.
- Task 09→10→11 có thể làm song song nhánh hoàn tất resource coverage/Task 07 khi schema, current grant/identity, canChat và quyết định Chat sẵn sàng. Không bắt đợi toàn bộ Commerce/AI/Payment.

## Kiểm chứng và giới hạn

Commands thực dùng: rg --files/rg -n để inventory; PowerShell ReadAllText/regex để đối chiếu task/metadata/links, Get-FileHash và SHA256 để so sánh source/protected inputs; tái sinh master từ index và 11 task. Kết quả kiểm tra cuối được ghi bên dưới sau khi chạy.

Không chạy Maven, PostgreSQL, Compose, Postman HTTP hoặc persistent DB trong đợt tài liệu này; không báo test ứng dụng pass từ việc kiểm tra spec. Source tests mới chỉ được inventory, chưa xác minh runtime. Các kết quả 13/35 tests của baseline cũ không được gán cho code vừa pull.

### Kết quả kiểm tra cuối

- **16 tài liệu**, **26 local links hợp lệ**, không missing link/U+FFFD hoặc các instruction lỗi thời đã xác định.
- Đủ **11 ID 01–11**, mỗi task có version 4.1, trạng thái, phần làm trước/sau và prerequisite; index có đúng 11 dòng task.
- Hai master giống nhau và chứa nguyên nội dung cả 11 task; Task 05 A/B và Task 06 A/B được đồng bộ sau chỉnh sửa.
- Checksum tổng hợp của **446 file Java/SQL/properties/pom** trước/sau giống nhau: 608936A43E73CC75003D42E0B16D3D26A81EFCC4B2F63F572719F76F747EBC06.
- AGENTS.md, Functional Requirements và reference v3 giữ nguyên checksum. Kết quả này chứng minh giới hạn thay đổi tài liệu, không chứng minh runtime/business tests pass.
