package com.stylemind.stylist.model.recommendation;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Offline recommendation values; these are not persistence entities or public API contracts. */
public final class EngineModel {
    private EngineModel() { }

    /** Legacy values remain representable only so invalid upstream mappings can be rejected explicitly. */
    public enum Slot { TOP, BOTTOM, ONE_PIECE, SHOES, ACCESSORY }
    public enum Holding { NONE, SAVED, OWNED }
    public enum SourceType { SHOP_VARIANT, OWNED_WARDROBE }
    public enum Kind { EXCLUDE_CATEGORY, EXCLUDE_COLOR, EXCLUDE_PRODUCT, EXCLUDE_VARIANT }

    /** The source discriminator is a closed union, so a candidate cannot carry both source references. */
    public sealed interface SourceReference permits ShopVariantReference, OwnedWardrobeReference {
        UUID variantId();
        SourceType type();
        default UUID wardrobeItemIdOrNull() { return null; }
    }

    /** Commerce identity for a purchasable or saved shop variant. */
    public record ShopVariantReference(UUID variantId) implements SourceReference {
        public ShopVariantReference { Objects.requireNonNull(variantId, "variantId"); }
        @Override public SourceType type() { return SourceType.SHOP_VARIANT; }
    }

    /** Synthetic P02 owned reference; the Commerce variant remains separate for catalog identity. */
    public record OwnedWardrobeReference(UUID wardrobeItemId, UUID variantId) implements SourceReference {
        public OwnedWardrobeReference {
            Objects.requireNonNull(wardrobeItemId, "wardrobeItemId");
            Objects.requireNonNull(variantId, "variantId");
        }
        @Override public SourceType type() { return SourceType.OWNED_WARDROBE; }
        @Override public UUID wardrobeItemIdOrNull() { return wardrobeItemId; }
    }

    /** Every item keeps its Commerce variant identity; a wardrobe item ID is a distinct typed source reference. */
    public record Item(UUID productId, UUID categoryId, UUID brandId, Slot slot, String color,
                       Set<String> styles, long priceVnd, boolean purchasable,
                       boolean womenCatalogProduct, Holding holding,
                       SourceReference sourceReference, boolean anchor) {
        public Item {
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(categoryId, "categoryId");
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(styles, "styles");
            Objects.requireNonNull(holding, "holding");
            Objects.requireNonNull(sourceReference, "sourceReference");
            styles = Set.copyOf(styles);
            if (slot != Slot.TOP && slot != Slot.BOTTOM)
                throw new IllegalArgumentException("Unsupported Stylist slot; only TOP and BOTTOM are allowed");
            if (priceVnd < 0) throw new IllegalArgumentException("Negative price");
            if (sourceReference.type() == SourceType.SHOP_VARIANT && holding == Holding.OWNED)
                throw new IllegalArgumentException("Owned items require an OWNED_WARDROBE source reference");
            if (sourceReference.type() == SourceType.OWNED_WARDROBE && holding != Holding.OWNED)
                throw new IllegalArgumentException("OWNED_WARDROBE source requires confirmed ownership");
            if (anchor && sourceReference.type() != SourceType.OWNED_WARDROBE)
                throw new IllegalArgumentException("Only owned wardrobe items can be anchors");
        }

        public UUID variantId() { return sourceReference.variantId(); }
        public UUID wardrobeItemIdOrNull() { return sourceReference.wardrobeItemIdOrNull(); }
        public long additionalCostVnd() { return holding == Holding.OWNED ? 0 : priceVnd; }
    }

    /** Raw kind codes let the engine reject unknown hard constraints and warn/ignore unknown soft ones. */
    public record Exclusion(String kindCode, UUID entityId, String text, boolean hard) {
        public Exclusion {
            if (kindCode == null || kindCode.isBlank()) throw new IllegalArgumentException("Missing exclusion kind");
            Kind known = knownKind(kindCode);
            if (known != null && (known == Kind.EXCLUDE_COLOR
                    ? entityId != null || text == null || text.isBlank()
                    : entityId == null || text != null))
                throw new IllegalArgumentException("Invalid exclusion value/XOR");
        }

        public Exclusion(Kind kind, UUID entityId, String text, boolean hard) {
            this(Objects.requireNonNull(kind, "kind").name(), entityId, text, hard);
        }

        public Kind knownKindOrNull() { return knownKind(kindCode); }

        public boolean matches(Item item) {
            Kind kind = knownKindOrNull();
            if (kind == null) throw new IllegalStateException("Unknown exclusion cannot be matched");
            return switch (kind) {
                case EXCLUDE_CATEGORY -> entityId.equals(item.categoryId());
                case EXCLUDE_PRODUCT -> entityId.equals(item.productId());
                case EXCLUDE_VARIANT -> entityId.equals(item.variantId());
                case EXCLUDE_COLOR -> text.equals(item.color());
            };
        }

        private static Kind knownKind(String value) {
            try { return Kind.valueOf(value); }
            catch (IllegalArgumentException exception) { return null; }
        }
    }

