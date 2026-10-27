package com.stylemind.wardrobecommunity.repository.outfit;

import com.stylemind.wardrobecommunity.entity.OutfitItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutfitItemRepository extends JpaRepository<OutfitItem, UUID> {
    List<OutfitItem> findByOutfitIdOrderByDisplayOrderAscOutfitItemIdAsc(UUID outfitId);

    Optional<OutfitItem> findByOutfitItemIdAndOutfitId(UUID outfitItemId, UUID outfitId);

    @Query("select coalesce(max(i.displayOrder), -1) from OutfitItem i where i.outfitId = :outfitId")
    int maxDisplayOrder(@Param("outfitId") UUID outfitId);
}
