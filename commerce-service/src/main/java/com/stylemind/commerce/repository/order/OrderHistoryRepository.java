package com.stylemind.commerce.repository.order;
import com.stylemind.commerce.entity.OrderStatusHistory; import java.util.List; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderHistoryRepository extends JpaRepository<OrderStatusHistory,UUID> { List<OrderStatusHistory> findByOrderIdOrderByChangedAtAscHistoryIdAsc(UUID orderId); }
