package com.stylemind.stylist.service.recommendation.internal;

import com.stylemind.stylist.model.recommendation.EngineModel.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Validates the complete request candidate set and applies deterministic per-slot/global caps. */
public final class CandidatePolicy {
    public static final int MAX_PER_MANDATORY_SLOT = 20;
    public static final int MAX_CANDIDATES = 60;

    private static final Comparator<Item> ITEM_ORDER = Comparator
            .comparing(Item::anchor).reversed()
            .thenComparing(i -> i.variantId().toString());
    private static final List<Slot> SLOT_ORDER = List.of(Slot.TOP, Slot.BOTTOM);

    public record Selection(Map<Slot, List<Item>> bySlot, List<Item> anchors, List<String> warnings) {
        public Selection {
            Map<Slot, List<Item>> copy = new EnumMap<>(Slot.class);
            bySlot.forEach((slot, items) -> copy.put(slot, List.copyOf(items)));
            bySlot = java.util.Collections.unmodifiableMap(copy);
            anchors = List.copyOf(anchors);
            warnings = List.copyOf(warnings);
        }

        public List<Item> in(Slot slot) { return bySlot.getOrDefault(slot, List.of()); }
        public int candidatesUsed() { return bySlot.values().stream().mapToInt(List::size).sum(); }
    }

    public Selection prepare(Input input) {
        List<String> warnings = new ArrayList<>();
        validateUniqueSources(input.candidates());
        validateSelectedAnchors(input);
        validateExclusionsAndPreferences(input, warnings);

        List<Item> eligible = input.candidates().stream().filter(item -> eligible(item, input)).toList();
        List<Item> anchors = input.candidates().stream().filter(Item::anchor).toList();
        if (!eligible.containsAll(anchors))
            throw new IllegalArgumentException("Selected owned anchor violates target scope, availability, or hard exclusion");
        validateAnchorSlots(anchors);

        Map<Slot, List<Item>> perSlot = new EnumMap<>(Slot.class);
        for (Slot slot : SLOT_ORDER) {
            List<Item> selected = eligible.stream().filter(item -> item.slot() == slot)
                    .sorted(ITEM_ORDER).limit(MAX_PER_MANDATORY_SLOT).toList();
            perSlot.put(slot, selected);
        }

        List<Item> bounded = new ArrayList<>();
        // Reserve every valid anchor before the global cap is applied.
        anchors.stream().sorted(Comparator.comparing((Item i) -> i.slot().ordinal())
                .thenComparing(i -> i.variantId().toString())).forEach(bounded::add);
        for (Slot slot : SLOT_ORDER) {
            for (Item item : perSlot.getOrDefault(slot, List.of())) {
                if (bounded.size() >= MAX_CANDIDATES) break;
                if (!item.anchor() && !bounded.contains(item)) bounded.add(item);
            }
        }
        if (bounded.size() < eligible.size()) warnings.add("CANDIDATES_TRUNCATED");

        Map<Slot, List<Item>> selectedBySlot = new EnumMap<>(Slot.class);
        for (Slot slot : SLOT_ORDER) {
            selectedBySlot.put(slot, bounded.stream().filter(item -> item.slot() == slot)
                    .sorted(ITEM_ORDER.thenComparing(i -> i.productId().toString())).toList());
        }
        return new Selection(selectedBySlot, anchors, warnings.stream().distinct().sorted().toList());
    }

    private static void validateUniqueSources(List<Item> candidates) {
        Set<UUID> variants = new HashSet<>();
        Set<UUID> wardrobeItems = new HashSet<>();
        for (Item item : candidates) {
            if (!variants.add(item.variantId())) throw new IllegalArgumentException("Duplicate Commerce variant source");
            UUID wardrobeItemId = item.wardrobeItemIdOrNull();
            if (wardrobeItemId != null && !wardrobeItems.add(wardrobeItemId))
                throw new IllegalArgumentException("Duplicate Wardrobe item source");
        }
    }

    private static void validateSelectedAnchors(Input input) {
        Map<UUID, Item> wardrobeItems = new LinkedHashMap<>();
        for (Item item : input.candidates()) {
            UUID wardrobeItemId = item.wardrobeItemIdOrNull();
            if (wardrobeItemId != null) wardrobeItems.put(wardrobeItemId, item);
        }
        Set<UUID> selectedIds = Set.copyOf(input.selectedOwnedWardrobeItemIds());
        for (UUID selectedId : selectedIds) {
            Item selected = wardrobeItems.get(selectedId);
            if (selected == null || selected.holding() != Holding.OWNED || !selected.anchor())
                throw new IllegalArgumentException("Selected owned item is missing or is not a permitted owned anchor");
        }
        for (Item item : input.candidates()) {
            UUID wardrobeItemId = item.wardrobeItemIdOrNull();
            boolean selected = wardrobeItemId != null && selectedIds.contains(wardrobeItemId);
            if (item.anchor() != selected)
                throw new IllegalArgumentException("Anchor flags must exactly match the request's selected owned IDs");
        }
    }

    private static void validateAnchorSlots(List<Item> anchors) {
        if (anchors.stream().map(Item::slot).distinct().count() != anchors.size())
            throw new IllegalArgumentException("Selected owned anchors conflict in the same slot");
        if (anchors.stream().anyMatch(item -> item.slot() != Slot.TOP && item.slot() != Slot.BOTTOM))
            throw new IllegalArgumentException("Selected owned anchor uses an unsupported slot");
    }

    private static void validateExclusionsAndPreferences(Input input, List<String> warnings) {
        if (new HashSet<>(input.exclusions()).size() != input.exclusions().size())
            throw new IllegalArgumentException("Duplicate exclusion");
        for (Exclusion exclusion : input.exclusions()) {
            Kind kind = exclusion.knownKindOrNull();
            if (kind == null) {
                if (exclusion.hard()) throw new IllegalArgumentException("Unknown hard exclusion kind");
                warnings.add("UNKNOWN_SOFT_EXCLUSION_IGNORED");
                continue;
            }
            if (kind == Kind.EXCLUDE_COLOR && !input.vocabulary().colors().contains(exclusion.text())) {
                if (exclusion.hard()) throw new IllegalArgumentException("Unknown hard color exclusion");
                warnings.add("UNKNOWN_SOFT_EXCLUSION_IGNORED");
            }
        }
        if (!input.vocabulary().styles().containsAll(input.preferences().styles()))
            warnings.add("UNKNOWN_SOFT_STYLE_IGNORED");
        if (!input.vocabulary().colors().containsAll(input.preferences().colors()))
            warnings.add("UNKNOWN_SOFT_COLOR_IGNORED");
        if (!input.vocabulary().brands().containsAll(input.preferences().brands()))
            warnings.add("UNKNOWN_SOFT_BRAND_IGNORED");
    }

    private static boolean eligible(Item item, Input input) {
        return item.womenCatalogProduct()
                && (item.holding() == Holding.OWNED || item.purchasable())
                && input.exclusions().stream().noneMatch(exclusion -> exclusion.hard()
                        && exclusion.knownKindOrNull() != null && exclusion.matches(item));
    }

}
