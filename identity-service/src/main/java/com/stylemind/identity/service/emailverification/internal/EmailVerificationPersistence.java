package com.stylemind.identity.service.emailverification.internal;

import com.stylemind.identity.client.notification.payload.EmailDispatch;
import com.stylemind.identity.dto.register.RegisterRequest;
import com.stylemind.identity.dto.register.RegisterResponse;
import com.stylemind.identity.model.account.Account;
import com.stylemind.identity.model.account.AccountState;
import com.stylemind.identity.model.emailverification.EmailVerificationChallenge;
import com.stylemind.identity.model.emailverification.ResendCommit;
import com.stylemind.identity.model.emailverification.VerificationOutcome;
import com.stylemind.identity.model.register.RegistrationCommit;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.emailverification.EmailVerificationRepository;
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
    private final EmailVerificationRepository challengeRepository;
    private final VerificationCodeService codeService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final Clock clock;

    public EmailVerificationPersistence(
            AccountRepository accountRepository,
            EmailVerificationRepository challengeRepository,
            VerificationCodeService codeService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            Clock clock) {
        this.accountRepository = accountRepository;
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
        Account account = new Account(accountId, normalizedEmail, passwordEncoder.encode(request.password()),
                INITIAL_ACCOUNT_STATUS, null, now);
        accountRepository.insertAccount(account);
        accountRepository.insertRole(accountId, REGISTRATION_ROLE);

        String code = codeService.generateCode();
        UUID challengeId = UUID.randomUUID();
        challengeRepository.insert(new EmailVerificationChallenge(
                challengeId, accountId, codeService.digest(accountId, challengeId, code), now.plus(OTP_TTL),
                0, null, now, now, 1, null));
        return new RegistrationCommit(new RegisterResponse(accountId, normalizedEmail, INITIAL_ACCOUNT_STATUS),
                new EmailDispatch(normalizedEmail, code));
    }

    @Transactional
    public VerificationOutcome verify(String email, String code) {
        Optional<AccountState> accountResult = accountRepository.lockByEmail(email);
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
        Optional<EmailVerificationChallenge> challengeResult = challengeRepository.lockByAccount(account.accountId());
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
            challengeRepository.update(new EmailVerificationChallenge(
                    challenge.challengeId(), challenge.accountId(), challenge.codeDigest(), challenge.expiresAt(),
                    attempts, challenge.consumedAt(), challenge.lastSendAttemptAt(), challenge.sendWindowStartedAt(),
                    challenge.sendCount(), lockedUntil));
            return VerificationOutcome.of(VerificationOutcome.Code.INVALID);
        }

        if (!challengeRepository.consume(account.accountId(), now)) {
            return VerificationOutcome.of(VerificationOutcome.Code.INVALID);
        }
        if (!accountRepository.activate(account.accountId(), now)) {
            throw new IllegalStateException("Account state changed while locked");
        }
        return VerificationOutcome.of(VerificationOutcome.Code.VERIFIED);
    }

    @Transactional
    public ResendCommit reserveResend(String email) {
        Optional<AccountState> accountResult = accountRepository.lockByEmail(email);
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
        Optional<EmailVerificationChallenge> challengeResult = challengeRepository.lockByAccount(account.accountId());
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
        challengeRepository.update(new EmailVerificationChallenge(
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
}
