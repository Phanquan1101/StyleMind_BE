package com.stylemind.identity.service.users;

import com.stylemind.identity.dto.users.ManageUsersPageResponse;
import com.stylemind.identity.dto.users.ManagedUserRolesResponse;
import com.stylemind.identity.dto.users.ManagedUserStatusResponse;
import com.stylemind.identity.model.users.ManageUserListCriteria;
import java.util.Set;
import java.util.UUID;

public interface ManageUsersService {
    ManageUsersPageResponse list(UUID actorId, ManageUserListCriteria criteria);
    ManagedUserStatusResponse updateStatus(UUID actorId, UUID targetId, String requestedStatus);
    ManagedUserRolesResponse replaceRoles(UUID actorId, UUID targetId, Set<String> requestedRoles);
}
