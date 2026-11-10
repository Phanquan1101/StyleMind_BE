package com.stylemind.stylist.service.recommendation.internal;

import com.stylemind.stylist.model.recommendation.EngineModel.Input;
import com.stylemind.stylist.model.recommendation.EngineModel.Item;
import com.stylemind.stylist.model.recommendation.EngineModel.Outfit;
import com.stylemind.stylist.model.recommendation.EngineModel.Result;
import java.util.ArrayList;
import java.util.List;

/** Stateless orchestration for the bounded offline recommendation pipeline. */
public final class OutfitEngine {
    public static final int MAX_PER_SLOT = CandidatePolicy.MAX_PER_MANDATORY_SLOT;
    public static final int MAX_CANDIDATES = CandidatePolicy.MAX_CANDIDATES;
    public static final int MAX_COMBINATIONS = OutfitComposer.MAX_COMBINATIONS;
    public static final int MAX_RESULTS = 3;

    private final CandidatePolicy candidatePolicy = new CandidatePolicy();
    private final OutfitComposer composer = new OutfitComposer();
    private final OutfitScorer scorer = new OutfitScorer();
    private final ResultDiversity diversity = new ResultDiversity();

    public Result recommend(Input input) {
        CandidatePolicy.Selection candidates = candidatePolicy.prepare(input);
        OutfitComposer.Composition composition = composer.compose(candidates);
        List<String> warnings = new ArrayList<>(candidates.warnings());
        if (composition.truncated()) warnings.add("SEARCH_TRUNCATED");

        List<Outfit> valid = new ArrayList<>();
        for (List<Item> items : composition.outfits()) {
            long additionalCost;
            try {
                additionalCost = BudgetPolicy.total(items);
            } catch (ArithmeticException exception) {
                warnings.add("OUTFIT_PRICE_OVERFLOW");
                continue;
            }
            if (input.hardBudget() && additionalCost > input.budgetVnd()) continue;
            valid.add(new Outfit(items, additionalCost, scorer.score(items, additionalCost, input)));
        }

        List<Outfit> selected = diversity.select(valid, MAX_RESULTS);
        if (selected.isEmpty()) warnings.add("NO_RESULT_WITHIN_BOUNDED_SEARCH");
        return new Result(selected, warnings.stream().distinct().sorted().toList(),
                candidates.candidatesUsed(), composition.combinationsChecked());
    }
}
