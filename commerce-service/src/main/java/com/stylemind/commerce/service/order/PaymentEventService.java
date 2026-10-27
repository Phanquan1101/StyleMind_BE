package com.stylemind.commerce.service.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.commerce.dto.order.PaymentConfirmedEvent;
import com.stylemind.commerce.dto.order.PaymentEventReceipt;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.repository.order.PaymentEventRepository;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventService {
    private final PaymentEventRepository repository;
    private final ObjectMapper mapper;
    public PaymentEventService(PaymentEventRepository repository, ObjectMapper mapper) {
        this.repository = repository; this.mapper = mapper;
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
        String reason = order.isEmpty() ? "ORDER_NOT_FOUND"
                : !event.customer_id().equals(order.get().customer()) ? "OWNER_MISMATCH"
                : !event.payment_method().equals(order.get().method()) ? "METHOD_MISMATCH"
                : event.amount_vnd() != order.get().amount() ? "AMOUNT_MISMATCH" : null;
        String outcome = "RECONCILIATION_REQUIRED";
        if (reason == null) {
            var applied = repository.sameAppliedPayment(event);
            if (applied.isPresent()) {
                outcome = applied.get() ? "DUPLICATE" : "RECONCILIATION_REQUIRED";
                reason = applied.get() ? "ALREADY_APPLIED" : "SECOND_PAYMENT";
            } else if (!"PENDING_PAYMENT".equals(order.get().status())) {
                reason = "ORDER_NOT_PAYABLE";
            } else {
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
