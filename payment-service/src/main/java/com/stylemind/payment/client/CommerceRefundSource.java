package com.stylemind.payment.client;

import com.stylemind.payment.dto.RefundSourceType;
import java.util.UUID;

public record CommerceRefundSource(RefundSourceType type, UUID id, UUID orderId, UUID customerId,
                                   UUID paymentAttemptId, long amountVnd) {
}
