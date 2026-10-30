# EP06 Task 02 — Scope V5 cho trạng thái gửi và vô hiệu hóa lời mời

**2026-10-28. APPROVED_SCOPE — DONE_LOCAL.** Owner đã duyệt V5. Migration, Entity/Repository và constraints đã thực chạy trên PostgreSQL 18.6 disposable; test fresh/upgrade V4→V5 bảo toàn Outfit/Chat/Friends/checksum pass, Wardrobe regression 294 pass. V1–V4 không đổi. Chưa áp DB lâu dài. [Báo cáo](../../qa/wardrobe/EP06_TASK02_03_VERIFICATION_REPORT_2026-10-28.md). Các mô tả chuẩn bị/NOT RUN còn lại bên dưới chỉ là checkpoint trước lượt verification.

## 1. Thay đổi đã duyệt

Chỉ thay hai bảng Friends trong `stylemind_wardrobe_community`, không thêm bảng:

| Bảng | Bổ sung | Mục đích |
| --- | --- | --- |
| `friend_invitation` | `delivery_state` | Phân biệt chưa xác định, đang thử gửi, SMTP đã tiếp nhận và gửi thất bại; PENDING không thay thế kết quả email |
| `friend_invitation` | `delivery_attempt_id` | Mã UUID từng lần thử; kết quả về muộn chỉ cập nhật đúng lần gửi, không ghi đè token/lần mới |
| `friend_invitation` | `last_delivery_attempt_at` | Cooldown 30 giây cho cùng sender/email, kể cả lần thử thất bại/timeout |
| `friend_invitation` | Cho phép `status = INVALIDATED` | Ghi rõ lời mời bị vô hiệu hóa; không giả lập hết hạn 7 ngày bằng cách sửa deadline |
| `friendship` | `invitations_invalid_before` | Giữ mốc vô hiệu hóa lời mời cũ qua cả lần unfriend rồi refriend; clear `unfriended_at` không làm lời mời cũ sống lại |

V3 có unique PENDING theo sender/email và unique Friendship theo canonical pair; tiếp tục sử dụng các constraint này. Không thêm counter hoặc hạn mức 3/20 mỗi giờ. Không lưu raw token, URL email, nội dung thư hoặc lỗi provider.

Mốc vô hiệu hóa trên Friendship bảo vệ ngay ở bước accept khi đã biết hai account ID. Không cần gọi Identity khi đang khóa row hoặc chỉ để hoàn tất unfriend. Nếu chưa thể gắn một lời mời theo email với account nhận, có thể chưa cập nhật vật lý `status` của row đó ngay; quyền accept/incoming phải kiểm tra mốc của cặp và từ chối ngay, không phụ thuộc việc materialize `INVALIDATED` sau đó.

## 2. DDL được duyệt

Tên migration: `V5__track_friend_invitation_delivery_and_invalidation.sql`. Khi bắt đầu implementation phải kiểm tra lại version kế tiếp; không ghi đè migration của thành viên khác.

```sql
ALTER TABLE public.friend_invitation
    ADD COLUMN delivery_state text NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN delivery_attempt_id uuid,
    ADD COLUMN last_delivery_attempt_at timestamptz;

ALTER TABLE public.friend_invitation
    ADD CONSTRAINT friend_invitation_delivery_state_check
        CHECK (delivery_state IN ('UNKNOWN', 'SENDING', 'SMTP_ACCEPTED', 'FAILED')),
    ADD CONSTRAINT friend_invitation_delivery_attempt_uq UNIQUE (delivery_attempt_id),
    ADD CONSTRAINT friend_invitation_delivery_attempt_check CHECK (
        (delivery_attempt_id IS NULL
            AND last_delivery_attempt_at IS NULL
            AND delivery_state = 'UNKNOWN')
        OR
        (delivery_attempt_id IS NOT NULL
            AND last_delivery_attempt_at IS NOT NULL)
    ),
    ADD CONSTRAINT friend_invitation_delivery_time_check CHECK (
        last_delivery_attempt_at IS NULL
        OR (last_delivery_attempt_at >= token_issued_at
            AND updated_at >= last_delivery_attempt_at)
    );

ALTER TABLE public.friend_invitation
    DROP CONSTRAINT friend_invitation_status_check,
    DROP CONSTRAINT friend_invitation_acceptance_check;

ALTER TABLE public.friend_invitation
    ADD CONSTRAINT friend_invitation_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'INVALIDATED')),
    ADD CONSTRAINT friend_invitation_acceptance_check CHECK (
        (status = 'ACCEPTED'
            AND accepted_by_account_id IS NOT NULL
            AND accepted_at IS NOT NULL
            AND accepted_by_account_id <> sender_account_id
            AND accepted_at >= token_issued_at
            AND accepted_at < expires_at)
        OR
        (status IN ('PENDING', 'EXPIRED', 'INVALIDATED')
            AND accepted_by_account_id IS NULL
            AND accepted_at IS NULL)
    );

ALTER TABLE public.friendship
    ADD COLUMN invitations_invalid_before timestamptz;

UPDATE public.friendship
SET invitations_invalid_before = unfriended_at
WHERE unfriended_at IS NOT NULL;

ALTER TABLE public.friendship
    ADD CONSTRAINT friendship_invitation_cutoff_check CHECK (
        invitations_invalid_before IS NULL
        OR invitations_invalid_before >= created_at
    );
```

