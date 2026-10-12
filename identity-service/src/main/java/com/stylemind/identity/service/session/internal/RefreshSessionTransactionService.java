package com.stylemind.identity.service.session.internal;

import com.stylemind.identity.security.JwtTokenService;
import com.stylemind.identity.dto.login.LoginResponse;
import com.stylemind.identity.errors.AccountNotActiveException;
import com.stylemind.identity.errors.InvalidRefreshTokenException;
import com.stylemind.identity.model.login.IssuedLoginTokens;
import com.stylemind.identity.model.login.LoginAccount;
import com.stylemind.identity.model.session.RefreshSession;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.session.RefreshSessionRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshSessionTransactionService {

    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "ADMIN");

    private final AccountRepository accountRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final JwtTokenService tokenService;
    private final Clock clock;

    public RefreshSessionTransactionService(
            AccountRepository accountRepository,
            RefreshSessionRepository refreshSessionRepository,
            JwtTokenService tokenService,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @Transactional
    public LoginResponse rotate(UUID accountId, String tokenHash) {
        LoginAccount account = accountRepository.lockLoginAccount(accountId)
                .orElseThrow(AccountNotActiveException::new);
        RefreshSession session = refreshSessionRepository.lockByTokenHash(tokenHash)
                .filter(current -> current.accountId().equals(accountId))
                .orElseThrow(InvalidRefreshTokenException::new);

        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (session.revokedAt() != null || !now.isBefore(session.expiresAt())) {
            throw new InvalidRefreshTokenException();
        }
        if (!"ACTIVE".equals(account.accountStatus()) || account.emailVerifiedAt() == null) {
            throw new AccountNotActiveException();
        }

        List<String> roles = accountRepository.findRoleCodes(accountId);
        if (roles.isEmpty() || !ALLOWED_ROLES.containsAll(roles)) {
            throw new AccountNotActiveException();
        }

        IssuedLoginTokens tokens = tokenService.rotate(accountId, roles, session.expiresAt());
        if (!refreshSessionRepository.revokeIfActive(tokenHash, now)) {
            throw new InvalidRefreshTokenException();
        }
        refreshSessionRepository.insert(UUID.randomUUID(), accountId, tokens.refreshTokenHash(),
                tokens.issuedAt(), session.expiresAt());
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer",
                tokens.accessExpiresInSeconds());
    }

    @Transactional
    public void revoke(UUID accountId, String tokenHash) {
        if (!accountRepository.lockAccountForSessionOperation(accountId)) {
            return;
        }
        RefreshSession session = refreshSessionRepository.lockByTokenHash(tokenHash).orElse(null);
        if (session == null || !session.accountId().equals(accountId) || session.revokedAt() != null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        refreshSessionRepository.revokeIfActive(tokenHash, now);
    }
}
