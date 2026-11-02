package com.stylemind.payment.client;

import java.util.UUID;

public interface CommerceOrderClient {

    CommerceOrderPaymentSnapshot getPaymentSnapshot(UUID orderId);
}
