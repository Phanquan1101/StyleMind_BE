package com.stylemind.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.payment.config.SepayPaymentProperties;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.SepayWebhookDecision;
import com.stylemind.payment.model.SepayWebhookExceptionReason;
import com.stylemind.payment.repository.PaymentReconciliationRepository;
import com.stylemind.payment.service.PaymentOutboxService;
import com.stylemind.payment.service.SepayWebhookAuthenticationService;
import com.stylemind.payment.service.impl.SepayWebhookServiceImpl;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;

class SepayContentReferenceTests {
    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static final String REFERENCE = "SM012345ABCDEF";
    private final ObjectMapper mapper = new ObjectMapper();
    private final PaymentReconciliationRepository repository = mock(PaymentReconciliationRepository.class);
    private final PaymentOutboxService outbox = mock(PaymentOutboxService.class);
    private final SepayWebhookAuthenticationService auth = mock(SepayWebhookAuthenticationService.class);

    private SepayWebhookServiceImpl service(String prefix) {
        when(auth.isAuthentic(any(), any(), any())).thenReturn(true);
        return new SepayWebhookServiceImpl(mapper,
                new SepayPaymentProperties("hmac", "synthetic", "", null, "SEPAY", "", "", "", prefix),
                auth, repository, outbox);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankCodeFallsBackToContentAndSettles(String code) throws Exception {
        var service = service("SM");
        var attempt = attempt(REFERENCE, 20000, NOW.plusSeconds(900));
        when(repository.findAttemptByPaymentReference(REFERENCE)).thenReturn(Optional.of(attempt));
        when(repository.insertSettledTransaction(any(), eq(20000L), eq("SEPAY"), eq("101"), any(), eq(NOW)))
                .thenReturn(true);
        assertThat(service.receive(body(code, "156271567364-" + REFERENCE + "-CHUYEN TIEN", 20000),
                new HttpHeaders(), NOW)).isEqualTo(SepayWebhookDecision.SETTLED);
        verify(outbox).recordPaymentConfirmed(attempt, 20000, NOW);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"nothing", "SM012345ABCDE", "SM012345ABCDEFF", "XSM012345ABCDEF",
            "SM012345ABCDEFZ", "SM012345abcdef", "SM012345ABCDEF SM111111111111"})
    void missingMalformedEmbeddedOrAmbiguousContentNeverCredits(String content) throws Exception {
        var service = service("SM");
        service.receive(body(null, content, 20000), new HttpHeaders(), NOW);
        verify(repository).insertPaymentException(isNull(), eq("SEPAY"), eq("101"), isNull(), eq(20000L),
                eq(SepayWebhookExceptionReason.MISSING_PAYMENT_REFERENCE), eq(NOW));
        verify(repository, never()).findAttemptByPaymentReference(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void explicitCodeRemainsAuthoritative() throws Exception {
        var service = service("SM");
        service.receive(body(" UNKNOWN ", REFERENCE, 20000), new HttpHeaders(), NOW);
        verify(repository).findAttemptByPaymentReference("UNKNOWN");
        verify(repository, never()).findAttemptByPaymentReference(REFERENCE);
        verifyNoInteractions(outbox);
    }

    @Test
    void repeatedSameReferenceIsNotAmbiguousAndUnknownReferenceIsNotCredited() throws Exception {
        var service = service("SM");
        service.receive(body(null, REFERENCE + " / " + REFERENCE, 20000), new HttpHeaders(), NOW);
        verify(repository).findAttemptByPaymentReference(REFERENCE);
        verify(repository).insertPaymentException(isNull(), eq("SEPAY"), eq("101"), eq(REFERENCE), eq(20000L),
                eq(SepayWebhookExceptionReason.UNMATCHED_REFERENCE), eq(NOW));
        verifyNoInteractions(outbox);
    }

    @Test
    void configuredPrefixIsLiteral() throws Exception {
        var service = service("S+");
        service.receive(body(null, "S+012345ABCDEF", 20000), new HttpHeaders(), NOW);
        verify(repository).findAttemptByPaymentReference("S+012345ABCDEF");
    }

    @Test
    void contentFallbackPreservesAmountAndExpiryChecks() throws Exception {
        var service = service("SM");
        when(repository.findAttemptByPaymentReference(REFERENCE))
                .thenReturn(Optional.of(attempt(REFERENCE, 30000, NOW.plusSeconds(900))))
                .thenReturn(Optional.of(attempt(REFERENCE, 20000, NOW)));
        service.receive(body(null, REFERENCE, 20000), new HttpHeaders(), NOW);
        service.receive(body(null, REFERENCE, 20000), new HttpHeaders(), NOW);
        verify(repository).insertPaymentException(any(), eq("SEPAY"), eq("101"), eq(REFERENCE), eq(20000L),
                eq(SepayWebhookExceptionReason.AMOUNT_MISMATCH), eq(NOW));
        verify(repository).insertPaymentException(any(), eq("SEPAY"), eq("101"), eq(REFERENCE), eq(20000L),
                eq(SepayWebhookExceptionReason.LATE_PAYMENT), eq(NOW));
        verifyNoInteractions(outbox);
    }

    private PaymentAttemptSnapshot attempt(String reference, long amount, Instant expiry) {
        return new PaymentAttemptSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), reference,
                "test", "SEPAY_VIETQR", amount, "PENDING", NOW.minusSeconds(60), expiry, null, reference);
    }

    private byte[] body(String code, String content, long amount) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("id", 101); body.put("code", code); body.put("content", content);
        body.put("transferType", "in"); body.put("transferAmount", amount);
        return mapper.writeValueAsBytes(body);
    }
}
