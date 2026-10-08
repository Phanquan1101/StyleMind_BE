package com.stylemind.identity.repository;

import com.stylemind.identity.model.MyProfileSnapshot;
import com.stylemind.identity.model.StyleProfileCommand;
import com.stylemind.identity.model.StyleProfileSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ProfileRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<MyProfileSnapshot> findMyProfile(UUID accountId) {
        return jdbc.query("""
                SELECT a.account_id, a.email, up.account_id AS profile_account_id, up.display_name
                FROM public.account a
                LEFT JOIN public.user_profile up ON up.account_id = a.account_id
                WHERE a.account_id = :accountId
                """, new MapSqlParameterSource("accountId", accountId), resultSet ->
                resultSet.next() ? Optional.of(new MyProfileSnapshot(
                        resultSet.getObject("account_id", UUID.class),
                        resultSet.getString("email"),
                        resultSet.getObject("profile_account_id") != null,
                        resultSet.getString("display_name"))) : Optional.empty());
    }

    public boolean accountExists(UUID accountId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public.account WHERE account_id = :accountId)
                """, new MapSqlParameterSource("accountId", accountId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public Optional<StyleProfileSnapshot> findStyleProfile(UUID accountId) {
        Optional<StyleProfileSnapshot> scalar = jdbc.query("""
                SELECT fit_preference, height_cm, usual_top_size, usual_bottom_size, shoe_size
                FROM public.style_profile
                WHERE account_id = :accountId
                """, new MapSqlParameterSource("accountId", accountId), resultSet ->
                resultSet.next() ? Optional.of(new StyleProfileSnapshot(
                        true,
                        resultSet.getString("fit_preference"),
                        resultSet.getBigDecimal("height_cm"),
                        resultSet.getString("usual_top_size"),
                        resultSet.getString("usual_bottom_size"),
                        resultSet.getString("shoe_size"),
                        List.of(), List.of(), List.of())) : Optional.empty());
        if (scalar.isEmpty()) {
            return Optional.empty();
        }

        return scalar.map(value -> new StyleProfileSnapshot(true,
                value.fitPreference(), value.heightCm(), value.usualTopSize(), value.usualBottomSize(),
                value.shoeSize(), findStyleCodes(accountId), findColorCodes(accountId), findBrandIds(accountId)));
    }

    public boolean lockOwnerAccount(UUID accountId) {
        return jdbc.query("""
                SELECT account_id FROM public.account
                WHERE account_id = :accountId
                FOR UPDATE
                """, new MapSqlParameterSource("accountId", accountId),
                (ResultSetExtractor<Boolean>) resultSet -> resultSet.next());
    }

    public void upsertDisplayName(UUID accountId, String displayName) {
        jdbc.update("""
                INSERT INTO public.user_profile (account_id, display_name)
                VALUES (:accountId, :displayName)
                ON CONFLICT (account_id) DO UPDATE SET display_name = EXCLUDED.display_name
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("displayName", displayName));
    }

    public void upsertStyleProfile(UUID accountId, StyleProfileCommand command) {
        jdbc.update("""
                INSERT INTO public.style_profile (
                    account_id, fit_preference, height_cm, usual_top_size, usual_bottom_size, shoe_size
                ) VALUES (
                    :accountId, :fitPreference, :heightCm, :usualTopSize, :usualBottomSize, :shoeSize
                )
                ON CONFLICT (account_id) DO UPDATE SET
                    fit_preference = EXCLUDED.fit_preference,
                    height_cm = EXCLUDED.height_cm,
                    usual_top_size = EXCLUDED.usual_top_size,
                    usual_bottom_size = EXCLUDED.usual_bottom_size,
                    shoe_size = EXCLUDED.shoe_size
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("fitPreference", command.fitPreference())
                .addValue("heightCm", command.heightCm())
                .addValue("usualTopSize", command.usualTopSize())
                .addValue("usualBottomSize", command.usualBottomSize())
                .addValue("shoeSize", command.shoeSize()));
    }

    public void replaceStyleCodes(UUID accountId, List<String> codes) {
        jdbc.update("DELETE FROM public.profile_style WHERE account_id = :accountId",
                new MapSqlParameterSource("accountId", accountId));
        batchInsert("INSERT INTO public.profile_style (account_id, style_code) VALUES (:accountId, :code)",
                accountId, codes);
    }

    public void replaceColorCodes(UUID accountId, List<String> codes) {
        jdbc.update("DELETE FROM public.profile_color WHERE account_id = :accountId",
                new MapSqlParameterSource("accountId", accountId));
        batchInsert("INSERT INTO public.profile_color (account_id, color_code) VALUES (:accountId, :code)",
                accountId, codes);
    }

    public void replaceBrandIds(UUID accountId, List<UUID> brandIds) {
        jdbc.update("DELETE FROM public.profile_brand WHERE account_id = :accountId",
                new MapSqlParameterSource("accountId", accountId));
        if (brandIds.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = brandIds.stream()
                .map(id -> new MapSqlParameterSource().addValue("accountId", accountId).addValue("brandId", id))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT INTO public.profile_brand (account_id, brand_id) VALUES (:accountId, :brandId)",
                batch);
    }

    private void batchInsert(String sql, UUID accountId, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = values.stream()
                .map(value -> new MapSqlParameterSource().addValue("accountId", accountId).addValue("code", value))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(sql, batch);
    }

    private List<String> findStyleCodes(UUID accountId) {
        return jdbc.query("""
                SELECT style_code FROM public.profile_style
                WHERE account_id = :accountId ORDER BY style_code
                """, new MapSqlParameterSource("accountId", accountId),
                (resultSet, rowNumber) -> resultSet.getString("style_code"));
    }

    private List<String> findColorCodes(UUID accountId) {
        return jdbc.query("""
                SELECT color_code FROM public.profile_color
                WHERE account_id = :accountId ORDER BY color_code
                """, new MapSqlParameterSource("accountId", accountId),
                (resultSet, rowNumber) -> resultSet.getString("color_code"));
    }

    private List<UUID> findBrandIds(UUID accountId) {
        return jdbc.query("""
                SELECT brand_id FROM public.profile_brand
                WHERE account_id = :accountId ORDER BY brand_id
                """, new MapSqlParameterSource("accountId", accountId),
                (resultSet, rowNumber) -> resultSet.getObject("brand_id", UUID.class));
    }
}
