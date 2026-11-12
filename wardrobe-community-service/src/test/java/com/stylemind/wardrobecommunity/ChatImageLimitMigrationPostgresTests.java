package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ChatImageLimitMigrationPostgresTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6")
            .withDatabaseName("chat_image_limit_test").withUsername("chat_test")
            .withPassword("test-only-not-a-secret");

    @Test
    void upgradesV6PreservingOldAttachmentsAndChecksumsAndAcceptsTenMiB() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("6")).load().migrate();
        var db = new JdbcTemplate(source);
        UUID friendship = UUID.randomUUID(), session = UUID.randomUUID(), participant = UUID.randomUUID();
        UUID actor = new UUID(0, 1), peer = new UUID(0, 2), message = UUID.randomUUID(), attachment = UUID.randomUUID();
        db.update("INSERT INTO friendship VALUES (?,?,?,now(),NULL,now(),now())", friendship, actor, peer);
        db.update("INSERT INTO chat_session VALUES (?,?,1,now(),now())", session, friendship);
        db.update("INSERT INTO chat_participant VALUES (?,?,?,1,now())", participant, session, actor);
        db.update("INSERT INTO chat_message VALUES (?,?,?,?,1,NULL,now())", message, session, actor, UUID.randomUUID());
        db.update("INSERT INTO chat_attachment VALUES (?,?,1,'existing-private-key','image/png',5242880,now())", attachment, message);
        var before = db.queryForMap("SELECT * FROM chat_attachment WHERE chat_attachment_id=?", attachment);
        var history = db.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(Flyway.configure().dataSource(source).load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForMap("SELECT * FROM chat_attachment WHERE chat_attachment_id=?", attachment)).isEqualTo(before);
        assertThat(db.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version<>'7' ORDER BY installed_rank")).isEqualTo(history);
        db.update("INSERT INTO chat_attachment VALUES (?,?,2,'new-private-key','image/png',10485760,now())", UUID.randomUUID(), message);
        assertThatThrownBy(() -> db.update("INSERT INTO chat_attachment VALUES (?,?,3,'too-large','image/png',10485761,now())", UUID.randomUUID(), message))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO chat_attachment VALUES (?,?,3,'empty','image/png',0,now())", UUID.randomUUID(), message))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(Flyway.configure().dataSource(source).load().migrate().migrationsExecuted).isZero();
    }
}
