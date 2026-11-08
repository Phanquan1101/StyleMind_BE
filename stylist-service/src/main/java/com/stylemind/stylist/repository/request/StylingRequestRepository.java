package com.stylemind.stylist.repository.request;

import com.stylemind.stylist.entity.request.StylingRequestEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StylingRequestRepository extends JpaRepository<StylingRequestEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StylingRequestEntity r where r.stylingRequestId = :requestId and exists "
            + "(select s.stylistSessionId from StylistSessionEntity s where "
            + "s.stylistSessionId = r.stylistSessionId and s.customerId = :customerId)")
    Optional<StylingRequestEntity> findOwnedForUpdate(
            @Param("requestId") UUID requestId, @Param("customerId") UUID customerId);

    @Query(value = """
            SELECT r.*
            FROM public.styling_request r
            JOIN public.stylist_session s ON s.stylist_session_id = r.stylist_session_id
            WHERE r.styling_request_id = :requestId AND s.customer_id = :customerId
            """, nativeQuery = true)
    Optional<StylingRequestEntity> findByRequestIdAndOwner(
            @Param("requestId") UUID requestId, @Param("customerId") UUID customerId);

    @Query(value = """
            SELECT r.stylist_session_id
            FROM public.styling_request r
            JOIN public.stylist_session s ON s.stylist_session_id = r.stylist_session_id
            WHERE r.styling_request_id = :requestId AND s.customer_id = :customerId
            """, nativeQuery = true)
    Optional<UUID> findOwnedSessionId(@Param("requestId") UUID requestId, @Param("customerId") UUID customerId);
}
