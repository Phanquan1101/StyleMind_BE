package com.stylemind.payment.service;

import com.stylemind.payment.client.CommerceOrderClient;
import com.stylemind.payment.client.CommerceOrderPaymentSnapshot;
import com.stylemind.payment.config.SepayPaymentProperties;
import com.stylemind.payment.dto.CreatePaymentAttemptRequest;
import com.stylemind.payment.dto.PaymentAttemptResponse;
import com.stylemind.payment.errors.PaymentApiException;
import com.stylemind.payment.model.PaymentAttemptCreation;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.PaymentAttemptStatus;
import com.stylemind.payment.model.PaymentMethod;
import com.stylemind.payment.repository.PaymentAttemptRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PaymentAttemptService {

    private static final Duration ONLINE_ATTEMPT_TTL = Duration.ofMinutes(15);
    private static final int MAX_REQUEST_KEY_LENGTH = 100;

    private final PaymentAttemptRepository repository;
    private final SepayQrPayloadBuilder qrPayloadBuilder;
    private final SepayPaymentProperties sepayProperties;
    private final CommerceOrderClient commerceOrderClient;

    public PaymentAttemptService(
            PaymentAttemptRepository repository,
            SepayQrPayloadBuilder qrPayloadBuilder,
            SepayPaymentProperties sepayProperties,
            CommerceOrderClient commerceOrderClient) {
        this.repository = repository;
        this.qrPayloadBuilder = qrPayloadBuilder;
        this.sepayProperties = sepayProperties;
        this.commerceOrderClient = commerceOrderClient;
    }

    @Transactional
    public PaymentAttemptResponse createAttempt(CreatePaymentAttemptRequest request, UUID customerId, Instant now) {
        validateCreateRequest(request);
        String requestKey = normalizeRequestKey(request, now);
        CommerceOrderPaymentSnapshot order = commerceOrderClient.getPaymentSnapshot(request.orderId());
        validateOrderSnapshot(order, customerId);
        validateRequestedMethod(request, order);
        repository.lockOrder(order.orderId());
        repository.expireStaleOnlineAttempts(now);
        return repository.findByOrderAndRequestKey(request.orderId(), requestKey)
                .map(existing -> existingAttemptResponse(existing, order, customerId))
                .orElseGet(() -> createNewAttempt(order, requestKey, now));
    }

    @Transactional
    public PaymentAttemptResponse createOrderAttempt(com.stylemind.payment.dto.OrderPaymentRequest request, Instant now) {
        CommerceOrderPaymentSnapshot order = request.order();
        repository.lockOrder(order.orderId());
        repository.expireStaleOnlineAttempts(now);
        return repository.findByOrderAndRequestKey(order.orderId(), request.requestKey())
                .map(existing -> existingAttemptResponse(existing, order, order.customerId()))
                .orElseGet(() -> createNewAttempt(order, request.requestKey(), now));
    }

    private PaymentAttemptResponse createNewAttempt(
            CommerceOrderPaymentSnapshot order,
            String requestKey,
            Instant now) {
        if (repository.existsSettledAttempt(order.orderId())) {
            throw new PaymentApiException(HttpStatus.CONFLICT, "ORDER_ALREADY_PAID", "Order already has a settled payment");
        }
        if (order.paymentMethod() == PaymentMethod.SEPAY_VIETQR && repository.existsPendingOnlineAttempt(order.orderId())) {
            throw new PaymentApiException(HttpStatus.CONFLICT, "PENDING_PAYMENT_ATTEMPT_EXISTS",
                    "Order already has a pending online payment attempt");
        }

        UUID paymentAttemptId = UUID.randomUUID();
        String paymentReference = buildPaymentReference(paymentAttemptId);
        Instant expiresAt = order.paymentMethod() == PaymentMethod.SEPAY_VIETQR ? now.plus(ONLINE_ATTEMPT_TTL) : null;
        String transferContent = order.paymentMethod() == PaymentMethod.SEPAY_VIETQR
                ? qrPayloadBuilder.buildTransferContent(paymentReference)
                : null;
        String qrPayload = order.paymentMethod() == PaymentMethod.SEPAY_VIETQR
                ? qrPayloadBuilder.buildQrImageUrl(order.acceptedTotalVnd(), transferContent)
                : null;
        String bankReference = order.paymentMethod() == PaymentMethod.SEPAY_VIETQR
                ? sepayProperties.qrBankName() + ":" + sepayProperties.qrAccountNumber()
                : null;

        PaymentAttemptCreation creation = new PaymentAttemptCreation(
                paymentAttemptId,
                order.orderId(),
                order.customerId(),
                paymentReference,
                requestKey,
                order.paymentMethod(),
                order.acceptedTotalVnd(),
                now,
                expiresAt,
                bankReference,
                qrPayload,
                transferContent);
        if (!repository.insert(creation)) {
            throw new PaymentApiException(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT_REQUEST",
                    "Duplicate payment attempt request");
        }
        return new PaymentAttemptResponse(
                paymentAttemptId,
                order.orderId(),
                order.paymentMethod().name(),
                order.acceptedTotalVnd(),
                paymentReference,
                toApiStatus(PaymentAttemptStatus.PENDING.name()),
                expiresAt,
                transferContent,
                qrPayload);
    }

    private PaymentAttemptResponse existingAttemptResponse(
            PaymentAttemptSnapshot existing,
            CommerceOrderPaymentSnapshot order,
            UUID customerId) {
        if (existing.expectedAmountVnd() != order.acceptedTotalVnd()
                || !existing.customerId().equals(customerId)
                || !order.paymentMethod().name().equals(existing.paymentMethod())) {
            throw new PaymentApiException(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT_REQUEST_MISMATCH",
                    "Duplicate request key does not match the original payment attempt");
        }
        return responseForSnapshot(existing, existing.attemptStatus(), null, null, null);
    }

    private void validateCreateRequest(CreatePaymentAttemptRequest request) {
        if (request == null
                || request.orderId() == null
                || request.method() == null
                || (request.requestKey() != null && request.requestKey().trim().length() > MAX_REQUEST_KEY_LENGTH)) {
            throw new PaymentApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Payment attempt request is invalid");
        }
    }

    private String normalizeRequestKey(CreatePaymentAttemptRequest request, Instant now) {
        if (StringUtils.hasText(request.requestKey())) {
            return request.requestKey().trim();
        }
        return request.method().name() + "-" + now.toEpochMilli();
    }

    private void validateOrderSnapshot(CommerceOrderPaymentSnapshot order, UUID customerId) {
        if (!order.customerId().equals(customerId)) {
            throw new PaymentApiException(HttpStatus.FORBIDDEN, "ORDER_OWNERSHIP_MISMATCH",
                    "Order does not belong to the authenticated customer");
        }
        if (order.acceptedTotalVnd() <= 0 || order.paymentMethod() == null) {
            throw new PaymentApiException(HttpStatus.BAD_GATEWAY, "COMMERCE_ORDER_SNAPSHOT_INVALID",
                    "Commerce order payment snapshot is invalid");
        }
        if (StringUtils.hasText(order.orderStatus())) {
            String status = order.orderStatus().trim().toUpperCase(Locale.ROOT);
            if (status.equals("CANCELLED") || status.equals("COMPLETED") || status.equals("DELIVERED")) {
                throw new PaymentApiException(HttpStatus.CONFLICT, "ORDER_TERMINAL",
                        "Cannot create a payment attempt for a terminal order");
            }
        }
    }

    private void validateRequestedMethod(CreatePaymentAttemptRequest request, CommerceOrderPaymentSnapshot order) {
        if (request.method() != order.paymentMethod()) {
            throw new PaymentApiException(HttpStatus.CONFLICT, "PAYMENT_METHOD_MISMATCH",
                    "Requested payment method does not match the order payment method");
        }
    }

    private String buildPaymentReference(UUID paymentAttemptId) {
        String compact = paymentAttemptId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        return sepayProperties.paymentCodePrefix() + compact;
    }

    private PaymentAttemptResponse responseForSnapshot(
            PaymentAttemptSnapshot attempt,
            String status,
            String qrPayload,
            String transferContent,
            Instant generatedAt) {
        return new PaymentAttemptResponse(
                attempt.paymentAttemptId(),
                attempt.orderId(),
                attempt.paymentMethod(),
                attempt.expectedAmountVnd(),
                attempt.paymentReference(),
                toApiStatus(status),
                attempt.expiresAt(),
                transferContent == null ? attempt.transferContent() : transferContent,
                qrPayload == null ? attempt.qrPayload() : qrPayload);
    }

    private String toApiStatus(String status) {
        return switch (status) {
            case "SETTLED" -> "PAID";
            case "REQUIRES_RECONCILIATION" -> "EXCEPTION";
            default -> status;
        };
    }


}
