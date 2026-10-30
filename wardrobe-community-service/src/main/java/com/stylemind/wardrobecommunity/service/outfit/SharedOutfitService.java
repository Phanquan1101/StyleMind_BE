package com.stylemind.wardrobecommunity.service.outfit;

import com.stylemind.wardrobecommunity.dto.outfit.*;

import java.util.UUID;

public interface SharedOutfitService {
    FriendOutfitListResponse friendsList(UUID actor, UUID friend, OutfitListCriteria criteria);

    SharedOutfitViewResponse friendView(UUID actor, UUID friend, UUID outfit);

    SharedOutfitViewResponse publicView(String token);

    @Deprecated
    SharedOutfitListResponse list(UUID actor, OutfitListCriteria criteria);

    @Deprecated
    SharedOutfitViewResponse view(UUID actor, UUID outfit);
}
