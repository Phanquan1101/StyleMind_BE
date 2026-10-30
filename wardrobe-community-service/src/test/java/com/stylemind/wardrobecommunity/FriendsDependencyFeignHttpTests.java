package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.wardrobecommunity.client.identity.IdentityEmailVerificationClient;
import com.stylemind.wardrobecommunity.client.notification.FriendInvitationMailClient;
import com.stylemind.wardrobecommunity.client.notification.payload.FriendInvitationEmailRequest;
import com.stylemind.wardrobecommunity.model.friendship.FriendInvitationDelivery;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.initialization-fail-timeout=-1","management.health.db.enabled=false"})
class FriendsDependencyFeignHttpTests {
    static final UUID ACTOR=UUID.randomUUID();
    static final String TOKEN="test-only-service-token-0123456789abcdef";
    static final AtomicInteger MAIL_STATUS=new AtomicInteger(202),MAIL_CALLS=new AtomicInteger();
    static final AtomicReference<String> LAST_ACCOUNT_BODY=new AtomicReference<>(),LAST_MAIL_BODY=new AtomicReference<>();
    static final AtomicBoolean TOKENS_VALID=new AtomicBoolean(true);
    static final HttpServer SERVER=server();
    @Autowired IdentityEmailVerificationClient identity;
    @Autowired FriendInvitationMailClient mail;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource static void props(DynamicPropertyRegistry props){
        props.add("stylemind.security.service-token",()->TOKEN);
        props.add("stylemind.identity.base-url",()->"http://127.0.0.1:"+SERVER.getAddress().getPort());
        props.add("stylemind.notification.base-url",()->"http://127.0.0.1:"+SERVER.getAddress().getPort());
    }
    @BeforeEach void reset(){MAIL_STATUS.set(202);MAIL_CALLS.set(0);TOKENS_VALID.set(true);}
    @AfterAll static void stop(){SERVER.stop(0);}

    @Test void realFeignSerializesIdsAndDecodesExplicitVerificationTimestamp()throws Exception{
        var item=identity.lookup(ACTOR).orElseThrow();
        assertThat(item.activeAndEmailVerified()).isTrue();
        assertThat(item.accountId()).isEqualTo(ACTOR);
        assertThat(mapper.readTree(LAST_ACCOUNT_BODY.get()).path("account_ids").get(0).asText()).isEqualTo(ACTOR.toString());
        assertThat(TOKENS_VALID.get()).isTrue();
    }

    @Test void realFeignEmailUsesCamelCaseDeadlineAndOnlyOneRequest()throws Exception{
        var deadline=OffsetDateTime.parse("2026-11-04T01:02:03Z");
        assertThat(mail.send(new FriendInvitationEmailRequest("friend@example.test","Linh",
                "https://app.example.test/invitation#fixture",deadline))).isEqualTo(FriendInvitationDelivery.ACCEPTED);
        var body=mapper.readTree(LAST_MAIL_BODY.get());
        assertThat(body.path("recipientEmail").asText()).isEqualTo("friend@example.test");
        assertThat(body.path("expiresAt").asText()).isEqualTo(deadline.toString());
        assertThat(body.path("invitationUrl").asText()).endsWith("#fixture");
        assertThat(MAIL_CALLS.get()).isEqualTo(1);
        assertThat(TOKENS_VALID.get()).isTrue();
    }

    @Test void actual503And504ResponsesDoNotTriggerFeignRetry(){
        for(int status:new int[]{503,504}){
            MAIL_STATUS.set(status);MAIL_CALLS.set(0);
            var result=mail.send(new FriendInvitationEmailRequest("friend@example.test","Linh",
                    "https://app.example.test/invitation#fixture",OffsetDateTime.now()));
            assertThat(result).isEqualTo(status==503?FriendInvitationDelivery.FAILED:FriendInvitationDelivery.UNKNOWN);
            assertThat(MAIL_CALLS.get()).isEqualTo(1);
        }
    }

    static HttpServer server(){
        try{
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/internal/v1/accounts/email-verification-lookup",exchange->{
                TOKENS_VALID.compareAndSet(true,TOKEN.equals(exchange.getRequestHeaders().getFirst("X-StyleMind-Service-Token")));
                LAST_ACCOUNT_BODY.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                byte[] body=("{\"items\":[{\"account_id\":\""+ACTOR+"\",\"email\":\"friend@example.test\","
                        +"\"account_status\":\"ACTIVE\",\"email_verified_at\":\"2026-10-28T01:02:03Z\"}]}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);
                exchange.getResponseBody().write(body);exchange.close();
            });
            server.createContext("/internal/v1/friend-invitations",exchange->{
                TOKENS_VALID.compareAndSet(true,TOKEN.equals(exchange.getRequestHeaders().getFirst("X-StyleMind-Service-Token")));
                LAST_MAIL_BODY.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                MAIL_CALLS.incrementAndGet();int status=MAIL_STATUS.get();
                byte[] body=("{\"code\":\""+(status==503?"EMAIL_DELIVERY_FAILED":"EMAIL_DELIVERY_UNKNOWN")+"\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,status==202?-1:body.length);
                if(status!=202)exchange.getResponseBody().write(body);exchange.close();
            });
            server.start();return server;
        }catch(Exception failed){throw new ExceptionInInitializerError(failed);}
    }
}
