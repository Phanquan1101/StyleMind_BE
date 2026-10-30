package com.stylemind.wardrobecommunity.dto.friendship;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record AcceptFriendInvitationRequest(@JsonProperty("friend_invitation_id") UUID friendInvitationId, String token) {
    @Override public String toString() { return "AcceptFriendInvitationRequest[REDACTED]"; }
}
