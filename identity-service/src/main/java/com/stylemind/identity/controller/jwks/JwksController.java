package com.stylemind.identity.controller.jwks;

import com.stylemind.identity.security.JwtKeyMaterial;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

    private final RSAKey publicJwk;

    public JwksController(JwtKeyMaterial keyMaterial) {
        this.publicJwk = keyMaterial.publicJwk();
    }

    @GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get public signing keys", description = "Returns public RSA keys used to verify access tokens.")
    @SecurityRequirements
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic())
                .body(Map.of("keys", List.of(publicJwk.toJSONObject())));
    }
}
