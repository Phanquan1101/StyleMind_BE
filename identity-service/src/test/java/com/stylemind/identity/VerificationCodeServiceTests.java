package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.stylemind.identity.security.VerificationCodeService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationCodeServiceTests {

    @Test
    void sixDigitCodePreservesLeadingZeroAndDigestIsBoundToAccountAndChallenge() {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) 0x5a);
        SecureRandom deterministic = new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                return 12_345;
            }
        };
        VerificationCodeService service = new VerificationCodeService(Base64.getEncoder().encodeToString(bytes),
                deterministic);
        UUID accountId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();

        String code = service.generateCode();
        byte[] digest = service.digest(accountId, challengeId, code);

        assertThat(code).isEqualTo("012345");
        assertThat(digest).hasSize(32);
        assertThat(service.matches(accountId, challengeId, code, digest)).isTrue();
        assertThat(service.matches(UUID.randomUUID(), challengeId, code, digest)).isFalse();
        assertThat(service.matches(accountId, UUID.randomUUID(), code, digest)).isFalse();
        assertThat(service.matches(accountId, challengeId, "999999", digest)).isFalse();
    }
}
