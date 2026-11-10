package com.stylemind.stylist.service.retrieval;

import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchQuery;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CandidateSearchResult;
import com.stylemind.stylist.model.retrieval.RetrievalModel.CatalogCandidate;
import com.stylemind.stylist.model.retrieval.RetrievalModel.IndexReport;
import java.util.List;

/** Internal fixture-only retrieval contract. No controller or public HTTP route is registered. */
public interface CandidateRetrievalService {
    IndexReport indexFixtures(List<CatalogCandidate> candidates);

    CandidateSearchResult search(CandidateSearchQuery query);
}
