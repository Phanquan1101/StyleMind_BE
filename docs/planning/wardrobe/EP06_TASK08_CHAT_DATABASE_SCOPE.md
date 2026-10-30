# EP06 Task 08 — Scope DB Chat theo cặp bạn bè

**2026-10-27, Asia/Bangkok — APPROVED_SCOPE, DONE_LOCAL.** Owner trả lời “Duyệt scope DB Chat Task 08” cho bốn bảng, constraint/index, Entity/Repository JPA và PostgreSQL tests trong tài liệu này. Đã triển khai [V4](../../../wardrobe-community-service/src/main/resources/db/migration/V4__create_friend_chat_tables.sql) đúng DDL; 22 test Chat và 131 regression pass, 0 skipped. Xem [báo cáo](../../qa/wardrobe/EP06_TASK08_IMPLEMENTATION_REPORT.md); chưa áp DB lâu dài. Các lần duyệt Chat theo Wardrobe trước đây không áp dụng cho mô hình này.

Căn cứ: B5–B7, B11–B18 trong [plan hiện hành](EP06_WORK_PLAN_AND_PROGRESS.md); [V3 Friends đã duyệt](EP06_TASK01_FRIENDS_DATABASE_SCOPE.md); yêu cầu tiếp tục phần độc lập với công việc của thành viên khác. Quy tắc nghiệp vụ đã chốt vẫn giữ nguyên; scope schema dưới đây đã được Owner duyệt, không duyệt ngầm API/WebSocket/storage.

## 1. Phạm vi đã duyệt

Thêm **bốn bảng** trong database của Wardrobe Community:

| Bảng | Mục đích |
| --- | --- |
| `chat_session` | Một hội thoại gắn với một Friendship ổn định, dùng lại sau khi kết bạn lại |
| `chat_participant` | Hai người tham gia hội thoại; làm đích FK cho người gửi tin |
| `chat_message` | Nội dung văn bản, người gửi, thứ tự server, mã chống gửi trùng |
| `chat_attachment` | Metadata từng ảnh của tin nhắn; tối đa năm vị trí ảnh, mỗi ảnh tối đa 5 MB |

- Đã kiểm tra history chỉ có V1–V3 và thêm **V4__create_friend_chat_tables.sql**. Không sửa V1–V3, không ALTER bảng Friends/Outfit hiện tại.
- Thêm Entity và Repository **Spring Data JPA**, theo convention của dự án, cùng test PostgreSQL cô lập. Service nghiệp vụ về sau tiếp tục dùng interface → impl → internal như Owner yêu cầu.
- Chỉ có FK nội bộ trong Wardrobe Community. Account ID là logical reference tới Identity; không đọc DB Identity/Commerce.
- Không triển khai API Chat, WebSocket, upload hoặc chọn nhà cung cấp lưu ảnh trong scope DB này. Không sửa code Khánh, Identity, Notification, Gateway hay Compose.
- Không tạo `wardrobe_id`, `outfit_id`, share grant hoặc visibility trong Chat. Task 04–07 không phải điều kiện của Task 08.
- Không chạy trên DB lâu dài. Không tạo bạn bè giả trong dữ liệu ứng dụng; fixture chỉ nằm trong DB test cô lập.

## 2. Quy tắc và nơi bảo vệ

| Quy tắc | Schema | Phần còn phải kiểm tra ở API/service sau này |
| --- | --- | --- |
| Một hội thoại cho một cặp; tái kết bạn dùng lại lịch sử | `chat_session.friendship_id` unique + FK tới Friendship ổn định trong V3 | Mở hội thoại chỉ cho Friendship đang hoạt động, giữ nguyên ID khi tái kết bạn |
| Hai người tham gia đúng cặp | Unique session/account, unique session/slot; slot chỉ 1 hoặc 2 | Tạo cả hai participants đúng account_low/high của Friendship trong cùng transaction; không có API sửa thành viên |
| Sender phải là thành viên session | FK kép `(chat_session_id, sender_account_id)` tới participant | Sender lấy từ principal và friendship phải đang hoạt động |
| Unfriend chặn cả hai nhưng giữ lịch sử | Không có cascade DELETE, không dùng cờ unfriend riêng trong Chat | Mỗi lần đọc/gửi/upload/đọc ảnh/subscribe/push đều kiểm tra Friendship hiện tại; cập nhật timestamp unfriend không xóa Chat |
| Thứ tự tin nhắn | Unique session/sequence; sequence dương; session giữ counter | Khóa Friendship trước Session, cấp số và ghi message trong một transaction |
| Gửi lại request không nhân đôi tin | Unique session/sender/client_message_id | Kiểm tra quyền trước khi trả retry; so sánh text + danh sách ảnh có thứ tự; cùng key khác payload sẽ được chốt ở contract Task 10 |
| Văn bản tối đa 4.000 ký tự | `char_length(content) BETWEEN 1 AND 4000`, nullable khi chỉ gửi ảnh | Đề xuất đếm Unicode code points để Java khớp PostgreSQL; kiểm tra chuỗi chỉ chứa khoảng trắng và payload rỗng ở validator/service |
| Tối đa 5 ảnh/tin | `display_order` chỉ 1–5 và unique message/order | Validate số lượng trước khi lưu; thứ tự ảnh theo request hợp lệ |
| Tối đa 5 MB/ảnh; JPG/JPEG, PNG, WebP | Size 1–5.242.880 bytes; MIME image/jpeg, image/png, image/webp | Kiểm tra bytes thực, không tin MIME/extension client; multipart envelope không tính vào dung lượng file |
| Ảnh cũng bị chặn sau unfriend | Chỉ lưu storage reference nội bộ; không có cột public URL | Storage riêng tư và đọc ảnh phải qua authorization; backend/provider/upload lifecycle còn TBD |

