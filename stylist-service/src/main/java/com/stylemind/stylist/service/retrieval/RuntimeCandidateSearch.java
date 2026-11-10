package com.stylemind.stylist.service.retrieval;

import com.stylemind.stylist.model.generation.GenerationModel.RuntimeCandidate;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface RuntimeCandidateSearch {
    List<RuntimeCandidate> search(float[] queryVector, String slot, List<UUID> categoryIds, int limit,
                                  String policyRevision);

    default List<RuntimeCandidate> search(float[] queryVector, String slot, List<UUID> categoryIds, int limit,
                                          String policyRevision, Duration timeout) {
        return search(queryVector, slot, categoryIds, limit, policyRevision);
    }
}
