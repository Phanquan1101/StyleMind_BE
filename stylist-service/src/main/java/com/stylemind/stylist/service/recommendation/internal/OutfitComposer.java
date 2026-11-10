package com.stylemind.stylist.service.recommendation.internal;

import com.stylemind.stylist.model.recommendation.EngineModel.Item;
import com.stylemind.stylist.model.recommendation.EngineModel.Slot;
import java.util.ArrayList;
import java.util.List;

/** Builds only the approved exact TOP + BOTTOM template in a stable bounded order. */
public final class OutfitComposer {
    public static final int MAX_COMBINATIONS = 1_000;

    public record Composition(List<List<Item>> outfits, boolean truncated) {
        public Composition { outfits = outfits.stream().map(List::copyOf).toList(); }
        public int combinationsChecked() { return outfits.size(); }
    }

    public Composition compose(CandidatePolicy.Selection candidates) {
        Search search = new Search();
        for (Item top : requiredOrCandidates(candidates, Slot.TOP)) {
            for (Item bottom : requiredOrCandidates(candidates, Slot.BOTTOM)) {
                if (!search.add(List.of(top, bottom))) return search.result();
            }
        }
        return search.result();
    }

    private static List<Item> requiredOrCandidates(CandidatePolicy.Selection candidates, Slot slot) {
        List<Item> anchors = candidates.anchors().stream().filter(item -> item.slot() == slot).toList();
        return anchors.isEmpty() ? candidates.in(slot) : anchors;
    }

    private static final class Search {
        private final List<List<Item>> outfits = new ArrayList<>();
        private boolean truncated;

        private boolean add(List<Item> outfit) {
            if (outfits.size() == MAX_COMBINATIONS) {
                truncated = true;
                return false;
            }
            outfits.add(List.copyOf(outfit));
            return true;
        }

        private Composition result() { return new Composition(outfits, truncated); }
    }
}
