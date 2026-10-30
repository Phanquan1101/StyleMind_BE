package com.stylemind.wardrobecommunity.service.outfit.internal;

import static com.stylemind.wardrobecommunity.service.outfit.internal.OutfitSharingAccessPolicy.*;

import com.stylemind.wardrobecommunity.dto.outfit.OutfitListCriteria;
import com.stylemind.wardrobecommunity.entity.Outfit;
import com.stylemind.wardrobecommunity.model.outfit.*;
import com.stylemind.wardrobecommunity.repository.friendship.FriendshipRepository;
import com.stylemind.wardrobecommunity.repository.outfit.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutfitSharingReadService {
    private final OutfitRepository outfits;
    private final OutfitItemRepository items;
    private final FriendshipRepository friends;

    @Transactional(readOnly = true)
    public void requireFriend(UUID actor, UUID peer) {
        if (actor.equals(peer) || friends.findActivePairForAccount(actor, peer).isEmpty())
            throw notFound();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FriendOutfitPageSnapshot list(UUID actor, UUID peer, OutfitListCriteria criteria) {
        requireFriend(actor, peer);
        long total = outfits.countFriendVisible(peer);
        List<FriendOutfitPageSnapshot.Entry> page =
                (long) criteria.page() * criteria.size() > Integer.MAX_VALUE
                        ? List.of()
                        : outfits
                                .findFriendVisiblePage(
                                        peer, PageRequest.of(criteria.page(), criteria.size()))
                                .stream()
                                .map(
                                        row -> {
                                            var outfit = (Outfit) row[0];
                                            return new FriendOutfitPageSnapshot.Entry(
                                                    outfit.getOutfitId(),
                                                    outfit.getName(),
                                                    (Long) row[1],
                                                    outfit.getVisibility(),
                                                    outfit.getUpdatedAt());
                                        })
                                .toList();
        return new FriendOutfitPageSnapshot(page, total);
    }

    @Transactional(readOnly = true)
    public OutfitViewerSnapshot friendSnapshot(UUID actor, UUID peer, UUID id) {
        return snapshot(friendOutfit(actor, peer, id));
    }

    @Transactional(readOnly = true)
    public void checkFriendOutfit(UUID actor, UUID peer, UUID id) {
        friendOutfit(actor, peer, id);
    }

    private Outfit friendOutfit(UUID actor, UUID peer, UUID id) {
        requireFriend(actor, peer);
        return outfits.findByOutfitIdAndOwnerAccountId(id, peer)
                .filter(o -> friendVisible(o.getVisibility()))
                .orElseThrow(OutfitSharingAccessPolicy::notFound);
    }

    @Transactional(readOnly = true)
    public OutfitViewerSnapshot publicSnapshot(String token) {
        return snapshot(publicOutfit(token));
    }

    @Transactional(readOnly = true)
    public void checkPublic(UUID id, String token) {
        if (!publicOutfit(token).getOutfitId().equals(id)) throw notFound();
    }

    private Outfit publicOutfit(String token) {
        if (token == null || token.isBlank() || token.length() > 128) throw notFound();
        return outfits.findByPublicToken(token)
                .filter(o -> "PUBLIC".equals(o.getVisibility()) && o.getPublicEnabledAt() != null)
                .orElseThrow(OutfitSharingAccessPolicy::notFound);
    }

    private OutfitViewerSnapshot snapshot(Outfit outfit) {
        return new OutfitViewerSnapshot(
                outfit.getOutfitId(),
                outfit.getOwnerAccountId(),
                outfit.getName(),
                items
                        .findByOutfitIdOrderByDisplayOrderAscOutfitItemIdAsc(outfit.getOutfitId())
                        .stream()
                        .map(
                                item ->
                                        new OutfitViewerSnapshot.Item(
                                                item.getOutfitItemId(),
                                                item.getVariantId(),
                                                item.getDisplayOrder()))
                        .toList());
    }
}
