package com.stylemind.commerce.repository.order;
import com.stylemind.commerce.entity.CancellationRequest; import java.util.Optional; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface CancellationRepository extends JpaRepository<CancellationRequest,UUID> { Optional<CancellationRequest> findByOrderId(UUID orderId); }
