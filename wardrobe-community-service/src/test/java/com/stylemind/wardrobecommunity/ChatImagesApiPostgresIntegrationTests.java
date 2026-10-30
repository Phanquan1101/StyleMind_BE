package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.stylemind.wardrobecommunity.entity.ChatSession;
import com.stylemind.wardrobecommunity.model.chat.ChatAttachmentContent;
import com.stylemind.wardrobecommunity.repository.chat.ChatSessionRepository;
import com.stylemind.wardrobecommunity.storage.chat.ChatImageStorage;
import com.stylemind.wardrobecommunity.websocket.ChatRealtimePublisher;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"management.health.db.enabled=false","spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@Import(ChatImagesApiPostgresIntegrationTests.Keys.class)
class ChatImagesApiPostgresIntegrationTests {
    static final UUID A=new UUID(0,1),B=new UUID(0,2),C=new UUID(0,3);
    static final RSAKey KEY=key();
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:18.6").withDatabaseName("chat_images_test").withUsername("image_test").withPassword("test-only-not-a-secret");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);r.add("spring.datasource.password",PG::getPassword);}
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ChatImageStorage storage;
    @MockitoSpyBean ChatSessionRepository sessions;
    @MockitoSpyBean ChatRealtimePublisher realtime;
    final Map<String,ChatAttachmentContent> objects=new ConcurrentHashMap<>();
    UUID pair,chat;
    byte[] png;
    List<byte[]> orderedImages;
    @BeforeEach void setup() throws Exception {
        objects.clear();orderedImages=null;png=ChatImageRequestValidatorTests.picture("png");
        for(String table:List.of("chat_attachment","chat_message","chat_participant","chat_session","friend_invitation","friendship"))jdbc.update("DELETE FROM public."+table);
        pair=UUID.randomUUID();OffsetDateTime earlier=OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        jdbc.update("INSERT INTO public.friendship(friendship_id,account_low_id,account_high_id,established_at,created_at,updated_at) VALUES(?,?,?,?,?,?)",pair,A,B,earlier,earlier,earlier);
        var opened=json("/api/v1/me/chats",HttpMethod.POST,A,Map.of("friend_account_id",B));
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        chat=UUID.fromString(mapper.readTree(opened.getBody()).path("chat_session_id").asText());
        doAnswer(inv->{objects.put(inv.getArgument(0),new ChatAttachmentContent(((com.stylemind.wardrobecommunity.model.chat.ChatImage)inv.getArgument(1)).bytes(),"image/png"));return null;}).when(storage).put(anyString(),any());
        when(storage.get(anyString())).thenAnswer(inv->objects.get(inv.getArgument(0)));
        doAnswer(inv->{objects.remove(inv.getArgument(0));return null;}).when(storage).delete(anyString());
    }
    @Test void multipartWithoutMetadataFilenameCommitsAndOnlyMembersReadPrivateBytes() throws Exception {
        var response=upload(A,UUID.randomUUID(),null,2);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode message=mapper.readTree(response.getBody());
        assertThat(message.path("content").isNull()).isTrue();
        assertThat(message.path("attachments")).hasSize(2);
        assertThat(response.getBody()).doesNotContain("storage_object_key","chat/v1/","public_url","download_url");
        String attachment=message.path("attachments").get(0).path("chat_attachment_id").asText();
        for(UUID actor:List.of(A,B)){
            var image=read(actor,chat.toString(),attachment);
            assertThat(image.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(image.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
            assertThat(image.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(image.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(image.getBody()).isEqualTo(png);
        }
        assertThat(read(C,chat.toString(),attachment).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(read(A,UUID.randomUUID().toString(),attachment).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(read(A,chat.toString(),UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    @Test void identicalRetryIsOriginalAndCaptionOrderOrTextModeConflicts() throws Exception {
        UUID key=UUID.randomUUID();var first=upload(A,key,"caption",2);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(upload(A,key,"caption",2).getBody()).isEqualTo(first.getBody());
        assertThat(upload(A,key,"different",2).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(upload(A,key,"caption",1).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(path(),HttpMethod.POST,A,Map.of("client_message_id",key,"content","caption")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(objects).hasSize(2);verify(realtime,times(1)).publish(any());
        UUID textKey=UUID.randomUUID();
        assertThat(json(path(),HttpMethod.POST,A,Map.of("client_message_id",textKey,"content","text")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(upload(A,textKey,null,1).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void fiveImagesAreAllowedAndSixOrInvalidCaptionNeverReachStorage() throws Exception {
        assertThat(upload(A,UUID.randomUUID(),"hello",5).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        int stored=objects.size();
        assertThat(upload(A,UUID.randomUUID(),null,6).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(A,UUID.randomUUID()," ",1).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(objects).hasSize(stored);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isEqualTo(1);
    }
    @Test void retryTreatsTheOrderOfDifferentImagesAsPartOfPayloadIdentity() throws Exception {
        var colored=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        colored.setRGB(0,0,0x00ff00);var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(colored,"png",out);
        byte[] second=out.toByteArray();UUID key=UUID.randomUUID();
        orderedImages=List.of(png,second);var original=upload(A,key,null,2);
        assertThat(original.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        orderedImages=List.of(second,png);assertThat(upload(A,key,null,2).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        orderedImages=List.of(png,second);var replay=upload(A,key,null,2);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(replay.getBody()).isEqualTo(original.getBody());
        assertThat(objects).hasSize(2);
    }
    @Test void outsiderCannotUploadOrProbeStorage(){
        var response=upload(C,UUID.randomUUID(),null,1);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(storage);
    }
    @Test void unfriendBlocksBothMembersThenRefriendRestoresSameStoredImage() throws Exception {
        JsonNode message=mapper.readTree(upload(A,UUID.randomUUID(),null,1).getBody());
        String attachment=message.path("attachments").get(0).path("chat_attachment_id").asText();
        assertThat(json("/api/v1/me/friends/"+B,HttpMethod.DELETE,A,null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        for(UUID actor:List.of(A,B)){
            assertThat(read(actor,chat.toString(),attachment).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(upload(actor,UUID.randomUUID(),null,1).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(objects).hasSize(1);
        OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("UPDATE public.friendship SET unfriended_at=NULL,established_at=?,updated_at=? WHERE friendship_id=?",now,now,pair);
        assertThat(read(B,chat.toString(),attachment).getBody()).isEqualTo(png);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_session",Integer.class)).isEqualTo(1);
    }
    @Test void partialStorageFailureRollsBackAggregateAndCompensatesOwnKeys(){
        doAnswer(inv->{objects.put(inv.getArgument(0),new ChatAttachmentContent(png,"image/png"));if(objects.size()==2)throw unavailable();return null;}).when(storage).put(anyString(),any());
        assertThat(upload(A,UUID.randomUUID(),null,2).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(objects).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_sequence_number FROM public.chat_session",Long.class)).isZero();
    }
    @Test void unfriendDuringCloudUploadIsRecheckedBeforeCommit(){
        doAnswer(inv->{
            objects.put(inv.getArgument(0),new ChatAttachmentContent(png,"image/png"));
            OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
            jdbc.update("UPDATE public.friendship SET unfriended_at=?,updated_at=? WHERE friendship_id=?",now,now,pair);return null;
        }).when(storage).put(anyString(),any());
        assertThat(upload(A,UUID.randomUUID(),null,1).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(objects).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isZero();
        verify(realtime,never()).publish(any());
    }
    @Test void attachmentCounterAndMessageRollbackTogetherOnDatabaseFault(){
        doAnswer(inv->{var result=inv.callRealMethod();ChatSession row=inv.getArgument(0);if(row.getLastSequenceNumber()>0)throw new IllegalStateException("Simulated image counter persistence fault");return result;}).when(sessions).saveAndFlush(any());
        assertThat(upload(A,UUID.randomUUID(),null,1).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_attachment",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_sequence_number FROM public.chat_session",Long.class)).isZero();
        // Generic transaction failures are conservatively reconciled; never delete after a possibly unknown commit.
        assertThat(objects).hasSize(1);verify(storage,never()).delete(anyString());verify(realtime,never()).publish(any());
    }
    @Test void concurrentIdenticalHttpRequestsCommitOneMessageAndKeepOnlyWinnerObjects() throws Exception {
        var firstPut=new CountDownLatch(1);var bothPuts=new CountDownLatch(2);
        doAnswer(inv->{
            objects.put(inv.getArgument(0),new ChatAttachmentContent(png,"image/png"));
            firstPut.countDown();bothPuts.countDown();
            if(!bothPuts.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Concurrent upload barrier did not complete");
            return null;
        }).when(storage).put(anyString(),any());
        UUID key=UUID.randomUUID();
        var pool=Executors.newFixedThreadPool(2);
        try{
            Future<ResponseEntity<String>> first=pool.submit(()->upload(A,key,null,1));
            assertThat(firstPut.await(8,TimeUnit.SECONDS)).isTrue();
            Future<ResponseEntity<String>> second=pool.submit(()->upload(A,key,null,1));
            var a=first.get(15,TimeUnit.SECONDS);var b=second.get(15,TimeUnit.SECONDS);
            assertThat(List.of(a.getStatusCode().value(),b.getStatusCode().value())).containsExactlyInAnyOrder(200,201);
            assertThat(a.getBody()).isEqualTo(b.getBody());assertThat(objects).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_message",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM public.chat_attachment",Integer.class)).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
    @Test void swaggerIncludesBothJsonAndMultipartAndPrivateImageRead() throws Exception {
        JsonNode spec=mapper.readTree(http.getForObject("/v3/api-docs",String.class));
        var content=spec.path("paths").path("/api/v1/chats/{chatId}/messages").path("post").path("requestBody").path("content");
        assertThat(content.has("application/json")).isTrue();assertThat(content.has("multipart/form-data")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/chats/{chatId}/attachments/{attachmentId}/content")).isTrue();
    }
    @Test void imageEndpointsRequireCustomerAuthenticationAndUuidSubject() throws Exception {
        String readPath="/api/v1/chats/"+chat+"/attachments/"+UUID.randomUUID()+"/content";
        assertThat(http.getForEntity(readPath,String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var noAuth=new HttpHeaders();noAuth.setContentType(MediaType.parseMediaType("multipart/form-data; boundary=empty"));
        assertThat(http.exchange(path(),HttpMethod.POST,new HttpEntity<>(new byte[0],noAuth),String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var admin=new HttpHeaders();admin.setBearerAuth(token(A.toString(),List.of("ADMIN")));
        assertThat(http.exchange(readPath,HttpMethod.GET,new HttpEntity<>(admin),String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        admin.setContentType(MediaType.parseMediaType("multipart/form-data; boundary=empty"));
        assertThat(http.exchange(path(),HttpMethod.POST,new HttpEntity<>(new byte[0],admin),String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var badSubject=new HttpHeaders();badSubject.setBearerAuth(token("bad-subject",List.of("CUSTOMER")));
        assertThat(http.exchange(readPath,HttpMethod.GET,new HttpEntity<>(badSubject),String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void realMultipartServletLimitReturnsSanitized413BeforeCloudUpload(){
        png=Arrays.copyOf(png,com.stylemind.wardrobecommunity.validation.chat.ChatImageRequestValidator.MAX_BYTES+1);
        var response=upload(A,UUID.randomUUID(),null,1);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).contains("CHAT_IMAGE_TOO_LARGE").doesNotContain("Exception",".java:");
        verifyNoInteractions(storage);
    }
    @Test void revocationDuringCloudReadPreventsReturningTheImageBytes() throws Exception {
        var message=mapper.readTree(upload(A,UUID.randomUUID(),null,1).getBody());
        String attachment=message.path("attachments").get(0).path("chat_attachment_id").asText();
        when(storage.get(anyString())).thenAnswer(inv->{
            OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
            jdbc.update("UPDATE public.friendship SET unfriended_at=?,updated_at=? WHERE friendship_id=?",now,now,pair);
            return objects.get(inv.getArgument(0));
        });
        assertThat(read(B,chat.toString(),attachment).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    String path(){return "/api/v1/chats/"+chat+"/messages";}
    ResponseEntity<String> upload(UUID actor,UUID key,String caption,int count){
        try{
            String boundary="ep06-"+UUID.randomUUID();var body=new ByteArrayOutputStream();
            Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("client_message_id",key);if(caption!=null)metadata.put("content",caption);
            write(body,"--"+boundary+"\r\nContent-Disposition: form-data; name=\"message\"\r\nContent-Type: application/json\r\n\r\n"+mapper.writeValueAsString(metadata)+"\r\n");
            for(int i=0;i<count;i++){write(body,"--"+boundary+"\r\nContent-Disposition: form-data; name=\"images\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n");body.write(orderedImages==null?png:orderedImages.get(i));write(body,"\r\n");}
            write(body,"--"+boundary+"--\r\n");
            var h=headers(actor);h.setContentType(MediaType.parseMediaType("multipart/form-data; boundary="+boundary));
            return http.exchange(path(),HttpMethod.POST,new HttpEntity<>(body.toByteArray(),h),String.class);
        }catch(Exception failure){throw new IllegalStateException("Image fixture request failed",failure);}
    }
    ResponseEntity<byte[]> read(UUID actor,String chatId,String attachment){return http.exchange("/api/v1/chats/"+chatId+"/attachments/"+attachment+"/content",HttpMethod.GET,new HttpEntity<>(headers(actor)),byte[].class);}
    ResponseEntity<String> json(String path,HttpMethod method,UUID actor,Object payload){
        try{return http.exchange(path,method,new HttpEntity<>(payload==null?null:mapper.writeValueAsString(payload),headers(actor)),String.class);}catch(Exception error){throw new IllegalStateException("Fixture request failed",error);}
    }
    HttpHeaders headers(UUID actor){var h=new HttpHeaders();h.setBearerAuth(token(actor));h.setContentType(MediaType.APPLICATION_JSON);return h;}
    static void write(ByteArrayOutputStream out,String text)throws Exception{out.write(text.getBytes(StandardCharsets.UTF_8));}
    com.stylemind.wardrobecommunity.errors.ApiOperationException unavailable(){return new com.stylemind.wardrobecommunity.errors.ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE,"CHAT_IMAGE_STORAGE_UNAVAILABLE","Unavailable");}
    static String token(UUID actor){return token(actor.toString(),List.of("CUSTOMER"));}
    static String token(String subject,List<String> roles){return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(KEY))).encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build(),JwtClaimsSet.builder().subject(subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).claim("roles",roles).build())).getTokenValue();}
    static RSAKey key(){try{var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);var p=g.generateKeyPair();return new RSAKey.Builder((RSAPublicKey)p.getPublic()).privateKey((RSAPrivateKey)p.getPrivate()).keyID(UUID.randomUUID().toString()).build();}catch(Exception error){throw new ExceptionInInitializerError(error);}}
    @TestConfiguration(proxyBeanMethods=false) static class Keys{@Bean @Primary JwtDecoder decoder()throws Exception{return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();}}
}
