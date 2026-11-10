package com.stylemind.stylist.repository.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.model.generation.GenerationModel.RuntimeCandidate;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy.Policy;
import com.stylemind.stylist.service.retrieval.RuntimeCandidateSearch;
import com.stylemind.stylist.service.retrieval.RuntimeEmbeddingPort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Runtime Qdrant adapter. The P04.1 adapter remains fixture-only and unchanged. */
@Component
public class RuntimeQdrantVectorStore implements RuntimeCandidateSearch {
    public static final int DIMENSIONS = 1_536;
    public static final String MODEL = "text-embedding-3-small";
    public static final String DISTANCE = "Cosine";
    private static final RuntimeEmbeddingPort.EmbeddingMetadata TEST_OPENAI_METADATA =
            new RuntimeEmbeddingPort.EmbeddingMetadata("openai", MODEL, DIMENSIONS,
                    "openai-text-embedding-3-small-1536-v1");
    private static final String COLLECTION_PREFIX = "stylemind_stylist_catalog_";
    private static final UUID MANIFEST_ID = UUID.nameUUIDFromBytes(
            "stylemind-stylist-runtime-index-manifest-v1".getBytes(StandardCharsets.UTF_8));

    private final ObjectMapper mapper;
    private final String configuredUrl;
    private final String alias;
    private final String templateRevision;
    private final String apiKey;
    private final RuntimeEmbeddingPort.EmbeddingMetadata embeddingMetadata;
    private final HttpClient http;
    private final Set<String> createdCollections = new HashSet<>();
    private final Set<String> publishedCollections = new HashSet<>();

    @Autowired
    public RuntimeQdrantVectorStore(ObjectMapper mapper, RuntimeEmbeddingPort embeddings,
            @Value("${stylemind.stylist.retrieval.qdrant-url:}") String configuredUrl,
            @Value("${stylemind.stylist.retrieval.collection-alias:}") String alias,
            @Value("${stylemind.stylist.retrieval.text-template-revision:catalog-v1}") String templateRevision,
            @Value("${STYLEMIND_STYLIST_QDRANT_API_KEY:}") String apiKey) {
        this(mapper, embeddings.metadata(), configuredUrl, alias, templateRevision, apiKey);
    }

    /** Backward-compatible constructor for existing OpenAI Qdrant integration fixtures. */
    public RuntimeQdrantVectorStore(ObjectMapper mapper, String configuredUrl, String alias,
            String templateRevision, String apiKey) {
        this(mapper, TEST_OPENAI_METADATA, configuredUrl, alias, templateRevision, apiKey);
    }

    public RuntimeQdrantVectorStore(ObjectMapper mapper, RuntimeEmbeddingPort.EmbeddingMetadata embeddingMetadata,
            String configuredUrl, String alias, String templateRevision, String apiKey) {
        this.mapper = mapper;
        this.configuredUrl = configuredUrl == null ? "" : configuredUrl.trim();
        this.embeddingMetadata = Objects.requireNonNull(embeddingMetadata);
        String configuredAlias = alias == null ? "" : alias.trim();
        this.alias = configuredAlias.isBlank() ? defaultAlias(embeddingMetadata) : configuredAlias;
        this.templateRevision = templateRevision == null ? "" : templateRevision.trim();
        this.apiKey = apiKey == null ? "" : apiKey;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        if (!this.alias.matches("stylemind_stylist_runtime_[a-z0-9_]{1,48}")) {
            throw new IllegalArgumentException("Runtime Qdrant alias must be service-scoped");
        }
        if (embeddingMetadata.dimensions() != DIMENSIONS) {
            throw new IllegalArgumentException("Runtime Qdrant requires the approved 1536-dimensional embedding profile");
        }
        if ("gemini".equals(embeddingMetadata.provider())
                && !this.alias.startsWith("stylemind_stylist_runtime_gemini_")) {
            throw new IllegalArgumentException("Gemini requires a separate Gemini-scoped Qdrant alias");
        }
        if ("openai".equals(embeddingMetadata.provider())
                && this.alias.startsWith("stylemind_stylist_runtime_gemini_")) {
            throw new IllegalArgumentException("OpenAI cannot use the Gemini Qdrant alias namespace");
        }
        if (!this.configuredUrl.isBlank()) validateOrigin(this.configuredUrl);
    }

    public RuntimeEmbeddingPort.EmbeddingMetadata embeddingMetadata() { return embeddingMetadata; }

    public boolean isConfigured() {
        return !configuredUrl.isBlank() && !templateRevision.isBlank();
    }

    public boolean isReady(Policy policy) {
        return isReady(policy, Duration.ofSeconds(4));
    }

