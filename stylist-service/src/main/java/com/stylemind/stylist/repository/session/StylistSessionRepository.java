package com.stylemind.stylist.repository.session;

import com.stylemind.stylist.entity.session.StylistSessionEntity;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StylistSessionRepository extends JpaRepository<StylistSessionEntity, UUID> {
    Optional<StylistSessionEntity> findByStylistSessionIdAndCustomerId(UUID stylistSessionId, UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StylistSessionEntity s where s.stylistSessionId = :sessionId "
            + "and s.customerId = :customerId")
    Optional<StylistSessionEntity> findOwnedForUpdate(
            @Param("sessionId") UUID sessionId, @Param("customerId") UUID customerId);
}
