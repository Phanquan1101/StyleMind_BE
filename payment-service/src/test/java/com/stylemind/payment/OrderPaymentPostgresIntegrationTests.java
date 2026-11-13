package com.stylemind.payment;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.payment.dto.OrderPaymentRequest;
import com.stylemind.payment.service.PaymentAttemptService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"stylemind.payment.sepay.qr-bank-name=TESTBANK",
        "stylemind.payment.sepay.qr-account-number=000-test-only"})
@Testcontainers
class OrderPaymentPostgresIntegrationTests {
    @Container static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("payment_handoff_test").withUsername("payment_test").withPassword("test-only");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired PaymentAttemptService payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper mapper;
    @Autowired com.stylemind.payment.repository.PaymentAttemptRepository attempts;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.stylemind.payment.client.CommerceOrderClient commerce;

    @Test
    void concurrentDeliveryCollectionAndRetryRecordOneReceiptAndOneOutbox() throws Exception {
        UUID order = UUID.randomUUID(), owner = UUID.randomUUID(), admin = UUID.randomUUID();
        var attempt = payments.createOrderAttempt(request(order, owner, 125000, "COD", "PLACED"), Instant.now());
        org.mockito.Mockito.when(commerce.getPaymentSnapshot(order)).thenReturn(
                new com.stylemind.payment.client.CommerceOrderPaymentSnapshot(order, owner,
                        com.stylemind.payment.model.PaymentMethod.COD, 125000, "COMPLETED"));
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 4; i++) jobs.add(executor.submit(
                    () -> payments.confirmDeliveredCod(order, admin, Instant.now()).status()));
            for (var job : jobs) assertThat(job.get(30, TimeUnit.SECONDS)).isEqualTo("PAID");
        }
        assertThat(payments.confirmDeliveredCod(order, admin, Instant.now()).id()).isEqualTo(attempt.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_transaction WHERE payment_attempt_id=?", Integer.class, attempt.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_outbox WHERE aggregate_id=?", Integer.class, attempt.id())).isEqualTo(1);
    }

    @Test
    void concurrentInitialRequestsCreateOneAttemptAndReturnSameId() throws Exception {
        UUID order = UUID.randomUUID();
        var request = request(order, UUID.randomUUID(), 125000, "SEPAY_VIETQR", "PENDING_PAYMENT");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<UUID>>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return payments.createOrderAttempt(request, Instant.now()).id();
                }));
            }
            start.countDown();
            var ids = new java.util.HashSet<UUID>();
            for (var future : futures) ids.add(future.get(30, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
        }
        assertThat(count(order)).isEqualTo(1);
        var replay = payments.createOrderAttempt(request, Instant.now());
        assertThat(replay.qrPayload()).contains("amount=125000");
        assertThat(replay.expiresAt()).isNotNull();
    }

    @Test
    void concurrentInternalDeliveryRecoverySettlesOnceAndRejectsMismatchedOwner() throws Exception {
        UUID order = UUID.randomUUID(), owner = UUID.randomUUID(), actor = UUID.randomUUID();
        var attempt = payments.createOrderAttempt(request(order, owner, 125000, "COD", "PLACED"), Instant.now());
        org.mockito.Mockito.when(commerce.getPaymentSnapshot(order)).thenReturn(
                new com.stylemind.payment.client.CommerceOrderPaymentSnapshot(order, UUID.randomUUID(),
                        com.stylemind.payment.model.PaymentMethod.COD, 125000, "COMPLETED", actor, Instant.now()));
        assertThatThrownBy(() -> payments.collectDeliveredCod(order, Instant.now()))
                .isInstanceOf(com.stylemind.payment.errors.PaymentApiException.class).hasMessage("COD order does not match the payment attempt");
        org.mockito.Mockito.when(commerce.getPaymentSnapshot(order)).thenReturn(
                new com.stylemind.payment.client.CommerceOrderPaymentSnapshot(order, owner,
                        com.stylemind.payment.model.PaymentMethod.COD, 125000, "COMPLETED", actor, Instant.now()));
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 4; i++) jobs.add(executor.submit(() -> payments.collectDeliveredCod(order, Instant.now()).status()));
            for (var job : jobs) assertThat(job.get(30, TimeUnit.SECONDS)).isEqualTo("PAID");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_transaction WHERE payment_attempt_id=?", Integer.class, attempt.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_outbox WHERE aggregate_id=?", Integer.class, attempt.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recorded_by_user_id FROM financial_transaction WHERE payment_attempt_id=?", UUID.class, attempt.id())).isEqualTo(actor);
    }

    @Test
    void mismatchedReplaysCannotChangeCustomerAmountOrMethod() throws Exception {
        UUID order = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        var original = payments.createOrderAttempt(request(order, owner, 125000, "COD", "PLACED"), Instant.now());
        for (var mismatch : java.util.List.of(request(order, UUID.randomUUID(), 125000, "COD", "PLACED"),
                request(order, owner, 1, "COD", "PLACED"),
                request(order, owner, 125000, "SEPAY_VIETQR", "PENDING_PAYMENT"))) {
            assertThatThrownBy(() -> payments.createOrderAttempt(mismatch, Instant.now()))
                    .isInstanceOf(com.stylemind.payment.errors.PaymentApiException.class);
        }
        var replay = payments.createOrderAttempt(request(order, owner, 125000, "COD", "PLACED"), Instant.now());
        assertThat(replay).isEqualTo(original);
        assertThat(count(order)).isEqualTo(1);
    }

    @Test
    void rolledBackCreationLeavesNoAttemptAndReleasesLockForRetry() throws Exception {
        UUID order = UUID.randomUUID();
        var request = request(order, UUID.randomUUID(), 125000, "COD", "PLACED");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            payments.createOrderAttempt(request, Instant.now());
            status.setRollbackOnly();
        });
        assertThat(count(order)).isZero();
        payments.createOrderAttempt(request, Instant.now());
        assertThat(count(order)).isEqualTo(1);
    }

    @Test
    void expiredInitialAttemptIsReplayedWithoutCreatingFreshAttempt() throws Exception {
        UUID order = UUID.randomUUID();
        var request = request(order, UUID.randomUUID(), 125000, "SEPAY_VIETQR", "PENDING_PAYMENT");
        Instant now = Instant.now();
        var original = payments.createOrderAttempt(request, now);
        var expired = payments.createOrderAttempt(request, now.plusSeconds(900));
        assertThat(expired.id()).isEqualTo(original.id());
        assertThat(expired.status()).isEqualTo("EXPIRED");
        assertThat(count(order)).isEqualTo(1);
    }

    @Test
    void bulkExpiryAndSettlementInvalidatePreviouslyLoadedJpaState() throws Exception {
        var now = Instant.now();
        var request = request(UUID.randomUUID(), UUID.randomUUID(), 125000, "SEPAY_VIETQR", "PENDING_PAYMENT");
        var id = payments.createOrderAttempt(request, now).id();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertThat(attempts.findById(id).orElseThrow().attemptStatus()).isEqualTo("PENDING");
            attempts.expireStaleOnlineAttempts(now.plusSeconds(900));
            assertThat(attempts.findById(id).orElseThrow().attemptStatus()).isEqualTo("EXPIRED");
            attempts.markSettled(id, now.plusSeconds(900));
            assertThat(attempts.findById(id).orElseThrow().attemptStatus()).isEqualTo("SETTLED");
            status.setRollbackOnly();
        });
        assertThat(attempts.findById(id).orElseThrow().attemptStatus()).isEqualTo("PENDING");
    }

    @Test
    void duplicateJpaInsertDoesNotAbortTheSurroundingTransaction() throws Exception {
        var now = Instant.now();
        var id = payments.createOrderAttempt(request(UUID.randomUUID(), UUID.randomUUID(), 125000, "COD", "PLACED"), now).id();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var saved = attempts.findById(id).orElseThrow();
            var duplicate = new com.stylemind.payment.model.PaymentAttemptCreation(saved.paymentAttemptId(),
                    saved.orderId(), saved.customerId(), saved.paymentReference(), saved.requestKey(),
                    com.stylemind.payment.model.PaymentMethod.COD, saved.expectedAmountVnd(), saved.generatedAt(),
                    null, null, null, null);
            assertThat(attempts.insert(duplicate)).isFalse();
            attempts.markSettled(id, now);
            assertThat(attempts.findById(id).orElseThrow().attemptStatus()).isEqualTo("SETTLED");
        });
        assertThat(attempts.findById(id).orElseThrow().attemptStatus()).isEqualTo("SETTLED");
    }

    private int count(UUID order) {
        return jdbc.queryForObject("SELECT count(*) FROM public.payment_attempt WHERE order_id = ?", Integer.class, order);
    }
    private OrderPaymentRequest request(UUID order, UUID owner, long amount, String method, String state) throws Exception {
        return OrderPaymentRequest.parse(mapper.readTree("""
                {"order_id":"%s","customer_id":"%s","accepted_total_vnd":%d,
                 "payment_method":"%s","order_status":"%s","request_key":"initial-%s"}
                """.formatted(order, owner, amount, method, state, order)));
    }
}
