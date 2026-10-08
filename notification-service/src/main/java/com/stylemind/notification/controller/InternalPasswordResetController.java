package com.stylemind.notification.controller;

import com.stylemind.notification.dto.PasswordResetEmailRequest;
import com.stylemind.notification.service.PasswordResetEmailSender;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
public class InternalPasswordResetController {

    private final PasswordResetEmailSender sender;

    public InternalPasswordResetController(PasswordResetEmailSender sender) {
        this.sender = sender;
    }

    @PostMapping(path = "/password-resets", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Send a password reset code", responses = {
            @ApiResponse(responseCode = "202", description = "SMTP accepted the message"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid service token"),
            @ApiResponse(responseCode = "503", description = "SMTP rejected the message"),
            @ApiResponse(responseCode = "504", description = "SMTP acceptance could not be determined")
    })
    @SecurityRequirements(value = @SecurityRequirement(name = "serviceToken"))
    public ResponseEntity<Void> send(@Valid @RequestBody PasswordResetEmailRequest request) {
        sender.send(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
