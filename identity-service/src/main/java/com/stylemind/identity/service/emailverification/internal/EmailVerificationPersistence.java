package com.stylemind.identity.service.emailverification.internal;

import com.stylemind.identity.client.notification.payload.EmailDispatch;
import com.stylemind.identity.dto.register.RegisterRequest;
import com.stylemind.identity.dto.register.RegisterResponse;
import com.stylemind.identity.model.account.AccountState;
import com.stylemind.identity.model.emailverification.EmailVerificationChallenge;
import com.stylemind.identity.model.emailverification.ResendCommit;
import com.stylemind.identity.model.emailverification.VerificationOutcome;
import com.stylemind.identity.model.register.RegistrationCommit;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.account.AccountRoleRepository;
import com.stylemind.identity.repository.emailverification.EmailVerificationRepository;
import com.stylemind.identity.entity.AccountRole;
import com.stylemind.identity.entity.EmailVerificationChallengeRow;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.security.VerificationCodeService;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;

@Service
public class EmailVerificationPersistence {

    private static final String INITIAL_ACCOUNT_STATUS = "PENDING_VERIFICATION";
    private static final String REGISTRATION_ROLE = "CUSTOMER";
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFICATION_LOCK = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration SEND_WINDOW = Duration.ofHours(1);
    private static final int MAX_SENDS_PER_WINDOW = 3;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final AccountRepository accountRepository;
    private final AccountRoleRepository roleRepository;
    private final EmailVerificationRepository challengeRepository;
    private final VerificationCodeService codeService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final Clock clock;

    public EmailVerificationPersistence(
            AccountRepository accountRepository,
            AccountRoleRepository roleRepository,
            EmailVerificationRepository challengeRepository,
            VerificationCodeService codeService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.challengeRepository = challengeRepository;
        this.codeService = codeService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public RegistrationCommit createRegistration(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        OffsetDateTime now = now();
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(accountId);
        account.setEmail(normalizedEmail);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setAccountStatus(INITIAL_ACCOUNT_STATUS);
        account.setRegisteredAt(now);
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueViolation(exception)) {
                throw new com.stylemind.identity.errors.EmailAlreadyExistsException();
            }
            throw exception;
        }
        roleRepository.saveAndFlush(new AccountRole(accountId, REGISTRATION_ROLE));

