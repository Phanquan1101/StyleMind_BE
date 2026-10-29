package com.stylemind.identity.repository;

import com.stylemind.identity.entity.StyleProfile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StyleProfileRepository extends JpaRepository<StyleProfile, UUID> {
}
