package com.stylemind.payment.service.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stylemind.payment.config.SepayPaymentProperties;
import com.stylemind.payment.dto.SepayWebhookRequest;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.SepayWebhookDecision;
import com.stylemind.payment.model.SepayWebhookExceptionReason;
import com.stylemind.payment.repository.PaymentReconciliationRepository;
import com.stylemind.payment.service.PaymentOutboxService;
import com.stylemind.payment.service.SepayWebhookAuthenticationService;
import com.stylemind.payment.service.SepayWebhookService;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SepayWebhookServiceImpl implements SepayWebhookService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter SEPAY_DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT);

    private final ObjectMapper objectMapper;
    private final SepayPaymentProperties properties;
    private final SepayWebhookAuthenticationService authenticationService;
    private final PaymentReconciliationRepository repository;
    private final PaymentOutboxService outboxService;

    public SepayWebhookServiceImpl(
            ObjectMapper objectMapper,
            SepayPaymentProperties properties,
            SepayWebhookAuthenticationService authenticationService,
            PaymentReconciliationRepository repository,
            PaymentOutboxService outboxService) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.authenticationService = authenticationService;
        this.repository = repository;
        this.outboxService = outboxService;
    }

    @Transactional
    @Override
    public SepayWebhookDecision receive(byte[] rawBody, HttpHeaders headers, Instant receivedAt) {
        if (!authenticationService.isAuthentic(rawBody, headers, receivedAt)) {
            throw new SepayWebhookUnauthorizedException();
        }

        SepayWebhookRequest request = parse(rawBody);
        String providerEventId = request.id() == null ? null : String.valueOf(request.id());
        long amount = request.transferAmount() == null ? 0 : request.transferAmount();
        ReferenceResolution resolution = resolveReference(request.code(), request.content());
        String receivedReference = resolution.reference();
        Instant settledAt = parseTransactionDate(request.transactionDate()).orElse(receivedAt);

        if (providerEventId == null || amount < 0) {
            recordException(null, providerEventId, receivedReference, Math.max(amount, 0),
                    SepayWebhookExceptionReason.INVALID_PAYLOAD, receivedAt);
            return SepayWebhookDecision.EXCEPTION_RECORDED;
        }

        if (!"in".equalsIgnoreCase(request.transferType())) {
            return recordException(null, providerEventId, receivedReference, amount,
                    SepayWebhookExceptionReason.UNSUPPORTED_TRANSFER_TYPE, receivedAt);
        }

        if (resolution.ambiguous()) {
            // Several different payment codes in one transfer: never guess which order it is for.
            return recordException(null, providerEventId, null, amount,
                    SepayWebhookExceptionReason.UNMATCHED_REFERENCE, receivedAt);
        }
        if (!StringUtils.hasText(receivedReference)) {
            return recordException(null, providerEventId, null, amount,
                    SepayWebhookExceptionReason.MISSING_PAYMENT_REFERENCE, receivedAt);
        }

        Optional<PaymentAttemptSnapshot> attempt = repository.findAttemptByPaymentReference(receivedReference);
        if (attempt.isEmpty()) {
            return recordException(null, providerEventId, receivedReference, amount,
                    SepayWebhookExceptionReason.UNMATCHED_REFERENCE, receivedAt);
        }

        PaymentAttemptSnapshot snapshot = attempt.get();
        if (repository.isRecordedEvent(properties.providerName(), providerEventId, receivedReference, amount))
            return SepayWebhookDecision.DUPLICATE;
        if (!"SEPAY_VIETQR".equals(snapshot.paymentMethod()))
            return recordException(snapshot.paymentAttemptId(), providerEventId, receivedReference, amount,
                    SepayWebhookExceptionReason.PAYMENT_METHOD_MISMATCH, receivedAt);
        if (snapshot.expiresAt() != null && !settledAt.isBefore(snapshot.expiresAt())) {
            return recordException(snapshot.paymentAttemptId(), providerEventId, receivedReference, amount,
                    SepayWebhookExceptionReason.LATE_PAYMENT, receivedAt);
        }
        if (!"PENDING".equals(snapshot.attemptStatus())) {
            recordException(snapshot.paymentAttemptId(), providerEventId, receivedReference, amount,
                    SepayWebhookExceptionReason.DUPLICATE_OR_ALREADY_SETTLED, receivedAt);
            return SepayWebhookDecision.DUPLICATE;
        }
        if (snapshot.expectedAmountVnd() != amount) {
            return recordException(snapshot.paymentAttemptId(), providerEventId, receivedReference, amount,
                    SepayWebhookExceptionReason.AMOUNT_MISMATCH, receivedAt);
        }

        boolean inserted = repository.insertSettledTransaction(
                snapshot.paymentAttemptId(),
                amount,
                properties.providerName(),
                providerEventId,
                normalizeBlankToNull(request.referenceCode()),
                settledAt);
        if (!inserted) {
            recordException(snapshot.paymentAttemptId(), providerEventId, receivedReference, amount,
                    SepayWebhookExceptionReason.DUPLICATE_OR_ALREADY_SETTLED, receivedAt);
            return SepayWebhookDecision.DUPLICATE;
        }
        outboxService.recordPaymentConfirmed(snapshot, amount, settledAt);
        return SepayWebhookDecision.SETTLED;
    }

    private SepayWebhookRequest parse(byte[] rawBody) {
        try (var parser = objectMapper.getFactory().createParser(rawBody)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(parser);
            if (!(node instanceof ObjectNode) || parser.nextToken() != null) throw new IOException("Invalid webhook object");
            var amount = node.path("transferAmount");
            var id = node.path("id");
            if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() <= 0
                    || !id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0)
                throw new IOException("Invalid webhook amount or event ID");
            return objectMapper.readerFor(SepayWebhookRequest.class)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(node);
        } catch (IOException exception) {
            throw new SepayWebhookMalformedException(exception);
        }
    }

    private SepayWebhookDecision recordException(
            UUID paymentAttemptId,
            String providerEventId,
            String receivedReference,
            long amount,
            SepayWebhookExceptionReason reason,
            Instant detectedAt) {
        persistException(paymentAttemptId, providerEventId, receivedReference, amount, reason, detectedAt);
        return SepayWebhookDecision.EXCEPTION_RECORDED;
    }

    private void persistException(
            UUID paymentAttemptId,
            String providerEventId,
            String receivedReference,
            long amount,
            SepayWebhookExceptionReason reason,
            Instant detectedAt) {
        String eventId = StringUtils.hasText(providerEventId)
                ? providerEventId
                : "invalid-" + UUID.randomUUID();
        repository.insertPaymentException(
                paymentAttemptId,
                properties.providerName(),
                eventId,
                receivedReference,
                amount,
                reason,
                detectedAt);
    }

    private Optional<Instant> parseTransactionDate(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(value, SEPAY_DATE_TIME);
            return Optional.of(localDateTime.atZone(VIETNAM_ZONE).toInstant());
        } catch (RuntimeException exception) {
            throw new SepayWebhookMalformedException(new IllegalArgumentException("Invalid transaction timestamp"));
        }
    }

    /** Outcome of looking for the payment reference: the reference to use, or ambiguous (several different codes). */
    private record ReferenceResolution(String reference, boolean ambiguous) { }

    /**
     * SePay normally fills "code" with the reference it detected in the transfer text. When "code" is empty or is not
     * a valid reference, the reference is read from "content" (prefix + 12 hex characters, case-insensitive, normalised
     * to upper case). Exactly one distinct code is accepted; more than one is ambiguous. A non-empty "code" that is not
     * in our format and has no usable code in "content" is kept as received so it is recorded as UNMATCHED_REFERENCE.
     */
    private ReferenceResolution resolveReference(String code, String content) {
        String prefix = properties.paymentCodePrefix();
        Pattern format = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(prefix) + "[0-9A-Fa-f]{12}(?![0-9A-Fa-f])",
                Pattern.CASE_INSENSITIVE);
        String trimmedCode = normalizeBlankToNull(code);
        if (trimmedCode != null && format.matcher(trimmedCode).matches()) {
            return new ReferenceResolution(trimmedCode.toUpperCase(java.util.Locale.ROOT), false);
        }
        Set<String> found = new LinkedHashSet<>();
        if (StringUtils.hasText(content)) {
            Matcher matcher = format.matcher(content);
            while (matcher.find()) {
                found.add(matcher.group().toUpperCase(java.util.Locale.ROOT));
            }
        }
        if (found.size() == 1) {
            return new ReferenceResolution(found.iterator().next(), false);
        }
        if (found.size() > 1) {
            return new ReferenceResolution(null, true);
        }
        return new ReferenceResolution(trimmedCode, false);
    }

    private String normalizeBlankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
