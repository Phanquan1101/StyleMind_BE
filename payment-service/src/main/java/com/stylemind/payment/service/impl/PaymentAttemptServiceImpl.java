package com.stylemind.payment.service.impl;

import com.stylemind.payment.client.CommerceOrderClient;
import com.stylemind.payment.client.CommerceOrderPaymentSnapshot;
import com.stylemind.payment.config.SepayPaymentProperties;
import com.stylemind.payment.dto.ConfirmCodCollectionRequest;
import com.stylemind.payment.dto.CreatePaymentAttemptRequest;
import com.stylemind.payment.dto.PaymentAttemptResponse;
import com.stylemind.payment.dto.PaymentExceptionListResponse;
import com.stylemind.payment.dto.PaymentExceptionResponse;
import com.stylemind.payment.errors.PaymentApiException;
import com.stylemind.payment.model.PaymentAttemptCreation;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.PaymentAttemptStatus;
import com.stylemind.payment.model.PaymentExceptionView;
import com.stylemind.payment.model.PaymentMethod;
import com.stylemind.payment.repository.PaymentAttemptRepository;
import com.stylemind.payment.service.PaymentAttemptService;
import com.stylemind.payment.service.PaymentOutboxService;
import com.stylemind.payment.service.SepayQrPayloadBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PaymentAttemptServiceImpl implements PaymentAttemptService {

    private static final Duration ONLINE_ATTEMPT_TTL = Duration.ofMinutes(15);
    private static final int MAX_REQUEST_KEY_LENGTH = 100;
    private static final int MAX_LIMIT = 100;

    private final PaymentAttemptRepository repository;
    private final PaymentOutboxService outboxService;
    private final SepayQrPayloadBuilder qrPayloadBuilder;
    private final SepayPaymentProperties sepayProperties;
    private final CommerceOrderClient commerceOrderClient;

    public PaymentAttemptServiceImpl(
            PaymentAttemptRepository repository,
            PaymentOutboxService outboxService,
            SepayQrPayloadBuilder qrPayloadBuilder,
            SepayPaymentProperties sepayProperties,
            CommerceOrderClient commerceOrderClient) {
        this.repository = repository;
        this.outboxService = outboxService;
        this.qrPayloadBuilder = qrPayloadBuilder;
        this.sepayProperties = sepayProperties;
        this.commerceOrderClient = commerceOrderClient;
    }

    @Transactional
    @Override
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
    @Override
    public PaymentAttemptResponse createOrderAttempt(com.stylemind.payment.dto.OrderPaymentRequest request, Instant now) {
        CommerceOrderPaymentSnapshot order = request.order();
        repository.lockOrder(order.orderId());
        repository.expireStaleOnlineAttempts(now);
        return repository.findByOrderAndRequestKey(order.orderId(), request.requestKey())
                .map(existing -> existingAttemptResponse(existing, order, order.customerId()))
                .orElseGet(() -> createNewAttempt(order, request.requestKey(), now));
    }

    @Transactional
    @Override
    public PaymentAttemptResponse confirmCodCollection(
            ConfirmCodCollectionRequest request,
            UUID adminUserId,
            Instant now) {
        validateCodCollectionRequest(request);
        PaymentAttemptSnapshot attempt = repository.findByIdForUpdate(request.paymentAttemptId())
                .orElseThrow(() -> new PaymentApiException(
                        HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Payment attempt was not found"));

        validateCollectionAttempt(request, attempt);
        Instant collectedAt = request.collectedAt() == null ? now : request.collectedAt();
        if (collectedAt.isAfter(now)) {
            throw new PaymentApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Collection cannot be recorded in the future");
        }
        boolean inserted = repository.insertManualCodTransaction(attempt, request.collectedAmountVnd(),
                normalizeBlankToNull(request.collectionReference()), adminUserId, collectedAt);
        if (!inserted) {
            throw new PaymentApiException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_CONFIRMED",
                    "Payment attempt already has a financial transaction");
        }
        outboxService.recordPaymentConfirmed(attempt, request.collectedAmountVnd(), collectedAt);
        return responseForSnapshot(attempt, PaymentAttemptStatus.SETTLED.name(), null, null, collectedAt);
    }

    private void validateCollectionAttempt(ConfirmCodCollectionRequest request, PaymentAttemptSnapshot attempt) {
        if (!PaymentMethod.COD.name().equals(attempt.paymentMethod())) {
            throw new PaymentApiException(HttpStatus.CONFLICT, "PAYMENT_METHOD_MISMATCH",
                    "Only COD payment attempts can be collected through this API");
        }
        if (!PaymentAttemptStatus.PENDING.name().equals(attempt.attemptStatus())) {
            throw new PaymentApiException(HttpStatus.CONFLICT, "PAYMENT_ATTEMPT_NOT_PENDING",
                    "Payment attempt is not pending");
        }
        if (attempt.expectedAmountVnd() != request.collectedAmountVnd()) {
            throw new PaymentApiException(HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH",
                    "Collected amount must match the expected amount");
        }

    }

    @Override
    public PaymentExceptionListResponse listExceptions(String status, int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT || offset < 0) {
            throw new PaymentApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "Pagination values are outside allowed bounds");
        }
        String filter = normalizeBlankToNull(status);
        if (filter != null && (filter.length() > 100 || filter.codePoints().anyMatch(Character::isISOControl)))
            throw new PaymentApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid exception status");
        List<PaymentExceptionView> items = repository.findExceptions(filter, limit, offset);
        return new PaymentExceptionListResponse(items.stream().map(this::toResponse).toList(), limit, offset);
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

    private void validateCodCollectionRequest(ConfirmCodCollectionRequest request) {
        if (request == null
                || request.paymentAttemptId() == null
                || request.collectedAmountVnd() == null
                || request.collectedAmountVnd() <= 0) {
            throw new PaymentApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "COD collection request is invalid");
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

    private PaymentExceptionResponse toResponse(PaymentExceptionView view) {
        return new PaymentExceptionResponse(
                view.exceptionId(),
                view.paymentAttemptId(),
                view.providerName(),
                view.providerEventId(),
                view.receivedReference(),
                view.receivedAmountVnd(),
                view.exceptionReason(),
                view.reconciliationStatus(),
                view.reviewedByUserId(),
                view.detectedAt());
    }

    private String normalizeBlankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
