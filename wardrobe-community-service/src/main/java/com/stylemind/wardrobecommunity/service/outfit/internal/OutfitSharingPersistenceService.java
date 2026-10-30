package com.stylemind.wardrobecommunity.service.outfit.internal;

import static com.stylemind.wardrobecommunity.service.outfit.internal.OutfitSharingAccessPolicy.*;

import com.stylemind.wardrobecommunity.dto.outfit.OutfitSharingResponse;
import com.stylemind.wardrobecommunity.entity.Outfit;
import com.stylemind.wardrobecommunity.repository.outfit.OutfitRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Service
public class OutfitSharingPersistenceService {
    private final OutfitRepository outfits;
    private final SecureRandom random = new SecureRandom();

    public OutfitSharingPersistenceService(OutfitRepository outfits) {
        this.outfits = outfits;
    }

    @Transactional(readOnly = true)
    public OutfitSharingResponse settings(UUID owner, UUID id) {
        return response(
                outfits.findByOutfitIdAndOwnerAccountId(id, owner)
                        .orElseThrow(OutfitSharingAccessPolicy::notFound));
    }

    @Transactional
    public OutfitSharingResponse change(UUID owner, UUID id, String mode) {
        if (!Set.of("PRIVATE", "FRIENDS", "PUBLIC").contains(mode))
            throw new IllegalArgumentException("Invalid trusted visibility");
        Outfit row =
                outfits.findLockedByOutfitIdAndOwnerAccountId(id, owner)
                        .orElseThrow(OutfitSharingAccessPolicy::notFound);
        return change(row, mode);
    }

    @Transactional
    public void disablePublic(UUID owner, UUID id) {
        Outfit row =
                outfits.findLockedByOutfitIdAndOwnerAccountId(id, owner)
                        .orElseThrow(OutfitSharingAccessPolicy::notFound);
        if ("PUBLIC".equals(row.getVisibility())) change(row, "PRIVATE");
    }

    private OutfitSharingResponse change(Outfit row, String mode) {
        if (mode.equals(row.getVisibility())) return response(row);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (now.isBefore(row.getUpdatedAt())) now = row.getUpdatedAt();
        if ("PUBLIC".equals(mode)) {
            if (row.getPublicToken() == null) {
                byte[] bytes = new byte[32];
                random.nextBytes(bytes);
                row.setPublicToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            }
            row.setPublicEnabledAt(now);
        } else row.setPublicEnabledAt(null);
        row.setVisibility(mode);
        row.setUpdatedAt(now);
        outfits.saveAndFlush(row);
        return response(row);
    }

    private OutfitSharingResponse response(Outfit row) {
        return new OutfitSharingResponse(
                row.getOutfitId(),
                row.getVisibility(),
                "PUBLIC".equals(row.getVisibility()),
                row.getPublicToken(),
                row.getPublicEnabledAt());
    }
}
