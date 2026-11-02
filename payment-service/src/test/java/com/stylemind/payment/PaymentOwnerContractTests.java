package com.stylemind.payment;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stylemind.payment.dto.OrderPaymentRequest;
import com.stylemind.payment.errors.PaymentApiException;
import com.stylemind.payment.mapper.PaymentMapper;
import com.stylemind.payment.model.PaymentAttemptSnapshot;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentOwnerContractTests {
    final ObjectMapper json = new ObjectMapper();

    @Test void internalContractAcceptsExplicitNullButRejectsMissingOrMalformedOwner() {
        var body = body();
        assertThat(OrderPaymentRequest.parse(body).order().customerId()).isNull();
        body.remove("customer_id");
        assertThatThrownBy(() -> OrderPaymentRequest.parse(body)).isInstanceOf(PaymentApiException.class);
        body.put("customer_id", "null");
        assertThatThrownBy(() -> OrderPaymentRequest.parse(body)).isInstanceOf(PaymentApiException.class);
        body.put("customer_id", UUID.randomUUID().toString());
        assertThat(OrderPaymentRequest.parse(body).order().customerId()).isNotNull();
    }

    @Test void legacyOutboxPayloadStillPreservesNullOwnerAndExactWholeVnd() throws Exception {
        var now = Instant.now();
        var attempt = new PaymentAttemptSnapshot(UUID.randomUUID(), UUID.randomUUID(), null, "GUESTREF", "initial",
                "COD", 125000, "PENDING", now, null, null, null);
        var payload = json.readTree(new PaymentMapper(json).toPaymentConfirmedPayload(attempt, 125000, now));
        assertThat(payload.has("customer_id")).isTrue();
        assertThat(payload.get("customer_id").isNull()).isTrue();
        assertThat(payload.get("amount_vnd").isIntegralNumber()).isTrue();
        assertThat(payload.get("amount_vnd").longValue()).isEqualTo(125000);
        assertThat(payload.size()).isEqualTo(7);
    }

    private ObjectNode body() {
        UUID order = UUID.randomUUID();
        return json.createObjectNode().put("order_id", order.toString()).putNull("customer_id")
                .put("payment_method", "COD").put("accepted_total_vnd", 125000)
                .put("order_status", "PENDING_PAYMENT").put("request_key", "initial-" + order);
    }
}
