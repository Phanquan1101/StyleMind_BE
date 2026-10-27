package com.stylemind.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.stylemind.payment.client.CommerceOrderClient;
import com.stylemind.payment.client.CommerceOrderPaymentSnapshot;
import com.stylemind.payment.config.SepayPaymentProperties;
import com.stylemind.payment.dto.OrderPaymentRequest;
import com.stylemind.payment.errors.PaymentApiException;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import com.stylemind.payment.model.PaymentMethod;
import com.stylemind.payment.repository.PaymentAttemptRepository;
import com.stylemind.payment.service.PaymentAttemptService;
import com.stylemind.payment.service.SepayQrPayloadBuilder;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentOrderAttemptServiceTests {
    final PaymentAttemptRepository repository = mock(PaymentAttemptRepository.class);
    final CommerceOrderClient commerce = mock(CommerceOrderClient.class);
    final SepayPaymentProperties properties = new SepayPaymentProperties("TESTBANK", "000-test-only", null, "SM");
    final PaymentAttemptService service = new PaymentAttemptService(repository,
            new SepayQrPayloadBuilder(properties), properties, commerce);
    final UUID order = UUID.randomUUID();
    final UUID owner = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-21T00:00:00Z");

    @Test
    void pushedSnapshotCreatesBothMethodsWithoutCallingCommerceLookup() {
        when(repository.insert(any())).thenReturn(true);
        for (PaymentMethod method : PaymentMethod.values()) {
            var response = service.createOrderAttempt(request(method, 125000), now);
            assertThat(response.amountVnd()).isEqualTo(125000);
            assertThat(response.method()).isEqualTo(method.name());
            if (method == PaymentMethod.COD) {
                assertThat(response.expiresAt()).isNull();
                assertThat(response.qrPayload()).isNull();
            } else {
                assertThat(response.expiresAt()).isEqualTo(now.plusSeconds(900));
                assertThat(response.qrPayload()).contains("amount=125000");
            }
        }
        verify(repository, times(2)).lockOrder(order);
        verifyNoInteractions(commerce);
    }

    @Test
    void matchingReplayReturnsSameAttemptAndDoesNotInsert() {
        var existing = snapshot(owner, 125000, "COD");
        when(repository.findByOrderAndRequestKey(order, "initial-" + order)).thenReturn(Optional.of(existing));
        var response = service.createOrderAttempt(request(PaymentMethod.COD, 125000), now);
        assertThat(response.id()).isEqualTo(existing.paymentAttemptId());
        var sequence = inOrder(repository);
        sequence.verify(repository).lockOrder(order);
        sequence.verify(repository).expireStaleOnlineAttempts(now);
        sequence.verify(repository).findByOrderAndRequestKey(order, "initial-" + order);
        verify(repository, never()).insert(any());
        verifyNoInteractions(commerce);
    }

    @Test
    void mismatchedCustomerAmountOrMethodReturnsConflictWithoutInsert() {
        for (var existing : java.util.List.of(snapshot(UUID.randomUUID(), 125000, "COD"),
                snapshot(owner, 1, "COD"), snapshot(owner, 125000, "SEPAY_VIETQR"))) {
            when(repository.findByOrderAndRequestKey(order, "initial-" + order)).thenReturn(Optional.of(existing));
            assertThatThrownBy(() -> service.createOrderAttempt(request(PaymentMethod.COD, 125000), now))
                    .isInstanceOfSatisfying(PaymentApiException.class,
                            e -> assertThat(e.code()).isEqualTo("DUPLICATE_PAYMENT_REQUEST_MISMATCH"));
        }
        verify(repository, never()).insert(any());
    }

    private OrderPaymentRequest request(PaymentMethod method, long amount) {
        return new OrderPaymentRequest(new CommerceOrderPaymentSnapshot(order, owner, method, amount,
                method == PaymentMethod.COD ? "PLACED" : "PENDING_PAYMENT"), "initial-" + order);
    }
    private PaymentAttemptSnapshot snapshot(UUID customer, long amount, String method) {
        return new PaymentAttemptSnapshot(UUID.randomUUID(), order, customer, "SMTEST", "initial-" + order,
                method, amount, "PENDING", now, null, null, null);
    }
}
