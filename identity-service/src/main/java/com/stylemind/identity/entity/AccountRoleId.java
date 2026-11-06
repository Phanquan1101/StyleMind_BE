package com.stylemind.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountRoleId implements Serializable {
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "role_code", nullable = false, columnDefinition = "text")
    private String roleCode;
}
