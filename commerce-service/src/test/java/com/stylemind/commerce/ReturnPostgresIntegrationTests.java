package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "stylemind.payment.ingress-token=refund-test-only-token",
        "stylemind.security.service-token=generic-test-only-service-token-0123456789",
        "stylemind.commerce.inventory.expiry-enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class ReturnPostgresIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> DB = IsolatedPostgres.create("postgres:18.6")
            .withDatabaseName("commerce_return_test").withUsername("return_test").withPassword("test-only");

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final String TOKEN_HEADER = "X-StyleMind-Service-Token";
    private static final String TOKEN = "refund-test-only-token";
    private static final String CALLBACK = "/internal/v1/refund-results";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void authenticateTestActors() {
        mockJwt("owner", OWNER, "CUSTOMER");
        mockJwt("other", OTHER, "CUSTOMER");
        mockJwt("admin", ADMIN, "ADMIN");
        mockJwt("unprivileged", OWNER, "UNRECOGNIZED");
    }

    @Test
    void onlyOwnerCanReadEligibleItemsAndASettledMatchingPaymentIsRequired() throws Exception {
        var order = order(OWNER, "COD", "COMPLETED", OffsetDateTime.now().minusDays(6));
        String path = returns(order.id());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer("other"))).andExpect(status().isNotFound());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer("admin"))).andExpect(status().isForbidden());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer("unprivileged"))).andExpect(status().isForbidden());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer("owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.ineligible_reason").value("PAYMENT_NOT_CONFIRMED"));
        payment(order, OTHER, "COD", 200_000);
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer("owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false));

        var paid = order(OWNER, "SEPAY_VIETQR", "COMPLETED", OffsetDateTime.now().minusDays(6));
        payment(paid, OWNER, "SEPAY_VIETQR", 200_000);
        mvc.perform(get(returns(paid.id())).header(HttpHeaders.AUTHORIZATION, bearer("owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.available_items[0].order_item_id").value(paid.item().toString()))
                .andExpect(jsonPath("$.available_items[0].sku_snapshot").value("original-sku"))
                .andExpect(jsonPath("$.available_items[0].color_snapshot").value("Blue"))
                .andExpect(jsonPath("$.available_items[0].size_snapshot").value("M"))
                .andExpect(jsonPath("$.available_items[0].quantity").value(2))
                .andExpect(jsonPath("$.available_items[0].unit_price_vnd").value(100_000));
        create(paid, UUID.randomUUID(), 1, "Damaged", evidence())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].color_snapshot").value("Blue"))
                .andExpect(jsonPath("$.items[0].size_snapshot").value("M"));
    }

    @Test
    void elapsedWindowAndNonCompletedOrdersPreventCreation() throws Exception {
        var expired = order(OWNER, "COD", "COMPLETED", OffsetDateTime.now().minusDays(7).minusSeconds(1));
        payment(expired, OWNER, "COD", 200_000);
        create(expired, UUID.randomUUID(), 1, "Damaged", evidence())
                .andExpect(status().isConflict());
        var beforeDeadline = order(OWNER, "COD", "COMPLETED", OffsetDateTime.now().minusDays(7).plusMinutes(1));
        payment(beforeDeadline, OWNER, "COD", 200_000);
        create(beforeDeadline, UUID.randomUUID(), 1, "Damaged", evidence()).andExpect(status().isCreated());
        var shipped = order(OWNER, "COD", "SHIPPED", null);
        payment(shipped, OWNER, "COD", 200_000);
        create(shipped, UUID.randomUUID(), 1, "Damaged", evidence())
                .andExpect(status().isConflict());
    }

    @Test
    void validatesImageContentsAndDecisionReasonsBeforeChangingState() throws Exception {
        var order = paidCodOrder();
        var counterfeit = new MockMultipartFile("images", "fake.png", "image/png",
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        create(order, UUID.randomUUID(), 1, "Damaged", counterfeit).andExpect(status().isBadRequest());
        create(order, UUID.randomUUID(), 1, "Damaged").andExpect(status().isBadRequest());
        var badMime = new MockMultipartFile("images", "false.jpg", "image/jpeg", evidence().getBytes());
        create(order, UUID.randomUUID(), 1, "Damaged", badMime).andExpect(status().isBadRequest());
        var valid = evidence("jpeg");
        create(order, UUID.randomUUID(), 1, "Damaged", valid, valid, valid, valid, valid, valid)
                .andExpect(status().isBadRequest());
        JsonNode created = read(create(order, UUID.randomUUID(), 1, "Damaged", valid)
                .andExpect(status().isCreated()));
        UUID id = uuid(created, "return_id");
        decision(id, "REJECT", " ").andExpect(status().isBadRequest());
        decision(id, "PASS_INSPECTION", null).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/returns").header(HttpHeaders.AUTHORIZATION, bearer("owner")))
                .andExpect(status().isForbidden());
    }

    @Test
    void evidenceIsPrivateAndShopCanRequestMoreThenCompleteAnInspectedReturn() throws Exception {
        var order = paidCodOrder();
        UUID requestKey = UUID.randomUUID();
        JsonNode created = read(create(order, requestKey, 1, "Damaged", evidence())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount_vnd").value(100_000)));
        UUID id = uuid(created, "return_id");
        UUID image = uuid(created.path("evidence").get(0), "image_id");
        String imagePath = "/api/v1/returns/" + id + "/evidence/" + image;
        mvc.perform(get(imagePath).header(HttpHeaders.AUTHORIZATION, bearer("other")))
                .andExpect(status().isNotFound());
        mvc.perform(get(imagePath).header(HttpHeaders.AUTHORIZATION, bearer("admin")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(evidence().getBytes()));

        assertThat(uuid(read(create(order, requestKey, 1, "Damaged", evidence())), "return_id")).isEqualTo(id);
        create(order, requestKey, 1, "Other reason", evidence()).andExpect(status().isConflict());
        decision(id, "REQUEST_IMAGES", "Show the seam").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MORE_IMAGES_REQUESTED"));
        String uploadPath = "/api/v1/returns/" + id + "/evidence";
        mvc.perform(multipart(uploadPath).file(evidence()).header(HttpHeaders.AUTHORIZATION, bearer("other")))
                .andExpect(status().isNotFound());
        mvc.perform(multipart(uploadPath).file(evidence()).header(HttpHeaders.AUTHORIZATION, bearer("owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.evidence.length()").value(2));
        decision(id, "APPROVE_PICKUP", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKUP_APPROVED"));
        String source = source("RETURN", id);
        mvc.perform(get(source).header(TOKEN_HEADER, TOKEN)).andExpect(status().isNotFound());
        decision(id, "PASS_INSPECTION", "Items match").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REFUND"));
        mvc.perform(get(source)).andExpect(status().isUnauthorized());
        mvc.perform(get(source).header(TOKEN_HEADER, "generic-test-only-service-token-0123456789"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(source).header(TOKEN_HEADER, TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_attempt_id").value(order.attempt().toString()))
                .andExpect(jsonPath("$.amount_vnd").value(100_000));
        UUID refundId = UUID.randomUUID();
        String paidAt = Instant.now().truncatedTo(ChronoUnit.MICROS).toString();
        callback(refundId, "RETURN", id, order.id(), 99_999, paidAt).andExpect(status().isConflict());
        callback(refundId, "RETURN", id, order.id(), 100_000, paidAt).andExpect(status().isNoContent());
        callback(refundId, "RETURN", id, order.id(), 100_000, paidAt).andExpect(status().isNoContent());
        callback(UUID.randomUUID(), "RETURN", id, order.id(), 100_000, paidAt).andExpect(status().isConflict());
        mvc.perform(get(returns(order.id())).header(HttpHeaders.AUTHORIZATION, bearer("owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.items[0].history.length()").value(6))
                .andExpect(jsonPath("$.available_items[0].quantity").value(1));
        assertThat(jdbc.queryForObject("SELECT quantity FROM product_variant WHERE variant_id=?",
                Integer.class, order.variant())).isEqualTo(8);
    }

    @Test
    void concurrentRequestsCannotReserveOverlappingQuantityAndRejectionReleasesIt() throws Exception {
        var order = paidCodOrder();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> concurrentCreate(order, start));
            var b = executor.submit(() -> concurrentCreate(order, start));
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        JsonNode listing = read(mvc.perform(get(returns(order.id()))
                .header(HttpHeaders.AUTHORIZATION, bearer("owner"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false)));
        UUID id = uuid(listing.path("items").get(0), "return_id");
        decision(id, "APPROVE_PICKUP", null).andExpect(status().isOk());
        decision(id, "FAIL_INSPECTION", "Item differs").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        create(order, UUID.randomUUID(), 2, "Damaged", evidence()).andExpect(status().isCreated());
    }

    @Test
    void orderListsProjectCollectedCodAndPartialThenFullRefundWithoutChangingDeliveryState() throws Exception {
        var order = paidCodOrder();
        String detail = "/api/v1/orders/" + order.id();
        mvc.perform(get(detail).header(HttpHeaders.AUTHORIZATION, bearer("owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment_status").value("PAID"))
                .andExpect(jsonPath("$.refunded_amount_vnd").value(0));
        for (int i = 1; i <= 2; i++) {
            UUID id = uuid(read(create(order, UUID.randomUUID(), 1, "Damaged", evidence())), "return_id");
            decision(id, "APPROVE_PICKUP", null).andExpect(status().isOk());
            decision(id, "PASS_INSPECTION", null).andExpect(status().isOk());
            callback(UUID.randomUUID(), "RETURN", id, order.id(), 100_000,
                    Instant.now().truncatedTo(ChronoUnit.MICROS).toString()).andExpect(status().isNoContent());
            mvc.perform(get(detail).header(HttpHeaders.AUTHORIZATION, bearer("owner")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.order_status").value("COMPLETED"))
                    .andExpect(jsonPath("$.refunded_amount_vnd").value(i * 100_000))
                    .andExpect(jsonPath("$.refund_status").value(i == 1 ? "PARTIALLY_REFUNDED" : "REFUNDED"));
        }
        mvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, bearer("owner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.order_id=='" + order.id() + "')].refund_status").value(org.hamcrest.Matchers.contains("REFUNDED")));
    }

    @Test
    void cancellationCompletionUpdatesExistingObligationAndCannotBeReusedForAnotherSource() throws Exception {
        var order = order(OWNER, "SEPAY_VIETQR", "CANCELLED", null);
        UUID event = payment(order, OWNER, "SEPAY_VIETQR", 200_000);
        UUID request = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cancellation_request(cancellation_request_id,order_id,requested_by_user_id,reason,
                    request_status,requested_at,reviewed_by_user_id,reviewed_at)
                VALUES (?,?,?,'change plans','APPROVED',now(),?,now())
                """, request, order.id(), OWNER, ADMIN);
        jdbc.update("""
                INSERT INTO cancellation_refund_obligation(cancellation_request_id,order_id,payment_event_id,
                    amount_vnd,obligation_status,created_at)
                VALUES (?,?,?,200000,'PENDING_REFUND',now())
                """, request, order.id(), event);
        mvc.perform(get(source("CANCELLATION", request)).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_attempt_id").value(order.attempt().toString()));
        UUID refund = UUID.randomUUID();
        String paidAt = Instant.now().truncatedTo(ChronoUnit.MICROS).toString();
        callback(refund, "CANCELLATION", request, UUID.randomUUID(), 200_000, paidAt)
                .andExpect(status().isConflict());
        callback(refund, "CANCELLATION", request, order.id(), 200_000, paidAt)
                .andExpect(status().isNoContent());
        callback(refund, "CANCELLATION", request, order.id(), 200_000, paidAt)
                .andExpect(status().isNoContent());
        var second = paidCodOrder();
        UUID returnId = uuid(read(create(second, UUID.randomUUID(), 1, "Damaged", evidence())), "return_id");
        decision(returnId, "APPROVE_PICKUP", null).andExpect(status().isOk());
        decision(returnId, "PASS_INSPECTION", null).andExpect(status().isOk());
        callback(refund, "RETURN", returnId, second.id(), 100_000, paidAt).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT obligation_status FROM cancellation_refund_obligation WHERE cancellation_request_id=?",
                String.class, request)).isEqualTo("REFUNDED");
    }

    @Test
    void aggregatesRepeatedOrderLinesAndRejectsClientSpecifiedPricesAndForeignLines() throws Exception {
        var order = paidCodOrder();
        var otherOrder = paidCodOrder();
        var malformedAmount = createBody(order.item(), UUID.randomUUID(), 1, "Damaged")
                .put("amount_vnd", 1);
        create(order, malformedAmount, evidence()).andExpect(status().isBadRequest());
        create(order, createBody(otherOrder.item(), UUID.randomUUID(), 1, "Damaged"), evidence())
                .andExpect(status().isConflict());
        var duplicateLine = createBody(order.item(), UUID.randomUUID(), 2, "Damaged");
        duplicateLine.withArray("items").add(json.createObjectNode()
                .put("order_item_id", order.item().toString()).put("quantity", 1));
        create(order, duplicateLine, evidence()).andExpect(status().isConflict());
        var combined = createBody(order.item(), UUID.randomUUID(), 1, "Damaged");
        combined.withArray("items").add(json.createObjectNode()
                .put("order_item_id", order.item().toString()).put("quantity", 1));
        create(order, combined, evidence()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.amount_vnd").value(200_000));
        mvc.perform(get("/api/v1/admin/returns?page=0&size=1")
                .header(HttpHeaders.AUTHORIZATION, bearer("admin"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items[0].order_id").value(order.id().toString()));
    }

    private int concurrentCreate(TestOrder order, CountDownLatch start) throws Exception {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start");
        }
        return create(order, UUID.randomUUID(), 2, "Damaged", evidence()).andReturn().getResponse().getStatus();
    }

    private TestOrder paidCodOrder() {
        var order = order(OWNER, "COD", "COMPLETED", OffsetDateTime.now().minusHours(1));
        payment(order, OWNER, "COD", 200_000);
        return order;
    }

    private TestOrder order(UUID owner, String method, String state, OffsetDateTime completedAt) {
        var order = new TestOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID category = UUID.randomUUID();
        UUID brand = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        jdbc.update("INSERT INTO category(category_id,name,slug,is_active) VALUES (?,?,?,true)",
                category, "Tops", category.toString());
        jdbc.update("INSERT INTO brand(brand_id,name,slug,is_active) VALUES (?,?,?,true)",
                brand, "StyleMind", brand.toString());
        jdbc.update("INSERT INTO product(product_id,category_id,brand_id,name,product_status) VALUES (?,?,?,'Shirt','ACTIVE')",
                product, category, brand);
        jdbc.update("INSERT INTO product_variant(variant_id,product_id,sku,unit_price_vnd,quantity,variant_status) VALUES (?,?,?,999999,8,'ACTIVE')",
                order.variant(), product, order.variant().toString());
        jdbc.update("""
                INSERT INTO customer_order(order_id,order_number,customer_id,checkout_key,order_status,
                    payment_method,accepted_total_vnd,placed_at,completed_at)
                VALUES (?,?,?,?,?,?,200000,now()-interval '10 days',?)
                """, order.id(), order.id().toString(), owner, order.id().toString(), state, method, completedAt);
        jdbc.update("INSERT INTO order_address_snapshot(order_id,recipient_name,recipient_phone,address_line) VALUES (?,'Test Owner','+84900000000','Synthetic address')", order.id());
        jdbc.update("""
                INSERT INTO order_item(order_item_id,order_id,variant_id,sku_snapshot,product_name_snapshot,
                    brand_name_snapshot,color_snapshot,size_snapshot,quantity,unit_price_vnd)
                VALUES (?,?,?,'original-sku','Original shirt','StyleMind','Blue','M',2,100000)
                """, order.item(), order.id(), order.variant());
        return order;
    }

    private UUID payment(TestOrder order, UUID owner, String method, long amount) {
        UUID event = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment_event_inbox(event_id,event_fingerprint,order_id,payment_attempt_id,customer_id,
                    payment_method,payment_reference,amount_vnd,confirmed_at,received_at,outcome,reason)
                VALUES (?,?,?,?,?,?,'RETURN-TEST',?,now(),now(),'APPLIED','PAYMENT_CONFIRMED')
                """, event, "a".repeat(64), order.id(), order.attempt(), owner, method, amount);
        return event;
    }

    private ResultActions create(TestOrder order, UUID key, int quantity, String reason, MockMultipartFile... images)
            throws Exception {
        return create(order, createBody(order.item(), key, quantity, reason), images);
    }

    private ObjectNode createBody(UUID item, UUID key, int quantity, String reason) {
        return json.createObjectNode().put("request_key", key.toString()).put("reason", reason)
                .set("items", json.createArrayNode().add(json.createObjectNode()
                        .put("order_item_id", item.toString()).put("quantity", quantity)));
    }

    private ResultActions create(TestOrder order, ObjectNode body, MockMultipartFile... images) throws Exception {
        var request = multipart(returns(order.id())).file(new MockMultipartFile(
                "request", "request.json", MediaType.APPLICATION_JSON_VALUE, json.writeValueAsBytes(body)));
        for (var image : images) {
            request.file(image);
        }
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer("owner")));
    }

    private ResultActions decision(UUID id, String action, String reason) throws Exception {
        return mvc.perform(post("/api/v1/admin/returns/" + id + "/decision")
                .header(HttpHeaders.AUTHORIZATION, bearer("admin")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(json.createObjectNode().put("action", action).put("reason", reason))));
    }

    private ResultActions callback(UUID refundId, String type, UUID sourceId, UUID order, long amount, String time)
            throws Exception {
        return mvc.perform(post(CALLBACK).header(TOKEN_HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(json.createObjectNode().put("refund_id", refundId.toString())
                        .put("source_type", type).put("source_id", sourceId.toString()).put("order_id", order.toString())
                        .put("amount_vnd", amount).put("refunded_at", time))));
    }

    private MockMultipartFile evidence() throws Exception {
        return evidence("png");
    }

    private MockMultipartFile evidence(String format) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
        return new MockMultipartFile("images", "evidence." + format, "image/" + format, output.toByteArray());
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.path(field).asText());
    }

    private void mockJwt(String token, UUID subject, String role) {
        when(jwtDecoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg", "none")
                .subject(subject.toString()).claim("roles", List.of(role)).build());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String returns(UUID id) {
        return "/api/v1/orders/" + id + "/returns";
    }

    private static String source(String type, UUID id) {
        return "/internal/v1/refund-sources/" + type + "/" + id;
    }

    private record TestOrder(UUID id, UUID item, UUID variant, UUID attempt) { }
}
