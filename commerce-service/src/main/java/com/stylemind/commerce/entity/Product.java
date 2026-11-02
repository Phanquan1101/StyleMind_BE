package com.stylemind.commerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "product", schema = "public")
@Getter @Setter @NoArgsConstructor
public class Product {
    @Id @Column(name = "product_id") private UUID productId;
    @Column(name = "category_id", nullable = false) private UUID categoryId;
    @Column(name = "brand_id", nullable = false) private UUID brandId;
    @Column(nullable = false, columnDefinition = "text") private String name;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "demographic_segment", columnDefinition = "text") private String demographicSegment;
    @Column(name = "product_status", nullable = false, columnDefinition = "text") private String productStatus;
}
