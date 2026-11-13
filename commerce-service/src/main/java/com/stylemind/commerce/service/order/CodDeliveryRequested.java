package com.stylemind.commerce.service.order;

import java.util.UUID;

public record CodDeliveryRequested(UUID orderId) {
    public static final String HISTORY_PREFIX = "COD delivery and collection requested";

    public static String historyReason(String reason) {
        return reason == null ? HISTORY_PREFIX : HISTORY_PREFIX + " | " + reason;
    }
}
