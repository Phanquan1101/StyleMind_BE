package com.stylemind.stylist.service.retrieval;

import java.util.List;

public interface RuntimeEmbeddingPort {
    float[] embed(String text);
    List<float[]> embedAll(List<String> texts);

    default float[] embedQuery(String text) { return embed(text); }
    default float[] embedDocument(String text) { return embed(text); }
    default List<float[]> embedDocuments(List<String> texts) { return embedAll(texts); }

    EmbeddingMetadata metadata();
    boolean isConfigured();

    record EmbeddingMetadata(String provider, String model, int dimensions, String configurationVersion) {
        public EmbeddingMetadata {
            if (provider == null || provider.isBlank() || model == null || model.isBlank()
                    || dimensions < 1 || configurationVersion == null || configurationVersion.isBlank()) {
                throw new IllegalArgumentException("Embedding identity is incomplete");
            }
        }
    }
}
