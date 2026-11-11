package com.stylemind.payment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.payment.dto.RefundRequests.Destination;
import com.stylemind.payment.errors.RefundErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RefundDestinationCipher {

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper mapper;
    private final SecretKey key;

    public RefundDestinationCipher(ObjectMapper mapper, @Value("${stylemind.payment.refund-encryption-key:}") String encodedKey) {
        this.mapper = mapper;
        this.key = decodeKey(encodedKey);
    }

    public byte[] encrypt(UUID refundId, Destination destination) {
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);
        byte[] plaintext = null;
        try {
            plaintext = mapper.writeValueAsBytes(destination);
            byte[] ciphertext = cipher(Cipher.ENCRYPT_MODE, refundId, nonce).doFinal(plaintext);
            byte[] envelope = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, envelope, nonce.length, ciphertext.length);
            return envelope;
        } catch (GeneralSecurityException | IOException failure) {
            throw RefundErrorCode.REFUND_DESTINATION_UNAVAILABLE.exception();
        } finally {
            clear(plaintext);
        }
    }

    public Destination decrypt(UUID refundId, byte[] envelope) {
        if (envelope == null || envelope.length <= NONCE_LENGTH + TAG_BITS / Byte.SIZE) {
            throw RefundErrorCode.REFUND_DESTINATION_UNAVAILABLE.exception();
        }
        byte[] plaintext = null;
        try {
            byte[] nonce = Arrays.copyOf(envelope, NONCE_LENGTH);
            plaintext = cipher(Cipher.DECRYPT_MODE, refundId, nonce)
                    .doFinal(envelope, NONCE_LENGTH, envelope.length - NONCE_LENGTH);
            return mapper.readValue(plaintext, Destination.class);
        } catch (GeneralSecurityException | IOException failure) {
            throw RefundErrorCode.REFUND_DESTINATION_UNAVAILABLE.exception();
        } finally {
            clear(plaintext);
        }
    }

    private Cipher cipher(int mode, UUID refundId, byte[] nonce) throws GeneralSecurityException {
        if (key == null) {
            throw RefundErrorCode.REFUND_DESTINATION_UNAVAILABLE.exception();
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(("stylemind.refund.destination.v1:" + refundId).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private static SecretKey decodeKey(String encodedKey) {
        byte[] keyBytes = null;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
            return keyBytes.length == 32 ? new SecretKeySpec(keyBytes, "AES") : null;
        } catch (IllegalArgumentException failure) {
            return null;
        } finally {
            clear(keyBytes);
        }
    }

    private static void clear(byte[] plaintext) {
        if (plaintext != null) {
            Arrays.fill(plaintext, (byte) 0);
        }
    }
}
