package com.stylemind.stylist.service.retrieval;

import com.stylemind.stylist.model.retrieval.RetrievalModel.CatalogCandidate;
import java.util.ArrayList;
import java.util.List;

/** Builds text only from reviewed, non-null fixture fields. It never includes image URLs or IDs. */
public final class CandidateTextBuilder {
    private CandidateTextBuilder() { }

    public static String indexText(CatalogCandidate candidate) {
        List<String> parts = new ArrayList<>();
        add(parts, candidate.productName());
        add(parts, candidate.descriptionRaw());
        add(parts, candidate.colorRaw());
        add(parts, candidate.sizeRaw());
        add(parts, candidate.materialRaw());
        return String.join(" ", parts);
    }

    private static void add(List<String> parts, String value) {
        if (value != null && !value.isBlank()) parts.add(value.trim());
    }
}
