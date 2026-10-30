# EP06 Task 01 — Scope schema lời mời và quan hệ bạn bè đã duyệt

**Ngày: 2026-10-24. Trạng thái: APPROVED_SCOPE — DONE_LOCAL.** Owner trả lời “duyệt” cho đúng hai bảng, constraint/index, Entity/Repository JPA và test PostgreSQL dưới đây. Đã triển khai [V3](../../../wardrobe-community-service/src/main/resources/db/migration/V3__create_friend_invitation_and_friendship_tables.sql) và kiểm thử trên PostgreSQL cô lập; chưa áp DB lâu dài. Nghiệp vụ căn cứ B1–B4, B7, B9–B11, B19 trong [plan hiện hành](EP06_WORK_PLAN_AND_PROGRESS.md).

## 1. Phạm vi cần duyệt

- Chỉ thêm hai bảng `public.friend_invitation`, `public.friendship` cùng constraint/index dưới đây trong `stylemind_wardrobe_community`.
- Migration đã triển khai là `V3__create_friend_invitation_and_friendship_tables.sql`; đã kiểm tra trước đó chỉ có V1/V2.
- Dùng Spring Data JPA theo lựa chọn Owner; Entity/Repository đã thêm trong scope được duyệt.
- Không ALTER/DROP bảng Outfit, OutfitItem hoặc OutfitShare; không sửa V1/V2, Identity, Notification, Gateway, Compose hay dữ liệu lâu dài.
- Không tạo Chat/attachment trong scope này. Chat dùng quan hệ bạn bè về sau, không có FK tới Wardrobe/Outfit.
- Account ID chỉ là logical reference tới Identity, không có FK hay query xuyên database.

## 2. Mapping yêu cầu

| Rule | Biểu diễn | Bảo vệ và evidence dự kiến |
| --- | --- | --- |
| B1–B3: mời email dù chưa có tài khoản | `recipient_email`; `accepted_by_account_id` chỉ có sau accept | Insert không đòi recipient account; email syntax ở validator, verification qua contract Identity |
| B2/B4: PENDING trước khi đúng người nhận | `status`, `accepted_at`, `accepted_by_account_id` | Token + live verified email + actor khác sender; accept và ghi Friendship cùng transaction |
| B9/B19: hạn 7 ngày; resend thay link và hạn | `token_hash`, `token_issued_at`, `expires_at` | Chỉ lưu digest; resend thay digest/deadline atomically; link cũ không khớp; deadline đúng 168 giờ |
| B10: gửi trùng PENDING dùng lời mời hiện có | Unique partial `(sender_account_id, recipient_email)` | Concurrent duplicate bị constraint chặn; duplicate thường không gửi lại email hay xoay token |
| B10: email lỗi giữ PENDING | Commit invitation trước Notification | Không có HTTP/SMTP trong transaction; lỗi không xóa row hoặc báo giả là đã gửi |
| B5/B11: một cặp stable để Chat dùng lại | Canonical UUID pair unique trong `friendship` | PostgreSQL unsigned UUID order; cặp A–B và B–A chung row |
| B7/B11: unfriend rồi kết bạn lại | `unfriended_at`; `established_at` của lần kết bạn hiện tại | Unfriend đánh dấu, không xóa row/history; accept lời mời mới hợp lệ mới khôi phục quan hệ |

## 3. DDL đã được duyệt

Owner đã duyệt toàn bộ DDL trong mục này, gồm `EXPIRED` để giải phóng unique PENDING sau hạn. Không thêm tính năng hủy/từ chối/block người dùng. Approval này không duyệt ngầm chi tiết API liên service hoặc Chat.

```sql
CREATE TABLE public.friend_invitation (
    friend_invitation_id uuid PRIMARY KEY,
    sender_account_id uuid NOT NULL,
    recipient_email text NOT NULL,
    status text NOT NULL,
    token_hash text NOT NULL,
    token_issued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    accepted_by_account_id uuid,
    accepted_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT friend_invitation_email_check CHECK (
        char_length(recipient_email) BETWEEN 3 AND 254
        AND recipient_email = lower(btrim(recipient_email))
    ),
    CONSTRAINT friend_invitation_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED')),
    CONSTRAINT friend_invitation_token_hash_check
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT friend_invitation_token_hash_uq UNIQUE (token_hash),
    CONSTRAINT friend_invitation_lifetime_check
        CHECK (expires_at = token_issued_at + interval '168 hours'),
    CONSTRAINT friend_invitation_acceptance_check CHECK (
        (status = 'ACCEPTED'
            AND accepted_by_account_id IS NOT NULL
            AND accepted_at IS NOT NULL
            AND accepted_by_account_id <> sender_account_id
            AND accepted_at >= token_issued_at
            AND accepted_at < expires_at)
        OR
        (status IN ('PENDING', 'EXPIRED')
            AND accepted_by_account_id IS NULL
            AND accepted_at IS NULL)
    ),
    CONSTRAINT friend_invitation_timestamps_check CHECK (
        token_issued_at >= created_at
        AND updated_at >= token_issued_at
        AND (accepted_at IS NULL OR updated_at >= accepted_at)
    )
);

CREATE UNIQUE INDEX friend_invitation_pending_sender_email_uq
    ON public.friend_invitation (sender_account_id, recipient_email)
    WHERE status = 'PENDING';

CREATE INDEX friend_invitation_sender_created_idx
    ON public.friend_invitation (sender_account_id, created_at DESC, friend_invitation_id);

CREATE INDEX friend_invitation_recipient_pending_idx
    ON public.friend_invitation (recipient_email, created_at DESC, friend_invitation_id)
    WHERE status = 'PENDING';

CREATE TABLE public.friendship (
    friendship_id uuid PRIMARY KEY,
    account_low_id uuid NOT NULL,
    account_high_id uuid NOT NULL,
    established_at timestamptz NOT NULL,
    unfriended_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT friendship_pair_order_check CHECK (account_low_id < account_high_id),
    CONSTRAINT friendship_pair_uq UNIQUE (account_low_id, account_high_id),
    CONSTRAINT friendship_timestamps_check CHECK (
        established_at >= created_at
        AND updated_at >= established_at
        AND (unfriended_at IS NULL
            OR (unfriended_at >= established_at AND updated_at >= unfriended_at))
    )
);

CREATE INDEX friendship_active_low_idx
    ON public.friendship (account_low_id, established_at DESC, friendship_id)
    WHERE unfriended_at IS NULL;

CREATE INDEX friendship_active_high_idx
    ON public.friendship (account_high_id, established_at DESC, friendship_id)
    WHERE unfriended_at IS NULL;
```