    public boolean isReady(Policy policy, Duration timeout) {
        if (!isConfigured() || policy == null) return false;
        if (timeout == null || timeout.isNegative() || timeout.isZero()) return false;
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            JsonNode info = send("GET", "/collections/" + alias, null, remaining(deadline));
            if (!collectionShapeMatches(info)) return false;
            JsonNode manifestResponse = send("GET", "/collections/" + alias + "/points/" + MANIFEST_ID, null,
                    remaining(deadline));
            JsonNode payload = manifestResponse.path("result").path("payload");
            return "INDEX_MANIFEST".equals(payload.path("record_type").asText())
                    && "COMPLETE".equals(payload.path("import_status").asText())
                    && payload.path("embedding_provider").asText().equals(embeddingMetadata.provider())
                    && payload.path("model").asText().equals(embeddingMetadata.model())
                    && payload.path("dimensions").asInt(-1) == embeddingMetadata.dimensions()
                    && payload.path("embedding_configuration_version").asText()
                            .equals(embeddingMetadata.configurationVersion())
                    && payload.path("distance").asText().equals(DISTANCE)
                    && payload.path("text_template_revision").asText().equals(templateRevision)
                    && payload.path("policy_revision").asText().equals(policy.revision())
                    && payload.path("candidate_count").canConvertToLong()
                    && payload.path("candidate_count").longValue() > 0;
        } catch (StylistApiException failure) {
            if (failure.errorCode() == ErrorCode.GENERATION_PROVIDER_TIMEOUT) throw failure;
            return false;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    @Override
    public List<RuntimeCandidate> search(float[] queryVector, String slot, List<UUID> categoryIds,
                                         int limit, String policyRevision) {
        return search(queryVector, slot, categoryIds, limit, policyRevision, Duration.ofSeconds(2));
    }

    @Override
    public List<RuntimeCandidate> search(float[] queryVector, String slot, List<UUID> categoryIds,
                                         int limit, String policyRevision, Duration timeout) {
        if (queryVector == null || queryVector.length != embeddingMetadata.dimensions() || !finiteNonZero(queryVector)
                || !("TOP".equals(slot) || "BOTTOM".equals(slot)) || categoryIds == null
                || categoryIds.isEmpty() || categoryIds.size() > 500 || limit < 1 || limit > 20
                || policyRevision == null || policyRevision.isBlank() || timeout == null
                || timeout.isNegative() || timeout.isZero()) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
        ObjectNode body = mapper.createObjectNode();
        ArrayNode vector = body.putArray("query");
        for (float value : queryVector) vector.add(value);
        body.put("limit", limit).put("with_payload", true).put("with_vectors", false);
        ObjectNode filter = body.putObject("filter");
        ArrayNode must = filter.putArray("must");
        match(must, "record_type", "CATALOG_CANDIDATE");
        match(must, "slot", slot);
        match(must, "demographic_segment", "WOMEN");
        match(must, "policy_revision", policyRevision);
        match(must, "embedding_provider", embeddingMetadata.provider());
        match(must, "embedding_model", embeddingMetadata.model());
        match(must, "embedding_configuration_version", embeddingMetadata.configurationVersion());
        match(must, "embedding_dimensions", embeddingMetadata.dimensions());
        ArrayNode anyApprovedCategory = must.addObject().putArray("should");
        categoryIds.forEach(id -> match(anyApprovedCategory, "category_id", id.toString()));

        JsonNode response = send("POST", "/collections/" + alias + "/points/query", body, timeout);
        JsonNode points = response.path("result").path("points");
        if (!points.isArray()) throw protocol();
        Set<UUID> ids = new HashSet<>();
        List<RuntimeCandidate> candidates = new ArrayList<>(points.size());
        for (JsonNode point : points) {
            UUID id = uuid(point.path("id").asText());
            double score = point.path("score").asDouble(Double.NaN);
            JsonNode payload = point.path("payload");
            if (!ids.add(id) || !Double.isFinite(score) || !payload.isObject()) throw protocol();
            Map<String, Object> values = mapper.convertValue(payload, new TypeReference<>() { });
            candidates.add(candidate(id, score, values, slot, categoryIds, policyRevision));
        }
        return List.copyOf(candidates);
    }

    public synchronized void buildAndPublish(String physicalCollection, Policy policy,
                                              List<IndexPoint> candidates) {
        if (!isConfigured() || policy == null || candidates == null || candidates.isEmpty()
                || !physicalCollection.matches(COLLECTION_PREFIX + "[a-f0-9]{32}")) {
            throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        }
        Set<UUID> ids = new HashSet<>();
        for (IndexPoint candidate : candidates) {
            if (candidate == null || candidate.id() == null || candidate.id().equals(MANIFEST_ID)
                    || !ids.add(candidate.id()) || !finiteNonZero(candidate.vector())
                    || candidate.payload() == null || !hasCurrentEmbeddingMetadata(candidate.payload())) throw protocol();
        }
        boolean published = false;
        try {
            createCollection(physicalCollection);
            upsert(physicalCollection, candidates);
            if (count(physicalCollection) != candidates.size()) throw protocol();
            upsert(physicalCollection, List.of(manifest(policy, candidates.size())));
            if (count(physicalCollection) != candidates.size() + 1L || !collectionShapeMatches(
                    send("GET", "/collections/" + physicalCollection, null))) throw protocol();
            publishAlias(physicalCollection);
            published = true;
            publishedCollections.add(physicalCollection);
        } finally {
            if (!published && createdCollections.contains(physicalCollection)) {
                try { deleteOwnedCollection(physicalCollection); } catch (RuntimeException ignored) { }
            }
        }
    }

    private RuntimeCandidate candidate(UUID pointId, double score, Map<String, Object> payload, String slot,
                                       List<UUID> allowedCategories, String policyRevision) {
        UUID productId = uuid(string(payload, "product_id"));
        UUID variantId = uuid(string(payload, "variant_id"));
        UUID categoryId = uuid(string(payload, "category_id"));
        if (!pointId.equals(variantId) || !allowedCategories.contains(categoryId)
                || !slot.equals(string(payload, "slot"))
                || !"WOMEN".equals(string(payload, "demographic_segment"))
                || !policyRevision.equals(string(payload, "policy_revision"))
                || !"CATALOG_CANDIDATE".equals(string(payload, "record_type"))
                || !hasCurrentEmbeddingMetadata(payload)) throw protocol();
        return new RuntimeCandidate(productId, variantId, categoryId,
                nullableString(payload, "category_name"), slot, "WOMEN",
                required(payload, "product_name"), nullableString(payload, "description"),
                nullableString(payload, "color"), nullableString(payload, "size"),
                nullableString(payload, "material"), score);
    }

    private IndexPoint manifest(Policy policy, int count) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("record_type", "INDEX_MANIFEST");
        payload.put("import_status", "COMPLETE");
        payload.put("embedding_provider", embeddingMetadata.provider());
        payload.put("model", embeddingMetadata.model());
        payload.put("dimensions", embeddingMetadata.dimensions());
        payload.put("embedding_configuration_version", embeddingMetadata.configurationVersion());
        payload.put("distance", DISTANCE);
        payload.put("text_template_revision", templateRevision);
        payload.put("policy_revision", policy.revision());
        payload.put("candidate_count", count);
        float[] vector = new float[DIMENSIONS];
        vector[0] = 1f;
        return new IndexPoint(MANIFEST_ID, vector, payload, true);
    }

