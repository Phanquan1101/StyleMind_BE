package com.stylemind.identity.repository;

import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.UserProfile;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    /** Rows of (account_id, email, display_name, account_status) for the internal batch lookup. */
    @Query("""
            select a.accountId, a.email, p.displayName, a.accountStatus
            from Account a join UserProfile p on p.accountId = a.accountId
            where a.accountId in :accountIds
            order by a.accountId
            """)
    List<Object[]> findInternalLookupRows(@Param("accountIds") Collection<UUID> accountIds);
}
