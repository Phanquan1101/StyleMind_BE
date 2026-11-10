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
class QrCheckoutMigrationPostgresTests {
    @Container static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("qr_checkout_upgrade").withUsername("qr_checkout_test").withPassword("test-only");

    @Test void upgradePreservesOrdersAndEnforcesCheckoutOwnershipAndIdempotency() {
        var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(source).target("11").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID order = UUID.randomUUID(), owner = UUID.randomUUID();
        jdbc.update("INSERT INTO customer_order(order_id,order_number,customer_id,checkout_key,order_status,payment_method,accepted_total_vnd,placed_at) VALUES (?,'LEGACY',?,'legacy-key','PLACED','COD',10000,now())", order, owner);
        assertThat(Flyway.configure().dataSource(source).load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT order_status FROM customer_order WHERE order_id=?", String.class, order)).isEqualTo("PLACED");
        insert(jdbc, owner, "new-key");
        assertThatThrownBy(() -> insert(jdbc, owner, "new-key")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO qr_checkout(checkout_id,checkout_key,checkout_status,total_vnd,shipping_snapshot,payment_reference_id,created_at,expires_at,updated_at) VALUES (?,'bad','PREPARING',10000,'{}'::jsonb,?,now(),now()+interval '15 minutes',now())", UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order", Integer.class)).isEqualTo(1);
    }
    private void insert(JdbcTemplate jdbc, UUID owner, String key) {
        jdbc.update("INSERT INTO qr_checkout(checkout_id,customer_id,checkout_key,checkout_status,total_vnd,shipping_snapshot,payment_reference_id,created_at,expires_at,updated_at) VALUES (?,?,?,'PREPARING',10000,'{}'::jsonb,?,now(),now()+interval '15 minutes',now())", UUID.randomUUID(), owner, key, UUID.randomUUID());
    }
}
