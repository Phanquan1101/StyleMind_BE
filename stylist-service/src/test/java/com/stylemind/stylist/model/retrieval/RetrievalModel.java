package com.stylemind.stylist.model.retrieval;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal P04.1 retrieval values. They are neither public API DTOs nor persistence entities. */
public final class RetrievalModel {
    public static final int VECTOR_DIMENSION = 128;
    public static final int MAX_LIMIT = 20;
    public static final String FIXTURE_TAG = "SYNTHETIC_TEST_ONLY";
    public static final String SOURCE_SEED = "SOURCE_SEED";
    public static final String SOURCE_SEED_SHA256 =
            "07A0AEEBFAB18CC42DC00EE034E499FC02A188B41C9F4DBF114A88AF7C1A1263";
    public static final String SOURCE_SEED_FILE =
            "commerce-service/src/main/resources/db/seed/development-catalog.json";
    public static final String SOURCE_RUNTIME_STATUS = "NOT_RUNTIME_VERIFIED";
    public static final String PRICE_SEMANTICS = "SOURCE_SEED_SNAPSHOT_NOT_CURRENT";

    private RetrievalModel() { }

    public record CatalogCandidate(
            UUID productId,
            UUID variantId,
            UUID categoryId,
            String categorySlug,
            String slot,
            String demographicSegmentRaw,
            String productName,
            String descriptionRaw,
            String colorRaw,
            String sizeRaw,
            String materialRaw,
            Long seedPriceVndSnapshot,
            String categoryMappingStatus,
            String fixtureEligibilityStatus,
            List<String> reviewSignals,
            String fixtureTag,
            String source,
            String sourceFile,
            String sourceSha256,
            String sourceRuntimeStatus,
            String priceSemantics,
            String productStatusSource,
            String variantStatusSource) {
        public CatalogCandidate {
            reviewSignals = reviewSignals == null ? List.of() : List.copyOf(reviewSignals);
        }
    }

    public record CandidateSearchQuery(String queryText, String slot, int limit) { }

    public record RetrievedCandidate(CatalogCandidate candidate, double score) {
        public RetrievedCandidate {
            if (candidate == null || !Double.isFinite(score)) {
                throw new IllegalArgumentException("Invalid retrieval hit");
            }
        }
    }

    public record CandidateSearchResult(List<RetrievedCandidate> candidates) {
        public CandidateSearchResult {
            candidates = List.copyOf(candidates);
        }
    }

    public record IndexReport(int indexedCount, Map<String, Integer> excludedByReason) {
        public IndexReport {
            if (indexedCount < 0) throw new IllegalArgumentException("Negative indexed count");
            excludedByReason = Map.copyOf(excludedByReason);
        }
    }

    public record VectorPoint(UUID id, List<Float> vector, Map<String, Object> payload) {
        public VectorPoint {
            if (id == null) throw new IllegalArgumentException("Qdrant point ID is required");
            if (vector == null || vector.size() != VECTOR_DIMENSION)
                throw new IllegalArgumentException("Qdrant vector must contain exactly 128 dimensions");
            if (vector.stream().anyMatch(value -> value == null || !Float.isFinite(value)))
                throw new IllegalArgumentException("Qdrant vector must contain finite values");
            vector = List.copyOf(vector);
            payload = Map.copyOf(payload);
        }
    }

    public record VectorHit(UUID id, double score, Map<String, Object> payload) {
        public VectorHit {
            if (id == null || !Double.isFinite(score) || payload == null) {
                throw new IllegalArgumentException("Invalid Qdrant hit");
            }
            payload = Map.copyOf(payload);
        }
    }
}
