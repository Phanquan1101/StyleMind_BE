package com.stylemind.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.payment.client.CommerceOrderClient;
import com.stylemind.payment.client.CommerceOrderPaymentSnapshot;
import com.stylemind.payment.config.SepayPaymentProperties;
import com.stylemind.payment.dto.ConfirmCodCollectionRequest;
import com.stylemind.payment.dto.CreatePaymentAttemptRequest;
import com.stylemind.payment.errors.PaymentApiException;
import com.stylemind.payment.model.PaymentAttemptCreation;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.PaymentMethod;
import com.stylemind.payment.repository.PaymentAttemptRepository;
import com.stylemind.payment.repository.PaymentReconciliationRepository;
import com.stylemind.payment.service.PaymentAttemptService;
import com.stylemind.payment.service.impl.PaymentAttemptServiceImpl;
import com.stylemind.payment.service.PaymentOutboxService;
import com.stylemind.payment.service.SepayQrPayloadBuilder;
import com.stylemind.payment.service.SepayWebhookAuthenticationService;
import com.stylemind.payment.service.impl.SepayWebhookAuthenticationServiceImpl;
import com.stylemind.payment.service.SepayWebhookService;
import com.stylemind.payment.service.impl.SepayWebhookServiceImpl;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class PaymentWorkflowServiceTests {

    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ATTEMPT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void createSepayAttemptUsesOrderAmountInQrAndExpiresAfterFifteenMinutes() {
        PaymentAttemptRepository repository = org.mockito.Mockito.mock(PaymentAttemptRepository.class);
        PaymentOutboxService outbox = org.mockito.Mockito.mock(PaymentOutboxService.class);
        CommerceOrderClient commerce = org.mockito.Mockito.mock(CommerceOrderClient.class);
        PaymentAttemptService service = attemptService(repository, outbox, commerce);
        when(commerce.getPaymentSnapshot(ORDER_ID)).thenReturn(orderSnapshot(PaymentMethod.SEPAY_VIETQR, 250_000, "PENDING"));
        when(repository.findByOrderAndRequestKey(ORDER_ID, "REQ-1")).thenReturn(Optional.empty());
        when(repository.insert(any())).thenReturn(true);

        var response = service.createAttempt(
                new CreatePaymentAttemptRequest(ORDER_ID, PaymentMethod.SEPAY_VIETQR, " REQ-1 "), CUSTOMER_ID, NOW);

        assertThat(response.amountVnd()).isEqualTo(250_000);
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(response.qrPayload()).contains("amount=250000");
        assertThat(response.transferContent()).startsWith("SM");
        verify(repository).expireStaleOnlineAttempts(NOW);
        verify(repository).insert(any(PaymentAttemptCreation.class));
    }

    @Test
    void createAttemptRejectsTerminalOrderAndPendingOnlineDuplicate() {
        PaymentAttemptRepository repository = org.mockito.Mockito.mock(PaymentAttemptRepository.class);
        CommerceOrderClient commerce = org.mockito.Mockito.mock(CommerceOrderClient.class);
        PaymentAttemptService service = attemptService(repository, org.mockito.Mockito.mock(PaymentOutboxService.class), commerce);
        when(commerce.getPaymentSnapshot(ORDER_ID))
                .thenReturn(orderSnapshot(PaymentMethod.SEPAY_VIETQR, 250_000, "CANCELLED"))
                .thenReturn(orderSnapshot(PaymentMethod.SEPAY_VIETQR, 250_000, "PENDING"));

        assertThatThrownBy(() -> service.createAttempt(
                new CreatePaymentAttemptRequest(ORDER_ID, PaymentMethod.SEPAY_VIETQR, "REQ-1"), CUSTOMER_ID, NOW))
                .isInstanceOf(PaymentApiException.class)
                .hasMessage("Cannot create a payment attempt for a terminal order");

        when(repository.findByOrderAndRequestKey(ORDER_ID, "REQ-2")).thenReturn(Optional.empty());
        when(repository.existsPendingOnlineAttempt(ORDER_ID)).thenReturn(true);
        assertThatThrownBy(() -> service.createAttempt(
                new CreatePaymentAttemptRequest(ORDER_ID, PaymentMethod.SEPAY_VIETQR, "REQ-2"), CUSTOMER_ID, NOW))
                .isInstanceOf(PaymentApiException.class)
                .hasMessage("Order already has a pending online payment attempt");
    }

    @Test
    void confirmCodCollectionRecordsOnceAndEmitsOutbox() {
        PaymentAttemptRepository repository = org.mockito.Mockito.mock(PaymentAttemptRepository.class);
        PaymentOutboxService outbox = org.mockito.Mockito.mock(PaymentOutboxService.class);
        CommerceOrderClient commerce = org.mockito.Mockito.mock(CommerceOrderClient.class);
        PaymentAttemptService service = attemptService(repository, outbox, commerce);
        PaymentAttemptSnapshot attempt = snapshot("COD", "PENDING", null, 120_000);
        when(repository.findByIdForUpdate(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(repository.insertManualCodTransaction(attempt, 120_000, "COD-001", ADMIN_ID, NOW)).thenReturn(true);

        var response = service.confirmCodCollection(
                new ConfirmCodCollectionRequest(ATTEMPT_ID, 120_000L, " COD-001 ", NOW), ADMIN_ID, NOW);

        assertThat(response.status()).isEqualTo("PAID");
        verify(outbox).recordPaymentConfirmed(attempt, 120_000, NOW);
    }

    @Test
    void confirmCodCollectionRejectsWrongAmountAndRepeatConfirmation() {
        PaymentAttemptRepository repository = org.mockito.Mockito.mock(PaymentAttemptRepository.class);
        PaymentAttemptService service = attemptService(
                repository,
                org.mockito.Mockito.mock(PaymentOutboxService.class),
                org.mockito.Mockito.mock(CommerceOrderClient.class));
        when(repository.findByIdForUpdate(ATTEMPT_ID)).thenReturn(Optional.of(snapshot("COD", "PENDING", null, 120_000)));

        assertThatThrownBy(() -> service.confirmCodCollection(
                new ConfirmCodCollectionRequest(ATTEMPT_ID, 100_000L, "COD-001", NOW), ADMIN_ID, NOW))
                .isInstanceOf(PaymentApiException.class)
                .hasMessage("Collected amount must match the expected amount");

        when(repository.findByIdForUpdate(ATTEMPT_ID)).thenReturn(Optional.of(snapshot("COD", "SETTLED", null, 120_000)));
        assertThatThrownBy(() -> service.confirmCodCollection(
                new ConfirmCodCollectionRequest(ATTEMPT_ID, 120_000L, "COD-001", NOW), ADMIN_ID, NOW))
                .isInstanceOf(PaymentApiException.class)
                .hasMessage("Payment attempt is not pending");
    }

    @Test
    void sepayWebhookSettlesExactAmountOnceAndEmitsOutbox() {
        PaymentReconciliationRepository repository = org.mockito.Mockito.mock(PaymentReconciliationRepository.class);
        PaymentOutboxService outbox = org.mockito.Mockito.mock(PaymentOutboxService.class);
        SepayWebhookService service = webhookService(repository, outbox);
        PaymentAttemptSnapshot attempt = snapshot("SEPAY_VIETQR", "PENDING", NOW.plusSeconds(900), 500_000);
        when(repository.findAttemptByPaymentReference("SMABC123")).thenReturn(Optional.of(attempt));
        when(repository.insertSettledTransaction(ATTEMPT_ID, 500_000, "SEPAY", "92704", "FT24012345678", NOW))
                .thenReturn(true);

        service.receive(sepayBody(500_000, "SMABC123", NOW), new HttpHeaders(), NOW);

        verify(outbox).recordPaymentConfirmed(attempt, 500_000, NOW);
    }

    @Test
    void sepayWebhookRecordsWrongAmountAndLatePaymentExceptions() {
        PaymentReconciliationRepository repository = org.mockito.Mockito.mock(PaymentReconciliationRepository.class);
        PaymentOutboxService outbox = org.mockito.Mockito.mock(PaymentOutboxService.class);
        SepayWebhookService service = webhookService(repository, outbox);
        when(repository.findAttemptByPaymentReference("SMABC123"))
                .thenReturn(Optional.of(snapshot("SEPAY_VIETQR", "PENDING", NOW.plusSeconds(900), 500_000)))
                .thenReturn(Optional.of(snapshot("SEPAY_VIETQR", "PENDING", NOW.minusSeconds(1), 500_000)));

        service.receive(sepayBody(499_000, "SMABC123", NOW), new HttpHeaders(), NOW);
        service.receive(sepayBody(500_000, "SMABC123", NOW), new HttpHeaders(), NOW);

        verify(repository).insertPaymentException(eq(ATTEMPT_ID), eq("SEPAY"), eq("92704"), eq("SMABC123"),
                eq(499_000L), eq(com.stylemind.payment.model.SepayWebhookExceptionReason.AMOUNT_MISMATCH), eq(NOW));
        verify(repository).insertPaymentException(eq(ATTEMPT_ID), eq("SEPAY"), eq("92704"), eq("SMABC123"),
                eq(500_000L), eq(com.stylemind.payment.model.SepayWebhookExceptionReason.LATE_PAYMENT), eq(NOW));
        verify(outbox, never()).recordPaymentConfirmed(any(), any(Long.class), any());
    }

    private PaymentAttemptService attemptService(
            PaymentAttemptRepository repository,
            PaymentOutboxService outbox,
            CommerceOrderClient commerce) {
        SepayPaymentProperties properties = properties("none");
        return new PaymentAttemptServiceImpl(
                repository,
                outbox,
                new SepayQrPayloadBuilder(properties),
                properties,
                commerce);
    }

    private SepayWebhookService webhookService(PaymentReconciliationRepository repository, PaymentOutboxService outbox) {
        return new SepayWebhookServiceImpl(
                new ObjectMapper(),
                properties("none"),
                new SepayWebhookAuthenticationServiceImpl(properties("none")),
                repository,
                outbox);
    }

    private SepayPaymentProperties properties(String mode) {
        return new SepayPaymentProperties(
                mode, "test-secret", "test-api-key", Duration.ofMinutes(5),
                "SEPAY", "MBBank", "0932442476", "NGUYEN LY VI", "SM");
    }

    private PaymentAttemptSnapshot snapshot(String method, String status, Instant expiresAt, long amount) {
        return new PaymentAttemptSnapshot(
                ATTEMPT_ID,
                ORDER_ID,
                CUSTOMER_ID,
                "SMABC123",
                "REQ-1",
                method,
                amount,
                status,
                NOW.minusSeconds(60),
                expiresAt,
                method.equals("SEPAY_VIETQR") ? "https://vietqr.app/img?amount=" + amount : null,
                method.equals("SEPAY_VIETQR") ? "SMABC123" : null);
    }

    private CommerceOrderPaymentSnapshot orderSnapshot(PaymentMethod method, long amount, String status) {
        return new CommerceOrderPaymentSnapshot(ORDER_ID, CUSTOMER_ID, method, amount, status);
    }

    private byte[] sepayBody(long amount, String code, Instant transactionTime) {
        String transactionDate = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(transactionTime);
        return ("""
                {"id":92704,"gateway":"MBBank","transactionDate":"%s","accountNumber":"0932442476","subAccount":"","code":"%s","content":"%s chuyen tien","transferType":"in","description":"","transferAmount":%d,"accumulated":0,"referenceCode":"FT24012345678"}
                """.formatted(transactionDate, code, code, amount)).getBytes(StandardCharsets.UTF_8);
    }
}
