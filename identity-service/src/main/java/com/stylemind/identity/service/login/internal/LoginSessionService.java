package com.stylemind.identity.service.login.internal;

import com.stylemind.identity.security.JwtTokenService;
import com.stylemind.identity.dto.login.LoginResponse;
import com.stylemind.identity.errors.AccountNotActiveException;
import com.stylemind.identity.errors.InvalidCredentialsException;
import com.stylemind.identity.model.login.IssuedLoginTokens;
import com.stylemind.identity.model.login.LoginAccount;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.session.RefreshSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginSessionService {

    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "ADMIN");

    private final AccountRepository accountRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final JwtTokenService tokenService;

    public LoginSessionService(
            AccountRepository accountRepository,
            RefreshSessionRepository refreshSessionRepository,
            JwtTokenService tokenService) {
        this.accountRepository = accountRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.tokenService = tokenService;
    }

    @Transactional
    public LoginResponse createSession(UUID accountId, String verifiedPasswordHash) {
        LoginAccount current = accountRepository.lockLoginAccount(accountId)
                .orElseThrow(AccountNotActiveException::new);
        if (!constantTimeEquals(current.passwordHash(), verifiedPasswordHash)) {
            throw new InvalidCredentialsException();
        }
        if (!"ACTIVE".equals(current.accountStatus()) || current.emailVerifiedAt() == null) {
            throw new AccountNotActiveException();
        }

        List<String> roles = accountRepository.findRoleCodes(accountId);
        if (roles.isEmpty() || !ALLOWED_ROLES.containsAll(roles)) {
            throw new AccountNotActiveException();
        }

        IssuedLoginTokens tokens = tokenService.issue(accountId, roles);
        refreshSessionRepository.insert(
                UUID.randomUUID(), accountId, tokens.refreshTokenHash(), tokens.issuedAt(), tokens.refreshExpiresAt());
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer",
                tokens.accessExpiresInSeconds());
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
