package com.stylemind.identity.errors;

import com.stylemind.identity.security.ApiProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> invalidRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Bad Request", "Request is missing or malformed",
                "INVALID_REQUEST", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> methodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", "HTTP method is not supported",
                "METHOD_NOT_ALLOWED", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> unsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                "Request media type is not supported", "UNSUPPORTED_MEDIA_TYPE", request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> emailAlreadyExists(
            EmailAlreadyExistsException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "Conflict", "An account with this email already exists",
                "EMAIL_ALREADY_EXISTS", request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> invalidCredentials(
            InvalidCredentialsException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Unauthorized", "Email or password is invalid",
                "INVALID_CREDENTIALS", request);
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ProblemDetail> accountNotActive(
            AccountNotActiveException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Forbidden", "Account is not eligible to sign in",
                "ACCOUNT_NOT_ACTIVE", request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> invalidRefreshToken(
            InvalidRefreshTokenException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Unauthorized", "Refresh token is invalid",
                "INVALID_REFRESH_TOKEN", request);
    }

    @ExceptionHandler(MalformedRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> malformedRefreshToken(
            MalformedRefreshTokenException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Bad Request", "Request is missing or malformed",
                "INVALID_REQUEST", request);
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
        if (isTokenEndpoint(request)) {
            response.header("Cache-Control", "no-store");
        }
        if (exception.retryAfterSeconds() > 0) {
            response.header("Retry-After", Long.toString(exception.retryAfterSeconds()));
        }
        return response.body(problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Not Found", "Resource was not found", "RESOURCE_NOT_FOUND", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure correlationId={} exceptionType={}",
                MDC.get("correlationId"), exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred", "INTERNAL_ERROR", request);
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String title,
            String detail,
            String code,
            HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (isTokenEndpoint(request)) {
            response.header("Cache-Control", "no-store");
        }
        return response.body(ApiProblemDetails.create(status, title, detail, code, request));
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
}
