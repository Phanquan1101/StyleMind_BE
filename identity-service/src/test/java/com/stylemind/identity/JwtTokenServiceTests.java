package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.stylemind.identity.security.jwt.JwtKeyMaterial;
import com.stylemind.identity.security.jwt.JwtProperties;
import com.stylemind.identity.security.jwt.JwtTokenService;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

class JwtTokenServiceTests {

    @Test
    void expiresInAndSignedAccessExpiryFollowConfiguredAccessTtl() throws Exception {
        RSAKey key = JwtTestSupport.SIGNING_KEY;
        JwtKeyMaterial keyMaterial = new JwtKeyMaterial(
                key.toRSAPrivateKey(), key.toRSAPublicKey(), key.getKeyID(), key, key.toPublicJWK());
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
        JwtProperties properties = new JwtProperties(
                JwtTestSupport.ISSUER,
                JwtTestSupport.AUDIENCE,
                "unused-in-this-test",
                Duration.ofMinutes(7),
                Duration.ofDays(7));
        Instant issuedAt = Instant.now().minusSeconds(2).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        JwtTokenService service = new JwtTokenService(
                encoder, properties, keyMaterial, Clock.fixed(issuedAt, ZoneOffset.UTC), new SecureRandom());

        JwtTokenService.IssuedTokens tokens = service.issue(UUID.randomUUID(), List.of("CUSTOMER"));
        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        Jwt accessToken = decoder.decode(tokens.accessToken());

        assertThat(tokens.accessExpiresInSeconds()).isEqualTo(420);
        assertThat(Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()).toSeconds()).isEqualTo(420);
        assertThat(accessToken.getIssuer().toString()).isEqualTo(JwtTestSupport.ISSUER);
        assertThat(accessToken.getAudience()).containsExactly(JwtTestSupport.AUDIENCE);
    }
}
