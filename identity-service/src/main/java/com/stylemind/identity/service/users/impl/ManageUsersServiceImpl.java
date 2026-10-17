package com.stylemind.identity.service.users.impl;

import com.stylemind.identity.dto.users.ManageUsersPageResponse;
import com.stylemind.identity.dto.users.ManagedUserResponse;
import com.stylemind.identity.dto.users.ManagedUserRolesResponse;
import com.stylemind.identity.dto.users.ManagedUserStatusResponse;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.model.users.AdminManagedAccountState;
import com.stylemind.identity.model.users.ManageUserListCriteria;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.account.AccountRoleRepository;
import com.stylemind.identity.service.users.ManageUsersService;
import com.stylemind.identity.service.users.internal.ManageUserSpecifications;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageUsersServiceImpl implements ManageUsersService {

    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "ADMIN");
    private static final String ACTIVE = "ACTIVE";
    private static final String SUSPENDED = "SUSPENDED";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository roleRepository;

    public ManageUsersServiceImpl(AccountRepository accountRepository, AccountRoleRepository roleRepository) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ManageUsersPageResponse list(UUID actorId, ManageUserListCriteria criteria) {
        // Shared locks on current ADMIN role rows serialize the listing snapshot against admin mutations.
        List<AccountRole> lockedAdmins = roleRepository.findReadLockedByIdRoleCodeOrderByIdAccountIdAsc("ADMIN");
        Account actor = accountRepository.findReadLockedByAccountId(actorId).orElseThrow(this::actorDenied);
        List<String> actorRoles = roles(actorId);
        requireEligibleAdmin(state(actor), actorRoles);

        Sort sort = Sort.by(Sort.Order.desc("registeredAt"), Sort.Order.asc("accountId"));
        Page<Account> page = accountRepository.findAll(ManageUserSpecifications.matching(criteria),
                PageRequest.of(criteria.page(), criteria.size(), sort));
        List<UUID> ids = page.getContent().stream().map(Account::getAccountId).toList();
        Map<UUID, List<String>> pageRoles = roleRepository.findByIdAccountIdIn(ids).stream()
                .collect(Collectors.groupingBy(AccountRole::getAccountId,
                        Collectors.mapping(AccountRole::getRoleCode, Collectors.toList())));
        List<ManagedUserResponse> items = page.getContent().stream()
                .map(account -> response(account, pageRoles.getOrDefault(account.getAccountId(), List.of())))
                .toList();
        long total = page.getTotalElements();
        long pages = total == 0 ? 0 : (total + criteria.size() - 1) / criteria.size();
        return new ManageUsersPageResponse(items, criteria.page(), criteria.size(), total, pages);
    }

    @Transactional
    public ManagedUserStatusResponse updateStatus(UUID actorId, UUID targetId, String requestedStatus) {
        List<AccountRole> lockedAdmins = lockAdminMutations();
        Map<UUID, Account> lockedAccounts = lockAccounts(actorId, targetId);
        Account actor = lockedAccounts.get(actorId);
        requireEligibleAdmin(actor == null ? null : state(actor), actor == null ? List.of() : roles(actorId));
        Account target = lockedAccounts.get(targetId);
        if (target == null) {
            throw notFound();
        }

        String current = target.getAccountStatus();
        if (current.equals(requestedStatus)) {
            return statusResponse(targetId, current, target.getEmailVerifiedAt());
        }
        List<String> targetRoles = roles(targetId);
        boolean currentQualifyingAdmin = qualifies(state(target), targetRoles);
        if (currentQualifyingAdmin && SUSPENDED.equals(requestedStatus)
                && countQualifyingAdmins(lockedAdmins) <= 1) {
            throw lastAdminRequired();
        }
        if (actorId.equals(targetId)) {
            throw selfChangeForbidden();
        }
        if (ACTIVE.equals(current) && SUSPENDED.equals(requestedStatus)) {
            // Approved transition.
        } else if (SUSPENDED.equals(current) && ACTIVE.equals(requestedStatus)
                && target.getEmailVerifiedAt() != null) {
            // Approved transition; verification state is never changed here.
        } else if (SUSPENDED.equals(current) && ACTIVE.equals(requestedStatus)) {
            throw transitionNotAllowed();
        } else {
            throw transitionNotAllowed();
        }

        target.setAccountStatus(requestedStatus);
        accountRepository.saveAndFlush(target);
        return statusResponse(targetId, requestedStatus, target.getEmailVerifiedAt());
    }

    @Transactional
    public ManagedUserRolesResponse replaceRoles(UUID actorId, UUID targetId, Set<String> requestedRoles) {
        List<AccountRole> lockedAdmins = lockAdminMutations();
        Map<UUID, Account> lockedAccounts = lockAccounts(actorId, targetId);
        Account actor = lockedAccounts.get(actorId);
        requireEligibleAdmin(actor == null ? null : state(actor), actor == null ? List.of() : roles(actorId));
        Account target = lockedAccounts.get(targetId);
        if (target == null) {
            throw notFound();
        }

        List<String> currentRoles = roles(targetId);
        List<String> desiredRoles = requestedRoles.stream().sorted().toList();
        if (currentRoles.equals(desiredRoles)) {
            return new ManagedUserRolesResponse(targetId, currentRoles);
        }
        boolean currentQualifyingAdmin = qualifies(state(target), currentRoles);
        if (currentQualifyingAdmin && currentRoles.contains("ADMIN") && !requestedRoles.contains("ADMIN")
                && countQualifyingAdmins(lockedAdmins) <= 1) {
            throw lastAdminRequired();
        }
        if (actorId.equals(targetId)) {
            throw selfChangeForbidden();
        }

        long deleted = roleRepository.deleteByIdAccountId(targetId);
        if (deleted != currentRoles.size()) {
            throw new IllegalStateException("Locked account role replacement did not delete the expected rows");
        }
        if (!ALLOWED_ROLES.containsAll(desiredRoles)) {
            throw new IllegalStateException("Requested roles passed validation but include an unsupported role");
        }
        roleRepository.saveAll(desiredRoles.stream().map(role -> new AccountRole(targetId, role)).toList());
        roleRepository.flush();
        List<String> persistedRoles = roles(targetId);
        if (!persistedRoles.equals(desiredRoles)) {
            throw new IllegalStateException("Account role replacement could not be verified");
        }
        return new ManagedUserRolesResponse(targetId, persistedRoles);
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

    private AdminManagedAccountState state(Account account) {
        return new AdminManagedAccountState(account.getAccountId(), account.getAccountStatus(),
                account.getEmailVerifiedAt(), account.getRegisteredAt());
    }

    private void requireEligibleAdmin(AdminManagedAccountState actor, List<String> roles) {
        if (actor == null || !ACTIVE.equals(actor.accountStatus()) || actor.emailVerifiedAt() == null
                || !roles.contains("ADMIN")) {
            throw actorDenied();
        }
    }

    private boolean qualifies(AdminManagedAccountState account, List<String> roles) {
        return ACTIVE.equals(account.accountStatus()) && account.emailVerifiedAt() != null
                && roles.contains("ADMIN");
    }

    private ManagedUserResponse response(Account account, List<String> roles) {
        return new ManagedUserResponse(account.getAccountId(), account.getEmail(), account.getAccountStatus(),
                account.getEmailVerifiedAt(), account.getRegisteredAt(), roles.stream().sorted().toList());
    }

    private ManagedUserStatusResponse statusResponse(UUID id, String status, OffsetDateTime emailVerifiedAt) {
        return new ManagedUserStatusResponse(id, status, emailVerifiedAt);
    }

    private ApiOperationException actorDenied() {
        return new ApiOperationException(HttpStatus.FORBIDDEN, "ADMIN_ACCESS_DENIED",
                "Current account is not eligible to manage users", 0);
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found", 0);
    }

    private ApiOperationException transitionNotAllowed() {
        return new ApiOperationException(HttpStatus.CONFLICT, "ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED",
                "The account status transition is not allowed", 0);
    }

    private ApiOperationException selfChangeForbidden() {
        return new ApiOperationException(HttpStatus.CONFLICT, "ADMIN_SELF_CHANGE_FORBIDDEN",
                "An Admin cannot change their own status or roles", 0);
    }

    private ApiOperationException lastAdminRequired() {
        return new ApiOperationException(HttpStatus.CONFLICT, "LAST_QUALIFYING_ADMIN_REQUIRED",
                "At least one other active and verified Admin is required", 0);
    }
}
