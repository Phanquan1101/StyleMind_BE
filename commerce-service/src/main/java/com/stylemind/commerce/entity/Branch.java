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
@Table(name = "branch", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class Branch {
    @Id
    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "branch_code", nullable = false, unique = true, columnDefinition = "text")
    private String branchCode;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "address_line", nullable = false, columnDefinition = "text")
    private String addressLine;

    @Column(name = "contact_phone", columnDefinition = "text")
    private String contactPhone;

    @Column(name = "branch_status", nullable = false, columnDefinition = "text")
    private String branchStatus;
}
