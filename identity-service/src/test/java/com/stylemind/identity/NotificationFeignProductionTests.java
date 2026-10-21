package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.identity.client.notification.NotificationEmailClient;
import com.stylemind.identity.client.notification.NotificationFeignClient;
import com.stylemind.identity.client.notification.payload.EmailDispatch;
import com.stylemind.identity.client.notification.payload.PasswordResetEmailDispatch;
import com.stylemind.identity.errors.EmailDeliveryException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = NotificationFeignProductionTests.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "stylemind.notification.service-token=tttttttttttttttttttttttttttttttt",
            "spring.cloud.openfeign.client.config.notification-service.connect-timeout=250",
            "spring.cloud.openfeign.client.config.notification-service.read-timeout=250",
            "spring.cloud.openfeign.client.config.notification-service.follow-redirects=false"
        })
class NotificationFeignProductionTests {

    private static final String SERVICE_TOKEN = "t".repeat(32);
    private static final String VERIFICATION_CODE = "012345";
    private static final String RESET_CODE = "065432";
    private static final String RECIPIENT = "delivery@example.test";
    private static final String RESET_RECIPIENT = "reset@example.test";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AtomicReferenceHolder SERVERS = new AtomicReferenceHolder();

    @Autowired
    private NotificationEmailClient emailClient;

    @Autowired
    private NotificationFeignClient feignClient;

    @DynamicPropertySource
    static void notificationProperties(DynamicPropertyRegistry registry) {
        registry.add("stylemind.notification.base-url", () -> servers().sourceUrl());
    }

    @BeforeEach
    void clearServerState() {
        servers().reset();
    }

    @AfterAll
    static void stopServers() {
        SERVERS.close();
    }

    @Test
    void productionFeignBeanSendsBothEmailContractsAndOnlyOneAttempt() {
        assertThat(feignClient).isNotNull();
        assertThat(Proxy.isProxyClass(feignClient.getClass())).isTrue();

        servers().respondWith(202);
        emailClient.send(new EmailDispatch(RECIPIENT, VERIFICATION_CODE));
        assertThat(servers().sourceAttempts()).isEqualTo(1);
        assertThat(servers().lastPath()).isEqualTo("/internal/v1/email-verifications");
        assertThat(servers().serviceTokenMatched()).isTrue();
        assertThat(servers().verificationPayloadMatched()).isTrue();

        servers().reset();
        servers().respondWith(202);
        emailClient.sendPasswordReset(new PasswordResetEmailDispatch(RESET_RECIPIENT, RESET_CODE));
        assertThat(servers().sourceAttempts()).isEqualTo(1);
        assertThat(servers().lastPath()).isEqualTo("/internal/v1/password-resets");
        assertThat(servers().serviceTokenMatched()).isTrue();
        assertThat(servers().resetPayloadMatched()).isTrue();
    }

    @Test
    void only202IsSuccessForBothProductionFeignOperations() {
        for (int status : new int[] {200, 204}) {
            servers().reset();
            servers().respondWith(status);
            assertUnknownDelivery(() -> emailClient.send(new EmailDispatch(RECIPIENT, VERIFICATION_CODE)));
            assertThat(servers().sourceAttempts()).isEqualTo(1);

            servers().reset();
            servers().respondWith(status);
            assertUnknownDelivery(() -> emailClient.sendPasswordReset(
                    new PasswordResetEmailDispatch(RESET_RECIPIENT, RESET_CODE)));
            assertThat(servers().sourceAttempts()).isEqualTo(1);
        }
    }

    @Test
    void serviceUnavailableWithRetryAfterMapsToFailureWithoutRetryForBothOperations() {
        servers().respondWith(503);
        assertDeliveryFailure(() -> emailClient.send(new EmailDispatch(RECIPIENT, VERIFICATION_CODE)),
                503, "EMAIL_DELIVERY_FAILED");
        assertThat(servers().sourceAttempts()).isEqualTo(1);

        servers().reset();
        servers().respondWith(503);
        assertDeliveryFailure(() -> emailClient.sendPasswordReset(
                new PasswordResetEmailDispatch(RESET_RECIPIENT, RESET_CODE)),
                503, "EMAIL_DELIVERY_FAILED");
        assertThat(servers().sourceAttempts()).isEqualTo(1);
    }

