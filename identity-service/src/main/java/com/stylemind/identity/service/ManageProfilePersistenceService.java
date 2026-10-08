package com.stylemind.identity.service;

import com.stylemind.identity.dto.MyProfileResponse;
import com.stylemind.identity.dto.StyleProfileResponse;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.model.MyProfileSnapshot;
import com.stylemind.identity.model.StyleProfileCommand;
import com.stylemind.identity.model.StyleProfileSnapshot;
import com.stylemind.identity.repository.ProfileRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageProfilePersistenceService {

    private final ProfileRepository profileRepository;

    public ManageProfilePersistenceService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MyProfileResponse getMyProfile(UUID accountId) {
        MyProfileSnapshot snapshot = profileRepository.findMyProfile(accountId).orElseThrow(this::notFound);
        MyProfileResponse.Profile profile = snapshot.profileExists()
                ? new MyProfileResponse.Profile(snapshot.displayName()) : null;
        return new MyProfileResponse(snapshot.accountId(), snapshot.email(), profile);
    }

    @Transactional
    public MyProfileResponse updateMyProfile(UUID accountId, String displayName) {
        requireLockedOwner(accountId);
        profileRepository.upsertDisplayName(accountId, displayName);
        MyProfileSnapshot snapshot = profileRepository.findMyProfile(accountId).orElseThrow(this::notFound);
        return new MyProfileResponse(snapshot.accountId(), snapshot.email(),
                new MyProfileResponse.Profile(snapshot.displayName()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StyleProfileResponse getStyleProfile(UUID accountId) {
        requireAccount(accountId);
        StyleProfileSnapshot snapshot = profileRepository.findStyleProfile(accountId)
                .orElseGet(StyleProfileSnapshot::absent);
        return response(snapshot);
    }

    @Transactional
    public StyleProfileResponse replaceStyleProfile(UUID accountId, StyleProfileCommand command) {
        requireLockedOwner(accountId);
        profileRepository.upsertStyleProfile(accountId, command);
        profileRepository.replaceStyleCodes(accountId, command.styleCodes());
        profileRepository.replaceColorCodes(accountId, command.colorCodes());
        profileRepository.replaceBrandIds(accountId, command.brandIds());
        StyleProfileSnapshot snapshot = profileRepository.findStyleProfile(accountId).orElseThrow(this::notFound);
        return response(snapshot);
    }

    private StyleProfileResponse response(StyleProfileSnapshot snapshot) {
        if (!snapshot.profileExists()) {
            return new StyleProfileResponse(null);
        }
        return new StyleProfileResponse(new StyleProfileResponse.StyleProfile(
                snapshot.fitPreference(), snapshot.heightCm(), snapshot.usualTopSize(), snapshot.usualBottomSize(),
                snapshot.shoeSize(), snapshot.styleCodes(), snapshot.colorCodes(), snapshot.brandIds()));
    }

    private void requireLockedOwner(UUID accountId) {
        if (!profileRepository.lockOwnerAccount(accountId)) {
            throw notFound();
        }
    }

    private void requireAccount(UUID accountId) {
        if (!profileRepository.accountExists(accountId)) {
            throw notFound();
        }
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found", 0);
    }
}
