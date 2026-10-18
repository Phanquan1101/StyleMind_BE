package com.stylemind.commerce.repository.order;
import com.stylemind.commerce.entity.Order; import java.util.List; import java.util.Optional; import java.util.UUID; import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock; import jakarta.persistence.LockModeType;
public interface OrderRepository extends JpaRepository<Order,UUID> {
 Optional<Order> findByCustomerIdAndCheckoutKey(UUID customerId,String checkoutKey);
 long countByCustomerIdAndOrderStatus(UUID customerId,String status);
 long countByCustomerId(UUID customerId);
 long countByOrderStatus(String status);
 List<Order> findByCustomerIdAndOrderStatusOrderByPlacedAtDescOrderIdDesc(UUID customerId,String status,Pageable pageable);
 List<Order> findByCustomerIdOrderByPlacedAtDescOrderIdDesc(UUID customerId,Pageable pageable);
 List<Order> findByOrderStatusOrderByPlacedAtDescOrderIdDesc(String status,Pageable pageable);
 List<Order> findAllByOrderByPlacedAtDescOrderIdDesc(Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<Order> findLockedByOrderId(UUID orderId);
}
