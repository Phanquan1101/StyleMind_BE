package com.stylemind.identity.controller;

import com.stylemind.identity.dto.LoginRequest;
import com.stylemind.identity.dto.LoginResponse;
import com.stylemind.identity.service.LoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
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
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(loginService.login(request));
    }
}
