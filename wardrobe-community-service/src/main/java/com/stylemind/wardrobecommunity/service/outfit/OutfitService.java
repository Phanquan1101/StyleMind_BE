package com.stylemind.wardrobecommunity.service.outfit;

import com.stylemind.wardrobecommunity.client.commerce.CommerceCatalogClient;
import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupItem;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitCreateCommand;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitDetailResponse;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitItemResponse;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitListCriteria;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitListResponse;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitSummaryResponse;
import com.stylemind.wardrobecommunity.entity.Outfit;
import com.stylemind.wardrobecommunity.entity.OutfitItem;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.repository.outfit.OutfitItemRepository;
import com.stylemind.wardrobecommunity.repository.outfit.OutfitRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outfit use cases. Calls to Commerce never run inside a database transaction: the catalog is read first,
 * then a short transaction (with the outfit row locked where the rule needs it) validates and writes.
 */
@Service
@RequiredArgsConstructor
public class OutfitService {

    private static final int MAX_OUTFITS_PER_OWNER = 100;
    private static final int MAX_ITEMS_PER_OUTFIT = 10;

    private final OutfitRepository outfits;
    private final OutfitItemRepository outfitItems;
    private final CommerceCatalogClient catalog;
    private final TransactionTemplate transaction;

    @Transactional(readOnly = true)
    public OutfitListResponse list(UUID ownerId, OutfitListCriteria criteria) {
        long total = outfits.countByOwnerAccountId(ownerId);
        long offset = (long) criteria.page() * criteria.size();
        List<OutfitSummaryResponse> items = offset > Integer.MAX_VALUE ? List.of()
                : outfits.findPageWithItemCount(ownerId, PageRequest.of(criteria.page(), criteria.size())).stream()
                        .map(row -> summary((Outfit) row[0], (Long) row[1]))
                        .toList();
        long totalPages = total == 0 ? 0 : (total + criteria.size() - 1) / criteria.size();
        return new OutfitListResponse(items, criteria.page(), criteria.size(), total, totalPages);
    }

    public OutfitDetailResponse create(UUID ownerId, OutfitCreateCommand command) {
        // The owner limit is a plain count: two concurrent creates can both pass it and overshoot by one (accepted).
        if (outfits.countByOwnerAccountId(ownerId) >= MAX_OUTFITS_PER_OWNER) {
            throw conflict("OUTFIT_LIMIT_REACHED", "Outfit limit has been reached");
        }
        Map<UUID, VariantLookupItem> catalogItems = catalog.lookup(command.variantIds());
        command.variantIds().forEach(variantId -> requirePurchasable(catalogItems, variantId));
        return transaction.execute(status -> {
            OffsetDateTime now = now();
            Outfit outfit = new Outfit();
            outfit.setOutfitId(UUID.randomUUID());
            outfit.setOwnerAccountId(ownerId);
            outfit.setName(command.name());
            outfit.setCreationSource(command.creationSource());
            outfit.setCreatedAt(now);
            outfit.setUpdatedAt(now);
            outfits.saveAndFlush(outfit);
            List<OutfitItem> created = new ArrayList<>();
            for (int position = 0; position < command.variantIds().size(); position++) {
                created.add(newItem(outfit.getOutfitId(), command.variantIds().get(position), position, now));
            }
            outfitItems.saveAllAndFlush(created);
            return detail(outfit, created, catalogItems);
        });
    }

    public OutfitDetailResponse get(UUID ownerId, UUID outfitId) {
        Outfit outfit = owned(ownerId, outfitId);
        List<OutfitItem> current = outfitItems.findByOutfitIdOrderByDisplayOrderAscOutfitItemIdAsc(outfitId);
        return detail(outfit, current, catalog.lookup(variantIds(current)));
    }

    @Transactional
    public OutfitSummaryResponse rename(UUID ownerId, UUID outfitId, String name) {
        Outfit outfit = owned(ownerId, outfitId);
        outfit.setName(name);
        outfit.setUpdatedAt(now());
        outfits.saveAndFlush(outfit);
        return summary(outfit, outfits.countItems(outfitId));
    }

