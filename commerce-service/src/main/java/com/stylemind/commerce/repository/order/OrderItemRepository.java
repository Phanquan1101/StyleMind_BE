package com.stylemind.commerce.repository.order;
import com.stylemind.commerce.entity.OrderItem; import java.util.List; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderItemRepository extends JpaRepository<OrderItem,UUID> { List<OrderItem> findByOrderIdOrderByOrderItemIdAsc(UUID orderId); }
