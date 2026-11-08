package com.stylemind.stylist.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchQuery;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchResult;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CatalogCandidate;
import com.stylemind.stylist.model.retrieval.RetrievalModel.IndexReport;
import com.stylemind.stylist.model.retrieval.RetrievalModel.RetrievedCandidate;
import com.stylemind.stylist.model.retrieval.RetrievalModel.VectorHit;
import com.stylemind.stylist.model.retrieval.RetrievalModel.VectorPoint;
import com.stylemind.stylist.repository.retrieval.CandidateVectorStore;
import com.stylemind.stylist.service.impl.retrieval.CandidateRetrievalServiceImpl;
import com.stylemind.stylist.service.retrieval.RetrievalUnavailableException;
import com.stylemind.stylist.service.retrieval.VectorStoreProtocolException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CandidateRetrievalServiceTests {
    private static final String COLLECTION = "stylemind_p041_test_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void indexesOnlyApprovedWomenFixtureRowsAndKeepsSourceSnapshotsExplicit() {
        CandidateVectorStore store = mock(CandidateVectorStore.class);
        FakeTestOnlyDeterministicEmbedding embedding = new FakeTestOnlyDeterministicEmbedding();
        CandidateRetrievalServiceImpl service = service(store, embedding);

        IndexReport report = service.indexFixtures(P04FixtureLoader.load());

        assertThat(report.indexedCount()).isEqualTo(6);
        assertThat(report.excludedByReason()).containsEntry("REVIEW_ONLY", 1)
                .containsEntry("DEMOGRAPHIC_NOT_APPROVED", 1);
        assertThat(embedding.callCount()).isEqualTo(6);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VectorPoint>> points = ArgumentCaptor.forClass(List.class);
        verify(store).upsert(eq(COLLECTION), points.capture());
        assertThat(points.getValue()).hasSize(6);
        assertThat(points.getValue()).allSatisfy(point -> {
            assertThat(point.vector()).hasSize(128);
            assertThat(point.payload()).containsEntry("fixture_tag", "SYNTHETIC_TEST_ONLY")
                    .containsEntry("source", "SOURCE_SEED")
                    .containsEntry("source_runtime_status", "NOT_RUNTIME_VERIFIED")
                    .containsEntry("price_semantics", "SOURCE_SEED_SNAPSHOT_NOT_CURRENT")
                    .doesNotContainKeys("current_price_vnd", "product_status", "variant_status",
                            "available_quantity", "on_hand_quantity", "image_url", "material_raw");
        });

        CatalogCandidate missingMaterial = P04FixtureLoader.load().getFirst();
        assertThat(missingMaterial.materialRaw()).isNull();
        assertThat(points.getValue().getFirst().payload()).containsEntry("material_status", "MISSING");
        assertThat(missingMaterial.seedPriceVndSnapshot()).isEqualTo(199000L);
        assertThat(missingMaterial.priceSemantics()).isEqualTo("SOURCE_SEED_SNAPSHOT_NOT_CURRENT");
        assertThat(missingMaterial.productStatusSource()).isEqualTo("NOT_IN_SEED");
        assertThat(missingMaterial.variantStatusSource()).isEqualTo("NOT_IN_SEED");
    }

    @Test
    void excludesPendingCategoryMappingWithoutWritingIt() {
        CandidateVectorStore store = mock(CandidateVectorStore.class);
        FakeTestOnlyDeterministicEmbedding embedding = new FakeTestOnlyDeterministicEmbedding();
        CandidateRetrievalServiceImpl service = service(store, embedding);
        CatalogCandidate pending = copy(P04FixtureLoader.load().getFirst(), null, null,
                "PENDING", "APPROVED_TEST_ONLY");

        IndexReport report = service.indexFixtures(List.of(pending));

        assertThat(report.indexedCount()).isZero();
        assertThat(report.excludedByReason()).containsEntry("MAPPING_NOT_APPROVED", 1);
        assertThat(embedding.callCount()).isZero();
        assertThatThrownBy(() -> service.indexFixtures(List.of(withSlot(P04FixtureLoader.load().getFirst(), "BOTTOM"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the approved P04.1 fixture map");
        verifyNoInteractions(store);
    }

    @Test
    void rejectsDuplicateVariantIdsBeforeEmbeddingOrWriting() {
        CandidateVectorStore store = mock(CandidateVectorStore.class);
        FakeTestOnlyDeterministicEmbedding embedding = new FakeTestOnlyDeterministicEmbedding();
        CandidateRetrievalServiceImpl service = service(store, embedding);
        CatalogCandidate candidate = P04FixtureLoader.load().getFirst();

        assertThatThrownBy(() -> service.indexFixtures(List.of(candidate, candidate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate fixture variant ID");

        assertThat(embedding.callCount()).isZero();
        verifyNoInteractions(store);
    }

    @Test
    void rejectsMissingIdentifiersMissingOrUnsupportedSlotAndWrongVectorDimension() {
        CandidateVectorStore store = mock(CandidateVectorStore.class);
        List<CatalogCandidate> base = P04FixtureLoader.load();
        CandidateRetrievalServiceImpl service = service(store, new FakeTestOnlyDeterministicEmbedding());

        assertThatThrownBy(() -> service.indexFixtures(List.of(withProductId(base.getFirst(), null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("identifiers are required");
        assertThatThrownBy(() -> service.indexFixtures(List.of(withVariantId(base.getFirst(), null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("identifiers are required");
        assertThatThrownBy(() -> service.indexFixtures(List.of(withCategoryId(base.getFirst(), null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("identifiers are required");
        assertThatThrownBy(() -> service.indexFixtures(List.of(withSlot(base.getFirst(), null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("slots are allowed");
        assertThatThrownBy(() -> service.indexFixtures(List.of(withSlot(base.getFirst(), "SHOES"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("slots are allowed");

        CandidateRetrievalServiceImpl wrongDimension = new CandidateRetrievalServiceImpl(
                store, text -> new float[127], COLLECTION);
        assertThatThrownBy(() -> wrongDimension.indexFixtures(List.of(base.getFirst())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly 128 dimensions");

        assertThatThrownBy(() -> new VectorPoint(UUID.randomUUID(), java.util.Collections.nCopies(127, 0.1f), Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("128");
        verify(store, never()).upsert(eq(COLLECTION), any());
    }

    @Test
    void validatesSlotAndLimitBoundariesBeforeCallingQdrant() {
        CandidateVectorStore store = mock(CandidateVectorStore.class);
        CandidateRetrievalServiceImpl service = service(store, new FakeTestOnlyDeterministicEmbedding());

        assertThatThrownBy(() -> service.search(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(new CandidateSearchQuery("shirt", "TOP", 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 1 and 20");
        assertThatThrownBy(() -> service.search(new CandidateSearchQuery("shirt", "BOTTOM", 21)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 1 and 20");
        assertThatThrownBy(() -> service.search(new CandidateSearchQuery("shirt", "ONE_PIECE", 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Only TOP and BOTTOM");
        assertThatThrownBy(() -> service.search(new CandidateSearchQuery("  ", "TOP", 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-blank");

        verifyNoInteractions(store);
    }

    @Test
    void emptyResultsAreDifferentFromQdrantUnavailable() {
        CandidateVectorStore store = mock(CandidateVectorStore.class);
        CandidateRetrievalServiceImpl service = service(store, new FakeTestOnlyDeterministicEmbedding());
        when(store.search(eq(COLLECTION), any(float[].class), eq("BOTTOM"), eq(5))).thenReturn(List.of());

        CandidateSearchResult empty = service.search(new CandidateSearchQuery("purple leggings", "BOTTOM", 5));
        assertThat(empty.candidates()).isEmpty();

        when(store.search(eq(COLLECTION), any(float[].class), eq("BOTTOM"), eq(5)))
                .thenThrow(new RetrievalUnavailableException("Qdrant test outage"));
        assertThatThrownBy(() -> service.search(new CandidateSearchQuery("purple leggings", "BOTTOM", 5)))
                .isInstanceOf(RetrievalUnavailableException.class)
                .hasMessageContaining("Qdrant test outage");
    }

    @Test
    void sortsEqualScoresByVariantIdAndRejectsPointsOutsideTheIndexedFixture() {
        CandidateVectorStore store = mock(CandidateVectorStore.class);
        CandidateRetrievalServiceImpl service = service(store, new FakeTestOnlyDeterministicEmbedding());
        List<CatalogCandidate> tops = P04FixtureLoader.load().stream()
                .filter(candidate -> "TOP".equals(candidate.slot())
                        && "APPROVED_TEST_ONLY".equals(candidate.fixtureEligibilityStatus()))
                .limit(2).toList();
        service.indexFixtures(tops);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VectorPoint>> pointCaptor = ArgumentCaptor.forClass(List.class);
        verify(store).upsert(eq(COLLECTION), pointCaptor.capture());
        List<VectorPoint> points = pointCaptor.getValue();
        VectorPoint first = points.get(0);
        VectorPoint second = points.get(1);
        VectorHit hit1 = new VectorHit(first.id(), 0.75, first.payload());
        VectorHit hit2 = new VectorHit(second.id(), 0.75, second.payload());
        when(store.search(eq(COLLECTION), any(float[].class), eq("TOP"), eq(5)))
                .thenReturn(List.of(hit2, hit1));

        List<RetrievedCandidate> resultA = service.search(new CandidateSearchQuery("women tops", "TOP", 5)).candidates();
        List<RetrievedCandidate> resultB = service.search(new CandidateSearchQuery("women tops", "TOP", 5)).candidates();
        List<String> expected = List.of(first.id().toString(), second.id().toString()).stream().sorted().toList();
        assertThat(resultA).extracting(hit -> hit.candidate().variantId().toString()).containsExactlyElementsOf(expected);
        assertThat(resultB).extracting(hit -> hit.candidate().variantId().toString()).containsExactlyElementsOf(expected);

        when(store.search(eq(COLLECTION), any(float[].class), eq("TOP"), eq(5)))
                .thenReturn(List.of(new VectorHit(UUID.randomUUID(), 0.5, Map.of())));
        assertThatThrownBy(() -> service.search(new CandidateSearchQuery("women tops", "TOP", 5)))
                .isInstanceOf(VectorStoreProtocolException.class)
                .hasMessageContaining("outside this indexed fixture");

        for (String requiredPayloadField : List.of("product_id", "variant_id", "slot")) {
            Map<String, Object> invalidPayload = new java.util.HashMap<>(first.payload());
            invalidPayload.remove(requiredPayloadField);
            when(store.search(eq(COLLECTION), any(float[].class), eq("TOP"), eq(5)))
                    .thenReturn(List.of(new VectorHit(first.id(), 0.5, invalidPayload)));
            assertThatThrownBy(() -> service.search(new CandidateSearchQuery("women tops", "TOP", 5)))
                    .isInstanceOf(VectorStoreProtocolException.class)
                    .hasMessageContaining("payload failed fixture scope validation");
        }
    }

    private static CandidateRetrievalServiceImpl service(CandidateVectorStore store,
                                                          FakeTestOnlyDeterministicEmbedding embedding) {
        return new CandidateRetrievalServiceImpl(store, embedding, COLLECTION);
    }

    private static CatalogCandidate copy(CatalogCandidate source, UUID productId, String slot,
                                         String mappingStatus, String eligibilityStatus) {
        return new CatalogCandidate(
                productId == null ? source.productId() : productId,
                source.variantId(), source.categoryId(), source.categorySlug(),
                slot == null ? source.slot() : slot,
                source.demographicSegmentRaw(), source.productName(), source.descriptionRaw(),
                source.colorRaw(), source.sizeRaw(), source.materialRaw(), source.seedPriceVndSnapshot(),
                mappingStatus == null ? source.categoryMappingStatus() : mappingStatus,
                eligibilityStatus == null ? source.fixtureEligibilityStatus() : eligibilityStatus,
                source.reviewSignals(), source.fixtureTag(), source.source(), source.sourceFile(),
                source.sourceSha256(), source.sourceRuntimeStatus(), source.priceSemantics(),
                source.productStatusSource(), source.variantStatusSource());
    }

    private static CatalogCandidate withProductId(CatalogCandidate source, UUID productId) {
        return new CatalogCandidate(productId, source.variantId(), source.categoryId(), source.categorySlug(),
                source.slot(), source.demographicSegmentRaw(), source.productName(), source.descriptionRaw(),
                source.colorRaw(), source.sizeRaw(), source.materialRaw(), source.seedPriceVndSnapshot(),
                source.categoryMappingStatus(), source.fixtureEligibilityStatus(), source.reviewSignals(),
                source.fixtureTag(), source.source(), source.sourceFile(), source.sourceSha256(),
                source.sourceRuntimeStatus(), source.priceSemantics(), source.productStatusSource(),
                source.variantStatusSource());
    }

    private static CatalogCandidate withSlot(CatalogCandidate source, String slot) {
        return new CatalogCandidate(source.productId(), source.variantId(), source.categoryId(),
                source.categorySlug(), slot, source.demographicSegmentRaw(), source.productName(),
                source.descriptionRaw(), source.colorRaw(), source.sizeRaw(), source.materialRaw(),
                source.seedPriceVndSnapshot(), source.categoryMappingStatus(), source.fixtureEligibilityStatus(),
                source.reviewSignals(), source.fixtureTag(), source.source(), source.sourceFile(),
                source.sourceSha256(), source.sourceRuntimeStatus(), source.priceSemantics(),
                source.productStatusSource(), source.variantStatusSource());
    }

    private static CatalogCandidate withVariantId(CatalogCandidate source, UUID variantId) {
        return new CatalogCandidate(source.productId(), variantId, source.categoryId(), source.categorySlug(),
                source.slot(), source.demographicSegmentRaw(), source.productName(), source.descriptionRaw(),
                source.colorRaw(), source.sizeRaw(), source.materialRaw(), source.seedPriceVndSnapshot(),
                source.categoryMappingStatus(), source.fixtureEligibilityStatus(), source.reviewSignals(),
                source.fixtureTag(), source.source(), source.sourceFile(), source.sourceSha256(),
                source.sourceRuntimeStatus(), source.priceSemantics(), source.productStatusSource(),
                source.variantStatusSource());
    }

    private static CatalogCandidate withCategoryId(CatalogCandidate source, UUID categoryId) {
        return new CatalogCandidate(source.productId(), source.variantId(), categoryId, source.categorySlug(),
                source.slot(), source.demographicSegmentRaw(), source.productName(), source.descriptionRaw(),
                source.colorRaw(), source.sizeRaw(), source.materialRaw(), source.seedPriceVndSnapshot(),
                source.categoryMappingStatus(), source.fixtureEligibilityStatus(), source.reviewSignals(),
                source.fixtureTag(), source.source(), source.sourceFile(), source.sourceSha256(),
                source.sourceRuntimeStatus(), source.priceSemantics(), source.productStatusSource(),
                source.variantStatusSource());
    }
}
