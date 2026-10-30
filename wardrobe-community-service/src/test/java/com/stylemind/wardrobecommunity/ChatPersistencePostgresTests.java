package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.wardrobecommunity.entity.ChatAttachment;
import com.stylemind.wardrobecommunity.entity.ChatMessage;
import com.stylemind.wardrobecommunity.entity.ChatParticipant;
import com.stylemind.wardrobecommunity.entity.ChatSession;
import com.stylemind.wardrobecommunity.entity.Friendship;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipPair;
import com.stylemind.wardrobecommunity.repository.chat.ChatAttachmentRepository;
import com.stylemind.wardrobecommunity.repository.chat.ChatMessageRepository;
import com.stylemind.wardrobecommunity.repository.chat.ChatParticipantRepository;
import com.stylemind.wardrobecommunity.repository.chat.ChatSessionRepository;
import com.stylemind.wardrobecommunity.repository.friendship.FriendshipRepository;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import java.util.concurrent.atomic.AtomicLong;
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

/** Repository/DDL evidence only; fixture transactions are not implemented Chat APIs or authorization. */
@SpringBootTest(properties = {
        "management.health.db.enabled=false",
        "stylemind.security.jwt.jwk-set-uri=",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers
class ChatPersistencePostgresTests {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-27T09:00:00Z");
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("80000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("ffffffff-0000-0000-0000-000000000003");
    private static final List<String> CHAT_TABLES =
            List.of("chat_session", "chat_participant", "chat_message", "chat_attachment");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("stylemind_chat_persistence_test")
            .withUsername("chat_test")
            .withPassword("test-only-not-a-secret");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private FriendshipRepository friendships;
    @Autowired private ChatSessionRepository sessions;
    @Autowired private ChatParticipantRepository participants;
    @Autowired private ChatMessageRepository messages;
    @Autowired private ChatAttachmentRepository attachments;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;

    @BeforeEach
    void resetOnlyDisposableTestDataInDependencyOrder() {
        jdbc.update("DELETE FROM public.chat_attachment");
        jdbc.update("DELETE FROM public.chat_message");
        jdbc.update("DELETE FROM public.chat_participant");
        jdbc.update("DELETE FROM public.chat_session");
        jdbc.update("DELETE FROM public.friendship");
    }

    @Test
    void freshV6AndAllJpaMappingsValidateWithOnlyLocalNonCascadingForeignKeys() {
        assertThat(versions(jdbc)).containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'public' AND t.relname LIKE 'chat_%' AND c.contype = 'f'
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_class r ON r.oid = c.confrelid
                JOIN pg_namespace n ON n.oid = r.relnamespace
                WHERE t.relname LIKE 'chat_%' AND c.contype = 'f'
                  AND (n.nspname <> 'public'
                    OR r.relname NOT IN ('friendship', 'chat_session', 'chat_participant', 'chat_message')
                    OR c.confdeltype <> 'a')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name LIKE 'chat_%'
                """, String.class)).doesNotContain("wardrobe_id", "outfit_id", "recipient_email", "public_url", "token");
    }

    @Test
    void seededV3UpgradePreservesAllExistingRowsAndV1ThroughV3Checksums() {
        String database = "chat_upgrade_test";
        jdbc.execute("CREATE DATABASE " + database);
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate upgrade = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("3")).load().migrate();
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
                INSERT INTO public.outfit_share (outfit_share_id, outfit_id, recipient_email, granted_at)
                VALUES (?, ?, 'existing@example.test', ?)
                """, UUID.randomUUID(), outfitId, NOW);
        upgrade.update("""
                INSERT INTO public.friend_invitation
                  (friend_invitation_id, sender_account_id, recipient_email, status, token_hash,
                   token_issued_at, expires_at, created_at, updated_at)
                VALUES (?, ?, 'future@example.test', 'PENDING', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), A, "a".repeat(64), NOW, NOW.plusHours(168), NOW, NOW);
        seedFriendship(upgrade, friendship(A, B));
        Friendship inactive = friendship(A, C);
        inactive.setUnfriendedAt(NOW);
        seedFriendship(upgrade, inactive);
        List<Map<String, Object>> oldHistory = upgrade.queryForList("""
                SELECT version, checksum FROM public.flyway_schema_history
                WHERE success AND version IN ('1', '2', '3') ORDER BY installed_rank
                """);
        List<String> oldRows = existingFingerprints(upgrade);

        assertThat(Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("4")).load()
                .migrate().migrationsExecuted).isEqualTo(1);

        assertThat(versions(upgrade)).containsExactly("1", "2", "3", "4");
        assertThat(upgrade.queryForList("""
                SELECT version, checksum FROM public.flyway_schema_history
                WHERE success AND version IN ('1', '2', '3') ORDER BY installed_rank
                """)).isEqualTo(oldHistory);
        assertThat(existingFingerprints(upgrade)).isEqualTo(oldRows);
        for (String table : CHAT_TABLES) {
            assertThat(upgrade.queryForObject("SELECT count(*) FROM public." + table, Integer.class)).isZero();
        }
    }

    @Test
    void repositoriesRoundTripSeparateFriendConversationsAndBothParticipants() {
        ChatSession ab = conversation(A, B);
        ChatSession ac = conversation(A, C);

        assertThat(sessions.findByFriendshipId(ab.getFriendshipId()).orElseThrow().getChatSessionId())
                .isEqualTo(ab.getChatSessionId());
        assertThat(participants.findByChatSessionIdOrderByParticipantSlotAsc(ab.getChatSessionId()))
                .extracting(ChatParticipant::getAccountId).containsExactly(A, B);
        assertThat(participants.findByChatSessionIdAndAccountId(ab.getChatSessionId(), C)).isEmpty();
        assertThat(participants.findByChatSessionIdAndAccountId(ac.getChatSessionId(), C)).isPresent();
        assertThat(sessions.findByFriendshipId(UUID.randomUUID())).isEmpty();
        assertThat(sessions.findById(ab.getChatSessionId()).orElseThrow().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void sessionRejectsUnknownFriendshipDuplicateConversationAndInvalidCounterOrTime() {
        ChatSession ab = conversation(A, B);
        violation("23505", () -> sessions.saveAndFlush(session(ab.getFriendshipId())));
        violation("23503", () -> sessions.saveAndFlush(session(UUID.randomUUID())));
        ChatSession negativeCounter = session(friendships.saveAndFlush(friendship(A, C)).getFriendshipId());
        negativeCounter.setLastSequenceNumber(-1);
        violation("23514", () -> sessions.saveAndFlush(negativeCounter));
        ChatSession backwardsTime = session(negativeCounter.getFriendshipId());
        backwardsTime.setUpdatedAt(NOW.minusSeconds(1));
        violation("23514", () -> sessions.saveAndFlush(backwardsTime));
    }

    @Test
    void concurrentSessionCreatesForOneFriendshipHaveExactlyOneWinner() throws Exception {
        Friendship pair = friendships.saveAndFlush(friendship(A, B));

        assertThat(race(() -> sessions.saveAndFlush(session(pair.getFriendshipId())),
                () -> sessions.saveAndFlush(session(pair.getFriendshipId()))))
                .containsExactlyInAnyOrder(true, false);
        assertThat(sessions.count()).isEqualTo(1);
    }

    @Test
    void participantConstraintsBoundSlotsAndForbidDuplicateMembership() {
        ChatSession ab = conversation(A, B);
        violation("23505", () -> participants.saveAndFlush(participant(ab.getChatSessionId(), A, 2)));
        violation("23505", () -> participants.saveAndFlush(participant(ab.getChatSessionId(), C, 1)));
        violation("23514", () -> participants.saveAndFlush(participant(ab.getChatSessionId(), C, 0)));
        violation("23514", () -> participants.saveAndFlush(participant(ab.getChatSessionId(), C, 3)));
        violation("23503", () -> participants.saveAndFlush(participant(UUID.randomUUID(), C, 1)));
        assertThat(participants.count()).isEqualTo(2);
    }

    @Test
    void senderMustExistInTheSameSessionEvenWhenTheyBelongToAnotherConversation() {
        ChatSession ab = conversation(A, B);
        conversation(A, C);
        violation("23503", () -> messages.saveAndFlush(message(ab.getChatSessionId(), C, 1, "Foreign sender")));
        violation("23503", () -> messages.saveAndFlush(message(UUID.randomUUID(), A, 1, "Missing session")));
        assertThat(messages.count()).isZero();
    }

    @Test
    void sequenceMustBePositiveAndUniqueWithinEachSession() {
        ChatSession ab = conversation(A, B);
        ChatSession ac = conversation(A, C);
        messages.saveAndFlush(message(ab.getChatSessionId(), A, 1, "First"));
        messages.saveAndFlush(message(ac.getChatSessionId(), A, 1, "Other session"));

        violation("23514", () -> messages.saveAndFlush(message(ab.getChatSessionId(), A, 0, "Zero")));
        violation("23514", () -> messages.saveAndFlush(message(ab.getChatSessionId(), A, -1, "Negative")));
        violation("23505", () -> messages.saveAndFlush(message(ab.getChatSessionId(), B, 1, "Duplicate sequence")));
        assertThat(messages.count()).isEqualTo(2);
    }

    @Test
    void clientKeyIsScopedBySessionAndSenderAndDoesNotDeduplicateDifferentKeys() {
        ChatSession ab = conversation(A, B);
        ChatSession ac = conversation(A, C);
        UUID key = UUID.randomUUID();
        ChatMessage original = message(ab.getChatSessionId(), A, 1, "Same content");
        original.setClientMessageId(key);
        messages.saveAndFlush(original);
        ChatMessage duplicate = message(ab.getChatSessionId(), A, 2, "Changed content");
        duplicate.setClientMessageId(key);
        violation("23505", () -> messages.saveAndFlush(duplicate));
        ChatMessage otherSender = message(ab.getChatSessionId(), B, 2, "Same content");
        otherSender.setClientMessageId(key);
        messages.saveAndFlush(otherSender);
        ChatMessage otherSession = message(ac.getChatSessionId(), A, 1, "Same content");
        otherSession.setClientMessageId(key);
        messages.saveAndFlush(otherSession);
        messages.saveAndFlush(message(ab.getChatSessionId(), A, 3, "Same content"));

        assertThat(messages.findByChatSessionIdAndSenderAccountIdAndClientMessageId(ab.getChatSessionId(), A, key)
                .orElseThrow().getChatMessageId()).isEqualTo(original.getChatMessageId());
        assertThat(messages.findByChatSessionIdAndSenderAccountIdAndClientMessageId(ac.getChatSessionId(), B, key)).isEmpty();
        assertThat(messages.count()).isEqualTo(4);
    }

    @Test
    void concurrentDuplicateClientKeysHaveOneWinnerWithDifferentSequenceNumbers() throws Exception {
        ChatSession ab = conversation(A, B);
        UUID key = UUID.randomUUID();
        ChatMessage first = message(ab.getChatSessionId(), A, 1, "Retry");
        ChatMessage second = message(ab.getChatSessionId(), A, 2, "Retry");
        first.setClientMessageId(key);
        second.setClientMessageId(key);
        assertThat(race(() -> messages.saveAndFlush(first), () -> messages.saveAndFlush(second)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(messages.count()).isEqualTo(1);
    }

    @Test
    void historyUsesExclusiveSequenceCursorAndNeverIncludesAnotherSession() {
        ChatSession ab = conversation(A, B);
        ChatSession ac = conversation(A, C);
        for (int sequence = 1; sequence <= 5; sequence++) {
            messages.saveAndFlush(message(ab.getChatSessionId(), A, sequence, "Same timestamp"));
        }
        messages.saveAndFlush(message(ac.getChatSessionId(), A, 4, "Another conversation"));

        assertThat(messages.findByChatSessionIdOrderBySequenceNumberDesc(ab.getChatSessionId(), PageRequest.of(0, 2)))
                .extracting(ChatMessage::getSequenceNumber).containsExactly(5L, 4L);
        assertThat(messages.findByChatSessionIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
                ab.getChatSessionId(), 4, PageRequest.of(0, 2)))
                .extracting(ChatMessage::getSequenceNumber).containsExactly(3L, 2L);
        assertThat(messages.findByChatSessionIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
                ab.getChatSessionId(), 2, PageRequest.of(0, 2)))
                .extracting(ChatMessage::getSequenceNumber).containsExactly(1L);
        assertThat(messages.findByChatSessionIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
                ab.getChatSessionId(), 1, PageRequest.of(0, 2))).isEmpty();
        assertThat(messages.findByChatSessionIdOrderBySequenceNumberDesc(UUID.randomUUID(), PageRequest.of(0, 2))).isEmpty();
    }

    @Test
    void textLimitUsesPostgresUnicodeCodePointsIncludingVietnameseAndSupplementaryEmoji() {
        ChatSession ab = conversation(A, B);
        String boundary = "ế".repeat(2000) + "😀".repeat(2000);
        assertThat(boundary.codePointCount(0, boundary.length())).isEqualTo(4000);
        assertThat(boundary.length()).isEqualTo(6000);
        ChatMessage saved = messages.saveAndFlush(message(ab.getChatSessionId(), A, 1, boundary));
        assertThat(messages.findById(saved.getChatMessageId()).orElseThrow().getContent()).isEqualTo(boundary);
        violation("23514", () -> messages.saveAndFlush(message(ab.getChatSessionId(), A, 2, boundary + "x")));
        violation("23514", () -> messages.saveAndFlush(message(ab.getChatSessionId(), A, 2, "")));
    }

    @Test
    void imageOnlyMessageRoundTripsNullTextAndPrivateMetadata() {
        ChatSession ab = conversation(A, B);
        UUID messageId = transaction.execute(status -> {
            ChatMessage saved = messages.saveAndFlush(message(ab.getChatSessionId(), A, 1, null));
            attachments.saveAndFlush(attachment(saved.getChatMessageId(), 1, "image/webp", 100));
            return saved.getChatMessageId();
        });

        assertThat(messages.findById(messageId).orElseThrow().getContent()).isNull();
        ChatAttachment image = attachments.findByChatMessageIdOrderByDisplayOrderAsc(messageId).getFirst();
        assertThat(image.getContentType()).isEqualTo("image/webp");
        assertThat(image.getSizeBytes()).isEqualTo(100);
        assertThat(image.getCreatedAt()).isEqualTo(NOW);
        assertThat(image.getStorageObjectKey()).startsWith("private-test/");
    }

    @Test
    void imagePositionsAllowFiveOrderedImagesAndRejectSixthOrDuplicatePosition() {
        ChatSession ab = conversation(A, B);
        ChatMessage saved = messages.saveAndFlush(message(ab.getChatSessionId(), A, 1, null));
        String[] types = {"image/jpeg", "image/png", "image/webp", "image/jpeg", "image/png"};
        for (int position = 5; position >= 1; position--) {
            attachments.saveAndFlush(attachment(saved.getChatMessageId(), position, types[position - 1], 5242880));
        }

        assertThat(attachments.findByChatMessageIdOrderByDisplayOrderAsc(saved.getChatMessageId()))
                .extracting(ChatAttachment::getDisplayOrder).containsExactly((short) 1, (short) 2, (short) 3, (short) 4, (short) 5);
        violation("23505", () -> attachments.saveAndFlush(attachment(saved.getChatMessageId(), 1, "image/jpeg", 1)));
        violation("23514", () -> attachments.saveAndFlush(attachment(saved.getChatMessageId(), 0, "image/jpeg", 1)));
        violation("23514", () -> attachments.saveAndFlush(attachment(saved.getChatMessageId(), 6, "image/jpeg", 1)));
        assertThat(attachments.count()).isEqualTo(5);
    }

    @Test
    void imageMetadataRejectsZeroNegativeOversizedUnsupportedMimeAndMissingMessage() {
        ChatSession ab = conversation(A, B);
        ChatMessage saved = messages.saveAndFlush(message(ab.getChatSessionId(), A, 1, null));
        for (long size : new long[] {0, -1, 5242881}) {
            violation("23514", () -> attachments.saveAndFlush(attachment(saved.getChatMessageId(), 1, "image/jpeg", size)));
        }
        for (String type : List.of("image/gif", "image/svg+xml", "application/octet-stream", "image/jpg", "IMAGE/PNG")) {
            violation("23514", () -> attachments.saveAndFlush(attachment(saved.getChatMessageId(), 1, type, 1)));
        }
        violation("23503", () -> attachments.saveAndFlush(attachment(UUID.randomUUID(), 1, "image/png", 1)));
        assertThat(attachments.count()).isZero();
    }

    @Test
    void storageKeysAreUniqueNonblankTrimmedAndBoundedAcrossMessages() {
        ChatSession ab = conversation(A, B);
        ChatMessage first = messages.saveAndFlush(message(ab.getChatSessionId(), A, 1, null));
        ChatMessage second = messages.saveAndFlush(message(ab.getChatSessionId(), B, 2, null));
        ChatAttachment original = attachment(first.getChatMessageId(), 1, "image/png", 1);
        original.setStorageObjectKey("k".repeat(1024));
        attachments.saveAndFlush(original);
        ChatAttachment duplicate = attachment(second.getChatMessageId(), 1, "image/png", 1);
        duplicate.setStorageObjectKey(original.getStorageObjectKey());
        violation("23505", () -> attachments.saveAndFlush(duplicate));
        for (String key : List.of("", " ", " leading", "trailing ", "k".repeat(1025))) {
            ChatAttachment invalid = attachment(second.getChatMessageId(), 1, "image/png", 1);
            invalid.setStorageObjectKey(key);
            violation("23514", () -> attachments.saveAndFlush(invalid));
        }
        assertThat(attachments.findByChatMessageIdOrderByDisplayOrderAsc(second.getChatMessageId())).isEmpty();
    }

    @Test
    void failedConversationCreationRollsBackSessionAndBothParticipantsTogether() {
        Friendship pair = friendships.saveAndFlush(friendship(A, B));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            ChatSession session = sessions.saveAndFlush(session(pair.getFriendshipId()));
            participants.saveAndFlush(participant(session.getChatSessionId(), A, 1));
            participants.saveAndFlush(participant(session.getChatSessionId(), B, 2));
            throw new IllegalStateException("Simulated transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(sessions.count()).isZero();
        assertThat(participants.count()).isZero();
        assertThat(friendships.count()).isEqualTo(1);
    }

    @Test
    void failedImageWriteRollsBackSessionCounterMessageAndEarlierImage() {
        ChatSession ab = conversation(A, B);
        violation("23514", () -> transaction.executeWithoutResult(status -> {
            ChatSession locked = sessions.findLockedByChatSessionId(ab.getChatSessionId()).orElseThrow();
            locked.setLastSequenceNumber(1);
            locked.setUpdatedAt(NOW.plusSeconds(1));
            sessions.saveAndFlush(locked);
            ChatMessage saved = messages.saveAndFlush(message(ab.getChatSessionId(), A, 1, null));
            attachments.saveAndFlush(attachment(saved.getChatMessageId(), 1, "image/png", 1));
            attachments.saveAndFlush(attachment(saved.getChatMessageId(), 2, "image/png", 5242881));
        }));
        assertThat(sessions.findById(ab.getChatSessionId()).orElseThrow().getLastSequenceNumber()).isZero();
        assertThat(sessions.findById(ab.getChatSessionId()).orElseThrow().getUpdatedAt()).isEqualTo(NOW);
        assertThat(messages.count()).isZero();
        assertThat(attachments.count()).isZero();
    }

    @Test
    void sessionWriteLockSerializesWritersAndSecondReadsCommittedCounter() throws Exception {
        ChatSession ab = conversation(A, B);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondTrying = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicLong secondObserved = new AtomicLong(-1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> transaction.executeWithoutResult(status -> {
                ChatSession locked = sessions.findLockedByChatSessionId(ab.getChatSessionId()).orElseThrow();
                firstLocked.countDown();
                await(releaseFirst);
                locked.setLastSequenceNumber(1);
                sessions.saveAndFlush(locked);
            }));
            assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = workers.submit(() -> transaction.executeWithoutResult(status -> {
                secondTrying.countDown();
                ChatSession locked = sessions.findLockedByChatSessionId(ab.getChatSessionId()).orElseThrow();
                secondObserved.set(locked.getLastSequenceNumber());
                locked.setLastSequenceNumber(2);
                sessions.saveAndFlush(locked);
            }));
            assertThat(secondTrying.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(secondObserved.get()).isEqualTo(1);
            assertThat(sessions.findById(ab.getChatSessionId()).orElseThrow().getLastSequenceNumber()).isEqualTo(2);
        } finally {
            releaseFirst.countDown();
            stop(workers);
        }
    }

    @Test
    void concurrentTransactionalFixtureWritersAllocateDistinctSequencesAndCommitMatchingCounter() throws Exception {
        ChatSession ab = conversation(A, B);
        Friendship pair = friendships.findById(ab.getFriendshipId()).orElseThrow();
        int writerCount = 8;
        CountDownLatch ready = new CountDownLatch(writerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(writerCount);
        List<Future<Long>> results = new ArrayList<>();
        try {
            for (int index = 0; index < writerCount; index++) {
                UUID sender = index % 2 == 0 ? A : B;
                results.add(workers.submit(() -> {
                    ready.countDown();
                    await(start);
                    return transaction.execute(status -> {
                        // Fixture only: exercise the intended local lock order, not a production Chat service.
                        friendships.findLockedByAccountLowIdAndAccountHighId(pair.getAccountLowId(), pair.getAccountHighId())
                                .orElseThrow();
                        ChatSession locked = sessions.findLockedByChatSessionId(ab.getChatSessionId()).orElseThrow();
                        long sequence = locked.getLastSequenceNumber() + 1;
                        locked.setLastSequenceNumber(sequence);
                        locked.setUpdatedAt(NOW.plusSeconds(sequence));
                        sessions.saveAndFlush(locked);
                        messages.saveAndFlush(message(ab.getChatSessionId(), sender, sequence, "Concurrent message"));
                        return sequence;
                    });
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Long> assigned = new ArrayList<>();
            for (Future<Long> result : results) {
                assigned.add(result.get(15, TimeUnit.SECONDS));
            }
            assertThat(assigned).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
            assertThat(messages.count()).isEqualTo(writerCount);
            assertThat(sessions.findById(ab.getChatSessionId()).orElseThrow().getLastSequenceNumber()).isEqualTo(writerCount);
        } finally {
            start.countDown();
            stop(workers);
        }
    }

    @Test
    void unfriendAndRefriendRetainSameConversationParticipantsMessagesAndImages() {
        ChatSession ab = conversation(A, B);
        ChatMessage saved = messages.saveAndFlush(message(ab.getChatSessionId(), A, 1, "Retained history"));
        attachments.saveAndFlush(attachment(saved.getChatMessageId(), 1, "image/jpeg", 100));
        List<String> before = chatFingerprints();

        transaction.executeWithoutResult(status -> {
            Friendship pair = friendships.findLockedByAccountLowIdAndAccountHighId(A, B).orElseThrow();
            pair.setUnfriendedAt(NOW.plusSeconds(1));
            pair.setUpdatedAt(NOW.plusSeconds(1));
            friendships.saveAndFlush(pair);
        });
        assertThat(friendships.findActivePairForAccount(A, B)).isEmpty();
        assertThat(chatFingerprints()).isEqualTo(before);

        transaction.executeWithoutResult(status -> {
            Friendship pair = friendships.findLockedByAccountLowIdAndAccountHighId(A, B).orElseThrow();
            pair.setUnfriendedAt(null);
            pair.setEstablishedAt(NOW.plusSeconds(2));
            pair.setUpdatedAt(NOW.plusSeconds(2));
            friendships.saveAndFlush(pair);
        });
        assertThat(sessions.findByFriendshipId(ab.getFriendshipId()).orElseThrow().getChatSessionId())
                .isEqualTo(ab.getChatSessionId());
        assertThat(chatFingerprints()).isEqualTo(before);
        // No read/send permission claim: current-friendship API and WebSocket guards are future tasks.
    }

    @Test
    void foreignKeysRejectParentDeletionInsteadOfCascadingAwayHistory() {
        ChatSession ab = conversation(A, B);
        ChatMessage saved = messages.saveAndFlush(message(ab.getChatSessionId(), A, 1, "Retained"));
        attachments.saveAndFlush(attachment(saved.getChatMessageId(), 1, "image/png", 1));
        List<String> before = chatFingerprints();

        violation("23503", () -> jdbc.update("DELETE FROM public.friendship WHERE friendship_id = ?", ab.getFriendshipId()));
        violation("23503", () -> jdbc.update("DELETE FROM public.chat_session WHERE chat_session_id = ?", ab.getChatSessionId()));
        violation("23503", () -> jdbc.update(
                "DELETE FROM public.chat_participant WHERE chat_session_id = ? AND account_id = ?", ab.getChatSessionId(), A));
        violation("23503", () -> jdbc.update("DELETE FROM public.chat_message WHERE chat_message_id = ?", saved.getChatMessageId()));
        assertThat(chatFingerprints()).isEqualTo(before);
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

    private ChatSession session(UUID friendshipId) {
        ChatSession row = new ChatSession();
        row.setChatSessionId(UUID.randomUUID());
        row.setFriendshipId(friendshipId);
        row.setCreatedAt(NOW);
        row.setUpdatedAt(NOW);
        return row;
    }

    private ChatParticipant participant(UUID sessionId, UUID accountId, int slot) {
        ChatParticipant row = new ChatParticipant();
        row.setChatParticipantId(UUID.randomUUID());
        row.setChatSessionId(sessionId);
        row.setAccountId(accountId);
        row.setParticipantSlot((short) slot);
        row.setJoinedAt(NOW);
        return row;
    }

    private ChatSession conversation(UUID first, UUID second) {
        return transaction.execute(status -> {
            Friendship pair = friendships.saveAndFlush(friendship(first, second));
            ChatSession row = sessions.saveAndFlush(session(pair.getFriendshipId()));
            participants.saveAndFlush(participant(row.getChatSessionId(), pair.getAccountLowId(), 1));
            participants.saveAndFlush(participant(row.getChatSessionId(), pair.getAccountHighId(), 2));
            return row;
        });
    }

    private ChatMessage message(UUID sessionId, UUID sender, long sequence, String text) {
        ChatMessage row = new ChatMessage();
        row.setChatMessageId(UUID.randomUUID());
        row.setChatSessionId(sessionId);
        row.setSenderAccountId(sender);
        row.setClientMessageId(UUID.randomUUID());
        row.setSequenceNumber(sequence);
        row.setContent(text);
        row.setCreatedAt(NOW);
        return row;
    }

    private ChatAttachment attachment(UUID messageId, int position, String type, long size) {
        ChatAttachment row = new ChatAttachment();
        row.setChatAttachmentId(UUID.randomUUID());
        row.setChatMessageId(messageId);
        row.setDisplayOrder((short) position);
        row.setStorageObjectKey("private-test/" + row.getChatAttachmentId());
        row.setContentType(type);
        row.setSizeBytes(size);
        row.setCreatedAt(NOW);
        return row;
    }

    private void seedFriendship(JdbcTemplate source, Friendship row) {
        source.update("""
                INSERT INTO public.friendship
                  (friendship_id, account_low_id, account_high_id, established_at, unfriended_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, row.getFriendshipId(), row.getAccountLowId(), row.getAccountHighId(), row.getEstablishedAt(),
                row.getUnfriendedAt(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private List<String> versions(JdbcTemplate source) {
        return source.queryForList("""
                SELECT version FROM public.flyway_schema_history
                WHERE success AND version IS NOT NULL ORDER BY installed_rank
                """, String.class);
    }

    private List<String> existingFingerprints(JdbcTemplate source) {
        // Hash rows so the public-link credential and invitation digest never appear in assertion output.
        return source.queryForList("""
                SELECT 'outfit:' || md5(row_to_json(o)::text) FROM public.outfit o
                UNION ALL SELECT 'item:' || md5(row_to_json(i)::text) FROM public.outfit_item i
                UNION ALL SELECT 'share:' || md5(row_to_json(s)::text) FROM public.outfit_share s
                UNION ALL SELECT 'invitation:' || md5(row_to_json(v)::text) FROM public.friend_invitation v
                UNION ALL SELECT 'friendship:' || md5(row_to_json(f)::text) FROM public.friendship f
                ORDER BY 1
                """, String.class);
    }

    private List<String> chatFingerprints() {
        return jdbc.queryForList("""
                SELECT 'session:' || md5(row_to_json(s)::text) FROM public.chat_session s
                UNION ALL SELECT 'participant:' || md5(row_to_json(p)::text) FROM public.chat_participant p
                UNION ALL SELECT 'message:' || md5(row_to_json(m)::text) FROM public.chat_message m
                UNION ALL SELECT 'attachment:' || md5(row_to_json(a)::text) FROM public.chat_attachment a
                ORDER BY 1
                """, String.class);
    }

    private List<Boolean> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one = workers.submit(contender(first, ready, start));
            Future<Boolean> two = workers.submit(contender(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            stop(workers);
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
                assertThat(sqlState(exception)).isEqualTo("23505");
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

    private void stop(ExecutorService workers) throws InterruptedException {
        workers.shutdownNow();
        assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private void violation(String expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DataIntegrityViolationException.class,
                exception -> assertThat(sqlState(exception)).isEqualTo(expected));
    }

    private String sqlState(DataIntegrityViolationException exception) {
        assertThat(exception.getMostSpecificCause()).isInstanceOf(SQLException.class);
        return ((SQLException) exception.getMostSpecificCause()).getSQLState();
    }
}

