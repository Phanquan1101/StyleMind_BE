package com.stylemind.identity.service;

import com.stylemind.identity.dto.users.ManageUsersListRequest;
import com.stylemind.identity.dto.users.ManageUsersPageResponse;
import com.stylemind.identity.dto.users.ManagedUserRolesResponse;
import com.stylemind.identity.dto.users.ManagedUserStatusResponse;
import java.util.Set;
import java.util.UUID;

public interface AdminUserService {
    ManageUsersPageResponse list(UUID actorId, ManageUsersListRequest request);
    ManagedUserStatusResponse updateStatus(UUID actorId, UUID targetId, String requestedStatus);
    ManagedUserRolesResponse replaceRoles(UUID actorId, UUID targetId, Set<String> requestedRoles);
}
