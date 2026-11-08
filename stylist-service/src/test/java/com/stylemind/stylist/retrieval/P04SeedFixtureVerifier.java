package com.stylemind.stylist.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.model.retrieval.RetrievalModel;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CatalogCandidate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Checkout-only evidence verifier; kept in test sources so production has no repository-path dependency. */
final class P04SeedFixtureVerifier {
    private static final String SEED_RELATIVE_PATH = "commerce-service/src/main/resources/db/seed/development-catalog.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private P04SeedFixtureVerifier() { }

    static byte[] readSeedFromCheckout() throws IOException {
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path directory = start; directory != null; directory = directory.getParent()) {
            Path seed = directory.resolve(SEED_RELATIVE_PATH);
            if (Files.isRegularFile(seed)) return Files.readAllBytes(seed);
        }
        throw new IOException("Could not locate the checked-out SOURCE_SEED above the test working directory");
    }

    static void verify(byte[] seedBytes, List<CatalogCandidate> fixture) {
        if (seedBytes == null || fixture == null || fixture.isEmpty()) {
            throw new IllegalStateException("SOURCE_SEED bytes and fixture rows are required");
        }

        String expectedHash = fixture.getFirst().sourceSha256();
        if (!RetrievalModel.SOURCE_SEED_SHA256.equals(expectedHash)
                || fixture.stream().anyMatch(candidate -> !expectedHash.equals(candidate.sourceSha256()))) {
            throw new IllegalStateException("SOURCE_SEED fixture evidence contains inconsistent recorded hashes");
        }
        String actualHash = sha256(seedBytes);
        if (!expectedHash.equals(actualHash)) {
            throw new IllegalStateException("SOURCE_SEED hash mismatch: expected " + expectedHash + ", actual " + actualHash);
        }

        final JsonNode seed;
        try {
            seed = OBJECT_MAPPER.readTree(seedBytes);
        } catch (IOException exception) {
            throw new IllegalStateException("SOURCE_SEED JSON could not be parsed", exception);
        }
        for (CatalogCandidate candidate : fixture) verifyCandidate(seed, candidate);
    }

    private static void verifyCandidate(JsonNode seed, CatalogCandidate candidate) {
        JsonNode product = findById(seed.path("products"), "productId", candidate.productId().toString());
        if (product == null) throw drift(candidate, "productId is absent from seed");
        requireText(product, "categoryId", candidate.categoryId().toString(), candidate);
        requireText(product, "name", candidate.productName(), candidate);
        requireText(product, "description", candidate.descriptionRaw(), candidate);
        requireText(product, "demographicSegment", candidate.demographicSegmentRaw(), candidate);

        JsonNode category = findById(seed.path("categories"), "categoryId", candidate.categoryId().toString());
        if (category == null) throw drift(candidate, "categoryId is absent from seed categories");
        requireText(category, "slug", candidate.categorySlug(), candidate);

        JsonNode variant = findById(product.path("variants"), "variantId", candidate.variantId().toString());
        if (variant == null) throw drift(candidate, "variantId is absent under its product");
        requireText(variant, "color", candidate.colorRaw(), candidate);
        requireText(variant, "size", candidate.sizeRaw(), candidate);
        JsonNode material = variant.get("material");
        if (candidate.materialRaw() == null ? material == null || !material.isNull()
                : material == null || !candidate.materialRaw().equals(material.asText())) {
            throw drift(candidate, "material differs from seed");
        }
        if (!variant.path("unitPriceVnd").canConvertToLong()
                || variant.path("unitPriceVnd").asLong() != candidate.seedPriceVndSnapshot()) {
            throw drift(candidate, "seed price differs from fixture snapshot");
        }
    }

    private static JsonNode findById(JsonNode rows, String idField, String expectedId) {
        if (!rows.isArray()) return null;
        for (JsonNode row : rows) {
            if (expectedId.equals(row.path(idField).asText())) return row;
        }
        return null;
    }

    private static void requireText(JsonNode row, String field, String expected, CatalogCandidate candidate) {
        if (expected == null || !row.has(field) || !expected.equals(row.path(field).asText())) {
            throw drift(candidate, field + " differs from seed");
        }
    }

    private static IllegalStateException drift(CatalogCandidate candidate, String reason) {
        return new IllegalStateException("SOURCE_SEED fixture drift for product " + candidate.productId()
                + " / variant " + candidate.variantId() + ": " + reason);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
