package com.stylemind.payment.repository;

import com.stylemind.payment.entity.FinancialTransactionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RefundLedgerRepository extends JpaRepository<FinancialTransactionEntity, UUID> {

    @Query(value = """
            SELECT transaction.* FROM public.financial_transaction transaction
            JOIN public.payment_attempt attempt ON attempt.payment_attempt_id = transaction.payment_attempt_id
            WHERE transaction.payment_attempt_id = :attemptId
              AND attempt.order_id = :orderId AND attempt.customer_id = :customerId
              AND attempt.attempt_status = 'SETTLED'
            FOR UPDATE OF transaction
            """, nativeQuery = true)
    Optional<FinancialTransactionEntity> lockSettledTransaction(UUID attemptId, UUID orderId, UUID customerId);
}
