package com.stylemind.commerce.repository.branch;

import com.stylemind.commerce.model.branch.Branch;
import com.stylemind.commerce.model.branch.BranchCommand;
import com.stylemind.commerce.model.branch.BranchStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BranchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public List<Branch> list(boolean activeOnly) {
        return jdbc.query("""
                SELECT branch_id, branch_code, name, address_line, contact_phone, branch_status
                FROM public.branch
                WHERE (:activeOnly = false OR branch_status = 'ACTIVE')
                ORDER BY name ASC, branch_id ASC
                """, new MapSqlParameterSource("activeOnly", activeOnly), (rs, row) -> branch(rs));
    }

    public Optional<Branch> find(UUID branchId, boolean activeOnly) {
        return jdbc.query("""
                SELECT branch_id, branch_code, name, address_line, contact_phone, branch_status
                FROM public.branch
                WHERE branch_id = :branchId AND (:activeOnly = false OR branch_status = 'ACTIVE')
                """, new MapSqlParameterSource("branchId", branchId).addValue("activeOnly", activeOnly), rs ->
                rs.next() ? Optional.of(branch(rs)) : Optional.empty());
    }

    public Branch insert(UUID branchId, BranchCommand command) {
        jdbc.update("""
                INSERT INTO public.branch (branch_id, branch_code, name, address_line, contact_phone, branch_status)
                VALUES (:branchId, :branchCode, :name, :addressLine, :contactPhone, :status)
                """, parameters(branchId, command));
        return find(branchId, false).orElseThrow();
    }

    public Optional<Branch> update(UUID branchId, BranchCommand command) {
        int rows = jdbc.update("""
                UPDATE public.branch
                SET branch_code = :branchCode, name = :name, address_line = :addressLine,
                    contact_phone = :contactPhone, branch_status = :status
                WHERE branch_id = :branchId
                """, parameters(branchId, command));
        return rows == 0 ? Optional.empty() : find(branchId, false);
    }

    public int delete(UUID branchId) {
        return jdbc.update("DELETE FROM public.branch WHERE branch_id = :branchId",
                new MapSqlParameterSource("branchId", branchId));
    }

    private MapSqlParameterSource parameters(UUID branchId, BranchCommand command) {
        return new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("branchCode", command.branchCode(), Types.VARCHAR)
                .addValue("name", command.name(), Types.VARCHAR)
                .addValue("addressLine", command.addressLine(), Types.VARCHAR)
                .addValue("contactPhone", command.contactPhone(), Types.VARCHAR)
                .addValue("status", command.branchStatus().name(), Types.VARCHAR);
    }

    private Branch branch(ResultSet rs) throws SQLException {
        return new Branch(rs.getObject("branch_id", UUID.class), rs.getString("branch_code"),
                rs.getString("name"), rs.getString("address_line"), rs.getString("contact_phone"),
                BranchStatus.valueOf(rs.getString("branch_status")));
    }
}
