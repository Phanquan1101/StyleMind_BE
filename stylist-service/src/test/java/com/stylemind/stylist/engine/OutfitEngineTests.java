package com.stylemind.stylist.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.stylist.model.recommendation.EngineModel.*;
import com.stylemind.stylist.service.recommendation.internal.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutfitEngineTests {
    private final OutfitEngine engine = new OutfitEngine();

    private static UUID id(long value) { return new UUID(0, value); }

    private static Item item(long variant, Slot slot, long price) {
        return new Item(id(variant + 10_000), id(1_000 + slot.ordinal()), id(20_000), slot,
                "SYNTHETIC_BLACK", Set.of("SYNTHETIC_MINIMAL"), price, true, true,
                Holding.NONE, new ShopVariantReference(id(variant)), false);
    }

    private static Item saved(Item item) {
        return new Item(item.productId(), item.categoryId(), item.brandId(), item.slot(), item.color(), item.styles(),
                item.priceVnd(), item.purchasable(), item.womenCatalogProduct(), Holding.SAVED,
                new ShopVariantReference(item.variantId()), false);
    }

    private static Item owned(long variant, Slot slot, long currentPrice, boolean anchor) {
        return new Item(id(variant + 10_000), id(1_000 + slot.ordinal()), id(20_000), slot,
                "SYNTHETIC_BLACK", Set.of("SYNTHETIC_MINIMAL"), currentPrice, false, true,
                Holding.OWNED, new OwnedWardrobeReference(id(100_000 + variant), id(variant)), anchor);
    }

    private static Item metadata(Item item, String color, Set<String> styles, UUID brandId) {
        return new Item(item.productId(), item.categoryId(), brandId, item.slot(), color, styles, item.priceVnd(),
                item.purchasable(), item.womenCatalogProduct(), item.holding(), item.sourceReference(), item.anchor());
    }

    private static Item availability(Item item, boolean purchasable, boolean inWomenCatalog) {
        return new Item(item.productId(), item.categoryId(), item.brandId(), item.slot(), item.color(), item.styles(),
                item.priceVnd(), purchasable, inWomenCatalog, item.holding(), item.sourceReference(), item.anchor());
    }

    private static Input input(List<Item> items, Long budget, boolean hard, Exclusion... exclusions) {
        return input(items, budget, hard, List.of(exclusions), defaultPreferences(), defaultVocabulary(),
                new Evidence(Set.of(), Set.of()));
    }

    private static Input input(List<Item> items, Long budget, boolean hard, List<Exclusion> exclusions,
                               Preferences preferences, Vocabulary vocabulary, Evidence evidence) {
        List<UUID> selected = items.stream().filter(Item::anchor).map(Item::wardrobeItemIdOrNull).toList();
        return new Input(items, selected, budget, hard, exclusions, preferences, vocabulary, evidence);
    }

    private static Preferences defaultPreferences() {
        return new Preferences(Set.of(), Set.of(), Set.of());
    }

    private static Vocabulary defaultVocabulary() {
        return new Vocabulary(Set.of("SYNTHETIC_MINIMAL"), Set.of("SYNTHETIC_BLACK", "SYNTHETIC_RED"), Set.of(id(20_000)));
    }

    private static List<Item> pair() {
        return List.of(item(1, Slot.TOP, 250_000), item(2, Slot.BOTTOM, 350_000));
    }

    @Test void completeTwoPieceCostsSum() {
        var result = engine.recommend(input(pair(), null, false));
        assertThat(result.outfits()).singleElement().satisfies(outfit -> {
            assertThat(outfit.items()).hasSize(2);
            assertThat(outfit.additionalCostVnd()).isEqualTo(600_000);
        });
    }

    @Test void everyOutfitContainsExactlyOneTopAndOneBottom() {
        var candidates = List.of(item(1, Slot.TOP, 100), item(2, Slot.TOP, 200),
                item(3, Slot.BOTTOM, 300), item(4, Slot.BOTTOM, 400));
        var result = engine.recommend(input(candidates, null, false));
        assertThat(result.outfits()).hasSize(3).allSatisfy(outfit -> {
            assertThat(outfit.items()).hasSize(2);
            assertThat(outfit.items()).extracting(Item::slot).containsExactlyInAnyOrder(Slot.TOP, Slot.BOTTOM);
            assertThat(outfit.variantIds()).hasSize(2);
        });
    }

    @Test void unsupportedSlotsAreRejectedAtTheEngineBoundary() {
        for (Slot unsupported : List.of(Slot.ONE_PIECE, Slot.SHOES, Slot.ACCESSORY)) {
            assertThatThrownBy(() -> item(99, unsupported, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("only TOP and BOTTOM");
        }
        assertThatThrownBy(() -> Slot.valueOf("OTHER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void repeatedCandidatesForOneSlotNeverAppearTogether() {
        var candidates = List.of(item(1, Slot.TOP, 1), item(2, Slot.TOP, 2),
                item(3, Slot.BOTTOM, 1), item(4, Slot.BOTTOM, 2));
        assertThat(engine.recommend(input(candidates, null, false)).outfits()).allSatisfy(outfit ->
                assertThat(outfit.items().stream().map(Item::slot).distinct().count()).isEqualTo(2));
    }

    @Test void missingMandatorySlotIsValidNoResult() {
        var result = engine.recommend(input(List.of(item(1, Slot.TOP, 1)), null, false));
        assertThat(result.noResult()).isTrue();
        assertThat(result.warnings()).contains("NO_RESULT_WITHIN_BOUNDED_SEARCH");
    }

    @Test void zeroBudgetIsValidForFreeOutfit() {
        assertThat(engine.recommend(input(List.of(item(1, Slot.TOP, 0), item(2, Slot.BOTTOM, 0)), 0L, true))
                .outfits()).hasSize(1);
    }

    @Test void hardBudgetAllowsExactEqualityAndRejectsOneVndOver() {
        assertThat(engine.recommend(input(pair(), 600_000L, true)).outfits()).hasSize(1);
        assertThat(engine.recommend(input(pair(), 599_999L, true)).noResult()).isTrue();
    }

    @Test void hardBudgetZeroRejectsPositiveShopPrice() {
        assertThat(engine.recommend(input(pair(), 0L, true)).noResult()).isTrue();
    }

    @Test void hardBudgetWithoutAmountIsInvalidInput() {
        assertThatThrownBy(() -> input(pair(), null, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void softBudgetChangesRankingEvidenceButDoesNotRejectOverBudget() {
        var candidates = List.of(item(1, Slot.TOP, 50), item(2, Slot.TOP, 200), item(3, Slot.BOTTOM, 1));
        var under = engine.recommend(input(candidates, 51L, false));
        assertThat(under.outfits()).hasSize(2);
        assertThat(under.outfits().getFirst().score()).isEqualTo(RationalScore.of(1));
        assertThat(under.outfits().getLast().score()).isEqualTo(RationalScore.of(0));
    }

    @Test void exactHardBudgetAppliesToTheOnlyTwoProductSlots() {
        var candidates = List.of(item(1, Slot.TOP, 250_000), item(2, Slot.BOTTOM, 350_000),
                item(3, Slot.BOTTOM, 350_001));
        var equal = engine.recommend(input(candidates, 600_000L, true));
        assertThat(equal.outfits()).singleElement().satisfies(outfit -> {
            assertThat(outfit.items()).hasSize(2);
            assertThat(outfit.additionalCostVnd()).isEqualTo(600_000);
        });
        assertThat(engine.recommend(input(candidates, 599_999L, true)).noResult()).isTrue();
    }

    @Test void negativeBudgetAndNegativeCandidatePriceAreInvalid() {
        assertThatThrownBy(() -> input(pair(), -1L, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item(1, Slot.TOP, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.1", "9223372036854775808"})
    void exactVndRejectsNegativeFractionalAndOverflowValues(String value) {
        assertThatThrownBy(() -> BudgetPolicy.exactVnd(new BigDecimal(value))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void exactVndAcceptsLongBoundaryAndRejectsNull() {
        assertThat(BudgetPolicy.exactVnd(new BigDecimal(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);
        assertThatThrownBy(() -> BudgetPolicy.exactVnd(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void overflowRejectsOnlyAffectedOutfitAndReportsIt() {
        var result = engine.recommend(input(List.of(item(1, Slot.TOP, Long.MAX_VALUE), item(2, Slot.TOP, 2),
                item(3, Slot.BOTTOM, 1)), null, false));
        assertThat(result.outfits()).singleElement().satisfies(outfit -> assertThat(outfit.additionalCostVnd()).isEqualTo(3));
        assertThat(result.warnings()).contains("OUTFIT_PRICE_OVERFLOW");
    }

    @Test void ownedAnchorIsFreeAndAlwaysRetainedWithPurchasableCompletion() {
        Item anchor = owned(1, Slot.TOP, 900_000, true);
        var result = engine.recommend(input(List.of(anchor, item(2, Slot.BOTTOM, 350_000)), 350_000L, true));
        assertThat(result.outfits()).singleElement().satisfies(outfit -> {
            assertThat(outfit.items()).contains(anchor);
            assertThat(outfit.additionalCostVnd()).isEqualTo(350_000);
        });
    }

    @Test void ownedBottomAnchorIsRetainedWithShopTopAtZeroAnchorCost() {
        Item anchor = owned(1, Slot.BOTTOM, 800_000, true);
        var result = engine.recommend(input(List.of(anchor, item(2, Slot.TOP, 40_000)), 40_000L, true));
        assertThat(result.outfits()).singleElement().satisfies(outfit -> {
            assertThat(outfit.items()).contains(anchor);
            assertThat(outfit.additionalCostVnd()).isEqualTo(40_000);
        });
    }

    @Test void savedShopVariantIsNotOwnedAndItsCurrentPriceIsCharged() {
        Item savedTop = saved(item(1, Slot.TOP, 250_000));
        var result = engine.recommend(input(List.of(savedTop, item(2, Slot.BOTTOM, 350_000)), 599_999L, true));
        assertThat(result.noResult()).isTrue();
        assertThat(savedTop.sourceReference().type()).isEqualTo(SourceType.SHOP_VARIANT);
        assertThat(savedTop.additionalCostVnd()).isEqualTo(250_000);
    }

    @Test void savedShopVariantMustStillBePurchasable() {
        Item unavailable = availability(saved(item(1, Slot.TOP, 1)), false, true);
        assertThat(engine.recommend(input(List.of(unavailable, item(2, Slot.BOTTOM, 1)), null, false)).noResult())
                .isTrue();
    }

    @Test void sourceDiscriminatorKeepsVariantAndWardrobeIdsDistinct() {
        Item owned = owned(1, Slot.TOP, 99, true);
        assertThat(owned.sourceReference().type()).isEqualTo(SourceType.OWNED_WARDROBE);
        assertThat(owned.variantId()).isEqualTo(id(1));
        assertThat(owned.wardrobeItemIdOrNull()).isEqualTo(id(100_001));
        assertThat(owned.wardrobeItemIdOrNull()).isNotEqualTo(owned.variantId());
    }

    @Test void sourceReferenceUnionRejectsMissingOrMismatchedReferences() {
        assertThatThrownBy(() -> new ShopVariantReference(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OwnedWardrobeReference(null, id(1))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Item(id(2), id(3), null, Slot.TOP, "SYNTHETIC_BLACK", Set.of(), 1,
                true, true, Holding.OWNED, new ShopVariantReference(id(1)), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(id(2), id(3), null, Slot.TOP, "SYNTHETIC_BLACK", Set.of(), 1,
                true, true, Holding.SAVED, new OwnedWardrobeReference(id(4), id(1)), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Item(id(2), id(3), null, Slot.TOP, "SYNTHETIC_BLACK", Set.of(), 1,
                true, true, Holding.NONE, new ShopVariantReference(id(1)), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void selectedOwnedIdsMustMatchPresentAnchorCandidatesExactly() {
        Item anchor = owned(1, Slot.TOP, 10, true);
        assertThatThrownBy(() -> engine.recommend(new Input(List.of(anchor), List.of(), null, false, List.of(),
                defaultPreferences(), defaultVocabulary(), new Evidence(Set.of(), Set.of()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.recommend(new Input(List.of(), List.of(id(100_001)), null, false, List.of(),
                defaultPreferences(), defaultVocabulary(), new Evidence(Set.of(), Set.of()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Input(List.of(anchor), List.of(id(100_001), id(100_001)), null, false,
                List.of(), defaultPreferences(), defaultVocabulary(), new Evidence(Set.of(), Set.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void selectedOwnedAnchorThatFailsHardExclusionIsInvalidContext() {
        Item anchor = owned(1, Slot.TOP, 1, true);
        assertThatThrownBy(() -> engine.recommend(input(List.of(anchor), null, false,
                new Exclusion(Kind.EXCLUDE_VARIANT, id(1), null, true))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void twoOwnedAnchorsInOneSlotAreInvalidContext() {
        assertThatThrownBy(() -> engine.recommend(input(List.of(owned(1, Slot.TOP, 1, true),
                owned(2, Slot.TOP, 1, true)), null, false))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void twoOwnedAnchorsFormTheCompleteOutfitWithoutCrossSell() {
        Item top = owned(1, Slot.TOP, 90_000, true);
        Item bottom = owned(2, Slot.BOTTOM, 80_000, true);
        var result = engine.recommend(input(List.of(top, bottom), 0L, true));
        assertThat(result.outfits()).singleElement().satisfies(outfit -> {
            assertThat(outfit.items()).containsExactlyInAnyOrder(top, bottom);
            assertThat(outfit.additionalCostVnd()).isZero();
        });
    }

    @Test void oneOwnedAnchorIsRetainedInEveryAlternativeResult() {
        Item anchor = owned(1, Slot.TOP, 90_000, true);
        var result = engine.recommend(input(List.of(anchor, item(2, Slot.BOTTOM, 10),
                item(3, Slot.BOTTOM, 20), item(4, Slot.BOTTOM, 30), item(5, Slot.BOTTOM, 40)), null, false));
        assertThat(result.outfits()).hasSize(3).allSatisfy(outfit -> assertThat(outfit.items()).contains(anchor));
    }

    @Test void duplicateVariantAndDuplicateWardrobeSourcesAreRejected() {
        Item top = item(1, Slot.TOP, 1);
        assertThatThrownBy(() -> engine.recommend(input(List.of(top, owned(1, Slot.BOTTOM, 1, false)), null, false)))
                .isInstanceOf(IllegalArgumentException.class);
        Item first = owned(1, Slot.TOP, 1, true);
        Item second = new Item(id(4), id(5), null, Slot.BOTTOM, "SYNTHETIC_BLACK", Set.of(), 1,
                true, true, Holding.OWNED, new OwnedWardrobeReference(first.wardrobeItemIdOrNull(), id(4)), false);
        assertThatThrownBy(() -> engine.recommend(input(List.of(first, second), null, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void inactiveAndOutOfScopeCandidatesAreFiltered() {
        Item inactive = availability(item(1, Slot.TOP, 1), false, true);
        Item outOfScope = availability(item(2, Slot.BOTTOM, 1), true, false);
        assertThat(engine.recommend(input(List.of(inactive, outOfScope), null, false)).noResult()).isTrue();
    }

    @Test void invalidSelectedAnchorIsNotSilentlyFiltered() {
        Item anchor = availability(owned(1, Slot.TOP, 1, true), false, false);
        assertThatThrownBy(() -> engine.recommend(input(List.of(anchor), null, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void hardExclusionWinsOverMatchingSoftPreference() {
        var prefs = new Preferences(Set.of(), Set.of("SYNTHETIC_BLACK"), Set.of());
        var result = engine.recommend(input(pair(), null, false,
                List.of(new Exclusion(Kind.EXCLUDE_COLOR, null, "SYNTHETIC_BLACK", true)),
                prefs, defaultVocabulary(), new Evidence(Set.of(), Set.of())));
        assertThat(result.noResult()).isTrue();
    }

    @Test void categoryProductVariantAndColorHardExclusionsMatchPrecisely() {
        Item candidate = item(1, Slot.TOP, 1);
        for (Exclusion exclusion : List.of(
                new Exclusion(Kind.EXCLUDE_CATEGORY, candidate.categoryId(), null, true),
                new Exclusion(Kind.EXCLUDE_PRODUCT, candidate.productId(), null, true),
                new Exclusion(Kind.EXCLUDE_VARIANT, candidate.variantId(), null, true),
                new Exclusion(Kind.EXCLUDE_COLOR, null, "SYNTHETIC_BLACK", true))) {
            assertThat(engine.recommend(input(List.of(candidate, item(2, Slot.BOTTOM, 1)), null, false, exclusion))
                    .noResult()).isTrue();
        }
    }

    @Test void unknownHardConstraintKindAndUnknownHardColorAreInvalid() {
        assertThatThrownBy(() -> engine.recommend(input(pair(), null, false,
                new Exclusion("EXCLUDE_FABRIC", id(7), null, true)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.recommend(input(pair(), null, false,
                new Exclusion(Kind.EXCLUDE_COLOR, null, "UNMAPPED_COLOR", true))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void unknownSoftConstraintAndPreferencesAreWarnedAndIgnored() {
        var prefs = new Preferences(Set.of("UNMAPPED_STYLE"), Set.of("UNMAPPED_COLOR"), Set.of(id(999)));
        var result = engine.recommend(input(pair(), null, false,
                List.of(new Exclusion("EXCLUDE_FABRIC", id(7), null, false),
                        new Exclusion(Kind.EXCLUDE_COLOR, null, "UNMAPPED_COLOR", false)),
                prefs, defaultVocabulary(), new Evidence(Set.of(), Set.of())));
        assertThat(result.outfits()).hasSize(1);
        assertThat(result.outfits().getFirst().score()).isEqualTo(RationalScore.of(0));
        assertThat(result.warnings()).contains("UNKNOWN_SOFT_EXCLUSION_IGNORED", "UNKNOWN_SOFT_STYLE_IGNORED",
                "UNKNOWN_SOFT_COLOR_IGNORED", "UNKNOWN_SOFT_BRAND_IGNORED");
    }

    @Test void softExclusionPenalizesMatchingItemsWithoutRejectingOutfit() {
        var result = engine.recommend(input(pair(), null, false,
                new Exclusion(Kind.EXCLUDE_VARIANT, id(1), null, false)));
        assertThat(result.outfits()).hasSize(1);
        assertThat(result.outfits().getFirst().score()).isEqualTo(RationalScore.of(-1));
    }

    @Test void knownExclusionRequiresCorrectTypedValueXor() {
        assertThatThrownBy(() -> new Exclusion(Kind.EXCLUDE_COLOR, id(1), "SYNTHETIC_BLACK", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Exclusion(Kind.EXCLUDE_PRODUCT, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void duplicateConstraintsCannotDoublePenalty() {
        var exclusion = new Exclusion(Kind.EXCLUDE_COLOR, null, "SYNTHETIC_BLACK", false);
        assertThatThrownBy(() -> engine.recommend(input(pair(), null, false, exclusion, exclusion)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void exactScoreUsesContextStyleColorBudgetAndReviewedCompatibilityWeights() {
        var prefs = new Preferences(Set.of("SYNTHETIC_MINIMAL"), Set.of("SYNTHETIC_BLACK"), Set.of());
        var evidence = new Evidence(Set.of(id(1)), Set.of(Set.of(id(1), id(2))));
        var result = engine.recommend(input(pair(), 600_000L, false, List.of(), prefs, defaultVocabulary(), evidence));
        assertThat(result.outfits().getFirst().score()).isEqualTo(RationalScore.of(11));
    }

    @Test void twoProductOutfitUsesExactRationalScoreFractions() {
        Item top = metadata(item(1, Slot.TOP, 10), "SYNTHETIC_RED", Set.of("SYNTHETIC_MINIMAL"), id(20_000));
        Item bottom = metadata(item(2, Slot.BOTTOM, 10), "SYNTHETIC_BLACK", Set.of(), null);
        var prefs = new Preferences(Set.of("SYNTHETIC_MINIMAL"), Set.of("SYNTHETIC_RED"), Set.of(id(20_000)));
        var result = engine.recommend(input(List.of(top, bottom), null, false,
                List.of(), prefs, defaultVocabulary(), new Evidence(Set.of(), Set.of())));
        assertThat(result.outfits()).singleElement().satisfies(outfit -> {
            assertThat(outfit.items()).hasSize(2);
            assertThat(outfit.score()).isEqualTo(RationalScore.of(5, 2));
        });
    }

    @Test void missingPreferenceEvidenceAndMetadataContributeZero() {
        Item noMetadata = metadata(item(1, Slot.TOP, 1), null, Set.of(), null);
        Item noMetadataBottom = metadata(item(2, Slot.BOTTOM, 1), null, Set.of(), null);
        var prefs = new Preferences(Set.of("SYNTHETIC_MINIMAL"), Set.of("SYNTHETIC_RED"), Set.of(id(20_000)));
        var result = engine.recommend(input(List.of(noMetadata, noMetadataBottom), null, false,
                List.of(), prefs, defaultVocabulary(), new Evidence(Set.of(), Set.of())));
        assertThat(result.outfits().getFirst().score()).isEqualTo(RationalScore.of(0));
    }

    @Test void contextValuesExplicitNullOverridesSavedScalarAndDoesNotMutateSources() {
        var explicit = new HashMap<String, String>();
        explicit.put("occasion", null);
        var saved = Map.of("occasion", "WORK", "style", "MINIMAL");
        var resolved = new ContextResolver().resolve(explicit, saved);
        assertThat(resolved).containsEntry("occasion", null).containsEntry("style", "MINIMAL");
        assertThat(saved).containsEntry("occasion", "WORK");
    }

    @Test void omittedContextAndRequestArraysDoNotInheritFromPreviousInput() {
        var previous = new Input(pair(), List.of(), null, false,
                List.of(new Exclusion(Kind.EXCLUDE_VARIANT, id(1), null, false)), defaultPreferences(),
                defaultVocabulary(), new Evidence(Set.of(), Set.of()));
        var current = new Input(List.of(), List.of(), null, false, List.of(), defaultPreferences(),
                defaultVocabulary(), new Evidence(Set.of(), Set.of()));
        assertThat(previous.exclusions()).hasSize(1);
        assertThat(current.exclusions()).isEmpty();
        assertThat(current.selectedOwnedWardrobeItemIds()).isEmpty();
        assertThat(new ContextResolver().resolve(Map.of(), Map.of())).isEmpty();
    }

    @Test void resultOrderingIsStableRegardlessOfInputOrder() {
        var candidates = new ArrayList<>(List.of(item(1, Slot.TOP, 10), item(2, Slot.TOP, 10),
                item(3, Slot.BOTTOM, 10), item(4, Slot.BOTTOM, 10)));
        var first = engine.recommend(input(candidates, null, false));
        Collections.reverse(candidates);
        assertThat(engine.recommend(input(candidates, null, false))).isEqualTo(first);
    }

    @Test void returnsAtMostThreeResultsThatDifferByNonAnchorItems() {
        Item anchor = owned(1, Slot.TOP, 10, true);
        var result = engine.recommend(input(List.of(anchor, item(2, Slot.BOTTOM, 1), item(3, Slot.BOTTOM, 1),
                item(4, Slot.BOTTOM, 1), item(5, Slot.BOTTOM, 1)), null, false));
        assertThat(result.outfits()).hasSize(3);
        assertThat(result.outfits()).allSatisfy(outfit -> assertThat(outfit.items()).contains(anchor));
        List<Set<UUID>> nonAnchorSets = result.outfits().stream().map(outfit -> outfit.items().stream()
                .filter(item -> !item.anchor()).map(Item::variantId).collect(Collectors.toSet())).toList();
        assertThat(nonAnchorSets).doesNotHaveDuplicates();
    }

    @Test void candidateCapPreservesAnchorsAndMandatoryPerSlotLimit() {
        List<Item> candidates = new ArrayList<>();
        for (int n = 1; n <= 22; n++) candidates.add(item(n, Slot.TOP, 1));
        Item anchor = owned(999, Slot.TOP, 100, true);
        candidates.add(anchor);
        candidates.add(item(40, Slot.BOTTOM, 1));
        var result = engine.recommend(input(candidates, null, false));
        assertThat(result.candidatesUsed()).isEqualTo(21);
        assertThat(result.outfits()).isNotEmpty().allSatisfy(outfit -> assertThat(outfit.items()).contains(anchor));
        assertThat(result.warnings()).contains("CANDIDATES_TRUNCATED");
    }

    @Test void twoSlotCandidateBoundRemainsWithinGlobalCapAndSearchLimit() {
        List<Item> candidates = new ArrayList<>();
        long next = 1;
        for (Slot slot : List.of(Slot.TOP, Slot.BOTTOM))
            for (int n = 0; n < 20; n++) candidates.add(item(next++, slot, 1));
        var result = engine.recommend(input(candidates, null, false));
        assertThat(OutfitEngine.MAX_CANDIDATES).isEqualTo(60);
        assertThat(result.candidatesUsed()).isEqualTo(40);
        assertThat(result.combinationsChecked()).isEqualTo(400);
        assertThat(result.combinationsChecked()).isLessThanOrEqualTo(OutfitEngine.MAX_COMBINATIONS);
        assertThat(result.warnings()).doesNotContain("CANDIDATES_TRUNCATED", "SEARCH_TRUNCATED");
    }

    @Test void theConfiguredSearchCapIsNotExceededByTwoSlotBound() {
        assertThat(OutfitEngine.MAX_COMBINATIONS).isEqualTo(1_000);
        var result = engine.recommend(input(List.of(item(1, Slot.TOP, 1), item(2, Slot.BOTTOM, 1)), null, false));
        assertThat(result.combinationsChecked()).isEqualTo(1);
    }

    @Test void whitelistFreezesCompleteOutfitsAndRejectsUnknownOrDuplicateAliases() {
        var result = engine.recommend(input(pair(), null, false));
        var whitelist = new OutfitWhitelist();
        var frozen = whitelist.freeze(result.outfits());
        assertThat(whitelist.select(frozen, List.of("O1"))).containsExactlyElementsOf(result.outfits());
        assertThatThrownBy(() -> whitelist.select(frozen, List.of("O999"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> whitelist.select(frozen, List.of("O1", "O1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> frozen.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void inputsAndOutputsAreImmutableAndEngineDoesNotMutateCandidates() {
        var candidates = new ArrayList<>(pair());
        var input = input(candidates, null, false);
        candidates.clear();
        var result = engine.recommend(input);
        assertThat(input.candidates()).hasSize(2);
        assertThatThrownBy(() -> result.outfits().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.outfits().getFirst().items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> input.candidates().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void seededCatalogsKeepEveryHardInvariant() {
        Random random = new Random(42);
        for (int run = 0; run < 100; run++) {
            List<Item> candidates = new ArrayList<>();
            for (int n = 0; n < 30; n++) {
                Slot slot = random.nextBoolean() ? Slot.TOP : Slot.BOTTOM;
                candidates.add(item(run * 100 + n + 1, slot, random.nextInt(1_000)));
            }
            long budget = random.nextInt(2_000);
            var result = engine.recommend(input(candidates, budget, true));
            assertThat(result.outfits()).hasSizeLessThanOrEqualTo(3).allSatisfy(outfit -> {
                assertThat(outfit.additionalCostVnd()).isLessThanOrEqualTo(budget);
                assertThat(outfit.additionalCostVnd()).isEqualTo(outfit.items().stream()
                        .mapToLong(Item::additionalCostVnd).sum());
                assertThat(outfit.variantIds()).hasSize(outfit.items().size());
                assertThat(outfit.items().stream().map(Item::slot).distinct().count()).isEqualTo(outfit.items().size());
                assertThat(outfit.items()).hasSize(2);
                assertThat(outfit.items()).extracting(Item::slot).containsExactlyInAnyOrder(Slot.TOP, Slot.BOTTOM);
            });
        }
    }

    @Test void publishedExamplesAreExecutableAndAllMappingsAreSynthetic() throws Exception {
        try (var stream = getClass().getResourceAsStream("/stylist/fixtures/p02-commerce-examples.json")) {
            JsonNode fixture = new ObjectMapper().readTree(stream);
            JsonNode mapping = fixture.path("fixture_mapping");
            assertThat(fixture.path("classification").asText()).isEqualTo("SYNTHETIC_TEST_ONLY");
            assertThat(mapping.path("classification").asText()).isEqualTo("SYNTHETIC_TEST_ONLY");
            Set<String> styles = stringSet(mapping.path("style_codes"));
            Set<String> colors = stringSet(mapping.path("color_codes"));
            List<Exclusion> noConstraints = List.of();

            for (JsonNode example : fixture.path("examples")) {
                assertThat(example.path("classification").asText()).isEqualTo("SYNTHETIC_TEST_ONLY");
                List<Item> items = new ArrayList<>();
                List<UUID> selected = new ArrayList<>();
                for (JsonNode row : example.path("items")) {
                    String categoryKey = row.path("category_key").asText();
                    String mappedSlot = mapping.path("category_to_slot").path(categoryKey).asText();
                    assertThat(mappedSlot).isNotBlank();
                    Slot slot = Slot.valueOf(mappedSlot);
                    String color = row.path("color_code").asText();
                    assertThat(colors).contains(color);
                    Set<String> itemStyles = stringSet(row.path("style_codes"));
                    assertThat(styles).containsAll(itemStyles);
                    long variant = row.path("variant_id").asLong();
                    Holding holding = Holding.valueOf(row.path("holding").asText());
                    SourceReference reference;
                    if ("OWNED_WARDROBE".equals(row.path("source_type").asText())) {
                        UUID wardrobeId = id(row.path("wardrobe_item_id").asLong());
                        reference = new OwnedWardrobeReference(wardrobeId, id(variant));
                        if (row.path("anchor").asBoolean()) selected.add(wardrobeId);
                    } else {
                        reference = new ShopVariantReference(id(variant));
                    }
                    items.add(new Item(id(variant + 10_000), id(50_000L + Math.abs((long) categoryKey.hashCode())),
                            null, slot, color, itemStyles, row.path("price_vnd").asLong(),
                            row.path("purchasable").asBoolean(), true, holding, reference,
                            row.path("anchor").asBoolean()));
                }
                var input = new Input(items, selected, example.path("budget_vnd").asLong(),
                        example.path("hard_budget").asBoolean(), noConstraints, defaultPreferences(),
                        new Vocabulary(styles, colors), new Evidence(Set.of(), Set.of()));
                var result = engine.recommend(input);
                List<JsonNode> expected = new ArrayList<>();
                example.path("expected_outfits").forEach(expected::add);
                assertThat(result.outfits()).as(example.path("name").asText()).hasSize(expected.size());
                for (int n = 0; n < expected.size(); n++) {
                    Set<Long> expectedIds = new HashSet<>();
                    expected.get(n).path("variant_ids").forEach(value -> expectedIds.add(value.asLong()));
                    Set<Long> actualIds = result.outfits().get(n).variantIds().stream()
                            .map(UUID::getLeastSignificantBits).collect(Collectors.toSet());
                    assertThat(actualIds).as(example.path("name").asText()).isEqualTo(expectedIds);
                    assertThat(result.outfits().get(n).additionalCostVnd())
                            .isEqualTo(expected.get(n).path("additional_cost_vnd").asLong());
                }
                if (expected.isEmpty()) assertThat(result.warnings()).contains("NO_RESULT_WITHIN_BOUNDED_SEARCH");
            }
        }
    }

    private static Set<String> stringSet(JsonNode node) {
        Set<String> values = new HashSet<>();
        node.forEach(value -> values.add(value.asText()));
        return Set.copyOf(values);
    }
}