**Giới hạn chính xác của DB:** slot/unique bảo đảm tối đa hai participant rows; FK sender bảo đảm sender thuộc participant rows của session. Chúng không tự chứng minh hai rows khớp Friendship hoặc session luôn có đủ hai rows. Đây là invariant của transactional creator trong Task 09, phải có integration/concurrency test trước khi mở API. DB cũng không tự đếm attachment ở một bảng khác để cấm message không có text lẫn ảnh; Task 10 phải kiểm tra điều đó atomically trước commit. Không coi các FK là JWT authorization.

Đề xuất không thêm trigger, participant lifecycle, group chat, trạng thái online, đã đọc/typing, sửa/xóa tin, block người dùng hoặc retention/expiry mới. Chúng không nằm trong phạm vi được giao.

## 3. DDL đã được duyệt

```sql
CREATE TABLE public.chat_session (
    chat_session_id uuid PRIMARY KEY,
    friendship_id uuid NOT NULL,
    last_sequence_number bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chat_session_friendship_fk
        FOREIGN KEY (friendship_id) REFERENCES public.friendship (friendship_id),
    CONSTRAINT chat_session_friendship_uq UNIQUE (friendship_id),
    CONSTRAINT chat_session_sequence_check CHECK (last_sequence_number >= 0),
    CONSTRAINT chat_session_timestamps_check CHECK (updated_at >= created_at)
);

CREATE TABLE public.chat_participant (
    chat_participant_id uuid PRIMARY KEY,
    chat_session_id uuid NOT NULL,
    account_id uuid NOT NULL,
    participant_slot smallint NOT NULL,
    joined_at timestamptz NOT NULL,
    CONSTRAINT chat_participant_session_fk
        FOREIGN KEY (chat_session_id) REFERENCES public.chat_session (chat_session_id),
    CONSTRAINT chat_participant_account_uq UNIQUE (chat_session_id, account_id),
    CONSTRAINT chat_participant_slot_uq UNIQUE (chat_session_id, participant_slot),
    CONSTRAINT chat_participant_slot_check CHECK (participant_slot IN (1, 2))
);

CREATE INDEX chat_participant_account_session_idx
    ON public.chat_participant (account_id, chat_session_id);

CREATE TABLE public.chat_message (
    chat_message_id uuid PRIMARY KEY,
    chat_session_id uuid NOT NULL,
    sender_account_id uuid NOT NULL,
    client_message_id uuid NOT NULL,
    sequence_number bigint NOT NULL,
    content text,
    created_at timestamptz NOT NULL,
    CONSTRAINT chat_message_sender_fk
        FOREIGN KEY (chat_session_id, sender_account_id)
        REFERENCES public.chat_participant (chat_session_id, account_id),
    CONSTRAINT chat_message_sequence_uq UNIQUE (chat_session_id, sequence_number),
    CONSTRAINT chat_message_client_key_uq
        UNIQUE (chat_session_id, sender_account_id, client_message_id),
    CONSTRAINT chat_message_sequence_check CHECK (sequence_number > 0),
    CONSTRAINT chat_message_content_check
        CHECK (content IS NULL OR char_length(content) BETWEEN 1 AND 4000)
);

CREATE TABLE public.chat_attachment (
    chat_attachment_id uuid PRIMARY KEY,
    chat_message_id uuid NOT NULL,
    display_order smallint NOT NULL,
    storage_object_key text NOT NULL,
    content_type text NOT NULL,
    size_bytes bigint NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT chat_attachment_message_fk
        FOREIGN KEY (chat_message_id) REFERENCES public.chat_message (chat_message_id),
    CONSTRAINT chat_attachment_order_uq UNIQUE (chat_message_id, display_order),
    CONSTRAINT chat_attachment_storage_key_uq UNIQUE (storage_object_key),
    CONSTRAINT chat_attachment_order_check CHECK (display_order BETWEEN 1 AND 5),
    CONSTRAINT chat_attachment_storage_key_check CHECK (
        char_length(storage_object_key) BETWEEN 1 AND 1024
        AND storage_object_key = btrim(storage_object_key)
    ),
    CONSTRAINT chat_attachment_content_type_check
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chat_attachment_size_check CHECK (size_bytes BETWEEN 1 AND 5242880)
);
```

