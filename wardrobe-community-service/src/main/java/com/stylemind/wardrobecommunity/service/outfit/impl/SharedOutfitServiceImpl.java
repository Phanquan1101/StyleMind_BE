package com.stylemind.wardrobecommunity.service.outfit.impl;

import com.stylemind.wardrobecommunity.client.commerce.CommerceCatalogClient;
import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupItem;
import com.stylemind.wardrobecommunity.client.identity.IdentityDirectoryClient;
import com.stylemind.wardrobecommunity.dto.outfit.*;
import com.stylemind.wardrobecommunity.model.outfit.OutfitViewerSnapshot;
import com.stylemind.wardrobecommunity.service.outfit.SharedOutfitService;
import com.stylemind.wardrobecommunity.service.outfit.internal.OutfitSharingReadService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SharedOutfitServiceImpl implements SharedOutfitService {
    private final OutfitSharingReadService persistence;
    private final IdentityDirectoryClient identity;
    private final CommerceCatalogClient catalog;

    @Override
    @Deprecated
    public SharedOutfitListResponse list(UUID actor, OutfitListCriteria criteria) {
        throw com.stylemind.wardrobecommunity.service.outfit.internal.OutfitSharingAccessPolicy
                .retired();
    }

    @Override
    @Deprecated
    public SharedOutfitViewResponse view(UUID actor, UUID outfit) {
        throw com.stylemind.wardrobecommunity.service.outfit.internal.OutfitSharingAccessPolicy
                .retired();
    }

    @Override
    public FriendOutfitListResponse friendsList(
            UUID actor, UUID peer, OutfitListCriteria criteria) {
        persistence.requireFriend(actor, peer);
        String display = name(peer);
        var page =
                persistence.list(
                        actor, peer,
                        criteria); // Current authorization and count/page after external I/O.
        return new FriendOutfitListResponse(
                page.items().stream()
                        .map(
                                o ->
                                        new FriendOutfitSummaryResponse(
                                                o.outfitId(),
                                                o.name(),
                                                display,
                                                o.itemCount(),
                                                o.visibility(),
                                                o.updatedAt()))
                        .toList(),
                criteria.page(),
                criteria.size(),
                page.total(),
                page.total() == 0 ? 0 : (page.total() + criteria.size() - 1) / criteria.size());
    }

    @Override
    public SharedOutfitViewResponse friendView(UUID actor, UUID peer, UUID outfit) {
        var snapshot = persistence.friendSnapshot(actor, peer, outfit);
        var response = project(snapshot);
        persistence.checkFriendOutfit(actor, peer, outfit);
        return response;
    }

    @Override
    public SharedOutfitViewResponse publicView(String token) {
        var snapshot = persistence.publicSnapshot(token);
        var response = project(snapshot);
        persistence.checkPublic(snapshot.outfitId(), token);
        return response;
    }

    private String name(UUID owner) {
        var account = identity.lookup(List.of(owner)).get(owner);
        return account == null ? null : account.displayName();
    }

    private SharedOutfitViewResponse project(OutfitViewerSnapshot snapshot) {
        String display = name(snapshot.ownerId());
        var products =
                catalog.lookup(
                        snapshot.items().stream()
                                .map(OutfitViewerSnapshot.Item::variantId)
                                .toList());
        return new SharedOutfitViewResponse(
                snapshot.outfitId(),
                snapshot.name(),
                display,
                snapshot.items().stream()
                        .map(item -> item(item, products.get(item.variantId())))
                        .toList());
    }

    private OutfitItemResponse item(OutfitViewerSnapshot.Item item, VariantLookupItem variant) {
        if (variant == null)
            return new OutfitItemResponse(
                    item.itemId(),
                    item.variantId(),
                    item.order(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false);
        return new OutfitItemResponse(
                item.itemId(),
                item.variantId(),
                item.order(),
                variant.productId(),
                variant.productName(),
                variant.color(),
                variant.size(),
                variant.unitPriceVnd(),
                variant.primaryImageUrl(),
                variant.purchasable());
    }
}
