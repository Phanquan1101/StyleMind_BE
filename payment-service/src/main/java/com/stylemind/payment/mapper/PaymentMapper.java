package com.stylemind.payment.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.payment.dto.PaymentAttemptResponse;
import com.stylemind.payment.dto.PaymentExceptionListResponse;
import com.stylemind.payment.dto.PaymentExceptionResponse;
import com.stylemind.payment.model.PaymentAttemptCreation;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.PaymentExceptionView;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    private final ObjectMapper objectMapper;

    public PaymentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PaymentAttemptResponse toResponse(PaymentAttemptCreation attempt) {
        return new PaymentAttemptResponse(attempt.paymentAttemptId(), attempt.orderId(), attempt.paymentMethod().name(),
                attempt.expectedAmountVnd(), attempt.paymentReference(), toApiStatus("PENDING"), attempt.expiresAt(),
                attempt.transferContent(), attempt.qrPayload());
    }

    public PaymentAttemptResponse toResponse(PaymentAttemptSnapshot attempt) {
        return toResponse(attempt, attempt.attemptStatus());
    }

    public PaymentAttemptResponse toResponse(PaymentAttemptSnapshot attempt, String status) {
        return new PaymentAttemptResponse(attempt.paymentAttemptId(), attempt.orderId(), attempt.paymentMethod(),
                attempt.expectedAmountVnd(), attempt.paymentReference(), toApiStatus(status), attempt.expiresAt(),
                attempt.transferContent(), attempt.qrPayload());
    }

    public PaymentExceptionResponse toResponse(PaymentExceptionView view) {
        return new PaymentExceptionResponse(view.exceptionId(), view.paymentAttemptId(), view.providerName(),
                view.providerEventId(), view.receivedReference(), view.receivedAmountVnd(), view.exceptionReason(),
                view.reconciliationStatus(), view.reviewedByUserId(), view.detectedAt());
    }

    public PaymentExceptionListResponse toExceptionListResponse(List<PaymentExceptionView> items, int limit, int offset) {
        return new PaymentExceptionListResponse(items.stream().map(this::toResponse).toList(), limit, offset);
    }

    public String toPaymentConfirmedPayload(PaymentAttemptSnapshot attempt, long amountVnd, Instant confirmedAt)
            throws JsonProcessingException {
        var payload = objectMapper.createObjectNode();
        payload.put("payment_attempt_id", attempt.paymentAttemptId().toString());
        payload.put("order_id", attempt.orderId().toString());
        if (attempt.customerId() == null) payload.putNull("customer_id");
        else payload.put("customer_id", attempt.customerId().toString());
        payload.put("payment_reference", attempt.paymentReference());
        payload.put("payment_method", attempt.paymentMethod());
        payload.put("amount_vnd", amountVnd);
        payload.put("confirmed_at", confirmedAt.toString());
        return objectMapper.writeValueAsString(payload);
    }

    private String toApiStatus(String status) {
        return switch (status) {
            case "SETTLED" -> "PAID";
            case "REQUIRES_RECONCILIATION" -> "EXCEPTION";
            default -> status;
        };
    }
}
