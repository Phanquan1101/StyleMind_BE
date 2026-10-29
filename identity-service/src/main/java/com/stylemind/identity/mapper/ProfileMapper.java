package com.stylemind.identity.mapper;

import com.stylemind.identity.dto.profile.MyProfileResponse;
import com.stylemind.identity.dto.profile.StyleProfileResponse;
import com.stylemind.identity.dto.profile.StyleProfileUpdateRequest;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.ProfileBrand;
import com.stylemind.identity.entity.ProfileColor;
import com.stylemind.identity.entity.ProfileStyle;
import com.stylemind.identity.entity.StyleProfile;
import com.stylemind.identity.entity.UserProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProfileMapper {
    public UserProfile newUserProfile(UUID accountId) {
        UserProfile profile = new UserProfile();
        profile.setAccountId(accountId);
        return profile;
    }

    public StyleProfile newStyleProfile(UUID accountId) {
        StyleProfile profile = new StyleProfile();
        profile.setAccountId(accountId);
        return profile;
    }

    public void updateUserProfile(UserProfile target, String displayName) {
        target.setDisplayName(displayName);
    }

    public void updateStyleProfile(StyleProfile target, StyleProfileUpdateRequest request) {
        target.setFitPreference(request.fitPreference());
        target.setHeightCm(request.heightCm());
        target.setWeightKg(request.weightKg());
        target.setUsualTopSize(request.usualTopSize());
        target.setUsualBottomSize(request.usualBottomSize());
        target.setShoeSize(request.shoeSize());
    }

    public List<ProfileStyle> styles(UUID accountId, List<String> codes) {
        return codes.stream().map(code -> new ProfileStyle(accountId, code)).toList();
    }

    public List<ProfileColor> colors(UUID accountId, List<String> codes) {
        return codes.stream().map(code -> new ProfileColor(accountId, code)).toList();
    }

    public List<ProfileBrand> brands(UUID accountId, List<UUID> ids) {
        return ids.stream().map(id -> new ProfileBrand(accountId, id)).toList();
    }

    public MyProfileResponse myProfile(Account account, UserProfile profile) {
        return new MyProfileResponse(account.getAccountId(), account.getEmail(), profile == null ? null
                : new MyProfileResponse.Profile(profile.getDisplayName()));
    }

    public StyleProfileResponse styleProfile(StyleProfile profile, List<String> styles,
            List<String> colors, List<UUID> brands) {
        if (profile == null) return new StyleProfileResponse(null);
        return new StyleProfileResponse(new StyleProfileResponse.StyleProfile(profile.getFitPreference(),
                profile.getHeightCm(), profile.getWeightKg(), profile.getUsualTopSize(),
                profile.getUsualBottomSize(), profile.getShoeSize(), styles, colors, brands));
    }
}
