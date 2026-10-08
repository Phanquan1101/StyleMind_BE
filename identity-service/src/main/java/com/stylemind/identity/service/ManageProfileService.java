package com.stylemind.identity.service;

import com.stylemind.identity.dto.MyProfileResponse;
import com.stylemind.identity.dto.PatchMyProfileRequest;
import com.stylemind.identity.dto.ReplaceStyleProfileRequest;
import com.stylemind.identity.dto.StyleProfileResponse;
import com.stylemind.identity.model.StyleProfileCommand;
import com.stylemind.identity.validation.ProfileRequestValidator;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ManageProfileService {

    private final ProfileRequestValidator validator;
    private final ManageProfilePersistenceService persistenceService;

    public ManageProfileService(ProfileRequestValidator validator,
            ManageProfilePersistenceService persistenceService) {
        this.validator = validator;
        this.persistenceService = persistenceService;
    }

    public MyProfileResponse getMyProfile(UUID accountId) {
        return persistenceService.getMyProfile(accountId);
    }

    public MyProfileResponse patchMyProfile(UUID accountId, PatchMyProfileRequest request) {
        String displayName = validator.validatePatch(request);
        return persistenceService.updateMyProfile(accountId, displayName);
    }

    public StyleProfileResponse getStyleProfile(UUID accountId) {
        return persistenceService.getStyleProfile(accountId);
    }

    public StyleProfileResponse replaceStyleProfile(UUID accountId, ReplaceStyleProfileRequest request) {
        StyleProfileCommand command = validator.validateReplacement(request);
        return persistenceService.replaceStyleProfile(accountId, command);
    }
}
