package com.stylemind.stylist.service.impl.knowledge;

import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.model.generation.GenerationModel.RequestContext;
import com.stylemind.stylist.service.knowledge.ReviewedKnowledgeService;
import com.stylemind.stylist.service.knowledge.ReviewedKnowledgeService.Fact;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.Duration;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class Neo4jReviewedKnowledgeService implements ReviewedKnowledgeService {
    private static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(2);
    static final String APPROVED_FACTS_QUERY = """
            MATCH (a:KnowledgeArtifact {artifact_id: $artifactId, version: $version})
            WHERE a.review_status = 'APPROVED' AND coalesce(a.revoked, false) = false
              AND a.reviewed_by IS NOT NULL AND a.reviewed_at IS NOT NULL
              AND a.source_reference IS NOT NULL AND a.source_reference <> ''
            MATCH (a)-[:CONTAINS]->(e:KnowledgeEntry)
            WHERE e.artifact_version = $version AND e.review_status = 'APPROVED'
              AND e.reviewed_by IS NOT NULL AND e.reviewed_at IS NOT NULL
              AND e.rule_text IS NOT NULL AND size(e.rule_text) > 0
              AND any(tag IN coalesce(e.tags, []) WHERE tag IN $contextTags)
            RETURN e.rule_text AS fact, a.source_reference AS sourceReference,
                   e.entry_id AS entryId, e.artifact_version AS artifactVersion
            ORDER BY e.entry_id
            LIMIT $limit
            """;

    private final ObjectProvider<Driver> driverProvider;
    private final String artifactId;
    private final String version;

    public Neo4jReviewedKnowledgeService(ObjectProvider<Driver> driverProvider,
            @Value("${stylemind.stylist.knowledge.artifact-id:}") String artifactId,
            @Value("${stylemind.stylist.knowledge.artifact-version:}") String version) {
        this.driverProvider = driverProvider;
        this.artifactId = artifactId == null ? "" : artifactId.trim();
        this.version = version == null ? "" : version.trim();
    }

    @Override
    public boolean isConfigured() {
        return !artifactId.isBlank() && !version.isBlank() && driverProvider.getIfAvailable() != null;
    }

    @Override
    public void assertReady() {
        assertReady(DEFAULT_QUERY_TIMEOUT);
    }

    @Override
    public void assertReady(Duration timeout) {
        if (!isConfigured()) throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        }
        try (var session = driverProvider.getObject().session()) {
            boolean approvedArtifactExists = session.executeRead(tx -> {
                var result = tx.run(new Query("""
                        MATCH (a:KnowledgeArtifact {artifact_id: $artifactId, version: $version})
                        WHERE a.review_status = 'APPROVED' AND coalesce(a.revoked, false) = false
                          AND a.reviewed_by IS NOT NULL AND a.reviewed_at IS NOT NULL
                          AND a.source_reference IS NOT NULL AND a.source_reference <> ''
                        RETURN count(a) = 1 AS ready
                        """, Values.parameters("artifactId", artifactId, "version", version)));
                return result.single().get("ready").asBoolean(false);
            }, TransactionConfig.builder().withTimeout(timeout).build());
            if (!approvedArtifactExists) throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        } catch (StylistApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        }
    }

    @Override
    public List<Fact> findApprovedFacts(RequestContext context, List<String> candidateAttributes) {
        return findApprovedFacts(context, candidateAttributes, DEFAULT_QUERY_TIMEOUT);
    }

    @Override
    public List<Fact> findApprovedFacts(RequestContext context, List<String> candidateAttributes, Duration timeout) {
        if (!isConfigured()) throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        }
        Set<String> tags = contextTags(context, candidateAttributes);
        try (var session = driverProvider.getObject().session()) {
            return session.executeRead(tx -> {
                var artifact = tx.run(new Query("""
                        MATCH (a:KnowledgeArtifact {artifact_id: $artifactId, version: $version})
                        WHERE a.review_status = 'APPROVED' AND coalesce(a.revoked, false) = false
                          AND a.reviewed_by IS NOT NULL AND a.reviewed_at IS NOT NULL
                          AND a.source_reference IS NOT NULL AND a.source_reference <> ''
                        RETURN count(a) = 1 AS ready
                        """, Values.parameters("artifactId", artifactId, "version", version)));
                if (!artifact.single().get("ready").asBoolean(false)) {
                    throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
                }
                if (tags.isEmpty()) return List.of();
                var result = tx.run(new Query(APPROVED_FACTS_QUERY, Values.parameters(
                        "artifactId", artifactId, "version", version, "contextTags", List.copyOf(tags), "limit", 10)));
                List<Fact> facts = new ArrayList<>();
                while (result.hasNext()) {
                    var record = result.next();
                    String fact = record.get("fact").asString("");
                    String source = record.get("sourceReference").asString("");
                    String entryId = record.get("entryId").asString("");
                    String artifactVersion = record.get("artifactVersion").asString("");
                    if (fact.isBlank() || fact.codePointCount(0, fact.length()) > 500
                            || source.isBlank() || source.codePointCount(0, source.length()) > 300
                            || entryId.isBlank() || !version.equals(artifactVersion)) {
                        throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
                    }
                    facts.add(new Fact(fact, source, entryId, artifactVersion));
                }
                return List.copyOf(facts);
            }, TransactionConfig.builder().withTimeout(timeout).build());
        } catch (StylistApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        }
    }

    private static Set<String> contextTags(RequestContext context, List<String> attributes) {
        Set<String> tags = new LinkedHashSet<>();
        addTerms(tags, context.occasion());
        addTerms(tags, context.intendedUse());
        addTerms(tags, context.styleContext());
        addTerms(tags, context.weatherContext());
        if (attributes != null) attributes.stream().limit(80).forEach(value -> addTerms(tags, value));
        return tags.stream().limit(64).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static void addTerms(Set<String> tags, String value) {
        if (value == null) return;
        for (String token : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isBlank() && token.codePointCount(0, token.length()) <= 48) tags.add(token);
            if (tags.size() >= 64) return;
        }
    }
}
