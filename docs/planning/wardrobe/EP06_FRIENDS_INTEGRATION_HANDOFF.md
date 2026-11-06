# EP06 Friends — Handoff Identity và Notification

**2026-10-28 — v4.10. Task 02/03 DONE_LOCAL trong phạm vi backend.** Focused 56/56; Wardrobe regression 294/294 và Notification 40/40 pass, không skipped. Identity 161 tests còn 15 lỗi baseline, tên lỗi trùng chính xác probe cũ; không coi full Identity gate pass. Bốn JAR thật + Mailpit/DB disposable đã chạy invitation → Register/verify/login → accept → Chat → unfriend/refriend. Năm collection Newman 123 requests/325 assertions/0 failures; chưa live cloud hoặc browser. [Báo cáo verification](../../qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md). Các khối v4.8/v4.9 bên dưới là checkpoint trước verification, không là trạng thái hiện tại.


**2026-10-28 — v4.9, SOURCE WRITTEN / VERIFICATION PENDING.** Owner đã trả lời “Duyệt scope V5” và “Duyệt thiết kế API”. Đã viết V5/JPA, lookup email nội bộ Identity và public send/resend/incoming/accept; chưa coi Task 02/03 Done vì chưa build/test. Docker để sau theo Owner. Các checkpoint phía dưới không thay evidence của lượt code mới. [Contract hiện hành](EP06_TASK02_INVITATION_API_DESIGN.md), [scope đã duyệt](EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md), [báo cáo](../../qa/wardrobe/EP06_TASK02_INVITATIONS_IMPLEMENTATION_REPORT.md).


**Cập nhật chốt nghiệp vụ 2026-10-28 — v4.8:** [quyết định Owner ngày 28](EP06_FRIENDS_INVITATION_RULES_2026-10-28.md) đã chốt mời chéo, pending cũ khi unfriend, repeated accept và 30 giây; bỏ cap 3/20 mỗi giờ. PENDING còn hạn đã SMTP tiếp nhận không gửi lại; expired/failed được thử lại bằng link mới/hạn mới. [scope V5](EP06_TASK02_INVITATION_STATE_DATABASE_SCOPE.md) và [thiết kế API](EP06_TASK02_INVITATION_API_DESIGN.md) là đề xuất chưa duyệt, chưa tạo V5 hoặc public mutations. Các đoạn checkpoint trước bên dưới không thay quy tắc mới.


**Cập nhật 2026-10-28 — Owner giao bổ sung Identity/Notification:** đã có lookup email-verification cho account chưa có profile, endpoint gửi thư mời kết bạn và Wardrobe clients. [Contract dependency](EP06_FRIENDS_DEPENDENCIES_CONTRACT.md), [báo cáo](../../qa/wardrobe/EP06_FRIENDS_DEPENDENCIES_IMPLEMENTATION_REPORT.md). Notification 40 tests pass; Identity lookup 9 tests pass, nhưng full regression có lỗi cũ đã tái hiện trên baseline. Public send/resend/accept/incoming và rule chi tiết chưa triển khai. Cloud đề xuất [R2/setup](../../operations/wardrobe/EP06_CLOUDFLARE_R2_SETUP.md), chưa chọn account hoặc tạo bucket.

**Các đoạn ngày 24/27 bên dưới là checkpoint trước khi dependency được triển khai; dùng contract mới cho tích hợp hiện hành.**

**2026-10-24 — PROPOSED CONTRACT, NOT APPROVED.** Tài liệu để trao đổi với service owner; không gửi thông báo tự động và không thay đổi code của họ. Quy tắc nghiệp vụ B1–B4, B9–B11, B19 đã được Owner chốt tại [plan](EP06_WORK_PLAN_AND_PROGRESS.md).

## Identity — Quân

**Rà source sau pull 2026-10-27:** verified-email fact vẫn chưa có trong directory response. Thêm một điểm cần bảo đảm: query `AccountRepository.findInternalLookupRows` đang inner join `UserProfile`, trong khi Register không tạo profile. Vì vậy API phục vụ incoming/accept cần trả facts cho cả account chưa có profile; không yêu cầu user tạo profile hoặc sửa Register để lách dependency. Quân chọn extension/endpoint/projection tương thích. Đây là yêu cầu handoff đề xuất, chưa tự sửa source Identity; [báo cáo readiness](../../qa/wardrobe/EP06_POST_PULL_READINESS_REVIEW_2026-10-27.md) phân biệt source finding và runtime evidence.

