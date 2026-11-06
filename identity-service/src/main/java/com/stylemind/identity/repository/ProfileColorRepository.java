package com.stylemind.identity.repository;

import com.stylemind.identity.entity.ProfileColor;
import com.stylemind.identity.entity.ProfileColorId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileColorRepository extends JpaRepository<ProfileColor, ProfileColorId> {
    List<ProfileColor> findByIdAccountIdOrderByIdColorCodeAsc(UUID accountId);
    long deleteByIdAccountId(UUID accountId);
}
