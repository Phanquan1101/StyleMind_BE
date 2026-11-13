package com.stylemind.payment.repository;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class PaymentOutboxRepository {

    private final EntityManager entityManager;

    public PaymentOutboxRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public record PendingEvent(UUID id, Instant createdAt, UUID attemptId, String reference, long amount, String payload, String method) { }

    public java.util.List<PendingEvent> pendingAfter(PendingEvent cursor, int limit) {
        String after = cursor == null ? "" : " AND (o.createdAt > :created OR (o.createdAt=:created AND o.outboxEventId > :id))";
        var query = entityManager.createQuery("""
                SELECT new com.stylemind.payment.repository.PaymentOutboxRepository$PendingEvent(
                    o.outboxEventId, o.createdAt, a.paymentAttemptId, a.paymentReference,
                    f.actualAmountVnd, o.eventPayload, a.paymentMethod)
                FROM PaymentOutboxEntity o, PaymentAttemptEntity a, FinancialTransactionEntity f
                WHERE a.paymentAttemptId=o.aggregateId AND f.paymentAttemptId=a.paymentAttemptId
                  AND o.publishedAt IS NULL AND o.eventType='PaymentConfirmed'
                  AND a.paymentMethod IN ('SEPAY_VIETQR','VNPAY','COD') AND a.attemptStatus='SETTLED'
                """ + after + " ORDER BY o.createdAt,o.outboxEventId", PendingEvent.class);
        if (cursor != null) query.setParameter("created", cursor.createdAt()).setParameter("id", cursor.id());
        return query.setMaxResults(limit).getResultList();
    }

    public boolean acknowledge(UUID id, Instant now) {
        return executeUpdate(entityManager.createNativeQuery("UPDATE public.payment_outbox SET published_at=:now WHERE outbox_event_id=:id AND published_at IS NULL")
                .setParameter("now", now).setParameter("id", id)) == 1;
    }

    public boolean insertPaymentConfirmed(UUID paymentAttemptId, String payload, Instant createdAt) {
        return executeUpdate(entityManager.createNativeQuery("""
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
                        ON CONFLICT DO NOTHING
                        """)
                .setParameter("outboxEventId", UUID.randomUUID())
                .setParameter("aggregateId", paymentAttemptId)
                .setParameter("eventPayload", payload)
                .setParameter("createdAt", createdAt)) == 1;
    }

    // Native DML bypasses the first-level cache; following reads must see the new database state.
    private int executeUpdate(jakarta.persistence.Query query) {
        int affected = query.executeUpdate();
        entityManager.clear();
        return affected;
    }
}
