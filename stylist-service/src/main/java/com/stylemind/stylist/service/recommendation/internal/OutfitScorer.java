package com.stylemind.stylist.service.recommendation.internal;

import com.stylemind.stylist.model.recommendation.EngineModel.Evidence;
import com.stylemind.stylist.model.recommendation.EngineModel.Exclusion;
import com.stylemind.stylist.model.recommendation.EngineModel.Input;
import com.stylemind.stylist.model.recommendation.EngineModel.Item;
import com.stylemind.stylist.model.recommendation.EngineModel.Kind;
import com.stylemind.stylist.model.recommendation.EngineModel.RationalScore;
import com.stylemind.stylist.model.recommendation.EngineModel.Vocabulary;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Applies only the explicit bounded weights to mapped preferences and reviewed binary evidence. */
public final class OutfitScorer {
    public RationalScore score(List<Item> items, long additionalCostVnd, Input input) {
        RationalScore score = RationalScore.of(0);
        Set<UUID> variantIds = items.stream().map(Item::variantId).collect(Collectors.toUnmodifiableSet());
        Evidence evidence = input.evidence();
        if (items.stream().anyMatch(item -> evidence.contextMatches().contains(item.variantId())))
            score = score.plus(RationalScore.of(3));
        if (evidence.compatibleOutfits().contains(variantIds)) score = score.plus(RationalScore.of(3));

        Vocabulary vocabulary = input.vocabulary();
        long styleMatches = items.stream().filter(item -> item.styles().stream()
                .anyMatch(style -> vocabulary.styles().contains(style) && input.preferences().styles().contains(style))).count();
        long colorMatches = items.stream().filter(item -> item.color() != null
                && vocabulary.colors().contains(item.color()) && input.preferences().colors().contains(item.color())).count();
        long brandMatches = items.stream().filter(item -> item.brandId() != null
                && vocabulary.brands().contains(item.brandId()) && input.preferences().brands().contains(item.brandId())).count();
        score = score.plus(RationalScore.of(2 * styleMatches, items.size()));
        score = score.plus(RationalScore.of(2 * colorMatches, items.size()));
        score = score.plus(RationalScore.of(brandMatches, items.size()));

        if (!input.hardBudget() && input.budgetVnd() != null && additionalCostVnd <= input.budgetVnd())
            score = score.plus(RationalScore.of(1));

        for (Exclusion exclusion : input.exclusions()) {
            Kind kind = exclusion.knownKindOrNull();
            if (!exclusion.hard() && kind != null
                    && (kind != Kind.EXCLUDE_COLOR || vocabulary.colors().contains(exclusion.text()))) {
                long matches = items.stream().filter(exclusion::matches).count();
                score = score.minus(RationalScore.of(matches));
            }
        }
        return score;
    }
}
