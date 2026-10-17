package com.stylemind.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "style_profile", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class StyleProfile {
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "fit_preference", columnDefinition = "text")
    private String fitPreference;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Column(name = "usual_top_size", columnDefinition = "text")
    private String usualTopSize;

    @Column(name = "usual_bottom_size", columnDefinition = "text")
    private String usualBottomSize;

    @Column(name = "shoe_size", columnDefinition = "text")
    private String shoeSize;
}
