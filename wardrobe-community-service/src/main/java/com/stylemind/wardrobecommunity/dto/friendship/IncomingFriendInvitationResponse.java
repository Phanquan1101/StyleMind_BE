package com.stylemind.wardrobecommunity.dto.friendship;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record IncomingFriendInvitationResponse(
        @JsonProperty("friend_invitation_id") UUID friendInvitationId,
        @JsonProperty("sender_account_id") UUID senderAccountId,
        @JsonProperty("sender_display_name") String senderDisplayName,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("expires_at") OffsetDateTime expiresAt) {}
