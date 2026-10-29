package com.stylemind.identity.service.profile;

import com.stylemind.identity.dto.profile.MyProfileResponse;
import com.stylemind.identity.dto.profile.PatchMyProfileRequest;
import com.stylemind.identity.dto.profile.ReplaceStyleProfileRequest;
import com.stylemind.identity.dto.profile.StyleProfileResponse;
import java.util.UUID;

public interface ManageProfileService {
    MyProfileResponse getMyProfile(UUID accountId);
    MyProfileResponse patchMyProfile(UUID accountId, PatchMyProfileRequest request);
    StyleProfileResponse getStyleProfile(UUID accountId);
    StyleProfileResponse replaceStyleProfile(UUID accountId, ReplaceStyleProfileRequest request);
}
