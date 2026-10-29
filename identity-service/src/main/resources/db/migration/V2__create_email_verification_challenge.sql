CREATE TABLE public.email_verification_challenge (
    challenge_id uuid PRIMARY KEY,
    account_id uuid NOT NULL UNIQUE REFERENCES public.account (account_id),
    code_digest bytea NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    failed_attempts smallint NOT NULL DEFAULT 0,
    consumed_at timestamp with time zone,
    last_send_attempt_at timestamp with time zone NOT NULL,
    send_window_started_at timestamp with time zone NOT NULL,
    send_count smallint NOT NULL,
    locked_until timestamp with time zone,
    CONSTRAINT email_verification_challenge_digest_length_check CHECK (octet_length(code_digest) = 32),
    CONSTRAINT email_verification_challenge_failed_attempts_check CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT email_verification_challenge_send_count_check CHECK (send_count BETWEEN 1 AND 3),
    CONSTRAINT email_verification_challenge_lock_state_check CHECK (
        (failed_attempts < 5 AND locked_until IS NULL)
        OR (failed_attempts = 5 AND locked_until IS NOT NULL)
    ),
    CONSTRAINT email_verification_challenge_expiry_check CHECK (expires_at > last_send_attempt_at)
);
