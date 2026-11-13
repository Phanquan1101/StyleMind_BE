package com.stylemind.payment.service.impl;

import com.stylemind.payment.dto.VnpayIpnResponse;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.SepayWebhookExceptionReason;
import com.stylemind.payment.repository.PaymentReconciliationRepository;
import com.stylemind.payment.service.PaymentOutboxService;
import com.stylemind.payment.service.VnpayIpnService;
import com.stylemind.payment.service.VnpayPaymentUrlBuilder;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VnpayIpnServiceImpl implements VnpayIpnService {
    private static final String PROVIDER = "VNPAY";
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter PAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final VnpayPaymentUrlBuilder signatures;
    private final com.stylemind.payment.config.VnpayPaymentProperties properties;
    private final PaymentReconciliationRepository repository;
    private final PaymentOutboxService outbox;

    public VnpayIpnServiceImpl(VnpayPaymentUrlBuilder signatures,
            com.stylemind.payment.config.VnpayPaymentProperties properties,
            PaymentReconciliationRepository repository, PaymentOutboxService outbox) {
        this.signatures = signatures;
        this.properties = properties;
        this.repository = repository;
        this.outbox = outbox;
    }

    @Transactional
    @Override
    public VnpayIpnResponse receive(Map<String, String> parameters, Instant receivedAt) {
        if (!signatures.verify(parameters) || !properties.tmnCode().equals(parameters.get("vnp_TmnCode")))
            return response("97", "Invalid signature");

        String reference = parameters.get("vnp_TxnRef");
        String eventId = parameters.get("vnp_TransactionNo");
        long rawAmount;
        try {
            rawAmount = Long.parseLong(parameters.getOrDefault("vnp_Amount", ""));
        } catch (NumberFormatException invalidAmount) {
            return response("04", "Invalid amount");
        }
        if (rawAmount <= 0 || rawAmount % 100 != 0 || eventId == null || eventId.isBlank()
                || reference == null || reference.isBlank()) return response("99", "Invalid request");
        long amount = rawAmount / 100;

        // A declined/cancelled payment is acknowledged but never settles the local attempt.
        if (!"00".equals(parameters.get("vnp_ResponseCode"))
                || !"00".equals(parameters.get("vnp_TransactionStatus"))) return response("00", "Confirm Success");

        Optional<PaymentAttemptSnapshot> found = repository.findAttemptByPaymentReference(reference);
        if (found.isEmpty()) {
            repository.insertPaymentException(null, PROVIDER, eventId, reference, amount,
                    SepayWebhookExceptionReason.UNMATCHED_REFERENCE, receivedAt);
            return response("01", "Order not found");
        }
        PaymentAttemptSnapshot attempt = found.get();
        if (repository.isRecordedEvent(PROVIDER, eventId, reference, amount)) return response("00", "Confirm Success");
        if (!"VNPAY".equals(attempt.paymentMethod())) {
            record(attempt.paymentAttemptId(), eventId, reference, amount,
                    SepayWebhookExceptionReason.PAYMENT_METHOD_MISMATCH, receivedAt);
            return response("00", "Confirm Success");
        }
        Instant settledAt = payDate(parameters.get("vnp_PayDate")).orElse(receivedAt);
        if ("EXPIRED".equals(attempt.attemptStatus())) {
            record(attempt.paymentAttemptId(), eventId, reference, amount,
                    SepayWebhookExceptionReason.LATE_PAYMENT, receivedAt);
            return response("00", "Confirm Success");
        }
        if (!"PENDING".equals(attempt.attemptStatus())) {
            record(attempt.paymentAttemptId(), eventId, reference, amount,
                    SepayWebhookExceptionReason.DUPLICATE_OR_ALREADY_SETTLED, receivedAt);
            return response("00", "Confirm Success");
        }
        if (attempt.expiresAt() != null && !settledAt.isBefore(attempt.expiresAt())) {
            record(attempt.paymentAttemptId(), eventId, reference, amount,
                    SepayWebhookExceptionReason.LATE_PAYMENT, receivedAt);
            return response("00", "Confirm Success");
        }
        if (attempt.expectedAmountVnd() != amount) {
            record(attempt.paymentAttemptId(), eventId, reference, amount,
                    SepayWebhookExceptionReason.AMOUNT_MISMATCH, receivedAt);
            return response("00", "Confirm Success");
        }
        if (!repository.insertSettledTransaction(attempt.paymentAttemptId(), amount, PROVIDER, eventId,
                parameters.get("vnp_BankTranNo"), settledAt)) {
            record(attempt.paymentAttemptId(), eventId, reference, amount,
                    SepayWebhookExceptionReason.DUPLICATE_OR_ALREADY_SETTLED, receivedAt);
            return response("00", "Confirm Success");
        }
        outbox.recordPaymentConfirmed(attempt, amount, settledAt);
        return response("00", "Confirm Success");
    }

    private void record(UUID attemptId, String eventId, String reference, long amount,
            SepayWebhookExceptionReason reason, Instant at) {
        repository.insertPaymentException(attemptId, PROVIDER, eventId, reference, amount, reason, at);
    }

    private Optional<Instant> payDate(String value) {
        if (value == null) return Optional.empty();
        try { return Optional.of(LocalDateTime.parse(value, PAY_DATE).atZone(VIETNAM).toInstant()); }
        catch (RuntimeException invalidDate) { return Optional.empty(); }
    }

    private VnpayIpnResponse response(String code, String message) { return new VnpayIpnResponse(code, message); }
}
