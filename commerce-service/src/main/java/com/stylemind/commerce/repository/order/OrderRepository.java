package com.stylemind.commerce.repository.order;
import com.stylemind.commerce.entity.Order; import java.time.OffsetDateTime; import java.util.List; import java.util.Optional; import java.util.UUID; import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType;
public interface OrderRepository extends JpaRepository<Order,UUID> {
 @Query(value = """
   SELECT o.order_id FROM customer_order o
   WHERE o.payment_method='COD' AND o.order_status='COMPLETED'
     AND (CAST(:after AS uuid) IS NULL OR o.order_id>CAST(:after AS uuid))
     AND EXISTS (SELECT 1 FROM order_status_history h WHERE h.order_id=o.order_id
       AND h.from_status='SHIPPED' AND h.to_status='COMPLETED' AND h.actor_user_id IS NOT NULL
       AND (h.reason='COD delivery and collection requested' OR h.reason LIKE 'COD delivery and collection requested | %'))
     AND NOT EXISTS (SELECT 1 FROM payment_event_inbox e WHERE e.order_id=o.order_id
       AND e.outcome='APPLIED' AND e.payment_method='COD' AND e.amount_vnd=o.accepted_total_vnd
       AND e.customer_id IS NOT DISTINCT FROM o.customer_id)
   ORDER BY o.order_id
   """, nativeQuery = true)
 List<UUID> findPendingCodCollectionOrderIds(@Param("after") UUID after, Pageable pageable);
 interface PaymentProgress {
  String getPaymentStatus();
  Long getRefundedAmountVnd();
  String getRefundStatus();
 }
 @Query(value = """
   SELECT CASE WHEN EXISTS (SELECT 1 FROM payment_event_inbox e WHERE e.order_id=o.order_id
     AND e.outcome='APPLIED' AND e.payment_method=o.payment_method AND e.amount_vnd=o.accepted_total_vnd
     AND e.customer_id IS NOT DISTINCT FROM o.customer_id) THEN 'PAID' ELSE 'PENDING' END AS "paymentStatus",
     COALESCE(r.amount,0) AS "refundedAmountVnd",
     CASE WHEN r.amount >= o.accepted_total_vnd THEN 'REFUNDED'
          WHEN r.amount > 0 THEN 'PARTIALLY_REFUNDED'
          WHEN EXISTS(SELECT 1 FROM return_request t WHERE t.order_id=o.order_id AND t.return_status='PENDING_REFUND')
            OR EXISTS(SELECT 1 FROM cancellation_refund_obligation c WHERE c.order_id=o.order_id AND c.obligation_status='PENDING_REFUND')
          THEN 'PENDING_REFUND' ELSE NULL END AS "refundStatus"
   FROM customer_order o LEFT JOIN
     (SELECT order_id,SUM(amount_vnd)::bigint AS amount FROM refund_result_inbox GROUP BY order_id) r ON r.order_id=o.order_id
   WHERE o.order_id=:orderId
   """, nativeQuery = true)
 PaymentProgress paymentProgress(@Param("orderId") UUID orderId);
 Optional<Order> findByGuestSessionHashAndCheckoutKey(String guestSessionHash,String checkoutKey);
 Optional<Order> findByOrderIdAndCustomerIdIsNullAndGuestSessionHash(UUID orderId,String guestSessionHash);
 Optional<Order> findByCustomerIdAndCheckoutKey(UUID customerId,String checkoutKey);
 long countByCustomerIdAndOrderStatus(UUID customerId,String status);
 long countByCustomerId(UUID customerId);
 long countByOrderStatus(String status);
 List<Order> findByCustomerIdAndOrderStatusOrderByPlacedAtDescOrderIdDesc(UUID customerId,String status,Pageable pageable);
 List<Order> findByCustomerIdOrderByPlacedAtDescOrderIdDesc(UUID customerId,Pageable pageable);
 List<Order> findByOrderStatusOrderByPlacedAtDescOrderIdDesc(String status,Pageable pageable);
 List<Order> findAllByOrderByPlacedAtDescOrderIdDesc(Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<Order> findLockedByOrderId(UUID orderId);
 @Query("select o.orderId from Order o where o.orderStatus='PENDING_PAYMENT' and o.paymentMethod in ('SEPAY_VIETQR','VNPAY') and o.placedAt<=:expiresAt order by o.placedAt,o.orderId") List<UUID> findExpiredPendingSepayOrderIds(@Param("expiresAt") OffsetDateTime expiresAt, Pageable pageable);
}
