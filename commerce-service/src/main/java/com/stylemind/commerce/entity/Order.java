package com.stylemind.commerce.entity;

import jakarta.persistence.Column; import jakarta.persistence.Entity; import jakarta.persistence.Id; import jakarta.persistence.Table;
import java.time.OffsetDateTime; import java.util.UUID; import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;

@Entity @Table(name="customer_order", schema="public") @Getter @Setter @NoArgsConstructor
public class Order {
 @Id @Column(name="order_id") private UUID orderId; @Column(name="order_number",nullable=false) private String orderNumber;
 @Column(name="customer_id",nullable=false) private UUID customerId; @Column(name="checkout_key",nullable=false) private String checkoutKey;
 @Column(name="order_status",nullable=false) private String orderStatus; @Column(name="payment_method",nullable=false) private String paymentMethod;
 @Column(name="accepted_total_vnd",nullable=false) private long acceptedTotalVnd; @Column(name="placed_at",nullable=false) private OffsetDateTime placedAt;
 @Column(name="completed_at") private OffsetDateTime completedAt;
}
