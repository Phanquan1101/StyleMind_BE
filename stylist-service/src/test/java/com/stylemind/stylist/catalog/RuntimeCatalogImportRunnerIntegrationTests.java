package com.stylemind.stylist.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.stylemind.stylist.StylistServiceApplication;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.client.commerce.CommerceCatalogClient;
import com.stylemind.stylist.repository.retrieval.RuntimeQdrantVectorStore;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy;
import com.stylemind.stylist.service.retrieval.RuntimeEmbeddingPort;
import com.stylemind.stylist.service.impl.catalog.RuntimeCatalogImportRunner;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Real importer -> Commerce Feign -> local embedding HTTP -> disposable runtime Qdrant. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("catalog-import")
@SpringBootTest(classes = StylistServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "management.health.neo4j.enabled=false",
            "management.health.db.enabled=false",
            "stylemind.stylist.catalog.import-enabled=false",
            "stylemind.openai.enabled=true",
            "stylemind.stylist.knowledge.enabled=false",
            "stylemind.stylist.retrieval.collection-alias=stylemind_stylist_runtime_wave2_import_test",
            "stylemind.stylist.retrieval.text-template-revision=wave2-import-fixture-v1",
            "stylemind.clients.commerce.connect-timeout-ms=500",
            "stylemind.clients.commerce.read-timeout-ms=1000"
        })
class RuntimeCatalogImportRunnerIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID TOP_CATEGORY = UUID.fromString("51000000-0000-4000-8000-000000000001");
    private static final UUID BOTTOM_CATEGORY = UUID.fromString("51000000-0000-4000-8000-000000000002");
    private static final String ALIAS = "stylemind_stylist_runtime_wave2_import_test";
    private static final AtomicReference<List<ObjectNode>> PRODUCTS = new AtomicReference<>(baselineProducts());
    private static final List<String> COMMERCE_REQUESTS = new CopyOnWriteArrayList<>();
    private static final List<Integer> EMBEDDING_BATCH_SIZES = new CopyOnWriteArrayList<>();
    private static final HttpServer COMMERCE = startServer("commerce-import-fixture", RuntimeCatalogImportRunnerIntegrationTests::commerce);
    private static final HttpServer EMBEDDINGS = startServer("openai-embedding-fixture", RuntimeCatalogImportRunnerIntegrationTests::embeddings);
    private static final Path POLICY_FILE = writePolicy();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("stylemind_stylist_import_test")
            .withUsername("stylist_import_test")
            .withPassword("stylist_import_test_only");

    @Container
    static final GenericContainer<?> QDRANT = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.20.0"))
            .withExposedPorts(6333)
            .waitingFor(Wait.forHttp("/collections").forPort(6333).forStatusCode(200));

    @Autowired CommerceCatalogClient commerce;
    @Autowired RuntimeEmbeddingPort embeddings;
    @Autowired RuntimeQdrantVectorStore qdrant;
    @Autowired ReviewedCatalogPolicy policyLoader;
    private RuntimeCatalogImportRunner importer;

    @DynamicPropertySource
    static void disposableInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("stylemind.clients.commerce.base-url", () -> baseUrl(COMMERCE));
        registry.add("stylemind.openai.base-url", () -> baseUrl(EMBEDDINGS));
        registry.add("stylemind.openai.api-key", () -> "local-fixture-key");
        registry.add("stylemind.stylist.retrieval.qdrant-url", () -> "http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333));
        registry.add("stylemind.stylist.catalog.policy-file", () -> POLICY_FILE.toString());
    }

    @BeforeEach
    void resetSyntheticProducerFixture() {
        PRODUCTS.set(baselineProducts());
        COMMERCE_REQUESTS.clear();
        EMBEDDING_BATCH_SIZES.clear();
        importer = new RuntimeCatalogImportRunner(commerce, policyLoader, embeddings, qdrant);
    }

    @AfterEach
    void removeOnlyThisFixtureAliasAndCollection() throws Exception {
        String collection = findPublishedCollection();
        if (collection == null) return;

        ObjectNode deleteAlias = JSON.createObjectNode();
        deleteAlias.putArray("actions").addObject().putObject("delete_alias").put("alias_name", ALIAS);
        var aliasRequest = java.net.http.HttpRequest.newBuilder(java.net.URI.create(qdrantUri() + "/collections/aliases"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(deleteAlias.toString())).build();
        var aliasResponse = java.net.http.HttpClient.newHttpClient().send(aliasRequest,
                java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(aliasResponse.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(aliasResponse.body()).path("result").asBoolean()).isTrue();

        var collectionRequest = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                qdrantUri() + "/collections/" + collection)).DELETE().build();
        var collectionResponse = java.net.http.HttpClient.newHttpClient().send(collectionRequest,
                java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(collectionResponse.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(collectionResponse.body()).path("result").asBoolean()).isTrue();
    }

    @AfterAll
    static void stopOnlyLocalHttpFixtures() {
        COMMERCE.stop(0);
        EMBEDDINGS.stop(0);
    }

    @Test
    void actualImportRunnerAcceptsNullableMetadataPaginatesAndPublishesQueryableIndex() throws Exception {
        List<ObjectNode> products = baselineProducts();
        for (int index = products.size(); index < 101; index++) products.add(inactiveMenProduct(index));
        PRODUCTS.set(List.copyOf(products));

        importer.run(new DefaultApplicationArguments(new String[0]));

        assertThat(COMMERCE_REQUESTS).containsExactly("page=0&size=100&demographic_segment=WOMEN",
                "page=1&size=100&demographic_segment=WOMEN");
        assertThat(EMBEDDING_BATCH_SIZES).contains(2);
        var policy = policyLoader.require();
        assertThat(qdrant.isReady(policy)).isTrue();
        float[] query = new float[RuntimeQdrantVectorStore.DIMENSIONS];
        query[0] = 1f;
        var top = qdrant.search(query, "TOP", List.of(TOP_CATEGORY), 5, policy.revision());
        var bottom = qdrant.search(query, "BOTTOM", List.of(BOTTOM_CATEGORY), 5, policy.revision());
        assertThat(top).hasSize(1).allSatisfy(candidate -> {
            assertThat(candidate.productName()).isEqualTo("Áo sơ mi trắng");
            assertThat(candidate.color()).isNull();
            assertThat(candidate.size()).isNull();
            assertThat(candidate.material()).isNull();
        });
        assertThat(bottom).hasSize(1).allSatisfy(candidate -> {
            assertThat(candidate.material()).isEqualTo("linen");
        });
    }

    @Test
    void configuredVariantAllowlistEmbedsOnlySelectedVariantsAndRejectsMissingIds() {
        List<ObjectNode> products = baselineProducts();
        UUID topVariantId = id(products.get(0).path("variants").get(0).path("variant_id").asText());
        UUID bottomVariantId = id(products.get(1).path("variants").get(0).path("variant_id").asText());
        addVariant(products.get(0), 101, "M", "blue", null);
        addVariant(products.get(1), 102, "L", "grey", "linen");
        PRODUCTS.set(List.copyOf(products));

        String selectedIds = topVariantId + "," + bottomVariantId;
        String unknownId = "53000000-0000-4000-8000-000000000999";
        RuntimeCatalogImportRunner missingVariantImporter = new RuntimeCatalogImportRunner(commerce, policyLoader,
                embeddings, qdrant, selectedIds + "," + unknownId);
        assertThatThrownBy(() -> missingVariantImporter.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(StylistApiException.class)
                .satisfies(failure -> assertThat(((StylistApiException) failure).errorCode())
                        .isEqualTo(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR));
        assertThat(EMBEDDING_BATCH_SIZES).isEmpty();
        assertThat(findPublishedCollection()).isNull();

        RuntimeCatalogImportRunner repeatedProductImporter = new RuntimeCatalogImportRunner(commerce, policyLoader,
                embeddings, qdrant, selectedIds + ",53000000-0000-4000-8000-000000000101");
        assertThatThrownBy(() -> repeatedProductImporter.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(StylistApiException.class)
                .satisfies(failure -> assertThat(((StylistApiException) failure).errorCode())
                        .isEqualTo(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR));
        assertThat(EMBEDDING_BATCH_SIZES).isEmpty();
        assertThat(findPublishedCollection()).isNull();

        RuntimeCatalogImportRunner allowlistedImporter = new RuntimeCatalogImportRunner(commerce, policyLoader,
                embeddings, qdrant, selectedIds);
        allowlistedImporter.run(new DefaultApplicationArguments(new String[0]));

        assertThat(EMBEDDING_BATCH_SIZES).containsExactly(2);
        float[] query = new float[RuntimeQdrantVectorStore.DIMENSIONS];
        query[0] = 1f;
        var top = qdrant.search(query, "TOP", List.of(TOP_CATEGORY), 5, policyLoader.require().revision());
        var bottom = qdrant.search(query, "BOTTOM", List.of(BOTTOM_CATEGORY), 5, policyLoader.require().revision());
        assertThat(top).extracting(candidate -> candidate.variantId()).containsExactly(topVariantId);
        assertThat(bottom).extracting(candidate -> candidate.variantId()).containsExactly(bottomVariantId);
    }

    @Test
    void duplicateIdsInvalidRequiredFieldsAndBlankOptionalMetadataDoNotPublishPartialIndex() {
        importer.run(new DefaultApplicationArguments(new String[0]));
        String originalCollection = publishedCollection();

        List<ObjectNode> duplicate = baselineProducts();
        ObjectNode repeated = duplicate.get(0).deepCopy();
        duplicate.add(repeated);
        PRODUCTS.set(List.copyOf(duplicate));
        assertImportRejected();
        assertThat(publishedCollection()).isEqualTo(originalCollection);

        List<ObjectNode> missingRequired = baselineProducts();
        missingRequired.get(0).put("name", " ");
        PRODUCTS.set(List.copyOf(missingRequired));
        assertImportRejected();
        assertThat(publishedCollection()).isEqualTo(originalCollection);

        List<ObjectNode> blankOptional = baselineProducts();
        ((ObjectNode) blankOptional.get(0).withArray("variants").get(0)).put("material", "  ");
        PRODUCTS.set(List.copyOf(blankOptional));
        assertImportRejected();
        assertThat(publishedCollection()).isEqualTo(originalCollection);

        float[] query = new float[RuntimeQdrantVectorStore.DIMENSIONS];
        query[0] = 1f;
        assertThat(qdrant.search(query, "TOP", List.of(TOP_CATEGORY), 5,
                policyLoader.require().revision())).hasSize(1);
    }

    private void assertImportRejected() {
        assertThatThrownBy(() -> importer.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(StylistApiException.class)
                .satisfies(failure -> assertThat(((StylistApiException) failure).errorCode())
                        .isEqualTo(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR));
    }

    private String publishedCollection() {
        String collection = findPublishedCollection();
        if (collection == null) throw new AssertionError("The disposable runtime alias was not published");
        return collection;
    }

    private String findPublishedCollection() {
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(qdrantUri() + "/aliases")).GET().build();
            var response = java.net.http.HttpClient.newHttpClient().send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode aliases = JSON.readTree(response.body()).path("result").path("aliases");
            for (JsonNode row : aliases) {
                if (ALIAS.equals(row.path("alias_name").asText())) return row.path("collection_name").asText();
            }
            return null;
        } catch (Exception exception) {
            throw new AssertionError("Could not inspect the disposable Qdrant alias", exception);
        }
    }

    private static String qdrantUri() {
        return "http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333);
    }

    private static void commerce(HttpExchange exchange) throws IOException {
        COMMERCE_REQUESTS.add(exchange.getRequestURI().getRawQuery());
        String body;
        if (!"GET".equals(exchange.getRequestMethod()) || !"/api/v1/products".equals(exchange.getRequestURI().getPath())) {
            body = "{}";
            respond(exchange, 404, body);
            return;
        }
        String query = exchange.getRequestURI().getRawQuery();
        int page = query != null && query.contains("page=1") ? 1 : 0;
        List<ObjectNode> products = PRODUCTS.get();
        int start = page * RuntimeCatalogImportRunner.PAGE_SIZE;
        int end = Math.min(products.size(), start + RuntimeCatalogImportRunner.PAGE_SIZE);
        ArrayNode rows = JSON.createArrayNode();
        products.subList(Math.min(start, products.size()), end).forEach(row -> rows.add(row.deepCopy()));
        ObjectNode result = JSON.createObjectNode().set("items", rows);
        result.put("page", page).put("size", RuntimeCatalogImportRunner.PAGE_SIZE)
                .put("total_elements", products.size()).put("total_pages",
                        (products.size() + RuntimeCatalogImportRunner.PAGE_SIZE - 1) / RuntimeCatalogImportRunner.PAGE_SIZE);
        respond(exchange, 200, result.toString());
    }

    private static void embeddings(HttpExchange exchange) throws IOException {
        JsonNode request = JSON.readTree(exchange.getRequestBody().readAllBytes());
        JsonNode input = request.path("input");
        int count = input.isArray() ? input.size() : 1;
        EMBEDDING_BATCH_SIZES.add(count);
        ArrayNode rows = JSON.createArrayNode();
        for (int index = 0; index < count; index++) {
            ObjectNode row = rows.addObject().put("object", "embedding").put("index", index);
            ArrayNode vector = row.putArray("embedding");
            for (int dimension = 0; dimension < 1_536; dimension++) vector.add(dimension == 0 ? 1f : 0.001f);
        }
        ObjectNode response = JSON.createObjectNode().put("object", "list").set("data", rows);
        response.put("model", "text-embedding-3-small");
        response.putObject("usage").put("prompt_tokens", count).put("total_tokens", count);
        respond(exchange, 200, response.toString());
    }

    private static List<ObjectNode> baselineProducts() {
        return new ArrayList<>(List.of(product(1, TOP_CATEGORY, "TOP", "Áo sơ mi trắng", null, null, null),
                product(2, BOTTOM_CATEGORY, "BOTTOM", "Quần linen", "M", "đen", "linen")));
    }

    private static ObjectNode product(int index, UUID category, String slot, String name,
                                      String size, String color, String material) {
        UUID product = id("52000000-0000-4000-8000-%012d".formatted(index));
        UUID variant = id("53000000-0000-4000-8000-%012d".formatted(index));
        ObjectNode root = JSON.createObjectNode().put("product_id", product.toString())
                .put("category_id", category.toString()).put("brand_id", "54000000-0000-4000-8000-000000000001")
                .put("category_name", "Womens " + slot).put("brand_name", "Synthetic Test Brand")
                .put("name", name).put("demographic_segment", "WOMEN").put("product_status", "ACTIVE");
        root.putNull("description");
        ObjectNode row = root.putArray("variants").addObject()
                .put("variant_id", variant.toString()).put("product_id", product.toString())
                .put("sku", "TEST-SKU-" + index).put("unit_price_vnd", 250_000)
                .put("variant_status", "ACTIVE");
        nullable(row, "size", size); nullable(row, "color", color); nullable(row, "material", material);
        row.putArray("images");
        return root;
    }

    private static void addVariant(ObjectNode product, int index, String size, String color, String material) {
        ObjectNode row = product.withArray("variants").addObject()
                .put("variant_id", "53000000-0000-4000-8000-%012d".formatted(index))
                .put("product_id", product.path("product_id").asText())
                .put("sku", "TEST-SKU-" + index).put("unit_price_vnd", 250_000)
                .put("variant_status", "ACTIVE");
        nullable(row, "size", size);
        nullable(row, "color", color);
        nullable(row, "material", material);
        row.putArray("images");
    }

    private static ObjectNode inactiveMenProduct(int index) {
        ObjectNode product = product(index + 1, UUID.randomUUID(), "OTHER", "Synthetic inactive product " + index,
                null, null, null);
        product.put("demographic_segment", "MEN").put("product_status", "INACTIVE");
        return product;
    }

    private static void nullable(ObjectNode node, String key, String value) {
        if (value == null) node.putNull(key); else node.put(key, value);
    }

    private static UUID id(String value) { return UUID.fromString(value); }

    private static HttpServer startServer(String name, com.sun.net.httpserver.HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task, name);
                thread.setDaemon(true);
                return thread;
            }));
            server.createContext("/", handler);
            server.start();
            return server;
        } catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static Path writePolicy() {
        try {
            Path target = Path.of("target", "wave2-runtime-import-fixture", "catalog-policy.synthetic-test-only.json")
                    .toAbsolutePath();
            Files.createDirectories(target.getParent());
            String json = """
                    {"review_status":"APPROVED","policy_revision":"wave2-synthetic-only-v1",
                     "reviewed_by":"SYNTHETIC_TEST_ONLY","category_mappings":[
                      {"category_id":"%s","slot":"TOP","demographic_segment":"WOMEN","review_status":"APPROVED","reviewed_by":"SYNTHETIC_TEST_ONLY"},
                      {"category_id":"%s","slot":"BOTTOM","demographic_segment":"WOMEN","review_status":"APPROVED","reviewed_by":"SYNTHETIC_TEST_ONLY"}],"colors":[]}
                    """.formatted(TOP_CATEGORY, BOTTOM_CATEGORY);
            Files.writeString(target, json, StandardCharsets.UTF_8);
            return target;
        } catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
}
