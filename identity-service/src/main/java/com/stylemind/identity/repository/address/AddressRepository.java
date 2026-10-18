package com.stylemind.identity.repository.address;

import com.stylemind.identity.model.address.AddressSnapshot;
import com.stylemind.identity.model.address.AddressValues;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class AddressRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AddressRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean lockAccount(UUID accountId) {
        return jdbc.query("SELECT account_id FROM public.account WHERE account_id = :accountId FOR UPDATE",
                new MapSqlParameterSource("accountId", accountId),
                (ResultSetExtractor<Boolean>) rs -> rs.next());
    }

    public boolean accountExists(UUID accountId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM public.account WHERE account_id = :id)",
                new MapSqlParameterSource("id", accountId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public List<AddressSnapshot> findAll(UUID accountId) {
        return jdbc.query("""
                SELECT address_id, label, recipient_name, recipient_phone, address_line, ward_code, is_default
                FROM public.customer_address WHERE account_id = :accountId
                ORDER BY is_default DESC, address_id ASC
                """, new MapSqlParameterSource("accountId", accountId), (rs, row) -> map(rs));
    }

    public Optional<AddressSnapshot> findOne(UUID accountId, UUID addressId) {
        return jdbc.query("""
                SELECT address_id, label, recipient_name, recipient_phone, address_line, ward_code, is_default
                FROM public.customer_address WHERE account_id = :accountId AND address_id = :addressId
                """, new MapSqlParameterSource().addValue("accountId", accountId).addValue("addressId", addressId),
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty());
    }

    public void insert(UUID addressId, UUID accountId, AddressValues values, boolean isDefault) {
        jdbc.update("""
                INSERT INTO public.customer_address
                    (address_id, account_id, label, recipient_name, recipient_phone, address_line, ward_code, is_default)
                VALUES (:addressId, :accountId, :label, :name, :phone, :line, :ward, :isDefault)
                """, new MapSqlParameterSource()
                .addValue("addressId", addressId).addValue("accountId", accountId).addValue("label", values.label())
                .addValue("name", values.recipientName()).addValue("phone", values.recipientPhone())
                .addValue("line", values.addressLine()).addValue("ward", values.wardCode())
                .addValue("isDefault", isDefault));
    }

    public void update(UUID accountId, UUID addressId, AddressValues values) {
        int changed = jdbc.update("""
                UPDATE public.customer_address SET label = :label, recipient_name = :name,
                    recipient_phone = :phone, address_line = :line, ward_code = :ward
                WHERE account_id = :accountId AND address_id = :addressId
                """, new MapSqlParameterSource().addValue("accountId", accountId).addValue("addressId", addressId)
                .addValue("label", values.label()).addValue("name", values.recipientName())
                .addValue("phone", values.recipientPhone()).addValue("line", values.addressLine())
                .addValue("ward", values.wardCode()));
        if (changed != 1) throw new IllegalStateException("Locked address disappeared during update");
    }

    public void clearDefaults(UUID accountId) {
        jdbc.update("UPDATE public.customer_address SET is_default = false WHERE account_id = :accountId",
                new MapSqlParameterSource("accountId", accountId));
    }

    public void setDefault(UUID accountId, UUID addressId) {
        int changed = jdbc.update("""
                UPDATE public.customer_address SET is_default = true
                WHERE account_id = :accountId AND address_id = :addressId
                """, new MapSqlParameterSource().addValue("accountId", accountId).addValue("addressId", addressId));
        if (changed != 1) throw new IllegalStateException("Locked address disappeared during default change");
    }

    public void delete(UUID accountId, UUID addressId) {
        int changed = jdbc.update("DELETE FROM public.customer_address WHERE account_id = :accountId AND address_id = :addressId",
                new MapSqlParameterSource().addValue("accountId", accountId).addValue("addressId", addressId));
        if (changed != 1) throw new IllegalStateException("Locked address disappeared during delete");
    }

    private static AddressSnapshot map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AddressSnapshot(rs.getObject("address_id", UUID.class), rs.getString("label"),
                rs.getString("recipient_name"), rs.getString("recipient_phone"), rs.getString("address_line"),
                rs.getString("ward_code"), rs.getBoolean("is_default"));
    }
}
