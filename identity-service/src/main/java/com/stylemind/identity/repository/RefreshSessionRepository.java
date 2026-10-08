package com.stylemind.identity.repository;

import com.stylemind.identity.model.RefreshSession;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshSessionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RefreshSessionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RefreshSession> findByTokenHash(String tokenHash) {
        return findByTokenHash(tokenHash, false);
    }

    public Optional<RefreshSession> lockByTokenHash(String tokenHash) {
        return findByTokenHash(tokenHash, true);
    }

    private Optional<RefreshSession> findByTokenHash(String tokenHash, boolean forUpdate) {
        String sql = """
                SELECT session_id, account_id, token_hash, issued_at, expires_at, revoked_at
                FROM public.refresh_session
                WHERE token_hash = :tokenHash
                """ + (forUpdate ? " FOR UPDATE" : "");
        return jdbcTemplate.query(sql, new MapSqlParameterSource("tokenHash", tokenHash), resultSet -> {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            return Optional.of(new RefreshSession(
                    resultSet.getObject("session_id", UUID.class),
                    resultSet.getObject("account_id", UUID.class),
                    resultSet.getString("token_hash"),
                    resultSet.getObject("issued_at", OffsetDateTime.class),
                    resultSet.getObject("expires_at", OffsetDateTime.class),
                    resultSet.getObject("revoked_at", OffsetDateTime.class)));
        });
    }

    public void insert(
            UUID sessionId,
            UUID accountId,
            String tokenHash,
            OffsetDateTime issuedAt,
            OffsetDateTime expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO public.refresh_session (
                    session_id, account_id, token_hash, issued_at, expires_at, revoked_at
                ) VALUES (
                    :sessionId, :accountId, :tokenHash, :issuedAt, :expiresAt, NULL
                )
                """, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("accountId", accountId)
                .addValue("tokenHash", tokenHash)
                .addValue("issuedAt", issuedAt)
                .addValue("expiresAt", expiresAt));
    }

    public boolean revokeIfActive(String tokenHash, OffsetDateTime revokedAt) {
        return jdbcTemplate.update("""
                UPDATE public.refresh_session
                SET revoked_at = :revokedAt
                WHERE token_hash = :tokenHash AND revoked_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("tokenHash", tokenHash)
                .addValue("revokedAt", revokedAt)) == 1;
    }

    public int revokeAllActiveForAccount(UUID accountId, OffsetDateTime revokedAt) {
        return jdbcTemplate.update("""
                UPDATE public.refresh_session SET revoked_at = :revokedAt
                WHERE account_id = :accountId AND revoked_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("revokedAt", revokedAt));
    }
}
