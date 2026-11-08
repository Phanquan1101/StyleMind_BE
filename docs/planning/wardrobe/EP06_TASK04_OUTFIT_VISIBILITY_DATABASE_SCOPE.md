# EP06 Task 04 — Outfit visibility V6 database scope

**APPROVED_SCOPE — 2026-10-29.** Owner trả lời “Duyệt scope V6” và duyệt contract/cách chuyển đổi; S1 đã chốt giữ PRIVATE cho tới khi Owner tự chọn FRIENDS. DDL/backfill dưới đây được phép triển khai và kiểm thử PostgreSQL cô lập; không áp DB lâu dài hoặc sửa V1–V5. Các nhãn proposal phía dưới giữ làm checkpoint trước approval.

**2026-10-29 — PROPOSED / NOT APPROVED.** Chỉ là DDL để Owner review. Không tạo file Flyway V6 hoặc chạy DDL, kể cả test, trước scope approval. [API/business proposal](EP06_OUTFIT_SHARING_CONTRACT_PROPOSAL.md).

## 1. Scope

- Service/database: wardrobe-community-service / stylemind_wardrobe_community.
- History source hiện có V1–V5; không chỉnh/xóa/re-number. Dự kiến file kế tiếp `V6__add_outfit_visibility.sql`, kiểm tra version lại khi code.
- Thêm một column Outfit.visibility, hai CHECK và một partial page index; không tạo/drop bảng, không cross-service FK, không đổi Friendship/Chat/Identity/Commerce.
- Default PRIVATE cho Outfit mới. Backfill có public_token → PUBLIC; còn lại PRIVATE. Không tự chuyển email-share-only thành FRIENDS, không gửi email/tạo lời mời/bạn bè. Giữ mọi rows/keys của outfit_share và outfit_item.
- public_token giữ nguyên. Public token có sẵn mà thiếu enabled_at: backfill từ updated_at; non-public xóa marker enabled_at (không xóa token). Không đổi name/items/owner/creation_source/created_at/updated_at trong migration.

## 2. DDL chính xác đề xuất

```sql
-- NOT APPROVED. Run only after explicit scope approval, in a coordinated cutover.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.outfit
        WHERE public_token IS NOT NULL
          AND (btrim(public_token) = '' OR char_length(public_token) > 128)
    ) THEN
        RAISE EXCEPTION 'Existing public link state requires review before Outfit visibility migration';
    END IF;
END;
$$;

ALTER TABLE public.outfit
    ADD COLUMN visibility text NOT NULL DEFAULT 'PRIVATE';

UPDATE public.outfit
SET visibility = CASE WHEN public_token IS NOT NULL THEN 'PUBLIC' ELSE 'PRIVATE' END,
    public_enabled_at = CASE
        WHEN public_token IS NOT NULL THEN COALESCE(public_enabled_at, updated_at)
        ELSE NULL
    END;

ALTER TABLE public.outfit
    ADD CONSTRAINT outfit_visibility_check
        CHECK (visibility IN ('PRIVATE', 'FRIENDS', 'PUBLIC')),
    ADD CONSTRAINT outfit_public_visibility_state_check
        CHECK (
            (visibility = 'PUBLIC'
                AND public_token IS NOT NULL
                AND btrim(public_token) <> ''
                AND char_length(public_token) <= 128
                AND public_enabled_at IS NOT NULL)
            OR
            (visibility IN ('PRIVATE', 'FRIENDS') AND public_enabled_at IS NULL)
        );

CREATE INDEX outfit_friend_visibility_page_idx
    ON public.outfit (owner_account_id, updated_at DESC, outfit_id DESC)
    WHERE visibility IN ('FRIENDS', 'PUBLIC');
```

CHECK cho phép giữ public_token khi PRIVATE/FRIENDS. Do đó mọi reader bắt buộc kiểm tra visibility, không chỉ token non-null. Existing outfit_public_token_uq giữ nguyên; không tự thêm format/entropy constraint lên token hợp lệ cũ hoặc đổi plaintext/hash storage.

## 3. Backfill và tác động quyền — cần Owner duyệt

| V1–V5 trước | Sau V6 đề xuất | Tác động |
| --- | --- | --- |
| Không token/không grant | PRIVATE | Không mở quyền mới |
| Có grant email, không token | PRIVATE, grant rows giữ nguyên | Quyền cũ theo email bị dừng bởi new reader; Owner chọn FRIENDS nếu muốn tất cả bạn bè xem |
| Có token, có/không grant | PUBLIC, cùng token | Giữ đường public đang tồn tại; grant không tự tạo friendship |
| Token có, enabled_at NULL | PUBLIC, enabled_at từ updated_at | Reader cũ xem bằng token đã hoạt động; bổ sung marker nhất quán |
| Token NULL, enabled_at có | PRIVATE, enabled_at NULL | Marker không có token không cấp public access trong code cũ; chuẩn hóa state |
| Token blank/length >128 | Abort trước ALTER | Không tự sửa secret/link; audit dữ liệu rồi chốt riêng |

Row counts/IDs/FKs/data của outfit_share/items/Friends/Chat giữ nguyên. Không tự dùng email lookup để gán account IDs. Không downgrade về binary cũ sau cutover vì binary cũ bỏ qua visibility; kế hoạch phục hồi dữ liệu/deploy phải theo môi trường Owner, không reset migration history.

## 4. Verification sau approval

1. Fresh V1→V6 và seeded upgrade V1–V5→V6, Flyway history/checksum cũ không đổi.
2. PRIVATE default; ba enum; reject NULL/invalid; state PUBLIC thiếu token/enabled_at bị chặn, giữ token inactive được phép; UNIQUE token vẫn có hiệu lực.
3. Seed đầy đủ bảng trên, so row counts/IDs/tokens/owner/items/grants/timestamps và unrelated tables trước/sau; migration không mở email-only cho toàn bạn bè.
4. Invalid-token preflight rollback không để lại partial schema; index definition đúng; JPA mapping validation trong test DB.
5. API/authorization tests ở Task 05/06/07 chứng minh legacy grants/public token giữ lại không bypass PRIVATE/FRIENDS. Không chạy persistent migration/Compose bằng approval cho tests.

## 5. Approval boundary

Approval cũ cho Wardrobe/WardrobeShare đã rút hoặc Friends/Chat V3–V5 không bao phủ V6. Cần Owner duyệt DDL/backfill trên và cutover ở contract. Trước đó chỉ hoàn tất documentation/static review, không báo migration/API Done.
