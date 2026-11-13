package com.stylemind.payment.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stylemind.payment.model.PaymentMethod;
import java.util.UUID;

public record CommerceOrderPaymentSnapshot(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("customer_id") UUID customerId,
        @JsonProperty("payment_method") PaymentMethod paymentMethod,
        @JsonProperty("accepted_total_vnd") long acceptedTotalVnd,
        @JsonProperty("order_status") String orderStatus,
        @JsonProperty("cod_collection_actor_id") UUID codCollectionActorId,
        @JsonProperty("cod_collection_requested_at") java.time.Instant codCollectionRequestedAt) {
    public CommerceOrderPaymentSnapshot(UUID orderId, UUID customerId, PaymentMethod paymentMethod,
            long acceptedTotalVnd, String orderStatus) {
        this(orderId, customerId, paymentMethod, acceptedTotalVnd, orderStatus, null, null);
    }
}
