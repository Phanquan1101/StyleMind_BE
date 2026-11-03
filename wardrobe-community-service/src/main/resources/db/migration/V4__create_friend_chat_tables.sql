-- EP06 Task 08: stable friend conversations, messages and private image metadata.
-- Scope approved by the owner on 2026-10-27. Preserve Outfit/Friends V1-V3.
-- All foreign keys stay within this service. No image files or public URLs are stored here.

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

