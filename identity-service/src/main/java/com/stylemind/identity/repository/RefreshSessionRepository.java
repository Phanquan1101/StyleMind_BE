package com.stylemind.identity.repository;

import com.stylemind.identity.entity.RefreshSessionRow;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSessionRow, UUID> {
    @Query("select session.accountId from RefreshSessionRow session where session.tokenHash = :tokenHash")
    Optional<UUID> findAccountIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshSessionRow> findLockedByTokenHash(String tokenHash);

    List<RefreshSessionRow> findByAccountIdAndRevokedAtIsNull(UUID accountId);
}