    private void createCollection(String collection) {
        JsonNode exists = send("GET", "/collections/" + collection + "/exists", null)
                .path("result").path("exists");
        if (!exists.isBoolean() || exists.asBoolean()) throw protocol();
        ObjectNode body = mapper.createObjectNode();
        body.putObject("vectors").put("size", embeddingMetadata.dimensions()).put("distance", DISTANCE);
        JsonNode response = send("PUT", "/collections/" + collection, body);
        if (!response.path("result").isBoolean() || !response.path("result").asBoolean()) throw protocol();
        createdCollections.add(collection);
    }

    private void upsert(String collection, List<IndexPoint> points) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode pointArray = body.putArray("points");
        for (IndexPoint point : points) {
            ObjectNode item = pointArray.addObject().put("id", point.id().toString());
            ArrayNode vector = item.putArray("vector");
            for (float value : point.vector()) vector.add(value);
            item.set("payload", mapper.valueToTree(point.payload()));
        }
        JsonNode result = send("PUT", "/collections/" + collection + "/points?wait=true", body)
                .path("result");
        String status = result.path("status").asText();
        if (!result.path("operation_id").canConvertToLong()
                || !("completed".equals(status) || "acknowledged".equals(status))) throw protocol();
    }

    private long count(String collection) {
        ObjectNode body = mapper.createObjectNode().put("exact", true);
        JsonNode count = send("POST", "/collections/" + collection + "/points/count", body)
                .path("result").path("count");
        if (!count.canConvertToLong()) throw protocol();
        return count.longValue();
    }

    private void publishAlias(String collection) {
        JsonNode aliasResponse = send("GET", "/aliases", null).path("result").path("aliases");
        if (!aliasResponse.isArray()) throw protocol();
        boolean exists = false;
        for (JsonNode item : aliasResponse) {
            if (alias.equals(item.path("alias_name").asText())) {
                exists = true;
            }
        }
        if (exists) throw protocol();
        ObjectNode actionBody = mapper.createObjectNode();
        ArrayNode actions = actionBody.putArray("actions");
        actions.addObject().putObject("create_alias").put("collection_name", collection).put("alias_name", alias);
        JsonNode result = send("POST", "/collections/aliases", actionBody).path("result");
        if (!result.isBoolean() || !result.asBoolean()) throw protocol();
    }

    private void deleteOwnedCollection(String collection) {
        if (!createdCollections.contains(collection) || publishedCollections.contains(collection)) return;
        JsonNode response = send("DELETE", "/collections/" + collection, null).path("result");
        if (!response.isBoolean() || !response.asBoolean()) throw protocol();
        createdCollections.remove(collection);
    }

    private boolean collectionShapeMatches(JsonNode response) {
        JsonNode vectors = response.path("result").path("config").path("params").path("vectors");
        return vectors.isObject() && vectors.path("size").asInt(-1) == embeddingMetadata.dimensions()
                && DISTANCE.equals(vectors.path("distance").asText());
    }

    private boolean hasCurrentEmbeddingMetadata(Map<String, Object> payload) {
        return embeddingMetadata.provider().equals(payload.get("embedding_provider"))
                && embeddingMetadata.model().equals(payload.get("embedding_model"))
                && Integer.valueOf(embeddingMetadata.dimensions()).equals(asInteger(payload.get("embedding_dimensions")))
                && embeddingMetadata.configurationVersion().equals(payload.get("embedding_configuration_version"));
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) return number.intValue();
        return null;
    }

    private static String defaultAlias(RuntimeEmbeddingPort.EmbeddingMetadata metadata) {
        return "gemini".equals(metadata.provider())
                ? "stylemind_stylist_runtime_gemini_v1" : "stylemind_stylist_runtime_v1";
    }

    private JsonNode send(String method, String path, ObjectNode body) {
        return send(method, path, body, Duration.ofSeconds(2));
    }

    private JsonNode send(String method, String path, ObjectNode body, Duration timeout) {
        if (!isConfigured()) throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(configuredUrl + path))
                    .timeout(timeout).header("Accept", "application/json");
            if (!apiKey.isBlank()) request.header("api-key", apiKey);
            if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
            else request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 408 || response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new StylistApiException(response.statusCode() == 408
                        ? ErrorCode.GENERATION_PROVIDER_TIMEOUT : ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw protocol();
            return mapper.readTree(response.body());
        } catch (StylistApiException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        } catch (IOException | IllegalArgumentException exception) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        }
    }

    private static Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        return Duration.ofNanos(nanos);
    }

    private static void match(ArrayNode conditions, String key, String value) {
        conditions.addObject().put("key", key).putObject("match").put("value", value);
    }

    private static void match(ArrayNode conditions, String key, int value) {
        conditions.addObject().put("key", key).putObject("match").put("value", value);
    }

    private static UUID uuid(String raw) {
        try {
            UUID parsed = UUID.fromString(raw);
            if (!parsed.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return parsed;
        } catch (RuntimeException exception) { throw protocol(); }
    }

    private static String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof String string) || string.isBlank()) throw protocol();
        return string;
    }

    private static String required(Map<String, Object> payload, String key) {
        String value = string(payload, key);
        if (value.codePointCount(0, value.length()) > 160) throw protocol();
        return value;
    }

    private static String nullableString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        if (!(value instanceof String string) || string.codePointCount(0, string.length()) > 2_000) throw protocol();
        return string;
    }

    private static boolean finiteVector(float[] vector) {
        if (vector == null || vector.length != DIMENSIONS) return false;
        for (float value : vector) if (!Float.isFinite(value)) return false;
        return true;
    }

    private static boolean finiteNonZero(float[] vector) {
        if (!finiteVector(vector)) return false;
        double norm = 0;
        for (float value : vector) norm += (double) value * value;
        return Double.isFinite(norm) && norm > 0;
    }

    private static void validateOrigin(String value) {
        URI uri = URI.create(value);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Qdrant runtime URL must be an HTTP(S) origin");
        }
    }

    private static StylistApiException protocol() {
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
    }

    public record IndexPoint(UUID id, float[] vector, Map<String, Object> payload, boolean manifest) {
        public IndexPoint(UUID id, float[] vector, Map<String, Object> payload) { this(id, vector, payload, false); }
        public IndexPoint {
            Objects.requireNonNull(id);
            vector = vector.clone();
            payload = Map.copyOf(payload);
        }
        @Override public float[] vector() { return vector.clone(); }
    }
}
