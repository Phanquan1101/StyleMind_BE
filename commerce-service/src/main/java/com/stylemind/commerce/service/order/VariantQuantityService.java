package com.stylemind.commerce.service.order;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface VariantQuantityService {
    record LockedOrder(String paymentMethod, String orderStatus) { }
    boolean allocate(Map<UUID, Integer> requested);
    void restoreCancelledOrder(UUID orderId);
    List<UUID> expiredPendingSepayOrderIds(OffsetDateTime now);
    Optional<LockedOrder> lockOrder(UUID orderId);
    boolean cancelExpiredOrder(UUID orderId, OffsetDateTime now);
}
