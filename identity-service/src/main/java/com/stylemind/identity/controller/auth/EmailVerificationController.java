package com.stylemind.identity.controller.auth;

import com.stylemind.identity.dto.emailverification.ResendEmailVerificationRequest;
import com.stylemind.identity.dto.emailverification.VerifyEmailRequest;
import com.stylemind.identity.service.emailverification.EmailVerificationService;
import com.stylemind.identity.model.emailverification.VerificationOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService verificationService;

    @PostMapping(path = "/verify-email", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Verify registration email", responses = {
            @ApiResponse(responseCode = "204", description = "Email verified"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired code"),
            @ApiResponse(responseCode = "409", description = "Account state does not allow verification"),
            @ApiResponse(responseCode = "429", description = "Verification temporarily locked")
    })
    @SecurityRequirements
    public ResponseEntity<Void> verify(@Valid @RequestBody VerifyEmailRequest request) {
        VerificationOutcome outcome = verificationService.verify(request.email(), request.code());
        verificationService.requireSuccess(outcome);
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
    public ResponseEntity<Void> resend(@Valid @RequestBody ResendEmailVerificationRequest request) {
        VerificationOutcome outcome = verificationService.resend(request.email());
        verificationService.requireSuccess(outcome);
        return ResponseEntity.noContent().build();
    }
}
