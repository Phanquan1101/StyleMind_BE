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
class OrderConfirmationMigrationPostgresTests {
    @Container static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("confirmation_upgrade").withUsername("confirmation_upgrade").withPassword("test-only");

    @Test void preservesLegacyOrdersAndAllowsOnlyOnePendingRequest() {
        var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(source).target("10").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID order = UUID.randomUUID(), customer = UUID.randomUUID(), request = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order(order_id,order_number,customer_id,checkout_key,order_status,payment_method,accepted_total_vnd,placed_at)
                VALUES (?, 'ORDER-LEGACY', ?, 'key-legacy', 'PLACED', 'COD', 10000, now())
                """, order, customer);
        jdbc.update("""
                INSERT INTO cancellation_request(cancellation_request_id,order_id,requested_by_user_id,reason,request_status,requested_at)
                VALUES (?, ?, ?, 'reason', 'PENDING', now())
                """, request, order, customer);
        assertThat(Flyway.configure().dataSource(source).target("11").load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT confirmation_state FROM customer_order WHERE order_id=?", String.class, order)).isEqualTo("LEGACY_UNKNOWN");
        assertThatThrownBy(() -> insertPending(jdbc, order, customer)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE cancellation_request SET request_status='REJECTED', reviewed_by_user_id=?, reviewed_at=now(), decision_reason='reason' WHERE cancellation_request_id=?", customer, request);
        insertPending(jdbc, order, customer);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cancellation_request WHERE order_id=?", Integer.class, order)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("UPDATE customer_order SET confirmation_state='CONFIRMED' WHERE order_id=?", order)).isInstanceOf(DataIntegrityViolationException.class);
    }
    private void insertPending(JdbcTemplate jdbc, UUID order, UUID customer) {
        jdbc.update("INSERT INTO cancellation_request(cancellation_request_id,order_id,requested_by_user_id,reason,request_status,requested_at) VALUES (?,?,?,'reason','PENDING',now())", UUID.randomUUID(), order, customer);
    }
}

