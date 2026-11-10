package com.stylemind.stylist.repository.constraint;

import com.stylemind.stylist.entity.constraint.StylingConstraintEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StylingConstraintRepository extends JpaRepository<StylingConstraintEntity, UUID> {
    List<StylingConstraintEntity> findAllByStylingRequestIdOrderByConstraintId(UUID stylingRequestId);
}
