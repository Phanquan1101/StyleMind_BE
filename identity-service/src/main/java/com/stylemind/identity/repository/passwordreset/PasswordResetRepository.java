package com.stylemind.identity.repository.passwordreset;

import com.stylemind.identity.model.passwordreset.PasswordResetChallenge;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordResetRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PasswordResetRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(PasswordResetChallenge challenge) {
        jdbc.update("""
                INSERT INTO public.password_reset_challenge (
                    challenge_id, account_id, otp_digest, otp_expires_at, failed_attempts, locked_until,
                    last_send_attempt_at, send_window_started_at, send_count, verified_at,
                    reset_token_hash, reset_token_expires_at, consumed_at
                ) VALUES (
                    :challengeId, :accountId, :otpDigest, :otpExpiresAt, :failedAttempts, :lockedUntil,
                    :lastSendAttemptAt, :sendWindowStartedAt, :sendCount, :verifiedAt,
                    :resetTokenHash, :resetTokenExpiresAt, :consumedAt
                )
                """, parameters(challenge));
    }

    public Optional<PasswordResetChallenge> lockByAccount(UUID accountId) {
        return select("account_id = :accountId", new MapSqlParameterSource("accountId", accountId), true);
    }

    public Optional<UUID> findAccountIdByResetTokenHash(byte[] hash) {
        return jdbc.query("""
                SELECT account_id FROM public.password_reset_challenge WHERE reset_token_hash = :tokenHash
                """, new MapSqlParameterSource("tokenHash", hash), result ->
                result.next() ? Optional.of(result.getObject("account_id", UUID.class)) : Optional.empty());
    }

    public Optional<PasswordResetChallenge> lockByResetTokenHash(byte[] hash) {
        return select("reset_token_hash = :tokenHash", new MapSqlParameterSource("tokenHash", hash), true);
    }

    public void rotateOtp(PasswordResetChallenge challenge) {
        jdbc.update("""
                UPDATE public.password_reset_challenge SET
                    challenge_id = :challengeId,
                    otp_digest = :otpDigest,
                    otp_expires_at = :otpExpiresAt,
                    failed_attempts = :failedAttempts,
                    locked_until = :lockedUntil,
                    last_send_attempt_at = :lastSendAttemptAt,
                    send_window_started_at = :sendWindowStartedAt,
                    send_count = :sendCount,
                    verified_at = NULL,
                    reset_token_hash = NULL,
                    reset_token_expires_at = NULL,
                    consumed_at = NULL
                WHERE account_id = :accountId
                """, parameters(challenge));
    }

    public boolean recordFailedAttempt(UUID accountId, UUID challengeId, int failedAttempts,
            OffsetDateTime lockedUntil) {
        return jdbc.update("""
                UPDATE public.password_reset_challenge
                SET failed_attempts = :failedAttempts, locked_until = :lockedUntil
                WHERE account_id = :accountId AND challenge_id = :challengeId
                  AND verified_at IS NULL AND consumed_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("challengeId", challengeId)
                .addValue("failedAttempts", (short) failedAttempts)
                .addValue("lockedUntil", lockedUntil)) == 1;
    }

    public boolean issueResetGrant(UUID accountId, UUID challengeId, OffsetDateTime verifiedAt,
            byte[] tokenHash, OffsetDateTime tokenExpiresAt) {
        return jdbc.update("""
                UPDATE public.password_reset_challenge SET
                    verified_at = :verifiedAt,
                    reset_token_hash = :tokenHash,
                    reset_token_expires_at = :tokenExpiresAt
                WHERE account_id = :accountId AND challenge_id = :challengeId
                  AND verified_at IS NULL AND consumed_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("challengeId", challengeId)
                .addValue("verifiedAt", verifiedAt)
                .addValue("tokenHash", tokenHash)
                .addValue("tokenExpiresAt", tokenExpiresAt)) == 1;
    }

    public boolean consumeResetGrant(UUID accountId, UUID challengeId, byte[] tokenHash,
            OffsetDateTime consumedAt) {
        return jdbc.update("""
                UPDATE public.password_reset_challenge SET consumed_at = :consumedAt
                WHERE account_id = :accountId AND challenge_id = :challengeId
                  AND reset_token_hash = :tokenHash AND verified_at IS NOT NULL AND consumed_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("challengeId", challengeId)
                .addValue("tokenHash", tokenHash)
                .addValue("consumedAt", consumedAt)) == 1;
    }

    private Optional<PasswordResetChallenge> select(String predicate, MapSqlParameterSource parameters,
            boolean forUpdate) {
        return jdbc.query("""
                SELECT challenge_id, account_id, otp_digest, otp_expires_at, failed_attempts, locked_until,
                       last_send_attempt_at, send_window_started_at, send_count, verified_at,
                       reset_token_hash, reset_token_expires_at, consumed_at
                FROM public.password_reset_challenge WHERE """ + " " + predicate
                + (forUpdate ? " FOR UPDATE" : ""),
                parameters, result -> result.next() ? Optional.of(map(result)) : Optional.empty());
    }

    private MapSqlParameterSource parameters(PasswordResetChallenge challenge) {
        return new MapSqlParameterSource()
                .addValue("challengeId", challenge.challengeId())
                .addValue("accountId", challenge.accountId())
                .addValue("otpDigest", challenge.otpDigest())
                .addValue("otpExpiresAt", challenge.otpExpiresAt())
                .addValue("failedAttempts", (short) challenge.failedAttempts())
                .addValue("lockedUntil", challenge.lockedUntil())
                .addValue("lastSendAttemptAt", challenge.lastSendAttemptAt())
                .addValue("sendWindowStartedAt", challenge.sendWindowStartedAt())
                .addValue("sendCount", (short) challenge.sendCount())
                .addValue("verifiedAt", challenge.verifiedAt())
                .addValue("resetTokenHash", challenge.resetTokenHash())
                .addValue("resetTokenExpiresAt", challenge.resetTokenExpiresAt())
                .addValue("consumedAt", challenge.consumedAt());
    }

    private PasswordResetChallenge map(ResultSet result) throws SQLException {
        return new PasswordResetChallenge(
                result.getObject("challenge_id", UUID.class),
                result.getObject("account_id", UUID.class),
                result.getBytes("otp_digest"),
                result.getObject("otp_expires_at", OffsetDateTime.class),
                result.getInt("failed_attempts"),
                result.getObject("locked_until", OffsetDateTime.class),
                result.getObject("last_send_attempt_at", OffsetDateTime.class),
                result.getObject("send_window_started_at", OffsetDateTime.class),
                result.getInt("send_count"),
                result.getObject("verified_at", OffsetDateTime.class),
                result.getBytes("reset_token_hash"),
                result.getObject("reset_token_expires_at", OffsetDateTime.class),
                result.getObject("consumed_at", OffsetDateTime.class));
    }
}
