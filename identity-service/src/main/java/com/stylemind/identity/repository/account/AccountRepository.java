package com.stylemind.identity.repository.account;

import com.stylemind.identity.entity.Account;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findLockedByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findLockedByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Account> findReadLockedByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Account> findLockedByAccountIdInOrderByAccountIdAsc(Collection<UUID> accountIds);

    List<Account> findByAccountIdIn(Collection<UUID> accountIds);
}
