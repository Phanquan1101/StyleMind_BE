package com.stylemind.stylist.service.generation;

import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.service.retrieval.RuntimeEmbeddingPort;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "stylemind.ai.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiRuntimeEmbeddingAdapter implements RuntimeEmbeddingPort {
    public static final String PROVIDER = "openai";
    public static final String MODEL = "text-embedding-3-small";
    public static final int DIMENSIONS = 1_536;
    public static final RuntimeEmbeddingPort.EmbeddingMetadata METADATA =
            new RuntimeEmbeddingPort.EmbeddingMetadata(PROVIDER, MODEL, DIMENSIONS,
                    "openai-text-embedding-3-small-1536-v1");
    private final ObjectProvider<EmbeddingModel> modelProvider;
    private final boolean enabled;

    public OpenAiRuntimeEmbeddingAdapter(ObjectProvider<EmbeddingModel> modelProvider,
            @Value("${stylemind.openai.enabled:false}") boolean enabled) {
        this.modelProvider = modelProvider;
        this.enabled = enabled;
    }

    @Override
    public float[] embed(String text) {
        if (!isConfigured() || text == null || text.isBlank()) throw unconfigured();
        try {
            return validate(modelProvider.getObject().embed(text));
        } catch (StylistApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw OpenAiFailureMapper.map(exception);
        }
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (!isConfigured() || texts == null || texts.isEmpty() || texts.size() > 64
                || texts.stream().anyMatch(value -> value == null || value.isBlank())) throw unconfigured();
        try {
            List<float[]> vectors = modelProvider.getObject().embed(List.copyOf(texts));
            if (vectors == null || vectors.size() != texts.size()) {
                throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
            }
            return vectors.stream().map(OpenAiRuntimeEmbeddingAdapter::validate).toList();
        } catch (StylistApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw OpenAiFailureMapper.map(exception);
        }
    }

    @Override
    public boolean isConfigured() { return enabled && modelProvider.getIfAvailable() != null; }

    @Override
    public RuntimeEmbeddingPort.EmbeddingMetadata metadata() { return METADATA; }

    private static float[] validate(float[] vector) {
        if (vector == null || vector.length != DIMENSIONS) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
            norm += (double) value * value;
        }
        if (!Double.isFinite(norm) || norm == 0) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
        return vector.clone();
    }

    private static StylistApiException unconfigured() {
        return new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
    }
}
