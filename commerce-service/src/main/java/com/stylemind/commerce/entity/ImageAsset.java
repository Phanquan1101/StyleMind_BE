package com.stylemind.commerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "image_asset", schema = "public")
@Getter @Setter @NoArgsConstructor
public class ImageAsset {
    @Id @Column(name = "image_asset_id") private UUID imageAssetId;
    @Column(name = "image_url", nullable = false, columnDefinition = "text") private String imageUrl;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
}
