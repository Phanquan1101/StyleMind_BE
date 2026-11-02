package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.commerce.client.payment.OrderPaymentRequest;
import com.stylemind.commerce.client.payment.PaymentClient;
import com.stylemind.commerce.errors.ApiOperationException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class PaymentClientHttpTests {
    final UUID order = UUID.randomUUID();
    final OrderPaymentRequest request = new OrderPaymentRequest(order, UUID.randomUUID(), "COD", 125000,
            "PLACED", "initial-" + order);

    @Test
    void postsTrustedSnapshotAndServiceHeaderWithoutBearerAndDoesNotRetryFailures() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        var responseCode = new AtomicInteger(201);
        var received = new AtomicReference<String>();
        var header = new AtomicReference<String>();
        var bearer = new AtomicReference<String>();
        server.createContext("/internal/v1/payments/order-attempts", exchange -> {
            calls.incrementAndGet();
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            header.set(exchange.getRequestHeaders().getFirst("X-StyleMind-Service-Token"));
            bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = responseCode.get() == 201 ? """
                    {"id":"%s","orderId":"%s","method":"COD","amountVnd":125000,
                     "reference":"SMTEST","status":"PENDING"}
                    """.formatted(UUID.randomUUID(), order) : "private-upstream-body";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseCode.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new PaymentClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-only-service-token");
            assertThat(client.create(request).orderId()).isEqualTo(order);
            assertThat(new ObjectMapper().readTree(received.get()).get("accepted_total_vnd").longValue()).isEqualTo(125000);
            assertThat(header.get()).isEqualTo("test-only-service-token");
            assertThat(bearer.get()).isNull();
            for (int code : new int[] {409, 500, 503, 200}) {
                responseCode.set(code);
                int before = calls.get();
                assertThatThrownBy(() -> client.create(request)).isInstanceOf(ApiOperationException.class)
                        .hasMessage(code == 409 ? "PAYMENT_ATTEMPT_CONFLICT" : "PAYMENT_DELIVERY_UNKNOWN");
                assertThat(calls.get()).isEqualTo(before + 1);
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void acceptedPostWithLostResponseTimesOutWithoutAutomaticReplay() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        var release = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/internal/v1/payments/order-attempts", exchange -> {
            exchange.getRequestBody().readAllBytes();
            calls.incrementAndGet();
            try {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var client = new PaymentClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(), "test");
            assertThatThrownBy(() -> client.create(request)).isInstanceOf(ApiOperationException.class)
                    .hasMessage("PAYMENT_DELIVERY_UNKNOWN");
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            release.countDown();
            server.stop(0);
            executor.close();
        }
    }

    @Test
    void rejectsInvalidSuccessfulResponsesAndDoesNotFollowRedirectWithCredential() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var responseCode = new AtomicInteger(201);
        var responseBody = new AtomicReference<>("{}");
        var redirectedCalls = new AtomicInteger();
        server.createContext("/internal/v1/payments/order-attempts", exchange -> {
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Location", "/untrusted");
            exchange.sendResponseHeaders(responseCode.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/untrusted", exchange -> {
            redirectedCalls.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new PaymentClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(), "test");
            for (String body : java.util.List.of("{}", "invalid-json", """
                    {"id":"%s","orderId":"%s","method":"COD","amountVnd":1,
                     "reference":"SMTEST","status":"PENDING"}
                    """.formatted(UUID.randomUUID(), order))) {
                responseBody.set(body);
                assertThatThrownBy(() -> client.create(request)).isInstanceOf(ApiOperationException.class)
                        .hasMessage("PAYMENT_DELIVERY_UNKNOWN");
            }
            responseCode.set(302);
            assertThatThrownBy(() -> client.create(request)).isInstanceOf(ApiOperationException.class)
                    .hasMessage("PAYMENT_DELIVERY_UNKNOWN");
            assertThat(redirectedCalls.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unconfiguredClientFailsBeforeNetworkAndConnectionFailureIsSafe() throws Exception {
        assertThatThrownBy(() -> new PaymentClient(RestClient.builder(), "", "").create(request))
                .isInstanceOf(ApiOperationException.class).hasMessage("PAYMENT_UNAVAILABLE");
        var socket = new java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress());
        int port = socket.getLocalPort();
        socket.close();
        assertThatThrownBy(() -> new PaymentClient(RestClient.builder(), "http://127.0.0.1:" + port, "test")
                .create(request)).isInstanceOf(ApiOperationException.class).hasMessage("PAYMENT_DELIVERY_UNKNOWN");
    }
}
