package com.stylemind.stylist.retrieval;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.stylist.model.retrieval.RetrievalModel.CatalogCandidate;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class P04SeedFixtureVerifierTests {
    @Test
    void currentCheckoutSeedHashAndFixtureRowsMatch() throws IOException {
        P04SeedFixtureVerifier.verify(P04SeedFixtureVerifier.readSeedFromCheckout(), P04FixtureLoader.load());
    }

    @Test
    void rejectsChangedSeedHashInsteadOfUpdatingFixtureEvidence() throws IOException {
        byte[] seed = P04SeedFixtureVerifier.readSeedFromCheckout();
        byte[] changedSeed = Arrays.copyOf(seed, seed.length + 1);
        changedSeed[seed.length] = (byte) ' ';

        assertThatThrownBy(() -> P04SeedFixtureVerifier.verify(changedSeed, P04FixtureLoader.load()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SOURCE_SEED hash mismatch")
                .hasMessageContaining("expected")
                .hasMessageContaining("actual");
    }

    @Test
    void rejectsFixtureIdentifierThatDoesNotExistInTheSeed() throws IOException {
        List<CatalogCandidate> fixture = P04FixtureLoader.load();
        CatalogCandidate original = fixture.getFirst();
        CatalogCandidate missingId = new CatalogCandidate(
                UUID.randomUUID(), original.variantId(), original.categoryId(), original.categorySlug(), original.slot(),
                original.demographicSegmentRaw(), original.productName(), original.descriptionRaw(), original.colorRaw(),
                original.sizeRaw(), original.materialRaw(), original.seedPriceVndSnapshot(), original.categoryMappingStatus(),
                original.fixtureEligibilityStatus(), original.reviewSignals(), original.fixtureTag(), original.source(),
                original.sourceFile(), original.sourceSha256(), original.sourceRuntimeStatus(), original.priceSemantics(),
                original.productStatusSource(), original.variantStatusSource());
        List<CatalogCandidate> changedFixture = new java.util.ArrayList<>(fixture);
        changedFixture.set(0, missingId);

        assertThatThrownBy(() -> P04SeedFixtureVerifier.verify(
                P04SeedFixtureVerifier.readSeedFromCheckout(), changedFixture))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("productId is absent from seed");
    }

    @Test
    void rejectsFixtureMetadataThatDoesNotMatchTheCurrentSeed() throws IOException {
        List<CatalogCandidate> fixture = P04FixtureLoader.load();
        CatalogCandidate original = fixture.getFirst();
        byte[] seed = P04SeedFixtureVerifier.readSeedFromCheckout();

        assertRejected(seed, copy(original, null, fixture.get(1).variantId(), null, null, null, null,
                null, null, null, null, null), "variantId is absent under its product");
        assertRejected(seed, copy(original, null, null, UUID.randomUUID(), null, null,
                null, null, null, null, null, null), "categoryId differs from seed");
        assertRejected(seed, copy(original, null, null, null, "wrong-category", null,
                null, null, null, null, null, null), "slug differs from seed");
        assertRejected(seed, copy(original, null, null, null, null, "UNISEX",
                null, null, null, null, null, null), "demographicSegment differs from seed");
        assertRejected(seed, copy(original, null, null, null, null, null,
                "wrong name", null, null, null, null, null), "name differs from seed");
        assertRejected(seed, copy(original, null, null, null, null, null,
                null, "wrong description", null, null, null, null), "description differs from seed");
        assertRejected(seed, copy(original, null, null, null, null, null,
                null, null, "wrong color", null, null, null), "color differs from seed");
        assertRejected(seed, copy(original, null, null, null, null, null,
                null, null, null, "wrong size", null, null), "size differs from seed");
        assertRejected(seed, copy(original, null, null, null, null, null,
                null, null, null, null, "unexpected material", null), "material differs from seed");
        assertRejected(seed, copy(original, null, null, null, null, null,
                null, null, null, null, null, original.seedPriceVndSnapshot() + 1),
                "seed price differs from fixture snapshot");
    }

    private static void assertRejected(byte[] seed, CatalogCandidate candidate, String expectedMessage) {
        assertThatThrownBy(() -> P04SeedFixtureVerifier.verify(seed, List.of(candidate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static CatalogCandidate copy(CatalogCandidate source, UUID productId, UUID variantId,
                                         UUID categoryId, String categorySlug, String demographic,
                                         String name, String description, String color, String size,
                                         String material, Long price) {
        return new CatalogCandidate(
                productId == null ? source.productId() : productId,
                variantId == null ? source.variantId() : variantId,
                categoryId == null ? source.categoryId() : categoryId,
                categorySlug == null ? source.categorySlug() : categorySlug,
                source.slot(),
                demographic == null ? source.demographicSegmentRaw() : demographic,
                name == null ? source.productName() : name,
                description == null ? source.descriptionRaw() : description,
                color == null ? source.colorRaw() : color,
                size == null ? source.sizeRaw() : size,
                material == null ? source.materialRaw() : material,
                price == null ? source.seedPriceVndSnapshot() : price,
                source.categoryMappingStatus(), source.fixtureEligibilityStatus(), source.reviewSignals(),
                source.fixtureTag(), source.source(), source.sourceFile(), source.sourceSha256(),
                source.sourceRuntimeStatus(), source.priceSemantics(), source.productStatusSource(),
                source.variantStatusSource());
    }
}
