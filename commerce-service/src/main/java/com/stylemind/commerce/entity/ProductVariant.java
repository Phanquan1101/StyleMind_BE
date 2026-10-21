package com.stylemind.commerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "product_variant", schema = "public")
@Getter @Setter @NoArgsConstructor
public class ProductVariant {
    @Id @Column(name = "variant_id") private UUID variantId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(nullable = false, unique = true, columnDefinition = "text") private String sku;
    @Column(columnDefinition = "text") private String size;
    @Column(columnDefinition = "text") private String color;
    @Column(columnDefinition = "text") private String material;
    @Column(name = "unit_price_vnd", nullable = false) private long unitPriceVnd;
    @Column(name = "variant_status", nullable = false, columnDefinition = "text") private String variantStatus;
}
