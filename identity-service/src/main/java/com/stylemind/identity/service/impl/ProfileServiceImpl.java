package com.stylemind.identity.service.impl;

import com.stylemind.identity.dto.profile.MyProfileResponse;
import com.stylemind.identity.dto.profile.StyleProfileResponse;
import com.stylemind.identity.dto.profile.StyleProfileUpdateRequest;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.StyleProfile;
import com.stylemind.identity.entity.UserProfile;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.ErrorCode;
import com.stylemind.identity.mapper.ProfileMapper;
import com.stylemind.identity.repository.AccountRepository;
import com.stylemind.identity.repository.ProfileBrandRepository;
import com.stylemind.identity.repository.ProfileColorRepository;
import com.stylemind.identity.repository.ProfileStyleRepository;
import com.stylemind.identity.repository.StyleProfileRepository;
import com.stylemind.identity.repository.UserProfileRepository;
import com.stylemind.identity.service.ProfileService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final AccountRepository accountRepository;
    private final UserProfileRepository userProfileRepository;
    private final StyleProfileRepository styleProfileRepository;
    private final ProfileStyleRepository profileStyleRepository;
    private final ProfileColorRepository profileColorRepository;
    private final ProfileBrandRepository profileBrandRepository;
    private final ProfileMapper mapper;

    public ProfileServiceImpl(AccountRepository accountRepository,
            UserProfileRepository userProfileRepository,
            StyleProfileRepository styleProfileRepository,
            ProfileStyleRepository profileStyleRepository,
            ProfileColorRepository profileColorRepository,
            ProfileBrandRepository profileBrandRepository,
            ProfileMapper mapper) {
        this.accountRepository = accountRepository;
        this.userProfileRepository = userProfileRepository;
        this.styleProfileRepository = styleProfileRepository;
        this.profileStyleRepository = profileStyleRepository;
        this.profileColorRepository = profileColorRepository;
        this.profileBrandRepository = profileBrandRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MyProfileResponse getMyProfile(UUID accountId) {
        return readMyProfile(accountId);
    }

    @Transactional
    public MyProfileResponse patchMyProfile(UUID accountId, String displayName) {
        lockOwner(accountId);
        UserProfile profile = userProfileRepository.findById(accountId).orElseGet(() -> mapper.newUserProfile(accountId));
        mapper.updateUserProfile(profile, displayName);
        userProfileRepository.save(profile);
        return readMyProfile(accountId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StyleProfileResponse getStyleProfile(UUID accountId) {
        requireAccount(accountId);
        return readStyleProfile(accountId);
    }

    @Transactional
    public StyleProfileResponse replaceStyleProfile(UUID accountId, StyleProfileUpdateRequest request) {
        lockOwner(accountId);
        StyleProfile profile = styleProfileRepository.findById(accountId).orElseGet(() -> mapper.newStyleProfile(accountId));
        mapper.updateStyleProfile(profile, request);
        styleProfileRepository.save(profile);

        profileStyleRepository.deleteByIdAccountId(accountId);
        profileColorRepository.deleteByIdAccountId(accountId);
        profileBrandRepository.deleteByIdAccountId(accountId);
        profileStyleRepository.saveAll(mapper.styles(accountId, request.styleCodes()));
        profileColorRepository.saveAll(mapper.colors(accountId, request.colorCodes()));
        profileBrandRepository.saveAll(mapper.brands(accountId, request.brandIds()));
        return readStyleProfile(accountId);
    }

    private MyProfileResponse readMyProfile(UUID accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow(() -> new ApiOperationException(ErrorCode.RESOURCE_NOT_FOUND));
        UserProfile profile = userProfileRepository.findById(accountId).orElse(null);
        return mapper.myProfile(account, profile);
    }

    private StyleProfileResponse readStyleProfile(UUID accountId) {
        StyleProfile profile = styleProfileRepository.findById(accountId).orElse(null);
        return mapper.styleProfile(profile,
                profile == null ? java.util.List.of() : profileStyleRepository.findByIdAccountIdOrderByIdStyleCodeAsc(accountId).stream()
                        .map(row -> row.getId().getStyleCode()).toList(),
                profile == null ? java.util.List.of() : profileColorRepository.findByIdAccountIdOrderByIdColorCodeAsc(accountId).stream()
                        .map(row -> row.getId().getColorCode()).toList(),
                profile == null ? java.util.List.of() : profileBrandRepository.findByIdAccountIdOrderByIdBrandIdAsc(accountId).stream()
                        .map(row -> row.getId().getBrandId()).toList());
    }

    private void lockOwner(UUID accountId) {
        if (accountRepository.findLockedByAccountId(accountId).isEmpty()) {
            throw new ApiOperationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireAccount(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new ApiOperationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

}
