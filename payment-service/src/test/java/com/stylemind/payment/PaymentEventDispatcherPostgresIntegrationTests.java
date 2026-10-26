package com.stylemind.payment;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.stylemind.payment.client.CommerceOrderPaymentSnapshot;
import com.stylemind.payment.dto.OrderPaymentRequest;
import com.stylemind.payment.model.PaymentMethod;
import com.stylemind.payment.service.*;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "stylemind.payment.sepay.webhook-auth-mode=api-key", "stylemind.payment.sepay.webhook-api-key=dispatch-test-only",
        "stylemind.payment.sepay.qr-bank-name=TESTBANK", "stylemind.payment.sepay.qr-account-number=000-test-only"})
@Testcontainers
class PaymentEventDispatcherPostgresIntegrationTests {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("payment_dispatch_test").withUsername("dispatch_test").withPassword("test-only");
    static final AtomicReference<String> MODE = new AtomicReference<>("success");
    static final Map<String, String> METHODS = new ConcurrentHashMap<>();
    static final Map<String, Integer> RECEIVED = new ConcurrentHashMap<>();
    static final AtomicReference<JdbcTemplate> FIXTURE_JDBC = new AtomicReference<>();
    static final AtomicBoolean NO_WRITE_TX_DURING_HTTP = new AtomicBoolean(true);
    static final AtomicLong PROVIDER_EVENT = new AtomicLong(700000);
    static final com.sun.net.httpserver.HttpServer SERVER = server();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl); registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("stylemind.payment.commerce.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
        registry.add("stylemind.payment.commerce.service-token", () -> "commerce-dispatch-test-only");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentAttemptService payments;
    @Autowired SepayWebhookService webhook;
    @Autowired PaymentEventDispatcher dispatcher;
    @Autowired com.stylemind.payment.repository.PaymentOutboxRepository outboxRepository;
    @Autowired PaymentEventDeliveryService delivery;
    @Autowired com.stylemind.payment.config.CommerceClientProperties commerceProperties;
    @Autowired ObjectMapper mapper;
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM public.payment_outbox"); jdbc.update("DELETE FROM public.payment_exception");
        jdbc.update("DELETE FROM public.financial_transaction"); jdbc.update("DELETE FROM public.payment_attempt");
        MODE.set("success"); RECEIVED.clear(); METHODS.clear(); FIXTURE_JDBC.set(jdbc); NO_WRITE_TX_DURING_HTTP.set(true);
    }
    @AfterAll static void closeServer() { SERVER.stop(0); }

    @Test void publishesOnlyAfterValidAcknowledgementOutsideWriteTransaction() throws Exception {
        settle(); assertThat(dispatcher.dispatchPending()).isEqualTo(1); assertThat(pending()).isZero();
        assertThat(RECEIVED).hasSize(1); assertThat(NO_WRITE_TX_DURING_HTTP.get()).isTrue();
        assertThat(dispatcher.dispatchPending()).isZero();
    }
    @Test void errorWrongAckAndMalformedAckRemainPendingThenRetryUsesSameEventId() throws Exception {
        settle(); String id = jdbc.queryForObject("SELECT outbox_event_id::text FROM public.payment_outbox", String.class);
        for (String mode : List.of("error", "wrong-id", "unknown", "malformed", "no-content")) {
            MODE.set(mode); assertThat(dispatcher.dispatchPending()).isZero(); assertThat(pending()).isEqualTo(1);
        }
        MODE.set("success"); assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(RECEIVED.keySet()).containsExactly(id); assertThat(RECEIVED.get(id)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.financial_transaction", Integer.class)).isEqualTo(1);
    }
    @Test void lostAcknowledgementRetriesWithoutChangingEventIdentityOrFinancialTruth() throws Exception {
        settle(); MODE.set("lost"); assertThat(dispatcher.dispatchPending()).isZero(); assertThat(pending()).isEqualTo(1);
        MODE.set("success"); assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(RECEIVED.values()).containsExactly(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.financial_transaction", Integer.class)).isEqualTo(1);
    }
    @Test void rejectedOrderIsPublishedWithOneAdminExceptionAndSettlementPreserved() throws Exception {
        settle(); MODE.set("reconcile"); assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(pending()).isZero(); assertThat(dispatcher.dispatchPending()).isZero();
        assertThat(jdbc.queryForObject("SELECT exception_reason FROM public.payment_exception", String.class)).isEqualTo("ORDER_PAYMENT_REJECTED");
        assertThat(jdbc.queryForObject("SELECT attempt_status FROM public.payment_attempt", String.class)).isEqualTo("SETTLED");
    }
    @Test void legacySepayPayloadIsDeliveredButPendingCodCannotPublishCollection() throws Exception {
        settle();
        String payload = jdbc.queryForObject("SELECT event_payload FROM public.payment_outbox", String.class);
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(payload); legacy.remove("payment_method");
        jdbc.update("UPDATE public.payment_outbox SET event_payload=?", mapper.writeValueAsString(legacy));
        assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        var order = UUID.randomUUID();
        var cod = payments.createOrderAttempt(new OrderPaymentRequest(new CommerceOrderPaymentSnapshot(order, UUID.randomUUID(),
                PaymentMethod.COD, 125000, "PLACED"), "initial-" + order), Instant.now());
        jdbc.update("INSERT INTO public.payment_outbox(outbox_event_id,aggregate_type,aggregate_id,event_type,event_payload,created_at) VALUES (?,'PaymentAttempt',?,'PaymentConfirmed','{}',now())",
                UUID.randomUUID(), cod.id());
        assertThat(dispatcher.dispatchPending()).isZero(); assertThat(pending()).isEqualTo(1);
    }
    @Test void poisonBatchDoesNotStarveLaterPendingRows() throws Exception {
        settle(); UUID attempt = jdbc.queryForObject("SELECT payment_attempt_id FROM public.payment_attempt", UUID.class);
        String valid = jdbc.queryForObject("SELECT event_payload FROM public.payment_outbox", String.class);
        jdbc.update("DELETE FROM public.payment_outbox");
        for (int i = 0; i < 26; i++) jdbc.update("INSERT INTO public.payment_outbox(outbox_event_id,aggregate_type,aggregate_id,event_type,event_payload,created_at) VALUES (?,'PaymentAttempt',?,'PaymentConfirmed',?,?)",
                new UUID(0, i + 1), attempt, i == 25 ? valid : "broken-json", java.sql.Timestamp.from(Instant.parse("2026-10-26T00:00:00Z")));
        assertThat(dispatcher.dispatchPending()).isZero(); assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(pending()).isEqualTo(25);
    }
    @Test void concurrentWorkersCanReplayButPublishOneRowAndOneRejectionException() throws Exception {
        settle(); MODE.set("reconcile");
        var otherWorker = new PaymentEventDispatcher(outboxRepository, delivery, commerceProperties, mapper, org.springframework.web.client.RestClient.builder(), false);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> dispatcher.dispatchPending()); var b = pool.submit(() -> otherWorker.dispatchPending());
            a.get(30, TimeUnit.SECONDS); b.get(30, TimeUnit.SECONDS);
        }
        assertThat(pending()).isZero(); assertThat(jdbc.queryForObject("SELECT count(*) FROM public.payment_exception", Integer.class)).isEqualTo(1);
    }
    @Test void actualCodCollectionPublishesCodAndLegacyPayloadUsesStoredMethod() throws Exception {
        collectCod();
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(jdbc.queryForObject("SELECT event_payload FROM public.payment_outbox", String.class));
        payload.remove("payment_method"); jdbc.update("UPDATE public.payment_outbox SET event_payload=?", mapper.writeValueAsString(payload));
        assertThat(dispatcher.dispatchPending()).isEqualTo(1); assertThat(pending()).isZero();
        assertThat(METHODS.values()).containsExactly("COD"); assertThat(NO_WRITE_TX_DURING_HTTP.get()).isTrue();
        assertThat(dispatcher.dispatchPending()).isZero();
    }
    @Test void codCollectionRetriesErrorsAndLostAckUsingSameIdentityWithoutRecordingMoreCash() {
        collectCod();
        for (String mode : List.of("error", "lost", "wrong-id", "malformed", "unknown", "no-content")) {
            MODE.set(mode); assertThat(dispatcher.dispatchPending()).isZero(); assertThat(pending()).isEqualTo(1);
        }
        MODE.set("success"); assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(RECEIVED).hasSize(1); assertThat(RECEIVED.values()).containsExactly(7);
        assertThat(METHODS.values()).containsExactly("COD");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.financial_transaction", Integer.class)).isEqualTo(1);
    }
    @Test void rejectedCodOrderProducesOneAdminExceptionWithoutUndoingCollectedMoney() {
        collectCod(); MODE.set("reconcile"); assertThat(dispatcher.dispatchPending()).isEqualTo(1);
        assertThat(dispatcher.dispatchPending()).isZero();
        assertThat(jdbc.queryForObject("SELECT exception_reason FROM public.payment_exception", String.class)).isEqualTo("ORDER_PAYMENT_REJECTED");
        assertThat(jdbc.queryForObject("SELECT attempt_status FROM public.payment_attempt", String.class)).isEqualTo("SETTLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.financial_transaction", Integer.class)).isEqualTo(1);
    }
    @Test void codPayloadCannotClaimAnOnlineMethod() throws Exception {
        collectCod();
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(jdbc.queryForObject("SELECT event_payload FROM public.payment_outbox", String.class));
        payload.put("payment_method", "SEPAY_VIETQR"); jdbc.update("UPDATE public.payment_outbox SET event_payload=?", mapper.writeValueAsString(payload));
        assertThat(dispatcher.dispatchPending()).isZero(); assertThat(RECEIVED).isEmpty(); assertThat(pending()).isEqualTo(1);
    }
    @Test void concurrentCodWorkersPublishOneOutboxAndOneReconciliationException() throws Exception {
        collectCod(); MODE.set("reconcile");
        var otherWorker = new PaymentEventDispatcher(outboxRepository, delivery, commerceProperties, mapper, org.springframework.web.client.RestClient.builder(), false);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> dispatcher.dispatchPending()); var b = pool.submit(() -> otherWorker.dispatchPending());
            a.get(30, TimeUnit.SECONDS); b.get(30, TimeUnit.SECONDS);
        }
        assertThat(pending()).isZero(); assertThat(jdbc.queryForObject("SELECT count(*) FROM public.payment_exception", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.financial_transaction", Integer.class)).isEqualTo(1);
    }
    private void collectCod() {
        UUID order = UUID.randomUUID();
        var attempt = payments.createOrderAttempt(new OrderPaymentRequest(new CommerceOrderPaymentSnapshot(order, UUID.randomUUID(),
                PaymentMethod.COD, 125000, "PLACED"), "initial-" + order), Instant.now());
        assertThat(dispatcher.dispatchPending()).isZero();
        payments.confirmCodCollection(new com.stylemind.payment.dto.ConfirmCodCollectionRequest(attempt.id(), 125000L,
                "COD-RECEIPT-TEST", null), UUID.randomUUID(), Instant.now());
    }
    private void settle() throws Exception {
        UUID order = UUID.randomUUID();
        var attempt = payments.createOrderAttempt(new OrderPaymentRequest(new CommerceOrderPaymentSnapshot(order, UUID.randomUUID(),
                PaymentMethod.SEPAY_VIETQR, 125000, "PENDING_PAYMENT"), "initial-" + order), Instant.now());
        var body = mapper.writeValueAsBytes(Map.of("id", PROVIDER_EVENT.incrementAndGet(), "code", attempt.reference(),
                "transferType", "in", "transferAmount", 125000));
        var headers = new HttpHeaders(); headers.set(HttpHeaders.AUTHORIZATION, "Apikey dispatch-test-only");
        webhook.receive(body, headers, Instant.now());
    }
    private int pending() { return jdbc.queryForObject("SELECT count(*) FROM public.payment_outbox WHERE published_at IS NULL", Integer.class); }
    private static com.sun.net.httpserver.HttpServer server() {
        try {
            var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v1/payment-events", exchange -> {
                var mapper = new ObjectMapper(); var body = mapper.readTree(exchange.getRequestBody());
                String id = body.path("event_id").asText(); RECEIVED.merge(id, 1, Integer::sum);
                METHODS.put(id, body.path("payment_method").asText());
                if (FIXTURE_JDBC.get().queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state='idle in transaction'", Integer.class) != 0)
                    NO_WRITE_TX_DURING_HTTP.set(false);
                String mode = MODE.get();
                if (mode.equals("lost")) { exchange.close(); return; }
                int status = !"commerce-dispatch-test-only".equals(exchange.getRequestHeaders().getFirst("X-StyleMind-Service-Token")) ? 401
                        : mode.equals("error") ? 503 : mode.equals("no-content") ? 204 : 200;
                byte[] bytes = mode.equals("malformed") ? "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        : mapper.writeValueAsBytes(Map.of("event_id", mode.equals("wrong-id") ? UUID.randomUUID().toString() : id,
                            "outcome", mode.equals("unknown") ? "UNKNOWN" : mode.equals("reconcile") ? "RECONCILIATION_REQUIRED" : "APPLIED",
                            "reason", "TEST_RECEIPT"));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
                if (status != 204) exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start(); return server;
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
}
