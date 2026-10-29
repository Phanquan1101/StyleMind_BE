package com.stylemind.identity.service.passwordreset.internal;

import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.PasswordResetChallengeRow;
import com.stylemind.identity.entity.RefreshSessionRow;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.passwordreset.PasswordResetRepository;
import com.stylemind.identity.repository.session.RefreshSessionRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
        Account account = accountRepository.findLockedByAccountId(accountId).orElse(null);
        if (account == null || !"ACTIVE".equals(account.getAccountStatus())
                || account.getEmailVerifiedAt() == null) {
            return false;
        }
        PasswordResetChallengeRow challenge = challengeRepository.findLockedByResetTokenHash(tokenHash).orElse(null);
        if (challenge == null || !challenge.getAccountId().equals(accountId) || challenge.getVerifiedAt() == null
                || challenge.getConsumedAt() != null || challenge.getResetTokenExpiresAt() == null
                || challenge.getResetTokenHash() == null
                || !java.security.MessageDigest.isEqual(challenge.getResetTokenHash(), tokenHash)) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        now = now.withNano((now.getNano() / 1_000) * 1_000);
        if (!now.isBefore(challenge.getResetTokenExpiresAt())) {
            return false;
        }

        account.setPasswordHash(newPasswordHash);
        challenge.setConsumedAt(now);
        accountRepository.flush();
        challengeRepository.flush();
        OffsetDateTime revokedAt = now;
        List<RefreshSessionRow> activeSessions = refreshSessionRepository
                .findByAccountIdAndRevokedAtIsNull(accountId);
        activeSessions.forEach(session -> session.setRevokedAt(revokedAt));
        refreshSessionRepository.saveAll(activeSessions);
        return true;
    }
}
