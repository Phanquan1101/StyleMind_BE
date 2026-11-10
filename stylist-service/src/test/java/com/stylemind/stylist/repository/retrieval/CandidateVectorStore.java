package com.stylemind.stylist.repository.retrieval;

import com.stylemind.stylist.model.retrieval.RetrievalModel.VectorHit;
import com.stylemind.stylist.model.retrieval.RetrievalModel.VectorPoint;
import java.util.List;

/** Vector-store boundary used by the internal P04.1 retrieval service. */
public interface CandidateVectorStore {
    void createCollection(String collectionName);

    void upsert(String collectionName, List<VectorPoint> points);

    List<VectorHit> search(String collectionName, float[] queryVector, String slot, int limit);
}
