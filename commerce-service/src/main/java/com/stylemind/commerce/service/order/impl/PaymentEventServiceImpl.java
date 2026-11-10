package com.stylemind.commerce.service.order.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.commerce.dto.order.PaymentConfirmedEvent;
import com.stylemind.commerce.dto.order.PaymentEventReceipt;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.repository.order.PaymentEventRepository;
import com.stylemind.commerce.repository.inventory.InventoryRepository;
import com.stylemind.commerce.service.order.PaymentEventService;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventServiceImpl implements PaymentEventService {
    private final PaymentEventRepository repository;
    private final ObjectMapper mapper;
    private final InventoryRepository inventory;
    private final com.stylemind.commerce.service.order.QrCheckoutReservationService qrCheckouts;
    private final com.stylemind.commerce.client.payment.PaymentClient paymentClient;
    public PaymentEventServiceImpl(PaymentEventRepository repository, ObjectMapper mapper,
            InventoryRepository inventory, com.stylemind.commerce.service.order.QrCheckoutReservationService qrCheckouts,
            com.stylemind.commerce.client.payment.PaymentClient paymentClient) {
        this.repository = repository; this.mapper = mapper; this.inventory = inventory;
        this.qrCheckouts = qrCheckouts; this.paymentClient = paymentClient;
    }
    @Transactional
    public PaymentEventReceipt receive(PaymentConfirmedEvent event, Instant now) {
        String hash = fingerprint(event);
        repository.lockEvent(event.event_id());
        var prior = repository.received(event.event_id());
        if (prior.isPresent()) {
            if (!hash.equals(prior.get().fingerprint())) throw new ApiOperationException(HttpStatus.CONFLICT,
                    "PAYMENT_EVENT_CONTENT_CONFLICT", "Event ID already has different content");
            return prior.get().receipt();
        }
        var order = repository.lockOrder(event.order_id());
        if (order.isEmpty()) {
            var checkout = repository.lockCheckout(event.order_id());
            if (checkout.isPresent()) {
                var value = checkout.get();
                if (value.attemptId() == null || value.reference() == null) {
                    var recovered = paymentClient.create(new com.stylemind.commerce.client.payment.OrderPaymentRequest(
                            event.order_id(), value.customer(), "SEPAY_VIETQR", value.amount(),
                            "PENDING_PAYMENT", "initial-" + event.order_id()));
                    qrCheckouts.recordPaymentAttempt(event.order_id(), value.customer(), value.guestHash(), recovered,
                            OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
                    value = repository.lockCheckout(event.order_id()).orElseThrow();
                }
                String checkoutReason = !java.util.Objects.equals(event.customer_id(), value.customer()) ? "OWNER_MISMATCH"
                        : !"SEPAY_VIETQR".equals(event.payment_method()) ? "METHOD_MISMATCH"
                        : event.amount_vnd() != value.amount() ? "AMOUNT_MISMATCH"
                        : !event.payment_attempt_id().toString().equals(value.attemptId())
                                || !event.payment_reference().equals(value.reference()) ? "PAYMENT_REFERENCE_MISMATCH"
                        : "CHECKOUT_RECONCILIATION_REQUIRED";
                if ("CHECKOUT_RECONCILIATION_REQUIRED".equals(checkoutReason)
                        && java.util.Set.of("PREPARING", "AWAITING_PAYMENT").contains(value.status())
                        && qrCheckouts.createPaidOrder(event.order_id(), OffsetDateTime.ofInstant(now, ZoneOffset.UTC))) {
                    var receipt = new PaymentEventReceipt(event.event_id(), "APPLIED", "PAYMENT_CONFIRMED");
                    repository.record(event, hash, receipt, now);
                    return receipt;
                }
                if ("ORDER_CREATED".equals(value.status()) && "CHECKOUT_RECONCILIATION_REQUIRED".equals(checkoutReason)) {
                    throw new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "CHECKOUT_PAYMENT_PROCESSING_UNAVAILABLE",
                            "Order was created concurrently; retry the event");
                }
                if ("CHECKOUT_RECONCILIATION_REQUIRED".equals(checkoutReason)) {
                    repository.markCheckoutForReconciliation(event.order_id(), now);
                }
                var receipt = new PaymentEventReceipt(event.event_id(), "RECONCILIATION_REQUIRED", checkoutReason);
                repository.record(event, hash, receipt, now);
                return receipt;
            }
        }
        String reason = order.isEmpty() ? "ORDER_NOT_FOUND"
                : !java.util.Objects.equals(event.customer_id(), order.get().customer()) ? "OWNER_MISMATCH"
                : !event.payment_method().equals(order.get().method()) ? "METHOD_MISMATCH"
                : event.amount_vnd() != order.get().amount() ? "AMOUNT_MISMATCH" : null;
        String outcome = "RECONCILIATION_REQUIRED";
        if (reason == null) {
            var applied = repository.sameAppliedPayment(event);
            if (applied.isPresent()) {
                outcome = applied.get() ? "DUPLICATE" : "RECONCILIATION_REQUIRED";
                reason = applied.get() ? "ALREADY_APPLIED" : "SECOND_PAYMENT";
            } else if ("COD".equals(event.payment_method())) {
                if (java.util.Set.of("PLACED", "PROCESSING", "SHIPPED", "COMPLETED").contains(order.get().status())) {
                    outcome = "APPLIED"; reason = "COD_COLLECTION_CONFIRMED";
                    // The durable inbox records cash receipt; fulfilment status/history must not change.
                } else reason = "ORDER_NOT_PAYABLE";
            } else if (!"PENDING_PAYMENT".equals(order.get().status())) {
                reason = "ORDER_NOT_PAYABLE";
            } else {
                inventory.consumeSepay(event.order_id(), OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
                repository.paid(event, now);
                outcome = "APPLIED"; reason = "PAYMENT_CONFIRMED";
            }
        }
        var receipt = new PaymentEventReceipt(event.event_id(), outcome, reason);
        repository.record(event, hash, receipt, now);
        return receipt;
    }
    private String fingerprint(PaymentConfirmedEvent event) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(event))); }
        catch (Exception failure) { throw new IllegalStateException("Cannot fingerprint payment event"); }
    }
}
