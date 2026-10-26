package com.stylemind.payment.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOutboxRepository {

    private final JdbcClient jdbcClient;

    public PaymentOutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public record PendingEvent(UUID id, Instant createdAt, UUID attemptId, String reference, long amount, String payload) { }

    public java.util.List<PendingEvent> pendingAfter(PendingEvent cursor, int limit) {
        String after = cursor == null ? "" : " AND (o.created_at,o.outbox_event_id) > (:created,:id)";
        var query = jdbcClient.sql("""
                SELECT o.outbox_event_id,o.created_at,a.payment_attempt_id,a.payment_reference,
                       a.expected_amount_vnd,o.event_payload
                FROM public.payment_outbox o JOIN public.payment_attempt a ON a.payment_attempt_id=o.aggregate_id
                WHERE o.published_at IS NULL AND o.event_type='PaymentConfirmed' AND a.payment_method='SEPAY_VIETQR'
                """ + after + " ORDER BY o.created_at,o.outbox_event_id LIMIT :limit").param("limit", limit);
        if (cursor != null) query = query.param("created", java.sql.Timestamp.from(cursor.createdAt())).param("id", cursor.id());
        return query.query((rs, row) -> new PendingEvent(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getLong(5), rs.getString(6))).list();
    }

    public boolean acknowledge(UUID id, Instant now) {
        return jdbcClient.sql("UPDATE public.payment_outbox SET published_at=:now WHERE outbox_event_id=:id AND published_at IS NULL")
                .param("now", java.sql.Timestamp.from(now)).param("id", id).update() == 1;
    }

    public boolean insertPaymentConfirmed(UUID paymentAttemptId, String payload, Instant createdAt) {
        try {
            return jdbcClient.sql("""
                            INSERT INTO public.payment_outbox (
                                outbox_event_id,
                                aggregate_type,
                                aggregate_id,
                                event_type,
                                event_payload,
                                created_at,
                                published_at
                            )
                            VALUES (
                                :outboxEventId,
                                'PaymentAttempt',
                                :aggregateId,
                                'PaymentConfirmed',
                                :eventPayload,
                                :createdAt,
                                NULL
                            )
                            """)
                    .param("outboxEventId", UUID.randomUUID())
                    .param("aggregateId", paymentAttemptId)
                    .param("eventPayload", payload)
                    .param("createdAt", java.sql.Timestamp.from(createdAt))
                    .update() == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }
}