`recipient_email` constraint chỉ bảo vệ canonical form/length, không chứng minh email có tồn tại hay đã xác minh. Database không biết actor JWT hoặc email của account; không dùng SQL constraint thay authorization.

## 4. Transaction và lifecycle dự kiến

1. **Gửi lời mời:** lấy sender từ principal; normalize/validate email theo contract; ghi PENDING với digest và mốc thời gian UTC ở độ chính xác microsecond. Duplicate PENDING còn hạn trả row hiện có. Lời mời quá hạn được chuyển EXPIRED trong transaction trước khi tạo PENDING mới; không dùng `now()` trong partial index. Việc materialize EXPIRED là đề xuất đi cùng scope này.
2. **Resend:** chỉ sender của PENDING; khóa row, phát hành token mới, cập nhật digest/issue/deadline cùng transaction. Email lỗi vẫn giữ phiên bản mới, link cũ không hồi sinh. Gửi mail sau commit. Giới hạn/tần suất resend còn cần contract; không tự lấy hạn mức của OTP hay Outfit.
3. **Accept:** current Identity facts lấy trước transaction; khóa invitation, kiểm tra token, trạng thái và deadline lại ở trong transaction. Ghi ACCEPTED và insert/update canonical Friendship atomically. Unique pair xử lý concurrent accept; nếu lỗi thì rollback cả hai. Không tin email/actor do client tự khai.
4. **Unfriend:** chỉ actor thuộc đúng cặp; khóa row, ghi `unfriended_at`, không DELETE row. Tương lai read/send/push Chat đều kiểm tra quan hệ hiện tại. Không cần schema Chat để triển khai thao tác này.
5. **Re-friend:** chỉ lời mời mới hợp lệ mới được kích hoạt lại cùng Friendship row; giữ `friendship_id`, cập nhật `established_at` và clear `unfriended_at`. Link ACCEPTED cũ không được tự kích hoạt lại.

Lock order chi tiết và kết quả API cho repeated accept, lời mời chéo, pending invitation tồn tại khi unfriend, resend đồng thời hoặc trạng thái đã là bạn cần ghi trong API contract trước Task 02; scope DDL không tự duyệt các nhánh nghiệp vụ đó. Không gán giới hạn số bạn bè/lời mời mới.

## 5. Kiểm thử trong scope đã duyệt

- Flyway fresh V1→V2→migration mới và upgrade từ V2 có seed Outfit/Item/Share/public link; checksum V1/V2 và dữ liệu seed giữ nguyên.
- Cặp A–B/B–A, UUID qua sign bit, self/null/duplicate pair, hai transaction tạo cùng cặp.
- PENDING recipient chưa có account; normalized email, token digest shape/unique; thời gian đúng 168 giờ, before/at/after deadline.
- Acceptance tuple không hợp lệ, self recipient, accepted sau expiry, timestamps không nhất quán; constraint thực thi trên PostgreSQL.
- Duplicate PENDING cùng sender/email kể cả concurrent; expired row + pending mới; hai sender khác nhau không bị gộp.
- Accept rollback toàn bộ, resend thay credential atomically và old-link rejection qua repository/HTTP khi contract sẵn sàng.
- Unfriend/re-friend giữ identity của pair; actor A/B/C isolation ở API, không coi constraint test là authorization test.

Đã dùng PostgreSQL 18.6/Testcontainers cô lập: **17 persistence/integration tests + 23 policy/token/pair unit tests**, cùng 62 regression hiện có, tổng **102 test pass, 0 skipped**. Xem [báo cáo](../../qa/wardrobe/EP06_TASK01_IMPLEMENTATION_REPORT.md). Accept/resend/unfriend ở đây mới được kiểm tra qua persistence/transaction, chưa phải API hoặc email E2E. Các HTTP/Identity/Notification cases trong danh sách tiếp tục thuộc Task 02/03.

## 6. Approval và giới hạn

Owner đã duyệt **hai bảng và toàn bộ constraint/index ở mục 3**, migration mới, Entity/Repository JPA và PostgreSQL tests trong service Wardrobe. Chưa duyệt Chat DDL, provider ảnh, API liên service hoặc thay đổi source của Khánh/Quân/Vi trong scope này.

Theo [AGENTS.md](../../../AGENTS.md): “A new schema change requires an explicitly approved migration scope”. Approval V1/V2 toàn Wardrobe/Chat cũ không áp dụng cho schema Friends mới.
