package com.stylemind.wardrobecommunity.service.outfit;

import com.stylemind.wardrobecommunity.dto.outfit.*;

import java.util.UUID;

public interface OutfitShareService {
    OutfitSharingResponse settings(UUID owner, UUID outfit);

    OutfitSharingResponse changeVisibility(UUID owner, UUID outfit, String visibility);

    PublicLinkResponse enablePublicLink(UUID owner, UUID outfit);

    void disablePublicLink(UUID owner, UUID outfit);

    /**
     * Retired contract. Preserve the Java adapter while preventing any old email grant side effect.
     */
    record ShareResult(ShareResponse share, boolean created) {}

    @Deprecated
    ShareResult share(UUID owner, UUID outfit, String email);

    @Deprecated
    ShareListResponse list(UUID owner, UUID outfit);

    @Deprecated
    void revoke(UUID owner, UUID outfit, UUID share);
}
