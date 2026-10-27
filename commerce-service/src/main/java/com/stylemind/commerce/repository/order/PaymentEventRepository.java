package com.stylemind.commerce.repository.order;

import com.stylemind.commerce.dto.order.PaymentConfirmedEvent;
import com.stylemind.commerce.dto.order.PaymentEventReceipt;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentEventRepository {
    private final JdbcClient jdbc;
    public PaymentEventRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    public record Stored(String fingerprint, PaymentEventReceipt receipt) { }
    public record Order(UUID customer, String method, long amount, String status) { }
    public void lockEvent(UUID id) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:key)")
                .param("key", id.getMostSignificantBits() ^ id.getLeastSignificantBits())
                .query((rs, row) -> true).single();
    }
    public Optional<Stored> received(UUID id) {
        return jdbc.sql("SELECT event_fingerprint,outcome,reason FROM public.payment_event_inbox WHERE event_id=:id")
                .param("id", id).query((rs, row) -> new Stored(rs.getString(1),
                        new PaymentEventReceipt(id, rs.getString(2), rs.getString(3)))).optional();
    }
    public Optional<Order> lockOrder(UUID id) {
        return jdbc.sql("SELECT customer_id,payment_method,accepted_total_vnd,order_status FROM public.customer_order WHERE order_id=:id FOR UPDATE")
                .param("id", id).query((rs, row) -> new Order(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getString(4))).optional();
    }
    public Optional<Boolean> sameAppliedPayment(PaymentConfirmedEvent event) {
        return jdbc.sql("""
                SELECT payment_attempt_id,customer_id,payment_method,payment_reference,amount_vnd,confirmed_at
                FROM public.payment_event_inbox WHERE order_id=:id AND outcome='APPLIED'
                """).param("id", event.order_id()).query((rs, row) ->
                    event.payment_attempt_id().equals(rs.getObject(1, UUID.class))
                    && event.customer_id().equals(rs.getObject(2, UUID.class))
                    && event.payment_method().equals(rs.getString(3)) && event.payment_reference().equals(rs.getString(4))
                    && event.amount_vnd() == rs.getLong(5)
                    && event.confirmed_at().truncatedTo(ChronoUnit.MICROS).equals(rs.getTimestamp(6).toInstant())).optional();
    }
    public void paid(PaymentConfirmedEvent event, Instant now) {
        int changed = jdbc.sql("UPDATE public.customer_order SET order_status='PAID' WHERE order_id=:id AND order_status='PENDING_PAYMENT'")
                .param("id", event.order_id()).update();
        if (changed != 1) throw new IllegalStateException("Order payment transition was not applied");
        jdbc.sql("""
                INSERT INTO public.order_status_history(history_id,order_id,from_status,to_status,actor_user_id,changed_at,reason)
                VALUES (:history,:id,'PENDING_PAYMENT','PAID',NULL,:now,'PaymentConfirmed')
                """).param("history", UUID.randomUUID()).param("id", event.order_id()).param("now", Timestamp.from(now)).update();
    }
    public void record(PaymentConfirmedEvent event, String fingerprint, PaymentEventReceipt receipt, Instant now) {
        jdbc.sql("""
                INSERT INTO public.payment_event_inbox(event_id,event_fingerprint,order_id,payment_attempt_id,customer_id,
                    payment_method,payment_reference,amount_vnd,confirmed_at,received_at,outcome,reason)
                VALUES (:event,:hash,:order,:attempt,:customer,:method,:reference,:amount,:confirmed,:received,:outcome,:reason)
                """).param("event", event.event_id()).param("hash", fingerprint).param("order", event.order_id())
                .param("attempt", event.payment_attempt_id()).param("customer", event.customer_id())
                .param("method", event.payment_method()).param("reference", event.payment_reference()).param("amount", event.amount_vnd())
                .param("confirmed", Timestamp.from(event.confirmed_at().truncatedTo(ChronoUnit.MICROS)))
                .param("received", Timestamp.from(now)).param("outcome", receipt.outcome()).param("reason", receipt.reason()).update();
    }
}
