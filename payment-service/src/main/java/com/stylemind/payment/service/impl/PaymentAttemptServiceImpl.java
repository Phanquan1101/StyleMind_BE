package com.stylemind.payment.service.impl;

import com.stylemind.payment.client.CommerceOrderClient;
import com.stylemind.payment.client.CommerceOrderPaymentSnapshot;
import com.stylemind.payment.config.SepayPaymentProperties;
import com.stylemind.payment.dto.ConfirmCodCollectionRequest;
import com.stylemind.payment.dto.CreatePaymentAttemptRequest;
import com.stylemind.payment.dto.PaymentAttemptResponse;
import com.stylemind.payment.dto.PaymentExceptionListResponse;
import com.stylemind.payment.errors.PaymentApiException;
import com.stylemind.payment.mapper.PaymentMapper;
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
    private final PaymentMapper mapper;

    public PaymentAttemptServiceImpl(
            PaymentAttemptRepository repository,
            PaymentOutboxService outboxService,
            SepayQrPayloadBuilder qrPayloadBuilder,
            SepayPaymentProperties sepayProperties,
            CommerceOrderClient commerceOrderClient,
            PaymentMapper mapper) {
        this.repository = repository;
        this.outboxService = outboxService;
        this.qrPayloadBuilder = qrPayloadBuilder;
        this.sepayProperties = sepayProperties;
        this.commerceOrderClient = commerceOrderClient;
        this.mapper = mapper;
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
        return mapper.toResponse(attempt, PaymentAttemptStatus.SETTLED.name());
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
        return mapper.toExceptionListResponse(items, limit, offset);
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
        return mapper.toResponse(creation);
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
        return mapper.toResponse(existing);
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

    private String normalizeBlankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
