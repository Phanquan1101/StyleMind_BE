package com.stylemind.identity.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "profile_color", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class ProfileColor {
    @EmbeddedId
    private ProfileColorId id;

    public ProfileColor(java.util.UUID accountId, String colorCode) {
        this.id = new ProfileColorId(accountId, colorCode);
    }
}