Flyway thực hiện toàn migration trong một transaction PostgreSQL. Không sửa V1–V4, không tự baseline/repair hoặc thay checksum. Chưa chạy DDL; approval này không cho phép tự mutate DB lâu dài.

## 3. Dữ liệu cũ và invariants

- Row lời mời cũ nhận `delivery_state = UNKNOWN`, mã/mốc gửi null: không suy đoán thư đã gửi hoặc chắc chắn thất bại. Không phát hành token hay email từ migration. V3 chưa có public send API nên không có lịch sử gửi do các API Friends hiện tại tạo.
- Giữ nguyên toàn bộ ID, recipient email, digest, deadline, acceptance tuple và thời gian cũ. Lời mời không tự đổi trạng thái khi migrate.
- Friendship đang inactive có cutoff bằng `unfriended_at` hiện có; row đang active giữ cutoff null. Không tự suy luận các lần unfriend/refriend không còn lưu trong history cũ.
- Từ khi API mới hoạt động, unfriend ghi `unfriended_at` và cutoff trong cùng transaction; refriend clear `unfriended_at` nhưng giữ cutoff. Accept/incoming loại lời mời có `created_at <= cutoff`. Không xoay token để biến lời mời cũ trước unfriend thành lời mời mới.
- Không giữ raw token để gửi lại cùng credential. Lần retry hợp lệ luôn phát hành credential mới; pending đã SMTP_ACCEPTED và chưa hết hạn không được retry.
- Cooldown kiểm tra lần thử gần nhất của **sender/email**, không chỉ invitation ID, để tạo row mới không né được 30 giây.
- `delivery_attempt_id` và điều kiện cập nhật theo ID xử lý completion trả muộn; không dựa vào khóa giữ xuyên HTTP/SMTP.
- `SENDING` bị treo không được coi là đã gửi hoặc thất bại. Chi tiết xử lý sau 30 giây đã được duyệt trong [thiết kế API](EP06_TASK02_INVITATION_API_DESIGN.md), đã có source orchestration, chưa kiểm thử.

## 4. Mapping và kiểm thử bắt buộc sau approval

| Yêu cầu | Dữ liệu/transaction | Evidence cần chạy |
| --- | --- | --- |
| B27: không gửi thêm khi PENDING đã gửi | `delivery_state`, status/deadline dưới lock | HTTP trùng và hai request đồng thời: chỉ một lần gọi Notification; không rotate digest/deadline |
| B25/B28: gửi lại sau lỗi/hết hạn, 30 giây | `last_delivery_attempt_at`, attempt ID, token/deadline atomically trước HTTP | Trước/đúng/sau 30 giây; failed/unknown; đổi invitation ID không né cooldown |
| B22: mời chéo chỉ chấp nhận | Identity facts trước transaction, kiểm tra lời mời hai chiều khi serialize | A → B và B → A đồng thời không tạo hai email/quan hệ; không auto-accept |
| B23: pending cũ mất hiệu lực | cutoff trên canonical Friendship, `INVALIDATED` khi materialize | Unfriend → accept cũ bị chặn; refriend → lời mời trước cutoff vẫn bị chặn; đổi email không bỏ qua account-pair cutoff |
| B24: link đã dùng | status/accepted actor và Friendship hiện tại | Retry khi còn bạn không ghi; sau unfriend không re-activate |
| Giữ migration history/dữ liệu | Fresh V1→V5 và upgrade V4→V5 | Hash V1–V4, seed Outfit/Share/Chat/Friends giữ nguyên; dữ liệu cũ UNKNOWN, inactive pair được backfill cutoff |

Unit, HTTP/controller, JPA/PostgreSQL, negative authorization A/B/C, rollback/concurrency và toàn Wardrobe regression phải thực chạy. Kiểm tra Postman JSON riêng với chạy Newman; invitation/email/Chat E2E không thay bằng SQL refriend fixture. Đã viết test V5 nhưng chưa chạy do Owner để Docker sau.

## 5. Phạm vi approval đã nhận

Owner đã duyệt **bốn cột, trạng thái INVALIDATED, các constraint và backfill đúng mục 2**, Entity/Repository tương ứng và test PostgreSQL cô lập. Việc duyệt nghiệp vụ trước đó hoặc V3/V4 không được coi là đã duyệt DDL này.

Không đụng DB/schema service khác, không thay Outfit/Chat tables, không Compose/DB lâu dài/real email/cloud. API mới theo [thiết kế Task 02](EP06_TASK02_INVITATION_API_DESIGN.md); không khẳng định toàn Task 02/03 Done từ một migration pass.
