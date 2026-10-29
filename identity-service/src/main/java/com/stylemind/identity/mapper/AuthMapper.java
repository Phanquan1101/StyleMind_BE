package com.stylemind.identity.mapper;

import com.stylemind.identity.dto.register.RegisterResponse;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import com.stylemind.identity.entity.EmailVerificationChallengeRow;
import com.stylemind.identity.entity.PasswordResetChallengeRow;
import com.stylemind.identity.entity.RefreshSessionRow;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public Account account(UUID accountId, String email, String passwordHash, String status,
            OffsetDateTime registeredAt) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setEmail(email);
        account.setPasswordHash(passwordHash);
        account.setAccountStatus(status);
        account.setRegisteredAt(registeredAt);
        return account;
    }

    public AccountRole accountRole(UUID accountId, String roleCode) {
        return new AccountRole(accountId, roleCode);
    }

    public RefreshSessionRow refreshSession(UUID sessionId, UUID accountId, String tokenHash,
            OffsetDateTime issuedAt, OffsetDateTime expiresAt) {
        RefreshSessionRow session = new RefreshSessionRow();
        session.setSessionId(sessionId);
        session.setAccountId(accountId);
        session.setTokenHash(tokenHash);
        session.setIssuedAt(issuedAt);
        session.setExpiresAt(expiresAt);
        return session;
    }

    public EmailVerificationChallengeRow emailVerificationChallenge(UUID challengeId, UUID accountId,
            byte[] codeDigest, OffsetDateTime expiresAt, int failedAttempts, OffsetDateTime lastSendAttemptAt,
            OffsetDateTime sendWindowStartedAt, int sendCount, OffsetDateTime lockedUntil) {
        EmailVerificationChallengeRow challenge = new EmailVerificationChallengeRow();
        challenge.setChallengeId(challengeId);
        challenge.setAccountId(accountId);
        challenge.setCodeDigest(codeDigest.clone());
        challenge.setExpiresAt(expiresAt);
        challenge.setFailedAttempts((short) failedAttempts);
        challenge.setLastSendAttemptAt(lastSendAttemptAt);
        challenge.setSendWindowStartedAt(sendWindowStartedAt);
        challenge.setSendCount((short) sendCount);
        challenge.setLockedUntil(lockedUntil);
        return challenge;
    }

    public PasswordResetChallengeRow passwordResetChallenge(UUID challengeId, UUID accountId, byte[] otpDigest,
            OffsetDateTime otpExpiresAt, int failedAttempts, OffsetDateTime lockedUntil,
            OffsetDateTime lastSendAttemptAt, OffsetDateTime sendWindowStartedAt, int sendCount,
            OffsetDateTime verifiedAt, byte[] resetTokenHash, OffsetDateTime resetTokenExpiresAt,
            OffsetDateTime consumedAt) {
        PasswordResetChallengeRow challenge = new PasswordResetChallengeRow();
        challenge.setChallengeId(challengeId);
        challenge.setAccountId(accountId);
        challenge.setOtpDigest(otpDigest.clone());
        challenge.setOtpExpiresAt(otpExpiresAt);
        challenge.setFailedAttempts((short) failedAttempts);
        challenge.setLockedUntil(lockedUntil);
        challenge.setLastSendAttemptAt(lastSendAttemptAt);
        challenge.setSendWindowStartedAt(sendWindowStartedAt);
        challenge.setSendCount((short) sendCount);
        challenge.setVerifiedAt(verifiedAt);
        challenge.setResetTokenHash(resetTokenHash == null ? null : resetTokenHash.clone());
        challenge.setResetTokenExpiresAt(resetTokenExpiresAt);
        challenge.setConsumedAt(consumedAt);
        return challenge;
    }

    public RegisterResponse registerResponse(UUID accountId, String email, String accountStatus) {
        return new RegisterResponse(accountId, email, accountStatus);
    }
}
