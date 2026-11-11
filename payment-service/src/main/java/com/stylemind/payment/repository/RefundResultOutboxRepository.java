package com.stylemind.payment.repository;

import com.stylemind.payment.dto.RefundResult;
import com.stylemind.payment.entity.RefundResultOutboxEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RefundResultOutboxRepository extends JpaRepository<RefundResultOutboxEntity, UUID> {

    @Query("""
            SELECT new com.stylemind.payment.dto.RefundResult(
                r.id, r.sourceType, r.sourceId, r.orderId, r.amountVnd, r.refundedAt)
            FROM RefundResultOutboxEntity o JOIN o.refund r
            WHERE o.publishedAt IS NULL AND o.nextAttemptAt <= :now
            ORDER BY o.nextAttemptAt, o.refundId
            """)
    List<RefundResult> due(Instant now, Pageable page);

    @Modifying
    @Transactional
    @Query("""
            UPDATE RefundResultOutboxEntity o SET o.publishedAt = :now
            WHERE o.refundId = :refundId AND o.publishedAt IS NULL
            """)
    int markPublished(UUID refundId, Instant now);

    @Modifying
    @Transactional
    @Query("""
            UPDATE RefundResultOutboxEntity o SET o.nextAttemptAt = :retryAt
            WHERE o.refundId = :refundId AND o.publishedAt IS NULL AND o.nextAttemptAt <= :checkedAt
            """)
    int retry(UUID refundId, Instant checkedAt, Instant retryAt);
}
