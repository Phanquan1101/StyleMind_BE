package com.stylemind.identity.repository;

import com.stylemind.identity.entity.UserProfile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
