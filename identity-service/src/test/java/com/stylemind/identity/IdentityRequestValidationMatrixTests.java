package com.stylemind.identity;

import com.stylemind.identity.dto.login.LoginRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.stylemind.identity.dto.register.RegisterRequest;
import com.stylemind.identity.service.emailverification.EmailVerificationService;
import com.stylemind.identity.service.login.LoginService;
import com.stylemind.identity.service.passwordreset.PasswordResetService;
import com.stylemind.identity.service.session.RefreshSessionService;
import com.stylemind.identity.service.register.RegisterService;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** HTTP DTO-validation probes. Service mocks prove invalid bodies stop before application side effects. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "management.health.db.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1"
})
@AutoConfigureMockMvc
class IdentityRequestValidationMatrixTests {

    private static final String ROOT = "/api/v1/auth";
    private static final String A = "validation-a@example.test";
    private static final String B = "validation-b@example.test";
    private static final String PASSWORD = "Valid-password-123";
    private static final String RESET_TOKEN = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[32]);
    private static final String REFRESH_TOKEN = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[32]);
    private static final List<String> ROUTES = List.of(
            ROOT + "/register", ROOT + "/verify-email", ROOT + "/resend-email-verification",
            ROOT + "/login", ROOT + "/refresh", ROOT + "/logout", ROOT + "/forgot-password",
            ROOT + "/verify-password-reset", ROOT + "/reset-password");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RegisterService registerService;
    @MockitoBean private EmailVerificationService emailVerificationService;
    @MockitoBean private LoginService loginService;
    @MockitoBean private RefreshSessionService refreshSessionService;
    @MockitoBean private PasswordResetService passwordResetService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("stylemind.verification.hmac-secret", JwtTestSupport::randomSecret);
        registry.add("stylemind.notification.service-token", JwtTestSupport::randomSecret);
        JwtTestSupport.registerProperties(registry);
    }

    @BeforeEach
    void resetInteractionsBeforeEach() {
        clearInvocations(registerService, emailVerificationService, loginService,
                refreshSessionService, passwordResetService);
    }

    @Test
    void missingAndMalformedBodiesAreRejectedForEveryIdentityPostRouteBeforeServiceCalls() throws Exception {
        for (String route : ROUTES) {
            assertBadRequest(route, null, "missing body");
            assertBadRequest(route, "{", "malformed JSON");
            assertBadRequest(route, "null", "JSON null body");
        }
    }

    @Test
    void fieldValidationRejectsMissingNullBlankWrongTypeAndMalformedValuesWithoutServiceCalls()
            throws Exception {
        List<Probe> probes = List.of(
                new Probe("/register", "{}", "required fields"),
                new Probe("/register", "{\"email\":null,\"password\":\"" + PASSWORD + "\"}", "null email"),
                new Probe("/register", "{\"email\":\"\",\"password\":\"" + PASSWORD + "\"}", "empty email"),
                new Probe("/register", "{\"email\":\"   \",\"password\":\"" + PASSWORD + "\"}", "blank email"),
                new Probe("/register", "{\"email\":\"not-an-email\",\"password\":\"" + PASSWORD + "\"}", "invalid email"),
                new Probe("/register", "{\"email\":[],\"password\":\"" + PASSWORD + "\"}", "email wrong type"),
                new Probe("/register", "{\"email\":\"" + A + "\"}", "missing password"),
                new Probe("/register", "{\"email\":\"" + A + "\",\"password\":null}", "null password"),
                new Probe("/register", "{\"email\":\"" + A + "\",\"password\":[]}", "password wrong type"),
                new Probe("/register", "{\"email\":\"" + A + "\",\"password\":\"\"}", "empty password"),
                new Probe("/register", "{\"email\":\"" + A + "\",\"password\":\"   \"}", "blank password"),
                new Probe("/register", "{\"email\":\"" + A + "\",\"password\":\"short\"}", "password below policy"),
                new Probe("/register", "{\"email\":\"" + A + "\",\"password\":\"" + "p".repeat(73) + "\"}", "password exceeds byte limit"),
                new Probe("/verify-email", "{\"email\":\"" + A + "\"}", "missing verification code"),
                new Probe("/verify-email", "{\"email\":null,\"code\":\"012345\"}", "null verification email"),
                new Probe("/verify-email", "{\"email\":\"\",\"code\":\"012345\"}", "empty verification email"),
                new Probe("/verify-email", "{\"email\":\"   \",\"code\":\"012345\"}", "blank verification email"),
                new Probe("/verify-email", "{\"email\":\"not-an-email\",\"code\":\"012345\"}", "invalid verification email"),
                new Probe("/verify-email", "{\"email\":[],\"code\":\"012345\"}", "verification email wrong type"),
                new Probe("/verify-email", "{\"email\":\"" + A + "\",\"code\":null}", "null verification code"),
                new Probe("/verify-email", "{\"email\":\"" + A + "\",\"code\":\"\"}", "empty verification code"),
                new Probe("/verify-email", "{\"email\":\"" + A + "\",\"code\":\"01234\"}", "short verification code"),
                new Probe("/verify-email", "{\"email\":\"" + A + "\",\"code\":\"0123456\"}", "long verification code"),
                new Probe("/verify-email", "{\"email\":\"" + A + "\",\"code\":\"12a345\"}", "non-digit verification code"),
                new Probe("/verify-email", "{\"email\":\"" + A + "\",\"code\":\" 12345\"}", "whitespace verification code"),
                new Probe("/verify-email", "{\"email\":\"" + A + "\",\"code\":{}}", "code wrong type"),
                new Probe("/resend-email-verification", "{}", "required email"),
                new Probe("/resend-email-verification", "{\"email\":null}", "null email"),
                new Probe("/resend-email-verification", "{\"email\":\"\"}", "empty email"),
                new Probe("/resend-email-verification", "{\"email\":\"   \"}", "blank email"),
                new Probe("/resend-email-verification", "{\"email\":\"bad\"}", "invalid email"),
                new Probe("/resend-email-verification", "{\"email\":[]}", "email wrong type"),
                new Probe("/login", "{\"password\":\"" + PASSWORD + "\"}", "missing email"),
                new Probe("/login", "{\"email\":null,\"password\":\"" + PASSWORD + "\"}", "null email"),
                new Probe("/login", "{\"email\":\"\",\"password\":\"" + PASSWORD + "\"}", "empty email"),
                new Probe("/login", "{\"email\":\"   \",\"password\":\"" + PASSWORD + "\"}", "blank email"),
                new Probe("/login", "{\"email\":[],\"password\":\"" + PASSWORD + "\"}", "email wrong type"),
                new Probe("/login", "{\"email\":\"" + A + "\"}", "missing password"),
                new Probe("/login", "{\"email\":\"" + A + "\",\"password\":null}", "null password"),
                new Probe("/login", "{\"email\":\"" + A + "\",\"password\":\"\"}", "empty password"),
                new Probe("/login", "{\"email\":\"" + A + "\",\"password\":\"   \"}", "blank password"),
                new Probe("/login", "{\"email\":\"" + A + "\",\"password\":[]}", "password wrong type"),
                new Probe("/login", "{\"email\":\"bad\",\"password\":\"" + PASSWORD + "\"}", "invalid email"),
                new Probe("/login", "{\"email\":\"" + A + "\",\"password\":\"" + "p".repeat(73) + "\"}", "password exceeds UTF-8 byte limit"),
                new Probe("/refresh", "{}", "missing refresh token"),
                new Probe("/refresh", "{\"refresh_token\":null}", "null refresh token"),
                new Probe("/refresh", "{\"refresh_token\":\"   \"}", "blank refresh token"),
                new Probe("/refresh", "{\"refresh_token\":\"short\"}", "short refresh token"),
                new Probe("/refresh", "{\"refresh_token\":\"" + ".".repeat(43) + "\"}", "bad refresh alphabet"),
                new Probe("/refresh", "{\"refresh_token\":\" " + REFRESH_TOKEN + "\"}", "leading whitespace refresh token"),
                new Probe("/refresh", "{\"refresh_token\":\"" + REFRESH_TOKEN + " \"}", "trailing whitespace refresh token"),
                new Probe("/refresh", "{\"refresh_token\":\"" + "A".repeat(44) + "\"}", "overlong refresh token"),
                new Probe("/refresh", "{\"refresh_token\":\"" + REFRESH_TOKEN + "=\"}", "padded refresh token"),
                new Probe("/refresh", "{\"refresh_token\":{}}", "refresh token wrong type"),
                new Probe("/logout", "{}", "missing refresh token"),
                new Probe("/logout", "{\"refresh_token\":null}", "null refresh token"),
                new Probe("/logout", "{\"refresh_token\":\"   \"}", "blank refresh token"),
                new Probe("/logout", "{\"refresh_token\":\"short\"}", "short refresh token"),
                new Probe("/logout", "{\"refresh_token\":\"" + ".".repeat(43) + "\"}", "bad refresh alphabet"),
                new Probe("/logout", "{\"refresh_token\":\" " + REFRESH_TOKEN + "\"}", "leading whitespace refresh token"),
                new Probe("/logout", "{\"refresh_token\":\"" + REFRESH_TOKEN + " \"}", "trailing whitespace refresh token"),
                new Probe("/logout", "{\"refresh_token\":\"" + "A".repeat(44) + "\"}", "overlong refresh token"),
                new Probe("/logout", "{\"refresh_token\":\"" + REFRESH_TOKEN + "=\"}", "padded refresh token"),
                new Probe("/logout", "{\"refresh_token\":[]}", "refresh token wrong type"),
                new Probe("/forgot-password", "{}", "required email"),
                new Probe("/forgot-password", "{\"email\":null}", "null email"),
                new Probe("/forgot-password", "{\"email\":\"\"}", "empty email"),
                new Probe("/forgot-password", "{\"email\":\"   \"}", "blank email"),
                new Probe("/forgot-password", "{\"email\":\"bad\"}", "invalid email"),
                new Probe("/forgot-password", "{\"email\":[]}", "email wrong type"),
                new Probe("/verify-password-reset", "{\"email\":null,\"code\":\"012345\"}", "null reset email"),
                new Probe("/verify-password-reset", "{\"email\":\"\",\"code\":\"012345\"}", "empty reset email"),
                new Probe("/verify-password-reset", "{\"email\":\"   \",\"code\":\"012345\"}", "blank reset email"),
                new Probe("/verify-password-reset", "{\"email\":\"not-an-email\",\"code\":\"012345\"}", "invalid reset email"),
                new Probe("/verify-password-reset", "{\"email\":\"" + A + "\"}", "missing reset code"),
                new Probe("/verify-password-reset", "{\"email\":\"" + A + "\",\"code\":null}", "null reset code"),
                new Probe("/verify-password-reset", "{\"email\":\"" + A + "\",\"code\":\"\"}", "empty reset code"),
                new Probe("/verify-password-reset", "{\"email\":\"" + A + "\",\"code\":\"01234\"}", "short reset code"),
                new Probe("/verify-password-reset", "{\"email\":\"" + A + "\",\"code\":\"0123456\"}", "long reset code"),
                new Probe("/verify-password-reset", "{\"email\":\"" + A + "\",\"code\":\"12a345\"}", "non-digit reset code"),
                new Probe("/verify-password-reset", "{\"email\":\"" + A + "\",\"code\":\" 12345\"}", "whitespace reset code"),
                new Probe("/verify-password-reset", "{\"email\":[],\"code\":\"012345\"}", "reset email wrong type"),
                new Probe("/verify-password-reset", "{\"email\":\"" + A + "\",\"code\":[]}", "reset code wrong type"),
                new Probe("/reset-password", "{}", "required token and password"),
                new Probe("/reset-password", "{\"reset_token\":null,\"new_password\":\"" + PASSWORD + "\"}", "null reset token"),
                new Probe("/reset-password", "{\"reset_token\":\"   \",\"new_password\":\"" + PASSWORD + "\"}", "blank reset token"),
                new Probe("/reset-password", "{\"reset_token\":\"short\",\"new_password\":\"" + PASSWORD + "\"}", "short reset token"),
                new Probe("/reset-password", "{\"reset_token\":\"" + ".".repeat(43) + "\",\"new_password\":\"" + PASSWORD + "\"}", "bad reset-token alphabet"),
                new Probe("/reset-password", "{\"reset_token\":\"" + "A".repeat(44) + "\",\"new_password\":\"" + PASSWORD + "\"}", "overlong reset token"),
                new Probe("/reset-password", "{\"reset_token\":\"" + RESET_TOKEN + "=\",\"new_password\":\"" + PASSWORD + "\"}", "padded reset token"),
                new Probe("/reset-password", "{\"reset_token\":[],\"new_password\":\"" + PASSWORD + "\"}", "reset token wrong type"),
                new Probe("/reset-password", "{\"reset_token\":\"" + RESET_TOKEN + "\",\"new_password\":null}", "null new password"),
                new Probe("/reset-password", "{\"reset_token\":\"" + RESET_TOKEN + "\",\"new_password\":\"\"}", "empty new password"),
                new Probe("/reset-password", "{\"reset_token\":\"" + RESET_TOKEN + "\",\"new_password\":\"   \"}", "blank new password"),
                new Probe("/reset-password", "{\"reset_token\":\"" + RESET_TOKEN + "\",\"new_password\":[]}", "new password wrong type"),
                new Probe("/reset-password", "{\"reset_token\":\"" + RESET_TOKEN + "\",\"new_password\":\"short\"}", "new password below policy"),
                new Probe("/reset-password", "{\"reset_token\":\"" + RESET_TOKEN + "\",\"new_password\":\"" + "p".repeat(73) + "\"}", "new password exceeds byte limit"));

        for (Probe probe : probes) {
            assertBadRequest(ROOT + probe.suffix(), probe.body(), probe.label());
        }
    }

    @Test
    void wrongContentTypeAndWrongMethodDoNotReachAnyIdentityService() throws Exception {
        for (String route : ROUTES) {
            clearServiceInteractions();
            mockMvc.perform(post(route).contentType(MediaType.TEXT_PLAIN).content("{}"))
                    .andExpect(status().isUnsupportedMediaType());
            verifyNoServiceInteractions();

            clearServiceInteractions();
            MvcResult result = mockMvc.perform(get(route)).andReturn();
            assertThat(result.getResponse().getStatus()).isBetween(400, 499);
            System.out.println("AUDIT_WRONG_METHOD " + route + " " + result.getResponse().getStatus());
            verifyNoServiceInteractions();
        }
    }

    @Test
    void internalFieldsCannotBindOntoPublicRequestDtos() throws Exception {
        String uuidB = "00000000-0000-0000-0000-00000000000b";
        String extras = "\"account_id\":\"" + uuidB + "\",\"role\":\"ADMIN\",\"roles\":[\"ADMIN\"],"
                + "\"account_status\":\"ACTIVE\",\"email_verified_at\":\"2099-01-01T00:00:00Z\","
                + "\"password_hash\":\"forged-hash\",\"recipient_email\":\"" + B + "\","
                + "\"recipientEmail\":\"" + B + "\",\"expires_at\":\"2099-01-01T00:00:00Z\","
                + "\"failed_attempts\":0,\"revoked_at\":null";

        mockMvc.perform(post(ROOT + "/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + A + "\",\"password\":\"" + PASSWORD + "\"," + extras + "}"))
                .andExpect(status().isCreated());
        var registration = org.mockito.ArgumentCaptor.forClass(RegisterRequest.class);
        verify(registerService).register(registration.capture());
        assertThat(registration.getValue().email()).isEqualTo(A);
        assertThat(registration.getValue().password()).isEqualTo(PASSWORD);

        clearServiceInteractions();
        mockMvc.perform(post(ROOT + "/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + A + "\",\"code\":\"012345\"," + extras + "}"))
                .andExpect(status().isNoContent());
        verify(emailVerificationService).verify(A, "012345");

        clearServiceInteractions();
        mockMvc.perform(post(ROOT + "/resend-email-verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + A + "\"," + extras + "}"))
                .andExpect(status().isNoContent());
        verify(emailVerificationService).resend(A);

        for (String routeAndBody : List.of(
                "/login|{\"email\":\"" + A + "\",\"password\":\"" + PASSWORD + "\"," + extras + "}",
                "/refresh|{\"refresh_token\":\"" + REFRESH_TOKEN + "\"," + extras + "}",
                "/logout|{\"refresh_token\":\"" + REFRESH_TOKEN + "\"," + extras + "}")) {
            int split = routeAndBody.indexOf('|');
            String suffix = routeAndBody.substring(0, split);
            String body = routeAndBody.substring(split + 1);
            clearServiceInteractions();
            mockMvc.perform(post(ROOT + suffix).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
            verifyNoServiceInteractions();
        }

        clearServiceInteractions();
        mockMvc.perform(post(ROOT + "/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + A + "\"," + extras + "}"))
                .andExpect(status().isAccepted());
        verify(passwordResetService).requestOtp(A);

        clearServiceInteractions();
        mockMvc.perform(post(ROOT + "/verify-password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + A + "\",\"code\":\"012345\"," + extras + "}"))
                .andExpect(status().isOk());
        verify(passwordResetService).verifyOtp(A, "012345");

        clearServiceInteractions();
        mockMvc.perform(post(ROOT + "/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reset_token\":\"" + RESET_TOKEN + "\",\"new_password\":\""
                                + PASSWORD + "\"," + extras + "}"))
                .andExpect(status().isNoContent());
        verify(passwordResetService).resetPassword(RESET_TOKEN, PASSWORD);
    }

    @Test
    void passwordInputsKeepLeadingAndTrailingWhitespaceAtControllerBoundary() throws Exception {
        String registerPassword = "  " + "register password value" + "  ";
        mockMvc.perform(post(ROOT + "/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + A + "\",\"password\":\"" + registerPassword + "\"}"))
                .andExpect(status().isCreated());
        var registration = org.mockito.ArgumentCaptor.forClass(RegisterRequest.class);
        verify(registerService).register(registration.capture());
        assertThat(registration.getValue().password()).isEqualTo(registerPassword);

        String loginPassword = "  " + PASSWORD + "  ";
        clearServiceInteractions();
        mockMvc.perform(post(ROOT + "/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + A + "\",\"password\":\"" + loginPassword + "\"}"))
                .andExpect(status().isOk());
        var login = org.mockito.ArgumentCaptor.forClass(com.stylemind.identity.dto.login.LoginRequest.class);
        verify(loginService).login(login.capture());
        assertThat(login.getValue().password()).isEqualTo(loginPassword);

        String resetPassword = "  " + "reset password value" + "  ";
        clearServiceInteractions();
        mockMvc.perform(post(ROOT + "/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reset_token\":\"" + RESET_TOKEN + "\",\"new_password\":\""
                                + resetPassword + "\"}"))
                .andExpect(status().isNoContent());
        verify(passwordResetService).resetPassword(RESET_TOKEN, resetPassword);
    }

    private void assertBadRequest(String route, String body, String label) throws Exception {
        clearServiceInteractions();
        var request = post(route).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(body);
        }
        mockMvc.perform(request).andExpect(status().isBadRequest());
        verifyNoServiceInteractions();
    }

    private void clearServiceInteractions() {
        clearInvocations(registerService, emailVerificationService, loginService,
                refreshSessionService, passwordResetService);
    }

    private void verifyNoServiceInteractions() {
        verifyNoInteractions(registerService, emailVerificationService, loginService,
                refreshSessionService, passwordResetService);
    }

    private record Probe(String suffix, String body, String label) { }
}
