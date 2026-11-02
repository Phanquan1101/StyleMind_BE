package com.stylemind.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.payment.config.SepayPaymentProperties;
import com.stylemind.payment.service.SepayQrPayloadBuilder;
import com.stylemind.payment.service.SepayWebhookAuthenticationService;
import com.stylemind.payment.service.impl.SepayWebhookAuthenticationServiceImpl;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class SepayPaymentSupportTests {

    @Test
    void hmacWebhookAuthenticationAcceptsValidSignature() throws Exception {
        SepayWebhookAuthenticationService service = new SepayWebhookAuthenticationServiceImpl(properties("hmac"));
        byte[] body = "{\"id\":92704,\"transferAmount\":5000000}".getBytes(StandardCharsets.UTF_8);
        long timestamp = 1_800_000_000L;
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-SePay-Timestamp", String.valueOf(timestamp));
        headers.add("X-SePay-Signature", "sha256=" + hmac("test-secret", timestamp + "." + new String(body)));

        assertThat(service.isAuthentic(body, headers, Instant.ofEpochSecond(timestamp + 120))).isTrue();
    }

    @Test
    void hmacWebhookAuthenticationRejectsExpiredTimestamp() throws Exception {
        SepayWebhookAuthenticationService service = new SepayWebhookAuthenticationServiceImpl(properties("hmac"));
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        long timestamp = 1_800_000_000L;
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-SePay-Timestamp", String.valueOf(timestamp));
        headers.add("X-SePay-Signature", "sha256=" + hmac("test-secret", timestamp + ".{}"));

        assertThat(service.isAuthentic(body, headers, Instant.ofEpochSecond(timestamp + 301))).isFalse();
    }

    @Test
    void apiKeyWebhookAuthenticationUsesAuthorizationHeader() {
        SepayWebhookAuthenticationService service = new SepayWebhookAuthenticationServiceImpl(properties("api-key"));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Apikey test-api-key");

        assertThat(service.isAuthentic("{}".getBytes(StandardCharsets.UTF_8), headers, Instant.now())).isTrue();
    }

    @Test
    void qrPayloadBuilderRequiresConfiguredBankAccount() {
        SepayQrPayloadBuilder builder = new SepayQrPayloadBuilder(new SepayPaymentProperties(
                "hmac", "test-secret", "test-api-key", Duration.ofMinutes(5), "SEPAY", "", "", "", "SM"));

        assertThatThrownBy(() -> builder.buildQrImageUrl(100_000, "SMABC123"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void qrPayloadBuilderBuildsVietQrImageUrl() {
        SepayQrPayloadBuilder builder = new SepayQrPayloadBuilder(new SepayPaymentProperties(
                "hmac", "test-secret", "test-api-key", Duration.ofMinutes(5),
                "SEPAY", "MBBank", "0932442476", "NGUYEN LY VI", "SM"));

        assertThat(builder.buildTransferContent("SMABC123")).isEqualTo("SMABC123");
        assertThat(builder.buildQrImageUrl(100_000, "SMABC123"))
                .isEqualTo("https://vietqr.app/img?acc=0932442476&bank=MBBank&amount=100000&des=SMABC123");
    }

    private SepayPaymentProperties properties(String mode) {
        return new SepayPaymentProperties(
                mode, "test-secret", "test-api-key", Duration.ofMinutes(5),
                "SEPAY", "MBBank", "0932442476", "NGUYEN LY VI", "SM");
    }

    private String hmac(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
