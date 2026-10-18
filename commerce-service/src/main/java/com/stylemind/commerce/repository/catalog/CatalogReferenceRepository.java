package com.stylemind.commerce.repository.catalog;

import com.stylemind.commerce.model.catalog.CatalogReference;
import com.stylemind.commerce.model.catalog.CatalogReferenceCommand;
import com.stylemind.commerce.model.catalog.CatalogReferenceType;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CatalogReferenceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public List<CatalogReference> list(CatalogReferenceType type) {
        return jdbc.query("""
                SELECT %1$s_id AS id, name, slug, is_active
                FROM public.%1$s
                ORDER BY name ASC, %1$s_id ASC
                """.formatted(table(type)), new MapSqlParameterSource(), (rs, row) ->
                new CatalogReference(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("slug"), rs.getBoolean("is_active")));
    }

    public Optional<CatalogReference> find(CatalogReferenceType type, UUID id) {
        return jdbc.query("""
                SELECT %1$s_id AS id, name, slug, is_active
                FROM public.%1$s
                WHERE %1$s_id = :id
                """.formatted(table(type)), new MapSqlParameterSource("id", id), rs -> rs.next()
                ? Optional.of(new CatalogReference(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("slug"), rs.getBoolean("is_active")))
                : Optional.empty());
    }

    public CatalogReference insert(CatalogReferenceType type, UUID id, CatalogReferenceCommand command) {
        try {
            int rows = jdbc.update("""
                    INSERT INTO public.%1$s (%1$s_id, name, slug, is_active)
                    VALUES (:id, :name, :slug, :active)
                    """.formatted(table(type)), parameters(id, command));
            if (rows != 1) {
                throw new IllegalStateException("Catalog reference insert did not affect one row");
            }
            return find(type, id).orElseThrow();
        } catch (DuplicateKeyException exception) {
            throw exception;
        }
    }

    public Optional<CatalogReference> update(CatalogReferenceType type, UUID id, CatalogReferenceCommand command) {
        try {
            int rows = jdbc.update("""
                    UPDATE public.%1$s
                    SET name = :name, slug = :slug, is_active = :active
                    WHERE %1$s_id = :id
                    """.formatted(table(type)), parameters(id, command));
            return rows == 0 ? Optional.empty() : find(type, id);
        } catch (DuplicateKeyException exception) {
            throw exception;
        }
    }

    public boolean exists(CatalogReferenceType type, UUID id) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public.%1$s WHERE %1$s_id = :id)
                """.formatted(table(type)), new MapSqlParameterSource("id", id), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public boolean isReferencedByProduct(CatalogReferenceType type, UUID id) {
        String column = type == CatalogReferenceType.CATEGORY ? "category_id" : "brand_id";
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public.product WHERE %s = :id)
                """.formatted(column), new MapSqlParameterSource("id", id), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public int delete(CatalogReferenceType type, UUID id) {
        return jdbc.update("""
                DELETE FROM public.%1$s WHERE %1$s_id = :id
                """.formatted(table(type)), new MapSqlParameterSource("id", id));
    }

    private MapSqlParameterSource parameters(UUID id, CatalogReferenceCommand command) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", command.name(), Types.VARCHAR)
                .addValue("slug", command.slug(), Types.VARCHAR)
                .addValue("active", command.active(), Types.BOOLEAN);
    }

    private String table(CatalogReferenceType type) {
        return switch (type) {
            case CATEGORY -> "category";
            case BRAND -> "brand";
        };
    }
}
