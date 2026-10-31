package com.stylemind.commerce.service.order;

import com.stylemind.commerce.client.payment.OrderPaymentRequest;

public interface OrderPaymentHandoff {

    void afterOrderCommitted(OrderPaymentRequest order);
}
