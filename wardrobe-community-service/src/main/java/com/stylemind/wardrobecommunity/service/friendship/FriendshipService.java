package com.stylemind.wardrobecommunity.service.friendship;

import com.stylemind.wardrobecommunity.dto.friendship.FriendInvitationListResponse;
import com.stylemind.wardrobecommunity.dto.friendship.FriendListResponse;
import com.stylemind.wardrobecommunity.dto.friendship.FriendPageCriteria;
import java.util.UUID;

public interface FriendshipService {
    FriendListResponse list(UUID actorId, FriendPageCriteria criteria);
    FriendInvitationListResponse outgoingInvitations(UUID actorId, FriendPageCriteria criteria);
    void unfriend(UUID actorId, UUID friendAccountId);
}
