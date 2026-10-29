package com.stylemind.payment.repository;

import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.SepayWebhookExceptionReason;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentReconciliationRepository {

    private final JdbcClient jdbcClient;

    public PaymentReconciliationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<PaymentAttemptSnapshot> findAttemptByPaymentReference(String paymentReference) {
        return jdbcClient.sql("""
                        SELECT payment_attempt_id, order_id, customer_id, payment_reference, request_key,
                               payment_method, expected_amount_vnd, attempt_status, generated_at, expires_at,
                               qr_payload, transfer_content
                        FROM public.payment_attempt
                        WHERE payment_reference = :paymentReference
                        FOR UPDATE
                        """)
                .param("paymentReference", paymentReference)
                .query((rs, rowNum) -> new PaymentAttemptSnapshot(
                        rs.getObject("payment_attempt_id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        rs.getString("payment_reference"),
                        rs.getString("request_key"),
                        rs.getString("payment_method"),
                        rs.getLong("expected_amount_vnd"),
                        rs.getString("attempt_status"),
                        rs.getTimestamp("generated_at").toInstant(),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                        rs.getString("qr_payload"),
                        rs.getString("transfer_content")))
                .optional();
    }

    public boolean isRecordedEvent(String provider, String eventId, String reference, long amount) {
        return jdbcClient.sql("""
                SELECT EXISTS(SELECT 1 FROM public.financial_transaction t
                    JOIN public.payment_attempt a ON a.payment_attempt_id=t.payment_attempt_id
                    WHERE t.provider_name=:provider AND t.provider_event_id=:event
                    AND a.payment_reference=:reference AND t.actual_amount_vnd=:amount)
                """).param("provider", provider).param("event", eventId).param("reference", reference)
                .param("amount", amount).query(Boolean.class).single();
    }

    public boolean insertSettledTransaction(
            UUID paymentAttemptId,
            long actualAmountVnd,
            String providerName,
            String providerEventId,
            String providerTransactionReference,
            Instant settledAt) {
        try {
            int inserted = jdbcClient.sql("""
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
                    .param("transactionId", UUID.randomUUID())
                    .param("paymentAttemptId", paymentAttemptId)
                    .param("actualAmountVnd", actualAmountVnd)
                    .param("providerTransactionReference", providerTransactionReference)
                    .param("providerEventId", providerEventId)
                    .param("providerName", providerName)
                    .param("settledAt", java.sql.Timestamp.from(settledAt))
                    .update();
            if (inserted == 1) {
                markAttemptSettled(paymentAttemptId, settledAt);
            }
            return inserted == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public boolean insertPaymentException(
            UUID paymentAttemptId,
            String providerName,
            String providerEventId,
            String receivedReference,
            long receivedAmountVnd,
            SepayWebhookExceptionReason reason,
            Instant detectedAt) {
        try {
            int inserted = jdbcClient.sql("""
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
                    .param("exceptionId", UUID.randomUUID())
                    .param("paymentAttemptId", paymentAttemptId)
                    .param("providerName", providerName)
                    .param("providerEventId", providerEventId)
                    .param("receivedReference", receivedReference)
                    .param("receivedAmountVnd", receivedAmountVnd)
                    .param("exceptionReason", reason.name())
                    .param("detectedAt", java.sql.Timestamp.from(detectedAt))
                    .update();
            if (inserted == 1 && paymentAttemptId != null) {
                markAttemptRequiresReconciliation(paymentAttemptId);
            }
            return inserted == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private void markAttemptSettled(UUID paymentAttemptId, Instant settledAt) {
        jdbcClient.sql("""
                        UPDATE public.payment_attempt
                        SET attempt_status = 'SETTLED',
                            confirmed_at = :settledAt
                        WHERE payment_attempt_id = :paymentAttemptId
                        """)
                .param("paymentAttemptId", paymentAttemptId)
                .param("settledAt", java.sql.Timestamp.from(settledAt))
                .update();
    }

    private void markAttemptRequiresReconciliation(UUID paymentAttemptId) {
        jdbcClient.sql("""
                        UPDATE public.payment_attempt
                        SET attempt_status = 'REQUIRES_RECONCILIATION'
                        WHERE payment_attempt_id = :paymentAttemptId
                          AND attempt_status <> 'SETTLED'
                        """)
                .param("paymentAttemptId", paymentAttemptId)
                .update();
    }
}
