package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.OffsetDateTime;
import java.util.*;

@Testcontainers
class OutfitVisibilityMigrationPostgresTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.6")
                    .withDatabaseName("outfit_visibility_test")
                    .withUsername("outfit_test")
                    .withPassword("test-only-not-a-secret");

    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-29T01:00:00Z");
    static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

    @Test
    void freshSchemaDefaultsPrivateAndEnforcesVisibilityAndLinkState() {
        var source = database();
        migrate(source, null);
        var db = new JdbcTemplate(source);
        UUID id = outfit(db, null, null);
        assertThat(
                        db.queryForObject(
                                "SELECT visibility FROM outfit WHERE outfit_id=?",
                                String.class,
                                id))
                .isEqualTo("PRIVATE");
        for (String sql :
                List.of(
                        "visibility=NULL",
                        "visibility='OTHER'",
                        "visibility='PUBLIC'",
                        "visibility='FRIENDS',public_enabled_at=now()")) {
            assertThatThrownBy(
                            () -> db.update("UPDATE outfit SET " + sql + " WHERE outfit_id=?", id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        db.update(
                "UPDATE outfit SET"
                    + " public_token='fixture-link',visibility='PUBLIC',public_enabled_at=? WHERE"
                    + " outfit_id=?",
                NOW,
                id);
        db.update(
                "UPDATE outfit SET visibility='FRIENDS',public_enabled_at=NULL WHERE outfit_id=?",
                id);
        assertThat(
                        db.queryForObject(
                                "SELECT public_token FROM outfit WHERE outfit_id=?",
                                String.class,
                                id))
                .isEqualTo("fixture-link");
        UUID other = outfit(db, null, null);
        assertThatThrownBy(
                        () ->
                                db.update(
                                        "UPDATE outfit SET public_token='fixture-link' WHERE"
                                            + " outfit_id=?",
                                        other))
                .isInstanceOf(DataIntegrityViolationException.class);
        String index =
                db.queryForObject(
                        "SELECT indexdef FROM pg_indexes WHERE"
                            + " indexname='outfit_friend_visibility_page_idx'",
                        String.class);
        assertThat(index)
                .contains("owner_account_id, updated_at DESC, outfit_id DESC", "FRIENDS", "PUBLIC");
    }

    @Test
    void upgradePreservesHistoryRowsTokensTimestampsAndUnrelatedData() {
        var source = database();
        migrate(source, "5");
        var db = new JdbcTemplate(source);
        UUID emailOnly = outfit(db, null, null),
                published = outfit(db, "fixture-existing-link", NOW),
                missingMarker = outfit(db, "fixture-missing-marker", null),
                orphanMarker = outfit(db, null, NOW);
        UUID share = UUID.randomUUID();
        db.update(
                "INSERT INTO outfit_share(outfit_share_id,outfit_id,recipient_email,granted_at)"
                    + " VALUES (?,?,'viewer@example.test',?)",
                share,
                emailOnly,
                NOW);
        db.update(
                "INSERT INTO"
                    + " outfit_item(outfit_item_id,outfit_id,variant_id,display_order,created_at)"
                    + " VALUES (?,?,?,0,?)",
                UUID.randomUUID(),
                published,
                UUID.randomUUID(),
                NOW);
        UUID friendship = UUID.randomUUID(),
                session = UUID.randomUUID(),
                message = UUID.randomUUID();
        db.update(
                "INSERT INTO"
                    + " friendship(friendship_id,account_low_id,account_high_id,established_at,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?)",
                friendship,
                A,
                B,
                NOW,
                NOW,
                NOW);
        db.update(
                "INSERT INTO"
                    + " chat_session(chat_session_id,friendship_id,last_sequence_number,created_at,updated_at)"
                    + " VALUES (?,?,1,?,?)",
                session,
                friendship,
                NOW,
                NOW);
        for (int slot = 1; slot <= 2; slot++)
            db.update(
                    "INSERT INTO"
                        + " chat_participant(chat_participant_id,chat_session_id,account_id,participant_slot,joined_at)"
                        + " VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    session,
                    slot == 1 ? A : B,
                    slot,
                    NOW);
        db.update(
                "INSERT INTO"
                    + " chat_message(chat_message_id,chat_session_id,sender_account_id,client_message_id,sequence_number,content,created_at)"
                    + " VALUES (?,?,?,?,1,'Preserved',?)",
                message,
                session,
                A,
                UUID.randomUUID(),
                NOW);
        db.update(
                "INSERT INTO"
                    + " chat_attachment(chat_attachment_id,chat_message_id,display_order,storage_object_key,content_type,size_bytes,created_at)"
                    + " VALUES (?,?,1,'fixture-key','image/png',10,?)",
                UUID.randomUUID(),
                message,
                NOW);
        db.update(
                "INSERT INTO"
                    + " friend_invitation(friend_invitation_id,sender_account_id,recipient_email,status,token_hash,token_issued_at,expires_at,created_at,updated_at)"
                    + " VALUES (?,?,'pending@example.test','PENDING',?,?,?,?,?)",
                UUID.randomUUID(),
                A,
                "a".repeat(64),
                NOW,
                NOW.plusDays(7),
                NOW,
                NOW);
        var before = fingerprints(db);
        var history =
                db.queryForList(
                        "SELECT version,checksum FROM flyway_schema_history ORDER BY"
                            + " installed_rank");
        assertThat(migrate(source, null)).isEqualTo(1);
        assertThat(fingerprints(db)).isEqualTo(before);
        assertThat(
                        db.queryForList(
                                "SELECT version,checksum FROM flyway_schema_history WHERE"
                                    + " version<>'6' ORDER BY installed_rank"))
                .isEqualTo(history);
        assertThat(
                        db.queryForObject(
                                "SELECT visibility FROM outfit WHERE outfit_id=?",
                                String.class,
                                emailOnly))
                .isEqualTo("PRIVATE");
        for (UUID id : List.of(published, missingMarker)) {
            assertThat(
                            db.queryForObject(
                                    "SELECT visibility FROM outfit WHERE outfit_id=?",
                                    String.class,
                                    id))
                    .isEqualTo("PUBLIC");
            assertThat(
                            db.queryForObject(
                                    "SELECT public_enabled_at FROM outfit WHERE outfit_id=?",
                                    OffsetDateTime.class,
                                    id))
                    .isEqualTo(NOW);
        }
        assertThat(
                        db.queryForObject(
                                "SELECT public_enabled_at FROM outfit WHERE outfit_id=?",
                                OffsetDateTime.class,
                                orphanMarker))
                .isNull();
    }

    @Test
    void invalidExistingTokensAbortBeforePartialSchemaAndDoNotRewriteThem() {
        for (String invalid : List.of("   ", "x".repeat(129))) {
            var source = database();
            migrate(source, "5");
            var db = new JdbcTemplate(source);
            UUID id = outfit(db, invalid, NOW);
            assertThatThrownBy(() -> migrate(source, null))
                    .isInstanceOf(org.flywaydb.core.api.FlywayException.class);
            assertThat(
                            db.queryForObject(
                                    "SELECT count(*) FROM information_schema.columns WHERE"
                                        + " table_schema='public' AND table_name='outfit' AND"
                                        + " column_name='visibility'",
                                    Integer.class))
                    .isZero();
            assertThat(
                            db.queryForObject(
                                    "SELECT public_token FROM outfit WHERE outfit_id=?",
                                    String.class,
                                    id))
                    .isEqualTo(invalid);
            assertThat(
                            db.queryForObject(
                                    "SELECT count(*) FROM flyway_schema_history WHERE success AND"
                                        + " version='6'",
                                    Integer.class))
                    .isZero();
        }
    }

    private UUID outfit(JdbcTemplate db, String token, OffsetDateTime enabled) {
        UUID id = UUID.randomUUID();
        db.update(
                "INSERT INTO"
                    + " outfit(outfit_id,owner_account_id,name,creation_source,created_at,updated_at,public_token,public_enabled_at)"
                    + " VALUES (?,?,'Existing','MANUAL',?,?,?,?)",
                id,
                A,
                NOW,
                NOW,
                token,
                enabled);
        return id;
    }

    private int migrate(DriverManagerDataSource source, String target) {
        var config = Flyway.configure().dataSource(source);
        if (target != null) config.target(MigrationVersion.fromVersion(target));
        return config.load().migrate().migrationsExecuted;
    }

    private DriverManagerDataSource database() {
        String name = "outfit_v6_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()))
                .execute("CREATE DATABASE " + name);
        return new DriverManagerDataSource(
                "jdbc:postgresql://"
                        + POSTGRES.getHost()
                        + ":"
                        + POSTGRES.getMappedPort(5432)
                        + "/"
                        + name,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private Map<String, List<String>> fingerprints(JdbcTemplate db) {
        var result = new LinkedHashMap<String, List<String>>();
        for (String table :
                List.of(
                        "outfit",
                        "outfit_item",
                        "outfit_share",
                        "friendship",
                        "friend_invitation",
                        "chat_session",
                        "chat_participant",
                        "chat_message",
                        "chat_attachment")) {
            String excluded =
                    table.equals("outfit") ? "-ARRAY['visibility','public_enabled_at']" : "";
            result.put(
                    table,
                    db.queryForList(
                            "SELECT md5((to_jsonb(t)"
                                    + excluded
                                    + ")::text) FROM "
                                    + table
                                    + " t ORDER BY 1",
                            String.class));
        }
        return result;
    }
}
