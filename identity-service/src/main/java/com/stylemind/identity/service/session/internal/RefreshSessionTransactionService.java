package com.stylemind.identity.service.session.internal;

import com.stylemind.identity.security.JwtTokenService;
import com.stylemind.identity.dto.login.LoginResponse;
import com.stylemind.identity.errors.AccountNotActiveException;
import com.stylemind.identity.errors.InvalidRefreshTokenException;
import com.stylemind.identity.model.login.IssuedLoginTokens;
import com.stylemind.identity.model.login.LoginAccount;
import com.stylemind.identity.model.session.RefreshSession;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.account.AccountRoleRepository;
import com.stylemind.identity.repository.session.RefreshSessionRepository;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import com.stylemind.identity.entity.RefreshSessionRow;
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
    private final AccountRoleRepository accountRoleRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final JwtTokenService tokenService;
    private final Clock clock;

    public RefreshSessionTransactionService(
            AccountRepository accountRepository,
            AccountRoleRepository accountRoleRepository,
            RefreshSessionRepository refreshSessionRepository,
            JwtTokenService tokenService,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @Transactional
    public LoginResponse rotate(UUID accountId, String tokenHash) {
        Account account = accountRepository.findLockedByAccountId(accountId)
                .orElseThrow(AccountNotActiveException::new);
        RefreshSessionRow session = refreshSessionRepository.findLockedByTokenHash(tokenHash)
                .filter(current -> current.getAccountId().equals(accountId))
                .orElseThrow(InvalidRefreshTokenException::new);

        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (session.getRevokedAt() != null || !now.isBefore(session.getExpiresAt())) {
            throw new InvalidRefreshTokenException();
        }
        if (!"ACTIVE".equals(account.getAccountStatus()) || account.getEmailVerifiedAt() == null) {
            throw new AccountNotActiveException();
        }

        List<String> roles = accountRoleRepository.findByIdAccountIdOrderByIdRoleCodeAsc(accountId).stream()
                .map(AccountRole::getRoleCode).toList();
        if (roles.isEmpty() || !ALLOWED_ROLES.containsAll(roles)) {
            throw new AccountNotActiveException();
        }

        IssuedLoginTokens tokens = tokenService.rotate(accountId, roles, session.getExpiresAt());
        session.setRevokedAt(now);
        refreshSessionRepository.flush();
        RefreshSessionRow replacement = new RefreshSessionRow();
        replacement.setSessionId(UUID.randomUUID());
        replacement.setAccountId(accountId);
        replacement.setTokenHash(tokens.refreshTokenHash());
        replacement.setIssuedAt(tokens.issuedAt());
        replacement.setExpiresAt(session.getExpiresAt());
        refreshSessionRepository.saveAndFlush(replacement);
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer",
                tokens.accessExpiresInSeconds());
    }

    @Transactional
    public void revoke(UUID accountId, String tokenHash) {
        if (accountRepository.findLockedByAccountId(accountId).isEmpty()) {
            return;
        }
        RefreshSessionRow session = refreshSessionRepository.findLockedByTokenHash(tokenHash).orElse(null);
        if (session == null || !session.getAccountId().equals(accountId) || session.getRevokedAt() != null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        session.setRevokedAt(now);
        refreshSessionRepository.flush();
    }
}
