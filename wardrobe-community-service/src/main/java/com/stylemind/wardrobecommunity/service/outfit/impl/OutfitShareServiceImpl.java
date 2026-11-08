package com.stylemind.wardrobecommunity.service.outfit.impl;

import com.stylemind.wardrobecommunity.dto.outfit.*;
import com.stylemind.wardrobecommunity.service.outfit.OutfitShareService;
import com.stylemind.wardrobecommunity.service.outfit.internal.OutfitSharingPersistenceService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutfitShareServiceImpl implements OutfitShareService {
    private final OutfitSharingPersistenceService persistence;

    @Override
    public OutfitSharingResponse settings(UUID owner, UUID outfit) {
        return persistence.settings(owner, outfit);
    }

    @Override
    public OutfitSharingResponse changeVisibility(UUID owner, UUID outfit, String mode) {
        return persistence.change(owner, outfit, mode);
    }

    @Override
    public PublicLinkResponse enablePublicLink(UUID owner, UUID outfit) {
        var settings = persistence.change(owner, outfit, "PUBLIC");
        return new PublicLinkResponse(settings.publicToken(), settings.publicEnabledAt());
    }

    @Override
    public void disablePublicLink(UUID owner, UUID outfit) {
        persistence.disablePublic(owner, outfit);
    }

    @Override
    @Deprecated
    public ShareResult share(UUID owner, UUID outfit, String email) {
        persistence.settings(owner, outfit);
        throw com.stylemind.wardrobecommunity.service.outfit.internal.OutfitSharingAccessPolicy
                .retired();
    }

    @Override
    @Deprecated
    public ShareListResponse list(UUID owner, UUID outfit) {
        persistence.settings(owner, outfit);
        throw com.stylemind.wardrobecommunity.service.outfit.internal.OutfitSharingAccessPolicy
                .retired();
    }

    @Override
    @Deprecated
    public void revoke(UUID owner, UUID outfit, UUID share) {
        persistence.settings(owner, outfit);
        throw com.stylemind.wardrobecommunity.service.outfit.internal.OutfitSharingAccessPolicy
                .retired();
    }
}
