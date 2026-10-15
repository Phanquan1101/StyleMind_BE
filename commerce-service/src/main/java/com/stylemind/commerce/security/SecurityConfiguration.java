package com.stylemind.commerce.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/*",
                                "/api/v1/brands", "/api/v1/brands/*",
                                "/api/v1/branches", "/api/v1/branches/*",
                                "/api/v1/products", "/api/v1/products/*",
                                "/api/v1/variant-images/*/content").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/variants/batch-lookup").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/cart").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/cart/items").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/cart/items/*").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/cart/items", "/api/v1/cart/items/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/cart/merge").authenticated()
                        .requestMatchers("/api/v1/admin/categories", "/api/v1/admin/categories/**",
                                "/api/v1/admin/brands", "/api/v1/admin/brands/**",
                                "/api/v1/admin/branches", "/api/v1/admin/branches/**",
                                "/api/v1/admin/products", "/api/v1/admin/products/**",
                                "/api/v1/admin/variants", "/api/v1/admin/variants/**",
                                "/api/v1/admin/variant-images", "/api/v1/admin/variant-images/**",
                                "/api/v1/admin/orders", "/api/v1/admin/orders/**",
                                "/api/v1/admin/cancellation-requests", "/api/v1/admin/cancellation-requests/**")
                        .hasAuthority("ROLE_ADMIN")
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
