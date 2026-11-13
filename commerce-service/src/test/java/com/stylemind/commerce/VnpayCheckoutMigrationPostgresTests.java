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
class VnpayCheckoutMigrationPostgresTests {
    @Container static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("vnpay_checkout_upgrade").withUsername("vnpay_checkout_test").withPassword("test-only");

    @Test void upgradeKeepsExistingQrSessionsAndSupportsVnpay() {
        var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(source).target("18").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID owner = UUID.randomUUID(), existing = UUID.randomUUID(), vnpay = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO qr_checkout(checkout_id,customer_id,checkout_key,checkout_status,total_vnd,
                    shipping_snapshot,payment_reference_id,created_at,expires_at,updated_at)
                VALUES (?,?,?,'PREPARING',10000,'{}'::jsonb,?,now(),now()+interval '15 minutes',now())
                """, existing, owner, "existing-vietqr", UUID.randomUUID());
        assertThat(Flyway.configure().dataSource(source).target("19").load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payment_method FROM qr_checkout WHERE checkout_id=?", String.class, existing))
                .isEqualTo("SEPAY_VIETQR");
        jdbc.update("""
                INSERT INTO qr_checkout(checkout_id,customer_id,checkout_key,checkout_status,payment_method,total_vnd,
                    shipping_snapshot,payment_reference_id,created_at,expires_at,updated_at)
                VALUES (?,?,?,'PREPARING','VNPAY',10000,'{}'::jsonb,?,now(),now()+interval '15 minutes',now())
                """, vnpay, owner, "new-vnpay", UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT payment_method FROM qr_checkout WHERE checkout_id=?", String.class, vnpay))
                .isEqualTo("VNPAY");
        assertThatThrownBy(() -> jdbc.update("UPDATE qr_checkout SET payment_method='COD' WHERE checkout_id=?", vnpay))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
