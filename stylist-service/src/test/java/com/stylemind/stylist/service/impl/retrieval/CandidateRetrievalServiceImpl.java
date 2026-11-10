package com.stylemind.stylist.service.impl.retrieval;

import com.stylemind.stylist.model.retrieval.RetrievalModel;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchQuery;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchResult;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CatalogCandidate;
import com.stylemind.stylist.model.retrieval.RetrievalModel.IndexReport;
import com.stylemind.stylist.model.retrieval.RetrievalModel.RetrievedCandidate;
import com.stylemind.stylist.model.retrieval.RetrievalModel.VectorHit;
import com.stylemind.stylist.model.retrieval.RetrievalModel.VectorPoint;
import com.stylemind.stylist.repository.retrieval.CandidateVectorStore;
import com.stylemind.stylist.service.retrieval.CandidateEmbeddingPort;
import com.stylemind.stylist.service.retrieval.CandidateRetrievalService;
import com.stylemind.stylist.service.retrieval.CandidateTextBuilder;
import com.stylemind.stylist.service.retrieval.VectorStoreProtocolException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Fixture-only P04.1 orchestration. It is not registered as an application bean or API. */
public final class CandidateRetrievalServiceImpl implements CandidateRetrievalService {
    private static final String APPROVED_FIXTURE_MAPPING = "APPROVED_TEST_ONLY";
    private static final String APPROVED_FIXTURE_ELIGIBILITY = "APPROVED_TEST_ONLY";
    private static final String NOT_IN_SEED = "NOT_IN_SEED";
    private static final String MISSING = "MISSING";
    private static final String APPROVED_TOP_FIXTURE_CATEGORY_ID = "fbb973c7-c888-53ba-8648-76a4700c3757";
    private static final String APPROVED_BOTTOM_FIXTURE_CATEGORY_ID = "397b5054-84f4-5cc6-8047-56787826b9bf";

    private final CandidateVectorStore vectorStore;
    private final CandidateEmbeddingPort embeddingPort;
    private final String collectionName;
    private final Map<UUID, CatalogCandidate> indexedCandidates = new HashMap<>();

