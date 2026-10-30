package com.stylemind.wardrobecommunity.service.friendship;

import com.stylemind.wardrobecommunity.dto.friendship.FriendPageCriteria;
import com.stylemind.wardrobecommunity.dto.friendship.IncomingFriendInvitationListResponse;
import com.stylemind.wardrobecommunity.model.friendship.InvitationActionResult;
import java.util.UUID;

public interface FriendInvitationService {
    InvitationActionResult create(UUID actorId, String recipientEmail);
    InvitationActionResult resend(UUID actorId, UUID invitationId);
    InvitationActionResult accept(UUID actorId, UUID invitationId, String token);
    IncomingFriendInvitationListResponse incoming(UUID actorId, FriendPageCriteria criteria);
}
