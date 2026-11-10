package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.stylemind.commerce.dto.order.AddressSnapshot;
import com.stylemind.commerce.service.order.IdentityAddressClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"management.health.db.enabled=false",
        "stylemind.commerce.inventory.expiry-enabled=false",
        "stylemind.security.service-token=isolated-test-token-01234567890123456789"})
@AutoConfigureMockMvc
@Testcontainers
@Import(BranchCartOrderPostgresIntegrationTests.TestConfig.class)
class BranchCartOrderPostgresIntegrationTests {

    private static final PostgreSQLContainer<?> POSTGRES = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("stylemind_commerce_bco_test")
            .withUsername("commerce_bco_test")
            .withPassword("test-only-not-a-secret");
    private static final RSAKey SIGNING_KEY = generateRsaKey();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OTHER_CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ADDRESS_ID = UUID.randomUUID();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER = POSTGRES;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.stylemind.commerce.service.order.OrderService orderService;
    @Autowired private com.stylemind.commerce.service.order.QrCheckoutReservationService qrReservations;
    @Autowired private com.stylemind.commerce.service.order.PaymentEventService paymentEvents;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.stylemind.commerce.client.payment.PaymentClient payment;

    private static final java.util.concurrent.atomic.AtomicInteger IDENTITY_CALLS = new java.util.concurrent.atomic.AtomicInteger();

