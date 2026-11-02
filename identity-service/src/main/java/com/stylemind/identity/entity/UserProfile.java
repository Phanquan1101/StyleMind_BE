package com.stylemind.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_profile", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "display_name", nullable = false, columnDefinition = "text")
    private String displayName;
}
