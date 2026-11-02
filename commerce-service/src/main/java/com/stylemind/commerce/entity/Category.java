package com.stylemind.commerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "category", schema = "public")
@Getter @Setter @NoArgsConstructor
public class Category {
    @Id @Column(name = "category_id", nullable = false)
    private UUID categoryId;
    @Column(nullable = false, columnDefinition = "text") private String name;
    @Column(nullable = false, unique = true, columnDefinition = "text") private String slug;
    @Column(name = "is_active", nullable = false) private boolean active;
}
