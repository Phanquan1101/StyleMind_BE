package com.stylemind.payment.security;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
public class CommerceHandoffSecurity {
    @Bean
    @Order(1)
    SecurityFilterChain commerceHandoffFilterChain(HttpSecurity http, ObjectMapper mapper,
            @Value("${stylemind.payment.handoff-token:}") String token) throws Exception {
        return http.securityMatcher("/internal/v1/payments/order-attempts")
                .csrf(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new TokenFilter(token, mapper), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().hasAuthority("COMMERCE_HANDOFF"))
                .build();
    }

    // Constructed only inside this chain, not registered as a global servlet filter.
    static final class TokenFilter extends OncePerRequestFilter {
        private final String token;
        private final ObjectMapper mapper;

        TokenFilter(String token, ObjectMapper mapper) {
            this.token = token;
            this.mapper = mapper;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String supplied = request.getHeader("X-StyleMind-Service-Token");
            HttpStatus failure = !StringUtils.hasText(token) ? HttpStatus.SERVICE_UNAVAILABLE
                    : supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                            supplied.getBytes(StandardCharsets.UTF_8)) ? HttpStatus.UNAUTHORIZED : null;
            if (failure != null) {
                response.setStatus(failure.value());
                response.setContentType("application/problem+json");
                response.setHeader("Cache-Control", "no-store");
                mapper.writeValue(response.getOutputStream(), ApiProblemDetails.create(failure,
                        failure.getReasonPhrase(), "Commerce payment authentication is unavailable or invalid",
                        "COMMERCE_HANDOFF_ACCESS_DENIED", request));
                return;
            }
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken("commerce-service", null,
                    List.of(new SimpleGrantedAuthority("COMMERCE_HANDOFF"))));
            SecurityContextHolder.setContext(context);
            chain.doFilter(request, response);
        }
    }
}
