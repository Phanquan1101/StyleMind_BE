package com.stylemind.identity.errors.handler;

import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.ErrorCode;
import com.stylemind.identity.security.handler.ApiProblemDetails;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> invalidRequest(Exception exception, HttpServletRequest request) {
        return response(ErrorCode.INVALID_REQUEST, "Bad Request", "Request is missing or malformed", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> methodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return response(ErrorCode.METHOD_NOT_ALLOWED, "Method Not Allowed", "HTTP method is not supported", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> unsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                "Request media type is not supported", request);
    }

    @ExceptionHandler(ApiOperationException.class)
    public ResponseEntity<ProblemDetail> operationFailure(
            ApiOperationException exception, HttpServletRequest request) {
        ProblemDetail problem = ApiProblemDetails.create(exception.status(), exception.status().getReasonPhrase(),
                exception.detail(), exception.code(), request);
        if (exception.accountCreated()) {
            problem.setProperty("account_created", true);
            problem.setProperty("account_status", "PENDING_VERIFICATION");
            problem.setProperty("next_action", "RESEND_EMAIL_VERIFICATION");
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.status());
        if (isNoStoreEndpoint(request)) {
            response.header("Cache-Control", "no-store");
        }
        if (exception.retryAfterSeconds() > 0) {
            response.header("Retry-After", Long.toString(exception.retryAfterSeconds()));
        }
        return response.body(problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return response(ErrorCode.RESOURCE_NOT_FOUND, "Not Found", "Resource was not found", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure correlationId={} exceptionType={}",
                MDC.get("correlationId"), exception.getClass().getSimpleName());
        return response(ErrorCode.INTERNAL_ERROR, "Internal Server Error", "An unexpected error occurred", request);
    }

    private ResponseEntity<ProblemDetail> response(
            ErrorCode errorCode,
            String title,
            String detail,
            HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(errorCode.status());
        if (isNoStoreEndpoint(request)) {
            response.header("Cache-Control", "no-store");
        }
        return response.body(ApiProblemDetails.create(errorCode.status(), title, detail, errorCode.code(), request));
    }

    private boolean isTokenEndpoint(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return "/api/v1/auth/login".equals(requestUri)
                || "/api/v1/auth/refresh".equals(requestUri)
                || "/api/v1/auth/logout".equals(requestUri)
                || "/api/v1/auth/forgot-password".equals(requestUri)
                || "/api/v1/auth/verify-password-reset".equals(requestUri)
                || "/api/v1/auth/reset-password".equals(requestUri);
    }

    private boolean isAddressEndpoint(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri.startsWith("/api/v1/me/addresses")
                || requestUri.startsWith("/api/v1/locations/");
    }

    private boolean isNoStoreEndpoint(HttpServletRequest request) {
        return isTokenEndpoint(request) || isAddressEndpoint(request) || isManageUsersEndpoint(request);
    }

    private boolean isManageUsersEndpoint(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/api/v1/admin/users".equals(path) || path.startsWith("/api/v1/admin/users/")
                || request.getRequestURI().startsWith("/api/v1/admin/users");
    }
}
