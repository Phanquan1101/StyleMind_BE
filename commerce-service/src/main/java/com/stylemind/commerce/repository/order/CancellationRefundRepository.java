package com.stylemind.commerce.repository.order;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CancellationRefundRepository {
    private final JdbcClient jdbc;

    public CancellationRefundRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean record(UUID requestId, UUID orderId, OffsetDateTime now) {
        return jdbc.sql("""
                INSERT INTO public.cancellation_refund_obligation
                    (cancellation_request_id,order_id,payment_event_id,amount_vnd,obligation_status,created_at)
                SELECT :request,o.order_id,e.event_id,e.amount_vnd,'PENDING_REFUND',:now
                FROM public.customer_order o JOIN public.payment_event_inbox e ON e.order_id=o.order_id
                WHERE o.order_id=:order AND e.outcome='APPLIED' AND e.payment_method IN ('SEPAY_VIETQR','VNPAY')
                    AND o.payment_method=e.payment_method AND o.customer_id=e.customer_id
                    AND o.accepted_total_vnd=e.amount_vnd
                """).param("request", requestId).param("order", orderId).param("now", now).update() == 1;
    }

    public String status(UUID requestId) {
        return jdbc.sql("SELECT obligation_status FROM public.cancellation_refund_obligation WHERE cancellation_request_id=:id")
                .param("id", requestId).query(String.class).optional().orElse(null);
    }

    public boolean isPending(UUID requestId) {
        return jdbc.sql("SELECT request_status='PENDING' FROM public.cancellation_request WHERE cancellation_request_id=:id")
                .param("id", requestId).query(Boolean.class).optional().orElse(false);
    }
}
