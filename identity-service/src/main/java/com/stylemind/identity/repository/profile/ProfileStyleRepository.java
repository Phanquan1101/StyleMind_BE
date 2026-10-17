package com.stylemind.identity.repository.profile;

import com.stylemind.identity.entity.ProfileStyle;
import com.stylemind.identity.entity.ProfileStyleId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileStyleRepository extends JpaRepository<ProfileStyle, ProfileStyleId> {
    List<ProfileStyle> findByIdAccountIdOrderByIdStyleCodeAsc(UUID accountId);
    long deleteByIdAccountId(UUID accountId);
}
