package com.stylemind.identity.repository.emailverification;

import com.stylemind.identity.entity.EmailVerificationChallengeRow;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EmailVerificationRepository extends JpaRepository<EmailVerificationChallengeRow, UUID> {
    Optional<EmailVerificationChallengeRow> findByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationChallengeRow> findLockedByAccountId(UUID accountId);
}
