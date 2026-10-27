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
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18.6")
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
