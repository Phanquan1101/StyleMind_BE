package com.stylemind.commerce.repository.order;
import com.stylemind.commerce.entity.OrderAddressSnapshot; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderAddressRepository extends JpaRepository<OrderAddressSnapshot,UUID> { }
