package com.stylemind.identity.controller;

import com.stylemind.identity.dto.ForgotPasswordRequest;
import com.stylemind.identity.dto.ForgotPasswordResponse;
import com.stylemind.identity.dto.ResetPasswordRequest;
import com.stylemind.identity.dto.ResetTokenResponse;
import com.stylemind.identity.dto.VerifyPasswordResetRequest;
import com.stylemind.identity.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
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
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping(path = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Request password reset instructions", responses = {
            @ApiResponse(responseCode = "202", description = "Generic response, independent of account eligibility"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @SecurityRequirements
    public ResponseEntity<ForgotPasswordResponse> requestOtp(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
                .body(passwordResetService.requestOtp(request.email()));
    }

    @PostMapping(path = "/verify-password-reset", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verify reset OTP and receive a one-use reset token", responses = {
            @ApiResponse(responseCode = "200", description = "OTP verified"),
            @ApiResponse(responseCode = "400", description = "Invalid request or verification failed"),
            @ApiResponse(responseCode = "429", description = "Verification temporarily locked")
    })
    @SecurityRequirements
    public ResponseEntity<ResetTokenResponse> verifyOtp(@Valid @RequestBody VerifyPasswordResetRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(passwordResetService.verifyOtp(request.email(), request.code()));
    }

    @PostMapping(path = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Set a new password using a one-use reset token", responses = {
            @ApiResponse(responseCode = "204", description = "Password changed and refresh sessions revoked"),
            @ApiResponse(responseCode = "400", description = "Invalid request or reset grant")
    })
    @SecurityRequirements
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.resetToken(), request.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
