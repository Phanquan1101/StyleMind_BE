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

/** Stable canonical pair. Unfriend marks the row; future Chat history must not be deleted. */
@Entity @Table(name = "friendship", schema = "public")
@Getter @Setter @NoArgsConstructor
public class Friendship {
    @Id @Column(name = "friendship_id") private UUID friendshipId;
    @Column(name = "account_low_id", nullable = false) private UUID accountLowId;
    @Column(name = "account_high_id", nullable = false) private UUID accountHighId;
    @Column(name = "established_at", nullable = false) private OffsetDateTime establishedAt;
    @Column(name = "unfriended_at") private OffsetDateTime unfriendedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "invitations_invalid_before") private OffsetDateTime invitationsInvalidBefore;
}
