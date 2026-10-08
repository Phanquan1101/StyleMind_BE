package com.stylemind.identity.service;

import com.stylemind.identity.model.LoginAccount;
import com.stylemind.identity.model.PasswordResetChallenge;
import com.stylemind.identity.repository.AccountRepository;
import com.stylemind.identity.repository.PasswordResetRepository;
import com.stylemind.identity.repository.RefreshSessionRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetTransactionService {

    private final AccountRepository accountRepository;
    private final PasswordResetRepository challengeRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final Clock clock;

    public PasswordResetTransactionService(AccountRepository accountRepository,
            PasswordResetRepository challengeRepository,
            RefreshSessionRepository refreshSessionRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.challengeRepository = challengeRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean reset(UUID accountId, byte[] tokenHash, String newPasswordHash) {
        LoginAccount account = accountRepository.lockLoginAccount(accountId).orElse(null);
        if (account == null || !"ACTIVE".equals(account.accountStatus()) || account.emailVerifiedAt() == null) {
            return false;
        }
        PasswordResetChallenge challenge = challengeRepository.lockByResetTokenHash(tokenHash).orElse(null);
        if (challenge == null || !challenge.accountId().equals(accountId) || challenge.verifiedAt() == null
                || challenge.consumedAt() != null || challenge.resetTokenExpiresAt() == null) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        now = now.withNano((now.getNano() / 1_000) * 1_000);
        if (!now.isBefore(challenge.resetTokenExpiresAt())) {
            return false;
        }

        if (!accountRepository.updatePasswordHash(accountId, newPasswordHash)) {
            throw new IllegalStateException("Password reset update did not affect the locked account");
        }
        if (!challengeRepository.consumeResetGrant(accountId, challenge.challengeId(), tokenHash, now)) {
            throw new IllegalStateException("Password reset grant could not be consumed");
        }
        refreshSessionRepository.revokeAllActiveForAccount(accountId, now);
        return true;
    }
}
