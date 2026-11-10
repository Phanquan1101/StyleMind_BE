package com.stylemind.stylist.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Loads only an operator-supplied review artifact. No code or seed fixture can approve a live mapping. */
@Component
public class ReviewedCatalogPolicy {
    private final ObjectMapper mapper;
    private final String policyFile;

    public ReviewedCatalogPolicy(ObjectMapper mapper,
            @Value("${stylemind.stylist.catalog.policy-file:}") String policyFile) {
        this.mapper = mapper;
        this.policyFile = policyFile == null ? "" : policyFile.trim();
    }

    public Optional<Policy> loadIfAvailable() {
        if (policyFile.isBlank()) return Optional.empty();
        try {
            return Optional.of(parse(Files.readAllBytes(Path.of(policyFile))));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof StylistApiException apiException) throw apiException;
            throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        }
    }

    public Policy require() {
        return loadIfAvailable().orElseThrow(() ->
                new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED));
    }

    public Policy parse(byte[] json) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject() || !"APPROVED".equals(text(root, "review_status"))) {
                throw invalidPolicy();
            }
            String revision = bounded(text(root, "policy_revision"), 1, 80);
            String reviewer = bounded(text(root, "reviewed_by"), 1, 160);
            JsonNode categoryRows = root.path("category_mappings");
            JsonNode colorRows = root.path("colors");
            if (!categoryRows.isArray() || categoryRows.size() == 0 || !colorRows.isArray()) throw invalidPolicy();

            Map<UUID, CategoryMapping> categories = new HashMap<>();
            for (JsonNode row : categoryRows) {
                if (!row.isObject() || !"APPROVED".equals(text(row, "review_status"))
                        || blank(text(row, "reviewed_by"))) throw invalidPolicy();
                UUID id = UUID.fromString(text(row, "category_id"));
                String slot = text(row, "slot");
                String demographic = text(row, "demographic_segment");
                if (!(slot.equals("TOP") || slot.equals("BOTTOM")) || !"WOMEN".equals(demographic)
                        || categories.putIfAbsent(id, new CategoryMapping(id, slot, demographic,
                                text(row, "reviewed_by"))) != null) throw invalidPolicy();
            }

            Map<String, String> colorAliases = new HashMap<>();
            Set<String> canonicalColors = new HashSet<>();
            for (JsonNode row : colorRows) {
                if (!row.isObject() || !"APPROVED".equals(text(row, "review_status"))
                        || blank(text(row, "reviewed_by"))) throw invalidPolicy();
                String canonical = canonicalKey(text(row, "canonical"));
                if (!canonicalColors.add(canonical)) throw invalidPolicy();
                List<String> aliases = new ArrayList<>();
                aliases.add(canonical);
                JsonNode aliasRows = row.path("aliases");
                if (!aliasRows.isArray()) throw invalidPolicy();
                for (JsonNode alias : aliasRows) {
                    if (!alias.isTextual()) throw invalidPolicy();
                    aliases.add(canonicalKey(alias.textValue()));
                }
                for (String alias : aliases) {
                    String previous = colorAliases.putIfAbsent(alias, canonical);
                    if (previous != null && !previous.equals(canonical)) throw invalidPolicy();
                }
            }
            return new Policy(revision, reviewer, categories, colorAliases, canonicalColors);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPolicy();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw invalidPolicy();
        return value.textValue();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String bounded(String value, int min, int max) {
        String trimmed = value.trim();
        if (trimmed.length() < min || trimmed.length() > max) throw invalidPolicy();
        return trimmed;
    }

    private static String canonicalKey(String value) {
        String key = bounded(value, 1, 64).toLowerCase(Locale.ROOT);
        if (key.codePoints().anyMatch(Character::isISOControl)) throw invalidPolicy();
        return key;
    }

    private static StylistApiException invalidPolicy() {
        return new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
    }

    public record CategoryMapping(UUID categoryId, String slot, String demographicSegment, String reviewedBy) { }

    public record Policy(String revision, String reviewedBy, Map<UUID, CategoryMapping> categories,
                         Map<String, String> colorAliases, Set<String> canonicalColors) {
        public Policy {
            categories = Map.copyOf(categories);
            colorAliases = Map.copyOf(colorAliases);
            canonicalColors = Set.copyOf(canonicalColors);
        }
        public List<UUID> categoryIdsForSlot(String slot) {
            return categories.values().stream().filter(mapping -> slot.equals(mapping.slot()))
                    .map(CategoryMapping::categoryId).sorted().toList();
        }
        public Optional<String> slot(UUID categoryId, String demographic) {
            CategoryMapping mapping = categories.get(categoryId);
            if (mapping == null || !"WOMEN".equals(demographic)
                    || !mapping.demographicSegment().equals(demographic)) return Optional.empty();
            return Optional.of(mapping.slot());
        }
        public Optional<String> color(String raw) {
            if (raw == null || raw.isBlank()) return Optional.empty();
            return Optional.ofNullable(colorAliases.get(raw.trim().toLowerCase(Locale.ROOT)));
        }
    }
}
