package com.stylemind.verification;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/** Runs only from the documented Docker wrapper against databases this class owns. */
class ChatRuntimeIntegrationTests {
    static final UUID A=new UUID(0,1),B=new UUID(0,2),C=new UUID(0,3);
    static final UUID OUTFIT_VARIANT=new UUID(0,101);
    final ObjectMapper json=new ObjectMapper();
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final List<Process> apps=new ArrayList<>();
    final Map<UUID,String> tokens=new HashMap<>();
    final Map<String,Stored> objects=new ConcurrentHashMap<>();
    final Path evidence=Path.of(required("ep06.evidence"));
    final Path repo=Path.of(required("ep06.repo"));
    int gatewayPort;
    PostgreSQLContainer<?> wardrobe;
    GenericContainer<?> mailpit;
    final BackblazeRuntimeStorage cloud="true".equals(System.getenv("EP06_LIVE_BACKBLAZE"))?new BackblazeRuntimeStorage():null;
    record Stored(byte[] bytes,String mime) { @Override public String toString(){return "Stored[REDACTED]";} }

    @Test @Timeout(value=12,unit=TimeUnit.MINUTES)
    void realIdentityJwksGatewayChatAndPostmanCollections() throws Exception {
        Files.createDirectories(evidence);
        Path privateDir=Files.createTempDirectory("ep06-runtime-private-");
        Path keyFile=privateDir.resolve("test-key.pem");
        var keys=KeyPairGenerator.getInstance("RSA");keys.initialize(2048);
        Files.writeString(keyFile,"-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(keys.generateKeyPair().getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n");
        Files.setPosixFilePermissions(keyFile,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        String secret=Base64.getEncoder().encodeToString(SecureRandom.getSeed(32));
        String serviceToken=Base64.getUrlEncoder().withoutPadding().encodeToString(SecureRandom.getSeed(32));
        HttpServer s3=fakeStorage();
        HttpServer commerce=fixtureCatalog(serviceToken);
        try(var identity=new PostgreSQLContainer<>("postgres:18.6").withDatabaseName("ep06_identity_runtime").withUsername("runtime_identity").withPassword(UUID.randomUUID().toString());
            var chatDb=new PostgreSQLContainer<>("postgres:18.6").withDatabaseName("ep06_wardrobe_runtime").withUsername("runtime_wardrobe").withPassword(UUID.randomUUID().toString());
            var mailbox=new GenericContainer<>("axllent/mailpit:v1.31.4")
                    .withNetworkMode("container:"+System.getenv("EP06_TEST_RUNNER_CONTAINER"))
                    .withEnv("MP_ENABLE_CHAOS","true")
                    .waitingFor(Wait.forLogMessage("(?is).*http.*(starting|accessible|listening).*",1)
                            .withStartupTimeout(Duration.ofSeconds(30)))){
            try {
            if(cloud!=null)cloud.probe();
            identity.start();chatDb.start();mailbox.start();wardrobe=chatDb;mailpit=mailbox;
            mailChaos(Map.of());
            int identityPort=port(),wardrobePort=port(),notificationPort=port();gatewayPort=port();
            launch("notification",required("ep06.notificationJar"),notificationPort,Map.of(
                "STYLEMIND_SERVICE_TOKEN",serviceToken,"STYLEMIND_SMTP_HOST","127.0.0.1",
                "STYLEMIND_SMTP_PORT","1025","STYLEMIND_SMTP_AUTH","false",
                "STYLEMIND_EMAIL_FROM","ep06-runtime@example.test","MANAGEMENT_HEALTH_DB_ENABLED","false",
                "SPRING_AUTOCONFIGURE_EXCLUDE","org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration"));
            awaitHealth(notificationPort,"notification");
            launch("identity",required("ep06.identityJar"),identityPort,Map.of(
                "STYLEMIND_IDENTITY_DB_URL",identity.getJdbcUrl(),"STYLEMIND_IDENTITY_DB_USERNAME",identity.getUsername(),
                "STYLEMIND_IDENTITY_DB_PASSWORD",identity.getPassword(),"STYLEMIND_JWT_PRIVATE_KEY_PATH",keyFile.toString(),
                "STYLEMIND_VERIFICATION_HMAC_SECRET",secret,"STYLEMIND_SERVICE_TOKEN",serviceToken,
                "STYLEMIND_NOTIFICATION_BASE_URL","http://127.0.0.1:"+notificationPort));
            awaitHealth(identityPort,"identity");
            String password="Ep06-runtime-"+UUID.randomUUID()+"!";
            seedIdentity(identity,password);
            var wardrobeEnvironment=new HashMap<>(Map.ofEntries(
                Map.entry("STYLEMIND_WARDROBE_COMMUNITY_DB_URL",chatDb.getJdbcUrl()),Map.entry("STYLEMIND_WARDROBE_COMMUNITY_DB_USERNAME",chatDb.getUsername()),
                Map.entry("STYLEMIND_WARDROBE_COMMUNITY_DB_PASSWORD",chatDb.getPassword()),Map.entry("STYLEMIND_SERVICE_TOKEN",serviceToken),
                Map.entry("STYLEMIND_JWT_JWK_SET_URI","http://127.0.0.1:"+identityPort+"/.well-known/jwks.json"),
                Map.entry("STYLEMIND_IDENTITY_BASE_URL","http://127.0.0.1:"+identityPort),
                Map.entry("STYLEMIND_COMMERCE_BASE_URL","http://127.0.0.1:"+commerce.getAddress().getPort()),
                Map.entry("STYLEMIND_NOTIFICATION_BASE_URL","http://127.0.0.1:"+notificationPort),
                Map.entry("STYLEMIND_FRIEND_INVITATION_PAGE_URL","https://frontend.example.test/friends/invitations"),
                Map.entry("STYLEMIND_CHAT_IMAGES_ENABLED","true"),Map.entry("STYLEMIND_CHAT_IMAGES_BUCKET","private-runtime-fixture"),
                Map.entry("STYLEMIND_CHAT_IMAGES_REGION","us-east-1"),Map.entry("STYLEMIND_CHAT_IMAGES_ENDPOINT","http://127.0.0.1:"+s3.getAddress().getPort()),
                Map.entry("AWS_ACCESS_KEY_ID","runtime-test-not-real"),Map.entry("AWS_SECRET_ACCESS_KEY","runtime-test-not-real")));
            if(cloud!=null)wardrobeEnvironment.putAll(cloud.environment());
            launch("wardrobe",required("ep06.wardrobeJar"),wardrobePort,wardrobeEnvironment);
            awaitHealth(wardrobePort,"wardrobe");
            launch("gateway",required("ep06.gatewayJar"),gatewayPort,Map.of(
                "STYLEMIND_IDENTITY_BASE_URL","http://127.0.0.1:"+identityPort,
                "STYLEMIND_WARDROBE_COMMUNITY_BASE_URL","http://127.0.0.1:"+wardrobePort));
            awaitHealth(gatewayPort,"gateway");
            for(UUID actor:List.of(A,B,C)){
                var login=request("/identity/api/v1/auth/login","POST",null,Map.of("email",email(actor),"password",password));
                status(login,200);
                String token=json.readTree(login.body()).path("access_token").asText();
                assertThat(token.isBlank()).as("Identity returned an access token").isFalse();
                tokens.put(actor,token);
            }
            resetChat();
            invitationStory();
            websocketStory();
            runCollections();
            if(cloud!=null)cloud.verifyReferencedObjectsPrivate(chatDb);
            System.out.println("EP06_RUNTIME_REAL_SERVICES_PASSED");
            } finally {
                stopApps();
                if(cloud!=null) {
                    // Capture remaining keys while the owned DB still exists. No active writer remains.
                    try {cloud.collect(chatDb);} finally {cloud.cleanup();}
                }
            }
        } finally {
            stopApps();
            if(cloud!=null)cloud.close();
            s3.stop(0);commerce.stop(0);tokens.clear();Files.deleteIfExists(keyFile);Files.deleteIfExists(privateDir);
        }
    }


    record MailLink(UUID id,String token) {
        Map<String,Object> body(){return Map.of("friend_invitation_id",id,"token",token);}
        @Override public String toString(){return "MailLink[REDACTED]";}
    }
    void invitationStory()throws Exception{
        var privateCreated=request("/wardrobe-community/api/v1/me/outfits","POST",A,
            Map.of("name","Private during invitation","creation_source","MANUAL","items",List.of(Map.of("variant_id",OUTFIT_VARIANT))));
        status(privateCreated,201);
        String privateOutfit=json.readTree(privateCreated.body()).path("outfit_id").asText();
        String ownedOutfit="/wardrobe-community/api/v1/me/outfits/"+privateOutfit;
        String invitee="ep06-new-"+UUID.randomUUID()+"@example.test";
        var sent=request("/wardrobe-community/api/v1/me/friend-invitations","POST",A,Map.of("recipient_email",invitee));
        status(sent,201);
        var stillPrivate=request(ownedOutfit+"/sharing","GET",A,null);status(stillPrivate,200);
        assertThat(json.readTree(stillPrivate.body()).path("visibility").asText()).isEqualTo("PRIVATE");
        MailLink original=mailLink(mailText(invitee));
        int count=mailCount(invitee);
        status(request("/wardrobe-community/api/v1/me/friend-invitations","POST",A,Map.of("recipient_email",invitee)),200);
        assertThat(mailCount(invitee)).as("Duplicate request does not generate another SMTP email").isEqualTo(count);
        status(request("/wardrobe-community/api/v1/me/friend-invitations/accept","POST",null,original.body()),401);
        status(request("/wardrobe-community/api/v1/me/friend-invitations/accept","POST",C,original.body()),404);
        String password="Ep06-new-"+UUID.randomUUID()+"!";
        var registration=request("/identity/api/v1/auth/register","POST",null,Map.of("email",invitee,"password",password));
        status(registration,201);
        UUID recipient=UUID.fromString(json.readTree(registration.body()).path("account_id").asText());
        var code=java.util.regex.Pattern.compile("\\b[0-9]{6}\\b").matcher(mailText(invitee));
        assertThat(code.find()).as("Private SMTP inbox contains the real verification OTP").isTrue();
        status(request("/identity/api/v1/auth/verify-email","POST",null,Map.of("email",invitee,"code",code.group())),204);
        var login=request("/identity/api/v1/auth/login","POST",null,Map.of("email",invitee,"password",password));
        status(login,200);String token=json.readTree(login.body()).path("access_token").asText();
        assertThat(!token.isBlank()).as("Verified recipient obtains a real Identity JWT").isTrue();tokens.put(recipient,token);
        var me=request("/identity/api/v1/me","GET",recipient,null);status(me,200);
        assertThat(json.readTree(me.body()).path("profile").isNull()).as("Registration did not require a profile").isTrue();
        status(request("/wardrobe-community/api/v1/me/friend-invitations/accept","POST",recipient,original.body()),200);
        String friendOutfit="/wardrobe-community/api/v1/me/friends/"+A+"/outfits/"+privateOutfit;
        status(request(friendOutfit,"GET",recipient,null),404);
        status(request(ownedOutfit+"/sharing","PATCH",A,Map.of("visibility","FRIENDS")),200);
        status(request(friendOutfit,"GET",recipient,null),200);
        var opened=request("/wardrobe-community/api/v1/me/chats","POST",A,Map.of("friend_account_id",recipient));
        status(opened,201);String chat=json.readTree(opened.body()).path("chat_session_id").asText();
        UUID message=UUID.randomUUID();
        status(request("/wardrobe-community/api/v1/chats/"+chat+"/messages","POST",A,
                Map.of("client_message_id",message,"content","Actual invitation registration and chat fixture")),201);
        status(request("/wardrobe-community/api/v1/chats/"+chat+"/messages","GET",recipient,null),200);
        status(request("/wardrobe-community/api/v1/me/friends/"+recipient,"DELETE",A,null),204);
        status(request(friendOutfit,"GET",recipient,null),404);
        status(request("/wardrobe-community/api/v1/chats/"+chat+"/messages","GET",recipient,null),404);
        var used=request("/wardrobe-community/api/v1/me/friend-invitations/accept","POST",recipient,original.body());
        status(used,409);assertThat(json.readTree(used.body()).path("code").asText()).isEqualTo("INVITATION_ALREADY_USED");
        // Reverse direction has its own sender/email cooldown; this is a new invitation, not a SQL refriend.
        status(request("/wardrobe-community/api/v1/me/friend-invitations","POST",recipient,Map.of("recipient_email",email(A))),201);
        MailLink fresh=mailLink(mailText(email(A)));
        status(request("/wardrobe-community/api/v1/me/friend-invitations/accept","POST",A,fresh.body()),200);
        status(request(friendOutfit,"GET",recipient,null),200);
        status(request(ownedOutfit,"DELETE",A,null),204);
        var restored=request("/wardrobe-community/api/v1/me/chats","POST",recipient,Map.of("friend_account_id",A));
        status(restored,200);assertThat(json.readTree(restored.body()).path("chat_session_id").asText()).isEqualTo(chat);
        var history=request("/wardrobe-community/api/v1/chats/"+chat+"/messages","GET",recipient,null);
        status(history,200);assertThat(json.readTree(history.body()).path("items").findValuesAsText("client_message_id")).contains(message.toString());
        System.out.println("EP06_RUNTIME_INVITATION_SMTP_REGISTER_VERIFY_LOGIN_ACCEPT_CHAT_REFRIEND_PASSED");
        System.out.println("EP06_RUNTIME_OUTFIT_PRIVATE_INVITATION_EXPLICIT_FRIENDS_UNFRIEND_REFRIEND_PASSED");
    }
    JsonNode mailMessages()throws Exception{
        String base="http://127.0.0.1:8025";
        var response=client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/messages?limit=100"))
                .timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
        status(response,200);JsonNode body=json.readTree(response.body());
        return body.has("messages")?body.path("messages"):body.path("Messages");
    }
    boolean addressed(JsonNode message,String email){
        JsonNode to=message.has("To")?message.path("To"):message.path("to");
        for(JsonNode recipient:to){String address=recipient.has("Address")?recipient.path("Address").asText():recipient.path("address").asText();
            if(email.equalsIgnoreCase(address))return true;}
        return false;
    }
    int mailCount(String email)throws Exception{
        int count=0;for(JsonNode message:mailMessages())if(addressed(message,email))count++;return count;
    }
    String mailText(String email)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        do{
            String id=null;Instant newest=Instant.MIN;
            for(JsonNode message:mailMessages())if(addressed(message,email)){
                Instant created=Instant.parse(message.has("Created")?message.path("Created").asText():message.path("created").asText());
                if(id==null||created.isAfter(newest)){id=message.has("ID")?message.path("ID").asText():message.path("id").asText();newest=created;}}
            if(id!=null){
                var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8025/api/v1/message/"+id))
                        .timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
                status(response,200);JsonNode body=json.readTree(response.body());
                return body.has("Text")?body.path("Text").asText():body.path("text").asText();
            }
            Thread.sleep(100);
        }while(System.nanoTime()<until);
        throw new AssertionError("No SMTP message for the private test recipient");
    }
    MailLink mailLink(String text){
        try{
            var match=java.util.regex.Pattern.compile("https?://[^\\s]+").matcher(text);
            if(!match.find())throw new IllegalArgumentException();
            String fragment=URI.create(match.group()).getRawFragment();
            var values=new HashMap<String,String>();for(String field:fragment.split("&")){String[] split=field.split("=",2);if(split.length==2)values.put(split[0],split[1]);}
            String token=values.get("token");if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException();
            return new MailLink(UUID.fromString(values.get("invitation_id")),token);
        }catch(RuntimeException invalid){throw new AssertionError("SMTP invitation link is missing or malformed; credentials omitted");}
    }
    void mailChaos(Map<String,?> chaos)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8025/api/v1/chaos"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type","application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(chaos))).build();
        status(client.send(request,HttpResponse.BodyHandlers.discarding()),200);
    }
    void clearRelationships()throws Exception{
        if(cloud!=null)cloud.verifyReferencedObjectsPrivate(wardrobe);
        try(var connection=DriverManager.getConnection(wardrobe.getJdbcUrl(),wardrobe.getUsername(),wardrobe.getPassword());var statement=connection.createStatement()){
            for(String table:List.of("chat_attachment","chat_message","chat_participant","chat_session","friend_invitation","friendship"))
                statement.executeUpdate("DELETE FROM public."+table);
        }
        objects.clear();
    }
    void runInvitationCollection(GenericContainer<?> node,String scenario,Map<String,?> values,int expectedRequests)throws Exception{
        Path privateFixture=Files.createTempFile("ep06-newman-invitation-",".json");
        try{
            Files.setPosixFilePermissions(privateFixture,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            Files.writeString(privateFixture,json.writeValueAsString(values));
            node.copyFileToContainer(MountableFile.forHostPath(privateFixture),"/tmp/invitation-runtime.json");
            var result=node.execInContainer("node","/tmp/run-newman.cjs","invitations.json",scenario);
            Files.writeString(evidence.resolve("newman-invitation-"+scenario+"-summary.txt"),result.getStdout());
            assertThat(result.getExitCode()).as("Newman invitation "+scenario+"; safe failure names in summary").isZero();
            String marker="EP06_NEWMAN_RESULT=";int at=result.getStdout().indexOf(marker);
            assertThat(at>=0).as("Newman produces a safe summary").isTrue();
            JsonNode summary=json.readTree(result.getStdout().substring(at+marker.length()).strip());
            assertThat(summary.path("requests").asInt()).as("Every selected request ran, without skipped mutations").isEqualTo(expectedRequests);
        }finally{Files.deleteIfExists(privateFixture);}
    }
    void launch(String name,String jar,int port,Map<String,String> environment)throws Exception{
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx256m","-jar",jar,"--server.port="+port)
                .directory(evidence.toFile()).redirectErrorStream(true).redirectOutput(evidence.resolve(name+"-runtime.log").toFile());
        // Real cloud credentials belong only in the Wardrobe process, not Identity/Notification/Gateway.
        builder.environment().keySet().removeIf(key->key.startsWith("AWS_") || key.startsWith("STYLEMIND_CHAT_IMAGES_"));
        builder.environment().putAll(environment);apps.add(builder.start());
    }
    void stopApps()throws Exception{
        for(int index=apps.size()-1;index>=0;index--){Process app=apps.get(index);app.destroy();if(!app.waitFor(8,TimeUnit.SECONDS))app.destroyForcibly().waitFor(3,TimeUnit.SECONDS);}
        apps.clear();
    }
    void awaitHealth(int port,String name)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(55);
        while(System.nanoTime()<until){
            try{if(client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200)return;}catch(Exception notReady){ }
            Thread.sleep(100);
        }
        throw new AssertionError(name+" did not become healthy; inspect its sanitized runtime log locally");
    }
    void seedIdentity(PostgreSQLContainer<?> db,String password)throws Exception{
        String hash="{bcrypt}"+new BCryptPasswordEncoder().encode(password);
        try(var connection=DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword())){
            for(UUID actor:List.of(A,B,C)){
                try(var s=connection.prepareStatement("INSERT INTO public.account(account_id,email,password_hash,account_status,email_verified_at,registered_at) VALUES(?,?,?,'ACTIVE',now(),now())")){
                    s.setObject(1,actor);s.setString(2,email(actor));s.setString(3,hash);s.executeUpdate();
                }
                try(var s=connection.prepareStatement("INSERT INTO public.account_role(account_id,role_code) VALUES(?,'CUSTOMER')")){s.setObject(1,actor);s.executeUpdate();}
                try(var s=connection.prepareStatement("INSERT INTO public.user_profile(account_id,display_name) VALUES(?,?)")){s.setObject(1,actor);s.setString(2,"Runtime "+actor);s.executeUpdate();}
            }
        }
    }
    void resetChat()throws Exception{
        if(cloud!=null)cloud.verifyReferencedObjectsPrivate(wardrobe);
        try(var connection=DriverManager.getConnection(wardrobe.getJdbcUrl(),wardrobe.getUsername(),wardrobe.getPassword());var statement=connection.createStatement()){
            for(String table:List.of("chat_attachment","chat_message","chat_participant","chat_session","friend_invitation","friendship"))statement.executeUpdate("DELETE FROM public."+table);
            try(var insert=connection.prepareStatement("INSERT INTO public.friendship(friendship_id,account_low_id,account_high_id,established_at,created_at,updated_at) VALUES(?,?,?,now(),now(),now())")){
                insert.setObject(1,UUID.randomUUID());insert.setObject(2,A);insert.setObject(3,B);insert.executeUpdate();
            }
        }
        objects.clear();
    }
    void restoreFriendship()throws Exception{
        try(var connection=DriverManager.getConnection(wardrobe.getJdbcUrl(),wardrobe.getUsername(),wardrobe.getPassword());
            var statement=connection.prepareStatement("UPDATE public.friendship SET unfriended_at=NULL,established_at=now(),updated_at=now() WHERE account_low_id=? AND account_high_id=?")){
            statement.setObject(1,A);statement.setObject(2,B);statement.executeUpdate();
        }
    }
    void websocketStory()throws Exception{
        var opened=request("/wardrobe-community/api/v1/me/chats","POST",A,Map.of("friend_account_id",B));status(opened,201);
        String chat=json.readTree(opened.body()).path("chat_session_id").asText();
        try(var a=socket(A);var b=socket(B)){
            a.send("SUBSCRIBE\nid:ack\ndestination:/user/queue/chats/"+chat+"/acks\n\n\u0000");
            b.send("SUBSCRIBE\nid:peer\ndestination:/topic/chats/"+chat+"\n\n\u0000");
            boolean subscribed=false;
            for(int attempt=0;attempt<5&&!subscribed;attempt++){
                String marker="ready-"+UUID.randomUUID();
                status(request("/wardrobe-community/api/v1/chats/"+chat+"/messages","POST",A,Map.of("client_message_id",UUID.randomUUID(),"content",marker)),201);
                subscribed=b.await(f->f.command().equals("MESSAGE")&&f.body().contains(marker),1000)!=null;
            }
            assertThat(subscribed).as("Peer subscription became live through Gateway").isTrue();
            UUID key=UUID.randomUUID();String text="Gateway actual Chat test";
            String frame="SEND\ndestination:/app/chats/"+chat+"/messages\ncontent-type:application/json\n\n"+json.writeValueAsString(Map.of("client_message_id",key,"content",text))+"\u0000";
            a.send(frame);
            var peer=b.await(f->f.command().equals("MESSAGE")&&f.body().contains(key.toString()),5000);
            assertThat(peer!=null).as("Peer received the saved message").isTrue();
            var ack=a.await(f->f.command().equals("MESSAGE")&&f.body().contains(key.toString()),5000);
            assertThat(ack!=null).as("Sender received private ACK").isTrue();
            JsonNode accepted=json.readTree(ack.body());
            assertThat(accepted.path("replayed").asBoolean()).isFalse();
            a.send(frame);
            var replay=a.await(f->f.command().equals("MESSAGE")&&f.body().contains(key.toString()),5000);
            assertThat(replay!=null).isTrue();assertThat(json.readTree(replay.body()).path("replayed").asBoolean()).isTrue();
            assertThat(b.await(f->f.command().equals("MESSAGE")&&f.body().contains(key.toString()),250)).isNull();
            UUID imageKey=UUID.randomUUID();
            var imageUpload=uploadImage(chat,imageKey);status(imageUpload,201);
            var imageEvent=b.await(f->f.command().equals("MESSAGE")&&f.body().contains(imageKey.toString()),5000);
            assertThat(imageEvent!=null).as("Peer receives image metadata through Gateway after commit").isTrue();
            JsonNode imageMessage=json.readTree(imageUpload.body());
            assertThat(json.readTree(imageEvent.body())).isEqualTo(imageMessage);
            assertThat(imageEvent.body()).doesNotContain("chat/v1/","storage_object_key");
            String attachment=imageMessage.path("attachments").get(0).path("chat_attachment_id").asText();
            var imageRead=readImage(chat,attachment,B);status(imageRead,200);
            assertThat(imageRead.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(imageRead.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
            assertThat(imageRead.body()).isEqualTo(pngFixture());
            status(readImage(chat,attachment,C),404);
            int objectCount=objects.size();
            String cloudVersion=cloud==null?null:cloud.currentVersion(wardrobe,attachment);
            var imageReplay=uploadImage(chat,imageKey);status(imageReplay,200);
            assertThat(json.readTree(imageReplay.body())).isEqualTo(imageMessage);
            if(cloud==null)assertThat(objects).hasSize(objectCount);
            else assertThat(cloudVersion.equals(cloud.currentVersion(wardrobe,attachment))).as("Cloud retry reuses the stored object version").isTrue();
            assertThat(b.await(f->f.command().equals("MESSAGE")&&f.body().contains(imageKey.toString()),250)).isNull();
            try(var outsider=socket(C)){
                outsider.send("SUBSCRIBE\nid:forbidden\ndestination:/topic/chats/"+chat+"\n\n\u0000");
                assertThat(outsider.await(f->f.command().equals("ERROR"),5000)!=null).as("Outsider subscription rejected").isTrue();
            }
            status(request("/wardrobe-community/api/v1/me/friends/"+B,"DELETE",A,null),204);
            for(UUID actor:List.of(A,B))status(request("/wardrobe-community/api/v1/chats/"+chat+"/messages","GET",actor,null),404);
            for(UUID actor:List.of(A,B))status(readImage(chat,attachment,actor),404);
            a.send(frame);assertThat(a.await(f->f.command().equals("ERROR"),5000)!=null).as("Existing socket cannot send after unfriend").isTrue();
            b.send(frame);assertThat(b.await(f->f.command().equals("ERROR"),5000)!=null).as("Both members lose send permission").isTrue();
            restoreFriendship();
            var restored=request("/wardrobe-community/api/v1/me/chats","POST",B,Map.of("friend_account_id",A));status(restored,200);
            assertThat(json.readTree(restored.body()).path("chat_session_id").asText()).isEqualTo(chat);
            var history=request("/wardrobe-community/api/v1/chats/"+chat+"/messages","GET",B,null);status(history,200);
            assertThat(json.readTree(history.body()).path("items").findValuesAsText("client_message_id")).contains(key.toString());
            status(readImage(chat,attachment,B),200);
        }
        System.out.println("EP06_RUNTIME_WS_GATEWAY_JWT_IMAGES_HISTORY_UNFRIEND_REFRIEND_PASSED");
    }
    byte[] pngFixture()throws Exception{var out=new java.io.ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
    HttpResponse<String> uploadImage(String chat,UUID key)throws Exception{
        String boundary="runtime-"+UUID.randomUUID();var out=new java.io.ByteArrayOutputStream();
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"message\"\r\nContent-Type: application/json\r\n\r\n"+json.writeValueAsString(Map.of("client_message_id",key,"content","runtime image"))+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"images\"; filename=\"fixture.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(pngFixture());out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+gatewayPort+"/wardrobe-community/api/v1/chats/"+chat+"/messages"))
                .timeout(Duration.ofSeconds(cloud==null?10:45)).header("Authorization","Bearer "+tokens.get(A)).header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<byte[]> readImage(String chat,String attachment,UUID actor)throws Exception{
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+gatewayPort+"/wardrobe-community/api/v1/chats/"+chat+"/attachments/"+attachment+"/content"))
                .timeout(Duration.ofSeconds(cloud==null?10:45)).header("Authorization","Bearer "+tokens.get(actor)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    void runCollections()throws Exception{
        String runner=System.getenv("EP06_TEST_RUNNER_CONTAINER");
        if(runner==null||runner.isBlank())throw new IllegalStateException("Use verify-ep06-chat-runtime.ps1 so Newman shares only this disposable runner network");
        Path fixture=evidence.resolve("chat-image.png");ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",fixture.toFile());
        try(var node=new GenericContainer<>("node:22-alpine").withNetworkMode("container:"+runner)
            .withEnv("EP06_BASE_URL","http://127.0.0.1:"+gatewayPort).withEnv("EP06_TOKEN_A",tokens.get(A)).withEnv("EP06_TOKEN_B",tokens.get(B)).withEnv("EP06_TOKEN_C",tokens.get(C))
            .withEnv("EP06_EMAIL_A",email(A)).withEnv("EP06_EMAIL_B",email(B))
            .withEnv("EP06_ACCOUNT_A",A.toString()).withEnv("EP06_ACCOUNT_B",B.toString()).withEnv("EP06_OUTFIT_VARIANT",OUTFIT_VARIANT.toString())
            .withCopyFileToContainer(MountableFile.forHostPath(repo.resolve("scripts/ep06-chat-runtime/run-newman.cjs")),"/tmp/run-newman.cjs")
            .withCopyFileToContainer(MountableFile.forHostPath(fixture),"/tmp/chat-image.png")
            .withCopyFileToContainer(MountableFile.forHostPath(repo.resolve("postman/StyleMind-Wardrobe-Manage-Friends.postman_collection.json")),"/tmp/friends.json")
            .withCopyFileToContainer(MountableFile.forHostPath(repo.resolve("postman/StyleMind-Wardrobe-Friend-Invitations.postman_collection.json")),"/tmp/invitations.json")
            .withCopyFileToContainer(MountableFile.forHostPath(repo.resolve("postman/StyleMind-Wardrobe-Chat-Sessions.postman_collection.json")),"/tmp/sessions.json")
            .withCopyFileToContainer(MountableFile.forHostPath(repo.resolve("postman/StyleMind-Wardrobe-Chat-Messages.postman_collection.json")),"/tmp/messages.json")
            .withCopyFileToContainer(MountableFile.forHostPath(repo.resolve("postman/StyleMind-Wardrobe-Chat-Images.postman_collection.json")),"/tmp/images.json")
            .withCopyFileToContainer(MountableFile.forHostPath(repo.resolve("postman/StyleMind-EP06-Share-Outfits.postman_collection.json")),"/tmp/outfits.json")
            .withCommand("sh","-c","npm --silent install --prefix /tmp/newman newman@6.2.1 && echo EP06_NEWMAN_READY && tail -f /dev/null")
            .waitingFor(Wait.forLogMessage(".*EP06_NEWMAN_READY.*",1).withStartupTimeout(Duration.ofMinutes(3)))){
            node.start();
            for(String collection:List.of("friends","sessions","messages","images","outfits")){
                resetChat();
                var result=node.execInContainer("node","/tmp/run-newman.cjs",collection+".json");
                Files.writeString(evidence.resolve("newman-"+collection+"-summary.txt"),result.getStdout());
                assertThat(result.getExitCode()).as("Newman "+collection+"; safe failure names are in its summary").isZero();
                assertThat(result.getStdout()).contains("EP06_NEWMAN_RESULT=");
            }
            clearRelationships();
            runInvitationCollection(node,"main",Map.of(),8);
            clearRelationships();
            mailChaos(Map.of("Recipient",Map.of("ErrorCode",451,"Probability",100)));
            HttpResponse<String> failed;
            try {failed=request("/wardrobe-community/api/v1/me/friend-invitations","POST",A,Map.of("recipient_email",email(B)));}
            finally {mailChaos(Map.of());}
            status(failed,503);
            String retryId=json.readTree(failed.body()).path("friend_invitation_id").asText();
            assertThat(!retryId.isBlank()).as("Failed send retains invitation metadata").isTrue();
            Thread.sleep(31000); // Exercise the real 30-second business cooldown, without changing the DB clock/state.
            runInvitationCollection(node,"retry",Map.of("retryInvitationId",retryId),1);
            MailLink link=mailLink(mailText(email(B)));
            assertThat(link.id().toString()).isEqualTo(retryId);
            runInvitationCollection(node,"link",Map.of("linkInvitationId",link.id().toString(),"invitationEmailToken",link.token()),1);
        }
    }
    HttpResponse<String> request(String path,String method,UUID actor,Object body)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+gatewayPort+path)).timeout(Duration.ofSeconds(10));
        if(actor!=null)builder.header("Authorization","Bearer "+tokens.get(actor));
        if(body!=null)builder.header("Content-Type","application/json");
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    void status(HttpResponse<?> response,int expected){assertThat(response.statusCode()).as("HTTP status; response body intentionally omitted").isEqualTo(expected);}
    Socket socket(UUID actor)throws Exception{
        var socket=new Socket();socket.ws=client.newWebSocketBuilder().header("Origin","http://localhost:3000").subprotocols("v12.stomp")
                .buildAsync(URI.create("ws://127.0.0.1:"+gatewayPort+"/wardrobe-community/ws/chat"),socket).get(5,TimeUnit.SECONDS);
        socket.send("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer "+tokens.get(actor)+"\n\n\u0000");
        assertThat(socket.await(f->f.command().equals("CONNECTED"),5000)!=null).as("Real Identity JWT accepted by Chat through Gateway").isTrue();return socket;
    }
    HttpServer fakeStorage()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            String key=exchange.getRequestURI().getPath();
            switch(exchange.getRequestMethod()){
                case "PUT"->{objects.put(key,new Stored(exchange.getRequestBody().readAllBytes(),exchange.getRequestHeaders().getFirst("Content-Type")));exchange.sendResponseHeaders(200,-1);}
                case "GET"->{Stored stored=objects.get(key);if(stored==null)exchange.sendResponseHeaders(404,-1);else{exchange.getResponseHeaders().set("Content-Type",stored.mime());exchange.sendResponseHeaders(200,stored.bytes().length);exchange.getResponseBody().write(stored.bytes());}}
                case "DELETE"->{objects.remove(key);exchange.sendResponseHeaders(204,-1);}
                default->exchange.sendResponseHeaders(405,-1);
            }
            exchange.close();
        });server.start();return server;
    }
    /** HTTP catalog fixture only: Commerce itself is not implemented or verified by this harness. */
    HttpServer fixtureCatalog(String serviceToken)throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/v1/variants/batch-lookup",exchange->{
            try {
                if(!"POST".equals(exchange.getRequestMethod())){exchange.sendResponseHeaders(405,-1);return;}
                if(!serviceToken.equals(exchange.getRequestHeaders().getFirst("X-StyleMind-Service-Token"))){exchange.sendResponseHeaders(401,-1);return;}
                JsonNode body=json.readTree(exchange.getRequestBody());
                boolean known=false;for(JsonNode id:body.path("variant_ids"))if(OUTFIT_VARIANT.toString().equals(id.asText()))known=true;
                Map<String,Object> item=Map.ofEntries(Map.entry("variant_id",OUTFIT_VARIANT),Map.entry("product_id",new UUID(0,201)),
                    Map.entry("product_name","HTTP catalog fixture"),Map.entry("product_status","ACTIVE"),Map.entry("variant_status","ACTIVE"),
                    Map.entry("color","White"),Map.entry("size","M"),Map.entry("unit_price_vnd",100000),Map.entry("purchasable",true));
                byte[] bytes=json.writeValueAsBytes(Map.of("items",known?List.of(item):List.of()));
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
            }finally{exchange.close();}
        });server.start();return server;
    }
    static int port()throws Exception{try(var socket=new ServerSocket(0)){return socket.getLocalPort();}}
    static String email(UUID actor){return "ep06-"+actor+"@example.test";}
    static String required(String name){String value=System.getProperty(name);if(value==null||value.isBlank())throw new IllegalStateException("Runtime wrapper must supply "+name);return value;}

    record Frame(String command,String body) {
        static Frame parse(String raw){int split=raw.indexOf("\n\n");int first=raw.indexOf('\n');return new Frame(first<0?raw:raw.substring(0,first),split<0?"":raw.substring(split+2));}
        @Override public String toString(){return "Frame[command="+command+",body=[REDACTED]]";}
    }
    static class Socket implements WebSocket.Listener,AutoCloseable{
        WebSocket ws;final StringBuilder buffer=new StringBuilder();final BlockingQueue<Frame> frames=new LinkedBlockingQueue<>();final List<Frame> pending=new ArrayList<>();
        @Override public void onOpen(WebSocket socket){socket.request(1);}
        @Override public CompletionStage<?> onText(WebSocket socket,CharSequence text,boolean last){buffer.append(text);int end;while((end=buffer.indexOf("\u0000"))>=0){String raw=buffer.substring(0,end).stripLeading();buffer.delete(0,end+1);if(!raw.isEmpty())frames.add(Frame.parse(raw));}socket.request(1);return CompletableFuture.completedFuture(null);}
        @Override public void onError(WebSocket socket,Throwable error){frames.add(new Frame("TRANSPORT_ERROR",""));}
        void send(String frame){ws.sendText(frame,true).join();}
        Frame await(Predicate<Frame> predicate,long millis)throws Exception{
            for(int i=0;i<pending.size();i++)if(predicate.test(pending.get(i)))return pending.remove(i);
            long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);
            while(System.nanoTime()<until){Frame frame=frames.poll(30,TimeUnit.MILLISECONDS);if(frame!=null){if(predicate.test(frame))return frame;pending.add(frame);}}
            return null;
        }
        @Override public void close(){if(ws!=null)ws.abort();}
    }
}
