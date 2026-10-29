package com.stylemind.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "customer_address", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class CustomerAddress {
    @Id
    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "label", columnDefinition = "text")
    private String label;

    @Column(name = "recipient_name", nullable = false, columnDefinition = "text")
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, columnDefinition = "text")
    private String recipientPhone;

    @Column(name = "address_line", nullable = false, columnDefinition = "text")
    private String addressLine;

    @Column(name = "ward_code", columnDefinition = "text")
    private String wardCode;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;
}
