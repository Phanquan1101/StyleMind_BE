package com.stylemind.notification.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    private final List<RequestMatcher> internalPostMatchers;
    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public InternalServiceTokenFilter(
            @Value("${stylemind.security.service-token:}") String token,
            @Qualifier("internalEmailVerificationPostMatcher") RequestMatcher emailVerificationPostMatcher,
            @Qualifier("internalPasswordResetPostMatcher") RequestMatcher internalPasswordResetPostMatcher,
            @Qualifier("internalOutfitShareInvitationPostMatcher") RequestMatcher internalOutfitShareInvitationPostMatcher,
            ObjectMapper objectMapper) {
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length < 32) {
            throw new IllegalStateException("STYLEMIND_SERVICE_TOKEN must contain at least 32 bytes");
        }
        this.internalPostMatchers = List.of(emailVerificationPostMatcher, internalPasswordResetPostMatcher,
                internalOutfitShareInvitationPostMatcher);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (internalPostMatchers.stream().anyMatch(matcher -> matcher.matches(request))) {
            String supplied = request.getHeader("X-StyleMind-Service-Token");
            byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expectedToken, actual)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                objectMapper.writeValue(response.getOutputStream(), ApiProblemDetails.create(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized",
                        "A valid internal service token is required", "INTERNAL_SERVICE_UNAUTHORIZED", request));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
