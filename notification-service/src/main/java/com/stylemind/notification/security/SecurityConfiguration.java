package com.stylemind.notification.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            InternalServiceTokenFilter internalServiceTokenFilter,
            @Qualifier("internalEmailVerificationPostMatcher") RequestMatcher internalEmailVerificationPostMatcher,
            @Qualifier("internalPasswordResetPostMatcher") RequestMatcher internalPasswordResetPostMatcher,
            @Qualifier("internalOutfitShareInvitationPostMatcher") RequestMatcher internalOutfitShareInvitationPostMatcher,
            @Qualifier("internalFriendInvitationPostMatcher") RequestMatcher internalFriendInvitationPostMatcher)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers(internalEmailVerificationPostMatcher).permitAll()
                        .requestMatchers(internalPasswordResetPostMatcher).permitAll()
                        .requestMatchers(internalOutfitShareInvitationPostMatcher).permitAll()
                        .requestMatchers(internalFriendInvitationPostMatcher).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(internalServiceTokenFilter, AuthorizationFilter.class)
                .build();
    }

    @Bean("internalEmailVerificationPostMatcher")
    RequestMatcher internalEmailVerificationPostMatcher(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        if (handlerMapping.getPatternParser() == null) {
            throw new IllegalStateException("Notification internal route requires Spring MVC PathPatternParser");
        }
        return PathPatternRequestMatcher.withPathPatternParser(handlerMapping.getPatternParser())
                .matcher(HttpMethod.POST, "/internal/v1/email-verifications");
    }

    @Bean("internalPasswordResetPostMatcher")
    RequestMatcher internalPasswordResetPostMatcher(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        if (handlerMapping.getPatternParser() == null) {
            throw new IllegalStateException("Notification internal route requires Spring MVC PathPatternParser");
        }
        return PathPatternRequestMatcher.withPathPatternParser(handlerMapping.getPatternParser())
                .matcher(HttpMethod.POST, "/internal/v1/password-resets");
    }

    @Bean("internalOutfitShareInvitationPostMatcher")
    RequestMatcher internalOutfitShareInvitationPostMatcher(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        if (handlerMapping.getPatternParser() == null) {
            throw new IllegalStateException("Notification internal route requires Spring MVC PathPatternParser");
        }
        return PathPatternRequestMatcher.withPathPatternParser(handlerMapping.getPatternParser())
                .matcher(HttpMethod.POST, "/internal/v1/outfit-share-invitations");
    }

    @Bean("internalFriendInvitationPostMatcher")
    RequestMatcher internalFriendInvitationPostMatcher(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        if (handlerMapping.getPatternParser() == null) {
            throw new IllegalStateException("Notification internal route requires Spring MVC PathPatternParser");
        }
        return PathPatternRequestMatcher.withPathPatternParser(handlerMapping.getPatternParser())
                .matcher(HttpMethod.POST, "/internal/v1/friend-invitations");
    }

    @Bean
    FilterRegistrationBean<InternalServiceTokenFilter> internalServiceTokenFilterServletRegistration(
            InternalServiceTokenFilter filter) {
        FilterRegistrationBean<InternalServiceTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
