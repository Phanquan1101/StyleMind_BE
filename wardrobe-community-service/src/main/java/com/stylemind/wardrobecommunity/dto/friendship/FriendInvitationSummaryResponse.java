package com.stylemind.wardrobecommunity.dto.friendship;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Sender-visible metadata only. Credentials, digests and the recipient account ID are never returned. */
public record FriendInvitationSummaryResponse(
        @JsonProperty("friend_invitation_id") @Schema(example = "30000000-0000-0000-0000-000000000003") UUID friendInvitationId,
        @JsonProperty("recipient_email") @Schema(example = "friend@example.test") String recipientEmail,
        @Schema(allowableValues = {"PENDING", "ACCEPTED", "EXPIRED", "INVALIDATED"}, example = "PENDING") String status,
        @JsonProperty("created_at") @Schema(example = "2026-10-24T09:00:00Z") OffsetDateTime createdAt,
        @JsonProperty("expires_at") @Schema(example = "2026-10-31T09:00:00Z") OffsetDateTime expiresAt,
        @JsonProperty("accepted_at") @Schema(nullable = true, example = "2026-10-25T09:00:00Z") OffsetDateTime acceptedAt) {
}
