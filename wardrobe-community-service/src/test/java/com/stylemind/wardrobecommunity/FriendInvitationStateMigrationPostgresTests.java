package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class FriendInvitationStateMigrationPostgresTests {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18.6")
        .withDatabaseName("friend_delivery_migration_test").withUsername("friends_test").withPassword("test-only-not-a-secret");
    static final OffsetDateTime NOW=OffsetDateTime.parse("2026-10-28T04:00:00Z");
    static final UUID A=new UUID(0,1),B=new UUID(0,2),C=new UUID(0,3);
    static final List<String> TABLES=List.of("outfit","outfit_item","outfit_share","friend_invitation","friendship",
        "chat_session","chat_participant","chat_message","chat_attachment");

    @Test void upgradeFromV4PreservesAllOldFieldsChecksumsAndSeededChatOutfitData(){
        var source=database();Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("4")).load().migrate();
        var db=new JdbcTemplate(source);
        UUID active=pair(db,A,B,null),inactive=pair(db,A,C,NOW.plusSeconds(1)),invitation=invitation(db);
        UUID outfit=UUID.randomUUID(),chat=UUID.randomUUID(),message=UUID.randomUUID();
        db.update("INSERT INTO public.outfit(outfit_id,owner_account_id,name,creation_source,created_at,updated_at,public_token,public_enabled_at) VALUES (?,?,'Existing','MANUAL',?,?,?,?)",
            outfit,A,NOW,NOW,"fixture-only-public-link",NOW);
        db.update("INSERT INTO public.outfit_item(outfit_item_id,outfit_id,variant_id,display_order,created_at) VALUES (?,?,?,0,?)",
            UUID.randomUUID(),outfit,UUID.randomUUID(),NOW);
        db.update("INSERT INTO public.outfit_share(outfit_share_id,outfit_id,recipient_email,granted_at) VALUES (?,?,'shared@example.test',?)",
            UUID.randomUUID(),outfit,NOW);
        db.update("INSERT INTO public.chat_session(chat_session_id,friendship_id,last_sequence_number,created_at,updated_at) VALUES (?,?,1,?,?)",
            chat,active,NOW,NOW);
        for(int slot=1;slot<=2;slot++)db.update("INSERT INTO public.chat_participant(chat_participant_id,chat_session_id,account_id,participant_slot,joined_at) VALUES (?,?,?,?,?)",
            UUID.randomUUID(),chat,slot==1?A:B,slot,NOW);
        db.update("INSERT INTO public.chat_message(chat_message_id,chat_session_id,sender_account_id,client_message_id,sequence_number,content,created_at) VALUES (?,?,?,?,1,'Existing message',?)",
            message,chat,A,UUID.randomUUID(),NOW);
        db.update("INSERT INTO public.chat_attachment(chat_attachment_id,chat_message_id,display_order,storage_object_key,content_type,size_bytes,created_at) VALUES (?,?,1,'fixture-only-private-key','image/png',10,?)",
            UUID.randomUUID(),message,NOW);
        var before=fingerprints(db);var history=db.queryForList("SELECT version,checksum FROM public.flyway_schema_history WHERE version IN ('1','2','3','4') ORDER BY installed_rank");
        assertThat(Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("5")).load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(fingerprints(db)).isEqualTo(before);
        assertThat(db.queryForList("SELECT version,checksum FROM public.flyway_schema_history WHERE version IN ('1','2','3','4') ORDER BY installed_rank")).isEqualTo(history);
        assertThat(db.queryForObject("SELECT delivery_state FROM public.friend_invitation WHERE friend_invitation_id=?",String.class,invitation)).isEqualTo("UNKNOWN");
        assertThat(db.queryForObject("SELECT delivery_attempt_id FROM public.friend_invitation WHERE friend_invitation_id=?",UUID.class,invitation)).isNull();
        assertThat(db.queryForObject("SELECT invitations_invalid_before FROM public.friendship WHERE friendship_id=?",OffsetDateTime.class,inactive)).isEqualTo(NOW.plusSeconds(1));
        assertThat(db.queryForObject("SELECT invitations_invalid_before FROM public.friendship WHERE friendship_id=?",OffsetDateTime.class,active)).isNull();
        assertThat(db.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isEqualTo(1);
    }

    @Test void deliveryTupleTimestampAndAttemptUniquenessAreEnforced(){
        var source=database();Flyway.configure().dataSource(source).load().migrate();var db=new JdbcTemplate(source);
        UUID id=invitation(db),attempt=UUID.randomUUID();
        assertThatThrownBy(()->db.update("UPDATE public.friend_invitation SET delivery_state='SMTP_ACCEPTED' WHERE friend_invitation_id=?",id))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("UPDATE public.friend_invitation SET delivery_state='OTHER' WHERE friend_invitation_id=?",id))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("UPDATE public.friend_invitation SET delivery_attempt_id=?,last_delivery_attempt_at=? WHERE friend_invitation_id=?",attempt,NOW.minusSeconds(1),id))
            .isInstanceOf(DataIntegrityViolationException.class);
        db.update("UPDATE public.friend_invitation SET delivery_state='SENDING',delivery_attempt_id=?,last_delivery_attempt_at=? WHERE friend_invitation_id=?",attempt,NOW,id);
        UUID other=invitation(db);
        assertThatThrownBy(()->db.update("UPDATE public.friend_invitation SET delivery_state='FAILED',delivery_attempt_id=?,last_delivery_attempt_at=? WHERE friend_invitation_id=?",attempt,NOW,other))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void invalidatedInvitationKeepsDeadlineAndCannotCarryAnAcceptanceTuple(){
        var source=database();Flyway.configure().dataSource(source).load().migrate();var db=new JdbcTemplate(source);
        UUID id=invitation(db);
        db.update("UPDATE public.friend_invitation SET status='INVALIDATED' WHERE friend_invitation_id=?",id);
        assertThat(db.queryForObject("SELECT expires_at FROM public.friend_invitation WHERE friend_invitation_id=?",OffsetDateTime.class,id)).isEqualTo(NOW.plusDays(7));
        assertThatThrownBy(()->db.update("UPDATE public.friend_invitation SET accepted_by_account_id=?,accepted_at=? WHERE friend_invitation_id=?",B,NOW,id))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private DriverManagerDataSource database(){
        String name="v5_"+UUID.randomUUID().toString().replace("-","");
        var admin=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
        admin.execute("CREATE DATABASE "+name);
        String url="jdbc:postgresql://"+POSTGRES.getHost()+":"+POSTGRES.getMappedPort(5432)+"/"+name;
        return new DriverManagerDataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword());
    }
    private UUID pair(JdbcTemplate db,UUID a,UUID b,OffsetDateTime unfriended){
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO public.friendship(friendship_id,account_low_id,account_high_id,established_at,unfriended_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
            id,a,b,NOW,unfriended,NOW,unfriended==null?NOW:unfriended);return id;
    }
    private UUID invitation(JdbcTemplate db){
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO public.friend_invitation(friend_invitation_id,sender_account_id,recipient_email,status,token_hash,token_issued_at,expires_at,created_at,updated_at) VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)",
            id,A,id+"@example.test",UUID.randomUUID().toString().replace("-","").repeat(2),NOW,NOW.plusDays(7),NOW,NOW);
        return id;
    }
    private Map<String,List<String>> fingerprints(JdbcTemplate db){
        var result=new LinkedHashMap<String,List<String>>();
        for(String table:TABLES)result.put(table,db.queryForList(
            "SELECT md5((to_jsonb(t)-ARRAY['delivery_state','delivery_attempt_id','last_delivery_attempt_at','invitations_invalid_before'])::text) FROM public."+table+" t ORDER BY 1",String.class));
        return result;
    }
}
