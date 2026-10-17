package com.stylemind.identity.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "account_role", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class AccountRole {
    @EmbeddedId
    private AccountRoleId id;

    public AccountRole(java.util.UUID accountId, String roleCode) {
        this.id = new AccountRoleId(accountId, roleCode);
    }

    public java.util.UUID getAccountId() { return id.getAccountId(); }
    public String getRoleCode() { return id.getRoleCode(); }
}
