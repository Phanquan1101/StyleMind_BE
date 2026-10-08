package com.stylemind.identity;

import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;

final class JwtTestSupport {

    static final String ISSUER = "https://identity.stylemind.local";
    static final String AUDIENCE = "stylemind-api";
    private static final KeyPair SIGNING_PAIR = generateKeyPair();
    static final RSAKey SIGNING_KEY = toJwk(SIGNING_PAIR);
    static final Path PRIVATE_KEY_FILE = writePrivateKey((RSAPrivateKey) SIGNING_PAIR.getPrivate());

    private JwtTestSupport() {
    }

    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("stylemind.jwt.issuer", () -> ISSUER);
        registry.add("stylemind.jwt.audience", () -> AUDIENCE);
        registry.add("stylemind.jwt.private-key-path", () -> PRIVATE_KEY_FILE.toString());
        registry.add("stylemind.jwt.access-ttl", () -> "PT15M");
        registry.add("stylemind.jwt.refresh-ttl", () -> "P7D");
    }

    static RSAKey generateRsaKey() {
        return toJwk(generateKeyPair());
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static RSAKey toJwk(KeyPair pair) {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
            String keyId = new RSAKey.Builder(publicKey).build().computeThumbprint().toString();
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    static String randomSecret() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static Path writePrivateKey(RSAPrivateKey privateKey) {
        try {
            Path path = Files.createTempFile("stylemind-identity-test-key-", ".pem");
            String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(privateKey.getEncoded());
            Files.writeString(path, "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");
            path.toFile().deleteOnExit();
            return path;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
