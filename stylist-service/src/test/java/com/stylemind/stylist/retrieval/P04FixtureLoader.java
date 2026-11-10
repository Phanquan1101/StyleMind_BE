package com.stylemind.stylist.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CatalogCandidate;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

final class P04FixtureLoader {
    private static final String RESOURCE = "/stylist/retrieval/p04-review-sample.synthetic-test-only.json";

    private P04FixtureLoader() { }

    static List<CatalogCandidate> load() {
        try (InputStream input = P04FixtureLoader.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("P04.1 test fixture resource is missing");
            List<CatalogCandidate> candidates = new ObjectMapper().readValue(input, new TypeReference<>() { });
            for (CatalogCandidate candidate : candidates) {
                boolean approvedFixtureMap = "ao".equals(candidate.categorySlug()) && "TOP".equals(candidate.slot())
                        || "quan".equals(candidate.categorySlug()) && "BOTTOM".equals(candidate.slot());
                if (!approvedFixtureMap || !"APPROVED_TEST_ONLY".equals(candidate.categoryMappingStatus())) {
                    throw new IllegalStateException("Fixture category mapping drifted from the owner-approved P04.1 map");
                }
            }
            return candidates;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read P04.1 test fixture", exception);
        }
    }
}