    /** Explicitly reviewed P02 mapping; test mappings are synthetic and never imply live taxonomy. */
    public record Vocabulary(Set<String> styles, Set<String> colors, Set<UUID> brands) {
        public Vocabulary {
            styles = Set.copyOf(styles);
            colors = Set.copyOf(colors);
            brands = Set.copyOf(brands);
        }
        public Vocabulary(Set<String> styles, Set<String> colors) { this(styles, colors, Set.of()); }
    }

    public record Preferences(Set<String> styles, Set<String> colors, Set<UUID> brands) {
        public Preferences {
            styles = Set.copyOf(styles);
            colors = Set.copyOf(colors);
            brands = Set.copyOf(brands);
        }
    }

    /** Curated binary evidence only; there is no semantic inference or external call in P02. */
    public record Evidence(Set<UUID> contextMatches, Set<Set<UUID>> compatibleOutfits) {
        public Evidence {
            contextMatches = Set.copyOf(contextMatches);
            compatibleOutfits = compatibleOutfits.stream().map(Set::copyOf)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    /** Each instance is one complete request context; the engine keeps no state between requests. */
    public record Input(List<Item> candidates, List<UUID> selectedOwnedWardrobeItemIds,
                        Long budgetVnd, boolean hardBudget, List<Exclusion> exclusions,
                        Preferences preferences, Vocabulary vocabulary, Evidence evidence) {
        public Input {
            candidates = List.copyOf(candidates);
            selectedOwnedWardrobeItemIds = List.copyOf(selectedOwnedWardrobeItemIds);
            exclusions = List.copyOf(exclusions);
            Objects.requireNonNull(preferences, "preferences");
            Objects.requireNonNull(vocabulary, "vocabulary");
            Objects.requireNonNull(evidence, "evidence");
            if (selectedOwnedWardrobeItemIds.stream().distinct().count() != selectedOwnedWardrobeItemIds.size())
                throw new IllegalArgumentException("Duplicate selected owned wardrobe item ID");
            if ((budgetVnd != null && budgetVnd < 0) || (hardBudget && budgetVnd == null))
                throw new IllegalArgumentException("Invalid budget");
        }
    }

    /** Reduced exact rational score, avoiding rounded or non-terminating decimal division. */
    public record RationalScore(BigInteger numerator, BigInteger denominator) implements Comparable<RationalScore> {
        public RationalScore {
            Objects.requireNonNull(numerator, "numerator");
            Objects.requireNonNull(denominator, "denominator");
            if (denominator.signum() == 0) throw new IllegalArgumentException("Score denominator cannot be zero");
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }

        public static RationalScore of(long whole) {
            return new RationalScore(BigInteger.valueOf(whole), BigInteger.ONE);
        }

        public static RationalScore of(long numerator, long denominator) {
            return new RationalScore(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
        }

        public RationalScore plus(RationalScore other) {
            return new RationalScore(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }

        public RationalScore minus(RationalScore other) {
            return new RationalScore(numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }

        @Override public int compareTo(RationalScore other) {
            return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
        }

        @Override public String toString() {
            return denominator.equals(BigInteger.ONE) ? numerator.toString() : numerator + "/" + denominator;
        }
    }

    public record Outfit(List<Item> items, long additionalCostVnd, RationalScore score) {
        public Outfit {
            items = List.copyOf(items);
            Objects.requireNonNull(score, "score");
            if (items.size() != 2 || additionalCostVnd < 0)
                throw new IllegalArgumentException("Invalid outfit size or cost");
            Set<Slot> slots = new java.util.HashSet<>();
            Set<UUID> variants = new java.util.HashSet<>();
            Set<UUID> wardrobeItems = new java.util.HashSet<>();
            long expectedCost = 0;
            for (Item item : items) {
                if (!slots.add(item.slot())) throw new IllegalArgumentException("More than one item in an outfit slot");
                if (!variants.add(item.variantId())) throw new IllegalArgumentException("Duplicate variant in outfit");
                UUID wardrobeItemId = item.wardrobeItemIdOrNull();
                if (wardrobeItemId != null && !wardrobeItems.add(wardrobeItemId))
                    throw new IllegalArgumentException("Duplicate wardrobe item in outfit");
                try { expectedCost = Math.addExact(expectedCost, item.additionalCostVnd()); }
                catch (ArithmeticException exception) { throw new IllegalArgumentException("Outfit cost overflow", exception); }
            }
            if (!slots.equals(Set.of(Slot.TOP, Slot.BOTTOM)))
                throw new IllegalArgumentException("Outfit must contain exactly one TOP and one BOTTOM");
            if (expectedCost != additionalCostVnd) throw new IllegalArgumentException("Outfit cost does not match item sources");
        }
        public Set<UUID> variantIds() {
            return items.stream().map(Item::variantId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    public record Result(List<Outfit> outfits, List<String> warnings, int candidatesUsed,
                         int combinationsChecked) {
        public Result {
            outfits = List.copyOf(outfits);
            warnings = List.copyOf(warnings);
            if (candidatesUsed < 0 || combinationsChecked < 0) throw new IllegalArgumentException("Negative counts");
        }
        public boolean noResult() { return outfits.isEmpty(); }
    }
}
