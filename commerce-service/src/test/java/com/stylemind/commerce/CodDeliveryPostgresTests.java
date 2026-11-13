package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.stylemind.commerce.client.payment.PaymentClient;
import com.stylemind.commerce.dto.order.PaymentConfirmedEvent;
import com.stylemind.commerce.enums.OrderStatus;
import com.stylemind.commerce.repository.order.OrderRepository;
import com.stylemind.commerce.service.order.CodCollectionDispatcher;
import com.stylemind.commerce.service.order.InternalPaymentSnapshotService;
import com.stylemind.commerce.service.order.OrderService;
import com.stylemind.commerce.service.order.PaymentEventService;
import com.stylemind.commerce.validation.order.OrderRequestValidator.StatusCommand;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
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

@SpringBootTest(properties = {"stylemind.commerce.inventory.expiry-enabled=false",
        "stylemind.commerce.cod.collection-scan-ms=600000"})
@Testcontainers
class CodDeliveryPostgresTests {
    @Container static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("cod_delivery_test").withUsername("commerce_test").withPassword("test-only");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired OrderService orders;
    @Autowired OrderRepository repository;
    @Autowired InternalPaymentSnapshotService snapshots;
    @Autowired PaymentEventService paymentEvents;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @org.springframework.test.context.bean.override.mockito.MockitoBean PaymentClient payment;

    @Test
    void paymentOutageCommitsDeliveryAndRecreatedWorkerRecoversFromDatabase() {
        UUID owner = UUID.randomUUID(), actor = UUID.randomUUID(), order = seed(owner, "COD", "SHIPPED");
        doThrow(new IllegalStateException("synthetic outage")).when(payment).collectDeliveredCod(order);
        var delivered = orders.updateStatus(actor, order, new StatusCommand(OrderStatus.COMPLETED, "Received cash"));
        assertThat(delivered.orderStatus()).isEqualTo("COMPLETED");
        assertThat(delivered.paymentStatus()).isEqualTo("PENDING");
        assertThat(snapshots.get(order).codCollectionActorId()).isEqualTo(actor);
        assertThat(snapshots.get(order).codCollectionRequestedAt()).isNotNull();
        verify(payment).collectDeliveredCod(order);
        assertThatThrownBy(() -> orders.get(UUID.randomUUID(), false, order))
                .isInstanceOf(com.stylemind.commerce.errors.ApiOperationException.class).hasMessage("RESOURCE_NOT_FOUND");
        doAnswer(call -> {
            var event = new PaymentConfirmedEvent(UUID.randomUUID(), "PaymentConfirmed", UUID.randomUUID(),
                    order, owner, "COD", "test-cod-reference", 125000, Instant.now());
            assertThat(paymentEvents.receive(event, Instant.now()).outcome()).isEqualTo("APPLIED");
            return null;
        }).when(payment).collectDeliveredCod(order);
        // A newly constructed worker has no event or in-memory request from the delivery command.
        var restartedWorker = new CodCollectionDispatcher(repository, payment);
        restartedWorker.recoverPendingCollections();
        assertThat(orders.get(owner, false, order).paymentStatus()).isEqualTo("PAID");
        restartedWorker.recoverPendingCollections();
        verify(payment, times(2)).collectDeliveredCod(order);
        assertThat(historyCount(order)).isEqualTo(1);
    }

    @Test
    void concurrentDeliveryCallsPersistOneIntentAndOneImmediateCollectionAttempt() throws Exception {
        UUID owner = UUID.randomUUID(), actor = UUID.randomUUID(), order = seed(owner, "COD", "SHIPPED");
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 4; i++) jobs.add(executor.submit(() ->
                    orders.updateStatus(actor, order, new StatusCommand(OrderStatus.COMPLETED, null)).orderStatus()));
            for (var job : jobs) assertThat(job.get(30, TimeUnit.SECONDS)).isEqualTo("COMPLETED");
        }
        assertThat(historyCount(order)).isEqualTo(1);
        verify(payment).collectDeliveredCod(order);
    }

    @Test
    void rollbackAndPendingCancellationLeaveNoDeliveryIntentOrPaymentCall() {
        UUID owner = UUID.randomUUID(), actor = UUID.randomUUID(), order = seed(owner, "COD", "SHIPPED");
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> {
            orders.updateStatus(actor, order, new StatusCommand(OrderStatus.COMPLETED, null));
            throw new IllegalStateException("synthetic rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(orders.get(owner, false, order).orderStatus()).isEqualTo("SHIPPED");
        assertThat(historyCount(order)).isZero();
        jdbc.update("""
                INSERT INTO cancellation_request(cancellation_request_id,order_id,requested_by_user_id,reason,request_status,requested_at)
                VALUES (?,?,?,'Cancel please','PENDING',now())
                """, UUID.randomUUID(), order, owner);
        assertThatThrownBy(() -> orders.updateStatus(actor, order, new StatusCommand(OrderStatus.COMPLETED, null)))
                .isInstanceOf(com.stylemind.commerce.errors.ApiOperationException.class).hasMessage("CANCELLATION_PENDING");
        assertThat(historyCount(order)).isZero();
        verify(payment, never()).collectDeliveredCod(order);
    }

    @Test
    void historicalCompletedCodAndOnlineDeliveryHaveNoAutomaticCodIntent() {
        UUID owner = UUID.randomUUID(), actor = UUID.randomUUID();
        UUID historical = seed(owner, "COD", "COMPLETED"), online = seed(owner, "VNPAY", "SHIPPED");
        orders.updateStatus(actor, online, new StatusCommand(OrderStatus.COMPLETED, null));
        assertThat(snapshots.get(historical).codCollectionActorId()).isNull();
        assertThat(snapshots.get(online).codCollectionActorId()).isNull();
        var pending = repository.findPendingCodCollectionOrderIds(null, org.springframework.data.domain.PageRequest.of(0, 100));
        assertThat(pending).doesNotContain(historical, online);
        verify(payment, never()).collectDeliveredCod(historical);
        verify(payment, never()).collectDeliveredCod(online);
    }

    private UUID seed(UUID owner, String method, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order(order_id,order_number,customer_id,checkout_key,order_status,payment_method,accepted_total_vnd,placed_at,completed_at)
                VALUES (?,?,?,?,?,?,125000,now(),CASE WHEN ?='COMPLETED' THEN now() ELSE NULL END)
                """, id, "COD-" + id, owner, "key-" + id, status, method, status);
        jdbc.update("INSERT INTO order_address_snapshot(order_id,recipient_name,recipient_phone,address_line) VALUES (?,'Test','0900000000','Test address')", id);
        return id;
    }

    private int historyCount(UUID order) {
        return jdbc.queryForObject("SELECT count(*) FROM order_status_history WHERE order_id=?", Integer.class, order);
    }
}
