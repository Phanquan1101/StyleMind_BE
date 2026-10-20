package com.stylemind.identity.repository.passwordreset;

import com.stylemind.identity.entity.PasswordResetChallengeRow;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PasswordResetRepository extends JpaRepository<PasswordResetChallengeRow, UUID> {
    Optional<PasswordResetChallengeRow> findByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetChallengeRow> findLockedByAccountId(UUID accountId);

    Optional<PasswordResetChallengeRow> findByResetTokenHash(byte[] resetTokenHash);

    default Optional<UUID> findAccountIdByResetTokenHash(byte[] resetTokenHash) {
        return findByResetTokenHash(resetTokenHash).map(PasswordResetChallengeRow::getAccountId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetChallengeRow> findLockedByResetTokenHash(byte[] resetTokenHash);
}
