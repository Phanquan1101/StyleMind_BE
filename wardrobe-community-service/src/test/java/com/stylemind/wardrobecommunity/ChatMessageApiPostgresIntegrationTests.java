package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.stylemind.wardrobecommunity.entity.ChatSession;
import com.stylemind.wardrobecommunity.model.friendship.FriendshipPair;
import com.stylemind.wardrobecommunity.repository.chat.ChatSessionRepository;
import com.stylemind.wardrobecommunity.repository.friendship.FriendshipRepository;
import com.stylemind.wardrobecommunity.websocket.ChatRealtimePublisher;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"management.health.db.enabled=false","spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Testcontainers
@Import(ChatMessageApiPostgresIntegrationTests.Keys.class)
class ChatMessageApiPostgresIntegrationTests {
    static final UUID A=new UUID(0,1), B=UUID.fromString("80000000-0000-0000-0000-000000000002"), C=new UUID(0,3);
    static final OffsetDateTime NOW=OffsetDateTime.parse("2026-10-27T10:00:00Z");
    static final RSAKey KEY=key();
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:18.6").withDatabaseName("chat_messages_test").withUsername("chat_test").withPassword("test-only-not-a-secret");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);r.add("spring.datasource.password",PG::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired FriendshipRepository friendships;
    @MockitoBean(name="chatClock") Clock clock;
    @MockitoBean(name="friendshipClock") Clock friendsClock;
    @MockitoSpyBean ChatSessionRepository sessions;
    @MockitoSpyBean ChatRealtimePublisher realtime;
    UUID chat, pair;
    String path() { return "/api/v1/chats/"+chat+"/messages"; }

    @BeforeEach void setup() throws Exception {
        for(String table:List.of("chat_attachment","chat_message","chat_participant","chat_session","friend_invitation","friendship"))jdbc.update("DELETE FROM public."+table);
        for(Clock c:List.of(clock,friendsClock)){when(c.instant()).thenReturn(NOW.toInstant());when(c.getZone()).thenReturn(ZoneOffset.UTC);}
        pair=UUID.randomUUID();
        jdbc.update("INSERT INTO public.friendship(friendship_id,account_low_id,account_high_id,established_at,created_at,updated_at) VALUES(?,?,?,?,?,?)",pair,A,B,NOW.minusDays(1),NOW.minusDays(1),NOW.minusDays(1));
        JsonNode opened=body(send(post("/api/v1/me/chats").contentType("application/json").content("{\"friend_account_id\":\""+B+"\"}"),A).andExpect(status().isCreated()));
        chat=UUID.fromString(opened.path("chat_session_id").asText());
    }

    @Test void bothRoutesRequireCustomerJwtAndValidUuidSubject() throws Exception {
        for(boolean write:List.of(false,true)){
            var request=write?post(path()).contentType("application/json").content(json(UUID.randomUUID(),"hi")):get(path());
            mvc.perform(request).andExpect(status().isUnauthorized());
            var admin=write?post(path()).contentType("application/json").content(json(UUID.randomUUID(),"hi")):get(path());
            mvc.perform(admin.header("Authorization","Bearer "+token(A.toString(),List.of("ADMIN")))).andExpect(status().isForbidden());
            var bad=write?post(path()).contentType("application/json").content(json(UUID.randomUUID(),"hi")):get(path());
            mvc.perform(bad.header("Authorization","Bearer "+token("bad-subject",List.of("CUSTOMER")))).andExpect(status().isUnauthorized());
        }
    }

    @Test void retryReturnsOriginalWithoutCounterTimeOrSecondPublicationAndConflictingPayloadIs409() throws Exception {
        UUID key=UUID.randomUUID();
        doAnswer(inv->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return inv.callRealMethod();}).when(realtime).publish(any());
        JsonNode original=body(send(post(path()).contentType("application/json").content(json(key," xin chào\n")),A).andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")));
        List<String> before=fingerprints();
        JsonNode retry=body(send(post(path()).contentType("application/json").content(json(key," xin chào\n")),A).andExpect(status().isOk()));
        assertThat(retry).isEqualTo(original);
        send(post(path()).contentType("application/json").content(json(key,"changed")),A).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(fingerprints()).isEqualTo(before);
        verify(realtime).publish(any());
    }

