package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.wardrobecommunity.entity.FriendInvitation;
import com.stylemind.wardrobecommunity.entity.Friendship;
import com.stylemind.wardrobecommunity.repository.friendship.FriendInvitationRepository;
import com.stylemind.wardrobecommunity.repository.friendship.FriendshipRepository;
import com.stylemind.wardrobecommunity.security.FriendInvitationTokenService;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipPair;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "management.health.db.enabled=false",
        "stylemind.security.jwt.jwk-set-uri=",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers
class FriendsPersistencePostgresTests {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-24T09:00:00Z");
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_friends_persistence_test")
            .withUsername("friends_test")
            .withPassword("test-only-not-a-secret");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private FriendInvitationRepository invitations;
    @Autowired private FriendshipRepository friendships;
    @Autowired private FriendInvitationTokenService tokens;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;

    @BeforeEach
    void resetFriendsOnly() {
        jdbc.update("DELETE FROM public.friend_invitation");
        jdbc.update("DELETE FROM public.friendship");
    }

    @Test
    void freshDatabaseMigratesThroughV6AndJpaMappingsValidate() {
        assertThat(jdbc.queryForList("""
                SELECT version FROM public.flyway_schema_history
                WHERE version IS NOT NULL AND success ORDER BY installed_rank
                """, String.class)).containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history' ORDER BY table_name
                """, String.class)).containsExactly("chat_attachment", "chat_message", "chat_participant", "chat_session",
                        "friend_invitation", "friendship", "outfit", "outfit_item", "outfit_share");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_schema = 'public' AND table_name IN ('friend_invitation', 'friendship')
                  AND constraint_type = 'FOREIGN KEY'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'friend_invitation'
                """, String.class)).contains("token_hash").doesNotContain("token", "raw_token", "public_token");
    }

    @Test
    void upgradeFromSeededV2PreservesOutfitRowsAndMigrationChecksums() {
        // Separate database inside this disposable test container; never the configured application database.
        String database = "friends_upgrade_test";
        jdbc.execute("CREATE DATABASE " + database);
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
        DriverManagerDataSource source = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate upgrade = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("2")).load().migrate();
        UUID outfitId = UUID.randomUUID();
        upgrade.update("""
                INSERT INTO public.outfit
                  (outfit_id, owner_account_id, name, creation_source, created_at, updated_at, public_token, public_enabled_at)
                VALUES (?, ?, 'Existing outfit', 'MANUAL', ?, ?, ?, ?)
                """, outfitId, A, NOW, NOW, "test-only-outfit-public-link", NOW);
        upgrade.update("""
                INSERT INTO public.outfit_item (outfit_item_id, outfit_id, variant_id, display_order, created_at)
                VALUES (?, ?, ?, 0, ?)
                """, UUID.randomUUID(), outfitId, UUID.randomUUID(), NOW);
        upgrade.update("""
                INSERT INTO public.outfit_share (outfit_share_id, outfit_id, recipient_email, granted_at, revoked_at)
                VALUES (?, ?, 'existing@example.test', ?, NULL)
                """, UUID.randomUUID(), outfitId, NOW);
        List<Map<String, Object>> oldHistory = migrationChecksums(upgrade);
        List<String> oldRows = outfitFingerprints(upgrade);

        assertThat(Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("3")).load()
                .migrate().migrationsExecuted).isEqualTo(1);

        assertThat(migrationChecksums(upgrade)).isEqualTo(oldHistory);
        assertThat(outfitFingerprints(upgrade)).isEqualTo(oldRows);
        assertThat(upgrade.queryForObject("SELECT count(*) FROM public.friend_invitation", Integer.class)).isZero();
        assertThat(upgrade.queryForObject("SELECT count(*) FROM public.friendship", Integer.class)).isZero();
    }

    @Test
    void pendingInvitationCanExistBeforeRecipientHasAnAccount() {
        FriendInvitation row = invitations.saveAndFlush(invitation(A, "future@example.test"));

        FriendInvitation saved = invitations.findById(row.getFriendInvitationId()).orElseThrow();
        assertThat(saved.getAcceptedByAccountId()).isNull();
        assertThat(saved.getAcceptedAt()).isNull();
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusHours(168));
        assertThat(saved.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void invitationOwnerLookupsAndPagesDoNotReturnAnotherSendersRows() {
        FriendInvitation first = invitations.saveAndFlush(invitation(A, "one@example.test"));
        invitations.saveAndFlush(invitation(A, "two@example.test"));
        invitations.saveAndFlush(invitation(C, "private@example.test"));

        assertThat(invitations.findByFriendInvitationIdAndSenderAccountId(first.getFriendInvitationId(), C)).isEmpty();
        transaction.executeWithoutResult(status -> assertThat(invitations
                .findLockedByFriendInvitationIdAndSenderAccountId(first.getFriendInvitationId(), C)).isEmpty());
        List<FriendInvitation> page1 = invitations.findBySenderAccountIdOrderByCreatedAtDescFriendInvitationIdDesc(
                A, PageRequest.of(0, 1));
        List<FriendInvitation> page2 = invitations.findBySenderAccountIdOrderByCreatedAtDescFriendInvitationIdDesc(
                A, PageRequest.of(1, 1));
        assertThat(page1).hasSize(1).allMatch(row -> row.getSenderAccountId().equals(A));
        assertThat(page2).hasSize(1).allMatch(row -> row.getSenderAccountId().equals(A));
        assertThat(page1.getFirst().getFriendInvitationId()).isNotEqualTo(page2.getFirst().getFriendInvitationId());
        assertThat(invitations.findBySenderAccountIdOrderByCreatedAtDescFriendInvitationIdDesc(
                B, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void incomingLookupRequiresTheExactRecipientAndStatus() {
        FriendInvitation pending = invitations.saveAndFlush(invitation(A, "recipient@example.test"));
        invitations.saveAndFlush(invitation(C, "other@example.test"));
        FriendInvitation accepted = invitation(D, "recipient@example.test");
        accept(accepted, B, NOW.plusMinutes(1));
        invitations.saveAndFlush(accepted);

        assertThat(invitations.findByRecipientEmailAndStatusOrderByCreatedAtDescFriendInvitationIdDesc(
                "recipient@example.test", "PENDING", PageRequest.of(0, 10)))
                .extracting(FriendInvitation::getFriendInvitationId).containsExactly(pending.getFriendInvitationId());
        assertThat(invitations.findByRecipientEmailAndStatusOrderByCreatedAtDescFriendInvitationIdDesc(
                "stranger@example.test", "PENDING", PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void activeFriendLookupsWorkForEitherMemberAndExcludeUnrelatedOrInactivePairs() {
        Friendship active = friendships.saveAndFlush(friendship(A, B));
        Friendship inactive = friendship(A, C);
        inactive.setUnfriendedAt(NOW.plusMinutes(1));
        inactive.setUpdatedAt(NOW.plusMinutes(1));
        friendships.saveAndFlush(inactive);
        friendships.saveAndFlush(friendship(C, D));

        for (UUID actor : List.of(A, B)) {
            assertThat(friendships.findActiveForAccount(actor, PageRequest.of(0, 10)))
                    .extracting(Friendship::getFriendshipId).containsExactly(active.getFriendshipId());
            assertThat(friendships.countActiveForAccount(actor)).isEqualTo(1);
        }
        assertThat(friendships.findActivePairForAccount(B, A)).isPresent();
        assertThat(friendships.findActivePairForAccount(C, B)).isEmpty();
        assertThat(friendships.findActivePairForAccount(A, C)).isEmpty();
    }

    @Test
    void unfriendAndRefriendReuseOneStablePairRow() {
        Friendship row = friendships.saveAndFlush(friendship(A, B));
        UUID id = row.getFriendshipId();
        transaction.executeWithoutResult(status -> {
            Friendship locked = friendships.findLockedByAccountLowIdAndAccountHighId(A, B).orElseThrow();
            locked.setUnfriendedAt(NOW.plusMinutes(1));
            locked.setUpdatedAt(NOW.plusMinutes(1));
            friendships.saveAndFlush(locked);
        });
        assertThat(friendships.findActivePairForAccount(A, B)).isEmpty();
        assertThat(friendships.findActivePairForAccount(B, A)).isEmpty();
        assertThat(friendships.findById(id)).isPresent();

        transaction.executeWithoutResult(status -> {
            Friendship locked = friendships.findLockedByAccountLowIdAndAccountHighId(A, B).orElseThrow();
            locked.setEstablishedAt(NOW.plusMinutes(2));
            locked.setUnfriendedAt(null);
            locked.setUpdatedAt(NOW.plusMinutes(2));
            friendships.saveAndFlush(locked);
        });

        assertThat(friendships.findActivePairForAccount(B, A).orElseThrow().getFriendshipId()).isEqualTo(id);
        assertThat(friendships.count()).isEqualTo(1);
    }

    @Test
    void canonicalPairMatchesTheActualPostgresUuidOrder() {
        Friendship row = friendships.saveAndFlush(friendship(
                UUID.fromString("80000000-0000-0000-0000-000000000000"),
                UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff")));

        assertThat(jdbc.queryForObject("SELECT account_low_id < account_high_id FROM public.friendship WHERE friendship_id = ?",
                Boolean.class, row.getFriendshipId())).isTrue();
    }

    @Test
    void acceptanceAndFriendshipWritesRollBackTogether() {
        FriendInvitation row = invitations.saveAndFlush(invitation(A, "recipient@example.test"));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            FriendInvitation locked = invitations.findLockedByFriendInvitationId(row.getFriendInvitationId()).orElseThrow();
            accept(locked, B, NOW.plusMinutes(1));
            invitations.saveAndFlush(locked);
            friendships.saveAndFlush(friendship(A, B));
            throw new IllegalStateException("test-only rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(invitations.findById(row.getFriendInvitationId()).orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(friendships.count()).isZero();
    }

    @Test
    void replacingCredentialCommitsOrRollsBackDigestAndDeadlineTogether() {
        FriendInvitation row = invitation(A, "recipient@example.test");
        var original = tokens.issue(row.getFriendInvitationId(), NOW.toInstant());
        row.setTokenHash(original.digest());
        invitations.saveAndFlush(row);
        OffsetDateTime resentAt = NOW.plusDays(6);
        var replacement = tokens.issue(row.getFriendInvitationId(), resentAt.toInstant());

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            replaceCredential(row.getFriendInvitationId(), replacement.digest(), resentAt);
            throw new IllegalStateException("test-only rollback");
        })).isInstanceOf(IllegalStateException.class);
        FriendInvitation rolledBack = invitations.findById(row.getFriendInvitationId()).orElseThrow();
        assertThat(tokens.matches(row.getFriendInvitationId(), original.value(), rolledBack.getTokenHash())).isTrue();
        assertThat(rolledBack.getExpiresAt()).isEqualTo(NOW.plusDays(7));

        transaction.executeWithoutResult(status -> replaceCredential(row.getFriendInvitationId(), replacement.digest(), resentAt));

        FriendInvitation committed = invitations.findById(row.getFriendInvitationId()).orElseThrow();
        assertThat(tokens.matches(row.getFriendInvitationId(), original.value(), committed.getTokenHash())).isFalse();
        assertThat(tokens.matches(row.getFriendInvitationId(), replacement.value(), committed.getTokenHash())).isTrue();
        assertThat(committed.getExpiresAt()).isEqualTo(resentAt.plusDays(7));
    }

    @Test
    void invitationConstraintsRejectUnnormalizedEmailRawTokenAndWrongDeadline() {
        FriendInvitation row = invitations.saveAndFlush(invitation(A, "valid@example.test"));
        UUID id = row.getFriendInvitationId();

        for (String email : List.of("UPPER@example.test", " valid@example.test ", "", "x".repeat(255))) {
            violation("23514", () -> jdbc.update("UPDATE public.friend_invitation SET recipient_email = ? WHERE friend_invitation_id = ?",
                    email, id));
        }
        violation("23514", () -> jdbc.update("UPDATE public.friend_invitation SET token_hash = ? WHERE friend_invitation_id = ?",
                "not-a-digest", id));
        violation("23514", () -> jdbc.update("UPDATE public.friend_invitation SET expires_at = ? WHERE friend_invitation_id = ?",
                NOW.plusDays(7).minusSeconds(1), id));
        violation("23514", () -> jdbc.update("UPDATE public.friend_invitation SET status = 'UNKNOWN' WHERE friend_invitation_id = ?", id));
    }

    @Test
    void acceptedStateRequiresDifferentRecipientAndTimeStrictlyBeforeExpiry() {
        FriendInvitation row = invitations.saveAndFlush(invitation(A, "valid@example.test"));
        UUID id = row.getFriendInvitationId();
        violation("23514", () -> jdbc.update("UPDATE public.friend_invitation SET status = 'ACCEPTED' WHERE friend_invitation_id = ?", id));
        String sql = """
                UPDATE public.friend_invitation SET status = 'ACCEPTED', accepted_by_account_id = ?,
                    accepted_at = ?, updated_at = ? WHERE friend_invitation_id = ?
                """;
        violation("23514", () -> jdbc.update(sql, A, NOW.plusMinutes(1), NOW.plusMinutes(1), id));
        violation("23514", () -> jdbc.update(sql, B, NOW.plusDays(7), NOW.plusDays(7), id));
        assertThat(jdbc.update(sql, B, NOW.plusDays(7).minusNanos(1000), NOW.plusDays(7), id)).isEqualTo(1);
    }

    @Test
    void expiredInvitationDoesNotPreventANewPendingInvitation() {
        FriendInvitation expired = invitation(A, "recipient@example.test");
        expired.setStatus("EXPIRED");
        expired.setTokenIssuedAt(NOW.minusDays(8));
        expired.setCreatedAt(NOW.minusDays(8));
        expired.setExpiresAt(NOW.minusDays(1));
        invitations.saveAndFlush(expired);

        FriendInvitation current = invitations.saveAndFlush(invitation(A, "recipient@example.test"));
        invitations.saveAndFlush(invitation(C, "recipient@example.test"));

        assertThat(invitations.findBySenderAccountIdAndRecipientEmailAndStatus(A, "recipient@example.test", "PENDING")
                .orElseThrow().getFriendInvitationId()).isEqualTo(current.getFriendInvitationId());
        assertThat(invitations.count()).isEqualTo(3);
    }

    @Test
    void databaseRejectsSelfReversedAndInvalidTemporalPairs() {
        String sql = """
                INSERT INTO public.friendship
                  (friendship_id, account_low_id, account_high_id, established_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        violation("23514", () -> jdbc.update(sql, UUID.randomUUID(), A, A, NOW, NOW, NOW));
        violation("23514", () -> jdbc.update(sql, UUID.randomUUID(), B, A, NOW, NOW, NOW));
        violation("23514", () -> jdbc.update(sql, UUID.randomUUID(), A, B, NOW, NOW, NOW.minusSeconds(1)));
        Friendship row = friendships.saveAndFlush(friendship(A, B));
        violation("23514", () -> jdbc.update("UPDATE public.friendship SET unfriended_at = ? WHERE friendship_id = ?",
                NOW.minusSeconds(1), row.getFriendshipId()));
    }

    @Test
    void concurrentDuplicatePendingInvitationsHaveExactlyOneWinner() throws Exception {
        List<Boolean> results = race(
                () -> invitations.saveAndFlush(invitation(A, "same@example.test")),
                () -> invitations.saveAndFlush(invitation(A, "same@example.test")));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(invitations.count()).isEqualTo(1);
    }

    @Test
    void concurrentReversePairCreatesHaveExactlyOneWinner() throws Exception {
        List<Boolean> results = race(
                () -> friendships.saveAndFlush(friendship(A, B)),
                () -> friendships.saveAndFlush(friendship(B, A)));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(friendships.count()).isEqualTo(1);
    }

    @Test
    void writeLockSerializesInvitationUpdatesAndSecondWriterSeesCommittedState() throws Exception {
        FriendInvitation row = invitations.saveAndFlush(invitation(A, "recipient@example.test"));
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondTrying = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<OffsetDateTime> secondObserved = new AtomicReference<>();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> transaction.executeWithoutResult(status -> {
                FriendInvitation locked = invitations.findLockedByFriendInvitationIdAndSenderAccountId(row.getFriendInvitationId(), A)
                        .orElseThrow();
                firstLocked.countDown();
                await(releaseFirst);
                locked.setUpdatedAt(NOW.plusSeconds(1));
                invitations.saveAndFlush(locked);
            }));
            assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = workers.submit(() -> transaction.executeWithoutResult(status -> {
                secondTrying.countDown();
                FriendInvitation locked = invitations.findLockedByFriendInvitationIdAndSenderAccountId(row.getFriendInvitationId(), A)
                        .orElseThrow();
                secondObserved.set(locked.getUpdatedAt());
                locked.setUpdatedAt(NOW.plusSeconds(2));
                invitations.saveAndFlush(locked);
            }));
            assertThat(secondTrying.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(secondObserved.get()).isEqualTo(NOW.plusSeconds(1));
            assertThat(invitations.findById(row.getFriendInvitationId()).orElseThrow().getUpdatedAt()).isEqualTo(NOW.plusSeconds(2));
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private FriendInvitation invitation(UUID sender, String email) {
        FriendInvitation row = new FriendInvitation();
        row.setFriendInvitationId(UUID.randomUUID());
        row.setSenderAccountId(sender);
        row.setRecipientEmail(email);
        row.setStatus("PENDING");
        row.setTokenHash(tokens.issue(row.getFriendInvitationId(), NOW.toInstant()).digest());
        row.setTokenIssuedAt(NOW);
        row.setExpiresAt(NOW.plusDays(7));
        row.setCreatedAt(NOW);
        row.setUpdatedAt(NOW);
        return row;
    }

    private Friendship friendship(UUID first, UUID second) {
        FriendshipPair pair = new FriendshipPair(first, second);
        Friendship row = new Friendship();
        row.setFriendshipId(UUID.randomUUID());
        row.setAccountLowId(pair.accountLowId());
        row.setAccountHighId(pair.accountHighId());
        row.setEstablishedAt(NOW);
        row.setCreatedAt(NOW);
        row.setUpdatedAt(NOW);
        return row;
    }

    private void accept(FriendInvitation row, UUID recipient, OffsetDateTime at) {
        row.setStatus("ACCEPTED");
        row.setAcceptedByAccountId(recipient);
        row.setAcceptedAt(at);
        row.setUpdatedAt(at);
    }

    private void replaceCredential(UUID id, String digest, OffsetDateTime issuedAt) {
        FriendInvitation locked = invitations.findLockedByFriendInvitationIdAndSenderAccountId(id, A).orElseThrow();
        locked.setTokenHash(digest);
        locked.setTokenIssuedAt(issuedAt);
        locked.setExpiresAt(issuedAt.plusDays(7));
        locked.setUpdatedAt(issuedAt);
        invitations.saveAndFlush(locked);
    }

    private List<Boolean> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> contender1 = contender(first, ready, start);
            Callable<Boolean> contender2 = contender(second, ready, start);
            Future<Boolean> result1 = workers.submit(contender1);
            Future<Boolean> result2 = workers.submit(contender2);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(result1.get(10, TimeUnit.SECONDS), result2.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Callable<Boolean> contender(Runnable write, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            await(start);
            try {
                transaction.executeWithoutResult(status -> write.run());
                return true;
            } catch (DataIntegrityViolationException exception) {
                assertThat(exception.getMostSpecificCause()).isInstanceOf(SQLException.class);
                assertThat(((SQLException) exception.getMostSpecificCause()).getSQLState()).isEqualTo("23505");
                return false;
            }
        };
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test worker timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test worker interrupted", exception);
        }
    }

    private void violation(String sqlState, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DataIntegrityViolationException.class, exception -> {
            assertThat(exception.getMostSpecificCause()).isInstanceOf(SQLException.class);
            assertThat(((SQLException) exception.getMostSpecificCause()).getSQLState()).isEqualTo(sqlState);
        });
    }

    private List<Map<String, Object>> migrationChecksums(JdbcTemplate source) {
        return source.queryForList("""
                SELECT version, checksum FROM public.flyway_schema_history
                WHERE version IN ('1', '2') AND success ORDER BY installed_rank
                """);
    }

    private List<String> outfitFingerprints(JdbcTemplate source) {
        // Hash snapshots so test output never includes the existing public-link credential.
        return source.queryForList("""
                SELECT 'outfit:' || md5(row_to_json(o)::text) FROM public.outfit o
                UNION ALL SELECT 'item:' || md5(row_to_json(i)::text) FROM public.outfit_item i
                UNION ALL SELECT 'share:' || md5(row_to_json(s)::text) FROM public.outfit_share s
                ORDER BY 1
                """, String.class);
    }
}
