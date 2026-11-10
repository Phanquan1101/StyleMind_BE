package com.stylemind.stylist.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FakeTestOnlyDeterministicEmbeddingTests {
    private final FakeTestOnlyDeterministicEmbedding embedding = new FakeTestOnlyDeterministicEmbedding();

    @Test
    void producesDeterministicNormalized128DimensionalVectors() {
        float[] first = embedding.embed("Áo nữ cotton xanh");
        float[] second = embedding.embed("Áo nữ cotton xanh");
        float[] normalizedEquivalent = embedding.embed("ÁO NỮ COTTON XANH");

        assertThat(first).hasSize(128).containsExactly(second);
        assertThat(first).containsExactly(normalizedEquivalent);
        double norm = 0;
        for (float component : first) {
            assertThat(Float.isFinite(component)).isTrue();
            norm += component * component;
        }
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.00001));
        assertThat(embedding.callCount()).isEqualTo(3);
    }

    @Test
    void rejectsNullBlankAndPunctuationOnlyText() {
        assertThatThrownBy(() -> embedding.embed(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> embedding.embed("  \t ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> embedding.embed("--- ...")).isInstanceOf(IllegalArgumentException.class);
    }
}
