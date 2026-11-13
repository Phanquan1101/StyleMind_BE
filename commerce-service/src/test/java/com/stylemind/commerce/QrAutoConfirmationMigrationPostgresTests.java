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
class QrAutoConfirmationMigrationPostgresTests {
    @Container static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("qr_auto_confirmation").withUsername("qr_auto_confirmation").withPassword("test-only");

    @Test void permitsAutomaticConfirmationWithoutAdminAndPreservesExistingOrders() {
        var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(source).target("17").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID existingOrder = UUID.randomUUID(), autoOrder = UUID.randomUUID(), customer = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order(order_id,order_number,customer_id,checkout_key,order_status,payment_method,accepted_total_vnd,placed_at,confirmation_state)
                VALUES (?, 'ORDER-EXISTING', ?, 'key-existing', 'PLACED', 'COD', 10000, now(), 'UNCONFIRMED')
                """, existingOrder, customer);
        assertThat(Flyway.configure().dataSource(source).target("18").load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT confirmation_state FROM customer_order WHERE order_id=?", String.class, existingOrder))
                .isEqualTo("UNCONFIRMED");
        jdbc.update("""
                INSERT INTO customer_order(order_id,order_number,customer_id,checkout_key,order_status,payment_method,accepted_total_vnd,placed_at,confirmation_state,confirmed_at)
                VALUES (?, 'ORDER-AUTO', ?, 'key-auto', 'PROCESSING', 'SEPAY_VIETQR', 10000, now(), 'AUTO_CONFIRMED', now())
                """, autoOrder, customer);
        assertThat(jdbc.queryForObject("SELECT confirmed_by_user_id FROM customer_order WHERE order_id=?", UUID.class, autoOrder)).isNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE customer_order SET confirmed_at=NULL WHERE order_id=?", autoOrder))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
