package com.stylemind.identity.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "profile_brand", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class ProfileBrand {
    @EmbeddedId
    private ProfileBrandId id;

    public ProfileBrand(java.util.UUID accountId, java.util.UUID brandId) {
        this.id = new ProfileBrandId(accountId, brandId);
    }
}
