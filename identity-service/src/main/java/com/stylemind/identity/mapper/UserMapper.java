package com.stylemind.identity.mapper;

import com.stylemind.identity.dto.users.ManagedUserResponse;
import com.stylemind.identity.dto.users.ManagedUserRolesResponse;
import com.stylemind.identity.dto.users.ManageUsersPageResponse;
import com.stylemind.identity.dto.users.ManagedUserStatusResponse;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public ManagedUserResponse response(Account account, List<String> roles) {
        return new ManagedUserResponse(account.getAccountId(), account.getEmail(), account.getAccountStatus(),
                account.getEmailVerifiedAt(), account.getRegisteredAt(), roles.stream().sorted().toList());
    }

    public ManagedUserStatusResponse status(UUID accountId, String status, OffsetDateTime emailVerifiedAt) {
        return new ManagedUserStatusResponse(accountId, status, emailVerifiedAt);
    }

    public ManagedUserRolesResponse roles(UUID accountId, List<String> roleCodes) {
        return new ManagedUserRolesResponse(accountId, roleCodes.stream().sorted().toList());
    }

    public List<AccountRole> accountRoles(UUID accountId, Collection<String> roleCodes) {
        return roleCodes.stream().map(roleCode -> new AccountRole(accountId, roleCode)).toList();
    }

    public ManageUsersPageResponse page(List<ManagedUserResponse> users, int page, int size,
            long totalElements, long totalPages) {
        return new ManageUsersPageResponse(users, page, size, totalElements, totalPages);
    }
}
