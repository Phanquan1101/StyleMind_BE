package com.stylemind.identity.controller;

import com.stylemind.identity.dto.emailverification.ResendEmailVerificationRequest;
import com.stylemind.identity.dto.emailverification.VerifyEmailRequest;
import com.stylemind.identity.dto.login.LoginRequest;
import com.stylemind.identity.dto.login.LoginResponse;
import com.stylemind.identity.dto.passwordreset.ForgotPasswordRequest;
import com.stylemind.identity.dto.passwordreset.ForgotPasswordResponse;
import com.stylemind.identity.dto.passwordreset.ResetPasswordRequest;
import com.stylemind.identity.dto.passwordreset.ResetTokenResponse;
import com.stylemind.identity.dto.passwordreset.VerifyPasswordResetRequest;
import com.stylemind.identity.dto.register.RegisterRequest;
import com.stylemind.identity.dto.register.RegisterResponse;
import com.stylemind.identity.dto.session.RefreshTokenRequest;
import com.stylemind.identity.service.AccountVerificationService;
import com.stylemind.identity.service.AuthService;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountVerificationService accountVerificationService;

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register an account",
            description = "Creates a pending customer account and emails a verification code.", responses = {
                    @ApiResponse(responseCode = "201", description = "Account created"),
                    @ApiResponse(responseCode = "400", description = "Request validation failed"),
                    @ApiResponse(responseCode = "409", description = "Email is already registered"),
                    @ApiResponse(responseCode = "503", description = "Email delivery failed after account creation"),
                    @ApiResponse(responseCode = "504", description = "Email delivery result is unknown after account creation")
            })
    @SecurityRequirements
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping(path = "/verify-email", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verify registration email", responses = {
            @ApiResponse(responseCode = "204", description = "Email verified"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired code"),
            @ApiResponse(responseCode = "409", description = "Account state does not allow verification"),
            @ApiResponse(responseCode = "429", description = "Verification temporarily locked")
    })
    @SecurityRequirements
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        accountVerificationService.verifyEmail(request.email(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/resend-email-verification", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Resend registration email verification", responses = {
            @ApiResponse(responseCode = "204", description = "SMTP accepted the verification email"),
            @ApiResponse(responseCode = "400", description = "No compatible pending account"),
            @ApiResponse(responseCode = "409", description = "Account state does not allow resend"),
            @ApiResponse(responseCode = "429", description = "Cooldown, send limit or verification lock"),
            @ApiResponse(responseCode = "503", description = "Email delivery failed"),
            @ApiResponse(responseCode = "504", description = "Email delivery result is unknown")
    })
    @SecurityRequirements
    public ResponseEntity<Void> resendEmailVerification(@Valid @RequestBody ResendEmailVerificationRequest request) {
        accountVerificationService.resendEmailVerification(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Log in", description = "Validates an account and returns an access and refresh token.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login succeeded",
                            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Request validation failed"),
                    @ApiResponse(responseCode = "401", description = "Credentials are invalid"),
                    @ApiResponse(responseCode = "403", description = "Account is not eligible to sign in"),
                    @ApiResponse(responseCode = "500", description = "Unexpected server failure")
            })
    @SecurityRequirements
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.login(request));
    }

    @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rotate a refresh token", description = "Consumes one refresh token and rotates its session. "
            + "The absolute refresh-session expiry is preserved.", responses = {
                    @ApiResponse(responseCode = "200", description = "Tokens issued",
                            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Refresh token input is malformed"),
                    @ApiResponse(responseCode = "401", description = "Refresh token is invalid"),
                    @ApiResponse(responseCode = "403", description = "Account is not eligible for tokens"),
                    @ApiResponse(responseCode = "500", description = "Unexpected server failure")
            })
    @SecurityRequirements
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.refresh(request.refreshToken()));
    }

    @PostMapping(path = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Revoke a refresh session", description = "Idempotently revokes only the session identified "
            + "by the supplied refresh token. Already issued access JWTs remain valid until expiry.", responses = {
                    @ApiResponse(responseCode = "204", description = "Session is revoked or does not exist"),
                    @ApiResponse(responseCode = "400", description = "Refresh token input is malformed"),
                    @ApiResponse(responseCode = "500", description = "Unexpected server failure")
            })
    @SecurityRequirements
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping(path = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Request password reset instructions", responses = {
            @ApiResponse(responseCode = "202", description = "Generic response, independent of account eligibility"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @SecurityRequirements
    public ResponseEntity<ForgotPasswordResponse> requestPasswordReset(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
                .body(accountVerificationService.requestPasswordReset(request.email()));
    }

    @PostMapping(path = "/verify-password-reset", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verify reset OTP and receive a one-use reset token", responses = {
            @ApiResponse(responseCode = "200", description = "OTP verified"),
            @ApiResponse(responseCode = "400", description = "Invalid request or verification failed"),
            @ApiResponse(responseCode = "429", description = "Verification temporarily locked")
    })
    @SecurityRequirements
    public ResponseEntity<ResetTokenResponse> verifyPasswordReset(
            @Valid @RequestBody VerifyPasswordResetRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accountVerificationService.verifyPasswordResetOtp(request.email(), request.code()));
    }

    @PostMapping(path = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Set a new password using a one-use reset token", responses = {
            @ApiResponse(responseCode = "204", description = "Password changed and refresh sessions revoked"),
            @ApiResponse(responseCode = "400", description = "Invalid request or reset grant")
    })
    @SecurityRequirements
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountVerificationService.resetPassword(request.resetToken(), request.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
