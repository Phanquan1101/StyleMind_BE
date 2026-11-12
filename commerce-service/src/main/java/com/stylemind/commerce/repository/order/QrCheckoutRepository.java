package com.stylemind.commerce.repository.order;

import com.stylemind.commerce.entity.QrCheckout;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QrCheckoutRepository extends JpaRepository<QrCheckout, UUID> {
    @Query("""
            select checkout from QrCheckout checkout
            where checkout.customerId = :customer
              and checkout.checkoutStatus in ('PREPARING', 'AWAITING_PAYMENT')
              and checkout.expiresAt > :now
            """)
    Page<QrCheckout> findPending(@Param("customer") UUID customer, @Param("now") OffsetDateTime now,
                                Pageable pageable);

    /** Unpaid checkouts the customer may want to see in the order list: still open, or expired/needing review recently. */
    @Query("""
            select checkout from QrCheckout checkout
            where checkout.customerId = :customer
              and checkout.checkoutStatus in ('PREPARING', 'AWAITING_PAYMENT', 'EXPIRED', 'RECONCILIATION_REQUIRED')
              and checkout.expiresAt > :since
            """)
    Page<QrCheckout> findRecentUnpaid(@Param("customer") UUID customer, @Param("since") OffsetDateTime since,
                                      Pageable pageable);
}
