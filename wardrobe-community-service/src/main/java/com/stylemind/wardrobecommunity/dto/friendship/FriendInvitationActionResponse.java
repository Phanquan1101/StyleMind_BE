package com.stylemind.wardrobecommunity.dto.friendship;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FriendInvitationActionResponse(
        @JsonProperty("friend_invitation_id") UUID friendInvitationId,
        String status, String outcome,
        @JsonProperty("delivery_status") String deliveryStatus,
        @JsonProperty("expires_at") OffsetDateTime expiresAt,
        @JsonProperty("retry_after_seconds") long retryAfterSeconds) {}
