package com.stylemind.payment.repository;

import com.stylemind.payment.model.PaymentAttemptCreation;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.PaymentExceptionView;
import com.stylemind.payment.model.PaymentMethod;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentAttemptRepository {

    private final JdbcClient jdbcClient;

    public PaymentAttemptRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void lockOrder(UUID orderId) {
        // Transaction-scoped, shared by public and internal creation; no schema/table or cross-service DB access.
        jdbcClient.sql("SELECT pg_advisory_xact_lock(:key)")
                .param("key", orderId.getMostSignificantBits() ^ orderId.getLeastSignificantBits())
                .query((rs, rowNum) -> true).single();
    }

    public int expireStaleOnlineAttempts(Instant now) {
        return jdbcClient.sql("""
                        UPDATE public.payment_attempt
                        SET attempt_status = 'EXPIRED'
                        WHERE payment_method = 'SEPAY_VIETQR'
                          AND attempt_status = 'PENDING'
                          AND expires_at <= :now
                        """)
                .param("now", java.sql.Timestamp.from(now))
                .update();
    }

    public Optional<PaymentAttemptSnapshot> findByOrderAndRequestKey(UUID orderId, String requestKey) {
        return jdbcClient.sql("""
                        SELECT payment_attempt_id, order_id, customer_id, payment_reference, request_key,
                               payment_method, expected_amount_vnd, attempt_status, generated_at, expires_at,
                               qr_payload, transfer_content
                        FROM public.payment_attempt
                        WHERE order_id = :orderId
                          AND request_key = :requestKey
                        """)
                .param("orderId", orderId)
                .param("requestKey", requestKey)
                .query(PaymentAttemptRepository::mapSnapshot)
                .optional();
    }

    public Optional<PaymentAttemptSnapshot> findById(UUID paymentAttemptId) {
        return jdbcClient.sql("""
                        SELECT payment_attempt_id, order_id, customer_id, payment_reference, request_key,
                               payment_method, expected_amount_vnd, attempt_status, generated_at, expires_at,
                               qr_payload, transfer_content
                        FROM public.payment_attempt
                        WHERE payment_attempt_id = :paymentAttemptId
                        """)
                .param("paymentAttemptId", paymentAttemptId)
                .query(PaymentAttemptRepository::mapSnapshot)
                .optional();
    }

    public Optional<PaymentAttemptSnapshot> findByIdForUpdate(UUID paymentAttemptId) {
        return jdbcClient.sql("""
                        SELECT payment_attempt_id, order_id, customer_id, payment_reference, request_key,
                               payment_method, expected_amount_vnd, attempt_status, generated_at, expires_at,
                               qr_payload, transfer_content
                        FROM public.payment_attempt WHERE payment_attempt_id=:id FOR UPDATE
                        """).param("id", paymentAttemptId).query(PaymentAttemptRepository::mapSnapshot).optional();
    }

    public boolean existsPendingOnlineAttempt(UUID orderId) {
        Boolean exists = jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM public.payment_attempt
                            WHERE order_id = :orderId
                              AND payment_method = 'SEPAY_VIETQR'
                              AND attempt_status = 'PENDING'
                        )
                        """)
                .param("orderId", orderId)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(exists);
    }

    public boolean existsSettledAttempt(UUID orderId) {
        Boolean exists = jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM public.payment_attempt
                            WHERE order_id = :orderId
                              AND attempt_status = 'SETTLED'
                        )
                        """)
                .param("orderId", orderId)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(exists);
    }

    public boolean insert(PaymentAttemptCreation attempt) {
        try {
            return jdbcClient.sql("""
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
                            """)
                    .param("paymentAttemptId", attempt.paymentAttemptId())
                    .param("orderId", attempt.orderId())
                    .param("customerId", attempt.customerId())
                    .param("paymentReference", attempt.paymentReference())
                    .param("requestKey", attempt.requestKey())
                    .param("paymentMethod", attempt.paymentMethod().name())
                    .param("expectedAmountVnd", attempt.expectedAmountVnd())
                    .param("generatedAt", java.sql.Timestamp.from(attempt.generatedAt()))
                    .param("expiresAt", attempt.expiresAt() == null ? null : java.sql.Timestamp.from(attempt.expiresAt()),
                            java.sql.Types.TIMESTAMP)
                    .param("providerBankAccountReference", attempt.providerBankAccountReference())
                    .param("qrPayload", attempt.qrPayload())
                    .param("transferContent", attempt.transferContent())
                    .update() == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public boolean insertManualCodTransaction(
            PaymentAttemptSnapshot attempt,
            long collectedAmountVnd,
            String collectionReference,
            UUID recordedByUserId,
            Instant collectedAt) {
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
                                :collectionReference,
                                NULL,
                                NULL,
                                :recordedByUserId,
                                :settledAt
                            )
                            ON CONFLICT DO NOTHING
                            """)
                    .param("transactionId", UUID.randomUUID())
                    .param("paymentAttemptId", attempt.paymentAttemptId())
                    .param("actualAmountVnd", collectedAmountVnd)
                    .param("collectionReference", collectionReference)
                    .param("recordedByUserId", recordedByUserId)
                    .param("settledAt", java.sql.Timestamp.from(collectedAt))
                    .update();
            if (inserted == 1) {
                markSettled(attempt.paymentAttemptId(), collectedAt);
            }
            return inserted == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public void markSettled(UUID paymentAttemptId, Instant confirmedAt) {
        jdbcClient.sql("""
                        UPDATE public.payment_attempt
                        SET attempt_status = 'SETTLED',
                            confirmed_at = :confirmedAt
                        WHERE payment_attempt_id = :paymentAttemptId
                        """)
                .param("paymentAttemptId", paymentAttemptId)
                .param("confirmedAt", java.sql.Timestamp.from(confirmedAt))
                .update();
    }

    public List<PaymentExceptionView> findExceptions(String status, int limit, int offset) {
        String statusFilter = status == null ? null : status.trim();
        return jdbcClient.sql("""
                        SELECT exception_id, payment_attempt_id, provider_name, provider_event_id,
                               received_reference, received_amount_vnd, exception_reason,
                               reconciliation_status, reviewed_by_user_id, detected_at
                        FROM public.payment_exception
                        WHERE (:status IS NULL OR reconciliation_status = :status)
                        ORDER BY detected_at DESC, exception_id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("status", statusFilter, java.sql.Types.VARCHAR)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> new PaymentExceptionView(
                        rs.getObject("exception_id", UUID.class),
                        rs.getObject("payment_attempt_id", UUID.class),
                        rs.getString("provider_name"),
                        rs.getString("provider_event_id"),
                        rs.getString("received_reference"),
                        rs.getLong("received_amount_vnd"),
                        rs.getString("exception_reason"),
                        rs.getString("reconciliation_status"),
                        rs.getObject("reviewed_by_user_id", UUID.class),
                        rs.getTimestamp("detected_at").toInstant()))
                .list();
    }

    private static PaymentAttemptSnapshot mapSnapshot(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        java.sql.Timestamp expiresAt = rs.getTimestamp("expires_at");
        return new PaymentAttemptSnapshot(
                rs.getObject("payment_attempt_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getObject("customer_id", UUID.class),
                rs.getString("payment_reference"),
                rs.getString("request_key"),
                rs.getString("payment_method"),
                rs.getLong("expected_amount_vnd"),
                rs.getString("attempt_status"),
                rs.getTimestamp("generated_at").toInstant(),
                expiresAt == null ? null : expiresAt.toInstant(),
                rs.getString("qr_payload"),
                rs.getString("transfer_content"));
    }
}
