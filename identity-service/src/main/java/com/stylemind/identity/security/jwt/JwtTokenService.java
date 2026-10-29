package com.stylemind.identity.security.jwt;

import com.stylemind.identity.dto.login.LoginResponse;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {

    public record IssuedTokens(
            String accessToken,
            String refreshToken,
            String refreshTokenHash,
            OffsetDateTime issuedAt,
            OffsetDateTime accessExpiresAt,
            OffsetDateTime refreshExpiresAt,
            long accessExpiresInSeconds) {

        public LoginResponse response() {
            return new LoginResponse(accessToken, refreshToken, "Bearer", accessExpiresInSeconds);
        }

        @Override
        public String toString() {
            return "IssuedTokens[accessToken=[REDACTED], refreshToken=[REDACTED], refreshTokenHash=[REDACTED],"
                    + " issuedAt=" + issuedAt + ", accessExpiresAt=" + accessExpiresAt
                    + ", refreshExpiresAt=" + refreshExpiresAt + ", accessExpiresInSeconds="
                    + accessExpiresInSeconds + "]";
        }
    }

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final JwtKeyMaterial keyMaterial;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            JwtProperties properties,
            JwtKeyMaterial keyMaterial,
            Clock clock,
            SecureRandom secureRandom) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.keyMaterial = keyMaterial;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public IssuedTokens issue(UUID accountId, List<String> roles) {
        Instant issuedAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return issue(accountId, roles, issuedAt, issuedAt, issuedAt.plus(properties.refreshTtl()));
    }

    public IssuedTokens rotate(UUID accountId, List<String> roles, OffsetDateTime refreshExpiresAt) {
        Instant sessionIssuedAt = clock.instant();
        Instant jwtIssuedAt = sessionIssuedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return issue(accountId, roles, sessionIssuedAt, jwtIssuedAt, refreshExpiresAt.toInstant());
    }

    public String refreshTokenHash(String refreshToken) {
        return sha256Hex(refreshToken);
    }

    private IssuedTokens issue(
            UUID accountId,
            List<String> roles,
            Instant sessionIssuedAt,
            Instant jwtIssuedAt,
            Instant refreshExpiresAt) {
        Instant accessExpiresAt = jwtIssuedAt.plus(properties.accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(accountId.toString())
                .issuedAt(jwtIssuedAt)
                .expiresAt(accessExpiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", List.copyOf(roles))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keyMaterial.keyId())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        byte[] refreshBytes = new byte[32];
        secureRandom.nextBytes(refreshBytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(refreshBytes);
        String refreshTokenHash = refreshTokenHash(refreshToken);

        return new IssuedTokens(
                accessToken,
                refreshToken,
                refreshTokenHash,
                OffsetDateTime.ofInstant(sessionIssuedAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(accessExpiresAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(refreshExpiresAt, ZoneOffset.UTC),
                properties.accessTtl().toSeconds());
    }

    private static String sha256Hex(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
