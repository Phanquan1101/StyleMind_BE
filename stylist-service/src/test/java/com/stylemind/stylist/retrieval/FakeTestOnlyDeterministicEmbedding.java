package com.stylemind.stylist.retrieval;

import com.stylemind.stylist.model.retrieval.RetrievalModel;
import com.stylemind.stylist.service.retrieval.CandidateEmbeddingPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** FAKE_TEST_ONLY: deterministic hash vectors; this is not a semantic embedding model. */
final class FakeTestOnlyDeterministicEmbedding implements CandidateEmbeddingPort {
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public float[] embed(String text) {
        calls.incrementAndGet();
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Embedding input must not be blank");

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        String[] tokens = normalized.split("[^\\p{L}\\p{N}]+");
        double[] vector = new double[RetrievalModel.VECTOR_DIMENSION];
        int tokenCount = 0;
        for (String token : tokens) {
            if (token.isBlank()) continue;
            byte[] digest = sha256(token.getBytes(StandardCharsets.UTF_8));
            int bucket = Math.floorMod(ByteBuffer.wrap(digest, 0, Integer.BYTES).getInt(), vector.length);
            double sign = (digest[4] & 1) == 0 ? 1.0 : -1.0;
            vector[bucket] += sign;
            tokenCount++;
        }
        if (tokenCount == 0) throw new IllegalArgumentException("Embedding input has no tokens");

        double normSquared = 0;
        for (double value : vector) normSquared += value * value;
        double norm = Math.sqrt(normSquared);
        float[] normalizedVector = new float[vector.length];
        for (int index = 0; index < vector.length; index++) normalizedVector[index] = (float) (vector[index] / norm);
        return normalizedVector;
    }

    int callCount() {
        return calls.get();
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
