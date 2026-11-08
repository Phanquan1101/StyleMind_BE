package com.stylemind.stylist.repository.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stylemind.stylist.model.retrieval.RetrievalModel;
import com.stylemind.stylist.model.retrieval.RetrievalModel.VectorHit;
import com.stylemind.stylist.model.retrieval.RetrievalModel.VectorPoint;
import com.stylemind.stylist.service.retrieval.RetrievalUnavailableException;
import com.stylemind.stylist.service.retrieval.VectorStoreProtocolException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Small REST adapter restricted to loopback Qdrant and disposable P04.1 collections. */
public final class QdrantHttpVectorStore implements CandidateVectorStore {
    private static final String COLLECTION_PREFIX = "/collections/";
    private static final String TEST_COLLECTION_PATTERN = "stylemind_p041_test_[a-f0-9]{32}";

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final Set<String> ownedCollections = new HashSet<>();
    private final Set<String> deletedCollections = new HashSet<>();

    public QdrantHttpVectorStore(URI baseUri, ObjectMapper objectMapper, Duration requestTimeout) {
        this.baseUri = normalizeBaseUri(baseUri);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Qdrant request timeout must be positive");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public synchronized void createCollection(String collectionName) {
        validateTestCollectionName(collectionName);
        if (ownedCollections.contains(collectionName) || deletedCollections.contains(collectionName)) {
            throw new IllegalStateException("A P04.1 collection name cannot be reused by this adapter");
        }
        if (collectionExists(collectionName)) {
            throw new VectorStoreProtocolException("Qdrant P04.1 collection already exists and is not owned by this adapter");
        }

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode vector = body.putObject("vectors");
        vector.put("size", RetrievalModel.VECTOR_DIMENSION);
        vector.put("distance", "Cosine");
        JsonNode response = sendJson("PUT", collectionPath(collectionName), body);
        if (!response.path("result").isBoolean() || !response.path("result").asBoolean()) {
            throw new VectorStoreProtocolException("Qdrant collection create response has an invalid shape");
        }

        // Ownership is recorded only after a successful, well-formed create response.
        ownedCollections.add(collectionName);
    }

