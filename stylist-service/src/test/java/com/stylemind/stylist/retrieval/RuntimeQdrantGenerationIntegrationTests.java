package com.stylemind.stylist.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.stylist.model.generation.GenerationModel.RuntimeCandidate;
import com.stylemind.stylist.repository.retrieval.RuntimeQdrantVectorStore;
import com.stylemind.stylist.repository.retrieval.RuntimeQdrantVectorStore.IndexPoint;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy.Policy;
import com.stylemind.stylist.service.retrieval.RuntimeEmbeddingPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Runtime 1536-dimensional adapter verification against an isolated Qdrant Testcontainer. */
@Testcontainers(disabledWithoutDocker = true)
class RuntimeQdrantGenerationIntegrationTests {
    private static final int PORT = 6333;

    @Container
    static final GenericContainer<?> QDRANT = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.20.0"))
            .withExposedPorts(PORT)
            .waitingFor(Wait.forHttp("/collections").forPort(PORT).forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(90)));

    @Test
    void publishesCompleteServiceScoped1536CosineIndexAndSearchesStrictTopBottomCategories() {
        UUID topCategory = UUID.randomUUID();
        UUID bottomCategory = UUID.randomUUID();
        UUID outsideCategory = UUID.randomUUID();
        Policy policy = policy(topCategory, bottomCategory);
        String alias = "stylemind_stylist_runtime_test_" + UUID.randomUUID().toString().replace("-", "");
        RuntimeQdrantVectorStore store = new RuntimeQdrantVectorStore(new ObjectMapper(),
                "http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(PORT), alias, "catalog-test-v2", "");

        UUID allowedTop = UUID.randomUUID();
        UUID outOfPolicyTop = UUID.randomUUID();
        UUID allowedBottom = UUID.randomUUID();
        List<IndexPoint> points = List.of(
                point(allowedTop, UUID.randomUUID(), topCategory, "TOP", 1.0f),
                point(outOfPolicyTop, UUID.randomUUID(), outsideCategory, "TOP", 0.99f),
                point(allowedBottom, UUID.randomUUID(), bottomCategory, "BOTTOM", 1.0f));
        String physical = "stylemind_stylist_catalog_" + UUID.randomUUID().toString().replace("-", "");

        store.buildAndPublish(physical, policy, points);

        assertThat(store.isReady(policy)).isTrue();
        assertThat(store.isReady(policy("different-revision", topCategory, bottomCategory))).isFalse();
        float[] query = vector(1.0f);
        List<RuntimeCandidate> tops = store.search(query, "TOP", List.of(topCategory), 20, "review-v1");
        List<RuntimeCandidate> bottoms = store.search(query, "BOTTOM", List.of(bottomCategory), 20, "review-v1");
        assertThat(tops).extracting(RuntimeCandidate::variantId).containsExactly(allowedTop);
        assertThat(bottoms).extracting(RuntimeCandidate::variantId).containsExactly(allowedBottom);
        assertThat(tops).allSatisfy(candidate -> {
            assertThat(candidate.slot()).isEqualTo("TOP");
            assertThat(candidate.demographicSegment()).isEqualTo("WOMEN");
        });
        assertThat(bottoms).allSatisfy(candidate -> assertThat(candidate.slot()).isEqualTo("BOTTOM"));
    }

    @Test
    void geminiUsesSeparateAliasAndCannotReadAnOpenAiIndexEvenAtTheSameDimension() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String qdrantUrl = "http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(PORT);
        RuntimeEmbeddingPort.EmbeddingMetadata openAi = new RuntimeEmbeddingPort.EmbeddingMetadata(
                "openai", "text-embedding-3-small", 1_536, "openai-text-embedding-3-small-1536-v1");
        RuntimeEmbeddingPort.EmbeddingMetadata gemini = new RuntimeEmbeddingPort.EmbeddingMetadata(
                "gemini", "gemini-embedding-001", 1_536, "gemini-embedding-001-1536-l2-query-document-v1");
        UUID topCategory = UUID.randomUUID();
        UUID bottomCategory = UUID.randomUUID();
        Policy policy = policy(topCategory, bottomCategory);

        String openAiPhysical = "stylemind_stylist_catalog_" + UUID.randomUUID().toString().replace("-", "");
        RuntimeQdrantVectorStore openAiStore = new RuntimeQdrantVectorStore(mapper, openAi, qdrantUrl, "",
                "catalog-test-v2", "");
        UUID openAiVariant = UUID.randomUUID();
        openAiStore.buildAndPublish(openAiPhysical, policy,
                List.of(point(openAiVariant, UUID.randomUUID(), topCategory, "TOP", 1.0f, openAi)));

        String geminiAlias = "stylemind_stylist_runtime_gemini_mismatch_" + UUID.randomUUID().toString().replace("-", "");
        createAlias(qdrantUrl, openAiPhysical, geminiAlias, mapper);
        RuntimeQdrantVectorStore geminiStore = new RuntimeQdrantVectorStore(mapper, gemini, qdrantUrl,
                geminiAlias, "catalog-test-v2", "");

        assertThat(openAiStore.isReady(policy)).isTrue();
        assertThat(geminiStore.isReady(policy)).isFalse();
        assertThat(geminiStore.search(vector(1.0f), "TOP", List.of(topCategory), 20, "review-v1"))
                .isEmpty();

        JsonNode aliases = mapper.readTree(get(qdrantUrl + "/aliases")).path("result").path("aliases");
        assertThat(aliases.findValuesAsText("alias_name"))
                .contains("stylemind_stylist_runtime_v1", geminiAlias);
        assertThat(aliases.findValuesAsText("collection_name")).contains(openAiPhysical);
        assertThat(geminiStore.embeddingMetadata()).isEqualTo(gemini);
    }

    private static Policy policy(UUID topCategory, UUID bottomCategory) {
        return policy("review-v1", topCategory, bottomCategory);
    }

    private static Policy policy(String revision, UUID topCategory, UUID bottomCategory) {
        String json = """
                {"review_status":"APPROVED","policy_revision":"%s","reviewed_by":"test-fixture",
                 "category_mappings":[
                   {"category_id":"%s","slot":"TOP","demographic_segment":"WOMEN","review_status":"APPROVED","reviewed_by":"test-fixture"},
                   {"category_id":"%s","slot":"BOTTOM","demographic_segment":"WOMEN","review_status":"APPROVED","reviewed_by":"test-fixture"}],
                 "colors":[]}
                """.formatted(revision, topCategory, bottomCategory);
        return new ReviewedCatalogPolicy(new ObjectMapper(), "").parse(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static IndexPoint point(UUID variantId, UUID productId, UUID categoryId, String slot, float first) {
        RuntimeEmbeddingPort.EmbeddingMetadata metadata = new RuntimeEmbeddingPort.EmbeddingMetadata(
                "openai", RuntimeQdrantVectorStore.MODEL, RuntimeQdrantVectorStore.DIMENSIONS,
                "openai-text-embedding-3-small-1536-v1");
        return point(variantId, productId, categoryId, slot, first, metadata);
    }

    private static IndexPoint point(UUID variantId, UUID productId, UUID categoryId, String slot, float first,
                                    RuntimeEmbeddingPort.EmbeddingMetadata metadata) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("record_type", "CATALOG_CANDIDATE");
        payload.put("catalog_source", "SYNTHETIC_TEST_ONLY");
        payload.put("product_id", productId.toString());
        payload.put("variant_id", variantId.toString());
        payload.put("category_id", categoryId.toString());
        payload.put("slot", slot);
        payload.put("demographic_segment", "WOMEN");
        payload.put("product_name", "Synthetic test garment");
        payload.put("policy_revision", "review-v1");
        payload.put("text_template_revision", "catalog-test-v2");
        payload.put("embedding_model", metadata.model());
        payload.put("embedding_provider", metadata.provider());
        payload.put("embedding_dimensions", metadata.dimensions());
        payload.put("embedding_configuration_version", metadata.configurationVersion());
        return new IndexPoint(variantId, vector(first), payload);
    }

    private static void createAlias(String baseUrl, String collection, String alias, ObjectMapper mapper)
            throws Exception {
        var body = mapper.createObjectNode();
        body.putArray("actions").addObject().putObject("create_alias")
                .put("collection_name", collection).put("alias_name", alias);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/collections/aliases"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
    }

    private static String get(String url) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
        return response.body();
    }

    private static float[] vector(float first) {
        float[] vector = new float[RuntimeQdrantVectorStore.DIMENSIONS];
        vector[0] = first;
        vector[1] = 0.01f;
        return vector;
    }
}
