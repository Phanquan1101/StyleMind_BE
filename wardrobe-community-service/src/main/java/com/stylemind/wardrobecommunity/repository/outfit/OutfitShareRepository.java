package com.stylemind.wardrobecommunity.repository.outfit;

import com.stylemind.wardrobecommunity.entity.OutfitShare;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutfitShareRepository extends JpaRepository<OutfitShare, UUID> {
    Optional<OutfitShare> findByOutfitIdAndRecipientEmail(UUID outfitId, String recipientEmail);

    Optional<OutfitShare> findByOutfitShareIdAndOutfitIdAndRevokedAtIsNull(UUID outfitShareId, UUID outfitId);

    Optional<OutfitShare> findByOutfitIdAndRecipientEmailAndRevokedAtIsNull(UUID outfitId, String recipientEmail);

    List<OutfitShare> findByOutfitIdAndRevokedAtIsNullOrderByGrantedAtDescOutfitShareIdDesc(UUID outfitId);

    long countByOutfitIdAndRevokedAtIsNull(UUID outfitId);

    long countByRecipientEmailAndRevokedAtIsNull(String recipientEmail);

    /** Shares the owner granted (including later revoked ones) since the given instant, over all of the owner's outfits. */
    @Query("""
            select count(s) from OutfitShare s join Outfit o on o.outfitId = s.outfitId
            where o.ownerAccountId = :ownerId and s.grantedAt >= :since
            """)
    long countGrantedSince(@Param("ownerId") UUID ownerId, @Param("since") OffsetDateTime since);

    /** Rows of (outfit, granted_at, item_count) for outfits actively shared with the email, newest grant first. */
    @Query("""
            select o, s.grantedAt, (select count(i) from OutfitItem i where i.outfitId = o.outfitId)
            from OutfitShare s join Outfit o on o.outfitId = s.outfitId
            where s.recipientEmail = :email and s.revokedAt is null
            order by s.grantedAt desc, s.outfitShareId desc
            """)
    List<Object[]> findSharedWithPage(@Param("email") String email, Pageable pageable);
}
