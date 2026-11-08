package com.stylemind.wardrobecommunity.repository.friendship;

import com.stylemind.wardrobecommunity.entity.Friendship;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    Optional<Friendship> findByAccountLowIdAndAccountHighId(UUID accountLowId, UUID accountHighId);

    /** Canonicalize a pair containing the authenticated actor before calling this write lookup. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Friendship> findLockedByAccountLowIdAndAccountHighId(UUID accountLowId, UUID accountHighId);

    @Query("""
            select f from Friendship f
            where (f.accountLowId = :actorId or f.accountHighId = :actorId) and f.unfriendedAt is null
            order by f.establishedAt desc, f.friendshipId desc
            """)
    List<Friendship> findActiveForAccount(@Param("actorId") UUID actorId, Pageable pageable);

    @Query("""
            select count(f) from Friendship f
            where (f.accountLowId = :actorId or f.accountHighId = :actorId) and f.unfriendedAt is null
            """)
    long countActiveForAccount(@Param("actorId") UUID actorId);

    @Query("""
            select f from Friendship f
            where ((f.accountLowId = :actorId and f.accountHighId = :peerId)
                or (f.accountHighId = :actorId and f.accountLowId = :peerId))
                and f.unfriendedAt is null
            """)
    Optional<Friendship> findActivePairForAccount(@Param("actorId") UUID actorId, @Param("peerId") UUID peerId);
}
