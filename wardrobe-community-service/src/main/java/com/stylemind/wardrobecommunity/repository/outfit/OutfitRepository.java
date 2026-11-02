package com.stylemind.wardrobecommunity.repository.outfit;

import com.stylemind.wardrobecommunity.entity.Outfit;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutfitRepository extends JpaRepository<Outfit, UUID> {
    Optional<Outfit> findByOutfitIdAndOwnerAccountId(UUID outfitId, UUID ownerAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Outfit> findLockedByOutfitIdAndOwnerAccountId(UUID outfitId, UUID ownerAccountId);

    long countByOwnerAccountId(UUID ownerAccountId);

    Optional<Outfit> findByPublicToken(String publicToken);

    /** Rows of (outfit, item_count) for one page, newest update first. */
    @Query("""
            select o, (select count(i) from OutfitItem i where i.outfitId = o.outfitId)
            from Outfit o
            where o.ownerAccountId = :ownerId
            order by o.updatedAt desc, o.outfitId desc
            """)
    List<Object[]> findPageWithItemCount(@Param("ownerId") UUID ownerId, Pageable pageable);

    @Query("select count(i) from OutfitItem i where i.outfitId = :outfitId")
    long countItems(@Param("outfitId") UUID outfitId);
}
