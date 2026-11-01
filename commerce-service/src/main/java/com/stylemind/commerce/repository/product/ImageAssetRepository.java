package com.stylemind.commerce.repository.product;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ImageAssetRepository {
    private final JdbcClient jdbc;

    public ImageAssetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID assetId, String imageUrl, OffsetDateTime createdAt) {
        jdbc.sql("""
                INSERT INTO public.image_asset(image_asset_id,image_url,created_at)
                VALUES (:id,:url,:created)
                """).param("id", assetId).param("url", imageUrl).param("created", createdAt).update();
    }

    public Map<UUID, String> urls(Collection<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT image_asset_id,image_url FROM public.image_asset
                WHERE image_asset_id IN (:ids)
                """).param("ids", assetIds).query((rs, row) -> {
                    result.put(rs.getObject("image_asset_id", UUID.class), rs.getString("image_url"));
                    return true;
                }).list();
        return result;
    }

    public void delete(UUID assetId) {
        jdbc.sql("DELETE FROM public.image_asset WHERE image_asset_id=:id")
                .param("id", assetId).update();
    }
}
