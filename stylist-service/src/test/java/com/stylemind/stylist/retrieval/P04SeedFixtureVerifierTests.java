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
}
