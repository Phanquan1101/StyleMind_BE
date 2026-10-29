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
public final class PasswordResetCryptography {

    private static final String PURPOSE = "PASSWORD_RESET";

    private final byte[] hmacSecret;
    private final SecureRandom random;

    public PasswordResetCryptography(
            @Value("${stylemind.verification.hmac-secret:}") String encodedSecret,
            SecureRandom random) {
        try {
            this.hmacSecret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("STYLEMIND_VERIFICATION_HMAC_SECRET must be valid base64", exception);
        }
        if (hmacSecret.length < 32) {
            throw new IllegalStateException("STYLEMIND_VERIFICATION_HMAC_SECRET must contain at least 32 random bytes");
        }
        this.random = random;
    }

    public String generateOtp() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    public byte[] otpDigest(UUID accountId, UUID challengeId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            String message = PURPOSE + ":" + accountId + ":" + challengeId + ":" + code;
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Password reset digest could not be computed");
        }
    }

    public boolean matchesOtp(UUID accountId, UUID challengeId, String code, byte[] expectedDigest) {
        return MessageDigest.isEqual(otpDigest(accountId, challengeId, code), expectedDigest);
    }

    public String generateResetToken() {
        byte[] token = new byte[32];
        random.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public byte[] resetTokenHash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Password reset token hash could not be computed");
        }
    }
}
