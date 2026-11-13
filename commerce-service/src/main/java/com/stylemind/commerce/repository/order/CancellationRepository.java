package com.stylemind.commerce.repository.order;
import com.stylemind.commerce.entity.CancellationRequest; import java.util.Optional; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock; import jakarta.persistence.LockModeType;
public interface CancellationRepository extends JpaRepository<CancellationRequest,UUID> {
 boolean existsByOrderId(UUID orderId);
 boolean existsByOrderIdAndRequestStatus(UUID orderId, String requestStatus);
 Optional<CancellationRequest> findFirstByOrderIdOrderByRequestedAtDescCancellationRequestIdDesc(UUID orderId);
 Optional<CancellationRequest> findByOrderIdAndRequestStatus(UUID orderId, String requestStatus);
 @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<CancellationRequest> findLockedByOrderIdAndRequestStatus(UUID orderId, String requestStatus);
 @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<CancellationRequest> findLockedByCancellationRequestId(UUID requestId);
}
