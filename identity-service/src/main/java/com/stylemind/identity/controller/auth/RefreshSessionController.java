package com.stylemind.identity.controller.auth;

import com.stylemind.identity.dto.login.LoginResponse;
import com.stylemind.identity.dto.session.RefreshTokenRequest;
import com.stylemind.identity.service.session.RefreshSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class RefreshSessionController {

    private final RefreshSessionService refreshSessionService;

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
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(refreshSessionService.refresh(request.refreshToken()));
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
        refreshSessionService.logout(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
