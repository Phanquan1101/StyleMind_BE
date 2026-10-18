package com.stylemind.identity.repository.profile;

import com.stylemind.identity.entity.ProfileColor;
import com.stylemind.identity.entity.ProfileColorId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileColorRepository extends JpaRepository<ProfileColor, ProfileColorId> {
    List<ProfileColor> findByIdAccountIdOrderByIdColorCodeAsc(UUID accountId);
    long deleteByIdAccountId(UUID accountId);
}
