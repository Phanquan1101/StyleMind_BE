package com.stylemind.identity.service;

import com.stylemind.identity.dto.profile.MyProfileResponse;
import com.stylemind.identity.dto.profile.StyleProfileResponse;
import com.stylemind.identity.dto.profile.StyleProfileUpdateRequest;
import java.util.UUID;

public interface ProfileService {
    MyProfileResponse getMyProfile(UUID accountId);
    MyProfileResponse patchMyProfile(UUID accountId, String displayName);
    StyleProfileResponse getStyleProfile(UUID accountId);
    StyleProfileResponse replaceStyleProfile(UUID accountId, StyleProfileUpdateRequest request);
}
