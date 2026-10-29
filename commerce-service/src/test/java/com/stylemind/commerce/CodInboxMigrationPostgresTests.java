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
class CodInboxMigrationPostgresTests {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("cod_upgrade_test").withUsername("upgrade_test").withPassword("test-only");

    @Test void upgradesExistingV7ReceiptWithoutRewritingDataAndAcceptsOnlyApprovedMethods() {
        var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(source).target("7").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID existing = UUID.randomUUID();
        insert(jdbc, existing, "SEPAY_VIETQR");
        String checksum = jdbc.queryForObject("SELECT checksum::text FROM flyway_schema_history WHERE version='7'", String.class);
        assertThatThrownBy(() -> insert(jdbc, UUID.randomUUID(), "COD"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var upgrade = Flyway.configure().dataSource(source).load().migrate();
        assertThat(upgrade.migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT checksum::text FROM flyway_schema_history WHERE version='7'", String.class)).isEqualTo(checksum);
        assertThat(jdbc.queryForObject("SELECT payment_reference FROM public.payment_event_inbox WHERE event_id=?", String.class, existing)).isEqualTo("MIGRATION-TEST");
        insert(jdbc, UUID.randomUUID(), "COD");
        assertThatThrownBy(() -> insert(jdbc, UUID.randomUUID(), "UNKNOWN"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.payment_event_inbox", Integer.class)).isEqualTo(2);
    }

    private void insert(JdbcTemplate jdbc, UUID id, String method) {
        jdbc.update("""
                INSERT INTO public.payment_event_inbox(event_id,event_fingerprint,order_id,payment_attempt_id,customer_id,
                    payment_method,payment_reference,amount_vnd,confirmed_at,received_at,outcome,reason)
                VALUES (?,repeat('a',64),?,?,?,?,'MIGRATION-TEST',125000,now(),now(),'APPLIED','MIGRATION_TEST')
                """, id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), method);
    }
}
