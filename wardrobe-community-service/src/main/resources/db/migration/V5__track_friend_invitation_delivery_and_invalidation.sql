-- EP06 Task 02: scope and API design explicitly approved by owner, 2026-10-28.
ALTER TABLE public.friend_invitation
    ADD COLUMN delivery_state text NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN delivery_attempt_id uuid,
    ADD COLUMN last_delivery_attempt_at timestamptz;

ALTER TABLE public.friend_invitation
    ADD CONSTRAINT friend_invitation_delivery_state_check
        CHECK (delivery_state IN ('UNKNOWN', 'SENDING', 'SMTP_ACCEPTED', 'FAILED')),
    ADD CONSTRAINT friend_invitation_delivery_attempt_uq UNIQUE (delivery_attempt_id),
    ADD CONSTRAINT friend_invitation_delivery_attempt_check CHECK (
        (delivery_attempt_id IS NULL AND last_delivery_attempt_at IS NULL AND delivery_state = 'UNKNOWN')
        OR (delivery_attempt_id IS NOT NULL AND last_delivery_attempt_at IS NOT NULL)
    ),
    ADD CONSTRAINT friend_invitation_delivery_time_check CHECK (
        last_delivery_attempt_at IS NULL
        OR (last_delivery_attempt_at >= token_issued_at AND updated_at >= last_delivery_attempt_at)
    );

ALTER TABLE public.friend_invitation
    DROP CONSTRAINT friend_invitation_status_check,
    DROP CONSTRAINT friend_invitation_acceptance_check;

ALTER TABLE public.friend_invitation
    ADD CONSTRAINT friend_invitation_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'INVALIDATED')),
    ADD CONSTRAINT friend_invitation_acceptance_check CHECK (
        (status = 'ACCEPTED' AND accepted_by_account_id IS NOT NULL AND accepted_at IS NOT NULL
            AND accepted_by_account_id <> sender_account_id
            AND accepted_at >= token_issued_at AND accepted_at < expires_at)
        OR (status IN ('PENDING', 'EXPIRED', 'INVALIDATED')
            AND accepted_by_account_id IS NULL AND accepted_at IS NULL)
    );

ALTER TABLE public.friendship ADD COLUMN invitations_invalid_before timestamptz;

UPDATE public.friendship SET invitations_invalid_before = unfriended_at WHERE unfriended_at IS NOT NULL;

ALTER TABLE public.friendship
    ADD CONSTRAINT friendship_invitation_cutoff_check CHECK (
        invitations_invalid_before IS NULL OR invitations_invalid_before >= created_at
    );
