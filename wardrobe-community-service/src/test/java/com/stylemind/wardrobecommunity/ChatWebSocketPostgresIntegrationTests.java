package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.stylemind.wardrobecommunity.dto.chat.ChatMessageResponse;
import com.stylemind.wardrobecommunity.websocket.ChatRealtimePublisher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.broker.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"management.health.db.enabled=false","spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@Import(ChatWebSocketPostgresIntegrationTests.Keys.class)
class ChatWebSocketPostgresIntegrationTests {
    static final UUID A=new UUID(0,1),B=new UUID(0,2),C=new UUID(0,3);
    static final RSAKey KEY=key();
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:18.6").withDatabaseName("chat_ws_test").withUsername("chat_test").withPassword("test-only-not-a-secret");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);r.add("spring.datasource.password",PG::getPassword);}
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChatRealtimePublisher realtime;
    @Autowired SimpUserRegistry users;
    @Autowired @Qualifier("simpleBrokerMessageHandler") AbstractBrokerMessageHandler broker;
    @MockitoBean(name="chatSocketClock") Clock socketClock;
    UUID chat,pair;
    final List<Socket> opened=new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        when(socketClock.instant()).thenAnswer(inv->Instant.now());when(socketClock.getZone()).thenReturn(ZoneOffset.UTC);
        for(String table:List.of("chat_attachment","chat_message","chat_participant","chat_session","friend_invitation","friendship"))jdbc.update("DELETE FROM public."+table);
        pair=UUID.randomUUID();OffsetDateTime earlier=OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        jdbc.update("INSERT INTO public.friendship(friendship_id,account_low_id,account_high_id,established_at,created_at,updated_at) VALUES(?,?,?,?,?,?)",pair,A,B,earlier,earlier,earlier);
        var created=http.exchange("/api/v1/me/chats",HttpMethod.POST,request(A,mapper.writeValueAsString(Map.of("friend_account_id",B))),String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        chat=UUID.fromString(mapper.readTree(created.getBody()).path("chat_session_id").asText());
    }
    @AfterEach void close(){opened.forEach(Socket::close);opened.clear();}

    @Test void stompConnectRequiresJwtCustomerUuidAndSanitizesErrors() throws Exception {
        for(String bearer:List.of("", "not-a-token", token(A.toString(),List.of("ADMIN"),Instant.now().plusSeconds(600)),
                token("not-a-uuid",List.of("CUSTOMER"),Instant.now().plusSeconds(600)),
                token(A.toString(),List.of("CUSTOMER"),Instant.now().minusSeconds(180)))){
            Socket socket=raw(null);
            socket.send("CONNECT\naccept-version:1.2\nhost:localhost\n"+(bearer.isEmpty()?"":"Authorization:Bearer "+bearer+"\n")+"\n\u0000");
            Frame error=socket.await(f->f.command.equals("ERROR"));
            assertThat(error.body).contains("code").doesNotContain(bearer.isEmpty()?"Bearer impossible":bearer,"Authorization","Exception",".java:");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isZero();
    }

    @Test void rejectsUntrustedOriginAndCredentialQueryOnUpgrade() {
        assertThatThrownBy(()->raw("https://untrusted.example")).isInstanceOf(Exception.class);
        assertThatThrownBy(()->{
            HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create("ws://localhost:"+port+"/ws/chat?access_token=forbidden"),new WebSocket.Listener(){}).get(5,TimeUnit.SECONDS);
        }).isInstanceOf(Exception.class);
    }

    @Test void textSendBroadcastsAfterCommitAndRetryOnlyAcknowledgesOriginal() throws Exception {
        Socket a=connect(A),b=connect(B);
        String at=topic(a,A),bt=topic(b,B),ack=ack(a,A);
        UUID key=UUID.randomUUID();
        a.send(sendFrame(key,"Xin chào 😀"));
        JsonNode received=mapper.readTree(b.await(f->f.isSubscription(bt)).body);
        JsonNode self=mapper.readTree(a.await(f->f.isSubscription(at)).body);
        JsonNode accepted=mapper.readTree(a.await(f->f.isSubscription(ack)).body);
        assertThat(received).isEqualTo(self);
        assertThat(received.path("sender_account_id").asText()).isEqualTo(A.toString());
        assertThat(received.path("sequence_number").asLong()).isEqualTo(1);
        assertThat(received.path("content").asText()).isEqualTo("Xin chào 😀");
        assertThat(received.path("created_at").isTextual()).isTrue();
        assertThat(accepted.path("replayed").asBoolean()).isFalse();
        assertThat(accepted.path("message")).isEqualTo(received);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isEqualTo(1);

        a.send(sendFrame(key,"Xin chào 😀"));
        JsonNode replay=mapper.readTree(a.await(f->f.isSubscription(ack)).body);
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(replay.path("message")).isEqualTo(received);
        assertThat(b.hasData(bt,300)).isFalse();
        assertThat(a.hasData(at,300)).isFalse();
    }

