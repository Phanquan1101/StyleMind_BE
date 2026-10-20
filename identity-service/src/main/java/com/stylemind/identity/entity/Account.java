package com.stylemind.identity.entity;

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
@Table(name = "account", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class Account {
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "email", nullable = false, unique = true, columnDefinition = "text")
    private String email;

    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;

    @Column(name = "account_status", nullable = false, columnDefinition = "text")
    private String accountStatus;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt;
}
