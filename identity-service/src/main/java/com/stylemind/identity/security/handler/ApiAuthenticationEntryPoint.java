package com.stylemind.identity.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        boolean bearerProvided = StringUtils.hasText(request.getHeader(HttpHeaders.AUTHORIZATION));
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        String code = bearerProvided ? "INVALID_BEARER_TOKEN" : "AUTHENTICATION_REQUIRED";
        String detail = bearerProvided ? "Bearer token is invalid" : "Authentication is required";

        response.setStatus(status.value());
        String requestUri = request.getRequestURI();
        if ("/api/v1/auth/login".equals(requestUri)
                || "/api/v1/auth/refresh".equals(requestUri)
                || "/api/v1/auth/logout".equals(requestUri)
                || isManageUsersEndpoint(request)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        objectMapper.writeValue(response.getOutputStream(),
                ApiProblemDetails.create(status, status.getReasonPhrase(), detail, code, request));
    }

    private boolean isManageUsersEndpoint(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/api/v1/admin/users".equals(path) || path.startsWith("/api/v1/admin/users/")
                || request.getRequestURI().startsWith("/api/v1/admin/users");
    }
}
