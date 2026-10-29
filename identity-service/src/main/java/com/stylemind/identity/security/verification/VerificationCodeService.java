package com.stylemind.identity.security.verification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public final class VerificationCodeService {

    private final byte[] secret;
    private final SecureRandom random;

    public VerificationCodeService(
            @Value("${stylemind.verification.hmac-secret:}") String encodedSecret,
            SecureRandom random) {
        try {
            this.secret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("STYLEMIND_VERIFICATION_HMAC_SECRET must be valid base64", exception);
        }
        if (secret.length < 32) {
            throw new IllegalStateException("STYLEMIND_VERIFICATION_HMAC_SECRET must contain at least 32 random bytes");
        }
        this.random = random;
    }

    public String generateCode() {
        int value = random.nextInt(1_000_000);
        return "%06d".formatted(value);
    }

    public byte[] digest(UUID accountId, UUID challengeId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String message = accountId + ":" + challengeId + ":" + code;
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Verification digest could not be computed");
        }
    }

    public boolean matches(UUID accountId, UUID challengeId, String code, byte[] expectedDigest) {
        return MessageDigest.isEqual(digest(accountId, challengeId, code), expectedDigest);
    }
}
