package com.stylemind.identity.service.passwordreset.internal;

import com.stylemind.identity.client.notification.payload.PasswordResetEmailDispatch;
import com.stylemind.identity.model.passwordreset.PasswordResetAccount;
import com.stylemind.identity.model.passwordreset.PasswordResetChallenge;
import com.stylemind.identity.model.passwordreset.PasswordResetVerification;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.passwordreset.PasswordResetRepository;
import com.stylemind.identity.security.PasswordResetCryptography;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetPersistence {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration SEND_WINDOW = Duration.ofHours(1);
    private static final int MAX_SENDS_PER_WINDOW = 3;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final AccountRepository accountRepository;
    private final PasswordResetRepository challengeRepository;
    private final PasswordResetCryptography cryptography;
    private final Clock clock;

    public PasswordResetPersistence(AccountRepository accountRepository,
            PasswordResetRepository challengeRepository, PasswordResetCryptography cryptography, Clock clock) {
        this.accountRepository = accountRepository;
        this.challengeRepository = challengeRepository;
        this.cryptography = cryptography;
        this.clock = clock;
    }

    @Transactional
    public PasswordResetEmailDispatch reserveOtp(String email) {
        Optional<PasswordResetAccount> accountResult = accountRepository.lockPasswordResetAccountByEmail(
                normalize(email));
        if (accountResult.isEmpty() || !eligible(accountResult.get())) {
            return null;
        }
        PasswordResetAccount account = accountResult.get();
        OffsetDateTime now = now();
        Optional<PasswordResetChallenge> currentResult = challengeRepository.lockByAccount(account.accountId());
        if (currentResult.isEmpty()) {
            UUID challengeId = UUID.randomUUID();
            String code = cryptography.generateOtp();
            challengeRepository.insert(new PasswordResetChallenge(challengeId, account.accountId(),
                    cryptography.otpDigest(account.accountId(), challengeId, code), now.plus(OTP_TTL),
                    0, null, now, now, 1, null, null, null, null));
            return new PasswordResetEmailDispatch(normalize(email), code);
        }

        PasswordResetChallenge current = currentResult.get();
        if (hasActiveGrant(current, now)) {
            return null;
        }
        if (current.lockedUntil() != null && now.isBefore(current.lockedUntil())) {
            return null;
        }

        OffsetDateTime windowEnd = current.sendWindowStartedAt().plus(SEND_WINDOW);
        boolean windowElapsed = !now.isBefore(windowEnd);
        OffsetDateTime cooldownEnd = current.lastSendAttemptAt().plus(SEND_COOLDOWN);
        boolean coolingDown = now.isBefore(cooldownEnd);
        boolean sendLimitReached = !windowElapsed && current.sendCount() >= MAX_SENDS_PER_WINDOW;
        if (coolingDown || sendLimitReached) {
            return null;
        }

        boolean lockElapsed = current.lockedUntil() != null;
        int sendCount = windowElapsed ? 1 : current.sendCount() + 1;
        OffsetDateTime windowStart = windowElapsed ? now : current.sendWindowStartedAt();
        UUID challengeId = UUID.randomUUID();
        String code = cryptography.generateOtp();
        challengeRepository.rotateOtp(new PasswordResetChallenge(
                challengeId,
                account.accountId(),
                cryptography.otpDigest(account.accountId(), challengeId, code),
                now.plus(OTP_TTL),
                lockElapsed ? 0 : current.failedAttempts(),
                lockElapsed ? null : current.lockedUntil(),
                now,
                windowStart,
                sendCount,
                null,
                null,
                null,
                null));
        return new PasswordResetEmailDispatch(normalize(email), code);
    }

    @Transactional
    public PasswordResetVerification verifyOtp(String email, String code) {
        Optional<PasswordResetAccount> accountResult = accountRepository.lockPasswordResetAccountByEmail(
                normalize(email));
        if (accountResult.isEmpty() || !eligible(accountResult.get())) {
            return PasswordResetVerification.invalid();
        }
        PasswordResetAccount account = accountResult.get();
        Optional<PasswordResetChallenge> challengeResult = challengeRepository.lockByAccount(account.accountId());
        if (challengeResult.isEmpty()) {
            return PasswordResetVerification.invalid();
        }

        PasswordResetChallenge challenge = challengeResult.get();
        OffsetDateTime now = now();
        if (challenge.lockedUntil() != null && now.isBefore(challenge.lockedUntil())) {
            return PasswordResetVerification.locked(secondsUntil(now, challenge.lockedUntil()));
        }
        if (challenge.failedAttempts() >= MAX_FAILED_ATTEMPTS || challenge.verifiedAt() != null
                || challenge.consumedAt() != null || !now.isBefore(challenge.otpExpiresAt())) {
            return PasswordResetVerification.invalid();
        }
        if (!cryptography.matchesOtp(account.accountId(), challenge.challengeId(), code, challenge.otpDigest())) {
            int attempts = challenge.failedAttempts() + 1;
            OffsetDateTime lockedUntil = attempts == MAX_FAILED_ATTEMPTS ? now.plus(LOCK_TTL) : null;
            if (!challengeRepository.recordFailedAttempt(account.accountId(), challenge.challengeId(),
                    attempts, lockedUntil)) {
                throw new IllegalStateException("Password reset attempt could not be recorded");
            }
            return PasswordResetVerification.invalid();
        }

        String resetToken = cryptography.generateResetToken();
        if (!challengeRepository.issueResetGrant(account.accountId(), challenge.challengeId(), now,
                cryptography.resetTokenHash(resetToken), now.plus(RESET_TOKEN_TTL))) {
            return PasswordResetVerification.invalid();
        }
        return PasswordResetVerification.verified(resetToken);
    }

    private static boolean eligible(PasswordResetAccount account) {
        return "ACTIVE".equals(account.accountStatus()) && account.emailVerifiedAt() != null;
    }

    private static boolean hasActiveGrant(PasswordResetChallenge challenge, OffsetDateTime now) {
        return challenge.verifiedAt() != null && challenge.consumedAt() == null
                && challenge.resetTokenExpiresAt() != null && now.isBefore(challenge.resetTokenExpiresAt());
    }

    private OffsetDateTime now() {
        OffsetDateTime current = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        return current.withNano((current.getNano() / 1_000) * 1_000);
    }

    private static long secondsUntil(OffsetDateTime now, OffsetDateTime end) {
        long millis = Duration.between(now, end).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
