package com.stylemind.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "email_verification_challenge", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationChallengeRow {
    @Id
    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;

    @Column(name = "code_digest", nullable = false, columnDefinition = "bytea")
    private byte[] codeDigest;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "last_send_attempt_at", nullable = false)
    private OffsetDateTime lastSendAttemptAt;

    @Column(name = "send_window_started_at", nullable = false)
    private OffsetDateTime sendWindowStartedAt;

    @Column(name = "send_count", nullable = false)
    private short sendCount;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;
}
