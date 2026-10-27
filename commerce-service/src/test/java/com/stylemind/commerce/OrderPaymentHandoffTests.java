package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.stylemind.commerce.client.payment.OrderPaymentRequest;
import com.stylemind.commerce.client.payment.PaymentClient;
import com.stylemind.commerce.service.order.OrderPaymentHandoff;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class OrderPaymentHandoffTests {
    @Test
    void sendsOnlyAfterCommitOutsideTransactionAndNeverOnRollback() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var client = context.getBean(PaymentClient.class);
            var manager = context.getBean(TestTransactions.class);
            var tx = new TransactionTemplate(manager);
            var request = new OrderPaymentRequest(UUID.randomUUID(), UUID.randomUUID(), "COD", 100,
                    "PLACED", "test");
            when(client.create(any())).thenAnswer(invocation -> {
                assertThat(manager.committed).isTrue();
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
                return null;
            });
            tx.executeWithoutResult(status -> {
                context.publishEvent(request);
                verifyNoInteractions(client);
            });
            verify(client).create(request);
            reset(client);
            tx.executeWithoutResult(status -> {
                context.publishEvent(request);
                status.setRollbackOnly();
            });
            verifyNoInteractions(client);
            // A publication without a transaction must not accidentally send an uncommitted snapshot.
            context.publishEvent(request);
            verifyNoInteractions(client);
        }
    }

    @Test
    void upstreamRuntimeFailureCannotUndoCommitOrEscapeCheckout() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var client = context.getBean(PaymentClient.class);
            var manager = context.getBean(TestTransactions.class);
            when(client.create(any())).thenThrow(new IllegalStateException("private-upstream-marker"));
            new TransactionTemplate(manager).executeWithoutResult(status -> context.publishEvent(
                    new OrderPaymentRequest(UUID.randomUUID(), UUID.randomUUID(), "COD", 100, "PLACED", "test")));
            assertThat(manager.committed).isTrue();
            verify(client).create(any());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Config {
        @Bean PaymentClient client() { return mock(PaymentClient.class); }
        @Bean OrderPaymentHandoff listener(PaymentClient client) { return new OrderPaymentHandoff(client); }
        @Bean TestTransactions transactionManager() { return new TestTransactions(); }
    }

    // Exercises real Spring transaction synchronization/listener advice without a database.
    static class TestTransactions extends AbstractPlatformTransactionManager {
        boolean committed;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { committed = false; }
        @Override protected void doCommit(DefaultTransactionStatus status) { committed = true; }
        @Override protected void doRollback(DefaultTransactionStatus status) { committed = false; }
    }
}
