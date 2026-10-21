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

@Entity @Table(name = "outfit", schema = "public")
@Getter @Setter @NoArgsConstructor
public class Outfit {
    @Id @Column(name = "outfit_id") private UUID outfitId;
    @Column(name = "owner_account_id", nullable = false) private UUID ownerAccountId;
    @Column(name = "name", nullable = false, columnDefinition = "text") private String name;
    @Column(name = "creation_source", nullable = false, columnDefinition = "text") private String creationSource;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
}