    @Transactional
    public void delete(UUID ownerId, UUID outfitId) {
        Outfit outfit = owned(ownerId, outfitId);
        outfits.delete(outfit); // outfit_item rows are removed by ON DELETE CASCADE
        outfits.flush();
    }

    public OutfitDetailResponse addItem(UUID ownerId, UUID outfitId, UUID variantId) {
        owned(ownerId, outfitId);
        List<OutfitItem> current = outfitItems.findByOutfitIdOrderByDisplayOrderAscOutfitItemIdAsc(outfitId);
        requireRoomAndNoDuplicate(current, variantId); // cheap pre-check; repeated under the row lock below
        Set<UUID> lookupIds = new LinkedHashSet<>(variantIds(current));
        lookupIds.add(variantId);
        Map<UUID, VariantLookupItem> catalogItems = catalog.lookup(lookupIds);
        requirePurchasable(catalogItems, variantId);
        return transaction.execute(status -> {
            Outfit outfit = lockedOwned(ownerId, outfitId);
            List<OutfitItem> fresh = new ArrayList<>(
                    outfitItems.findByOutfitIdOrderByDisplayOrderAscOutfitItemIdAsc(outfitId));
            requireRoomAndNoDuplicate(fresh, variantId);
            OffsetDateTime now = now();
            OutfitItem added = newItem(outfitId, variantId, outfitItems.maxDisplayOrder(outfitId) + 1, now);
            outfitItems.saveAndFlush(added);
            outfit.setUpdatedAt(now);
            outfits.saveAndFlush(outfit);
            fresh.add(added);
            return detail(outfit, fresh, catalogItems);
        });
    }