Unique session/sequence hỗ trợ cursor history theo sequence, kể cả đọc index theo chiều ngược; unique message/order hỗ trợ lấy ảnh theo thứ tự. Không tạo index trùng các unique index này. FK mặc định không cascade; việc cố xóa Friendship/Session/Participant đã được tham chiếu phải bị chặn, không âm thầm xóa lịch sử.

`storage_object_key` là tham chiếu nội bộ do server quản lý, tối đa 1.024 ký tự như giới hạn kỹ thuật đề xuất, không phải URL cho client. Không lưu binary ảnh, original filename, email, token hay thông tin xác thực vào các bảng Chat. Trạng thái upload chưa hoàn tất, cleanup file mồ côi và ánh xạ provider sẽ được review ở contract upload; không giả vờ rằng metadata ở đây đã cung cấp upload/storage runtime.

Các timestamps dùng UTC và microsecond như Friends. Migration không tạo sẵn session/message hay sửa trạng thái Friendship. Counter và sequence cần service giữ đồng bộ trong cùng transaction; SQL CHECK không tự so sánh counter với tất cả messages.

## 4. Entity/Repository đã triển khai trong scope

- `entity/ChatSession.java`, `ChatParticipant.java`, `ChatMessage.java`, `ChatAttachment.java`: explicit column mappings, UUID ID, OffsetDateTime; không serialize JPA entity làm HTTP DTO.
- `repository/chat/`: tìm session theo Friendship; lookup/lock session; tìm participant theo session/account; tra retry theo session/sender/client key; history theo sequence; ảnh theo message/display_order.
- Repository lookup không tự cấp quyền cho người gọi. Những methods cấp business access sẽ được gọi bên trong transaction/authorization của service, chưa có public Chat controller trong bước này.
- Không thêm Service interface/impl rỗng hoặc endpoint placeholder để coi là đã hoàn thành Task 09/10.

## 5. Kiểm thử trong scope đã duyệt

1. Fresh Flyway V1→V4, Hibernate validate bốn Entity mới; không dùng H2 thay PostgreSQL.
2. Upgrade từ V3 có Outfit/Item/Share/public-link, invitations và active/inactive Friendship; so sánh fingerprint dữ liệu và checksum V1–V3, không in token/public-link fixture.
3. Session unique theo Friendship, FK local hợp lệ, các cặp A–B và A–C độc lập; concurrent tạo cùng session chỉ có một winner.
4. Participant không trùng account/slot, slot ngoài 1–2 bị chặn; message của account không phải participant bị FK chặn. Phân biệt test constraint với API authentication/authorization chưa có.
5. Sequence dương/unique; client key unique theo đúng session + sender, hai sender có thể dùng cùng client key, session khác cũng vậy.
6. Khóa session cấp sequence đồng thời và rollback counter + message + attachment; không nhận các lần ghi chưa commit là đã gửi thành công.
7. Boundary text 4.000/4.001 code points, gồm Vietnamese/emoji; null text hỗ trợ image-only và ghi rõ empty-payload guard chưa có API.
8. Image order 1–5, duplicate position/position 6, size đúng 5 MB/quá 1 byte, MIME ngoài JPG/PNG/WebP, storage key unique/nonblank; chỉ test metadata, không coi là đã sniff file bytes.
9. Unfriend/re-friend bằng persistence fixture giữ cùng session/participants/messages/ảnh; FK chặn xóa parent làm mất lịch sử.
10. Chạy regression gate `mvn -B -ntp verify` bằng Java 21 + PostgreSQL Testcontainers cô lập. Report count thật; skipped không tính pass.

DDL chỉ chuẩn bị nền tảng lưu trữ. **Task 09/10/11, current-friendship API guards, WebSocket và upload chưa Done khi Task 08 pass.** Swagger/Postman của các HTTP API mới sẽ được bổ sung khi các API đó được triển khai.

## 6. Dependency và bước kế tiếp

- Task 08 không cần endpoint verified-email, Notification template hoặc Share Outfit của Khánh để kiểm thử persistence.
- Send/accept/resend/incoming Friends vẫn chờ [handoff Identity/Notification](EP06_FRIENDS_INTEGRATION_HANDOFF.md). Không tự làm module của họ để mở đường.
- Sau DB: chốt contract mở/lấy/list session và dùng interface → impl → internal; chốt list khi unfriend và HTTP errors trước khi triển khai các hành vi đó.
- WebSocket: còn auth/handshake, destinations, ack, subscribe/send/push authorization, Gateway route và chặn connection/subscription đang mở sau unfriend.
- Ảnh: còn private storage/provider, upload shape/lifecycle, kiểm tra file thực và download authorization. Không âm thầm cấp public link ảnh.

Theo [AGENTS.md](../../../AGENTS.md): **“A new schema change requires an explicitly approved migration scope”.** Owner đã duyệt bốn bảng, constraint/index, Entity/Repository và test ở tài liệu này. Approval này không mở rộng sang API, WebSocket, provider ảnh hoặc thay đổi code của thành viên khác.