    @Test
    void timeoutAndClosedConnectionMapToUnknownWithoutRetry() {
        servers().delayResponseBy(900);
        assertUnknownDelivery(() -> emailClient.send(new EmailDispatch(RECIPIENT, VERIFICATION_CODE)));
        assertThat(servers().sourceAttempts()).isEqualTo(1);

        servers().reset();
        servers().closeConnectionWithoutResponse();
        assertUnknownDelivery(() -> emailClient.sendPasswordReset(
                new PasswordResetEmailDispatch(RESET_RECIPIENT, RESET_CODE)));
        assertThat(servers().sourceAttempts()).isEqualTo(1);
    }

    @Test
    void productionFeignDoesNotFollowRedirectsToSameOrDifferentHost() {
        for (int status : new int[] {301, 302, 307, 308}) {
            for (boolean differentHost : new boolean[] {false, true}) {
                servers().reset();
                servers().redirectWith(status, differentHost);
                assertUnknownDelivery(() -> emailClient.send(
                        new EmailDispatch(RECIPIENT, VERIFICATION_CODE)));
                assertThat(servers().sourceAttempts()).isEqualTo(1);
                assertThat(servers().sameHostTargetAttempts()).isZero();
                assertThat(servers().differentHostTargetAttempts()).isZero();

                servers().reset();
                servers().redirectWith(status, differentHost);
                assertUnknownDelivery(() -> emailClient.sendPasswordReset(
                        new PasswordResetEmailDispatch(RESET_RECIPIENT, RESET_CODE)));
                assertThat(servers().sourceAttempts()).isEqualTo(1);
                assertThat(servers().sameHostTargetAttempts()).isZero();
                assertThat(servers().differentHostTargetAttempts()).isZero();
            }
        }
    }

    private void assertUnknownDelivery(Runnable delivery) {
        assertThatThrownBy(delivery::run)
                .isInstanceOfSatisfying(EmailDeliveryException.class, failure -> {
                    assertThat(failure.status().value()).isEqualTo(504);
                    assertThat(failure.code()).isEqualTo("EMAIL_DELIVERY_UNKNOWN");
                });
    }

    private void assertDeliveryFailure(Runnable delivery, int status, String code) {
        assertThatThrownBy(delivery::run)
                .isInstanceOfSatisfying(EmailDeliveryException.class, failure -> {
                    assertThat(failure.status().value()).isEqualTo(status);
                    assertThat(failure.code()).isEqualTo(code);
                });
    }

    private static TestServers servers() {
        return SERVERS.get();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
    @EnableFeignClients(clients = NotificationFeignClient.class)
    @Import(NotificationEmailClient.class)
    static class TestApplication {}

    private static final class AtomicReferenceHolder {
        private volatile TestServers value;

        private TestServers get() {
            TestServers current = value;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                if (value == null) {
                    try {
                        value = new TestServers();
                    } catch (IOException exception) {
                        throw new IllegalStateException("Cannot start local Feign test endpoints", exception);
                    }
                }
                return value;
            }
        }

        private void close() {
            TestServers current = value;
            if (current != null) {
                current.close();
            }
        }
    }

