package com.stylemind.payment.repository;

import com.stylemind.payment.entity.PaymentAttemptEntity;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.SepayWebhookExceptionReason;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class PaymentReconciliationRepository {

    private final EntityManager entityManager;

    public PaymentReconciliationRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<PaymentAttemptSnapshot> findAttemptByPaymentReference(String paymentReference) {
        return entityManager.createQuery("SELECT a FROM PaymentAttemptEntity a WHERE a.paymentReference=:reference", PaymentAttemptEntity.class)
                .setParameter("reference", paymentReference).setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .getResultList().stream().map(PaymentAttemptEntity::snapshot).findFirst();
    }

    public boolean isRecordedEvent(String provider, String eventId, String reference, long amount) {
        return entityManager.createQuery("""
                SELECT count(t) FROM FinancialTransactionEntity t, PaymentAttemptEntity a
                WHERE a.paymentAttemptId=t.paymentAttemptId AND t.providerName=:provider
                  AND t.providerEventId=:event AND a.paymentReference=:reference AND t.actualAmountVnd=:amount
                """, Long.class).setParameter("provider", provider).setParameter("event", eventId)
                .setParameter("reference", reference).setParameter("amount", amount).getSingleResult() > 0;
    }

    public boolean insertSettledTransaction(
            UUID paymentAttemptId,
            long actualAmountVnd,
            String providerName,
            String providerEventId,
            String providerTransactionReference,
            Instant settledAt) {
        int inserted = executeUpdate(entityManager.createNativeQuery("""
                        INSERT INTO public.financial_transaction (
                            transaction_id,
                            payment_attempt_id,
                            actual_amount_vnd,
                            provider_transaction_reference,
                            provider_event_id,
                            provider_name,
                            recorded_by_user_id,
                            settled_at
                        )
                        VALUES (
                            :transactionId,
                            :paymentAttemptId,
                            :actualAmountVnd,
                            :providerTransactionReference,
                            :providerEventId,
                            :providerName,
                            NULL,
                            :settledAt
                        )
                        ON CONFLICT DO NOTHING
                        """)
                .setParameter("transactionId", UUID.randomUUID())
                .setParameter("paymentAttemptId", paymentAttemptId)
                .setParameter("actualAmountVnd", actualAmountVnd)
                .setParameter("providerTransactionReference", providerTransactionReference)
                .setParameter("providerEventId", providerEventId)
                .setParameter("providerName", providerName)
                .setParameter("settledAt", settledAt));
        if (inserted == 1) {
            markAttemptSettled(paymentAttemptId, settledAt);
        }
        return inserted == 1;
    }

    public boolean insertPaymentException(
            UUID paymentAttemptId,
            String providerName,
            String providerEventId,
            String receivedReference,
            long receivedAmountVnd,
            SepayWebhookExceptionReason reason,
            Instant detectedAt) {
        int inserted = executeUpdate(entityManager.createNativeQuery("""
                        INSERT INTO public.payment_exception (
                            exception_id,
                            payment_attempt_id,
                            provider_name,
                            provider_event_id,
                            received_reference,
                            received_amount_vnd,
                            exception_reason,
                            reconciliation_status,
                            reviewed_by_user_id,
                            detected_at
                        )
                        VALUES (
                            :exceptionId,
                            :paymentAttemptId,
                            :providerName,
                            :providerEventId,
                            :receivedReference,
                            :receivedAmountVnd,
                            :exceptionReason,
                            'OPEN',
                            NULL,
                            :detectedAt
                        )
                        ON CONFLICT (provider_name, provider_event_id) DO NOTHING
                        """)
                .setParameter("exceptionId", UUID.randomUUID())
                .setParameter("paymentAttemptId", paymentAttemptId)
                .setParameter("providerName", providerName)
                .setParameter("providerEventId", providerEventId)
                .setParameter("receivedReference", receivedReference)
                .setParameter("receivedAmountVnd", receivedAmountVnd)
                .setParameter("exceptionReason", reason.name())
                .setParameter("detectedAt", detectedAt));
        if (inserted == 1 && paymentAttemptId != null) {
            markAttemptRequiresReconciliation(paymentAttemptId);
        }
        return inserted == 1;
    }

    private void markAttemptSettled(UUID paymentAttemptId, Instant settledAt) {
        executeUpdate(entityManager.createNativeQuery("""
                        UPDATE public.payment_attempt
                        SET attempt_status = 'SETTLED',
                            confirmed_at = :settledAt
                        WHERE payment_attempt_id = :paymentAttemptId
                        """)
                .setParameter("paymentAttemptId", paymentAttemptId)
                .setParameter("settledAt", settledAt));
    }

    private void markAttemptRequiresReconciliation(UUID paymentAttemptId) {
        executeUpdate(entityManager.createNativeQuery("""
                        UPDATE public.payment_attempt
                        SET attempt_status = 'REQUIRES_RECONCILIATION'
                        WHERE payment_attempt_id = :paymentAttemptId
                          AND attempt_status <> 'SETTLED'
                        """)
                .setParameter("paymentAttemptId", paymentAttemptId));
    }

    // Native DML bypasses the first-level cache; following reads must see the new database state.
    private int executeUpdate(jakarta.persistence.Query query) {
        int affected = query.executeUpdate();
        entityManager.clear();
        return affected;
    }
}
