package com.stylemind.identity.service.impl;

import com.stylemind.identity.dto.users.ManagedUserResponse;
import com.stylemind.identity.dto.users.ManagedUserRolesResponse;
import com.stylemind.identity.dto.users.ManagedUserStatusResponse;
import com.stylemind.identity.dto.users.ManageUsersListRequest;
import com.stylemind.identity.dto.users.ManageUsersPageResponse;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.ErrorCode;
import com.stylemind.identity.mapper.UserMapper;
import com.stylemind.identity.repository.AccountRepository;
import com.stylemind.identity.repository.AccountRoleRepository;
import com.stylemind.identity.repository.AccountSpecifications;
import com.stylemind.identity.service.AdminUserService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "ADMIN");
    private static final String ACTIVE = "ACTIVE";
    private static final String SUSPENDED = "SUSPENDED";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository roleRepository;
    private final UserMapper mapper;

    public AdminUserServiceImpl(AccountRepository accountRepository, AccountRoleRepository roleRepository,
            UserMapper mapper) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.mapper = mapper;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ManageUsersPageResponse list(UUID actorId, ManageUsersListRequest request) {
        // Shared locks on current ADMIN role rows serialize the listing snapshot against admin mutations.
        List<AccountRole> lockedAdmins = roleRepository.findReadLockedByIdRoleCodeOrderByIdAccountIdAsc("ADMIN");
        Account actor = accountRepository.findReadLockedByAccountId(actorId)
                .orElseThrow(() -> new ApiOperationException(ErrorCode.ADMIN_ACCESS_DENIED));
        List<String> actorRoles = roles(actorId);
        requireEligibleAdmin(actor, actorRoles);

        Sort sort = Sort.by(Sort.Order.desc("registeredAt"), Sort.Order.asc("accountId"));
        Page<Account> result = accountRepository.findAll(AccountSpecifications.matching(request),
                PageRequest.of(request.page(), request.size(), sort));
        List<UUID> ids = result.getContent().stream().map(Account::getAccountId).toList();
        Map<UUID, List<String>> pageRoles = roleRepository.findByIdAccountIdIn(ids).stream()
                .collect(Collectors.groupingBy(AccountRole::getAccountId,
                        Collectors.mapping(AccountRole::getRoleCode, Collectors.toList())));
        List<ManagedUserResponse> items = result.getContent().stream()
                .map(account -> mapper.response(account, pageRoles.getOrDefault(account.getAccountId(), List.of())))
                .toList();
        long total = result.getTotalElements();
        long pages = total == 0 ? 0 : (total + request.size() - 1) / request.size();
        return mapper.page(items, request.page(), request.size(), total, pages);
    }

    @Transactional
    public ManagedUserStatusResponse updateStatus(UUID actorId, UUID targetId, String requestedStatus) {
        List<AccountRole> lockedAdmins = lockAdminMutations();
        Map<UUID, Account> lockedAccounts = lockAccounts(actorId, targetId);
        Account actor = lockedAccounts.get(actorId);
        requireEligibleAdmin(actor, actor == null ? List.of() : roles(actorId));
        Account target = lockedAccounts.get(targetId);
        if (target == null) {
            throw new ApiOperationException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        String current = target.getAccountStatus();
        if (current.equals(requestedStatus)) {
            return mapper.status(targetId, current, target.getEmailVerifiedAt());
        }
        List<String> targetRoles = roles(targetId);
        if (qualifies(target, targetRoles) && SUSPENDED.equals(requestedStatus)
                && countQualifyingAdmins(lockedAdmins) <= 1) {
            throw new ApiOperationException(ErrorCode.LAST_QUALIFYING_ADMIN_REQUIRED);
        }
        if (actorId.equals(targetId)) {
            throw new ApiOperationException(ErrorCode.ADMIN_SELF_CHANGE_FORBIDDEN);
        }
        if (ACTIVE.equals(current) && SUSPENDED.equals(requestedStatus)) {
            // Approved transition.
        } else if (SUSPENDED.equals(current) && ACTIVE.equals(requestedStatus)
                && target.getEmailVerifiedAt() != null) {
            // Approved transition; verification state is never changed here.
        } else {
            throw new ApiOperationException(ErrorCode.ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED);
        }

        target.setAccountStatus(requestedStatus);
        accountRepository.saveAndFlush(target);
        return mapper.status(targetId, requestedStatus, target.getEmailVerifiedAt());
    }

    @Transactional
    public ManagedUserRolesResponse replaceRoles(UUID actorId, UUID targetId, Set<String> requestedRoles) {
        List<AccountRole> lockedAdmins = lockAdminMutations();
        Map<UUID, Account> lockedAccounts = lockAccounts(actorId, targetId);
        Account actor = lockedAccounts.get(actorId);
        requireEligibleAdmin(actor, actor == null ? List.of() : roles(actorId));
        Account target = lockedAccounts.get(targetId);
        if (target == null) {
            throw new ApiOperationException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        List<String> currentRoles = roles(targetId);
        List<String> desiredRoles = requestedRoles.stream().sorted().toList();
        if (currentRoles.equals(desiredRoles)) {
            return mapper.roles(targetId, currentRoles);
        }
        if (qualifies(target, currentRoles) && currentRoles.contains("ADMIN")
                && !requestedRoles.contains("ADMIN") && countQualifyingAdmins(lockedAdmins) <= 1) {
            throw new ApiOperationException(ErrorCode.LAST_QUALIFYING_ADMIN_REQUIRED);
        }
        if (actorId.equals(targetId)) {
            throw new ApiOperationException(ErrorCode.ADMIN_SELF_CHANGE_FORBIDDEN);
        }

        long deleted = roleRepository.deleteByIdAccountId(targetId);
        if (deleted != currentRoles.size()) {
            throw new IllegalStateException("Locked account role replacement did not delete the expected rows");
        }
        if (!ALLOWED_ROLES.containsAll(desiredRoles)) {
            throw new IllegalStateException("Requested roles passed validation but include an unsupported role");
        }
        roleRepository.saveAll(mapper.accountRoles(targetId, desiredRoles));
        roleRepository.flush();
        List<String> persistedRoles = roles(targetId);
        if (!persistedRoles.equals(desiredRoles)) {
            throw new IllegalStateException("Account role replacement could not be verified");
        }
        return mapper.roles(targetId, persistedRoles);
    }

    private List<AccountRole> lockAdminMutations() {
        return roleRepository.findLockedByIdRoleCodeOrderByIdAccountIdAsc("ADMIN");
    }

    private Map<UUID, Account> lockAccounts(UUID actorId, UUID targetId) {
        List<UUID> ids = java.util.stream.Stream.of(actorId, targetId).distinct().sorted().toList();
        return accountRepository.findLockedByAccountIdInOrderByAccountIdAsc(ids).stream()
                .collect(Collectors.toMap(Account::getAccountId, Function.identity()));
    }

    private int countQualifyingAdmins(List<AccountRole> lockedAdmins) {
        List<UUID> adminIds = lockedAdmins.stream().map(AccountRole::getAccountId).distinct().toList();
        if (adminIds.isEmpty()) {
            return 0;
        }
        return (int) accountRepository.findByAccountIdIn(adminIds).stream()
                .filter(account -> ACTIVE.equals(account.getAccountStatus()) && account.getEmailVerifiedAt() != null)
                .count();
    }

    private List<String> roles(UUID accountId) {
        return roleRepository.findByIdAccountIdOrderByIdRoleCodeAsc(accountId).stream()
                .map(AccountRole::getRoleCode).toList();
    }

    private void requireEligibleAdmin(Account actor, List<String> roles) {
        if (actor == null || !ACTIVE.equals(actor.getAccountStatus()) || actor.getEmailVerifiedAt() == null
                || !roles.contains("ADMIN")) {
            throw new ApiOperationException(ErrorCode.ADMIN_ACCESS_DENIED);
        }
    }

    private boolean qualifies(Account account, List<String> roles) {
        return ACTIVE.equals(account.getAccountStatus()) && account.getEmailVerifiedAt() != null
                && roles.contains("ADMIN");
    }
}
