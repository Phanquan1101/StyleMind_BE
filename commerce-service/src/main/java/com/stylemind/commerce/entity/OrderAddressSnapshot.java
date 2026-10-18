package com.stylemind.commerce.entity;
import jakarta.persistence.Column; import jakarta.persistence.Entity; import jakarta.persistence.Id; import jakarta.persistence.Table; import java.util.UUID; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
@Entity @Table(name="order_address_snapshot",schema="public") @Getter @Setter @NoArgsConstructor
public class OrderAddressSnapshot { @Id @Column(name="order_id") private UUID orderId; @Column(name="source_address_id") private UUID sourceAddressId; @Column(name="recipient_name",nullable=false) private String recipientName; @Column(name="recipient_phone",nullable=false) private String recipientPhone; @Column(name="address_line",nullable=false) private String addressLine; }
