package com.stylemind.identity.repository.users;

import com.stylemind.identity.model.users.AdminManagedAccountState;
import com.stylemind.identity.model.users.ManageUserListCriteria;
import com.stylemind.identity.model.users.ManagedUserPageData;
import com.stylemind.identity.model.users.ManagedUserSummary;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ManageUsersRepository {

    private static final int ADMIN_LOCK_NAMESPACE = 1_397_248_846;
    private static final int ADMIN_LOCK_KEY = 207;
    private final NamedParameterJdbcTemplate jdbc;

    public ManageUsersRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AdminManagedAccountState> lockActorForRead(UUID actorId) {
        return jdbc.query("""
                SELECT a.account_id, a.account_status, a.email_verified_at, a.registered_at
                FROM public.account a
                WHERE a.account_id = :accountId
                FOR SHARE OF a
                """, new MapSqlParameterSource("accountId", actorId), rs -> rs.next()
                ? Optional.of(new AdminManagedAccountState(rs.getObject("account_id", UUID.class),
                        rs.getString("account_status"), rs.getObject("email_verified_at", OffsetDateTime.class),
                        rs.getObject("registered_at", OffsetDateTime.class)))
                : Optional.empty());
    }

    public void lockAdminListing() {
        jdbc.getJdbcTemplate().execute("SELECT pg_advisory_xact_lock_shared(" + ADMIN_LOCK_NAMESPACE + ", "
                + ADMIN_LOCK_KEY + ")");
    }

    public void lockAdminMutations() {
        jdbc.getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(" + ADMIN_LOCK_NAMESPACE + ", "
                + ADMIN_LOCK_KEY + ")");
    }

    public Map<UUID, AdminManagedAccountState> lockAccountsForMutation(Collection<UUID> accountIds) {
        List<UUID> sortedIds = accountIds.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        if (sortedIds.isEmpty()) {
            return Map.of();
        }
        List<AdminManagedAccountState> rows = jdbc.query("""
                SELECT account_id, account_status, email_verified_at, registered_at
                FROM public.account
                WHERE account_id IN (:accountIds)
                ORDER BY account_id
                FOR UPDATE
                """, new MapSqlParameterSource("accountIds", sortedIds), (rs, row) ->
                new AdminManagedAccountState(rs.getObject("account_id", UUID.class),
                        rs.getString("account_status"), rs.getObject("email_verified_at", OffsetDateTime.class),
                        rs.getObject("registered_at", OffsetDateTime.class)));
        Map<UUID, AdminManagedAccountState> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(row.accountId(), row));
        return result;
    }

    public List<String> roles(UUID accountId) {
        return jdbc.query("""
                SELECT role_code FROM public.account_role
                WHERE account_id = :accountId ORDER BY role_code
                """, new MapSqlParameterSource("accountId", accountId),
                (rs, row) -> rs.getString("role_code"));
    }

    public long countQualifyingAdmins() {
        Long count = jdbc.queryForObject("""
                SELECT count(*)
                FROM public.account a
                WHERE a.account_status = 'ACTIVE'
                  AND a.email_verified_at IS NOT NULL
                  AND EXISTS (SELECT 1 FROM public.account_role r
                              WHERE r.account_id = a.account_id AND r.role_code = 'ADMIN')
                """, new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }

    public ManagedUserPageData userPage(ManageUserListCriteria criteria) {
        return jdbc.query("""
                WITH filtered AS MATERIALIZED (
                    SELECT a.account_id, a.email, a.account_status, a.email_verified_at, a.registered_at
                    FROM public.account a
                    WHERE (:status IS NULL OR a.account_status = :status)
                      AND (:emailPattern IS NULL OR lower(a.email) LIKE :emailPattern ESCAPE '\\')
                      AND (:role IS NULL OR EXISTS (
                          SELECT 1 FROM public.account_role filter_role
                          WHERE filter_role.account_id = a.account_id AND filter_role.role_code = :role
                      ))
                ),
                page AS MATERIALIZED (
                    SELECT * FROM filtered
                    ORDER BY registered_at DESC, account_id ASC
                    LIMIT :size OFFSET :offset
                ),
                total AS (
                    SELECT count(*) AS total_elements FROM filtered
                )
                SELECT total.total_elements, page.account_id, page.email, page.account_status,
                       page.email_verified_at, page.registered_at,
                       COALESCE(string_agg(role.role_code, ',' ORDER BY role.role_code), '') AS roles_csv
                FROM total
                LEFT JOIN page ON true
                LEFT JOIN public.account_role role ON role.account_id = page.account_id
                GROUP BY total.total_elements, page.account_id, page.email, page.account_status,
                         page.email_verified_at, page.registered_at
                ORDER BY page.registered_at DESC NULLS LAST, page.account_id ASC NULLS LAST
                """, listParameters(criteria)
                .addValue("size", criteria.size(), Types.INTEGER)
                .addValue("offset", criteria.offset(), Types.BIGINT), rs -> {
                    long totalElements = 0;
                    List<ManagedUserSummary> items = new ArrayList<>();
                    while (rs.next()) {
                        totalElements = rs.getLong("total_elements");
                        UUID accountId = rs.getObject("account_id", UUID.class);
                        if (accountId != null) {
                            String rolesCsv = rs.getString("roles_csv");
                            List<String> roles = rolesCsv == null || rolesCsv.isEmpty()
                                    ? List.of() : List.of(rolesCsv.split(","));
                            items.add(new ManagedUserSummary(accountId, rs.getString("email"),
                                    rs.getString("account_status"),
                                    rs.getObject("email_verified_at", OffsetDateTime.class),
                                    rs.getObject("registered_at", OffsetDateTime.class), roles));
                        }
                    }
                    return new ManagedUserPageData(totalElements, items);
                });
    }

    public int updateStatus(UUID accountId, String status) {
        return jdbc.update("""
                UPDATE public.account SET account_status = :status
                WHERE account_id = :accountId
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("status", status, Types.VARCHAR));
    }

    public int deleteRoles(UUID accountId) {
        return jdbc.update("DELETE FROM public.account_role WHERE account_id = :accountId",
                new MapSqlParameterSource("accountId", accountId));
    }

    public int insertRole(UUID accountId, String role) {
        return jdbc.update("INSERT INTO public.account_role (account_id, role_code) VALUES (:accountId, :role)",
                new MapSqlParameterSource().addValue("accountId", accountId).addValue("role", role));
    }

    private MapSqlParameterSource listParameters(ManageUserListCriteria criteria) {
        String emailPattern = criteria.emailContains() == null
                ? null : "%" + escapeLike(criteria.emailContains().toLowerCase(java.util.Locale.ROOT)) + "%";
        return new MapSqlParameterSource()
                .addValue("status", criteria.status(), Types.VARCHAR)
                .addValue("role", criteria.role(), Types.VARCHAR)
                .addValue("emailPattern", emailPattern, Types.VARCHAR);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