Hiện `InternalAccountItem` trả `account_id`, `email`, `display_name`, `account_status` qua batch lookup. Chưa có `email_verified` hoặc `email_verified_at`. Không suy `ACTIVE` là bằng chứng email verified và không đọc database Identity.

**Đề nghị:** một contract nội bộ trả current email + verification cho account ID lấy từ JWT, bằng mở rộng tương thích batch lookup hiện có hoặc endpoint riêng do Identity owner chọn. Tối thiểu cần account ID, email hiện tại, bằng chứng verification rõ ràng; mô tả missing account, unavailable/timeout, auth service-token, normalization và khả năng email thay đổi. Không đưa email/verified từ request của client vào facts.

- Phần của Tín: gọi client Wardrobe, kiểm tra đúng email nhận lời mời, không accept khi facts thiếu/không verified; transaction local ghi invitation + Friendship.
- Phần Identity/frontend: người chưa có account đăng ký/verify/login theo luồng hiện có; frontend giữ tham chiếu lời mời và tiếp tục accept sau auth. GET link không tự kết bạn.
- Không sửa Register, JWT claim, OTP, Identity DB hoặc ghi token mời vào log. Chưa tuyên bố E2E register-resume đã chạy.

## Notification — Vi

Hiện `/internal/v1/outfit-share-invitations` gửi template mời xem Outfit. Không đổi nghĩa endpoint/template này thành mời kết bạn.

**Đề nghị:** endpoint nội bộ riêng, ví dụ `POST /internal/v1/friend-invitations` — route và DTO cần Vi duyệt. Request dự kiến chứa địa chỉ nhận, thông tin sender cần hiển thị, invitation ID, link frontend nhận lời mời và hạn dùng; auth bằng convention service-token hiện có. Không nhận template HTML hay callback URL tùy ý từ client công khai.

Kết quả đề xuất để review: `202` là SMTP accepted; `503` definite failure; timeout/unexpected response là unknown. Không diễn giải acceptance thành bảo đảm vào inbox và không tự retry unknown. Link mới/hạn mới theo B19 áp dụng cả khi resend gặp lỗi; invitation vẫn PENDING.

- Phần của Tín: commit DB trước HTTP, truyền payload đúng contract, trả delivery state trung thực và hỗ trợ resend riêng; không gửi email khi chỉ gọi duplicate invitation.
- Phần Notification: template mời kết bạn, validate link theo host frontend được cấu hình tin cậy, transport SMTP và result semantics; không lưu token vào log, không đọc DB Wardrobe.
- Test thực dùng Mailpit cô lập nếu/khí contract đã có, không gửi tới email người dùng thật. Mocks không chứng minh email runtime.

## API công khai của Tín — cần hoàn thiện trước Task 02

Route skeleton đã có trong plan; contract tiếp theo phải mô tả DTO/schema, principal, validation, pagination/errors, resend và transaction cho từng route. Cần chốt lời mời chéo, pending invitation có sẵn khi unfriend, repeated accept và giới hạn resend; không tự thêm block, từ chối, nhóm chat hay finite friend cap.

Đã triển khai phần A theo Controller → Service → Repository JPA trong Wardrobe, bảo toàn error conventions hiện có: list friends, own outgoing invitations và unfriend, kèm Swagger/OpenAPI, Postman JSON/run guide và unit/HTTP/PostgreSQL/negative authorization/concurrency. Task 02 vẫn PARTIAL; send/accept/resend/incoming và E2E chưa Done. Chi tiết tại [contract Task 02](EP06_TASK02_FRIENDS_API_CONTRACT.md).

## Trình tự không bị chặn bởi tích hợp

Task 01 đã được Owner duyệt và triển khai: policy/token, V3, Entity/Repository JPA và PostgreSQL tests; [báo cáo Task 01](../../qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md) ghi nhận 102 test pass, 0 skipped tại checkpoint đó. Task 02 A đã có ba API local; [báo cáo Task 02](../../qa/wardrobe/EP06_TASK02_IMPLEMENTATION_REPORT.md) ghi gate mới nhất 131 pass, 0 skipped. Friend display name dùng directory contract hiện có, không cần suy verification từ status. Accept/incoming integration đợi Identity verification facts; delivery integration đợi Notification friend-invitation contract. Không yêu cầu Khánh sửa Share Outfit cho hai User Story này.
