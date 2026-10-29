package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.stylemind.identity.mapper.AuthMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthMapperTests {

    private final AuthMapper mapper = new AuthMapper();

    @Test
    void createsAuthEntitiesAndRegistrationResponseFromSuppliedValues() {
        UUID accountId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-10-28T12:00:00Z");
        byte[] emailDigest = {1, 2, 3};
        byte[] resetDigest = {4, 5, 6};
        byte[] resetTokenHash = {7, 8, 9};

        var account = mapper.account(accountId, "customer@example.test", "{bcrypt}hash", "PENDING_VERIFICATION", now);
        var role = mapper.accountRole(accountId, "CUSTOMER");
        var session = mapper.refreshSession(sessionId, accountId, "hashed-refresh-token", now, now.plusDays(7));
        var emailChallenge = mapper.emailVerificationChallenge(challengeId, accountId, emailDigest, now.plusMinutes(5),
                2, now, now.minusHours(1), 3, now.plusMinutes(15));
        var resetChallenge = mapper.passwordResetChallenge(challengeId, accountId, resetDigest, now.plusMinutes(5),
                1, now.plusMinutes(15), now, now.minusHours(1), 2, now, resetTokenHash, now.plusMinutes(5), null);
        var response = mapper.registerResponse(accountId, account.getEmail(), account.getAccountStatus());

        emailDigest[0] = 99;
        resetDigest[0] = 99;
        resetTokenHash[0] = 99;

        assertThat(account.getPasswordHash()).isEqualTo("{bcrypt}hash");
        assertThat(role.getAccountId()).isEqualTo(accountId);
        assertThat(role.getRoleCode()).isEqualTo("CUSTOMER");
        assertThat(session.getSessionId()).isEqualTo(sessionId);
        assertThat(session.getExpiresAt()).isEqualTo(now.plusDays(7));
        assertThat(emailChallenge.getCodeDigest()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(emailChallenge.getFailedAttempts()).isEqualTo((short) 2);
        assertThat(resetChallenge.getOtpDigest()).containsExactly((byte) 4, (byte) 5, (byte) 6);
        assertThat(resetChallenge.getResetTokenHash()).containsExactly((byte) 7, (byte) 8, (byte) 9);
        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.accountStatus()).isEqualTo("PENDING_VERIFICATION");
    }
}
