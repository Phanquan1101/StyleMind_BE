package com.stylemind.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.payment.client.RestCommerceOrderClient;
import com.stylemind.payment.config.CommerceClientProperties;
import com.stylemind.payment.errors.PaymentApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class CommerceOrderSnapshotClientTests {
    @Test
    void sendsDedicatedTokenAndReadsCompletedSnapshotOverHttp() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        AtomicReference<String> token = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/orders/" + orderId + "/payment-snapshot", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-StyleMind-Service-Token"));
            byte[] body = ("{\"order_id\":\"" + orderId + "\",\"customer_id\":\"" + customerId
                    + "\",\"payment_method\":\"COD\",\"accepted_total_vnd\":120000,\"order_status\":\"COMPLETED\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var properties = new CommerceClientProperties("http://127.0.0.1:" + server.getAddress().getPort(), "event-token");
            var client = new RestCommerceOrderClient(properties, RestClient.builder(), "snapshot-token");
            var order = client.getPaymentSnapshot(orderId);
            assertThat(token.get()).isEqualTo("snapshot-token");
            assertThat(order.orderId()).isEqualTo(orderId);
            assertThat(order.customerId()).isEqualTo(customerId);
            assertThat(order.orderStatus()).isEqualTo("COMPLETED");
            assertThatThrownBy(() -> client.getPaymentSnapshot(UUID.randomUUID()))
                    .isInstanceOf(PaymentApiException.class);
        } finally { server.stop(0); }
    }
}
