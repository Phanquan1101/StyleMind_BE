package com.stylemind.identity.repository;

import com.stylemind.identity.entity.EmailVerificationChallengeRow;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationRepository extends JpaRepository<EmailVerificationChallengeRow, UUID> {
    Optional<EmailVerificationChallengeRow> findByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationChallengeRow> findLockedByAccountId(UUID accountId);
}