    private static final class TestServers implements AutoCloseable {
        private final HttpServer source;
        private final HttpServer differentHost;
        private final ExecutorService executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "notification-feign-production-test");
            thread.setDaemon(true);
            return thread;
        });
        private final AtomicInteger sourceAttempts = new AtomicInteger();
        private final AtomicInteger sameHostTargetAttempts = new AtomicInteger();
        private final AtomicInteger differentHostTargetAttempts = new AtomicInteger();
        private final AtomicBoolean serviceTokenMatched = new AtomicBoolean();
        private final AtomicBoolean verificationPayloadMatched = new AtomicBoolean();
        private final AtomicBoolean resetPayloadMatched = new AtomicBoolean();
        private final AtomicBoolean closeWithoutResponse = new AtomicBoolean();
        private volatile int status = 202;
        private volatile long delayMillis;
        private volatile String redirectLocation;
        private volatile String lastPath;

        private TestServers() throws IOException {
            source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            differentHost = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            source.setExecutor(executor);
            differentHost.setExecutor(executor);
            source.createContext("/", this::handleSource);
            differentHost.createContext("/redirect-target", this::handleDifferentHostTarget);
            source.start();
            differentHost.start();
        }

        private String sourceUrl() {
            return "http://127.0.0.1:" + source.getAddress().getPort();
        }

        private void reset() {
            sourceAttempts.set(0);
            sameHostTargetAttempts.set(0);
            differentHostTargetAttempts.set(0);
            serviceTokenMatched.set(false);
            verificationPayloadMatched.set(false);
            resetPayloadMatched.set(false);
            closeWithoutResponse.set(false);
            status = 202;
            delayMillis = 0;
            redirectLocation = null;
            lastPath = null;
        }

        private void respondWith(int status) {
            this.status = status;
        }

        private void delayResponseBy(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        private void closeConnectionWithoutResponse() {
            closeWithoutResponse.set(true);
        }

        private void redirectWith(int status, boolean crossHost) {
            this.status = status;
            this.redirectLocation = crossHost
                    ? "http://localhost:" + differentHost.getAddress().getPort() + "/redirect-target"
                    : "/same-host-target";
        }

        private void handleSource(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/same-host-target".equals(path)) {
                sameHostTargetAttempts.incrementAndGet();
                inspectSensitiveHeadersAndBody(exchange, path);
                respond(exchange, 202, null);
                return;
            }
            sourceAttempts.incrementAndGet();
            lastPath = path;
            inspectSensitiveHeadersAndBody(exchange, path);
            if (closeWithoutResponse.get()) {
                exchange.close();
                return;
            }
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    exchange.close();
                    return;
                }
            }
            respond(exchange, status, redirectLocation);
        }

        private void handleDifferentHostTarget(HttpExchange exchange) throws IOException {
            differentHostTargetAttempts.incrementAndGet();
            inspectSensitiveHeadersAndBody(exchange, exchange.getRequestURI().getPath());
            respond(exchange, 202, null);
        }

        private void inspectSensitiveHeadersAndBody(HttpExchange exchange, String path) throws IOException {
            serviceTokenMatched.set(SERVICE_TOKEN.equals(
                    exchange.getRequestHeaders().getFirst(NotificationFeignClient.SERVICE_TOKEN_HEADER)));
            JsonNode json;
            try {
                json = OBJECT_MAPPER.readTree(exchange.getRequestBody().readAllBytes());
            } catch (Exception exception) {
                json = OBJECT_MAPPER.createObjectNode();
            }
            if ("/internal/v1/email-verifications".equals(path)) {
                verificationPayloadMatched.set(RECIPIENT.equals(json.path("recipientEmail").asText())
                        && VERIFICATION_CODE.equals(json.path("verificationCode").asText())
                        && !json.has("resetCode"));
            } else if ("/internal/v1/password-resets".equals(path)) {
                resetPayloadMatched.set(RESET_RECIPIENT.equals(json.path("recipientEmail").asText())
                        && RESET_CODE.equals(json.path("resetCode").asText())
                        && !json.has("verificationCode"));
            }
        }

        private void respond(HttpExchange exchange, int status, String location) throws IOException {
            if (location != null) {
                exchange.getResponseHeaders().add("Location", location);
            }
            if (status == 503) {
                exchange.getResponseHeaders().add("Retry-After", "0");
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Connection", "close");
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        }

        private int sourceAttempts() {
            return sourceAttempts.get();
        }

        private int sameHostTargetAttempts() {
            return sameHostTargetAttempts.get();
        }

        private int differentHostTargetAttempts() {
            return differentHostTargetAttempts.get();
        }

        private boolean serviceTokenMatched() {
            return serviceTokenMatched.get();
        }

        private boolean verificationPayloadMatched() {
            return verificationPayloadMatched.get();
        }

        private boolean resetPayloadMatched() {
            return resetPayloadMatched.get();
        }

        private String lastPath() {
            return lastPath;
        }

        @Override
        public void close() {
            source.stop(0);
            differentHost.stop(0);
            executor.shutdownNow();
        }
    }
}
