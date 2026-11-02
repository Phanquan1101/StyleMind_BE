package com.stylemind.identity.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "profile_style", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class ProfileStyle {
    @EmbeddedId
    private ProfileStyleId id;

    public ProfileStyle(java.util.UUID accountId, String styleCode) {
        this.id = new ProfileStyleId(accountId, styleCode);
    }
}
