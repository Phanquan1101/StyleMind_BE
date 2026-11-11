package com.stylemind.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.payment.dto.RefundRequests.Destination;
import com.stylemind.payment.errors.PaymentApiException;
import com.stylemind.payment.security.RefundDestinationCipher;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RefundDestinationCipherTests {
    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final Destination DESTINATION = new Destination("Synthetic Bank", "000112233", "Test Owner");

    @Test
    void refusesPlaintextWritesWhenTheKeyIsMissingMalformedOrWrongLength() {
        for (String invalid : new String[] {"", "not-base64!", "dG9vLXNob3J0"}) {
            var cipher = new RefundDestinationCipher(new ObjectMapper(), invalid);
            assertUnavailable(() -> cipher.encrypt(UUID.randomUUID(), DESTINATION));
        }
    }

    @Test
    void ciphertextRequiresTheSameCaseAndRejectsTampering() {
        var cipher = new RefundDestinationCipher(new ObjectMapper(), KEY);
        UUID refundId = UUID.randomUUID();
        byte[] ciphertext = cipher.encrypt(refundId, DESTINATION);
        assertThat(cipher.decrypt(refundId, ciphertext)).isEqualTo(DESTINATION);
        assertUnavailable(() -> cipher.decrypt(UUID.randomUUID(), ciphertext));
        ciphertext[ciphertext.length - 1] ^= 1;
        assertUnavailable(() -> cipher.decrypt(refundId, ciphertext));
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(PaymentApiException.class,
                failure -> assertThat(failure.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
