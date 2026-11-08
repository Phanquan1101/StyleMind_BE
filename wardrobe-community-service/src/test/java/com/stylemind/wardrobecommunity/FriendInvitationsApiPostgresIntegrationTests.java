package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.stylemind.wardrobecommunity.client.identity.*;
import com.stylemind.wardrobecommunity.client.identity.payload.*;
import com.stylemind.wardrobecommunity.client.notification.*;
import com.stylemind.wardrobecommunity.client.notification.payload.FriendInvitationEmailRequest;
import com.stylemind.wardrobecommunity.entity.*;
import com.stylemind.wardrobecommunity.model.friendship.*;
import com.stylemind.wardrobecommunity.repository.friendship.*;
import com.stylemind.wardrobecommunity.service.friendship.*;
import java.net.URI;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(properties={"management.health.db.enabled=false","spring.jpa.hibernate.ddl-auto=validate",
    "stylemind.friends.invitation-page-url=https://frontend.example.test/friends/invitations"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Testcontainers
@Import(FriendInvitationsApiPostgresIntegrationTests.Keys.class)
class FriendInvitationsApiPostgresIntegrationTests {
    static final String PATH="/api/v1/me/friend-invitations";
    static final UUID A=new UUID(0,1),B=new UUID(0,2),C=new UUID(0,3);
    static final Instant START=Instant.parse("2026-10-28T04:00:00Z");
    static final RSAKey KEY=key();
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:18.6")
        .withDatabaseName("friend_invitation_api_test").withUsername("friends_test").withPassword("test-only-not-a-secret");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);
        r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate jdbc;
    @Autowired FriendInvitationRepository invitations; @Autowired FriendshipRepository friendships;
    @Autowired FriendInvitationService service; @Autowired FriendshipService friendshipService;
    @MockitoBean IdentityEmailVerificationClient identity;
    @MockitoBean IdentityDirectoryClient directory;
    @MockitoBean FriendInvitationMailClient mail;
    @MockitoBean(name="friendshipClock") Clock clock;
    final Map<UUID,AccountEmailVerificationItem> accounts=new ConcurrentHashMap<>();
    final List<FriendInvitationEmailRequest> emails=new CopyOnWriteArrayList<>();
    final AtomicReference<Instant> time=new AtomicReference<>();
    final AtomicReference<FriendInvitationDelivery> delivery=new AtomicReference<>();

    @BeforeEach void reset(){
        jdbc.update("DELETE FROM public.chat_attachment");jdbc.update("DELETE FROM public.chat_message");
        jdbc.update("DELETE FROM public.chat_participant");jdbc.update("DELETE FROM public.chat_session");
        jdbc.update("DELETE FROM public.friend_invitation");jdbc.update("DELETE FROM public.friendship");
        time.set(START);delivery.set(FriendInvitationDelivery.ACCEPTED);emails.clear();accounts.clear();
        for(UUID id:List.of(A,B,C))accounts.put(id,facts(id,email(id)));
        when(clock.instant()).thenAnswer(x->time.get());when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(identity.lookup(any(UUID.class))).thenAnswer(x->{outside();return Optional.ofNullable(accounts.get(x.getArgument(0)));});
        when(identity.lookupByEmail(anyString())).thenAnswer(x->{outside();String email=x.getArgument(0);
            return accounts.values().stream().filter(a->a.email().equals(email)).findFirst();});
        when(directory.lookup(anyCollection())).thenAnswer(x->{outside();Map<UUID,AccountLookupItem> values=new HashMap<>();
            Collection<UUID> ids=x.getArgument(0);for(UUID id:ids)if(accounts.containsKey(id))
                values.put(id,new AccountLookupItem(id,email(id),"Test user","ACTIVE"));return values;});
        when(mail.send(any())).thenAnswer(x->{outside();emails.add(x.getArgument(0));return delivery.get();});
    }

    @Test void createPendingThenExplicitIncomingAcceptCreatesSymmetricFriendship() throws Exception {
        var result=create(A,email(B),201);
        UUID id=UUID.fromString(result.path("friend_invitation_id").asText());
        assertThat(result.path("delivery_status").asText()).isEqualTo("SMTP_ACCEPTED");
        assertThat(friendships.count()).isZero();assertThat(emails).hasSize(1);
        call(get(PATH+"/incoming"),B,null).andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(1));
        call(post(PATH+"/"+id+"/accept"),B,"{}").andExpect(status().isOk())
            .andExpect(jsonPath("$.outcome").value("FRIENDSHIP_ACCEPTED")).andExpect(header().string("Cache-Control","no-store"));
        assertThat(friendships.countActiveForAccount(A)).isEqualTo(1);
        assertThat(friendships.countActiveForAccount(B)).isEqualTo(1);
        call(post(PATH+"/"+id+"/accept"),B,"{}").andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("ALREADY_FRIENDS"));
        assertThat(friendships.count()).isEqualTo(1);
        assertThat(result.toString().contains("token")).isFalse();
    }

    @Test void duplicateSuccessfulPendingNeverSendsAgainEvenAfterThirtySeconds() throws Exception {
        var first=create(A,email(B),201);String id=first.path("friend_invitation_id").asText();
        var before=invitations.findById(UUID.fromString(id)).orElseThrow();
        time.set(START.plusSeconds(31));
        var duplicate=create(A," "+email(B).toUpperCase(Locale.ROOT)+" ",200);
        assertThat(duplicate.path("friend_invitation_id").asText()).isEqualTo(id);
        assertThat(duplicate.path("outcome").asText()).isEqualTo("PENDING_EXISTS");
        call(post(PATH+"/"+id+"/resend"),A,"{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_ALREADY_SENT"));
        var after=invitations.findById(UUID.fromString(id)).orElseThrow();
        assertThat(before.getTokenHash().equals(after.getTokenHash())).isTrue();
        assertThat(after.getExpiresAt()).isEqualTo(before.getExpiresAt());assertThat(emails).hasSize(1);
    }

    @Test void reverseInvitationReturnsAcceptActionWithoutEmailOrImplicitFriendship() throws Exception {
        var first=create(A,email(B),201);
        var reverse=create(B,email(A),200);
        assertThat(reverse.path("outcome").asText()).isEqualTo("INCOMING_EXISTS");
        assertThat(reverse.path("friend_invitation_id").asText()).isEqualTo(first.path("friend_invitation_id").asText());
        assertThat(invitations.count()).isEqualTo(1);assertThat(friendships.count()).isZero();assertThat(emails).hasSize(1);
    }

    @Test void recipientCanRegisterLaterAndOnlyTheMatchingVerifiedAccountCanAccept() throws Exception {
        accounts.remove(B);var first=create(A,email(B),201);UUID id=UUID.fromString(first.path("friend_invitation_id").asText());
        String linkBody=linkBody(id,emails.getFirst());
        call(post(PATH+"/accept"),C,linkBody).andExpect(status().isNotFound());
        accounts.put(B,new AccountEmailVerificationItem(B,email(B),"ACTIVE",null));
        call(post(PATH+"/accept"),B,linkBody).andExpect(status().isForbidden());
        accounts.put(B,facts(B,email(B)));
        call(post(PATH+"/accept"),B,linkBody).andExpect(status().isOk());
        assertThat(friendships.count()).isEqualTo(1);
    }

    @Test void failedSendCanRetryAtExactlyThirtySecondsWithNewCredential() throws Exception {
        delivery.set(FriendInvitationDelivery.FAILED);
        var failed=call(post(PATH),A,recipient(email(B))).andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After","30")).andReturn();
        UUID id=UUID.fromString(mapper.readTree(failed.getResponse().getContentAsByteArray()).path("friend_invitation_id").asText());
        String old=linkBody(id,emails.getFirst());
        time.set(START.plusSeconds(29));
        call(post(PATH+"/"+id+"/resend"),A,"{}").andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","1"));
        create(A,email(B),200);assertThat(emails).hasSize(1);
        time.set(START.plusSeconds(30));delivery.set(FriendInvitationDelivery.ACCEPTED);
        call(post(PATH+"/"+id+"/resend"),A,"{}").andExpect(status().isOk());
        assertThat(emails).hasSize(2);
        call(post(PATH+"/accept"),B,old).andExpect(status().isNotFound());
        call(post(PATH+"/accept"),B,linkBody(id,emails.getLast())).andExpect(status().isOk());
        assertThat(invitations.findById(id).orElseThrow().getExpiresAt().toInstant()).isEqualTo(START.plusSeconds(30).plus(Duration.ofDays(7)));
    }

    @Test void expiryAllowsResendButInvalidatesOldLinkAndResetsSevenDays() throws Exception {
        UUID id=UUID.fromString(create(A,email(B),201).path("friend_invitation_id").asText());
        String old=linkBody(id,emails.getFirst());
        time.set(START.plus(Duration.ofDays(7)));
        call(post(PATH+"/accept"),B,old).andExpect(status().isGone());
        call(post(PATH+"/"+id+"/resend"),A,"{}").andExpect(status().isOk());
        call(post(PATH+"/accept"),B,old).andExpect(status().isNotFound());
        assertThat(invitations.findById(id).orElseThrow().getExpiresAt().toInstant()).isEqualTo(START.plus(Duration.ofDays(14)));
    }

    @Test void unknownDeliveryRequiresExplicitRetryWithoutAutomaticEmail() throws Exception {
        delivery.set(FriendInvitationDelivery.UNKNOWN);
        call(post(PATH),A,recipient(email(B))).andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_UNKNOWN"));
        assertThat(emails).hasSize(1);
        UUID id=invitations.findAll().getFirst().getFriendInvitationId();
        time.set(START.plusSeconds(30));delivery.set(FriendInvitationDelivery.ACCEPTED);
        create(A,email(B),200);assertThat(emails).hasSize(1);
        call(post(PATH+"/"+id+"/resend"),A,"{}").andExpect(status().isOk());assertThat(emails).hasSize(2);
    }

    @Test void creatingANewIdCannotBypassSenderEmailCooldown() throws Exception {
        delivery.set(FriendInvitationDelivery.FAILED);
        call(post(PATH),A,recipient(email(B))).andExpect(status().isServiceUnavailable());
        jdbc.update("UPDATE public.friend_invitation SET status='INVALIDATED'");
        time.set(START.plusSeconds(10));
        call(post(PATH),A,recipient(email(B))).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","20"));
        assertThat(emails).hasSize(1);assertThat(invitations.count()).isEqualTo(1);
    }

    @Test void unfriendBlocksUsedAndOldPendingLinksEvenAfterRefriend() throws Exception {
        UUID accepted=UUID.fromString(create(A,email(B),201).path("friend_invitation_id").asText());
        call(post(PATH+"/"+accepted+"/accept"),B,"{}").andExpect(status().isOk());
        FriendInvitation old=new FriendInvitation();old.setFriendInvitationId(UUID.randomUUID());old.setSenderAccountId(B);
        old.setRecipientEmail(email(A));old.setStatus("PENDING");old.setTokenHash("b".repeat(64));
        old.setTokenIssuedAt(OffsetDateTime.ofInstant(START,ZoneOffset.UTC));old.setCreatedAt(old.getTokenIssuedAt());
        old.setUpdatedAt(old.getCreatedAt());old.setExpiresAt(old.getCreatedAt().plusDays(7));invitations.saveAndFlush(old);
        UUID pair=friendships.findAll().getFirst().getFriendshipId();
        time.set(START.plusSeconds(1));friendshipService.unfriend(A,B);
        call(post(PATH+"/"+accepted+"/accept"),B,"{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_ALREADY_USED"));
        call(post(PATH+"/"+old.getFriendInvitationId()+"/accept"),A,"{}").andExpect(status().isConflict());
        call(get(PATH+"/incoming"),A,null).andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(0));
        time.set(START.plusSeconds(31));UUID fresh=UUID.fromString(create(A,email(B),201).path("friend_invitation_id").asText());
        call(post(PATH+"/"+fresh+"/accept"),B,"{}").andExpect(status().isOk());
        assertThat(friendships.findAll().getFirst().getFriendshipId()).isEqualTo(pair);
        assertThat(friendships.findAll().getFirst().getInvitationsInvalidBefore()).isNotNull();
        call(post(PATH+"/"+old.getFriendInvitationId()+"/accept"),A,"{}").andExpect(status().isConflict());
    }

    @Test void concurrentCrossedSendsProduceOnePendingAndNoImplicitFriendship() throws Exception {
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var one=workers.submit(()->{ready.countDown();go.await();return service.create(A,email(B));});
            var two=workers.submit(()->{ready.countDown();go.await();return service.create(B,email(A));});
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            List<String> outcomes=List.of(one.get(15,TimeUnit.SECONDS).response().outcome(),two.get(15,TimeUnit.SECONDS).response().outcome());
            assertThat(outcomes).containsExactlyInAnyOrder("EMAIL_ACCEPTED","INCOMING_EXISTS");
        }
        assertThat(invitations.count()).isEqualTo(1);assertThat(friendships.count()).isZero();assertThat(emails).hasSize(1);
    }

    @Test void concurrentDuplicateSendsAndAcceptsDoNotDuplicateMailOrFriendship() throws Exception {
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var one=workers.submit(()->service.create(A,email(B)));var two=workers.submit(()->service.create(A,email(B)));
            one.get(15,TimeUnit.SECONDS);two.get(15,TimeUnit.SECONDS);
            UUID id=invitations.findAll().getFirst().getFriendInvitationId();
            var first=workers.submit(()->service.accept(B,id,null));var second=workers.submit(()->service.accept(B,id,null));
            assertThat(List.of(first.get(15,TimeUnit.SECONDS).response().outcome(),second.get(15,TimeUnit.SECONDS).response().outcome()))
                .containsExactlyInAnyOrder("FRIENDSHIP_ACCEPTED","ALREADY_FRIENDS");
        }
        assertThat(emails).hasSize(1);assertThat(invitations.count()).isEqualTo(1);assertThat(friendships.count()).isEqualTo(1);
    }

    @Test void lateFirstCompletionCannotOverwriteSuccessfulRetry() throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var count=new AtomicInteger();
        when(mail.send(any())).thenAnswer(x->{outside();emails.add(x.getArgument(0));
            if(count.incrementAndGet()==1){started.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Fixture timeout");
                return FriendInvitationDelivery.FAILED;}return FriendInvitationDelivery.ACCEPTED;});
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var old=workers.submit(()->service.create(A,email(B)));
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();UUID id=invitations.findAll().getFirst().getFriendInvitationId();
            try {
                time.set(START.plusSeconds(30));service.resend(A,id);
            } finally {release.countDown();}
            assertThat(old.get(15,TimeUnit.SECONDS).response().outcome()).isEqualTo("DELIVERY_SUPERSEDED");
            assertThat(invitations.findById(id).orElseThrow().getDeliveryState()).isEqualTo("SMTP_ACCEPTED");
        }
    }

    @Test void rollbackDoesNotConsumeInvitationOrCreateHalfAFriendship() throws Exception {
        UUID id=UUID.fromString(create(A,email(B),201).path("friend_invitation_id").asText());
        jdbc.execute("CREATE FUNCTION fixture_reject_friendship() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture rollback'; END; $$");
        jdbc.execute("CREATE TRIGGER fixture_reject_friendship BEFORE INSERT ON public.friendship FOR EACH ROW EXECUTE FUNCTION fixture_reject_friendship()");
        try {assertThatThrownBy(()->service.accept(B,id,null)).isInstanceOf(RuntimeException.class);}
        finally {jdbc.execute("DROP TRIGGER fixture_reject_friendship ON public.friendship");jdbc.execute("DROP FUNCTION fixture_reject_friendship()");}
        assertThat(friendships.count()).isZero();assertThat(invitations.findById(id).orElseThrow().getStatus()).isEqualTo("PENDING");
    }

    @Test void strictInputAndSelfInviteCannotMutateOrSend() throws Exception {
        for(String invalid:List.of("{}","null","[]","{\"recipient_email\":null}","{\"recipient_email\":\"invalid\"}",
            "{\"recipient_email\":\"b@example.test\",\"sender_id\":\"spoof\"}",
            "{\"recipient_email\":\"b@example.test\",\"recipient_email\":\"c@example.test\"}",
            "{\"recipient_email\":\"b@example.test\"} {}"))
            call(post(PATH),A,invalid).andExpect(status().isBadRequest());
        call(post(PATH),A,recipient(email(A))).andExpect(status().isBadRequest());
        assertThat(invitations.count()).isZero();verifyNoInteractions(mail);
    }

    @Test void allRoutesRequireCustomerBearerAndCannotUseAnotherSendersInvitation() throws Exception {
        for(var route:List.of(post(PATH).content(recipient(email(B))),get(PATH+"/incoming"),
            post(PATH+"/"+UUID.randomUUID()+"/resend").content("{}"),post(PATH+"/accept").content("{}"))){
            mvc.perform(route.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(recipient(email(B)))
            .header(HttpHeaders.AUTHORIZATION,"Bearer "+token(A,List.of("ADMIN")))).andExpect(status().isForbidden());
        UUID id=UUID.fromString(create(A,email(B),201).path("friend_invitation_id").asText());
        call(post(PATH+"/"+id+"/resend"),C,"{}").andExpect(status().isNotFound());
        assertThat(emails).hasSize(1);
    }

    @Test void incomingPaginationNeverReturnsOtherEmailOrExpiredInvitation() throws Exception {
        create(A,email(B),201);create(C,email(B),201);
        call(get(PATH+"/incoming").param("size","1"),B,null).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.total_elements").value(2));
        call(get(PATH+"/incoming"),A,null).andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(0));
        time.set(START.plus(Duration.ofDays(7)));
        call(get(PATH+"/incoming"),B,null).andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(0));
        assertThat(invitations.count()).isEqualTo(2);assertThat(emails).hasSize(2);
    }

    @Test void swaggerContainsBothAcceptancePathsWithoutPublishingInternalLookup() throws Exception {
        var result=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var paths=mapper.readTree(result.getResponse().getContentAsByteArray()).path("paths");
        assertThat(paths.has(PATH+"/accept")).isTrue();assertThat(paths.has(PATH+"/{invitationId}/accept")).isTrue();
        assertThat(paths.has(PATH+"/incoming")).isTrue();assertThat(paths.has("/internal/v1/accounts/email-verification-lookup-by-email")).isFalse();
    }

    JsonNode create(UUID actor,String email,int expected)throws Exception {
        var result=call(post(PATH),actor,recipient(email)).andExpect(status().is(expected)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
    ResultActions call(MockHttpServletRequestBuilder request,UUID actor,String body)throws Exception {
        request.header(HttpHeaders.AUTHORIZATION,"Bearer "+token(actor,List.of("CUSTOMER")));
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }
    String recipient(String email)throws Exception{return mapper.writeValueAsString(Map.of("recipient_email",email));}
    String linkBody(UUID id,FriendInvitationEmailRequest email)throws Exception {
        String fragment=URI.create(email.invitationUrl()).getRawFragment();String credential=fragment.substring(fragment.indexOf("&token=")+7);
        return mapper.writeValueAsString(Map.of("friend_invitation_id",id,"token",credential));
    }
    static void outside(){assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();}
    static String email(UUID id){return id.equals(A)?"a@example.test":id.equals(B)?"b@example.test":"c@example.test";}
    static AccountEmailVerificationItem facts(UUID id,String email){return new AccountEmailVerificationItem(id,email,"ACTIVE",OffsetDateTime.ofInstant(START.minusSeconds(60),ZoneOffset.UTC));}
    static String token(UUID id,List<String> roles){
        var claims=JwtClaimsSet.builder().subject(id.toString()).issuedAt(Instant.now().minusSeconds(1)).expiresAt(Instant.now().plusSeconds(600)).claim("roles",roles).build();
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(KEY)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build(),claims)).getTokenValue();
    }
    static RSAKey key(){try{var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate()).keyID(UUID.randomUUID().toString()).build();
        }catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @TestConfiguration(proxyBeanMethods=false) static class Keys {
        @Bean @Primary JwtDecoder invitationTestDecoder()throws Exception{return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();}
    }
}
