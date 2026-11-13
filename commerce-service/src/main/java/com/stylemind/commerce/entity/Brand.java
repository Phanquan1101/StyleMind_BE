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

@Entity
@Table(name = "brand", schema = "public")
@Getter @Setter @NoArgsConstructor
public class Brand {
    @Id @Column(name = "brand_id", nullable = false)
    private UUID brandId;
    @Column(nullable = false, columnDefinition = "text") private String name;
    @Column(nullable = false, columnDefinition = "text") private String slug;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "deleted_at") private OffsetDateTime deletedAt;
}
