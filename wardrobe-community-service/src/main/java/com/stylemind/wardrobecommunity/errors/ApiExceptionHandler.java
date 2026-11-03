package com.stylemind.wardrobecommunity.errors;

import com.stylemind.wardrobecommunity.security.ApiProblemDetails;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(FriendInvitationFailure.class)
    public ResponseEntity<ProblemDetail> invitationFailure(FriendInvitationFailure exception, HttpServletRequest request) {
        ProblemDetail detail = ApiProblemDetails.create(exception.status(), exception.status().getReasonPhrase(),
                "Friend invitation action could not be completed", exception.getMessage(), request);
        if (exception.invitationId() != null) detail.setProperty("friend_invitation_id", exception.invitationId());
        detail.setProperty("retry_after_seconds", exception.retryAfter());
        var response = ResponseEntity.status(exception.status()).cacheControl(org.springframework.http.CacheControl.noStore());
        if (exception.retryAfter() > 0) response.header("Retry-After", Long.toString(exception.retryAfter()));
        return response.body(detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> uploadTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large", "Image upload exceeds the configured multipart limit",
                "CHAT_IMAGE_TOO_LARGE", request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ProblemDetail> malformedUpload(MultipartException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Bad Request", "Image request is missing or malformed", "INVALID_REQUEST", request);
    }

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

    @ExceptionHandler(ApiOperationException.class)
    public ResponseEntity<ProblemDetail> operationFailure(
            ApiOperationException exception, HttpServletRequest request) {
        return response(exception.status(), exception.status().getReasonPhrase(), exception.detail(),
                exception.code(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Not Found", "Resource was not found", "RESOURCE_NOT_FOUND", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure correlationId={}", MDC.get("correlationId"), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred", "INTERNAL_ERROR", request);
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String title,
            String detail,
            String code,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiProblemDetails.create(status, title, detail, code, request));
    }
}
