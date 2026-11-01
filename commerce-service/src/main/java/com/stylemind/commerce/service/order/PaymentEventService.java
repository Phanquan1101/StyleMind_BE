package com.stylemind.commerce.service.order;

import com.stylemind.commerce.dto.order.PaymentConfirmedEvent;
import com.stylemind.commerce.dto.order.PaymentEventReceipt;
import java.time.Instant;

public interface PaymentEventService {

    PaymentEventReceipt receive(PaymentConfirmedEvent event, Instant now);
}