    @Test void differentKeysAndOtherSenderCanSendIdenticalText() throws Exception {
        UUID key=UUID.randomUUID();
        sendText(A,key,"same",201);sendText(B,key,"same",201);sendText(A,UUID.randomUUID(),"same",201);
        assertThat(jdbc.queryForList("SELECT sequence_number FROM public.chat_message ORDER BY sequence_number",Long.class)).containsExactly(1L,2L,3L);
        assertThat(jdbc.queryForObject("SELECT last_sequence_number FROM public.chat_session WHERE chat_session_id=?",Long.class,chat)).isEqualTo(3);
    }

    @Test void chronologicalExclusiveCursorHasNoDuplicateOrMissingMessagesAndReadsDoNotWrite() throws Exception {
        for(int i=1;i<=5;i++)sendText(i%2==0?B:A,UUID.randomUUID(),"m"+i,201);
        List<String> before=fingerprints();
        JsonNode first=body(send(get(path()+"?limit=3"),A).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")));
        assertThat(first.path("items").findValuesAsText("content")).containsExactly("m3","m4","m5");
        assertThat(first.path("has_more").asBoolean()).isTrue();assertThat(first.path("next_before_sequence").asLong()).isEqualTo(3);
        JsonNode older=body(send(get(path()+"?before_sequence=3&limit=3"),B).andExpect(status().isOk()));
        assertThat(older.path("items").findValuesAsText("content")).containsExactly("m1","m2");
        assertThat(older.path("has_more").asBoolean()).isFalse();assertThat(older.path("next_before_sequence").isNull()).isTrue();
        send(get(path()+"?before_sequence=1"),A).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        assertThat(fingerprints()).isEqualTo(before);
    }

    @Test void outsiderAndInvalidMembershipCannotReadSendOrReplay() throws Exception {
        UUID key=UUID.randomUUID();sendText(A,key,"private",201);
        send(get(path()),C).andExpect(status().isNotFound());
        send(post(path()).contentType("application/json").content(json(key,"private")),C).andExpect(status().isNotFound());
        jdbc.update("UPDATE public.chat_participant SET account_id=? WHERE chat_session_id=? AND account_id=?",C,chat,B);
        for(UUID actor:List.of(A,B,C)){
            send(get(path()),actor).andExpect(status().isNotFound());
            send(post(path()).contentType("application/json").content(json(key,"private")),actor).andExpect(status().isNotFound());
        }
    }

    @Test void unfriendBlocksHistoryNewSendAndRetryForBothThenRefriendRestoresOriginalHistory() throws Exception {
        UUID key=UUID.randomUUID();JsonNode first=sendText(A,key,"retained",201);
        send(delete("/api/v1/me/friends/"+B),A).andExpect(status().isNoContent());
        for(UUID actor:List.of(A,B)){
            send(get(path()),actor).andExpect(status().isNotFound());
            send(post(path()).contentType("application/json").content(json(key,"retained")),actor).andExpect(status().isNotFound());
        }
        jdbc.update("UPDATE public.friendship SET unfriended_at=NULL,established_at=?,updated_at=? WHERE friendship_id=?",NOW,NOW,pair);
        JsonNode history=body(send(get(path()),B).andExpect(status().isOk()));
        assertThat(history.path("items").get(0)).isEqualTo(first);
        sendText(A,key,"retained",200);
    }

    @Test void textAndCursorValidationRejectUnsafeOrAmbiguousInputWithoutWriting() throws Exception {
        UUID key=UUID.randomUUID();
        String boundary="😀".repeat(4000);
        sendText(A,key,boundary,201);
        for(String text:List.of("", " ", "\u00a0",boundary+"a","a\u0000b")){
            send(post(path()).contentType("application/json").content(json(UUID.randomUUID(),text)),A).andExpect(status().isBadRequest());
        }
        for(String query:List.of("?before_sequence=0","?before_sequence=-1","?before_sequence=9223372036854775808","?limit=0","?limit=101","?limit=1&limit=2","?owner="+C)){
            send(get(path()+query),A).andExpect(status().isBadRequest());
        }
        send(post(path()+"?owner="+C).contentType("application/json").content(json(key,boundary)),A).andExpect(status().isBadRequest());
        send(post(path()).contentType("application/json").content("{\"client_message_id\":\""+key+"\",\"content\":\"a\",\"sender_account_id\":\""+B+"\"}"),A).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isEqualTo(1);
    }

    @Test void failureAfterMessageAndCounterWriteRollsBackEverythingAndDoesNotPublish() throws Exception {
        doAnswer(inv->{
            ChatSession s=inv.getArgument(0);
            jdbc.update("UPDATE public.chat_session SET last_sequence_number=?,updated_at=? WHERE chat_session_id=?",s.getLastSequenceNumber(),s.getUpdatedAt(),s.getChatSessionId());
            throw new IllegalStateException("Simulated counter write failure");
        }).when(sessions).saveAndFlush(any());
        send(post(path()).contentType("application/json").content(json(UUID.randomUUID(),"rollback")),A).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_sequence_number FROM public.chat_session WHERE chat_session_id=?",Long.class,chat)).isZero();
        verify(realtime,never()).publish(any());
    }

    @Test void concurrentDifferentMessagesAllocateUniqueSequencesOverRealHttp() throws Exception {
        try(var workers=Executors.newFixedThreadPool(4)){
            List<Future<Integer>> responses=new ArrayList<>();
            for(int i=0;i<8;i++){UUID sender=i%2==0?A:B;responses.add(workers.submit(()->http.exchange(path(),HttpMethod.POST,request(sender,json(UUID.randomUUID(),"parallel")),String.class).getStatusCode().value()));}
            for(var result:responses)assertThat(result.get(15,TimeUnit.SECONDS)).isEqualTo(201);
        }
        assertThat(jdbc.queryForList("SELECT sequence_number FROM public.chat_message ORDER BY sequence_number",Long.class)).containsExactly(1L,2L,3L,4L,5L,6L,7L,8L);
    }

    @Test void concurrentIdenticalRetryHasOneCreatedOneReplayAndOneRowOverHttp() throws Exception {
        UUID key=UUID.randomUUID();var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)){
            Callable<Integer> task=()->{start.await();return http.exchange(path(),HttpMethod.POST,request(A,json(key,"retry")),String.class).getStatusCode().value();};
            var one=workers.submit(task);var two=workers.submit(task);start.countDown();
            assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,200);
        }finally{start.countDown();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isEqualTo(1);
    }

    @Test void sendWaitingOnUnfriendRechecksAfterCommitAndStoresNothing() throws Exception {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var workers=Executors.newFixedThreadPool(2);
        try{
            var changing=workers.submit(()->transaction.executeWithoutResult(tx->{
                var f=friendships.findLockedByAccountLowIdAndAccountHighId(A,B).orElseThrow();f.setUnfriendedAt(NOW);f.setUpdatedAt(NOW);friendships.saveAndFlush(f);
                locked.countDown();await(release);
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var sending=workers.submit(()->http.exchange(path(),HttpMethod.POST,request(A,json(UUID.randomUUID(),"blocked")),String.class));
            try{
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%friendship%'",Integer.class)==0){
                    if(System.nanoTime()>deadline)throw new AssertionError("No request waiting for Friendship lock");Thread.sleep(10);
                }
                assertThatThrownBy(()->sending.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            }finally{release.countDown();}
            changing.get(10,TimeUnit.SECONDS);assertThat(sending.get(10,TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }finally{release.countDown();workers.shutdownNow();workers.awaitTermination(10,TimeUnit.SECONDS);}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isZero();
    }

    @Test void historyReturnsOnlyExistingSafeImageMetadataAndNoStorageUrl() throws Exception {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO public.chat_message(chat_message_id,chat_session_id,sender_account_id,client_message_id,sequence_number,content,created_at) VALUES(?,?,?,?,1,NULL,?)",id,chat,A,UUID.randomUUID(),NOW);
        jdbc.update("INSERT INTO public.chat_attachment(chat_attachment_id,chat_message_id,display_order,storage_object_key,content_type,size_bytes,created_at) VALUES(?,?,1,?,'image/png',100,?)",UUID.randomUUID(),id,"private-test-not-a-real-file",NOW);
        JsonNode history=body(send(get(path()),A).andExpect(status().isOk()));
        JsonNode row=history.path("items").get(0);
        assertThat(row.path("content").isNull()).isTrue();
        var fields=new ArrayList<String>();row.path("attachments").get(0).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("chat_attachment_id","display_order","content_type","size_bytes");
        assertThat(history.toString()).doesNotContain("private-test","storage_object_key","public_url","download_url");
        send(post("/api/v1/chats/"+chat+"/attachments").contentType("application/json").content("{}"),A).andExpect(status().isNotFound());
    }

    @Test void openApiDocumentsTextWriteCursorHistoryAndSafeDtos() throws Exception {
        JsonNode doc=body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        JsonNode route=doc.path("paths").path("/api/v1/chats/{chatId}/messages");
        assertThat(route.path("post").path("responses").has("201")).isTrue();
        assertThat(route.path("post").path("responses").has("409")).isTrue();
        assertThat(route.path("get").path("parameters").toString()).contains("before_sequence","limit");
        assertThat(doc.at("/components/schemas/ChatAttachmentMetadataResponse/properties").toString()).doesNotContain("storage_object_key","url");
    }

    JsonNode sendText(UUID actor,UUID key,String text,int status) throws Exception {return body(send(post(path()).contentType("application/json").content(json(key,text)),actor).andExpect(status().is(status)));}
    String json(UUID key,String text) throws Exception{return mapper.writeValueAsString(Map.of("client_message_id",key,"content",text));}
    ResultActions send(MockHttpServletRequestBuilder r,UUID actor) throws Exception{return mvc.perform(r.header("Authorization","Bearer "+token(actor.toString(),List.of("CUSTOMER"))));}
    JsonNode body(ResultActions r) throws Exception{return mapper.readTree(r.andReturn().getResponse().getContentAsByteArray());}
    HttpEntity<String> request(UUID actor,String body){var h=new HttpHeaders();h.setBearerAuth(token(actor.toString(),List.of("CUSTOMER")));h.setContentType(MediaType.APPLICATION_JSON);return new HttpEntity<>(body,h);}
    List<String> fingerprints(){return jdbc.queryForList("SELECT 's:'||md5(row_to_json(s)::text) FROM public.chat_session s UNION ALL SELECT 'm:'||md5(row_to_json(m)::text) FROM public.chat_message m ORDER BY 1",String.class);}
    void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    static String token(String subject,List<String> roles){return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(KEY))).encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build(),JwtClaimsSet.builder().subject(subject).issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(600)).claim("roles",roles).build())).getTokenValue();}
    static RSAKey key(){try{var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);var p=g.generateKeyPair();return new RSAKey.Builder((RSAPublicKey)p.getPublic()).privateKey((RSAPrivateKey)p.getPrivate()).keyID(UUID.randomUUID().toString()).build();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @TestConfiguration(proxyBeanMethods=false) static class Keys {@Bean @Primary JwtDecoder decoder() throws Exception{return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();}}
}

