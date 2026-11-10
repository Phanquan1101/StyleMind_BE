package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PaidCancellationMigrationPostgresTests {
    @Container static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("refund_upgrade").withUsername("refund_upgrade").withPassword("test-only");

    @Test void upgradesWithoutCreatingRefundsForExistingOrdersAndEnforcesConstraints() {
        var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(source).target("12").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID order = UUID.randomUUID(), customer = UUID.randomUUID(), request = UUID.randomUUID(), event = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order(order_id,order_number,customer_id,checkout_key,order_status,payment_method,accepted_total_vnd,placed_at)
                VALUES (?, 'MIGRATION-QR', ?, 'migration-qr', 'PAID', 'SEPAY_VIETQR', 100000, now())
                """, order, customer);
        jdbc.update("""
                INSERT INTO cancellation_request(cancellation_request_id,order_id,requested_by_user_id,reason,request_status,requested_at)
                VALUES (?, ?, ?, 'change plans', 'PENDING', now())
                """, request, order, customer);
        jdbc.update("""
                INSERT INTO payment_event_inbox(event_id,event_fingerprint,order_id,payment_attempt_id,customer_id,payment_method,
                    payment_reference,amount_vnd,confirmed_at,received_at,outcome,reason)
                VALUES (?, ?, ?, ?, ?, 'SEPAY_VIETQR', 'MIGRATION', 100000, now(), now(), 'APPLIED', 'PAYMENT_CONFIRMED')
                """, event, "a".repeat(64), order, UUID.randomUUID(), customer);
        assertThat(Flyway.configure().dataSource(source).target("13").load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cancellation_refund_obligation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT order_status FROM customer_order WHERE order_id=?", String.class, order)).isEqualTo("PAID");
        String insert = "INSERT INTO cancellation_refund_obligation VALUES (?,?,?,?,?,now())";
        assertThatThrownBy(() -> jdbc.update(insert, request, order, event, 0, "PENDING_REFUND"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, request, order, event, 100000, "REFUNDED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, request, order, event, 100000, "PENDING_REFUND");
        assertThatThrownBy(() -> jdbc.update(insert, request, order, event, 100000, "PENDING_REFUND"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
