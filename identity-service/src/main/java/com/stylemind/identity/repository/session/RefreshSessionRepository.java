package com.stylemind.identity.repository.session;

import com.stylemind.identity.entity.RefreshSessionRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RefreshSessionRepository extends JpaRepository<RefreshSessionRow, UUID> {
    Optional<RefreshSessionRow> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshSessionRow> findLockedByTokenHash(String tokenHash);

    List<RefreshSessionRow> findByAccountIdAndRevokedAtIsNull(UUID accountId);
}
