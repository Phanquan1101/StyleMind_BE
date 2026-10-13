package com.stylemind.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-email").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/resend-email-verification").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-password-reset").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers("/api/v1/admin/users", "/api/v1/admin/users/**")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/v1/me", "/api/v1/me/style-profile")
                        .hasAnyAuthority("ROLE_CUSTOMER", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/me/addresses", "/api/v1/me/addresses/*",
                                "/api/v1/locations/provinces", "/api/v1/locations/provinces/*/wards")
                        .hasAnyAuthority("ROLE_CUSTOMER", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/me/addresses", "/api/v1/me/addresses/*",
                                "/api/v1/locations/provinces", "/api/v1/locations/provinces/*/wards")
                        .hasAnyAuthority("ROLE_CUSTOMER", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/me/addresses")
                        .hasAnyAuthority("ROLE_CUSTOMER", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/me/addresses/*")
                        .hasAnyAuthority("ROLE_CUSTOMER", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/me/addresses/*/default")
                        .hasAnyAuthority("ROLE_CUSTOMER", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/me/addresses/*")
                        .hasAnyAuthority("ROLE_CUSTOMER", "ROLE_ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
