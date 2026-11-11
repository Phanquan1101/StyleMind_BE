package com.stylemind.payment;

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
class GuestPaymentMigrationPostgresTests {
    @Container static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("guest_payment_upgrade").withUsername("payment_test").withPassword("test-only");

    @Test void upgradesPopulatedCustomerAndGuestAttemptsThroughTheRefundMigration() {
        var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(source).target("1").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID customer = UUID.randomUUID(), id = insert(jdbc, customer);
        String checksum = jdbc.queryForObject("SELECT checksum::text FROM flyway_schema_history WHERE version='1'", String.class);
        assertThatThrownBy(() -> insert(jdbc, null)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(Flyway.configure().dataSource(source).target("2").load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT checksum::text FROM flyway_schema_history WHERE version='1'", String.class)).isEqualTo(checksum);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM public.payment_attempt WHERE payment_attempt_id=?", UUID.class, id)).isEqualTo(customer);
        UUID guest = insert(jdbc, null);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM public.payment_attempt WHERE payment_attempt_id=?", UUID.class, guest)).isNull();
        assertThat(Flyway.configure().dataSource(source).target("3").load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM public.payment_attempt WHERE payment_attempt_id=?", UUID.class, id)).isEqualTo(customer);
        assertThat(jdbc.queryForObject("SELECT customer_id FROM public.payment_attempt WHERE payment_attempt_id=?", UUID.class, guest)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.refund_case", Long.class)).isZero();
        UUID transaction = UUID.randomUUID(), refund = UUID.randomUUID();
        jdbc.update("UPDATE payment_attempt SET attempt_status='SETTLED',confirmed_at=now() WHERE payment_attempt_id=?", id);
        jdbc.update("INSERT INTO financial_transaction(transaction_id,payment_attempt_id,actual_amount_vnd,settled_at) VALUES (?,?,125000,now())", transaction, id);
        jdbc.update("""
                INSERT INTO refund_case(refund_id,source_type,source_id,order_id,customer_id,transaction_id,amount_vnd,status,updated_at)
                SELECT ?,'RETURN',?,order_id,customer_id,?,50000,'AWAITING_DETAILS',now() FROM payment_attempt WHERE payment_attempt_id=?
                """, refund, UUID.randomUUID(), transaction, id);
        assertThat(Flyway.configure().dataSource(source).target("4").load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM refund_case WHERE refund_id=?", String.class, refund)).isEqualTo("AWAITING_DETAILS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refund_transfer_receipt", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT actual_amount_vnd FROM financial_transaction WHERE transaction_id=?", Long.class, transaction)).isEqualTo(125000);
    }

    private UUID insert(JdbcTemplate jdbc, UUID customer) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.payment_attempt(payment_attempt_id,order_id,customer_id,payment_reference,request_key,payment_method,
                    expected_amount_vnd,attempt_status,generated_at) VALUES (?,?,?,?,?,'COD',125000,'PENDING',now())
                """, id, UUID.randomUUID(), customer, "GUEST-" + id, "initial-" + id);
        return id;
    }
}

