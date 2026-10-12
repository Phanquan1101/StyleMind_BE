package com.stylemind.identity.repository.emailverification;

import com.stylemind.identity.model.emailverification.EmailVerificationChallenge;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmailVerificationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public EmailVerificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(EmailVerificationChallenge challenge) {
        jdbc.update("""
                INSERT INTO public.email_verification_challenge (
                    challenge_id, account_id, code_digest, expires_at, failed_attempts, consumed_at,
                    last_send_attempt_at, send_window_started_at, send_count, locked_until
                ) VALUES (
                    :challengeId, :accountId, :codeDigest, :expiresAt, :failedAttempts, :consumedAt,
                    :lastSendAttemptAt, :sendWindowStartedAt, :sendCount, :lockedUntil
                )
                """, parameters(challenge));
    }

    public Optional<EmailVerificationChallenge> lockByAccount(UUID accountId) {
        return jdbc.query("SELECT * FROM public.email_verification_challenge WHERE account_id = :accountId FOR UPDATE",
                new MapSqlParameterSource("accountId", accountId), resultSet ->
                        resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty());
    }

    public void update(EmailVerificationChallenge challenge) {
        jdbc.update("""
                UPDATE public.email_verification_challenge SET
                    challenge_id = :challengeId,
                    code_digest = :codeDigest,
                    expires_at = :expiresAt,
                    failed_attempts = :failedAttempts,
                    consumed_at = :consumedAt,
                    last_send_attempt_at = :lastSendAttemptAt,
                    send_window_started_at = :sendWindowStartedAt,
                    send_count = :sendCount,
                    locked_until = :lockedUntil
                WHERE account_id = :accountId
                """, parameters(challenge));
    }

    public boolean consume(UUID accountId, OffsetDateTime consumedAt) {
        return jdbc.update("""
                UPDATE public.email_verification_challenge
                SET consumed_at = :consumedAt
                WHERE account_id = :accountId AND consumed_at IS NULL
                """, new MapSqlParameterSource().addValue("accountId", accountId).addValue("consumedAt", consumedAt)) == 1;
    }

    private MapSqlParameterSource parameters(EmailVerificationChallenge challenge) {
        return new MapSqlParameterSource()
                .addValue("challengeId", challenge.challengeId())
                .addValue("accountId", challenge.accountId())
                .addValue("codeDigest", challenge.codeDigest())
                .addValue("expiresAt", challenge.expiresAt())
                .addValue("failedAttempts", (short) challenge.failedAttempts())
                .addValue("consumedAt", challenge.consumedAt())
                .addValue("lastSendAttemptAt", challenge.lastSendAttemptAt())
                .addValue("sendWindowStartedAt", challenge.sendWindowStartedAt())
                .addValue("sendCount", (short) challenge.sendCount())
                .addValue("lockedUntil", challenge.lockedUntil());
    }

    private EmailVerificationChallenge map(ResultSet resultSet) throws SQLException {
        return new EmailVerificationChallenge(
                resultSet.getObject("challenge_id", UUID.class),
                resultSet.getObject("account_id", UUID.class),
                resultSet.getBytes("code_digest"),
                resultSet.getObject("expires_at", OffsetDateTime.class),
                resultSet.getInt("failed_attempts"),
                resultSet.getObject("consumed_at", OffsetDateTime.class),
                resultSet.getObject("last_send_attempt_at", OffsetDateTime.class),
                resultSet.getObject("send_window_started_at", OffsetDateTime.class),
                resultSet.getInt("send_count"),
                resultSet.getObject("locked_until", OffsetDateTime.class));
    }
}
