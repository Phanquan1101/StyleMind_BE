package com.stylemind.stylist.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.model.generation.GenerationModel.RequestContext;
import com.stylemind.stylist.service.impl.knowledge.KnowledgeArtifactImporter;
import com.stylemind.stylist.service.impl.knowledge.Neo4jReviewedKnowledgeService;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Reviewed-artifact query/import tests against a disposable Neo4j Testcontainer. */
@Testcontainers(disabledWithoutDocker = true)
class Neo4jReviewedKnowledgeIntegrationTests {
    private static final String ARTIFACT_ID = "synthetic-fixture-artifact";
    private static final String VERSION = "fixture-v1";

    @Container
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5.26-community")
            .withAdminPassword("test-only-password");

    private static Driver driver;
    private Neo4jReviewedKnowledgeService knowledge;

    @BeforeAll
    static void connectToDisposableGraph() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(),
                AuthTokens.basic("neo4j", "test-only-password"));
    }

    @AfterAll
    static void closeDriver() {
        if (driver != null) driver.close();
    }

    @BeforeEach
    void cleanFixtureNodesAndCreateService() {
        clearGraph();
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("disposableNeo4jDriver", driver);
        ObjectProvider<Driver> provider = factory.getBeanProvider(Driver.class);
        knowledge = new Neo4jReviewedKnowledgeService(provider, ARTIFACT_ID, VERSION);
    }

    @Test
    void returnsOnlyScopedApprovedFactsAndHealthyNoMatchReturnsEmpty() {
        createArtifact("APPROVED", false, true);
        assertThat(knowledge.isConfigured()).isTrue();
        knowledge.assertReady();

        List<Neo4jReviewedKnowledgeService.Fact> facts = knowledge.findApprovedFacts(
                context("work"), List.of("linen", "white"));
        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().text()).isEqualTo("Linen can add texture to this reviewed outfit");
        assertThat(facts.getFirst().sourceReference()).isEqualTo("synthetic://stylist-knowledge-fixture");
        assertThat(facts.getFirst().entryId()).isEqualTo("fact-work-linen");
        assertThat(knowledge.findApprovedFacts(context("unknown-context"), List.of("unmatched"))).isEmpty();
    }

    @Test
    void draftOrRevokedArtifactsAreUnavailableAndImporterDoesNotUndoRevocation() throws Exception {
        createArtifact("DRAFT", false, true);
        assertUnavailable(() -> knowledge.assertReady());

        clearGraph();
        createArtifact("APPROVED", true, true);
        assertUnavailable(() -> knowledge.findApprovedFacts(context("work"), List.of("linen")));
        clearGraph();

        String reviewedArtifact = """
                {"review_status":"APPROVED","artifact_id":"%s","version":"%s",
                 "reviewed_by":"synthetic-test-reviewer","reviewed_at":"2026-11-05T00:00:00Z",
                 "source_reference":"synthetic://stylist-knowledge-fixture","entries":[
                   {"review_status":"APPROVED","entry_id":"fact-work-linen",
                    "rule_text":"Linen can add texture to this reviewed outfit",
                    "reviewed_by":"synthetic-test-reviewer","reviewed_at":"2026-11-05T00:00:00Z",
                    "tags":["work","linen"]}]}
                """.formatted(ARTIFACT_ID, VERSION);
        var file = Files.createTempFile("stylist-knowledge-fixture-", ".json");
        try {
            Files.writeString(file, reviewedArtifact);
            new KnowledgeArtifactImporter(new ObjectMapper(), driver, file.toString())
                    .run(new DefaultApplicationArguments(new String[0]));
            try (var session = driver.session()) {
                session.run("MATCH (a:KnowledgeArtifact {artifact_id:$id, version:$version}) SET a.revoked=true",
                        Values.parameters("id", ARTIFACT_ID, "version", VERSION)).consume();
            }
            new KnowledgeArtifactImporter(new ObjectMapper(), driver, file.toString())
                    .run(new DefaultApplicationArguments(new String[0]));
            assertUnavailable(() -> knowledge.assertReady());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private void createArtifact(String status, boolean revoked, boolean matchingEntry) {
        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                        CREATE (a:KnowledgeArtifact {artifact_id:$id, version:$version,
                          review_status:$status, revoked:$revoked, reviewed_by:'synthetic-test-reviewer',
                          reviewed_at:datetime('2026-11-05T00:00:00Z'),
                          source_reference:'synthetic://stylist-knowledge-fixture'})
                        """, Values.parameters("id", ARTIFACT_ID, "version", VERSION,
                        "status", status, "revoked", revoked)).consume();
                if (matchingEntry) {
                    tx.run("""
                            MATCH (a:KnowledgeArtifact {artifact_id:$id, version:$version})
                            CREATE (a)-[:CONTAINS]->(:KnowledgeEntry {artifact_version:$version,
                              entry_id:'fact-work-linen', review_status:'APPROVED',
                              reviewed_by:'synthetic-test-reviewer', reviewed_at:datetime('2026-11-05T00:00:00Z'),
                              rule_text:'Linen can add texture to this reviewed outfit', tags:['work','linen']})
                            """, Values.parameters("id", ARTIFACT_ID, "version", VERSION)).consume();
                }
                return null;
            });
        }
    }

    private static void clearGraph() {
        try (var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
        }
    }

    private static RequestContext context(String occasion) {
        return new RequestContext(UUID.randomUUID(), UUID.randomUUID(), "PROCESSING", "synthetic query",
                occasion, null, null, null, null, false, false, List.of());
    }

    private static void assertUnavailable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(StylistApiException.class)
                .satisfies(failure -> assertThat(((StylistApiException) failure).errorCode())
                        .isEqualTo(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED));
    }
}
