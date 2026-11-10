package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class VariantQuantityMergeMigrationPostgresTests {
    @Container static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("quantity_merge_upgrade").withUsername("quantity_merge_test").withPassword("test-only");

    @Test void upgradesDeployedQrSchemaAndPreservesUnavailableQuantities() {
        var source = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(source).target("13").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID category = UUID.randomUUID(), brand = UUID.randomUUID(), product = UUID.randomUUID();
        UUID variant = UUID.randomUUID(), owner = UUID.randomUUID(), checkout = UUID.randomUUID();
        jdbc.update("INSERT INTO category(category_id,name,slug,is_active) VALUES (?,'Tops','tops',true)", category);
        jdbc.update("INSERT INTO brand(brand_id,name,slug,is_active) VALUES (?,'Brand','brand',true)", brand);
        jdbc.update("INSERT INTO product(product_id,category_id,brand_id,name,product_status) VALUES (?,?,?,'Shirt','ACTIVE')", product, category, brand);
        jdbc.update("INSERT INTO product_variant(variant_id,product_id,sku,unit_price_vnd,variant_status) VALUES (?,?,'SHIRT',100000,'ACTIVE')", variant, product);
        jdbc.update("INSERT INTO variant_inventory(variant_id,on_hand_quantity,reserved_quantity,updated_at) VALUES (?,10,2,now())", variant);
        jdbc.update("""
                INSERT INTO qr_checkout(checkout_id,customer_id,checkout_key,checkout_status,total_vnd,
                    shipping_snapshot,payment_reference_id,created_at,expires_at,updated_at)
                VALUES (?,?,'held-qr','AWAITING_PAYMENT',200000,'{}',?,now(),now()+interval '15 minutes',now())
                """, checkout, owner, checkout);
        jdbc.update("INSERT INTO qr_checkout_item VALUES (?,?,2,100000,'{}','ACTIVE')", checkout, variant);
        String history = jdbc.queryForObject("SELECT string_agg(checksum::text,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success", String.class);

        assertThat(Flyway.configure().dataSource(source).load().migrate().migrationsExecuted).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT quantity FROM product_variant WHERE variant_id=?", Integer.class, variant)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT reservation_status FROM qr_checkout_item WHERE checkout_id=?", String.class, checkout)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT checkout_status FROM qr_checkout WHERE checkout_id=?", String.class, checkout)).isEqualTo("AWAITING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.variant_inventory') IS NULL AND to_regclass('public.inventory_reservation') IS NULL AND to_regclass('public.branch') IS NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT string_agg(checksum::text,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success AND installed_rank<=13", String.class)).isEqualTo(history);
    }
}
