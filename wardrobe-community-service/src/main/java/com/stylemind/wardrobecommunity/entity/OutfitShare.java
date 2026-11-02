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

@Entity @Table(name = "outfit_share", schema = "public")
@Getter @Setter @NoArgsConstructor
public class OutfitShare {
    @Id @Column(name = "outfit_share_id") private UUID outfitShareId;
    @Column(name = "outfit_id", nullable = false) private UUID outfitId;
    @Column(name = "recipient_email", nullable = false, columnDefinition = "text") private String recipientEmail;
    @Column(name = "granted_at", nullable = false) private OffsetDateTime grantedAt;
    @Column(name = "revoked_at") private OffsetDateTime revokedAt;
}
