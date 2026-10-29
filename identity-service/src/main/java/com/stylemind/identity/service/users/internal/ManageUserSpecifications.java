package com.stylemind.identity.service.users.internal;

import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.AccountRole;
import com.stylemind.identity.model.users.ManageUserListCriteria;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Builds the optional list filters with the JPA Criteria API; no query string or SQL is embedded here. */
public final class ManageUserSpecifications {

    private ManageUserSpecifications() { }

    public static Specification<Account> matching(ManageUserListCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("accountStatus"), criteria.status()));
            }
            if (criteria.emailContains() != null) {
                String text = criteria.emailContains().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(builder.like(builder.lower(root.get("email")), "%" + text + "%", '\\'));
            }
            if (criteria.role() != null) {
                Subquery<Integer> roleMatch = query.subquery(Integer.class);
                Root<AccountRole> role = roleMatch.from(AccountRole.class);
                roleMatch.select(builder.literal(1));
                roleMatch.where(
                        builder.equal(role.get("id").get("accountId"), root.get("accountId")),
                        builder.equal(role.get("id").get("roleCode"), criteria.role()));
                predicates.add(builder.exists(roleMatch));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
