package com.stylemind.identity.repository;

import com.stylemind.identity.entity.ProfileBrand;
import com.stylemind.identity.entity.ProfileBrandId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileBrandRepository extends JpaRepository<ProfileBrand, ProfileBrandId> {
    List<ProfileBrand> findByIdAccountIdOrderByIdBrandIdAsc(UUID accountId);
    long deleteByIdAccountId(UUID accountId);
}
