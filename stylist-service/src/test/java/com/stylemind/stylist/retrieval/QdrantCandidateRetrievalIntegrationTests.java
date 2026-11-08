package com.stylemind.stylist.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchQuery;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CatalogCandidate;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchResult;
import com.stylemind.stylist.model.retrieval.RetrievalModel.IndexReport;
import com.stylemind.stylist.repository.retrieval.QdrantHttpVectorStore;
import com.stylemind.stylist.service.impl.retrieval.CandidateRetrievalServiceImpl;
import com.stylemind.stylist.service.retrieval.CandidateTextBuilder;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class QdrantCandidateRetrievalIntegrationTests {
    private static final int QDRANT_HTTP_PORT = 6333;

    @Container
    private static final GenericContainer<?> QDRANT = new GenericContainer<>(
            DockerImageName.parse("qdrant/qdrant:v1.20.0"))
            .withExposedPorts(QDRANT_HTTP_PORT)
            .waitingFor(Wait.forHttp("/collections").forPort(QDRANT_HTTP_PORT).forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(90)));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> ownedCollections = new ArrayList<>();
    private QdrantHttpVectorStore store;

    @BeforeEach
    void connectToDisposableQdrant() {
        URI baseUri = URI.create("http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(QDRANT_HTTP_PORT));
        store = new QdrantHttpVectorStore(baseUri, objectMapper, Duration.ofSeconds(5));
    }

    @AfterEach
    void deleteOnlyCollectionsCreatedByThisTest() {
        for (String collection : ownedCollections) store.deleteTestCollection(collection);
        ownedCollections.clear();
    }

    @Test
    void indexesApprovedFixtureAndRetrievesOnlyTheRequestedTopOrBottomSlot() {
        List<CatalogCandidate> fixture = P04FixtureLoader.load();
        FakeTestOnlyDeterministicEmbedding embedding = new FakeTestOnlyDeterministicEmbedding();
        CandidateRetrievalServiceImpl service = newService(embedding, fixture);

        CandidateSearchResult tops = service.search(new CandidateSearchQuery(
                CandidateTextBuilder.indexText(fixture.getFirst()), "TOP", 20));
        CandidateSearchResult bottoms = service.search(new CandidateSearchQuery(
                CandidateTextBuilder.indexText(fixture.get(3)), "BOTTOM", 20));

        assertThat(tops.candidates()).isNotEmpty().allSatisfy(hit -> assertThat(hit.candidate().slot()).isEqualTo("TOP"));
        assertThat(bottoms.candidates()).isNotEmpty().allSatisfy(hit -> assertThat(hit.candidate().slot()).isEqualTo("BOTTOM"));
        assertThat(tops.candidates().getFirst().candidate().variantId().toString())
                .isEqualTo("55db1c3c-7dce-5a5e-a3ac-a11bc3a8245f");
        assertThat(bottoms.candidates().getFirst().candidate().variantId().toString())
                .isEqualTo("2389bc86-ad6b-5dc8-87c1-29d2251633ad");
        assertThat(tops.candidates()).extracting(hit -> hit.candidate().variantId().toString())
                .contains("55db1c3c-7dce-5a5e-a3ac-a11bc3a8245f")
                .doesNotContain("2389bc86-ad6b-5dc8-87c1-29d2251633ad");
        assertThat(bottoms.candidates()).extracting(hit -> hit.candidate().variantId().toString())
                .contains("2389bc86-ad6b-5dc8-87c1-29d2251633ad")
                .doesNotContain("55db1c3c-7dce-5a5e-a3ac-a11bc3a8245f");
        assertThat(tops.candidates()).extracting(hit -> hit.candidate().variantId().toString())
                .doesNotContain("e37df57b-bf8a-55a7-9bf1-0a32cb024a35", "eb910d8e-bb4c-56e0-855d-8c2ff473cd34");

        CatalogCandidate firstTop = tops.candidates().stream()
                .map(hit -> hit.candidate())
                .filter(candidate -> candidate.variantId().toString().equals("55db1c3c-7dce-5a5e-a3ac-a11bc3a8245f"))
                .findFirst().orElseThrow();
        assertThat(firstTop.productId().toString()).isEqualTo("01ec9297-370b-5096-8851-94331a0bcb60");
        assertThat(firstTop.materialRaw()).isNull();
        assertThat(firstTop.seedPriceVndSnapshot()).isEqualTo(199000L);
        assertThat(firstTop.priceSemantics()).isEqualTo("SOURCE_SEED_SNAPSHOT_NOT_CURRENT");
        assertThat(firstTop.productStatusSource()).isEqualTo("NOT_IN_SEED");
        assertThat(firstTop.variantStatusSource()).isEqualTo("NOT_IN_SEED");
        assertThat(embedding.callCount()).isEqualTo(8);
    }

    @Test
    void enforcesLimitBoundsAndReturnsAnEmptySetWhenTheRequestedSlotWasNotIndexed() {
        List<CatalogCandidate> topsOnly = P04FixtureLoader.load().stream()
                .filter(candidate -> "TOP".equals(candidate.slot())
                        && "APPROVED_TEST_ONLY".equals(candidate.fixtureEligibilityStatus()))
                .toList();
        CandidateRetrievalServiceImpl service = newService(new FakeTestOnlyDeterministicEmbedding(), topsOnly);

        assertThat(service.search(new CandidateSearchQuery("women white shirt", "TOP", 1)).candidates()).hasSizeLessThanOrEqualTo(1);
        assertThat(service.search(new CandidateSearchQuery("women white shirt", "TOP", 20)).candidates()).hasSizeLessThanOrEqualTo(3);
        assertThat(service.search(new CandidateSearchQuery("women jeans", "BOTTOM", 20)).candidates()).isEmpty();
        assertThatThrownBy(() -> service.search(new CandidateSearchQuery("women shirt", "TOP", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(new CandidateSearchQuery("women shirt", "TOP", 21)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CandidateRetrievalServiceImpl newService(FakeTestOnlyDeterministicEmbedding embedding,
                                                       List<CatalogCandidate> rows) {
        String collection = "stylemind_p041_test_" + UUID.randomUUID().toString().replace("-", "");
        store.createCollection(collection);
        ownedCollections.add(collection);
        CandidateRetrievalServiceImpl service = new CandidateRetrievalServiceImpl(store, embedding, collection);
        IndexReport report = service.indexFixtures(rows);
        assertThat(report.indexedCount()).isEqualTo(rows.stream()
                .filter(candidate -> "APPROVED_TEST_ONLY".equals(candidate.fixtureEligibilityStatus())
                        && "WOMEN".equals(candidate.demographicSegmentRaw())
                        && candidate.reviewSignals().isEmpty()).count());
        return service;
    }
}
