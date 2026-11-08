package com.stylemind.stylist.service.retrieval;

/** Embedding boundary for internal candidate retrieval. P04.1 supplies only a test fake. */
public interface CandidateEmbeddingPort {
    float[] embed(String text);
}
