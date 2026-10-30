-- EP06 Task 01: email friend invitations and stable canonical friendships.
-- Scope approved by the owner on 2026-10-24; preserve Outfit V1/V2 history.
-- Account IDs are logical Identity references only; no cross-service foreign keys.

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
