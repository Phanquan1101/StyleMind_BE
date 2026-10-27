package com.stylemind.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.payment.config.SepayPaymentProperties;
import com.stylemind.payment.service.SepayQrPayloadBuilder;
import org.junit.jupiter.api.Test;

class SepayQrPayloadBuilderTests {

    @Test
    void qrPayloadBuilderRequiresConfiguredBankAccount() {
        SepayQrPayloadBuilder builder = new SepayQrPayloadBuilder(new SepayPaymentProperties("hmac", "test-only-secret", "", null, "SEPAY",
                "", "", "", "SM"));

        assertThatThrownBy(() -> builder.buildQrImageUrl(100_000, "SMABC123"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void qrPayloadBuilderBuildsVietQrImageUrl() {
        SepayQrPayloadBuilder builder = new SepayQrPayloadBuilder(new SepayPaymentProperties("hmac", "test-only-secret", "", null, "SEPAY",
                "TESTBANK", "000-test-only", "TEST FIXTURE", "SM"));

        assertThat(builder.buildTransferContent("SMABC123")).isEqualTo("SMABC123");
        assertThat(builder.buildQrImageUrl(100_000, "SMABC123"))
                .isEqualTo("https://vietqr.app/img?acc=000-test-only&bank=TESTBANK&amount=100000&des=SMABC123");
    }

}
