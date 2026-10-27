package com.stylemind.commerce.service.order;

import com.stylemind.commerce.client.payment.OrderPaymentRequest;
import com.stylemind.commerce.client.payment.PaymentAttemptResponse;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.repository.order.CodOrderConfirmationRepository;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodOrderConfirmationService {
    private final CodOrderConfirmationRepository repository;
    public CodOrderConfirmationService(CodOrderConfirmationRepository repository) { this.repository = repository; }

    @Transactional
    public void confirm(OrderPaymentRequest request, PaymentAttemptResponse attempt) {
        if (attempt == null || attempt.id() == null || !request.orderId().equals(attempt.orderId())
                || !"COD".equals(request.paymentMethod()) || !"COD".equals(attempt.method())
                || request.acceptedTotalVnd() != attempt.amountVnd() || attempt.status() == null || !Set.of("PENDING", "PAID").contains(attempt.status()))
            throw new ApiOperationException(HttpStatus.BAD_GATEWAY, "PAYMENT_DELIVERY_UNKNOWN", "COD payment attempt was not confirmed");
        var order = repository.lock(request.orderId()).orElseThrow(() -> conflict());
        if (!request.customerId().equals(order.customer()) || !"COD".equals(order.method())
                || request.acceptedTotalVnd() != order.amount()) throw conflict();
        if ("PENDING_PAYMENT".equals(order.status())) repository.confirm(request.orderId(), Instant.now());
        else if (!Set.of("PLACED", "PROCESSING", "SHIPPED", "COMPLETED").contains(order.status())) throw conflict();
        // Confirmation concerns fulfilment readiness only. A financial transaction is never written here.
    }
    private ApiOperationException conflict() {
        return new ApiOperationException(HttpStatus.CONFLICT, "COD_ORDER_NOT_CONFIRMABLE", "COD order is not confirmable");
    }
}
