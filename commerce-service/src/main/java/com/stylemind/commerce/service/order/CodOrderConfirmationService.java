package com.stylemind.commerce.service.order;

import com.stylemind.commerce.client.payment.OrderPaymentRequest;
import com.stylemind.commerce.client.payment.PaymentAttemptResponse;

public interface CodOrderConfirmationService {

    void confirm(OrderPaymentRequest request, PaymentAttemptResponse attempt);
}