    public CandidateRetrievalServiceImpl(CandidateVectorStore vectorStore,
                                         CandidateEmbeddingPort embeddingPort,
                                         String collectionName) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.collectionName = Objects.requireNonNull(collectionName, "collectionName");
        if (!collectionName.matches("stylemind_p041_test_[a-f0-9]{32}")) {
            throw new IllegalArgumentException("P04.1 requires a unique disposable collection name");
        }
    }

    @Override
    public IndexReport indexFixtures(List<CatalogCandidate> candidates) {
        if (candidates == null) throw new IllegalArgumentException("Candidate fixture list is required");

        Set<UUID> batchIds = new HashSet<>();
        for (CatalogCandidate candidate : candidates) {
            validateCandidate(candidate);
            if (!batchIds.add(candidate.variantId())) {
                throw new IllegalArgumentException("Duplicate fixture variant ID");
            }
            if (indexedCandidates.containsKey(candidate.variantId())) {
                throw new IllegalArgumentException("Fixture variant is already indexed");
            }
        }

        Map<String, Integer> excluded = new LinkedHashMap<>();
        List<CatalogCandidate> eligible = new ArrayList<>();
        for (CatalogCandidate candidate : candidates) {
            String reason = exclusionReason(candidate);
            if (reason == null) eligible.add(candidate);
            else excluded.merge(reason, 1, Integer::sum);
        }

        List<VectorPoint> points = new ArrayList<>(eligible.size());
        for (CatalogCandidate candidate : eligible) {
            float[] vector = validatedVector(embeddingPort.embed(CandidateTextBuilder.indexText(candidate)));
            points.add(new VectorPoint(candidate.variantId(), toList(vector), payload(candidate)));
        }

        if (!points.isEmpty()) vectorStore.upsert(collectionName, points);
        for (CatalogCandidate candidate : eligible) indexedCandidates.put(candidate.variantId(), candidate);
        return new IndexReport(eligible.size(), excluded);
    }

    @Override
    public CandidateSearchResult search(CandidateSearchQuery query) {
        validateQuery(query);
        float[] vector = validatedVector(embeddingPort.embed(query.queryText()));
        List<VectorHit> hits = vectorStore.search(collectionName, vector, query.slot(), query.limit());

        Set<UUID> returnedIds = new HashSet<>();
        List<RetrievedCandidate> candidates = new ArrayList<>(hits.size());
        for (VectorHit hit : hits) {
            if (!returnedIds.add(hit.id())) throw new VectorStoreProtocolException("Qdrant returned a duplicate point");
            CatalogCandidate candidate = indexedCandidates.get(hit.id());
            if (candidate == null) throw new VectorStoreProtocolException("Qdrant returned a point outside this indexed fixture");
            verifyHitScope(hit, candidate, query.slot());
            candidates.add(new RetrievedCandidate(candidate, hit.score()));
        }

        candidates.sort(Comparator.comparingDouble(RetrievedCandidate::score).reversed()
                .thenComparing(result -> result.candidate().variantId().toString()));
        return new CandidateSearchResult(candidates.stream().limit(query.limit()).toList());
    }

    private static void validateQuery(CandidateSearchQuery query) {
        if (query == null || query.queryText() == null || query.queryText().isBlank()) {
            throw new IllegalArgumentException("A non-blank retrieval query is required");
        }
        if (!"TOP".equals(query.slot()) && !"BOTTOM".equals(query.slot())) {
            throw new IllegalArgumentException("Only TOP and BOTTOM retrieval slots are allowed");
        }
        if (query.limit() < 1 || query.limit() > RetrievalModel.MAX_LIMIT) {
            throw new IllegalArgumentException("Retrieval limit must be between 1 and 20");
        }
    }

    private static void validateCandidate(CatalogCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("Null catalog candidate");
        if (candidate.productId() == null || candidate.variantId() == null || candidate.categoryId() == null) {
            throw new IllegalArgumentException("Product, variant and category identifiers are required");
        }
        if (!"TOP".equals(candidate.slot()) && !"BOTTOM".equals(candidate.slot())) {
            throw new IllegalArgumentException("Only TOP and BOTTOM fixture slots are allowed");
        }
        if (candidate.categorySlug() == null || candidate.categorySlug().isBlank()
                || candidate.demographicSegmentRaw() == null || candidate.demographicSegmentRaw().isBlank()
                || candidate.productName() == null || candidate.productName().isBlank()) {
            throw new IllegalArgumentException("Category, raw demographic and product name are required");
        }
        if (!isApprovedP04FixtureCategoryMapping(candidate)) {
            throw new IllegalArgumentException("Category mapping is outside the approved P04.1 fixture map");
        }
        if (candidate.seedPriceVndSnapshot() == null || candidate.seedPriceVndSnapshot() < 0) {
            throw new IllegalArgumentException("A non-negative SOURCE_SEED price snapshot is required");
        }
        if (!RetrievalModel.FIXTURE_TAG.equals(candidate.fixtureTag())
                || !RetrievalModel.SOURCE_SEED.equals(candidate.source())
                || !RetrievalModel.SOURCE_SEED_FILE.equals(candidate.sourceFile())
                || !RetrievalModel.SOURCE_SEED_SHA256.equals(candidate.sourceSha256())
                || !RetrievalModel.SOURCE_RUNTIME_STATUS.equals(candidate.sourceRuntimeStatus())
                || !RetrievalModel.PRICE_SEMANTICS.equals(candidate.priceSemantics())
                || !NOT_IN_SEED.equals(candidate.productStatusSource())
                || !NOT_IN_SEED.equals(candidate.variantStatusSource())) {
            throw new IllegalArgumentException("Candidate provenance is outside the approved seed fixture");
        }
        if (candidate.categoryMappingStatus() == null || candidate.fixtureEligibilityStatus() == null) {
            throw new IllegalArgumentException("Fixture mapping and eligibility decisions are required");
        }
    }

    /** This exact ID/slug/slot allowlist belongs only to the owner-approved P04.1 seed fixture. */
    private static boolean isApprovedP04FixtureCategoryMapping(CatalogCandidate candidate) {
        return APPROVED_TOP_FIXTURE_CATEGORY_ID.equals(candidate.categoryId().toString())
                && "ao".equals(candidate.categorySlug()) && "TOP".equals(candidate.slot())
                || APPROVED_BOTTOM_FIXTURE_CATEGORY_ID.equals(candidate.categoryId().toString())
                && "quan".equals(candidate.categorySlug()) && "BOTTOM".equals(candidate.slot());
    }

    private static String exclusionReason(CatalogCandidate candidate) {
        if (!APPROVED_FIXTURE_MAPPING.equals(candidate.categoryMappingStatus())) return "MAPPING_NOT_APPROVED";
        if (!"WOMEN".equals(candidate.demographicSegmentRaw())) return "DEMOGRAPHIC_NOT_APPROVED";
        if (!APPROVED_FIXTURE_ELIGIBILITY.equals(candidate.fixtureEligibilityStatus())
                || !candidate.reviewSignals().isEmpty()) return "REVIEW_ONLY";
        return null;
    }

    private static float[] validatedVector(float[] vector) {
        if (vector == null || vector.length != RetrievalModel.VECTOR_DIMENSION) {
            throw new IllegalArgumentException("Embedding must contain exactly 128 dimensions");
        }
        double normSquared = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Embedding contains a non-finite value");
            normSquared += (double) value * value;
        }
        if (normSquared == 0 || !Double.isFinite(normSquared)) {
            throw new IllegalArgumentException("Embedding must be a finite non-zero vector");
        }
        return vector.clone();
    }

    private static List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) values.add(value);
        return values;
    }

    private static Map<String, Object> payload(CatalogCandidate candidate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product_id", candidate.productId().toString());
        payload.put("variant_id", candidate.variantId().toString());
        payload.put("category_id", candidate.categoryId().toString());
        payload.put("category_slug", candidate.categorySlug());
        payload.put("slot", candidate.slot());
        payload.put("demographic_segment_raw", candidate.demographicSegmentRaw());
        payload.put("product_name", candidate.productName());
        payload.put("description_raw", candidate.descriptionRaw());
        payload.put("color_raw", candidate.colorRaw());
        payload.put("size_raw", candidate.sizeRaw());
        payload.put("material_status", candidate.materialRaw() == null ? MISSING : "PRESENT_IN_SEED");
        if (candidate.materialRaw() != null) payload.put("material_raw", candidate.materialRaw());
        payload.put("price_vnd_seed_snapshot", candidate.seedPriceVndSnapshot());
        payload.put("price_semantics", candidate.priceSemantics());
        payload.put("product_status_source", candidate.productStatusSource());
        payload.put("variant_status_source", candidate.variantStatusSource());
        payload.put("category_mapping_status", candidate.categoryMappingStatus());
        payload.put("fixture_eligibility_status", candidate.fixtureEligibilityStatus());
        payload.put("review_signals", candidate.reviewSignals());
        payload.put("fixture_tag", candidate.fixtureTag());
        payload.put("source", candidate.source());
        payload.put("source_file", candidate.sourceFile());
        payload.put("source_sha256", candidate.sourceSha256());
        payload.put("source_runtime_status", candidate.sourceRuntimeStatus());
        return payload;
    }

    private static void verifyHitScope(VectorHit hit, CatalogCandidate candidate, String requestedSlot) {
        Map<String, Object> payload = hit.payload();
        requirePayload(payload, "product_id", candidate.productId().toString());
        requirePayload(payload, "variant_id", candidate.variantId().toString());
        requirePayload(payload, "slot", requestedSlot);
        requirePayload(payload, "category_mapping_status", APPROVED_FIXTURE_MAPPING);
        requirePayload(payload, "fixture_eligibility_status", APPROVED_FIXTURE_ELIGIBILITY);
        requirePayload(payload, "fixture_tag", RetrievalModel.FIXTURE_TAG);
        requirePayload(payload, "source", RetrievalModel.SOURCE_SEED);
        requirePayload(payload, "source_sha256", RetrievalModel.SOURCE_SEED_SHA256);
        requirePayload(payload, "source_runtime_status", RetrievalModel.SOURCE_RUNTIME_STATUS);
        requirePayload(payload, "demographic_segment_raw", "WOMEN");
        if (!requestedSlot.equals(candidate.slot()) || !candidate.reviewSignals().isEmpty()) {
            throw new VectorStoreProtocolException("Qdrant returned a candidate outside the approved fixture filter");
        }
    }

    private static void requirePayload(Map<String, Object> payload, String key, String expected) {
        if (!expected.equals(payload.get(key))) {
            throw new VectorStoreProtocolException("Qdrant payload failed fixture scope validation");
        }
    }
}