    private UUID variantId;
    private UUID inactiveVariantId;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM public.cancellation_refund_obligation");
        jdbc.update("DELETE FROM public.qr_checkout_item");
        jdbc.update("DELETE FROM public.qr_checkout");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_cod_confirmation_test ON public.order_status_history");
        org.mockito.Mockito.reset(payment);
        org.mockito.Mockito.when(payment.create(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            com.stylemind.commerce.client.payment.OrderPaymentRequest request = invocation.getArgument(0);
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM public.customer_order WHERE order_id = ?",
                    Integer.class, request.orderId())).isEqualTo(1);
            return new com.stylemind.commerce.client.payment.PaymentAttemptResponse(UUID.randomUUID(), request.orderId(),
                    request.paymentMethod(), request.acceptedTotalVnd(), "SMTEST", "PENDING", null, null, null);
        });
        jdbc.update("DELETE FROM public.payment_event_inbox");
        jdbc.update("DELETE FROM public.cancellation_request");
        jdbc.update("DELETE FROM public.inventory_reservation");
        jdbc.update("DELETE FROM public.order_status_history");
        jdbc.update("DELETE FROM public.order_item");
        jdbc.update("DELETE FROM public.order_address_snapshot");
        jdbc.update("DELETE FROM public.customer_order");
        jdbc.update("DELETE FROM public.cart_item");
        jdbc.update("DELETE FROM public.cart");
        jdbc.update("DELETE FROM public.variant_image");
        jdbc.update("DELETE FROM public.image_asset");
        jdbc.update("DELETE FROM public.variant_inventory");
        jdbc.update("DELETE FROM public.product_variant");
        jdbc.update("DELETE FROM public.product");
        jdbc.update("DELETE FROM public.branch");
        jdbc.update("DELETE FROM public.category");
        jdbc.update("DELETE FROM public.brand");
        UUID categoryId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        inactiveVariantId = UUID.randomUUID();
        jdbc.update("INSERT INTO public.category(category_id, name, slug, is_active) VALUES (?, 'Tops', 'tops', true)",
                categoryId);
        jdbc.update("INSERT INTO public.brand(brand_id, name, slug, is_active) VALUES (?, 'StyleMind', 'stylemind', true)",
                brandId);
        jdbc.update("""
                INSERT INTO public.product(product_id, category_id, brand_id, name, product_status)
                VALUES (?, ?, ?, 'Linen Shirt', 'ACTIVE')
                """, productId, categoryId, brandId);
        jdbc.update("""
                INSERT INTO public.product_variant(variant_id, product_id, sku, color, size, unit_price_vnd, variant_status)
                VALUES (?, ?, 'SKU-A', 'White', 'M', 100000, 'ACTIVE')
                """, variantId, productId);
        jdbc.update("""
                INSERT INTO public.product_variant(variant_id, product_id, sku, unit_price_vnd, variant_status)
                VALUES (?, ?, 'SKU-X', 90000, 'INACTIVE')
                """, inactiveVariantId, productId);
        jdbc.update("""
                INSERT INTO public.variant_inventory(variant_id, on_hand_quantity, reserved_quantity, updated_at)
                VALUES (?, 100, 0, now()), (?, 100, 0, now())
                """, variantId, inactiveVariantId);
    }

    @Test
    void prepareQrCheckoutUsesServerPriceAndAddressWithoutCreatingOrder() {
        var command = new com.stylemind.commerce.validation.order.OrderRequestValidator.CheckoutCommand(
                "qr-prepare-01", ADDRESS_ID, com.stylemind.commerce.enums.PaymentMethod.SEPAY_VIETQR);
        var selection = new com.stylemind.commerce.dto.order.CheckoutSelection(List.of(), variantId, 2);
        UUID checkout = orderService.prepareQrCheckout(CUSTOMER_ID, "Bearer test", command, selection);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE order_id=?", Integer.class, checkout)).isZero();
        assertThat(jdbc.queryForObject("SELECT total_vnd FROM qr_checkout WHERE checkout_id=?", Long.class, checkout)).isEqualTo(200000L);
        assertThat(jdbc.queryForObject("SELECT shipping_snapshot->>'recipientName' FROM qr_checkout WHERE checkout_id=?", String.class, checkout)).isEqualTo("Nguyen Van A");
        assertThat(jdbc.queryForObject("SELECT product_snapshot->>'sku' FROM qr_checkout_item WHERE checkout_id=?", String.class, checkout)).isEqualTo("SKU-A");
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(2);
        org.mockito.Mockito.verifyNoInteractions(payment);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderService.prepareQrCheckout(CUSTOMER_ID, "Bearer test", command, selection))
                .isInstanceOf(com.stylemind.commerce.errors.ApiOperationException.class);
        var cod = new com.stylemind.commerce.validation.order.OrderRequestValidator.CheckoutCommand(
                command.checkoutKey(), ADDRESS_ID, com.stylemind.commerce.enums.PaymentMethod.COD);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderService.checkoutSelection(CUSTOMER_ID, "Bearer test", cod, selection))
                .isInstanceOf(com.stylemind.commerce.errors.ApiOperationException.class);
    }

    @Test
    void prepareQrCheckoutRejectsInsufficientStockAndCod() {
        jdbc.update("UPDATE variant_inventory SET on_hand_quantity=0 WHERE variant_id=?", variantId);
        var selection = new com.stylemind.commerce.dto.order.CheckoutSelection(List.of(), variantId, 1);
        var qr = new com.stylemind.commerce.validation.order.OrderRequestValidator.CheckoutCommand(
                "qr-no-stock-01", ADDRESS_ID, com.stylemind.commerce.enums.PaymentMethod.SEPAY_VIETQR);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderService.prepareQrCheckout(CUSTOMER_ID, "Bearer test", qr, selection))
                .isInstanceOf(com.stylemind.commerce.errors.ApiOperationException.class);
        var cod = new com.stylemind.commerce.validation.order.OrderRequestValidator.CheckoutCommand(
                "qr-no-cod-01", ADDRESS_ID, com.stylemind.commerce.enums.PaymentMethod.COD);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderService.prepareQrCheckout(CUSTOMER_ID, "Bearer test", cod, selection))
                .isInstanceOf(com.stylemind.commerce.errors.ApiOperationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM qr_checkout", Integer.class)).isZero();
        org.mockito.Mockito.verifyNoInteractions(payment);
    }

    @Test
    void qrAndCodCannotUseTheSameCheckoutKeyConcurrently() throws Exception {
        var selection = new com.stylemind.commerce.dto.order.CheckoutSelection(List.of(), variantId, 1);
        var qr = new com.stylemind.commerce.validation.order.OrderRequestValidator.CheckoutCommand(
                "qr-cod-race-01", ADDRESS_ID, com.stylemind.commerce.enums.PaymentMethod.SEPAY_VIETQR);
        var cod = new com.stylemind.commerce.validation.order.OrderRequestValidator.CheckoutCommand(
                "qr-cod-race-01", ADDRESS_ID, com.stylemind.commerce.enums.PaymentMethod.COD);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                try { orderService.prepareQrCheckout(CUSTOMER_ID, "Bearer test", qr, selection); return true; }
                catch (com.stylemind.commerce.errors.ApiOperationException conflict) { return false; }
            });
            var second = pool.submit(() -> {
                start.await();
                try { orderService.checkoutSelection(CUSTOMER_ID, "Bearer test", cod, selection); return true; }
                catch (com.stylemind.commerce.errors.ApiOperationException conflict) { return false; }
            });
            start.countDown();
            assertThat(first.get(20, java.util.concurrent.TimeUnit.SECONDS))
                    .isNotEqualTo(second.get(20, java.util.concurrent.TimeUnit.SECONDS));
        }
        int total = jdbc.queryForObject("SELECT (SELECT count(*) FROM customer_order WHERE checkout_key='qr-cod-race-01') + (SELECT count(*) FROM qr_checkout WHERE checkout_key='qr-cod-race-01')", Integer.class);
        assertThat(total).isEqualTo(1);
    }

    @Test
    void legacyGuestEndpointDoesNotCreateVietQrOrderBeforePayment() throws Exception {
        String guest = "guest-session-0123456789abcdef0123456789abcdef";
        mockMvc.perform(post("/api/v1/guest/orders/buy-now").header("X-Guest-Session-Key", guest)
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkout_key\":\"guest-legacy-qr-01\",\"payment_method\":\"SEPAY_VIETQR\",\"shipping_address\":{\"recipient_name\":\"An\",\"recipient_phone\":\"0987654321\",\"address_line\":\"Ha Noi\"},\"variant_id\":\"" + variantId + "\",\"quantity\":1}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order", Integer.class)).isZero();
    }

    @Test
    void guestQrCheckoutChecksSessionAndCreatesOrderOnlyAfterPayment() throws Exception {
        String guest = "guest-session-0123456789abcdef0123456789abcdef";
        var prepared = mockMvc.perform(post("/api/v1/guest/qr-checkouts/buy-now")
                .header("X-Guest-Session-Key", guest).contentType(MediaType.APPLICATION_JSON)
                .content("{\"checkout_key\":\"guest-qr-flow-01\",\"payment_method\":\"SEPAY_VIETQR\",\"shipping_address\":{\"recipient_name\":\"An\",\"recipient_phone\":\"0987654321\",\"address_line\":\"Ha Noi\"},\"variant_id\":\"" + variantId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(json(prepared).path("checkoutId").asText()), attemptId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/guest/qr-checkouts/{id}", id).header("X-Guest-Session-Key", "other-session-0123456789abcdef0123456789abcdef"))
                .andExpect(status().isNotFound());
        org.mockito.Mockito.reset(payment);
        org.mockito.Mockito.when(payment.create(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.stylemind.commerce.client.payment.PaymentAttemptResponse(attemptId, id, "SEPAY_VIETQR", 100000,
                        "SMGUEST", "PENDING", Instant.now().plusSeconds(800), "code", "qr"));
        mockMvc.perform(post("/api/v1/guest/qr-checkouts/{id}/payment-attempt", id).header("X-Guest-Session-Key", guest))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order", Integer.class)).isZero();
        var event = new com.stylemind.commerce.dto.order.PaymentConfirmedEvent(UUID.randomUUID(), "PaymentConfirmed", attemptId,
                id, null, "SEPAY_VIETQR", "SMGUEST", 100000, Instant.now());
        assertThat(paymentEvents.receive(event, Instant.now()).outcome()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT order_status FROM customer_order WHERE order_id=?", String.class, id)).isEqualTo("PAID");
    }

    @Test
    void legacyOrderEndpointsCannotCreateNewVietQrOrdersBeforePayment() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}");
        var result = postJson("/api/v1/orders", customerToken(), "{\"checkout_key\":\"legacy-qr-block-01\",\"shipping_address_id\":\""
                + ADDRESS_ID + "\",\"payment_method\":\"SEPAY_VIETQR\"}");
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order", Integer.class)).isZero();
    }

    @Test
    void paidQrCancellationRecordsRefundAndRestoresStockOnlyOnce() throws Exception {
        UUID id = paidQrOrder();
        JsonNode request = json(postJson("/api/v1/orders/" + id + "/cancellation-requests", customerToken(), "{\"reason\":\"Khong con nhu cau\"}"));
        String requestId = request.path("cancellation_request_id").asText();
        mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"))
                .andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) {
            var result = mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}", requestId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"));
            if (i == 0) result.andExpect(status().isOk()).andExpect(jsonPath("$.refund_status").value("PENDING_REFUND"));
            else result.andExpect(status().isConflict());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cancellation_refund_obligation WHERE order_id=?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_vnd FROM cancellation_refund_obligation WHERE order_id=?", Long.class, id)).isEqualTo(100000L);
        assertThat(jdbc.queryForObject("SELECT order_status FROM customer_order WHERE order_id=?", String.class, id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT reservation_status FROM qr_checkout_item WHERE checkout_id=?", String.class, id)).isEqualTo("RELEASED");
        mockMvc.perform(get("/api/v1/orders/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancellation_request.refund_status").value("PENDING_REFUND"));
    }

    @Test
    void paidQrCancellationRequiresEvidenceAndCannotApproveAfterShipping() throws Exception {
        UUID id = paidQrOrder();
        JsonNode request = json(postJson("/api/v1/orders/" + id + "/cancellation-requests", customerToken(), "{\"reason\":\"Khong con nhu cau\"}"));
        jdbc.update("DELETE FROM payment_event_inbox WHERE order_id=?", id);
        mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}", request.path("cancellation_request_id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_RECONCILIATION_REQUIRED"));
        jdbc.update("UPDATE customer_order SET order_status='SHIPPED' WHERE order_id=?", id);
        mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}", request.path("cancellation_request_id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cancellation_refund_obligation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(99);
    }

    @Test
    void concurrentPaidQrApprovalCreatesOneRefundObligation() throws Exception {
        UUID id = paidQrOrder();
        JsonNode request = json(postJson("/api/v1/orders/" + id + "/cancellation-requests", customerToken(), "{\"reason\":\"Khong con nhu cau\"}"));
        String requestId = request.path("cancellation_request_id").asText();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> approve = () -> {
                start.await();
                return mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(approve);
            var second = pool.submit(approve);
            start.countDown();
            assertThat(List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cancellation_refund_obligation", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(100);
    }

    @Test
    void paidQrCancellationRollsBackRefundAndInventoryWhenHistoryFails() throws Exception {
        UUID id = paidQrOrder();
        JsonNode request = json(postJson("/api/v1/orders/" + id + "/cancellation-requests", customerToken(), "{\"reason\":\"Khong con nhu cau\"}"));
        String requestId = request.path("cancellation_request_id").asText();
        jdbc.execute("CREATE OR REPLACE FUNCTION public.fail_paid_cancel_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.to_status='CANCELLED' THEN RAISE EXCEPTION 'test-only cancellation failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_paid_cancel BEFORE INSERT ON public.order_status_history FOR EACH ROW EXECUTE FUNCTION public.fail_paid_cancel_fn()");
        try {
            mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}", requestId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"))
                    .andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("DROP TRIGGER fail_paid_cancel ON public.order_status_history");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cancellation_refund_obligation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT order_status FROM customer_order WHERE order_id=?", String.class, id)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT request_status FROM cancellation_request WHERE cancellation_request_id=?", String.class, UUID.fromString(requestId))).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(99);
    }

    private UUID paidQrOrder() throws Exception {
        var prepared = postJson("/api/v1/qr-checkouts/buy-now", customerToken(),
                "{\"checkout_key\":\"qr-refund-test\",\"shipping_address_id\":\"" + ADDRESS_ID
                        + "\",\"payment_method\":\"SEPAY_VIETQR\",\"variant_id\":\"" + variantId + "\",\"quantity\":1}");
        assertThat(prepared.getResponse().getStatus()).isEqualTo(201);
        UUID id = UUID.fromString(json(prepared).path("checkoutId").asText());
        UUID attemptId = UUID.randomUUID();
        org.mockito.Mockito.reset(payment);
        org.mockito.Mockito.when(payment.create(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.stylemind.commerce.client.payment.PaymentAttemptResponse(attemptId, id,
                        "SEPAY_VIETQR", 100000, "SMREFUND", "PENDING", Instant.now().plusSeconds(800), "code", "qr"));
        assertThat(postJson("/api/v1/qr-checkouts/" + id + "/payment-attempt", customerToken(), "{}").getResponse().getStatus()).isEqualTo(200);
        var event = new com.stylemind.commerce.dto.order.PaymentConfirmedEvent(UUID.randomUUID(), "PaymentConfirmed", attemptId,
                id, CUSTOMER_ID, "SEPAY_VIETQR", "SMREFUND", 100000, Instant.now());
        assertThat(paymentEvents.receive(event, Instant.now()).outcome()).isEqualTo("APPLIED");
        return id;
    }

    @Test
    void qrCheckoutApiCreatesPaidOrderOnlyAfterPaymentEvent() throws Exception {
        var prepared = postJson("/api/v1/qr-checkouts/buy-now", customerToken(),
                "{\"checkout_key\":\"qr-api-paid-01\",\"shipping_address_id\":\"" + ADDRESS_ID
                        + "\",\"payment_method\":\"SEPAY_VIETQR\",\"variant_id\":\"" + variantId + "\",\"quantity\":1}");
        assertThat(prepared.getResponse().getStatus()).isEqualTo(201);
        UUID id = UUID.fromString(json(prepared).path("checkoutId").asText());
        UUID attemptId = UUID.randomUUID();
        org.mockito.Mockito.reset(payment);
        org.mockito.Mockito.when(payment.create(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.stylemind.commerce.client.payment.PaymentAttemptResponse(attemptId, id,
                        "SEPAY_VIETQR", 100000, "SMTEST", "PENDING", Instant.now().plusSeconds(800), "code", "qr"));
        assertThat(postJson("/api/v1/qr-checkouts/" + id + "/payment-attempt", customerToken(), "{}").getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order", Integer.class)).isZero();
        var event = new com.stylemind.commerce.dto.order.PaymentConfirmedEvent(UUID.randomUUID(), "PaymentConfirmed", attemptId,
                id, CUSTOMER_ID, "SEPAY_VIETQR", "SMTEST", 100000, Instant.now());
        assertThat(paymentEvents.receive(event, Instant.now()).outcome()).isEqualTo("APPLIED");
        mockMvc.perform(get("/api/v1/qr-checkouts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ORDER_CREATED"));
        assertThat(jdbc.queryForObject("SELECT order_status FROM customer_order WHERE order_id=?", String.class, id)).isEqualTo("PAID");
        assertThat(postJson("/api/v1/qr-checkouts/" + id + "/cancel", customerToken(), "{}").getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void qrCheckoutApiAllowsOwnerToCancelWithoutCreatingAnOrder() throws Exception {
        var prepared = postJson("/api/v1/qr-checkouts/buy-now", customerToken(),
                "{\"checkout_key\":\"qr-api-test-01\",\"shipping_address_id\":\"" + ADDRESS_ID
                        + "\",\"payment_method\":\"SEPAY_VIETQR\",\"variant_id\":\"" + variantId + "\",\"quantity\":1}");
        assertThat(prepared.getResponse().getStatus()).isEqualTo(201);
        String id = json(prepared).path("checkoutId").asText();
        assertThat(id).isNotBlank();
        mockMvc.perform(get("/api/v1/qr-checkouts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken())))
                .andExpect(status().isNotFound());
        org.mockito.Mockito.reset(payment);
        org.mockito.Mockito.when(payment.create(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            com.stylemind.commerce.client.payment.OrderPaymentRequest request = invocation.getArgument(0);
            return new com.stylemind.commerce.client.payment.PaymentAttemptResponse(UUID.randomUUID(), request.orderId(),
                    request.paymentMethod(), request.acceptedTotalVnd(), "SMTEST", "PENDING", Instant.now().plusSeconds(800), "test", "qr");
        });
        assertThat(postJson("/api/v1/qr-checkouts/" + id + "/payment-attempt", customerToken(), "{}").getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson("/api/v1/qr-checkouts/" + id + "/cancel", customerToken(), "{}").getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson("/api/v1/qr-checkouts/" + id + "/payment-attempt", customerToken(), "{}").getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isZero();
    }

    @Test
    void lateQrEventRecoversPaymentReferenceAfterResponseWasLost() {
        UUID checkout = UUID.randomUUID(), attemptId = UUID.randomUUID();
        var now = java.time.OffsetDateTime.now();
        qrReservations.reserve(checkout, CUSTOMER_ID, null, "qr-recover-01", 100000, "{}",
                List.of(new com.stylemind.commerce.service.order.QrCheckoutReservationService.Item(variantId, 1, 100000, "{}")), now);
        qrReservations.cancel(checkout, CUSTOMER_ID, null, now);
        org.mockito.Mockito.reset(payment);
        org.mockito.Mockito.when(payment.create(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.stylemind.commerce.client.payment.PaymentAttemptResponse(attemptId, checkout,
                        "SEPAY_VIETQR", 100000, "SMTEST", "SETTLED", now.plusMinutes(5).toInstant(), null, null));
        var event = new com.stylemind.commerce.dto.order.PaymentConfirmedEvent(UUID.randomUUID(), "PaymentConfirmed", attemptId,
                checkout, CUSTOMER_ID, "SEPAY_VIETQR", "SMTEST", 100000, now.toInstant());
        assertThat(paymentEvents.receive(event, now.toInstant()).outcome()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT checkout_status FROM qr_checkout WHERE checkout_id=?", String.class, checkout)).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order", Integer.class)).isZero();
    }

    @Test
    void qrPaymentConfirmationCreatesOnePaidOrderFromTrustedSnapshots() {
        var command = new com.stylemind.commerce.validation.order.OrderRequestValidator.CheckoutCommand(
                "qr-paid-test-01", ADDRESS_ID, com.stylemind.commerce.enums.PaymentMethod.SEPAY_VIETQR);
        var selection = new com.stylemind.commerce.dto.order.CheckoutSelection(List.of(), variantId, 2);
        UUID checkout = orderService.prepareQrCheckout(CUSTOMER_ID, "Bearer test", command, selection);
        var now = java.time.OffsetDateTime.now();
        UUID attemptId = UUID.randomUUID();
        var attempt = new com.stylemind.commerce.client.payment.PaymentAttemptResponse(attemptId, checkout,
                "SEPAY_VIETQR", 200000, "SMTEST", "PENDING", now.plusMinutes(10).toInstant(), "code", "qr");
        assertThat(qrReservations.recordPaymentAttempt(checkout, CUSTOMER_ID, attempt, now)).isTrue();
        var event = new com.stylemind.commerce.dto.order.PaymentConfirmedEvent(UUID.randomUUID(), "PaymentConfirmed", attemptId,
                checkout, CUSTOMER_ID, "SEPAY_VIETQR", "SMTEST", 200000, now.toInstant());
        assertThat(paymentEvents.receive(event, now.toInstant()).outcome()).isEqualTo("APPLIED");
        assertThat(paymentEvents.receive(event, now.toInstant()).outcome()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT order_status FROM customer_order WHERE order_id=?", String.class, checkout)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_item WHERE order_id=?", Integer.class, checkout)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(98);
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservation WHERE order_id=? AND reservation_status='CONSUMED'", Integer.class, checkout)).isEqualTo(1);
    }

    @Test
    void qrPaymentAttemptRacingCancellationIsRecordedButNotMadeAvailable() {
        UUID checkout = UUID.randomUUID();
        var now = java.time.OffsetDateTime.now();
        qrReservations.reserve(checkout, CUSTOMER_ID, null, "qr-attempt-race-01", 100000, "{}",
                List.of(new com.stylemind.commerce.service.order.QrCheckoutReservationService.Item(variantId, 1, 100000, "{}")), now);
        qrReservations.cancel(checkout, CUSTOMER_ID, null, now);
        UUID attemptId = UUID.randomUUID();
        var attempt = new com.stylemind.commerce.client.payment.PaymentAttemptResponse(attemptId, checkout,
                "SEPAY_VIETQR", 100000, "SMTEST", "PENDING", null, null, null);
        assertThat(qrReservations.recordPaymentAttempt(checkout, CUSTOMER_ID, attempt, now)).isFalse();
        assertThat(jdbc.queryForObject("SELECT checkout_status FROM qr_checkout WHERE checkout_id=?", String.class, checkout)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT shipping_snapshot->'paymentAttempt'->>'id' FROM qr_checkout WHERE checkout_id=?", String.class, checkout)).isEqualTo(attemptId.toString());
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order", Integer.class)).isZero();
    }

    @Test
    void insufficientQrStockRollsBackEveryReservation() {
        UUID secondVariant = UUID.randomUUID();
        UUID product = jdbc.queryForObject("SELECT product_id FROM product_variant WHERE variant_id=?", UUID.class, variantId);
        jdbc.update("INSERT INTO product_variant(variant_id,product_id,sku,unit_price_vnd,variant_status) VALUES (?,?,'QR-EMPTY',100000,'ACTIVE')", secondVariant, product);
        jdbc.update("INSERT INTO variant_inventory(variant_id,on_hand_quantity,reserved_quantity,updated_at) VALUES (?,0,0,now())", secondVariant);
        UUID checkout = UUID.randomUUID();
        var now = java.time.OffsetDateTime.now();
        var items = List.of(new com.stylemind.commerce.service.order.QrCheckoutReservationService.Item(variantId, 1, 100000, "{}"),
                new com.stylemind.commerce.service.order.QrCheckoutReservationService.Item(secondVariant, 1, 100000, "{}"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> qrReservations.reserve(checkout, CUSTOMER_ID, null,
                "qr-no-stock", 200000, "{}", items, now)).isInstanceOf(com.stylemind.commerce.errors.ApiOperationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM qr_checkout WHERE checkout_id=?", Integer.class, checkout)).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isZero();
    }

    @Test
    void qrCheckoutReservesWithoutCreatingOrderAndCancelsOnlyForOwner() {
        UUID checkout = UUID.randomUUID();
        var now = java.time.OffsetDateTime.now();
        qrReservations.reserve(checkout, CUSTOMER_ID, null, "qr-reservation-test", 200000, "{}",
                List.of(new com.stylemind.commerce.service.order.QrCheckoutReservationService.Item(variantId, 2, 100000, "{}")), now);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE order_id=?", Integer.class, checkout)).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> qrReservations.cancel(checkout, OTHER_CUSTOMER_ID, null, now))
                .isInstanceOf(com.stylemind.commerce.errors.ApiOperationException.class);
        qrReservations.cancel(checkout, CUSTOMER_ID, null, now);
        qrReservations.cancel(checkout, CUSTOMER_ID, null, now);
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isZero();
        assertThat(jdbc.queryForObject("SELECT checkout_status FROM qr_checkout WHERE checkout_id=?", String.class, checkout)).isEqualTo("CANCELLED");
    }

    @Test
    void expiredQrCheckoutReleasesStockOnceAndDoesNotCreateOrder() {
        UUID checkout = UUID.randomUUID();
        var now = java.time.OffsetDateTime.now();
        qrReservations.reserve(checkout, CUSTOMER_ID, null, "qr-expiry-test", 200000, "{}",
                List.of(new com.stylemind.commerce.service.order.QrCheckoutReservationService.Item(variantId, 2, 100000, "{}")), now);
        qrReservations.expire(checkout, now.plusMinutes(14));
        assertThat(qrReservations.expiredCheckoutIds(now.plusMinutes(14))).doesNotContain(checkout);
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(2);
        assertThat(qrReservations.expiredCheckoutIds(now.plusMinutes(16))).contains(checkout);
        qrReservations.expire(checkout, now.plusMinutes(16));
        qrReservations.expire(checkout, now.plusMinutes(16));
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isZero();
        assertThat(jdbc.queryForObject("SELECT checkout_status FROM qr_checkout WHERE checkout_id=?", String.class, checkout)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT reservation_status FROM qr_checkout_item WHERE checkout_id=?", String.class, checkout)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE order_id=?", Integer.class, checkout)).isZero();
    }

    @Test
    void qrCancellationRacingExpiryReleasesReservationOnce() throws Exception {
        UUID checkout = UUID.randomUUID();
        var now = java.time.OffsetDateTime.now();
        qrReservations.reserve(checkout, CUSTOMER_ID, null, "qr-expiry-race", 200000, "{}",
                List.of(new com.stylemind.commerce.service.order.QrCheckoutReservationService.Item(variantId, 2, 100000, "{}")), now);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var cancel = pool.submit(() -> {
                start.await();
                try { qrReservations.cancel(checkout, CUSTOMER_ID, null, now.plusMinutes(16)); }
                catch (com.stylemind.commerce.errors.ApiOperationException conflict) {
                    assertThat(conflict.code()).isEqualTo("CHECKOUT_NOT_CANCELLABLE");
                }
                return null;
            });
            var expire = pool.submit(() -> { start.await(); qrReservations.expire(checkout, now.plusMinutes(16)); return null; });
            start.countDown();
            cancel.get(10, java.util.concurrent.TimeUnit.SECONDS);
            expire.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM variant_inventory WHERE variant_id=?", Integer.class, variantId)).isZero();
        assertThat(jdbc.queryForObject("SELECT checkout_status FROM qr_checkout WHERE checkout_id=?", String.class, checkout)).isIn("EXPIRED", "CANCELLED");
    }

    @Test
    void adminCannotBypassQrReconciliationThroughStatusUpdate() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}");
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"admin-qr-cancel-001","shipping_address_id":"%s","payment_method":"SEPAY_VIETQR"}
                """.formatted(ADDRESS_ID)));
        String id = order.path("order_id").asText();
        mockMvc.perform(put("/api/v1/admin/orders/{id}/status", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"order_status\":\"CANCELLED\",\"reason\":\"Khach yeu cau\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_RECONCILIATION_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT order_status FROM public.customer_order WHERE order_id=?", String.class, UUID.fromString(id))).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void internalPaymentSnapshotRequiresServiceTokenAndReturnsOrderState() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}");
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"internal-snapshot-001","shipping_address_id":"%s","payment_method":"COD"}
                """.formatted(ADDRESS_ID)));
        String id = order.path("order_id").asText();
        mockMvc.perform(get("/internal/v1/orders/{id}/payment-snapshot", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/orders/{id}/payment-snapshot", id)
                        .header("X-StyleMind-Service-Token", "isolated-test-token-01234567890123456789"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order_id").value(id))
                .andExpect(jsonPath("$.payment_method").value("COD"))
                .andExpect(jsonPath("$.order_status").value("PLACED"));
    }

    @Test
    void adminCanApproveUnpaidCodCancellationWhilePreparingButOnlyOnce() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":2,\"guest_session_key\":null}");
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"processing-cancel-001","shipping_address_id":"%s","payment_method":"COD"}
                """.formatted(ADDRESS_ID)));
        UUID id = UUID.fromString(order.path("order_id").asText());
        mockMvc.perform(post("/api/v1/admin/orders/{id}/confirm", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/orders/{id}/status", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"order_status\":\"PROCESSING\",\"reason\":null}"))
                .andExpect(status().isOk());
        JsonNode request = json(postJson("/api/v1/orders/" + id + "/cancellation-requests", customerToken(), "{\"reason\":\"Khong con nhu cau\"}"));
        String requestId = request.path("cancellation_request_id").asText();
        mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.request_status").value("APPROVED"));
        assertThat(jdbc.queryForObject("SELECT order_status FROM public.customer_order WHERE order_id=?", String.class, id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM public.variant_inventory WHERE variant_id=?", Integer.class, variantId)).isZero();
        mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM public.variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(100);
    }

    @Test
    void legacyOrdersRequireReviewAndShippingPreventsPendingApproval() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}");
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"legacy-cancel-001","shipping_address_id":"%s","payment_method":"COD"}
                """.formatted(ADDRESS_ID)));
        UUID id = UUID.fromString(order.path("order_id").asText());
        jdbc.update("UPDATE public.customer_order SET confirmation_state='LEGACY_UNKNOWN' WHERE order_id=?", id);
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khong con nhu cau\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCELLATION_REVIEW_REQUIRED"));
        JsonNode request = json(postJson("/api/v1/orders/" + id + "/cancellation-requests", customerToken(), "{\"reason\":\"Khong con nhu cau\"}"));
        jdbc.update("UPDATE public.customer_order SET order_status='SHIPPED' WHERE order_id=?", id);
        mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}", request.path("cancellation_request_id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT order_status FROM public.customer_order WHERE order_id=?", String.class, id)).isEqualTo("SHIPPED");
    }

    @Test
    void confirmedCodRequiresReviewAndRejectedRequestCanBeResubmitted() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}");
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"confirmed-cancel-001","shipping_address_id":"%s","payment_method":"COD"}
                """.formatted(ADDRESS_ID)));
        String id = order.path("order_id").asText();
        mockMvc.perform(post("/api/v1/admin/orders/{id}/confirm", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmation_state").value("CONFIRMED"));
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khong con nhu cau\"}"))
                .andExpect(status().isConflict());
        JsonNode request = json(postJson("/api/v1/orders/" + id + "/cancellation-requests", customerToken(), "{\"reason\":\"Khong con nhu cau\"}"));
        mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}", request.path("cancellation_request_id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_status\":\"REJECTED\",\"decision_reason\":\"Dang chuan bi hang\"}"))
                .andExpect(status().isOk());
        JsonNode second = json(postJson("/api/v1/orders/" + id + "/cancellation-requests", customerToken(), "{\"reason\":\"Khong con nhu cau\"}"));
        assertThat(second.path("cancellation_request_id").asText()).isNotEqualTo(request.path("cancellation_request_id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.cancellation_request WHERE order_id=?", Integer.class, UUID.fromString(id))).isEqualTo(2);
    }

    @Test
    void customerCanCancelAnUnconfirmedOrderImmediatelyAndReleaseStock() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":2,\"guest_session_key\":null}");
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"direct-cancel-0001","shipping_address_id":"%s","payment_method":"COD"}
                """.formatted(ADDRESS_ID)));
        String id = order.path("order_id").asText();
        postJson("/api/v1/orders/" + id + "/cancellation-requests", customerToken(), "{\"reason\":\"Khong con nhu cau\"}");
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khong con nhu cau\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khong con nhu cau\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order_status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellation_request.request_status").value("APPROVED"));
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM public.variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(100);
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khong con nhu cau\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order_status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM public.variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(100);
    }

    @Test
    void customerCannotDirectlyCancelPaidOrder() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}");
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"direct-cancel-0002","shipping_address_id":"%s","payment_method":"SEPAY_VIETQR"}
                """.formatted(ADDRESS_ID)));
        UUID id = UUID.fromString(order.path("order_id").asText());
        jdbc.update("UPDATE public.customer_order SET order_status='PAID' WHERE order_id=?", id);
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khong con nhu cau\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCELLATION_NOT_ALLOWED"));
    }

    @Test
    void directCancellationOfVietQrFailsClosedUntilPaymentCanBeCheckedSafely() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":2,\"guest_session_key\":null}");
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"direct-cancel-0003","shipping_address_id":"%s","payment_method":"SEPAY_VIETQR"}
                """.formatted(ADDRESS_ID)));
        String id = order.path("order_id").asText();
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khong con nhu cau\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_CHECK_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM public.variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM public.variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(100);
    }

    @Test
    void directCancellationRejectsCodCollectionAlreadyApplied() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}");
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"direct-cancel-0004","shipping_address_id":"%s","payment_method":"COD"}
                """.formatted(ADDRESS_ID)));
        UUID id = UUID.fromString(order.path("order_id").asText());
        jdbc.update("""
                INSERT INTO public.payment_event_inbox(event_id,event_fingerprint,order_id,payment_attempt_id,customer_id,payment_method,payment_reference,amount_vnd,confirmed_at,received_at,outcome,reason)
                VALUES (?, ?, ?, ?, ?, 'COD', 'test-reference', 100000, now(), now(), 'APPLIED', 'COD_COLLECTION_CONFIRMED')
                """, UUID.randomUUID(), "a".repeat(64), id, UUID.randomUUID(), CUSTOMER_ID);
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khong con nhu cau\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCELLATION_NOT_ALLOWED"));
    }

    @Test
    void simultaneousDirectCancellationRestoresStockOnlyOnce() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":2,\"guest_session_key\":null}");
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"direct-cancel-0005","shipping_address_id":"%s","payment_method":"COD"}
                """.formatted(ADDRESS_ID)));
        String id = order.path("order_id").asText();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> cancel = () -> { start.await(); return mockMvc.perform(post("/api/v1/orders/{id}/cancel", id)
                    .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Khong con nhu cau\"}")).andReturn().getResponse().getStatus(); };
            var a = pool.submit(cancel); var b = pool.submit(cancel); start.countDown();
            assertThat(List.of(a.get(), b.get())).containsOnly(200);
        }
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM public.variant_inventory WHERE variant_id=?", Integer.class, variantId)).isEqualTo(100);
    }

    @Test
    void selectedCheckoutPreservesUnselectedCartAndRejectsForeignItems() throws Exception {
        jdbc.update("UPDATE public.product_variant SET variant_status='ACTIVE' WHERE variant_id=?", inactiveVariantId);
        JsonNode first = json(postJson("/api/v1/cart/items", customerToken(),
                "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}"));
        String selected = first.path("items").get(0).path("cart_item_id").asText();
        postJson("/api/v1/cart/items", customerToken(),
                "{\"variant_id\":\"" + inactiveVariantId + "\",\"quantity\":1,\"guest_session_key\":null}");
        String ids = "[\"" + selected + "\"]";
        mockMvc.perform(post("/api/v1/checkout/selected/prices").header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cart_item_ids\":[\"" + selected + "\",\"" + selected + "\"]}"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/cart/items", otherCustomerToken(),
                "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}");
        mockMvc.perform(post("/api/v1/checkout/selected/prices").header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cart_item_ids\":" + ids + "}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/checkout/selected/prices").header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cart_item_ids\":" + ids + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(post("/api/v1/orders/selected").header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkout_key\":\"selected-test-001\",\"shipping_address_id\":\"" + ADDRESS_ID + "\",\"payment_method\":\"COD\",\"cart_item_ids\":" + ids + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(post("/api/v1/checkout/recover").header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkout_key\":\"selected-test-001\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(post("/api/v1/checkout/recover").header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkout_key\":\"selected-test-001\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].variant_id").value(inactiveVariantId.toString()));
        mockMvc.perform(post("/api/v1/checkout/selected/prices").header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cart_item_ids\":" + ids + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void simultaneousSelectedRequestsWithSameKeyReturnDuplicateForRecovery() throws Exception {
        JsonNode cart = json(postJson("/api/v1/cart/items", customerToken(),
                "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}"));
        String id = cart.path("items").get(0).path("cart_item_id").asText();
        String body = "{\"checkout_key\":\"same-selected-key-01\",\"shipping_address_id\":\"" + ADDRESS_ID + "\",\"payment_method\":\"COD\",\"cart_item_ids\":[\"" + id + "\"]}";
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<MvcResult>>();
            for (int i = 0; i < 2; i++) tasks.add(pool.submit(() -> { ready.countDown(); start.await(); return postJson("/api/v1/orders/selected", customerToken(), body); }));
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            var results = new java.util.ArrayList<MvcResult>();
            for (var task : tasks) results.add(task.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(results.stream().map(result -> result.getResponse().getStatus()).toList()).containsExactlyInAnyOrder(201, 409);
            MvcResult conflict = results.stream().filter(result -> result.getResponse().getStatus() == 409).findFirst().orElseThrow();
            assertThat(json(conflict).path("code").asText()).isEqualTo("DUPLICATE_CHECKOUT");
        }
    }

    @Test
    void concurrentUpdateAndSelectedCheckoutDoNotDeadlock() throws Exception {
        JsonNode cart = json(postJson("/api/v1/cart/items", customerToken(),
                "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}"));
        String id = cart.path("items").get(0).path("cart_item_id").asText();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var checkout = pool.submit(() -> { ready.countDown(); start.await();
                return postJson("/api/v1/orders/selected", customerToken(), "{\"checkout_key\":\"selected-update-race\",\"shipping_address_id\":\"" + ADDRESS_ID + "\",\"payment_method\":\"COD\",\"cart_item_ids\":[\"" + id + "\"]}").getResponse().getStatus(); });
            var update = pool.submit(() -> { ready.countDown(); start.await();
                return mockMvc.perform(put("/api/v1/cart/items/" + id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2,\"guest_session_key\":null}")).andReturn().getResponse().getStatus(); });
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(checkout.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(201);
            assertThat(update.get(30, java.util.concurrent.TimeUnit.SECONDS)).isIn(200, 404);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM public.customer_order", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void concurrentGuestMergeAndSelectedCheckoutDoNotDuplicatePurchasedItem() throws Exception {
        String key = "guest-merge-race-0001";
        JsonNode cart = json(postJson("/api/v1/cart/items", null,
                "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":\"" + key + "\"}"));
        String id = cart.path("items").get(0).path("cart_item_id").asText();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var checkout = pool.submit(() -> { ready.countDown(); start.await();
                return mockMvc.perform(post("/api/v1/guest/orders/selected").header("X-Guest-Session-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"checkout_key\":\"guest-merge-race-01\",\"payment_method\":\"COD\",\"shipping_address\":{\"recipient_name\":\"An\",\"recipient_phone\":\"0123456789\",\"address_line\":\"Ha Noi\"},\"cart_item_ids\":[\"" + id + "\"]}")).andReturn().getResponse().getStatus(); });
            var merge = pool.submit(() -> { ready.countDown(); start.await();
                return postJson("/api/v1/cart/merge", customerToken(), "{\"guest_session_key\":\"" + key + "\"}").getResponse().getStatus(); });
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            int checkoutStatus = checkout.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(merge.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(checkoutStatus).isIn(201, 409);
            int remaining = jdbc.queryForObject("SELECT count(*) FROM public.cart_item ci JOIN public.cart c ON c.cart_id=ci.cart_id WHERE c.customer_id=? AND c.cart_status='ACTIVE'", Integer.class, CUSTOMER_ID);
            assertThat(remaining).isEqualTo(checkoutStatus == 201 ? 0 : 1);
        }
    }

    @Test
    void concurrentSelectedCheckoutCannotOrderSameCartItemTwice() throws Exception {
        JsonNode cart = json(postJson("/api/v1/cart/items", customerToken(),
                "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}"));
        String id = cart.path("items").get(0).path("cart_item_id").asText();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 2; i++) {
                final int index = i;
                tasks.add(pool.submit(() -> { ready.countDown(); start.await();
                    return postJson("/api/v1/orders/selected", customerToken(),
                            "{\"checkout_key\":\"selected-race-" + index + "\",\"shipping_address_id\":\"" + ADDRESS_ID + "\",\"payment_method\":\"COD\",\"cart_item_ids\":[\"" + id + "\"]}").getResponse().getStatus(); }));
            }
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var statuses = new java.util.ArrayList<Integer>();
            for (var task : tasks) statuses.add(task.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM public.customer_order", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void guestSelectedCheckoutKeepsUnselectedItemsAndRejectsAnotherGuest() throws Exception {
        jdbc.update("UPDATE public.product_variant SET variant_status='ACTIVE' WHERE variant_id=?", inactiveVariantId);
        String key = "guest-selected-owner-0001";
        JsonNode first = json(postJson("/api/v1/cart/items", null,
                "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":\"" + key + "\"}"));
        String id = first.path("items").get(0).path("cart_item_id").asText();
        postJson("/api/v1/cart/items", null, "{\"variant_id\":\"" + inactiveVariantId + "\",\"quantity\":1,\"guest_session_key\":\"" + key + "\"}");
        String ids = "[\"" + id + "\"]";
        mockMvc.perform(post("/api/v1/guest/checkout/selected/prices").header("X-Guest-Session-Key", "guest-foreign-owner-0001")
                .contentType(MediaType.APPLICATION_JSON).content("{\"cart_item_ids\":" + ids + "}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/guest/orders/selected").header("X-Guest-Session-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkout_key\":\"guest-select-001\",\"payment_method\":\"COD\",\"shipping_address\":{\"recipient_name\":\"An\",\"recipient_phone\":\"0123456789\",\"address_line\":\"Ha Noi\"},\"cart_item_ids\":" + ids + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get("/api/v1/cart").header("X-Guest-Session-Key", key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void guestBuyNowUsesGuestKeyAndLeavesCartUnchanged() throws Exception {
        String key = "guest-buy-now-test-owner";
        postJson("/api/v1/cart/items", null, "{\"variant_id\":\"" + variantId + "\",\"quantity\":2,\"guest_session_key\":\"" + key + "\"}");
        String item = "\"variant_id\":\"" + variantId + "\",\"quantity\":1";
        mockMvc.perform(post("/api/v1/guest/checkout/buy-now/prices")
                .contentType(MediaType.APPLICATION_JSON).content("{" + item + "}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/guest/checkout/buy-now/prices").header("X-Guest-Session-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{" + item + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(1));
        mockMvc.perform(post("/api/v1/guest/orders/buy-now").header("X-Guest-Session-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkout_key\":\"guest-buynow-001\",\"payment_method\":\"COD\",\"shipping_address\":{\"recipient_name\":\"An\",\"recipient_phone\":\"0123456789\",\"address_line\":\"Ha Noi\"}," + item + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/guest/checkout/recover").header("X-Guest-Session-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkout_key\":\"guest-buynow-001\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(1));
        mockMvc.perform(post("/api/v1/guest/checkout/recover").header("X-Guest-Session-Key", "guest-foreign-owner-0001")
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkout_key\":\"guest-buynow-001\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/cart").header("X-Guest-Session-Key", key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void buyNowDoesNotChangeExistingCart() throws Exception {
        postJson("/api/v1/cart/items", customerToken(),
                "{\"variant_id\":\"" + variantId + "\",\"quantity\":2,\"guest_session_key\":null}");
        String selection = "\"variant_id\":\"" + variantId + "\",\"quantity\":1";
        mockMvc.perform(post("/api/v1/checkout/buy-now/prices").header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{" + selection + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(1));
        mockMvc.perform(post("/api/v1/orders/buy-now").header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkout_key\":\"buynow-test-001\",\"shipping_address_id\":\"" + ADDRESS_ID + "\",\"payment_method\":\"COD\"," + selection + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].quantity").value(1));
        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void guestHeaderCartCrudPreservesOwnerIsolation() throws Exception {
        String owner = "guest-header-integration-owner-a";
        String foreign = "guest-header-integration-owner-b";
        JsonNode cart = json(postJson("/api/v1/cart/items", null,
                "{\"variant_id\":\"" + variantId + "\",\"quantity\":2,\"guest_session_key\":\"" + owner + "\"}"));
        String itemId = cart.path("items").get(0).path("cart_item_id").asText();
        mockMvc.perform(get("/api/v1/cart").header("X-Guest-Session-Key", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total_vnd").value(200000));
        mockMvc.perform(get("/api/v1/cart").header("X-Guest-Session-Key", foreign))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/guest/checkout/prices").header("X-Guest-Session-Key", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_vnd").value(200000));
        mockMvc.perform(delete("/api/v1/cart/items/" + itemId).header("X-Guest-Session-Key", foreign))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/cart/items/" + itemId).header("X-Guest-Session-Key", owner))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/cart").header("X-Guest-Session-Key", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        postJson("/api/v1/cart/items", null,
                "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":\"" + owner + "\"}");
        mockMvc.perform(delete("/api/v1/cart/items").header("X-Guest-Session-Key", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void branchCrudRejectsDuplicateCodeHidesInactiveAndRequiresAdmin() throws Exception {
        assertProblem(postJson("/api/v1/admin/branches", customerToken(), branchBody("HN01", "ACTIVE")),
                403, "ACCESS_DENIED");
        JsonNode created = json(postJson("/api/v1/admin/branches", adminToken(), branchBody("HN01", "ACTIVE")));
        UUID branchId = UUID.fromString(created.path("branch_id").asText());
        assertProblem(postJson("/api/v1/admin/branches", adminToken(), branchBody("HN01", "ACTIVE")),
                409, "DUPLICATE_BRANCH_CODE");
        mockMvc.perform(put("/api/v1/admin/branches/{id}", branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchBody("HN01", "INACTIVE")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/branches/{id}", branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/branches/{id}", branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch_status").value("INACTIVE"));
        mockMvc.perform(put("/api/v1/admin/branches/{id}", branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchBody("HN01", "ACTIVE")))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/branches/{id}", branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/branches/{id}", branchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT branch_status FROM public.branch WHERE branch_id = ?",
                String.class, branchId)).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.branch WHERE branch_id = ?",
                Integer.class, branchId)).isEqualTo(1);
    }

    @Test
    void cartCrudValidatesOwnerQuantityActiveVariantsAndTotals() throws Exception {
        JsonNode cart = json(postJson("/api/v1/cart/items", null, """
                {"variant_id":"%s","quantity":2,"guest_session_key":"guest-session-0001"}
                """.formatted(variantId)));
        UUID itemId = UUID.fromString(cart.path("items").get(0).path("cart_item_id").asText());
        assertThat(cart.path("total_vnd").asLong()).isEqualTo(200000);
        assertProblem(postJson("/api/v1/cart/items", null, """
                {"variant_id":"%s","quantity":1,"guest_session_key":"guest-session-0001"}
                """.formatted(inactiveVariantId)), 409, "VARIANT_NOT_ACTIVE");
        mockMvc.perform(put("/api/v1/cart/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3,\"guest_session_key\":\"guest-session-0001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_vnd").value(300000));
        mockMvc.perform(delete("/api/v1/cart/items/{id}", itemId)
                        .queryParam("guest_session_key", "other-guest-session-0001"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/cart/items")
                        .queryParam("guest_session_key", "guest-session-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void mergeCombinesDuplicateVariantsAndRollsBackInvalidGuestCart() throws Exception {
        postJson("/api/v1/cart/items", null, """
                {"variant_id":"%s","quantity":1,"guest_session_key":"guest-session-0002"}
                """.formatted(variantId));
        postJson("/api/v1/cart/items", customerToken(), """
                {"variant_id":"%s","quantity":2,"guest_session_key":null}
                """.formatted(variantId));
        mockMvc.perform(post("/api/v1/cart/merge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guest_session_key\":\"guest-session-0002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.total_vnd").value(300000));
        mockMvc.perform(post("/api/v1/cart/merge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guest_session_key\":\"guest-session-0002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        UUID badCart = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public.cart(cart_id, guest_session_key, cart_status, created_at, updated_at)
                VALUES (?, 'guest-session-0003', 'ACTIVE', now(), now())
                """, badCart);
        jdbc.update("""
                INSERT INTO public.cart_item(cart_item_id, cart_id, variant_id, quantity, last_seen_price_vnd, added_at, updated_at)
                VALUES (?, ?, ?, 1, 90000, now(), now())
                """, UUID.randomUUID(), badCart, inactiveVariantId);
        assertProblem(mockMvc.perform(post("/api/v1/cart/merge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guest_session_key\":\"guest-session-0003\"}"))
                .andReturn(), 409, "INVALID_CART_ITEM");
        Integer activeGuest = jdbc.queryForObject(
                "SELECT count(*) FROM public.cart WHERE guest_session_key = 'guest-session-0003' AND cart_status = 'ACTIVE'",
                Integer.class);
        assertThat(activeGuest).isEqualTo(1);
    }

    @Test
    void checkoutSnapshotsPricesPreventsRetryAndOrderStatusAndCancellationAreGuarded() throws Exception {
        postJson("/api/v1/cart/items", customerToken(), """
                {"variant_id":"%s","quantity":2,"guest_session_key":null}
                """.formatted(variantId));
        jdbc.update("UPDATE public.product_variant SET unit_price_vnd = 120000 WHERE variant_id = ?", variantId);
        JsonNode order = json(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"checkout-key-0001","shipping_address_id":"%s","payment_method":"COD"}
                """.formatted(ADDRESS_ID)));
        UUID orderId = UUID.fromString(order.path("order_id").asText());
        assertThat(order.path("accepted_total_vnd").asLong()).isEqualTo(240000);
        assertThat(order.path("address").path("recipient_name").asText()).isEqualTo("Nguyen Van A");
        assertProblem(postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"checkout-key-0001","shipping_address_id":"%s","payment_method":"COD"}
                """.formatted(ADDRESS_ID)), 409, "DUPLICATE_CHECKOUT");
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/admin/orders/{id}/status", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"order_status\":\"COMPLETED\",\"reason\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"));
        JsonNode cancellation = json(postJson("/api/v1/orders/" + orderId + "/cancellation-requests",
                customerToken(), "{\"reason\":\"Customer changed delivery plan\"}"));
        mockMvc.perform(put("/api/v1/admin/cancellation-requests/{id}",
                        cancellation.path("cancellation_request_id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_status\":\"APPROVED\",\"decision_reason\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_status").value("APPROVED"));
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_status").value("CANCELLED"))
                .andExpect(jsonPath("$.history.length()").value(3));
    }

    @Test
    void committedCheckoutSendsSnapshotAndRecoveryKeepsBothRolesSelfOnly() throws Exception {
        for (String method : List.of("COD", "SEPAY_VIETQR")) {
            postJson("/api/v1/cart/items", customerToken(), """
                    {"variant_id":"%s","quantity":1,"guest_session_key":null}
                    """.formatted(variantId));
            MvcResult result = postJson("/api/v1/orders", customerToken(), """
                    {"checkout_key":"handoff-%s","shipping_address_id":"%s","payment_method":"%s"}
                    """.formatted(method, ADDRESS_ID, method));
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            UUID id = UUID.fromString(json(result).path("order_id").asText());
            assertThat(json(result).has("payment_attempt")).isFalse();
            org.mockito.Mockito.verify(payment).create(org.mockito.ArgumentMatchers.argThat(r ->
                    r.orderId().equals(id) && r.customerId().equals(CUSTOMER_ID) && r.acceptedTotalVnd() == 100000
                            && r.paymentMethod().equals(method) && r.requestKey().equals("initial-" + id)));
            mockMvc.perform(post("/api/v1/orders/{id}/payment-attempt", id)
                    .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(id.toString()));
            mockMvc.perform(post("/api/v1/orders/{id}/payment-attempt", id)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token(OTHER_CUSTOMER_ID, "ADMIN"))))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/v1/orders/{id}/payment-attempt", id)
                    .header(HttpHeaders.AUTHORIZATION, bearer(otherCustomerToken())))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void paymentFailurePreservesCommittedOrderAndRolledBackCheckoutSendsNothing() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("test-upstream-private-marker"))
                .when(payment).create(org.mockito.ArgumentMatchers.any());
        postJson("/api/v1/cart/items", customerToken(), """
                {"variant_id":"%s","quantity":1,"guest_session_key":null}
                """.formatted(variantId));
        MvcResult result = postJson("/api/v1/orders", customerToken(), """
                {"checkout_key":"handoff-failure","shipping_address_id":"%s","payment_method":"SEPAY_VIETQR"}
                """.formatted(ADDRESS_ID));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).path("order_status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.customer_order", Integer.class)).isEqualTo(1);
        org.mockito.Mockito.verify(payment).create(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.reset(payment);
        postJson("/api/v1/cart/items", customerToken(), """
                {"variant_id":"%s","quantity":1,"guest_session_key":null}
                """.formatted(variantId));
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            orderService.checkout(CUSTOMER_ID, bearer(customerToken()),
                    new com.stylemind.commerce.validation.order.OrderRequestValidator.CheckoutCommand(
                            "handoff-rollback", ADDRESS_ID, com.stylemind.commerce.enums.PaymentMethod.COD));
            status.setRollbackOnly();
        });
        org.mockito.Mockito.verifyNoInteractions(payment);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.customer_order", Integer.class)).isEqualTo(1);
    }

    @Test
    void codCheckoutOnlyConfirmsAfterPendingAttemptAcknowledgementAndNeverMarksPaid() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            com.stylemind.commerce.client.payment.OrderPaymentRequest snapshot = invocation.getArgument(0);
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(snapshot.orderStatus()).isEqualTo("PENDING_PAYMENT");
            assertThat(jdbc.queryForObject("SELECT order_status FROM public.customer_order WHERE order_id=?", String.class,
                    snapshot.orderId())).isEqualTo("PENDING_PAYMENT");
            return new com.stylemind.commerce.client.payment.PaymentAttemptResponse(UUID.randomUUID(), snapshot.orderId(),
                    "COD", snapshot.acceptedTotalVnd(), "SMCODTEST", "PENDING", null, null, null);
        }).when(payment).create(org.mockito.ArgumentMatchers.any());
        var result = createCod("cod-two-steps");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).path("order_status").asText()).isEqualTo("PLACED");
        assertThat(jdbc.queryForList("SELECT to_status FROM public.order_status_history ORDER BY changed_at", String.class))
                .containsExactly("PENDING_PAYMENT", "PLACED");
        assertThat(json(result).path("history").size()).isEqualTo(2);
    }

    @Test
    void unavailablePaymentKeepsCodPendingAndOwnerRecoveryConfirmsOnce() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("test-only outage")).when(payment).create(org.mockito.ArgumentMatchers.any());
        var result = createCod("cod-pending-recovery");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).path("order_status").asText()).isEqualTo("PENDING_PAYMENT");
        UUID id = UUID.fromString(json(result).path("order_id").asText());
        UUID attempt = UUID.randomUUID();
        org.mockito.Mockito.doAnswer(invocation -> {
            com.stylemind.commerce.client.payment.OrderPaymentRequest snapshot = invocation.getArgument(0);
            return new com.stylemind.commerce.client.payment.PaymentAttemptResponse(attempt, id, "COD",
                    snapshot.acceptedTotalVnd(), "SMCODTEST", "PENDING", null, null, null);
        }).when(payment).create(org.mockito.ArgumentMatchers.any());
        for (int i = 0; i < 2; i++) mockMvc.perform(post("/api/v1/orders/{id}/payment-attempt", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(jdbc.queryForObject("SELECT order_status FROM public.customer_order WHERE order_id=?", String.class, id)).isEqualTo("PLACED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.order_status_history WHERE order_id=?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void malformedPaymentAcknowledgementCannotConfirmCodOrder() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            com.stylemind.commerce.client.payment.OrderPaymentRequest snapshot = invocation.getArgument(0);
            return new com.stylemind.commerce.client.payment.PaymentAttemptResponse(UUID.randomUUID(), snapshot.orderId(),
                    "COD", snapshot.acceptedTotalVnd(), "SMTEST", "CANCELLED", null, null, null);
        }).when(payment).create(org.mockito.ArgumentMatchers.any());
        var result = createCod("cod-invalid-ack"); UUID id = UUID.fromString(json(result).path("order_id").asText());
        assertThat(json(result).path("order_status").asText()).isEqualTo("PENDING_PAYMENT");
        mockMvc.perform(post("/api/v1/orders/{id}/payment-attempt", id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isBadGateway());
        assertThat(jdbc.queryForObject("SELECT order_status FROM public.customer_order WHERE order_id=?", String.class, id)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void cancelledCodIsNotRevivedByAnInflightAttemptAcknowledgement() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            com.stylemind.commerce.client.payment.OrderPaymentRequest snapshot = invocation.getArgument(0);
            jdbc.update("UPDATE public.customer_order SET order_status='CANCELLED' WHERE order_id=?", snapshot.orderId());
            return new com.stylemind.commerce.client.payment.PaymentAttemptResponse(UUID.randomUUID(), snapshot.orderId(),
                    "COD", snapshot.acceptedTotalVnd(), "SMTEST", "PENDING", null, null, null);
        }).when(payment).create(org.mockito.ArgumentMatchers.any());
        var result = createCod("cod-cancel-race");
        assertThat(json(result).path("order_status").asText()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.order_status_history WHERE to_status='PLACED'", Integer.class)).isZero();
    }

    @Test
    void confirmationWriteFailureLeavesCodPendingAndRetryCommitsOrderAndHistoryTogether() throws Exception {
        jdbc.execute("CREATE OR REPLACE FUNCTION public.fail_cod_confirmation_test_fn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.to_status='PLACED' THEN RAISE EXCEPTION 'test-only failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_cod_confirmation_test BEFORE INSERT ON public.order_status_history FOR EACH ROW EXECUTE FUNCTION public.fail_cod_confirmation_test_fn()");
        MvcResult result;
        try {
            result = createCod("cod-confirm-rollback");
            assertThat(json(result).path("order_status").asText()).isEqualTo("PENDING_PAYMENT");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM public.order_status_history", Integer.class)).isEqualTo(1);
        } finally { jdbc.execute("DROP TRIGGER fail_cod_confirmation_test ON public.order_status_history"); }
        UUID id = UUID.fromString(json(result).path("order_id").asText());
        mockMvc.perform(post("/api/v1/orders/{id}/payment-attempt", id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT order_status FROM public.customer_order WHERE order_id=?", String.class, id)).isEqualTo("PLACED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.order_status_history", Integer.class)).isEqualTo(2);
    }

    @Test
    void adminCannotMarkUnconfirmedCodAsPaidOrSkipAutomaticConfirmation() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("test-only outage")).when(payment).create(org.mockito.ArgumentMatchers.any());
        var result = createCod("cod-no-admin-shortcut"); String id = json(result).path("order_id").asText();
        for (String target : List.of("PAID", "PLACED", "PROCESSING")) mockMvc.perform(put("/api/v1/admin/orders/{id}/status", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"order_status\":\"" + target + "\",\"reason\":null}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COD_CONFIRMATION_REQUIRED"));
    }

    @Test
    void guestCheckoutSupportsBothMethodsDirectShippingAndOwnerIsolation() throws Exception {
        int identityBefore = IDENTITY_CALLS.get();
        for (String method : List.of("COD", "SEPAY_VIETQR")) {
            String key = "guest-checkout-" + method;
            addGuestItem(key);
            mockMvc.perform(get("/api/v1/guest/checkout/prices").header("X-Guest-Session-Key", key))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total_vnd").value(100000));
            var result = guestCheckout(key, "checkout-" + method, method);
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            var order = json(result); String id = order.path("order_id").asText();
            assertThat(order.get("customer_id").isNull()).isTrue();
            assertThat(order.path("order_status").asText()).isEqualTo(method.equals("COD") ? "PLACED" : "PENDING_PAYMENT");
            assertThat(order.path("address").path("recipient_phone").asText()).isEqualTo("my phone 123");
            assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(result.getResponse().getContentAsString()).doesNotContain("guest_session_hash", key);
            mockMvc.perform(get("/api/v1/guest/orders/" + id).header("X-Guest-Session-Key", key)).andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/guest/orders/" + id).header("X-Guest-Session-Key", "other-guest-checkout-key"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/orders/" + id).header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/admin/orders/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/orders/" + id + "/cancellation-requests")
                    .header(HttpHeaders.AUTHORIZATION, bearer(customerToken())).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"please cancel\"}")).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/v1/guest/orders/" + id + "/payment-attempt")
                    .header("X-Guest-Session-Key", "other-guest-checkout-key")).andExpect(status().isNotFound());
            mockMvc.perform(post("/api/v1/guest/orders/" + id + "/payment-attempt")
                    .header("X-Guest-Session-Key", key)).andExpect(status().isOk());
            assertProblem(guestCheckout(key, "checkout-" + method, method),409,"DUPLICATE_CHECKOUT");
            jdbc.update("UPDATE public.customer_order SET order_status='CANCELLED' WHERE order_id=?", UUID.fromString(id));
            mockMvc.perform(post("/api/v1/guest/orders/" + id + "/payment-attempt")
                    .header("X-Guest-Session-Key", key)).andExpect(status().isConflict());
        }
        assertThat(IDENTITY_CALLS.get()).isEqualTo(identityBefore);
    }

    @Test
    void concurrentGuestCheckoutConsumesOnlyOneCart() throws Exception {
        String key = "guest-concurrent-checkout"; addGuestItem(key);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i=0;i<2;i++) {
                final int index=i;
                tasks.add(pool.submit(() -> { ready.countDown(); start.await();
                    return guestCheckout(key,"concurrent-key-"+index,"COD").getResponse().getStatus(); }));
            }
            assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
            var statuses = new java.util.ArrayList<Integer>();
            for (var task:tasks) statuses.add(task.get(30,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201,409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.customer_order",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cart_status FROM public.cart WHERE guest_session_key=?",String.class,key)).isEqualTo("CHECKED_OUT");
    }

    @Test
    void failedGuestCheckoutRollsBackOrderCartAndInventoryAndDoesNotCallPayment() throws Exception {
        String key="guest-rollback-checkout"; addGuestItem(key);
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_guest_address_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only persistence failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_guest_address_test BEFORE INSERT ON public.order_address_snapshot FOR EACH ROW EXECUTE FUNCTION fail_guest_address_test()");
        try {
            assertThat(guestCheckout(key,"rollback-checkout-key","COD").getResponse().getStatus()).isEqualTo(500);
        } finally { jdbc.execute("DROP TRIGGER fail_guest_address_test ON public.order_address_snapshot"); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.customer_order",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT cart_status FROM public.cart WHERE guest_session_key=?",String.class,key)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM public.variant_inventory WHERE variant_id=?",Integer.class,variantId)).isEqualTo(100);
        org.mockito.Mockito.verifyNoInteractions(payment);
        assertThat(guestCheckout(key,"rollback-checkout-key","COD").getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void guestPaymentFailureLeavesSavedOrderForRecovery() throws Exception {
        String key="guest-payment-failure";addGuestItem(key);
        org.mockito.Mockito.doThrow(new IllegalStateException("test-only outage")).when(payment).create(org.mockito.ArgumentMatchers.any());
        var result=guestCheckout(key,"guest-failure-key","COD");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).path("order_status").asText()).isEqualTo("PENDING_PAYMENT");
        mockMvc.perform(post("/api/v1/guest/orders/"+json(result).path("order_id").asText()+"/payment-attempt")
                .header("X-Guest-Session-Key",key)).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.customer_order",Integer.class)).isEqualTo(1);
    }

    private void addGuestItem(String key) throws Exception {
        assertThat(postJson("/api/v1/cart/items",null,"{\"variant_id\":\""+variantId+"\",\"quantity\":1,\"guest_session_key\":\""+key+"\"}")
                .getResponse().getStatus()).isEqualTo(200);
    }

    private MvcResult guestCheckout(String key,String checkoutKey,String method) throws Exception {
        return mockMvc.perform(post("/api/v1/guest/orders").header("X-Guest-Session-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"checkout_key":"%s","payment_method":"%s","shipping_address":
                 {"recipient_name":" Guest Buyer ","recipient_phone":" my phone 123 ","address_line":" 1 Main St, Hanoi "}}
                """.formatted(checkoutKey,method))).andReturn();
    }

    private MvcResult createCod(String key) throws Exception {
        postJson("/api/v1/cart/items", customerToken(), "{\"variant_id\":\"" + variantId + "\",\"quantity\":1,\"guest_session_key\":null}");
        return postJson("/api/v1/orders", customerToken(), "{\"checkout_key\":\"" + key
                + "\",\"shipping_address_id\":\"" + ADDRESS_ID + "\",\"payment_method\":\"COD\"}");
    }

    private String branchBody(String code, String status) {
        return """
                {"branch_code":"%s","name":"Ha Noi","address_line":"1 Main St","contact_phone":null,"branch_status":"%s"}
                """.formatted(code, status);
    }

    private MvcResult postJson(String path, String token, String body) throws Exception {
        var builder = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void assertProblem(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(json(result).path("code").asText()).isEqualTo(code);
    }

    private String adminToken() {
        return token(UUID.randomUUID(), "ADMIN");
    }

    private String customerToken() {
        return token(CUSTOMER_ID, "CUSTOMER");
    }

    private String otherCustomerToken() {
        return token(OTHER_CUSTOMER_ID, "CUSTOMER");
    }

    private String token(UUID subject, String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject.toString())
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("roles", List.of(role))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(SIGNING_KEY.getKeyID()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(SIGNING_KEY)))
                .encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static RSAKey generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() throws Exception {
            return NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
        }

        @Bean
        @Primary
        IdentityAddressClient identityAddressClient() {
            return new IdentityAddressClient(null) {
                @Override
                public AddressSnapshot getAddress(UUID addressId, String authorizationHeader) {
                    IDENTITY_CALLS.incrementAndGet();
                    if (!ADDRESS_ID.equals(addressId)) {
                        throw new com.stylemind.commerce.errors.ApiOperationException(
                                org.springframework.http.HttpStatus.CONFLICT, "INVALID_SHIPPING_ADDRESS",
                                "Shipping address does not exist for this customer");
                    }
                    return new AddressSnapshot(addressId, "Nguyen Van A", "+84901234567",
                            "1 Nguyen Trai, Thanh Xuan, Ha Noi");
                }
            };
        }
    }
}
