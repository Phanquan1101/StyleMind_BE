package com.stylemind.identity.repository;

import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import com.stylemind.identity.entity.AccountRoleId;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccountRoleRepository extends JpaRepository<AccountRole, AccountRoleId> {
    List<AccountRole> findByIdAccountIdOrderByIdRoleCodeAsc(UUID accountId);
    List<AccountRole> findByIdAccountIdIn(Collection<UUID> accountIds);
    long deleteByIdAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    List<AccountRole> findReadLockedByIdRoleCodeOrderByIdAccountIdAsc(String roleCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AccountRole> findLockedByIdRoleCodeOrderByIdAccountIdAsc(String roleCode);
}
