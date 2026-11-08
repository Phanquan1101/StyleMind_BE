package com.stylemind.notification;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "management.health.db.enabled=false", "management.health.mail.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.mail.properties.mail.smtp.connectiontimeout=1000",
        "spring.mail.properties.mail.smtp.timeout=1000"})
class InternalFriendInvitationHttpSmtpTests {
    static final String PATH="/internal/v1/friend-invitations";
    static final String TOKEN=Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    static final HttpClient HTTP=HttpClient.newHttpClient();
    static final ObjectMapper JSON=new ObjectMapper();
    static final SmtpRecorder SMTP=new SmtpRecorder();
    static final String BODY="{\"recipientEmail\":\"friend@example.test\",\"senderName\":\"Linh Trần\","
            +"\"invitationUrl\":\"https://app.example.test/invitations#fixture-token\",\"expiresAt\":\"2026-11-04T01:02:03Z\"}";
    @LocalServerPort int port;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry props) {
        props.add("stylemind.security.service-token",()->TOKEN);
        props.add("spring.mail.host",()->"127.0.0.1");
        props.add("spring.mail.port",()->SMTP.server.getLocalPort());
    }
    @BeforeEach void reset(){SMTP.mode.set("ACCEPT");SMTP.messages.clear();}
    @AfterAll static void close()throws Exception{SMTP.close();}

    @Test void authorizedRequestReallySendsUtf8SmtpAndReturns202NoStore()throws Exception{
        var response=call(PATH,"POST",TOKEN,BODY);
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        byte[] bytes=SMTP.messages.poll(3,TimeUnit.SECONDS);
        assertThat(bytes).isNotNull();
        var mail=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(bytes));
        assertThat(mail.getSubject()).isEqualTo("Lời mời kết bạn trên StyleMind");
        assertThat(FriendInvitationEmailServiceTests.part(mail,"text/plain"))
                .contains("Linh Trần","fixture-token","xác minh email","2026-11-04T01:02:03Z");
    }

    @Test void missingWrongOrOnlyBearerTokenNeverSendsMail()throws Exception{
        int before=SMTP.received.get();
        for(String token:new String[]{"","incorrect-token"}){
            var response=call(PATH,"POST",token,BODY);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(JSON.readTree(response.body()).path("code").asText()).isEqualTo("INTERNAL_SERVICE_UNAUTHORIZED");
        }
        assertThat(SMTP.received.get()).isEqualTo(before);
    }

    @Test void encodedMappedPathsHaveSameTokenBoundaryAndOtherMethodsNeverSend()throws Exception{
        for(String path:List.of(PATH,"/internal/v1/%66riend-invitations","/internal/v1/friend%2dinvitations")){
            int status=call(path,"POST",TOKEN,BODY).statusCode();
            if(status==400||status==404)continue;
            assertThat(status).isEqualTo(202);
            assertThat(call(path,"POST","",BODY).statusCode()).isEqualTo(401);
        }
        int before=SMTP.received.get();
        for(String method:List.of("GET","PUT","DELETE"))assertThat(call(PATH,method,TOKEN,BODY).statusCode()).isNotEqualTo(202);
        assertThat(SMTP.received.get()).isEqualTo(before);
    }

    @Test void invalidPayloadsAndUrlSchemesNeverSendMail()throws Exception{
        int before=SMTP.received.get();
        for(String body:List.of("null","{}","{",BODY.replace("friend@example.test","bad"),
                BODY.replace("Linh Trần"," "),BODY.replace("https://app.example.test/invitations#fixture-token","javascript:alert(1)"),
                BODY.replace("2026-11-04T01:02:03Z","invalid-date"),
                BODY.replace("\"expiresAt\":\"2026-11-04T01:02:03Z\"","\"expiresAt\":null"))){
            assertThat(call(PATH,"POST",TOKEN,body).statusCode()).isEqualTo(400);
        }
        assertThat(SMTP.received.get()).isEqualTo(before);
    }

    @Test void actualSmtpRejectAndTimeoutHaveDistinctSanitizedResponses()throws Exception{
        SMTP.mode.set("REJECT");
        var failed=call(PATH,"POST",TOKEN,BODY);
        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(JSON.readTree(failed.body()).path("code").asText()).isEqualTo("EMAIL_DELIVERY_FAILED");
        SMTP.mode.set("TIMEOUT");
        var unknown=call(PATH,"POST",TOKEN,BODY);
        assertThat(unknown.statusCode()).isEqualTo(504);
        assertThat(JSON.readTree(unknown.body()).path("code").asText()).isEqualTo("EMAIL_DELIVERY_UNKNOWN");
        assertThat(failed.body()+unknown.body()).doesNotContain("friend@example.test","fixture-token","SMTP test rejection");
    }

    @Test void openApiDocumentsServiceTokenAndAllDeliveryOutcomes()throws Exception{
        var response=HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v3/api-docs")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var operation=JSON.readTree(response.body()).path("paths").path(PATH).path("post");
        assertThat(operation.path("security").toString()).contains("serviceToken").doesNotContain("bearerAuth");
        for(String status:List.of("202","400","401","503","504"))assertThat(operation.path("responses").has(status)).isTrue();
    }

    HttpResponse<String> call(String path,String method,String token,String body)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
        if(!token.isEmpty())builder.header("X-StyleMind-Service-Token",token);
        return HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }

    /** SMTP test server only. Does not contact an external mailbox or persist mail. */
    static final class SmtpRecorder implements AutoCloseable{
        final ServerSocket server;
        final AtomicReference<String> mode=new AtomicReference<>("ACCEPT");
        final AtomicInteger received=new AtomicInteger();
        final BlockingQueue<byte[]> messages=new LinkedBlockingQueue<>();
        final ExecutorService handlers=Executors.newVirtualThreadPerTaskExecutor();
        final Thread worker;
        SmtpRecorder(){
            try{server=new ServerSocket(0,50,InetAddress.getLoopbackAddress());}
            catch(IOException e){throw new UncheckedIOException(e);}
            worker=new Thread(this::run,"ep06-smtp-test");worker.setDaemon(true);worker.start();
        }
        void run(){
            while(!server.isClosed()){
                try {
                    Socket socket=server.accept();
                    handlers.submit(()->handle(socket));
                } catch(IOException ignored) { }
            }
        }
        void handle(Socket socket) {
                try(socket){
                    socket.setSoTimeout(3000);
                    var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    var writer=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
                    send(writer,"220 smtp-test ESMTP");
                    String line;
                    while((line=reader.readLine())!=null){
                        String upper=line.toUpperCase(Locale.ROOT);
                        if(upper.startsWith("EHLO")){send(writer,"250-smtp-test");send(writer,"250 8BITMIME");}
                        else if(upper.startsWith("RCPT")&&mode.get().equals("REJECT"))send(writer,"550 SMTP test rejection");
                        else if(upper.equals("DATA")){
                            send(writer,"354 End with dot");var body=new StringBuilder();
                            while((line=reader.readLine())!=null&&!line.equals(".")){
                                if(line.startsWith(".."))line=line.substring(1);
                                body.append(line).append("\r\n");
                            }
                            received.incrementAndGet();messages.offer(body.toString().getBytes(StandardCharsets.UTF_8));
                            if(mode.get().equals("TIMEOUT"))Thread.sleep(2500);
                            send(writer,"250 accepted");
                        }else if(upper.equals("QUIT")){send(writer,"221 bye");break;}
                        else send(writer,"250 OK");
                    }
                }catch(IOException ignored){ }catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}
        }
        static void send(BufferedWriter writer,String line)throws IOException{writer.write(line+"\r\n");writer.flush();}
        @Override public void close()throws Exception{server.close();worker.interrupt();handlers.shutdownNow();worker.join(1500);}
    }
}
