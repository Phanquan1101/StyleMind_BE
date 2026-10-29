package com.stylemind.identity.service.impl;

import com.stylemind.identity.client.notification.NotificationEmailClient;
import com.stylemind.identity.client.notification.payload.EmailDispatch;
import com.stylemind.identity.dto.login.LoginRequest;
import com.stylemind.identity.dto.login.LoginResponse;
import com.stylemind.identity.dto.register.RegisterRequest;
import com.stylemind.identity.dto.register.RegisterResponse;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import com.stylemind.identity.entity.RefreshSessionRow;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.ErrorCode;
import com.stylemind.identity.mapper.AuthMapper;
import com.stylemind.identity.repository.AccountRepository;
import com.stylemind.identity.repository.AccountRoleRepository;
import com.stylemind.identity.repository.EmailVerificationRepository;
import com.stylemind.identity.repository.RefreshSessionRepository;
import com.stylemind.identity.security.jwt.JwtTokenService;
import com.stylemind.identity.security.verification.VerificationCodeService;
import com.stylemind.identity.service.AuthService;
import com.stylemind.identity.validation.auth.RefreshTokenFormat;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String INITIAL_ACCOUNT_STATUS = "PENDING_VERIFICATION";
    private static final String REGISTRATION_ROLE = "CUSTOMER";
    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "ADMIN");
    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofMinutes(5);

    private final AccountRepository accountRepository;
    private final AccountRoleRepository roleRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final VerificationCodeService verificationCodeService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final NotificationEmailClient emailClient;
    private final AuthMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final String timingEqualizationHash;

    public AuthServiceImpl(
            AccountRepository accountRepository,
            AccountRoleRepository roleRepository,
            EmailVerificationRepository emailVerificationRepository,
            RefreshSessionRepository refreshSessionRepository,
            VerificationCodeService verificationCodeService,
            PasswordEncoder passwordEncoder,
            JwtTokenService tokenService,
            NotificationEmailClient emailClient,
            AuthMapper mapper,
            Clock clock,
            TransactionTemplate transactions) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.verificationCodeService = verificationCodeService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.emailClient = emailClient;
        this.mapper = mapper;
        this.clock = clock;
        this.transactions = transactions;
        this.timingEqualizationHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public RegisterResponse register(RegisterRequest request) {
        RegistrationDispatch committed;
        try {
            committed = transactions.execute(status -> createRegistration(request));
        } catch (DataIntegrityViolationException exception) {
            // The database unique constraint remains the final guard if another request wins after existsByEmail.
            throw new ApiOperationException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (committed == null) {
            throw new IllegalStateException("Registration transaction returned no result");
        }
        try {
            emailClient.send(committed.emailDispatch());
        } catch (ApiOperationException exception) {
            throw ApiOperationException.registrationDeliveryFailure(exception);
        }
        return committed.response();
    }

    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        Account candidate = accountRepository.findByEmail(normalizedEmail).orElse(null);
        if (candidate == null) {
            passwordEncoder.matches(request.password(), timingEqualizationHash);
            throw new ApiOperationException(ErrorCode.INVALID_CREDENTIALS);
        }

        boolean passwordMatches;
        try {
            passwordMatches = passwordEncoder.matches(request.password(), candidate.getPasswordHash());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Stored credential cannot be verified", exception);
        }
        if (!passwordMatches) {
            throw new ApiOperationException(ErrorCode.INVALID_CREDENTIALS);
        }

        UUID accountId = candidate.getAccountId();
        String verifiedPasswordHash = candidate.getPasswordHash();
        LoginResponse response = transactions.execute(status -> createLoginSession(accountId, verifiedPasswordHash));
        if (response == null) {
            throw new IllegalStateException("Login transaction returned no response");
        }
        return response;
    }

    public LoginResponse refresh(String refreshToken) {
        String tokenHash = refreshTokenHash(refreshToken);
        LoginResponse response = transactions.execute(status -> rotateSession(tokenHash));
        if (response == null) {
            throw new IllegalStateException("Refresh transaction returned no response");
        }
        return response;
    }

    public void logout(String refreshToken) {
        String tokenHash = refreshTokenHash(refreshToken);
        transactions.executeWithoutResult(status -> revokeSession(tokenHash));
    }

    private RegistrationDispatch createRegistration(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (accountRepository.existsByEmail(normalizedEmail)) {
            throw new ApiOperationException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        OffsetDateTime now = now();
        UUID accountId = UUID.randomUUID();
        Account account = mapper.account(accountId, normalizedEmail, passwordEncoder.encode(request.password()),
                INITIAL_ACCOUNT_STATUS, now);
        accountRepository.save(account);
        roleRepository.save(mapper.accountRole(accountId, REGISTRATION_ROLE));

        String code = verificationCodeService.generateCode();
        UUID challengeId = UUID.randomUUID();
        emailVerificationRepository.save(mapper.emailVerificationChallenge(challengeId, accountId,
                verificationCodeService.digest(accountId, challengeId, code), now.plus(EMAIL_VERIFICATION_TTL),
                0, now, now, 1, null));

        return new RegistrationDispatch(mapper.registerResponse(accountId, normalizedEmail, INITIAL_ACCOUNT_STATUS),
                new EmailDispatch(normalizedEmail, code));
    }

    private LoginResponse createLoginSession(UUID accountId, String verifiedPasswordHash) {
        Account account = accountRepository.findLockedByAccountId(accountId)
                .orElseThrow(() -> new ApiOperationException(ErrorCode.ACCOUNT_NOT_ACTIVE));
        if (!constantTimeEquals(account.getPasswordHash(), verifiedPasswordHash)) {
            throw new ApiOperationException(ErrorCode.INVALID_CREDENTIALS);
        }

        List<String> roles = eligibleRoles(account);
        JwtTokenService.IssuedTokens tokens = tokenService.issue(accountId, roles);
        persistRefreshSession(accountId, tokens, tokens.refreshExpiresAt());
        return tokens.response();
    }

    private LoginResponse rotateSession(String tokenHash) {
        // Read the owner inside this transaction, then acquire locks in account -> session order.
        UUID accountId = refreshSessionRepository.findAccountIdByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiOperationException(ErrorCode.INVALID_REFRESH_TOKEN));
        Account account = accountRepository.findLockedByAccountId(accountId)
                .orElseThrow(() -> new ApiOperationException(ErrorCode.ACCOUNT_NOT_ACTIVE));
        RefreshSessionRow session = refreshSessionRepository.findLockedByTokenHash(tokenHash)
                .filter(current -> current.getAccountId().equals(accountId))
                .orElseThrow(() -> new ApiOperationException(ErrorCode.INVALID_REFRESH_TOKEN));

        OffsetDateTime now = now();
        if (session.getRevokedAt() != null || !now.isBefore(session.getExpiresAt())) {
            throw new ApiOperationException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        List<String> roles = eligibleRoles(account);
        JwtTokenService.IssuedTokens tokens = tokenService.rotate(accountId, roles, session.getExpiresAt());
        session.setRevokedAt(now);
        refreshSessionRepository.flush();
        persistRefreshSession(accountId, tokens, session.getExpiresAt());
        return tokens.response();
    }

    private void revokeSession(String tokenHash) {
        UUID accountId = refreshSessionRepository.findAccountIdByTokenHash(tokenHash).orElse(null);
        if (accountId == null || accountRepository.findLockedByAccountId(accountId).isEmpty()) {
            return;
        }
        RefreshSessionRow session = refreshSessionRepository.findLockedByTokenHash(tokenHash)
                .filter(current -> current.getAccountId().equals(accountId))
                .orElse(null);
        if (session == null || session.getRevokedAt() != null) {
            return;
        }
        session.setRevokedAt(now());
    }

    private List<String> eligibleRoles(Account account) {
        if (!isEligibleForTokens(account)) {
            throw new ApiOperationException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        List<String> roles = roleRepository.findByIdAccountIdOrderByIdRoleCodeAsc(account.getAccountId()).stream()
                .map(AccountRole::getRoleCode).toList();
        if (roles.isEmpty() || !ALLOWED_ROLES.containsAll(roles)) {
            throw new ApiOperationException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        return roles;
    }

    private void persistRefreshSession(UUID accountId, JwtTokenService.IssuedTokens tokens,
            OffsetDateTime expiresAt) {
        refreshSessionRepository.save(mapper.refreshSession(UUID.randomUUID(), accountId, tokens.refreshTokenHash(),
                tokens.issuedAt(), expiresAt));
    }

    private String refreshTokenHash(String refreshToken) {
        if (!RefreshTokenFormat.isValid(refreshToken)) {
            throw new ApiOperationException(ErrorCode.INVALID_REQUEST);
        }
        return tokenService.refreshTokenHash(refreshToken);
    }

    private static boolean isEligibleForTokens(Account account) {
        return "ACTIVE".equals(account.getAccountStatus()) && account.getEmailVerifiedAt() != null;
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record RegistrationDispatch(RegisterResponse response, EmailDispatch emailDispatch) { }
}
