package com.stylemind.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Requires the shared service token on every /internal/** request; fails closed when no valid token is configured. */
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    public static final String SERVICE_TOKEN_HEADER = "X-StyleMind-Service-Token";

    private static final Logger log = LoggerFactory.getLogger(InternalServiceTokenFilter.class);
    private static final RequestMatcher INTERNAL_PATHS = PathPatternRequestMatcher.withDefaults().matcher("/internal/**");

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public InternalServiceTokenFilter(String token, ObjectMapper objectMapper) {
        byte[] configured = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        // Keep startup independent of an unset token; internal routes then reject every caller.
        this.expectedToken = configured.length >= 32 ? configured : null;
        this.objectMapper = objectMapper;
        if (expectedToken == null) {
            log.warn("STYLEMIND_SERVICE_TOKEN is missing or shorter than 32 bytes; every /internal/** request will be rejected");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !INTERNAL_PATHS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(SERVICE_TOKEN_HEADER);
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (expectedToken == null || !MessageDigest.isEqual(expectedToken, actual)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), ApiProblemDetails.create(
                    HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "A valid internal service token is required", "INTERNAL_SERVICE_UNAUTHORIZED", request));
            return;
        }
        chain.doFilter(request, response);
    }
}