    @Test void outsiderCannotSubscribeOrPublishAndBusinessErrorsStayPrivate() throws Exception {
        for(String destination:List.of("/topic/chats/"+chat,"/user/queue/chats/"+chat+"/acks","/user/"+A+"/queue/chat-errors")){
            Socket c=connect(C);c.send("SUBSCRIBE\nid:outsider\ndestination:"+destination+"\n\n\u0000");
            assertThat(c.await(f->f.command.equals("ERROR")).body).contains("code");
        }
        Socket c=connect(C);c.send("SEND\ndestination:/topic/chats/"+chat+"\ncontent-type:application/json\n\n{}\u0000");
        assertThat(c.await(f->f.command.equals("ERROR")).body).contains("ACCESS_DENIED");
        Socket a=connect(A);String errors=errors(a,A);
        a.send("SEND\ndestination:/app/chats/"+chat+"/messages\ncontent-type:application/json\n\n{\"private-body-marker\":1}\u0000");
        String body=a.await(f->f.isSubscription(errors)).body;
        assertThat(body).contains("INVALID_REQUEST").doesNotContain("private-body-marker","Authorization","storage_object_key");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isZero();
    }

    @Test void existingSubscriptionsDropAfterUnfriendAndResumeOnlyWhenFriendshipActiveAgain() throws Exception {
        Socket a=connect(A),b=connect(B);String at=topic(a,A),bt=topic(b,B);
        ChatMessageResponse saved=sendHttp("before");
        a.await(f->f.isSubscription(at));b.await(f->f.isSubscription(bt));
        assertThat(http.exchange("/api/v1/me/friends/"+B,HttpMethod.DELETE,request(A,null),String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // A previously persisted notification is queued only after unfriend committed.
        realtime.publish(saved);
        assertThat(a.hasData(at,300)).isFalse();assertThat(b.hasData(bt,300)).isFalse();
        try(Socket denied=connect(A)){denied.send(sendFrame(UUID.randomUUID(),"after"));assertThat(denied.await(f->f.command.equals("ERROR")).body).contains("RESOURCE_NOT_FOUND");}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isEqualTo(1);
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("UPDATE public.friendship SET unfriended_at=NULL,established_at=?,updated_at=? WHERE friendship_id=?",now,now,pair);
        ChatMessageResponse resumed=sendHttp("resumed");
        assertThat(mapper.readTree(a.await(f->f.isSubscription(at)).body).path("chat_message_id").asText()).isEqualTo(resumed.chatMessageId().toString());
        assertThat(mapper.readTree(b.await(f->f.isSubscription(bt)).body).path("sequence_number").asLong()).isEqualTo(2);
        var history=http.exchange("/api/v1/chats/"+chat+"/messages",HttpMethod.GET,request(B,null),String.class);
        assertThat(mapper.readTree(history.getBody()).path("items").findValuesAsText("content")).containsExactly("before","resumed");
    }

    @Test void tokenExpiryBlocksIdleOutboundAndSubsequentInboundWithoutNewHandshake() throws Exception {
        Socket a=connect(A);String at=topic(a,A);
        when(socketClock.instant()).thenReturn(Instant.now().plusSeconds(3600));
        sendHttp("committed but expired socket cannot receive");
        assertThat(a.hasData(at,300)).isFalse();
        a.send(sendFrame(UUID.randomUUID(),"must not persist"));
        assertThat(a.await(f->f.command.equals("ERROR")).body).contains("UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isEqualTo(1);
    }

    Socket raw(String origin) throws Exception {
        Socket socket=new Socket();
        var builder=HttpClient.newHttpClient().newWebSocketBuilder().subprotocols("v12.stomp");
        if(origin!=null)builder.header("Origin",origin);
        socket.ws=builder.buildAsync(URI.create("ws://localhost:"+port+"/ws/chat"),socket).get(5,TimeUnit.SECONDS);
        opened.add(socket);return socket;
    }
    Socket connect(UUID actor) throws Exception {
        Socket socket=raw(null);
        socket.send("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer "+token(actor.toString(),List.of("CUSTOMER"),Instant.now().plusSeconds(600))+"\n\n\u0000");
        socket.await(f->f.command.equals("CONNECTED"));return socket;
    }
    String topic(Socket socket,UUID actor) throws Exception {return subscribe(socket,actor,"/topic/chats/"+chat,"/topic/chats/"+chat);}
    String ack(Socket socket,UUID actor) throws Exception {return subscribe(socket,actor,"/user/queue/chats/"+chat+"/acks",null);}
    String errors(Socket socket,UUID actor) throws Exception {return subscribe(socket,actor,"/user/queue/chat-errors",null);}
    String subscribe(Socket socket,UUID actor,String destination,String actual) throws Exception {
        String id="s"+Socket.IDS.incrementAndGet();
        socket.send("SUBSCRIBE\nid:"+id+"\ndestination:"+destination+"\n\n\u0000");
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<end){
            var user=users.getUser(actor.toString());
            if(user!=null){
                for(var session:user.getSessions()){
                    String resolved=actual!=null?actual:destination.substring("/user".length())+"-user"+session.getId();
                    var h=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);h.setDestination(resolved);
                    var subscriptions=((SimpleBrokerMessageHandler)broker).getSubscriptionRegistry().findSubscriptions(MessageBuilder.createMessage(new byte[0],h.getMessageHeaders()));
                    if(subscriptions.values().stream().anyMatch(ids->ids.contains(id)))return id;
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Subscription not registered");
    }
    String sendFrame(UUID key,String text) throws Exception {return "SEND\ndestination:/app/chats/"+chat+"/messages\ncontent-type:application/json\n\n"+mapper.writeValueAsString(Map.of("client_message_id",key,"content",text))+"\u0000";}
    ChatMessageResponse sendHttp(String text) throws Exception {
        var response=http.exchange("/api/v1/chats/"+chat+"/messages",HttpMethod.POST,request(A,mapper.writeValueAsString(Map.of("client_message_id",UUID.randomUUID(),"content",text))),ChatMessageResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);return response.getBody();
    }
    HttpEntity<String> request(UUID actor,String body){var h=new HttpHeaders();h.setBearerAuth(token(actor.toString(),List.of("CUSTOMER"),Instant.now().plusSeconds(600)));if(body!=null)h.setContentType(MediaType.APPLICATION_JSON);return new HttpEntity<>(body,h);}
    static String token(String subject,List<String> roles,Instant expires){return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(KEY))).encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build(),JwtClaimsSet.builder().subject(subject).issuedAt(Instant.now().minusSeconds(600)).expiresAt(expires).claim("roles",roles).build())).getTokenValue();}
    static RSAKey key(){try{var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);var p=g.generateKeyPair();return new RSAKey.Builder((RSAPublicKey)p.getPublic()).privateKey((RSAPrivateKey)p.getPrivate()).keyID(UUID.randomUUID().toString()).build();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @TestConfiguration(proxyBeanMethods=false) static class Keys {@Bean @Primary JwtDecoder decoder() throws Exception{return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();}}

    record Frame(String command,Map<String,String> headers,String body){
        boolean isSubscription(String id){return command.equals("MESSAGE")&&id.equals(headers.get("subscription"));}
        static Frame parse(String raw){int split=raw.indexOf("\n\n");String head=split<0?raw:raw.substring(0,split);String body=split<0?"":raw.substring(split+2);String[] lines=head.split("\n");Map<String,String> headers=new HashMap<>();for(int i=1;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon>0)headers.put(lines[i].substring(0,colon),lines[i].substring(colon+1));}return new Frame(lines[0],headers,body);}
    }
    static class Socket implements WebSocket.Listener,AutoCloseable {
        static final AtomicInteger IDS=new AtomicInteger();
        WebSocket ws;
        final StringBuilder buffer=new StringBuilder();
        final BlockingQueue<Frame> frames=new LinkedBlockingQueue<>();
        final List<Frame> pending=new ArrayList<>();
        @Override public void onOpen(WebSocket ws){ws.request(1);}
        @Override public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){
            buffer.append(data);int end;while((end=buffer.indexOf("\u0000"))>=0){String frame=buffer.substring(0,end).stripLeading();buffer.delete(0,end+1);if(!frame.isEmpty())frames.add(Frame.parse(frame));}
            ws.request(1);return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket ws,Throwable error){frames.add(new Frame("TRANSPORT_ERROR",Map.of(),"Transport failed"));}
        void send(String frame){ws.sendText(frame,true).join();}
        Frame await(Predicate<Frame> match) throws Exception {
            for(int i=0;i<pending.size();i++)if(match.test(pending.get(i)))return pending.remove(i);
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<end){Frame f=frames.poll(100,TimeUnit.MILLISECONDS);if(f!=null){if(match.test(f))return f;pending.add(f);}}
            throw new AssertionError("Expected WebSocket frame not received; commands="+pending.stream().map(Frame::command).toList());
        }
        boolean hasData(String subscription,long millis) throws Exception {
            if(pending.stream().anyMatch(f->f.isSubscription(subscription)))return true;
            long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);
            while(System.nanoTime()<end){Frame f=frames.poll(10,TimeUnit.MILLISECONDS);if(f!=null){pending.add(f);if(f.isSubscription(subscription))return true;}}
            return false;
        }
        @Override public void close(){if(ws!=null){try{ws.sendClose(WebSocket.NORMAL_CLOSURE,"test complete").get(1,TimeUnit.SECONDS);}catch(Exception ignored){}finally{ws.abort();}}}
    }
}

