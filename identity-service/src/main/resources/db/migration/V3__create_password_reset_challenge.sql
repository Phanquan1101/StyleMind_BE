CREATE TABLE public.password_reset_challenge (
    challenge_id uuid PRIMARY KEY,
    account_id uuid NOT NULL UNIQUE REFERENCES public.account (account_id),
    otp_digest bytea NOT NULL,
    otp_expires_at timestamp with time zone NOT NULL,
    failed_attempts smallint NOT NULL DEFAULT 0,
    locked_until timestamp with time zone,
    last_send_attempt_at timestamp with time zone NOT NULL,
    send_window_started_at timestamp with time zone NOT NULL,
    send_count smallint NOT NULL,
    verified_at timestamp with time zone,
    reset_token_hash bytea UNIQUE,
    reset_token_expires_at timestamp with time zone,
    consumed_at timestamp with time zone,
    CONSTRAINT password_reset_challenge_otp_digest_length_check
        CHECK (octet_length(otp_digest) = 32),
    CONSTRAINT password_reset_challenge_failed_attempts_check
        CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT password_reset_challenge_send_count_check
        CHECK (send_count BETWEEN 1 AND 3),
    CONSTRAINT password_reset_challenge_lock_state_check CHECK (
        (failed_attempts < 5 AND locked_until IS NULL)
        OR (failed_attempts = 5 AND locked_until IS NOT NULL)
    ),
    CONSTRAINT password_reset_challenge_reset_token_hash_length_check
        CHECK (reset_token_hash IS NULL OR octet_length(reset_token_hash) = 32),
    CONSTRAINT password_reset_challenge_grant_state_check CHECK (
        (verified_at IS NULL AND reset_token_hash IS NULL AND reset_token_expires_at IS NULL AND consumed_at IS NULL)
        OR (verified_at IS NOT NULL AND reset_token_hash IS NOT NULL AND reset_token_expires_at IS NOT NULL
            AND reset_token_expires_at > verified_at
            AND (consumed_at IS NULL OR consumed_at >= verified_at))
    ),
    CONSTRAINT password_reset_challenge_otp_expiry_check
        CHECK (otp_expires_at > last_send_attempt_at),
    CONSTRAINT password_reset_challenge_send_window_check
        CHECK (send_window_started_at <= last_send_attempt_at)
);
