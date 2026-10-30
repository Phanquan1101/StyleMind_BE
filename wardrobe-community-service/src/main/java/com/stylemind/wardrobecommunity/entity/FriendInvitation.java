package com.stylemind.wardrobecommunity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Invitation credentials are digest-only. Do not serialize this entity as an HTTP response. */
@Entity @Table(name = "friend_invitation", schema = "public")
@Getter @Setter @NoArgsConstructor
public class FriendInvitation {
    @Id @Column(name = "friend_invitation_id") private UUID friendInvitationId;
    @Column(name = "sender_account_id", nullable = false) private UUID senderAccountId;
    @Column(name = "recipient_email", nullable = false, columnDefinition = "text") private String recipientEmail;
    @Column(name = "status", nullable = false, columnDefinition = "text") private String status;
    @Column(name = "token_hash", nullable = false, columnDefinition = "text") private String tokenHash;
    @Column(name = "token_issued_at", nullable = false) private OffsetDateTime tokenIssuedAt;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "accepted_by_account_id") private UUID acceptedByAccountId;
    @Column(name = "accepted_at") private OffsetDateTime acceptedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "delivery_state", nullable = false, columnDefinition = "text") private String deliveryState = "UNKNOWN";
    @Column(name = "delivery_attempt_id") private UUID deliveryAttemptId;
    @Column(name = "last_delivery_attempt_at") private OffsetDateTime lastDeliveryAttemptAt;
}
