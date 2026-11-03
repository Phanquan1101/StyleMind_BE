package com.stylemind.identity.service.impl;

import com.stylemind.identity.client.notification.NotificationEmailClient;
import com.stylemind.identity.client.notification.payload.EmailDispatch;
import com.stylemind.identity.client.notification.payload.PasswordResetEmailDispatch;
import com.stylemind.identity.dto.passwordreset.ForgotPasswordResponse;
import com.stylemind.identity.dto.passwordreset.ResetTokenResponse;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.EmailVerificationChallengeRow;
import com.stylemind.identity.entity.PasswordResetChallengeRow;
import com.stylemind.identity.entity.RefreshSessionRow;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.ErrorCode;
import com.stylemind.identity.mapper.AuthMapper;
import com.stylemind.identity.repository.AccountRepository;
import com.stylemind.identity.repository.EmailVerificationRepository;
import com.stylemind.identity.repository.PasswordResetRepository;
import com.stylemind.identity.repository.RefreshSessionRepository;
import com.stylemind.identity.security.verification.PasswordResetCryptography;
import com.stylemind.identity.security.verification.VerificationCodeService;
import com.stylemind.identity.service.AccountVerificationService;
import com.stylemind.identity.validation.auth.PasswordResetTokenFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountVerificationServiceImpl implements AccountVerificationService {

    private static final Logger log = LoggerFactory.getLogger("com.stylemind.identity.service.AccountVerificationService");
    private static final String INITIAL_ACCOUNT_STATUS = "PENDING_VERIFICATION";
    private static final String GENERIC_RESET_REQUEST_MESSAGE =
            "If the account is eligible, password reset instructions will be sent.";
    private static final Duration EMAIL_OTP_TTL = Duration.ofMinutes(5);
    private static final Duration EMAIL_VERIFICATION_LOCK = Duration.ofMinutes(15);
    private static final Duration EMAIL_RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration EMAIL_SEND_WINDOW = Duration.ofHours(1);
    private static final int EMAIL_MAX_SENDS_PER_WINDOW = 3;
    private static final int EMAIL_MAX_FAILED_ATTEMPTS = 5;

    private static final Duration RESET_OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration RESET_LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration RESET_SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration RESET_SEND_WINDOW = Duration.ofHours(1);
    private static final int RESET_MAX_SENDS_PER_WINDOW = 3;
    private static final int RESET_MAX_FAILED_ATTEMPTS = 5;

    private final AccountRepository accountRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final VerificationCodeService verificationCodeService;
    private final PasswordResetCryptography resetCryptography;
    private final NotificationEmailClient emailClient;
    private final AuthMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public AccountVerificationServiceImpl(
            AccountRepository accountRepository,
            EmailVerificationRepository emailVerificationRepository,
            PasswordResetRepository passwordResetRepository,
            RefreshSessionRepository refreshSessionRepository,
            VerificationCodeService verificationCodeService,
            PasswordResetCryptography resetCryptography,
            NotificationEmailClient emailClient,
            AuthMapper mapper,
            PasswordEncoder passwordEncoder,
            Clock clock,
            TransactionTemplate transactions) {
        this.accountRepository = accountRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.verificationCodeService = verificationCodeService;
        this.resetCryptography = resetCryptography;
        this.emailClient = emailClient;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.transactions = transactions;
    }

    public void verifyEmail(String email, String code) {
        ApiOperationException failure = transactions.execute(status -> verifyEmailInTransaction(email, code));
        throwIfFailure(failure);
    }

    public void resendEmailVerification(String email) {
        ResendDispatch result = transactions.execute(status -> reserveEmailResend(email));
        if (result == null) {
            throw new IllegalStateException("Email resend transaction returned no result");
        }
        throwIfFailure(result.failure());
        if (result.dispatch() == null) {
            throw new IllegalStateException("Email resend transaction returned no dispatch");
        }
        try {
            emailClient.send(result.dispatch());
        } catch (ApiOperationException exception) {
            throw new ApiOperationException(exception.errorCode(),
                    "Email delivery could not be confirmed. The account remains PENDING_VERIFICATION.",
                    exception.retryAfterSeconds());
        }
    }

    public ForgotPasswordResponse requestPasswordReset(String email) {
        PasswordResetEmailDispatch dispatch = transactions.execute(status -> reservePasswordResetOtp(email));
        if (dispatch != null) {
            try {
                emailClient.sendPasswordReset(dispatch);
            } catch (ApiOperationException exception) {
                log.warn("Password reset email delivery did not complete correlationId={} outcome={}",
                        MDC.get("correlationId"), exception.code());
            }
        }
        return new ForgotPasswordResponse(GENERIC_RESET_REQUEST_MESSAGE);
    }

    public ResetTokenResponse verifyPasswordResetOtp(String email, String code) {
        ResetVerification result = transactions.execute(status -> verifyPasswordResetOtpInTransaction(email, code));
        if (result == null) {
            throw new IllegalStateException("Password reset verification transaction returned no result");
        }
        throwIfFailure(result.failure());
        return result.response();
    }

    public void resetPassword(String resetToken, String newPassword) {
        if (!PasswordResetTokenFormat.isValid(resetToken)) {
            throw new ApiOperationException(ErrorCode.INVALID_REQUEST);
        }
        byte[] tokenHash = resetCryptography.resetTokenHash(resetToken);
        UUID accountId = passwordResetRepository.findAccountIdByResetTokenHash(tokenHash)
                .orElseThrow(() -> new ApiOperationException(ErrorCode.PASSWORD_RESET_INVALID));

        // Password hashing stays outside the transaction that locks the account and reset challenge.
        String newPasswordHash = passwordEncoder.encode(newPassword);
        Boolean reset = transactions.execute(status -> resetPasswordInTransaction(accountId, tokenHash, newPasswordHash));
        if (!Boolean.TRUE.equals(reset)) {
            throw new ApiOperationException(ErrorCode.PASSWORD_RESET_INVALID);
        }
    }

    private ApiOperationException verifyEmailInTransaction(String email, String code) {
        Account account = accountRepository.findLockedByEmail(email).orElse(null);
        if (account == null) {
            return new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        if ("ACTIVE".equals(account.getAccountStatus())) {
            return new ApiOperationException(ErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        if (!INITIAL_ACCOUNT_STATUS.equals(account.getAccountStatus())) {
            return new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_NOT_ALLOWED);
        }

        EmailVerificationChallengeRow challenge = emailVerificationRepository
                .findLockedByAccountId(account.getAccountId()).orElse(null);
        if (challenge == null) {
            return new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }

        OffsetDateTime now = now();
        if (challenge.getLockedUntil() != null && now.isBefore(challenge.getLockedUntil())) {
            return new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED,
                    ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED.getDefaultMessage(),
                    secondsUntil(now, challenge.getLockedUntil()));
        }
        if (challenge.getFailedAttempts() >= EMAIL_MAX_FAILED_ATTEMPTS) {
            return new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (challenge.getConsumedAt() != null) {
            return new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        if (!now.isBefore(challenge.getExpiresAt())) {
            return new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }
        if (!verificationCodeService.matches(account.getAccountId(), challenge.getChallengeId(), code,
                challenge.getCodeDigest())) {
            int attempts = challenge.getFailedAttempts() + 1;
            challenge.setFailedAttempts((short) attempts);
            if (attempts == EMAIL_MAX_FAILED_ATTEMPTS) {
                challenge.setLockedUntil(now.plus(EMAIL_VERIFICATION_LOCK));
            }
            emailVerificationRepository.flush();
            return new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }

        EmailVerificationChallengeRow lockedChallenge = emailVerificationRepository
                .findLockedByAccountId(account.getAccountId()).orElse(null);
        if (lockedChallenge == null || lockedChallenge.getConsumedAt() != null) {
            return new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        lockedChallenge.setConsumedAt(now);
        emailVerificationRepository.flush();

        Account lockedAccount = accountRepository.findLockedByAccountId(account.getAccountId()).orElse(null);
        if (lockedAccount == null || !INITIAL_ACCOUNT_STATUS.equals(lockedAccount.getAccountStatus())
                || lockedAccount.getEmailVerifiedAt() != null) {
            throw new IllegalStateException("Account state changed while locked");
        }
        lockedAccount.setAccountStatus("ACTIVE");
        lockedAccount.setEmailVerifiedAt(now);
        accountRepository.flush();
        return null;
    }

    private ResendDispatch reserveEmailResend(String email) {
        Account account = accountRepository.findLockedByEmail(email).orElse(null);
        if (account == null) {
            return new ResendDispatch(new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_INVALID), null);
        }
        if ("ACTIVE".equals(account.getAccountStatus())) {
            return new ResendDispatch(new ApiOperationException(ErrorCode.EMAIL_ALREADY_VERIFIED), null);
        }
        if (!INITIAL_ACCOUNT_STATUS.equals(account.getAccountStatus())) {
            return new ResendDispatch(new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_NOT_ALLOWED), null);
        }

        EmailVerificationChallengeRow current = emailVerificationRepository
                .findLockedByAccountId(account.getAccountId()).orElse(null);
        if (current == null) {
            return new ResendDispatch(new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_INVALID), null);
        }

        OffsetDateTime now = now();
        if (current.getLockedUntil() != null && now.isBefore(current.getLockedUntil())) {
            return new ResendDispatch(new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED,
                    ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED.getDefaultMessage(),
                    secondsUntil(now, current.getLockedUntil())), null);
        }

        OffsetDateTime windowEnd = current.getSendWindowStartedAt().plus(EMAIL_SEND_WINDOW);
        boolean windowElapsed = !now.isBefore(windowEnd);
        OffsetDateTime cooldownEnd = current.getLastSendAttemptAt().plus(EMAIL_RESEND_COOLDOWN);
        boolean coolingDown = now.isBefore(cooldownEnd);
        boolean windowLimited = !windowElapsed && current.getSendCount() >= EMAIL_MAX_SENDS_PER_WINDOW;
        if (coolingDown || windowLimited) {
            long cooldownWait = coolingDown ? secondsUntil(now, cooldownEnd) : 0;
            long windowWait = windowLimited ? secondsUntil(now, windowEnd) : 0;
            return new ResendDispatch(new ApiOperationException(ErrorCode.EMAIL_VERIFICATION_SEND_LIMIT_EXCEEDED,
                    ErrorCode.EMAIL_VERIFICATION_SEND_LIMIT_EXCEEDED.getDefaultMessage(),
                    Math.max(cooldownWait, windowWait)), null);
        }

        boolean lockElapsed = current.getLockedUntil() != null && !now.isBefore(current.getLockedUntil());
        int sendCount = windowElapsed ? 1 : current.getSendCount() + 1;
        OffsetDateTime windowStart = windowElapsed ? now : current.getSendWindowStartedAt();
        int attempts = lockElapsed ? 0 : current.getFailedAttempts();
        OffsetDateTime lockedUntil = lockElapsed ? null : current.getLockedUntil();
        String verificationCode = verificationCodeService.generateCode();
        UUID challengeId = UUID.randomUUID();

        emailVerificationRepository.delete(current);
        emailVerificationRepository.flush();
        EmailVerificationChallengeRow replacement = mapper.emailVerificationChallenge(challengeId,
                account.getAccountId(), verificationCodeService.digest(account.getAccountId(), challengeId,
                        verificationCode),
                now.plus(EMAIL_OTP_TTL), attempts, now, windowStart, sendCount, lockedUntil);
        emailVerificationRepository.save(replacement);

        return new ResendDispatch(null, new EmailDispatch(email, verificationCode));
    }

    private PasswordResetEmailDispatch reservePasswordResetOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        Account account = accountRepository.findLockedByEmail(normalizedEmail).orElse(null);
        if (!isPasswordResetEligible(account)) {
            return null;
        }

        OffsetDateTime now = passwordResetNow();
        PasswordResetChallengeRow current = passwordResetRepository
                .findLockedByAccountId(account.getAccountId()).orElse(null);
        if (current == null) {
            UUID challengeId = UUID.randomUUID();
            String code = resetCryptography.generateOtp();
            passwordResetRepository.save(mapper.passwordResetChallenge(challengeId, account.getAccountId(),
                    resetCryptography.otpDigest(account.getAccountId(), challengeId, code), now.plus(RESET_OTP_TTL),
                    0, null, now, now, 1, null, null, null, null));
            return new PasswordResetEmailDispatch(normalizedEmail, code);
        }

        if (hasActiveResetGrant(current, now)) {
            return null;
        }
        if (current.getLockedUntil() != null && now.isBefore(current.getLockedUntil())) {
            return null;
        }

        OffsetDateTime windowEnd = current.getSendWindowStartedAt().plus(RESET_SEND_WINDOW);
        boolean windowElapsed = !now.isBefore(windowEnd);
        OffsetDateTime cooldownEnd = current.getLastSendAttemptAt().plus(RESET_SEND_COOLDOWN);
        boolean coolingDown = now.isBefore(cooldownEnd);
        boolean sendLimitReached = !windowElapsed && current.getSendCount() >= RESET_MAX_SENDS_PER_WINDOW;
        if (coolingDown || sendLimitReached) {
            return null;
        }

        boolean lockElapsed = current.getLockedUntil() != null;
        int sendCount = windowElapsed ? 1 : current.getSendCount() + 1;
        OffsetDateTime windowStart = windowElapsed ? now : current.getSendWindowStartedAt();
        UUID challengeId = UUID.randomUUID();
        String code = resetCryptography.generateOtp();
        int failedAttempts = lockElapsed ? 0 : current.getFailedAttempts();
        OffsetDateTime lockedUntil = lockElapsed ? null : current.getLockedUntil();

        passwordResetRepository.delete(current);
        passwordResetRepository.flush();
        PasswordResetChallengeRow replacement = mapper.passwordResetChallenge(challengeId, account.getAccountId(),
                resetCryptography.otpDigest(account.getAccountId(), challengeId, code), now.plus(RESET_OTP_TTL),
                failedAttempts, lockedUntil, now, windowStart, sendCount, null, null, null, null);
        passwordResetRepository.save(replacement);
        return new PasswordResetEmailDispatch(normalizedEmail, code);
    }

    private ResetVerification verifyPasswordResetOtpInTransaction(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        Account account = accountRepository.findLockedByEmail(normalizedEmail).orElse(null);
        if (!isPasswordResetEligible(account)) {
            return new ResetVerification(null,
                    new ApiOperationException(ErrorCode.PASSWORD_RESET_VERIFICATION_INVALID));
        }

        PasswordResetChallengeRow challenge = passwordResetRepository
                .findLockedByAccountId(account.getAccountId()).orElse(null);
        if (challenge == null) {
            return new ResetVerification(null,
                    new ApiOperationException(ErrorCode.PASSWORD_RESET_VERIFICATION_INVALID));
        }

        OffsetDateTime now = passwordResetNow();
        if (challenge.getLockedUntil() != null && now.isBefore(challenge.getLockedUntil())) {
            return new ResetVerification(null,
                    new ApiOperationException(ErrorCode.PASSWORD_RESET_ATTEMPTS_EXCEEDED,
                            ErrorCode.PASSWORD_RESET_ATTEMPTS_EXCEEDED.getDefaultMessage(),
                            secondsUntil(now, challenge.getLockedUntil())));
        }
        if (challenge.getFailedAttempts() >= RESET_MAX_FAILED_ATTEMPTS || challenge.getVerifiedAt() != null
                || challenge.getConsumedAt() != null || !now.isBefore(challenge.getOtpExpiresAt())) {
            return new ResetVerification(null,
                    new ApiOperationException(ErrorCode.PASSWORD_RESET_VERIFICATION_INVALID));
        }
        if (!resetCryptography.matchesOtp(account.getAccountId(), challenge.getChallengeId(), code,
                challenge.getOtpDigest())) {
            int attempts = challenge.getFailedAttempts() + 1;
            challenge.setFailedAttempts((short) attempts);
            challenge.setLockedUntil(attempts == RESET_MAX_FAILED_ATTEMPTS ? now.plus(RESET_LOCK_TTL) : null);
            passwordResetRepository.flush();
            return new ResetVerification(null,
                    new ApiOperationException(ErrorCode.PASSWORD_RESET_VERIFICATION_INVALID));
        }

        String resetToken = resetCryptography.generateResetToken();
        PasswordResetChallengeRow lockedChallenge = passwordResetRepository
                .findLockedByAccountId(account.getAccountId()).orElse(null);
        if (lockedChallenge == null || !lockedChallenge.getChallengeId().equals(challenge.getChallengeId())
                || lockedChallenge.getVerifiedAt() != null || lockedChallenge.getConsumedAt() != null) {
            return new ResetVerification(null,
                    new ApiOperationException(ErrorCode.PASSWORD_RESET_VERIFICATION_INVALID));
        }
        lockedChallenge.setVerifiedAt(now);
        lockedChallenge.setResetTokenHash(resetCryptography.resetTokenHash(resetToken));
        lockedChallenge.setResetTokenExpiresAt(now.plus(RESET_TOKEN_TTL));
        passwordResetRepository.flush();
        return new ResetVerification(new ResetTokenResponse(resetToken, 300), null);
    }

    private boolean resetPasswordInTransaction(UUID accountId, byte[] tokenHash, String newPasswordHash) {
        Account account = accountRepository.findLockedByAccountId(accountId).orElse(null);
        if (!isPasswordResetEligible(account)) {
            return false;
        }
        PasswordResetChallengeRow challenge = passwordResetRepository.findLockedByResetTokenHash(tokenHash)
                .orElse(null);
        if (challenge == null || !challenge.getAccountId().equals(accountId) || challenge.getVerifiedAt() == null
                || challenge.getConsumedAt() != null || challenge.getResetTokenExpiresAt() == null
                || challenge.getResetTokenHash() == null
                || !MessageDigest.isEqual(challenge.getResetTokenHash(), tokenHash)) {
            return false;
        }

        OffsetDateTime now = passwordResetNow();
        if (!now.isBefore(challenge.getResetTokenExpiresAt())) {
            return false;
        }

        account.setPasswordHash(newPasswordHash);
        challenge.setConsumedAt(now);
        accountRepository.flush();
        passwordResetRepository.flush();
        List<RefreshSessionRow> activeSessions = refreshSessionRepository
                .findByAccountIdAndRevokedAtIsNull(accountId);
        activeSessions.forEach(session -> session.setRevokedAt(now));
        refreshSessionRepository.saveAll(activeSessions);
        return true;
    }

    private static boolean isPasswordResetEligible(Account account) {
        return account != null && "ACTIVE".equals(account.getAccountStatus()) && account.getEmailVerifiedAt() != null;
    }

    private static boolean hasActiveResetGrant(PasswordResetChallengeRow challenge, OffsetDateTime now) {
        return challenge.getVerifiedAt() != null && challenge.getConsumedAt() == null
                && challenge.getResetTokenExpiresAt() != null && now.isBefore(challenge.getResetTokenExpiresAt());
    }

    private static void throwIfFailure(ApiOperationException failure) {
        if (failure != null) {
            throw failure;
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private OffsetDateTime passwordResetNow() {
        OffsetDateTime current = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        return current.withNano((current.getNano() / 1_000) * 1_000);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static long secondsUntil(OffsetDateTime now, OffsetDateTime end) {
        long millis = Duration.between(now, end).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    private record ResendDispatch(ApiOperationException failure, EmailDispatch dispatch) { }

    private record ResetVerification(ResetTokenResponse response, ApiOperationException failure) { }
}
