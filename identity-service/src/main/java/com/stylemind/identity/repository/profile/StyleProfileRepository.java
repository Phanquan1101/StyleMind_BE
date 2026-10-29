package com.stylemind.identity.repository.profile;

import com.stylemind.identity.entity.StyleProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StyleProfileRepository extends JpaRepository<StyleProfile, UUID> {
}
