package com.stylemind.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.stylemind.notification.dto.OutfitShareInvitationRequest;
import com.stylemind.notification.service.OutfitShareInvitationSender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.health.db.enabled=false",
        "management.health.mail.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1"
})
class InternalOutfitShareInvitationHttpSecurityTests {

    private static final String SERVICE_TOKEN = randomSecret();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String PATH = "/internal/v1/outfit-share-invitations";
    private static final String BODY = body("friend@example.test", "Linh Tran", "Weekend look",
            "http://localhost:3000/shared/outfits/6f1c1d1e-0000-4000-8000-000000000001");

    @LocalServerPort
    private int port;

    @MockitoBean
    private OutfitShareInvitationSender sender;

    @DynamicPropertySource
    static void serviceSecret(DynamicPropertyRegistry registry) {
        registry.add("stylemind.security.service-token", () -> SERVICE_TOKEN);
    }

    @Test
    void validRequestWithTheServiceTokenIsAcceptedAndSent() throws Exception {
        assertThat(request("POST", SERVICE_TOKEN, "application/json", BODY)).isEqualTo(202);
        var sent = org.mockito.ArgumentCaptor.forClass(OutfitShareInvitationRequest.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().recipientEmail()).isEqualTo("friend@example.test");
        assertThat(sent.getValue().sharerName()).isEqualTo("Linh Tran");
        assertThat(sent.getValue().outfitName()).isEqualTo("Weekend look");
    }

    @Test
    void missingOrWrongTokenIs401AndOtherMethodsNeverSend() throws Exception {
        assertThat(request("POST", null, "application/json", BODY)).isEqualTo(401);
        assertThat(request("POST", "wrong-service-token-value-that-is-invalid", "application/json", BODY))
                .isEqualTo(401);
        verifyNoInteractions(sender);
        for (String method : List.of("GET", "PUT", "DELETE")) {
            assertThat(request(method, SERVICE_TOKEN, "application/json", BODY)).isNotEqualTo(202);
            verifyNoInteractions(sender);
        }
    }

    @Test
    void invalidBodiesAndContentTypesNeverInvokeTheSender() throws Exception {
        String url = "http://localhost:3000/shared/outfits/x";
        List<String> invalidBodies = List.of(
                "null", "{", "{}",
                body("bad", "Linh", "Look", url),
                body("", "Linh", "Look", url),
                body("friend@example.test", "", "Look", url),
                body("friend@example.test", "   ", "Look", url),
                body("friend@example.test", "x".repeat(101), "Look", url),
                body("friend@example.test", "Linh", "", url),
                body("friend@example.test", "Linh", "x".repeat(101), url),
                body("friend@example.test", "Linh", "Look", ""),
                body("friend@example.test", "Linh", "Look", "javascript:alert(1)"),
                body("friend@example.test", "Linh", "Look", "ftp://example.test/file"),
                body("friend@example.test", "Linh", "Look", "/relative/path"),
                body("friend@example.test", "Linh", "Look", "http://example.test/a b"),
                body("friend@example.test", "Linh", "Look", "http://example.test/" + "a".repeat(2048)));
        for (String invalid : invalidBodies) {
            reset(sender);
            assertThat(request("POST", SERVICE_TOKEN, "application/json", invalid)).as(invalid).isEqualTo(400);
            verifyNoInteractions(sender);
        }
        assertThat(request("POST", SERVICE_TOKEN, "text/plain", BODY)).isEqualTo(415);
        verifyNoInteractions(sender);
    }

    private static String body(String email, String sharer, String outfit, String url) {
        return "{\"recipientEmail\":\"" + email + "\",\"sharerName\":\"" + sharer + "\",\"outfitName\":\""
                + outfit + "\",\"outfitUrl\":\"" + url + "\"}";
    }

    private int request(String method, String token, String contentType, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + PATH))
                .header("Content-Type", contentType)
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (token != null) {
            builder.header("X-StyleMind-Service-Token", token);
        }
        HttpResponse<Void> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
