package com.stylemind.commerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "variant_image", schema = "public")
@Getter @Setter @NoArgsConstructor
public class VariantImage {
    @Id @Column(name = "image_id") private UUID imageId;
    @Column(name = "variant_id", nullable = false) private UUID variantId;
    @Column(name = "image_url", nullable = false, columnDefinition = "text") private String imageUrl;
    @Column(name = "alt_text", columnDefinition = "text") private String altText;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "is_primary", nullable = false) private boolean primary;
}
