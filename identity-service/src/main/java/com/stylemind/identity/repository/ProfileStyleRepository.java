package com.stylemind.identity.repository;

import com.stylemind.identity.entity.ProfileStyle;
import com.stylemind.identity.entity.ProfileStyleId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileStyleRepository extends JpaRepository<ProfileStyle, ProfileStyleId> {
    List<ProfileStyle> findByIdAccountIdOrderByIdStyleCodeAsc(UUID accountId);
    long deleteByIdAccountId(UUID accountId);
}
