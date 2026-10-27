package com.stylemind.commerce.repository.order;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CodOrderConfirmationRepository {
    private final JdbcClient jdbc;
    public CodOrderConfirmationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    public record Order(UUID customer, String method, long amount, String status) { }
    public Optional<Order> lock(UUID id) {
        return jdbc.sql("SELECT customer_id,payment_method,accepted_total_vnd,order_status FROM public.customer_order WHERE order_id=:id FOR UPDATE")
                .param("id", id).query((rs, row) -> new Order(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getString(4))).optional();
    }
    public void confirm(UUID id, Instant now) {
        int updated = jdbc.sql("UPDATE public.customer_order SET order_status='PLACED' WHERE order_id=:id AND order_status='PENDING_PAYMENT' AND payment_method='COD'")
                .param("id", id).update();
        if (updated != 1) throw new IllegalStateException("COD order confirmation was not applied");
        jdbc.sql("""
                INSERT INTO public.order_status_history(history_id,order_id,from_status,to_status,actor_user_id,changed_at,reason)
                VALUES (:history,:id,'PENDING_PAYMENT','PLACED',NULL,:now,'COD payment attempt created; payment not collected')
                """).param("history", UUID.randomUUID()).param("id", id).param("now", Timestamp.from(now)).update();
    }
}