    public OutfitDetailResponse changeItemVariant(UUID ownerId, UUID outfitId, UUID outfitItemId, UUID newVariantId) {
        owned(ownerId, outfitId);
        List<OutfitItem> current = outfitItems.findByOutfitIdOrderByDisplayOrderAscOutfitItemIdAsc(outfitId);
        if (current.stream().noneMatch(row -> row.getOutfitItemId().equals(outfitItemId))) {
            throw notFound();
        }
        Set<UUID> lookupIds = new LinkedHashSet<>(variantIds(current));
        lookupIds.add(newVariantId);
        Map<UUID, VariantLookupItem> catalogItems = catalog.lookup(lookupIds);
        return transaction.execute(status -> {
            Outfit outfit = lockedOwned(ownerId, outfitId);
            List<OutfitItem> fresh = outfitItems.findByOutfitIdOrderByDisplayOrderAscOutfitItemIdAsc(outfitId);
            OutfitItem row = fresh.stream().filter(candidate -> candidate.getOutfitItemId().equals(outfitItemId))
                    .findFirst().orElseThrow(OutfitService::notFound);
            if (row.getVariantId().equals(newVariantId)) {
                return detail(outfit, fresh, catalogItems);
            }
            // Check order: current variant gone, new variant not purchasable, different product, duplicate.
            VariantLookupItem existing = catalogItems.get(row.getVariantId());
            if (existing == null) {
                throw conflict("OUTFIT_ITEM_UNAVAILABLE", "Item variant is no longer available; remove it and add a new one");
            }
            VariantLookupItem replacement = requirePurchasable(catalogItems, newVariantId);
            if (!existing.productId().equals(replacement.productId())) {
                throw new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                        "Request is missing or malformed");
            }
            if (fresh.stream().anyMatch(other -> other.getVariantId().equals(newVariantId))) {
                throw conflict("OUTFIT_ITEM_DUPLICATE", "Variant is already in the outfit");
            }
            row.setVariantId(newVariantId);
            outfitItems.saveAndFlush(row);
            outfit.setUpdatedAt(now());
            outfits.saveAndFlush(outfit);
            return detail(outfit, fresh, catalogItems);
        });
    }

    @Transactional
    public void removeItem(UUID ownerId, UUID outfitId, UUID outfitItemId) {
        Outfit outfit = lockedOwned(ownerId, outfitId);
        OutfitItem row = outfitItems.findByOutfitItemIdAndOutfitId(outfitItemId, outfitId)
                .orElseThrow(OutfitService::notFound);
        if (outfitItems.findByOutfitIdOrderByDisplayOrderAscOutfitItemIdAsc(outfitId).size() <= 1) {
            throw conflict("OUTFIT_LAST_ITEM", "An outfit must keep at least one item");
        }
        outfitItems.delete(row);
        outfit.setUpdatedAt(now());
        outfits.saveAndFlush(outfit);
    }

    private Outfit owned(UUID ownerId, UUID outfitId) {
        return outfits.findByOutfitIdAndOwnerAccountId(outfitId, ownerId).orElseThrow(OutfitService::notFound);
    }

    private Outfit lockedOwned(UUID ownerId, UUID outfitId) {
        return outfits.findLockedByOutfitIdAndOwnerAccountId(outfitId, ownerId).orElseThrow(OutfitService::notFound);
    }

    private void requireRoomAndNoDuplicate(List<OutfitItem> current, UUID variantId) {
        if (current.size() >= MAX_ITEMS_PER_OUTFIT) {
            throw conflict("OUTFIT_ITEM_LIMIT_REACHED", "Outfit item limit has been reached");
        }
        if (current.stream().anyMatch(row -> row.getVariantId().equals(variantId))) {
            throw conflict("OUTFIT_ITEM_DUPLICATE", "Variant is already in the outfit");
        }
    }

    /** Same status and code Cart uses when a variant is missing or inactive. */
    private VariantLookupItem requirePurchasable(Map<UUID, VariantLookupItem> catalogItems, UUID variantId) {
        VariantLookupItem item = catalogItems.get(variantId);
        if (item == null || !item.purchasable()) {
            throw conflict("VARIANT_NOT_ACTIVE", "Variant does not exist or is inactive");
        }
        return item;
    }

    private OutfitItem newItem(UUID outfitId, UUID variantId, int displayOrder, OffsetDateTime now) {
        OutfitItem item = new OutfitItem();
        item.setOutfitItemId(UUID.randomUUID());
        item.setOutfitId(outfitId);
        item.setVariantId(variantId);
        item.setDisplayOrder(displayOrder);
        item.setCreatedAt(now);
        return item;
    }

    private List<UUID> variantIds(List<OutfitItem> rows) {
        return rows.stream().map(OutfitItem::getVariantId).toList();
    }

    private OutfitSummaryResponse summary(Outfit outfit, long itemCount) {
        return new OutfitSummaryResponse(outfit.getOutfitId(), outfit.getName(), outfit.getCreationSource(),
                itemCount, outfit.getCreatedAt(), outfit.getUpdatedAt());
    }

    private OutfitDetailResponse detail(Outfit outfit, List<OutfitItem> rows, Map<UUID, VariantLookupItem> catalogItems) {
        List<OutfitItemResponse> items = rows.stream()
                .sorted(java.util.Comparator.comparingInt(OutfitItem::getDisplayOrder)
                        .thenComparing(OutfitItem::getOutfitItemId))
                .map(row -> itemResponse(row, catalogItems.get(row.getVariantId())))
                .toList();
        return new OutfitDetailResponse(outfit.getOutfitId(), outfit.getName(), outfit.getCreationSource(),
                outfit.getCreatedAt(), outfit.getUpdatedAt(), items, outfit.getPublicToken());
    }

    /** A variant Commerce no longer returns keeps its row: product fields are null and available is false. */
    private OutfitItemResponse itemResponse(OutfitItem row, VariantLookupItem variant) {
        if (variant == null) {
            return new OutfitItemResponse(row.getOutfitItemId(), row.getVariantId(), row.getDisplayOrder(),
                    null, null, null, null, null, null, false);
        }
        return new OutfitItemResponse(row.getOutfitItemId(), row.getVariantId(), row.getDisplayOrder(),
                variant.productId(), variant.productName(), variant.color(), variant.size(),
                variant.unitPriceVnd(), variant.primaryImageUrl(), variant.purchasable());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private static ApiOperationException conflict(String code, String detail) {
        return new ApiOperationException(HttpStatus.CONFLICT, code, detail);
    }
}
