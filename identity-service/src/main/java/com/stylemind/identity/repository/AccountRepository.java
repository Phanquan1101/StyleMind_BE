package com.stylemind.identity.repository;

import com.stylemind.identity.errors.EmailAlreadyExistsException;
import com.stylemind.identity.model.AccountState;
import com.stylemind.identity.model.Account;
import com.stylemind.identity.model.LoginAccount;
import com.stylemind.identity.model.PasswordResetAccount;
import java.util.Optional;
import java.util.UUID;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "account_email_key";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AccountRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAccount(Account account) {
        String sql = """
                INSERT INTO public.account (
                    account_id, email, password_hash, account_status, email_verified_at, registered_at
                ) VALUES (
                    :accountId, :email, :passwordHash, :accountStatus, :emailVerifiedAt, :registeredAt
                )
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("accountId", account.accountId())
                .addValue("email", account.email())
                .addValue("passwordHash", account.passwordHash())
                .addValue("accountStatus", account.accountStatus())
                .addValue("emailVerifiedAt", account.emailVerifiedAt())
                .addValue("registeredAt", account.registeredAt());

        try {
            jdbcTemplate.update(sql, parameters);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueViolation(exception)) {
                throw new EmailAlreadyExistsException();
            }
            throw exception;
        }
    }

    public void insertRole(UUID accountId, String roleCode) {
        String sql = "INSERT INTO public.account_role (account_id, role_code) VALUES (:accountId, :roleCode)";
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("roleCode", roleCode));
    }

    public Optional<LoginAccount> findForLogin(String email) {
        return jdbcTemplate.query("""
                SELECT account_id, password_hash, account_status, email_verified_at
                FROM public.account
                WHERE email = :email
                """, new MapSqlParameterSource("email", email), resultSet -> {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            return Optional.of(new LoginAccount(
                    resultSet.getObject("account_id", UUID.class),
                    resultSet.getString("password_hash"),
                    resultSet.getString("account_status"),
                    resultSet.getObject("email_verified_at", java.time.OffsetDateTime.class)));
        });
    }

    public Optional<LoginAccount> lockLoginAccount(UUID accountId) {
        return jdbcTemplate.query("""
                SELECT account_id, password_hash, account_status, email_verified_at
                FROM public.account
                WHERE account_id = :accountId
                FOR UPDATE
                """, new MapSqlParameterSource("accountId", accountId), resultSet -> {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            return Optional.of(new LoginAccount(
                    resultSet.getObject("account_id", UUID.class),
                    resultSet.getString("password_hash"),
                    resultSet.getString("account_status"),
                    resultSet.getObject("email_verified_at", java.time.OffsetDateTime.class)));
        });
    }

    public boolean lockAccountForSessionOperation(UUID accountId) {
        return jdbcTemplate.query("""
                SELECT account_id
                FROM public.account
                WHERE account_id = :accountId
                FOR UPDATE
                """, new MapSqlParameterSource("accountId", accountId), resultSet -> {
                    return resultSet.next();
                });
    }

    public java.util.List<String> findRoleCodes(UUID accountId) {
        return jdbcTemplate.query("""
                SELECT role_code
                FROM public.account_role
                WHERE account_id = :accountId
                ORDER BY role_code
                """, new MapSqlParameterSource("accountId", accountId),
                (resultSet, rowNumber) -> resultSet.getString("role_code"));
    }

    public Optional<AccountState> lockByEmail(String email) {
        return jdbcTemplate.query("SELECT account_id, account_status FROM public.account WHERE email = :email FOR UPDATE",
                new MapSqlParameterSource("email", email), resultSet -> {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new AccountState(
                            resultSet.getObject("account_id", UUID.class), resultSet.getString("account_status")));
                });
    }

    public Optional<PasswordResetAccount> lockPasswordResetAccountByEmail(String email) {
        return jdbcTemplate.query("""
                SELECT account_id, account_status, email_verified_at
                FROM public.account WHERE email = :email FOR UPDATE
                """, new MapSqlParameterSource("email", email), resultSet -> {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new PasswordResetAccount(
                            resultSet.getObject("account_id", UUID.class),
                            resultSet.getString("account_status"),
                            resultSet.getObject("email_verified_at", java.time.OffsetDateTime.class)));
                });
    }

    public boolean updatePasswordHash(UUID accountId, String passwordHash) {
        return jdbcTemplate.update("""
                UPDATE public.account SET password_hash = :passwordHash
                WHERE account_id = :accountId AND account_status = 'ACTIVE' AND email_verified_at IS NOT NULL
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("passwordHash", passwordHash)) == 1;
    }

    public boolean activate(UUID accountId, java.time.OffsetDateTime verifiedAt) {
        return jdbcTemplate.update("""
                UPDATE public.account
                SET account_status = 'ACTIVE', email_verified_at = :verifiedAt
                WHERE account_id = :accountId AND account_status = 'PENDING_VERIFICATION'
                  AND email_verified_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("verifiedAt", verifiedAt)) == 1;
    }

    private boolean isEmailUniqueViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException postgresException
                    && "23505".equals(postgresException.getSQLState())
                    && postgresException.getServerErrorMessage() != null
                    && EMAIL_UNIQUE_CONSTRAINT.equals(postgresException.getServerErrorMessage().getConstraint())) {
                return true;
            }
        }
        return false;
    }
}
