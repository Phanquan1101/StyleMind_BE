package com.stylemind.commerce.service.order.impl;

import com.stylemind.commerce.entity.OrderStatusHistory;
import com.stylemind.commerce.entity.ProductVariant;
import com.stylemind.commerce.repository.order.OrderHistoryRepository;
import com.stylemind.commerce.repository.order.OrderItemRepository;
import com.stylemind.commerce.repository.order.OrderRepository;
import com.stylemind.commerce.repository.product.ProductVariantRepository;
import com.stylemind.commerce.service.order.VariantQuantityService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class VariantQuantityServiceImpl implements VariantQuantityService {
    private final ProductVariantRepository variants;
    private final OrderItemRepository orderItems;
    private final OrderRepository orders;
    private final OrderHistoryRepository history;

    public VariantQuantityServiceImpl(ProductVariantRepository variants, OrderItemRepository orderItems,
            OrderRepository orders, OrderHistoryRepository history) {
        this.variants = variants; this.orderItems = orderItems; this.orders = orders; this.history = history;
    }
    @Override public boolean allocate(Map<UUID, Integer> requested) {
        List<ProductVariant> locked = variants.findLockedByVariantIdIn(requested.keySet());
        if (locked.size() != requested.size() || locked.stream().anyMatch(v -> requested.get(v.getVariantId()) > v.getQuantity())) return false;
        locked.forEach(v -> v.setQuantity(v.getQuantity() - requested.get(v.getVariantId()))); variants.saveAllAndFlush(locked); return true;
    }
    @Override public void restoreCancelledOrder(UUID orderId) {
        Map<UUID, Integer> quantities = orderItems.findLockedByOrderId(orderId).stream().collect(java.util.stream.Collectors.toMap(i -> i.getVariantId(), i -> i.getQuantity(), Integer::sum));
        restore(quantities);
    }
    @Override public void restore(Map<UUID, Integer> quantities) {
        if (quantities.isEmpty()) return;
        List<ProductVariant> locked = variants.findLockedByVariantIdIn(quantities.keySet());
        if (locked.size() != quantities.size()) throw new IllegalStateException("Order variant no longer exists");
        locked.forEach(v -> v.setQuantity(Math.addExact(v.getQuantity(), quantities.get(v.getVariantId())))); variants.saveAllAndFlush(locked);
    }
    @Override public List<UUID> expiredPendingSepayOrderIds(OffsetDateTime now) { return orders.findExpiredPendingSepayOrderIds(now.minusMinutes(15), PageRequest.of(0, 100)); }
    @Override public Optional<LockedOrder> lockOrder(UUID orderId) { return orders.findLockedByOrderId(orderId).map(o -> new LockedOrder(o.getPaymentMethod(), o.getOrderStatus())); }
    @Override public boolean cancelExpiredOrder(UUID orderId, OffsetDateTime now) {
        return orders.findLockedByOrderId(orderId).filter(o -> "PENDING_PAYMENT".equals(o.getOrderStatus())
                && java.util.Set.of("SEPAY_VIETQR", "VNPAY").contains(o.getPaymentMethod())).map(o -> {
            o.setOrderStatus("CANCELLED"); orders.saveAndFlush(o); OrderStatusHistory entry = new OrderStatusHistory(); entry.setHistoryId(UUID.randomUUID()); entry.setOrderId(orderId); entry.setFromStatus("PENDING_PAYMENT"); entry.setToStatus("CANCELLED"); entry.setChangedAt(now); entry.setReason("Online payment window expired"); history.save(entry); return true;
        }).orElse(false);
    }
}
