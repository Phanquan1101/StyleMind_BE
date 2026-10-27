package com.stylemind.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.stylemind.payment.client.CommerceOrderClient;
import com.stylemind.payment.client.CommerceOrderPaymentSnapshot;
import com.stylemind.payment.config.SepayPaymentProperties;
import com.stylemind.payment.dto.CreatePaymentAttemptRequest;
import com.stylemind.payment.errors.PaymentApiException;
import com.stylemind.payment.model.PaymentMethod;
import com.stylemind.payment.repository.PaymentAttemptRepository;
import com.stylemind.payment.service.PaymentAttemptService;
import com.stylemind.payment.service.SepayQrPayloadBuilder;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentCreationServiceTests {
    final PaymentAttemptRepository repository = mock(PaymentAttemptRepository.class);
    final CommerceOrderClient commerce = mock(CommerceOrderClient.class);
    final UUID order = UUID.randomUUID(), owner = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-22T00:00:00Z");
    final SepayPaymentProperties properties = new SepayPaymentProperties("TESTBANK", "000-test-only", null, "SM");
    final PaymentAttemptService payments = new PaymentAttemptService(repository,
            new SepayQrPayloadBuilder(properties), properties, commerce);

    @Test
    void onlineAmountAndFifteenMinuteExpiryComeFromOrder() {
        when(commerce.getPaymentSnapshot(order)).thenReturn(snapshot("PENDING_PAYMENT"));
        when(repository.insert(any())).thenReturn(true);
        var response = payments.createAttempt(request(" initial-test "), owner, now);
        assertThat(response.amountVnd()).isEqualTo(237000);
        assertThat(response.qrPayload()).contains("amount=237000");
        assertThat(response.expiresAt()).isEqualTo(now.plusSeconds(900));
        verify(repository).expireStaleOnlineAttempts(now);
        verify(repository).insert(argThat(row -> row.requestKey().equals("initial-test")));
    }

    @Test
    void terminalOrderSettledOrderAndPendingDuplicateAreRejected() {
        when(commerce.getPaymentSnapshot(order)).thenReturn(snapshot("CANCELLED"));
        assertCode("ORDER_TERMINAL");
        when(commerce.getPaymentSnapshot(order)).thenReturn(snapshot("PENDING_PAYMENT"));
        when(repository.existsSettledAttempt(order)).thenReturn(true);
        assertCode("ORDER_ALREADY_PAID");
        when(repository.existsSettledAttempt(order)).thenReturn(false);
        when(repository.existsPendingOnlineAttempt(order)).thenReturn(true);
        assertCode("PENDING_PAYMENT_ATTEMPT_EXISTS");
        verify(repository, never()).insert(any());
    }

    @Test
    void missingBankConfigurationDoesNotInsertAttempt() {
        when(commerce.getPaymentSnapshot(order)).thenReturn(snapshot("PENDING_PAYMENT"));
        var empty = new SepayPaymentProperties(null, null, null, null);
        var unconfigured = new PaymentAttemptService(repository, new SepayQrPayloadBuilder(empty), empty, commerce);
        assertThatThrownBy(() -> unconfigured.createAttempt(request("config-test"), owner, now))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).insert(any());
    }

    @Test
    void malformedCreationRequestIsRejectedBeforeLookup() {
        assertThatThrownBy(() -> payments.createAttempt(null, owner, now)).isInstanceOf(PaymentApiException.class);
        assertThatThrownBy(() -> payments.createAttempt(request("x".repeat(101)), owner, now))
                .isInstanceOf(PaymentApiException.class);
        verifyNoInteractions(commerce, repository);
    }

    private void assertCode(String code) {
        assertThatThrownBy(() -> payments.createAttempt(request("test-request"), owner, now))
                .isInstanceOfSatisfying(PaymentApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
    private CommerceOrderPaymentSnapshot snapshot(String status) {
        return new CommerceOrderPaymentSnapshot(order, owner, PaymentMethod.SEPAY_VIETQR, 237000, status);
    }
    private CreatePaymentAttemptRequest request(String key) {
        return new CreatePaymentAttemptRequest(order, PaymentMethod.SEPAY_VIETQR, key);
    }
}
