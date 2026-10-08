package com.stylemind.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.stylemind.notification.dto.EmailVerificationRequest;
import com.stylemind.notification.service.VerificationEmailSender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
class InternalEmailVerificationHttpSecurityTests {

    private static final String SERVICE_TOKEN = randomSecret();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String BODY = "{\"recipientEmail\":\"register-test@example.test\","
            + "\"verificationCode\":\"012345\"}";
    private static final String CANONICAL_PATH = "/internal/v1/email-verifications";
    private static final List<String> CANDIDATE_PATHS = List.of(
            CANONICAL_PATH,
            "/internal/v1/%65mail-verifications",
            "/internal/v1/email%2dverifications");

    @LocalServerPort
    private int port;

    @MockitoBean
    private VerificationEmailSender sender;

    @DynamicPropertySource
    static void serviceSecret(DynamicPropertyRegistry registry) {
        registry.add("stylemind.security.service-token", () -> SERVICE_TOKEN);
    }

    @Test
    void embeddedServerProtectsEveryMvcAcceptedEndpointPathAndNeverSendsForOtherMethods() throws Exception {
        Set<String> mappedPaths = new LinkedHashSet<>();
        for (String path : CANDIDATE_PATHS) {
            reset(sender);
            int authorizedStatus = request(path, "POST", SERVICE_TOKEN);
            if (authorizedStatus == 404 || authorizedStatus == 400) {
                verifyNoInteractions(sender);
                continue;
            }

            assertThat(authorizedStatus).as("valid token request to %s", path).isEqualTo(202);
            mappedPaths.add(path);
            verify(sender).send(any());

            reset(sender);
            assertThat(request(path, "POST", null)).as("missing token on %s", path).isEqualTo(401);
            verifyNoInteractions(sender);

            reset(sender);
            assertThat(request(path, "POST", "wrong-service-token-value-that-is-invalid"))
                    .as("wrong token on %s", path).isEqualTo(401);
            verifyNoInteractions(sender);

            for (String method : List.of("GET", "PUT", "DELETE")) {
                reset(sender);
                int methodStatus = request(path, method, SERVICE_TOKEN);
                assertThat(methodStatus).as("%s must not invoke sender for %s", method, path)
                        .isNotEqualTo(202);
                verifyNoInteractions(sender);
            }
        }

        assertThat(mappedPaths).contains(CANONICAL_PATH, "/internal/v1/%65mail-verifications",
                "/internal/v1/email%2dverifications");
    }

    @Test
    void invalidBodiesAndContentTypesNeverInvokeTheEmailSender() throws Exception {
        List<String> invalidBodies = List.of(
                "null", "{", "{}", "{\"recipientEmail\":null,\"verificationCode\":\"012345\"}",
                "{\"recipientEmail\":\"\",\"verificationCode\":\"012345\"}",
                "{\"recipientEmail\":\"   \",\"verificationCode\":\"012345\"}",
                "{\"recipientEmail\":\"bad\",\"verificationCode\":\"012345\"}",
                "{\"recipientEmail\":[],\"verificationCode\":\"012345\"}",
                "{\"recipientEmail\":\"a@example.test\"}",
                "{\"recipientEmail\":\"a@example.test\",\"verificationCode\":null}",
                "{\"recipientEmail\":\"a@example.test\",\"verificationCode\":\"\"}",
                "{\"recipientEmail\":\"a@example.test\",\"verificationCode\":\"   \"}",
                "{\"recipientEmail\":\"a@example.test\",\"verificationCode\":\"12345\"}",
                "{\"recipientEmail\":\"a@example.test\",\"verificationCode\":\"1234567\"}",
                "{\"recipientEmail\":\"a@example.test\",\"verificationCode\":\"12a345\"}",
                "{\"recipientEmail\":\"a@example.test\",\"verificationCode\":[]}");
        for (String body : invalidBodies) {
            reset(sender);
            assertThat(request(CANONICAL_PATH, "POST", SERVICE_TOKEN, "application/json", body))
                    .isEqualTo(400);
            verifyNoInteractions(sender);
        }

        reset(sender);
        assertThat(request(CANONICAL_PATH, "POST", SERVICE_TOKEN, "text/plain", BODY)).isEqualTo(415);
        verifyNoInteractions(sender);

        reset(sender);
        assertThat(request(CANONICAL_PATH, "POST", SERVICE_TOKEN, "application/json", null)).isEqualTo(400);
        verifyNoInteractions(sender);
    }

    @Test
    void extraClientFieldsCannotChangeTheBoundRecipientAndLeadingZeroCodeStaysAString() throws Exception {
        String body = "{\"recipientEmail\":\"register-a@example.test\",\"verificationCode\":\"012345\","
                + "\"recipient_email\":\"register-b@example.test\",\"account_id\":\"other-account\","
                + "\"role\":\"ADMIN\",\"account_status\":\"ACTIVE\",\"expires_at\":\"2099-01-01T00:00:00Z\","
                + "\"failed_attempts\":0,\"revoked_at\":null}";
        reset(sender);
        assertThat(request(CANONICAL_PATH, "POST", SERVICE_TOKEN, "application/json", body)).isEqualTo(202);
        var sent = org.mockito.ArgumentCaptor.forClass(EmailVerificationRequest.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().recipientEmail()).isEqualTo("register-a@example.test");
        assertThat(sent.getValue().verificationCode()).isEqualTo("012345");
    }

    @Test
    void overlongEmailBehaviorIsRecordedWithoutAssumingAnUndocumentedMaximum() throws Exception {
        String overlongButStructured = "a".repeat(64) + "@" + "b".repeat(63) + "."
                + "c".repeat(63) + "." + "d".repeat(62);
        String body = "{\"recipientEmail\":\"" + overlongButStructured
                + "\",\"verificationCode\":\"012345\"}";
        reset(sender);
        int actualStatus = request(CANONICAL_PATH, "POST", SERVICE_TOKEN, "application/json", body);
        assertThat(actualStatus).isIn(202, 400);
        if (actualStatus == 202) {
            verify(sender).send(any());
        } else {
            verifyNoInteractions(sender);
        }
        System.out.println("AUDIT_UNSPECIFIED_MAX_EMAIL " + CANONICAL_PATH + " " + actualStatus);
    }

    private int request(String path, String method, String token) throws Exception {
        return request(path, method, token, "application/json", BODY);
    }

    private int request(String path, String method, String token, String contentType, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", contentType)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
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
