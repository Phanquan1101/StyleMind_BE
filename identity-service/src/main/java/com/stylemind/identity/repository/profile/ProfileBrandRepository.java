package com.stylemind.identity.repository.profile;

import com.stylemind.identity.entity.ProfileBrand;
import com.stylemind.identity.entity.ProfileBrandId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileBrandRepository extends JpaRepository<ProfileBrand, ProfileBrandId> {
    List<ProfileBrand> findByIdAccountIdOrderByIdBrandIdAsc(UUID accountId);
    long deleteByIdAccountId(UUID accountId);
}
