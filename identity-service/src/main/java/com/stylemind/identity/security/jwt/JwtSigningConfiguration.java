package com.stylemind.identity.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtSigningConfiguration {

    @Bean
    JwtKeyMaterial jwtKeyMaterial(JwtProperties properties) {
        validateProperties(properties);
        Path path = Path.of(properties.privateKeyPath().trim());
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException("JWT private key file is missing or unreadable");
        }

        try {
            byte[] der = decodePkcs8Pem(Files.readString(path, StandardCharsets.US_ASCII));
            RSAPrivateKey privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(privateKey instanceof RSAPrivateCrtKey rsaPrivateCrtKey)) {
                throw new IllegalStateException("JWT private key must contain RSA CRT parameters");
            }
            if (privateKey.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("JWT RSA key must be at least 2048 bits");
            }

            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), rsaPrivateCrtKey.getPublicExponent()));
            String keyId = new RSAKey.Builder(publicKey).build().computeThumbprint().toString();
            RSAKey signingJwk = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyUse(KeyUse.SIGNATURE)
                    .build();
            return new JwtKeyMaterial(privateKey, publicKey, keyId, signingJwk, signingJwk.toPublicJWK());
        } catch (IOException | java.security.GeneralSecurityException | com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException("JWT private key must be a valid PKCS#8 RSA key", exception);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyMaterial keyMaterial) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(keyMaterial.signingJwk())));
    }

    private static void validateProperties(JwtProperties properties) {
        if (!StringUtils.hasText(properties.issuer())) {
            throw new IllegalStateException("STYLEMIND_JWT_ISSUER must be configured");
        }
        if (!StringUtils.hasText(properties.audience())) {
            throw new IllegalStateException("STYLEMIND_JWT_AUDIENCE must be configured");
        }
        if (!StringUtils.hasText(properties.privateKeyPath())) {
            throw new IllegalStateException("STYLEMIND_JWT_PRIVATE_KEY_PATH must be configured");
        }
        requireWholePositiveSeconds(properties.accessTtl(), "STYLEMIND_JWT_ACCESS_TTL");
        requireWholePositiveSeconds(properties.refreshTtl(), "STYLEMIND_JWT_REFRESH_TTL");
    }

    private static void requireWholePositiveSeconds(Duration duration, String propertyName) {
        if (duration == null || duration.isNegative() || duration.isZero()
                || duration.getNano() != 0 || duration.getSeconds() < 1) {
            throw new IllegalStateException(propertyName + " must be a positive whole number of seconds");
        }
    }

    private static byte[] decodePkcs8Pem(String pem) {
        String header = "-----BEGIN PRIVATE KEY-----";
        String footer = "-----END PRIVATE KEY-----";
        int headerIndex = pem.indexOf(header);
        int footerIndex = pem.indexOf(footer);
        if (headerIndex < 0 || footerIndex <= headerIndex) {
            throw new IllegalStateException("JWT private key must use PKCS#8 PEM encoding");
        }
        String body = pem.substring(headerIndex + header.length(), footerIndex).replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT private key PEM is malformed", exception);
        }
    }
}
