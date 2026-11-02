package com.stylemind.commerce.security;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class PaymentServiceSecurityTests {
    @Test void blankConfiguredCredentialFailsClosed() throws Exception {
        var request = new MockHttpServletRequest("POST", "/internal/v1/payment-events");
        request.addHeader("X-StyleMind-Service-Token", "untrusted-test-only");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new PaymentServiceSecurity.TokenFilter("", new ObjectMapper()).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(503); assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).doesNotContain("untrusted-test-only");
    }
}
