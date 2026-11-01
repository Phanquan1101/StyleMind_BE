package com.stylemind.payment.service;

import com.stylemind.payment.dto.ConfirmCodCollectionRequest;
import com.stylemind.payment.dto.CreatePaymentAttemptRequest;
import com.stylemind.payment.dto.PaymentAttemptResponse;
import com.stylemind.payment.dto.PaymentExceptionListResponse;
import java.time.Instant;
import java.util.UUID;

public interface PaymentAttemptService {
    PaymentAttemptResponse createAttempt(CreatePaymentAttemptRequest request, UUID customerId, Instant now);
    PaymentAttemptResponse createOrderAttempt(com.stylemind.payment.dto.OrderPaymentRequest request, Instant now);
    PaymentAttemptResponse confirmCodCollection(
            ConfirmCodCollectionRequest request,
            UUID adminUserId,
            Instant now);
    PaymentExceptionListResponse listExceptions(String status, int limit, int offset);
}
