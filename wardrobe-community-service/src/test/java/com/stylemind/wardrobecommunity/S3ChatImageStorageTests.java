package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.chat.ChatImage;
import com.stylemind.wardrobecommunity.storage.chat.S3ChatImageStorage;
import com.stylemind.wardrobecommunity.validation.chat.ChatImageRequestValidator;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;

class S3ChatImageStorageTests {
    HttpServer server;
    String oldKey,oldSecret;
    final Map<String,byte[]> objects=new ConcurrentHashMap<>();
    final AtomicBoolean signed=new AtomicBoolean();
    @BeforeEach void setup()throws Exception {
        oldKey=System.getProperty("aws.accessKeyId");oldSecret=System.getProperty("aws.secretAccessKey");
        System.setProperty("aws.accessKeyId","unit-test-access");System.setProperty("aws.secretAccessKey","unit-test-secret-not-real");
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    }
    @AfterEach void stop(){
        if(server!=null)server.stop(0);
        restore("aws.accessKeyId",oldKey);restore("aws.secretAccessKey",oldSecret);
    }
    @Test void realSdkSignsPutGetDeleteAndKeepsFileBytesAndMimePrivate()throws Exception {
        server.createContext("/",request->{
            String authorization=request.getRequestHeaders().getFirst("Authorization");
            signed.set(authorization!=null&&authorization.startsWith("AWS4-HMAC-SHA256 "));
            assertThat(request.getRequestHeaders().getFirst("x-amz-acl")).isNull();
            String key=request.getRequestURI().getPath();
            switch(request.getRequestMethod()){
                case "PUT" -> {objects.put(key,request.getRequestBody().readAllBytes());request.sendResponseHeaders(200,-1);}
                case "GET" -> {byte[] bytes=objects.get(key);request.getResponseHeaders().set("Content-Type","image/png");request.sendResponseHeaders(200,bytes.length);request.getResponseBody().write(bytes);}
                case "DELETE" -> {objects.remove(key);request.sendResponseHeaders(204,-1);}
                default -> request.sendResponseHeaders(405,-1);
            }
            request.close();
        });server.start();
        byte[] bytes=ChatImageRequestValidatorTests.picture("png");
        try(var storage=new CloseableStorage(configured())){
            storage.value.put("chat/v1/test-image.png",new ChatImage(bytes,"image/png",ChatImageRequestValidator.sha256(bytes)));
            assertThat(storage.value.get("chat/v1/test-image.png").bytes()).isEqualTo(bytes);
            assertThat(signed).isTrue();assertThat(objects).hasSize(1);
            storage.value.delete("chat/v1/test-image.png");assertThat(objects).isEmpty();
        }
    }
    @Test void missingConfigurationFailsClosedWithoutSelectingAnyLocalStorage(){
        try(var storage=new CloseableStorage(new S3ChatImageStorage(false,"","",""))){
            assertUnavailable(storage.value::requireAvailable);
            assertUnavailable(()->storage.value.get("chat/v1/test"));
            assertUnavailable(()->storage.value.delete("chat/v1/test"));
        }
    }
    @Test void providerFailureIsSanitizedWithoutPropagatingHeadersOrRequestBody(){
        server.createContext("/",request->{byte[] body="<Error><Code>AccessDenied</Code><Message>private-provider-marker</Message></Error>".getBytes(java.nio.charset.StandardCharsets.UTF_8);request.sendResponseHeaders(403,body.length);request.getResponseBody().write(body);request.close();});server.start();
        try(var storage=new CloseableStorage(configured())){
            assertThatThrownBy(()->storage.value.get("chat/v1/test")).isInstanceOfSatisfying(ApiOperationException.class,error->{
                assertThat(error.code()).isEqualTo("CHAT_IMAGE_STORAGE_UNAVAILABLE");
                assertThat(error.getMessage()).doesNotContain("private-provider-marker","unit-test-access","unit-test-secret-not-real");
                assertThat(error.getCause()).isNull();
            });
        }
    }
    @Test void oversizedProviderResponseIsRejectedBeforeReadingUnboundedBytes(){
        server.createContext("/",request->{request.getResponseHeaders().set("Content-Type","image/png");request.sendResponseHeaders(200,ChatImageRequestValidator.MAX_BYTES+1L);request.close();});server.start();
        try(var storage=new CloseableStorage(configured())){assertUnavailable(()->storage.value.get("chat/v1/test"));}
    }
    S3ChatImageStorage configured(){return new S3ChatImageStorage(true,"private-test","http://127.0.0.1:"+server.getAddress().getPort(),"us-east-1");}
    void assertUnavailable(Runnable operation){assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiOperationException.class,error->assertThat(error.code()).isEqualTo("CHAT_IMAGE_STORAGE_UNAVAILABLE"));}
    void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
    static class CloseableStorage implements AutoCloseable{final S3ChatImageStorage value;CloseableStorage(S3ChatImageStorage value){this.value=value;}public void close(){value.close();}}
}
