package com.stylemind.identity.repository;

import com.stylemind.identity.dto.users.ManageUsersListRequest;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class AccountSpecifications {
    private AccountSpecifications() { }

    public static Specification<Account> matching(ManageUsersListRequest request) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (request.status() != null) predicates.add(builder.equal(root.get("accountStatus"), request.status()));
            if (request.emailContains() != null) {
                String text = request.emailContains().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(builder.like(builder.lower(root.get("email")), "%" + text + "%", '\\'));
            }
            if (request.role() != null) {
                Subquery<Integer> roleMatch = query.subquery(Integer.class);
                Root<AccountRole> role = roleMatch.from(AccountRole.class);
                roleMatch.select(builder.literal(1));
                roleMatch.where(builder.equal(role.get("id").get("accountId"), root.get("accountId")),
                        builder.equal(role.get("id").get("roleCode"), request.role()));
                predicates.add(builder.exists(roleMatch));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
