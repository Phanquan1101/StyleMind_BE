package com.stylemind.wardrobecommunity.service.outfit;

import com.stylemind.wardrobecommunity.client.commerce.CommerceCatalogClient;
import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupItem;
import com.stylemind.wardrobecommunity.client.identity.IdentityDirectoryClient;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupItem;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitItemResponse;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitListCriteria;
import com.stylemind.wardrobecommunity.dto.outfit.SharedOutfitListResponse;
import com.stylemind.wardrobecommunity.dto.outfit.SharedOutfitSummaryResponse;
import com.stylemind.wardrobecommunity.dto.outfit.SharedOutfitViewResponse;
import com.stylemind.wardrobecommunity.entity.Outfit;
import com.stylemind.wardrobecommunity.entity.OutfitItem;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.repository.outfit.OutfitItemRepository;
import com.stylemind.wardrobecommunity.repository.outfit.OutfitRepository;
import com.stylemind.wardrobecommunity.repository.outfit.OutfitShareRepository;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Viewer-side reads: outfits shared with the caller email and the public link. No call here holds a transaction. */
@Service
@RequiredArgsConstructor
public class SharedOutfitService {

    private static final int MAX_PUBLIC_TOKEN_LENGTH = 128;

    private final OutfitRepository outfits;
    private final OutfitItemRepository outfitItems;
    private final OutfitShareRepository shares;
    private final IdentityDirectoryClient identity;
    private final CommerceCatalogClient catalog;

    /** Never calls Commerce. */
    public SharedOutfitListResponse list(UUID callerId, OutfitListCriteria criteria) {
        String email = callerEmail(callerId);
        long total = email == null ? 0 : shares.countByRecipientEmailAndRevokedAtIsNull(email);
        long offset = (long) criteria.page() * criteria.size();
        List<Object[]> rows = email == null || offset > Integer.MAX_VALUE ? List.of()
                : shares.findSharedWithPage(email, PageRequest.of(criteria.page(), criteria.size()));
        Set<UUID> ownerIds = new LinkedHashSet<>();
        rows.forEach(row -> ownerIds.add(((Outfit) row[0]).getOwnerAccountId()));
        Map<UUID, AccountLookupItem> owners = identity.lookup(ownerIds);
        List<SharedOutfitSummaryResponse> items = rows.stream().map(row -> {
            Outfit outfit = (Outfit) row[0];
            AccountLookupItem owner = owners.get(outfit.getOwnerAccountId());
            return new SharedOutfitSummaryResponse(outfit.getOutfitId(), outfit.getName(),
                    owner == null ? null : owner.displayName(), (Long) row[2], (OffsetDateTime) row[1]);
        }).toList();
        long totalPages = total == 0 ? 0 : (total + criteria.size() - 1) / criteria.size();
        return new SharedOutfitListResponse(items, criteria.page(), criteria.size(), total, totalPages);
    }

    public SharedOutfitViewResponse view(UUID callerId, UUID outfitId) {
        String email = callerEmail(callerId);
        if (email == null || shares.findByOutfitIdAndRecipientEmailAndRevokedAtIsNull(outfitId, email).isEmpty()) {
            throw notFound();
        }
        return build(outfits.findById(outfitId).orElseThrow(SharedOutfitService::notFound));
    }

    public SharedOutfitViewResponse publicView(String publicToken) {
        if (publicToken == null || publicToken.isBlank() || publicToken.length() > MAX_PUBLIC_TOKEN_LENGTH) {
            throw notFound();
        }
        return build(outfits.findByPublicToken(publicToken).orElseThrow(SharedOutfitService::notFound));
    }

    private SharedOutfitViewResponse build(Outfit outfit) {
        List<OutfitItem> rows = outfitItems.findByOutfitIdOrderByDisplayOrderAscOutfitItemIdAsc(outfit.getOutfitId());
        AccountLookupItem owner = identity.lookup(List.of(outfit.getOwnerAccountId())).get(outfit.getOwnerAccountId());
        Map<UUID, VariantLookupItem> catalogItems = catalog.lookup(rows.stream().map(OutfitItem::getVariantId).toList());
        List<OutfitItemResponse> items = rows.stream()
                .sorted(Comparator.comparingInt(OutfitItem::getDisplayOrder).thenComparing(OutfitItem::getOutfitItemId))
                .map(row -> itemResponse(row, catalogItems.get(row.getVariantId())))
                .toList();
        return new SharedOutfitViewResponse(outfit.getOutfitId(), outfit.getName(),
                owner == null ? null : owner.displayName(), items);
    }

    /** The caller email from Identity, or null when Identity does not know the account. */
    private String callerEmail(UUID callerId) {
        AccountLookupItem caller = identity.lookup(List.of(callerId)).get(callerId);
        return caller == null ? null : caller.email().strip().toLowerCase(Locale.ROOT);
    }

    /** Same shape and rule as the owner detail: a variant Commerce no longer returns is kept as unavailable. */
    private OutfitItemResponse itemResponse(OutfitItem row, VariantLookupItem variant) {
        if (variant == null) {
            return new OutfitItemResponse(row.getOutfitItemId(), row.getVariantId(), row.getDisplayOrder(),
                    null, null, null, null, null, null, false);
        }
        return new OutfitItemResponse(row.getOutfitItemId(), row.getVariantId(), row.getDisplayOrder(),
                variant.productId(), variant.productName(), variant.color(), variant.size(),
                variant.unitPriceVnd(), variant.primaryImageUrl(), variant.purchasable());
    }

    private static ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }
}
