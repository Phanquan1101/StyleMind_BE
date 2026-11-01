package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import com.stylemind.commerce.enums.PaymentMethod;
import com.stylemind.commerce.repository.inventory.InventoryRepository;
import com.stylemind.commerce.seed.DevelopmentCatalogSeedRunner;
import com.stylemind.commerce.service.inventory.InventoryExpiryTransaction;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "management.health.db.enabled=false",
        "stylemind.commerce.catalog-seed.enabled=true",
        "stylemind.commerce.inventory.expiry-enabled=false",
        "stylemind.commerce.inventory.expiry-scan-ms=3600000"
})
@Testcontainers
class CatalogSeedInventoryPostgresIntegrationTests {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_catalog_inventory_test")
            .withUsername("catalog_inventory_test")
            .withPassword("test-only-not-a-secret");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DevelopmentCatalogSeedRunner seedRunner;
    @Autowired private InventoryRepository inventory;
    @Autowired private InventoryExpiryTransaction expiry;
    @Autowired private TransactionTemplate transactions;

    @Test
    void seedCreatesExpectedCatalogSharesAssetsAndDoesNotResetExistingStock() throws Exception {
        jdbc.update("UPDATE public.variant_inventory SET on_hand_quantity=20,reserved_quantity=0");
        assertThat(count("product")).isEqualTo(381);
        assertThat(count("product_variant")).isEqualTo(2120);
        assertThat(count("image_asset")).isEqualTo(600);
        assertThat(count("variant_image")).isEqualTo(2400);
        assertThat(count("variant_inventory")).isEqualTo(2120);
        assertThat(jdbc.queryForObject("SELECT sum(on_hand_quantity) FROM public.variant_inventory", Long.class))
                .isEqualTo(42400L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.product WHERE name='Aneri - Quần legging nữ - Mẫu 39121' AND product_status='ACTIVE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM public.product_variant v JOIN public.product p ON p.product_id=v.product_id
                WHERE p.name='Aneri - Quần legging nữ - Mẫu 39121' AND v.variant_status='ACTIVE'
                """, Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT v.color) FROM public.product_variant v
                JOIN public.product p ON p.product_id=v.product_id
                WHERE p.name='Aneri - Quần legging nữ - Mẫu 39121'
                """, Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM public.product_variant v
                JOIN public.product p ON p.product_id=v.product_id
                WHERE p.name='Aneri - Quần legging nữ - Mẫu 39121'
                  AND v.color IN ('Hồng','Be','Đỏ','Xanh dương','Vàng')
                """, Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM public.image_asset
                WHERE image_url LIKE '%/e_upscale/e_upscale/e_improve/f_auto/q_auto:best/%'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM public.image_asset
                WHERE image_url LIKE 'https://res.cloudinary.com/%/image/upload/v%'
                """, Integer.class)).isEqualTo(600);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT image_asset_id FROM public.variant_image
                    GROUP BY image_asset_id HAVING count(*) = 4
                ) shared
                """, Integer.class)).isEqualTo(600);

        UUID variantId = firstVariant();
        jdbc.update("UPDATE public.variant_inventory SET on_hand_quantity=7 WHERE variant_id=?", variantId);
        seedRunner.run(new DefaultApplicationArguments());
        assertThat(stock(variantId)).containsExactly(7, 0);
        assertThat(count("product")).isEqualTo(381);
    }

    @Test
    void sepayCodExpiryCancellationAndConcurrentAllocationKeepStockConsistent() throws Exception {
        UUID variantId = firstVariant();
        jdbc.update("UPDATE public.variant_inventory SET on_hand_quantity=20,reserved_quantity=0 WHERE variant_id=?",
                variantId);
        OffsetDateTime now = OffsetDateTime.now();

        UUID sepayOrder = order("SEPAY_VIETQR", "PENDING_PAYMENT");
        transactions.executeWithoutResult(status ->
                assertThat(inventory.allocate(sepayOrder, PaymentMethod.SEPAY_VIETQR,
                        Map.of(variantId, 3), now)).isTrue());
        assertThat(stock(variantId)).containsExactly(20, 3);
        transactions.executeWithoutResult(status -> inventory.consumeSepay(sepayOrder, now.plusMinutes(1)));
        assertThat(stock(variantId)).containsExactly(17, 0);

        UUID codOrder = order("COD", "PLACED");
        transactions.executeWithoutResult(status ->
                assertThat(inventory.allocate(codOrder, PaymentMethod.COD,
                        Map.of(variantId, 2), now)).isTrue());
        assertThat(stock(variantId)).containsExactly(15, 0);
        transactions.executeWithoutResult(status -> inventory.releaseForCancellation(codOrder, now.plusMinutes(1)));
        assertThat(stock(variantId)).containsExactly(17, 0);

        UUID expiredOrder = order("SEPAY_VIETQR", "PENDING_PAYMENT");
        transactions.executeWithoutResult(status ->
                assertThat(inventory.allocate(expiredOrder, PaymentMethod.SEPAY_VIETQR,
                        Map.of(variantId, 4), now.minusMinutes(16))).isTrue());
        expiry.expire(expiredOrder, now);
        assertThat(stock(variantId)).containsExactly(17, 0);
        assertThat(jdbc.queryForObject("SELECT order_status FROM public.customer_order WHERE order_id=?",
                String.class, expiredOrder)).isEqualTo("CANCELLED");

        jdbc.update("UPDATE public.variant_inventory SET on_hand_quantity=20,reserved_quantity=0 WHERE variant_id=?",
                variantId);
        UUID firstOrder = order("COD", "PLACED");
        UUID secondOrder = order("COD", "PLACED");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> allocateAfter(start, firstOrder, variantId, now));
            Future<Boolean> second = executor.submit(() -> allocateAfter(start, secondOrder, variantId, now));
            start.countDown();
            assertThat(new boolean[] {first.get(), second.get()}).containsExactlyInAnyOrder(true, false);
        }
        assertThat(stock(variantId)).containsExactly(5, 0);
    }

    private boolean allocateAfter(CountDownLatch start, UUID orderId, UUID variantId, OffsetDateTime now)
            throws InterruptedException {
        start.await();
        return Boolean.TRUE.equals(transactions.execute(status ->
                inventory.allocate(orderId, PaymentMethod.COD, Map.of(variantId, 15), now)));
    }

    private UUID order(String paymentMethod, String status) {
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.customer_order(order_id,order_number,customer_id,checkout_key,order_status,
                    payment_method,accepted_total_vnd,placed_at)
                VALUES (?,?,?,?,?,?,100000,now())
                """, orderId, "TEST-" + orderId, UUID.randomUUID(), "key-" + orderId, status, paymentMethod);
        return orderId;
    }

    private UUID firstVariant() {
        return jdbc.queryForObject("SELECT variant_id FROM public.product_variant ORDER BY variant_id LIMIT 1",
                UUID.class);
    }

    private int[] stock(UUID variantId) {
        return jdbc.queryForObject("""
                SELECT on_hand_quantity,reserved_quantity FROM public.variant_inventory WHERE variant_id=?
                """, (rs, row) -> new int[] {rs.getInt(1), rs.getInt(2)}, variantId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM public." + table, Integer.class);
    }
}
