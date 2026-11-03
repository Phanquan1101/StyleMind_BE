package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.stylemind.identity.entity.Account;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.ErrorCode;
import com.stylemind.identity.errors.handler.ApiExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerTests {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void operationErrorsPreserveProblemCodeRetryAfterAndNoStoreHeaders() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/forgot-password");
        ApiOperationException failure = new ApiOperationException(
                ErrorCode.PASSWORD_RESET_ATTEMPTS_EXCEEDED, "Try again later", 900);

        ResponseEntity<ProblemDetail> response = handler.operationFailure(failure, request);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().getStatus()).isEqualTo(429);
        assertThat(response.getBody().getProperties()).containsEntry("code", "PASSWORD_RESET_ATTEMPTS_EXCEEDED");
    }

    @Test
    void registrationDeliveryFailurePreservesRecoveryDetails() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        ApiOperationException deliveryFailure = ApiOperationException.registrationDeliveryFailure(
                new ApiOperationException(ErrorCode.EMAIL_DELIVERY_UNKNOWN, "Delivery outcome is unknown"));

        ResponseEntity<ProblemDetail> response = handler.operationFailure(deliveryFailure, request);

        assertThat(response.getStatusCode().value()).isEqualTo(504);
        assertThat(response.getBody().getDetail()).isEqualTo(
                "Account was created and remains PENDING_VERIFICATION. Request a new verification email.");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "EMAIL_DELIVERY_UNKNOWN")
                .containsEntry("account_created", true)
                .containsEntry("account_status", "PENDING_VERIFICATION")
                .containsEntry("next_action", "RESEND_EMAIL_VERIFICATION");
    }
}
