package com.stylemind.stylist.service.generation;

import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.ai.openai.api.common.OpenAiApiClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

final class OpenAiFailureMapper {
    private OpenAiFailureMapper() { }

    static StylistApiException map(RuntimeException failure) {
        if (hasTimeout(failure)) return new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OpenAiApiClientErrorException) {
                return new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
            }
            if (cause instanceof RestClientResponseException response) {
                int status = response.getStatusCode().value();
                if (status == 408 || status == 504) {
                    return new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
                }
                if (status == 401 || status == 403 || status == 429 || status >= 500) {
                    return new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
                }
                return new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
            }
        }
        if (failure instanceof ResourceAccessException) {
            return new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        }
        return new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
    }

    private static boolean hasTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) return true;
        }
        return false;
    }
}
