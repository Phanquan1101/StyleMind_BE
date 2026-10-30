package com.stylemind.wardrobecommunity.service.friendship.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One short, DB-local mutex for friend mutations, including absent rows. Never hold across network I/O. */
@Component
public class FriendshipMutationLock {
    @PersistenceContext private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(1398036813, 6) as text)",
                String.class).getSingleResult();
    }
}
