package com.stylemind.wardrobecommunity.errors;

import com.stylemind.wardrobecommunity.controller.outfit.*;
import com.stylemind.wardrobecommunity.security.ApiProblemDetails;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Scope cache controls to sharing endpoints without changing other services' error contracts. */
@RestControllerAdvice(
        assignableTypes = {
            OutfitSharingController.class,
            FriendOutfitController.class,
            OutfitShareController.class,
            SharedOutfitController.class,
            PublicOutfitController.class
        })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OutfitSharingExceptionHandler {
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> malformed(Exception error, HttpServletRequest request) {
        return operation(
                new ApiOperationException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST",
                        "Request is missing or malformed"),
                request);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> media(Exception error, HttpServletRequest request) {
        return operation(
                new ApiOperationException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_MEDIA_TYPE",
                        "Request media type is not supported"),
                request);
    }

    @ExceptionHandler(ApiOperationException.class)
    public ResponseEntity<ProblemDetail> operation(
            ApiOperationException error, HttpServletRequest request) {
        return ResponseEntity.status(error.status())
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiProblemDetails.create(
                                error.status(),
                                error.status().getReasonPhrase(),
                                error.detail(),
                                error.code(),
                                request));
    }
}
