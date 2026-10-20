package com.stylemind.identity.service.passwordreset.internal;

import com.stylemind.identity.client.notification.payload.PasswordResetEmailDispatch;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.PasswordResetChallengeRow;
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
        Optional<PasswordResetAccount> accountResult = accountRepository.findLockedByEmail(normalize(email))
                .map(account -> new PasswordResetAccount(account.getAccountId(), account.getAccountStatus(),
                        account.getEmailVerifiedAt()));
        if (accountResult.isEmpty() || !eligible(accountResult.get())) {
            return null;
        }
        PasswordResetAccount account = accountResult.get();
        OffsetDateTime now = now();
        Optional<PasswordResetChallenge> currentResult = challengeRepository
                .findLockedByAccountId(account.accountId()).map(PasswordResetPersistence::snapshot);
        if (currentResult.isEmpty()) {
            UUID challengeId = UUID.randomUUID();
            String code = cryptography.generateOtp();
            challengeRepository.saveAndFlush(row(new PasswordResetChallenge(challengeId, account.accountId(),
                    cryptography.otpDigest(account.accountId(), challengeId, code), now.plus(OTP_TTL),
                    0, null, now, now, 1, null, null, null, null)));
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
        saveRotatedChallenge(new PasswordResetChallenge(
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
        Optional<PasswordResetAccount> accountResult = accountRepository.findLockedByEmail(normalize(email))
                .map(account -> new PasswordResetAccount(account.getAccountId(), account.getAccountStatus(),
                        account.getEmailVerifiedAt()));
        if (accountResult.isEmpty() || !eligible(accountResult.get())) {
            return PasswordResetVerification.invalid();
        }
        PasswordResetAccount account = accountResult.get();
        Optional<PasswordResetChallenge> challengeResult = challengeRepository
                .findLockedByAccountId(account.accountId()).map(PasswordResetPersistence::snapshot);
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
            PasswordResetChallengeRow locked = challengeRepository.findLockedByAccountId(account.accountId())
                    .orElse(null);
            if (locked == null || !locked.getChallengeId().equals(challenge.challengeId())
                    || locked.getVerifiedAt() != null || locked.getConsumedAt() != null) {
                throw new IllegalStateException("Password reset attempt could not be recorded");
            }
            locked.setFailedAttempts((short) attempts);
            locked.setLockedUntil(lockedUntil);
            challengeRepository.flush();
            return PasswordResetVerification.invalid();
        }

        String resetToken = cryptography.generateResetToken();
        PasswordResetChallengeRow locked = challengeRepository.findLockedByAccountId(account.accountId()).orElse(null);
        if (locked == null || !locked.getChallengeId().equals(challenge.challengeId())
                || locked.getVerifiedAt() != null || locked.getConsumedAt() != null) {
            return PasswordResetVerification.invalid();
        }
        locked.setVerifiedAt(now);
        locked.setResetTokenHash(cryptography.resetTokenHash(resetToken));
        locked.setResetTokenExpiresAt(now.plus(RESET_TOKEN_TTL));
        challengeRepository.flush();
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

    private void saveRotatedChallenge(PasswordResetChallenge value) {
        PasswordResetChallengeRow existing = challengeRepository.findLockedByAccountId(value.accountId())
                .orElse(null);
        if (existing != null && !existing.getChallengeId().equals(value.challengeId())) {
            challengeRepository.delete(existing);
            challengeRepository.flush();
            existing = null;
        }
        if (existing == null) {
            challengeRepository.saveAndFlush(row(value));
            return;
        }
        apply(value, existing);
        challengeRepository.flush();
    }

    private static PasswordResetChallengeRow row(PasswordResetChallenge value) {
        PasswordResetChallengeRow row = new PasswordResetChallengeRow();
        row.setChallengeId(value.challengeId());
        row.setAccountId(value.accountId());
        apply(value, row);
        return row;
    }

    private static void apply(PasswordResetChallenge value, PasswordResetChallengeRow row) {
        row.setOtpDigest(value.otpDigest().clone());
        row.setOtpExpiresAt(value.otpExpiresAt());
        row.setFailedAttempts((short) value.failedAttempts());
        row.setLockedUntil(value.lockedUntil());
        row.setLastSendAttemptAt(value.lastSendAttemptAt());
        row.setSendWindowStartedAt(value.sendWindowStartedAt());
        row.setSendCount((short) value.sendCount());
        row.setVerifiedAt(value.verifiedAt());
        row.setResetTokenHash(value.resetTokenHash() == null ? null : value.resetTokenHash().clone());
        row.setResetTokenExpiresAt(value.resetTokenExpiresAt());
        row.setConsumedAt(value.consumedAt());
    }

    private static PasswordResetChallenge snapshot(PasswordResetChallengeRow row) {
        return new PasswordResetChallenge(row.getChallengeId(), row.getAccountId(), row.getOtpDigest().clone(),
                row.getOtpExpiresAt(), row.getFailedAttempts(), row.getLockedUntil(), row.getLastSendAttemptAt(),
                row.getSendWindowStartedAt(), row.getSendCount(), row.getVerifiedAt(),
                row.getResetTokenHash() == null ? null : row.getResetTokenHash().clone(),
                row.getResetTokenExpiresAt(), row.getConsumedAt());
    }
}
