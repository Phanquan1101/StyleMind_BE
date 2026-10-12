package com.stylemind.identity.service.users.impl;

import com.stylemind.identity.service.users.ManageUsersService;
import com.stylemind.identity.dto.users.ManageUsersPageResponse;
import com.stylemind.identity.dto.users.ManagedUserResponse;
import com.stylemind.identity.dto.users.ManagedUserRolesResponse;
import com.stylemind.identity.dto.users.ManagedUserStatusResponse;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.model.users.AdminManagedAccountState;
import com.stylemind.identity.model.users.ManageUserListCriteria;
import com.stylemind.identity.model.users.ManagedUserPageData;
import com.stylemind.identity.model.users.ManagedUserSummary;
import com.stylemind.identity.repository.users.ManageUsersRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageUsersServiceImpl implements ManageUsersService {

    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "ADMIN");
    private static final String ACTIVE = "ACTIVE";
    private static final String SUSPENDED = "SUSPENDED";

    private final ManageUsersRepository repository;

    public ManageUsersServiceImpl(ManageUsersRepository repository) {
        this.repository = repository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ManageUsersPageResponse list(UUID actorId, ManageUserListCriteria criteria) {
        repository.lockAdminListing();
        AdminManagedAccountState actor = repository.lockActorForRead(actorId)
                .orElseThrow(this::actorDenied);
        requireEligibleAdmin(actor, repository.roles(actorId));
        ManagedUserPageData page = repository.userPage(criteria);
        List<ManagedUserResponse> items = page.items().stream()
                .map(this::response).toList();
        long total = page.totalElements();
        long pages = total == 0 ? 0 : (total + criteria.size() - 1) / criteria.size();
        return new ManageUsersPageResponse(items, criteria.page(), criteria.size(), total, pages);
    }

    @Transactional
    public ManagedUserStatusResponse updateStatus(UUID actorId, UUID targetId, String requestedStatus) {
        repository.lockAdminMutations();
        Map<UUID, AdminManagedAccountState> locked = repository.lockAccountsForMutation(List.of(actorId, targetId));
        AdminManagedAccountState actor = locked.get(actorId);
        requireEligibleAdmin(actor, actor == null ? List.of() : repository.roles(actorId));
        AdminManagedAccountState target = locked.get(targetId);
        if (target == null) {
            throw notFound();
        }

        String current = target.accountStatus();
        if (current.equals(requestedStatus)) {
            return statusResponse(targetId, current, target.emailVerifiedAt());
        }
        boolean currentQualifyingAdmin = qualifies(target, repository.roles(targetId));
        if (currentQualifyingAdmin && SUSPENDED.equals(requestedStatus)
                && repository.countQualifyingAdmins() <= 1) {
            throw lastAdminRequired();
        }
        if (actorId.equals(targetId)) {
            throw selfChangeForbidden();
        }
        if (ACTIVE.equals(current) && SUSPENDED.equals(requestedStatus)) {
            // Approved transition.
        } else if (SUSPENDED.equals(current) && ACTIVE.equals(requestedStatus)
                && target.emailVerifiedAt() != null) {
            // Approved transition; verification state is never changed here.
        } else if (SUSPENDED.equals(current) && ACTIVE.equals(requestedStatus)) {
            throw transitionNotAllowed();
        } else {
            throw transitionNotAllowed();
        }

        if (repository.updateStatus(targetId, requestedStatus) != 1) {
            throw new IllegalStateException("Locked account status update did not affect one row");
        }
        return statusResponse(targetId, requestedStatus, target.emailVerifiedAt());
    }

    @Transactional
    public ManagedUserRolesResponse replaceRoles(UUID actorId, UUID targetId, Set<String> requestedRoles) {
        repository.lockAdminMutations();
        Map<UUID, AdminManagedAccountState> locked = repository.lockAccountsForMutation(List.of(actorId, targetId));
        AdminManagedAccountState actor = locked.get(actorId);
        requireEligibleAdmin(actor, actor == null ? List.of() : repository.roles(actorId));
        AdminManagedAccountState target = locked.get(targetId);
        if (target == null) {
            throw notFound();
        }

        List<String> currentRoles = repository.roles(targetId);
        List<String> desiredRoles = requestedRoles.stream().sorted().toList();
        if (currentRoles.equals(desiredRoles)) {
            return new ManagedUserRolesResponse(targetId, currentRoles);
        }
        boolean currentQualifyingAdmin = qualifies(target, currentRoles);
        if (currentQualifyingAdmin && currentRoles.contains("ADMIN") && !requestedRoles.contains("ADMIN")
                && repository.countQualifyingAdmins() <= 1) {
            throw lastAdminRequired();
        }
        if (actorId.equals(targetId)) {
            throw selfChangeForbidden();
        }

        int deleted = repository.deleteRoles(targetId);
        if (deleted != currentRoles.size()) {
            throw new IllegalStateException("Locked account role replacement did not delete the expected rows");
        }
        for (String role : desiredRoles) {
            if (!ALLOWED_ROLES.contains(role) || repository.insertRole(targetId, role) != 1) {
                throw new IllegalStateException("Account role replacement did not insert the expected row");
            }
        }
        List<String> persistedRoles = repository.roles(targetId);
        if (!persistedRoles.equals(desiredRoles)) {
            throw new IllegalStateException("Account role replacement could not be verified");
        }
        return new ManagedUserRolesResponse(targetId, persistedRoles);
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

    private ManagedUserResponse response(ManagedUserSummary summary) {
        return new ManagedUserResponse(summary.accountId(), summary.email(), summary.accountStatus(),
                summary.emailVerifiedAt(), summary.registeredAt(), summary.roles());
    }

    private ManagedUserStatusResponse statusResponse(UUID id, String status,
            java.time.OffsetDateTime emailVerifiedAt) {
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