        String code = codeService.generateCode();
        UUID challengeId = UUID.randomUUID();
        challengeRepository.saveAndFlush(challengeRow(new EmailVerificationChallenge(
                challengeId, accountId, codeService.digest(accountId, challengeId, code), now.plus(OTP_TTL),
                0, null, now, now, 1, null)));
        return new RegistrationCommit(new RegisterResponse(accountId, normalizedEmail, INITIAL_ACCOUNT_STATUS),
                new EmailDispatch(normalizedEmail, code));
    }

    @Transactional
    public VerificationOutcome verify(String email, String code) {
        Optional<AccountState> accountResult = accountRepository.findLockedByEmail(email)
                .map(account -> new AccountState(account.getAccountId(), account.getAccountStatus()));
        if (accountResult.isEmpty()) {
            return VerificationOutcome.of(VerificationOutcome.Code.INVALID);
        }
        AccountState account = accountResult.get();
        if ("ACTIVE".equals(account.accountStatus())) {
            return VerificationOutcome.of(VerificationOutcome.Code.ALREADY_VERIFIED);
        }
        if (!INITIAL_ACCOUNT_STATUS.equals(account.accountStatus())) {
            return VerificationOutcome.of(VerificationOutcome.Code.NOT_ALLOWED);
        }
        Optional<EmailVerificationChallenge> challengeResult = challengeRepository
                .findLockedByAccountId(account.accountId()).map(EmailVerificationPersistence::challenge);
        if (challengeResult.isEmpty()) {
            return VerificationOutcome.of(VerificationOutcome.Code.INVALID);
        }

        EmailVerificationChallenge challenge = challengeResult.get();
        OffsetDateTime now = now();
        if (challenge.lockedUntil() != null && now.isBefore(challenge.lockedUntil())) {
            return VerificationOutcome.retry(VerificationOutcome.Code.ATTEMPTS_EXCEEDED,
                    Duration.between(now, challenge.lockedUntil()));
        }
        if (challenge.failedAttempts() >= MAX_FAILED_ATTEMPTS) {
            return VerificationOutcome.of(VerificationOutcome.Code.ATTEMPTS_EXCEEDED);
        }
        if (challenge.consumedAt() != null) {
            return VerificationOutcome.of(VerificationOutcome.Code.INVALID);
        }
        if (!now.isBefore(challenge.expiresAt())) {
            return VerificationOutcome.of(VerificationOutcome.Code.EXPIRED);
        }
        if (!codeService.matches(account.accountId(), challenge.challengeId(), code, challenge.codeDigest())) {
            int attempts = challenge.failedAttempts() + 1;
            OffsetDateTime lockedUntil = attempts == MAX_FAILED_ATTEMPTS ? now.plus(VERIFICATION_LOCK)
                    : challenge.lockedUntil();
            saveChallenge(new EmailVerificationChallenge(
                    challenge.challengeId(), challenge.accountId(), challenge.codeDigest(), challenge.expiresAt(),
                    attempts, challenge.consumedAt(), challenge.lastSendAttemptAt(), challenge.sendWindowStartedAt(),
                    challenge.sendCount(), lockedUntil));
            return VerificationOutcome.of(VerificationOutcome.Code.INVALID);
        }

        EmailVerificationChallengeRow lockedChallenge = challengeRepository
                .findLockedByAccountId(account.accountId()).orElse(null);
        if (lockedChallenge == null || lockedChallenge.getConsumedAt() != null) {
            return VerificationOutcome.of(VerificationOutcome.Code.INVALID);
        }
        lockedChallenge.setConsumedAt(now);
        challengeRepository.flush();
        Account lockedAccount = accountRepository.findLockedByAccountId(account.accountId()).orElse(null);
        if (lockedAccount == null || !INITIAL_ACCOUNT_STATUS.equals(lockedAccount.getAccountStatus())
                || lockedAccount.getEmailVerifiedAt() != null) {
            throw new IllegalStateException("Account state changed while locked");
        }
        lockedAccount.setAccountStatus("ACTIVE");
        lockedAccount.setEmailVerifiedAt(now);
        accountRepository.flush();
        return VerificationOutcome.of(VerificationOutcome.Code.VERIFIED);
    }

    @Transactional
    public ResendCommit reserveResend(String email) {
        Optional<AccountState> accountResult = accountRepository.findLockedByEmail(email)
                .map(account -> new AccountState(account.getAccountId(), account.getAccountStatus()));
        if (accountResult.isEmpty()) {
            return new ResendCommit(VerificationOutcome.of(VerificationOutcome.Code.INVALID), null);
        }
        AccountState account = accountResult.get();
        if ("ACTIVE".equals(account.accountStatus())) {
            return new ResendCommit(VerificationOutcome.of(VerificationOutcome.Code.ALREADY_VERIFIED), null);
        }
        if (!INITIAL_ACCOUNT_STATUS.equals(account.accountStatus())) {
            return new ResendCommit(VerificationOutcome.of(VerificationOutcome.Code.NOT_ALLOWED), null);
        }
        Optional<EmailVerificationChallenge> challengeResult = challengeRepository
                .findLockedByAccountId(account.accountId()).map(EmailVerificationPersistence::challenge);
        if (challengeResult.isEmpty()) {
            return new ResendCommit(VerificationOutcome.of(VerificationOutcome.Code.INVALID), null);
        }

        EmailVerificationChallenge current = challengeResult.get();
        OffsetDateTime now = now();
        if (current.lockedUntil() != null && now.isBefore(current.lockedUntil())) {
            return new ResendCommit(VerificationOutcome.retry(VerificationOutcome.Code.ATTEMPTS_EXCEEDED,
                    Duration.between(now, current.lockedUntil())), null);
        }
        boolean lockElapsed = current.lockedUntil() != null && !now.isBefore(current.lockedUntil());
        OffsetDateTime windowEnd = current.sendWindowStartedAt().plus(SEND_WINDOW);
        boolean windowElapsed = !now.isBefore(windowEnd);
        OffsetDateTime cooldownEnd = current.lastSendAttemptAt().plus(RESEND_COOLDOWN);
        boolean cooldown = now.isBefore(cooldownEnd);
        boolean windowLimited = !windowElapsed && current.sendCount() >= MAX_SENDS_PER_WINDOW;
        if (cooldown || windowLimited) {
            long cooldownWait = cooldown ? secondsUntil(now, cooldownEnd) : 0;
            long windowWait = windowLimited ? secondsUntil(now, windowEnd) : 0;
            return new ResendCommit(VerificationOutcome.retry(VerificationOutcome.Code.SEND_LIMIT_EXCEEDED,
                    Duration.ofSeconds(Math.max(cooldownWait, windowWait))), null);
        }

        int sendCount = windowElapsed ? 1 : current.sendCount() + 1;
        OffsetDateTime windowStart = windowElapsed ? now : current.sendWindowStartedAt();
        String code = codeService.generateCode();
        UUID challengeId = UUID.randomUUID();
        int attempts = lockElapsed ? 0 : current.failedAttempts();
        OffsetDateTime lockedUntil = lockElapsed ? null : current.lockedUntil();
        saveChallenge(new EmailVerificationChallenge(
                challengeId, account.accountId(), codeService.digest(account.accountId(), challengeId, code),
                now.plus(OTP_TTL), attempts, null, now, windowStart, sendCount, lockedUntil));
        return new ResendCommit(VerificationOutcome.of(VerificationOutcome.Code.VERIFIED),
                new EmailDispatch(email, code));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private long secondsUntil(OffsetDateTime now, OffsetDateTime end) {
        long millis = Duration.between(now, end).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    private void saveChallenge(EmailVerificationChallenge value) {
        EmailVerificationChallengeRow existing = challengeRepository.findLockedByAccountId(value.accountId())
                .orElse(null);
        if (existing != null && !existing.getChallengeId().equals(value.challengeId())) {
            challengeRepository.delete(existing);
            challengeRepository.flush();
            existing = null;
        }
        if (existing == null) {
            challengeRepository.saveAndFlush(challengeRow(value));
            return;
        }
        existing.setCodeDigest(value.codeDigest().clone());
        existing.setExpiresAt(value.expiresAt());
        existing.setFailedAttempts((short) value.failedAttempts());
        existing.setConsumedAt(value.consumedAt());
        existing.setLastSendAttemptAt(value.lastSendAttemptAt());
        existing.setSendWindowStartedAt(value.sendWindowStartedAt());
        existing.setSendCount((short) value.sendCount());
        existing.setLockedUntil(value.lockedUntil());
        challengeRepository.flush();
    }

    private static EmailVerificationChallengeRow challengeRow(EmailVerificationChallenge value) {
        EmailVerificationChallengeRow row = new EmailVerificationChallengeRow();
        row.setChallengeId(value.challengeId());
        row.setAccountId(value.accountId());
        row.setCodeDigest(value.codeDigest().clone());
        row.setExpiresAt(value.expiresAt());
        row.setFailedAttempts((short) value.failedAttempts());
        row.setConsumedAt(value.consumedAt());
        row.setLastSendAttemptAt(value.lastSendAttemptAt());
        row.setSendWindowStartedAt(value.sendWindowStartedAt());
        row.setSendCount((short) value.sendCount());
        row.setLockedUntil(value.lockedUntil());
        return row;
    }

    private static EmailVerificationChallenge challenge(EmailVerificationChallengeRow row) {
        return new EmailVerificationChallenge(row.getChallengeId(), row.getAccountId(), row.getCodeDigest().clone(),
                row.getExpiresAt(), row.getFailedAttempts(), row.getConsumedAt(), row.getLastSendAttemptAt(),
                row.getSendWindowStartedAt(), row.getSendCount(), row.getLockedUntil());
    }

    private boolean isEmailUniqueViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "account_email_key".equals(violation.getConstraintName())) {
                return true;
            }
            if (cause instanceof PSQLException postgres && "23505".equals(postgres.getSQLState())
                    && postgres.getServerErrorMessage() != null
                    && "account_email_key".equals(postgres.getServerErrorMessage().getConstraint())) {
                return true;
            }
        }
        return false;
    }
}
