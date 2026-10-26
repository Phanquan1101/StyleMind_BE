package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stylemind.payment.ingress-token=event-test-only-key")
@Testcontainers
class PaymentEventPostgresIntegrationTests {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("commerce_event_test").withUsername("event_test").withPassword("test-only");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    static final String PATH = "/internal/v1/payment-events";
    @BeforeEach void reset() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_event_test ON public.payment_event_inbox");
        jdbc.update("DELETE FROM public.payment_event_inbox");
        jdbc.update("DELETE FROM public.order_status_history");
        jdbc.update("DELETE FROM public.customer_order");
    }
    @Test void appliesSepayOnceAndReturnsSameDurableAcknowledgement() {
        UUID owner = UUID.randomUUID(), order = order(owner, "PENDING_PAYMENT", "SEPAY_VIETQR");
        var event = event(order, owner);
        var first = send(event);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(first.getBody().path("outcome").asText()).isEqualTo("APPLIED");
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(send(event).getBody()).isEqualTo(first.getBody());
        assertThat(state(order)).isEqualTo("PAID"); assertThat(count("order_status_history")).isEqualTo(1);
        assertThat(count("payment_event_inbox")).isEqualTo(1);
    }
    @Test void eventIdContentConflictCannotChangeAppliedPayment() {
        UUID owner = UUID.randomUUID(), order = order(owner, "PENDING_PAYMENT", "SEPAY_VIETQR");
        var event = event(order, owner); send(event); event.put("amount_vnd", 1);
        assertThat(send(event).getStatusCode().value()).isEqualTo(409);
        assertThat(count("order_status_history")).isEqualTo(1); assertThat(state(order)).isEqualTo("PAID");
    }
    @Test void equivalentPaymentUnderAnotherEventIdIsDuplicateAtPostgresTimePrecision() {
        UUID owner = UUID.randomUUID(), order = order(owner, "PENDING_PAYMENT", "SEPAY_VIETQR");
        var event = event(order, owner); send(event); event.put("event_id", UUID.randomUUID());
        assertThat(send(event).getBody().path("outcome").asText()).isEqualTo("DUPLICATE");
        assertThat(count("order_status_history")).isEqualTo(1);
    }
    @Test void distinctSecondPaymentIsReconciliationWithoutRepeatingHistory() {
        UUID owner = UUID.randomUUID(), order = order(owner, "PENDING_PAYMENT", "SEPAY_VIETQR");
        send(event(order, owner));
        assertThat(send(event(order, owner)).getBody().path("reason").asText()).isEqualTo("SECOND_PAYMENT");
        assertThat(count("order_status_history")).isEqualTo(1);
    }
    @Test void terminalAndInappropriateOrdersAreNeverReopenedOrRegressed() {
        for (String status : List.of("CANCELLED", "COMPLETED", "SHIPPED", "PROCESSING", "PLACED", "PAID")) {
            UUID owner = UUID.randomUUID(), order = order(owner, status, "SEPAY_VIETQR");
            var ack = send(event(order, owner));
            assertThat(ack.getBody().path("outcome").asText()).isEqualTo("RECONCILIATION_REQUIRED");
            assertThat(state(order)).isEqualTo(status);
        }
        assertThat(count("order_status_history")).isZero();
    }
    @Test void missingForeignWrongAmountAndWrongMethodOrdersAreReconciled() {
        UUID owner = UUID.randomUUID();
        var missing = event(UUID.randomUUID(), owner);
        assertThat(send(missing).getBody().path("reason").asText()).isEqualTo("ORDER_NOT_FOUND");
        UUID order = order(owner, "PENDING_PAYMENT", "SEPAY_VIETQR");
        assertThat(send(event(order, UUID.randomUUID())).getBody().path("reason").asText()).isEqualTo("OWNER_MISMATCH");
        var wrongAmount = event(order, owner); wrongAmount.put("amount_vnd", 1);
        assertThat(send(wrongAmount).getBody().path("reason").asText()).isEqualTo("AMOUNT_MISMATCH");
        UUID cod = order(owner, "PLACED", "COD");
        assertThat(send(event(cod, owner)).getBody().path("reason").asText()).isEqualTo("METHOD_MISMATCH");
        assertThat(state(order)).isEqualTo("PENDING_PAYMENT"); assertThat(state(cod)).isEqualTo("PLACED");
        assertThat(count("order_status_history")).isZero();
    }
    @Test void serviceCredentialIsMandatoryAndBearerCannotReplaceIt() {
        var body = event(UUID.randomUUID(), UUID.randomUUID());
        for (String key : Arrays.asList(null, "wrong-test-only")) {
            var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
            if (key != null) headers.set("X-StyleMind-Service-Token", key);
            headers.setBearerAuth("untrusted-test-only");
            assertThat(http.postForEntity(PATH, new HttpEntity<>(body, headers), JsonNode.class).getStatusCode().value()).isEqualTo(401);
        }
        assertThat(count("payment_event_inbox")).isZero();
    }
    @Test void malformedAndFractionalEventFieldsAreRejectedBeforeWriting() {
        var original = event(UUID.randomUUID(), UUID.randomUUID());
        for (var mutation : List.<Map<String, Object>>of(Map.of("amount_vnd", 125000.9), Map.of("amount_vnd", "125000"),
                Map.of("amount_vnd", 0), Map.of("customer_id", "1-1-1-1-1"), Map.of("payment_method", "UNKNOWN"),
                Map.of("confirmed_at", "invalid"), Map.of("unknown", true))) {
            var invalid = new HashMap<>(original); invalid.putAll(mutation);
            assertThat(send(invalid).getStatusCode().value()).isEqualTo(400);
        }
        assertThat(count("payment_event_inbox")).isZero();
    }
    @Test void concurrentSameAndDistinctEventIdsApplyOneOrderTransition() throws Exception {
        UUID owner = UUID.randomUUID(), order = order(owner, "PENDING_PAYMENT", "SEPAY_VIETQR");
        var event = event(order, owner);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<Future<ResponseEntity<JsonNode>>>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> send(event)));
            for (var job : jobs) assertThat(job.get(30, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
        }
        assertThat(count("payment_event_inbox")).isEqualTo(1); assertThat(count("order_status_history")).isEqualTo(1);
        UUID next = order(owner, "PENDING_PAYMENT", "SEPAY_VIETQR"); var a = event(next, owner); var b = event(next, owner);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var x = pool.submit(() -> send(a)); var y = pool.submit(() -> send(b));
            assertThat(x.get(30, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
            assertThat(y.get(30, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
        }
        assertThat(count("order_status_history")).isEqualTo(2);
    }
    @Test void failedInboxWriteRollsBackOrderAndHistoryThenRetrySucceeds() {
        UUID owner = UUID.randomUUID(), order = order(owner, "PENDING_PAYMENT", "SEPAY_VIETQR");
        var event = event(order, owner);
        jdbc.execute("CREATE OR REPLACE FUNCTION public.fail_event_test_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_event_test BEFORE INSERT ON public.payment_event_inbox FOR EACH ROW EXECUTE FUNCTION public.fail_event_test_fn()");
        try {
            assertThat(send(event).getStatusCode().value()).isEqualTo(500);
            assertThat(state(order)).isEqualTo("PENDING_PAYMENT"); assertThat(count("order_status_history")).isZero();
        } finally { jdbc.execute("DROP TRIGGER fail_event_test ON public.payment_event_inbox"); }
        assertThat(send(event).getBody().path("outcome").asText()).isEqualTo("APPLIED");
    }
    @Test void codCollectionIsDurableAcrossFulfilmentStatesWithoutChangingOrderHistory() {
        UUID owner = UUID.randomUUID();
        for (String status : List.of("PLACED", "PROCESSING", "SHIPPED", "COMPLETED")) {
            UUID order = order(owner, status, "COD"); var event = codEvent(order, owner);
            var first = send(event); assertThat(first.getStatusCode().value()).isEqualTo(200);
            assertThat(first.getBody().path("outcome").asText()).isEqualTo("APPLIED");
            assertThat(first.getBody().path("reason").asText()).isEqualTo("COD_COLLECTION_CONFIRMED");
            assertThat(send(event).getBody()).isEqualTo(first.getBody());
            event.put("event_id", UUID.randomUUID());
            assertThat(send(event).getBody().path("outcome").asText()).isEqualTo("DUPLICATE");
            assertThat(send(codEvent(order, owner)).getBody().path("reason").asText()).isEqualTo("SECOND_PAYMENT");
            assertThat(state(order)).isEqualTo(status);
        }
        assertThat(count("order_status_history")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.payment_event_inbox WHERE outcome='APPLIED' AND payment_method='COD'", Integer.class)).isEqualTo(4);
    }
    @Test void codCancelledPendingAndInappropriateOrdersAreReconciledWithoutRevivingThem() {
        UUID owner = UUID.randomUUID();
        for (String status : List.of("CANCELLED", "PENDING_PAYMENT", "PAID")) {
            UUID order = order(owner, status, "COD"); var receipt = send(codEvent(order, owner));
            assertThat(receipt.getBody().path("outcome").asText()).isEqualTo("RECONCILIATION_REQUIRED");
            assertThat(receipt.getBody().path("reason").asText()).isEqualTo("ORDER_NOT_PAYABLE");
            assertThat(state(order)).isEqualTo(status);
        }
        assertThat(count("order_status_history")).isZero();
    }
    @Test void codOwnerAmountMethodMissingOrderAndCredentialChecksCannotApplyMoney() {
        UUID owner = UUID.randomUUID(), order = order(owner, "PLACED", "COD");
        assertThat(send(codEvent(order, UUID.randomUUID())).getBody().path("reason").asText()).isEqualTo("OWNER_MISMATCH");
        var wrong = codEvent(order, owner); wrong.put("amount_vnd", 1);
        assertThat(send(wrong).getBody().path("reason").asText()).isEqualTo("AMOUNT_MISMATCH");
        UUID online = order(owner, "PENDING_PAYMENT", "SEPAY_VIETQR");
        assertThat(send(codEvent(online, owner)).getBody().path("reason").asText()).isEqualTo("METHOD_MISMATCH");
        assertThat(send(codEvent(UUID.randomUUID(), owner)).getBody().path("reason").asText()).isEqualTo("ORDER_NOT_FOUND");
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("untrusted-test-only");
        assertThat(http.postForEntity(PATH, new HttpEntity<>(codEvent(order, owner), headers), JsonNode.class).getStatusCode().value()).isEqualTo(401);
        assertThat(state(order)).isEqualTo("PLACED"); assertThat(state(online)).isEqualTo("PENDING_PAYMENT");
        assertThat(count("order_status_history")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.payment_event_inbox WHERE outcome='APPLIED'", Integer.class)).isZero();
    }
    @Test void codConcurrentReplaysPersistOneAppliedCollectionAndRejectContentConflict() throws Exception {
        UUID owner = UUID.randomUUID(), order = order(owner, "SHIPPED", "COD"); var event = codEvent(order, owner);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<Future<ResponseEntity<JsonNode>>>();
            for (int i = 0; i < 4; i++) jobs.add(pool.submit(() -> send(event)));
            for (var job : jobs) assertThat(job.get(30, TimeUnit.SECONDS).getBody().path("outcome").asText()).isEqualTo("APPLIED");
        }
        assertThat(count("payment_event_inbox")).isEqualTo(1);
        event.put("amount_vnd", 1); assertThat(send(event).getStatusCode().value()).isEqualTo(409);
        assertThat(state(order)).isEqualTo("SHIPPED"); assertThat(count("order_status_history")).isZero();
    }
    @Test void codDistinctConcurrentEventIdsHaveOneAppliedReceipt() throws Exception {
        UUID owner = UUID.randomUUID(), order = order(owner, "COMPLETED", "COD");
        var a = codEvent(order, owner); var b = new HashMap<>(a); b.put("event_id", UUID.randomUUID());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var x = pool.submit(() -> send(a)); var y = pool.submit(() -> send(b));
            assertThat(List.of(x.get(30, TimeUnit.SECONDS).getBody().path("outcome").asText(),
                    y.get(30, TimeUnit.SECONDS).getBody().path("outcome").asText())).containsExactlyInAnyOrder("APPLIED", "DUPLICATE");
        }
        assertThat(state(order)).isEqualTo("COMPLETED"); assertThat(count("order_status_history")).isZero();
    }
    @Test void failedCodInboxWriteIsNotAcknowledgedAndRetryRecordsCollectionOnce() {
        UUID owner = UUID.randomUUID(), order = order(owner, "PLACED", "COD"); var event = codEvent(order, owner);
        jdbc.execute("CREATE OR REPLACE FUNCTION public.fail_event_test_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_event_test BEFORE INSERT ON public.payment_event_inbox FOR EACH ROW EXECUTE FUNCTION public.fail_event_test_fn()");
        try { assertThat(send(event).getStatusCode().value()).isEqualTo(500); assertThat(count("payment_event_inbox")).isZero(); }
        finally { jdbc.execute("DROP TRIGGER fail_event_test ON public.payment_event_inbox"); }
        assertThat(send(event).getBody().path("outcome").asText()).isEqualTo("APPLIED");
        assertThat(count("payment_event_inbox")).isEqualTo(1); assertThat(state(order)).isEqualTo("PLACED");
        assertThat(count("order_status_history")).isZero();
    }
    private Map<String, Object> codEvent(UUID order, UUID owner) {
        var body = event(order, owner); body.put("payment_method", "COD"); return body;
    }
    private UUID order(UUID owner, String status, String method) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.customer_order(order_id,order_number,customer_id,checkout_key,order_status,payment_method,accepted_total_vnd,placed_at) VALUES (?,?,?,?,?,?,125000,?)",
                id, "EV-" + id, owner, id.toString(), status, method, java.sql.Timestamp.from(Instant.now()));
        return id;
    }
    private Map<String, Object> event(UUID order, UUID owner) {
        return new HashMap<>(Map.of("event_id", UUID.randomUUID(), "event_type", "PaymentConfirmed",
                "payment_attempt_id", UUID.randomUUID(), "order_id", order, "customer_id", owner,
                "payment_method", "SEPAY_VIETQR", "payment_reference", "SMTEST", "amount_vnd", 125000,
                "confirmed_at", "2026-10-26T00:00:00.123456789Z"));
    }
    private ResponseEntity<JsonNode> send(Map<String, Object> event) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-StyleMind-Service-Token", "event-test-only-key");
        return http.postForEntity(PATH, new HttpEntity<>(event, headers), JsonNode.class);
    }
    private String state(UUID id) { return jdbc.queryForObject("SELECT order_status FROM public.customer_order WHERE order_id=?", String.class, id); }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM public." + table, Integer.class); }
}