    @Override
    public synchronized void upsert(String collectionName, List<VectorPoint> points) {
        validateTestCollectionName(collectionName);
        requireOwnedCollection(collectionName);
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) return;

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode pointArray = body.putArray("points");
        for (VectorPoint point : points) {
            ObjectNode pointNode = pointArray.addObject();
            pointNode.put("id", point.id().toString());
            ArrayNode vectorNode = pointNode.putArray("vector");
            point.vector().forEach(vectorNode::add);
            pointNode.set("payload", objectMapper.valueToTree(point.payload()));
        }
        JsonNode response = sendJson("PUT", collectionPath(collectionName) + "/points?wait=true", body);
        JsonNode operation = response.path("result");
        String operationStatus = operation.path("status").asText();
        if (!operation.isObject() || !operation.path("operation_id").canConvertToLong()
                || !("completed".equals(operationStatus) || "acknowledged".equals(operationStatus))) {
            throw new VectorStoreProtocolException("Qdrant upsert response has an invalid operation result");
        }
    }

    @Override
    public synchronized List<VectorHit> search(String collectionName, float[] queryVector, String slot, int limit) {
        validateTestCollectionName(collectionName);
        requireOwnedCollection(collectionName);
        if (queryVector == null || queryVector.length != RetrievalModel.VECTOR_DIMENSION) {
            throw new IllegalArgumentException("Qdrant query vector must contain 128 dimensions");
        }
        if (!"TOP".equals(slot) && !"BOTTOM".equals(slot)) {
            throw new IllegalArgumentException("Only TOP and BOTTOM Qdrant filters are allowed");
        }
        if (limit < 1 || limit > RetrievalModel.MAX_LIMIT) {
            throw new IllegalArgumentException("Qdrant limit must be between 1 and 20");
        }

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode query = body.putArray("query");
        for (float value : queryVector) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Qdrant query vector must be finite");
            query.add(value);
        }
        body.put("limit", limit);
        body.put("with_payload", true);
        addFixtureScopeFilter(body.putObject("filter"), slot);

        JsonNode response = sendJson("POST", collectionPath(collectionName) + "/points/query", body);
        JsonNode points = response.path("result").path("points");
        if (!points.isArray()) throw new VectorStoreProtocolException("Qdrant query response has no points array");

        List<VectorHit> hits = new ArrayList<>(points.size());
        for (JsonNode point : points) {
            try {
                UUID id = UUID.fromString(point.path("id").asText());
                double score = point.path("score").asDouble(Double.NaN);
                JsonNode payloadNode = point.path("payload");
                if (!payloadNode.isObject()) throw new IllegalArgumentException("Missing payload");
                Map<String, Object> payload = objectMapper.convertValue(payloadNode,
                        new TypeReference<Map<String, Object>>() { });
                hits.add(new VectorHit(id, score, payload));
            } catch (IllegalArgumentException exception) {
                throw new VectorStoreProtocolException("Qdrant returned an invalid point", exception);
            }
        }
        return List.copyOf(hits);
    }

    /** Deletes only a P04.1 collection created successfully by this adapter instance. */
    public synchronized void deleteTestCollection(String collectionName) {
        validateTestCollectionName(collectionName);
        if (deletedCollections.contains(collectionName)) return;
        requireOwnedCollection(collectionName);

        JsonNode response = sendJson("DELETE", collectionPath(collectionName), objectMapper.createObjectNode());
        if (!response.path("result").isBoolean() || !response.path("result").asBoolean()) {
            throw new VectorStoreProtocolException("Qdrant collection delete response has an invalid shape");
        }

        // Keep ownership on any failed/uncertain delete so only this instance can retry it.
        ownedCollections.remove(collectionName);
        deletedCollections.add(collectionName);
    }

    private boolean collectionExists(String collectionName) {
        JsonNode response = sendJson("GET", collectionPath(collectionName) + "/exists", null);
        JsonNode exists = response.path("result").path("exists");
        if (!exists.isBoolean()) {
            throw new VectorStoreProtocolException("Qdrant collection-exists response has an invalid shape");
        }
        return exists.asBoolean();
    }

    private void requireOwnedCollection(String collectionName) {
        if (!ownedCollections.contains(collectionName)) {
            throw new IllegalStateException("This adapter instance does not own the P04.1 collection");
        }
    }

    private JsonNode sendJson(String method, String path, ObjectNode body) {
        final HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json");
            if (body == null) {
                request = builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
            } else {
                String json = objectMapper.writeValueAsString(body);
                request = builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json))
                        .build();
            }
        } catch (JsonProcessingException exception) {
            throw new VectorStoreProtocolException("Could not encode Qdrant request", exception);
        }

        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RetrievalUnavailableException("Qdrant request was interrupted", exception);
        } catch (IOException exception) {
            throw new RetrievalUnavailableException("Qdrant request failed or timed out", exception);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            if (status >= 300 && status < 400) {
                throw new VectorStoreProtocolException("Qdrant returned an unexpected redirect status " + status);
            }
            if (status == 408 || status == 429 || status >= 500) {
                throw new RetrievalUnavailableException("Qdrant returned HTTP " + status);
            }
            // 4xx, including an existing collection conflict, is a request/protocol rejection.
            throw new VectorStoreProtocolException("Qdrant rejected the request with HTTP " + status);
        }

        if (response.body() == null || response.body().isBlank()) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException exception) {
            throw new VectorStoreProtocolException("Qdrant returned malformed JSON", exception);
        }
    }

    private static void addFixtureScopeFilter(ObjectNode filter, String slot) {
        ArrayNode must = filter.putArray("must");
        addMatch(must, "slot", slot);
        addMatch(must, "category_mapping_status", "APPROVED_TEST_ONLY");
        addMatch(must, "fixture_eligibility_status", "APPROVED_TEST_ONLY");
        addMatch(must, "fixture_tag", RetrievalModel.FIXTURE_TAG);
        addMatch(must, "source", RetrievalModel.SOURCE_SEED);
        addMatch(must, "source_sha256", RetrievalModel.SOURCE_SEED_SHA256);
        addMatch(must, "source_runtime_status", RetrievalModel.SOURCE_RUNTIME_STATUS);
        addMatch(must, "demographic_segment_raw", "WOMEN");
    }

    private static void addMatch(ArrayNode must, String key, String value) {
        ObjectNode condition = must.addObject();
        condition.put("key", key);
        condition.putObject("match").put("value", value);
    }

    private static String collectionPath(String collectionName) {
        return COLLECTION_PREFIX + collectionName;
    }

    private static URI normalizeBaseUri(URI baseUri) {
        Objects.requireNonNull(baseUri, "baseUri");
        String scheme = baseUri.getScheme();
        String rawHost = baseUri.getHost();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || rawHost == null || baseUri.getRawUserInfo() != null
                || baseUri.getRawPath() != null && !baseUri.getRawPath().isEmpty()
                || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null
                || baseUri.getPort() == 0 || baseUri.getPort() > 65535) {
            throw new IllegalArgumentException("P04.1 Qdrant endpoint must be a plain HTTP(S) loopback origin");
        }

        String host = stripIpv6Brackets(rawHost);
        if (!"localhost".equalsIgnoreCase(host) && !isNumericAddressLiteral(host)) {
            throw new IllegalArgumentException("P04.1 Qdrant endpoint must resolve from localhost or a loopback IP");
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0 || List.of(addresses).stream().anyMatch(address -> !address.isLoopbackAddress())) {
                throw new IllegalArgumentException("P04.1 Qdrant endpoint is not loopback-only");
            }

            // Pin localhost to a numeric loopback address to prevent later DNS re-resolution/rebinding.
            InetAddress pinnedAddress = List.of(addresses).stream()
                    .filter(Inet4Address.class::isInstance)
                    .findFirst()
                    .orElse(addresses[0]);
            return new URI(scheme.toLowerCase(java.util.Locale.ROOT), null,
                    pinnedAddress.getHostAddress(), baseUri.getPort(), null, null, null);
        } catch (UnknownHostException | URISyntaxException exception) {
            throw new IllegalArgumentException("P04.1 Qdrant endpoint is not a valid loopback origin", exception);
        }
    }

    private static String stripIpv6Brackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) return host.substring(1, host.length() - 1);
        return host;
    }

    private static boolean isNumericAddressLiteral(String host) {
        return host.matches("[0-9.]+") || host.indexOf(':') >= 0 && host.matches("[0-9a-fA-F:.]+");
    }

    private static void validateTestCollectionName(String collectionName) {
        if (collectionName == null || !collectionName.matches(TEST_COLLECTION_PATTERN)) {
            throw new IllegalArgumentException("Qdrant access is restricted to a unique P04.1 test collection");
        }
    }
}
