package com.stylemind.payment.repository;

import com.stylemind.payment.entity.PaymentAttemptEntity;
import com.stylemind.payment.entity.PaymentExceptionEntity;
import com.stylemind.payment.model.PaymentAttemptCreation;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.PaymentExceptionView;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class PaymentAttemptRepository {

    private final EntityManager entityManager;

    public PaymentAttemptRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void lockOrder(UUID orderId) {
        // Transaction-scoped, shared by public and internal creation; no schema/table or cross-service DB access.
        entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(:key)")
                .setParameter("key", orderId.getMostSignificantBits() ^ orderId.getLeastSignificantBits())
                .getSingleResult();
    }

    public int expireStaleOnlineAttempts(Instant now) {
        return executeUpdate(entityManager.createNativeQuery("""
                        UPDATE public.payment_attempt
                        SET attempt_status = 'EXPIRED'
                        WHERE payment_method IN ('SEPAY_VIETQR', 'VNPAY')
                          AND attempt_status = 'PENDING'
                          AND expires_at <= :now
                        """)
                .setParameter("now", now));
    }

    public Optional<PaymentAttemptSnapshot> findByOrderAndRequestKey(UUID orderId, String requestKey) {
        return entityManager.createQuery("SELECT a FROM PaymentAttemptEntity a WHERE a.orderId=:orderId AND a.requestKey=:key", PaymentAttemptEntity.class)
                .setParameter("orderId", orderId).setParameter("key", requestKey)
                .getResultList().stream().map(PaymentAttemptEntity::snapshot).findFirst();
    }

    public Optional<PaymentAttemptSnapshot> findById(UUID id) {
        return Optional.ofNullable(entityManager.find(PaymentAttemptEntity.class, id)).map(PaymentAttemptEntity::snapshot);
    }

    public Optional<PaymentAttemptSnapshot> findCodForOrder(UUID orderId) {
        return entityManager.createQuery("SELECT a FROM PaymentAttemptEntity a WHERE a.orderId=:id AND a.paymentMethod='COD' AND a.attemptStatus IN ('PENDING','SETTLED') ORDER BY CASE WHEN a.attemptStatus='SETTLED' THEN 0 ELSE 1 END, a.generatedAt, a.paymentAttemptId", PaymentAttemptEntity.class)
                .setParameter("id", orderId).setMaxResults(1).getResultList().stream()
                .map(PaymentAttemptEntity::snapshot).findFirst();
    }

    public Optional<PaymentAttemptSnapshot> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(entityManager.find(PaymentAttemptEntity.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE))
                .map(PaymentAttemptEntity::snapshot);
    }

    public boolean existsPendingOnlineAttempt(UUID orderId) {
        return entityManager.createQuery("SELECT count(a) FROM PaymentAttemptEntity a WHERE a.orderId=:id AND a.paymentMethod IN ('SEPAY_VIETQR','VNPAY') AND a.attemptStatus='PENDING'", Long.class)
                .setParameter("id", orderId).getSingleResult() > 0;
    }

    public boolean existsSettledAttempt(UUID orderId) {
        return entityManager.createQuery("SELECT count(a) FROM PaymentAttemptEntity a WHERE a.orderId=:id AND a.attemptStatus='SETTLED'", Long.class)
                .setParameter("id", orderId).getSingleResult() > 0;
    }

    public boolean hasCancelledVnpayAttempt(UUID orderId) {
        return entityManager.createQuery("SELECT count(a) FROM PaymentAttemptEntity a WHERE a.orderId=:id AND a.paymentMethod='VNPAY' AND a.attemptStatus='CANCELLED'", Long.class)
                .setParameter("id", orderId).getSingleResult() > 0;
    }

    public boolean cancelUnpaidVnpayAttempts(UUID orderId) {
        // The IPN also locks the attempt row before settling it. A concurrent settlement
        // therefore finishes first or sees CANCELLED and enters reconciliation.
        executeUpdate(entityManager.createNativeQuery("""
                UPDATE public.payment_attempt SET attempt_status='CANCELLED'
                WHERE order_id=:id AND payment_method='VNPAY' AND attempt_status='PENDING'
                """).setParameter("id", orderId));
        Number total = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM public.payment_attempt WHERE order_id=:id AND payment_method='VNPAY'
                """).setParameter("id", orderId).getSingleResult();
        Number unsafe = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM public.payment_attempt a
                WHERE a.order_id=:id AND a.payment_method='VNPAY'
                  AND (a.attempt_status NOT IN ('CANCELLED','EXPIRED')
                       OR EXISTS (SELECT 1 FROM public.financial_transaction t WHERE t.payment_attempt_id=a.payment_attempt_id))
                """).setParameter("id", orderId).getSingleResult();
        return total.longValue() > 0 && unsafe.longValue() == 0;
    }

    public boolean insert(PaymentAttemptCreation attempt) {
        return executeUpdate(entityManager.createNativeQuery("""
                        INSERT INTO public.payment_attempt (
                            payment_attempt_id,
                            order_id,
                            customer_id,
                            payment_reference,
                            request_key,
                            payment_method,
                            expected_amount_vnd,
                            attempt_status,
                            generated_at,
                            expires_at,
                            provider_order_reference,
                            provider_bank_account_reference,
                            provider_virtual_account,
                            qr_payload,
                            transfer_content,
                            confirmed_at
                        )
                        VALUES (
                            :paymentAttemptId,
                            :orderId,
                            :customerId,
                            :paymentReference,
                            :requestKey,
                            :paymentMethod,
                            :expectedAmountVnd,
                            'PENDING',
                            :generatedAt,
                            :expiresAt,
                            NULL,
                            :providerBankAccountReference,
                            NULL,
                            :qrPayload,
                            :transferContent,
                            NULL
                        )
                        ON CONFLICT DO NOTHING
                        """)
                .setParameter("paymentAttemptId", attempt.paymentAttemptId())
                .setParameter("orderId", attempt.orderId())
                .setParameter("customerId", attempt.customerId())
                .setParameter("paymentReference", attempt.paymentReference())
                .setParameter("requestKey", attempt.requestKey())
                .setParameter("paymentMethod", attempt.paymentMethod().name())
                .setParameter("expectedAmountVnd", attempt.expectedAmountVnd())
                .setParameter("generatedAt", attempt.generatedAt())
                .setParameter("expiresAt", attempt.expiresAt())
                .setParameter("providerBankAccountReference", attempt.providerBankAccountReference())
                .setParameter("qrPayload", attempt.qrPayload())
                .setParameter("transferContent", attempt.transferContent())) == 1;
    }

    public boolean insertManualCodTransaction(
            PaymentAttemptSnapshot attempt,
            long collectedAmountVnd,
            String collectionReference,
            UUID recordedByUserId,
            Instant collectedAt) {
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
                            :collectionReference,
                            NULL,
                            NULL,
                            :recordedByUserId,
                            :settledAt
                        )
                        ON CONFLICT DO NOTHING
                        """)
                .setParameter("transactionId", UUID.randomUUID())
                .setParameter("paymentAttemptId", attempt.paymentAttemptId())
                .setParameter("actualAmountVnd", collectedAmountVnd)
                .setParameter("collectionReference", collectionReference)
                .setParameter("recordedByUserId", recordedByUserId)
                .setParameter("settledAt", collectedAt));
        if (inserted == 1) {
            markSettled(attempt.paymentAttemptId(), collectedAt);
        }
        return inserted == 1;
    }

    public void markSettled(UUID paymentAttemptId, Instant confirmedAt) {
        executeUpdate(entityManager.createNativeQuery("""
                        UPDATE public.payment_attempt
                        SET attempt_status = 'SETTLED',
                            confirmed_at = :confirmedAt
                        WHERE payment_attempt_id = :paymentAttemptId
                        """)
                .setParameter("paymentAttemptId", paymentAttemptId)
                .setParameter("confirmedAt", confirmedAt));
    }

    public List<PaymentExceptionView> findExceptions(String status, int limit, int offset) {
        String filter = status == null ? null : status.trim();
        String where = filter == null ? "" : " WHERE e.reconciliationStatus=:status";
        var query = entityManager.createQuery("SELECT e FROM PaymentExceptionEntity e" + where
                + " ORDER BY e.detectedAt DESC, e.exceptionId DESC", PaymentExceptionEntity.class);
        if (filter != null) query.setParameter("status", filter);
        return query.setFirstResult(offset).setMaxResults(limit).getResultList().stream().map(PaymentExceptionEntity::view).toList();
    }

    // Native DML bypasses the first-level cache; following reads must see the new database state.
    private int executeUpdate(jakarta.persistence.Query query) {
        int affected = query.executeUpdate();
        entityManager.clear();
        return affected;
    }
}
