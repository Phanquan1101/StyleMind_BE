package com.stylemind.identity.service.profile.internal;

import com.stylemind.identity.dto.profile.MyProfileResponse;
import com.stylemind.identity.dto.profile.StyleProfileResponse;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.ProfileBrand;
import com.stylemind.identity.entity.ProfileColor;
import com.stylemind.identity.entity.ProfileStyle;
import com.stylemind.identity.entity.StyleProfile;
import com.stylemind.identity.entity.UserProfile;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.model.profile.MyProfileSnapshot;
import com.stylemind.identity.model.profile.StyleProfileCommand;
import com.stylemind.identity.model.profile.StyleProfileSnapshot;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.profile.ProfileBrandRepository;
import com.stylemind.identity.repository.profile.ProfileColorRepository;
import com.stylemind.identity.repository.profile.ProfileStyleRepository;
import com.stylemind.identity.repository.profile.StyleProfileRepository;
import com.stylemind.identity.repository.profile.UserProfileRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageProfilePersistenceService {

    private final AccountRepository accountRepository;
    private final UserProfileRepository userProfileRepository;
    private final StyleProfileRepository styleProfileRepository;
    private final ProfileStyleRepository profileStyleRepository;
    private final ProfileColorRepository profileColorRepository;
    private final ProfileBrandRepository profileBrandRepository;

    public ManageProfilePersistenceService(AccountRepository accountRepository,
            UserProfileRepository userProfileRepository,
            StyleProfileRepository styleProfileRepository,
            ProfileStyleRepository profileStyleRepository,
            ProfileColorRepository profileColorRepository,
            ProfileBrandRepository profileBrandRepository) {
        this.accountRepository = accountRepository;
        this.userProfileRepository = userProfileRepository;
        this.styleProfileRepository = styleProfileRepository;
        this.profileStyleRepository = profileStyleRepository;
        this.profileColorRepository = profileColorRepository;
        this.profileBrandRepository = profileBrandRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MyProfileResponse getMyProfile(UUID accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow(this::notFound);
        UserProfile profile = userProfileRepository.findById(accountId).orElse(null);
        return new MyProfileResponse(account.getAccountId(), account.getEmail(), profile == null ? null
                : new MyProfileResponse.Profile(profile.getDisplayName()));
    }

    @Transactional
    public MyProfileResponse updateMyProfile(UUID accountId, String displayName) {
        requireLockedOwner(accountId);
        UserProfile profile = userProfileRepository.findById(accountId).orElseGet(() -> {
            UserProfile created = new UserProfile();
            created.setAccountId(accountId);
            return created;
        });
        profile.setDisplayName(displayName);
        userProfileRepository.saveAndFlush(profile);
        return getMyProfile(accountId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StyleProfileResponse getStyleProfile(UUID accountId) {
        requireAccount(accountId);
        return response(readStyleSnapshot(accountId));
    }

    @Transactional
    public StyleProfileResponse replaceStyleProfile(UUID accountId, StyleProfileCommand command) {
        requireLockedOwner(accountId);
        StyleProfile profile = styleProfileRepository.findById(accountId).orElseGet(() -> {
            StyleProfile created = new StyleProfile();
            created.setAccountId(accountId);
            return created;
        });
        profile.setFitPreference(command.fitPreference());
        profile.setHeightCm(command.heightCm());
        profile.setWeightKg(command.weightKg());
        profile.setUsualTopSize(command.usualTopSize());
        profile.setUsualBottomSize(command.usualBottomSize());
        profile.setShoeSize(command.shoeSize());
        styleProfileRepository.saveAndFlush(profile);

        profileStyleRepository.deleteByIdAccountId(accountId);
        profileColorRepository.deleteByIdAccountId(accountId);
        profileBrandRepository.deleteByIdAccountId(accountId);
        profileStyleRepository.saveAll(command.styleCodes().stream()
                .map(code -> new ProfileStyle(accountId, code)).toList());
        profileColorRepository.saveAll(command.colorCodes().stream()
                .map(code -> new ProfileColor(accountId, code)).toList());
        profileBrandRepository.saveAll(command.brandIds().stream()
                .map(brandId -> new ProfileBrand(accountId, brandId)).toList());
        profileStyleRepository.flush();
        profileColorRepository.flush();
        profileBrandRepository.flush();
        return response(readStyleSnapshot(accountId));
    }

    private StyleProfileSnapshot readStyleSnapshot(UUID accountId) {
        StyleProfile profile = styleProfileRepository.findById(accountId).orElse(null);
        if (profile == null) {
            return StyleProfileSnapshot.absent();
        }
        return new StyleProfileSnapshot(true, profile.getFitPreference(), profile.getHeightCm(), profile.getWeightKg(),
                profile.getUsualTopSize(), profile.getUsualBottomSize(), profile.getShoeSize(),
                profileStyleRepository.findByIdAccountIdOrderByIdStyleCodeAsc(accountId).stream()
                        .map(row -> row.getId().getStyleCode()).toList(),
                profileColorRepository.findByIdAccountIdOrderByIdColorCodeAsc(accountId).stream()
                        .map(row -> row.getId().getColorCode()).toList(),
                profileBrandRepository.findByIdAccountIdOrderByIdBrandIdAsc(accountId).stream()
                        .map(row -> row.getId().getBrandId()).toList());
    }

    private StyleProfileResponse response(StyleProfileSnapshot snapshot) {
        if (!snapshot.profileExists()) {
            return new StyleProfileResponse(null);
        }
        return new StyleProfileResponse(new StyleProfileResponse.StyleProfile(
                snapshot.fitPreference(), snapshot.heightCm(), snapshot.weightKg(), snapshot.usualTopSize(),
                snapshot.usualBottomSize(),
                snapshot.shoeSize(), snapshot.styleCodes(), snapshot.colorCodes(), snapshot.brandIds()));
    }

    private void requireLockedOwner(UUID accountId) {
        if (accountRepository.findLockedByAccountId(accountId).isEmpty()) {
            throw notFound();
        }
    }

    private void requireAccount(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw notFound();
        }
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found", 0);
    }
}
