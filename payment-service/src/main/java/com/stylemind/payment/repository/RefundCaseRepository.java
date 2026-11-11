package com.stylemind.payment.repository;

import com.stylemind.payment.dto.RefundSourceType;
import com.stylemind.payment.entity.RefundCaseEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RefundCaseRepository extends JpaRepository<RefundCaseEntity, UUID> {

    Optional<RefundCaseEntity> findBySourceTypeAndSourceId(RefundSourceType sourceType, UUID sourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundCaseEntity r WHERE r.sourceType = :sourceType AND r.sourceId = :sourceId")
    Optional<RefundCaseEntity> lockCase(RefundSourceType sourceType, UUID sourceId);

    // Source uniqueness is also enforced by the database. The lock lets concurrent prepares re-read the winner.
    @Query(value = """
            SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(
                concat('refund-source:', :type, ':', CAST(:sourceId AS text)), 0))
            """, nativeQuery = true)
    int lockSource(String type, UUID sourceId);

    @Query(value = """
            SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(concat('refund-transfer:', :reference), 0))
            """, nativeQuery = true)
    int lockTransferReference(String reference);

    boolean existsByTransferReference(String transferReference);

    @Query("SELECT COALESCE(SUM(r.amountVnd), 0) FROM RefundCaseEntity r WHERE r.transactionId = :transactionId")
    long allocatedAmount(UUID transactionId);
}
