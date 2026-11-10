package com.stylemind.stylist.service.impl.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Explicit reviewed-artifact importer. It never fetches source URLs or creates/approves review labels. */
@Component
@Profile("knowledge-import")
public class KnowledgeArtifactImporter implements ApplicationRunner {
    private static final String IMPORT_QUERY = """
            MERGE (a:KnowledgeArtifact {artifact_id: $artifactId, version: $version})
            ON CREATE SET a.revoked = false
            SET a.review_status = 'APPROVED', a.reviewed_by = $reviewedBy,
                a.reviewed_at = datetime($reviewedAt), a.source_reference = $sourceReference
            WITH a
            UNWIND $entries AS row
            MERGE (e:KnowledgeEntry {artifact_id: $artifactId, entry_id: row.entryId, artifact_version: $version})
            SET e.rule_text = row.ruleText, e.tags = row.tags, e.review_status = 'APPROVED',
                e.reviewed_by = row.reviewedBy, e.reviewed_at = datetime(row.reviewedAt)
            MERGE (a)-[:CONTAINS]->(e)
            """;

    private final ObjectMapper mapper;
    private final Driver driver;
    private final String artifactFile;

    public KnowledgeArtifactImporter(ObjectMapper mapper, Driver driver,
            @Value("${stylemind.stylist.knowledge.import-file:}") String artifactFile) {
        this.mapper = mapper;
        this.driver = driver;
        this.artifactFile = artifactFile == null ? "" : artifactFile.trim();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (artifactFile.isBlank()) throw new IllegalStateException("Reviewed knowledge artifact file is required");
        JsonNode root = mapper.readTree(Files.readAllBytes(Path.of(artifactFile)));
        Artifact artifact = validate(root);
        try (var session = driver.session()) {
            session.executeWrite(tx -> tx.run(IMPORT_QUERY, Values.parameters(
                    "artifactId", artifact.id(), "version", artifact.version(),
                    "reviewedBy", artifact.reviewedBy(), "reviewedAt", artifact.reviewedAt().toString(),
                    "sourceReference", artifact.sourceReference(), "entries", artifact.entries())).consume());
        }
    }

    private static Artifact validate(JsonNode root) {
        if (root == null || !root.isObject() || !"APPROVED".equals(text(root, "review_status"))) invalid();
        String id = bounded(text(root, "artifact_id"), 1, 120);
        String version = bounded(text(root, "version"), 1, 80);
        String reviewer = bounded(text(root, "reviewed_by"), 1, 160);
        Instant reviewedAt = instant(text(root, "reviewed_at"));
        String source = bounded(text(root, "source_reference"), 1, 300);
        JsonNode entriesNode = root.path("entries");
        if (!entriesNode.isArray() || entriesNode.size() == 0 || entriesNode.size() > 100) invalid();
        Set<String> ids = new HashSet<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (JsonNode node : entriesNode) {
            if (!node.isObject() || !"APPROVED".equals(text(node, "review_status"))) invalid();
            String entryId = bounded(text(node, "entry_id"), 1, 120);
            String fact = bounded(text(node, "rule_text"), 1, 500);
            String entryReviewer = bounded(text(node, "reviewed_by"), 1, 160);
            Instant entryReviewedAt = instant(text(node, "reviewed_at"));
            JsonNode tagNode = node.path("tags");
            if (!tagNode.isArray() || tagNode.size() == 0 || tagNode.size() > 20 || !ids.add(entryId)) invalid();
            List<String> tags = new ArrayList<>();
            for (JsonNode tag : tagNode) tags.add(bounded(tag.isTextual() ? tag.textValue() : "", 1, 48));
            if (tags.stream().distinct().count() != tags.size()) invalid();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entryId", entryId); row.put("ruleText", fact); row.put("tags", List.copyOf(tags));
            row.put("reviewedBy", entryReviewer); row.put("reviewedAt", entryReviewedAt.toString());
            entries.add(Map.copyOf(row));
        }
        return new Artifact(id, version, reviewer, reviewedAt, source, List.copyOf(entries));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) invalid();
        return value.textValue();
    }

    private static String bounded(String value, int min, int max) {
        String text = value == null ? "" : value.trim();
        if (text.codePointCount(0, text.length()) < min || text.codePointCount(0, text.length()) > max
                || text.codePoints().anyMatch(Character::isISOControl)) invalid();
        return text;
    }

    private static Instant instant(String value) {
        try { return Instant.parse(value); }
        catch (RuntimeException exception) { return invalid(); }
    }

    private static <T> T invalid() {
        throw new IllegalArgumentException("Knowledge artifact must already contain explicit reviewer approval");
    }

    private record Artifact(String id, String version, String reviewedBy, Instant reviewedAt,
                            String sourceReference, List<Map<String, Object>> entries) { }
}
