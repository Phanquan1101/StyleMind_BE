package com.stylemind.stylist.service.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.service.retrieval.RuntimeEmbeddingPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "stylemind.ai.provider", havingValue = "gemini")
public class GeminiRuntimeEmbeddingAdapter implements RuntimeEmbeddingPort {
    public static final String PROVIDER = "gemini";
    public static final RuntimeEmbeddingPort.EmbeddingMetadata METADATA =
            new RuntimeEmbeddingPort.EmbeddingMetadata(PROVIDER, GeminiDeveloperApiClient.EMBEDDING_MODEL,
                    GeminiDeveloperApiClient.EMBEDDING_DIMENSIONS,
                    "gemini-embedding-001-1536-l2-query-document-v1");
    private static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(10);

    private final GeminiDeveloperApiClient client;
    private final ObjectMapper json;

    public GeminiRuntimeEmbeddingAdapter(GeminiDeveloperApiClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Override public float[] embed(String text) { return embedQuery(text); }

    @Override
    public float[] embedQuery(String text) { return embedOne(text, "RETRIEVAL_QUERY"); }

    @Override
    public float[] embedDocument(String text) { return embedOne(text, "RETRIEVAL_DOCUMENT"); }

    @Override
    public List<float[]> embedAll(List<String> texts) { return embedDocuments(texts); }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        if (texts == null || texts.isEmpty() || texts.size() > 64
                || texts.stream().anyMatch(value -> value == null || value.isBlank())) throw unconfigured();
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) vectors.add(embedOne(text, "RETRIEVAL_DOCUMENT"));
        return List.copyOf(vectors);
    }

    private float[] embedOne(String text, String taskType) {
        if (!isConfigured() || text == null || text.isBlank()) throw unconfigured();
        JsonNode count = client.countEmbeddingTokens(text, EMBEDDING_TIMEOUT);
        int tokens = count.path("totalTokens").asInt(-1);
        if (tokens < 0) throw protocol();
        // Stay conservatively below the 2,048-token model limit; no truncation flag is sent.
        if (tokens > GeminiDeveloperApiClient.EMBEDDING_MAX_COUNT_TOKENS) {
            throw new StylistApiException(ErrorCode.GENERATION_INPUT_TOO_LARGE);
        }
        JsonNode response = client.batchEmbedContent(text, taskType, EMBEDDING_TIMEOUT);
        JsonNode values = response.path("embedding").path("values");
        if (!values.isArray() || values.size() != METADATA.dimensions()) throw protocol();
        float[] vector = new float[values.size()];
        double normSquared = 0;
        for (int i = 0; i < values.size(); i++) {
            JsonNode value = values.get(i);
            if (!value.isNumber()) throw protocol();
            float converted = value.floatValue();
            if (!Float.isFinite(converted)) throw protocol();
            vector[i] = converted;
            normSquared += (double) converted * converted;
        }
        if (!Double.isFinite(normSquared) || normSquared <= 0) throw protocol();
        double norm = Math.sqrt(normSquared);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
            if (!Float.isFinite(vector[i])) throw protocol();
        }
        return vector;
    }

    @Override public EmbeddingMetadata metadata() { return METADATA; }
    @Override public boolean isConfigured() { return client != null && client.isConfigured(); }

    private static StylistApiException unconfigured() {
        return new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
    }

    private static StylistApiException protocol() {
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
    }
}
