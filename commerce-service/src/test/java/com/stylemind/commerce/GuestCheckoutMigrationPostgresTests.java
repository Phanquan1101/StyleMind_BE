package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class GuestCheckoutMigrationPostgresTests {
    @Container static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("guest_upgrade_test").withUsername("guest_upgrade_test").withPassword("test-only");

    @Test void upgradesPopulatedV8AndEnforcesExactlyOneOwnerAndGuestCheckoutUniqueness() {
        var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(source).target("8").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID id = UUID.randomUUID(), customer = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.customer_order(order_id,order_number,customer_id,checkout_key,order_status,payment_method,accepted_total_vnd,placed_at)
                VALUES (?, ?, ?, 'upgrade-key', 'PLACED', 'COD', 125000, now())
                """, id, "UPGRADE-" + id, customer);
        String checksums = jdbc.queryForObject("SELECT string_agg(checksum::text,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success", String.class);
        assertThat(Flyway.configure().dataSource(source).target("9").load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM public.customer_order WHERE order_id=?", UUID.class, id)).isEqualTo(customer);
        assertThat(jdbc.queryForObject("SELECT string_agg(checksum::text,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success AND version <> '9'", String.class)).isEqualTo(checksums);
        insert(jdbc, null, "a".repeat(64));
        assertThatThrownBy(() -> insert(jdbc, null, "a".repeat(64))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        insert(jdbc, null, "b".repeat(64));
        assertThatThrownBy(() -> insert(jdbc, null, null)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(jdbc, customer, "c".repeat(64))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("""
                INSERT INTO public.payment_event_inbox(event_id,event_fingerprint,order_id,payment_attempt_id,customer_id,payment_method,
                    payment_reference,amount_vnd,confirmed_at,received_at,outcome,reason)
                VALUES (?,repeat('a',64),?,?,NULL,'COD','GUEST',125000,now(),now(),'APPLIED','COD_COLLECTION_CONFIRMED')
                """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        // The owner already applied guest V9. V10 must retain that history and populated guest data.
        String throughV9 = jdbc.queryForObject("SELECT string_agg(checksum::text,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success", String.class);
        assertThat(Flyway.configure().dataSource(source).target("10").load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT string_agg(checksum::text,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success AND version <> '10'", String.class)).isEqualTo(throughV9);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM public.customer_order WHERE order_id=?", UUID.class, id)).isEqualTo(customer);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.customer_order WHERE customer_id IS NULL", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.variant_inventory') IS NOT NULL", Boolean.class)).isTrue();
    }

    private void insert(JdbcTemplate jdbc, UUID customer, String hash) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.customer_order(order_id,order_number,customer_id,guest_session_hash,checkout_key,order_status,payment_method,accepted_total_vnd,placed_at)
                VALUES (?, ?, ?, ?, 'same-guest-key', 'PENDING_PAYMENT', 'COD', 125000, now())
                """, id, "GUEST-" + id, customer, hash);
    }
}
