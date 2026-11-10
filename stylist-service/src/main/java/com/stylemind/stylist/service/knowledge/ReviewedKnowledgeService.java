package com.stylemind.stylist.service.knowledge;

import com.stylemind.stylist.model.generation.GenerationModel.RequestContext;
import java.time.Duration;
import java.util.List;

public interface ReviewedKnowledgeService {
    boolean isConfigured();
    void assertReady();
    List<Fact> findApprovedFacts(RequestContext context, List<String> candidateAttributes);

    default void assertReady(Duration timeout) { assertReady(); }
    default List<Fact> findApprovedFacts(RequestContext context, List<String> candidateAttributes, Duration timeout) {
        return findApprovedFacts(context, candidateAttributes);
    }

    record Fact(String text, String sourceReference, String entryId, String artifactVersion) { }
}
